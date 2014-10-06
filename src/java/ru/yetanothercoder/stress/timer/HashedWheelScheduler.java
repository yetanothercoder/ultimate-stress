package ru.yetanothercoder.stress.timer;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

/**
 * @author Mikhail Baturov, 6/27/13 1:31 PM
 */
public class HashedWheelScheduler implements Scheduler {

    private final HashedWheelTimer hwTimer = new HashedWheelTimer(10, MICROSECONDS);

    @Override
    public void startAtFixedRate(final Runnable task, final int rateMicros) {
        hwTimer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (timeout.isCancelled()) return;

                hwTimer.newTimeout(this, rateMicros, MICROSECONDS);
                task.run();
            }
        }, rateMicros, MICROSECONDS);
    }

    @Override
    public void executeNow(final Runnable task) {
        hwTimer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (timeout.isCancelled()) return;

                task.run();
            }
        }, 0, TimeUnit.MICROSECONDS);
    }

    @Override
    public void shutdown() {
        hwTimer.stop();
    }
}
