package com.haricalcx.haribrowser;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * App-private quarantine storage. It never executes quarantined content.
 * Removing the original file is a separate, best-effort SAF operation that
 * the UI invokes only after explicit user confirmation.
 */
public final class QuarantineStore {
    private static final long MAX_BYTES = DownloadedFileScanner.MAX_SCAN_BYTES;
    private QuarantineStore() {}

    public static final class SaveResult {
        public final File file;
        public final String sha256;
        public final long bytes;
        private SaveResult(File file, String sha256, long bytes) {
            this.file = file;
            this.sha256 = sha256;
            this.bytes = bytes;
        }
    }

    public static SaveResult copyVerified(Context context, Uri source, String displayName, String expectedHash)
            throws IOException {
        if (context == null || source == null) throw new IOException("A readable source is required.");
        File directory = quarantineDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Quarantine storage could not be created.");

        String safeName = sanitizeName(displayName);
        File temporary = File.createTempFile("hari-", ".part", directory);
        MessageDigest digest = newDigest();
        long total = 0;
        boolean completed = false;
        try (InputStream input = context.getContentResolver().openInputStream(source);
             FileOutputStream output = new FileOutputStream(temporary)) {
            if (input == null) throw new IOException("Android could not reopen the selected file.");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BYTES) throw new IOException("Quarantine copy exceeds the 100 MiB safety limit.");
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
            output.flush();
            output.getFD().sync();
            String hash = toHex(digest.digest());
            if (expectedHash != null && !expectedHash.trim().isEmpty() &&
                    !hash.equalsIgnoreCase(expectedHash.trim())) {
                throw new IOException("The file changed after scanning. It was not quarantined; scan it again.");
            }
            File destination = new File(directory, System.currentTimeMillis() + "__" + hash + "__" + safeName + ".qtn");
            if (!temporary.renameTo(destination)) throw new IOException("Quarantine copy could not be finalized.");
            completed = true;
            return new SaveResult(destination, hash, total);
        } finally {
            if (!completed && temporary.exists()) temporary.delete();
        }
    }

    public static boolean tryDeleteOriginal(Context context, Uri source) {
        try {
            if (context == null || source == null || !DocumentsContract.isDocumentUri(context, source)) return false;
            return DocumentsContract.deleteDocument(context.getContentResolver(), source);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static List<File> list(Context context) {
        File directory = quarantineDirectory(context);
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".qtn"));
        if (files == null) return new ArrayList<>();
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return new ArrayList<>(Arrays.asList(files));
    }

    public static String displayName(File file) {
        if (file == null) return "Quarantined file";
        String name = file.getName();
        String[] pieces = name.split("__", 3);
        String display = pieces.length == 3 ? pieces[2].replaceFirst("\\.qtn$", "") : name;
        return display.replace('_', ' ');
    }

    public static String expectedHash(File file) {
        if (file == null) return "";
        String[] pieces = file.getName().split("__", 3);
        if (pieces.length < 2) return "";
        return pieces[1].matches("(?i)^[0-9a-f]{64}$") ? pieces[1].toLowerCase(Locale.ROOT) : "";
    }

    public static boolean deleteQuarantined(Context context, File file) {
        try {
            File directory = quarantineDirectory(context).getCanonicalFile();
            File target = file.getCanonicalFile();
            return target.getParentFile() != null && target.getParentFile().equals(directory)
                    && target.getName().endsWith(".qtn") && target.delete();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void restoreTo(Context context, File file, Uri destination) throws IOException {
        if (file == null || destination == null || !file.exists()) throw new IOException("The quarantined file or destination is unavailable.");
        File directory = quarantineDirectory(context).getCanonicalFile();
        if (!file.getCanonicalFile().getParentFile().equals(directory) || !file.getName().endsWith(".qtn")) {
            throw new IOException("The selected quarantine entry is invalid.");
        }
        String expected = expectedHash(file);
        MessageDigest digest = newDigest();
        try (InputStream input = new FileInputStream(file);
             OutputStream output = context.getContentResolver().openOutputStream(destination, "w")) {
            if (output == null) throw new IOException("Android could not open the restore destination.");
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BYTES) throw new IOException("The quarantined file exceeds the restore safety limit.");
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
            output.flush();
        }
        if (!expected.isEmpty() && !expected.equalsIgnoreCase(toHex(digest.digest()))) {
            throw new IOException("Quarantine integrity check failed. Do not open the restored copy.");
        }
    }

    private static File quarantineDirectory(Context context) {
        return new File(context.getNoBackupFilesDir(), "hari-quarantine");
    }

    private static String sanitizeName(String input) {
        String value = input == null ? "file.bin" : input.replaceAll("[^A-Za-z0-9._()-]", "_");
        value = value.replace("..", "_");
        while (value.startsWith(".")) value = value.substring(1);
        if (value.trim().isEmpty()) value = "file.bin";
        return value.length() > 100 ? value.substring(value.length() - 100) : value;
    }

    private static MessageDigest newDigest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is not available.", impossible);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }
}
