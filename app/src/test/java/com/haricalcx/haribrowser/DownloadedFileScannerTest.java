package com.haricalcx.haribrowser;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class DownloadedFileScannerTest {
    @Test
    public void ordinaryTextGetsOnlyABasicCheckResultAndHash() throws Exception {
        DownloadedFileScanner.Result result = DownloadedFileScanner.scan(
                new ByteArrayInputStream("hello world".getBytes(StandardCharsets.UTF_8)),
                "notes.txt", "text/plain", 11);
        assertEquals("BASIC_CHECKS_ONLY", result.status);
        assertEquals(64, result.sha256.length());
        assertTrue(result.details.contains("does not mean the file is virus-free"));
    }

    @Test
    public void legacyTestPatternDoesNotProduceAMalwareVerdict() throws Exception {
        byte[] sample = ("prefix " +
                "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*")
                .getBytes(StandardCharsets.US_ASCII);
        DownloadedFileScanner.Result result = DownloadedFileScanner.scan(
                new ByteArrayInputStream(sample), "sample.txt", "text/plain", sample.length);
        assertEquals("BASIC_CHECKS_ONLY", result.status);
        assertFalse(result.isSuspicious());
        assertTrue(result.details.contains("does not mean the file is virus-free"));
    }

    @Test
    public void executableContentWithAnImageExtensionIsSuspicious() throws Exception {
        byte[] sample = new byte[] {0x4d, 0x5a, 0, 0, 1, 2, 3, 4};
        DownloadedFileScanner.Result result = DownloadedFileScanner.scan(
                new ByteArrayInputStream(sample), "photo.jpg", "image/jpeg", sample.length);
        assertEquals("SUSPICIOUS", result.status);
        assertTrue(result.details.contains("MZ/PE"));
    }

    @Test
    public void executableFileTypeRequiresReviewButIsNotLabeledMalwareByTypeAlone() throws Exception {
        byte[] sample = new byte[] {0x50, 0x4b, 0x03, 0x04};
        DownloadedFileScanner.Result result = DownloadedFileScanner.scan(
                new ByteArrayInputStream(sample), "installer.apk", "application/vnd.android.package-archive", sample.length);
        assertEquals("REVIEW_REQUIRED", result.status);
        assertFalse(result.isSuspicious());
        assertTrue(result.requiresReview());
    }

    @Test
    public void multiIndicatorSignatureRequiresReviewWithoutDeclaringMalware() throws Exception {
        byte[] sample = ("<?php if (isset($_REQUEST['cmd'])) { " +
                "echo base64_decode($x); system($x); eval($y); } ?>")
                .getBytes(StandardCharsets.UTF_8);
        DownloadedFileScanner.Result result = DownloadedFileScanner.scan(
                new ByteArrayInputStream(sample), "payload.txt", "text/plain", sample.length);
        assertEquals("REVIEW_REQUIRED", result.status);
        assertFalse(result.isSuspicious());
        assertTrue(result.details.contains("PHP_WEBSHELL_INDICATORS"));
    }

    @Test
    public void excessiveFilesAreNotReportedAsClean() throws Exception {
        DownloadedFileScanner.Result result = DownloadedFileScanner.scan(
                new ByteArrayInputStream(new byte[0]), "large.bin", "application/octet-stream",
                DownloadedFileScanner.MAX_SCAN_BYTES + 1);
        assertEquals("SCAN_INCOMPLETE", result.status);
        assertEquals("", result.sha256);
    }
}
