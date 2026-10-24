package com.haricalcx.haribrowser;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public final class TabSessionStoreTest {
    @Test
    public void roundTripsOnlyBoundedSafeUrlsAndSelectedIndex() {
        List<String> urls = Arrays.asList("https://example.com/a", "", "http://insecure.example");
        TabSessionStore.Session session = TabSessionStore.decode(TabSessionStore.encode(urls, 1));
        assertEquals(Arrays.asList("https://example.com/a", ""), session.urls);
        assertEquals(1, session.selectedIndex);
    }

    @Test
    public void rejectsDangerousSchemesAndCredentials() {
        for (String url : Arrays.asList("http://example.com", "file:///secret",
                "javascript:alert(1)", "https://user:pass@example.com", "https:///missing-host")) {
            assertFalse(url, TabSessionStore.isSafeHttpsUrl(url));
        }
    }

    @Test
    public void boundsTabCountAndClampsSelectedIndex() {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < 30; i++) urls.add("https://site" + i + ".example");
        TabSessionStore.Session session = TabSessionStore.decode(
                TabSessionStore.encode(urls, 99));
        assertEquals(TabSessionStore.MAX_TABS, session.urls.size());
        assertEquals(TabSessionStore.MAX_TABS - 1, session.selectedIndex);
    }

    @Test
    public void malformedOrOversizedSessionRestoresEmptyState() {
        assertTrue(TabSessionStore.decode("not-json").urls.isEmpty());
        assertTrue(TabSessionStore.decode("x".repeat(200000)).urls.isEmpty());
    }
}
