package ru.yetanothercoder.tests.stress.requests;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * @author Mikhail Baturov, 7/3/13 9:07 PM
 */
public class HttpRequestsFromFiles implements RequestSource {

    private final String prefix;

    public HttpRequestsFromFiles(String prefix) {
        this.prefix = prefix;


    }

    @Override
    public ChannelBuffer next() {
        return null;
    }
}
