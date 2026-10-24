package com.haricalcx.haribrowser;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Regression tests for the serialized background queue used by encrypted-store operations. */
public final class StorageTaskQueueTest {
    @Test
    public void serializesTasksInSubmissionOrderOffTheCallingThread() throws Exception {
        List<Integer> observed = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch completed = new CountDownLatch(3);
        AtomicReference<Thread> worker = new AtomicReference<>();
        Thread caller = Thread.currentThread();

        for (int i = 0; i < 3; i++) {
            final int sequence = i;
            StorageTaskQueue.execute(() -> {
                worker.compareAndSet(null, Thread.currentThread());
                observed.add(sequence);
                completed.countDown();
            });
        }

        assertTrue("Storage work should complete", completed.await(5, TimeUnit.SECONDS));
        assertEquals(Arrays.asList(0, 1, 2), observed);
        assertNotNull(worker.get());
        assertNotSame("Storage work must not run on the caller", caller, worker.get());
    }
}
