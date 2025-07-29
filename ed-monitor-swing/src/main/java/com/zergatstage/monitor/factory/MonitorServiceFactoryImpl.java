package com.zergatstage.monitor.factory;

import com.zergatstage.monitor.handlers.HandlerConfiguration;
import com.zergatstage.monitor.handlers.LogEventHandler;
import com.zergatstage.monitor.service.JournalLogMonitor;
import com.zergatstage.monitor.service.StatusMonitor;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executor;

public class MonitorServiceFactoryImpl implements MonitorServiceFactory {
    private final Executor executor;

    // constructor‐injection of your scheduler
    public MonitorServiceFactoryImpl(Executor executor) {
        this.executor = executor;
    }

    @Override
    public JournalLogMonitor createLogService(Path logDirectory) {
        Map<String, LogEventHandler> handlers = HandlerConfiguration.getLogEventHandlers();
        return new JournalLogMonitor(logDirectory, handlers, executor);
    }

    @Override
    public StatusMonitor createStatusService(Path logDirectory) {
        return new StatusMonitor(
                logDirectory.resolve("Status.json"),
                logDirectory.resolve("Journal.log")
        );
    }
}
