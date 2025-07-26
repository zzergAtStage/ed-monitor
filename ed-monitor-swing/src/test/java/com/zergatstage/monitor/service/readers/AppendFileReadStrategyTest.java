package com.zergatstage.monitor.service.readers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppendFileReadStrategyTest {

    @TempDir
    Path tempDir;

    @Test
    void testReadChanges_NewContentAppended() throws IOException {
        // Arrange
        Path tempFile = tempDir.resolve("testFile.txt");
        Files.writeString(tempFile, "Line 1\nLine 2\n");
        AppendFileReadStrategy strategy = new AppendFileReadStrategy();

        // Act
        FileReadStrategy.ReadResult result1 = strategy.readChanges(tempFile, 0L);
        Files.writeString(tempFile, "Line 3\n", java.nio.file.StandardOpenOption.APPEND);
        FileReadStrategy.ReadResult result2 = strategy.readChanges(tempFile, result1.getNewState());

        // Assert
        assertEquals("Line 1\nLine 2\n", result1.getNewContent());
        assertEquals("Line 3\n", result2.getNewContent());
    }

    @Test
    void testReadChanges_NogetNewContent() throws IOException {
        // Arrange
        Path tempFile = tempDir.resolve("testFile.txt");
        Files.writeString(tempFile, "Line 1\nLine 2\n");
        AppendFileReadStrategy strategy = new AppendFileReadStrategy();

        // Act
        FileReadStrategy.ReadResult result1 = strategy.readChanges(tempFile, 0L);
        FileReadStrategy.ReadResult result2 = strategy.readChanges(tempFile, result1.getNewState());

        // Assert
        assertEquals("Line 1\nLine 2\n", result1.getNewContent());
        assertEquals("", result2.getNewContent());
    }

    @Test
    void testReadChanges_EmptyFile() throws IOException {
        // Arrange
        Path tempFile = tempDir.resolve("emptyFile.txt");
        Files.createFile(tempFile);
        AppendFileReadStrategy strategy = new AppendFileReadStrategy();

        // Act
        FileReadStrategy.ReadResult result = strategy.readChanges(tempFile, 0L);

        // Assert
        assertEquals("", result.getNewContent());
        assertEquals(0L, result.getNewState());
    }
}