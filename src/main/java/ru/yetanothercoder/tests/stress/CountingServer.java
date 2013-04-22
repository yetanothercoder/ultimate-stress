package ru.yetanothercoder.tests.stress;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Mikhail Baturov, 4/22/13 12:02 PM
 */
public class CountingServer {

    private final AtomicInteger received = new AtomicInteger(0);

    public static final ChannelBuffer RESP204 = ChannelBuffers.copiedBuffer(
            "HTTP/1.1 204 No Content\n" +
                    "Server: netty-stress\n" +
                    "Content-Length: 0\n\n", Charset.defaultCharset());

    private final int port;

    private final ScheduledExecutorService periodicExecutor = Executors.newSingleThreadScheduledExecutor();

    public CountingServer(int port) {
        this.port = port;
    }

    public void start() {
        // Configure the server.
        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newFixedThreadPool(8)));

        // Set up the pipeline factory.
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(new CountingHandler());
            }
        });

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port));

        periodicExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                int perSecond = received.getAndSet(0);
                if (perSecond > 0) {
                    System.out.printf("received: %s rps%n", perSecond);
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }




    private class CountingHandler extends SimpleChannelUpstreamHandler {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            received.incrementAndGet();

            ChannelBuffer message = (ChannelBuffer) e.getMessage();

            //System.out.println("received:\n" + new String(message.array()));

            e.getChannel().write(RESP204);
            e.getChannel().close();
        }
    }


    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8080;
        }
        new CountingServer(port).start();
    }
}
