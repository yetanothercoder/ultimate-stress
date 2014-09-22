package ru.yetanothercoder.stress;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ru.yetanothercoder.stress.config.StressConfig;
import ru.yetanothercoder.stress.server.CountingServer;

import java.util.concurrent.TimeUnit;

/**
 * @author Mikhail Baturov, 7/4/13 2:29 PM
 */
public class StressClientTest {
    private CountingServer server;

    @Before
    public void setUp() throws Exception {
        server = new CountingServer(8888, 50, false);
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    @Test
    public void testReceivingAndSending() throws Exception {
        StressConfig config = new StressConfig.Builder().url("http://localhost:8888/").rps(100).build();
        StressClient client = new StressClient(config);
        TimeUnit.SECONDS.sleep(1);

        client.start();
        TimeUnit.SECONDS.sleep(3);

        client.stop(true);
        TimeUnit.SECONDS.sleep(1);
//        System.out.println(client.respStats.size + " " + server.ch.total.get());
        Assert.assertTrue(client.respStats.size - server.ch.total.get() < 5);
    }

    @Test
    public void testYa() throws Exception {
        StressClient client = new StressClient(new StressConfig.Builder().url("http://ya.ru").build());
        client.start();

        TimeUnit.SECONDS.sleep(5);

        client.stop(true);
    }
}
