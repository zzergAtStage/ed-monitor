package com.zergatstage.monitor.factory;

import com.zergatstage.monitor.handlers.HandlerConfiguration;
import com.zergatstage.monitor.handlers.LogEventHandler;
import com.zergatstage.monitor.service.JournalLogMonitor;
import com.zergatstage.monitor.service.StatusMonitor;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
@Log4j2
public class MonitorServiceFactoryImpl implements MonitorServiceFactory {
    private final ExecutorService cargoExecutor;
    private final ExecutorService generalExecutor;
    int treadCount = 0;
    public MonitorServiceFactoryImpl() {
        // Single‐threaded executor to process *all* cargo events in strict order:
        this.cargoExecutor   = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cargo‐processor");
            t.setDaemon(true);
            return t;
        });
        // A pool for everything else:
        this.generalExecutor = Executors.newFixedThreadPool(

                Math.max(Runtime.getRuntime().availableProcessors() - 10, 4), // leave some threads for UI
                r -> {
                    Thread t = new Thread(r, "general‐worker-" + treadCount++);
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    @Override
    public JournalLogMonitor createLogService(Path logDirectory) {
        Map<String, LogEventHandler> handlers = HandlerConfiguration.getLogEventHandlers();
        return new JournalLogMonitor(
                logDirectory,
                // inject a dispatcher that picks the right executor per event:

                handlers,
                (eventJson, handler) -> {
                    if (handler.isCargoRelated()) {
                        cargoExecutor.execute(() -> {
                            log.debug("Dispatching cargo event: {}", handler.getEventType());
                            handler.handleEvent(eventJson);}
                        );
                    } else {
                        generalExecutor.execute(() -> handler.handleEvent(eventJson));
                    }
                }
        );
    }

    @Override
    public StatusMonitor createStatusService(Path logDirectory) {
        return new StatusMonitor(
                logDirectory.resolve("Status.json"),
                logDirectory.resolve("Journal.log")
        );
    }

    public void shutdown() throws InterruptedException {
        cargoExecutor.shutdown();
        generalExecutor.shutdown();
        cargoExecutor.awaitTermination(5, TimeUnit.SECONDS);
        generalExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
