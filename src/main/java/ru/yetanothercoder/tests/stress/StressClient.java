package ru.yetanothercoder.tests.stress;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import java.net.BindException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Mikhail Baturov,  4/20/13 11:11 AM
 */
public class StressClient {

    private final int rps;
    private final int connLimit;
    private final String host;
    private final int port;
    private final ChannelBuffer GET;
    private final ClientBootstrap bootstrap;
    private final AtomicInteger connected = new AtomicInteger(0);
    private final AtomicInteger sent = new AtomicInteger(0);
    private final AtomicInteger te = new AtomicInteger(0);
    private final AtomicInteger be = new AtomicInteger(0);
    private final AtomicInteger ce = new AtomicInteger(0);
    private final String name;

    public StressClient(String name, String host, int port, int rps, int connLimit) {
        this.name = name;
        if (rps > 1_000_000) throw new UnsupportedOperationException("rps must be <=1M");

        this.port = port;
        this.connLimit = connLimit;
        this.rps = rps;
        this.host = host;
        GET = ChannelBuffers.copiedBuffer(
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
        //bootstrap.setOption("reuseAddress", "true");
    }

    public void start() {
        int rateMicros = 1000000 / rps;
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (connected.get() < connLimit) {
                    // counting connections here is not fully fair, but works!
                    connected.incrementAndGet();
                    ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));

                    future.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            // by logic you should decrementing connections here, but.. it's not work
                            // connected.decrementAndGet();
                        }
                    });
                }
            }
        }, 0, rateMicros, TimeUnit.MICROSECONDS);

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                System.out.printf("%10s stat: connected=%5s, sent=%5s, ERRORS: timeouts=%5s, binds=%5s, connects=%s%n",
                        name, connected.get(), sent.getAndSet(0), te.getAndSet(0), be.getAndSet(0), ce.getAndSet(0));
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        String host = "localhost";
        if (args.length > 0) {
            host = args[0];
        }
        int port = 8080;
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }

        new StressClient("client1", host, port, 40_000, 40_000).start();
//        new StressClient("client2", host, port, 30_000, 30_000).start();
    }


    private class StressClientHandler extends SimpleChannelUpstreamHandler {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            // the same here - decrementing connections here is not fully fair but works!
            connected.decrementAndGet();
            e.getChannel().close();
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            // by logic you should count connection here, but in practice - it doesn't work
            // connected.incrementAndGet();
            e.getChannel().write(GET);
            sent.incrementAndGet();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            e.getChannel().close();

            Throwable exc = e.getCause();

            if (exc instanceof ConnectTimeoutException) {
                te.incrementAndGet();
            } else if (exc instanceof BindException) {
                be.incrementAndGet();
            } else if (exc instanceof ConnectException) {
                ce.incrementAndGet();
            } else {
                exc.printStackTrace();
            }
        }
    }
}
