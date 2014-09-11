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
        server = new CountingServer(8888);
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    @Test
    public void testReceivingAndSending() throws Exception {
        StressConfig config = new StressConfig.Builder("localhost").port(8888).rps(100).build();
        StressClient client = new StressClient(config);
        TimeUnit.SECONDS.sleep(1);

        client.start();
        TimeUnit.SECONDS.sleep(3);

        client.stop(true);
        TimeUnit.SECONDS.sleep(1);

        Assert.assertTrue(client.getSentTotal() - server.total.get() < 5);
    }

    @Test
    public void testYa() throws Exception {
        StressClient client = new StressClient("ya.ru");
        client.start();

        TimeUnit.SECONDS.sleep(5);

        client.stop(true);
    }
}
