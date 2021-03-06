package ru.yetanothercoder.stress.requests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.Charset;
import java.util.List;

/**
 * @author Mikhail Baturov,  6/24/13 11:44 PM
 */
public class GetHttpRequestGenerator implements RequestGenerator {
    private final ByteBuf GET_REQUEST;

    public GetHttpRequestGenerator(String host, int port, String query, List<String> headers) {
        String getRequest = "GET %s HTTP/1.1%n";

        boolean hostHeader = false, agentHeader = false, connHeader = false;
        for (String header : headers) {
            if (!hostHeader && header.startsWith("Host:")) hostHeader = true;
            if (!agentHeader && header.startsWith("User-Agent:")) agentHeader = true;
            if (!connHeader && header.startsWith("Connection:")) connHeader = true;

            getRequest += header + "%n";
        }

        if (!hostHeader) getRequest += "Host: %s:%s%n";
        if (!agentHeader) getRequest += "User-Agent: github.com/yetanothercoder/ultimate-stress%n";
        if (!connHeader) getRequest += "Connection: close%n";

        getRequest = String.format(getRequest, query, host, port);

        GET_REQUEST = Unpooled.copiedBuffer(getRequest.getBytes(Charset.defaultCharset()));
    }

    @Override
    public ByteBuf next() {
        return GET_REQUEST.retain();
    }

    @Override
    public String toString() {
        return "GetHttpRequestGenerator";
    }
}
