package com.haricalcx.haribrowser;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class AntivirusEngineTest {
    private static byte[] zip(String entryName, byte[] payload) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(payload);
            output.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static AntivirusEngine.Result scan(byte[] bytes, String name) throws Exception {
        return AntivirusEngine.scan(
                () -> new ByteArrayInputStream(bytes),
                name, "application/octet-stream", bytes.length);
    }

    @Test
    public void plainZipDoesNotClaimToBeVirusFree() throws Exception {
        byte[] archive = zip("notes.txt", "hello".getBytes(StandardCharsets.UTF_8));
        AntivirusEngine.Result result = scan(archive, "notes.zip");
        assertEquals("BASIC_CHECKS_ONLY", result.status);
        assertTrue(result.details.contains("small offline multi-indicator rule set"));
        assertEquals(1, result.archiveEntriesScanned);
    }

    @Test
    public void eicarInsideZipIsFound() throws Exception {
        byte[] eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
                .getBytes(StandardCharsets.US_ASCII);
        AntivirusEngine.Result result = scan(zip("sample.txt", eicar), "sample.zip");
        assertEquals("MALICIOUS_TEST_SIGNATURE", result.status);
        assertTrue(result.details.contains("EICAR antivirus test signature"));
    }

    @Test
    public void archiveTraversalPathIsFlagged() throws Exception {
        byte[] archive = zip("../outside.sh", "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        AntivirusEngine.Result result = scan(archive, "bundle.zip");
        assertEquals("SUSPICIOUS", result.status);
        assertTrue(result.details.contains("path-traversal-style entry name"));
    }

    @Test
    public void multiIndicatorSignatureIsDetectedInsideZipEntries() throws Exception {
        byte[] webShell = ("<?php if (isset($_REQUEST['cmd'])) { " +
                "echo base64_decode($x); system($x); eval($y); } ?>")
                .getBytes(StandardCharsets.UTF_8);
        AntivirusEngine.Result result = scan(zip("payload.txt", webShell), "bundle.zip");
        assertEquals("REVIEW_REQUIRED", result.status);
        assertTrue(result.details.contains("PHP_WEBSHELL_INDICATORS"));
        assertTrue(result.details.contains("payload.txt"));
    }

    @Test
    public void unsupportedArchiveIsNotReportedAsClean() throws Exception {
        byte[] payload = new byte[] {1, 2, 3, 4};
        AntivirusEngine.Result result = scan(payload, "backup.rar");
        assertEquals("SCAN_INCOMPLETE", result.status);
        assertTrue(result.details.contains("not unpacked"));
    }

    @Test
    public void archiveEntryCountIsReported() throws Exception {
        byte[] archive = zip("folder/image.png", new byte[] {1, 2, 3});
        AntivirusEngine.Result result = scan(archive, "bundle.zip");
        assertEquals(1, result.archiveEntriesScanned);
    }

    @Test
    public void reputationLookupNeedsARealHashAndConfiguredKey() {
        assertEquals("INVALID_HASH", MalwareBazaarReputation.lookup("", "key").status);
        assertEquals("NOT_CONFIGURED",
                MalwareBazaarReputation.lookup("a".repeat(64), "").status);
    }
}
