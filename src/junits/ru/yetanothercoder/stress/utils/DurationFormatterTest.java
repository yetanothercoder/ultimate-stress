package ru.yetanothercoder.stress.utils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class DurationFormatterTest {

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testFormat() throws Exception {
        Assert.assertEquals("1.1h", DurationFormatter.format(3_673_077L));
        Assert.assertEquals("2.46m", DurationFormatter.format(166_077L));
        Assert.assertEquals("3,08s", DurationFormatter.format(3_077L));
        Assert.assertEquals("77ms", DurationFormatter.format(-77));
        Assert.assertEquals("0ms", DurationFormatter.format(0));
    }
}