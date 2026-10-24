package com.haricalcx.haribrowser;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Offline-first, self-contained start page. No remote scripts, fonts, pixels or images are loaded.
 * External destinations are opened only after the user taps them.
 */
final class HomePage {
    private HomePage() {}

    private static volatile String cachedDirectorySource;
    private static volatile String cachedTemplateSource;

    static String render(Context context, boolean dark, String engine, String bookmarkJson, boolean privateMode,
                         String accentName, String language) {
        return renderWithTemplate(readTemplateAsset(context), readDirectoryAsset(context), dark, engine,
                bookmarkJson, privateMode, accentName, language);
    }

    /** Render the bundled page template with dynamic values; package-visible for JVM regression tests. */
    static String renderWithTemplate(String template, String directorySource,
                                     boolean dark, String engine, String bookmarkJson, boolean privateMode,
                                     String accentName, String language) {
        String accent = "#2563eb";
        String accentText = "#ffffff";
        if ("Violet".equals(accentName)) accent = "#7c3aed";
        else if ("Mint".equals(accentName)) accent = "#059669";
        else if ("Sunset".equals(accentName)) accent = "#ea580c";
        else if ("Rose".equals(accentName)) accent = "#e11d48";
        String theme = dark
                ? "--bg:#080c19;--surface:#12192d;--surface2:#1a2540;--text:#eef3ff;--muted:#9aa9c5;--line:#2b3751;--shadow:0 18px 52px #0005"
                : "--bg:#f6f7ff;--surface:#ffffff;--surface2:#edf0ff;--text:#161f37;--muted:#657189;--line:#dce0ef;--shadow:0 18px 52px #25396515";
        theme += ";--accent:" + accent + ";--accentText:" + accentText;
        String linksJson = directoryJson(directorySource);
        String savedJson = bookmarkJson == null || bookmarkJson.trim().isEmpty() ? "[]" : forScript(bookmarkJson);
        String selectedEngine = JSONObject.quote(engine == null ? "DuckDuckGo" : engine);
        String mode = privateMode ? "PRIVATE SESSION" : "NORMAL SESSION";
        String selectedLanguage = JSONObject.quote(language == null ? "en" : language);

        String html = template;
        if (html == null || html.isEmpty() || html.length() > 256 * 1024) {
            return "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>Hari Browser</title><body style=\"font:16px system-ui;padding:24px\"><h1>Hari Browser</h1><p>The offline home page could not be loaded. You can still use the address bar to open an HTTPS website.</p></body></html>";
        }
        return html.replace("THEME", theme)
                .replace("__DARKCLASS__", dark ? "dark" : "")
                .replace("__MODE__", mode)
                .replace("__DIRECTORY__", linksJson)
                .replace("__BOOKMARKS__", savedJson)
                .replace("__ENGINE__", selectedEngine)
                .replace("__PRIVATE__", Boolean.toString(privateMode))
                .replace("__LANGUAGE__", selectedLanguage);
    }

    /** Read the static HTML/CSS/JavaScript template from a bounded local-only APK asset. */
    private static String readTemplateAsset(Context context) {
        if (context == null) return "";
        String cached = cachedTemplateSource;
        if (cached != null) return cached;
        synchronized (HomePage.class) {
            if (cachedTemplateSource != null) return cachedTemplateSource;
            try (InputStream input = context.getAssets().open("home_page_template.html");
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                int total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 256 * 1024) {
                        cachedTemplateSource = "";
                        return cachedTemplateSource;
                    }
                    output.write(buffer, 0, count);
                }
                String loaded = new String(output.toByteArray(), StandardCharsets.UTF_8);
                cachedTemplateSource = loaded.startsWith("<!doctype html>")
                        && loaded.contains("__DIRECTORY__") && loaded.contains("__BOOKMARKS__")
                        ? loaded : "";
            } catch (IOException unavailable) {
                // Fail to a static offline fallback instead of evaluating a partially read template.
                cachedTemplateSource = "";
            }
            return cachedTemplateSource;
        }
    }

    /** Read the static curated directory from a bounded, local-only APK asset. */
    private static String readDirectoryAsset(Context context) {
        if (context == null) return "";
        String cached = cachedDirectorySource;
        if (cached != null) return cached;
        synchronized (HomePage.class) {
            if (cachedDirectorySource != null) return cachedDirectorySource;
            try (InputStream input = context.getAssets().open("home_directory.txt");
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                int total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 65536) {
                        cachedDirectorySource = "";
                        return cachedDirectorySource;
                    }
                    output.write(buffer, 0, count);
                }
                cachedDirectorySource = new String(output.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException unavailable) {
                // Home remains functional if a future packaging mistake drops this optional list.
                cachedDirectorySource = "";
            }
            return cachedDirectorySource;
        }
    }

    /** Parse only well-formed HTTPS entries and encode them safely for an inline script value. */
    static String directoryJson(String source) {
        JSONArray directory = new JSONArray();
        String normalized = source == null ? "" : source.trim();
        if (!normalized.isEmpty()) {
            for (String row : normalized.split("\\R")) {
                String[] parts = row.split("\\|", 3);
                if (parts.length != 3 || !parts[2].startsWith("https://")) continue;
                if (parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) continue;
                try {
                    JSONObject item = new JSONObject();
                    item.put("category", parts[0]);
                    item.put("name", parts[1]);
                    item.put("url", parts[2]);
                    directory.put(item);
                } catch (JSONException ignored) {}
            }
        }
        return forScript(directory.toString());
    }

    private static String forScript(String json) {
        // Prevent user-supplied bookmark titles/URLs from closing the containing script element.
        String slash = "\\";
        return json.replace("&", slash + "u0026")
                .replace("<", slash + "u003c")
                .replace(">", slash + "u003e");
    }
}
