package com.zergatstage.monitor.component;

import javax.swing.*;
import java.awt.*;

public class StepCategoryPanel extends JPanel implements StepPanel {
    private final JTextField categoryField = new JTextField(20);

    public StepCategoryPanel() {
        setLayout(new BorderLayout());
        add(new JLabel("Enter Category:"), BorderLayout.NORTH);
        add(categoryField, BorderLayout.CENTER);
    }

    @Override
    public boolean isValidStep() {
        if (categoryField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Category cannot be empty.");
            return false;
        }
        return true;
    }

    @Override
    public Object getStepData() {
        return categoryField.getText().trim();
    }

    @Override
    public String getStepTitle() {
        return "Category";
    }
}

