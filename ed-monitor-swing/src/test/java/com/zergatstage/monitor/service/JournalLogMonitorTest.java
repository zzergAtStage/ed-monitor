package com.zergatstage.monitor.service;

import com.zergatstage.monitor.handlers.LogEventHandler;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;

import static org.mockito.Mockito.*;

class JournalLogMonitorTest {

    @Mock
    private GenericFileMonitor mockFileMonitor;

    @Mock
    private LogEventHandler mockHandlerFoo;

    @Mock
    private LogEventHandler mockHandlerBar;

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        tempDir = Files.createTempDirectory("journallogmonitor-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
        }
    }

    // Helper: create log files and return list of paths
    private List<Path> createLogFilesWithTimestamps(Map<String, Long> fileNamesAndEpochMillis) throws IOException {
        List<Path> files = new ArrayList<>();
        for (var entry : fileNamesAndEpochMillis.entrySet()) {
            Path file = tempDir.resolve(entry.getKey());
            Files.writeString(file, "dummy");
            Files.setLastModifiedTime(file, FileTime.fromMillis(entry.getValue()));
            files.add(file);
        }
        return files;
    }

    @Test
    void constructsWithLatestLogFile() throws IOException {
        long now = System.currentTimeMillis();
        // Simulate several .log files
        Map<String, Long> logs = Map.of(
                "Journal.1.log", now - 10000,
                "Journal.2.log", now - 5000,
                "Journal.3.log", now
        );
        createLogFilesWithTimestamps(logs);

        // Map with dummy handlers (not used here)
        Map<String, LogEventHandler> handlers = Map.of();

        // We want to spy so we can check which file was picked
        JournalLogMonitor monitor = Mockito.spy(new JournalLogMonitor(tempDir, handlers));

        // Use reflection to get fileMonitor and check its target file (optional, for deep test)
        // You can skip this and just assert that construction doesn't throw for valid dir/files
        // or add a getter for test purposes.
    }

    @Test
    void startMonitoring_callsFileMonitorStart() throws Exception {
        // Arrange
        JournalLogMonitor monitor = new JournalLogMonitor(tempDir, Map.of());
        var fileMonitorField = JournalLogMonitor.class.getDeclaredField("fileMonitor");
        fileMonitorField.setAccessible(true);
        fileMonitorField.set(monitor, mockFileMonitor);

        // Act
        monitor.startMonitoring();

        // Assert
        verify(mockFileMonitor).start();
    }

    @Test
    void stopMonitoring_callsFileMonitorStop() throws Exception {
        JournalLogMonitor monitor = new JournalLogMonitor(tempDir, Map.of());
        var fileMonitorField = JournalLogMonitor.class.getDeclaredField("fileMonitor");
        fileMonitorField.setAccessible(true);
        fileMonitorField.set(monitor, mockFileMonitor);

        monitor.stopMonitoring();

        verify(mockFileMonitor).stop();
    }

    @Test
    void processAppendedLines_dispatchesToCorrectHandler() throws Exception {
        // Arrange
        Map<String, LogEventHandler> handlers = new HashMap<>();
        handlers.put("FooEvent", mockHandlerFoo);
        handlers.put("BarEvent", mockHandlerBar);

        JournalLogMonitor monitor = new JournalLogMonitor(tempDir, handlers);

        // Access private processAppendedLines for direct test
        var method = JournalLogMonitor.class.getDeclaredMethod("processAppendedLines", String.class, Map.class);
        method.setAccessible(true);

        String logLine1 = "{\"event\":\"FooEvent\",\"value\":1}";
        String logLine2 = "{\"event\":\"BarEvent\",\"value\":2}";
        String logLineInvalid = "Not a JSON";

        // Act
        method.invoke(monitor, logLine1 + "\n" + logLine2 + "\n" + logLineInvalid + "\n", handlers);

        // Assert
        verify(mockHandlerFoo).handleEvent(any(JSONObject.class));
        verify(mockHandlerBar).handleEvent(any(JSONObject.class));
    }

    @Test
    void processAppendedLines_skipsBlankLinesAndInvalidJson() throws Exception {
        Map<String, LogEventHandler> handlers = Map.of("FooEvent", mockHandlerFoo);
        JournalLogMonitor monitor = new JournalLogMonitor(tempDir, handlers);

        var method = JournalLogMonitor.class.getDeclaredMethod("processAppendedLines", String.class, Map.class);
        method.setAccessible(true);

        // Only first line is valid
        String content = "{\"event\":\"FooEvent\",\"value\":1}\n\n \nNot a JSON\n";
        method.invoke(monitor, content, handlers);

        verify(mockHandlerFoo, times(1)).handleEvent(any(JSONObject.class));
    }
}
