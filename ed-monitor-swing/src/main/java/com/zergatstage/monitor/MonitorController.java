package com.zergatstage.monitor;

import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.handlers.ExitHandler;
import com.zergatstage.monitor.service.JournalLogMonitor;
import com.zergatstage.monitor.service.MarketDataIOService;
import com.zergatstage.monitor.service.StatusMonitor;

import java.awt.event.ActionEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MonitorController {
    private final JournalLogMonitor logService;
    private final StatusMonitor statusService;
    //private final MarketDataIOService marketDataIOService;
    private final ScheduledExecutorService scheduler;
    private final ExitHandler exitHandler;

    public MonitorController(JournalLogMonitor logService,
                             StatusMonitor statusService,
                             ExitHandler exitHandler) {

        this.logService = logService;
        this.statusService = statusService;
        this.exitHandler = exitHandler;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public void startAll() {
        logService.startMonitoring();
        scheduler.scheduleAtFixedRate(logService::scheduledCheckLogs, 0, 1, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopAll));
    }

    public void stopAll() {
        scheduler.shutdownNow();
        logService.stopMonitoring();
        statusService.stop();
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

    public void startStatusMonitor(ActionEvent e) {
        statusService.start();
    }

    public void stopStatusMonitor(ActionEvent e) {
        statusService.stop();
    }

    public void toggleStatusMonitor(ActionEvent e) {
        // delegate: if running, stop; else start
        statusService.stop();
        statusService.start();
    }
}