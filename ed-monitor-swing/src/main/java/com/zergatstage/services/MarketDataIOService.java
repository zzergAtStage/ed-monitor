package com.zergatstage.services;

import com.zergatstage.domain.makret.MarketDataUpdateEvent;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.zergatstage.services.config.LogMonitorConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that monitors the Market.json file for changes.
 * When a change is detected, it publishes a MarketDataUpdateEvent.
 */
@Slf4j
public class MarketDataIOService {


    private final Path marketFile;
    private String lastContent;
    private final Consumer<MarketDataUpdateEvent> eventConsumer;
    private final ScheduledExecutorService scheduler;

    /**
     * Constructs the MarketDataIOService.
     * The file path is constructed based on the user's home directory.
     *
     * @param eventConsumer a consumer to handle market update events
     */
    public MarketDataIOService(Consumer<MarketDataUpdateEvent> eventConsumer) {
        Path logDirectory = LogMonitorConfig.logDirectory();
        this.marketFile = logDirectory.resolve("Market.json");
        this.eventConsumer = eventConsumer;
        this.lastContent = "";
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
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
            String content = new String(Files.readAllBytes(marketFile));
            if (!content.equals(lastContent)) {
                lastContent = content;
                eventConsumer.accept(new MarketDataUpdateEvent(this, content));
                log.info("Published market data update event.");
            }
        } catch (IOException e) {
            log.error("Error reading market file: {}", e.getMessage(), e);
        }
    }
}
