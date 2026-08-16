package com.zergatstage.monitor.service.server;

import com.zergatstage.monitor.config.ServerManagementProperties;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Coordinates starting/stopping the optional Spring Boot backend without blocking the EDT.
 */
@Slf4j
public class ServerLifecycleService implements AutoCloseable {
    /**
     * Value of {@code spring.application.name} / {@code info.app.name} that ed-monitor-server
     * reports on its {@code /actuator/info} endpoint. Used to make sure a port that answers
     * health checks is actually our backend and not some unrelated Spring Boot app sharing the
     * same default port.
     */
    private static final String EXPECTED_APP_NAME = "ed-monitor-server";

    private final ServerManagementProperties properties;
    private final ExecutorService executor;
    private final HttpClient httpClient;
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final AtomicReference<Process> processRef = new AtomicReference<>();
    private final String sessionIdentifier = "ed-monitor-session-" + UUID.randomUUID();
    private volatile URI effectiveHealthUri;
    private volatile Consumer<String> onServerStarted;

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
        this.effectiveHealthUri = properties.healthUri();
    }

    public void setOnServerStarted(Consumer<String> listener) {
        this.onServerStarted = listener;
    }

    public String getEffectiveBaseUrl() {
        URI uri = effectiveHealthUri;
        return uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
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

    /**
     * Synchronous variant intended for application shutdown paths.
     */
    public ServerCommandResult stopBackendAndWait() {
        try {
            long millis = properties.shutdownTimeout().plusSeconds(5).toMillis();
            return stopBackend().get(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ServerCommandResult.failure("Shutdown interrupted");
        } catch (TimeoutException e) {
            return ServerCommandResult.failure("Timed out waiting for backend to stop");
        } catch (Exception e) {
            return ServerCommandResult.failure("Failed to stop backend: " + e.getMessage());
        }
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
                if (isOurBackend()) {
                    return ServerCommandResult.success("Backend already running (detected existing instance)");
                }
                log.warn("Something is already answering health checks on port {} but it isn't ed-monitor-server "
                        + "(failed /actuator/info identity check); starting our own instance on a different port instead",
                    portOf(effectiveHealthUri));
            }
            int preferredPort = portOf(effectiveHealthUri);
            int port = findFreePort(preferredPort);
            if (port != preferredPort) {
                log.info("Port {} is busy; starting backend on port {} instead", preferredPort, port);
                effectiveHealthUri = withPort(effectiveHealthUri, port);
            }
            List<String> command = buildLaunchCommand();
            command.add("--server.port=" + port);
            ProcessBuilder builder = new ProcessBuilder(command);
            properties.workingDirectory().ifPresent(path -> builder.directory(path.toFile()));
            builder.redirectErrorStream(true);
            builder.inheritIO();
            Process process = builder.start();
            processRef.set(process);
            process.onExit().thenAccept(p -> {
                processRef.compareAndSet(process, null);
                log.info("ed-monitor-server (pid {}) exited with code {}", p.pid(), safeExitValue(p));
            });
            log.info("Started ed-monitor-server (pid {}) on port {} with session {}", process.pid(), port, sessionIdentifier);
        } catch (IOException e) {
            return ServerCommandResult.failure("Failed to start backend: " + e.getMessage());
        } finally {
            lifecycleLock.unlock();
        }
        return waitForStartupHealth();
    }

    private List<String> buildLaunchCommand() throws IOException {
        if (properties.hasCommandOverride()) {
            return new ArrayList<>(properties.launchCommand());
        }
        Path jarPath = properties.jarPath()
                .orElseThrow(() -> new IllegalStateException("server.jarPath is not configured"));
        if (!Files.isRegularFile(jarPath)) {
            throw new IOException("Backend jar not found: " + jarPath);
        }
        String javaExecutable = resolveJavaExecutable();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.add("-Dcom.zergatstage.monitor.sessionId=" + sessionIdentifier);
        command.add("-jar");
        command.add(jarPath.toString());
        command.addAll(properties.additionalArgs());
        return command;
    }

    private String resolveJavaExecutable() {
        if (properties.hasCommandOverride()) {
            return properties.launchCommand().getFirst();
        }
        String configured = properties.javaCommand();
        if (!isWindows()) {
            return configured;
        }
        try {
            Path javaPath = Paths.get(configured);
            if (!Files.isRegularFile(javaPath)) {
                return configured;
            }
            Path renamed = javaPath.getParent().resolve("ed-monitor-server.exe");
            if (!Files.isRegularFile(renamed) || Files.size(renamed) != Files.size(javaPath)) {
                Files.copy(javaPath, renamed, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            return renamed.toString();
        } catch (Exception e) {
            log.warn("Failed to prepare renamed Java executable, using default: {}", e.getMessage());
            return configured;
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    private ServerCommandResult waitForStartupHealth() {
        Instant deadline = Instant.now().plus(properties.startupTimeout());
        while (Instant.now().isBefore(deadline)) {
            if (!isProcessAlive()) {
                return ServerCommandResult.failure("Backend process exited during startup");
            }
            ServerHealthState state = fetchHealthState();
            if (state == ServerHealthState.ONLINE) {
                notifyServerStarted();
                return ServerCommandResult.success("Backend is online (port " + portOf(effectiveHealthUri) + ")");
            }
            if (state == ServerHealthState.UNHEALTHY) {
                notifyServerStarted();
                return ServerCommandResult.success("Backend started but reported unhealthy status (port " + portOf(effectiveHealthUri) + ")");
            }
            sleep(Duration.ofMillis(750));
        }
        return ServerCommandResult.failure("Backend health check timed out after " + properties.startupTimeout().toSeconds() + "s");
    }

    private ServerCommandResult stopInternal() {
        lifecycleLock.lock();
        try {
            List<ProcessHandle> targets = findBackendProcesses();
            if (targets.isEmpty()) {
                processRef.set(null);
                return ServerCommandResult.success("Backend already stopped");
            }

            List<Long> failed = new ArrayList<>();
            int stopped = 0;
            for (ProcessHandle handle : targets) {
                if (stopHandle(handle, properties.shutdownTimeout())) {
                    stopped++;
                    clearTrackedProcess(handle);
                } else {
                    failed.add(handle.pid());
                }
            }
            if (!failed.isEmpty()) {
                return ServerCommandResult.failure("Failed to stop backend pids " + failed);
            }
            return ServerCommandResult.success("Backend stopped (" + stopped + " instance(s))");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ServerCommandResult.failure("Shutdown interrupted");
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void clearTrackedProcess(ProcessHandle handle) {
        Process tracked = processRef.get();
        if (tracked != null && tracked.pid() == handle.pid()) {
            processRef.compareAndSet(tracked, null);
        }
    }

    private List<ProcessHandle> findBackendProcesses() {
        Set<Long> seenPids = new HashSet<>();
        List<ProcessHandle> matches = new ArrayList<>();

        Process tracked = processRef.get();
        if (tracked != null) {
            if (tracked.isAlive()) {
                ProcessHandle handle = tracked.toHandle();
                matches.add(handle);
                seenPids.add(handle.pid());
            } else {
                processRef.compareAndSet(tracked, null);
            }
        }

        List<String> tokens = buildMatchTokens();
        try {
            ProcessHandle.allProcesses()
                    .filter(ProcessHandle::isAlive)
                    .filter(ph -> ph.pid() != ProcessHandle.current().pid())
                    .filter(ph -> !seenPids.contains(ph.pid()))
                    .filter(ph -> isBackendProcess(ph, tokens))
                    .forEach(ph -> {
                        matches.add(ph);
                        seenPids.add(ph.pid());
                    });
        } catch (SecurityException e) {
            log.warn("Unable to enumerate processes for backend shutdown: {}", e.getMessage());
        }
        return matches;
    }

    private boolean isBackendProcess(ProcessHandle handle, List<String> tokens) {
        if (tokens.isEmpty()) {
            return false;
        }
        ProcessHandle.Info info = handle.info();
        StringBuilder buffer = new StringBuilder();
        info.command().ifPresent(cmd -> buffer.append(cmd).append(' '));
        info.commandLine().ifPresent(cmd -> buffer.append(cmd).append(' '));
        info.arguments().ifPresent(args -> {
            for (String arg : args) {
                buffer.append(arg).append(' ');
            }
        });
        String haystack = buffer.toString().toLowerCase();
        if (haystack.isBlank()) {
            return false;
        }
        for (String token : tokens) {
            if (!token.isBlank() && haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildMatchTokens() {
        List<String> tokens = new ArrayList<>();
        properties.jarPath().ifPresent(path -> {
            String normalized = path.toAbsolutePath().normalize().toString().toLowerCase();
            tokens.add(normalized);
            tokens.add(path.getFileName().toString().toLowerCase());
        });
        properties.workingDirectory().ifPresent(path ->
                tokens.add(path.toAbsolutePath().normalize().toString().toLowerCase()));
        tokens.add(sessionIdentifier.toLowerCase());
        if (properties.hasCommandOverride()) {
            for (String token : properties.commandOverride()) {
                if (token != null && !token.isBlank()) {
                    String lower = token.toLowerCase();
                    tokens.add(lower);
                    int sep = lower.lastIndexOf(System.getProperty("file.separator"));
                    if (sep >= 0 && sep < lower.length() - 1) {
                        tokens.add(lower.substring(sep + 1));
                    }
                }
            }
        }
        tokens.add("ed-monitor-server");
        return tokens;
    }

    private boolean stopHandle(ProcessHandle handle, Duration timeout) throws InterruptedException {
        if (!handle.isAlive()) {
            return true;
        }
        log.info("Stopping ed-monitor-server pid {}", handle.pid());
        handle.destroy();
        if (awaitTermination(handle, timeout)) {
            return true;
        }
        log.warn("Backend pid {} did not stop gracefully, forcing termination", handle.pid());
        handle.destroyForcibly();
        return awaitTermination(handle, Duration.ofSeconds(5));
    }

    private boolean awaitTermination(ProcessHandle handle, Duration timeout) throws InterruptedException {
        try {
            handle.onExit().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            return !handle.isAlive();
        } catch (Exception e) {
            log.warn("Error waiting for pid {} to exit: {}", handle.pid(), e.getMessage());
            return !handle.isAlive();
        }
    }

    /**
     * Confirms that whatever is answering health checks on the configured port is actually
     * ed-monitor-server, by checking its {@code /actuator/info} response for our app name.
     * Without this, any unrelated Spring Boot app exposing Actuator health on the same port
     * (e.g. another local project defaulting to 8080) would be mistaken for an already-running
     * backend, and every subsequent request from ed-monitor-swing would silently go to it instead.
     */
    private boolean isOurBackend() {
        URI infoUri = infoUri(effectiveHealthUri);
        HttpRequest request = HttpRequest.newBuilder(infoUri)
                .timeout(properties.healthTimeout())
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            JSONObject body = new JSONObject(response.body());
            String appName = body.optJSONObject("app") != null
                    ? body.getJSONObject("app").optString("name", "")
                    : "";
            return EXPECTED_APP_NAME.equalsIgnoreCase(appName);
        } catch (IOException | InterruptedException | JSONException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.debug("Identity check against {} failed: {}", infoUri, e.getMessage());
            return false;
        }
    }

    private static URI infoUri(URI healthUri) {
        String path = healthUri.getPath();
        String infoPath = path.endsWith("/health") ? path.substring(0, path.length() - "health".length()) + "info" : path;
        try {
            return new URI(healthUri.getScheme(), healthUri.getUserInfo(), healthUri.getHost(), healthUri.getPort(),
                    infoPath, healthUri.getQuery(), healthUri.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot derive info URI from " + healthUri, e);
        }
    }

    private ServerHealthState fetchHealthState() {
        HttpRequest request = HttpRequest.newBuilder(effectiveHealthUri)
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
            // 4xx means something else is on this port (e.g. another web app returning 403)
            return ServerHealthState.DOWN;
        } catch (IOException e) {
            // Connection refused/reset means nothing is listening on the port yet (still
            // booting) or it crashed - either way the server hasn't actually answered, so this
            // is DOWN, not UNHEALTHY. UNHEALTHY is reserved for a genuine HTTP 503 response.
            // waitForStartupHealth() relies on this: treating a refused connection as UNHEALTHY
            // would make it fire the "server started" callback on the very first poll attempt,
            // often within milliseconds of spawning the process, well before Spring Boot has
            // bound its port.
            log.debug("Health check failed: {}", e.getMessage());
            return ServerHealthState.DOWN;
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

    private void notifyServerStarted() {
        Consumer<String> listener = onServerStarted;
        if (listener != null) {
            listener.accept(getEffectiveBaseUrl());
        }
    }

    private static int portOf(URI uri) {
        int p = uri.getPort();
        return p == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : p;
    }

    private static URI withPort(URI uri, int port) {
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), port,
                    uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot reconstruct URI with port " + port, e);
        }
    }

    private static int findFreePort(int preferred) throws IOException {
        try (ServerSocket ss = new ServerSocket(preferred)) {
            return preferred;
        } catch (IOException ignored) {
            // preferred port is busy, let OS pick one
        }
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
