package com.zergatstage.monitor;

import com.zergatstage.services.*;
import com.zergatstage.services.config.LogMonitorConfig;
import com.zergatstage.services.handlers.*;
import lombok.extern.slf4j.Slf4j;
import org.h2.tools.Server;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The EliteLogMonitorFrame class creates the main application frame.
 * It uses a JTabbedPane to hold two tabs:
 *  - "Drone provisioner" which displays real-time log event updates.
 *  - "Construction Sites" which allows users to manage construction projects.
 * Additionally, a runner panel is added at the bottom with buttons to start and stop
 * the StatusMonitor service.
 */
@Slf4j
public class EliteLogMonitorFrame extends JFrame {

    private final JLabel asteroidProspectedLabel;
    private final StatusMonitor statusMonitor;
    private boolean isStatusMonitorRunning = false; // Flag to track service state
    private boolean isLogMonitorRunning = false; // Flag to track service state
    private final LogMonitor logMonitor;


    // Initialize the ScheduledExecutorService
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Constructs the EliteLogMonitorFrame, initializes UI components,
     * sets up the log monitoring service, and integrates the runner panel.
     */
    public EliteLogMonitorFrame() {
        super("Elite Dangerous Log Monitor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 500);

        // Create the main container panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout());

        // -------------------------------
        // Create Tabbed Pane for Main Content
        // -------------------------------
        JTabbedPane tabbedPane = new JTabbedPane();
        Color darkBackground = new Color(12, 12, 12);   // #0C0C0C
        Color lightBackground = new Color(50, 29, 11);   // #321d0b

        Color accentGray = new Color(63, 63, 63);   // #3F3F3F
        Color orangeText = new Color(236, 151, 6);  // #EC9706
        Color neutralText = new Color(191, 191, 191);// #BFBFBF

        createMenuBar();


        // Log Monitor Panel (Tab 1)
        JPanel logMonitorPanel = new JPanel(new GridLayout(2, 1));

        JLabel droneLaunchedLabel = new JLabel("Drone Launched: No");
        asteroidProspectedLabel = new JLabel("Asteroid Prospected: No");
        asteroidProspectedLabel.setOpaque(true);
        resetAsteroidLabel();

        logMonitorPanel.add(droneLaunchedLabel);
        logMonitorPanel.add(asteroidProspectedLabel);
        tabbedPane.addTab("Drone provisioner", logMonitorPanel);

        // Construction Sites Panel (Tab 2)
        ConstructionSitePanel constructionSitePanel = new ConstructionSitePanel();
        tabbedPane.addTab("Construction Sites", constructionSitePanel);

        // Add the tabbed pane to the center of the main panel
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        /*
        Runner Panel for StatusMonitor Controls
        -------------------------------
        */
        JPanel runnerPanel = getStatusManageJPanel(lightBackground, orangeText, darkBackground);

        mainPanel.add(runnerPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        LogMonitorConfig logMonitorconfig = new LogMonitorConfig();
        Path logDirectory = logMonitorconfig.logDirectory();
        //starting log monitor
        logMonitor = new LogMonitor(logDirectory,
                getLogEventHandlers(droneLaunchedLabel));
        logMonitor.startMonitoring();
        isLogMonitorRunning = true;

        // Schedule the periodic task
        scheduler.scheduleAtFixedRate(logMonitor::scheduledCheckLogs, 0, 1, TimeUnit.SECONDS);

        // logMonitor.scheduledCheckLogs();

        // Add a shutdown hook to clean up resources
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isLogMonitorRunning) {
                stopLogMonitor();
            }
            scheduler.shutdown();
        }));

        // -------------------------------
        // StatusMonitor Initialization
        // -------------------------------
        // Assume Status.json and Journal.log are located in the same log directory
        Path statusFile = logDirectory.resolve("Status.json");
        Path sessionSummaryFile = logDirectory.resolve("Journal.log");
        statusMonitor = new StatusMonitor(statusFile.toString(), sessionSummaryFile.toString());

        setVisible(true);
    }

    private List<LogEventHandler> getLogEventHandlers(JLabel droneLaunchedLabel) {
        LogEventHandler droneHandler = new DroneLaunchEventHandler(droneLaunchedLabel, this);
        LogEventHandler asteroidHandler = new AsteroidProspectEventHandler(asteroidProspectedLabel);
        LogEventHandler cargoHandler = new CargoUpdateEventHandler();
        LogEventHandler constructionDepotHandler = new ColonisationConstructionDepot();
        LogEventHandler dockedEventHandler = new DockedEventHandler();
        return List.of(droneHandler, asteroidHandler,
                cargoHandler, constructionDepotHandler, dockedEventHandler);
    }

    /**
     * Creates and sets up the main menu bar for the application.
     * Adds "File", "Tools", and "Dictionary" menus with example actions.
     */
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = getFileJMenu();
        JMenu toolsMenu = new JMenu("Tools");

        JMenuItem statusMonitorToggle = new JMenuItem("Toggle Status File Monitoring");
        statusMonitorToggle.addActionListener(_ -> {
            if (isStatusMonitorRunning) {
                stopStatusMonitor();
            } else {
                startStatusMonitor();
            }
        });

        toolsMenu.add(statusMonitorToggle);
        JMenuItem logMonitorToggle = new JMenuItem("Toggle Logs Monitoring");
        logMonitorToggle.addActionListener(_ -> {
            if (isLogMonitorRunning) {
                stopLogMonitor();
            } else {
                startLogMonitor();
            }
        });

        toolsMenu.add(logMonitorToggle);

        JMenu dictionaryMenu = new JMenu("Dictionary");

        JMenuItem openDictionaryItem = new JMenuItem("Open Dictionary");
        openDictionaryItem.addActionListener(e -> {
            DictionaryManagerDialog dialog = new DictionaryManagerDialog(this);
            dialog.setVisible(true);
        });


        dictionaryMenu.add(openDictionaryItem);

        // Add menus to the menu bar
        menuBar.add(fileMenu);
        menuBar.add(toolsMenu);
        menuBar.add(dictionaryMenu);

        // Set the menu bar for this frame
        setJMenuBar(menuBar);
    }

    private void startLogMonitor() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(logMonitor::scheduledCheckLogs, 0, 1, TimeUnit.SECONDS);
        }
        isLogMonitorRunning = true;
        logMonitor.startMonitoring();
    }
    private void stopLogMonitor() {
        isLogMonitorRunning = false;
        logMonitor.stopMonitoring();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private JMenu getFileJMenu() {

        JMenu fileMenu = new JMenu("File");
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(_ -> {
            log.info("Exiting application...");
            logMonitor.stopMonitoring();
            if (statusMonitor != null && isStatusMonitorRunning) {
                statusMonitor.stop();
            }
            System.exit(0);
        });

        JMenuItem logonItem = new JMenuItem("Logon");
        logonItem.addActionListener(_ -> {
            log.info("Logon process");
            //implement connection and authorizing methods

        });
        JMenuItem loadMarketItem = new JMenuItem("Load Market File");
        loadMarketItem.addActionListener(_ -> {
            // Example placeholder action
            JOptionPane.showMessageDialog(this,
                    "Market file loading not implemented yet.",
                    "Load Market File",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        fileMenu.add(loadMarketItem);
        fileMenu.add(logonItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        return fileMenu;
    }


    private JPanel getStatusManageJPanel(Color lightBackground, Color orangeText, Color darkBackground) {
        JPanel runnerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        // Start button: Orange background with white text
        JButton startButton = new JButton("Start Status Monitor");
        startButton.setBackground(lightBackground); // Orange
        startButton.setForeground(orangeText);
        startButton.addActionListener(_ -> startStatusMonitor());

        // Stop button: Black background with orange text
        JButton stopButton = new JButton("Stop Status Monitor");
        stopButton.setBackground(darkBackground);
        stopButton.setForeground(orangeText); // Orange
        stopButton.addActionListener(_ -> stopStatusMonitor());


        runnerPanel.add(startButton);
        runnerPanel.add(stopButton);
        return runnerPanel;
    }

    /**
     * Resets the asteroid prospecting label to its default appearance.
     */
    public void resetAsteroidLabel() {
        asteroidProspectedLabel.setText("Asteroid Prospected: No");
        asteroidProspectedLabel.setBackground(DisplayConfig.COLOR_DEFAULT);
        asteroidProspectedLabel.setForeground(Color.BLACK);
    }

    /**
     * Starts the StatusMonitor service if it is not already running.
     */
    private void startStatusMonitor() {
        if (!isStatusMonitorRunning) {
            statusMonitor.start();
            isStatusMonitorRunning = true;
            log.info("Status Monitor started.");
        } else {
            log.info("Status Monitor is already running.");
        }
    }

    /**
     * Stops the StatusMonitor service if it is currently running.
     */
    private void stopStatusMonitor() {
        if (isStatusMonitorRunning) {
            statusMonitor.stop();
            isStatusMonitorRunning = false;
            log.info("Status Monitor stopped.");
        } else {
            log.info("Status Monitor is not running.");
        }
    }
}