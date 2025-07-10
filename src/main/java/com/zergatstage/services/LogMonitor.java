package com.zergatstage.services;

import com.zergatstage.services.handlers.LogEventHandler;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * The LogMonitor class is responsible for monitoring the Elite Dangerous log directory
 * for new log entries. It now leverages Spring Boot’s scheduling capabilities and is controlled
 * via UI interactions (start/stop). This makes the service lifecycle fully managed by Spring Boot,
 * while still allowing UI-based control.
 */
@Log4j2
@Service
public class LogMonitor {

    private final Path logDirectory;
    private Path lastProcessedFile;
    private long lastProcessedFilePosition;
    private Instant lastProcessedTimestamp;
    private final List<LogEventHandler> eventHandlers;

    // Flag to indicate whether monitoring is enabled (controlled from the UI)
    private volatile boolean monitoring;

    /**
     * Constructs a LogMonitor.
     *
     * @param logDirectory  the directory where log files are stored.
     * @param eventHandlers a list of handlers to process log events.
     */
    public LogMonitor(Path logDirectory, List<LogEventHandler> eventHandlers) {
        this.logDirectory = logDirectory;
        this.eventHandlers = new ArrayList<>(eventHandlers);
        this.lastProcessedFilePosition = 0;
        this.lastProcessedTimestamp = Instant.now().minus(5 , ChronoUnit.MINUTES);
        this.monitoring = false; // Monitoring is initially off.
    }

    /**
     * Enables the log monitoring process.
     * This method can be called from the UI to start processing log entries.
     */
    public void startMonitoring() {
        this.monitoring = true;
        log.info("Log monitoring started at: {} ",Instant.now());
    }

    /**
     * Disables the log monitoring process.
     * This method can be called from the UI to stop processing log entries.
     */
    public void stopMonitoring() {
        this.monitoring = false;
        log.info("Log monitoring stopped at: {}", Instant.now());
    }

    /**
     * Periodically checks the log directory for new log entries.
     * This method is scheduled by Spring Boot to run every 1 second.
     * It only processes log files if monitoring is enabled.
     */
    @Scheduled(fixedDelay = 1000)
    public void scheduledCheckLogs() {
        if (!monitoring) {
            return; // Do nothing if monitoring is not enabled.
        }
        checkLogs();
    }

    /**
     * Checks the log directory for new log files and processes new log entries.
     */
    private void checkLogs() {
        try {
            List<Path> logFiles = findLogFiles(logDirectory);
            if (logFiles.isEmpty()) {
                log.warn("No log files found in: {}" ,logDirectory);
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
            log.error("Error checking logs: {}", e.getMessage());
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
            log.error("Error processing log file: {} \n {}",e.getMessage(),e.getStackTrace() );
        }
    }

    /**
     * Processes a log line by parsing it as JSON and delegating to event handlers.
     *
     * @param line the log file line.
     */
    private void processLogLine(String line) {
        try {
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
            log.warn("Error processing log line: {}", e.getMessage());
        }
    }

    /**
     * Processes an individual JSON object by checking its timestamp and delegating to appropriate event handlers.
     *
     * @param jsonObject the JSON object representing a log event.
     */
    private void processJsonObject(JSONObject jsonObject) {
        try {
            String timestampStr = jsonObject.getString("timestamp");
            Instant timestamp = OffsetDateTime.parse(timestampStr).toInstant();
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
