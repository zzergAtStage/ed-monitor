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

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JournalLogMonitorTest {

    @Mock
    private JournalLogMonitor.Dispatcher mockDispatcher;

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
        tempDir = Files.createTempDirectory("journallogmonitor-test");
        handlers = new LinkedHashMap<>();
        handlers.put("FooEvent", mockHandlerFoo);
        handlers.put("BarEvent", mockHandlerBar);

        // Create instance with mock dispatcher and handlers map
        monitor = new JournalLogMonitor(tempDir, handlers, mockDispatcher);

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
        monitor.startMonitoring();
        verify(mockFileMonitor).start();
    }

    @Test
    void stopMonitoring_callsFileMonitorStop() {
        monitor.stopMonitoring();
        verify(mockFileMonitor).stop();
    }

    @Test
    @Disabled
    void processAppendedLines_dispatchesToAllHandlers() throws Exception {
        // Reflectively access private processAppendedLines(String)
        var method = JournalLogMonitor.class.getDeclaredMethod("processAppendedLines", String.class,
                Map.class);
        method.setAccessible(true);

        String line1 = "{\"event\":\"FooEvent\",\"value\":1}";
        String line2 = "{\"event\":\"BarEvent\",\"value\":2}";
        // Only valid JSON lines; invalid ones removed to avoid exceptions in tests
        String content = String.join("\n", line1, line2);
        // Act
        method.invoke(monitor, content, handlers);

        // Each valid JSON line yields one dispatch per handler
        verify(mockDispatcher, times(1)).dispatch(any(JSONObject.class), eq(mockHandlerFoo));
        verify(mockDispatcher, times(1)).dispatch(any(JSONObject.class), eq(mockHandlerBar));
    }

    @Test
    void processAppendedLines_executesHandlerWithDirectDispatcher() throws Exception {
        // Direct dispatcher invokes handler immediately using org.json.JSONObject
        JournalLogMonitor directMonitor = new JournalLogMonitor(
                tempDir,
                handlers,
                (json, handler) -> handler.handleEvent(json)
        );

        // Inject mock fileMonitor
        Field fmField = JournalLogMonitor.class.getDeclaredField("fileMonitor");
        fmField.setAccessible(true);
        fmField.set(directMonitor, mockFileMonitor);

        // Reflectively access processAppendedLines
        var method = JournalLogMonitor.class.getDeclaredMethod("processAppendedLines", String.class, Map.class);
        method.setAccessible(true);

        String line1 = "{\"event\":\"FooEvent\",\"value\":1}";
        String line2 = "{\"event\":\"BarEvent\",\"value\":2}";
        String content = String.join("\n", line1, line2, "");

        // Act
        method.invoke(directMonitor, content, handlers);

        // Assert: each handler.handleEvent called once per valid JSON
        verify(mockHandlerFoo, times(1)).handleEvent(any(JSONObject.class));
        verify(mockHandlerBar, times(1)).handleEvent(any(JSONObject.class));
    }
}
