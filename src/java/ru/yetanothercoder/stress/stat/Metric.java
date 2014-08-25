package ru.yetanothercoder.stress.stat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @ThreadSafe
 */
public class Metric {
    public final String name;
    private final Queue<Integer> times = new ConcurrentLinkedQueue<>();
    private final AtomicInteger quickSize = new AtomicInteger(0);

    public Metric(String name) {
        this.name = name;
    }

    public boolean register(long ms) {
        if (times.add((int) ms)) {
            quickSize.incrementAndGet();
            return true;
        }
        return false;
    }

    public MetricResults calculateAndReset() {
        int size = quickSize.getAndSet(0);
        if (size == 0) return null;

        List<Integer> snapshot = new ArrayList<>(size);
        MetricResults result = new MetricResults(name, size);
        while (size-- > 0) {
            int time = times.remove();
            result.av += time;
            snapshot.add(time);
        }

        result.av /= snapshot.size();

        Collections.sort(snapshot, Collections.reverseOrder());

        int p99 = (int) (0.01 * snapshot.size());
        int p90 = (int) (0.10 * snapshot.size());
        int p75 = (int) (0.25 * snapshot.size());
        int p50 = (int) (0.50 * snapshot.size());

        int std = 0;
        int max = 0;
        for (int i = 0; i < snapshot.size(); i++) {
            int time = snapshot.get(i);

            if (time > max) max = time;

            std += Math.pow(time - result.av, 2);

            if (result.p99 < 0 && i >= p99) {
                result.p99 = time;
            }

            if (result.p90 < 0 && i >= p90) {
                result.p90 = time;
            }

            if (result.p75 < 0 && i >= p75) {
                result.p75 = time;
            }

            if (result.p50 < 0 && i >= p50) {
                result.p50 = time;
                break;
            }
        }
        result.std = snapshot.size() < 2 ? 0 : (int) Math.sqrt(std / (snapshot.size() - 1));
        result.max = max;

        return result;
    }

    public class MetricResults {
        final String name;
        final int size;
        int p50 = -1, p75 = -1, p90 = -1, p99 = -1, av = 0;
        int std = 0, max;

        public MetricResults(String name, int size) {
            this.name = name;
            this.size = size;
        }

        @Override
        public String toString() {
            return name + ",ms{" +
                    "av=" + av +
                    ",std=" + std +
                    ",max=" + max +
                    ",%=" + p50 + "/" + p75 + "/" + p90 + "/" + p99 +
                    ",qs=" + quickSize.get() + "/" + size +
                    "}";
        }
    }
}
