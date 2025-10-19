package com.zergatstage.monitor.service;

import com.zergatstage.monitor.service.managers.MarketDataUpdateEvent;

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
    private final Path logDirectory;
    private final Consumer<MarketDataUpdateEvent> eventConsumer;
    private final ScheduledExecutorService scheduler;

    private WatchService watchService;
    private Thread watchThread;

    private enum State { IDLE, WAITING, ACTIVE }
    private volatile State state = State.IDLE;

    /**
     * Constructs the service with a custom file‐reading strategy.
     *
     * @param fileReadStrategy strategy to detect/return file changes
     * @param eventConsumer    consumer to handle and publish update events
     */
    public MarketDataIOService(FileReadStrategy fileReadStrategy,
            Consumer<MarketDataUpdateEvent> eventConsumer) {
        this.fileReadStrategy = fileReadStrategy;
        this.logDirectory = LogMonitorConfig.logDirectory();
        this.marketFile = this.logDirectory.resolve("Market.json");
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
     * Starts file monitoring using WatchService with lazy activation.
     * Falls back to a low-frequency safety poll to catch missed events.
     */
    public void start() {
        startWatcher();
        // Safety net: very low-frequency poll (once per 60s) in case of missed events
        scheduler.scheduleWithFixedDelay(() -> {
            if (state == State.ACTIVE) {
                checkMarketFile();
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Stops the periodic file monitoring.
     */
    public void stop() {
        try {
            if (watchThread != null) {
                watchThread.interrupt();
            }
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException ignored) { }
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

    private void startWatcher() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            if (Files.exists(marketFile)) {
                registerDir(StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                state = State.ACTIVE;
                log.info("MarketDataIOService: ACTIVE (file exists)");
            } else if (Files.exists(logDirectory)) {
                registerDir(StandardWatchEventKinds.ENTRY_CREATE);
                state = State.WAITING;
                log.info("MarketDataIOService: WAITING (dir exists, file missing)");
            } else {
                registerParent(StandardWatchEventKinds.ENTRY_CREATE);
                state = State.IDLE;
                log.info("MarketDataIOService: IDLE (dir missing)");
            }
        } catch (IOException e) {
            log.warn("WatchService setup failed: {}. Falling back to polling.", e.getMessage());
            // Fallback to periodic polling if watcher cannot start
            scheduler.scheduleWithFixedDelay(this::checkMarketFile, 0, 5, TimeUnit.SECONDS);
            return;
        }

        watchThread = new Thread(this::watchLoop, "market-json-watch");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private void watchLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.take();
                Path dir = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path name = ev.context();
                    Path child = dir.resolve(name);

                    handleWatchEvent(dir, child, kind);
                }
                boolean valid = key.reset();
                if (!valid) {
                    log.warn("WatchKey no longer valid for {}", dir);
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException ignored) {
        } catch (Exception e) {
            log.warn("Watch loop error: {}", e.getMessage());
        }
    }

    private void handleWatchEvent(Path dir, Path child, WatchEvent.Kind<?> kind) {
        try {
            switch (state) {
                case IDLE -> {
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE && child.equals(logDirectory)) {
                        // Directory created; now watch for file creation
                        registerDir(StandardWatchEventKinds.ENTRY_CREATE);
                        state = State.WAITING;
                        log.info("MarketDataIOService: WAITING (dir created)");
                    }
                }
                case WAITING -> {
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE && child.equals(marketFile)) {
                        // Switch to active watching of file changes
                        registerDir(StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                        state = State.ACTIVE;
                        log.info("MarketDataIOService: ACTIVE (file created)");
                        checkMarketFile();
                    }
                }
                case ACTIVE -> {
                    if (child.equals(marketFile)) {
                        if (kind == StandardWatchEventKinds.ENTRY_MODIFY || kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            checkMarketFile();
                        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            // File removed; go back to waiting
                            registerDir(StandardWatchEventKinds.ENTRY_CREATE);
                            state = State.WAITING;
                            log.info("MarketDataIOService: WAITING (file deleted)");
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to (re)register watch: {}", e.getMessage());
        }
    }

    private void registerParent(WatchEvent.Kind<?>... kinds) throws IOException {
        Path parent = logDirectory.getParent();
        if (parent == null) parent = logDirectory;
        parent.register(watchService, kinds);
    }

    private void registerDir(WatchEvent.Kind<?>... kinds) throws IOException {
        logDirectory.register(watchService, kinds);
    }
}
