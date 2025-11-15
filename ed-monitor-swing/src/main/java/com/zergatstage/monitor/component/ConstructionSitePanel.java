package com.zergatstage.monitor.component;

import com.zergatstage.domain.ConstructionSite;
import com.zergatstage.domain.MaterialRequirement;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.routes.service.GreedyRouteOptimizationService;
import com.zergatstage.monitor.routes.service.RouteOptimizationService;
import com.zergatstage.monitor.routes.spi.DefaultRouteOptimizerDataProvider;
import com.zergatstage.monitor.routes.spi.RouteOptimizerDataProvider;
import com.zergatstage.monitor.routes.ui.RouteOptimizerController;
import com.zergatstage.monitor.routes.ui.RouteOptimizerDialog;
import com.zergatstage.monitor.routes.ui.RouteOptimizerModel;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import com.zergatstage.monitor.service.ConstructionSiteUpdateListener;
import com.zergatstage.monitor.service.managers.CargoInventoryManager;
import com.zergatstage.monitor.service.managers.MarketDataUpdateService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The ConstructionSitePanel class provides a UI panel for managing
 * construction sites and tracking their material requirements.
 * It has two main tables:
 * 1) A "Site Progress Table" at the top: columns for "Site" and "Progress" (with a progress bar).
 * 2) A "Commodities Table" at the bottom: columns for "Site", "Material", "Required", "Delivered", "Remaining".
 */
public class ConstructionSitePanel extends JPanel implements ConstructionSiteUpdateListener {

    // zero-based column indices in commoditiesTable:
    private static final int MATERIAL_COL = 1;
    private static final int REMAINING_COL = 5;  // adjust if your column order differs
    // Top table: site progress
    private final JTable siteProgressTable;
    private final DefaultTableModel siteProgressTableModel;
    private final DefaultTableModel commoditiesTableModel;
    private final ConstructionSiteManager siteManager;
    private final CargoInventoryManager cargoInventoryManager;
    private final MarketDataUpdateService marketDataService;
    private final JComboBox<MarketComboItem> marketComboBox;
    private final JButton planRouteButton;
    // Summary fields
    private JPanel summaryPanel;
    private JLabel deliveredLabel;
    private JLabel remainingLabel;
    private JLabel estimatedRunsLabel;
    private static final int DEFAULT_CARGO_CAPACITY = 1232;
    private static final String ALL_SITES_LABEL = "All Sites";
    private static final String SELECTED_SITES_LABEL = "Selected Sites";
    private final LinkedHashSet<String> selectedSiteIds = new LinkedHashSet<>();
    private boolean suppressSiteSelectionEvents;
    /**
     * Constructs the ConstructionSitePanel and initializes the UI components.
     */
    public ConstructionSitePanel() {

        setLayout(new BorderLayout());
        siteManager = ConstructionSiteManager.getInstance();

        cargoInventoryManager = CargoInventoryManager.getInstance();
        marketDataService = DefaultManagerFactory.getInstance().getMarketDataUpdateService();
        marketDataService.addListener(this::populateMarketComboBox);
        // ============= Site Progress Table (Top) =============
        String[] siteProgressColumns = {"Site", "Progress"};
        siteProgressTableModel = new DefaultTableModel(siteProgressColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make this table read-only
            }
        };

        siteProgressTable = new JTable(siteProgressTableModel);
        siteProgressTable.setAutoCreateRowSorter(true);

        // Use our custom renderer for the "Progress" column (index 1)
        siteProgressTable.getColumnModel().getColumn(1).setCellRenderer(new ProgressBarCellRenderer());

        JScrollPane siteProgressScroll = new JScrollPane(siteProgressTable);

        siteProgressTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressSiteSelectionEvents) {
                selectedSiteIds.clear();
                int[] selectedRows = siteProgressTable.getSelectedRows();
                for (int viewRow : selectedRows) {
                    int modelRow = safeConvertViewRowToModel(siteProgressTable, viewRow);
                    if (modelRow >= 0) {
                        selectedSiteIds.add((String) siteProgressTableModel.getValueAt(modelRow, 0));
                    }
                }
                refreshCommoditiesTable();
                updatePlanRouteButtonState();
            }
        });


        // ============= Commodities Table (Bottom) =============
        String[] commodityColumns = {"Site", "Material", "Required", "InCargo", "Delivered", "Remaining"};
        commoditiesTableModel = new DefaultTableModel(commodityColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // read-only as well
            }
        };
        // Bottom table: commodities
        JTable commoditiesTable = new JTable(commoditiesTableModel);
        commoditiesTable.setAutoCreateRowSorter(true);
        // apply our highlighter
        commoditiesTable.getColumnModel().getColumn(MATERIAL_COL)
                .setCellRenderer(new HighlightRenderer());
        commoditiesTable.getColumnModel().getColumn(REMAINING_COL)
                .setCellRenderer(new HighlightRenderer());
        JScrollPane commoditiesScroll = new JScrollPane(commoditiesTable);
        // ============= Split Pane for top/bottom layout =============
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(siteProgressScroll, BorderLayout.CENTER);
        topPanel.add(createSummaryPanel(), BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                topPanel,
                commoditiesScroll
        );
        splitPane.setResizeWeight(0.3);
        add(splitPane, BorderLayout.CENTER);

        // ============= Control Panel (Market & Filters) =============

        marketComboBox = new JComboBox<>();
        for (Market m : marketDataService.getAllMarkets()) {
            marketComboBox.addItem(new MarketComboItem(m.getMarketId(), m.getStationName()));
        }
        marketComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected,
                                                          boolean cellHasFocus)
            {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Market) {
                    setText(((Market) value).getStationName());
                }
                return this;
            }
        });
        marketComboBox.addActionListener(e -> refreshCommoditiesTable());

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        controlPanel.add(new JLabel("Market:"));
        controlPanel.add(marketComboBox);
        JButton clearFilterButton = new JButton("Clear Filter");
        clearFilterButton.addActionListener(event -> {
            if (selectedSiteIds.isEmpty() && siteProgressTable.getSelectedRowCount() == 0) {
                refreshCommoditiesTable();
                return;
            }
            suppressSiteSelectionEvents = true;
            try {
                selectedSiteIds.clear();
                siteProgressTable.clearSelection();
            } finally {
                suppressSiteSelectionEvents = false;
            }
            refreshCommoditiesTable();
        });
        controlPanel.add(clearFilterButton);
        planRouteButton = new JButton("Plan Route...");
        planRouteButton.setEnabled(false);
        planRouteButton.addActionListener(e -> openRouteOptimizer());
        controlPanel.add(planRouteButton);

        add(controlPanel, BorderLayout.SOUTH);

        // Register as a listener so that the panel updates when the siteManager data changes.
        siteManager.addListener(() -> SwingUtilities.invokeLater(this::refreshAll));
        cargoInventoryManager.addListener(() -> SwingUtilities.invokeLater(this::refreshAll));
        marketDataService.addListener(() -> SwingUtilities.invokeLater(this::refreshAll));

    }

    private JPanel createSummaryPanel() {
        summaryPanel = new JPanel(new GridBagLayout());
        summaryPanel.setBorder(BorderFactory.createTitledBorder("Delivery Summary"));
        summaryPanel.setBackground(new Color(240, 248, 255)); // Light blue background

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;

        // Delivered commodities section
        gbc.gridx = 0; gbc.gridy = 0;
        summaryPanel.add(new JLabel("Total Delivered:"), gbc);

        gbc.gridx = 1;
        deliveredLabel = new JLabel("0 t");
        deliveredLabel.setFont(deliveredLabel.getFont().deriveFont(Font.BOLD, 14f));
        deliveredLabel.setForeground(new Color(34, 139, 34)); // Forest green
        summaryPanel.add(deliveredLabel, gbc);

        // Remaining commodities section
        gbc.gridx = 2; gbc.gridy = 0;
        summaryPanel.add(new JLabel("Total Remaining:"), gbc);

        gbc.gridx = 3;
        remainingLabel = new JLabel("0 t");
        remainingLabel.setFont(remainingLabel.getFont().deriveFont(Font.BOLD, 14f));
        remainingLabel.setForeground(new Color(220, 20, 60)); // Crimson
        summaryPanel.add(remainingLabel, gbc);

        // Estimated runs section
        gbc.gridx = 4; gbc.gridy = 0;
        summaryPanel.add(new JLabel("Estimated Runs:"), gbc);

        gbc.gridx = 5;
        estimatedRunsLabel = new JLabel("0");
        estimatedRunsLabel.setFont(estimatedRunsLabel.getFont().deriveFont(Font.BOLD, 14f));
        estimatedRunsLabel.setForeground(new Color(30, 144, 255)); // Dodger blue
        summaryPanel.add(estimatedRunsLabel, gbc);

        // Add cargo capacity info
        gbc.gridx = 6; gbc.gridy = 0;
        JLabel capacityLabel = new JLabel("(Capacity: " + DEFAULT_CARGO_CAPACITY + "t)");
        capacityLabel.setFont(capacityLabel.getFont().deriveFont(Font.ITALIC, 11f));
        capacityLabel.setForeground(Color.GRAY);
        summaryPanel.add(capacityLabel, gbc);

        return summaryPanel;
    }


    private void populateCommoditiesTableForSite(String siteId) {
        commoditiesTableModel.setRowCount(0);

        ConstructionSite site = siteManager.getSites().values().stream()
                .filter(s -> s.getSiteId().equals(siteId))
                .findFirst()
                .orElse(null);

        if (site != null) {
            for (MaterialRequirement req : site.getRequirements()) {
                Object[] row = {
                        site.getSiteId(),
                        new RequiredCommodityItem(req.getCommodity().getId(), req.getCommodity().getNameLocalised()),
                        req.getRequiredQuantity(),
                        cargoInventoryManager.getInCargo(req.getCommodity().getId()), // Get in cargo from inventory
                        req.getDeliveredQuantity(),
                        req.getRemainingQuantity()
                };
                commoditiesTableModel.addRow(row);
            }
        }
    }

    private void populateAggregatedCommoditiesTable(Collection<ConstructionSite> sites, String siteLabel) {
        commoditiesTableModel.setRowCount(0);

        Map<Long, CommodityAggregate> aggregates = new LinkedHashMap<>();
        for (ConstructionSite site : sites) {
            for (MaterialRequirement req : site.getRequirements()) {
                long commodityId = req.getCommodity().getId();
                CommodityAggregate aggregate = aggregates.computeIfAbsent(
                        commodityId,
                        id -> new CommodityAggregate(new RequiredCommodityItem(id, req.getCommodity().getNameLocalised()))
                );
                aggregate.required += req.getRequiredQuantity();
                aggregate.delivered += req.getDeliveredQuantity();
                aggregate.remaining += req.getRemainingQuantity();
            }
        }

        for (CommodityAggregate aggregate : aggregates.values()) {
            commoditiesTableModel.addRow(new Object[]{
                    siteLabel,
                    aggregate.item,
                    aggregate.required,
                    cargoInventoryManager.getInCargo(aggregate.item.getId()),
                    aggregate.delivered,
                    aggregate.remaining
            });
        }
    }

    /**
     * Refreshes both the "Site Progress Table" and the "Commodities Table."
     */
    private void refreshAll() {
        refreshSiteProgressTable();
        refreshCommoditiesTable();
    }

    /**
     * Updates the top table, which shows each site's name and overall progress.
     */
    private void refreshSiteProgressTable() {
        suppressSiteSelectionEvents = true;
        try {
            siteProgressTableModel.setRowCount(0);
            LinkedHashSet<String> previousSelection = new LinkedHashSet<>(selectedSiteIds);
            selectedSiteIds.clear();
            List<Integer> modelRowsToSelect = new ArrayList<>();
            int rowIndex = 0;
            for (ConstructionSite site : siteManager.getSites().values()) {
                Object[] row = {
                        site.getSiteId(),
                        site.getProgressPercent()
                };
                siteProgressTableModel.addRow(row);
                if (previousSelection.contains(site.getSiteId())) {
                    selectedSiteIds.add(site.getSiteId());
                    modelRowsToSelect.add(rowIndex);
                }
                rowIndex++;
            }

            siteProgressTable.clearSelection();
            if (!selectedSiteIds.isEmpty()) {
                for (int modelRow : modelRowsToSelect) {
                    int viewRow = safeConvertModelRowToView(siteProgressTable, modelRow);
                    if (viewRow >= 0) {
                        siteProgressTable.addRowSelectionInterval(viewRow, viewRow);
                    }
                }
            }
        } finally {
            suppressSiteSelectionEvents = false;
        }
    }

    /**
     * Updates the bottom table, which lists each site's material requirements in detail.
     */
    private void refreshCommoditiesTable() {
        if (selectedSiteIds.isEmpty()) {
            populateAggregatedCommoditiesTable(siteManager.getSites().values(), ALL_SITES_LABEL);
        } else if (selectedSiteIds.size() == 1) {
            populateCommoditiesTableForSite(selectedSiteIds.iterator().next());
        } else {
            populateAggregatedCommoditiesTable(resolveSites(selectedSiteIds), SELECTED_SITES_LABEL);
        }
        updateSummaryPanel();
    }

    /**
     * Invoked when the construction site data has been updated.
     */
    @Override
    public void onConstructionSiteUpdated() {
        refreshAll();
    }

    private void populateMarketComboBox() {
        marketComboBox.removeAllItems();
        Arrays.stream(marketDataService.getAllMarkets()).toList()
                .forEach(market -> {
                    marketComboBox.addItem(new MarketComboItem(market.getMarketId(), market.getStationName()));
                });
    }

    private class HighlightRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // only care about Material or Remaining columns
            if (column == MATERIAL_COL || column == REMAINING_COL) {
                // convert to model row in case of sorting
                int modelRow = safeConvertViewRowToModel(table, row);
                if (modelRow < 0) {
                    setBackground(Color.WHITE);
                    return this;
                }

                RequiredCommodityItem material = (RequiredCommodityItem) table.getModel().getValueAt(modelRow, MATERIAL_COL);
                Integer remaining = (Integer) table.getModel().getValueAt(modelRow, REMAINING_COL);
                MarketComboItem selected = (MarketComboItem) marketComboBox.getSelectedItem();
                long marketId = (selected == null) ? 0 : selected.getId();
                int stock=0;
                // lookup stock from your registry for this site & commodity
                if (marketId == 0) {
                    stock = marketDataService.getStockForSite(material.getId());
                } else {
                    stock = marketDataService.getStockForSite(material.getId(), marketId);
                }

                if (remaining != null && remaining != 0 && stock > 0) {
                    setBackground(new Color(144, 238, 144)); // light green
                } else {
                    setBackground(Color.WHITE);
                }
            } else {
                setBackground(Color.WHITE);
            }

            return this;
        }
    }
    private void updateSummaryPanel() {
        int totalDelivered = 0;
        int totalRemaining = 0;

        Collection<ConstructionSite> targetSites = selectedSiteIds.isEmpty()
                ? siteManager.getSites().values()
                : resolveSites(selectedSiteIds);

        for (ConstructionSite site : targetSites) {
            for (MaterialRequirement req : site.getRequirements()) {
                totalDelivered += req.getDeliveredQuantity();
                totalRemaining += req.getRemainingQuantity();
            }
        }

        // Calculate estimated runs needed
        int estimatedRuns = (totalRemaining > 0) ?
                (int) Math.ceil((double) totalRemaining / DEFAULT_CARGO_CAPACITY) : 0;

        // Update labels
        deliveredLabel.setText(String.format("%,d t", totalDelivered));
        remainingLabel.setText(String.format("%,d t", totalRemaining));
        estimatedRunsLabel.setText(String.valueOf(estimatedRuns));

        // Update colors based on progress
        if (totalRemaining == 0 && totalDelivered > 0) {
            remainingLabel.setForeground(new Color(34, 139, 34)); // Green when complete
        } else {
            remainingLabel.setForeground(new Color(220, 20, 60)); // Red when incomplete
        }
    }

    private void updatePlanRouteButtonState() {
        planRouteButton.setEnabled(selectedSiteIds.size() == 1);
    }

    private void openRouteOptimizer() {
        if (selectedSiteIds.size() != 1) {
            JOptionPane.showMessageDialog(this,
                    "Select exactly one construction site to plan deliveries.",
                    "Route Optimizer",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String siteId = selectedSiteIds.iterator().next();
        ConstructionSite site = resolveSites(List.of(siteId)).stream().findFirst().orElse(null);
        if (site == null) {
            JOptionPane.showMessageDialog(this,
                    "Unable to load construction site details.",
                    "Route Optimizer",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            RouteOptimizerDataProvider dataProvider =
                    new DefaultRouteOptimizerDataProvider(resolveServerBaseUrl());
            RouteOptimizationService optimizationService = new GreedyRouteOptimizationService(dataProvider);
            RouteOptimizerModel model = new RouteOptimizerModel();
            RouteOptimizerController controller =
                    new RouteOptimizerController(model, dataProvider, optimizationService);
            RouteOptimizerDialog dialog =
                    new RouteOptimizerDialog(SwingUtilities.getWindowAncestor(this), model, controller);
            dialog.displaySite(site.getMarketId(), DEFAULT_CARGO_CAPACITY, 2);
            dialog.setVisible(true);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                    "Invalid ED server URL: " + ex.getMessage(),
                    "Route Optimizer",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private String resolveServerBaseUrl() {
        return System.getProperty("ed.server.baseUrl",
                System.getenv().getOrDefault("ED_SERVER_BASE_URL", "http://localhost:8080"));
    }

    private Collection<ConstructionSite> resolveSites(Collection<String> siteIds) {
        List<ConstructionSite> result = new ArrayList<>();
        for (ConstructionSite site : siteManager.getSites().values()) {
            if (siteIds.contains(site.getSiteId())) {
                result.add(site);
            }
        }
        return result;
    }

    private int safeConvertViewRowToModel(JTable table, int viewRow) {
        if (viewRow < 0 || viewRow >= table.getRowCount()) {
            return -1;
        }
        try {
            return table.convertRowIndexToModel(viewRow);
        } catch (IndexOutOfBoundsException ex) {
            return -1;
        }
    }

    private int safeConvertModelRowToView(JTable table, int modelRow) {
        if (modelRow < 0 || modelRow >= table.getModel().getRowCount()) {
            return -1;
        }
        try {
            return table.convertRowIndexToView(modelRow);
        } catch (IndexOutOfBoundsException ex) {
            return -1;
        }
    }

    private static class CommodityAggregate {
        private final RequiredCommodityItem item;
        private int required;
        private int delivered;
        private int remaining;

        private CommodityAggregate(RequiredCommodityItem item) {
            this.item = item;
        }
    }

/** Simple wrapper so combo returns ID but shows name. */
	private static class MarketComboItem {
	    private final long id;
	    private final String name;
	    public MarketComboItem(long id, String name) {
    	    this.id = id; this.name = name;
    	}
	    public long getId() { return id; }
	    @Override public String toString() { return name; }
	}
    private static class RequiredCommodityItem {
        private final long id;
        private final String name;

        public RequiredCommodityItem(long id, String name) {
            this.name = name;
            this.id = id;
        }

        public long getId() {
            return this.id;
        }

        @Override public String toString() { return name; }
    }
}


