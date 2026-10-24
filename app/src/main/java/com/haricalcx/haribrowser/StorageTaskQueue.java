package com.haricalcx.haribrowser;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One process-wide serial queue for small encrypted-store transactions.
 * Each task must capture only application-scoped objects, not an Activity or View.
 */
final class StorageTaskQueue {
    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "HariBrowser-Storage");
        thread.setDaemon(true);
        return thread;
    });

    private StorageTaskQueue() {}

    static void execute(Runnable task) {
        QUEUE.execute(Objects.requireNonNull(task, "task"));
    }
}
