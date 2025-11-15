package com.zergatstage.monitor;

import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.zergatstage.ClientApp;
import com.zergatstage.monitor.config.ServerManagementProperties;
import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.handlers.ExitHandler;
import com.zergatstage.monitor.service.JournalLogMonitor;
import com.zergatstage.monitor.service.MarketDataIOService;
import com.zergatstage.monitor.service.StatusMonitor;
import com.zergatstage.monitor.service.managers.MarketDataUpdateEvent;
import com.zergatstage.monitor.service.managers.MarketDataUpdateService;
import com.zergatstage.monitor.service.server.ServerLifecycleService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitorController {
    private final JournalLogMonitor logService;
    private final StatusMonitor statusService;
    private final MarketDataIOService marketDataIOService;
    private final MarketDataUpdateService marketDataUpdateService;
    @Getter
    private final ScheduledExecutorService scheduler;
    private final ExitHandler exitHandler;
    private final ServerLifecycleService serverLifecycleService;

    public MonitorController(JournalLogMonitor logService,
                             StatusMonitor statusService,
                             ExitHandler exitHandler) {

        this.logService = logService;
        this.statusService = statusService;
        this.exitHandler = exitHandler;
        this.scheduler = Executors.newScheduledThreadPool(4);

        Consumer<MarketDataUpdateEvent> marketConsumer = this::onMarketDataUpdate;
        this.marketDataIOService = new MarketDataIOService(marketConsumer);
        marketDataUpdateService = DefaultManagerFactory.getInstance().getMarketDataUpdateService();
        initCommodityRegisrtyOverMarketDataIOService();
        serverLifecycleService = new ServerLifecycleService(ServerManagementProperties.load());
    }

    private void initCommodityRegisrtyOverMarketDataIOService() {
        try (InputStream in = ClientApp.class
                .getClassLoader()
                .getResourceAsStream("Market-default.json")) {
            if (in == null) {
                throw new IllegalStateException("Could not find Market.json on classpath");
            }
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(in));
            marketDataUpdateService.onMarketDataUpdate(new MarketDataUpdateEvent(this,  reader.lines().collect(Collectors.joining())));
        } catch (IOException e) {
            log.error("Error reading Market.json: {}", e.getMessage());
        }
    }

    public void startAll() {
        logService.startMonitoring();
        marketDataIOService.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopAll));
    }

    public void stopAll() {
        scheduler.shutdownNow();
        logService.stopMonitoring();
        statusService.stop();
        marketDataIOService.stop();
        serverLifecycleService.close();
    }

    public void onExit(ActionEvent e) {
        stopAll();
        exitHandler.exit(0);
    }

    public void toggleLogMonitor(ActionEvent e) {
        // delegate: if running, stop; else start and schedule
        logService.stopMonitoring();
        startAll();
    }

    public void toggleStatusMonitor(ActionEvent e) {
        // delegate: if running, stop; else start
        statusService.stop();
        statusService.start();
    }
    /**
     * Callback that receives each new MarketDataUpdateEvent.
     * Here you can parse the JSON payload, update the UI, etc.
     *
     * @param event the event carrying the fresh market-data content
     */
    private void onMarketDataUpdate(MarketDataUpdateEvent event) {
        // TODO: replace with factory


        //marketDataUpdateService.onMarketDataUpdate(event);
        scheduler.schedule(() -> marketDataUpdateService.onMarketDataUpdate(event)
                , 0, TimeUnit.SECONDS);

    }

    public ServerLifecycleService getServerLifecycleService() {
        return serverLifecycleService;
    }
}
