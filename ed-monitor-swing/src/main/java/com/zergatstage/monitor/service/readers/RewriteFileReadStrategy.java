package com.zergatstage.monitor.service.readers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File read strategy for files that are completely rewritten.
 */
public class RewriteFileReadStrategy implements FileReadStrategy {

    /**
     * Reads the entire file content and compares it to the previous content.
     *
     * @param file the file to read from.
     * @param previousState the previous content (expected to be a String).
     * @return a ReadResult containing the file content if it changed; otherwise, an empty string.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public ReadResult readChanges(Path file, Object previousState) throws IOException {
        String content = new String(Files.readAllBytes(file));
        String lastContent = previousState instanceof String ? (String) previousState : "";
        if (!content.equals(lastContent)) {
            return new ReadResult(content, content);
        }
        return new ReadResult("", lastContent);
    }
}