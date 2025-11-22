package com.zergatstage.monitor;

import java.awt.BorderLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Point;
import java.awt.IllegalComponentStateException;
import java.awt.Image;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;

import com.zergatstage.monitor.component.ConstructionSitePanel;
import com.zergatstage.monitor.component.DictionaryManagerDialog;
import com.zergatstage.monitor.component.DroneProvisionerPanel;
import com.zergatstage.monitor.component.ServerManagementMenu;
import com.zergatstage.monitor.config.UiConstants;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitorView {
    private final MonitorController controller;
    private final JFrame frame;
    private final DroneProvisionerPanel droneProvisionerPanel;
    private final ConstructionSitePanel constructionSitePanel;
    private float currentOpacity = 0.6f;
    private boolean overlayMode = false;

    public MonitorView(MonitorController controller, Path logDirectory) {
        this.controller = controller;
        this.frame = new JFrame(UiConstants.TITLE);

        droneProvisionerPanel = new DroneProvisionerPanel();
        constructionSitePanel = new ConstructionSitePanel();
        // Status labels
        Image icon = null;
        try {
            icon = ImageIO.read(
                    Objects.requireNonNull(getClass().getResourceAsStream("/app-logo.png")));
            frame.setIconImage(icon);
        } catch (IOException e) {
            log.error("Failed to load application icon", e);
        }

        // Ensure the app stays on top when not minimized
        frame.setAlwaysOnTop(false);

        buildUI();
        controller.startAll();
    }

    private void buildUI() {
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setJMenuBar(createMenuBar());

        // Main panel with tabs

        JPanel mainPanel = new JPanel(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Construction Sites", constructionSitePanel);
        tabs.addTab("Asteroid provisioner", droneProvisionerPanel);

        mainPanel.add(tabs, BorderLayout.CENTER);

        frame.setContentPane(mainPanel);
        frame.setSize(800,600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu file = new JMenu(UiConstants.FILE_MENU);
        JMenuItem loadMarket = new JMenuItem(UiConstants.LOAD_MARKET_ITEM);
        loadMarket.addActionListener(e -> JOptionPane.showMessageDialog(frame,
                "Market file loading not implemented yet.",
                UiConstants.LOAD_MARKET_ITEM,
                JOptionPane.INFORMATION_MESSAGE));

        JMenuItem logon = new JMenuItem(UiConstants.LOGON_ITEM);
        logon.addActionListener(e -> JOptionPane.showMessageDialog(frame,
                "Logon process invoked.", UiConstants.LOGON_ITEM, JOptionPane.INFORMATION_MESSAGE));

        JMenuItem exit = new JMenuItem(UiConstants.EXIT_ITEM);
        exit.addActionListener(controller::onExit);

        file.add(loadMarket);
        file.add(logon);
        file.addSeparator();
        file.add(exit);

        JMenu tools = new JMenu(UiConstants.TOOLS_MENU);
        JMenuItem toggleStatus = new JMenuItem(UiConstants.TOGGLE_STATUS_MONITOR);
        toggleStatus.addActionListener(controller::toggleStatusMonitor);
        JMenuItem toggleLog = new JMenuItem(UiConstants.TOGGLE_LOG_MONITOR);
        toggleLog.addActionListener(controller::toggleLogMonitor);
        tools.add(toggleStatus);
        tools.add(toggleLog);

        // Overlay mode toggle (undecorated + adjustable opacity)
        JCheckBoxMenuItem overlayToggle = new JCheckBoxMenuItem("Overlay mode (undecorated + opacity)", overlayMode);
        overlayToggle.addActionListener(e -> {
            boolean enable = overlayToggle.isSelected();
            setOverlayMode(enable);
        });
        tools.addSeparator();
        tools.add(overlayToggle);

        // Opacity adjustment
        JMenuItem setOpacity = new JMenuItem("Set opacity…");
        setOpacity.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(frame, "Enter opacity 0.2 - 1.0", String.valueOf(currentOpacity));
            if (input == null) return;
            try {
                float val = Float.parseFloat(input.trim());
                if (val < 0.2f) val = 0.2f;
                if (val > 1.0f) val = 1.0f;
                currentOpacity = val;
                if (overlayMode) applyWindowOpacity();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Invalid number. Use 0.2 - 1.0", "Invalid input", JOptionPane.WARNING_MESSAGE);
            }
        });
        tools.add(setOpacity);

        JMenu dict = new JMenu(UiConstants.DICTIONARY_MENU);
        JMenuItem openDict = new JMenuItem(UiConstants.OPEN_DICTIONARY);
        openDict.addActionListener(e -> {
            DictionaryManagerDialog dialog = new DictionaryManagerDialog(frame);
            dialog.setVisible(true);
        });
        dict.add(openDict);

        menuBar.add(file);
        menuBar.add(tools);
        menuBar.add(new ServerManagementMenu(frame, controller.getServerLifecycleService()).build());
        menuBar.add(dict);
        return menuBar;
    }

    private void setOverlayMode(boolean enable) {
        if (this.overlayMode == enable) return;
        this.overlayMode = enable;
        // Save state
        Dimension size = frame.getSize();
        Point loc = frame.getLocation();
        boolean wasVisible = frame.isVisible();
        try {
            if (wasVisible) frame.setVisible(false);
        } catch (IllegalComponentStateException ignore) {
            // If not yet displayable, ignore
        }

        // Ensure the window is opaque before changing decoration to avoid IAE
        forceOpaque();
        frame.dispose();
        frame.setUndecorated(enable);

        // Apply opacity if supported in overlay mode; otherwise revert to 1.0
        if (enable) {
            if (!applyWindowOpacity()) {
                JOptionPane.showMessageDialog(frame,
                        "Window translucency is not supported on this system.",
                        "Opacity not supported",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } else {
            // Back to normal
            safeSetOpacity(1.0f);
        }

        frame.setSize(size);
        frame.setLocation(loc);
        if (wasVisible) frame.setVisible(true);
    }

    private boolean applyWindowOpacity() {
        try {
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
                safeSetOpacity(currentOpacity);
                return true;
            } else {
                log.warn("Window translucency not supported on this platform; skipping opacity.");
                return false;
            }
        } catch (Throwable t) {
            log.warn("Failed to set window opacity; proceeding without it.", t);
            return false;
        }
    }

    private void safeSetOpacity(float value) {
        try {
            frame.setOpacity(value);
        } catch (Throwable t) {
            // Ignore if unsupported at runtime; we keep running
            log.warn("setOpacity failed (value={}): {}", value, t.toString());
        }
    }

    private void forceOpaque() {
        try {
            // Full alpha background marks window as opaque for the toolkit
            Color bg = frame.getBackground();
            if (bg == null || bg.getAlpha() != 255) {
                frame.setBackground(new Color(0, 0, 0, 255));
            }
        } catch (Throwable ignored) {
        }
        // Attempt to reset opacity to fully opaque (ok if this no-ops)
        try { frame.setOpacity(1.0f); } catch (Throwable ignored) { }
    }
}
