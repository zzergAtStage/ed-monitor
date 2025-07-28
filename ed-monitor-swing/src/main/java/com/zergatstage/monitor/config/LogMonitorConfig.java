package com.zergatstage.monitor.config;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration class for the LogMonitor and related beans.
 * <p>
 * This class externalizes the creation of the log directory and LogMonitor,
 * thereby removing the need to directly call constructors in legacy Swing code.
 * The beans are now managed by Spring Boot and can be injected where needed.
 * </p>
 */
@Slf4j
public class LogMonitorConfig {

    /**
     * Creates the bean representing the log directory.
     *
     * @return the log directory path.
     */
    public static Path logDirectory() {
        return Paths.get(System.getProperty("user.home"),
                "Saved Games", "Frontier Developments", "Elite Dangerous");
    }
}
