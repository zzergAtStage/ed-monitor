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
 */
public class ConstructionSitePanel extends JPanel {

    private final JTable siteTable;
    private final DefaultTableModel tableModel;
    private final ConstructionSiteManager siteManager;

    /**
     * Constructs the ConstructionSitePanel and initializes the UI components.
     */
    public ConstructionSitePanel() {
        siteManager = new ConstructionSiteManager();

        // Set the layout for the panel
        setLayout(new BorderLayout());

        // Define table columns for tracking construction site data
        String[] columns = {"Site", "Material", "Required", "Delivered", "Remaining"};
        tableModel = new DefaultTableModel(columns, 0);
        siteTable = new JTable(tableModel);

        // Add the table within a scroll pane to the center of the panel
        JScrollPane scrollPane = new JScrollPane(siteTable);
        add(scrollPane, BorderLayout.CENTER);

        // Create a control panel with an "Add Site" button at the bottom
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add Site");
        addButton.addActionListener(this::handleAddSite);
        controlPanel.add(addButton);

        // Additional controls (e.g., edit/remove) can be added here

        add(controlPanel, BorderLayout.SOUTH);
    }

    /**
     * Handles the action event of adding a new construction site.
     *
     * @param event the ActionEvent triggered by the "Add Site" button.
     */
    private void handleAddSite(ActionEvent event) {
        // For demonstration, we create a sample construction site with one material requirement.
        // In a complete implementation, you might show a dialog to collect user input.
        String siteId = "Site-" + (siteManager.getSites().size() + 1);
        List<MaterialRequirement> requirements = new ArrayList<>();
        // Sample requirement: 100 units of "Steel" needed.
        requirements.add(new MaterialRequirement("Steel", 100));
        ConstructionSite site = new ConstructionSite(siteId, requirements);
        siteManager.addSite(site);
        refreshTable();
    }

    /**
     * Refreshes the table content with current construction site data.
     */
    public void refreshTable() {
        // Clear existing rows from the table
        tableModel.setRowCount(0);
        // Populate the table with updated construction site data
        for (ConstructionSite site : siteManager.getSites()) {
            for (MaterialRequirement req : site.getRequirements()) {
                Object[] row = {
                        site.getSiteId(),
                        req.getMaterialName(),
                        req.getRequiredQuantity(),
                        req.getDeliveredQuantity(),
                        req.getRemainingQuantity()
                };
                tableModel.addRow(row);
            }
        }
    }
}
