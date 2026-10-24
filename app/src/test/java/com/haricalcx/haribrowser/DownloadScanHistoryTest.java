package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class DownloadScanHistoryTest {
    @Test
    public void keepsNewestTwentyEntriesAndMovesRepeatedIdToFront() throws Exception {
        String serialized = "[]";
        for (int i = 0; i < DownloadScanHistory.MAX_ENTRIES + 4; i++) {
            serialized = DownloadScanHistory.append(serialized, i, "file-" + i + ".apk",
                    "REVIEW_REQUIRED", "Local inspection only", "", i).toString();
        }
        serialized = DownloadScanHistory.append(serialized, 3, "updated.apk",
                "SUSPICIOUS", "Needs review", "", 999).toString();

        JSONArray result = DownloadScanHistory.parse(serialized);
        assertEquals(DownloadScanHistory.MAX_ENTRIES, result.length());
        assertEquals(3L, result.getJSONObject(0).getLong("id"));
        assertEquals("updated.apk", result.getJSONObject(0).getString("filename"));
        assertEquals("SUSPICIOUS", result.getJSONObject(0).getString("status"));
        assertEquals(999L, result.getJSONObject(0).getLong("scannedAt"));
    }

    @Test
    public void sanitizesDisplayFieldsAndOnlyKeepsValidSha256() throws Exception {
        String filename = "bad\nname.apk";
        String longDetails = "x".repeat(DownloadScanHistory.MAX_DETAILS_CHARS + 50);
        JSONArray result = DownloadScanHistory.append("[]", 7, filename,
                "SUSPICIOUS<script>", longDetails, "not-a-hash", -5);
        JSONObject item = result.getJSONObject(0);

        assertEquals("bad name.apk", item.getString("filename"));
        assertEquals("SUSPICIOUSscript", item.getString("status"));
        assertEquals(DownloadScanHistory.MAX_DETAILS_CHARS, item.getString("details").length());
        assertEquals("", item.getString("sha256"));
        assertEquals(0L, item.getLong("scannedAt"));
    }

    @Test
    public void malformedStorageAndInvalidRecordsDoNotBreakHistory() throws Exception {
        assertEquals(0, DownloadScanHistory.parse("not-json").length());
        JSONArray invalid = new JSONArray().put(new JSONObject().put("id", -2))
                .put(new JSONObject().put("id", 5).put("filename", "safe.txt"))
                .put(new JSONObject().put("id", 5).put("filename", "duplicate.txt"));
        JSONArray parsed = DownloadScanHistory.parse(invalid.toString());
        assertEquals(1, parsed.length());
        assertEquals("Downloaded file", parsed.getJSONObject(0).getString("filename"));
    }

    @Test
    public void refusesNegativeDownloadId() {
        assertThrows(IllegalArgumentException.class, () -> DownloadScanHistory.append(
                "[]", -1, "file", "SCAN_INCOMPLETE", "unavailable", "", 0));
    }
}
