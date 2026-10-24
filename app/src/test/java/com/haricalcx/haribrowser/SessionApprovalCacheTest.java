package com.haricalcx.haribrowser;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class SessionApprovalCacheTest {
    @After
    public void tearDown() {
        SessionApprovalCache.clear();
    }

    @Test
    public void evictsOldestApprovalAfterBoundIsReached() {
        for (int i = 0; i < SessionApprovalCache.MAX_APPROVED_URLS; i++) {
            SessionApprovalCache.approve("https://site" + i + ".example/");
        }
        SessionApprovalCache.approve("https://new.example/");

        assertEquals(SessionApprovalCache.MAX_APPROVED_URLS, SessionApprovalCache.sizeForTests());
        assertFalse(SessionApprovalCache.contains("https://site0.example/"));
        assertTrue(SessionApprovalCache.contains("https://site1.example/"));
        assertTrue(SessionApprovalCache.contains("https://new.example/"));
    }

    @Test
    public void refreshingApprovalPreservesItAsMostRecent() {
        SessionApprovalCache.approve("https://old.example/");
        for (int i = 0; i < SessionApprovalCache.MAX_APPROVED_URLS - 1; i++) {
            SessionApprovalCache.approve("https://site" + i + ".example/");
        }
        SessionApprovalCache.approve("https://old.example/");
        SessionApprovalCache.approve("https://new.example/");

        assertTrue(SessionApprovalCache.contains("https://old.example/"));
        assertFalse(SessionApprovalCache.contains("https://site0.example/"));
    }

    @Test
    public void ignoresEmptyApprovalsAndMatchesExactUrls() {
        SessionApprovalCache.approve(null);
        SessionApprovalCache.approve("");
        SessionApprovalCache.approve("https://example.com/");
        assertEquals(1, SessionApprovalCache.sizeForTests());
        assertFalse(SessionApprovalCache.contains("https://example.com/other"));
    }
}
