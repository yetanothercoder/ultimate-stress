package ru.yetanothercoder.stress;


import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutException;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultThreadFactory;
import ru.yetanothercoder.stress.cli.CliParser;
import ru.yetanothercoder.stress.config.StressConfig;
import ru.yetanothercoder.stress.server.CountingServer;
import ru.yetanothercoder.stress.stat.CountersHolder;
import ru.yetanothercoder.stress.stat.Metric;
import ru.yetanothercoder.stress.timer.Scheduler;
import ru.yetanothercoder.stress.utils.Utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static ru.yetanothercoder.stress.utils.Utils.formatLatency;

/**
 * Stress Client based on Netty
 * Schedulers requests, count stats, check for error and output all info to STDIN/STDERR
 * <p/>
 * TODO: 1. usage legend 2. duration in any units 3. content-length support
 *
 * @author Mikhail Baturov, http://www.yetanothercoder.ru/search/label/stress
 */
public class StressClient {

    public static final int MILLION = 1_000_000;
    public static final int NUM_OF_CORES = Runtime.getRuntime().availableProcessors();
    public static final int HTTP_STATUS_WIDTH = 20;
    static final AttributeKey<Long> TS_ATTR = AttributeKey.valueOf("TS");
    static final AttributeKey<Integer> STATUS_ATTR = AttributeKey.valueOf("status");
    static final AttributeKey<Boolean> READ_ATTR = AttributeKey.valueOf("read");

    private final SocketAddress addr;
    private final Bootstrap bootstrap;

    private final CountersHolder ch = new CountersHolder();

    private final AtomicInteger dynamicRate = new AtomicInteger(1); // maximum rps ~1M (starting point)

    private String name;
    private final ScheduledExecutorService statExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService requestExecutor = Executors.newFixedThreadPool(NUM_OF_CORES);
    private final Scheduler scheduler;

    private final StressClientHandler stressClientHandler = new StressClientHandler();

    private volatile boolean pause = false, stopped = false;

    private volatile long started;

    private final StressConfig c;


    private final Metric responseSummary = new Metric("Summary Response");
    private final Metric successResp = new Metric("Success Responses");
    private final Metric errorResp = new Metric("Error Responses");
    private final Metric rpsStat = new Metric("Req/s");
    private final Metric connStat = new Metric("Conn/s");

    Metric.MetricResults respStats;

    public static void main(String[] args) throws Exception {
        try {
            StressConfig config = CliParser.parseAndValidate(args);

            if (config.server > 0) {
                new CountingServer(config.server, config.serverRandomDelayMs, config.debug).start();
            } else {
                new StressClient(config).start();
            }
        } catch (Exception e) {
            System.err.printf("wrong params: `%s`%n", e);

            System.out.printf(
                    "Usage*: java -jar ultimate-stress-x.x.jar [-t <N> -s <N> -rt <N> -wt=<N> -sh=<1,2,3> -Dprint=<0,1> -debug=<any> -sample <N> -Dtfactor=1.2 -Dtfactor0=1.1] <url> [<rps>]%n" +
                            "-t duration in seconds%n" +
                            "-s server option%n%n" +

                            "*See actual CLI format and docs at https://github.com/yetanothercoder/ultimate-stress/wiki/CLI"
            );
            System.exit(1);
        }
    }

    public StressClient(StressConfig config) {
        this.c = config;

        this.addr = new InetSocketAddress(c.getHost(), c.getPort());

        if (c.initRps > MILLION) throw new IllegalArgumentException("rps<=1M!");

        if (c.initRps > 0) {
            dynamicRate.set(MILLION / c.initRps);
        }

        scheduler = c.type.createScheduler();

        bootstrap = initNetty(c.readTimeoutMs, c.writeTimeoutMs, 100, stressClientHandler);

        name = generateName();
    }

    private String generateName() {
        String name = "Client";
        try {
            name += "@" + InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            // ignore
        }
        return name;
    }

    public static Bootstrap initNetty(final int readTimeoutMs, final int writeTimeoutMs, final int connTimeoutMs, final ChannelHandler clientHandler) {
        NioEventLoopGroup workers = new NioEventLoopGroup(NUM_OF_CORES, new DefaultThreadFactory("NettyWorker"));

        Bootstrap bootstrap = new Bootstrap()
                .group(workers)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connTimeoutMs)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        if (readTimeoutMs > 0) {
                            ch.pipeline().addLast("readTimer",
                                    new ReadTimeoutHandler(readTimeoutMs, MILLISECONDS));
                        }
                        if (writeTimeoutMs > 0) {
                            ch.pipeline().addLast("writeTimer",
                                    new WriteTimeoutHandler(writeTimeoutMs, MILLISECONDS));
                        }
                        ch.pipeline().addLast("stress", clientHandler);
                    }
                });

        return bootstrap;
    }

    public void start() throws InterruptedException {

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                if (!pause && e instanceof InternalError && e.getCause() instanceof FileNotFoundException) {
                    processLimitErrors();
                }
            }
        });

        if (c.sample > 0) {
            sampleRequests(Math.max(c.sample, MILLION));
        }

        int initRps = MILLION / dynamicRate.get();
        System.out.printf("Starting stress `%s` to `%s` with %,d rps (rate=%,d micros), full config:%n%s%n",
                name, addr, initRps, dynamicRate.get(), c);

        if (!checkConnection()) {
            System.err.printf("ERROR: no connection to %s:%,d%n", c.getHost(), c.getPort());
            System.exit(0);
        }

        started = System.currentTimeMillis();
        scheduler.startAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (!pause) sendOne();
            }
        }, dynamicRate);

        statExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                printPeriodicStats();

            }
        }, 0, 1, SECONDS);

        if (c.durationSec > 0) {
            statExecutor.schedule(new Runnable() {
                @Override
                public void run() {
                    System.out.printf("duration %s seconds elapsed, exiting...%n", c.durationSec);
                    StressClient.this.stop(true);
                    System.exit(0);
                }
            }, c.durationSec, SECONDS);
        }

        enableStoppingOnShutdown();
    }

    private void enableStoppingOnShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                StressClient.this.stop(true);
            }
        });
    }

    private boolean checkConnection() {
        // java 6 style to handle such errors >>
        try (Socket socket = new Socket(c.getHost(), c.getPort())) {
            return socket.isConnected();
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    private void printPeriodicStats() {
        int conn = ch.connected.get();
        if (dynamicRate.get() > 1) {
            connStat.register(conn);
            ch.connected.set(0);
        }
        final int sentSoFar = ch.sent.getAndSet(0);
        rpsStat.register(sentSoFar);

        if (c.quiet) {
            System.out.print(".");
        } else {
            System.out.printf("STAT: sent=%,6d, received=%,6d, connected=%,6d, rate=%,4d | ERRORS: timeouts=%,5d, binds=%,5d, connects=%,5d, io=%,5d, oe=%,d%n",
                    sentSoFar,
                    ch.received.getAndSet(0),
                    conn, dynamicRate.get(),
                    ch.te.getAndSet(0),
                    ch.be.getAndSet(0),
                    ch.ce.getAndSet(0),
                    ch.ie.getAndSet(0),
                    ch.oe.getAndSet(0)
            );
        }
    }

    private void sendOne() {
        try {
            ChannelFuture future = bootstrap.connect(addr);
            ch.connected.incrementAndGet();
        } catch (ChannelException e) {
            if (e.getCause() instanceof SocketException) {
                processLimitErrors();
            } else {
                ch.oe.incrementAndGet();
            }
            if (c.debug) {
                e.printStackTrace(System.err);
            }
        }

    }

    public void stop(boolean showSummaryStat) {
        if (stopped) return;

        System.out.printf("client `%s` stopping...%n", name);

        requestExecutor.shutdownNow();
        scheduler.shutdown();
        statExecutor.shutdown();
        bootstrap.group().shutdownGracefully();

        stopped = true;

        if (showSummaryStat) {
            printSummaryStat();
        }
    }

    private void printSummaryStat() {
        long totalMs = System.currentTimeMillis() - started;
        long totalDurationSec = MILLISECONDS.toSeconds(totalMs);

        double receivedMb = ch.receivedBytes.get() / 1e6;
        double sentMb = ch.sentBytes.get() / 1e6;

        respStats = responseSummary.calculateAndReset();
        Metric.MetricResults connStats = connStat.calculateAndReset();
        Metric.MetricResults rpsStats = rpsStat.calculateAndReset();
        long totalRps = respStats.size / totalDurationSec;


        System.out.printf(
                "%nFinished stress @ %s for %s%n" +
                        "  Used %d-%d threads and ~%d connection per sec%n" +
                        "     STATS         AVG       STDEV         MAX %n" +
                        "    Latency  %9s %11s %11s%n" +
                        "    Req/Sec %9s %11s %11s%n" +
                        "  Overall Latency Distribution%n" +
                        "     50%% %10s%n" +
                        "     75%% %10s%n" +
                        "     90%% %10s%n" +
                        "     99%% %10s%n",

                c.url, formatLatency(totalMs),
                NUM_OF_CORES, NUM_OF_CORES * 2, connStats.p50,
                formatLatency(respStats.av), formatLatency(respStats.std), formatLatency(respStats.max),
                rpsStats.av, rpsStats.std, rpsStats.max,
                formatLatency(respStats.p50),
                formatLatency(respStats.p75),
                formatLatency(respStats.p90),
                formatLatency(respStats.p99)
        );

        if (c.httpStatuses && respStats.size > 0) {
            Metric.MetricResults successStats = successResp.calculateAndReset();
            Metric.MetricResults errorStats = errorResp.calculateAndReset();
            int successes = (int) (successStats.size * 1.0 / respStats.size * 100);
            int errors = (int) (errorStats.size * 1.0 / respStats.size * 100);
            System.out.printf(
                    "  %d%% Success(2xx-3xx) Responses:%n" +
                            "     50%% %10s%n" +
                            "     75%% %10s%n" +
                            "     90%% %10s%n" +
                            "     99%% %10s%n" +
                            "  %d%% Error(4xx-5xx) Responses:%n" +
                            "     50%% %10s%n" +
                            "     75%% %10s%n" +
                            "     90%% %10s%n" +
                            "     99%% %10s%n",

                    successes,
                    formatLatency(successStats.p50),
                    formatLatency(successStats.p75),
                    formatLatency(successStats.p90),
                    formatLatency(successStats.p99),

                    errors,
                    formatLatency(errorStats.p50),
                    formatLatency(errorStats.p75),
                    formatLatency(errorStats.p90),
                    formatLatency(errorStats.p99)
            );

            if (c.debug) System.err.printf("%nDEBUG, Metrics: %s, %s%n", successStats, errorStats);
        }

        System.out.printf("%nSUMMARY: %,d requests sent (%,d received) in %s, sent %,.2f MB, received %,.2f MB, RPS~%s%n",
                ch.total.get(), respStats.size, formatLatency(totalMs), sentMb, receivedMb, totalRps
        );

        if (c.debug) System.err.printf("%nDEBUG, Metrics: %s, %s, %s%n", respStats, rpsStats, connStats);
    }

    public int getSentTotal() {
        return ch.total.get();
    }

    private void processLimitErrors() {
        if (pause || (ch.be.get() + ch.ce.get() < 10)) return;

        pause = true;

        int oldRate = dynamicRate.get();
        int newRate = dynamicRate.get() > 1 ? tuneRate() : calculateInitRate();

        int newRps = (MILLION / newRate);
        int oldRps = (MILLION / oldRate);

        System.err.printf("ERROR: reached connection limit! Decreasing rps: %,d->%,d (rate: %,d->%,d micros)%n",
                oldRps, newRps, oldRate, newRate);

        dynamicRate.set(newRate);

        statExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                pause = false;
            }
        }, 3, SECONDS);
    }

    private int tuneRate() {
        return (int) Math.ceil(c.tuningFactor * dynamicRate.get());
    }

    private int calculateInitRate() {
        int conn = ch.connected.get();
        return (int) c.initialTuningFactor * MILLION / conn;
    }

    private void sampleRequests(final int sampleSize) {
        System.out.print("request sampling: ");
        long total = 0;
        ByteBuf[] sampleAgainstJitOpt = new ByteBuf[1000];
        Arrays.fill(sampleAgainstJitOpt, Unpooled.copiedBuffer("empty".getBytes()));

        int tenPercent = sampleSize / 10;
        for (int i = 0, p = 0; i < sampleSize; i++) {
            if (i % tenPercent == 0) System.out.printf(" %,d%%", ++p * 10);

            long t0 = System.nanoTime();
            ByteBuf request = c.requestGenerator.next();
            total += System.nanoTime() - t0;

            sampleAgainstJitOpt[new Random().nextInt(1000)] = request;
        }
        long requestNs = total / sampleSize;
        System.out.printf("%n%nrequest preparation time: %,d ns (av on %,d runs), so MAX rps=%,d%n", requestNs, sampleSize, 1_000_000_000 / requestNs);

        String randomRequest = new String(sampleAgainstJitOpt[new Random().nextInt(1000)].array());
        System.out.printf("random sample: %n%s%n%n", randomRequest);
    }


    @ChannelHandler.Sharable
    private class StressClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws Exception {
            if (pause) {
                ctx.close();
                return;
            }

            requestExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    ByteBuf req = c.requestGenerator.next();
                    ctx.attr(TS_ATTR).set(System.currentTimeMillis());
                    ctx.writeAndFlush(req);

                    ch.sentBytes.addAndGet(req.capacity());
                    ch.total.incrementAndGet();
                }
            });

            ch.sent.incrementAndGet();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf resp) throws Exception {
            if (pause) {
                ctx.channel().close();
                return;
            }

            final int status = getStatus(resp);
            if (status > 0) { // beginning of response
                ctx.attr(STATUS_ATTR).set(status);

                ch.received.incrementAndGet();
            }

            ch.receivedBytes.addAndGet(resp.capacity());
            if (c.print) {
                System.out.printf("response: %s%n", resp.toString(Charset.defaultCharset()));
            }


        }

        private void countStatuses(int status, long latency) {
            if (status >= 200 && status < 400) {
                successResp.register(latency);
            } else if (status >= 400) {
                errorResp.register(latency);
            }
        }

        private int getStatus(ByteBuf resp) {
            String statusLine = resp.toString(0, HTTP_STATUS_WIDTH, Charset.defaultCharset());
            return Utils.parseStatus(statusLine);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            // work-around against netty bug
            // http://stackoverflow.com/questions/25972426/netty-4-channelinboundhandleradapter-channelreadcomplete-called-twice
            if (ctx.attr(READ_ATTR).get() != null) return;

            Long start = ctx.attr(TS_ATTR).get();
            if (start != null) {
                final long latency = System.currentTimeMillis() - start;
                responseSummary.register(latency);

                Integer status = ctx.attr(STATUS_ATTR).get();
                if (status != null && c.httpStatuses) {
                    countStatuses(status, latency);
                }
            }
            ctx.attr(READ_ATTR).set(true);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable exc) throws Exception {
            ctx.close();

            if (exc instanceof ConnectTimeoutException ||
                    exc instanceof ReadTimeoutException || exc instanceof WriteTimeoutException) {
                ch.te.incrementAndGet();
            } else if (exc instanceof BindException) {
                ch.be.incrementAndGet();
                processLimitErrors();
            } else if (exc instanceof ConnectException) {
                ch.ce.incrementAndGet();
                processLimitErrors();
            } else if (exc instanceof IOException) {
                ch.ie.incrementAndGet();
            } else {
                ch.oe.incrementAndGet();
            }

            if (c.debug) {
                exc.printStackTrace(System.err);
            }
        }

    }
}
