package tests;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Mikhail Baturov, 6/23/13 7:52 PM
 */
public class Tests {
    static final ScheduledExecutorService shExec = Executors.newSingleThreadScheduledExecutor();

    static AtomicInteger c = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("started!");
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                System.err.printf("thread=%s, exc:%n", t);
                e.printStackTrace();
            }
        });

        ScheduledFuture<?> result = shExec.scheduleAtFixedRate(new Delay2Sec(), 0, 1, TimeUnit.SECONDS);

        new Thread(new Runnable() {
            @Override
            public void run() {
                throw new RuntimeException();
            }
        }).start();

        try {
            result.get();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }


    static class Delay2Sec implements Runnable {

        @Override
        public void run() {
            System.out.println("counter=" + c.getAndIncrement());
            /*try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {*/
                //throw new RuntimeException();
//            }
        }
    }
}
