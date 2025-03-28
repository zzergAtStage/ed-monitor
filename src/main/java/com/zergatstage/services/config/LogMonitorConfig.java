package com.zergatstage.services.config;

import com.zergatstage.services.LogMonitor;
import com.zergatstage.services.handlers.LogEventHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Configuration class for the LogMonitor and related beans.
 * <p>
 * This class externalizes the creation of the log directory and LogMonitor,
 * thereby removing the need to directly call constructors in legacy Swing code.
 * The beans are now managed by Spring Boot and can be injected where needed.
 * </p>
 */
@Configuration
public class LogMonitorConfig {

    /**
     * Creates the bean representing the log directory.
     *
     * @return the log directory path.
     */
    @Bean
    public Path logDirectory() {
        return Paths.get(System.getProperty("user.home"),
                "Saved Games", "Frontier Developments", "Elite Dangerous");
    }

    /**
     * Creates the LogMonitor bean.
     * <p>
     * The LogMonitor bean is constructed using the log directory and a list of event handlers.
     * The event handlers are assumed to be managed by Spring (either through component scanning or explicit bean definitions).
     * </p>
     *
     * @param logDirectory  the directory where log files are stored.
     * @param eventHandlers the list of event handlers to process log events.
     * @return a LogMonitor bean.
     */
    @Bean
    public LogMonitor logMonitor(Path logDirectory, List<LogEventHandler> eventHandlers) {
        return new LogMonitor(logDirectory, eventHandlers);
    }
}
