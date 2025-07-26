package com.zergatstage;

import com.zergatstage.monitor.MonitorController;
import com.zergatstage.monitor.MonitorView;
import com.zergatstage.monitor.config.LogMonitorConfig;
import com.zergatstage.monitor.factory.MonitorServiceFactoryImpl;
import com.zergatstage.monitor.handlers.DefaultExitHandler;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Path;

/**
 * @author S.Brusentsov
 * date on 12/01/2025
 */


@Log4j2
public class ClientApp {

    public static void main(String[] args) {
        MonitorServiceFactoryImpl monitorServiceFactory = new MonitorServiceFactoryImpl();
        Path logFilePath = LogMonitorConfig.logDirectory();
        MonitorView view = new MonitorView(
                new MonitorController(
                        monitorServiceFactory.createLogService(logFilePath),
                        monitorServiceFactory.createStatusService(logFilePath), // statusService wired in view
                        new DefaultExitHandler()
                ), logFilePath
        );
    }
}