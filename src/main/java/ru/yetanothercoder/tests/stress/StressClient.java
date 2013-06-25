package ru.yetanothercoder.tests.stress;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.handler.timeout.WriteTimeoutException;
import org.jboss.netty.handler.timeout.WriteTimeoutHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;

import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Integer.parseInt;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * TCP/IP Stress Client based on Netty
 *
 * @author Mikhail Baturov, http://www.yetanothercoder.ru/search/label/en
 */
public class StressClient {

    private final RequestSource requestSource;
    private final SocketAddress addr;
    private final int rps;
    private final int connLimit;
    private final ClientBootstrap bootstrap;
    private final AtomicInteger connected = new AtomicInteger(0);
    private final AtomicInteger sent = new AtomicInteger(0);
    private final AtomicInteger received = new AtomicInteger(0);
    private final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger te = new AtomicInteger(0);
    private final AtomicInteger be = new AtomicInteger(0);
    private final AtomicInteger ce = new AtomicInteger(0);
    private final AtomicInteger ie = new AtomicInteger(0);
    private final AtomicInteger nn = new AtomicInteger(0);
    private final String name = "Client#1";
    private final HashedWheelTimer hwTimer;
    private final ScheduledExecutorService statExecutor = Executors.newSingleThreadScheduledExecutor();
    private final StressClientHandler stressClientHandler = new StressClientHandler();
    private final boolean print;
    private final boolean debug;

    /**
     * connection limit factor (limit=rps*factor), as tcp connection number is slightly greater than response number
     */
    private static final double CONNECTION_LIMIT_FACTOR = 1.5;

    public StressClient(String host, int port, RequestSource requestSource, int rps) {
        this(host, port, requestSource, rps, -1, -1, false, false);
    }

    public StressClient(String host, int port, RequestSource requestSource, int rps,
                        final int readTimeoutMs, final int writeTimeoutMs, boolean print, boolean debug) {

        if (rps >= 1_000_000) throw new IllegalArgumentException("rps<=1M!");


        this.hwTimer = new HashedWheelTimer(1, TimeUnit.MILLISECONDS, 1024); // tuning these params didn't matter much

        this.requestSource = requestSource;
        this.print = print;
        this.addr = new InetSocketAddress(host, port);

        this.connLimit = (int) (rps * CONNECTION_LIMIT_FACTOR);
        this.rps = rps;
        this.debug = debug;

        bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        // Set up the pipeline factory.
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                if (readTimeoutMs > 0) {
                    pipeline.addLast("readTimer",
                            new ReadTimeoutHandler(hwTimer, readTimeoutMs, TimeUnit.MILLISECONDS));
                }
                if (writeTimeoutMs > 0) {
                    pipeline.addLast("writeTimer",
                            new WriteTimeoutHandler(hwTimer, writeTimeoutMs, TimeUnit.MILLISECONDS));
                }
                pipeline.addLast("stress", stressClientHandler);
                return pipeline;
            }
        });
        //bootstrap.setOption("reuseAddress", "true");
    }

    public void start() {
        System.out.printf("Started stress client `%s to `%s` with %,d rps%n", name, addr, rps);

        final int rateMicros = 1_000_000 / rps;
        hwTimer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (!timeout.isCancelled() && connected.get() < connLimit) {
                    sendOne();
                }
                hwTimer.newTimeout(this, rateMicros, MICROSECONDS);
            }
        }, rateMicros, MICROSECONDS);

        statExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                showStats();

            }
        }, 0, 1, SECONDS);
    }

    private void showStats() {
        int sentSoFar = sent.getAndSet(0);
        total.addAndGet(sentSoFar);
        System.out.printf("%10s STAT: connected=%5s, sent=%5s, received=%5s, ERRORS: timeouts=%5s, binds=%5s, connects=%5s, io=%5s, nn=%s%n",
                name,
                connected.get(),
                sentSoFar,
                received.getAndSet(0),
                te.getAndSet(0),
                be.getAndSet(0),
                ce.getAndSet(0),
                ie.getAndSet(0),
                nn.getAndSet(0)
        );
    }

    private void sendOne() {
        // counting connections beforehand!
        connected.incrementAndGet();

        ChannelFuture future = bootstrap.connect(addr);

//                    future.getChannel().getCloseFuture().addListener(new ChannelFutureListener() {
//                        @Override
//                        public void operationComplete(ChannelFuture future) throws Exception {
//                            // by logic you should decrementing connections here, but.. it's not work
//                            // connected.decrementAndGet();
//                        }
//                    });
    }

    public void stop() {
        System.out.printf("client `%s` stopping...%n", name);
        hwTimer.stop();
        statExecutor.shutdownNow();
        bootstrap.shutdown();
    }

    public static void main(String[] args) {
        final String host = args.length > 0 ? args[0] : "localhost";
        final int port = args.length > 1 ? parseInt(args[1]) : 8080;
        final int rps = args.length > 2 ? parseInt(args[2]) : 1_000;

        //new StressClient(, 40_000).start();
        final StressClient client = new StressClient(host, port, new StubHttpRequest(), rps);
        client.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                client.stop();
            }
        });
    }

    public int getSentTotal() {
        return total.get();
    }

    private class StressClientHandler extends SimpleChannelUpstreamHandler {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            ChannelBuffer resp = (ChannelBuffer) e.getMessage();
            String startLabel = resp.readBytes(64).toString(Charset.defaultCharset());
            if (startLabel.contains("HTTP/1.")) {
                // the same here - decrementing connections here is not fully fair but works!
                connected.decrementAndGet();
                received.incrementAndGet();
            }
            if (print) {
                System.out.println("\n" + startLabel + resp.toString(Charset.defaultCharset()));
            }
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            // by logic you should count connection here, but in practice - it doesn't work
            // connected.incrementAndGet();
            e.getChannel().write(requestSource.next());
            sent.incrementAndGet();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            e.getChannel().close();
            connected.decrementAndGet();

            Throwable exc = e.getCause();

            if (exc instanceof ConnectTimeoutException ||
                    exc instanceof ReadTimeoutException || exc instanceof WriteTimeoutException) {
                te.incrementAndGet();
            } else if (exc instanceof BindException) {
                be.incrementAndGet();
            } else if (exc instanceof ConnectException) {
                ce.incrementAndGet();
            } else if (exc instanceof IOException) {
                ie.incrementAndGet();
            } else {
                nn.incrementAndGet();
            }

            if (debug) {
                exc.printStackTrace(System.out);
            }
        }
    }
}
