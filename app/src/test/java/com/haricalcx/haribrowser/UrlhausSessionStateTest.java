package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

public final class UrlhausSessionStateTest {
    @Test
    public void admitsOnlyFourConcurrentLookupsAndRejectsDuplicates() {
        UrlhausSessionState state = new UrlhausSessionState();
        assertEquals(UrlhausSessionState.LookupAdmission.STARTED, state.beginLookup(url(1)));
        assertEquals(UrlhausSessionState.LookupAdmission.ALREADY_RUNNING, state.beginLookup(url(1)));
        assertEquals(UrlhausSessionState.LookupAdmission.STARTED, state.beginLookup(url(2)));
        assertEquals(UrlhausSessionState.LookupAdmission.STARTED, state.beginLookup(url(3)));
        assertEquals(UrlhausSessionState.LookupAdmission.STARTED, state.beginLookup(url(4)));
        assertEquals(UrlhausSessionState.LookupAdmission.CAPACITY_REACHED, state.beginLookup(url(5)));
        assertEquals(4, state.pendingCount());
    }

    @Test
    public void completingLookupReleasesCapacity() {
        UrlhausSessionState state = new UrlhausSessionState();
        for (int i = 0; i < UrlhausSessionState.MAX_PENDING_LOOKUPS; i++) {
            assertEquals(UrlhausSessionState.LookupAdmission.STARTED, state.beginLookup(url(i)));
        }
        state.finishLookup(url(0));
        assertEquals(UrlhausSessionState.LookupAdmission.STARTED, state.beginLookup(url(9)));
        assertEquals(UrlhausSessionState.MAX_PENDING_LOOKUPS, state.pendingCount());
    }

    @Test
    public void cachesOnlyDefinitiveStatusesAndCapsCacheWithLeastRecentlyUsedEviction() {
        UrlhausSessionState state = new UrlhausSessionState();
        state.cacheResult(url(1), new UrlhausReputation.Result(false, "SERVICE_UNAVAILABLE", "unavailable"));
        assertNull(state.cachedResult(url(1)));

        for (int i = 0; i < UrlhausSessionState.MAX_CACHED_RESULTS; i++) {
            state.cacheResult(url(i), new UrlhausReputation.Result(false, "NO_MATCH", "no match"));
        }
        assertEquals(UrlhausSessionState.MAX_CACHED_RESULTS, state.cachedResultCount());

        // Refresh the first entry, then make space. LRU policy must retain the entry we touched.
        assertNotNull(state.cachedResult(url(0)));
        state.cacheResult(url(UrlhausSessionState.MAX_CACHED_RESULTS),
                new UrlhausReputation.Result(true, "KNOWN_MALWARE_URL", "listed"));
        assertEquals(UrlhausSessionState.MAX_CACHED_RESULTS, state.cachedResultCount());
        assertNotNull(state.cachedResult(url(0)));
        assertNull(state.cachedResult(url(1)));
        assertNotNull(state.cachedResult(url(UrlhausSessionState.MAX_CACHED_RESULTS)));
    }

    private static String url(int index) {
        return "https://example.test/" + index;
    }
}
