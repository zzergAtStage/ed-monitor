package com.zergatstage;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.zergatstage.monitor.MonitorController;
import com.zergatstage.monitor.MonitorView;
import com.zergatstage.monitor.config.LogMonitorConfig;
import com.zergatstage.monitor.factory.MonitorServiceFactory;
import com.zergatstage.monitor.factory.MonitorServiceFactoryImpl;
import com.zergatstage.monitor.handlers.DefaultExitHandler;
import com.zergatstage.monitor.service.JournalLogMonitor;
import com.zergatstage.monitor.service.StatusMonitor;
import lombok.extern.log4j.Log4j2;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * @author S.Brusentsov
 * date on 12/01/2025
 */


@Log4j2
public class ClientApp {

    public static void main(String[] args) {
        System.out.println("Starting Log Monitor Application…");

        log.info("Debug enabled? {}", log.isDebugEnabled());


        // 2) hand it to the factory
        MonitorServiceFactory factory =
                new MonitorServiceFactoryImpl();

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
