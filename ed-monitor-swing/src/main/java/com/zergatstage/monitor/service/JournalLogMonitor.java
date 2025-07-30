package com.zergatstage.monitor.service;

import com.zergatstage.monitor.handlers.LogEventHandler;
import com.zergatstage.monitor.service.readers.AppendFileReadStrategy;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.json.JSONTokener;


import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The JournalLogMonitor class is responsible for monitoring the Elite Dangerous log directory
 * for new Journal's logs entries. It is controlled now
 * via UI interactions (start/stop). This makes the service lifecycle fully managed by user.
 * Scheduling is provided by MonitorController.
 */
@Log4j2
public class JournalLogMonitor {
    public interface Dispatcher {
        void dispatch(JSONObject eventJson, LogEventHandler handler);
    }
    private final GenericFileMonitor fileMonitor;
    private final Dispatcher dispatcher;
    /**
     * Constructs a LogMonitor.
     *
     * @param logDirectoryPath   the path to the folder containing your Journal.log
     * @param eventHandlers      the list of event handlers to dispatch incoming events to
     */
    public JournalLogMonitor(Path logDirectoryPath,
                             Map<String, LogEventHandler> eventHandlers, Dispatcher dispatcher) {
        this.dispatcher = dispatcher;

        // Process lastest log file in the directory
        List<Path> logFiles = findLogFiles(logDirectoryPath);
        Path latestLogFile = findLatestLogFile(logFiles);
        // Use AppendFileReadStrategy so we only process new lines added to the file
        this.fileMonitor = new GenericFileMonitor(
                latestLogFile,
                new AppendFileReadStrategy(),
                // onUpdate callback: process each JSON line
                newContent ->
                    processAppendedLines(newContent, eventHandlers)
        );
    }

    /**
     * Enables the log monitoring process.
     * This method can be called from the UI to start processing log entries.
     */
    public void startMonitoring() {
        this.fileMonitor.start();
        log.info("Log monitoring started at: {} ",Instant.now());
    }

    /**
     * Disables the log monitoring process.
     * This method can be called from the UI to stop processing log entries.
     */
    public void stopMonitoring() {
       this.fileMonitor.stop();
        log.info("Log monitoring ({}) stopped at: {}",this, Instant.now());
    }
    /**
     * Finds all log files in the specified directory.
     *
     * @param directory the log directory.
     * @return a list of log file paths.
     */
    private List<Path> findLogFiles(Path directory){
        List<Path> logFiles = new ArrayList<>();
        if (Files.exists(directory) && Files.isDirectory(directory)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.log")) {//*.log
                stream.forEach(logFiles::add);
            } catch (IOException e) {
                log.error("Error reading log directory: {}", e.getMessage());
                System.err.println("Error reading log directory: " + e.getMessage());
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
     * Parses a chunk of appended text into individual JSON lines,
     * then finds and invokes the appropriate handler(s) for each event.
     *
     * @param newContent     the raw text newly appended to Journal.log
     * @param handlers       the registered list of LogEventHandler implementations
     */
    private void processAppendedLines(String newContent, Map<String, LogEventHandler> handlers) {
        // Split into lines on both Unix and Windows line endings
        String[] lines = newContent.split("\\r?\\n");
        for (String line : lines) {
            if (line.isBlank()) {
                continue;  // skip empty lines
            }
            processLine(handlers, line);
        }
    }

    private void processLine(Map<String, LogEventHandler> handlers, String line) {
        try {
            JSONObject json = new JSONObject(new JSONTokener(line));
            String eventType = json.getString("event");
            // Dispatch to all handlers that claim they can handle this event
            LogEventHandler logEventHandler = handlers.get(eventType);
            if (logEventHandler != null) {
                dispatcher.dispatch(json, logEventHandler);
            }

        } catch (Exception e) {
            System.err.println("Error parsing or handling line: " + line + " – " + e.getMessage());
            e.printStackTrace();
        }
    }
}
