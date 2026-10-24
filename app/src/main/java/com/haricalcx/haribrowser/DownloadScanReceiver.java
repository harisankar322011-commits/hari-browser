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
 * Checks only completed downloads explicitly enqueued through Hari Browser.
 * No file contents, URLs, or hashes are uploaded. Private-mode metadata stays
 * in the private profile and is discarded when that session closes.
 */
public final class DownloadScanReceiver extends BroadcastReceiver {
    private static final String REGISTRY = "hari_browser_download_tracking";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        if (id < 0) return;

        SharedPreferences registry = context.getSharedPreferences(REGISTRY, Context.MODE_PRIVATE);
        String registryKey = "download_prefs_" + id;
        String targetPrefsName = registry.getString(registryKey, null);
        if (targetPrefsName == null) return;

        boolean privateTarget = "hari_browser_private_settings".equals(targetPrefsName);
        SharedPreferences prefs = context.getSharedPreferences(targetPrefsName, Context.MODE_PRIVATE);
        String trackedKey = "hari_browser_download_" + id;
        if (!prefs.getBoolean(trackedKey, false)) {
            registry.edit().remove(registryKey).apply();
            return;
        }

        prefs.edit().remove(trackedKey).putBoolean("hari_browser_download_scanning_" + id, true).apply();
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                scanOne(context.getApplicationContext(), id, prefs, privateTarget);
            } catch (Exception error) {
                if (!privateTarget || prefs.getBoolean("private_session_active", false)) {
                    String filename = prefs.getString("hari_browser_download_name_" + id, "Downloaded file");
                    saveResult(prefs, id, filename, "SCAN_INCOMPLETE",
                            "The browser could not complete local checks for this download.", "", privateTarget);
                }
            } finally {
                prefs.edit().remove("hari_browser_download_scanning_" + id)
                        .remove("hari_browser_download_name_" + id).apply();
                registry.edit().remove(registryKey).apply();
                pending.finish();
            }
        }, "HariDownloadScan").start();
    }

    private void scanOne(Context context, long id, SharedPreferences prefs, boolean privateTarget)
            throws Exception {
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
                    "The download did not complete successfully, so no file checks were made.", "", privateTarget);
            return;
        }

        AntivirusEngine.Result scan = AntivirusEngine.scan(
                () -> new ParcelFileDescriptor.AutoCloseInputStream(manager.openDownloadedFile(id)),
                filename, mimeType, size);
        String scanStatus = scan.status;
        String scanDetails = scan.details;
        boolean reputationHit = false;
        // Online reputation is opt-in through an encrypted key setting. Private-mode downloads
        // remain local-only and never trigger this network lookup.
        if (!privateTarget && !scan.sha256.isEmpty()) {
            String authKey = new SecureStore(context, false).get("hari_rep_store_1", "");
            if (!authKey.isEmpty()) {
                MalwareBazaarReputation.Result reputation = MalwareBazaarReputation.lookup(scan.sha256, authKey);
                if (reputation.knownMalware) {
                    scanStatus = "KNOWN_MALWARE_HASH";
                    scanDetails += "\n\nOnline hash reputation:\n" + reputation.details;
                    reputationHit = true;
                } else if (!"NO_MATCH".equals(reputation.status) && !"SERVICE_UNAVAILABLE".equals(reputation.status)) {
                    scanDetails += "\n\nOnline hash reputation:\n" + reputation.details;
                } else if ("NO_MATCH".equals(reputation.status)) {
                    scanDetails += "\n\nOnline hash reputation:\n" + reputation.details;
                }
            }
        }
        saveResult(prefs, id, filename, scanStatus, scanDetails, scan.sha256, privateTarget);
        if ((scan.isSuspicious() || reputationHit) && (!privateTarget || prefs.getBoolean("private_session_active", false))) {
            prefs.edit().putLong("last_suspicious_download_id", id)
                    .putString("last_suspicious_download_filename", filename == null ? "Downloaded file" : filename)
                    .apply();
        }
    }

    private void saveResult(SharedPreferences prefs, long id, String filename, String status,
                            String details, String hash, boolean privateTarget) {
        // If private browsing closed while a scan was running, discard its result.
        if (privateTarget && !prefs.getBoolean("private_session_active", false)) return;
        String safeName = filename == null ? "Downloaded file" : filename.replace('\n', ' ').replace('\r', ' ');
        String report = "File: " + safeName + "\nResult: " + status + "\n" +
                "Details: " + details + "\n" +
                (hash == null || hash.isEmpty() ? "SHA-256: unavailable" : "SHA-256: " + hash);
        String history = prefs.getString("download_scan_history", "[]");
        String updatedHistory;
        try {
            updatedHistory = DownloadScanHistory.append(history, id, safeName, status, details,
                    hash, System.currentTimeMillis()).toString();
        } catch (RuntimeException invalidHistory) {
            // Preserve the current report even if an old/corrupt history preference cannot be parsed.
            updatedHistory = DownloadScanHistory.append("[]", id, safeName, status, details,
                    hash, System.currentTimeMillis()).toString();
        }
        prefs.edit()
                .putString("last_download_scan_status", status)
                .putString("last_download_scan_filename", safeName)
                .putString("last_download_scan", report)
                .putString("last_download_scan_sha256", hash == null ? "" : hash)
                .putLong("last_download_scan_id", id)
                .putString("download_scan_history", updatedHistory)
                .apply();
    }
}
