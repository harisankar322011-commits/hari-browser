package com.haricalcx.haribrowser;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class BrowserActivity extends ComponentActivity {
    private static final int BG = Color.rgb(244, 247, 250);
    private static final int CARD = Color.WHITE;
    private static final int INK = Color.rgb(17, 29, 48);
    private static final int MUTED = Color.rgb(102, 116, 136);
    private static final int ACCENT = Color.rgb(22, 184, 166);
    private static final int OUTLINE = Color.rgb(222, 229, 237);
    private static final String HOME_URL = "https://hari-browser-home.invalid/";
    private static final String KEY_BOOKMARKS = "bookmarks_v1";
    private static final String KEY_HISTORY = "history_v1";

    private SharedPreferences settings;
    private SecureStore secureStore;
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

    private boolean trackersEnabled() { return settings.getBoolean("block_trackers", true); }
    private boolean javascriptEnabled() { return settings.getBoolean("javascript", true); }
    private boolean desktopEnabled() { return settings.getBoolean("desktop_mode", false); }
    private boolean cookiesEnabled() { return settings.getBoolean("cookies_enabled", true); }
    private boolean historyEnabled() { return settings.getBoolean("history_enabled", false); }
    private boolean imagesEnabled() { return settings.getBoolean("images_enabled", true); }
    private int textZoom() { return settings.getInt("text_zoom", 100); }
    private String searchEngine() { return settings.getString("search_engine", "DuckDuckGo"); }

    private static final class Tab {
        WebView webView;
        String title = "New tab";
        String url = HOME_URL;
        boolean home = true;
        boolean readerMode = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        privateMode = this instanceof PrivateActivity;
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
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
        settings = getSharedPreferences("hari_browser_settings", MODE_PRIVATE);
        secureStore = new SecureStore(this);
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

        buildInterface();

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            addNewTab(intent.getData().toString(), true);
        } else {
            addNewTab(null, true);
        }
    }

    private void buildInterface() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(12), dp(8), dp(12), dp(6));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = Math.max(0, insets.getSystemWindowInsetTop());
            int bottomInset = Math.max(0, insets.getSystemWindowInsetBottom());
            root.setPadding(dp(12), dp(8) + topInset, dp(12), dp(6) + bottomInset);
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView brand = new TextView(this);
        brand.setText("◈  HARI BROWSER");
        brand.setTextColor(INK);
        brand.setTextSize(17);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(44), 1));

        modeBadge = new TextView(this);
        modeBadge.setText(privateMode ? "PRIVATE SESSION" : "STANDARD MODE");
        modeBadge.setTextSize(10);
        modeBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        modeBadge.setTextColor(privateMode ? Color.WHITE : Color.rgb(0, 105, 94));
        modeBadge.setPadding(dp(9), dp(7), dp(9), dp(7));
        modeBadge.setBackground(round(privateMode ? Color.rgb(94, 70, 174) : Color.rgb(216, 247, 241), 0, 12));
        header.addView(modeBadge);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setPadding(dp(10), dp(3), dp(4), dp(3));
        addressRow.setBackground(round(CARD, OUTLINE, 15));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextColor(INK);
        addressBar.setHintTextColor(MUTED);
        addressBar.setHint("Search privately or enter an HTTPS address");
        addressBar.setTextSize(15);
        addressBar.setSelectAllOnFocus(false);
        addressBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setPadding(dp(8), 0, dp(5), 0);
        addressBar.setBackgroundColor(Color.TRANSPARENT);
        addressRow.addView(addressBar, new LinearLayout.LayoutParams(0, dp(48), 1));

        TextView goButton = pillButton("GO", true, () -> navigateFromAddressBar());
        addressRow.addView(goButton, new LinearLayout.LayoutParams(dp(52), dp(40)));
        root.addView(addressRow, new LinearLayout.LayoutParams(-1, dp(54)));

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
        controls.setPadding(0, dp(4), 0, dp(4));
        addCompactButton(controls, "‹", "Back", () -> {
            WebView w = currentWebView();
            if (w != null && w.canGoBack()) w.goBack();
        });
        addCompactButton(controls, "›", "Forward", () -> {
            WebView w = currentWebView();
            if (w != null && w.canGoForward()) w.goForward();
        });
        addCompactButton(controls, "↻", "Reload", () -> {
            WebView w = currentWebView();
            if (w != null) {
                if (currentTab().home) loadHome(currentTab());
                else w.reload();
            }
        });
        tabButton = compactButton("Tabs 1", "Open tab switcher", () -> showTabSwitcher());
        controls.addView(tabButton, new LinearLayout.LayoutParams(0, dp(38), 1));
        addCompactButton(controls, "□", "New tab", () -> addNewTab(null, true));
        addCompactButton(controls, "⋮", "Menu", () -> showSettings());
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(44)));

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
        securityStatus.setTextSize(10);
        securityStatus.setSingleLine(true);
        securityStatus.setGravity(Gravity.CENTER_VERTICAL);
        securityStatus.setPadding(dp(3), dp(2), dp(3), dp(2));
        root.addView(securityStatus, new LinearLayout.LayoutParams(-1, dp(20)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(0, dp(4), 0, 0);
        addBottomButton(bottom, "⌂\nHome", () -> {
            Tab t = currentTab();
            if (t != null) loadHome(t);
        });
        addBottomButton(bottom, "★\nSave", () -> addBookmark());
        addBottomButton(bottom, "◷\nHistory", () -> showHistory());
        addBottomButton(bottom, "⌕\nFind", () -> showFindDialog());
        addBottomButton(bottom, "⚙\nSettings", () -> showSettings());
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(54)));

        setContentView(root);
        root.requestApplyInsets();
        refreshModeBadge();
    }

    private void addCompactButton(LinearLayout parent, String icon, String description, Runnable action) {
        parent.addView(compactButton(icon, description, action),
                new LinearLayout.LayoutParams(0, dp(38), 1));
    }

    private TextView compactButton(String text, String description, Runnable action) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setContentDescription(description);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(INK);
        view.setTextSize(text.length() > 2 ? 12 : 20);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setBackground(round(Color.WHITE, OUTLINE, 11));
        view.setPadding(dp(2), dp(2), dp(2), dp(2));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private TextView pillButton(String text, boolean primary, Runnable action) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(13);
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
        view.setTextSize(11);
        view.setLineSpacing(0f, 0.9f);
        view.setTextColor(INK);
        view.setPadding(dp(1), dp(2), dp(1), dp(2));
        view.setBackground(round(Color.WHITE, OUTLINE, 12));
        view.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
        lp.setMargins(dp(2), 0, dp(2), 0);
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
        if (tabButton != null) tabButton.setText("Tabs " + tabs.size());
        Tab tab = currentTab();
        if (tab != null && securityStatus != null) {
            String message = privateMode ? "Private profile • history not saved" :
                    (tab.home ? "Ready • HTTPS-only browsing" : "HTTPS • " + safeHost(tab.url));
            securityStatus.setText(message);
        }
    }

    private void configureWebView(Tab tab) {
        WebView webView = tab.webView;
        WebSettings web = webView.getSettings();
        web.setJavaScriptEnabled(javascriptEnabled());
        web.setBlockNetworkImage(!imagesEnabled());
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
        web.setUserAgentString(desktopEnabled() ? defaultUserAgent.replace(" Mobile", "") : defaultUserAgent);

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
                return PrivacyGuard.intercept(request, trackersEnabled());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme)) {
                    runOnUiThread(() -> showToast("Blocked insecure HTTP. Use an HTTPS version."));
                    return true;
                }
                if (!"https".equalsIgnoreCase(scheme) || uri.getHost() == null) {
                    runOnUiThread(() -> showToast("This link type is blocked for your safety."));
                    return true;
                }
                return false;
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
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                requestDownload(url, contentDisposition, mimeType));
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
                if ("Google".equals(engine)) return "https://www.google.com/search?q=" + encoded;
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
        tab.home = false;
        tab.readerMode = false;
        tab.url = url;
        tab.webView.loadUrl(url);
        updateTabControls();
    }

    private void loadHome(Tab tab) {
        if (tab == null || tab.webView == null) return;
        tab.home = true;
        tab.readerMode = false;
        tab.url = HOME_URL;
        tab.title = "Hari Browser";
        if (currentTab() == tab) addressBar.setText("");
        tab.webView.loadDataWithBaseURL(HOME_URL, homeHtml(), "text/html", "UTF-8", null);
        updateTabControls();
    }

    private String homeHtml() {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<meta charset='utf-8'><title>Hari Browser</title><style>" +
                "*{box-sizing:border-box}body{margin:0;background:#f4f7fa;color:#111d30;font-family:Arial,sans-serif}" +
                ".wrap{max-width:760px;margin:0 auto;padding:32px 18px 26px}.logo{font-size:13px;letter-spacing:2px;color:#168d80;font-weight:800}" +
                "h1{font-size:34px;line-height:1.12;margin:12px 0 10px}p{color:#667488;line-height:1.65}" +
                ".badge{display:inline-block;background:#d8f7f1;color:#00695e;border-radius:18px;padding:7px 11px;font-size:12px;font-weight:bold}" +
                ".grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:22px}" +
                "a.tile{display:block;text-decoration:none;background:#fff;border:1px solid #dee5ed;border-radius:16px;padding:16px;color:#111d30;font-weight:bold;min-height:78px}" +
                "a.tile span{display:block;font-size:12px;color:#667488;font-weight:normal;margin-top:5px;line-height:1.4}" +
                ".note{font-size:12px;background:#fff;border:1px solid #dee5ed;border-radius:14px;padding:14px;margin-top:22px}" +
                "</style></head><body><div class='wrap'><div class='logo'>◈ HARI BROWSER</div>" +
                "<h1>Your web.<br>More private.</h1><p>Search, learn and explore with privacy controls built in.</p>" +
                "<span class='badge'>HTTPS-only • Local tracker blocking</span>" +
                "<div class='grid'>" +
                "<a class='tile' href='https://duckduckgo.com/'>Search the web<span>DuckDuckGo search</span></a>" +
                "<a class='tile' href='https://www.wikipedia.org/'>Wikipedia<span>Knowledge and reference</span></a>" +
                "<a class='tile' href='https://github.com/'>GitHub<span>Code and open source</span></a>" +
                "<a class='tile' href='https://developer.mozilla.org/'>Web development<span>MDN documentation</span></a>" +
                "<a class='tile' href='https://www.nasa.gov/'>Space and science<span>NASA</span></a>" +
                "<a class='tile' href='https://www.bbc.com/news'>News<span>BBC News</span></a>" +
                "</div><div class='note'><b>Privacy by default</b><p>Browsing history is off by default. Bookmarks are encrypted on this device. Third-party cookies, HTTP pages, file access, and site permission requests are blocked or disabled.</p>" +
                "<p>Private mode uses a separate browser profile on Android 9+ and clears common site data when closed. It does not hide traffic from websites, your network, or your internet provider.</p></div></div></body></html>";
    }

    private void addBookmark() {
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
        String[] labels = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            String label = tab.title == null || tab.title.trim().isEmpty()
                    ? (tab.home ? "New tab" : safeHost(tab.url)) : tab.title;
            labels[i] = (i == currentTabIndex ? "● " : "") + label;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Tabs (" + tabs.size() + ")")
                .setItems(labels, (d, which) -> selectTab(which))
                .setPositiveButton("+ New tab", (d, w) -> addNewTab(null, true))
                .setNegativeButton("Close current", (d, w) -> closeCurrentTab())
                .setNeutralButton("Done", null)
                .create();
        dialog.show();
    }

    private void closeCurrentTab() {
        if (tabs.isEmpty()) return;
        Tab old = tabs.remove(currentTabIndex);
        try {
            if (old.webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) old.webView.getParent()).removeView(old.webView);
            }
            old.webView.stopLoading();
            old.webView.destroy();
        } catch (Exception ignored) {}
        if (tabs.isEmpty()) {
            currentTabIndex = -1;
            addNewTab(null, true);
        } else {
            selectTab(Math.min(currentTabIndex, tabs.size() - 1));
        }
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

    private void showSettings() {
        String[] options = {
                (trackersEnabled() ? "✓ " : "○ ") + "Block ads and trackers",
                (javascriptEnabled() ? "✓ " : "○ ") + "JavaScript (compatibility / risk)",
                (desktopEnabled() ? "✓ " : "○ ") + "Desktop website mode",
                (cookiesEnabled() ? "✓ " : "○ ") + "Allow first-party cookies",
                (historyEnabled() ? "✓ " : "○ ") + "Save browsing history",
                (currentTab() != null && currentTab().readerMode ? "✓ " : "○ ") + "Toggle reader-style view",
                "Clear cookies, cache, site storage and history",
                "Open downloads",
                "Bookmarks",
                "About privacy and security",
                "Open private browsing",
                "Share current page",
                "Choose search engine",
                (imagesEnabled() ? "✓ " : "○ ") + "Load website images",
                "Choose page text size"
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
                    }
                })
                .setNegativeButton("Close", null)
                .show();
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

    private void reconfigureWebViews() {
        for (Tab tab : tabs) {
            if (tab.webView != null) {
                WebSettings web = tab.webView.getSettings();
                web.setJavaScriptEnabled(javascriptEnabled());
                web.setBlockNetworkImage(!imagesEnabled());
                web.setTextZoom(textZoom());
                web.setUserAgentString(desktopEnabled() ? defaultUserAgent.replace(" Mobile", "") : defaultUserAgent);
                CookieManager.getInstance().setAcceptThirdPartyCookies(tab.webView, false);
            }
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
        if (filename.trim().isEmpty() || filename.equals(".") || filename.equals("..")) {
            filename = "download";
        }
        final String safeFilename = filename;
        String message = "Save " + safeFilename + " to Downloads? The download will remain on your device even after private browsing closes.";
        new AlertDialog.Builder(this)
                .setTitle("Download file")
                .setMessage(privateMode ? message : "Save " + safeFilename + " to your Downloads folder?")
                .setNegativeButton("Cancel", null)
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
            manager.enqueue(request);
            showToast("Download started. Check system notifications.");
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
        if (tab == null || !PrivacyGuard.isHttpsUrl(tab.url)) {
            showToast("Open an HTTPS webpage before sharing.");
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, tab.url);
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
                        "• Bookmarks and optional history use AES-GCM encryption with a key held by Android Keystore.\\n" +
                        "• History is off by default; private mode never saves history.\\n" +
                        "• No app analytics, ad SDKs, account, or remote blocklist.\\n\\n" +
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

    private void refreshModeBadge() {
        if (modeBadge == null) return;
        modeBadge.setText(privateMode ? "PRIVATE SESSION" : "STANDARD MODE");
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
        if (privateMode) {
            // Best-effort cleanup in the isolated WebView profile when private browsing closes.
            try {
                CookieManager cookies = CookieManager.getInstance();
                cookies.removeAllCookies(value -> cookies.flush());
                cookies.flush();
                WebStorage.getInstance().deleteAllData();
            } catch (Exception ignored) {}
        }
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
