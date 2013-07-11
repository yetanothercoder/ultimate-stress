package ru.yetanothercoder.stress.exp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Mikhail Baturov, 7/11/13 1:59 PM
 */
public class LimitedSocketBased {

    final static AtomicInteger sent = new AtomicInteger(0);
    final static AtomicInteger errors = new AtomicInteger(0);

    static void doRequest(String host, int port) {
        try (Socket s = new Socket(host, port)) {
            PrintWriter pw = new PrintWriter(s.getOutputStream());
            pw.println("GET / HTTP/1.1");
            pw.println("Host: " + host);
            pw.println();
            pw.println();
            pw.flush();
            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String t;
            while ((t = br.readLine()) != null) {
//                System.out.println(t);
                if (t.length() == 0) break;
            }
            sent.incrementAndGet();
            br.close();
        } catch (Exception ignore) {
            errors.incrementAndGet();
        }

    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        startStatEverySec();

        int N = 10000;
        Thread[] threads = new Thread[N];

        for (int i = 0; i < N; i++) {
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) doRequest("localhost", 8080);
                }
            });

            threads[i].start();
        }
    }

    private static void startStatEverySec() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                System.out.printf("sent per sec: %d, errors per: %d%n", sent.getAndSet(0), errors.getAndSet(0));

            }
        }, 0, 1, SECONDS);
    }
}
