package com.zergatstage.monitor.component;

import com.zergatstage.domain.ConstructionSite;
import com.zergatstage.domain.MaterialRequirement;
import com.zergatstage.domain.dictionary.Commodity;
import com.zergatstage.dto.ConstructionSiteDTO;
import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.service.CommodityRegistry;
import com.zergatstage.monitor.service.managers.CargoInventoryManager;
import com.zergatstage.monitor.service.ConstructionSiteManager;
import com.zergatstage.monitor.service.ConstructionSiteUpdateListener;
import com.zergatstage.monitor.service.managers.MarketDataUpdateService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;

/**
 * The ConstructionSitePanel class provides a UI panel for managing
 * construction sites and tracking their material requirements.
 * It has two main tables:
 * 1) A "Site Progress Table" at the top: columns for "Site" and "Progress" (with a progress bar).
 * 2) A "Commodities Table" at the bottom: columns for "Site", "Material", "Required", "Delivered", "Remaining".
 */
public class ConstructionSitePanel extends JPanel implements ConstructionSiteUpdateListener {

    // Top table: site progress
    private final JTable siteProgressTable;
    private final DefaultTableModel siteProgressTableModel;

    private final DefaultTableModel commoditiesTableModel;
    private final CommodityRegistry commodityRegistry;
    private final ConstructionSiteManager siteManager;
    private final CargoInventoryManager cargoInventoryManager;
    private final MarketDataUpdateService marketDataService;

    // zero-based column indices in commoditiesTable:
    private static final int MATERIAL_COL  = 1;
    private static final int REMAINING_COL = 5;  // adjust if your column order differs

    /**
     * Constructs the ConstructionSitePanel and initializes the UI components.
     */
    public ConstructionSitePanel() {

        setLayout(new BorderLayout());
        siteManager = ConstructionSiteManager.getInstance();

        cargoInventoryManager = CargoInventoryManager.getInstance();
        commodityRegistry = CommodityRegistry.getInstance();
        marketDataService = DefaultManagerFactory.getInstance().getMarketDataUpdateService();
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
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                siteProgressScroll,
                commoditiesScroll
        );
        splitPane.setResizeWeight(0.3); // Give 30% space to top, 70% to bottom
        add(splitPane, BorderLayout.CENTER);

        // ============= Control Panel (Add Site, Add Commodity) =============
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

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
        cargoInventoryManager.addListener(this::refreshAll);

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
        ConstructionSiteDTO newSite = new ConstructionSiteDTO(0, siteId, new ArrayList<>());
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
                        req.getCommodity().getNameLocalised(),
                        req.getRequiredQuantity(),
                        req.getDeliveredQuantity(),
                        req.getRemainingQuantity()
                };
                commoditiesTableModel.addRow(row);
            }
        }
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
                        req.getCommodity().getNameLocalised(),
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

    private class HighlightRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column)
        {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // only care about Material or Remaining columns
            if (column == MATERIAL_COL || column == REMAINING_COL) {
                // convert to model row in case of sorting
                int modelRow = table.convertRowIndexToModel(row);


                String material = (String) table.getModel().getValueAt(modelRow, MATERIAL_COL);
                Integer remaining = (Integer) table.getModel().getValueAt(modelRow, REMAINING_COL);

                // lookup stock from your registry for this site & commodity
                int stock = marketDataService.getStockForSite(material);

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


}


