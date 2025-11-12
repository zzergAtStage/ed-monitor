package com.zergatstage.monitor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zergatstage.monitor.service.managers.MarketDataUpdateEvent;
import com.zergatstage.monitor.service.readers.FileReadStrategy;

class MarketDataIOServiceTest {

    private String originalUserHome;

    @BeforeEach
    void setUp() {
        originalUserHome = System.getProperty("user.home");
    }

    @AfterEach
    void tearDown() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void publishesEventsOnCreateAndModify() throws Exception {
        Path tempHome = Files.createTempDirectory("edm-home-");
        System.setProperty("user.home", tempHome.toString());

        // Prepare expected log directory structure
        Path logDir = tempHome
                .resolve("Saved Games")
                .resolve("Frontier Developments")
                .resolve("Elite Dangerous");
        Files.createDirectories(logDir);
        Path marketFile = logDir.resolve("Market.json");

        // Capture events in a thread-safe list
        Vector<MarketDataUpdateEvent> events = new Vector<>();
        Consumer<MarketDataUpdateEvent> collector = events::add;

        // Simple FileReadStrategy that returns entire file on first read or when content changes
        FileReadStrategy strategy = new FileReadStrategy() {
            @Override
            public ReadResult readChanges(Path file, Object previousState) throws IOException {
                String current = Files.exists(file) ? Files.readString(file) : "";
                String prev = previousState instanceof String ? (String) previousState : null;
                if (prev == null || !prev.equals(current)) {
                    return new ReadResult(current, current);
                } else {
                    return new ReadResult("", prev);
                }
            }
        };

        MarketDataIOService io = new MarketDataIOService(strategy, collector);
        io.start();

        try {
            // Create the file (should trigger one event)
            Files.writeString(marketFile, "{\"event\":\"Market\"}", StandardCharsets.UTF_8);

            waitUntil(() -> events.size() >= 1, Duration.ofSeconds(5));
            assertTrue(events.size() >= 1, "Expected at least one event on file create");

            // Modify the file (should trigger another event)
            Files.writeString(marketFile, "{\"event\":\"Market-Updated\"}", StandardCharsets.UTF_8);

            waitUntil(() -> events.size() >= 2, Duration.ofSeconds(5));
            assertTrue(events.size() >= 2, "Expected a second event on file modify");
        } finally {
            io.stop();
        }
    }

    @Test
    void stopsCleanly() {
        // Set up temporary home so construction doesn't fail
        Path tempHome;
        try {
            tempHome = Files.createTempDirectory("edm-home-");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.setProperty("user.home", tempHome.toString());

        FileReadStrategy strategy = (file, previousState) -> new FileReadStrategy.ReadResult("", previousState);
        Vector<MarketDataUpdateEvent> events = new Vector<>();
        MarketDataIOService io = new MarketDataIOService(strategy, events::add);
        io.start();
        io.stop();
        // If no exception is thrown, consider it passed
        assertEquals(0, events.size());
    }

    private static void waitUntil(SupplierLike condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeout);
        while (System.nanoTime() < deadline) {
            if (condition.get()) return;
            Thread.sleep(50);
        }
    }

    @FunctionalInterface
    interface SupplierLike { boolean get(); }
}

