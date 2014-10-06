package ru.yetanothercoder.stress;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ru.yetanothercoder.stress.config.StressConfig;
import ru.yetanothercoder.stress.server.CountingServer;

import static java.util.concurrent.TimeUnit.SECONDS;

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
        StressConfig config = new StressConfig.Builder()
                .url("http://localhost:8888/")
                .rps(500)
                .connectionNum(300)
//                .readTimeout(100).writeTimeout(100)
                .build();
        StressClient client = new StressClient(config);
        SECONDS.sleep(1);

        client.start();
        SECONDS.sleep(100);

        client.stop(true);
        SECONDS.sleep(1);
        System.out.println();
        Assert.assertTrue("resp in client=" + client.respStats.size + ", req on server=" + server.ch.total.get(),
                Math.abs(client.respStats.size - server.ch.total.get()) < 10);
    }

    @Test
    public void testYa() throws Exception {
        StressClient client = new StressClient(new StressConfig.Builder().url("http://ya.ru").build());
        client.start();

        SECONDS.sleep(5);

        client.stop(true);
    }
}
