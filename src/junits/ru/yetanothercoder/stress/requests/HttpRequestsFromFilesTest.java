package ru.yetanothercoder.stress.requests;

import junit.framework.Assert;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;

/**
 * @author Mikhail Baturov, 7/4/13 4:38 PM
 */
public class HttpRequestsFromFilesTest {
    @Test
    public void testNext() throws Exception {
        final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        File file1 = File.createTempFile("http", ".tmp", tmpDir);
        File file2 = File.createTempFile("http", ".tmp", tmpDir);
        file1.deleteOnExit();
        file2.deleteOnExit();

        PrintWriter wr = new PrintWriter(file1);
        wr.print("abc");
        wr.close();

        wr = new PrintWriter(file2);
        wr.print("def");
        wr.close();

        HttpRequestsFromFiles source = new HttpRequestsFromFiles(tmpDir.getPath(), "http");

        Assert.assertEquals("abc\n\n", new String(source.next().array()));
        Assert.assertEquals("def\n\n", new String(source.next().array()));
        Assert.assertEquals("abc\n\n", new String(source.next().array()));
        Assert.assertEquals("def\n\n", new String(source.next().array()));
    }
}
