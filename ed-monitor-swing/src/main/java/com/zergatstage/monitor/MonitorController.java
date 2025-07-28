package com.zergatstage.monitor;

import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.service.CommodityRegistry;
import com.zergatstage.monitor.service.managers.MarketDataParser;
import com.zergatstage.monitor.service.managers.MarketDataUpdateEvent;
import com.zergatstage.monitor.handlers.ExitHandler;
import com.zergatstage.monitor.service.JournalLogMonitor;
import com.zergatstage.monitor.service.MarketDataIOService;
import com.zergatstage.monitor.service.StatusMonitor;
import com.zergatstage.monitor.service.managers.MarketDataUpdateService;

import java.awt.event.ActionEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public class MonitorController {
    private final JournalLogMonitor logService;
    private final StatusMonitor statusService;
    private final MarketDataIOService marketDataIOService;
    private final ScheduledExecutorService scheduler;
    private final ExitHandler exitHandler;

    public MonitorController(JournalLogMonitor logService,
                             StatusMonitor statusService,
                             ExitHandler exitHandler) {

        this.logService = logService;
        this.statusService = statusService;
        this.exitHandler = exitHandler;
        this.scheduler = Executors.newScheduledThreadPool(1);

        Consumer<MarketDataUpdateEvent> marketConsumer = this::onMarketDataUpdate;
        this.marketDataIOService = new MarketDataIOService(marketConsumer);
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
        MarketDataUpdateService marketDataUpdateService = new MarketDataUpdateService(
                DefaultManagerFactory.getInstance().getCommodityRegistry(),
                DefaultManagerFactory.getInstance().getMarketDataParser());
        marketDataUpdateService.onMarketDataUpdate(event);


    }
}