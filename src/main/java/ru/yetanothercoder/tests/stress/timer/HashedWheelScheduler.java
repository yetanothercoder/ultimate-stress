package ru.yetanothercoder.tests.stress.timer;

import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

/**
 * @author Mikhail Baturov, 6/27/13 1:31 PM
 */
public class HashedWheelScheduler implements Scheduler {

    private final HashedWheelTimer hwTimer = new HashedWheelTimer(10, MICROSECONDS);

    @Override
    public void startAtFixedRate(final Runnable task, final AtomicInteger rateMicro) {
        hwTimer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (timeout.isCancelled()) return;

                hwTimer.newTimeout(this, rateMicro.get(), MICROSECONDS);
                task.run();
            }
        }, rateMicro.get(), MICROSECONDS);
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
