package com.zergatstage.monitor.service.readers;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class RewriteFileReadStrategyTest {

    private RewriteFileReadStrategy strategy;
    private Path tempFile;


    @BeforeEach
    void setUp() throws IOException {
        strategy = new RewriteFileReadStrategy();
        tempFile = Files.createTempFile("rewrite_strategy_test", ".txt");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    @Test
    void testReadChanges_contentChanged_returnsNewContent() throws IOException {
        String initialContent = "Hello";
        Files.write(tempFile, initialContent.getBytes());
        Object previousState = "";

        FileReadStrategy.ReadResult result = strategy.readChanges(tempFile, previousState);

        assertEquals(initialContent, result.getNewContent());
        assertEquals(initialContent, result.getNewState());
    }

    @Test
    void testReadChanges_contentUnchanged_returnsEmpty() throws IOException {
        String initialContent = "NoChange";
        Files.write(tempFile, initialContent.getBytes());
        Object previousState = "NoChange";

        FileReadStrategy.ReadResult result = strategy.readChanges(tempFile, previousState);

        assertEquals("", result.getNewContent());
        assertEquals("NoChange", result.getNewState());
    }

    @Test
    void testReadChanges_previousStateNull_treatedAsEmpty() throws IOException {
        String fileContent = "SomeData";
        Files.write(tempFile, fileContent.getBytes());

        FileReadStrategy.ReadResult result = strategy.readChanges(tempFile, null);

        assertEquals(fileContent, result.getNewContent());
        assertEquals(fileContent, result.getNewState());
    }

    @Test
    void testReadChanges_previousStateNotString_treatedAsEmpty() throws IOException {
        String fileContent = "SomeData";
        Files.write(tempFile, fileContent.getBytes());

        FileReadStrategy.ReadResult result = strategy.readChanges(tempFile, 1234); // Integer

        assertEquals(fileContent, result.getNewContent());
        assertEquals(fileContent, result.getNewState());
    }

    @Test
    void testReadChanges_ioException() {
        Path fakePath = Paths.get("/invalid/path/doesnotexist.txt");
        assertThrows(IOException.class, () -> strategy.readChanges(fakePath, null));
    }
}
