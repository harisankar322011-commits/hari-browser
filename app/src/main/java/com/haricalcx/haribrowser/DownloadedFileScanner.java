package com.haricalcx.haribrowser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

/**
 * Bounded local checks for files downloaded by Hari Browser. This is not a
 * general-purpose antivirus engine: it has no full malware-signature database.
 */
public final class DownloadedFileScanner {
    public static final long MAX_SCAN_BYTES = 100L * 1024L * 1024L;
    private static final String EICAR_TEST_SIGNATURE =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    private DownloadedFileScanner() {}

    public static final class Result {
        public final String status;
        public final String details;
        public final String sha256;
        public final long bytesScanned;

        private Result(String status, String details, String sha256, long bytesScanned) {
            this.status = status;
            this.details = details;
            this.sha256 = sha256;
            this.bytesScanned = bytesScanned;
        }

        public boolean isSuspicious() {
            return "SUSPICIOUS".equals(status) || "MALICIOUS_TEST_SIGNATURE".equals(status);
        }

        public boolean requiresReview() {
            return isSuspicious() || "REVIEW_REQUIRED".equals(status) || "SCAN_INCOMPLETE".equals(status);
        }
    }

    public static Result scan(InputStream input, String filename, String mimeType, long expectedSize)
            throws IOException {
        if (input == null) return new Result("SCAN_INCOMPLETE", "Download stream was unavailable.", "", 0);
        if (expectedSize > MAX_SCAN_BYTES) {
            return new Result("SCAN_INCOMPLETE", "File exceeds the 100 MiB local-check limit; no clean verdict is available.", "", 0);
        }

        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            return new Result("SCAN_INCOMPLETE", "SHA-256 is unavailable on this device.", "", 0);
        }

        ByteArrayOutputStream header = new ByteArrayOutputStream(64);
        MalwareSignatureEngine.StreamMatcher signatureMatcher = MalwareSignatureEngine.newScanner();
        byte[] buffer = new byte[8192];
        long total = 0;
        String overlap = "";
        boolean eicarMatch = false;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > MAX_SCAN_BYTES) {
                return new Result("SCAN_INCOMPLETE", "File exceeded the 100 MiB local-check limit while reading; no clean verdict is available.", "", total);
            }
            digest.update(buffer, 0, count);
            signatureMatcher.update(buffer, count);
            if (header.size() < 64) {
                header.write(buffer, 0, Math.min(count, 64 - header.size()));
            }
            if (!eicarMatch) {
                String chunk = overlap + new String(buffer, 0, count, StandardCharsets.ISO_8859_1);
                eicarMatch = chunk.contains(EICAR_TEST_SIGNATURE);
                overlap = chunk.substring(Math.max(0, chunk.length() - EICAR_TEST_SIGNATURE.length() - 8));
            }
        }

        List<MalwareSignatureEngine.Finding> signatureFindings = signatureMatcher.finish();
        String hash = toHex(digest.digest());
        byte[] bytes = header.toByteArray();
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        StringBuilder findings = new StringBuilder();
        StringBuilder reviewFindings = new StringBuilder();

        if (eicarMatch) {
            add(findings, "Matched the EICAR antivirus test signature (a test pattern, not proof of a real infection)");
            return new Result("MALICIOUS_TEST_SIGNATURE", findings.toString(), hash, total);
        }

        if (hasMagic(bytes, 0x4d, 0x5a) && !endsWithAny(name, ".exe", ".dll", ".ocx")) {
            add(findings, "Executable (MZ/PE) content does not match the filename extension");
        }
        if (hasMagic(bytes, 0x7f, 0x45, 0x4c, 0x46) && !endsWithAny(name, ".elf", ".so")) {
            add(findings, "ELF executable content does not match the filename extension");
        }
        if (startsWithText(bytes, "%PDF-") && !name.endsWith(".pdf")) {
            add(findings, "PDF content does not match the filename extension");
        }
        if (hasMagic(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) && !name.endsWith(".png")) {
            add(findings, "PNG image content does not match the filename extension");
        }
        if (hasMagic(bytes, 0xff, 0xd8, 0xff) && !endsWithAny(name, ".jpg", ".jpeg")) {
            add(findings, "JPEG image content does not match the filename extension");
        }
        if (startsWithZip(bytes) && endsWithAny(name, ".pdf", ".png", ".jpg", ".jpeg", ".txt", ".html")) {
            add(findings, "ZIP/archive content does not match the filename extension");
        }
        if (looksLikeHtml(bytes) && endsWithAny(name, ".pdf", ".png", ".jpg", ".jpeg", ".apk")) {
            add(findings, "HTML-like content does not match the filename extension");
        }
        if (!mime.isEmpty() && mime.contains("pdf") && !name.endsWith(".pdf")) {
            add(findings, "Server MIME type and filename extension disagree (PDF MIME)");
        }

        for (MalwareSignatureEngine.Finding finding : signatureFindings) {
            add(reviewFindings, "Offline signature rule " + finding.ruleId + ": " + finding.description);
        }

        boolean activeType = endsWithAny(name, ".apk", ".exe", ".msi", ".bat", ".cmd", ".com",
                ".scr", ".ps1", ".vbs", ".js", ".jar", ".sh", ".dex", ".so", ".html", ".htm", ".svg");
        if (activeType) {
            add(reviewFindings, "Potentially executable, installable, scriptable, or active-content file type; review the source before opening. File type alone is not proof of malware.");
        }

        if (findings.length() > 0) {
            return new Result("SUSPICIOUS", findings.toString(), hash, total);
        }
        if (reviewFindings.length() > 0) {
            return new Result("REVIEW_REQUIRED", reviewFindings.toString(), hash, total);
        }
        return new Result("BASIC_CHECKS_ONLY",
                "No included starter signature or obvious checked-format mismatch matched. The built-in multi-indicator rule set is limited and this does not mean the file is virus-free.",
                hash, total);
    }

    private static boolean startsWithZip(byte[] data) {
        return data.length >= 4 && data[0] == 0x50 && data[1] == 0x4b &&
                (data[2] == 0x03 || data[2] == 0x05 || data[2] == 0x07) &&
                (data[3] == 0x04 || data[3] == 0x06 || data[3] == 0x08);
    }

    private static boolean hasMagic(byte[] data, int... values) {
        if (data.length < values.length) return false;
        for (int i = 0; i < values.length; i++) {
            if ((data[i] & 0xff) != (values[i] & 0xff)) return false;
        }
        return true;
    }

    private static boolean startsWithText(byte[] data, String value) {
        byte[] expected = value.getBytes(StandardCharsets.US_ASCII);
        if (data.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (data[i] != expected[i]) return false;
        return true;
    }

    private static boolean looksLikeHtml(byte[] data) {
        String prefix = new String(data, StandardCharsets.ISO_8859_1).trim().toLowerCase(Locale.ROOT);
        return prefix.startsWith("<!doctype html") || prefix.startsWith("<html") ||
                prefix.startsWith("<script") || prefix.startsWith("<?xml");
    }

    private static boolean endsWithAny(String value, String... suffixes) {
        for (String suffix : suffixes) if (value.endsWith(suffix)) return true;
        return false;
    }

    private static void add(StringBuilder out, String value) {
        if (out.length() > 0) out.append("; ");
        out.append(value);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }
}
