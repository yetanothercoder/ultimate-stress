package ru.yetanothercoder.stress.timer;

import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static ru.yetanothercoder.stress.StressClient.NUM_OF_CORES;

/**
 * @author Mikhail Baturov, 6/27/13 1:26 PM
 */
public class ExecutorScheduler implements Scheduler {

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(NUM_OF_CORES, new DefaultThreadFactory("request-scheduler"));

    @Override
    public void startAtFixedRate(final Runnable task, final int rateMicros) {
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                if (executor.isShutdown()) return;

                executor.schedule(this, rateMicros, MICROSECONDS);
                task.run();
            }
        }, rateMicros, MICROSECONDS);
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
