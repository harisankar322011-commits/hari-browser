package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class OfflineReadingTest {
    @Test
    public void savesOnlyHttpsTextSnapshotsAndBoundsTheirSize() throws Exception {
        String body = "  article text  ";
        JSONArray result = OfflineReading.addOrReplace("[]", "Example article",
                "https://example.com/article", body, 123L);

        assertEquals(1, result.length());
        JSONObject saved = result.getJSONObject(0);
        assertEquals("Example article", saved.getString("title"));
        assertEquals("https://example.com/article", saved.getString("url"));
        assertEquals("article text", saved.getString("body"));
        assertEquals(123L, saved.getLong("savedAt"));
        assertThrows(IllegalArgumentException.class, () -> OfflineReading.addOrReplace(
                "[]", "HTTP article", "http://example.com/article", body, 124L));
        assertThrows(IllegalArgumentException.class, () -> OfflineReading.addOrReplace(
                "[]", "Local file", "file:///tmp/private.txt", body, 125L));
    }

    @Test
    public void replacesAnExistingSnapshotForTheSameUrl() throws Exception {
        String first = OfflineReading.addOrReplace("[]", "Old title",
                "https://example.com/article", "old body", 1L).toString();
        JSONArray updated = OfflineReading.addOrReplace(first, "Updated title",
                "https://example.com/article", "new body", 2L);

        assertEquals(1, updated.length());
        assertEquals("Updated title", updated.getJSONObject(0).getString("title"));
        assertEquals("new body", updated.getJSONObject(0).getString("body"));
        assertEquals(2L, updated.getJSONObject(0).getLong("savedAt"));
    }

    @Test
    public void keepsOnlyTheMostRecentConfiguredNumberOfSnapshots() throws Exception {
        String saved = "[]";
        for (int i = 0; i < OfflineReading.MAX_SAVED_PAGES + 3; i++) {
            saved = OfflineReading.addOrReplace(saved, "Page " + i,
                    "https://example.com/page/" + i, "Body " + i, i).toString();
        }
        JSONArray result = OfflineReading.parse(saved);
        assertEquals(OfflineReading.MAX_SAVED_PAGES, result.length());
        assertEquals("Page " + (OfflineReading.MAX_SAVED_PAGES + 2),
                result.getJSONObject(0).getString("title"));
    }

    @Test
    public void trimsAndCapsCapturedText() {
        String huge = "x".repeat(OfflineReading.MAX_BODY_CHARS + 20);
        assertEquals(OfflineReading.MAX_BODY_CHARS, OfflineReading.limitText(huge).length());
        assertEquals("small", OfflineReading.limitText("  small  "));
        assertEquals("", OfflineReading.limitText("  \n  "));
    }

    @Test
    public void decodesJavascriptResultAsPlainText() {
        assertEquals("line one\n\"quoted\"",
                OfflineReading.decodeJavascriptString("\"line one\\n\\\"quoted\\\"\""));
        assertEquals("", OfflineReading.decodeJavascriptString(null));
        assertEquals("", OfflineReading.decodeJavascriptString("null"));
    }

    @Test
    public void ignoresMalformedStoredItemsAndCanRemoveOneSnapshot() throws Exception {
        String saved = OfflineReading.addOrReplace("[]", "Keep",
                "https://example.com/keep", "kept", 1L).toString();
        saved = OfflineReading.addOrReplace(saved, "Remove",
                "https://example.com/remove", "removed", 2L).toString();

        JSONArray result = OfflineReading.removeByUrl(saved, "https://example.com/remove");
        assertEquals(1, result.length());
        assertEquals("https://example.com/keep", result.getJSONObject(0).getString("url"));
        assertEquals(0, OfflineReading.parse("not-json").length());
    }
}
