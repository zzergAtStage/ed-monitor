package com.zergatstage.services;

import com.zergatstage.services.readers.FileReadStrategy;
import com.zergatstage.services.readers.FileReadStrategy.ReadResult;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * A generic file monitor that polls a file for updates using a specified file reading strategy.
 */
@Log4j2
public class GenericFileMonitor {

    private final Path file;
    private final FileReadStrategy readStrategy;
    private final Consumer<String> onUpdate;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledTask;
    private Object previousState;

    /**
     * Constructs a GenericFileMonitor.
     *
     * @param file the file to monitor.
     * @param readStrategy the strategy for reading file updates.
     * @param onUpdate a callback function that processes new content.
     */
    public GenericFileMonitor(Path file, FileReadStrategy readStrategy, Consumer<String> onUpdate) {
        this.file = file;
        this.readStrategy = readStrategy;
        this.onUpdate = onUpdate;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.previousState = null;
    }

    /**
     * Starts the file monitoring service.
     */
    public void start() {
        if (scheduledTask == null || scheduledTask.isCancelled() || scheduledTask.isDone()) {
            scheduledTask = executor.scheduleAtFixedRate(this::checkFile, 0, 1, TimeUnit.SECONDS);
            log.info("File monitoring started for {}\t at: {}", file, Instant.now());
        }
    }

    /**
     * Stops the file monitoring service.
     */
    public void stop() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(true);
            log.info("File monitoring stopped for {} at: {}", file, Instant.now());
        }
    }

    /**
     * Checks the file for updates using the provided file read strategy.
     */
    private void checkFile() {
        try {
            ReadResult result = readStrategy.readChanges(file, previousState);
            if (!result.newContent().isEmpty()) {
                // Process the new content using the provided callback
                onUpdate.accept(result.newContent());
                // Update the previous state for the next polling cycle
                previousState = result.newState();
            }
        } catch (IOException e) {
            log.error("Error monitoring file {}: {}", file, e.getMessage());
        }
    }
}