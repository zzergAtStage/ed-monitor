package com.zergatstage.monitor.service;

import com.zergatstage.domain.makret.MarketDataUpdateEvent;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.zergatstage.monitor.config.LogMonitorConfig;
import com.zergatstage.monitor.service.readers.FileReadStrategy;
import com.zergatstage.monitor.service.readers.FileReadStrategy.ReadResult;
import com.zergatstage.monitor.service.readers.RewriteFileReadStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that monitors the Market.json file for changes.
 * When a change is detected, it publishes a MarketDataUpdateEvent.
 */
@Slf4j
public class MarketDataIOService {

    private Object previousState;
    private final FileReadStrategy fileReadStrategy;
    private final Path marketFile;
    private final Consumer<MarketDataUpdateEvent> eventConsumer;
    private final ScheduledExecutorService scheduler;

    /**
     * Constructs the service with a custom file‐reading strategy.
     *
     * @param fileReadStrategy strategy to detect/return file changes
     * @param eventConsumer    consumer to handle and publish update events
     */
    public MarketDataIOService(FileReadStrategy fileReadStrategy,
            Consumer<MarketDataUpdateEvent> eventConsumer) {
        this.fileReadStrategy = fileReadStrategy;
        Path logDirectory = LogMonitorConfig.logDirectory();
        this.marketFile = logDirectory.resolve("Market.json");
        this.eventConsumer = eventConsumer;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }
    /**
     * Convenience constructor that uses the default
     * {@link com.zergatstage.monitor.service.readers.RewriteFileReadStrategy}.
     *
     * @param eventConsumer consumer to handle and publish update events
     */
    public MarketDataIOService(Consumer<MarketDataUpdateEvent> eventConsumer) {
        this(new RewriteFileReadStrategy(),
                eventConsumer);
    }

    /**
     * Starts the periodic file monitoring.
     */
    public void start() {
        scheduler.scheduleWithFixedDelay(this::checkMarketFile, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Stops the periodic file monitoring.
     */
    public void stop() {
        scheduler.shutdown();
    }

    /**
     * Checks the market file for changes.
     */
    private void checkMarketFile() {
        try {
            ReadResult result = fileReadStrategy.readChanges(marketFile, previousState);
            String newContent = result.getNewContent();
            Object newState   = result.getNewState();
            // Only proceed if the strategy detected new content
            if (!newContent.isEmpty()) {
                previousState = newState;  // update our “cursor”
                eventConsumer.accept(new MarketDataUpdateEvent(this, newContent));
                log.info("Published market data update event.");
            }
        } catch (IOException e) {
            log.error("Error reading market file: {}", e.getMessage(), e);
        }
    }
}
