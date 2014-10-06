package ru.yetanothercoder.stress.timer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Mikhail Baturov, 6/27/13 1:34 PM
 */
public class PlainScheduler implements Scheduler {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean stop = false;

    @Override
    public void startAtFixedRate(final Runnable task, final int rateMicros) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    task.run();

                    try {
                        TimeUnit.MICROSECONDS.sleep(rateMicros);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        });
    }

    @Override
    public void executeNow(Runnable task) {
        if (executor.isShutdown()) return;

        executor.execute(task);
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
    }
}
