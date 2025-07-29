package com.zergatstage.monitor.service;

import com.zergatstage.monitor.handlers.LogEventHandler;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JournalLogMonitorTest {

    @Mock
    private Executor mockExecutor;

    @Mock
    private GenericFileMonitor mockFileMonitor;

    @Mock
    private LogEventHandler mockHandlerFoo;

    @Mock
    private LogEventHandler mockHandlerBar;

    private Path tempDir;
    private Map<String, LogEventHandler> handlers;
    private JournalLogMonitor monitor;

    @BeforeEach
    void setUp() throws IOException, NoSuchFieldException, IllegalAccessException {
        MockitoAnnotations.openMocks(this);
        tempDir = Files.createTempDirectory("journallogmonitor-test");
        handlers = new LinkedHashMap<>();
        handlers.put("FooEvent", mockHandlerFoo);
        handlers.put("BarEvent", mockHandlerBar);

        // Create instance with mock executor and handlers map
        monitor = new JournalLogMonitor(tempDir, handlers, mockExecutor);

        // Inject mock fileMonitor into monitor
        Field fileMonitorField = JournalLogMonitor.class.getDeclaredField("fileMonitor");
        fileMonitorField.setAccessible(true);
        fileMonitorField.set(monitor, mockFileMonitor);
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

    @Test
    void startMonitoring_callsFileMonitorStart() {
        // Act
        monitor.startMonitoring();

        // Assert
        verify(mockFileMonitor).start();
    }

    @Test
    void stopMonitoring_callsFileMonitorStop() {
        // Act
        monitor.stopMonitoring();

        // Assert
        verify(mockFileMonitor).stop();
    }

    @Test
    void processAppendedLines_submitsTasksToExecutor() throws Exception {
        // Reflectively access private processAppendedLines now expecting a Map
        var method = JournalLogMonitor.class.getDeclaredMethod(
                "processAppendedLines", String.class, Map.class);
        method.setAccessible(true);

        // Prepare input with two valid JSON lines and one invalid
        String line1 = "{\"event\":\"FooEvent\",\"value\":1}";
        String line2 = "{\"event\":\"BarEvent\",\"value\":2}";
        String invalid = "Not a JSON";
        String content = String.join("\n", line1, line2, invalid, "");

        // Act: pass the handlers map directly
        method.invoke(monitor, content, handlers);

        // Assert: executor.execute called twice, once per valid JSON line
        verify(mockExecutor, times(2)).execute(any(Runnable.class));
    }

    @Test
    void processAppendedLines_executesHandlerOnDirectExecutor() throws Exception {
        // Use a direct executor (runs tasks immediately)
        Executor directExec = Runnable::run;
        JournalLogMonitor directMonitor =
                new JournalLogMonitor(tempDir, handlers, directExec);

        // Inject mock fileMonitor to satisfy start/stop
        Field fmField = JournalLogMonitor.class.getDeclaredField("fileMonitor");
        fmField.setAccessible(true);
        fmField.set(directMonitor, mockFileMonitor);

        // Reflectively access updated processAppendedLines signature
        var method = JournalLogMonitor.class.getDeclaredMethod(
                "processAppendedLines", String.class, Map.class);
        method.setAccessible(true);

        // Prepare valid content
        String line1 = "{\"event\":\"FooEvent\",\"value\":1}";
        String line2 = "{\"event\":\"BarEvent\",\"value\":2}";
        String content = String.join("\n", line1, line2, "");

        // Act: pass the handlers map directly
        method.invoke(directMonitor, content, handlers);

        // Assert: each handler.handleEvent called once
        verify(mockHandlerFoo, times(1)).handleEvent(any(JSONObject.class));
        verify(mockHandlerBar, times(1)).handleEvent(any(JSONObject.class));
    }
}
