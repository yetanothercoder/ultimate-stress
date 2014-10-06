package ru.yetanothercoder.stress.timer;

/**
 * Scheduler for fixed rate task
 *
 * @author Mikhail Baturov, 6/27/13 1:24 PM
 */
public interface Scheduler {
    void startAtFixedRate(Runnable task, int rateMicros);

    void executeNow(Runnable task);

    void shutdown();
}
