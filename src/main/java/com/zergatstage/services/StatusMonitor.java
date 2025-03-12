package com.zergatstage.services;

import com.zergatstage.services.readers.RewriteFileReadStrategy;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * The StatusMonitor now delegates file monitoring to a GenericFileMonitor,
 * making it extendable to other file types that are rewritten.
 */
@Log4j2
public class StatusMonitor {

     private final GenericFileMonitor fileMonitor;

    /**
     * Constructs a StatusMonitor.
     *
     * @param statusFilePath the path to the Status.json file.
     * @param sessionSummaryFilePath the path to the session summary file.
     */
    public StatusMonitor(String statusFilePath, String sessionSummaryFilePath) {
        Path statusFile = Paths.get(statusFilePath);
        Path sessionSummaryFile = Paths.get(sessionSummaryFilePath);
        // Here we use the RewriteFileReadStrategy because the file is rewritten.
        fileMonitor = new GenericFileMonitor(statusFile, new RewriteFileReadStrategy(),
                newStatusContent -> {
            try {
                // Validate and parse the JSON to ensure we have a valid state update
                JSONObject jsonState = new JSONObject(new JSONTokener(newStatusContent));
                // Append the new state to the session summary
                appendStateToSessionSummary(jsonState, sessionSummaryFile);
            } catch (Exception e) {
                System.err.println("Error processing status file content: " + e.getMessage());
            }
        });
    }

    /**
     * Starts monitoring the status file.
     */
    public void start() {
        fileMonitor.start();
    }

    /**
     * Stops monitoring the status file.
     */
    public void stop() {
        fileMonitor.stop();
    }

    /**
     * Appends the given JSON state to the session summary file.
     *
     * @param jsonState the JSON object representing the current status.
     * @param sessionSummaryFilePath the path to the session summary file.
     */
    private void appendStateToSessionSummary(JSONObject jsonState, Path sessionSummaryFilePath) {
        try (BufferedWriter writer = Files.newBufferedWriter(sessionSummaryFilePath,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(jsonState.toString());
            writer.newLine();
            log.debug("Status updated: {}", jsonState);
        } catch (IOException e) {
            log.error("Error writing to session summary file: {}", e.getMessage());
        }
    }
}