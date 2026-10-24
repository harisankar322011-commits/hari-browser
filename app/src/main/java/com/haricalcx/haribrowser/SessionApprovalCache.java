package com.haricalcx.haribrowser;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/** Bounded, process-local approvals for destinations already reviewed this session. */
final class SessionApprovalCache {
    static final int MAX_APPROVED_URLS = 256;
    private static final Set<String> APPROVED_URLS = new LinkedHashSet<>();

    private SessionApprovalCache() {}

    static synchronized boolean contains(String url) {
        return url != null && APPROVED_URLS.contains(url);
    }

    static synchronized void approve(String url) {
        if (url == null || url.isEmpty()) return;
        // Refresh the insertion order when a destination is approved again.
        APPROVED_URLS.remove(url);
        APPROVED_URLS.add(url);
        while (APPROVED_URLS.size() > MAX_APPROVED_URLS) {
            Iterator<String> oldest = APPROVED_URLS.iterator();
            if (!oldest.hasNext()) break;
            oldest.next();
            oldest.remove();
        }
    }

    static synchronized void clear() {
        APPROVED_URLS.clear();
    }

    static synchronized int sizeForTests() {
        return APPROVED_URLS.size();
    }
}
