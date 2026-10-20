package com.haricalcx.haribrowser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Bounded, local-first file inspection for Hari Browser.
 *
 * Checks include SHA-256, existing local format/EICAR checks, ZIP/APK/JAR/OOXML
 * archive entry inspection, archive path traversal markers, resource limits,
 * and suspicious active-content entries. This intentionally does not claim to
 * replace a native AV engine with a maintained malware-signature database.
 */
public final class AntivirusEngine {
    public static final int MAX_ARCHIVE_ENTRIES = 2000;
    public static final long MAX_EXPANDED_ARCHIVE_BYTES = 100L * 1024L * 1024L;
    private static final int MAX_NESTED_ARCHIVE_CAPTURE = 8 * 1024 * 1024;
    private static final int MAX_ARCHIVE_DEPTH = 2;
    private static final long MAX_ENTRY_BYTES = 50L * 1024L * 1024L;
    private static final String EICAR =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    public interface StreamSource {
        InputStream open() throws IOException;
    }

    public static final class Result {
        public final String status;
        public final String details;
        public final String sha256;
        public final long bytesScanned;
        public final int archiveEntriesScanned;

        private Result(String status, String details, String sha256, long bytesScanned, int archiveEntriesScanned) {
            this.status = status;
            this.details = details;
            this.sha256 = sha256 == null ? "" : sha256;
            this.bytesScanned = bytesScanned;
            this.archiveEntriesScanned = archiveEntriesScanned;
        }

        public boolean isSuspicious() {
            return "SUSPICIOUS".equals(status) || "MALICIOUS_TEST_SIGNATURE".equals(status);
        }

        public boolean requiresReview() {
            return isSuspicious() || "REVIEW_REQUIRED".equals(status) || "SCAN_INCOMPLETE".equals(status);
        }
    }

    private static final class Budget {
        int entries;
        long expanded;
        boolean incomplete;
        boolean testSignature;
        final List<String> findings = new ArrayList<>();
    }

    private AntivirusEngine() {}

    public static Result scan(StreamSource source, String filename, String mimeType, long expectedSize)
            throws IOException {
        if (source == null) {
            return new Result("SCAN_INCOMPLETE", "A readable source was not provided.", "", 0, 0);
        }

        DownloadedFileScanner.Result basic;
        try (InputStream input = source.open()) {
            basic = DownloadedFileScanner.scan(input, filename, mimeType, expectedSize);
        }
        if ("SCAN_INCOMPLETE".equals(basic.status)) {
            return new Result("SCAN_INCOMPLETE", basic.details, basic.sha256, basic.bytesScanned, 0);
        }

        Budget budget = new Budget();
        boolean archive = false;
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        boolean archiveByName = isZipFamilyName(lowerName);
        try (InputStream input = source.open()) {
            byte[] signature = new byte[4];
            int read = readAtMost(input, signature);
            archive = isZipSignature(signature, read);
        }

        if (archive) {
            try (InputStream input = source.open()) {
                inspectZip(input, lowerName, 0, budget);
            } catch (ZipException malformed) {
                budget.incomplete = true;
                add(budget.findings, "A ZIP-like file could not be fully parsed.");
            }
        } else if (archiveByName) {
            if (isUnsupportedArchiveName(lowerName)) {
                budget.incomplete = true;
                add(budget.findings, "This archive format is not unpacked by the current engine; only ZIP-based archives receive entry inspection.");
            } else {
                add(budget.findings, "The file extension suggests a ZIP-based archive, but the file signature is not ZIP.");
            }
        }

        StringBuilder details = new StringBuilder(basic.details == null ? "" : basic.details);
        for (String finding : budget.findings) {
            if (details.length() > 0) details.append("\n");
            details.append("Archive inspection: ").append(finding);
        }
        if (archive) {
            if (details.length() > 0) details.append("\n");
            details.append("Archive entries inspected: ").append(budget.entries).append(".");
        }
        if (budget.incomplete) {
            if (details.length() > 0) details.append("\n");
            details.append("Some content could not be fully inspected because an archive or resource limit was reached.");
        }
        details.append("\nLocal scanner capability: bounded heuristics and test signatures only; no complete malware-signature engine is bundled.");

        String status;
        if (budget.testSignature || "MALICIOUS_TEST_SIGNATURE".equals(basic.status)) {
            status = "MALICIOUS_TEST_SIGNATURE";
        } else if (basic.isSuspicious() || !budget.findings.isEmpty() && !budget.incomplete) {
            status = "SUSPICIOUS";
        } else if (budget.incomplete || "SCAN_INCOMPLETE".equals(basic.status)) {
            status = "SCAN_INCOMPLETE";
        } else if ("REVIEW_REQUIRED".equals(basic.status)) {
            status = "REVIEW_REQUIRED";
        } else {
            status = "BASIC_CHECKS_ONLY";
        }

        return new Result(status, details.toString(), basic.sha256, basic.bytesScanned, budget.entries);
    }

    private static void inspectZip(InputStream source, String containerName, int depth, Budget budget)
            throws IOException {
        if (depth > MAX_ARCHIVE_DEPTH) {
            budget.incomplete = true;
            add(budget.findings, "Nested archive depth limit reached.");
            return;
        }
        boolean apkContainer = containerName.endsWith(".apk") || containerName.endsWith(".aab");
        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++budget.entries > MAX_ARCHIVE_ENTRIES) {
                    budget.incomplete = true;
                    add(budget.findings, "Maximum archive-entry count reached.");
                    return;
                }
                String entryName = entry.getName() == null ? "" : entry.getName();
                String lowerEntry = entryName.toLowerCase(Locale.ROOT);
                if (isUnsafeArchivePath(entryName)) {
                    add(budget.findings, "Archive contains a path-traversal-style entry name: " + shortName(entryName));
                }
                if (hasActivePayloadExtension(lowerEntry) && !(apkContainer && (lowerEntry.endsWith(".dex") || lowerEntry.endsWith(".so")))) {
                    add(budget.findings, "Archive contains an active or executable entry: " + shortName(entryName));
                }
                long declaredSize = entry.getSize();
                long compressed = entry.getCompressedSize();
                if (declaredSize > MAX_ENTRY_BYTES) {
                    budget.incomplete = true;
                    add(budget.findings, "An archive entry exceeds the per-entry inspection limit.");
                    return;
                }
                if (declaredSize > 10L * 1024L * 1024L && compressed > 0 && declaredSize / compressed > 200) {
                    add(budget.findings, "An archive entry has an unusually high declared expansion ratio.");
                }

                boolean nestedCandidate = depth < MAX_ARCHIVE_DEPTH && isZipFamilyName(lowerEntry);
                ByteArrayOutputStream nested = nestedCandidate ? new ByteArrayOutputStream() : null;
                boolean nestedTooLarge = false;
                long entryBytes = 0;
                String overlap = "";
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    entryBytes += count;
                    budget.expanded += count;
                    if (entryBytes > MAX_ENTRY_BYTES || budget.expanded > MAX_EXPANDED_ARCHIVE_BYTES) {
                        budget.incomplete = true;
                        add(budget.findings, "Expanded archive content exceeded the safety limit.");
                        return;
                    }
                    String chunk = overlap + new String(buffer, 0, count, java.nio.charset.StandardCharsets.ISO_8859_1);
                    if (chunk.contains(EICAR)) {
                        budget.testSignature = true;
                        add(budget.findings, "The EICAR antivirus test signature was found in an archive entry (test pattern, not proof of a real infection).");
                    }
                    overlap = chunk.substring(Math.max(0, chunk.length() - EICAR.length() - 8));
                    if (nested != null && !nestedTooLarge) {
                        if ((long) nested.size() + count <= MAX_NESTED_ARCHIVE_CAPTURE) {
                            nested.write(buffer, 0, count);
                        } else {
                            nestedTooLarge = true;
                            nested = null;
                            budget.incomplete = true;
                            add(budget.findings, "A nested archive was too large for bounded recursive inspection.");
                        }
                    }
                }
                zip.closeEntry();

                if (nested != null && isZipBytes(nested.toByteArray())) {
                    byte[] nestedBytes = nested.toByteArray();
                    inspectZip(new ByteArrayInputStream(nestedBytes), lowerEntry, depth + 1, budget);
                }
            }
        }
    }

    private static boolean isZipFamilyName(String name) {
        return name.endsWith(".zip") || name.endsWith(".apk") || name.endsWith(".aab") ||
                name.endsWith(".jar") || name.endsWith(".docx") || name.endsWith(".xlsx") ||
                name.endsWith(".pptx") || name.endsWith(".odt") || name.endsWith(".epub");
    }

    private static boolean isUnsupportedArchiveName(String name) {
        return name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".tar") ||
                name.endsWith(".gz") || name.endsWith(".tgz") || name.endsWith(".xz") ||
                name.endsWith(".bz2");
    }

    private static boolean isZipBytes(byte[] bytes) {
        return bytes != null && bytes.length >= 4 && bytes[0] == 0x50 && bytes[1] == 0x4b &&
                (bytes[2] == 0x03 || bytes[2] == 0x05 || bytes[2] == 0x07) &&
                (bytes[3] == 0x04 || bytes[3] == 0x06 || bytes[3] == 0x08);
    }

    private static boolean isZipSignature(byte[] bytes, int read) {
        return read >= 4 && isZipBytes(bytes);
    }

    private static int readAtMost(InputStream input, byte[] output) throws IOException {
        int offset = 0;
        while (offset < output.length) {
            int count = input.read(output, offset, output.length - offset);
            if (count == -1) break;
            offset += count;
        }
        return offset;
    }

    private static boolean isUnsafeArchivePath(String name) {
        if (name == null || name.isEmpty()) return false;
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) return true;
        for (String component : normalized.split("/")) if ("..".equals(component)) return true;
        return normalized.indexOf('\0') >= 0;
    }

    private static boolean hasActivePayloadExtension(String name) {
        return name.endsWith(".exe") || name.endsWith(".dll") || name.endsWith(".msi") ||
                name.endsWith(".bat") || name.endsWith(".cmd") || name.endsWith(".com") ||
                name.endsWith(".scr") || name.endsWith(".ps1") || name.endsWith(".vbs") ||
                name.endsWith(".sh") || name.endsWith(".apk") || name.endsWith(".dex") ||
                name.endsWith(".jar") || name.endsWith(".so") || name.endsWith(".hta");
    }

    private static String shortName(String name) {
        String clean = name == null ? "" : name.replace('\n', ' ').replace('\r', ' ');
        return clean.length() > 120 ? clean.substring(clean.length() - 120) : clean;
    }

    private static void add(List<String> items, String value) {
        if (!items.contains(value)) items.add(value);
    }
}
