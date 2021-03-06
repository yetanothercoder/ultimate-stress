package ru.yetanothercoder.stress.stat;

import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fast & simple metrics calculator: average, percentiles, std etc.
 */
@ThreadSafe
public class Metric {
    public final String name;
    private final Queue<Integer> times = new ConcurrentLinkedQueue<>();
    private final AtomicInteger quickSize = new AtomicInteger(0);

    public Metric(String name) {
        this.name = name;
    }

    public boolean register(long v) {
        if (times.add((int) v)) {
            quickSize.incrementAndGet();
            return true;
        }
        return false;
    }

    public MetricResults calculateAndReset() {
        final int size = quickSize.getAndSet(0);
        MetricResults result = new MetricResults(name, size);
        if (size == 0) return result;

        List<Integer> snapshot = new ArrayList<>(size);
        int i = size;
        while (i-- > 0) {
            int time = times.remove();
            result.av += time;
            snapshot.add(time);
        }

        result.av /= size;

        Collections.sort(snapshot, Collections.reverseOrder());

        int p99 = (int) (0.01 * size);
        int p90 = (int) (0.10 * size);
        int p75 = (int) (0.25 * size);
        int p50 = (int) (0.50 * size);

        int std = 0;
        int max = Integer.MIN_VALUE, min = Integer.MAX_VALUE;
        for (i = 0; i < size; i++) {
            int time = snapshot.get(i);

            if (time > max) max = time;
            if (time < min) min = time;


            std += Math.pow(time - result.av, 2);

            if (result.p99 == 0 && i >= p99) {
                result.p99 = time;
            }

            if (result.p90 == 0 && i >= p90) {
                result.p90 = time;
            }

            if (result.p75 == 0 && i >= p75) {
                result.p75 = time;
            }

            if (result.p50 == 0 && i >= p50) {
                result.p50 = time;
                break;
            }
        }
        result.std = size < 2 ? 0 : (int) Math.sqrt(std / (size - 1));
        result.max = max;
        result.min = min;

        return result;
    }

    @NotThreadSafe
    public class MetricResults {
        public final String name;
        public final int size;
        public int p50, p75, p90, p99, av;
        public int std, max, min;

        public MetricResults(String name, int size) {
            this.name = name;
            this.size = size;
        }

        @Override
        public String toString() {
            return name + ",ms{" +
                    "av=" + av +
                    ",std=" + std +
                    ",min/max=" + min + "/" + max +
                    ",%=" + p50 + "/" + p75 + "/" + p90 + "/" + p99 +
                    ",qs=" + quickSize.get() + "/" + size +
                    "}";
        }
    }
}
