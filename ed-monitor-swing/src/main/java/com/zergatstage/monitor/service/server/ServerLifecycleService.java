package com.zergatstage.monitor.service.server;

import com.zergatstage.monitor.config.ServerManagementProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Coordinates starting/stopping the optional Spring Boot backend without blocking the EDT.
 */
@Slf4j
public class ServerLifecycleService implements AutoCloseable {
    private final ServerManagementProperties properties;
    private final ExecutorService executor;
    private final HttpClient httpClient;
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final AtomicReference<Process> processRef = new AtomicReference<>();

    public ServerLifecycleService(ServerManagementProperties properties) {
        this(properties, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ed-monitor-server-lifecycle");
            t.setDaemon(true);
            return t;
        }));
    }

    ServerLifecycleService(ServerManagementProperties properties, ExecutorService executor) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.healthTimeout())
                .build();
    }

    public CompletableFuture<ServerCommandResult> startBackend() {
        return CompletableFuture.supplyAsync(this::startInternal, executor)
                .exceptionally(ex -> {
                    log.error("Backend start failed", ex);
                    return ServerCommandResult.failure(ex.getMessage());
                });
    }

    public CompletableFuture<ServerCommandResult> stopBackend() {
        return CompletableFuture.supplyAsync(this::stopInternal, executor)
                .exceptionally(ex -> {
                    log.error("Backend stop failed", ex);
                    return ServerCommandResult.failure(ex.getMessage());
                });
    }

    public CompletableFuture<ServerCommandResult> restartBackend() {
        return stopBackend().thenCompose(stopResult -> {
            if (stopResult.success() || notRunningMessage(stopResult)) {
                return startBackend();
            }
            return CompletableFuture.completedFuture(stopResult);
        });
    }

    public CompletableFuture<ServerHealthState> checkHealth() {
        return CompletableFuture.supplyAsync(this::fetchHealthState, executor);
    }

    private boolean notRunningMessage(ServerCommandResult result) {
        return result.message() != null && result.message().toLowerCase().contains("stopped");
    }

    private ServerCommandResult startInternal() {
        lifecycleLock.lock();
        try {
            Process running = processRef.get();
            if (running != null && running.isAlive()) {
                return ServerCommandResult.success("Backend already running (pid %d)".formatted(running.pid()));
            }
            ServerHealthState externalState = fetchHealthState();
            if (externalState == ServerHealthState.ONLINE || externalState == ServerHealthState.UNHEALTHY) {
                return ServerCommandResult.success("Backend already running (detected existing instance)");
            }
            if (!properties.hasCommandOverride()) {
                Path jarPath = properties.jarPath()
                        .orElseThrow(() -> new IllegalStateException("server.jarPath is not configured"));
                if (!Files.isRegularFile(jarPath)) {
                    return ServerCommandResult.failure("Backend jar not found: " + jarPath);
                }
            }
            ProcessBuilder builder = new ProcessBuilder(properties.launchCommand());
            properties.workingDirectory().ifPresent(path -> builder.directory(path.toFile()));
            builder.redirectErrorStream(true);
            builder.inheritIO();
            Process process = builder.start();
            processRef.set(process);
            process.onExit().thenAccept(p -> {
                processRef.compareAndSet(process, null);
                log.info("ed-monitor-server (pid {}) exited with code {}", p.pid(), safeExitValue(p));
            });
            log.info("Started ed-monitor-server (pid {})", process.pid());
        } catch (IOException e) {
            return ServerCommandResult.failure("Failed to start backend: " + e.getMessage());
        } finally {
            lifecycleLock.unlock();
        }
        return waitForStartupHealth();
    }

    private ServerCommandResult waitForStartupHealth() {
        Instant deadline = Instant.now().plus(properties.startupTimeout());
        while (Instant.now().isBefore(deadline)) {
            if (!isProcessAlive()) {
                return ServerCommandResult.failure("Backend process exited during startup");
            }
            ServerHealthState state = fetchHealthState();
            if (state == ServerHealthState.ONLINE) {
                return ServerCommandResult.success("Backend is online");
            }
            if (state == ServerHealthState.UNHEALTHY) {
                return ServerCommandResult.success("Backend started but reported unhealthy status");
            }
            sleep(Duration.ofMillis(750));
        }
        return ServerCommandResult.failure("Backend health check timed out after " + properties.startupTimeout().toSeconds() + "s");
    }

    private ServerCommandResult stopInternal() {
        lifecycleLock.lock();
        try {
            Process running = processRef.get();
            if (running == null || !running.isAlive()) {
                processRef.set(null);
                return ServerCommandResult.success("Backend already stopped");
            }
            running.destroy();
            if (!running.waitFor(properties.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Backend pid {} did not stop gracefully, forcing termination", running.pid());
                running.destroyForcibly();
                running.waitFor(5, TimeUnit.SECONDS);
            }
            processRef.compareAndSet(running, null);
            return ServerCommandResult.success("Backend stopped");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ServerCommandResult.failure("Shutdown interrupted");
        } finally {
            lifecycleLock.unlock();
        }
    }

    private ServerHealthState fetchHealthState() {
        HttpRequest request = HttpRequest.newBuilder(properties.healthUri())
                .timeout(properties.healthTimeout())
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return ServerHealthState.ONLINE;
            }
            if (status == 503) {
                return ServerHealthState.UNHEALTHY;
            }
            if (status >= 500) {
                return ServerHealthState.ERROR;
            }
            return ServerHealthState.UNHEALTHY;
        } catch (IOException e) {
            log.debug("Health check failed: {}", e.getMessage());
            return isProcessAlive() ? ServerHealthState.UNHEALTHY : ServerHealthState.DOWN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ServerHealthState.ERROR;
        }
    }

    private boolean isProcessAlive() {
        Process process = processRef.get();
        return process != null && process.isAlive();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int safeExitValue(Process process) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
