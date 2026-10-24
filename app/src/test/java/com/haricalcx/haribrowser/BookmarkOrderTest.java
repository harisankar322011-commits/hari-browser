package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public final class BookmarkOrderTest {
    private static String bookmarks() throws Exception {
        return new JSONArray()
                .put(new JSONObject().put("title", "One").put("url", "https://one.example/").put("time", 1))
                .put(new JSONObject().put("title", "Two").put("url", "https://two.example/").put("time", 2))
                .put(new JSONObject().put("title", "Three").put("url", "https://three.example/").put("time", 3))
                .toString();
    }

    @Test
    public void movesBookmarkUpAndPreservesItsMetadata() throws Exception {
        String moved = BookmarkOrder.move(bookmarks(), "https://three.example/", -1);
        JSONArray array = new JSONArray(moved);
        assertEquals("One", array.getJSONObject(0).getString("title"));
        assertEquals("Three", array.getJSONObject(1).getString("title"));
        assertEquals(3, array.getJSONObject(1).getInt("time"));
        assertEquals("Two", array.getJSONObject(2).getString("title"));
    }

    @Test
    public void movesBookmarkDownAndKeepsStableArraySchema() throws Exception {
        JSONArray array = new JSONArray(BookmarkOrder.move(bookmarks(), "https://one.example/", 1));
        assertEquals("Two", array.getJSONObject(0).getString("title"));
        assertEquals("One", array.getJSONObject(1).getString("title"));
        assertEquals(3, array.length());
    }

    @Test
    public void boundaryAndMissingUrlDoNotReorderList() throws Exception {
        String original = bookmarks();
        assertEquals(original, BookmarkOrder.move(original, "https://one.example/", -1));
        assertEquals(original, BookmarkOrder.move(original, "https://missing.example/", 1));
        assertEquals(original, BookmarkOrder.move(original, "http://one.example/", 1));
    }

    @Test
    public void malformedStorageReturnsEmptyArrayAndInvalidDirectionIsRejected() {
        assertEquals("[]", BookmarkOrder.move("not-json", "https://one.example/", 1));
        assertThrows(IllegalArgumentException.class, () ->
                BookmarkOrder.move("[]", "https://one.example/", 0));
    }
}
