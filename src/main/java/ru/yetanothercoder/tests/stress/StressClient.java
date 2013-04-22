package ru.yetanothercoder.tests.stress;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Mikhail Baturov,  4/20/13 11:11 AM
 */
public class StressClient {

    private final int rps;
    private final String host;
    private final int port;
    private final ChannelBuffer getRequest;
    private final ClientBootstrap bootstrap;

    public StressClient(int rps, String host, int port) {
        this.port = port;
        if (rps > 1000) throw new UnsupportedOperationException();

        this.rps = rps;
        this.host = host;
        getRequest = ChannelBuffers.copiedBuffer(
                        "GET /my/app HTTP/1.1\n" +
                        "Host: " + host + "\n" +
                        "Connection: close\n" +
                        "User-Agent: Mozilla/5.0 (Windows NT 6.1; WOW64; rv:11.0) Gecko/20100101 Firefox/11.0\n",
                Charset.defaultCharset());

        bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        // Set up the pipeline factory.
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(new StressClientHandler());
            }
        });
    }

    public void start() {
        int rateMillis = 1000 / rps;
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }, 0, rateMillis, TimeUnit.MILLISECONDS);
    }

    public static void main(String[] args) {

    }




    private class StressClientHandler extends SimpleChannelUpstreamHandler {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            super.messageReceived(ctx, e);
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            e.getChannel().write(getRequest);
        }
    }
}
