package ru.yetanothercoder.stress.stat;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class MetricTest {

    Metric stat = new Metric("testMetric");

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testCalculateTimeMetrics() throws Exception {
        List<Integer> times = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            times.add(i + 1);

        }
        Collections.shuffle(times);
        for (int t : times) {
            stat.register(t);
        }

        Metric.MetricResults result = stat.calculateAndReset();
        System.out.println(result);
        assertTrue(result.av == 500);
        assertTrue(result.p50 == 500);
        assertTrue(result.p75 == 750);
        assertTrue(result.p90 == 900);
        assertTrue(result.p99 == 990);

        Assert.assertTrue(stat.calculateAndReset().size == 0);

        stat.register(0);
        stat.register(0);
        stat.register(0);
        stat.register(1);

        result = stat.calculateAndReset();
        System.out.println(result);

        stat.register(0);
        System.out.println(stat.calculateAndReset());

    }
}