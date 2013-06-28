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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author Mikhail Baturov, 4/22/13 12:02 PM
 */
public class CountingServer {

    private final AtomicInteger received = new AtomicInteger(0);
    private final AtomicInteger errors = new AtomicInteger(0);

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
        hwTimer = new HashedWheelTimer(10, MILLISECONDS);
    }

    public void start() {
        System.out.printf("SERVER: started counting server on %s port with %,dms random delay%n", port, randomDelay);

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
                    System.out.printf("SERVER: received %,d rps, errors: %,d%n", perSecond, errors.getAndSet(0));
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
            //super.exceptionCaught(ctx, e);
        }

        private void writeAnswer(Channel channel) {
            channel.write(RESP204).addListener(ChannelFutureListener.CLOSE);
        }
    }


    public static void main(String[] args) throws Exception {
        final int port = Integer.valueOf(System.getProperty("port",  "8080"));
        final int delay = Integer.valueOf(System.getProperty("delay",  "100"));

        new CountingServer(port, delay, MILLISECONDS).start();
    }
}
