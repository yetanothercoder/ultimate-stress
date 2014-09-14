package ru.yetanothercoder.stress.requests;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.net.URI;
import java.nio.charset.Charset;

/**
 * @author Mikhail Baturov,  6/24/13 11:44 PM
 */
public class StubHttpGenerator implements RequestGenerator {
    final String STUB;

    public static void main(String[] args) {
        URI url = URI.create("http://ya.ru/my/app?p1=1&p2=2");
        System.out.println(url.getQuery());
        System.out.println(url.getRawQuery());
        System.out.println(url.getPath());
    }

    public StubHttpGenerator(String host, int port, String query) {
        STUB = String.format(
                "GET %s HTTP/1.1%n" +
                        "Host: %s:%s%n" +
                        "Connection: close%n" +
                        "User-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64; rv:11.0) Gecko/20100101 Firefox/11.0%n%n",

                query,
                host,
                port
        );
    }

    @Override
    public ChannelBuffer next() {
        return ChannelBuffers.copiedBuffer(STUB.getBytes(Charset.defaultCharset()));
    }

    @Override
    public String toString() {
        return "StubHttpGenerator";
    }
}
