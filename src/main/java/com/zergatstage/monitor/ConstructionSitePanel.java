package com.zergatstage.monitor;

import com.zergatstage.domain.ConstructionSite;
import com.zergatstage.domain.MaterialRequirement;
import com.zergatstage.services.ConstructionSiteManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The ConstructionSitePanel class provides a UI panel for managing
 * construction sites and tracking their material requirements.
 *
 * It has two main tables:
 *  1) A "Site Progress Table" at the top: columns for "Site" and "Progress" (with a progress bar).
 *  2) A "Commodities Table" at the bottom: columns for "Site", "Material", "Required", "Delivered", "Remaining".
 */
public class ConstructionSitePanel extends JPanel {

    // Top table: site progress
    private final JTable siteProgressTable;
    private final DefaultTableModel siteProgressTableModel;

    // Bottom table: commodities
    private final JTable commoditiesTable;
    private final DefaultTableModel commoditiesTableModel;

    private final ConstructionSiteManager siteManager;

    /**
     * A sample list of available commodities to choose from when adding materials.
     */
    private static final String[] AVAILABLE_COMMODITIES = {
            "Biowaste", "Building Fabricators", "Ceramic Composites",
            "Computer Components", "Copper", "Crop Harvesters",
            "Evacuation Shelter", "Food Cartridges", "Fruit and Vegetables",
            "Grain", "Liquid Oxygen", "Pesticides", "Steel",
            "Survival Equipment", "Land Enrichment Systems"
    };

    /**
     * Constructs the ConstructionSitePanel and initializes the UI components.
     */
    public ConstructionSitePanel() {
        siteManager = new ConstructionSiteManager();
        setLayout(new BorderLayout());

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

        // ============= Commodities Table (Bottom) =============
        String[] commodityColumns = {"Site", "Material", "Required", "Delivered", "Remaining"};
        commoditiesTableModel = new DefaultTableModel(commodityColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // read-only as well
            }
        };
        commoditiesTable = new JTable(commoditiesTableModel);
        commoditiesTable.setAutoCreateRowSorter(true);

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

        add(controlPanel, BorderLayout.SOUTH);
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
        if (siteManager.getSites().stream().anyMatch(s -> s.getSiteId().equals(siteId))) {
            JOptionPane.showMessageDialog(
                    this,
                    "A site with this ID already exists!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // Create the new site
        ConstructionSite newSite = new ConstructionSite(siteId, new ArrayList<>());
        siteManager.addSite(newSite);

        refreshAll();
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

        // 1) Select the target site
        String[] siteIds = siteManager.getSites().stream()
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

        // Find the selected site
        ConstructionSite selectedSite = siteManager.getSites().stream()
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
                new MaterialRequirement(selectedCommodity, requiredQuantity)
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

        for (ConstructionSite site : siteManager.getSites()) {
            Object[] row = {
                    site.getSiteId(),
                    site.getProgressPercent() // an integer from 0–100
            };
            siteProgressTableModel.addRow(row);
        }
    }

    /**
     * Updates the bottom table, which lists each site's material requirements in detail.
     */
    private void refreshCommoditiesTable() {
        commoditiesTableModel.setRowCount(0);

        for (ConstructionSite site : siteManager.getSites()) {
            for (MaterialRequirement req : site.getRequirements()) {
                Object[] row = {
                        site.getSiteId(),
                        req.getMaterialName(),
                        req.getRequiredQuantity(),
                        req.getDeliveredQuantity(),
                        req.getRemainingQuantity()
                };
                commoditiesTableModel.addRow(row);
            }
        }
    }
}
