package ru.yetanothercoder.stress.server;

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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author Mikhail Baturov, 4/22/13 12:02 PM
 */
public class CountingServer {

    private final AtomicInteger received = new AtomicInteger(0);
    public final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger errors = new AtomicInteger(0);

    public static final ChannelBuffer RESP204 = ChannelBuffers.copiedBuffer(
            "HTTP/1.1 204 No Content\n" +
                    "Server: netty-stress\n" +
                    "Content-Length: 0\n\n", Charset.defaultCharset());

    private final int port;
    private final boolean debug;

    private final Timer hwTimer;
    private final int randomDelay;
    private final TimeUnit delayUnit;
    private final Random r = new Random();
    private ServerBootstrap bootstrap;

    private volatile boolean stopped = false;

    public CountingServer(int port) {
        this(port, -1, null, false);
    }

    public CountingServer(int port, int randomDelay, TimeUnit delayUnit, boolean debug) {
        this.delayUnit = delayUnit;
        this.randomDelay = randomDelay;
        this.port = port;
        this.debug = debug;
        hwTimer = new HashedWheelTimer(10, MILLISECONDS);
    }

    public void start() {
        System.out.printf("SERVER: started counting server on %s port with %,d ms random delay%n", port, randomDelay);

        // Configure the server.
        bootstrap = new ServerBootstrap(
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
                total.addAndGet(perSecond);

                if (perSecond > 0) {
                    System.out.printf("SERVER: received %,d rps, errors: %,d%n", perSecond, errors.getAndSet(0));
                }
            }
        }, 0, 1, TimeUnit.SECONDS);

        enableStoppingOnShutdown();
    }

    private void enableStoppingOnShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                CountingServer.this.stop();
            }
        });
    }

    public void stop() {
        if (stopped) return;

        System.out.println("SERVER: stopping...");

        bootstrap.shutdown();
        hwTimer.stop();

        stopped = true;
    }

    private class CountingHandler extends SimpleChannelUpstreamHandler {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
            received.incrementAndGet();

            if (debug) {
                ChannelBuffer message = (ChannelBuffer) e.getMessage();
                System.out.printf("received: %s%n", new String(message.array()));
            }

            final Channel channel = e.getChannel();

            if (randomDelay > 0) {
                int delay = r.nextInt(randomDelay);

                hwTimer.newTimeout(new TimerTask() {
                    @Override
                    public void run(Timeout timeout) throws Exception {
                        if (!timeout.isCancelled() && channel.isOpen()) {
                            writeAnswer(channel);
                        }
                    }
                }, delay, delayUnit);
            } else {
                writeAnswer(channel);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            errors.incrementAndGet();

            if (debug) {
                e.getCause().printStackTrace(System.err);
            }
        }

        private void writeAnswer(Channel channel) {
            channel.write(RESP204).addListener(ChannelFutureListener.CLOSE);
        }
    }


    public static void main(String[] args) throws Exception {
        final int port = args.length > 0 ? Integer.valueOf(args[0]) : 8080;
        final int delay = args.length > 1 ? Integer.valueOf(args[1]) : 100;

        boolean debug = System.getProperty("debug") != null;

        final CountingServer server = new CountingServer(port, delay, MILLISECONDS, debug);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.stop();
            }
        });
    }
}
