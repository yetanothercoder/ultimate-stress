package ru.yetanothercoder.stress.requests;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GetHttpRequestGeneratorTest {

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void test() throws Exception {
        GetHttpRequestGenerator g = new GetHttpRequestGenerator("myhost", 888, "/my?q=1", ImmutableList.of("User-Agent: Agent777", "Connection: open"));
        String request = g.next().toString(Charsets.UTF_8);

        Assert.assertEquals(String.format(
                "GET /my?q=1 HTTP/1.1%n" +
                        "User-Agent: Agent777%n" +
                        "Connection: open%n" +
                        "Host: myhost:888%n"), request);
    }
}