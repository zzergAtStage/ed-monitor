package com.zergatstage.monitor.component;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public class StepWizardDialog extends JDialog {
    private final List<StepPanel> steps;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel stepContainer = new JPanel(cardLayout);
    private final JLabel stepLabel = new JLabel();
    private int currentStepIndex = 0;
    private final List<JLabel> stepIndicators = new ArrayList<>();

    public StepWizardDialog(Frame parent, List<StepPanel> steps) {
        super(parent, "Step-by-Step Wizard", true);
        this.steps = steps;
        initComponents();
    }

    private void initComponents() {
        stepContainer.setPreferredSize(new Dimension(400, 200));
        for (int i = 0; i < steps.size(); i++) {
            stepContainer.add((Component) steps.get(i), "step" + i);
        }

        JButton backButton = new JButton("Back");
        JButton nextButton = new JButton("Next");

        backButton.addActionListener(e -> navigateStep(-1));
        nextButton.addActionListener(e -> {
            StepPanel currentStep = steps.get(currentStepIndex);
            if (!currentStep.isValidStep()) return;

            if (currentStepIndex == steps.size() - 1) {
                // Finish
                dispose();
            } else {
                navigateStep(1);
            }
        });

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        controls.add(backButton);
        controls.add(nextButton);

        updateStepLabel();

        this.setLayout(new BorderLayout());
        this.add(stepLabel, BorderLayout.NORTH);
        this.add(stepContainer, BorderLayout.CENTER);
        this.add(controls, BorderLayout.SOUTH);
        this.pack();
        this.setLocationRelativeTo(null);
        this.setResizable(false);
    }

    private void navigateStep(int delta) {
        currentStepIndex += delta;
        cardLayout.show(stepContainer, "step" + currentStepIndex);
        updateStepLabel();
    }

    private void updateStepLabel() {
        StringBuilder labelBuilder = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            if (i == currentStepIndex) {
                labelBuilder.append("▶ ");
            }
            labelBuilder.append(steps.get(i).getStepTitle());
            if (i < steps.size() - 1) labelBuilder.append(" → ");
        }
        stepLabel.setText(labelBuilder.toString());
    }

    /**
     * Collects results from all steps (only call after dialog is closed).
     */
    public List<Object> getResults() {
        List<Object> results = new ArrayList<>();
        for (StepPanel step : steps) {
            results.add(step.getStepData());
        }
        return results;
    }
}
