package com.haricalcx.haribrowser;

import java.util.Locale;

/**
 * Pure policy for the browser's download confirmation dialog.
 *
 * This class only classifies names/MIME labels and explains the limits of the
 * browser's lightweight checks. It does not claim to detect malware.
 */
final class DownloadSafetyPolicy {
    private static final String EXECUTABLE_WARNING =
            "Warning: this file may install or run software. Hari Browser will not automatically execute it. Only keep it if you trust the source.";
    private static final String CHECK_LIMITS =
            "After download, Hari Browser can check the file type and calculate a checksum. This is not a malware verdict.";

    private DownloadSafetyPolicy() {}

    static boolean looksExecutable(String filename, String mimeType) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String type = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (type.contains("apk") || type.contains("x-msdownload")
                || type.contains("x-executable") || type.contains("x-dosexec")) {
            return true;
        }
        return name.endsWith(".apk") || name.endsWith(".exe") || name.endsWith(".msi")
                || name.endsWith(".bat") || name.endsWith(".cmd") || name.endsWith(".sh")
                || name.endsWith(".jar");
    }

    static String confirmationMessage(String filename, boolean potentiallyExecutable) {
        String safeName = filename == null || filename.trim().isEmpty() ? "download" : filename;
        StringBuilder message = new StringBuilder("Save ")
                .append(safeName)
                .append(" to Downloads?");
        if (potentiallyExecutable) {
            message.append("\n\n").append(EXECUTABLE_WARNING);
        }
        return message.append("\n\n").append(CHECK_LIMITS).toString();
    }
}
