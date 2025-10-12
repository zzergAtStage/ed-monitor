package com.zergatstage.monitor.component;

import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.service.managers.AsteroidManager;
import com.zergatstage.monitor.service.managers.DroneManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * DroneProvisionerPanel - UI panel that displays last 3 prospected asteroids as colored rectangles.
 * - Top combobox selects which material to track (Tritium, Platinum, Painite)
 * - Three stacked rectangles show the most recent first
 * - Coloring rules:
 *     < 30%  -> gray  (#B0B0B0)
 *     30-43% -> orange (#FFA500)
 *     > 43%  -> violet (#8A2BE2)
 *
 * Managers in the app should call onAsteroidProspected(...) or onDroneLaunched()
 * or you can wire observer callbacks from your managers here (see commented examples).
 */
public class DroneProvisionerPanel extends JPanel {

    private static final Color COLOR_GRAY = Color.decode("#B0B0B0");
    private static final Color COLOR_ORANGE = Color.decode("#FFA500");
    private static final Color COLOR_VIOLET = Color.decode("#8A2BE2");

    private final JComboBox<String> metalCombo;
    private final JLabel droneLaunchedLabel;
    private final DrawingPanel drawingPanel;

    // keep last 3 proportions (0..1). newest at head
    private final Deque<Double> recentProportions = new ArrayDeque<>(3);

    private final DroneManager droneManager;
    private final AsteroidManager asteroidManager;

    public DroneProvisionerPanel() {
        this.droneManager = DefaultManagerFactory.getInstance().getDroneManager();
        this.asteroidManager = DefaultManagerFactory.getInstance().getAsteroidManager();

        setLayout(new BorderLayout(6, 6));

        // Top control: metal selection and drone label
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        metalCombo = new JComboBox<>(new String[]{"Tritium", "Platinum", "Painite"});
        metalCombo.addActionListener(e -> {
            String selected = Objects.toString(metalCombo.getSelectedItem(), "Tritium");
            // notify asteroid manager of selection change if needed
            asteroidManager.setSelectedMaterial(selected);
            // clear recent data
            clearRecent();
        });
        top.add(new JLabel("Track:"));
        top.add(metalCombo);

        droneLaunchedLabel = new JLabel("Drone Launched: No");
        top.add(Box.createHorizontalStrut(10));
        top.add(droneLaunchedLabel);

        add(top, BorderLayout.NORTH);

        // drawing area
        drawingPanel = new DrawingPanel();
        drawingPanel.setPreferredSize(new Dimension(300, 220));
        add(drawingPanel, BorderLayout.CENTER);

        // initialize with empty (no prospect)
        clearRecent();

        // try to register as listener with managers if they expose typical addListener methods.
        // Uncomment/adapt to your manager API:
//         asteroidManager.addListener(this::onAsteroidProspected);
         asteroidManager.addListener(() -> SwingUtilities.invokeLater(this::onAsteroidProspected));
         droneManager.addListener(() -> SwingUtilities.invokeLater(this::onDroneLaunched)); //addDroneListener(() -> onDroneLaunched());

    }

    // Clear and repaint
    private void clearRecent() {
        recentProportions.clear();
        recentProportions.addLast(0.0);
        recentProportions.addLast(0.0);
        recentProportions.addLast(0.0);
        drawingPanel.repaint();
    }

    /**
     * This method should be called when an asteroid prospect event arrives.
     * The event JSON typically contains "Materials": [ { "Name": "...", "Proportion": 0.12 }, ... ]
     */
    public void onAsteroidProspected() {
        double proportion = Double.NaN;
        double knownProportionValue = asteroidManager.getProportionForSelectedMaterial();

        if (!Double.isNaN(knownProportionValue)) {
            proportion = knownProportionValue;
        }

        // If still NaN, fall back to 0.0
        if (Double.isNaN(proportion)) {
            proportion = 0.0;
        }

        pushProportion(proportion);
    }

    /**
     * Called when a drone is launched.
     * UI will update the label briefly.
     */
    public void onDroneLaunched() {
        SwingUtilities.invokeLater(() -> {
            droneLaunchedLabel.setText("Drone Launched: Yes");
            // optionally reset label after a short delay
            Timer t = new Timer(3000, e -> droneLaunchedLabel.setText("Drone Launched: No"));
            t.setRepeats(false);
            t.start();
        });
    }

    // keep only 3 latest
    private void pushProportion(double p) {
        synchronized (recentProportions) {
            // convert from 0..1 or 0..100 if event uses percent; normalize heuristically
            if (p > 1.0) {
                // assume percent value (0..100)
                p = p / 100.0;
            }
            // clamp
            if (p < 0.0) p = 0.0;
            if (p > 1.0) p = 1.0;

            if (recentProportions.size() == 3) {
                recentProportions.removeLast();
            }
            recentProportions.addFirst(p);
        }
        drawingPanel.repaint();
    }

    // Map proportion to color according to your rules
    private Color colorFor(double proportion) {
        double percent = proportion * 100.0;
        if (percent < 30.0) return COLOR_GRAY;
        if (percent <= 43.0) return COLOR_ORANGE;
        return COLOR_VIOLET;
    }

    // small helper for text formatting
    private static String fmtPercent(double p) {
        return String.format("%.1f%%", p * 100.0);
    }

    // inner component that does the rectangles drawing
    private class DrawingPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            // background
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());

            int rows = 3;
            int padding = 12;
            int gap = 8;
            int totalGap = (rows - 1) * gap + 2 * padding;
            int h = Math.max(20, (getHeight() - totalGap) / rows);
            int w = getWidth() - 2 * padding;
            int x = padding;
            int y = padding;

            String metal = Objects.toString(metalCombo.getSelectedItem(), "Tritium");

            synchronized (recentProportions) {
                Double[] arr = recentProportions.toArray(new Double[0]);
                // ensure length 3
                Double[] use = new Double[]{0.0, 0.0, 0.0};
                for (int i = 0; i < Math.min(arr.length, 3); i++) use[i] = arr[i];

                for (int i = 0; i < rows; i++) {
                    double p = use[i] == null ? 0.0 : use[i];
                    Color c = colorFor(p);
                    g.setColor(c);
                    g.fillRect(x, y, w, h);

                    // border
                    g.setColor(Color.BLACK);
                    g.drawRect(x, y, w, h);

                    // text: metal name + percent
                    g.setColor(Color.BLACK);
                    String text = String.format("%s: %s", metal, fmtPercent(p));
                    FontMetrics fm = g.getFontMetrics();
                    int tx = x + 8;
                    int ty = y + (h + fm.getAscent()) / 2 - 2;
                    g.drawString(text, tx, ty);

                    y += h + gap;
                }
            }
        }
    }
}

