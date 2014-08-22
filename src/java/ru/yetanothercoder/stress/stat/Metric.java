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

        List<Integer> current = new ArrayList<>(size);
        MetricResults result = new MetricResults(name, size);
        while (size-- > 0) {
            int time = times.remove();
            result.av += time;
            current.add(time);
        }

        result.av /= current.size();

        Collections.sort(current, Collections.reverseOrder());

        int p99 = (int) (0.01 * current.size());
        int p90 = (int) (0.10 * current.size());
        int p75 = (int) (0.25 * current.size());
        int p50 = (int) (0.50 * current.size());

        for (int i = 0; i < current.size(); i++) {
            int time = current.get(i);
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
        return result;
    }

    public class MetricResults {
        final String name;
        final int size;
        int p50 = -1, p75 = -1, p90 = -1, p99 = -1, av = 0;

        public MetricResults(String name, int size) {
            this.name = name;
            this.size = size;
        }

        @Override
        public String toString() {
            return name + ",ms{" +
                    "av=" + av +
                    ",%=" + p50 + "/" + p75 + "/" + p90 + "/" + p99 +
                    ",qs=" + quickSize.get() + "/" + size +
                    '}';
        }
    }
}
