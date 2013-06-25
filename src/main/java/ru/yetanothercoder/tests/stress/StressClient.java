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

import java.io.IOException;
import java.net.*;
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
    private String name;

    private final HashedWheelTimer hwTimer;
    private final ScheduledExecutorService statExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService requestExecutor = Executors.newSingleThreadScheduledExecutor();

    private final StressClientHandler stressClientHandler = new StressClientHandler();
    private final boolean print;
    private final boolean debug;

    /**
     * in practice, real rps was this times lower, seems due to jvm overhead
     */
    private static final double RPS_IMPERICAL_MULTIPLIER = 2;

    public StressClient(String host, int port, RequestSource requestSource, int rps) {
        this(host, port, requestSource, rps, -1, -1, false, false);
    }

    public StressClient(String host, int port, RequestSource requestSource, int rps,
                        final int readTimeoutMs, final int writeTimeoutMs, boolean print, boolean debug) {

        if (rps >= 1_000_000) throw new IllegalArgumentException("rps<=1M!");


        this.hwTimer = new HashedWheelTimer(100, MICROSECONDS); // tuning these params didn't matter much

        this.requestSource = requestSource;
        this.print = print;
        this.addr = new InetSocketAddress(host, port);

        this.connLimit = (int) (rps * RPS_IMPERICAL_MULTIPLIER);
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

        name = "Client";
        try {
            name += "@" + InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            // ignore
        }
    }

    public void start() {

        final int delayMicro = (int) (1_000_000 / rps);

        System.out.printf("Started stress client `%s` to `%s` with %,d rps (%,d micros between requests)%n", name, addr, rps, delayMicro);

        requestExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (!requestExecutor.isShutdown()) {
                    sendOne();
                }
            }
        }, 0, delayMicro, MICROSECONDS);

        /*hwTimer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                hwTimer.newTimeout(this, delayMicro, MICROSECONDS);

                if (!timeout.isCancelled()) {
                    sendOne();
                }
            }
        }, delayMicro, MICROSECONDS);*/

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
        System.out.printf("STAT: sent=%5s, received=%5s, connected=%5s | ERRORS: timeouts=%5s, binds=%5s, connects=%5s, io=%5s, nn=%s%n",
                sentSoFar,
                received.getAndSet(0),
                connected.getAndSet(0),
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

        bootstrap.connect(addr);
    }

    public void stop() {
        System.out.printf("client `%s` stopping...%n", name);
        hwTimer.stop();
        requestExecutor.shutdownNow();
        statExecutor.shutdownNow();
        bootstrap.shutdown();
    }

    public static void main(String[] args) {
        final String host = args.length > 0 ? args[0] : "localhost";
        final int port = args.length > 1 ? parseInt(args[1]) : 8080;
        final int rps = args.length > 2 ? parseInt(args[2]) : 1_000;

        final StressClient client = new StressClient(host, port, new StubHttpRequest(), rps, 3000, 3000, false, false);
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
            if (print) {
                System.out.printf("response: %s%n", resp.toString(Charset.defaultCharset()));
            }
            received.incrementAndGet();
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            e.getChannel().write(requestSource.next());
            sent.incrementAndGet();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            e.getChannel().close();

            Throwable exc = e.getCause();

            if (exc instanceof ConnectTimeoutException ||
                    exc instanceof ReadTimeoutException || exc instanceof WriteTimeoutException) {
                te.incrementAndGet();
            } else if (exc instanceof BindException) {
                if (be.incrementAndGet() % 50 == 0) {
                    System.err.printf("ERROR: not enough ports! You should decrease rps(=%,d now)%n", rps);
                }

            } else if (exc instanceof ConnectException) {
                ce.incrementAndGet();
            } else if (exc instanceof IOException) {
                ie.incrementAndGet();
            } else {
                nn.incrementAndGet();
            }

            if (debug) {
                exc.printStackTrace(System.err);
            }
        }
    }
}
