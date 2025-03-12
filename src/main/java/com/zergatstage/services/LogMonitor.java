package com.zergatstage.services;

import com.zergatstage.services.handlers.LogEventHandler;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONArray;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * The LogMonitor class is responsible for continuously monitoring the
 * Elite Dangerous log directory, including the commander log file,
 * for new log entries and delegating event processing to registered
 * LogEventHandlers. This modified version handles multiple events
 * per log line, supporting mixed event types.
 */
@Log4j2
public class LogMonitor {

    private final Path logDirectory;
    private Path lastProcessedFile;
    private long lastProcessedFilePosition;
    private Instant lastProcessedTimestamp;
    private final ScheduledExecutorService executor;
    private final List<LogEventHandler> eventHandlers;

    /**
     * Constructs a LogMonitor.
     *
     * @param logDirectory the directory where log files are stored.
     * @param eventHandlers a list of handlers to process log events.
     */
    public LogMonitor(Path logDirectory, List<LogEventHandler> eventHandlers) {
        this.logDirectory = logDirectory;
        this.eventHandlers = new ArrayList<>(eventHandlers);
        this.lastProcessedFilePosition = 0;
        this.lastProcessedTimestamp = Instant.now();
        this.executor = Executors.newScheduledThreadPool(1);
    }

    /**
     * Starts the log monitoring service.
     * It schedules the periodic check for new log entries.
     */
    public void start() {
        executor.scheduleAtFixedRate(this::checkLogs, 0, 1, TimeUnit.SECONDS);
        System.out.println("Log monitoring started at: " + Instant.now());
    }

    /**
     * Checks the log directory for new log files and processes new log entries.
     */
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
                    // New log file detected, reset reading position
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

    /**
     * Finds all log files in the specified directory.
     *
     * @param directory the log directory.
     * @return a list of log file paths.
     * @throws IOException if an I/O error occurs.
     */
    private List<Path> findLogFiles(Path directory) throws IOException {
        List<Path> logFiles = new ArrayList<>();
        if (Files.exists(directory) && Files.isDirectory(directory)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.log")) {
                stream.forEach(logFiles::add);
            }
        }
        return logFiles;
    }

    /**
     * Determines the latest log file based on the last modified time.
     *
     * @param logFiles the list of log files.
     * @return the latest log file, or null if none found.
     */
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

    /**
     * Processes new entries in the specified log file.
     *
     * @param logFile the log file to process.
     */
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

    /**
     * Processes a log line. This method has been enhanced to handle multiple events
     * per line. It attempts to parse the line as either a JSONObject or a JSONArray,
     * and then processes each JSON object found.
     *
     * @param line the log file line.
     */
    private void processLogLine(String line) {
        try {
            // Parse the next JSON value from the line.
            Object jsonValue = new JSONTokener(line).nextValue();
            if (jsonValue instanceof JSONObject) {
                // Process a single JSON object.
                processJsonObject((JSONObject) jsonValue);
            } else if (jsonValue instanceof JSONArray jsonArray) {
                // Process each JSON object in the array.
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    processJsonObject(jsonObject);
                }
            }
        } catch (Exception e) {

        }
    }

    /**
     * Processes an individual JSON object by checking its timestamp and delegating to the appropriate event handlers.
     *
     * @param jsonObject the JSON object representing a log event.
     */
    private void processJsonObject(JSONObject jsonObject) {
        try {
            // Extract the timestamp from the JSON event.
            String timestampStr = jsonObject.getString("timestamp");
            Instant timestamp = OffsetDateTime.parse(timestampStr).toInstant();

            // Process only new events based on timestamp
            if (timestamp.isAfter(lastProcessedTimestamp)) {
                String eventType = jsonObject.getString("event");
                for (LogEventHandler handler : eventHandlers) {
                    if (handler.canHandle(eventType)) {
                        handler.handleEvent(jsonObject);
                    }
                }
                lastProcessedTimestamp = timestamp;
            }
        } catch (Exception e) {
            log.warn("Error processing JSON: {}", e.getMessage());
        }
    }
}
