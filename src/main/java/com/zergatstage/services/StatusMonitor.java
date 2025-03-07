package com.zergatstage.services;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.*;

/**
 * The StatusMonitor class monitors the game's Status.json file.
 * It reads the file at regular intervals and, when a change is detected,
 * appends the new state to a session summary file (e.g., Journal.log) to create a running history.
 *
 * The service supports start/stop operations by canceling or scheduling its task without shutting down the executor.
 */
public class StatusMonitor {

    private final Path statusFile;              // Path to the Status.json file
    private final Path sessionSummaryFile;      // Path to the session summary file (e.g., Journal.log)
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledTask;   // Reference to the scheduled polling task
    private String lastStateContent;

    /**
     * Constructs a StatusMonitor.
     *
     * @param statusFile         the path to the Status.json file.
     * @param sessionSummaryFile the path to the session summary file.
     */
    public StatusMonitor(Path statusFile, Path sessionSummaryFile) {
        this.statusFile = statusFile;
        this.sessionSummaryFile = sessionSummaryFile;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.lastStateContent = "";
    }

    /**
     * Starts the status monitoring service.
     * It polls the Status.json file every second.
     */
    public void start() {
        if (scheduledTask == null || scheduledTask.isCancelled() || scheduledTask.isDone()) {
            scheduledTask = executor.scheduleAtFixedRate(this::checkStatusFile, 0, 1, TimeUnit.SECONDS);
        System.out.println("Status monitoring started at: " + Instant.now());
        } else {
            System.out.println("Status monitoring is already running.");
        }
    }

    /**
     * Stops the status monitoring service.
     * The scheduled task is canceled while keeping the executor active for potential restarts.
     */
    public void stop() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(true);
            System.out.println("Status monitoring stopped at: " + Instant.now());
        } else {
            System.out.println("Status monitoring is not running.");
        }
    }

    /**
     * Checks the Status.json file for updates.
     * If the content differs from the last recorded state, the new state is appended to the session summary file.
     */
    private void checkStatusFile() {
        try {
            // Read the current content of the Status.json file
            String content = new String(Files.readAllBytes(statusFile));
            if (!content.equals(lastStateContent)) {
                // File content has changed: update the last state and append it to the summary file
                lastStateContent = content;
                // Validate and parse the JSON to ensure we have a valid state update
                JSONObject jsonState = new JSONObject(new JSONTokener(content));
                appendStateToSessionSummary(jsonState);
            }
        } catch (IOException e) {
            System.err.println("Error reading status file: " + e.getMessage());
        } catch (Exception e) {
            // Log any parsing or processing errors without stopping the monitor
            System.err.println("Error processing status file content: " + e.getMessage());
        }
    }

    /**
     * Appends the given JSON state to the session summary file.
     * Each state is written on a new line.
     *
     * @param jsonState the JSON object representing the current status.
     */
    private void appendStateToSessionSummary(JSONObject jsonState) {
        try (BufferedWriter writer = Files.newBufferedWriter(sessionSummaryFile,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(jsonState.toString());
            writer.newLine();
            System.out.println("Status updated: " + jsonState.toString());
        } catch (IOException e) {
            System.err.println("Error writing to session summary file: " + e.getMessage());
        }
    }
}
