package com.zergatstage;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONArray;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.GridLayout;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EliteLogMonitor extends JFrame {
    private final Instant appStartTime;
    private final JLabel droneLaunchedLabel;
    private final JLabel asteroidProspectedLabel;
    private final Path logDirectory;
    private Path lastProcessedFile;
    private long lastProcessedFilePosition;
    private Instant lastProcessedTimestamp;

    // Tritium threshold constants
    private static final double TRITIUM_THRESHOLD_LOW = 10.0;
    private static final double TRITIUM_THRESHOLD_MEDIUM = 15.1;
    private static final double TRITIUM_THRESHOLD_HIGH = 25.0;

    // Color constants
    private static final Color COLOR_DEFAULT = new Color(238, 238, 238); // Light gray
    private static final Color COLOR_LOW = Color.GRAY;
    private static final Color COLOR_MEDIUM = Color.YELLOW;
    private static final Color COLOR_HIGH = Color.ORANGE;
    private static final Color COLOR_VERY_HIGH = new Color(147, 112, 219); // Medium purple

    public EliteLogMonitor() {
        super("Elite Dangerous Log Monitor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(2, 1));

        droneLaunchedLabel = new JLabel("Drone Launched: No");
        asteroidProspectedLabel = new JLabel("Asteroid Prospected: No");

        // Set default background and make labels opaque
        asteroidProspectedLabel.setOpaque(true);
        resetAsteroidLabel();

        add(droneLaunchedLabel);
        add(asteroidProspectedLabel);

        setSize(300, 100);

        // Initialize monitoring variables
        appStartTime = Instant.now();
        lastProcessedFilePosition = 0;
        lastProcessedTimestamp = Instant.now();
        logDirectory = Paths.get(System.getProperty("user.home"),
                "Saved Games", "Frontier Developments", "Elite Dangerous");

        startLogMonitoring();
        setVisible(true);
    }

    private void resetAsteroidLabel() {
        asteroidProspectedLabel.setText("Asteroid Prospected: No");
        asteroidProspectedLabel.setBackground(COLOR_DEFAULT);
        asteroidProspectedLabel.setForeground(Color.BLACK);
    }


    private void startLogMonitoring() {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(this::checkLogs, 0, 1, TimeUnit.SECONDS);
        System.out.println("Log monitoring started at: " + appStartTime);
    }

    private void checkLogs() {
        try {
            List<Path> logFiles = findLogFiles(logDirectory);
            if (logFiles.isEmpty()) {
                System.out.println("No log files found in: " + logDirectory);
                return;
            }

            Path latestLogFile = findLatestLogFile(logFiles);
            if (latestLogFile != null) {
                if (!latestLogFile.equals(lastProcessedFile)) {
                    // New log file detected, reset position
                    lastProcessedFilePosition = 0;
                    lastProcessedFile = latestLogFile;
                }
                processLogFile(latestLogFile);
            }
        } catch (IOException e) {
            System.err.println("Error checking logs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<Path> findLogFiles(Path directory) throws IOException {
        List<Path> logFiles = new ArrayList<>();
        if (Files.exists(directory) && Files.isDirectory(directory)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.log")) {
                stream.forEach(logFiles::add);
            }
        }
        return logFiles;
    }

    private Path findLatestLogFile(List<Path> logFiles) {
        return logFiles.stream()
                .max((f1, f2) -> {
                    try {
                        return Files.getLastModifiedTime(f1).compareTo(Files.getLastModifiedTime(f2));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .orElse(null);
    }

    private void processLogFile(Path logFile) {
        try {
            long fileSize = Files.size(logFile);
            if (fileSize > lastProcessedFilePosition) {
                try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                    raf.seek(lastProcessedFilePosition);
                    String line;
                    while ((line = raf.readLine()) != null) {
                        processLogLine(line);
                    }
                    lastProcessedFilePosition = fileSize;
                }
            }
        } catch (IOException e) {
            System.err.println("Error processing log file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processLogLine(String line) {
        try {
            JSONObject jsonObject = new JSONObject(new JSONTokener(line));
            String timestampStr = jsonObject.getString("timestamp");
            Instant timestamp = OffsetDateTime.parse(timestampStr).toInstant();

            if (timestamp.isAfter(lastProcessedTimestamp)) {
                String event = jsonObject.getString("event");

                if ("LaunchDrone".equals(event)) {
                    processLaunchDrone(jsonObject);
                } else if ("ProspectedAsteroid".equals(event)) {
                    processProspectedAsteroid(jsonObject);
                }

                lastProcessedTimestamp = timestamp;
            }
        } catch (Exception e) {
            // Silently ignore malformed JSON lines
        }
    }
    private void processLaunchDrone(JSONObject jsonObject) {
        try {
            String droneType = jsonObject.getString("Type");
            SwingUtilities.invokeLater(() -> {
                droneLaunchedLabel.setText("Drone Launched: Yes (" + droneType + ")");
                if ("Prospector".equals(droneType)) {
                    resetAsteroidLabel();
                }
            });
        } catch (Exception e) {
            System.err.println("Error processing drone launch: " + e.getMessage());
        }
    }

    private void processProspectedAsteroid(JSONObject jsonObject) {
        double tritiumProportion = 0.0;

        try {
            if (jsonObject.has("Materials")) {
                JSONArray materials = jsonObject.getJSONArray("Materials");
                for (int i = 0; i < materials.length(); i++) {
                    JSONObject material = materials.getJSONObject(i);
                    if ("Tritium".equalsIgnoreCase(material.getString("Name"))) {
                        tritiumProportion = material.getDouble("Proportion");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing materials: " + e.getMessage());
        }

        final double finalTritiumProportion = tritiumProportion;
        SwingUtilities.invokeLater(() -> {
            asteroidProspectedLabel.setText(String.format("Asteroid Prospected: Yes (Tritium: %.1f%%)", finalTritiumProportion));

            // Set color based on tritium content
            if (finalTritiumProportion < TRITIUM_THRESHOLD_LOW) {
                asteroidProspectedLabel.setBackground(COLOR_LOW);
                asteroidProspectedLabel.setForeground(Color.WHITE);
            } else if (finalTritiumProportion < TRITIUM_THRESHOLD_MEDIUM) {
                System.out.println("High tritium found: " + finalTritiumProportion);
                asteroidProspectedLabel.setBackground(COLOR_MEDIUM);
                asteroidProspectedLabel.setForeground(Color.BLACK);
            } else if (finalTritiumProportion < TRITIUM_THRESHOLD_HIGH) {
                asteroidProspectedLabel.setBackground(COLOR_HIGH);
                asteroidProspectedLabel.setForeground(Color.BLACK);
            } else {
                asteroidProspectedLabel.setBackground(COLOR_VERY_HIGH);
                asteroidProspectedLabel.setForeground(Color.WHITE);
            }

            System.out.println("Tritium content: " + finalTritiumProportion + "%");
        });
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(EliteLogMonitor::new);
    }
}