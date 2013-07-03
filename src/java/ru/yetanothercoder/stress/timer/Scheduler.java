package ru.yetanothercoder.stress.timer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Mikhail Baturov, 6/27/13 1:24 PM
 */
public interface Scheduler {
    void startAtFixedRate(Runnable task, AtomicInteger rateMicro);
    void executeNow(Runnable task);
    void shutdown();
}
