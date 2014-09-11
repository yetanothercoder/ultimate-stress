package ru.yetanothercoder.stress.timer;

public enum SchedulerType {
    HASHEDWHEEL() {
        @Override
        public Scheduler createScheduler() {
            return new HashedWheelScheduler();
        }
    },
    PLAIN() {
        @Override
        public Scheduler createScheduler() {
            return new PlainScheduler();
        }
    },
    STANDARD() {
        @Override
        public Scheduler createScheduler() {
            return new ExecutorScheduler();
        }
    };

    public abstract Scheduler createScheduler();
}
