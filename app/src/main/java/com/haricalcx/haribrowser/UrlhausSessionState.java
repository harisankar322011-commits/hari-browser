package com.haricalcx.haribrowser;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Bounds the lifetime and concurrency of optional, session-only URL reputation work.
 *
 * <p>Only definitive URLhaus outcomes are cached. The cache is an access-ordered LRU
 * so a long browsing session cannot accumulate an unbounded number of visited URLs.</p>
 */
final class UrlhausSessionState {
    static final int MAX_PENDING_LOOKUPS = 4;
    static final int MAX_CACHED_RESULTS = 128;

    enum LookupAdmission {
        STARTED,
        ALREADY_RUNNING,
        CAPACITY_REACHED,
        INVALID_URL
    }

    private final Set<String> pending = new LinkedHashSet<>();
    private final LinkedHashMap<String, UrlhausReputation.Result> cached =
            new LinkedHashMap<>(16, 0.75f, true);

    synchronized LookupAdmission beginLookup(String url) {
        if (url == null || url.trim().isEmpty()) return LookupAdmission.INVALID_URL;
        if (pending.contains(url)) return LookupAdmission.ALREADY_RUNNING;
        if (pending.size() >= MAX_PENDING_LOOKUPS) return LookupAdmission.CAPACITY_REACHED;
        pending.add(url);
        return LookupAdmission.STARTED;
    }

    synchronized void finishLookup(String url) {
        if (url != null) pending.remove(url);
    }

    synchronized UrlhausReputation.Result cachedResult(String url) {
        if (url == null) return null;
        return cached.get(url);
    }

    synchronized void cacheResult(String url, UrlhausReputation.Result result) {
        if (url == null || url.trim().isEmpty() || result == null) return;
        if (!"KNOWN_MALWARE_URL".equals(result.status) && !"NO_MATCH".equals(result.status)) return;

        cached.put(url, result);
        while (cached.size() > MAX_CACHED_RESULTS) {
            Iterator<Map.Entry<String, UrlhausReputation.Result>> iterator =
                    cached.entrySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
    }

    synchronized int pendingCount() {
        return pending.size();
    }

    synchronized int cachedResultCount() {
        return cached.size();
    }
}
