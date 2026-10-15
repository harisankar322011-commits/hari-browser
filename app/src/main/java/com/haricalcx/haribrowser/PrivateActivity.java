package com.haricalcx.haribrowser;

import android.os.Build;
import android.os.Bundle;
import android.webkit.WebView;

/**
 * Runs in a dedicated Android process so it can use its own WebView data directory.
 * The suffix must be set before any WebView is created in this process.
 */
public class PrivateActivity extends BrowserActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix("private");
        }
        super.onCreate(savedInstanceState);
    }
}
