package ru.yetanothercoder.stress;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.handler.timeout.WriteTimeoutException;
import org.jboss.netty.handler.timeout.WriteTimeoutHandler;
import org.jboss.netty.util.HashedWheelTimer;
import ru.yetanothercoder.stress.requests.HttpFileTemplateSource;
import ru.yetanothercoder.stress.requests.RequestSource;
import ru.yetanothercoder.stress.server.CountingServer;
import ru.yetanothercoder.stress.stat.Metric;
import ru.yetanothercoder.stress.timer.ExecutorScheduler;
import ru.yetanothercoder.stress.timer.HashedWheelScheduler;
import ru.yetanothercoder.stress.timer.PlainScheduler;
import ru.yetanothercoder.stress.timer.Scheduler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Integer.valueOf;
import static java.lang.System.getProperty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Stress Client based on Netty
 * Schedulers requests, count stats, check for error and output all info to STDIN/STDERR
 *
 * @author Mikhail Baturov, http://www.yetanothercoder.ru/search/label/stress
 */
public class StressClient {

    public static final int MILLION = 1000000;

    private final RequestSource requestSource;
    private final SocketAddress addr;
    private final ClientBootstrap bootstrap;
    private final AtomicInteger connected = new AtomicInteger(0);
    private final AtomicInteger sent = new AtomicInteger(0);
    private final AtomicInteger received = new AtomicInteger(0);
    private final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger receivedBytes = new AtomicInteger(0), sentBytes = new AtomicInteger(0);
    private final AtomicInteger te = new AtomicInteger(0);
    private final AtomicInteger be = new AtomicInteger(0);
    private final AtomicInteger ce = new AtomicInteger(0);
    private final AtomicInteger ie = new AtomicInteger(0);
    private final AtomicInteger dynamicRate = new AtomicInteger(1);

    private final AtomicInteger nn = new AtomicInteger(0);
    private final double tuningFactor;
    private final double initialTuningFactor;

    private String name;
    private final HashedWheelTimer hwTimer;
    private final ScheduledExecutorService statExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService requestExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final Scheduler scheduler;

    private final StressClientHandler stressClientHandler = new StressClientHandler();
    private final boolean print;
    private final boolean debug;
    private volatile boolean pause = false, stopped = false;

    private final String host;
    private final int port;
    private final int durationSec;
    private volatile long started;

    private final Map<String, String> config = new LinkedHashMap<>();
    private CountingServer server = null;
    private final int sample;
    private final int exec;
    private final int initRps;

    private final Metric responseSummary = new Metric("Summary Response");
    private final Metric rpsStat = new Metric("RPS");

    public static void main(String[] args) throws Exception {
        final String host = args.length > 0 ? args[0] : "localhost";
        final int port = args.length > 1 ? Integer.valueOf(args[1]) : 8080;
        final int rps = args.length > 2 ? valueOf(args[2]) : -1;

        Map<String, String> r = new HashMap<>();
        r.put("$browser", "Mozilla/5.0");
        r.put("$v", "11.0");

        HttpFileTemplateSource reqSrc = new HttpFileTemplateSource(".", "http", host + ":" + port, r);
        final StressClient client = new StressClient(host, port, rps, reqSrc);
        client.start();
    }

    /**
     * Init client<br/>
     * If rps param < 0, then the client tries to find a maximum rps available on current machine this time
     *
     * @param host          target host
     * @param port          target port
     * @param rps           number of request per second, if <0 -
     * @param requestSource request source iterator
     */
    public StressClient(String host, int port, int rps, RequestSource requestSource) {

        // params >>
        this.host = host;
        this.port = port;
        initRps = rps;
        durationSec = valueOf(registerParam("seconds", "-1"));

        final int readTimeoutMs = valueOf(registerParam("read.ms", "1000"));
        final int writeTimeoutMs = valueOf(registerParam("write.ms", "1000"));

        exec = valueOf(registerParam("exec", "1"));
        sample = valueOf(registerParam("sample", "-1"));
        print = "1".equals(registerParam("print", "0"));
        debug = "1".equals(registerParam("debug", "0"));

        if ("1".equals(registerParam("server", "0"))) {
            server = new CountingServer(this.port, 100, MILLISECONDS, debug);
        }

        tuningFactor = Double.valueOf(registerParam("tfactor", "1.1"));
        initialTuningFactor = Double.valueOf(registerParam("tfactor0", "1.2"));
        // << params

        this.requestSource = requestSource;
        this.hwTimer = new HashedWheelTimer();
        this.addr = new InetSocketAddress(this.host, this.port);

        if (initRps > MILLION) throw new IllegalArgumentException("rps<=1M!");

        if (initRps > 0) {
            dynamicRate.set(MILLION / initRps);
        }

        scheduler = initMainExecutor();

        bootstrap = initNetty(readTimeoutMs, writeTimeoutMs);

        name = setName();
    }

    private String setName() {
        String name = "Client";
        try {
            name += "@" + InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            // ignore
        }
        return name;
    }

    private ClientBootstrap initNetty(final int readTimeoutMs, final int writeTimeoutMs) {
        ClientBootstrap bootstrap = new ClientBootstrap(
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
        return bootstrap;
    }

    private Scheduler initMainExecutor() {
        if (exec == 2) {
            return new ExecutorScheduler();
        } else if (exec == 3) {
            return new PlainScheduler();
        } else {
            return new HashedWheelScheduler();
        }
    }

    private String registerParam(String name, String def) {
        String value = getProperty(name, def);
        config.put(name, value);
        return value;
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

        if (sample > 0) {
            sampleRequests(Math.max(sample, MILLION));
        }

        int initRps = MILLION / dynamicRate.get();
        System.out.printf("Starting stress `%s` to `%s` with %,d rps (rate=%,d micros), full config: %s%n",
                name, addr, initRps, dynamicRate.get(), config);

        if (server != null) {
            server.start();
            TimeUnit.SECONDS.sleep(2);
        }


        if (!checkConnection()) {
            System.err.printf("ERROR: no connection to %s:%,d%n", host, port);
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

        if (durationSec > 0) {
            statExecutor.schedule(new Runnable() {
                @Override
                public void run() {
                    System.out.printf("duration %s seconds elapsed, exiting...%n", durationSec);
                    StressClient.this.stop(true);
                    System.exit(0);
                }
            }, durationSec, SECONDS);
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
        try (Socket socket = new Socket(host, port)) {
            return socket.isConnected();
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    private void printPeriodicStats() {
        int conn = connected.get();
        if (dynamicRate.get() > 1) {
            connected.set(0);
        }
        final int sentSoFar = sent.getAndSet(0);
        total.addAndGet(sentSoFar);
        rpsStat.register(sentSoFar);

        System.out.printf("STAT: sent=%,6d, received=%,6d, connected=%,6d, rate=%,4d | ERRORS: timeouts=%,5d, binds=%,5d, connects=%,5d, io=%,5d, nn=%,d%n",
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
        try {
            ChannelFuture future = bootstrap.connect(addr);
            connected.incrementAndGet();
        } catch (ChannelException e) {
            if (e.getCause() instanceof SocketException) {
                processLimitErrors();
            } else {
                nn.incrementAndGet();
            }
            if (debug) {
                e.printStackTrace(System.err);
            }
        }

    }

    public void stop(boolean showSummaryStat) {
        if (stopped) return;

        System.out.printf("client `%s` stopping...%n", name);

        requestExecutor.shutdownNow();
        scheduler.shutdown();
        hwTimer.stop();
        statExecutor.shutdown();
        if (server != null) server.stop();
        bootstrap.shutdown();

        stopped = true;

        if (showSummaryStat) {
            printSummaryStat();
        }
    }

    private void printSummaryStat() {
        long totalDurationSec = MILLISECONDS.toSeconds(System.currentTimeMillis() - started);

        double receivedMb = receivedBytes.get() / 1e6;
        double sentMb = sentBytes.get() / 1e6;

        System.out.printf("STAT: %,d requests in %,d sec, sent %,.2f MB, received %,.2f MB, RPS=%s%n", total.get(), totalDurationSec, sentMb, receivedMb, total.get() / totalDurationSec);
        System.out.printf("STAT: %s, %s%n", responseSummary.calculateAndReset(), rpsStat.calculateAndReset());
    }

    public int getSentTotal() {
        return total.get();
    }

    private void processLimitErrors() {
        if (pause || (be.get() + ce.get() < 10)) return;

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
        return (int) Math.ceil(tuningFactor * dynamicRate.get());
    }

    private int calculateInitRate() {
        int conn = connected.get();
        return (int) initialTuningFactor * MILLION / conn;
    }

    private void sampleRequests(final int sampleSize) {
        System.out.print("request sampling: ");
        long total = 0;

        ChannelBuffer[] sampleAgainstJitOpt = new ChannelBuffer[1000];
        Arrays.fill(sampleAgainstJitOpt, ChannelBuffers.copiedBuffer("empty".getBytes()));

        int tenPercent = sampleSize / 10;
        for (int i = 0, p = 0; i < sampleSize; i++) {
            if (i % tenPercent == 0) System.out.printf(" %,d%%", ++p * 10);

            long t0 = System.nanoTime();
            ChannelBuffer request = requestSource.next();
            total += System.nanoTime() - t0;

            sampleAgainstJitOpt[new Random().nextInt(1000)] = request;
        }
        long requestNs = total / sampleSize;
        System.out.printf("%n%nrequest preparation time: %,d ns (av on %,d runs), so MAX rps=%,d%n", requestNs, sampleSize, 1_000_000_000 / requestNs);

        String randomRequest = new String(sampleAgainstJitOpt[new Random().nextInt(1000)].array());
        System.out.printf("random sample: %n%s%n%n", randomRequest);
    }


    private class StressClientHandler extends SimpleChannelUpstreamHandler {

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            if (pause) {
                e.getChannel().close();
                return;
            }

            Long start = (Long) ctx.getAttachment();
            if (start != null) {
                responseSummary.register(System.currentTimeMillis() - start);
            }

            ChannelBuffer resp = (ChannelBuffer) e.getMessage();
            receivedBytes.addAndGet(resp.capacity());
            if (print) {
                System.out.printf("response: %s%n", resp.toString(Charset.defaultCharset()));
            }
            received.incrementAndGet();
        }

        @Override
        public void channelConnected(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
            if (pause) {
                e.getChannel().close();
                return;
            }
            requestExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    ChannelBuffer req = requestSource.next();
                    ctx.setAttachment(System.currentTimeMillis());
                    e.getChannel().write(req);

                    sentBytes.addAndGet(req.capacity());
                }
            });

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
                be.incrementAndGet();
                processLimitErrors();
            } else if (exc instanceof ConnectException) {
                ce.incrementAndGet();
                processLimitErrors();
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
