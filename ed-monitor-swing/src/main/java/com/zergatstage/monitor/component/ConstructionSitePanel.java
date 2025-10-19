package com.zergatstage.monitor.component;

import com.zergatstage.domain.ConstructionSite;
import com.zergatstage.domain.MaterialRequirement;
import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.domain.makret.Market;
import com.zergatstage.dto.ConstructionSiteDTO;
import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.service.CommodityRegistry;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import com.zergatstage.monitor.service.ConstructionSiteUpdateListener;
import com.zergatstage.monitor.service.managers.CargoInventoryManager;
import com.zergatstage.monitor.service.managers.MarketDataUpdateService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private final CommodityRegistry commodityRegistry;
    private final ConstructionSiteManager siteManager;
    private final CargoInventoryManager cargoInventoryManager;
    private final MarketDataUpdateService marketDataService;
    private final JComboBox<MarketComboItem> marketComboBox;
    // Summary fields
    private JPanel summaryPanel;
    private JLabel deliveredLabel;
    private JLabel remainingLabel;
    private JLabel estimatedRunsLabel;
    private static final int DEFAULT_CARGO_CAPACITY = 1232;
    /**
     * Constructs the ConstructionSitePanel and initializes the UI components.
     */
    public ConstructionSitePanel() {

        setLayout(new BorderLayout());
        siteManager = ConstructionSiteManager.getInstance();

        cargoInventoryManager = CargoInventoryManager.getInstance();
        commodityRegistry = CommodityRegistry.getInstance();
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
            if (!e.getValueIsAdjusting()) {
                int selectedRow = siteProgressTable.getSelectedRow();
                if (selectedRow != -1) {
                    // Adjust for row sorting
                    int modelRow = siteProgressTable.convertRowIndexToModel(selectedRow);
                    String siteId = (String) siteProgressTableModel.getValueAt(modelRow, 0);
                    refreshCommoditiesTableForSite(siteId);
                }
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

        // ============= Control Panel (Add Site, Add Commodity) =============

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
        marketComboBox.addActionListener(e -> {
            int sel = siteProgressTable.getSelectedRow();
            if (sel >= 0) {
                String siteId = (String)
                        siteProgressTableModel.getValueAt(
                                siteProgressTable.convertRowIndexToModel(sel), 0);
                refreshCommoditiesTableForSite(siteId);
            } else if (siteProgressTable.getRowCount() > 0) {
                String siteId = (String)
                        siteProgressTableModel.getValueAt(
                                siteProgressTable.convertRowIndexToModel(0), 0);
                refreshCommoditiesTableForSite(siteId);
            }
        });

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        controlPanel.add(new JLabel("Market:"));
        controlPanel.add(marketComboBox);
        JButton addSiteButton = new JButton("Add Site");
        addSiteButton.addActionListener(this::handleAddSite);
        controlPanel.add(addSiteButton);

        JButton addCommodityButton = new JButton("Add Commodity");
        addCommodityButton.addActionListener(this::handleAddCommodity);
        controlPanel.add(addCommodityButton);
        JButton clearFilterButton = new JButton("Clear Filter");
        clearFilterButton.addActionListener(_ -> refreshCommoditiesTable());
        controlPanel.add(clearFilterButton);

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


    /**
     * Handles the action event of adding a new construction site.
     * Prompts the user to enter a unique site ID.
     */
    private void handleAddSite(ActionEvent event) {
        String siteId = JOptionPane.showInputDialog(
                this,
                "Enter a unique Construction Site ID:",
                "Add Site",
                JOptionPane.PLAIN_MESSAGE
        );

        if (siteId == null || siteId.trim().isEmpty()) {
            return; // User canceled or empty
        }

        // Check if a site with this ID already exists
        boolean isSitePresent = siteManager.getSites().values().stream()
                .anyMatch(s -> s.getSiteId().equals(siteId));
        if (isSitePresent) {
            JOptionPane.showMessageDialog(
                    this,
                    "A site with this ID already exists!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // Create the new site with 0 marketId. The not zero ID means site is imported.
        ConstructionSiteDTO newSite = new ConstructionSiteDTO(0, siteId, new CopyOnWriteArrayList<>());
        siteManager.addSite(newSite);

        refreshAll();
    }

    private void refreshCommoditiesTableForSite(String siteId) {
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
        updateSummaryPanel();
    }

    /**
     * Handles the action event of adding a new commodity (material) to an existing site.
     */
    private void handleAddCommodity(ActionEvent event) {
        if (siteManager.getSites().isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "No sites available. Please add a site first.",
                    "Warning",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        /*
         * A sample list of available commodities to choose from when adding materials.
         */
        String[] AVAILABLE_COMMODITIES = commodityRegistry.getAllNames();
        // 1) Select the target site
        String[] siteIds = siteManager.getSites().values().stream()
                .map(ConstructionSite::getSiteId)
                .toArray(String[]::new);

        String selectedSiteId = (String) JOptionPane.showInputDialog(
                this,
                "Select a site:",
                "Add Commodity",
                JOptionPane.PLAIN_MESSAGE,
                null,
                siteIds,
                siteIds[0]
        );

        if (selectedSiteId == null) {
            return; // user canceled
        }

        // 2) Select the commodity from the predefined list
        String selectedCommodity = (String) JOptionPane.showInputDialog(
                this,
                "Select a commodity:",
                "Add Commodity",
                JOptionPane.PLAIN_MESSAGE,
                null,
                AVAILABLE_COMMODITIES,
                AVAILABLE_COMMODITIES[0]
        );

        if (selectedCommodity == null) {
            return; // user canceled
        }

        // 3) Enter required quantity
        String quantityStr = JOptionPane.showInputDialog(
                this,
                "Enter required quantity:",
                "Add Commodity",
                JOptionPane.PLAIN_MESSAGE
        );

        if (quantityStr == null) {
            return; // user canceled
        }

        int requiredQuantity;
        try {
            requiredQuantity = Integer.parseInt(quantityStr.trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Invalid quantity!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // Find the selected site with StringID - manually added entities
        ConstructionSite selectedSite = siteManager.getSites().values().stream()
                .filter(s -> s.getSiteId().equals(selectedSiteId))
                .findFirst()
                .orElse(null);

        if (selectedSite == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Site not found!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // Add the new requirement
        selectedSite.getRequirements().add(
                MaterialRequirement.builder()
                        .commodity(Commodity.builder().build()) //TODO: Replace with real choice
                        .requiredQuantity(requiredQuantity)
                        .build()
        );

        refreshAll();
    }

    /**
     * Refreshes both the "Site Progress Table" and the "Commodities Table."
     */
    private void refreshAll() {
        refreshSiteProgressTable();
        refreshCommoditiesTable();
        updateSummaryPanel();
    }

    /**
     * Updates the top table, which shows each site's name and overall progress.
     */
    private void refreshSiteProgressTable() {
        siteProgressTableModel.setRowCount(0);

        for (ConstructionSite site : siteManager.getSites().values()) {
            Object[] row = {
                    site.getSiteId(),
                    site.getProgressPercent()
            };
            siteProgressTableModel.addRow(row);
        }
    }

    /**
     * Updates the bottom table, which lists each site's material requirements in detail.
     */
    private void refreshCommoditiesTable() {
        commoditiesTableModel.setRowCount(0);
        // Placeholder for "In Cargo" column, not used in this context
        for (ConstructionSite site : siteManager.getSites().values()) {
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
                int modelRow = table.convertRowIndexToModel(row);


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

        // Calculate totals based on current filter (selected site or all sites)
        int selectedRow = siteProgressTable.getSelectedRow();
        if (selectedRow != -1) {
            // Show summary for selected site only
            int modelRow = siteProgressTable.convertRowIndexToModel(selectedRow);
            String siteId = (String) siteProgressTableModel.getValueAt(modelRow, 0);

            ConstructionSite site = siteManager.getSites().values().stream()
                    .filter(s -> s.getSiteId().equals(siteId))
                    .findFirst()
                    .orElse(null);

            if (site != null) {
                for (MaterialRequirement req : site.getRequirements()) {
                    totalDelivered += req.getDeliveredQuantity();
                    totalRemaining += req.getRemainingQuantity();
                }
            }
        } else {
            // Show summary for all sites
            for (ConstructionSite site : siteManager.getSites().values()) {
                for (MaterialRequirement req : site.getRequirements()) {
                    totalDelivered += req.getDeliveredQuantity();
                    totalRemaining += req.getRemainingQuantity();
                }
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


