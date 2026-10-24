package com.haricalcx.haribrowser;

/** Pure policy for the parent-PIN failed-attempt counter and temporary lockout. */
final class TeenProtectionPinLockout {
    static final int MAX_ATTEMPTS = 5;
    static final long LOCKOUT_MILLIS = 60_000L;

    private TeenProtectionPinLockout() {}

    static final class State {
        final int failures;
        final long lockUntilMillis;
        final boolean locked;

        State(int failures, long lockUntilMillis, boolean locked) {
            this.failures = failures;
            this.lockUntilMillis = lockUntilMillis;
            this.locked = locked;
        }
    }

    static State afterFailedAttempt(int previousFailures, long nowMillis) {
        int failures = Math.max(0, previousFailures) + 1;
        if (failures >= MAX_ATTEMPTS) {
            return new State(0, nowMillis + LOCKOUT_MILLIS, true);
        }
        return new State(failures, 0L, false);
    }

    static long remainingSeconds(long lockUntilMillis, long nowMillis) {
        if (lockUntilMillis <= nowMillis) return 0L;
        return Math.max(1L, (lockUntilMillis - nowMillis + 999L) / 1000L);
    }
}
