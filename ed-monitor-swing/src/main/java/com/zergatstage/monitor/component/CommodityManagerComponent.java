package com.zergatstage.monitor.component;

import com.zergatstage.dto.CommodityDTO;
import com.zergatstage.monitor.service.CommodityUIService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class CommodityManagerComponent extends JPanel {

    private final CommodityUIService commodityUIService;
    private final DefaultTableModel tableModel;
    private final JTable table;


    public CommodityManagerComponent() {
        this.commodityUIService = CommodityUIService.getInstance();
        setLayout(new BorderLayout());

        // Table setup
        tableModel = new DefaultTableModel(new Object[]{"ID", "Category", "Name"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Prevent inline editing
            }
            // Important: Override getColumnClass for proper sorting
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) {
                    return Integer.class; // ID column
                } else {
                    return String.class; // Category and Name columns
                }
            }

        };
        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
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
        addButton.addActionListener(event -> onAddCommodity());
        editButton.addActionListener(event -> onEditCommodity());
        deleteButton.addActionListener(event -> onDeleteCommodity());

        loadCommodities();
        // Get the TableRowSorter
        TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) table.getRowSorter();

        // Define the sort keys
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();

        // Sort by Category (column index 1) first, ascending
        sortKeys.add(new RowSorter.SortKey(1, SortOrder.ASCENDING));

        // Then by Name (column index 2) within each Category, ascending
        sortKeys.add(new RowSorter.SortKey(2, SortOrder.ASCENDING));

        // Apply the sort keys to the sorter
        sorter.setSortKeys(sortKeys);

        // To immediately apply the sort
        sorter.sort();
    }

    /**
     * Loads commodities from the service and refreshes the table.
     */
    private void loadCommodities() {
        tableModel.setRowCount(0); // Clear existing data
        List<CommodityDTO> commodities = commodityUIService.getAll();
        for (CommodityDTO c : commodities) {
            tableModel.addRow(new Object[]{c.getId(), c.getCategoryLocalised(), c.getNameLocalised()});
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

        long id = (Long) tableModel.getValueAt(row, 0);
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
