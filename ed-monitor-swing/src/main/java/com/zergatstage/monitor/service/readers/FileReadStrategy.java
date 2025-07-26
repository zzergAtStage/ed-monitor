package com.zergatstage.monitor.service.readers;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Strategy interface for reading file changes.
 */
public interface FileReadStrategy {

    /**
     * Reads updates from the specified file.
     *
     * @param file          the file to read updates from.
     * @param previousState an object representing the previous state (could be a position, content, etc.).
     * @return a Result object containing new content and the updated state.
     * @throws IOException if an I/O error occurs.
     */
    ReadResult readChanges(Path file, Object previousState) throws IOException;

    /**
     * Encapsulates the result of reading changes.
     *
     * @param newContent -- GETTER --
     *                   Returns the new content.
     * @param newState   -- GETTER --
     *                   Returns the updated state.
     */
    record ReadResult(String newContent, Object newState) {
        /**
         * Constructs a new ReadResult.
         *
         * @param newContent the new content read from the file.
         * @param newState   the new state to be persisted for subsequent reads.
         */
        public ReadResult {}
    }
}