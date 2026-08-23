package com.zergatstage.monitor;

import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.zergatstage.ClientApp;
import com.zergatstage.monitor.config.ServerManagementProperties;
import com.zergatstage.monitor.factory.DefaultManagerFactory;
import com.zergatstage.monitor.handlers.ExitHandler;
import com.zergatstage.monitor.service.CargoDataIOService;
import com.zergatstage.monitor.service.JournalLogMonitor;
import com.zergatstage.monitor.service.MarketDataIOService;
import com.zergatstage.monitor.service.StatusMonitor;
import com.zergatstage.monitor.service.managers.CargoInventoryManager;
import com.zergatstage.monitor.service.managers.MarketDataUpdateEvent;
import com.zergatstage.monitor.service.managers.MarketDataUpdateService;
import com.zergatstage.monitor.service.server.ServerCommandResult;
import com.zergatstage.monitor.service.server.ServerLifecycleService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitorController {
    private final JournalLogMonitor logService;
    private final StatusMonitor statusService;
    private final MarketDataIOService marketDataIOService;
    private final MarketDataUpdateService marketDataUpdateService;
    private final CargoDataIOService cargoDataIOService;
    @Getter
    private final ScheduledExecutorService scheduler;
    private final ExitHandler exitHandler;
    private final ServerLifecycleService serverLifecycleService;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

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
        this.cargoDataIOService = new CargoDataIOService(CargoInventoryManager.getInstance());
        initCommodityRegisrtyOverMarketDataIOService();
        serverLifecycleService = new ServerLifecycleService(ServerManagementProperties.load());
        serverLifecycleService.setOnServerStarted(url -> {
            DefaultManagerFactory.getInstance().updateBaseUrl(url);
            marketDataUpdateService.refreshFromServer();
        });
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
        cargoDataIOService.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopAll, "ed-monitor-shutdown"));
    }

    public void stopAll() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdownNow();
        logService.stopMonitoring();
        statusService.stop();
        marketDataIOService.stop();
        cargoDataIOService.stop();
        stopBackendOnShutdown();
        serverLifecycleService.close();
    }

    public void onExit(ActionEvent e) {
        stopAll();
        exitHandler.exit(0);
    }

    /**
     * Relaunches the application with the same command line the current JVM was started with,
     * then gracefully shuts down this instance. Used after a setting (e.g. UI scale) that only
     * takes effect on a fresh launch.
     *
     * @return true if a new process was successfully started (this process will exit shortly
     *         after); false if the relaunch could not be performed, in which case this instance
     *         keeps running unchanged.
     */
    public boolean restartApplication() {
        if (!relaunchProcess()) {
            return false;
        }
        stopAll();
        exitHandler.exit(0);
        return true;
    }

    private boolean relaunchProcess() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        Optional<String> command = info.command();
        if (command.isEmpty()) {
            log.warn("Cannot relaunch: current process command is unavailable.");
            return false;
        }

        List<String> commandLine = new ArrayList<>();
        commandLine.add(preferWindowlessJavaLauncher(command.get()));
        info.arguments().ifPresent(args -> commandLine.addAll(Arrays.asList(args)));

        try {
            new ProcessBuilder(commandLine).inheritIO().start();
            log.info("Relaunched application: {}", commandLine);
            return true;
        } catch (IOException e) {
            log.warn("Failed to relaunch application: {}", e.getMessage());
            return false;
        }
    }

    /**
     * On Windows, prefer javaw.exe over java.exe for the relaunch so no console window flashes.
     */
    private static String preferWindowlessJavaLauncher(String javaExecutable) {
        if (!javaExecutable.endsWith("java.exe")) {
            return javaExecutable;
        }
        Path javaw = Path.of(javaExecutable).resolveSibling("javaw.exe");
        return Files.exists(javaw) ? javaw.toString() : javaExecutable;
    }

    public void toggleLogMonitor(ActionEvent e) {
        logService.stopMonitoring();
        logService.startMonitoring();
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

    private void stopBackendOnShutdown() {
        ServerCommandResult result = serverLifecycleService.stopBackendAndWait();
        if (result.success()) {
            log.info("Backend stop on shutdown: {}", result.message());
        } else {
            log.warn("Backend stop on shutdown: {}", result.message());
        }
    }
}
