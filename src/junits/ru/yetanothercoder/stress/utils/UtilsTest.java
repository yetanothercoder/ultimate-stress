package ru.yetanothercoder.stress.utils;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UtilsTest {

    @Test
    public void testStatusParsing() throws Exception {
        Assert.assertEquals(204, Utils.parseStatus("HTTP/1.1 204 No Content"));
        Assert.assertEquals(308, Utils.parseStatus("HTTP/1.0  308 No Content"));
        Assert.assertEquals(-1, Utils.parseStatus("HTTP  308 No Content"));
    }

    @Test
    public void testLatencyFormat() throws Exception {
        Assert.assertEquals("1.1h", Utils.formatLatency(3_673_077L));
        Assert.assertEquals("2.46m", Utils.formatLatency(166_077L));
        Assert.assertEquals("3,08s", Utils.formatLatency(3_077L));
        Assert.assertEquals("77ms", Utils.formatLatency(-77));
        Assert.assertEquals("0ms", Utils.formatLatency(0));
    }

    @Test
    public void testName() throws Exception {
        Path cd = Paths.get(".");
        System.out.println(Files.isDirectory(cd));
        System.out.println(Files.isReadable(cd));
        System.out.println(cd.toAbsolutePath());
    }
}