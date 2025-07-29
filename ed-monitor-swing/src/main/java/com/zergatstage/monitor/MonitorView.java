package com.zergatstage.monitor;

import com.zergatstage.monitor.component.ConstructionSitePanel;
import com.zergatstage.monitor.component.DictionaryManagerDialog;
import com.zergatstage.monitor.component.DroneProvisionerPanel;
import com.zergatstage.monitor.config.UiConstants;
import com.zergatstage.monitor.handlers.HandlerConfiguration;
import com.zergatstage.monitor.handlers.LogEventHandler;
import com.zergatstage.monitor.service.JournalLogMonitor;
import com.zergatstage.monitor.service.StatusMonitor;
import lombok.extern.slf4j.Slf4j;


import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
public class MonitorView {
    private final MonitorController controller;
    private final JFrame frame;
    private final DroneProvisionerPanel droneProvisionerPanel;
    private final ConstructionSitePanel constructionSitePanel;

    public MonitorView(MonitorController controller, Path logDirectory) {
        this.controller = controller;
        this.frame = new JFrame(UiConstants.TITLE);

        droneProvisionerPanel = new DroneProvisionerPanel();
        constructionSitePanel = new ConstructionSitePanel();
        // Status labels
        Image icon = null;
        try {
            icon = ImageIO.read(
                    getClass().getResourceAsStream("/app-logo.png"));
            frame.setIconImage(icon);
        } catch (IOException e) {
            log.error("Failed to load application icon", e);
        }

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

        JMenu dict = new JMenu(UiConstants.DICTIONARY_MENU);
        JMenuItem openDict = new JMenuItem(UiConstants.OPEN_DICTIONARY);
        openDict.addActionListener(e -> {
            DictionaryManagerDialog dialog = new DictionaryManagerDialog(frame);
            dialog.setVisible(true);
        });
        dict.add(openDict);

        menuBar.add(file);
        menuBar.add(tools);
        menuBar.add(dict);
        return menuBar;
    }
}
