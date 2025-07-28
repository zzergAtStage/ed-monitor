package com.zergatstage.monitor.factory;

import com.zergatstage.monitor.service.JournalLogMonitor;
import com.zergatstage.monitor.service.StatusMonitor;

import java.nio.file.Path;

public interface MonitorServiceFactory {
    JournalLogMonitor createLogService(Path logDirectory);
    StatusMonitor createStatusService(Path logDirectory);
}
