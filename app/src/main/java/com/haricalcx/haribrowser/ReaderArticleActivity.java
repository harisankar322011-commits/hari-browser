package com.haricalcx.haribrowser;

import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import java.net.URI;

/** Native, text-only article reader. Extracted web HTML is never loaded in this screen. */
public final class ReaderArticleActivity extends ComponentActivity {
    static final String EXTRA_TITLE = "reader_title";
    static final String EXTRA_BYLINE = "reader_byline";
    static final String EXTRA_TEXT = "reader_text";
    static final String EXTRA_URL = "reader_url";
    static final String EXTRA_LANGUAGE = "reader_language";
    static final String EXTRA_DIRECTION = "reader_direction";

    private static final int BACKGROUND = Color.rgb(250, 249, 246);
    private static final int INK = Color.rgb(28, 35, 48);
    private static final int MUTED = Color.rgb(94, 103, 118);
    private static final int ACCENT = Color.rgb(24, 89, 145);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String title = bounded(getIntent().getStringExtra(EXTRA_TITLE), ReaderArticleExtractor.MAX_TITLE_CHARS);
        String byline = bounded(getIntent().getStringExtra(EXTRA_BYLINE), ReaderArticleExtractor.MAX_BYLINE_CHARS);
        String text = bounded(getIntent().getStringExtra(EXTRA_TEXT), ReaderArticleExtractor.MAX_TEXT_CHARS);
        String url = bounded(getIntent().getStringExtra(EXTRA_URL), 8192);
        String direction = getIntent().getStringExtra(EXTRA_DIRECTION);

        // Validate again at the display boundary. This activity is internal and not exported.
        if (!ReaderArticleExtractor.isSafeHttpsUrl(url) || text.trim().length() < 32) {
            finish();
            return;
        }
        if (title.isEmpty()) title = "Article";
        if (direction == null) direction = "";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(18), dp(24), dp(18), dp(18));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("←", 25, ACCENT);
        back.setContentDescription("Back to browser");
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(42), dp(46)));
        TextView heading = text("Reader mode", 17, INK);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setPadding(dp(8), 0, 0, 0);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout article = new LinearLayout(this);
        article.setOrientation(LinearLayout.VERTICAL);
        article.setPadding(dp(2), dp(18), dp(2), dp(28));

        TextView titleView = text(title, 27, INK);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setLineSpacing(dp(2), 1.08f);
        article.addView(titleView);

        if (!byline.isEmpty()) {
            TextView bylineView = text(byline, 13, MUTED);
            bylineView.setPadding(0, dp(12), 0, 0);
            article.addView(bylineView);
        }

        TextView sourceView = text(sourceHost(url), 12, ACCENT);
        sourceView.setPadding(0, dp(7), 0, dp(16));
        article.addView(sourceView);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(220, 222, 226));
        article.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        TextView body = text(text, 18, INK);
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(6), 1.12f);
        body.setPadding(0, dp(20), 0, dp(22));
        if ("rtl".equals(direction)) body.setTextDirection(View.TEXT_DIRECTION_RTL);
        else if ("ltr".equals(direction)) body.setTextDirection(View.TEXT_DIRECTION_LTR);
        article.addView(body);

        TextView note = text("Text-only article extraction. Images, interactive content and original page layout are not included.", 12, MUTED);
        note.setPadding(0, dp(12), 0, dp(18));
        article.addView(note);

        scroll.addView(article);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String bounded(String value, int limit) {
        if (value == null) return "";
        String result = value.trim();
        if (result.length() > limit) result = result.substring(0, limit);
        if (!result.isEmpty() && Character.isHighSurrogate(result.charAt(result.length() - 1))) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String sourceHost(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost() == null ? "HTTPS article" : uri.getHost();
        } catch (Exception ignored) {
            return "HTTPS article";
        }
    }
}
