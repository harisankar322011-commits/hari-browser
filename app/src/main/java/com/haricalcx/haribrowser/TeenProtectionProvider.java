package com.haricalcx.haribrowser;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Private, call-only provider that lets the isolated private WebView process
 * read the canonical Teen Protection flag from the main application process.
 * It deliberately exposes no PIN hashes or preference-writing operation.
 */
public final class TeenProtectionProvider extends ContentProvider {
    public static final Uri CONTENT_URI =
            Uri.parse("content://com.haricalcx.haribrowser.teenprotection/state");
    public static final String METHOD_IS_ENABLED = "is_teen_protection_enabled";
    public static final String RESULT_ENABLED = "enabled";
    private static final String PREFS_NAME = "hari_browser_protection_settings";

    @Override
    public boolean onCreate() {
        return getContext() != null;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!METHOD_IS_ENABLED.equals(method)) return super.call(method, arg, extras);
        Context context = getContext();
        if (context == null) return null;

        SharedPreferences preferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Bundle result = new Bundle();
        result.putBoolean(RESULT_ENABLED,
                preferences.getBoolean(TeenProtection.ENABLED_KEY, false));
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("TeenProtectionProvider is call-only.");
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("TeenProtectionProvider is read-only.");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("TeenProtectionProvider is read-only.");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("TeenProtectionProvider is read-only.");
    }
}
