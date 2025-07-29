package com.zergatstage;

import com.zergatstage.monitor.MonitorController;
import com.zergatstage.monitor.MonitorView;
import com.zergatstage.monitor.config.InitCommodities;
import com.zergatstage.monitor.config.LogMonitorConfig;
import com.zergatstage.monitor.factory.MonitorServiceFactory;
import com.zergatstage.monitor.factory.MonitorServiceFactoryImpl;
import com.zergatstage.monitor.handlers.DefaultExitHandler;
import com.zergatstage.monitor.service.JournalLogMonitor;
import com.zergatstage.monitor.service.StatusMonitor;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author S.Brusentsov
 * date on 12/01/2025
 */


@Log4j2
public class ClientApp {
    public static void main(String[] args) {
        System.out.println("Starting Log Monitor Application…");

        // 1) build your scheduler once
        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(4);

        // 2) hand it to the factory
        MonitorServiceFactory factory =
                new MonitorServiceFactoryImpl(scheduler);

        Path logDir = LogMonitorConfig.logDirectory();

        // 3) create services (they’ll share that scheduler)
        JournalLogMonitor logService =
                factory.createLogService(logDir);
        StatusMonitor statusService =
                factory.createStatusService(logDir);

        // 4) pass services into your controller
        MonitorController controller =
                new MonitorController(logService, statusService, new DefaultExitHandler());

        // 5) build the UI
        new MonitorView(controller, logDir);
    }
}
