package com.zergatstage.monitor.service.readers;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * File read strategy for files that are appended with new lines.
 */
@Log4j2
public class  AppendFileReadStrategy implements FileReadStrategy {

    /**
     * Reads new content appended to the file.
     *
     * @param file the file to read from.
     * @param previousState the last read position (expected to be a Long).
     * @return a ReadResult containing the appended content and the new file pointer.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public ReadResult readChanges(Path file, Object previousState) throws IOException {

        long lastPosition = previousState instanceof Long ? (Long) previousState : 0L;
        long fileSize = file.toFile().length();
        StringBuilder newContent = new StringBuilder();

        if (fileSize > lastPosition) {
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                raf.seek(lastPosition);
                String line;
                while ((line = raf.readLine()) != null) {
                    newContent.append(line).append("\n");
                }
                lastPosition = raf.getFilePointer();
            }
        }
        return new ReadResult(newContent.toString(), lastPosition);
    }
}
