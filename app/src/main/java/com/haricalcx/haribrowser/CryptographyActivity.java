package com.haricalcx.haribrowser;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dedicated WebView screen for HARI's local cryptography utility.
 *
 * The page is served from WebViewAssetLoader's HTTPS-like origin so Web Crypto
 * can use a secure context. This WebView is locked to that one local document.
 * Its only JavaScript bridge operation is a bounded, user-confirmed SAF save.
 */
public final class CryptographyActivity extends ComponentActivity {
    private static final String ASSET_URL = CryptographySecurityPolicy.ASSET_URL;
    private static final int MAX_BRIDGE_BASE64_CHARS = 16 * 1024 * 1024;
    private static final int MAX_OUTPUT_BYTES = 12 * 1024 * 1024;

    private final ExecutorService fileWorker = Executors.newSingleThreadExecutor();
    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private ActivityResultLauncher<String> openFileLauncher;
    private ActivityResultLauncher<Intent> saveDocumentLauncher;
    private ValueCallback<Uri[]> pendingFileChooser;
    private PendingSave pendingSave;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        openFileLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    ValueCallback<Uri[]> callback = pendingFileChooser;
                    pendingFileChooser = null;
                    if (callback != null) {
                        callback.onReceiveValue(uri == null ? null : new Uri[]{uri});
                    }
                });

        saveDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onSaveDocumentResult);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true); // Required by the bundled cryptography page only.
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setGeolocationEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        webView.addJavascriptInterface(new CryptoFileBridge(), "HariCryptoBridge");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (pendingFileChooser != null) {
                    pendingFileChooser.onReceiveValue(null);
                }
                pendingFileChooser = filePathCallback;
                try {
                    openFileLauncher.launch("*/*");
                    return true;
                } catch (RuntimeException error) {
                    pendingFileChooser = null;
                    filePathCallback.onReceiveValue(null);
                    Toast.makeText(CryptographyActivity.this,
                            "Could not open the file picker.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (CryptographySecurityPolicy.isAllowedAssetUrl(uri.toString())) {
                    WebResourceResponse response = assetLoader.shouldInterceptRequest(uri);
                    if (response != null) return response;
                }
                // Deny every network or non-asset subresource request.
                return new WebResourceResponse("text/plain", "UTF-8",
                        new ByteArrayInputStream(new byte[0]));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // The tool is a single local document; no external navigation is allowed.
                return !CryptographySecurityPolicy.isAllowedAssetUrl(
                        request.getUrl().toString());
            }
        });

        setContentView(webView);
        webView.loadUrl(ASSET_URL);
    }

    private void onSaveDocumentResult(ActivityResult result) {
        PendingSave save = pendingSave;
        pendingSave = null;
        if (save == null) return;

        Intent data = result == null ? null : result.getData();
        Uri destination = result != null && result.getResultCode() == RESULT_OK
                && data != null ? data.getData() : null;
        if (destination == null) {
            save.encoded = null;
            notifyPage(false, "Save cancelled.", save.statusId);
            return;
        }

        String encoded = save.encoded;
        save.encoded = null;
        fileWorker.execute(() -> {
            byte[] decoded = null;
            try {
                decoded = Base64.decode(encoded, Base64.NO_WRAP);
                if (decoded.length > MAX_OUTPUT_BYTES) {
                    throw new IOException("Output exceeds the 12 MiB safety limit.");
                }
                try (OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                    if (output == null) throw new IOException("The selected location could not be opened.");
                    output.write(decoded);
                    output.flush();
                }
                notifyPage(true, "File saved successfully.", save.statusId);
            } catch (Exception error) {
                notifyPage(false, "Could not save the file. Choose another location and try again.",
                        save.statusId);
            } finally {
                if (decoded != null) Arrays.fill(decoded, (byte) 0);
            }
        });
    }

    private void notifyPage(boolean success, String message, String statusId) {
        final String safeStatus = "textStatus".equals(statusId) ? "textStatus" : "fileStatus";
        final String script = "window.hariCryptoSaveCompleted && window.hariCryptoSaveCompleted("
                + success + "," + JSONObject.quote(message) + ","
                + JSONObject.quote(safeStatus) + ");";
        runOnUiThread(() -> {
            if (webView != null && !isFinishing()) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    public final class CryptoFileBridge {
        @JavascriptInterface
        public void saveFileBase64(String encoded, String filename, String mimeType, String statusId) {
            String safeStatus = "textStatus".equals(statusId) ? "textStatus" : "fileStatus";
            if (encoded == null || encoded.isEmpty()
                    || encoded.length() > MAX_BRIDGE_BASE64_CHARS
                    || (encoded.length() & 3) != 0) {
                notifyPage(false, "Output is too large or invalid for the Android save dialog.",
                        safeStatus);
                return;
            }
            for (int i = 0; i < encoded.length(); i++) {
                char c = encoded.charAt(i);
                boolean valid = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                        || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=';
                if (!valid) {
                    notifyPage(false, "Output encoding was rejected.", safeStatus);
                    return;
                }
            }

            PendingSave candidate = new PendingSave(
                    encoded,
                    CryptographySecurityPolicy.safeFileName(filename),
                    CryptographySecurityPolicy.safeMimeType(mimeType),
                    safeStatus);
            runOnUiThread(() -> beginSave(candidate));
        }
    }

    private void beginSave(PendingSave candidate) {
        if (isFinishing() || isDestroyed()) {
            candidate.encoded = null;
            return;
        }
        if (pendingSave != null) {
            candidate.encoded = null;
            notifyPage(false, "A save operation is already in progress.", candidate.statusId);
            return;
        }
        pendingSave = candidate;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(candidate.mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, candidate.filename);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            saveDocumentLauncher.launch(intent);
        } catch (ActivityNotFoundException | IllegalStateException error) {
            pendingSave = null;
            candidate.encoded = null;
            notifyPage(false, "Android could not open a save-location picker.", candidate.statusId);
        }
    }

    @Override
    protected void onDestroy() {
        if (pendingFileChooser != null) {
            pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = null;
        }
        if (pendingSave != null) {
            pendingSave.encoded = null;
            pendingSave = null;
        }
        fileWorker.shutdownNow();
        if (webView != null) {
            webView.removeJavascriptInterface("HariCryptoBridge");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private static final class PendingSave {
        String encoded;
        final String filename;
        final String mimeType;
        final String statusId;

        PendingSave(String encoded, String filename, String mimeType, String statusId) {
            this.encoded = encoded;
            this.filename = filename;
            this.mimeType = mimeType;
            this.statusId = statusId;
        }
    }
}