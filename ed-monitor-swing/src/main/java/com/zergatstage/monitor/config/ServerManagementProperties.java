package com.zergatstage.monitor.config;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Loads process/health configuration for the optional Spring Boot backend.
 * <p>
 * Defaults are provided via {@code /server-management.properties} on the classpath.
 * Users can override any property by supplying {@code -Dserver.management.config=/custom/file.properties}
 * (or setting {@code SERVER_MANAGEMENT_CONFIG}). The override file is merged on top
 * of the classpath defaults.
 */
@Slf4j
public final class ServerManagementProperties {
    private static final String CLASSPATH_RESOURCE = "/server-management.properties";
    private static final String SYSTEM_OVERRIDE = "server.management.config";
    private static final String ENV_OVERRIDE = "SERVER_MANAGEMENT_CONFIG";

    private final String javaCommand;
    private final Path jarPath;
    private final Path workingDirectory;
    private final List<String> additionalArgs;
    private final List<String> commandOverride;
    private final URI healthUri;
    private final Duration healthTimeout;
    private final Duration startupTimeout;
    private final Duration shutdownTimeout;

    private ServerManagementProperties(String javaCommand,
                                       Path jarPath,
                                       Path workingDirectory,
                                       List<String> additionalArgs,
                                       List<String> commandOverride,
                                       URI healthUri,
                                       Duration healthTimeout,
                                       Duration startupTimeout,
                                       Duration shutdownTimeout) {
        this.javaCommand = javaCommand;
        this.jarPath = jarPath;
        this.workingDirectory = workingDirectory;
        this.additionalArgs = List.copyOf(additionalArgs);
        this.commandOverride = List.copyOf(commandOverride);
        this.healthUri = healthUri;
        this.healthTimeout = healthTimeout;
        this.startupTimeout = startupTimeout;
        this.shutdownTimeout = shutdownTimeout;
    }

    public static ServerManagementProperties load() {
        Properties props = readProperties();
        String javaCommand = props.getProperty("server.javaCommand", "java").trim();
        List<String> commandOverride = parseArgs(props.getProperty("server.command"));
        Path jarPath = resolvePath(props.getProperty("server.jarPath"))
                .orElse(null);
        if (commandOverride.isEmpty() && jarPath == null) {
            throw new IllegalStateException("server.jarPath must be set when server.command is empty");
        }
        Path workingDir = resolvePath(props.getProperty("server.workingDir"))
                .orElseGet(() -> jarPath != null ? jarPath.getParent() : null);
        URI healthUri = URI.create(props.getProperty("server.healthUrl", "http://localhost:8080/actuator/health").trim());
        Duration healthTimeout = parseMillis(props.getProperty("server.healthTimeoutMillis"), 2_000);
        Duration startupTimeout = parseSeconds(props.getProperty("server.startupTimeoutSeconds"), 45);
        Duration shutdownTimeout = parseSeconds(props.getProperty("server.shutdownTimeoutSeconds"), 15);
        List<String> args = parseArgs(props.getProperty("server.args"));
        return new ServerManagementProperties(javaCommand, jarPath, workingDir, args, commandOverride,
                healthUri, healthTimeout, startupTimeout, shutdownTimeout);
    }

    public List<String> launchCommand() {
        if (!commandOverride.isEmpty()) {
            return commandOverride;
        }
        if (jarPath == null) {
            throw new IllegalStateException("server.jarPath is not configured");
        }
        List<String> command = new ArrayList<>();
        command.add(javaCommand);
        command.add("-jar");
        command.add(jarPath.toString());
        command.addAll(additionalArgs);
        return List.copyOf(command);
    }

    public String javaCommand() {
        return javaCommand;
    }

    public List<String> additionalArgs() {
        return additionalArgs;
    }

    public Optional<Path> workingDirectory() {
        return Optional.ofNullable(workingDirectory);
    }

    public URI healthUri() {
        return healthUri;
    }

    public Duration healthTimeout() {
        return healthTimeout;
    }

    public Duration startupTimeout() {
        return startupTimeout;
    }

    public Duration shutdownTimeout() {
        return shutdownTimeout;
    }

    public Optional<Path> jarPath() {
        return Optional.ofNullable(jarPath);
    }

    public boolean hasCommandOverride() {
        return !commandOverride.isEmpty();
    }

    public List<String> commandOverride() {
        return commandOverride;
    }

    private static Properties readProperties() {
        Properties props = new Properties();
        try (InputStream in = ServerManagementProperties.class.getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + CLASSPATH_RESOURCE + " on classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + CLASSPATH_RESOURCE, e);
        }

        resolveOverridePath().ifPresent(path -> {
            if (!Files.exists(path)) {
                log.warn("Server management override file {} does not exist; ignoring", path);
                return;
            }
            try (InputStream override = Files.newInputStream(path)) {
                props.load(override);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load override properties from " + path, e);
            }
        });
        return props;
    }

    private static Optional<Path> resolveOverridePath() {
        String sysProp = System.getProperty(SYSTEM_OVERRIDE);
        String env = System.getenv(ENV_OVERRIDE);
        String raw = (sysProp != null && !sysProp.isBlank()) ? sysProp : env;
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(raw.trim()).toAbsolutePath().normalize());
    }

    private static Optional<Path> resolvePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(raw.trim()).toAbsolutePath().normalize());
    }

    private static Duration parseSeconds(String value, int defaultValue) {
        long seconds = parseLong(value, defaultValue);
        return Duration.ofSeconds(Math.max(seconds, 1));
    }

    private static Duration parseMillis(String value, int defaultValue) {
        long millis = parseLong(value, defaultValue);
        return Duration.ofMillis(Math.max(millis, 100));
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static List<String> parseArgs(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;
        for (char c : raw.toCharArray()) {
            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inQuotes = true;
                quoteChar = c;
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return List.copyOf(tokens);
    }
}
