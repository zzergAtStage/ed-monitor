package com.zergatstage.monitor.factory;

import com.zergatstage.monitor.handlers.HandlerConfiguration;
import com.zergatstage.monitor.handlers.LogEventHandler;
import com.zergatstage.monitor.service.JournalLogMonitor;
import com.zergatstage.monitor.service.StatusMonitor;

import java.nio.file.Path;
import java.util.List;

public class MonitorServiceFactoryImpl implements MonitorServiceFactory {
    @Override
    public JournalLogMonitor createLogService(Path logDirectory) {
        List<LogEventHandler> handlers = HandlerConfiguration.getLogEventHandlers();
        return new JournalLogMonitor(logDirectory, handlers);
    }

    @Override
    public StatusMonitor createStatusService(Path logDirectory) {
        return new StatusMonitor(
                logDirectory.resolve("Status.json"),
                logDirectory.resolve("Journal.log")
        );
    }
}
