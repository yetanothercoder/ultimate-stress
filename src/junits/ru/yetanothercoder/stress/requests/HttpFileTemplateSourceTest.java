package ru.yetanothercoder.stress.requests;

import org.junit.Assert;
import org.junit.Test;
import ru.yetanothercoder.stress.utils.HostPort;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
        wr.print("abc$1zz$HP");
        wr.close();

        wr = new PrintWriter(file2);
        wr.print("def$2zz");
        wr.close();

        Map<String, String> repl = new HashMap<>();
        repl.put("$1", "11");
        repl.put("$2", "22");

        HttpFileTemplateGenerator source = new HttpFileTemplateGenerator(Paths.get(tmpDir.getPath()), "http", "77", repl);

        String[] requests = new String[]{new String(source.next().array()), new String(source.next().array()), new String(source.next().array()), new String(source.next().array())};

        Arrays.sort(requests); // against OS random directory listings

        Assert.assertEquals("abc11zz77\n\n", requests[0]);
        Assert.assertEquals("abc11zz77\n\n", requests[1]);
        Assert.assertEquals("def22zz\n\n", requests[2]);
        Assert.assertEquals("def22zz\n\n", requests[3]);
    }

    @Test
    public void testHostPortFromTemplates() throws Exception {
        final File tmpDir = new File(System.getProperty("java.io.tmpdir"));

        File file = File.createTempFile("http1", ".tmp", tmpDir);
        file.deleteOnExit();
        PrintWriter wr = new PrintWriter(file);
        wr.print("GET /my/app/?param1=1 HTTP/1.0\n" +
                "Host: myhost777:8077\n" +
                "Connection: close\n" +
                "Referer: test.ru\n");
        wr.close();


        file = File.createTempFile("http1", ".tmp", tmpDir);
        file.deleteOnExit();
        wr = new PrintWriter(file);
        wr.print("GET /my/app/?param2=2 HTTP/1.0\n" +
                "Host: myhost888\n" +
                "Connection: close\n" +
                "Referer: test.ru\n");
        wr.close();

        file = File.createTempFile("http1", ".tmp", tmpDir);
        file.deleteOnExit();
        wr = new PrintWriter(file);
        wr.print("GET /my/app/?param2=2 HTTP/1.0\n" +
                "Connection: close\n" +
                "Referer: test.ru\n");
        wr.close();

        HttpFileTemplateGenerator source = new HttpFileTemplateGenerator(Paths.get(tmpDir.getPath()), "http1", null, null);

        List<HostPort> hp = source.parseHosts();

        Assert.assertTrue(hp.contains(new HostPort("myhost777", 8077)));
        Assert.assertTrue(hp.contains(new HostPort("myhost888", 80)));

        System.out.println(hp);
    }
}
