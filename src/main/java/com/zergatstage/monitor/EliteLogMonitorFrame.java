package com.zergatstage.monitor;

import com.zergatstage.services.*;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * The EliteLogMonitorFrame class creates the main application frame.
 * It uses a JTabbedPane to hold two tabs:
 *  - "Log Monitor" which displays real-time log event updates.
 *  - "Construction Sites" which allows users to manage construction projects.
 * Additionally, a runner panel is added at the bottom with buttons to start and stop
 * the StatusMonitor service.
 */
public class EliteLogMonitorFrame extends JFrame {

    private final JLabel droneLaunchedLabel;
    private final JLabel asteroidProspectedLabel;
    private final LogMonitor logMonitor;
    private StatusMonitor statusMonitor;
    private boolean isStatusMonitorRunning = false; // Flag to track service state

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

        // Log Monitor Panel (Tab 1)
        JPanel logMonitorPanel = new JPanel(new GridLayout(2, 1));
        droneLaunchedLabel = new JLabel("Drone Launched: No");
        asteroidProspectedLabel = new JLabel("Asteroid Prospected: No");
        asteroidProspectedLabel.setOpaque(true);
        resetAsteroidLabel();

        logMonitorPanel.add(droneLaunchedLabel);
        logMonitorPanel.add(asteroidProspectedLabel);
        tabbedPane.addTab("Log Monitor", logMonitorPanel);

        // Construction Sites Panel (Tab 2)
        ConstructionSitePanel constructionSitePanel = new ConstructionSitePanel();
        tabbedPane.addTab("Construction Sites", constructionSitePanel);

        // Add the tabbed pane to the center of the main panel
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // -------------------------------
        // Runner Panel for StatusMonitor Controls
        // -------------------------------
        JPanel runnerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        // Start button: Orange background with white text
        JButton startButton = new JButton("Start Status Monitor");
        startButton.setBackground(new Color(255, 165, 0)); // Orange
        startButton.setForeground(Color.WHITE);
        startButton.addActionListener(e -> startStatusMonitor());

        // Stop button: Black background with orange text
        JButton stopButton = new JButton("Stop Status Monitor");
        stopButton.setBackground(Color.BLACK);
        stopButton.setForeground(new Color(255, 165, 0)); // Orange
        stopButton.addActionListener(e -> stopStatusMonitor());

        runnerPanel.add(startButton);
        runnerPanel.add(stopButton);

        // Add the runner panel to the south of the main panel
        mainPanel.add(runnerPanel, BorderLayout.SOUTH);

        // Set the main panel as the content pane of the frame
        setContentPane(mainPanel);

        // -------------------------------
        // Log Monitor Initialization
        // -------------------------------
        LogEventHandler droneHandler = new DroneLaunchEventHandler(droneLaunchedLabel, this);
        LogEventHandler asteroidHandler = new AsteroidProspectEventHandler(asteroidProspectedLabel);

        Path logDirectory = Paths.get(System.getProperty("user.home"),
                "Saved Games", "Frontier Developments", "Elite Dangerous");

        logMonitor = new LogMonitor(logDirectory, Arrays.asList(droneHandler, asteroidHandler));
        logMonitor.start();

        // -------------------------------
        // StatusMonitor Initialization
        // -------------------------------
        // Assume Status.json and Journal.log are located in the same log directory
        Path statusFile = logDirectory.resolve("Status.json");
        Path sessionSummaryFile = logDirectory.resolve("Journal.log");
        statusMonitor = new StatusMonitor(statusFile, sessionSummaryFile);

        setVisible(true);
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
            System.out.println("Status Monitor started.");
        } else {
            System.out.println("Status Monitor is already running.");
        }
    }

    /**
     * Stops the StatusMonitor service if it is currently running.
     */
    private void stopStatusMonitor() {
        if (isStatusMonitorRunning) {
            statusMonitor.stop();
            isStatusMonitorRunning = false;
            System.out.println("Status Monitor stopped.");
        } else {
            System.out.println("Status Monitor is not running.");
        }
    }

    /**
     * Main method to launch the EliteLogMonitorFrame application.
     *
     * @param args command-line arguments (not used).
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(EliteLogMonitorFrame::new);
    }
}
