package com.haricalcx.haribrowser;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import android.webkit.PermissionRequest;
import android.webkit.SafeBrowsingResponse;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class BrowserActivity extends ComponentActivity {
    private static int BG = Color.rgb(244, 247, 250);
    private static int CARD = Color.WHITE;
    private static int INK = Color.rgb(17, 29, 48);
    private static int MUTED = Color.rgb(102, 116, 136);
    private static int ACCENT = Color.rgb(22, 184, 166);
    private static int OUTLINE = Color.rgb(222, 229, 237);
    private static final String HOME_URL = "https://hari-browser-home.invalid/";
    private static final String KEY_BOOKMARKS = "bookmarks_v1";
    private static final String KEY_HISTORY = "history_v1";
    private static final String KEY_READ_LATER = "read_later_v1";
    private static final String KEY_HARI_AI_API_KEY = "hari_ai_api_key_v1";
    private static final int MAX_HARI_AI_PROMPT_CHARS = 6000;

    private SharedPreferences settings;
    private SecureStore secureStore;
    private SharedPreferences protectionSettings;
    private SecureStore protectionStore;
    private boolean privateMode;
    private LinearLayout root;
    private EditText addressBar;
    private FrameLayout webContainer;
    private ProgressBar progressBar;
    private TextView modeBadge;
    private TextView tabButton;
    private TextView securityStatus;
    private final ArrayList<Tab> tabs = new ArrayList<>();
    private int currentTabIndex = -1;
    private String defaultUserAgent;
    private ActivityResultLauncher<String> fileChooserLauncher;
    private ActivityResultLauncher<Intent> voiceSearchLauncher;
    private ValueCallback<Uri[]> fileChooserCallback;
    private View fullscreenCustomView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private int previousSystemUiVisibility;
    private final ArrayList<String> recentlyClosedUrls = new ArrayList<>();
    private static ArrayList<String> pendingThemeRestoreUrls;
    private static int pendingThemeRestoreIndex;
    private static boolean pendingThemeRestorePrivateMode;
    // Session-only approvals avoid repeated prompts for a URL the user already reviewed.
    private static final java.util.Set<String> APPROVED_SITE_URLS =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    // Opt-in online URL reputation results remain in memory only and never enter browser history.
    private final Set<String> pendingUrlhausChecks = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, UrlhausReputation.Result> urlhausSessionResults =
            Collections.synchronizedMap(new HashMap<>());

    private String ui(String english) {
        return BrowserUiText.translate(settings.getString("home_language", "en"), english);
    }

    private void startVoiceSearch() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                BrowserUiText.speechLocale(settings.getString("home_language", "en")));
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, ui("Speak your search"));
        try {
            voiceSearchLauncher.launch(intent);
        } catch (RuntimeException unavailable) {
            showToast(ui("Voice search isn't available on this device."));
        }
    }

    private boolean darkTheme() { return settings.getBoolean("dark_theme", false); }
    private boolean trackersEnabled() { return settings.getBoolean("block_trackers", true); }
    private boolean javascriptEnabled() { return settings.getBoolean("javascript", true); }
    private boolean desktopEnabled() { return settings.getBoolean("desktop_mode", false); }
    private boolean cookiesEnabled() { return settings.getBoolean("cookies_enabled", true); }
    private boolean historyEnabled() { return settings.getBoolean("history_enabled", false); }
    private boolean fingerprintProtectionEnabled() { return settings.getBoolean("fingerprint_protection", true); }
    private boolean teenProtectionEnabled() {
        if (!privateMode) return TeenProtection.isEnabled(protectionSettings);
        try {
            // Private browsing runs in a separate process with a separate WebView profile.
            // Ask the main-process provider for the authoritative setting instead of trusting
            // a potentially stale SharedPreferences cache in the private process.
            Bundle state = getContentResolver().call(TeenProtectionProvider.CONTENT_URI,
                    TeenProtectionProvider.METHOD_IS_ENABLED, null, null);
            if (state != null && state.containsKey(TeenProtectionProvider.RESULT_ENABLED)) {
                return state.getBoolean(TeenProtectionProvider.RESULT_ENABLED);
            }
        } catch (Exception ignored) {}
        // If state sharing fails, private browsing fails closed rather than bypassing protection.
        return true;
    }
    private boolean imagesEnabled() { return settings.getBoolean("images_enabled", true); }
    private boolean dataSaverEnabled() { return settings.getBoolean("lite_mode_enabled", false); }
    private int textZoom() { return settings.getInt("text_zoom", 100); }
    private String privacyReducedUserAgent() {
        // Preserve the current WebView engine version while removing model/build identifiers.
        return defaultUserAgent.replaceAll("(?i)Android [0-9.]+; [^;)]+", "Android 14; K");
    }
    private String searchEngine() { return settings.getString("search_engine", "DuckDuckGo"); }
    private String accentName() { return settings.getString("accent_color", "Ocean"); }
    private int accentColor() {
        switch (accentName()) {
            case "Violet": return Color.rgb(124, 58, 237);
            case "Mint": return Color.rgb(5, 150, 105);
            case "Sunset": return Color.rgb(234, 88, 12);
            case "Rose": return Color.rgb(225, 29, 72);
            default: return Color.rgb(37, 99, 235);
        }
    }

    private void applyThemePalette() {
        if (darkTheme()) {
            BG = Color.rgb(8, 12, 25);
            CARD = Color.rgb(18, 25, 45);
            INK = Color.rgb(238, 243, 255);
            MUTED = Color.rgb(151, 164, 189);
            ACCENT = accentColor();
            OUTLINE = Color.rgb(43, 55, 81);
        } else {
            BG = Color.rgb(246, 247, 255);
            CARD = Color.WHITE;
            INK = Color.rgb(22, 31, 55);
            MUTED = Color.rgb(101, 113, 137);
            ACCENT = accentColor();
            OUTLINE = Color.rgb(220, 224, 239);
        }
    }

    private static final class HariAiMessage {
        final String role;
        final String text;
        HariAiMessage(String role, String text) { this.role = role; this.text = text; }
    }

    private static final class Tab {
        WebView webView;
        String title = "New tab";
        String url = HOME_URL;
        boolean home = true;
        boolean readerMode = false;
        long navigationAttempt = 0L;
        ScriptHandler fingerprintScriptHandler;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        privateMode = this instanceof PrivateActivity;
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fullscreenCustomView != null) {
                    hideFullscreenCustomView();
                    return;
                }
                WebView web = currentWebView();
                if (web != null && web.canGoBack()) {
                    web.goBack();
                    return;
                }
                if (privateMode) {
                    finish();
                    return;
                }
                Tab tab = currentTab();
                if (tab != null && !tab.home) {
                    loadHome(tab);
                    return;
                }
                setEnabled(false);
                try {
                    getOnBackPressedDispatcher().onBackPressed();
                } finally {
                    setEnabled(true);
                }
            }
        });
        settings = getSharedPreferences(privateMode ? "hari_browser_private_settings" : "hari_browser_settings", MODE_PRIVATE);
        if (privateMode) settings.edit().putBoolean("private_session_active", true).apply();
        // Shared by normal and private sessions so private browsing cannot bypass protection.
        protectionSettings = getSharedPreferences("hari_browser_protection_settings", MODE_PRIVATE);
        protectionStore = new SecureStore(this, false);
        applyThemePalette();
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (fileChooserCallback != null) {
                        fileChooserCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
                        fileChooserCallback = null;
                    }
                });
        voiceSearchLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != android.app.Activity.RESULT_OK) return;
                    Intent voiceResult = result.getData();
                    ArrayList<String> matches = voiceResult == null ? null :
                            voiceResult.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches == null || matches.isEmpty() || matches.get(0).trim().isEmpty()) {
                        showToast(ui("No speech was recognized."));
                        return;
                    }
                    if (addressBar != null) {
                        addressBar.setText(matches.get(0).trim());
                        navigateFromAddressBar();
                    }
                });
        secureStore = new SecureStore(this, privateMode);
        defaultUserAgent = WebSettings.getDefaultUserAgent(this);
        WebView.setWebContentsDebuggingEnabled(false);

        Window window = getWindow();
        if (privateMode) {
            // Hide page contents from screenshots, screen recording, and app-switcher previews.
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        }
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        window.setNavigationBarDividerColor(BG);
        window.getDecorView().setSystemUiVisibility(darkTheme() ? 0 :
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        buildInterface();

        Intent intent = getIntent();
        if (pendingThemeRestoreUrls != null && pendingThemeRestorePrivateMode == privateMode) {
            ArrayList<String> restoreUrls = pendingThemeRestoreUrls;
            int restoreIndex = pendingThemeRestoreIndex;
            pendingThemeRestoreUrls = null;
            pendingThemeRestoreIndex = 0;
            for (String url : restoreUrls) {
                if (url == null || url.isEmpty()) addNewTab(null, true);
                else addNewTab(url, true);
            }
            if (!tabs.isEmpty()) selectTab(Math.max(0, Math.min(restoreIndex, tabs.size() - 1)));
        } else if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            addNewTab(intent.getData().toString(), true);
        } else {
            addNewTab(null, true);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Keep each WebView alive while rebuilding the UI for the new orientation.
        if (fullscreenCustomView != null || tabs.isEmpty()) return;
        int selected = Math.max(0, Math.min(currentTabIndex, tabs.size() - 1));
        buildInterface();
        selectTab(selected);
    }

    private void buildInterface() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int widthDp = getResources().getConfiguration().screenWidthDp;
        boolean compact = widthDp < 380;
        int horizontalPadding = compact ? 8 : 12;
        root.setPadding(dp(horizontalPadding), dp(2), dp(horizontalPadding), dp(1));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = Math.max(0, insets.getSystemWindowInsetTop());
            int bottomInset = Math.max(0, insets.getSystemWindowInsetBottom());
            root.setPadding(dp(horizontalPadding), dp(2) + topInset,
                    dp(horizontalPadding), dp(1) + bottomInset);
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView brand = new TextView(this);
        brand.setText("◈  HARI BROWSER");
        brand.setTextColor(INK);
        brand.setTextSize(widthDp < 380 ? 11 : (widthDp < 480 ? 12 : 14));
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setSingleLine(true);
        brand.setEllipsize(TextUtils.TruncateAt.END);
        brand.setIncludeFontPadding(false);
        brand.setAutoSizeTextTypeUniformWithConfiguration(10, 15, 1, TypedValue.COMPLEX_UNIT_SP);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(29), 1));

        modeBadge = new TextView(this);
        // A compact visible label avoids clipping at large system font sizes and in landscape.
        modeBadge.setText(privateMode ? "PRIVATE" : "STANDARD");
        modeBadge.setContentDescription(privateMode ? "Private browsing session" : "Standard browsing mode");
        modeBadge.setSingleLine(true);
        modeBadge.setMaxLines(1);
        modeBadge.setEllipsize(TextUtils.TruncateAt.END);
        modeBadge.setGravity(Gravity.CENTER);
        modeBadge.setIncludeFontPadding(false);
        modeBadge.setMaxWidth(dp(compact ? 88 : 136));
        modeBadge.setTextSize(9);
        modeBadge.setAutoSizeTextTypeUniformWithConfiguration(8, 9, 1, TypedValue.COMPLEX_UNIT_SP);
        modeBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        modeBadge.setTextColor(privateMode ? Color.WHITE : Color.rgb(0, 105, 94));
        modeBadge.setPadding(dp(compact ? 4 : 6), dp(compact ? 3 : 4),
                dp(compact ? 4 : 6), dp(compact ? 3 : 4));
        modeBadge.setBackground(round(privateMode ? Color.rgb(94, 70, 174) : Color.rgb(216, 247, 241), 0, 12));
        header.addView(modeBadge);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(31)));

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setPadding(dp(7), dp(2), dp(3), dp(2));
        addressRow.setBackground(round(CARD, OUTLINE, 15));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextColor(INK);
        addressBar.setHintTextColor(MUTED);
        addressBar.setHint(ui("Search or enter an HTTPS address"));
        addressBar.setContentDescription(ui("Search privately or enter a secure HTTPS website address"));
        addressBar.setTextSize(compact ? 12 : (widthDp < 480 ? 13 : 14));
        addressBar.setAutoSizeTextTypeUniformWithConfiguration(11, 15, 1, TypedValue.COMPLEX_UNIT_SP);
        addressBar.setSelectAllOnFocus(false);
        addressBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setPadding(dp(6), 0, dp(4), 0);
        addressBar.setBackgroundColor(Color.TRANSPARENT);
        addressRow.addView(addressBar, new LinearLayout.LayoutParams(0, dp(36), 1));

        TextView voiceButton = compactButton("🎙", ui("Voice search"), () -> startVoiceSearch());
        voiceButton.setTextSize(14);
        addressRow.addView(voiceButton, new LinearLayout.LayoutParams(dp(36), dp(29)));
        TextView goButton = pillButton(ui("GO"), true, () -> navigateFromAddressBar());
        addressRow.addView(goButton, new LinearLayout.LayoutParams(dp(42), dp(29)));
        root.addView(addressRow, new LinearLayout.LayoutParams(-1, dp(42)));

        addressBar.setOnEditorActionListener((view, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                            event.getAction() == KeyEvent.ACTION_DOWN);
            if (go) {
                navigateFromAddressBar();
                return true;
            }
            return false;
        });

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(0, dp(1), 0, dp(1));
        addCompactButton(controls, "‹", ui("Back"), () -> {
            WebView w = currentWebView();
            if (w != null && w.canGoBack()) w.goBack();
        });
        addCompactButton(controls, "›", ui("Forward"), () -> {
            WebView w = currentWebView();
            if (w != null && w.canGoForward()) w.goForward();
        });
        TextView reloadButton = compactButton("↻", ui("Reload page (long press to stop)"), () -> {
            WebView w = currentWebView();
            if (w != null) {
                if (currentTab().home) loadHome(currentTab());
                else w.reload();
            }
        });
        reloadButton.setOnLongClickListener(v -> {
            WebView w = currentWebView();
            if (w != null) {
                w.stopLoading();
                progressBar.setVisibility(View.GONE);
                showToast(ui("Loading stopped."));
            }
            return true;
        });
        controls.addView(reloadButton, new LinearLayout.LayoutParams(0, dp(29), 1));
        tabButton = compactButton(ui("Tabs") + " 1", ui("Open tab switcher"), () -> showTabSwitcher());
        controls.addView(tabButton, new LinearLayout.LayoutParams(0, dp(29), 1));
        addCompactButton(controls, "□", ui("New tab"), () -> addNewTab(null, true));
        addCompactButton(controls, "⋮", ui("Menu"), () -> showSettings());
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(32)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(-1, dp(3)));

        webContainer = new FrameLayout(this);
        webContainer.setBackground(round(CARD, OUTLINE, 15));
        root.addView(webContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        securityStatus = new TextView(this);
        securityStatus.setTextColor(MUTED);
        securityStatus.setTextSize(9);
        securityStatus.setSingleLine(true);
        securityStatus.setEllipsize(TextUtils.TruncateAt.START);
        securityStatus.setGravity(Gravity.CENTER_VERTICAL);
        securityStatus.setPadding(dp(3), dp(2), dp(3), dp(2));
        root.addView(securityStatus, new LinearLayout.LayoutParams(-1, dp(15)));
        securityStatus.setContentDescription(ui("Tap to view site security information"));
        securityStatus.setOnClickListener(v -> showSiteSecurity());

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(0, dp(1), 0, 0);
        addBottomButton(bottom, "⌂\n" + ui("Home"), () -> {
            Tab t = currentTab();
            if (t != null) loadHome(t);
        });
        addBottomButton(bottom, "★\n" + ui("Save"), () -> addBookmark());
        addBottomButton(bottom, "◷\n" + ui("History"), () -> showHistory());
        addBottomButton(bottom, "⌕\n" + ui("Find"), () -> showFindDialog());
        addBottomButton(bottom, "⚙\n" + ui("Settings"), () -> showSettings());
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(compact ? 39 : 37)));

        setContentView(root);
        root.requestApplyInsets();
        refreshModeBadge();
    }

    private void addCompactButton(LinearLayout parent, String icon, String description, Runnable action) {
        parent.addView(compactButton(icon, description, action),
                new LinearLayout.LayoutParams(0, dp(29), 1));
    }

    private TextView compactButton(String text, String description, Runnable action) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setContentDescription(description);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(INK);
        int widthDp = getResources().getConfiguration().screenWidthDp;
        view.setTextSize(text.length() > 2 ? (widthDp < 380 ? 9 : 10)
                : (widthDp < 380 ? 15 : 17));
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setBackground(round(Color.WHITE, OUTLINE, 11));
        view.setPadding(dp(1), dp(1), dp(1), dp(1));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private TextView pillButton(String text, boolean primary, Runnable action) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(13);
        view.setAutoSizeTextTypeUniformWithConfiguration(10, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(primary ? Color.WHITE : INK);
        view.setBackground(round(primary ? ACCENT : CARD, primary ? ACCENT : OUTLINE, 12));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void addBottomButton(LinearLayout parent, String text, Runnable action) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(9);
        view.setAutoSizeTextTypeUniformWithConfiguration(7, 9, 1, TypedValue.COMPLEX_UNIT_SP);
        view.setLineSpacing(0f, 0.9f);
        view.setIncludeFontPadding(false);
        view.setMaxLines(2);
        view.setTextColor(INK);
        view.setPadding(dp(1), dp(1), dp(1), dp(1));
        view.setBackground(round(Color.WHITE, OUTLINE, 12));
        view.setOnClickListener(v -> action.run());
        int itemHeight = getResources().getConfiguration().screenWidthDp < 380 ? 35 : 34;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(itemHeight), 1);
        lp.setMargins(dp(1), 0, dp(1), 0);
        parent.addView(view, lp);
    }

    private GradientDrawable round(int color, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private Tab currentTab() {
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) return null;
        return tabs.get(currentTabIndex);
    }

    private WebView currentWebView() {
        Tab t = currentTab();
        return t == null ? null : t.webView;
    }

    private void addNewTab(String initialUrl, boolean loadInitial) {
        Tab tab = new Tab();
        WebView webView = new WebView(this);
        tab.webView = webView;
        configureWebView(tab);
        tabs.add(tab);
        selectTab(tabs.size() - 1);
        if (loadInitial) {
            if (initialUrl == null || initialUrl.trim().isEmpty()) loadHome(tab);
            else navigateTo(tab, initialUrl);
        }
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        currentTabIndex = index;
        Tab tab = tabs.get(index);
        if (tab.webView.getParent() != null) {
            ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
        }
        webContainer.removeAllViews();
        webContainer.addView(tab.webView, new FrameLayout.LayoutParams(-1, -1));
        if (tab.home) addressBar.setText("");
        else if (tab.url != null) addressBar.setText(tab.url);
        updateTabControls();
    }

    private void updateTabControls() {
        if (tabButton != null) tabButton.setText(ui("Tabs") + " " + tabs.size());
        Tab tab = currentTab();
        if (tab != null && securityStatus != null) {
            String message = privateMode ? "Private • history not saved" :
                    (tab.home ? "Ready • HTTPS-only browsing" : "HTTPS • " + safeHost(tab.url));
            String officialService = tab.home || tab.url == null ? null :
                    OfficialDomainRegistry.serviceForHost(safeHost(tab.url));
            if (officialService != null) {
                message += " • Listed official: " + officialService;
            }
            message += fingerprintProtectionActive(tab) ? " • FP shield ON" :
                    (fingerprintProtectionEnabled() ? " • FP shield unavailable" : " • FP shield OFF");
            securityStatus.setText(message);
            securityStatus.setTextColor(officialService != null ? Color.rgb(0, 105, 94) : MUTED);
            String qualification = officialService != null
                    ? " This is a curated domain-list match, not a guarantee that the page or transaction is safe."
                    : "";
            securityStatus.setContentDescription(message + "." + qualification
                    + " Tap for site security details.");
        }
    }

    private void configureWebView(Tab tab) {
        WebView webView = tab.webView;
        WebSettings web = webView.getSettings();
        web.setJavaScriptEnabled(javascriptEnabled());
        web.setBlockNetworkImage(PrivacyGuard.shouldBlockNetworkImages(
                tab.home, imagesEnabled(), dataSaverEnabled()));
        web.setTextZoom(textZoom());
        web.setDomStorageEnabled(true);
        web.setAllowFileAccess(false);
        web.setAllowContentAccess(false);
        web.setAllowFileAccessFromFileURLs(false);
        web.setAllowUniversalAccessFromFileURLs(false);
        web.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setSaveFormData(false);
        web.setMediaPlaybackRequiresUserGesture(true);
        web.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setSupportMultipleWindows(false);
        web.setGeolocationEnabled(false);
        web.setCacheMode(privateMode ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
        web.setSafeBrowsingEnabled(true);
        web.setUserAgentString(desktopEnabled() ? privacyReducedUserAgent().replace(" Mobile", "") : privacyReducedUserAgent());
        updateFingerprintProtection(tab);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(cookiesEnabled());
        cookies.setAcceptThirdPartyCookies(webView, false);

        webView.setBackgroundColor(Color.WHITE);
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri requestUri = request.getUrl();
                if (request.isForMainFrame() && teenProtectionEnabled()
                        && TeenProtection.isBlockedUrl(requestUri.toString())) {
                    runOnUiThread(() -> showToast("Teen Protection blocked this search or website."));
                    String blockedPage = "<!doctype html><meta charset=\"utf-8\">"
                            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                            + "<title>Browsing blocked</title><h2>Teen Protection</h2>"
                            + "<p>This search or website matches a restricted age-safety rule covering selected explicit, gambling, graphic-violence, self-harm-instruction, weapon/explosive-instruction, or illegal-drug queries.</p>"
                            + "<p>Try a learning-focused or age-appropriate destination.</p>";
                    return new WebResourceResponse("text/html", "UTF-8", 200, "OK",
                            Collections.singletonMap("Cache-Control", "no-store"),
                            new ByteArrayInputStream(blockedPage.getBytes(StandardCharsets.UTF_8)));
                }
                String requestPath = requestUri.getPath();
                if ("https".equalsIgnoreCase(requestUri.getScheme())
                        && "hari-browser-home.invalid".equalsIgnoreCase(requestUri.getHost())
                        && requestPath != null
                        && requestPath.startsWith("/assets/wallpapers/")) {
                    String fileName = requestPath.substring("/assets/wallpapers/".length());
                    if (!isBundledWallpaperName(fileName)) {
                        return new WebResourceResponse("text/plain", "UTF-8",
                                new ByteArrayInputStream(new byte[0]));
                    }
                    try {
                        // Serve only explicitly allowed packaged JPEGs; never expose arbitrary files.
                        return new WebResourceResponse("image/jpeg", null,
                                getAssets().open("wallpapers/" + fileName));
                    } catch (IOException ignored) {
                        return new WebResourceResponse("text/plain", "UTF-8",
                                new ByteArrayInputStream(new byte[0]));
                    }
                }
                return PrivacyGuard.intercept(request, trackersEnabled() || dataSaverEnabled());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("https".equalsIgnoreCase(scheme) &&
                        "hari-browser-home.invalid".equalsIgnoreCase(uri.getHost())) {
                    // Do not let an ordinary website forge an internal action URL.
                    if (tab.home) {
                        String path = uri.getPath();
                        if (path != null && path.startsWith("/action/")) {
                            String action = path.substring("/action/".length());
                            runOnUiThread(() -> handleHomeAction(action));
                        }
                    } else {
                        runOnUiThread(() -> showToast("Blocked an invalid internal browser action link."));
                    }
                    // Never send the synthetic home URL to the public internet.
                    return true;
                }
                if ("http".equalsIgnoreCase(scheme)) {
                    runOnUiThread(() -> showHttpsUpgradePrompt(tab, url));
                    return true;
                }
                if (!"https".equalsIgnoreCase(scheme) || uri.getHost() == null) {
                    runOnUiThread(() -> showToast("This link type is blocked for your safety."));
                    return true;
                }
                String protectedUrl = protectSearchUrl(url);
                if (protectedUrl == null) return true;
                // Keep same-site in-page navigation smooth; scan before opening a new destination.
                if (!request.isForMainFrame()) {
                    view.getSettings().setJavaScriptEnabled(javascriptEnabled());
                    return false;
                }
                SiteSafetyChecker.Report urlReport = SiteSafetyChecker.inspect(protectedUrl);
                if (tab.url != null && !safeHost(tab.url).isEmpty()
                        && safeHost(tab.url).equalsIgnoreCase(safeHost(protectedUrl))
                        && !urlReport.hasWarnings()) {
                    view.getSettings().setJavaScriptEnabled(javascriptEnabled());
                    return false;
                }
                runOnUiThread(() -> showSiteSafetyCheck(tab, protectedUrl));
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                tab.url = url == null ? "" : url;
                tab.home = HOME_URL.equals(url) || "about:blank".equals(url);
                if (currentTab() == tab) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(3);
                    updateTabControls();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                tab.url = url == null ? "" : url;
                tab.title = view.getTitle() == null || view.getTitle().trim().isEmpty()
                        ? safeHost(tab.url) : view.getTitle();
                tab.home = HOME_URL.equals(url) || "about:blank".equals(url);
                if (!privateMode && historyEnabled() && !tab.home && PrivacyGuard.isHttpsUrl(url)) {
                    rememberHistory(tab.title, url);
                }
                if (currentTab() == tab) {
                    progressBar.setVisibility(View.GONE);
                    if (tab.home) addressBar.setText("");
                    else addressBar.setText(url);
                    updateTabControls();
                }
                if (tab.readerMode) applyReaderStyles(tab);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                runOnUiThread(() -> showToast("Blocked: the site's security certificate is invalid."));
            }

            @Override
            public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType,
                                          SafeBrowsingResponse callback) {
                callback.backToSafety(true);
                runOnUiThread(() -> new AlertDialog.Builder(BrowserActivity.this)
                        .setTitle("Dangerous site blocked")
                        .setMessage("Android Safe Browsing identified this page as potentially unsafe. Hari Browser returned to safety.")
                        .setPositiveButton("OK", null)
                        .show());
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    runOnUiThread(() -> showToast("Page could not load. Check the address or connection."));
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                runOnUiThread(() -> {
                    showToast("A web page crashed and was closed.");
                    if (tab.webView != null) {
                        if (tab.webView.getParent() instanceof ViewGroup) {
                            ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
                        }
                        try { tab.webView.destroy(); } catch (Exception ignored) {}
                        tab.webView = new WebView(BrowserActivity.this);
                        configureWebView(tab);
                        if (currentTab() == tab) {
                            webContainer.removeAllViews();
                            webContainer.addView(tab.webView, new FrameLayout.LayoutParams(-1, -1));
                            loadHome(tab);
                        }
                    }
                });
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (currentTab() == tab) {
                    progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                    progressBar.setProgress(newProgress);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title != null && !title.trim().isEmpty()) tab.title = title;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // Camera, microphone, MIDI, and other site permissions are denied by default.
                request.deny();
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                String mime = "*/*";
                try {
                    String[] accepted = params.getAcceptTypes();
                    if (accepted != null && accepted.length > 0 && accepted[0] != null &&
                            !accepted[0].trim().isEmpty()) mime = accepted[0];
                } catch (Exception ignored) {}
                try {
                    fileChooserLauncher.launch(mime);
                    return true;
                } catch (Exception e) {
                    fileChooserCallback = null;
                    callback.onReceiveValue(null);
                    showToast("The system file picker could not be opened.");
                    return true;
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenCustomView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenCustomView = view;
                fullscreenCallback = callback;
                previousSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
                root.setVisibility(View.GONE);
                FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                decor.addView(view, new FrameLayout.LayoutParams(-1, -1));
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreenCustomView();
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                requestDownload(url, contentDisposition, mimeType));
    }

    private void hideFullscreenCustomView() {
        if (fullscreenCustomView == null) return;
        try {
            ViewGroup parent = (ViewGroup) fullscreenCustomView.getParent();
            if (parent != null) parent.removeView(fullscreenCustomView);
        } catch (Exception ignored) {}
        fullscreenCustomView = null;
        if (fullscreenCallback != null) {
            try { fullscreenCallback.onCustomViewHidden(); } catch (Exception ignored) {}
            fullscreenCallback = null;
        }
        if (root != null) root.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(previousSystemUiVisibility);
    }

    private void navigateFromAddressBar() {
        String input = addressBar.getText().toString().trim();
        if (input.isEmpty()) {
            Tab tab = currentTab();
            if (tab != null) loadHome(tab);
            return;
        }
        String resolved = resolveInput(input);
        if (resolved == null) return;
        hideKeyboard();
        navigateTo(currentTab(), resolved);
    }

    private String resolveInput(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://")) {
            showToast("HTTP is blocked. Hari Browser requires HTTPS.");
            return null;
        }
        if (lower.startsWith("https://")) {
            if (PrivacyGuard.isHttpsUrl(input)) return input;
            showToast("Enter a valid HTTPS web address.");
            return null;
        }
        if (input.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) {
            showToast("Only HTTPS web addresses are allowed.");
            return null;
        }
        if (input.contains(" ") || !input.contains(".")) {
            try {
                String encoded = URLEncoder.encode(input, "UTF-8");
                String engine = searchEngine();
                if ("Startpage".equals(engine)) return "https://www.startpage.com/sp/search?query=" + encoded;
                if ("Brave Search".equals(engine)) return "https://search.brave.com/search?q=" + encoded;
                if ("Google".equals(engine)) return "https://www.google.co.in/search?q=" + encoded;
                if ("Bing".equals(engine)) return "https://www.bing.com/search?q=" + encoded;
                return "https://duckduckgo.com/?q=" + encoded;
            } catch (Exception e) {
                return "https://duckduckgo.com/";
            }
        }
        return "https://" + input;
    }

    private void navigateTo(Tab tab, String url) {
        if (tab == null || tab.webView == null) return;
        if (!PrivacyGuard.isHttpsUrl(url)) {
            showToast("Blocked: only HTTPS pages can be opened.");
            return;
        }
        String protectedUrl = protectSearchUrl(url);
        if (protectedUrl == null) return;
        // Every address-bar, bookmark, history and app-link navigation gets a local preflight.
        showSiteSafetyCheck(tab, protectedUrl);
    }

    private void loadAfterSiteSafetyCheck(Tab tab, String url) {
        if (tab == null || tab.webView == null || !PrivacyGuard.isHttpsUrl(url)) return;
        tab.home = false;
        tab.readerMode = false;
        tab.webView.getSettings().setJavaScriptEnabled(javascriptEnabled());
        tab.webView.getSettings().setBlockNetworkImage(!imagesEnabled() || dataSaverEnabled());
        tab.url = url;
        tab.webView.loadUrl(url);
        updateTabControls();
    }

    private void showSiteSafetyCheck(Tab tab, String url) {
        if (tab == null || tab.webView == null || !PrivacyGuard.isHttpsUrl(url)) return;
        final long attempt = ++tab.navigationAttempt;
        boolean onlineChecksEnabled = !privateMode &&
                getSharedPreferences("hari_urlhaus_settings", MODE_PRIVATE)
                        .getBoolean("auto_urlhaus_enabled", false);
        if (onlineChecksEnabled) {
            UrlhausReputation.Result cached = urlhausSessionResults.get(url);
            if (cached != null) {
                showSiteSafetyCheckDialog(tab, url, cached, attempt);
                return;
            }
            String authKey = protectionStore.get("hari_rep_store_1", "");
            if (!authKey.isEmpty()) {
                if (!pendingUrlhausChecks.add(url)) {
                    showToast("URL reputation check is already running. Please wait.");
                    return;
                }
                showToast("Checking this destination with URLhaus…");
                new Thread(() -> {
                    UrlhausReputation.Result reputation;
                    try {
                        reputation = UrlhausReputation.lookup(url, authKey);
                    } catch (Exception error) {
                        reputation = new UrlhausReputation.Result(false, "SERVICE_UNAVAILABLE",
                                "Online URL reputation was unavailable. Local URL checks remain active.");
                    } finally {
                        pendingUrlhausChecks.remove(url);
                    }
                    if ("KNOWN_MALWARE_URL".equals(reputation.status) || "NO_MATCH".equals(reputation.status)) {
                        urlhausSessionResults.put(url, reputation);
                    }
                    UrlhausReputation.Result finalReputation = reputation;
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || tab.navigationAttempt != attempt ||
                                !tabs.contains(tab)) return;
                        showSiteSafetyCheckDialog(tab, url, finalReputation, attempt);
                    });
                }, "HariUrlReputation").start();
                return;
            }
        }
        if (APPROVED_SITE_URLS.contains(url)) {
            loadAfterSiteSafetyCheck(tab, url);
            return;
        }
        showSiteSafetyCheckDialog(tab, url, null, attempt);
    }

    private void showSiteSafetyCheckDialog(Tab tab, String url,
                                           UrlhausReputation.Result reputation, long attempt) {
        if (tab == null || tab.webView == null || tab.navigationAttempt != attempt ||
                !PrivacyGuard.isHttpsUrl(url)) return;
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect(url);
        StringBuilder message = new StringBuilder();
        message.append("Destination: ").append(report.getHost()).append("\n\n");
        message.append("LOCAL URL CHECK\n");
        for (String check : report.getPassedChecks()) {
            message.append("✓ ").append(check).append("\n");
        }
        if (!report.getWarnings().isEmpty()) {
            message.append("\nREVIEW THESE SIGNALS\n");
            for (String warning : report.getWarnings()) {
                message.append("⚠ ").append(warning).append("\n");
            }
        }
        boolean knownMalware = reputation != null && reputation.listed;
        if (reputation != null) {
            message.append("\n\nONLINE URL REPUTATION (URLhaus)\n");
            message.append(reputation.details).append("\n");
            if (knownMalware) {
                message.append("\nWARNING: avoid this URL. It has a URLhaus malware-distribution record.");
            } else if ("NO_MATCH".equals(reputation.status)) {
                message.append("\nNo record does not mean safe. URLhaus does not identify every phishing or scam site.");
            } else {
                message.append("\nThe online check did not complete. The URL has not received a clean verdict.");
            }
        } else if (!privateMode &&
                getSharedPreferences("hari_urlhaus_settings", MODE_PRIVATE)
                        .getBoolean("auto_urlhaus_enabled", false)) {
            message.append("\n\nOnline URL reputation could not be run because no Auth-Key is configured. ");
            message.append("Only the local URL checks below were performed.");
        }
        message.append("\n\nLocal checks run on this device and review URL patterns only; they do not certify the website or inspect all page content. ");
        message.append("India-focused scam checks look for selected impersonation and OTP/KYC/payment wording in the URL only; false positives and missed scams are possible. ");
        message.append("Android WebView certificate validation and Safe Browsing remain active during connection.");
        if (reputation != null) {
            message.append("\n\nThis URL was sent to URLhaus because online URL reputation is enabled in normal browsing.");
        } else {
            message.append("\n\nThis check did not send the URL to a third-party scanner.");
        }

        String title = knownMalware ? "⚠ Known malware-distribution URL" :
                report.hasWarnings() ? "Review site before opening" : "Site safety check";
        String positiveLabel = knownMalware ? "I understand — open anyway" :
                report.hasWarnings() ? "Open anyway" : "Open site";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message.toString())
                .setNegativeButton("Cancel", null)
                .setPositiveButton(positiveLabel, (dialog, which) -> {
                    if (isFinishing() || isDestroyed() || tab.navigationAttempt != attempt ||
                            !tabs.contains(tab)) {
                        showToast("The navigation changed. Please retry the address if you still want to open it.");
                        return;
                    }
                    APPROVED_SITE_URLS.add(url);
                    loadAfterSiteSafetyCheck(tab, url);
                })
                .show();
    }

    private void loadHome(Tab tab) {
        if (tab == null || tab.webView == null) return;
        tab.home = true;
        tab.readerMode = false;
        // The self-contained start page needs JavaScript for search, tabs and local tools,
        // even when JavaScript is disabled for ordinary websites.
        tab.webView.getSettings().setJavaScriptEnabled(true);
        tab.webView.getSettings().setBlockNetworkImage(false);
        tab.url = HOME_URL;
        tab.title = "Hari Browser";
        if (currentTab() == tab) addressBar.setText("");
        tab.webView.loadDataWithBaseURL(HOME_URL, homeHtml(), "text/html", "UTF-8", null);
        updateTabControls();
    }

    private String homeHtml() {
        // Do not even read standard-profile bookmark data inside the private process.
        String bookmarksJson = privateMode ? "[]" : readArray(KEY_BOOKMARKS).toString();
        return HomePage.render(darkTheme(), searchEngine(), bookmarksJson, privateMode,
                accentName(), settings.getString("home_language", "en"));
    }

    /**
     * The home screen uses HTTPS-shaped internal action URLs so untrusted pages never receive a
     * JavaScript bridge. Only links on the synthetic home origin reach these native actions.
     */
    private void handleHomeAction(String action) {
        switch (action) {
            case "newtab":
                addNewTab(null, true);
                break;
            case "tabs":
                showTabSwitcher();
                break;
            case "history":
                showHistory();
                break;
            case "bookmarks":
                showBookmarks();
                break;
            case "downloads":
                openDownloads();
                break;
            case "private":
                startActivity(new Intent(BrowserActivity.this, PrivateActivity.class));
                break;
            case "settings":
                showSettings();
                break;
            case "ai":
                showHariAiDialog();
                break;
            case "find":
                showFindDialog();
                break;
            case "bookmark":
                addBookmark();
                break;
            case "qr":
                showCurrentPageQr();
                break;
            case "print":
                printCurrentPage();
                break;
            case "share":
                sharePage();
                break;
            case "cryptography":
                startActivity(new Intent(BrowserActivity.this, CryptographyActivity.class));
                break;
            case "security":
                showSiteSecurity();
                break;
            case "clear":
                confirmClearData();
                break;
            case "emergency":
                confirmEmergencyClear();
                break;
            case "privacy":
                showPrivacyInfo();
                break;
            case "reader":
                if (currentTab() != null && !currentTab().home) {
                    currentTab().readerMode = !currentTab().readerMode;
                    if (currentTab().readerMode) applyReaderStyles(currentTab());
                    else currentTab().webView.reload();
                } else {
                    showToast("Open a webpage first.");
                }
                break;
            case "desktop":
                settings.edit().putBoolean("desktop_mode", !desktopEnabled()).apply();
                reconfigureWebViews();
                showToast("Desktop website mode " + (desktopEnabled() ? "enabled." : "disabled."));
                break;
            case "images":
                settings.edit().putBoolean("images_enabled", !imagesEnabled()).apply();
                reconfigureWebViews();
                showToast("Website images " + (imagesEnabled() ? "enabled." : "blocked."));
                break;
            case "javascript":
                settings.edit().putBoolean("javascript", !javascriptEnabled()).apply();
                reconfigureWebViews();
                showToast("JavaScript " + (javascriptEnabled() ? "enabled." : "disabled."));
                break;
            case "cookies":
                settings.edit().putBoolean("cookies_enabled", !cookiesEnabled()).apply();
                CookieManager.getInstance().setAcceptCookie(cookiesEnabled());
                if (!cookiesEnabled()) CookieManager.getInstance().removeAllCookies(null);
                reconfigureWebViews();
                showToast("First-party cookies " + (cookiesEnabled() ? "enabled." : "disabled."));
                break;
            case "trackers":
                settings.edit().putBoolean("block_trackers", !trackersEnabled()).apply();
                showToast("Tracker blocking " + (trackersEnabled() ? "enabled." : "disabled."));
                break;
            case "theme":
                toggleThemeAndRecreate();
                break;
            case "translate":
                translateCurrentPage();
                break;
            case "external":
                openCurrentExternally();
                break;
            case "duplicate":
                duplicateCurrentTab();
                break;
            case "closeothers":
                closeOtherTabs();
                break;
            case "restore":
                restoreRecentlyClosedTab();
                break;
            case "export":
                exportBookmarks();
                break;
            case "import":
                importBookmarks();
                break;
            default:
                if (action != null && action.startsWith("language-")) {
                    setHomeLanguage(action.substring("language-".length()));
                } else if (action != null && action.startsWith("searchengine-")) {
                    setHomeSearchEngine(action.substring("searchengine-".length()));
                } else {
                    showToast("That browser action is not available.");
                }
                break;
        }
    }

    private void setHomeLanguage(String languageCode) {
        String[] validCodes = {"en", "ml", "hi", "ta", "te", "kn", "bn", "mr", "gu", "pa", "ur", "es", "fr", "ar"};
        boolean valid = false;
        for (String code : validCodes) {
            if (code.equals(languageCode)) { valid = true; break; }
        }
        if (!valid) {
            showToast("That language is not available.");
            return;
        }
        settings.edit().putString("home_language", languageCode).apply();
        Tab tab = currentTab();
        if (tab != null && tab.home) loadHome(tab);
        showToast("Home page language updated.");
    }

    private void setHomeSearchEngine(String key) {
        String engine;
        switch (key) {
            case "duckduckgo": engine = "DuckDuckGo"; break;
            case "startpage": engine = "Startpage"; break;
            case "brave": engine = "Brave Search"; break;
            case "google": engine = "Google"; break;
            case "bing": engine = "Bing"; break;
            default:
                showToast("That search engine is not available.");
                return;
        }
        settings.edit().putString("search_engine", engine).apply();
        Tab tab = currentTab();
        if (tab != null && tab.home) loadHome(tab);
        showToast("Default search engine saved: " + engine);
    }

    private void addBookmark() {
        if (privateMode) {
            showToast("Bookmarks are disabled in private browsing to avoid saving private activity.");
            return;
        }
        Tab tab = currentTab();
        if (tab == null || tab.home || !PrivacyGuard.isHttpsUrl(tab.url)) {
            showToast("Open a website before saving a bookmark.");
            return;
        }
        JSONArray bookmarks = readArray(KEY_BOOKMARKS);
        try {
            JSONArray updated = new JSONArray();
            for (int i = 0; i < bookmarks.length(); i++) {
                JSONObject item = bookmarks.optJSONObject(i);
                if (item != null && !tab.url.equals(item.optString("url"))) updated.put(item);
            }
            JSONObject entry = new JSONObject();
            entry.put("title", tab.title == null ? safeHost(tab.url) : tab.title);
            entry.put("url", tab.url);
            entry.put("time", System.currentTimeMillis());
            updated.put(entry);
            secureStore.put(KEY_BOOKMARKS, updated.toString());
            showToast("Bookmark saved securely on this device.");
        } catch (JSONException ignored) {
            showToast("Could not save this bookmark.");
        }
    }

    private JSONArray readArray(String key) {
        try {
            return new JSONArray(secureStore.get(key, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void rememberHistory(String title, String url) {
        if (privateMode) return;
        JSONArray old = readArray(KEY_HISTORY);
        JSONArray updated = new JSONArray();
        try {
            JSONObject entry = new JSONObject();
            entry.put("title", title == null ? safeHost(url) : title);
            entry.put("url", url);
            entry.put("time", System.currentTimeMillis());
            updated.put(entry);
            for (int i = 0; i < old.length() && updated.length() < 100; i++) {
                JSONObject item = old.optJSONObject(i);
                if (item != null && !url.equals(item.optString("url"))) updated.put(item);
            }
            secureStore.put(KEY_HISTORY, updated.toString());
        } catch (JSONException ignored) {
            // Do not persist plaintext if encryption fails.
        }
    }

    private void showBookmarks() {
        if (privateMode) {
            showToast("Saved bookmarks are unavailable in private browsing.");
            return;
        }
        JSONArray array = readArray(KEY_BOOKMARKS);
        showSavedItems(array, "Bookmarks", true);
    }

    private void showHistory() {
        if (privateMode) {
            showToast("History is never saved in private mode.");
            return;
        }
        if (!historyEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("History is off")
                    .setMessage("Browsing history is disabled by default. Turn it on in Settings to save history encrypted on this device.")
                    .setPositiveButton("Settings", (d, w) -> showSettings())
                    .setNegativeButton("Close", null)
                    .show();
            return;
        }
        showSavedItems(readArray(KEY_HISTORY), "Recent history", false);
    }

    private void showSavedItems(JSONArray array, String title, boolean bookmarks) {
        if (privateMode && bookmarks) {
            showToast("Saved bookmarks are unavailable in private browsing.");
            return;
        }
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(8), dp(14), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        AlertDialog listDialog = new AlertDialog.Builder(this)
                .setTitle(title).setView(scroll).setPositiveButton("Done", null).create();
        if (array.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText(bookmarks ? "No bookmarks saved yet." : "No browsing history available.");
            empty.setTextColor(MUTED);
            empty.setPadding(dp(8), dp(18), dp(8), dp(18));
            list.addView(empty);
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String url = item.optString("url", "");
            String name = item.optString("title", safeHost(url));
            if (!PrivacyGuard.isHttpsUrl(url)) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            row.setBackground(round(Color.WHITE, OUTLINE, 12));
            TextView titleView = new TextView(this);
            titleView.setText(name);
            titleView.setTextColor(INK);
            titleView.setTextSize(14);
            titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView urlView = new TextView(this);
            urlView.setText(safeHost(url));
            urlView.setTextColor(MUTED);
            urlView.setTextSize(11);
            row.addView(titleView);
            row.addView(urlView);
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.END);
            TextView open = pillButton("Open", true, () -> {
                navigateTo(currentTab(), url);
                listDialog.dismiss();
            });
            LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(dp(74), dp(36));
            openLp.setMargins(dp(4), dp(6), 0, 0);
            actions.addView(open, openLp);
            if (bookmarks) {
                TextView remove = pillButton("Remove", false, () -> {
                    listDialog.dismiss();
                    removeBookmark(url);
                });
                LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(dp(82), dp(36));
                removeLp.setMargins(dp(6), dp(6), 0, 0);
                actions.addView(remove, removeLp);
            }
            row.addView(actions);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.setMargins(0, dp(5), 0, dp(5));
            list.addView(row, rowLp);
        }
        listDialog.show();
    }

    private void removeBookmark(String url) {
        if (privateMode) {
            showToast("Bookmarks cannot be changed in private browsing.");
            return;
        }
        JSONArray old = readArray(KEY_BOOKMARKS);
        JSONArray updated = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && !url.equals(item.optString("url"))) updated.put(item);
        }
        secureStore.put(KEY_BOOKMARKS, updated.toString());
        showToast("Bookmark removed.");
        // Reopen the list to reflect the change.
        new android.os.Handler(getMainLooper()).postDelayed(this::showBookmarks, 180);
    }

    private void showTabSwitcher() {
        if (tabs.isEmpty()) {
            addNewTab(null, true);
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), dp(6));

        TextView summary = new TextView(this);
        summary.setText(tabs.size() + (tabs.size() == 1 ? " open tab" : " open tabs")
                + "  •  Select a card to switch");
        summary.setTextColor(MUTED);
        summary.setTextSize(12);
        summary.setPadding(dp(2), dp(2), dp(2), dp(12));
        content.addView(summary);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout tabList = new LinearLayout(this);
        tabList.setOrientation(LinearLayout.VERTICAL);

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        for (int i = 0; i < tabs.size(); i++) {
            final int tabIndex = i;
            Tab tab = tabs.get(i);
            String titleText = tab.title == null || tab.title.trim().isEmpty()
                    ? (tab.home ? "New tab" : safeHost(tab.url)) : tab.title;
            String subtitle = tab.home ? "Hari Browser start page" : safeHost(tab.url);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(10), dp(9), dp(10));
            card.setMinimumHeight(dp(68));
            card.setBackground(round(CARD, tabIndex == currentTabIndex ? ACCENT : OUTLINE, 14));
            card.setClickable(true);
            card.setFocusable(true);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = new TextView(this);
            title.setText((tabIndex == currentTabIndex ? "●  " : "") + titleText);
            title.setTextColor(tabIndex == currentTabIndex ? ACCENT : INK);
            title.setTextSize(14);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);

            TextView host = new TextView(this);
            host.setText(subtitle);
            host.setTextColor(MUTED);
            host.setTextSize(11);
            host.setSingleLine(true);
            host.setEllipsize(android.text.TextUtils.TruncateAt.END);
            host.setPadding(0, dp(3), 0, 0);

            labels.addView(title);
            labels.addView(host);
            card.addView(labels, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView close = new TextView(this);
            close.setText("×");
            close.setTextColor(MUTED);
            close.setTextSize(24);
            close.setGravity(Gravity.CENTER);
            close.setContentDescription("Close tab " + titleText);
            close.setBackground(round(BG, OUTLINE, 11));
            LinearLayout.LayoutParams closeParams =
                    new LinearLayout.LayoutParams(dp(42), dp(42));
            closeParams.leftMargin = dp(8);
            card.addView(close, closeParams);

            card.setOnClickListener(v -> {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                selectTab(tabIndex);
            });
            close.setOnClickListener(v -> {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                closeTabAt(tabIndex);
                if (!isFinishing()) showTabSwitcher();
            });

            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(8);
            tabList.addView(card, cardParams);
        }
        scroll.addView(tabList);
        int scrollHeight = Math.min(420, Math.max(150, tabs.size() * 82));
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(scrollHeight)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Your tabs")
                .setView(content)
                .setPositiveButton("＋ New tab", (d, which) -> addNewTab(null, true))
                .setNegativeButton("Done", null)
                .create();
        dialogHolder[0] = dialog;
        dialog.show();
    }

    private void closeCurrentTab() {
        closeTabAt(currentTabIndex);
    }

    private void closeTabAt(int index) {
        if (index < 0 || index >= tabs.size()) return;
        Tab old = tabs.get(index);
        if (!old.home && PrivacyGuard.isHttpsUrl(old.url)) {
            recentlyClosedUrls.add(old.url);
            if (recentlyClosedUrls.size() > 10) recentlyClosedUrls.remove(0);
        }
        tabs.remove(index);
        destroyTab(old);
        if (tabs.isEmpty()) {
            currentTabIndex = -1;
            addNewTab(null, true);
            return;
        }
        int nextIndex = currentTabIndex;
        if (index < currentTabIndex) nextIndex--;
        else if (index == currentTabIndex) nextIndex = Math.min(index, tabs.size() - 1);
        selectTab(Math.max(0, nextIndex));
    }

    private void showFindDialog() {
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("Find text on this page");
        query.setInputType(InputType.TYPE_CLASS_TEXT);
        query.setPadding(dp(12), dp(10), dp(12), dp(10));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Find in page")
                .setView(query)
                .setPositiveButton("Find next", null)
                .setNeutralButton("Find previous", null)
                .setNegativeButton("Close", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                WebView web = currentWebView();
                String value = query.getText().toString();
                if (web != null && !value.isEmpty()) web.findAllAsync(value);
                if (web != null) web.findNext(true);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                WebView web = currentWebView();
                if (web != null) web.findNext(false);
            });
        });
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }


    private void showHariAiSettingsDialog() {
        String existingKey = secureStore.get(KEY_HARI_AI_API_KEY, "");
        boolean hasKey = !TextUtils.isEmpty(existingKey);

        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setTextColor(INK);
        keyInput.setHintTextColor(MUTED);
        keyInput.setHint("Paste API key here");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        keyInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        keyInput.setBackground(round(CARD, OUTLINE, 12));

        LinearLayout keyPanel = new LinearLayout(this);
        keyPanel.setOrientation(LinearLayout.VERTICAL);
        keyPanel.setPadding(dp(8), dp(4), dp(8), dp(4));
        keyPanel.addView(keyInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        TextView testButton = pillButton("Test connection", false, () -> {});
        LinearLayout.LayoutParams testButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        testButtonParams.setMargins(0, dp(8), 0, dp(4));
        keyPanel.addView(testButton, testButtonParams);

        TextView testStatus = new TextView(this);
        testStatus.setText("Testing sends one short prompt and may use API quota.");
        testStatus.setTextColor(MUTED);
        testStatus.setTextSize(12);
        testStatus.setPadding(dp(2), dp(4), dp(2), dp(4));
        keyPanel.addView(testStatus);

        String message =
                "Your key is encrypted on this device using Android Keystore. Prompts are sent online when you use Hari AI; the service may process them under its own terms. " +
                "A key stored in a mobile app cannot be protected like a server-side secret, so use a restricted key and set usage limits.\n\n" +
                "Use Test connection to verify the key before saving; this sends a short request to the AI service.\n\n" +
                (hasKey ? "A key is already saved. Enter a new key to replace it, or leave the field empty to test the saved key." : "No API key is saved yet.");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Hari AI settings")
                .setMessage(message)
                .setView(keyPanel)
                .setPositiveButton("Save key", null)
                .setNeutralButton("Clear key", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button clearButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            clearButton.setEnabled(hasKey);
            testButton.setOnClickListener(v -> {
                String candidate = keyInput.getText().toString().trim();
                if (candidate.isEmpty()) candidate = existingKey.trim();
                if (candidate.isEmpty()) {
                    keyInput.setError("Enter an API key to test.");
                    return;
                }
                final String keyToTest = candidate;
                testButton.setEnabled(false);
                testStatus.setText("Testing Hari AI connection…");
                Thread testThread = new Thread(() -> {
                    String problem = null;
                    try {
                        HariAiClient.testConnection(keyToTest);
                    } catch (Exception error) {
                        problem = error.getMessage();
                        if (TextUtils.isEmpty(problem)) {
                            problem = "Could not test the connection. Check your internet and key settings.";
                        }
                    }
                    final String result = problem;
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                        testButton.setEnabled(true);
                        if (result == null) {
                            testStatus.setText("Connection successful. Hari AI received and answered a test prompt.");
                            testStatus.setTextColor(Color.rgb(23, 128, 73));
                        } else {
                            testStatus.setText(result);
                            testStatus.setTextColor(Color.rgb(190, 48, 48));
                        }
                    });
                }, "HariAI-key-test");
                testThread.start();
            });
            saveButton.setOnClickListener(v -> {
                String value = keyInput.getText().toString().trim();
                if (value.isEmpty()) {
                    keyInput.setError("Paste an API key first.");
                    return;
                }
                if (value.length() > 512 || value.contains("\n") || value.contains("\r")) {
                    keyInput.setError("That key format is not valid.");
                    return;
                }
                secureStore.put(KEY_HARI_AI_API_KEY, value);
                String saved = secureStore.get(KEY_HARI_AI_API_KEY, "");
                if (!value.equals(saved)) {
                    showToast("Could not securely save the key. Try again.");
                    return;
                }
                dialog.dismiss();
                showToast("Hari AI is ready.");
            });
            clearButton.setOnClickListener(v -> new AlertDialog.Builder(BrowserActivity.this)
                    .setTitle("Clear Hari AI key?")
                    .setMessage("Hari AI will stop working until a new key is saved.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear key", (d, which) -> {
                        secureStore.put(KEY_HARI_AI_API_KEY, "");
                        dialog.dismiss();
                        showToast("Hari AI key cleared.");
                    })
                    .show());
        });
        dialog.show();
    }

    private void showHariAiDialog() {
        String apiKey = secureStore.get(KEY_HARI_AI_API_KEY, "");
        if (TextUtils.isEmpty(apiKey.trim())) {
            new AlertDialog.Builder(this)
                    .setTitle("Hari AI")
                    .setMessage("Set up Hari AI first by adding your API key in Hari AI settings.")
                    .setPositiveButton("Set up", (d, which) -> showHariAiSettingsDialog())
                    .setNegativeButton("Close", null)
                    .show();
            return;
        }

        ArrayList<HariAiMessage> messages = new ArrayList<>();
        boolean[] requestInFlight = {false};

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(4), dp(12), dp(4));

        TextView privacyNote = new TextView(this);
        privacyNote.setText("Ask questions, learn and create. Prompts are sent online. Chat history stays in this open screen and is not saved by Hari Browser. Avoid passwords or secrets.");
        privacyNote.setTextColor(MUTED);
        privacyNote.setTextSize(11);
        privacyNote.setPadding(dp(2), dp(4), dp(2), dp(9));
        content.addView(privacyNote);

        ScrollView transcriptScroll = new ScrollView(this);
        transcriptScroll.setFillViewport(true);
        TextView transcript = new TextView(this);
        transcript.setTextColor(INK);
        transcript.setTextSize(13);
        transcript.setTextIsSelectable(true);
        transcript.setPadding(dp(12), dp(10), dp(12), dp(10));
        transcript.setBackground(round(CARD, OUTLINE, 12));
        transcript.setText("Hari AI is ready. What can I help you with today?");
        transcriptScroll.addView(transcript, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(transcriptScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(175)));

        TextView status = new TextView(this);
        status.setTextColor(MUTED);
        status.setTextSize(11);
        status.setPadding(dp(2), dp(5), dp(2), dp(5));
        status.setText("Ready");
        content.addView(status);

        EditText questionInput = new EditText(this);
        questionInput.setSingleLine(false);
        questionInput.setMinLines(2);
        questionInput.setMaxLines(4);
        questionInput.setTextColor(INK);
        questionInput.setHintTextColor(MUTED);
        questionInput.setHint("Ask Hari AI anything…");
        questionInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        questionInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        questionInput.setGravity(Gravity.TOP | Gravity.START);
        questionInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        questionInput.setBackground(round(CARD, OUTLINE, 12));

        LinearLayout composeRow = new LinearLayout(this);
        composeRow.setOrientation(LinearLayout.HORIZONTAL);
        composeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        inputParams.setMargins(0, dp(4), dp(7), dp(4));
        composeRow.addView(questionInput, inputParams);
        TextView sendButton = pillButton("Send", true, () -> {});
        composeRow.addView(sendButton, new LinearLayout.LayoutParams(dp(72), dp(42)));
        content.addView(composeRow);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Hari AI")
                .setView(content)
                .setNeutralButton("Settings", null)
                .setNegativeButton("Close", null)
                .create();

        sendButton.setOnClickListener(v -> {
            if (requestInFlight[0]) return;
            String question = questionInput.getText().toString().trim();
            if (question.isEmpty()) {
                questionInput.setError("Enter a question first.");
                return;
            }
            if (question.length() > MAX_HARI_AI_PROMPT_CHARS) {
                questionInput.setError("Keep each message under 6,000 characters.");
                return;
            }
            String currentKey = secureStore.get(KEY_HARI_AI_API_KEY, "");
            if (TextUtils.isEmpty(currentKey.trim())) {
                dialog.dismiss();
                showHariAiSettingsDialog();
                return;
            }

            messages.add(new HariAiMessage("user", question));
            questionInput.setText("");
            transcript.setText(formatHariAiMessages(messages));
            status.setText("Hari AI is thinking…");
            requestInFlight[0] = true;
            sendButton.setEnabled(false);
            questionInput.setEnabled(false);

            final JSONArray contents;
            try {
                contents = buildHariAiContents(messages);
            } catch (JSONException error) {
                requestInFlight[0] = false;
                sendButton.setEnabled(true);
                questionInput.setEnabled(true);
                messages.remove(messages.size() - 1);
                transcript.setText(formatHariAiMessages(messages));
                status.setText("Could not prepare the message. Try again.");
                return;
            }

            Thread requestThread = new Thread(() -> {
                String answer = null;
                String problem = null;
                try {
                    answer = HariAiClient.generateReply(currentKey, contents);
                } catch (Exception ignored) {
                    problem = ignored.getMessage();
                    if (TextUtils.isEmpty(problem)) {
                        problem = "Could not get a reply. Check your connection and try again.";
                    }
                }
                final String finalAnswer = answer;
                final String finalProblem = problem;
                runOnUiThread(() -> {
                    requestInFlight[0] = false;
                    if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                    sendButton.setEnabled(true);
                    questionInput.setEnabled(true);
                    if (finalAnswer != null && !finalAnswer.trim().isEmpty()) {
                        messages.add(new HariAiMessage("model", finalAnswer.trim()));
                        while (messages.size() > 40) {
                            messages.remove(0);
                            if (!messages.isEmpty()) messages.remove(0);
                        }
                        status.setText("Ready");
                    } else {
                        if (!messages.isEmpty()) {
                            HariAiMessage last = messages.get(messages.size() - 1);
                            if ("user".equals(last.role) && question.equals(last.text)) {
                                messages.remove(messages.size() - 1);
                            }
                        }
                        status.setText(finalProblem == null ? "Could not get a reply. Try again." : finalProblem);
                    }
                    transcript.setText(formatHariAiMessages(messages));
                    transcriptScroll.post(() -> transcriptScroll.fullScroll(View.FOCUS_DOWN));
                });
            }, "HariAI-request");
            requestThread.start();
        });

        questionInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendButton.performClick();
                return true;
            }
            return false;
        });

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v -> {
                    dialog.dismiss();
                    showHariAiSettingsDialog();
                }));
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private JSONArray buildHariAiContents(ArrayList<HariAiMessage> messages) throws JSONException {
        JSONArray contents = new JSONArray();
        int start = Math.max(0, messages.size() - 12);
        // Keep the first item in a chat request as a user turn, even when trimming history.
        if (start > 0 && "model".equals(messages.get(start).role)) start++;
        for (int i = start; i < messages.size(); i++) {
            HariAiMessage message = messages.get(i);
            JSONObject part = new JSONObject();
            part.put("text", message.text);
            JSONArray parts = new JSONArray();
            parts.put(part);
            JSONObject item = new JSONObject();
            item.put("role", message.role);
            item.put("parts", parts);
            contents.put(item);
        }
        return contents;
    }

    private String formatHariAiMessages(ArrayList<HariAiMessage> messages) {
        if (messages.isEmpty()) return "Hari AI is ready. What can I help you with today?";
        StringBuilder output = new StringBuilder();
        for (HariAiMessage message : messages) {
            output.append("user".equals(message.role) ? "You\n" : "Hari AI\n")
                    .append(message.text)
                    .append("\n\n────────────────────\n\n");
        }
        return output.toString().trim();
    }

    private void showHttpsUpgradePrompt(Tab tab, String insecureUrl) {
        String upgradedUrl = PrivacyGuard.httpsUpgradeUrl(insecureUrl);
        if (upgradedUrl == null) {
            showToast("Blocked insecure HTTP. This address could not be upgraded safely.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Insecure HTTP link")
                .setMessage("Hari Browser will not open this address over unencrypted HTTP. Try the HTTPS version instead? It may fail if the site does not support HTTPS. The upgraded address will still receive the normal site-safety check.")
                .setPositiveButton("Try HTTPS", (dialog, which) ->
                        showSiteSafetyCheck(tab, upgradedUrl))
                .setNegativeButton("Cancel", (dialog, which) ->
                        showToast("Insecure HTTP blocked."))
                .show();
    }

    private void showSettings() {
        String[] options = {
                (trackersEnabled() ? "✓ " : "○ ") + ui("Block ads and trackers"),
                (javascriptEnabled() ? "✓ " : "○ ") + ui("JavaScript (compatibility / risk)"),
                (desktopEnabled() ? "✓ " : "○ ") + ui("Desktop website mode"),
                (cookiesEnabled() ? "✓ " : "○ ") + ui("Allow first-party cookies"),
                (historyEnabled() ? "✓ " : "○ ") + ui("Save browsing history"),
                (currentTab() != null && currentTab().readerMode ? "✓ " : "○ ") + ui("Toggle reader-style view"),
                ui("Clear cookies, cache, site storage and history"),
                ui("Open downloads"),
                ui("Bookmarks"),
                ui("About privacy and security"),
                ui("Open private browsing"),
                ui("Share current page"),
                ui("Choose search engine"),
                (imagesEnabled() ? "✓ " : "○ ") + ui("Load website images"),
                ui("Choose page text size"),
                ui("Copy current page address"),
                ui("Translate current page to English"),
                ui("Open current page in another app"),
                ui("Duplicate current tab"),
                ui("Close other tabs"),
                ui("Restore last closed tab"),
                ui("Export bookmarks"),
                ui("Import bookmarks"),
                (darkTheme() ? "✓ " : "○ ") + ui("Dark browser theme"),
                ui("Site security details"),
                ui("Emergency clear browsing data"),
                ui("Show QR code for current page"),
                ui("Print / save current page as PDF"),
                ui("Choose home page language"),
                ui("Choose browser accent colour"),
                (fingerprintProtectionEnabled() ? "✓ " : "○ ") + ui("Maximum anti-fingerprinting shield"),
                ui("About Hari Browser - What's new in 2.3"),
                ui("Hari AI · API settings"),
                ui("Teen protection") + " · " + (teenProtectionEnabled() ? "ON" : "OFF"),
                ui("Security & privacy scan"),
                ui("Site permission controls"),
                (privateMode ? "Clear browsing data on exit (automatic in private mode)" :
                        (settings.getBoolean("clear_data_on_exit", false) ? "✓ " : "○ ") + "Clear browsing data on browser exit"),
                ui("Hari Antivirus · scan a file and manage quarantine"),
                (dataSaverEnabled() ? "✓ " : "○ ") + ui("Lite mode · block images and known trackers"),
                ui("Family safety guide"),
                ui("Save current page for offline reading"),
                ui("Read saved pages (offline text)")
        };
        new AlertDialog.Builder(this)
                .setTitle(privateMode ? "Private settings" : "Settings")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            settings.edit().putBoolean("block_trackers", !trackersEnabled()).apply();
                            showToast("Tracker blocking " + (trackersEnabled() ? "enabled." : "disabled."));
                            break;
                        case 1:
                            settings.edit().putBoolean("javascript", !javascriptEnabled()).apply();
                            reconfigureWebViews();
                            showToast("JavaScript " + (javascriptEnabled() ? "enabled." : "disabled."));
                            break;
                        case 2:
                            settings.edit().putBoolean("desktop_mode", !desktopEnabled()).apply();
                            reconfigureWebViews();
                            showToast("Desktop mode " + (desktopEnabled() ? "enabled." : "disabled."));
                            break;
                        case 3:
                            settings.edit().putBoolean("cookies_enabled", !cookiesEnabled()).apply();
                            CookieManager.getInstance().setAcceptCookie(cookiesEnabled());
                            if (!cookiesEnabled()) CookieManager.getInstance().removeAllCookies(null);
                            reconfigureWebViews();
                            showToast("First-party cookies " + (cookiesEnabled() ? "enabled." : "disabled."));
                            break;
                        case 4:
                            if (privateMode) {
                                showToast("History is never stored in private mode.");
                            } else {
                                boolean next = !historyEnabled();
                                settings.edit().putBoolean("history_enabled", next).apply();
                                if (!next) secureStore.put(KEY_HISTORY, "[]");
                                showToast(next ? "History enabled; it is stored encrypted." : "History disabled and cleared.");
                            }
                            break;
                        case 5:
                            Tab tab = currentTab();
                            if (tab != null && !tab.home) {
                                tab.readerMode = !tab.readerMode;
                                if (tab.readerMode) applyReaderStyles(tab);
                                else tab.webView.reload();
                            } else {
                                showToast("Open a webpage first.");
                            }
                            break;
                        case 6:
                            confirmClearData();
                            break;
                        case 7:
                            openDownloads();
                            break;
                        case 8:
                            showBookmarks();
                            break;
                        case 9:
                            showPrivacyInfo();
                            break;
                        case 10:
                            startActivity(new Intent(BrowserActivity.this, PrivateActivity.class));
                            break;
                        case 11:
                            sharePage();
                            break;
                        case 12:
                            showSearchEnginePicker();
                            break;
                        case 13:
                            settings.edit().putBoolean("images_enabled", !imagesEnabled()).apply();
                            reconfigureWebViews();
                            showToast("Website images " + (imagesEnabled() ? "enabled." : "blocked."));
                            break;
                        case 14:
                            showTextZoomPicker();
                            break;
                        case 15:
                            copyCurrentUrl();
                            break;
                        case 16:
                            translateCurrentPage();
                            break;
                        case 17:
                            openCurrentExternally();
                            break;
                        case 18:
                            duplicateCurrentTab();
                            break;
                        case 19:
                            closeOtherTabs();
                            break;
                        case 20:
                            restoreRecentlyClosedTab();
                            break;
                        case 21:
                            exportBookmarks();
                            break;
                        case 22:
                            importBookmarks();
                            break;
                        case 23:
                            toggleThemeAndRecreate();
                            break;
                        case 24:
                            showSiteSecurity();
                            break;
                        case 25:
                            confirmEmergencyClear();
                            break;
                        case 26:
                            showCurrentPageQr();
                            break;
                        case 27:
                            printCurrentPage();
                            break;
                        case 28:
                            showHomeLanguagePicker();
                            break;
                        case 29:
                            showAccentPicker();
                            break;
                        case 30:
                            boolean protectionEnabled = !fingerprintProtectionEnabled();
                            settings.edit().putBoolean("fingerprint_protection", protectionEnabled).apply();
                            reconfigureWebViews();
                            Tab protectionTab = currentTab();
                            if (protectionTab != null) {
                                if (protectionTab.home) loadHome(protectionTab);
                                else protectionTab.webView.reload();
                            }
                            if (protectionEnabled && !fingerprintScriptSupported()) {
                                showToast("Shield saved, but update Android System WebView for full support.");
                            } else {
                                showToast(protectionEnabled ? "Anti-fingerprinting shield enabled." : "Anti-fingerprinting shield disabled.");
                            }
                            break;
                        case 31:
                            showAboutDialog();
                            break;
                        case 32:
                            showHariAiSettingsDialog();
                            break;
                        case 33:
                            handleTeenProtectionMenu();
                            break;
                        case 34:
                            showSecurityPrivacyScan();
                            break;
                        case 35:
                            showSitePermissionControls();
                            break;
                        case 36:
                            if (privateMode) {
                                showToast("Private mode already clears its cookies and site storage when it closes.");
                            } else {
                                boolean clearOnExit = !settings.getBoolean("clear_data_on_exit", false);
                                settings.edit().putBoolean("clear_data_on_exit", clearOnExit).apply();
                                showToast(clearOnExit
                                        ? "Browsing data will be cleared when you exit Hari Browser; Android may still terminate the app without a final callback."
                                        : "Automatic browsing-data clearing on exit disabled.");
                            }
                            break;
                        case 37:
                            Intent antivirusIntent = new Intent(BrowserActivity.this, AntivirusActivity.class);
                            antivirusIntent.putExtra("private_mode", privateMode);
                            startActivity(antivirusIntent);
                            break;
                        case 38:
                            boolean liteMode = !dataSaverEnabled();
                            settings.edit().putBoolean("lite_mode_enabled", liteMode).apply();
                            reconfigureWebViews();
                            Tab liteTab = currentTab();
                            if (liteMode && liteTab != null && !liteTab.home && liteTab.webView != null) {
                                liteTab.webView.reload();
                            }
                            showToast(liteMode
                                    ? "Lite mode enabled: website images and known tracker requests are blocked. Data savings are not estimated."
                                    : "Lite mode disabled. Your separate image and tracker settings apply.");
                            break;
                        case 39:
                            showFamilySafetyGuide();
                            break;
                        case 40:
                            saveCurrentPageForOfflineReading();
                            break;
                        case 41:
                            showSavedPages();
                            break;
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }


    private void showFamilySafetyGuide() {
        String message =
                "A few habits make the browser safer for everyone\n\n" +
                "• Check the actual website address before signing in. A padlock or HTTPS means the connection is encrypted; it does not prove the site is honest.\n\n" +
                "• Never share passwords, bank PINs, OTPs, recovery codes, or an AI API key with an unexpected website, message, or person. A legitimate support agent should not ask for an OTP.\n\n" +
                "• If a site warning appears, stop and ask a trusted adult before continuing. The official-site label means only that the hostname matches Hari Browser's curated list.\n\n" +
                "• Teen Protection and tracker blocking are helpful but incomplete. They may miss harmful content or block a harmless result. App-level protection can be bypassed if someone clears app data or uninstalls the app; use Android family controls for device-wide supervision.\n\n" +
                "• Hari Antivirus performs limited local file checks. It is not a complete antivirus and does not scan other apps. Keep Android and System WebView updated. Do not install the previously flagged Hari Browser 2.3.0 APK while its device-protection warning remains unresolved.\n\n" +
                "• Hari AI sends the prompts you submit to the AI service configured in Settings. Voice search uses Android's selected speech-recognition service, which may process audio or transcripts according to that service's policy.\n\n" +
                "• Hari Browser does not provide its own DNS-over-HTTPS service. For encrypted DNS, review Android's system Private DNS setting and choose a provider you trust; DNS providers can see the domain lookups they resolve.";
        new AlertDialog.Builder(this)
                .setTitle(ui("Family safety guide"))
                .setMessage(message)
                .setPositiveButton("OK", null)
                .setNeutralButton("Site security details", (dialog, which) -> showSiteSecurity())
                .show();
    }

    private void saveCurrentPageForOfflineReading() {
        if (privateMode) {
            showToast("Offline reading is disabled in Private mode so private pages are not persisted.");
            return;
        }
        Tab tab = currentTab();
        if (tab == null || tab.home || tab.webView == null || !PrivacyGuard.isHttpsUrl(tab.url)) {
            showToast("Open an HTTPS website first to save its readable text.");
            return;
        }
        final String pageUrl = tab.url;
        final String pageTitle = tab.title == null || tab.title.trim().isEmpty()
                ? safeHost(pageUrl) : tab.title.trim();
        try {
            tab.webView.evaluateJavascript(
                    "(function(){try{return document.body ? document.body.innerText.slice(0,24000) : '';}catch(e){return '';}})()",
                    encodedText -> {
                        if (tab.webView == null || !pageUrl.equals(tab.url)
                                || !pageUrl.equals(tab.webView.getUrl())) {
                            showToast("The page changed before it could be saved. Please try again.");
                            return;
                        }
                        String body = OfflineReading.decodeJavascriptString(encodedText);
                        if (body.isEmpty()) {
                            showToast("No readable page text was available to save.");
                            return;
                        }
                        String existing = secureStore.get(KEY_READ_LATER, "[]");
                        final JSONArray updated;
                        try {
                            updated = OfflineReading.addOrReplace(existing, pageTitle,
                                    pageUrl, body, System.currentTimeMillis());
                        } catch (IllegalArgumentException invalidPage) {
                            showToast("This address or page could not be saved safely.");
                            return;
                        }
                        String serialized = updated.toString();
                        secureStore.put(KEY_READ_LATER, serialized);
                        if (!serialized.equals(secureStore.get(KEY_READ_LATER, ""))) {
                            showToast("The offline snapshot could not be stored securely.");
                            return;
                        }
                        showToast("Saved a text-only offline snapshot. Images, links, scripts and cookies are not saved.");
                    });
        } catch (RuntimeException unavailable) {
            showToast("Could not capture this page for offline reading.");
        }
    }

    private void showSavedPages() {
        if (privateMode) {
            showToast("Saved pages are available only in normal browsing, not Private mode.");
            return;
        }
        JSONArray saved = OfflineReading.parse(secureStore.get(KEY_READ_LATER, "[]"));
        if (saved.length() == 0) {
            new AlertDialog.Builder(this).setTitle("Saved pages")
                    .setMessage("No offline reading snapshots yet. Open an HTTPS page and choose 'Save current page for offline reading'.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] labels = new String[saved.length()];
        String[] urls = new String[saved.length()];
        for (int i = 0; i < saved.length(); i++) {
            JSONObject item = saved.optJSONObject(i);
            if (item == null) {
                labels[i] = "Saved page";
                urls[i] = "";
                continue;
            }
            String title = item.optString("title", "Saved page");
            urls[i] = item.optString("url", "");
            labels[i] = title + "  —  " + safeHost(urls[i]);
        }
        new AlertDialog.Builder(this).setTitle("Saved pages · text only")
                .setItems(labels, (dialog, which) -> showSavedPage(urls[which]))
                .setNeutralButton("Clear all", (dialog, which) ->
                        new AlertDialog.Builder(this)
                                .setTitle("Clear saved pages?")
                                .setMessage("This removes all encrypted offline text snapshots from this browser profile.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Clear saved pages", (confirm, selected) -> {
                                    secureStore.put(KEY_READ_LATER, "[]");
                                    showToast("Offline saved pages cleared.");
                                }).show())
                .setNegativeButton("Close", null)
                .show();
    }

    private void showSavedPage(String pageUrl) {
        if (pageUrl == null || pageUrl.isEmpty()) return;
        JSONArray saved = OfflineReading.parse(secureStore.get(KEY_READ_LATER, "[]"));
        JSONObject selected = null;
        for (int i = 0; i < saved.length(); i++) {
            JSONObject item = saved.optJSONObject(i);
            if (item != null && pageUrl.equals(item.optString("url", ""))) {
                selected = item;
                break;
            }
        }
        if (selected == null) {
            showToast("That offline snapshot is no longer available.");
            return;
        }
        String title = selected.optString("title", "Saved page");
        String body = selected.optString("body", "");
        ScrollView scroll = new ScrollView(this);
        TextView text = new TextView(this);
        text.setText(body);
        text.setTextSize(16);
        text.setTextColor(INK);
        text.setTextIsSelectable(true);
        text.setPadding(dp(16), dp(12), dp(16), dp(16));
        scroll.addView(text, new ScrollView.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(pageUrl + "\n\nText-only saved copy. It is offline and may be out of date.")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .setNeutralButton("Remove saved page", (dialog, which) -> {
                    String current = secureStore.get(KEY_READ_LATER, "[]");
                    secureStore.put(KEY_READ_LATER,
                            OfflineReading.removeByUrl(current, pageUrl).toString());
                    showToast("Offline snapshot removed.");
                })
                .show();
    }

    private String protectSearchUrl(String url) {
        if (!teenProtectionEnabled()) return url;
        if (TeenProtection.isBlockedUrl(url)) {
            showToast("Teen Protection blocked this search or website.");
            return null;
        }
        if (TeenProtection.isBraveSearchHostUrl(url)) {
            // Brave documents its strict preference as a cookie; the query parameter below is only a fallback.
            CookieManager cookies = CookieManager.getInstance();
            cookies.setCookie("https://search.brave.com/", "safesearch=strict; Path=/; Secure; SameSite=Lax");
            cookies.flush();
        }
        return TeenProtection.withStrictSafeSearch(url);
    }

    private boolean verifyTeenProtectionPin(String candidate, EditText input) {
        long now = System.currentTimeMillis();
        long lockUntil = protectionSettings.getLong(TeenProtection.PIN_LOCK_UNTIL_KEY, 0L);
        if (lockUntil > now) {
            input.setError("Try again in " + Math.max(1, (lockUntil - now + 999) / 1000) + " seconds.");
            return false;
        }
        String salt = protectionStore.get(TeenProtection.PIN_SALT_KEY, "");
        String hash = protectionStore.get(TeenProtection.PIN_HASH_KEY, "");
        if (!TeenProtection.verifyPin(candidate, salt, hash)) {
            int failures = protectionSettings.getInt(TeenProtection.PIN_FAILURES_KEY, 0) + 1;
            if (failures >= 5) {
                protectionSettings.edit()
                        .putInt(TeenProtection.PIN_FAILURES_KEY, 0)
                        .putLong(TeenProtection.PIN_LOCK_UNTIL_KEY, now + 60_000L)
                        .apply();
                input.setError("Too many attempts. Try again in 60 seconds.");
            } else {
                protectionSettings.edit().putInt(TeenProtection.PIN_FAILURES_KEY, failures).apply();
                input.setError("Incorrect PIN. Attempts left: " + (5 - failures) + ".");
            }
            return false;
        }
        protectionSettings.edit().remove(TeenProtection.PIN_FAILURES_KEY)
                .remove(TeenProtection.PIN_LOCK_UNTIL_KEY).apply();
        return true;
    }

    private void handleTeenProtectionMenu() {
        if (privateMode) {
            String status = teenProtectionEnabled() ? "ON" : "OFF";
            new AlertDialog.Builder(this)
                    .setTitle("Teen Protection · " + status)
                    .setMessage("Teen Protection is managed from standard browsing settings so private mode cannot change its parent PIN or switch it off. Its current protection state is " + status + ".")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        if (teenProtectionEnabled()) showTeenProtectionDisableDialog();
        else showTeenProtectionSetupDialog();
    }

    private void showTeenProtectionSetupDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), dp(4), dp(4), dp(2));

        TextView description = new TextView(this);
        description.setText(
                "Checks search queries on this device for a starter list of explicit adult-content, gambling, " +
                "graphic real-world violence, self-harm instructions, dangerous weapon/explosive instructions, " +
                "and illegal-drug procurement/manufacture. It blocks selected adult/gambling domains and requests " +
                "strict SafeSearch for Google, Bing, Brave Search and DuckDuckGo. Startpage filtering is controlled " +
                "by Startpage itself. A parent PIN is required to turn this feature off from Hari Browser.\n\n" +
                "Filtering is phrase-based and can miss altered wording or content inside a webpage; legitimate " +
                "queries can occasionally be blocked. Prevention, educational and help-seeking searches are allowed " +
                "where possible. This is a browser-level safeguard, not a complete content filter or Android " +
                "parental-control service, and it cannot guarantee that every inappropriate page or image is blocked.");
        description.setTextColor(MUTED);
        description.setTextSize(13);
        description.setPadding(0, 0, 0, dp(10));
        panel.addView(description);

        EditText pinInput = new EditText(this);
        pinInput.setSingleLine(true);
        pinInput.setHint("Create parent PIN (6–8 digits)");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setTextColor(INK);
        pinInput.setHintTextColor(MUTED);
        pinInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        panel.addView(pinInput);

        EditText confirmInput = new EditText(this);
        confirmInput.setSingleLine(true);
        confirmInput.setHint("Confirm parent PIN");
        confirmInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        confirmInput.setTextColor(INK);
        confirmInput.setHintTextColor(MUTED);
        confirmInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        panel.addView(confirmInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Enable Teen Protection")
                .setView(panel)
                .setPositiveButton("Enable", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pin = pinInput.getText().toString();
            String confirm = confirmInput.getText().toString();
            if (!pin.matches("[0-9]{6,8}")) {
                pinInput.setError("Use 6 to 8 digits.");
                return;
            }
            if (!pin.equals(confirm)) {
                confirmInput.setError("The PINs do not match.");
                return;
            }
            try {
                String salt = TeenProtection.createPinSalt();
                String hash = TeenProtection.hashPin(pin, salt);
                protectionStore.put(TeenProtection.PIN_SALT_KEY, salt);
                protectionStore.put(TeenProtection.PIN_HASH_KEY, hash);
                String savedSalt = protectionStore.get(TeenProtection.PIN_SALT_KEY, "");
                String savedHash = protectionStore.get(TeenProtection.PIN_HASH_KEY, "");
                if (!TeenProtection.verifyPin(pin, savedSalt, savedHash)) {
                    showToast("Could not securely save the parent PIN. Protection was not enabled.");
                    return;
                }
                protectionSettings.edit().putBoolean(TeenProtection.ENABLED_KEY, true)
                        .remove(TeenProtection.PIN_FAILURES_KEY)
                        .remove(TeenProtection.PIN_LOCK_UNTIL_KEY).apply();
                dialog.dismiss();
                showToast("Teen Protection enabled.");
            } catch (Exception ignoredError) {
                showToast("Could not configure Teen Protection. Try again.");
            }
        }));
        dialog.show();
    }

    private void showTeenProtectionDisableDialog() {
        EditText pinInput = new EditText(this);
        pinInput.setSingleLine(true);
        pinInput.setHint("Parent PIN");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setTextColor(INK);
        pinInput.setHintTextColor(MUTED);
        pinInput.setPadding(dp(10), dp(8), dp(10), dp(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Teen Protection is ON")
                .setMessage("Enter the parent PIN to disable this browser's teen search protection.")
                .setView(pinInput)
                .setPositiveButton("Continue", null)
                .setNegativeButton("Keep enabled", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!verifyTeenProtectionPin(pinInput.getText().toString(), pinInput)) return;
            new AlertDialog.Builder(this)
                    .setTitle("Disable Teen Protection?")
                    .setMessage("Restricted search terms will no longer be blocked and strict SafeSearch will no longer be added by Hari Browser.")
                    .setNegativeButton("Keep enabled", null)
                    .setPositiveButton("Disable", (confirmation, which) -> {
                        protectionSettings.edit().putBoolean(TeenProtection.ENABLED_KEY, false).apply();
                        showToast("Teen Protection disabled.");
                    })
                    .show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showAboutDialog() {
        String message =
                "Version 2.3.0\n\n" +
                "A refreshed start page with clearer spacing, adaptive cards and improved phone/tablet layouts.\n\n" +
                "- Home page available in ten Indian languages plus English, Spanish, French and Arabic\n" +
                "- Search-engine choice now persists from the home screen\n" +
                "- Faster directory filtering and batched shortcut rendering\n" +
                "- Parent-PIN-protected Teen Protection blocks selected explicit, gambling, graphic-violence, dangerous-instruction and self-harm-instruction searches\n" +
                "- Hari AI Settings can test the API key and explain common key, model and quota errors\n" +
                "- Optional Hari AI chat with API setup in Settings\n" +
                "- API key encrypted locally; prompts are sent online only when you use Hari AI\n" +
                "- Cleaner visual hierarchy, improved focus states and more efficient off-screen rendering\n" +
                "- HTTPS-only navigation, Safe Browsing, encrypted bookmarks and private browsing remain available\n\n" +
                "Privacy note: tracker blocking and Teen Protection are best-effort. Some search providers control their own filtering, and websites or your selected search provider still receive the requests needed to load services.";
        new AlertDialog.Builder(this)
                .setTitle("Hari Browser 2.3")
                .setMessage(message)
                .setNeutralButton("Privacy & security", (dialog, which) -> showPrivacyInfo())
                .setPositiveButton("Done", null)
                .show();
    }

    private void showHomeLanguagePicker() {
        String[] labels = {"English (India)", "Malayalam (മലയാളം)", "Hindi (हिन्दी)", "Tamil (தமிழ்)",
                "Telugu (తెలుగు)", "Kannada (ಕನ್ನಡ)", "Bengali (বাংলা)", "Marathi (मराठी)",
                "Gujarati (ગુજરાતી)", "Punjabi (ਪੰਜਾਬੀ)", "Urdu (اردو)",
                "Spanish (Español)", "French (Français)", "Arabic (العربية)"};
        String[] codes = {"en", "ml", "hi", "ta", "te", "kn", "bn", "mr", "gu", "pa", "ur", "es", "fr", "ar"};
        String current = settings.getString("home_language", "en");
        int selected = 0;
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(current)) { selected = i; break; }
        }
        new AlertDialog.Builder(this).setTitle("Home page language")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    setHomeLanguage(codes[which]);
                    dialog.dismiss();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showAccentPicker() {
        String[] accents = {"Ocean", "Violet", "Mint", "Sunset", "Rose"};
        int selected = 0;
        for (int i = 0; i < accents.length; i++) {
            if (accents[i].equals(accentName())) { selected = i; break; }
        }
        new AlertDialog.Builder(this).setTitle("Browser accent colour")
                .setSingleChoiceItems(accents, selected, (dialog, which) -> {
                    settings.edit().putString("accent_color", accents[which]).apply();
                    showToast("Accent colour updated.");
                    dialog.dismiss();
                    recreateWithSavedTabs();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showTextZoomPicker() {
        final int[] values = {80, 90, 100, 110, 125, 150};
        String[] labels = {"Small — 80%", "Compact — 90%", "Normal — 100%", "Large — 110%", "Larger — 125%", "Extra large — 150%"};
        int selected = 2;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == textZoom()) selected = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Page text size")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    settings.edit().putInt("text_zoom", values[which]).apply();
                    for (Tab tab : tabs) {
                        if (tab.webView != null) tab.webView.getSettings().setTextZoom(values[which]);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSearchEnginePicker() {
        String[] engines = {"DuckDuckGo", "Startpage", "Brave Search", "Google", "Bing"};
        String current = searchEngine();
        int selected = 0;
        for (int i = 0; i < engines.length; i++) {
            if (engines[i].equals(current)) selected = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Default search engine")
                .setSingleChoiceItems(engines, selected, (dialog, which) -> {
                    settings.edit().putString("search_engine", engines[which]).apply();
                    dialog.dismiss();
                    showToast("Search engine set to " + engines[which] + ".");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCurrentPageQr() {
        Tab tab = currentTab();
        if (tab == null || tab.home || !PrivacyGuard.isHttpsUrl(tab.url)) {
            showToast("Open an HTTPS webpage first.");
            return;
        }
        try {
            int size = dp(260);
            BitMatrix matrix = new QRCodeWriter().encode(tab.url, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            ImageView image = new ImageView(this);
            image.setImageBitmap(bitmap);
            image.setAdjustViewBounds(true);
            image.setPadding(dp(12), dp(12), dp(12), dp(12));
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            TextView host = new TextView(this);
            host.setText(safeHost(tab.url));
            host.setTextColor(INK);
            host.setGravity(Gravity.CENTER);
            host.setPadding(dp(8), dp(8), dp(8), dp(8));
            box.addView(image, new LinearLayout.LayoutParams(dp(284), dp(284)));
            box.addView(host, new LinearLayout.LayoutParams(-1, -2));
            new AlertDialog.Builder(this).setTitle("Scan to open this page").setView(box)
                    .setPositiveButton("Done", null)
                    .setNeutralButton("Copy link", (d, w) -> copyCurrentUrl())
                    .show();
        } catch (WriterException e) {
            showToast("Could not generate the QR code.");
        } catch (Exception e) {
            showToast("Could not display the QR code.");
        }
    }

    private void printCurrentPage() {
        Tab tab = currentTab();
        if (tab == null || tab.home || tab.webView == null || !PrivacyGuard.isHttpsUrl(tab.url)) {
            showToast("Open an HTTPS webpage before printing.");
            return;
        }
        try {
            PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            if (printManager == null) {
                showToast("Android printing is not available on this device.");
                return;
            }
            String title = tab.title == null || tab.title.trim().isEmpty()
                    ? safeHost(tab.url) : tab.title;
            String jobName = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (jobName.length() > 70) jobName = jobName.substring(0, 70);
            printManager.print("Hari Browser - " + jobName,
                    tab.webView.createPrintDocumentAdapter("Hari Browser - " + jobName),
                    new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setColorMode(PrintAttributes.COLOR_MODE_COLOR).build());
        } catch (Exception e) {
            showToast("Could not open Android's print or PDF screen.");
        }
    }

    private void copyCurrentUrl() {
        Tab tab = currentTab();
        if (tab == null || tab.home || !PrivacyGuard.isHttpsUrl(tab.url)) {
            showToast("Open an HTTPS webpage first.");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Hari Browser address", tab.url));
            showToast("Page address copied.");
        }
    }

    private void translateCurrentPage() {
        Tab tab = currentTab();
        if (tab == null || tab.home || !PrivacyGuard.isHttpsUrl(tab.url)) {
            showToast("Open an HTTPS webpage first.");
            return;
        }
        final String pageUrl = tab.url;
        new AlertDialog.Builder(this)
                .setTitle("Translate this page?")
                .setMessage("The current page URL will be sent to Google Translate. Continue only if you are comfortable sharing that address with the translation service.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (d, w) -> {
                    try {
                        String url = "https://translate.google.com/translate?sl=auto&tl=en&u=" +
                                URLEncoder.encode(pageUrl, "UTF-8");
                        addNewTab(url, true);
                    } catch (Exception e) {
                        showToast("Could not prepare the translation link.");
                    }
                })
                .show();
    }

    private void openCurrentExternally() {
        Tab tab = currentTab();
        if (tab == null || tab.home || !PrivacyGuard.isHttpsUrl(tab.url)) {
            showToast("Open an HTTPS webpage first.");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(tab.url));
            startActivity(Intent.createChooser(intent, "Open page with"));
        } catch (ActivityNotFoundException ignored) {
            showToast("No compatible app is available.");
        }
    }

    private void duplicateCurrentTab() {
        Tab tab = currentTab();
        if (tab == null) return;
        if (tab.home) addNewTab(null, true);
        else if (PrivacyGuard.isHttpsUrl(tab.url)) addNewTab(tab.url, true);
        else showToast("Only HTTPS pages can be duplicated.");
    }

    private void closeOtherTabs() {
        Tab keep = currentTab();
        if (keep == null) return;
        for (Tab tab : new ArrayList<>(tabs)) {
            if (tab != keep) destroyTab(tab);
        }
        tabs.clear();
        tabs.add(keep);
        currentTabIndex = 0;
        selectTab(0);
        showToast("Other tabs closed.");
    }

    private void restoreRecentlyClosedTab() {
        if (recentlyClosedUrls.isEmpty()) {
            showToast("No recently closed tab to restore.");
            return;
        }
        String url = recentlyClosedUrls.remove(recentlyClosedUrls.size() - 1);
        addNewTab(url, true);
    }

    private void exportBookmarks() {
        if (privateMode) {
            showToast("Bookmark export is unavailable in private browsing.");
            return;
        }
        JSONArray bookmarks = readArray(KEY_BOOKMARKS);
        if (bookmarks.length() == 0) {
            showToast("There are no bookmarks to export.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Export bookmarks?")
                .setMessage("The export contains readable website addresses and titles. Share it only with people you trust. Private browsing data is never included.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Export", (d, w) -> {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/json");
                    share.putExtra(Intent.EXTRA_SUBJECT, "Hari Browser bookmarks");
                    share.putExtra(Intent.EXTRA_TEXT, bookmarks.toString());
                    startActivity(Intent.createChooser(share, "Export bookmarks securely"));
                })
                .show();
    }

    private void importBookmarks() {
        if (privateMode) {
            showToast("Bookmark import is unavailable in private browsing.");
            return;
        }
        EditText input = new EditText(this);
        input.setHint("Paste a Hari Browser bookmark JSON export");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(5);
        input.setMaxLines(12);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        new AlertDialog.Builder(this)
                .setTitle("Import bookmarks")
                .setMessage("Only import files/text you trust. Only HTTPS bookmarks are accepted.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Import", (d, w) -> mergeImportedBookmarks(input.getText().toString()))
                .show();
    }

    private void mergeImportedBookmarks(String source) {
        if (privateMode) {
            showToast("Bookmark import is unavailable in private browsing.");
            return;
        }
        if (source == null || source.trim().isEmpty() || source.length() > 500000) {
            showToast("The bookmark export is empty or too large.");
            return;
        }
        try {
            JSONArray incoming = new JSONArray(source);
            JSONArray existing = readArray(KEY_BOOKMARKS);
            JSONArray merged = new JSONArray();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (int pass = 0; pass < 2; pass++) {
                JSONArray list = pass == 0 ? existing : incoming;
                for (int i = 0; i < list.length() && merged.length() < 500; i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) continue;
                    String url = item.optString("url", "");
                    if (!PrivacyGuard.isHttpsUrl(url) || !seen.add(url)) continue;
                    JSONObject safe = new JSONObject();
                    String title = item.optString("title", safeHost(url));
                    if (title.length() > 300) title = title.substring(0, 300);
                    safe.put("title", title);
                    safe.put("url", url);
                    safe.put("time", item.optLong("time", System.currentTimeMillis()));
                    merged.put(safe);
                }
            }
            secureStore.put(KEY_BOOKMARKS, merged.toString());
            showToast("Imported bookmark list saved securely (" + merged.length() + " total).");
        } catch (Exception e) {
            showToast("Invalid bookmark JSON. Nothing was imported.");
        }
    }

    private void toggleThemeAndRecreate() {
        settings.edit().putBoolean("dark_theme", !darkTheme()).apply();
        recreateWithSavedTabs();
    }

    private void recreateWithSavedTabs() {
        ArrayList<String> urls = new ArrayList<>();
        for (Tab tab : tabs) {
            urls.add(tab.home || !PrivacyGuard.isHttpsUrl(tab.url) ? "" : tab.url);
        }
        pendingThemeRestoreUrls = urls;
        pendingThemeRestoreIndex = Math.max(0, currentTabIndex);
        pendingThemeRestorePrivateMode = privateMode;
        recreate();
    }

    private void showSiteSecurity() {
        Tab tab = currentTab();
        if (tab == null || tab.home || !PrivacyGuard.isHttpsUrl(tab.url)) {
            new AlertDialog.Builder(this).setTitle("Hari Browser security")
                    .setMessage("HTTPS-only navigation is enabled. Open an HTTPS website to inspect its current protection settings.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String message = "Website: " + safeHost(tab.url) + "\n\n" +
                "Connection: HTTPS (certificate errors are rejected)\n" +
                "Safe Browsing: enabled where provided by Android WebView\n" +
                "Mixed content: blocked\n" +
                "Third-party cookies: blocked\n" +
                "Camera/microphone requests: denied by default\n" +
                "Geolocation: disabled\n" +
                "Local file/content access: disabled for webpages\n" +
                "Tracker blocking: " + (trackersEnabled() ? "enabled (local list)" : "disabled") + "\n" +
                "JavaScript: " + (javascriptEnabled() ? "enabled" : "disabled") + "\n" +
                "Anti-fingerprinting: " + (fingerprintProtectionActive(tab) ? "active" :
                        (fingerprintProtectionEnabled() ? "unsupported by this WebView" : "disabled")) + "\n" +
                "Reduces selected hardware hints, screen precision, canvas/WebGL signals and device identifiers.\n" +
                "Images: " + (imagesEnabled() ? "enabled" : "blocked") + "\n" +
                "Browser mode: " + (privateMode ? "private" : "standard") + "\n\n" +
                "HTTPS protects the connection in transit; it does not prove a website is trustworthy or prevent the site itself from tracking you.";
        new AlertDialog.Builder(this).setTitle("Site security").setMessage(message)
                .setPositiveButton("Copy address", (d, w) -> copyCurrentUrl())
                .setNegativeButton("Close", null).show();
    }

    private void showSecurityPrivacyScan() {
        Tab tab = currentTab();
        StringBuilder report = new StringBuilder();
        report.append("HARI BROWSER PROTECTION STATUS\n\n");
        report.append("✓ HTTPS-only navigation: ON (HTTP pages are rejected)\n");
        report.append("✓ TLS certificate errors: CANCELLED (invalid certificates are not bypassed)\n");
        report.append("✓ Android WebView Safe Browsing: enabled where supported by the installed WebView\n");
        report.append("✓ Mixed content: blocked by browser policy\n");
        report.append("✓ Third-party cookies: BLOCKED\n");
        report.append("Local tracker blocking: ").append(trackersEnabled() ? "ON (local list; incomplete)" : "OFF").append("\n");
        if (tab != null && !tab.home && fingerprintProtectionActive(tab)) {
            report.append("Anti-fingerprinting: ACTIVE for supported signals\n");
        } else if (!fingerprintProtectionEnabled()) {
            report.append("Anti-fingerprinting: OFF in Settings\n");
        } else {
            report.append("Anti-fingerprinting: enabled in Settings; some protections may be unsupported by this WebView\n");
        }
        report.append("JavaScript: ").append(javascriptEnabled() ? "ON (needed by many sites; adds script execution risk)" : "OFF").append("\n");
        report.append("First-party cookies: ").append(cookiesEnabled() ? "ON" : "OFF").append("\n");
        report.append("Website images: ").append(imagesEnabled() ? "ON" : "BLOCKED").append("\n");
        report.append("Teen Protection: ").append(teenProtectionEnabled() ? "ON" : "OFF").append("\n");
        report.append("Browsing history: ");
        if (privateMode) report.append("not saved in private mode\n");
        else report.append(historyEnabled() ? "ON (encrypted on this device)" : "OFF by default/disabled").append("\n");
        report.append("Site permissions: camera/microphone requests denied; geolocation disabled; local file/content access disabled\n");
        report.append("Clear browsing data on exit: ").append(settings.getBoolean("clear_data_on_exit", false) ? "ON (best effort when the activity exits)" : "OFF").append("\n");
        SharedPreferences downloadPrefs = settings;
        String latestDownloadStatus = downloadPrefs.getString("last_download_scan_status", null);
        if (latestDownloadStatus == null) {
            report.append("Browser download scan: no completed tracked download has been checked yet\n");
        } else {
            report.append("Latest browser download check: ").append(latestDownloadStatus).append(" (")
                    .append(downloadPrefs.getString("last_download_scan_filename", "download")).append(")\n");
        }

        report.append("\nWHAT THIS SCAN CANNOT DO\n");
        report.append("• It checks Hari Browser's settings and URL patterns, not every privacy or security measure of a website.\n");
        report.append("• Certificate validation and Safe Browsing perform additional checks during connection. Site content and server security headers are not fully audited by this local scan.\n");
        report.append("• Tracked downloads and user-selected files receive bounded local SHA-256, format-mismatch, EICAR test-signature and ZIP-based archive checks. APKs also receive a limited package-permission review. Optional MalwareBazaar reputation sends only a SHA-256 hash when configured, and is disabled for private-mode downloads. No complete ClamAV/YARA-class malware-signature engine is included, so these checks are not a full antivirus verdict.\n");
        report.append("• A BASIC_CHECKS_ONLY result is not a guarantee that a file is safe. Large files can be marked SCAN_INCOMPLETE. File contents are never uploaded; SHA-256 hashes are sent to MalwareBazaar only when the user configures optional hash reputation. Private-mode downloads stay local-only.\n");
        report.append("• This scan does not inspect all page content or prove a site's privacy claims. India-focused scam checks are URL heuristics and can miss scams or flag legitimate URLs. The Hari Antivirus screen lets you scan a user-selected file, review APK metadata, and manage an app-private quarantine; it does not scan every file or another app's private storage.\n");
        report.append("• Device-wide virus/malware scanning or cleaning is NOT available to Hari Browser. Android's app sandbox prevents an ordinary browser from safely scanning infections in other apps or private files. Use Google Play Protect for a device scan.");

        new AlertDialog.Builder(this)
                .setTitle("Security & privacy scan")
                .setMessage(report.toString())
                .setPositiveButton("Done", null)
                .setNegativeButton("Latest download scan", (dialog, which) -> showLastDownloadScan())
                .setNeutralButton("Site security details", (dialog, which) -> showSiteSecurity())
                .show();
    }

    private void showSitePermissionControls() {
        String message = "HARI BROWSER SITE PERMISSION POLICY\n\n" +
                "Camera and microphone: website permission requests are denied by default.\n" +
                "Location: WebView geolocation is disabled.\n" +
                "Local files and content providers: access is disabled for webpages.\n" +
                "Pop-up windows: automatic JavaScript windows and multiple WebView windows are disabled.\n" +
                "File uploads: a website can open only Android's system picker after you choose an upload control; Hari Browser does not grant general file-system access.\n\n" +
                "This is a deny-by-default policy, not a per-site allow-list. If a site needs a capability, use a trusted site and an external app instead. No permission prompt means a site has not been granted that capability by this browser.";
        new AlertDialog.Builder(this).setTitle("Site permission controls").setMessage(message)
                .setPositiveButton("Done", null).show();
    }

    private void showLastDownloadScan() {
        SharedPreferences downloadPrefs = settings;
        String report = downloadPrefs.getString("last_download_scan", null);
        if (report == null || report.trim().isEmpty()) {
            report = "No completed download tracked by Hari Browser has been scanned yet. Future completed downloads receive limited on-device checks. This is not a full antivirus engine.";
        }
        long suspiciousId = downloadPrefs.getLong("last_suspicious_download_id", -1L);
        String suspiciousName = downloadPrefs.getString("last_suspicious_download_filename", "flagged download");
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("Latest download scan")
                .setMessage(report + (suspiciousId >= 0 ? "\n\nLast flagged download: " + suspiciousName : ""))
                .setPositiveButton("Done", null);
        if (suspiciousId >= 0) {
            dialog.setNeutralButton("Delete flagged download", (d, w) ->
                    confirmDeleteFlaggedDownload(suspiciousId, suspiciousName));
        }
        dialog.show();
    }

    private void confirmDeleteFlaggedDownload(long downloadId, String filename) {
        new AlertDialog.Builder(this)
                .setTitle("Delete flagged download?")
                .setMessage("Delete '" + filename + "' from Android Download Manager? This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    int removed = manager == null ? 0 : manager.remove(downloadId);
                    SharedPreferences downloadPrefs = settings;
                    SharedPreferences.Editor edit = downloadPrefs.edit()
                            .remove("hari_browser_download_" + downloadId)
                            .remove("hari_browser_download_name_" + downloadId);
                    if (downloadPrefs.getLong("last_suspicious_download_id", -1L) == downloadId) {
                        edit.remove("last_suspicious_download_id").remove("last_suspicious_download_filename");
                    }
                    if (downloadPrefs.getLong("last_download_scan_id", -1L) == downloadId) {
                        edit.remove("last_download_scan").remove("last_download_scan_status")
                                .remove("last_download_scan_filename").remove("last_download_scan_sha256")
                                .remove("last_download_scan_id");
                    }
                    edit.apply();
                    showToast(removed > 0 ? "Flagged download deleted." : "Download entry was already unavailable.");
                })
                .show();
    }

    private void confirmEmergencyClear() {
        new AlertDialog.Builder(this)
                .setTitle("Emergency privacy clear")
                .setMessage("This will clear cookies, cache, site storage, SSL preferences, current tabs and saved browsing history. Bookmarks and downloaded files will remain.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear now", (d, w) -> {
                    clearBrowsingData();
                    for (Tab tab : new ArrayList<>(tabs)) destroyTab(tab);
                    tabs.clear();
                    currentTabIndex = -1;
                    addNewTab(null, true);
                    recentlyClosedUrls.clear();
                })
                .show();
    }

    private void destroyTab(Tab tab) {
        if (tab == null || tab.webView == null) return;
        try {
            if (tab.webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
            }
            tab.webView.stopLoading();
            tab.webView.removeAllViews();
            tab.webView.destroy();
        } catch (Exception ignored) {}
    }

    private void reconfigureWebViews() {
        for (Tab tab : tabs) {
            if (tab.webView != null) {
                WebSettings web = tab.webView.getSettings();
                web.setJavaScriptEnabled(tab.home || javascriptEnabled());
                web.setBlockNetworkImage(PrivacyGuard.shouldBlockNetworkImages(
                        tab.home, imagesEnabled(), dataSaverEnabled()));
                web.setTextZoom(textZoom());
                web.setUserAgentString(desktopEnabled() ? privacyReducedUserAgent().replace(" Mobile", "") : privacyReducedUserAgent());
                updateFingerprintProtection(tab);
                CookieManager.getInstance().setAcceptThirdPartyCookies(tab.webView, false);
            }
        }
    }

    private boolean fingerprintScriptSupported() {
        try {
            return WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean fingerprintProtectionActive(Tab tab) {
        return fingerprintProtectionEnabled() && fingerprintScriptSupported() &&
                tab != null && tab.fingerprintScriptHandler != null;
    }

    private void updateFingerprintProtection(Tab tab) {
        if (tab == null || tab.webView == null) return;
        if (tab.fingerprintScriptHandler != null) {
            try { tab.fingerprintScriptHandler.remove(); } catch (RuntimeException ignored) {}
            tab.fingerprintScriptHandler = null;
        }
        if (!fingerprintProtectionEnabled() || !fingerprintScriptSupported()) return;
        try {
            tab.fingerprintScriptHandler = WebViewCompat.addDocumentStartJavaScript(
                    tab.webView, FingerprintProtection.SCRIPT, Collections.singleton("*"));
        } catch (RuntimeException ignored) {
            // Fail safely: a protection failure must not prevent ordinary browsing.
        }
    }

    private void applyReaderStyles(Tab tab) {
        if (tab == null || tab.webView == null) return;
        String js = "(function(){var old=document.getElementById('hari-reader-style');" +
                "if(old)old.remove();var s=document.createElement('style');s.id='hari-reader-style';" +
                "s.textContent='body{max-width:780px!important;margin:24px auto!important;padding:18px!important;" +
                "font-family:Georgia,serif!important;font-size:20px!important;line-height:1.8!important;" +
                "background:#fff!important;color:#172033!important}img,video,iframe,nav,aside,footer,header," +
                "[role=banner],[role=navigation],.ad,.ads,.advertisement{max-width:100%!important}' ;" +
                "document.head.appendChild(s);})();";
        tab.webView.evaluateJavascript(js, null);
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this)
                .setTitle("Clear browsing data?")
                .setMessage("This clears cookies, cache, site storage and saved browsing history for this profile. Encrypted bookmarks are kept. Downloads are not deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear data", (d, w) -> clearBrowsingData())
                .show();
    }

    private void clearBrowsingData() {
        try {
            CookieManager cookies = CookieManager.getInstance();
            cookies.removeAllCookies(value -> cookies.flush());
            cookies.flush();
            WebStorage.getInstance().deleteAllData();
        } catch (Exception ignored) {}
        for (Tab tab : tabs) {
            if (tab.webView != null) {
                try {
                    tab.webView.clearCache(true);
                    tab.webView.clearHistory();
                    tab.webView.clearFormData();
                    tab.webView.clearSslPreferences();
                } catch (Exception ignored) {}
            }
        }
        if (!privateMode) secureStore.put(KEY_HISTORY, "[]");
        showToast("Browsing data cleared. Downloads and bookmarks were kept.");
    }

    private void requestDownload(String url, String contentDisposition, String mimeType) {
        if (!PrivacyGuard.isHttpsUrl(url)) {
            showToast("Downloads must use HTTPS.");
            return;
        }
        String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
        filename = filename.replaceAll("[^a-zA-Z0-9._() -]", "_");
        if (filename.trim().isEmpty() || filename.equals(".") || filename.equals("..")) filename = "download";
        final String safeFilename = filename;
        String type = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        boolean potentiallyExecutable = type.contains("apk") || type.contains("x-msdownload")
                || type.contains("x-executable") || type.contains("x-dosexec")
                || safeFilename.toLowerCase(Locale.ROOT).endsWith(".apk")
                || safeFilename.toLowerCase(Locale.ROOT).endsWith(".exe")
                || safeFilename.toLowerCase(Locale.ROOT).endsWith(".msi")
                || safeFilename.toLowerCase(Locale.ROOT).endsWith(".bat")
                || safeFilename.toLowerCase(Locale.ROOT).endsWith(".cmd")
                || safeFilename.toLowerCase(Locale.ROOT).endsWith(".sh")
                || safeFilename.toLowerCase(Locale.ROOT).endsWith(".jar");
        String warning = potentiallyExecutable
                ? "\\n\\nWarning: this file may install or run software. Hari Browser will not automatically execute it. Only keep it if you trust the source."
                : "";
        new AlertDialog.Builder(this)
                .setTitle("Download file")
                .setMessage("Save " + safeFilename + " to Downloads?" + warning
                        + "\\n\\nAfter download, Hari Browser can check the file type and calculate a checksum. This is not a malware verdict.")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("File safety info", (d, w) -> showToast("File checks detect some risky types only; they cannot prove a file is malware-free."))
                .setPositiveButton("Download", (d, w) -> startDownload(url, safeFilename, mimeType))
                .show();
    }

    private void startDownload(String url, String filename, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(filename);
            request.setDescription("Downloaded with Hari Browser");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setMimeType(mimeType == null ? "application/octet-stream" : mimeType);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            // Do not copy cookies or authorization headers into a separate download process.
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("Download service unavailable");
            long downloadId = manager.enqueue(request);
            // Track only files explicitly enqueued by Hari Browser. The receiver ignores every other download.
            String downloadPrefsName = privateMode ? "hari_browser_private_settings" : "hari_browser_settings";
            SharedPreferences downloadPrefs = getSharedPreferences(downloadPrefsName, MODE_PRIVATE);
            downloadPrefs.edit()
                    .putBoolean("hari_browser_download_" + downloadId, true)
                    .putString("hari_browser_download_name_" + downloadId, filename)
                    .apply();
            // The shared registry holds only the preference-file name, never the downloaded filename.
            getSharedPreferences("hari_browser_download_tracking", MODE_PRIVATE).edit()
                    .putString("download_prefs_" + downloadId, downloadPrefsName)
                    .apply();
            showToast("Download started. Hari Browser will run limited local checks when it completes; this is not full antivirus.");
        } catch (Exception e) {
            showToast("Download could not be started.");
        }
    }

    private void openDownloads() {
        try {
            startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
        } catch (ActivityNotFoundException ignored) {
            showToast("Open the Downloads app to view saved files.");
        }
    }

    private void sharePage() {
        Tab tab = currentTab();
        if (tab == null || tab.home || !PrivacyGuard.isHttpsUrl(tab.url)) {
            showToast("Open an HTTPS webpage before sharing.");
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, tab.title == null ? safeHost(tab.url) : tab.title);
        share.putExtra(Intent.EXTRA_TEXT, (tab.title == null ? "" : tab.title + "\n") + tab.url);
        startActivity(Intent.createChooser(share, "Share page link"));
    }

    private void showPrivacyInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Hari Browser privacy")
                .setMessage(
                        "Local-first design\\n\\n" +
                        "• HTTPS-only navigation; cleartext traffic is disabled.\\n" +
                        "• Android WebView Safe Browsing is enabled; certificate errors are cancelled.\\n" +
                        "• Third-party cookies are disabled; common ad/analytics hosts are blocked locally.\\n" +
                        "• The anti-fingerprinting shield reduces selected hardware hints, screen precision, canvas/WebGL signals and device identifiers when supported by Android System WebView.\\n" +
                        "• Bookmarks and optional history use AES-GCM encryption with a key held by Android Keystore.\\n" +
                        "• History is off by default; private mode never saves history.\\n" +
                        "• No app analytics, ad SDKs, or browser account. No remote reputation request is made unless you explicitly configure MalwareBazaar hash lookup; only SHA-256 hashes are submitted, never file contents or URLs.\\n\\n" +
                        "Limitations: the device's WebView provides the rendering engine; websites and the selected search engine receive normal requests. This app does not hide traffic from your network or internet provider. The blocker is incomplete, and private mode cannot erase downloads, screenshots, DNS/network logs, or every possible site artifact.")
                .setPositiveButton("OK", null)
                .show();
    }

    private String safeHost(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? "" : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean isBundledWallpaperName(String fileName) {
        switch (fileName) {
            case "aurora.jpg":
            case "ocean.jpg":
            case "sunset.jpg":
            case "forest.jpg":
            case "midnight.jpg":
            case "violet.jpg":
            case "desert.jpg":
            case "coral.jpg":
            case "glacier.jpg":
            case "nebula.jpg":
                return true;
            default:
                return false;
        }
    }

    private void refreshModeBadge() {
        if (modeBadge == null) return;
        modeBadge.setText(privateMode ? "PRIVATE" : "STANDARD");
        modeBadge.setContentDescription(privateMode ? "Private browsing session" : "Standard browsing mode");
        modeBadge.setTextColor(privateMode ? Color.WHITE : Color.rgb(0, 105, 94));
        modeBadge.setBackground(round(privateMode ? Color.rgb(94, 70, 174) : Color.rgb(216, 247, 241), 0, 12));
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
            addressBar.clearFocus();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            String url = intent.getData().toString();
            if (currentTab() == null) addNewTab(url, true);
            else navigateTo(currentTab(), url);
        }
    }

    @Override
    protected void onDestroy() {
        if (!privateMode && isFinishing() && settings != null &&
                settings.getBoolean("clear_data_on_exit", false)) {
            // Best-effort cleanup when the user explicitly exits the browser.
            try {
                CookieManager cookies = CookieManager.getInstance();
                cookies.removeAllCookies(value -> cookies.flush());
                cookies.flush();
                WebStorage.getInstance().deleteAllData();
            } catch (Exception ignored) {}
            for (Tab tab : tabs) {
                if (tab.webView != null) {
                    try {
                        tab.webView.clearCache(true);
                        tab.webView.clearHistory();
                        tab.webView.clearFormData();
                        tab.webView.clearSslPreferences();
                    } catch (Exception ignored) {}
                }
            }
            if (secureStore != null) secureStore.put(KEY_HISTORY, "[]");
        }
        if (privateMode) {
            // Keep private download names and scan reports out of the standard profile.
            if (settings != null) {
                settings.edit().putBoolean("private_session_active", false)
                        .remove("last_download_scan").remove("last_download_scan_status")
                        .remove("last_download_scan_filename").remove("last_download_scan_sha256")
                        .remove("last_download_scan_id").remove("last_suspicious_download_id")
                        .remove("last_suspicious_download_filename").apply();
            }
            SharedPreferences registry = getSharedPreferences("hari_browser_download_tracking", MODE_PRIVATE);
            SharedPreferences.Editor registryEdit = registry.edit();
            SharedPreferences.Editor privateDownloadEdit = settings == null ? null : settings.edit();
            for (String key : registry.getAll().keySet()) {
                if (key.startsWith("download_prefs_") &&
                        "hari_browser_private_settings".equals(registry.getString(key, ""))) {
                    String downloadId = key.substring("download_prefs_".length());
                    registryEdit.remove(key);
                    if (privateDownloadEdit != null) {
                        privateDownloadEdit.remove("hari_browser_download_" + downloadId)
                                .remove("hari_browser_download_name_" + downloadId)
                                .remove("hari_browser_download_scanning_" + downloadId);
                    }
                }
            }
            registryEdit.apply();
            if (privateDownloadEdit != null) privateDownloadEdit.apply();
            // Best-effort cleanup in the isolated WebView profile when private browsing closes.
            try {
                CookieManager cookies = CookieManager.getInstance();
                cookies.removeAllCookies(value -> cookies.flush());
                cookies.flush();
                WebStorage.getInstance().deleteAllData();
            } catch (Exception ignored) {}
        }
        if (fileChooserCallback != null) {
            try { fileChooserCallback.onReceiveValue(null); } catch (Exception ignored) {}
            fileChooserCallback = null;
        }
        if (fullscreenCustomView != null) hideFullscreenCustomView();
        for (Tab tab : tabs) {
            if (tab.webView != null) {
                try {
                    tab.webView.stopLoading();
                    tab.webView.clearHistory();
                    if (privateMode) {
                        tab.webView.clearCache(true);
                        tab.webView.clearFormData();
                        tab.webView.clearSslPreferences();
                    }
                    tab.webView.removeAllViews();
                    tab.webView.destroy();
                } catch (Exception ignored) {}
            }
        }
        tabs.clear();
        super.onDestroy();
    }
}
