package com.haricalcx.haribrowser;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Scans only completed downloads explicitly enqueued through Hari Browser.
 * No file contents, URLs, or hashes are uploaded to a server.
 */
public final class DownloadScanReceiver extends BroadcastReceiver {
    private static final String PREFS = "hari_browser_settings";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        if (id < 0) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String trackedKey = "hari_browser_download_" + id;
        if (!prefs.getBoolean(trackedKey, false)) return;

        prefs.edit().remove(trackedKey).putBoolean("hari_browser_download_scanning_" + id, true).apply();
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                scanOne(context.getApplicationContext(), id, prefs);
            } catch (Exception error) {
                prefs.edit()
                        .putString("last_download_scan_status", "SCAN_INCOMPLETE")
                        .putString("last_download_scan_filename", prefs.getString("hari_browser_download_name_" + id, "Downloaded file"))
                        .putString("last_download_scan", "Result: SCAN_INCOMPLETE\nDetails: The browser could not complete local checks for this download.")
                        .putLong("last_download_scan_id", id)
                        .apply();
            } finally {
                prefs.edit().remove("hari_browser_download_scanning_" + id)
                        .remove("hari_browser_download_name_" + id).apply();
                pending.finish();
            }
        }, "HariDownloadScan").start();
    }

    private void scanOne(Context context, long id, SharedPreferences prefs) throws Exception {
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) throw new IllegalStateException("Download service unavailable");
        Cursor resultCursor = manager.query(new DownloadManager.Query().setFilterById(id));
        if (resultCursor == null) throw new IllegalStateException("Download details unavailable");

        String filename;
        String mimeType = "";
        long size = -1L;
        int status;
        try (Cursor cursor = resultCursor) {
            if (!cursor.moveToFirst()) throw new IllegalStateException("Download entry unavailable");
            filename = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
            int mimeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE);
            if (mimeIndex >= 0 && !cursor.isNull(mimeIndex)) mimeType = cursor.getString(mimeIndex);
            int sizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex);
            status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        }

        if (status != DownloadManager.STATUS_SUCCESSFUL) {
            saveResult(prefs, id, filename, "SCAN_INCOMPLETE",
                    "The download did not complete successfully, so no file checks were made.", "");
            return;
        }

        DownloadedFileScanner.Result scan;
        try (ParcelFileDescriptor descriptor = manager.openDownloadedFile(id);
             InputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            scan = DownloadedFileScanner.scan(input, filename, mimeType, size);
        }
        saveResult(prefs, id, filename, scan.status, scan.details, scan.sha256);
        if (scan.isSuspicious()) {
            prefs.edit().putLong("last_suspicious_download_id", id).apply();
        }
    }

    private void saveResult(SharedPreferences prefs, long id, String filename, String status,
                            String details, String hash) {
        String safeName = filename == null ? "Downloaded file" : filename.replace('\n', ' ').replace('\r', ' ');
        String report = "File: " + safeName + "\nResult: " + status + "\n" +
                "Details: " + details + "\n" +
                (hash == null || hash.isEmpty() ? "SHA-256: unavailable" : "SHA-256: " + hash);
        prefs.edit()
                .putString("last_download_scan_status", status)
                .putString("last_download_scan_filename", safeName)
                .putString("last_download_scan", report)
                .putString("last_download_scan_sha256", hash == null ? "" : hash)
                .putLong("last_download_scan_id", id)
                .apply();
    }
}
