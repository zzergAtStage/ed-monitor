package com.zergatstage.monitor;

import com.zergatstage.services.*;
import com.zergatstage.services.handlers.*;
import lombok.extern.slf4j.Slf4j;
import org.h2.tools.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.sql.SQLException;

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

    private final JLabel droneLaunchedLabel;
    private final JLabel asteroidProspectedLabel;
    private final Path logDirectory;
    private StatusMonitor statusMonitor;
    private boolean isStatusMonitorRunning = false; // Flag to track service state

    @Autowired
    ApplicationContext applicationContext;
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
        Color darkBackground = new Color( 12, 12, 12);   // #0C0C0C
        Color lightBackground = new Color(50, 29, 11);   // #321d0b

        Color accentGray     = new Color( 63, 63, 63);   // #3F3F3F
        Color orangeText     = new Color( 236, 151, 6);  // #EC9706
        Color neutralText    = new Color( 191, 191, 191);// #BFBFBF

        createMenuBar();


        // Log Monitor Panel (Tab 1)
        JPanel logMonitorPanel = new JPanel(new GridLayout(2, 1));

        droneLaunchedLabel = new JLabel("Drone Launched: No");
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

        LogEventHandler droneHandler = new DroneLaunchEventHandler(droneLaunchedLabel, this);
        LogEventHandler asteroidHandler = new AsteroidProspectEventHandler(asteroidProspectedLabel);
        LogEventHandler cargoHandler = new CargoUpdateEventHandler();
        LogEventHandler constructionDepotHandler = new ColonisationConstructionDepot();


        ApplicationContext context = ApplicationContextProvider.getApplicationContext();
        LogMonitor logMonitor = context.getBean(LogMonitor.class);
        logDirectory = context.getBean(Path.class);
        logMonitor.scheduledCheckLogs();

        // -------------------------------
        // StatusMonitor Initialization
        // -------------------------------
        // Assume Status.json and Journal.log are located in the same log directory
        Path statusFile = logDirectory.resolve("Status.json");
        Path sessionSummaryFile = logDirectory.resolve("Journal.log");
        statusMonitor = new StatusMonitor(statusFile.toString(), sessionSummaryFile.toString());

        setVisible(true);
    }

    /**
     * Creates and sets up the main menu bar for the application.
     * Adds "File", "Tools", and "Dictionary" menus with example actions.
     */
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = getFileJMenu();
        JMenu toolsMenu = new JMenu("Tools");

        JMenuItem monitorToggle = new JMenuItem("Toggle Log Monitoring");
        monitorToggle.addActionListener(e -> {
            if (isStatusMonitorRunning) {
                stopStatusMonitor();
            } else {
                startStatusMonitor();
            }
        });

        toolsMenu.add(monitorToggle);

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

    private JMenu getFileJMenu() {
        applicationContext = ApplicationContextProvider.getApplicationContext();
        JMenu fileMenu = new JMenu("File");
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            log.info("Exiting application...");
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        });

        JMenuItem loadMarketItem = new JMenuItem("Load Market File");
        loadMarketItem.addActionListener(e -> {
            // Example placeholder action
            JOptionPane.showMessageDialog(this,
                    "Market file loading not implemented yet.",
                    "Load Market File",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        fileMenu.add(loadMarketItem);
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
        startButton.addActionListener(e -> startStatusMonitor());

        // Stop button: Black background with orange text
        JButton stopButton = new JButton("Stop Status Monitor");
        stopButton.setBackground(darkBackground);
        stopButton.setForeground(orangeText); // Orange
        stopButton.addActionListener(e -> stopStatusMonitor());


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

    /**
     * Main method to launch the EliteLogMonitorFrame application.
     *
     * @param args command-line arguments (not used).
     */
    public static void main(String[] args) {
        // Start H2 Console
        try {
            Server webServer = Server.createWebServer("-webPort", "8082", "-tcpAllowOthers").start();
           log.info("H2 Console started at: http://localhost:8082");
        } catch (SQLException e) {
            log.error("Error starting the H2 webServer: {} \n {}", e.getMessage(),e.getCause().getMessage());
        }
        SwingUtilities.invokeLater(EliteLogMonitorFrame::new);
    }
}
