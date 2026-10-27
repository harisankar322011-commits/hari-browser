package com.haricalcx.haribrowser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.*;

public class FileScanSnapshotTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void repeatedOpensReadTheSameCapturedBytes() throws Exception {
        byte[] original = "stable scan snapshot".getBytes(StandardCharsets.UTF_8);
        byte[] expected = original.clone();
        File file;

        try (FileScanSnapshot snapshot = FileScanSnapshot.capture(
                new ByteArrayInputStream(original), temporaryFolder.getRoot(), original.length)) {
            file = snapshot.file();
            assertEquals(expected.length, snapshot.length());
            assertArrayEquals(expected, readAll(snapshot.openStream()));

            // Changing the caller-owned byte array must not change the captured copy.
            Arrays.fill(original, (byte) 'x');
            assertArrayEquals(expected, readAll(snapshot.openStream()));
        }

        assertFalse("Temporary scan copy must be removed after use", file.exists());
    }

    @Test
    public void rejectsActualBytesOverLimitAndDeletesPartialSnapshot() throws Exception {
        byte[] fiveBytes = new byte[] {1, 2, 3, 4, 5};

        try {
            FileScanSnapshot.capture(
                    new ByteArrayInputStream(fiveBytes), temporaryFolder.getRoot(), 4);
            fail("Oversized input must not create a usable scan snapshot");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("exceeds"));
        }

        File[] remaining = temporaryFolder.getRoot().listFiles();
        assertNotNull(remaining);
        assertEquals("Partial snapshots must be deleted after rejection", 0, remaining.length);
    }

    @Test
    public void acceptsInputExactlyAtConfiguredLimit() throws Exception {
        byte[] bytes = new byte[] {10, 20, 30, 40};

        try (FileScanSnapshot snapshot = FileScanSnapshot.capture(
                new ByteArrayInputStream(bytes), temporaryFolder.getRoot(), bytes.length)) {
            assertEquals(bytes.length, snapshot.length());
            assertArrayEquals(bytes, readAll(snapshot.openStream()));
        }
    }

    @Test
    public void rejectsUnavailableInputStream() throws Exception {
        try {
            FileScanSnapshot.capture(null, temporaryFolder.getRoot(), 10);
            fail("Missing source must not be treated as an empty file");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("unavailable"));
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int count;
            while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
}
