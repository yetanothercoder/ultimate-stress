package ru.yetanothercoder.tests.stress;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.concurrent.Executors;
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

    private final Timer hwTimer;
    private final int randomDelay;
    private final TimeUnit delayUnit;
    private final Random r = new Random();

    public CountingServer(int port) {
        this(port, -1, null);
    }

    public CountingServer(int port, int randomDelay, TimeUnit delayUnit) {
        this.delayUnit = delayUnit;
        this.randomDelay = randomDelay;
        this.port = port;
        hwTimer = new HashedWheelTimer(10, TimeUnit.MILLISECONDS);
    }

    public void start() {
        // Configure the server.
        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        // Set up the pipeline factory.
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(new CountingHandler());
            }
        });

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port));

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable() {
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
        public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
            received.incrementAndGet();

            ChannelBuffer message = (ChannelBuffer) e.getMessage();
            //System.out.println("received:\n" + new String(message.array()));

            if (randomDelay > 0) {
                int delay = r.nextInt(randomDelay);
                hwTimer.newTimeout(new TimerTask() {
                    @Override
                    public void run(Timeout timeout) throws Exception {
                        writeAnswer(e);
                    }
                }, delay, delayUnit);
            } else {
                writeAnswer(e);
            }
        }

        private void writeAnswer(MessageEvent e) {
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
