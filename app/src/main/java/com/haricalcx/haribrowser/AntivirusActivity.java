package com.haricalcx.haribrowser;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User-initiated file inspection and quarantine screen. Uses Android's SAF so
 * the user chooses which file is visible to the scanner; it does not request
 * broad storage access or inspect other apps' private files.
 */
public final class AntivirusActivity extends ComponentActivity {
    private static final String KEY_REPUTATION_PREF = "hari_rep_store_1";
    private static final int INK = 0xff182238;
    private static final int MUTED = 0xff56657a;
    private static final int ACCENT = 0xff0a7f75;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<String[]> openFileLauncher;
    private ActivityResultLauncher<String> restoreFileLauncher;
    private SecureStore secureStore;
    private TextView resultText;
    private TextView reputationStatus;
    private ProgressBar progress;
    private Button scanButton;
    private Button quarantineButton;
    private Button reputationButton;
    private Button urlCheckButton;
    private Button urlAutoCheckButton;
    private boolean privateModeLaunch;
    private Uri currentUri;
    private String currentName;
    private String currentMime;
    private AntivirusEngine.Result currentResult;
    private boolean lastKnownMalware;
    private File pendingRestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        privateModeLaunch = getIntent() != null && getIntent().getBooleanExtra("private_mode", false);
        secureStore = new SecureStore(this, false);

        openFileLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                    // A provider may grant only a temporary read permission; scan it immediately.
                }
                startScan(uri);
            }
        });
        restoreFileLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
            File selected = pendingRestore;
            pendingRestore = null;
            if (uri == null || selected == null) return;
            worker.execute(() -> {
                String message;
                try {
                    QuarantineStore.restoreTo(this, selected, uri);
                    message = "A verified copy was restored. The quarantined original remains in Hari Antivirus until you delete it.";
                } catch (Exception error) {
                    message = "Restore failed: " + safeError(error);
                }
                final String display = message;
                runOnUiThread(() -> showMessage(display));
            });
        });

        buildUi();
        refreshReputationStatus();
    }

    private void buildUi() {
        int bg = 0xfff3f6fb;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(18), dp(18), dp(22));
        scroll.addView(page);

        TextView title = new TextView(this);
        title.setText("🛡 Hari Antivirus");
        title.setTextColor(INK);
        title.setTextSize(26);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        page.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Hari Signature Engine " + MalwareSignatureEngine.ENGINE_VERSION + " · offline rules + opt-in threat reputation");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(15);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.bottomMargin = dp(14);
        page.addView(subtitle, subtitleParams);

        TextView scope = new TextView(this);
        scope.setText("Privacy first: files are scanned on this device. Optional MalwareBazaar checks send only a SHA-256 hash. URLhaus checks send a URL only after you paste it and confirm. No automatic URL reporting occurs.");
        scope.setTextColor(INK);
        scope.setTextSize(14);
        scope.setPadding(dp(14), dp(12), dp(14), dp(12));
        scope.setBackgroundColor(0xffe0f2ef);
        LinearLayout.LayoutParams scopeParams = new LinearLayout.LayoutParams(-1, -2);
        scopeParams.bottomMargin = dp(12);
        page.addView(scope, scopeParams);

        scanButton = button("Choose a file to scan");
        scanButton.setOnClickListener(v -> openFileLauncher.launch(new String[]{"*/*"}));
        page.addView(scanButton, buttonParams());

        quarantineButton = button("Quarantine / remove flagged file");
        quarantineButton.setVisibility(View.GONE);
        quarantineButton.setOnClickListener(v -> confirmQuarantine());
        page.addView(quarantineButton, buttonParams());

        Button quarantineList = button("Manage quarantine");
        quarantineList.setOnClickListener(v -> showQuarantineList());
        page.addView(quarantineList, buttonParams());

        reputationButton = button("Configure online hash reputation");
        reputationButton.setOnClickListener(v -> configureReputation());
        page.addView(reputationButton, buttonParams());

        urlCheckButton = button("Check a website URL with URLhaus");
        urlCheckButton.setOnClickListener(v -> checkUrlReputation());
        page.addView(urlCheckButton, buttonParams());

        urlAutoCheckButton = button("Automatic URL reputation: OFF");
        urlAutoCheckButton.setOnClickListener(v -> toggleAutomaticUrlReputation());
        page.addView(urlAutoCheckButton, buttonParams());

        reputationStatus = new TextView(this);
        reputationStatus.setTextColor(MUTED);
        reputationStatus.setTextSize(13);
        LinearLayout.LayoutParams repParams = new LinearLayout.LayoutParams(-1, -2);
        repParams.bottomMargin = dp(8);
        page.addView(reputationStatus, repParams);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        page.addView(progress, new LinearLayout.LayoutParams(-2, dp(42)));

        resultText = new TextView(this);
        resultText.setTextColor(INK);
        resultText.setTextSize(14);
        resultText.setTextIsSelectable(true);
        resultText.setPadding(dp(14), dp(14), dp(14), dp(14));
        resultText.setBackgroundColor(0xffffffff);
        resultText.setText(
                "Ready to scan.\n\n" +
                "Supported local checks:\n" +
                "• SHA-256, selected format mismatches and the EICAR antivirus test signature.\n" +
                "• Streaming multi-indicator signature rules for PHP web-shell combinations, PowerShell download/execute chains, Android dropper capability combinations, cryptocurrency miners, ransomware-note phrases and obfuscated scripts.\n" +
                "• Bounded ZIP-based archive, APK, JAR and Office Open XML entry inspection, including nested-archive limits.\n" +
                "• Archive traversal markers, active/executable entries, APK package metadata and signer-certificate fingerprints.\n" +
                "• Optional MalwareBazaar known-malware hash reputation; only SHA-256 is submitted, never file bytes or filename.\n\n" +
                "Important: the offline rule set is a small starter set, not a comprehensive maintained malware-signature database. Multi-indicator matches require review and are not by themselves proof of malware. Hashes with no reputation match are not guaranteed safe. Hari Browser only sees files you select and its own tracked downloads; Android prevents it from scanning every other app or the whole device.");
        LinearLayout.LayoutParams reportParams = new LinearLayout.LayoutParams(-1, -2);
        reportParams.topMargin = dp(10);
        page.addView(resultText, reportParams);

        Button done = button("Done");
        done.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams doneParams = buttonParams();
        doneParams.topMargin = dp(18);
        page.addView(done, doneParams);

        setContentView(scroll);
    }

    private void startScan(Uri uri) {
        currentUri = uri;
        currentResult = null;
        lastKnownMalware = false;
        scanButton.setEnabled(false);
        quarantineButton.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        resultText.setText("Scanning selected file locally…\nThis may take a while for an archive. No file content is uploaded.");
        worker.execute(() -> {
            String fileName = queryDisplayName(uri);
            long size = querySize(uri);
            String mime = getContentResolver().getType(uri);
            AntivirusEngine.Result scan = null;
            String apkReport = "";
            MalwareBazaarReputation.Result reputation = null;
            String failure = null;
            try {
                scan = AntivirusEngine.scan(
                        () -> getContentResolver().openInputStream(uri),
                        fileName, mime, size);
                if (fileName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                    apkReport = inspectApk(uri, size);
                }
                if (!privateModeLaunch) {
                    String apiKey = secureStore.get(KEY_REPUTATION_PREF, "");
                    if (!apiKey.isEmpty() && !scan.sha256.isEmpty()) {
                        reputation = MalwareBazaarReputation.lookup(scan.sha256, apiKey);
                    }
                }
            } catch (Exception error) {
                failure = safeError(error);
            }

            AntivirusEngine.Result completedScan = scan;
            String completedApkReport = apkReport;
            MalwareBazaarReputation.Result completedReputation = reputation;
            String completedFailure = failure;
            String completedName = fileName;
            String completedMime = mime;
            runOnUiThread(() -> {
                scanButton.setEnabled(true);
                progress.setVisibility(View.GONE);
                currentName = completedName;
                currentMime = completedMime;
                currentResult = completedScan;
                lastKnownMalware = completedReputation != null && completedReputation.knownMalware;
                if (completedFailure != null || completedScan == null) {
                    resultText.setText("SCAN INCOMPLETE\n\n" + (completedFailure == null ? "No result was available." : completedFailure) +
                            "\n\nDo not treat this result as proof the file is safe.");
                    quarantineButton.setVisibility(View.GONE);
                    return;
                }
                StringBuilder report = new StringBuilder();
                report.append("File: ").append(safeDisplay(completedName)).append("\n");
                report.append("Result: ").append(lastKnownMalware ? "KNOWN_MALWARE_HASH" : completedScan.status).append("\n");
                report.append("Bytes scanned: ").append(completedScan.bytesScanned).append("\n");
                report.append("SHA-256: ").append(completedScan.sha256.isEmpty() ? "unavailable" : completedScan.sha256).append("\n\n");
                report.append(completedScan.details);
                if (!completedApkReport.isEmpty()) report.append("\n\nAPK PACKAGE REVIEW\n").append(completedApkReport);
                if (completedReputation != null) report.append("\n\nONLINE HASH REPUTATION\n").append(completedReputation.details);
                else if (!privateModeLaunch && secureStore.get(KEY_REPUTATION_PREF, "").isEmpty()) {
                    report.append("\n\nONLINE HASH REPUTATION\nNot configured. Local checks remain available; no file data was sent.");
                } else if (privateModeLaunch) {
                    report.append("\n\nONLINE HASH REPUTATION\nDisabled for this private browsing session.");
                }
                report.append("\n\nInterpretation: a warning indicates a risk signal requiring review, not proof of infection. A basic-check result or hash with no reputation match does not prove safety.");
                resultText.setText(report.toString());
                quarantineButton.setVisibility(completedScan.requiresReview() || lastKnownMalware ? View.VISIBLE : View.GONE);
            });
        });
    }

    private String inspectApk(Uri uri, long expectedSize) {
        if (expectedSize > DownloadedFileScanner.MAX_SCAN_BYTES) {
            return "APK manifest review skipped because the file exceeds the 100 MiB inspection limit.";
        }
        File temporary = null;
        try {
            temporary = File.createTempFile("hari-apk-review-", ".apk", getCacheDir());
            long bytes = 0;
            try (InputStream input = getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) return "Android could not reopen this APK for manifest review.";
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    bytes += count;
                    if (bytes > DownloadedFileScanner.MAX_SCAN_BYTES) {
                        return "APK manifest review stopped at the file-size limit.";
                    }
                    output.write(buffer, 0, count);
                }
                output.flush();
            }

            PackageInfo info = getPackageManager().getPackageArchiveInfo(
                    temporary.getAbsolutePath(),
                    PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNING_CERTIFICATES);
            if (info == null) {
                return "Android could not parse the APK package metadata. This is not itself a malware verdict.";
            }
            StringBuilder out = new StringBuilder();
            out.append("Package: ").append(info.packageName == null ? "unknown" : info.packageName);
            out.append("\nVersion: ").append(info.versionName == null ? "unknown" : info.versionName);
            List<String> reviewPermissions = new ArrayList<>();
            String[] permissions = info.requestedPermissions;
            if (permissions != null) {
                for (String permission : permissions) {
                    if (isHigherRiskPermission(permission)) reviewPermissions.add(permission);
                }
            }
            out.append("\nHigher-risk capabilities in a small review list: ").append(reviewPermissions.size());
            if (!reviewPermissions.isEmpty()) {
                for (String permission : reviewPermissions) out.append("\n• ").append(permission);
                out.append("\nReview these capabilities before installation; legitimate apps may need them.");
            } else {
                out.append("\nNo permission from the scanner's small review list was found. This does not prove the APK is safe.");
            }

            if (info.signingInfo != null) {
                Signature[] signatures = info.signingInfo.getApkContentsSigners();
                if (signatures != null && signatures.length > 0) {
                    out.append("\nSigner certificate SHA-256: ").append(certificateHash(signatures[0].toByteArray()));
                }
            }
            out.append("\nThis is static package metadata review, not dynamic execution or a malware verdict.");
            return out.toString();
        } catch (Exception error) {
            return "APK metadata review failed: " + safeError(error);
        } finally {
            if (temporary != null) temporary.delete();
        }
    }

    private boolean isHigherRiskPermission(String permission) {
        if (permission == null) return false;
        switch (permission) {
            case "android.permission.READ_SMS":
            case "android.permission.RECEIVE_SMS":
            case "android.permission.SEND_SMS":
            case "android.permission.RECEIVE_MMS":
            case "android.permission.READ_CALL_LOG":
            case "android.permission.WRITE_CALL_LOG":
            case "android.permission.READ_CONTACTS":
            case "android.permission.WRITE_CONTACTS":
            case "android.permission.RECORD_AUDIO":
            case "android.permission.CAMERA":
            case "android.permission.SYSTEM_ALERT_WINDOW":
            case "android.permission.REQUEST_INSTALL_PACKAGES":
            case "android.permission.READ_PHONE_STATE":
            case "android.permission.ACCESS_FINE_LOCATION":
            case "android.permission.ACCESS_COARSE_LOCATION":
            case "android.permission.RECEIVE_BOOT_COMPLETED":
            case "android.permission.READ_EXTERNAL_STORAGE":
            case "android.permission.MANAGE_EXTERNAL_STORAGE":
                return true;
            default:
                return false;
        }
    }

    private String certificateHash(byte[] certificate) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(certificate);
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte value : hash) out.append(String.format(Locale.ROOT, "%02X", value & 0xff));
            return out.toString();
        } catch (Exception ignored) {
            return "unavailable";
        }
    }

    private void confirmQuarantine() {
        if (currentUri == null || currentResult == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Quarantine this file?")
                .setMessage("Hari Antivirus will copy the file into app-private storage and verify its SHA-256. After that, it will ask Android's document provider to delete the original. Android may deny deletion; if so, the original will remain and you must remove it manually. Do not quarantine a file solely because a heuristic may be a false positive.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Quarantine", (dialog, which) -> quarantineCurrentFile())
                .show();
    }

    private void quarantineCurrentFile() {
        Uri source = currentUri;
        AntivirusEngine.Result scan = currentResult;
        String name = currentName;
        if (source == null || scan == null) return;
        scanButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        worker.execute(() -> {
            String message;
            try {
                QuarantineStore.SaveResult saved = QuarantineStore.copyVerified(this, source, name, scan.sha256);
                boolean originalDeleted = QuarantineStore.tryDeleteOriginal(this, source);
                message = "Verified quarantine copy saved in private app storage.\nSHA-256: " + saved.sha256 + "\nBytes: " + saved.bytes + "\n";
                if (originalDeleted) {
                    message += "\nAndroid reported that the original file was removed.";
                } else {
                    message += "\nAndroid did not allow Hari Antivirus to remove the original. The original may still be in Downloads or its original folder; delete it manually if you intend to remove it.";
                }
            } catch (Exception error) {
                message = "Quarantine failed: " + safeError(error) + "\nThe original file was not intentionally changed.";
            }
            String display = message;
            runOnUiThread(() -> {
                scanButton.setEnabled(true);
                progress.setVisibility(View.GONE);
                resultText.setText(display);
            });
        });
    }

    private void showQuarantineList() {
        List<File> files = QuarantineStore.list(this);
        if (files.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Quarantine").setMessage("No quarantined files are stored in Hari Antivirus.").setPositiveButton("Done", null).show();
            return;
        }
        String[] labels = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            labels[i] = QuarantineStore.displayName(file) + " · " + formatBytes(file.length());
        }
        new AlertDialog.Builder(this)
                .setTitle("Quarantined files")
                .setItems(labels, (dialog, which) -> showQuarantineEntryOptions(files.get(which)))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showQuarantineEntryOptions(File file) {
        String[] options = {"Restore a verified copy…", "Permanently delete quarantined copy"};
        new AlertDialog.Builder(this)
                .setTitle(QuarantineStore.displayName(file))
                .setMessage("Quarantined content is held in Hari Antivirus's private app storage. Do not restore a file unless you understand the risk.")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pendingRestore = file;
                        restoreFileLauncher.launch(QuarantineStore.displayName(file));
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Delete quarantined file?")
                                .setMessage("This permanently deletes the private quarantine copy. It does not affect any original copy that may still exist elsewhere.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete", (d, w) -> {
                                    boolean deleted = QuarantineStore.deleteQuarantined(this, file);
                                    showMessage(deleted ? "Quarantined copy deleted." : "Quarantined copy could not be deleted.");
                                }).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleAutomaticUrlReputation() {
        if (privateModeLaunch) {
            showMessage("Automatic reputation checks are never enabled in private browsing.");
            return;
        }
        SharedPreferences preferences = getSharedPreferences("hari_urlhaus_settings", MODE_PRIVATE);
        boolean enabled = preferences.getBoolean("auto_urlhaus_enabled", false);
        if (enabled) {
            preferences.edit().putBoolean("auto_urlhaus_enabled", false).apply();
            refreshReputationStatus();
            showMessage("Automatic URL reputation is off. Normal browsing URLs will no longer be sent to URLhaus.");
            return;
        }
        if (secureStore.get(KEY_REPUTATION_PREF, "").isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Configure URLhaus first")
                    .setMessage("Automatic checking requires an abuse.ch Auth-Key. Configure it first, then enable this setting. No URL has been sent.")
                    .setPositiveButton("Configure key", (dialog, which) -> configureReputation())
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Enable automatic URL checks?")
                .setMessage("When enabled, Hari Browser will send each new HTTPS destination URL you open in NORMAL browsing to URLhaus / abuse.ch before loading it. The service may log the URL and normal network metadata. Private browsing never sends URLs. URLhaus primarily identifies malware-distribution URLs, not every phishing or Indian payment scam. You can turn this off here at any time. Enable?")
                .setNegativeButton("Keep off", null)
                .setPositiveButton("Enable", (dialog, which) -> {
                    preferences.edit().putBoolean("auto_urlhaus_enabled", true).apply();
                    refreshReputationStatus();
                    showMessage("Automatic URL reputation enabled for normal browsing only. Each new destination may be shared with URLhaus before it opens.");
                })
                .show();
    }

    private void checkUrlReputation() {
        if (privateModeLaunch) {
            showMessage("Online website reputation checks are disabled in private browsing.");
            return;
        }
        String authKey = secureStore.get(KEY_REPUTATION_PREF, "");
        if (authKey.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Configure URL reputation")
                    .setMessage("First configure an abuse.ch Auth-Key using “Configure online hash reputation”. The same key supports the manual URLhaus query. No URL is sent until you paste a URL here and confirm the next dialog.")
                    .setPositiveButton("Configure key", (dialog, which) -> configureReputation())
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://example.com/path");
        new AlertDialog.Builder(this)
                .setTitle("Check a website URL")
                .setMessage("Paste the HTTPS URL you want checked. After you press “Send URL for check”, the URL (without any #fragment) will be sent to URLhaus / abuse.ch using your Auth-Key. URLhaus focuses on known malware-distribution URLs; it is not a complete phishing, scam, or general website safety service. The provider receives the request and may log normal connection metadata. Cancel sends nothing.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> {
                    String candidate = input.getText().toString().trim();
                    String validated = UrlhausReputation.validateHttpsUrl(candidate);
                    if (validated == null) {
                        showMessage("The address is invalid. Enter an HTTPS URL without a username or password in the address.");
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Send URL to URLhaus?")
                            .setMessage("Confirm that you want to send this website address to the external URLhaus / abuse.ch reputation service. It will not be saved in Hari Browser history by this screen. URLhaus results concern malware distribution, and a no-match result does not prove safety.")
                            .setNegativeButton("Do not send", null)
                            .setPositiveButton("Send URL for check", (confirmDialog, confirmWhich) -> performUrlhausLookup(validated))
                            .show();
                })
                .show();
    }

    private void performUrlhausLookup(String url) {
        String authKey = secureStore.get(KEY_REPUTATION_PREF, "");
        if (authKey.isEmpty()) {
            showMessage("No Auth-Key is configured. The URL was not sent.");
            return;
        }
        urlCheckButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        resultText.setText("Checking the URL with URLhaus after your confirmation…");
        worker.execute(() -> {
            UrlhausReputation.Result result = UrlhausReputation.lookup(url, authKey);
            runOnUiThread(() -> {
                urlCheckButton.setEnabled(true);
                progress.setVisibility(View.GONE);
                String heading;
                if (result.listed) {
                    heading = "⚠ KNOWN MALWARE-DISTRIBUTION URL";
                } else if ("NO_MATCH".equals(result.status)) {
                    heading = "NO URLHAUS RECORD FOUND — NOT A SAFETY VERDICT";
                } else {
                    heading = "URL REPUTATION CHECK INCOMPLETE";
                }
                resultText.setText(heading + "\n\n" + result.details +
                        "\n\nPrivacy: this screen does not save the checked URL to browser history. The external service received the URL because you explicitly confirmed the lookup.");
            });
        });
    }

    private void configureReputation() {
        if (privateModeLaunch) {
            showMessage("Online reputation checks are disabled when Hari Antivirus is opened from a private browsing session.");
            return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("MalwareBazaar Auth-Key");
        input.setText(secureStore.get(KEY_REPUTATION_PREF, ""));
        new AlertDialog.Builder(this)
                .setTitle("Optional hash reputation")
                .setMessage("Enter an Auth-Key from abuse.ch MalwareBazaar (create one at https://auth.abuse.ch/). When configured, Hari Antivirus sends only a file's SHA-256 hash to the service for a known-malware lookup; it does not upload the file or its filename. The provider still receives the network request and may log normal connection metadata. A no-match response does not mean safe. Leave blank and choose Disable to turn this off.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Disable", (dialog, which) -> {
                    secureStore.remove(KEY_REPUTATION_PREF);
                    getSharedPreferences("hari_urlhaus_settings", MODE_PRIVATE).edit()
                            .putBoolean("auto_urlhaus_enabled", false).apply();
                    refreshReputationStatus();
                    showMessage("Online reputation disabled. Automatic URL checks are off too.");
                })
                .setPositiveButton("Save key", (dialog, which) -> {
                    String key = input.getText().toString().trim();
                    if (key.length() < 16 || key.length() > 512) {
                        showMessage("The key was not saved. Paste the complete Auth-Key supplied by abuse.ch.");
                        return;
                    }
                    secureStore.put(KEY_REPUTATION_PREF, key);
                    refreshReputationStatus();
                    showMessage("Hash-only reputation lookup enabled. No key is included in the APK.");
                })
                .show();
    }

    private void refreshReputationStatus() {
        if (reputationStatus == null) return;
        boolean configured = !privateModeLaunch && !secureStore.get(KEY_REPUTATION_PREF, "").isEmpty();
        if (privateModeLaunch) {
            reputationStatus.setText("Online hash reputation: disabled in private browsing.");
            reputationButton.setVisibility(View.GONE);
            if (urlCheckButton != null) urlCheckButton.setVisibility(View.GONE);
            if (urlAutoCheckButton != null) urlAutoCheckButton.setVisibility(View.GONE);
        } else {
            if (urlCheckButton != null) urlCheckButton.setVisibility(View.VISIBLE);
            boolean autoEnabled = getSharedPreferences("hari_urlhaus_settings", MODE_PRIVATE)
                    .getBoolean("auto_urlhaus_enabled", false);
            if (urlAutoCheckButton != null) {
                urlAutoCheckButton.setVisibility(View.VISIBLE);
                urlAutoCheckButton.setText("Automatic URL reputation: " + (autoEnabled ? "ON" : "OFF"));
                urlAutoCheckButton.setEnabled(true);
            }
            reputationStatus.setText((configured
                    ? "Hash reputation: configured (SHA-256 only). "
                    : "Hash reputation: not configured. ") +
                    "Automatic URLhaus sharing: " + (autoEnabled ? "ON for normal browsing only." : "OFF."));
        }
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        String segment = uri.getLastPathSegment();
        return segment == null || segment.trim().isEmpty() ? "selected-file.bin" : segment;
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private String safeError(Exception error) {
        String message = error == null ? "" : error.getMessage();
        if (message == null || message.trim().isEmpty()) return error == null ? "Unknown error" : error.getClass().getSimpleName();
        String clean = message.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 240 ? clean.substring(0, 240) : clean;
    }

    private String safeDisplay(String value) {
        return value == null ? "selected file" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    private Button button(String text) {
        Button value = new Button(this);
        value.setText(text);
        value.setTextColor(INK);
        value.setAllCaps(false);
        value.setMinHeight(dp(48));
        return value;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        return params;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showMessage(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
