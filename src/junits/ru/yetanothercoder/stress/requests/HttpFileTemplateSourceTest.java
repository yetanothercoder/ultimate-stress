package ru.yetanothercoder.stress.requests;

import junit.framework.Assert;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Mikhail Baturov, 7/4/13 4:38 PM
 */
public class HttpFileTemplateSourceTest {
    @Test
    public void testNext() throws Exception {
        final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        File file1 = File.createTempFile("http", ".tmp", tmpDir);
        File file2 = File.createTempFile("http", ".tmp", tmpDir);
        file1.deleteOnExit();
        file2.deleteOnExit();

        PrintWriter wr = new PrintWriter(file1);
        wr.print("abc$1zz$hp");
        wr.close();

        wr = new PrintWriter(file2);
        wr.print("def$2zz");
        wr.close();

        Map<String, String> repl = new HashMap<>();
        repl.put("$1", "11");
        repl.put("$2", "22");

        HttpFileTemplateSource source = new HttpFileTemplateSource(tmpDir.getPath(), "http", "77", repl);

        String[] requests = new String[] {new String(source.next().array()), new String(source.next().array()), new String(source.next().array()), new String(source.next().array())};

        Arrays.sort(requests); // against OS random directory listings

        Assert.assertEquals("abc11zz77\n\n", requests[0]);
        Assert.assertEquals("abc11zz77\n\n", requests[1]);
        Assert.assertEquals("def22zz\n\n", requests[2]);
        Assert.assertEquals("def22zz\n\n", requests[3]);
    }
}
