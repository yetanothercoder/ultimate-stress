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
import ru.yetanothercoder.tests.stress.timer.HashedWheelScheduler;
import ru.yetanothercoder.tests.stress.timer.Scheduler;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * TODO: 1. check connectivity before start 2. count connection refused exceptions
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
    private final ExecutorService requestExecutor = Executors.newCachedThreadPool();
    private final Scheduler scheduler;
    private final AtomicInteger dynamicRate = new AtomicInteger(1);

    private final StressClientHandler stressClientHandler = new StressClientHandler();
    private final boolean print;
    private final boolean debug;
    private final AtomicInteger mode = new AtomicInteger(0);

    /**
     * in practice, real rps was this times lower, seems due to jvm overhead
     */
    private static final double RPS_IMPERICAL_MULTIPLIER = 2;

    public StressClient(String host, int port, RequestSource requestSource, int rps) {
        this(host, port, requestSource, rps, -1, -1, false, false);
    }

    public StressClient(String host, int port, RequestSource requestSource, int rps,
                        final int readTimeoutMs, final int writeTimeoutMs, boolean print, boolean debug) {

        if (rps > 1000000) throw new IllegalArgumentException("rps<=1M!");

        this.scheduler = new HashedWheelScheduler();
        this.hwTimer = new HashedWheelTimer();

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
                            new ReadTimeoutHandler(hwTimer, readTimeoutMs, MILLISECONDS));
                }
                if (writeTimeoutMs > 0) {
                    pipeline.addLast("writeTimer",
                            new WriteTimeoutHandler(hwTimer, writeTimeoutMs, MILLISECONDS));
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

        if (rps > 0) {
            dynamicRate.set((int) (1000000 / rps / RPS_IMPERICAL_MULTIPLIER));
        }
        System.out.printf("Started stress client `%s` to `%s` with %d rps (rate=%,d micros)%n", name, addr, rps, dynamicRate.get());

        scheduler.startAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (mode.get() == 0) sendOne();
            }
        }, dynamicRate);

        statExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                showStats();

            }
        }, 0, 1, SECONDS);
    }

    private void showStats() {
        int conn = connected.get();
        if (dynamicRate.get() > 1 || rps > 0) {
            /*if (be.get() > 50) {
                dynamicRate.set((int) (dynamicRate.get() * 1.2));
            }*/
            connected.set(0);
        }
        int sentSoFar = sent.getAndSet(0);
        total.addAndGet(sentSoFar);
        System.out.printf("STAT: sent=%5s, received=%5s, connected=%5s, rate=%3s | ERRORS: timeouts=%5s, binds=%5s, connects=%5s, io=%5s, nn=%s%n",
                sentSoFar,
                received.getAndSet(0),
                conn, dynamicRate.get(),
                te.getAndSet(0),
                be.getAndSet(0),
                ce.getAndSet(0),
                ie.getAndSet(0),
                nn.getAndSet(0)
        );
    }

    private void sendOne() {
        // counting connections beforehand!
//        connected.incrementAndGet();

        try {
            ChannelFuture future = bootstrap.connect(addr);
            connected.incrementAndGet();
        } catch (ChannelException e) {
            if (e.getCause() instanceof SocketException) {
                countPortErrorsOrExit(e.getCause());
            } else {
                nn.incrementAndGet();
            }
            if (debug) {
                e.printStackTrace(System.err);
            }
        }

    }

    public void stop() {
        System.out.printf("client `%s` stopping...%n", name);
        requestExecutor.shutdownNow();
        scheduler.shutdown();
        hwTimer.stop();
        statExecutor.shutdownNow();
        bootstrap.shutdown();
    }

    public static void main(String[] args) throws Exception {
        final String host = System.getProperty("host", "localhost");
        final int port = Integer.valueOf(System.getProperty("port", "8080"));
        final int rps = Integer.valueOf(System.getProperty("rps", "-1"));
        final boolean server = System.getProperty("server") != null;
        final boolean debug = System.getProperty("debug") != null;

        if (server) {
            new CountingServer(port, 100, MILLISECONDS).start();
            TimeUnit.SECONDS.sleep(2);
        }

        final StressClient client = new StressClient(host, port, new StubHttpRequest(), rps, 3000, 3000, false, debug);
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
//            connected.decrementAndGet();
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
            requestExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    e.getChannel().write(requestSource.next());
                }
            });

            sent.incrementAndGet();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            e.getChannel().close();
            //connected.decrementAndGet();

            Throwable exc = e.getCause();

            if (exc instanceof ConnectTimeoutException ||
                    exc instanceof ReadTimeoutException || exc instanceof WriteTimeoutException) {
                te.incrementAndGet();
            } else if (exc instanceof BindException) {
                countPortErrorsOrExit(exc);
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

    private void countPortErrorsOrExit(Throwable e) {
//        if (be.incrementAndGet() > 0) {
        mode.set(1);
        int conn = connected.get();
//            final int connectedNow = connected.get();

//            int max = Math.max(Math.max(connected.get(), received.get()), sent.get());

        System.err.printf("ERROR: not enough ports! You should decrease rps(=%d now), max: %,d%n", rps, conn);
        //e.printStackTrace(System.err);


        int newRate;
        if (dynamicRate.get() > 1 || rps > 0) {
            // just tune
            newRate = (int) (1.1 * dynamicRate.get());
            newRate = Math.max(newRate, dynamicRate.get() + 1);
        } else {
            // set initial value
            newRate = (int) (1000000 / conn);
        }

        dynamicRate.set(newRate);
        int newRps = (int) ((1000000 / newRate) / RPS_IMPERICAL_MULTIPLIER);
        System.out.printf("new rps: %d%n", newRps);


        statExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                mode.set(0);
            }
        }, 3, SECONDS);
//            System.exit(1);
//        }
    }
}
