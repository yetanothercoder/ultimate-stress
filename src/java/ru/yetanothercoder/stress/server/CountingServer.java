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
import ru.yetanothercoder.stress.stat.CountersHolder;
import ru.yetanothercoder.stress.stat.Metric;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static ru.yetanothercoder.stress.StressClient.THREADS;
import static ru.yetanothercoder.stress.utils.Utils.formatLatency;

/**
 * @author Mikhail Baturov, 4/22/13 12:02 PM
 */
public class CountingServer {

    public final CountersHolder ch = new CountersHolder();

    public static final ChannelBuffer RESP204 = ChannelBuffers.copiedBuffer(String.format(
                    "HTTP/1.1 204 No Content%n" +
                            "Server: github.com/yetanothercoder/ultimate-stress%n" +
                            "Content-Length: 0%n%n"),
            Charset.defaultCharset());

    private final int port;
    private final boolean debug;

    private final Timer hwTimer = new HashedWheelTimer(10, MILLISECONDS);
    private final int randomDelay;
    private final TimeUnit delayUnit = TimeUnit.MILLISECONDS;
    private final Random r = new Random();
    private ServerBootstrap bootstrap;

    private volatile boolean stopped = false;
    private volatile long started;

    private final Metric rpsStat = new Metric("Req/s");
    private final Metric ownLatency = new Metric("Own Response Latency");

    public CountingServer(int port) {
        this(port, -1, false);
    }

    public CountingServer(int port, int randomDelay, boolean debug) {
        this.randomDelay = randomDelay;
        this.port = port;
        this.debug = debug;
    }

    public void start() {
        System.out.printf("SERVER: started counting server on %s port", port);
        if (randomDelay > 0) System.out.printf(" with %,d ms random delay", randomDelay);
        System.out.println();

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

        started = System.currentTimeMillis();

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                final int perSecond = ch.received.getAndSet(0);

                if (perSecond > 0) {
                    ch.total.addAndGet(perSecond);
                    rpsStat.register(perSecond);
                    System.out.printf("SERVER: received %,d rps, errors: %,d%n", perSecond, ch.oe.getAndSet(0));
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

        printSummaryStat();
    }


    private void printSummaryStat() {
        long totalMs = System.currentTimeMillis() - started;
        long totalDurationSec = MILLISECONDS.toSeconds(totalMs);

        double receivedMb = ch.receivedBytes.get() / 1e6;

        Metric.MetricResults rpsStats = rpsStat.calculateAndReset();
        Metric.MetricResults respStats = ownLatency.calculateAndReset();
        long totalRps = ch.total.get() / totalDurationSec;


        System.out.printf("%n" +
                        "SERVER: Stats so far:%n" +
                        "SERVER:  Used %d-%d threads for processing requests%n" +
                        "SERVER:     STATS         AVG       STDEV         MAX %n" +
                        "SERVER:    Req/Sec %9s %11s %11s%n" +
                        "SERVER:    OWN Latency %6s %11s %11s%n" +
                        "SERVER:  Overall Latency Distribution%n" +
                        "SERVER:     50%% %10s%n" +
                        "SERVER:     75%% %10s%n" +
                        "SERVER:     90%% %10s%n" +
                        "SERVER:     99%% %10s%n",

                THREADS, THREADS * 2,
                rpsStats.av, rpsStats.std, rpsStats.max,
                formatLatency(respStats.av), formatLatency(respStats.std), formatLatency(respStats.max),
                formatLatency(respStats.p50),
                formatLatency(respStats.p75),
                formatLatency(respStats.p90),
                formatLatency(respStats.p99)
        );

        System.out.printf("%nSERVER.SUMMARY: received %,d requests in %s, total size %,.2f MB and RPS~%s, errors: %s%n",
                ch.total.get(), formatLatency(totalMs), receivedMb, totalRps, ch.errors.get()
        );

        if (debug) System.err.printf("%nSERVER.DEBUG, Metrics: %s, %s%n", respStats, rpsStats);
    }

    private class CountingHandler extends SimpleChannelUpstreamHandler {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
            final long start = System.currentTimeMillis();
            ch.received.incrementAndGet();

            ChannelBuffer message = (ChannelBuffer) e.getMessage();

            ch.receivedBytes.addAndGet(message.capacity());

            if (debug) {
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
                            ownLatency.register(System.currentTimeMillis() - start);
                        }
                    }
                }, delay, delayUnit);
            } else {
                writeAnswer(channel);
                ownLatency.register(System.currentTimeMillis() - start);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            ch.oe.incrementAndGet();
            ch.errors.incrementAndGet();

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

        final CountingServer server = new CountingServer(port, delay, debug);
        server.start();
    }
}
