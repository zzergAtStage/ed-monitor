package com.zergatstage.services;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import lombok.extern.java.Log;

/**
 * The MarketDataIOService monitors the Market.json file for changes.
 * When a change is detected, it notifies the registered listener with the new JSON data.
 */
@Log
public class MarketDataIOService {

    private final Path marketFile;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledTask;
    private String lastContent;
    private final MarketDataListener listener;

    /**
     * Listener interface to be implemented by clients that want to receive market data updates.
     */
    public interface MarketDataListener {
        /**
         * Called when new market data is available.
         *
         * @param jsonData the updated JSON data from the market file.
         */
        void onMarketDataUpdate(String jsonData);
    }

    /**
     * Constructs a MarketDataIOService.
     *
     * @param listener the listener to notify when new market data is available.
     */
    public MarketDataIOService( MarketDataListener listener) {
        this.marketFile  = Paths.get(System.getProperty("user.home"), "Saved Games", "Frontier Developments", "Elite Dangerous", "Market.json");
        this.listener = listener;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.lastContent = "";
    }

    /**
     * Starts monitoring the Market.json file.
     */
    public void start() {

        if (scheduledTask == null || scheduledTask.isCancelled() || scheduledTask.isDone()) {
            scheduledTask = executor.scheduleAtFixedRate(this::checkMarketFile, 0, 1, TimeUnit.SECONDS);
            log.info("Market Data IO Service started.");
        }
    }

    /**
     * Stops monitoring the Market.json file.
     */
    public void stop() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(true);
            log.info("Market Data IO Service stopped.");
        }
    }

    /**
     * Reads the market file and checks for changes.
     * If the content has changed, the new data is passed to the listener.
     */
    private void checkMarketFile() {
        try {
            String content = new String(Files.readAllBytes(marketFile));
            if (!content.equals(lastContent)) {
                lastContent = content;
                listener.onMarketDataUpdate(content);
            }
        } catch (IOException e) {
            log.severe("Error reading market file: " + e.getMessage());
        }
    }
}
