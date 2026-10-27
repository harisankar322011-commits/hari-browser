package com.haricalcx.haribrowser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A bounded, app-private copy of a user-selected file so multiple scan passes
 * inspect the same bytes rather than reopening a mutable document-provider URI.
 */
public final class FileScanSnapshot implements AutoCloseable {
    private static final int BUFFER_SIZE = 8192;

    private final File file;
    private final long length;
    private boolean closed;

    private FileScanSnapshot(File file, long length) {
        this.file = file;
        this.length = length;
    }

    /**
     * Copies the source stream into a temporary file, rejecting actual content
     * larger than {@code maxBytes}. Ownership of {@code source} stays with the caller.
     */
    public static FileScanSnapshot capture(InputStream source, File directory, long maxBytes)
            throws IOException {
        if (source == null) throw new IOException("Selected file stream was unavailable.");
        if (directory == null) throw new IOException("A private scan directory was not available.");
        if (maxBytes < 0) throw new IllegalArgumentException("Maximum scan size must not be negative.");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Private scan directory could not be created.");
        }

        File snapshot = File.createTempFile("hari-scan-snapshot-", ".bin", directory);
        long total = 0;
        boolean complete = false;
        try {
            try (OutputStream output = new FileOutputStream(snapshot)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int count;
                while ((count = source.read(buffer)) != -1) {
                    if (count == 0) {
                        int single = source.read();
                        if (single == -1) break;
                        if (total >= maxBytes) {
                            throw new IOException("File exceeds the configured scan-size limit; no clean verdict is available.");
                        }
                        output.write(single);
                        total++;
                        continue;
                    }
                    if (count > maxBytes - total) {
                        throw new IOException("File exceeds the configured scan-size limit; no clean verdict is available.");
                    }
                    output.write(buffer, 0, count);
                    total += count;
                }
                output.flush();
            }
            complete = true;
            return new FileScanSnapshot(snapshot, total);
        } finally {
            if (!complete && snapshot.exists() && !snapshot.delete()) {
                snapshot.deleteOnExit();
            }
        }
    }

    public InputStream openStream() throws IOException {
        if (closed || !file.isFile()) throw new IOException("The scan snapshot is no longer available.");
        return new FileInputStream(file);
    }

    public File file() {
        if (closed) throw new IllegalStateException("The scan snapshot is already closed.");
        return file;
    }

    public long length() {
        return length;
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
            throw new IOException("Temporary scan snapshot could not be removed.");
        }
        closed = true;
    }
}
