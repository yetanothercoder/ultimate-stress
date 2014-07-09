package ru.yetanothercoder.stress;

import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ru.yetanothercoder.stress.requests.StubHttpRequest;
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
        StressClient client = new StressClient("localhost", 8888, 100, new StubHttpRequest());
        TimeUnit.SECONDS.sleep(1);

        client.start();
        TimeUnit.SECONDS.sleep(3);

        client.stop();
        TimeUnit.SECONDS.sleep(1);

        Assert.assertTrue(client.getSentTotal() - server.total.get() < 5);
    }

    @Test
    public void testYa() throws Exception {
        StressClient client = new StressClient("ya.ru", 80, 10, new StubHttpRequest());
        TimeUnit.SECONDS.sleep(1);

        client.start();
        TimeUnit.SECONDS.sleep(1);

        client.stop();
        TimeUnit.SECONDS.sleep(1);
    }
}
