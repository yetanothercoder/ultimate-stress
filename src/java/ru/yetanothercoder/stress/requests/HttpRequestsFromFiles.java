package ru.yetanothercoder.stress.requests;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Mikhail Baturov, 7/3/13 9:07 PM
 */
public class HttpRequestsFromFiles implements RequestSource {

    private final String prefix;
    private final File dir;
    private final List<ByteBuffer> contents;
    private final AtomicInteger i = new AtomicInteger(0);

    public HttpRequestsFromFiles(String path, String prefix) {
        this.dir = new File(path);
        if (!dir.exists()) throw new IllegalArgumentException("Incorrect path: " + path);
        this.prefix = prefix;

        try {
            contents = readFiles();
        } catch (IOException e) {
            throw new IllegalArgumentException("failed reading files from " + dir.getAbsolutePath(), e);
        }
    }

    private List<ByteBuffer> readFiles() throws IOException {
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return prefix == null || name.startsWith(prefix);
            }
        };

        List<ByteBuffer> result = new ArrayList<ByteBuffer>();
        File[] files = dir.listFiles(filter);
        Arrays.sort(files);
        for (File file : files) {
            byte[] contents = Files.readAllBytes(Paths.get(file.getPath()));
            result.add(ByteBuffer.wrap(add2EmptyLinesAtEnd(contents)));
        }

        return result;
    }

    @Override
    public ChannelBuffer next() {
        ByteBuffer bb = contents.get(i.getAndIncrement() % contents.size());
        ByteBuffer processed = processOnEachRequest(bb);
        return ChannelBuffers.wrappedBuffer(processed);
    }

    protected ByteBuffer processOnEachRequest(ByteBuffer fileContents) {
        return fileContents;
    }

    private byte[] add2EmptyLinesAtEnd(byte[] contents) {
        byte[] result = Arrays.copyOf(contents, contents.length + 2);
        result[contents.length] = 10;       // new line ascii code
        result[contents.length + 1] = 10;
        return result;
    }
}
