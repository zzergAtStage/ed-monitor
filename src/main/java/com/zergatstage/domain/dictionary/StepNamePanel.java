package com.zergatstage.domain.dictionary;

import com.zergatstage.shared.components.StepPanel;

import javax.swing.*;
import java.awt.*;

public class StepNamePanel extends JPanel implements StepPanel {
    private final JTextField nameField = new JTextField(20);

    public StepNamePanel() {
        setLayout(new BorderLayout());
        add(new JLabel("Enter Name:"), BorderLayout.NORTH);
        add(nameField, BorderLayout.CENTER);
    }

    @Override
    public boolean isValidStep() {
        if (nameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name cannot be empty.");
            return false;
        }
        return true;
    }

    @Override
    public Object getStepData() {
        return nameField.getText().trim();
    }

    @Override
    public String getStepTitle() {
        return "Name";
    }
}
