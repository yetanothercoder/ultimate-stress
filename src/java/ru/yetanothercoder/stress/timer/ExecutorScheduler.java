package ru.yetanothercoder.stress.timer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

/**
 * @author Mikhail Baturov, 6/27/13 1:26 PM
 */
public class ExecutorScheduler implements Scheduler {

    private final ScheduledExecutorService requestExecutor = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void startAtFixedRate(final Runnable task, final AtomicInteger rateMicro) {
        requestExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                if (requestExecutor.isShutdown()) return;

                requestExecutor.schedule(this, rateMicro.get(), MICROSECONDS);
                task.run();
            }
        }, rateMicro.get(), MICROSECONDS);
    }

    @Override
    public void executeNow(Runnable task) {
        if (requestExecutor.isShutdown()) return;

        requestExecutor.execute(task);
    }

    @Override
    public void shutdown() {
        requestExecutor.shutdownNow();
    }
}
