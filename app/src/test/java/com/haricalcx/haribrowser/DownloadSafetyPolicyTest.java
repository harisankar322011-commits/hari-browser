package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

/** Regression coverage for the download confirmation and executable-file warnings. */
public final class DownloadSafetyPolicyTest {
    @Test
    public void identifiesExecutableRiskFromMimeTypeAndExtension() {
        assertTrue(DownloadSafetyPolicy.looksExecutable("package.apk", "application/octet-stream"));
        assertTrue(DownloadSafetyPolicy.looksExecutable("installer.bin", "application/x-msdownload"));
        assertTrue(DownloadSafetyPolicy.looksExecutable("run.sh", "text/plain"));
        assertFalse(DownloadSafetyPolicy.looksExecutable("notes.pdf", "application/pdf"));
    }

    @Test
    public void normalizesNullAndMixedCaseInputs() {
        assertFalse(DownloadSafetyPolicy.looksExecutable(null, null));
        assertTrue(DownloadSafetyPolicy.looksExecutable("Installer.APK", null));
        assertTrue(DownloadSafetyPolicy.looksExecutable("unknown.bin", "APPLICATION/X-EXECUTABLE"));
    }

    @Test
    public void confirmationMessageUsesRealLineBreaksRatherThanLiteralBackslashN() {
        String message = DownloadSafetyPolicy.confirmationMessage("installer.apk", true);

        assertTrue(message.contains("\n\nWarning:"));
        assertTrue(message.contains("\n\nAfter download,"));
        assertFalse(message.contains("\\n"));
    }

    @Test
    public void ordinaryDownloadsStillExplainTheLimitsOfTheFileChecks() {
        String message = DownloadSafetyPolicy.confirmationMessage("notes.pdf", false);

        assertTrue(message.startsWith("Save notes.pdf to Downloads?"));
        assertFalse(message.contains("Warning: this file may install or run software."));
        assertTrue(message.contains("This is not a malware verdict."));
    }
}
