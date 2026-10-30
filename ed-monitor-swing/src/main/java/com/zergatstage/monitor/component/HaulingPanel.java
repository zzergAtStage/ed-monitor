package com.zergatstage.monitor.component;

import com.zergatstage.domain.ConstructionSite;
import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.http.dto.MarketDto;
import com.zergatstage.monitor.routes.dto.CarrierSnapshot;
import com.zergatstage.monitor.routes.dto.DeliveryRunDto;
import com.zergatstage.monitor.routes.dto.HaulingMode;
import com.zergatstage.monitor.routes.dto.PurchaseDto;
import com.zergatstage.monitor.routes.dto.RoutePlanDto;
import com.zergatstage.monitor.routes.dto.RunLegDto;
import com.zergatstage.monitor.routes.service.GreedyRouteOptimizationService;
import com.zergatstage.monitor.routes.service.RouteOptimizationService;
import com.zergatstage.monitor.routes.spi.DefaultRouteOptimizerDataProvider;
import com.zergatstage.monitor.routes.spi.RouteOptimizerDataProvider;
import com.zergatstage.monitor.routes.ui.HaulingPlanState;
import com.zergatstage.monitor.routes.ui.RouteOptimizerController;
import com.zergatstage.monitor.routes.ui.RouteOptimizerModel;
import com.zergatstage.monitor.service.CommodityRegistry;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import com.zergatstage.monitor.service.MarketDataHttpService;
import com.zergatstage.monitor.service.managers.CargoInventoryManager;
import com.zergatstage.monitor.service.managers.CarrierManager;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Persistent "Hauling" tab that plans delivery routes for a construction site and, unlike the
 * legacy {@link com.zergatstage.monitor.routes.ui.RouteOptimizerDialog}, stays open across
 * plans so the commander can iterate on cargo capacity, run limits, and Fleet Carrier staging
 * without reopening a modal window each time.
 *
 * <p>Wraps the same {@link RouteOptimizerModel}/{@link RouteOptimizerController} pair the legacy
 * dialog uses, adding: a construction site picker, a {@link HaulingMode} picker that drives
 * whether cargo is delivered straight to the site or staged through a personal Fleet Carrier, and
 * (for the carrier modes) a live view of {@link CarrierManager} state that can be corrected
 * manually when journal data is stale or ambiguous.</p>
 *
 * <p>This tab also reacts live to in-game MarketBuy/MarketSell events (and carrier hold changes):
 * see {@link #onCargoStateChanged()} for how a purchase is credited against the current leg only,
 * without reshuffling the rest of the plan.</p>
 */
public class HaulingPanel extends JPanel {

    private static final DecimalFormat TONNAGE_FORMAT = new DecimalFormat("#,##0.0");
    private static final int DEFAULT_MAX_MARKETS = 2;
    private static final int FALLBACK_CARGO_CAPACITY = 1298;
    private static final String CARRIER_CARD_KNOWN = "known";
    private static final String CARRIER_CARD_UNKNOWN = "unknown";
    private static final int CARGO_CHANGE_DEBOUNCE_MS = 500;

    private final ConstructionSiteManager siteManager;
    private final CarrierManager carrierManager;
    private final CargoInventoryManager cargoInventoryManager;
    private final CommodityRegistry commodityRegistry;
    private final MarketDataHttpService marketDataHttpService;
    private final RouteOptimizerModel model;
    private final RouteOptimizerController controller;
    private final HaulingPlanState haulingPlanState;
    private final Timer cargoChangeDebounceTimer;

    private final JComboBox<SiteItem> siteComboBox = new JComboBox<>();
    private final JComboBox<HaulingMode> modeComboBox = new JComboBox<>(HaulingMode.values());
    private final JSpinner capacitySpinner =
        new JSpinner(new SpinnerNumberModel(500.0, 10.0, 2000.0, 10.0));
    private final JSpinner maxMarketsSpinner =
        new JSpinner(new SpinnerNumberModel(DEFAULT_MAX_MARKETS, 1, 5, 1));

    private final JPanel carrierBlock = new JPanel(new CardLayout());
    private final JLabel carrierIdentityLabel = new JLabel("—");
    private final JLabel carrierCargoLabel = new JLabel("—");
    private final JLabel carrierAgeLabel = new JLabel("—");
    private final JTextField systemField = new JTextField(18);
    private final JLabel systemManualLabel = new JLabel("(manual)");
    private final DefaultTableModel holdTableModel;
    private final JTable holdTable;

    private final JLabel coverageValueLabel = new JLabel("Coverage: 0%");
    private final JProgressBar coverageProgress = new JProgressBar(0, 100);
    private final DefaultTableModel runsTableModel;
    private final DefaultTableModel legsTableModel;
    private final JTable runsTable;
    private final JTable legsTable;
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton recalcButton = new JButton("Recalculate");
    private final JButton markSupplyHubButton = new JButton("Mark as supply hub");

    private final List<Long> legRowMarketIds = new ArrayList<>();
    private RoutePlanDto currentPlan = new RoutePlanDto();
    private boolean adjustingControls;
    private boolean suppressSiteComboEvents;
    private boolean refreshingHoldTable;

    /**
     * Market id of the construction site whose data was last handed to
     * {@link RouteOptimizerController#loadConstructionSite(long)}. The combo box populates itself
     * with its item listener suppressed (and {@link javax.swing.JComboBox#setSelectedIndex(int)}
     * fires nothing when the index is already selected), so combo selection alone is not proof
     * that the controller's request carries a site id. This field is what
     * {@link #ensureSelectedSiteLoaded()} compares against.
     */
    private Long loadedSiteMarketId;

    /**
     * Onboard cargo snapshot (commodity id -> tons) as of the last time it was successfully
     * diffed, used by {@link #onCargoStateChanged()} to compute the delta since the previous
     * known-good reading. Never {@code null}; empty when the plan was just adopted or the last
     * known state was empty.
     */
    private Map<Long, Integer> lastOnboardSnapshot = Map.of();

    /**
     * Constructs the panel and wires it to the process-wide managers. Safe to construct once and
     * keep alive for the application's lifetime, unlike the per-click dialog stack it replaces.
     */
    public HaulingPanel() {
        this.siteManager = ConstructionSiteManager.getInstance();
        this.carrierManager = CarrierManager.getInstance();
        this.cargoInventoryManager = CargoInventoryManager.getInstance();
        this.commodityRegistry = CommodityRegistry.getInstance();
        this.marketDataHttpService = new MarketDataHttpService(resolveServerBaseUrl());

        RouteOptimizerDataProvider dataProvider = new DefaultRouteOptimizerDataProvider(resolveServerBaseUrl());
        RouteOptimizationService optimizationService = new GreedyRouteOptimizationService(dataProvider);
        this.model = new RouteOptimizerModel();
        this.controller = new RouteOptimizerController(model, dataProvider, optimizationService);
        controller.setErrorHandler(this::showError);
        this.haulingPlanState = new HaulingPlanState(commodityRegistry);
        // Fires on the EDT (javax.swing.Timer semantics) and is restarted on every cargo/carrier
        // change so a burst of MarketBuy events (filling a hold) collapses into one recompute
        // instead of thrashing the UI on every individual purchase.
        this.cargoChangeDebounceTimer = new Timer(CARGO_CHANGE_DEBOUNCE_MS, e -> onCargoStateChanged());
        this.cargoChangeDebounceTimer.setRepeats(false);

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        runsTableModel = new DefaultTableModel(new Object[]{"Run", "Route", "Total t", "Materials"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        legsTableModel = new DefaultTableModel(new Object[]{"Leg", "Market", "Category", "Material", "t", "Market Age"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        runsTable = new JTable(runsTableModel);
        legsTable = new JTable(legsTableModel);
        runsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        legsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        runsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateLegDetailForSelection();
            }
        });
        legsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                markSupplyHubButton.setEnabled(getSelectedLegMarketId() != null);
            }
        });

        holdTableModel = new DefaultTableModel(new Object[]{"Commodity", "Tons"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 1 ? Integer.class : Object.class;
            }
        };
        holdTable = new JTable(holdTableModel);
        holdTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildTablesPanel(), BorderLayout.CENTER);
        add(buildFooterPanel(), BorderLayout.SOUTH);

        attachControlListeners();
        registerModelListener();
        registerManagerListeners();

        refreshSiteComboBox();
        updateCarrierBlockVisibility();
        refreshCarrierBlock();
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new GridBagLayout());
        header.setBorder(BorderFactory.createTitledBorder("Hauling Plan"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        header.add(new JLabel("Construction site:"), gbc);
        gbc.gridx = 1;
        siteComboBox.setPreferredSize(new Dimension(260, siteComboBox.getPreferredSize().height));
        header.add(siteComboBox, gbc);

        gbc.gridx = 2;
        header.add(new JLabel("Mode:"), gbc);
        gbc.gridx = 3;
        modeComboBox.setRenderer(new HaulingModeRenderer());
        header.add(modeComboBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        header.add(new JLabel("Cargo capacity (t):"), gbc);
        gbc.gridx = 1;
        capacitySpinner.setPreferredSize(new Dimension(90, capacitySpinner.getPreferredSize().height));
        header.add(capacitySpinner, gbc);

        gbc.gridx = 2;
        header.add(new JLabel("Max markets per run:"), gbc);
        gbc.gridx = 3;
        header.add(maxMarketsSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        header.add(buildCarrierBlock(), gbc);

        gbc.gridy = 3;
        coverageProgress.setStringPainted(true);
        header.add(coverageProgress, gbc);

        gbc.gridy = 4;
        coverageValueLabel.setFont(coverageValueLabel.getFont().deriveFont(12f));
        header.add(coverageValueLabel, gbc);

        return header;
    }

    private JPanel buildCarrierBlock() {
        carrierBlock.setBorder(BorderFactory.createTitledBorder("Fleet Carrier"));

        JPanel known = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        known.add(new JLabel("Carrier:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        known.add(carrierIdentityLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 1;
        known.add(new JLabel("Cargo / Free / Total:"), gbc);
        gbc.gridx = 1;
        known.add(carrierCargoLabel, gbc);

        gbc.gridx = 2;
        known.add(new JLabel("Hold snapshot:"), gbc);
        gbc.gridx = 3;
        known.add(carrierAgeLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        known.add(new JLabel("System:"), gbc);
        gbc.gridx = 1;
        known.add(systemField, gbc);
        gbc.gridx = 2;
        systemManualLabel.setForeground(java.awt.Color.GRAY);
        systemManualLabel.setFont(systemManualLabel.getFont().deriveFont(java.awt.Font.ITALIC));
        known.add(systemManualLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.BOTH;
        JScrollPane holdScroll = new JScrollPane(holdTable);
        holdScroll.setPreferredSize(new Dimension(400, 120));
        known.add(holdScroll, gbc);

        JPanel unknown = new JPanel(new BorderLayout());
        unknown.add(new JLabel("Carrier is unknown until a CarrierStats event arrives "
            + "(open carrier management in-game to refresh)."), BorderLayout.CENTER);

        carrierBlock.add(known, CARRIER_CARD_KNOWN);
        carrierBlock.add(unknown, CARRIER_CARD_UNKNOWN);
        return carrierBlock;
    }

    private JPanel buildTablesPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 0.6;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(runsTable), gbc);

        gbc.gridy = 1;
        gbc.weighty = 0.4;
        panel.add(new JScrollPane(legsTable), gbc);
        return panel;
    }

    private JPanel buildFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout());
        recalcButton.addActionListener(e -> recalculate());
        markSupplyHubButton.setEnabled(false);
        markSupplyHubButton.addActionListener(e -> markSelectedMarketAsSupplyHub());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(markSupplyHubButton);
        buttons.add(recalcButton);

        statusLabel.setForeground(java.awt.Color.DARK_GRAY);
        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private void attachControlListeners() {
        siteComboBox.addItemListener(e -> {
            if (suppressSiteComboEvents || e.getStateChange() != ItemEvent.SELECTED) {
                return;
            }
            SiteItem item = (SiteItem) e.getItem();
            loadSite(item.marketId);
        });

        modeComboBox.addActionListener(e -> {
            updateCarrierBlockVisibility();
            if (adjustingControls) {
                return;
            }
            applyRequestContext();
            rebuildPlanForSelectedSite();
        });

        ChangeListener spinnerHandler = e -> {
            if (adjustingControls) {
                return;
            }
            applyRequestContext();
            controller.updateOptimizationParameters(currentCapacity(), currentMaxMarkets());
            rebuildPlanForSelectedSite();
        };
        capacitySpinner.addChangeListener(spinnerHandler);
        maxMarketsSpinner.addChangeListener(spinnerHandler);

        systemField.addActionListener(e -> commitSystemFieldEdit());
        systemField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitSystemFieldEdit();
            }
        });

        holdTableModel.addTableModelListener(this::onHoldTableEdited);
    }

    private void registerModelListener() {
        model.addPropertyChangeListener(evt -> {
            switch (evt.getPropertyName()) {
                case RouteOptimizerModel.PROPERTY_ROUTE_PLAN -> updatePlan((RoutePlanDto) evt.getNewValue());
                case RouteOptimizerModel.PROPERTY_ERROR -> updateStatus((Throwable) evt.getNewValue());
                default -> {
                }
            }
        });
    }

    private void registerManagerListeners() {
        // ConstructionSiteManager fires listeners on the calling (non-EDT) thread, so this hop
        // is required. CarrierManager and CargoInventoryManager extend BaseManager, whose
        // notifyListeners() already hops to the EDT internally, so listeners registered on them
        // below must NOT be wrapped in another SwingUtilities.invokeLater.
        siteManager.addListener(() -> SwingUtilities.invokeLater(this::refreshSiteComboBox));
        carrierManager.addListener(this::refreshCarrierBlock);
        carrierManager.addListener(this::scheduleCargoChangeProcessing);
        cargoInventoryManager.addListener(this::scheduleCargoChangeProcessing);
    }

    /**
     * Restarts the debounce timer backing {@link #onCargoStateChanged()}. Runs on the EDT (the
     * calling manager listener is already there courtesy of {@code BaseManager#notifyListeners()}),
     * so a burst of individual MarketBuy events collapses into a single recompute ~{@value
     * #CARGO_CHANGE_DEBOUNCE_MS}ms after the last one instead of firing per event.
     */
    private void scheduleCargoChangeProcessing() {
        cargoChangeDebounceTimer.restart();
    }

    // ----- Site selection -----

    /**
     * Preselects the given construction site (by market id) in this tab, refreshing the site
     * list first if necessary. Intended to be called by {@link ConstructionSitePanel} when the
     * commander clicks "Plan Route..." there, so the Hauling tab takes over instead of a modal
     * dialog.
     *
     * @param marketId market id of the construction site to preselect
     */
    public void selectSite(long marketId) {
        refreshSiteComboBox();
        for (int i = 0; i < siteComboBox.getItemCount(); i++) {
            SiteItem item = siteComboBox.getItemAt(i);
            if (item != null && item.marketId == marketId) {
                siteComboBox.setSelectedIndex(i);
                // setSelectedIndex is a no-op (no ItemEvent, hence no loadSite) when the combo
                // already had this site selected - which is exactly what happens for the very
                // first site in the list. Load explicitly instead of relying on the event.
                ensureSelectedSiteLoaded();
                return;
            }
        }
    }

    private void refreshSiteComboBox() {
        Long previousSelection = getSelectedSiteMarketId();
        suppressSiteComboEvents = true;
        try {
            siteComboBox.removeAllItems();
            List<ConstructionSite> sites = new ArrayList<>(siteManager.getSites().values());
            sites.sort(Comparator.comparing(ConstructionSite::getSiteId, String.CASE_INSENSITIVE_ORDER));
            for (ConstructionSite site : sites) {
                siteComboBox.addItem(new SiteItem(site.getMarketId(), site.getSiteId(), site.getProgressPercent()));
            }
            if (previousSelection != null) {
                for (int i = 0; i < siteComboBox.getItemCount(); i++) {
                    if (siteComboBox.getItemAt(i).marketId == previousSelection) {
                        siteComboBox.setSelectedIndex(i);
                        break;
                    }
                }
            }
        } finally {
            suppressSiteComboEvents = false;
        }
        // Repopulating clears the selection, so the model may have auto-selected the first item
        // (or the restore loop may have landed on a different site) while the item listener was
        // suppressed. Reconcile the controller with whatever is actually selected now - but only
        // once a site has already been loaded, so the initial population (which runs in the
        // constructor, possibly before the backend is up) stays free of HTTP traffic.
        if (loadedSiteMarketId != null) {
            ensureSelectedSiteLoaded();
        }
    }

    /**
     * Loads the currently selected construction site into the controller unless it is already the
     * loaded one. Cheap no-op on repeated calls, so it is safe to invoke from every combo
     * repopulation triggered by {@link ConstructionSiteManager} updates (docking, server sync).
     */
    private void ensureSelectedSiteLoaded() {
        Long selected = getSelectedSiteMarketId();
        if (selected == null || selected.equals(loadedSiteMarketId)) {
            return;
        }
        loadSite(selected);
    }

    /**
     * Rebuilds the plan for the selected site, first fetching the site if the controller has not
     * seen it yet (in which case the fetch triggers the plan build on completion).
     */
    private void rebuildPlanForSelectedSite() {
        Long selected = getSelectedSiteMarketId();
        if (selected == null) {
            return;
        }
        if (model.getRouteOptimizationRequest().getConstructionSiteId() == null) {
            loadedSiteMarketId = null;
            ensureSelectedSiteLoaded();
            return;
        }
        controller.buildRoutePlan();
    }

    private Long getSelectedSiteMarketId() {
        SiteItem item = (SiteItem) siteComboBox.getSelectedItem();
        return item != null ? item.marketId : null;
    }

    private void loadSite(long marketId) {
        // Recorded up front (not on completion) so a failed load does not make every subsequent
        // combo refresh retry it and stack up error dialogs.
        loadedSiteMarketId = marketId;
        adjustingControls = true;
        try {
            double capacity = clampToSpinnerRange((SpinnerNumberModel) capacitySpinner.getModel(),
                getEffectiveCargoCapacity());
            capacitySpinner.setValue(capacity);
        } finally {
            adjustingControls = false;
        }
        applyRequestContext();
        controller.updateOptimizationParameters(currentCapacity(), currentMaxMarkets());
        controller.setOnboardCommodities(cargoInventoryManager.getOnboardCommoditySnapshot());
        controller.loadConstructionSite(marketId);
    }

    private void recalculate() {
        Long siteId = getSelectedSiteMarketId();
        if (siteId == null) {
            JOptionPane.showMessageDialog(this,
                "Select a construction site before planning deliveries.",
                "Hauling",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (model.getRouteOptimizationRequest().getConstructionSiteId() == null) {
            // Site is selected in the UI but was never fetched (or the fetch failed), so the
            // request carries no site id and the optimizer would reject it. Fetch it now;
            // loadConstructionSite() builds the plan itself once the data arrives.
            loadedSiteMarketId = null;
            ensureSelectedSiteLoaded();
            return;
        }
        requestTailRebuild();
    }

    /**
     * Requests a fresh plan for the remaining (not-yet-delivered) demand, pushing the current
     * onboard cargo snapshot first so the optimizer credits it instead of re-planning purchases
     * for cargo already in the hold. Used both by the "Recalculate" button and by
     * {@link #onCargoStateChanged()} once the current leg is fully satisfied — never on every
     * individual purchase, since that would reshuffle the route out from under the commander
     * mid-shopping.
     *
     * <p>The resulting {@link RoutePlanDto} arrives asynchronously via the model's
     * {@value RouteOptimizerModel#PROPERTY_ROUTE_PLAN} property and is adopted by
     * {@link #updatePlan(RoutePlanDto)}, which resets {@link #haulingPlanState} to the start of
     * the new (tail) plan.</p>
     */
    private void requestTailRebuild() {
        if (getSelectedSiteMarketId() == null) {
            return;
        }
        applyRequestContext();
        controller.updateOptimizationParameters(currentCapacity(), currentMaxMarkets());
        controller.setOnboardCommodities(cargoInventoryManager.getOnboardCommoditySnapshot());
        controller.buildRoutePlan();
    }

    /**
     * Pushes the currently selected {@link HaulingMode} and, for the carrier-involved modes, a
     * fresh {@link CarrierSnapshot} into the shared {@link RouteOptimizerModel} request.
     */
    private void applyRequestContext() {
        HaulingMode mode = (HaulingMode) modeComboBox.getSelectedItem();
        HaulingMode effectiveMode = mode != null ? mode : HaulingMode.DIRECT;
        CarrierSnapshot snapshot = effectiveMode == HaulingMode.DIRECT ? null : buildCarrierSnapshot();
        model.updateRouteOptimizationRequest(request -> {
            request.setMode(effectiveMode);
            request.setCarrier(snapshot);
        });
    }

    private CarrierSnapshot buildCarrierSnapshot() {
        return new CarrierSnapshot(carrierManager.getCarrierId(), carrierManager.getCurrentSystem(),
            carrierManager.getFreeSpace(), carrierManager.getHoldSnapshot());
    }

    private void updateCarrierBlockVisibility() {
        HaulingMode mode = (HaulingMode) modeComboBox.getSelectedItem();
        boolean relevant = mode == HaulingMode.LOAD_CARRIER || mode == HaulingMode.UNLOAD_TO_SITE;
        carrierBlock.setVisible(relevant);
    }

    private double currentCapacity() {
        return ((Number) capacitySpinner.getValue()).doubleValue();
    }

    private int currentMaxMarkets() {
        return ((Number) maxMarketsSpinner.getValue()).intValue();
    }

    // ----- Carrier block -----

    private void refreshCarrierBlock() {
        CardLayout layout = (CardLayout) carrierBlock.getLayout();
        boolean known = carrierManager.isKnown();
        layout.show(carrierBlock, known ? CARRIER_CARD_KNOWN : CARRIER_CARD_UNKNOWN);
        if (!known) {
            return;
        }

        StringBuilder identity = new StringBuilder();
        identity.append(carrierManager.getCallsign() != null ? carrierManager.getCallsign() : "Unknown callsign");
        if (carrierManager.getName() != null && !carrierManager.getName().isBlank()) {
            identity.append(" \"").append(carrierManager.getName()).append('"');
        }
        carrierIdentityLabel.setText(identity.toString());
        carrierCargoLabel.setText(String.format(Locale.ROOT, "%,d / %,d / %,d t",
            carrierManager.getTotalCapacity() - carrierManager.getFreeSpace(),
            carrierManager.getFreeSpace(),
            carrierManager.getTotalCapacity()));
        carrierAgeLabel.setText(formatAge(carrierManager.getHoldSnapshotAt()));

        if (!systemField.hasFocus()) {
            String system = carrierManager.getCurrentSystem();
            systemField.setText(system != null ? system : "");
        }
        systemManualLabel.setVisible(carrierManager.isSystemManuallySet());

        refreshingHoldTable = true;
        try {
            holdTableModel.setRowCount(0);
            for (Map.Entry<Long, Integer> entry : carrierManager.getHoldSnapshot().entrySet()) {
                Commodity commodity = commodityRegistry.getCommodityById(entry.getKey());
                String name = commodity != null && commodity.getNameLocalised() != null
                    ? commodity.getNameLocalised()
                    : "#" + entry.getKey();
                holdTableModel.addRow(new Object[]{new CommodityItem(entry.getKey(), name), entry.getValue()});
            }
        } finally {
            refreshingHoldTable = false;
        }
    }

    private void commitSystemFieldEdit() {
        String text = systemField.getText() == null ? "" : systemField.getText().trim();
        if (text.isEmpty() || text.equals(carrierManager.getCurrentSystem())) {
            return;
        }
        carrierManager.setSystemManually(text);
    }

    private void onHoldTableEdited(TableModelEvent e) {
        if (refreshingHoldTable || e.getType() != TableModelEvent.UPDATE || e.getColumn() != 1) {
            return;
        }
        int row = e.getFirstRow();
        if (row < 0 || row >= holdTableModel.getRowCount()) {
            return;
        }
        Object idCell = holdTableModel.getValueAt(row, 0);
        Object valueCell = holdTableModel.getValueAt(row, 1);
        if (!(idCell instanceof CommodityItem item)) {
            return;
        }
        int tons;
        try {
            tons = valueCell instanceof Number number
                ? number.intValue()
                : Integer.parseInt(String.valueOf(valueCell).trim());
        } catch (NumberFormatException ex) {
            return;
        }
        carrierManager.setHoldQuantityManually(item.id, tons);
    }

    // ----- Plan rendering -----

    private void updatePlan(RoutePlanDto plan) {
        if (plan == null) {
            return;
        }
        currentPlan = plan;
        haulingPlanState.adoptPlan(plan);
        // Re-baseline the delta tracker to the cargo state as of plan adoption: the optimizer
        // already credited whatever is currently onboard against outstanding demand (via
        // setOnboardCommodities), so onCargoStateChanged() must only react to changes from here.
        lastOnboardSnapshot = isCargoStateKnown()
            ? cargoInventoryManager.getOnboardCommoditySnapshot()
            : Map.of();
        runsTableModel.setRowCount(0);
        int runNumber = 1;
        for (DeliveryRunDto run : plan.getRuns()) {
            String route = buildRouteDescription(run);
            String materials = buildMaterialsSummary(run.getMaterialsSummaryTons());
            runsTableModel.addRow(new Object[]{
                runNumber++,
                route,
                TONNAGE_FORMAT.format(run.getTotalTonnage()),
                materials
            });
        }
        if (runsTableModel.getRowCount() > 0) {
            runsTable.setRowSelectionInterval(0, 0);
        } else {
            legsTableModel.setRowCount(0);
            legRowMarketIds.clear();
        }
        int coveragePercent = (int) Math.round(plan.getCoverageFraction() * 100);
        coverageProgress.setValue(coveragePercent);
        coverageProgress.setString(coveragePercent + "%");
        coverageValueLabel.setText(String.format(Locale.ROOT, "Coverage: %d%% of outstanding demand", coveragePercent));
        statusLabel.setText("Plan updated. Runs: " + plan.getRuns().size());
    }

    private void updateLegDetailForSelection() {
        int selectedRow = runsTable.getSelectedRow();
        legsTableModel.setRowCount(0);
        legRowMarketIds.clear();
        if (selectedRow < 0 || currentPlan == null || currentPlan.getRuns() == null) {
            return;
        }
        int modelIndex = runsTable.convertRowIndexToModel(selectedRow);
        if (modelIndex < 0 || modelIndex >= currentPlan.getRuns().size()) {
            return;
        }
        DeliveryRunDto run = currentPlan.getRuns().get(modelIndex);
        int legIndex = 1;
        int legPosition = 0;
        for (RunLegDto leg : run.getLegs()) {
            boolean isActiveLeg = modelIndex == haulingPlanState.getCurrentRunIndex()
                && legPosition == haulingPlanState.getCurrentLegIndex();
            String age = formatAge(lookupMarketLastUpdated(leg.getMarketId()));
            List<PurchaseDto> purchasesForDisplay = new ArrayList<>(leg.getPurchases());
            purchasesForDisplay.sort(
                Comparator.comparing((PurchaseDto p) -> p.getCategory() == null ? "" : p.getCategory(),
                        String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(PurchaseDto::getMaterialName, String.CASE_INSENSITIVE_ORDER));
            for (PurchaseDto purchase : purchasesForDisplay) {
                legsTableModel.addRow(new Object[]{
                    legIndex,
                    leg.getMarketName(),
                    purchase.getCategory() == null ? "" : purchase.getCategory(),
                    purchase.getMaterialName(),
                    formatPurchaseTons(purchase, isActiveLeg),
                    age
                });
                legRowMarketIds.add(leg.getMarketId());
            }
            legIndex++;
            legPosition++;
        }
    }

    /**
     * Formats the tonnage cell for one leg-detail row. For the leg currently being worked
     * ({@code isActiveLeg}), shows "remaining / planned" so the commander can see live progress
     * as purchases land; for any other leg, shows the plain planned tonnage.
     *
     * @param purchase    the planned purchase line
     * @param isActiveLeg whether this row belongs to the leg {@link #haulingPlanState} considers current
     * @return formatted tonnage string for display
     */
    private String formatPurchaseTons(PurchaseDto purchase, boolean isActiveLeg) {
        if (!isActiveLeg) {
            return TONNAGE_FORMAT.format(purchase.getAmountTons());
        }
        long commodityId = purchase.getMaterialName() != null
            ? commodityRegistry.findCommodityId(purchase.getMaterialName(), purchase.getMaterialName())
            : -1;
        if (commodityId < 0) {
            return TONNAGE_FORMAT.format(purchase.getAmountTons());
        }
        int remaining = haulingPlanState.getRemainingForCurrentLeg(commodityId);
        return TONNAGE_FORMAT.format(remaining) + " / " + TONNAGE_FORMAT.format(purchase.getAmountTons());
    }

    private Instant lookupMarketLastUpdated(Long marketId) {
        if (marketId == null) {
            return null;
        }
        for (MarketDto market : model.getCandidateMarkets()) {
            if (market != null && Objects.equals(market.getMarketId(), marketId)) {
                return market.getLastUpdated();
            }
        }
        return null;
    }

    private String buildRouteDescription(DeliveryRunDto run) {
        List<String> names = new ArrayList<>();
        if (run.getLegs() != null) {
            for (RunLegDto leg : run.getLegs()) {
                names.add(leg.getMarketName());
            }
        }
        SiteItem selected = (SiteItem) siteComboBox.getSelectedItem();
        names.add(selected != null ? selected.siteId : "Construction Site");
        return String.join(" → ", names);
    }

    private String buildMaterialsSummary(Map<String, Double> materials) {
        if (materials == null || materials.isEmpty()) {
            return "—";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Double> entry : materials.entrySet()) {
            parts.add(entry.getKey() + " " + TONNAGE_FORMAT.format(entry.getValue()));
        }
        return String.join(", ", parts);
    }

    private void updateStatus(Throwable error) {
        statusLabel.setText(error == null ? " " : "Error: " + error.getMessage());
    }

    private void showError(Throwable throwable) {
        JOptionPane.showMessageDialog(this, throwable.getMessage(), "Hauling", JOptionPane.ERROR_MESSAGE);
    }

    // ----- Supply hub -----

    private Long getSelectedLegMarketId() {
        int viewRow = legsTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = legsTable.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= legRowMarketIds.size()) {
            return null;
        }
        return legRowMarketIds.get(modelRow);
    }

    private void markSelectedMarketAsSupplyHub() {
        Long marketId = getSelectedLegMarketId();
        if (marketId == null) {
            return;
        }
        markSupplyHubButton.setEnabled(false);
        statusLabel.setText("Marking market as supply hub…");
        controller.runAsync(() -> {
            try {
                marketDataHttpService.putSupplyHub(marketId, true);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Marked market " + marketId + " as a supply hub."));
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("Failed to mark supply hub: " + ex.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> markSupplyHubButton.setEnabled(getSelectedLegMarketId() != null));
            }
        });
    }

    // ----- Reactive recompute -----

    /**
     * Reacts to a live MarketBuy/MarketSell journal event (or a carrier hold change) that may
     * have made progress against the currently displayed plan's current leg. Invoked on the EDT
     * by {@link #cargoChangeDebounceTimer} after it settles, ~{@value #CARGO_CHANGE_DEBOUNCE_MS}ms
     * after the last cargo/carrier change, so a burst of purchases while filling a hold collapses
     * into a single recompute.
     *
     * <p><strong>Deliberately narrow in scope:</strong> this only credits the delta against the
     * current leg's planned lines and refreshes that leg's rendering — it never calls
     * {@link RouteOptimizerController#buildRoutePlan()} directly. A full re-optimization is only
     * requested once {@link HaulingPlanState#isCurrentLegComplete()} turns true (via
     * {@link #requestTailRebuild()}), so market ordering never reshuffles mid-shopping while the
     * commander is still working through the current leg's list.</p>
     *
     * <p><strong>Unknown cargo state guard:</strong> {@code CargoInventoryManager} reports an
     * unknown cargo state (e.g. immediately after a {@code Loadout} event, which resets it) as an
     * empty {@link com.zergatstage.monitor.service.managers.CargoInventoryManager#getOnboardCommoditySnapshot()}
     * map — indistinguishable, by that map alone, from a genuinely empty hold. Diffing against it
     * would look like the commander instantly sold their entire cargo and would wrongly credit
     * the whole leg as complete. This method checks
     * {@code cargoInventoryManager.getShipVariant().isCargoStateKnown()} explicitly first and
     * skips the delta entirely (without disturbing {@link #lastOnboardSnapshot}) while the state
     * is unknown, resuming normal diffing once a {@code Cargo} event re-establishes it.</p>
     */
    public void onCargoStateChanged() {
        if (!isCargoStateKnown()) {
            return;
        }
        Map<Long, Integer> snapshot = cargoInventoryManager.getOnboardCommoditySnapshot();
        Map<Long, Integer> previous = lastOnboardSnapshot;
        lastOnboardSnapshot = snapshot;
        haulingPlanState.applyCargoDelta(previous, snapshot);
        updateLegDetailForSelection();
        if (haulingPlanState.isCurrentLegComplete() && !haulingPlanState.isPlanExhausted()) {
            haulingPlanState.advanceLeg();
            statusLabel.setText("Current leg complete — recalculating the remaining route…");
            requestTailRebuild();
        }
    }

    /**
     * @return {@code true} when {@link CargoInventoryManager} has a synchronized view of the
     *         ship's cargo hold (i.e. at least one {@code Cargo} event has been seen since the
     *         last {@code Loadout}), {@code false} while it is unknown
     */
    private boolean isCargoStateKnown() {
        return cargoInventoryManager.getShipVariant() != null
            && cargoInventoryManager.getShipVariant().isCargoStateKnown();
    }

    // ----- Helpers -----

    private int getEffectiveCargoCapacity() {
        int actualCapacity = cargoInventoryManager.getCargoCapacity();
        int capacity = actualCapacity > 0 ? actualCapacity : FALLBACK_CARGO_CAPACITY;
        int onboardTons = cargoInventoryManager.getOnboardCommoditySnapshot().values().stream()
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();
        return Math.max(capacity, onboardTons);
    }

    private static double clampToSpinnerRange(SpinnerNumberModel model, double value) {
        double min = ((Number) model.getMinimum()).doubleValue();
        double max = ((Number) model.getMaximum()).doubleValue();
        return Math.max(min, Math.min(max, value));
    }

    private static String formatAge(Instant instant) {
        if (instant == null) {
            return "—";
        }
        Duration duration = Duration.between(instant, Instant.now());
        if (duration.isNegative()) {
            duration = Duration.ZERO;
        }
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        }
        long days = hours / 24;
        return days + " day" + (days == 1 ? "" : "s") + " ago";
    }

    private String resolveServerBaseUrl() {
        return DefaultManagerFactory.getInstance().getCurrentBaseUrl();
    }

    /** Combo box item pairing a construction site's market id with a human-readable label. */
    private static final class SiteItem {
        private final long marketId;
        private final String siteId;
        private final int progressPercent;

        private SiteItem(long marketId, String siteId, int progressPercent) {
            this.marketId = marketId;
            this.siteId = siteId;
            this.progressPercent = progressPercent;
        }

        @Override
        public String toString() {
            return siteId + " (" + progressPercent + "%)";
        }
    }

    /** Table cell value pairing a commodity id with its display name for the hold table. */
    private static final class CommodityItem {
        private final long id;
        private final String name;

        private CommodityItem(long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Renders {@link HaulingMode} constants with readable labels without touching the enum. */
    private static final class HaulingModeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof HaulingMode mode) {
                setText(switch (mode) {
                    case DIRECT -> "Direct to site";
                    case LOAD_CARRIER -> "Load carrier";
                    case UNLOAD_TO_SITE -> "Unload carrier to site";
                });
            }
            return this;
        }
    }
}
