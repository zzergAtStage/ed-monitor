package com.zergatstage.monitor.routes.ui;

import com.zergatstage.monitor.http.dto.ConstructionSiteDto;
import com.zergatstage.monitor.http.dto.MarketDto;
import com.zergatstage.monitor.routes.dto.DeliveryRunDto;
import com.zergatstage.monitor.routes.dto.PurchaseDto;
import com.zergatstage.monitor.routes.dto.RoutePlanDto;
import com.zergatstage.monitor.routes.dto.RunLegDto;
import com.zergatstage.monitor.routes.service.RouteOptimizationService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Swing panel that binds {@link RouteOptimizerModel} and {@link RouteOptimizerController}
 * into an interactive UI. Presents request parameters, generated runs, and detailed leg
 * information without embedding optimization logic directly in the view.
 */
public class RouteOptimizerPanel extends JPanel {

    private static final DecimalFormat TONNAGE_FORMAT = new DecimalFormat("#,##0.0");
    private static final int DEFAULT_MAX_MARKETS = 2;

    private final RouteOptimizerModel model;
    private final RouteOptimizerController controller;
    private final JLabel siteNameLabel = new JLabel("Select a site to begin");
    private final JLabel requirementSummaryLabel = new JLabel("Requirements: —");
    private final JLabel marketsSummaryLabel = new JLabel("Candidate markets: 0");
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel coverageValueLabel = new JLabel("Coverage: 0%");
    private final JProgressBar coverageProgress = new JProgressBar(0, 100);
    private final JSpinner capacitySpinner =
        new JSpinner(new SpinnerNumberModel(500.0, 10.0, 2000.0, 10.0));
    private final JSpinner maxMarketsSpinner =
        new JSpinner(new SpinnerNumberModel(DEFAULT_MAX_MARKETS, 1, 5, 1));
    private final DefaultTableModel runsTableModel;
    private final DefaultTableModel legsTableModel;
    private final JTable runsTable;
    private final JTable legsTable;
    private RoutePlanDto currentPlan = new RoutePlanDto();
    private boolean adjustingSpinners;

    public RouteOptimizerPanel(RouteOptimizerModel model,
                               RouteOptimizerController controller) {
        this.model = Objects.requireNonNull(model, "model");
        this.controller = Objects.requireNonNull(controller, "controller");
        setLayout(new BorderLayout(8, 8));
        setPreferredSize(new Dimension(900, 600));

        controller.setErrorHandler(this::showError);

        runsTableModel = new DefaultTableModel(new Object[]{"Run", "Route", "Total t", "Materials"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        legsTableModel = new DefaultTableModel(new Object[]{"Leg", "Market", "Material", "t"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        runsTable = new JTable(runsTableModel);
        legsTable = new JTable(legsTableModel);
        runsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        legsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        runsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateLegDetailForSelection();
            }
        });

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildTablesPanel(), BorderLayout.CENTER);
        add(buildFooterPanel(), BorderLayout.SOUTH);
        registerModelListener();
        attachSpinnerListeners();
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new GridBagLayout());
        header.setBorder(BorderFactory.createTitledBorder("Route Parameters"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        header.add(siteNameLabel, gbc);

        gbc.gridy++;
        requirementSummaryLabel.setFont(requirementSummaryLabel.getFont().deriveFont(12f));
        header.add(requirementSummaryLabel, gbc);

        gbc.gridy++;
        marketsSummaryLabel.setFont(marketsSummaryLabel.getFont().deriveFont(12f));
        header.add(marketsSummaryLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        header.add(new JLabel("Cargo capacity (t):"), gbc);
        gbc.gridx = 1;
        capacitySpinner.setPreferredSize(new Dimension(80, capacitySpinner.getPreferredSize().height));
        header.add(capacitySpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        header.add(new JLabel("Max markets per run:"), gbc);
        gbc.gridx = 1;
        header.add(maxMarketsSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        coverageProgress.setStringPainted(true);
        header.add(coverageProgress, gbc);

        gbc.gridy++;
        coverageValueLabel.setFont(coverageValueLabel.getFont().deriveFont(12f));
        header.add(coverageValueLabel, gbc);

        return header;
    }

    private JPanel buildTablesPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 0.6;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(runsTable), gbc);

        gbc.gridy = 1;
        gbc.weighty = 0.4;
        panel.add(new JScrollPane(legsTable), gbc);
        return panel;
    }

    private JPanel buildFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout());
        JButton recalcButton = new JButton("Recalculate");
        recalcButton.addActionListener(e -> controller.buildRoutePlan());
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> {
            Window window = SwingUtilities.getWindowAncestor(RouteOptimizerPanel.this);
            if (window != null) {
                window.dispose();
            }
        });

        JPanel buttons = new JPanel();
        buttons.add(recalcButton);
        buttons.add(closeButton);

        statusLabel.setForeground(java.awt.Color.DARK_GRAY);
        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private void registerModelListener() {
        PropertyChangeListener listener = evt -> {
            switch (evt.getPropertyName()) {
                case RouteOptimizerModel.PROPERTY_CONSTRUCTION_SITE -> updateSiteSummary((ConstructionSiteDto) evt.getNewValue());
                case RouteOptimizerModel.PROPERTY_CANDIDATE_MARKETS -> updateCandidateMarketsSummary((List<MarketDto>) evt.getNewValue());
                case RouteOptimizerModel.PROPERTY_ROUTE_PLAN -> updatePlan((RoutePlanDto) evt.getNewValue());
                case RouteOptimizerModel.PROPERTY_ERROR -> updateStatus((Throwable) evt.getNewValue());
                default -> {
                }
            }
        };
        model.addPropertyChangeListener(listener);
    }

    private void attachSpinnerListeners() {
        var parameterChangeHandler = (java.util.function.Consumer<ChangeEvent>) evt -> {
            if (adjustingSpinners) {
                return;
            }
            double capacity = ((Number) capacitySpinner.getValue()).doubleValue();
            int maxMarkets = ((Number) maxMarketsSpinner.getValue()).intValue();
            controller.updateOptimizationParameters(capacity, maxMarkets);
            if (model.getConstructionSite() != null) {
                controller.buildRoutePlan();
            }
        };
        capacitySpinner.addChangeListener(e -> parameterChangeHandler.accept(e));
        maxMarketsSpinner.addChangeListener(e -> parameterChangeHandler.accept(e));
    }

    private void updateSiteSummary(ConstructionSiteDto site) {
        if (site == null) {
            siteNameLabel.setText("Select a construction site to plan deliveries");
            requirementSummaryLabel.setText("Requirements: —");
            return;
        }
        siteNameLabel.setText(String.format(Locale.ROOT, "%s (ID: %d)", site.getSiteId(), site.getMarketId()));
        int delivered = 0;
        int remaining = 0;
        if (site.getRequirements() != null) {
            for (var req : site.getRequirements()) {
                delivered += req.getDeliveredQuantity();
                remaining += Math.max(0, req.getRequiredQuantity() - req.getDeliveredQuantity());
            }
        }
        requirementSummaryLabel.setText(
            String.format(Locale.ROOT, "Delivered: %,d t   Remaining: %,d t", delivered, remaining));
        statusLabel.setText("Site data loaded. Press Recalculate to build plan.");
    }

    private void updateCandidateMarketsSummary(List<MarketDto> markets) {
        int count = markets == null ? 0 : markets.size();
        marketsSummaryLabel.setText("Candidate markets: " + count);
    }

    private void updatePlan(RoutePlanDto plan) {
        if (plan == null) {
            return;
        }
        currentPlan = plan;
        runsTableModel.setRowCount(0);
        int runNumber = 1;
        for (DeliveryRunDto run : plan.getRuns()) {
            String route = buildRouteDescription(run);
            String materials = buildMaterialsSummary(run.getMaterialsSummaryTons());
            runsTableModel.addRow(new Object[]{
                runNumber++,
                route,
                TONNAGE_FORMAT.format(run.getTotalTonnage()),
                materials
            });
        }
        if (runsTableModel.getRowCount() > 0) {
            runsTable.setRowSelectionInterval(0, 0);
        } else {
            legsTableModel.setRowCount(0);
        }
        int coveragePercent = (int) Math.round(plan.getCoverageFraction() * 100);
        coverageProgress.setValue(coveragePercent);
        coverageProgress.setString(coveragePercent + "%");
        coverageValueLabel.setText(String.format(Locale.ROOT, "Coverage: %d%% of outstanding demand", coveragePercent));
        statusLabel.setText("Plan updated. Runs: " + plan.getRuns().size());
    }

    private void updateLegDetailForSelection() {
        int selectedRow = runsTable.getSelectedRow();
        if (selectedRow < 0 || currentPlan == null || currentPlan.getRuns() == null) {
            legsTableModel.setRowCount(0);
            return;
        }
        int modelIndex = runsTable.convertRowIndexToModel(selectedRow);
        if (modelIndex < 0 || modelIndex >= currentPlan.getRuns().size()) {
            return;
        }
        DeliveryRunDto run = currentPlan.getRuns().get(modelIndex);
        legsTableModel.setRowCount(0);
        int legIndex = 1;
        for (RunLegDto leg : run.getLegs()) {
            for (PurchaseDto purchase : leg.getPurchases()) {
                legsTableModel.addRow(new Object[]{
                    legIndex,
                    leg.getMarketName(),
                    purchase.getMaterialName(),
                    TONNAGE_FORMAT.format(purchase.getAmountTons())
                });
            }
            legIndex++;
        }
    }

    private String buildRouteDescription(DeliveryRunDto run) {
        List<String> names = new ArrayList<>();
        if (run.getLegs() != null) {
            for (RunLegDto leg : run.getLegs()) {
                names.add(leg.getMarketName());
            }
        }
        if (model.getConstructionSite() != null && model.getConstructionSite().getSiteId() != null) {
            names.add(model.getConstructionSite().getSiteId());
        } else {
            names.add("Construction Site");
        }
        return String.join(" → ", names);
    }

    private String buildMaterialsSummary(Map<String, Double> materials) {
        if (materials == null || materials.isEmpty()) {
            return "—";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Double> entry : materials.entrySet()) {
            parts.add(entry.getKey() + " " + TONNAGE_FORMAT.format(entry.getValue()));
        }
        return String.join(", ", parts);
    }

    private void updateStatus(Throwable error) {
        if (error == null) {
            statusLabel.setText(" ");
        } else {
            statusLabel.setText("Error: " + error.getMessage());
        }
    }

    private void showError(Throwable throwable) {
        JOptionPane.showMessageDialog(
            this,
            throwable.getMessage(),
            "Route Optimization Error",
            JOptionPane.ERROR_MESSAGE
        );
    }

    /**
     * Initializes the view with the selected construction site and request defaults.
     *
     * @param constructionSiteId site identifier to load via controller
     * @param defaultCapacity    default cargo capacity
     * @param defaultMaxMarkets  default max markets per run
     */
    public void displaySite(long constructionSiteId, double defaultCapacity, int defaultMaxMarkets) {
        adjustingSpinners = true;
        capacitySpinner.setValue(defaultCapacity);
        maxMarketsSpinner.setValue(defaultMaxMarkets);
        adjustingSpinners = false;
        controller.updateOptimizationParameters(defaultCapacity, defaultMaxMarkets);
        controller.loadConstructionSite(constructionSiteId);
    }
}
