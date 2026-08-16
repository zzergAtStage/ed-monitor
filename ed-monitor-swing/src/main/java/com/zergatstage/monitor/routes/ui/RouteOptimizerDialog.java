package com.zergatstage.monitor.routes.ui;

import javax.swing.JDialog;
import javax.swing.WindowConstants;
import java.awt.Window;
import java.util.Map;

/**
 * Modal dialog wrapper for {@link RouteOptimizerPanel}. Ensures controller resources are
 * released when the window closes.
 */
public class RouteOptimizerDialog extends JDialog {

    private final RouteOptimizerPanel panel;
    private final RouteOptimizerController controller;

    public RouteOptimizerDialog(Window owner,
                                RouteOptimizerModel model,
                                RouteOptimizerController controller) {
        super(owner, "Route Optimizer", ModalityType.APPLICATION_MODAL);
        this.controller = controller;
        this.panel = new RouteOptimizerPanel(model, controller);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(panel);
        pack();
        setLocationRelativeTo(owner);
        com.zergatstage.monitor.util.KeyBindingUtil.installEscapeToClose(this);
    }

    public void displaySite(long constructionSiteId,
                            double defaultCapacity,
                            int defaultMaxMarkets,
                            Map<Long, Integer> onboardCommodities) {
        panel.displaySite(constructionSiteId, defaultCapacity, defaultMaxMarkets, onboardCommodities);
    }

    @Override
    public void dispose() {
        controller.shutdown();
        super.dispose();
    }
}
