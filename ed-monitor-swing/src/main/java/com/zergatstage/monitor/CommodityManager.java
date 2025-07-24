package com.zergatstage.monitor;

import com.zergatstage.dto.CommodityDTO;
import com.zergatstage.services.CommodityUIService;
import com.zergatstage.shared.components.StepCategoryPanel;
import com.zergatstage.shared.components.StepPanel;
import com.zergatstage.shared.components.StepWizardDialog;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.UUID;


public class CommodityManager extends JPanel {

    private final CommodityUIService commodityUIService;
    private final DefaultTableModel tableModel;
    private final JTable table;

    public CommodityManager() {
        this.commodityUIService = CommodityUIService.getInstance();
        setLayout(new BorderLayout());

        // Table setup
        tableModel = new DefaultTableModel(new Object[]{"ID", "Category", "Name"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Prevent inline editing
            }
        };
        table = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add");
        JButton editButton = new JButton("Edit");
        JButton deleteButton = new JButton("Delete");

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Button actions
        addButton.addActionListener(e -> onAddCommodity());
        editButton.addActionListener(e -> onEditCommodity());
        deleteButton.addActionListener(e -> onDeleteCommodity());

        loadCommodities();
    }

    /**
     * Loads commodities from the service and refreshes the table.
     */
    private void loadCommodities() {
        tableModel.setRowCount(0); // Clear existing data
        List<CommodityDTO> commodities = commodityUIService.getAll();
        for (CommodityDTO c : commodities) {
            tableModel.addRow(new Object[]{c.getId(), c.getCategory(), c.getName()});
        }
    }

    /**
     * Handles editing the selected commodity.
     */
    private void onEditCommodity() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select a commodity to edit.");
            return;
        }

        String id = (String) tableModel.getValueAt(row, 0);
        String oldCategory = (String) tableModel.getValueAt(row, 1);
        String oldName = (String) tableModel.getValueAt(row, 2);

        String category = JOptionPane.showInputDialog(this, "Edit category:", oldCategory);
        if (category == null || category.trim().isEmpty()) return;

        String name = JOptionPane.showInputDialog(this, "Edit name:", oldName);
        if (name == null || name.trim().isEmpty()) return;

        CommodityDTO updated = CommodityDTO.builder()
                .id(id)
                .name(name)
                .category(category)
        //, name,"", category, "");
                .build();
        commodityUIService.updateCommodity(updated);
        loadCommodities();
    }
    /**
     * Handles adding a new commodity via input dialogs.
     */
    private void onAddCommodity() {
        List<StepPanel> steps = List.of(
                new StepCategoryPanel(),
                new StepNamePanel()
        );

        StepWizardDialog dialog = new StepWizardDialog(null, steps);
        dialog.setVisible(true);

        List<Object> results = dialog.getResults();
        if (results.size() == 2) {
            String category = (String) results.get(0);
            String name = (String) results.get(1);

            CommodityDTO newCommodity = CommodityDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .name(name)
                    .category(category)
                    .build();
                    //(UUID.randomUUID().toString(), name, "", category, "");
            commodityUIService.getOrAddCommodity(newCommodity.getId(), newCommodity.getName(), newCommodity.getCategory());
            loadCommodities();
        }
    }


    /**
     * Handles deleting the selected commodity.
     */
    private void onDeleteCommodity() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select a commodity to delete.");
            return;
        }

        String id = (String) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete this commodity?",
                "Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            commodityUIService.deleteCommodity(id);
            loadCommodities();
        }
    }
}
