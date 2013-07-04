package ru.yetanothercoder.stress.requests;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.nio.charset.Charset;

/**
 * @author Mikhail Baturov,  6/24/13 11:44 PM
 */
public class StubHttpRequest implements RequestSource {
    final String STUB = "GET /my/app HTTP/1.1\n" +
            "Host: magichost!\n" +
            "Connection: close\n" +
            "User-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64; rv:11.0) Gecko/20100101 Firefox/11.0\n\n";

    @Override
    public ChannelBuffer next() {
        return ChannelBuffers.copiedBuffer(STUB.getBytes(Charset.defaultCharset()));
    }
}
