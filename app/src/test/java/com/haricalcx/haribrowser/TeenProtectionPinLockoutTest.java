package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

/** Regression tests for failed parent-PIN attempts and their temporary lockout. */
public final class TeenProtectionPinLockoutTest {
    @Test
    public void firstFourFailuresIncrementCounterWithoutLocking() {
        for (int previous = 0; previous < 4; previous++) {
            TeenProtectionPinLockout.State state =
                    TeenProtectionPinLockout.afterFailedAttempt(previous, 1_000L);

            assertFalse("Failure " + (previous + 1) + " must not lock yet", state.locked);
            assertEquals(previous + 1, state.failures);
            assertEquals(0L, state.lockUntilMillis);
        }
    }

    @Test
    public void fifthFailureStartsOneMinuteLockoutAndResetsFailureCounter() {
        TeenProtectionPinLockout.State state =
                TeenProtectionPinLockout.afterFailedAttempt(4, 12_345L);

        assertTrue(state.locked);
        assertEquals(0, state.failures);
        assertEquals(72_345L, state.lockUntilMillis);
    }

    @Test
    public void remainingLockoutTimeIsRoundedUpAndNeverNegative() {
        assertEquals(60L, TeenProtectionPinLockout.remainingSeconds(61_000L, 1_000L));
        assertEquals(1L, TeenProtectionPinLockout.remainingSeconds(1_001L, 1_000L));
        assertEquals(0L, TeenProtectionPinLockout.remainingSeconds(1_000L, 1_000L));
        assertEquals(0L, TeenProtectionPinLockout.remainingSeconds(500L, 1_000L));
    }
}
