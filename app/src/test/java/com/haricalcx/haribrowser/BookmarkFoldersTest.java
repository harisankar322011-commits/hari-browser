package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public final class BookmarkFoldersTest {
    private static String sample() throws Exception {
        return new JSONArray()
                .put(new JSONObject().put("title", "Legacy").put("url", "https://legacy.example/").put("time", 1))
                .put(new JSONObject().put("title", "School").put("url", "https://school.example/").put("time", 2).put("folder", "Education"))
                .put(new JSONObject().put("title", "Work").put("url", "https://work.example/").put("time", 3).put("folder", "Work"))
                .put(new JSONObject().put("title", "Unsafe").put("url", "javascript:alert(1)").put("folder", "Education"))
                .toString();
    }

    @Test
    public void legacyBookmarksBelongToUnfiledWithoutSchemaMigration() throws Exception {
        JSONArray list = new JSONArray(sample());
        assertEquals("Unfiled", BookmarkFolders.folderFor(list.getJSONObject(0)));
        assertEquals(Arrays.asList("Unfiled", "Education", "Work"), BookmarkFolders.folderNames(sample()));
    }

    @Test
    public void movingBookmarkAddsFolderAndPreservesOtherMetadata() throws Exception {
        JSONArray list = new JSONArray(BookmarkFolders.move(sample(), "https://legacy.example/", "Personal"));
        JSONObject item = list.getJSONObject(0);
        assertEquals("Personal", item.getString("folder"));
        assertEquals("Legacy", item.getString("title"));
        assertEquals(1, item.getInt("time"));
        assertEquals(4, list.length());
    }

    @Test
    public void unfiledRemovesOptionalFolderFieldAndFilteringKeepsValidUrlsOnly() throws Exception {
        String filed = BookmarkFolders.move(sample(), "https://legacy.example/", "Personal");
        String unfiled = BookmarkFolders.move(filed, "https://legacy.example/", "Unfiled");
        JSONObject item = new JSONArray(unfiled).getJSONObject(0);
        assertFalse(item.has("folder"));
        JSONArray education = new JSONArray(BookmarkFolders.filterByFolder(unfiled, "Education"));
        assertEquals(1, education.length());
        assertEquals("https://school.example/", education.getJSONObject(0).getString("url"));
    }

    @Test
    public void folderNamesAreBoundedAndControlCharactersAreRemoved() {
        String normalized = BookmarkFolders.normalizeFolderName("  My\n  Folder  ");
        assertEquals("My Folder", normalized);
        assertEquals(40, BookmarkFolders.normalizeFolderName("x".repeat(80)).length());
        assertEquals("", BookmarkFolders.normalizeFolderName(" \n\t "));
    }

    @Test
    public void malformedDataFailsClosedAndMissingUrlDoesNotChangeBookmarks() throws Exception {
        assertEquals("[]", BookmarkFolders.filterByFolder("not-json", "Unfiled"));
        assertEquals("[]", BookmarkFolders.move("not-json", "https://a.example/", "Work"));
        String original = sample();
        assertEquals(new JSONArray(original).toString(),
                BookmarkFolders.move(original, "https://missing.example/", "Work"));
    }
}
