package ru.yetanothercoder.stress;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.handler.timeout.WriteTimeoutException;
import org.jboss.netty.handler.timeout.WriteTimeoutHandler;
import org.jboss.netty.util.HashedWheelTimer;
import ru.yetanothercoder.stress.requests.RequestSource;
import ru.yetanothercoder.stress.timer.ExecutorScheduler;
import ru.yetanothercoder.stress.timer.HashedWheelScheduler;
import ru.yetanothercoder.stress.timer.PlainScheduler;
import ru.yetanothercoder.stress.timer.Scheduler;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * TODO: 1. src directory 2. unit test 3. http files
 *
 * TCP/IP Stress Client based on Netty
 *
 * @author Mikhail Baturov, http://www.yetanothercoder.ru/search/label/en
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
    private volatile boolean pause = false;

    private final String host;
    private final int port;
    private final Map<String, String> config = new LinkedHashMap<String, String>();
    private CountingServer server = null;

    public StressClient(String host, String port, String rps, RequestSource requestSource) {

        // params >>
        this.host = registerParam("host", host);
        this.port = valueOf(registerParam("port", port));
        int initRps = valueOf(registerParam("rps", rps));

        final int readTimeoutMs = valueOf(registerParam("read.ms", "3000"));
        final int writeTimeoutMs = valueOf(registerParam("write.ms", "3000"));

        int exec = valueOf(registerParam("exec", "1"));
        print = registerParam("print") != null;
        debug = registerParam("debug") != null;

        if (registerParam("server") != null) {
            server = new CountingServer(this.port, 100, MILLISECONDS, debug);
        }

        tuningFactor = Double.valueOf(registerParam("tfactor", "1.2"));
        initialTuningFactor = Double.valueOf(registerParam("tfactor0", "1.1"));
        // << params

        if (initRps > MILLION) throw new IllegalArgumentException("rps<=1M!");

        if (initRps > 0) {
            dynamicRate.set(MILLION / initRps);
        }

        if (exec == 2) {
            this.scheduler = new ExecutorScheduler();
        } else if (exec == 3) {
            this.scheduler = new PlainScheduler();
        } else {
            this.scheduler = new HashedWheelScheduler();
        }


        this.requestSource = requestSource;

        this.addr = new InetSocketAddress(this.host, this.port);
        bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        this.hwTimer = new HashedWheelTimer();
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

    private String registerParam(String name) {
        return registerParam(name, null);
    }

    private String registerParam(String name, String def) {
        String value = getProperty(name, def);
        config.put(name, value);
        return value;
    }

    public void start() throws InterruptedException {
        System.out.printf("Starting stress `%s` to `%s` with %d rps (rate=%d micros), full config: %s%n",
                name, addr, MILLION / dynamicRate.get(), dynamicRate.get(), config);

        if (server != null) {
            server.start();
            TimeUnit.SECONDS.sleep(2);
        }


        if (!checkConnection()) {
            System.err.printf("ERROR: no connection to %s:%d%n", host, port);
            System.exit(0);
        }


        scheduler.startAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (!pause) sendOne();
            }
        }, dynamicRate);

        statExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                showStats();

            }
        }, 0, 1, SECONDS);
    }

    private boolean checkConnection() {
        Socket socket = null;

        // java 6 style to handle such errors >>
        try {
            socket = new Socket(host, port);
            return socket.isConnected();
        } catch (IOException e) {
            // ignore
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return false;
    }

    private void showStats() {
        int conn = connected.get();
        if (dynamicRate.get() > 1) {
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

    public void stop() {
        System.out.printf("client `%s` stopping...%n", name);

        requestExecutor.shutdownNow();
        scheduler.shutdown();
        hwTimer.stop();
        statExecutor.shutdownNow();
        if (server != null) server.stop();
        bootstrap.shutdown();
    }

    public static void main(String[] args) throws Exception {
        final String host = args.length > 0 ? args[0] : "localhost";
        final String port = args.length > 1 ? args[1] : "8080";
        final String rps = args.length > 2 ? args[2] : "-1";

        final StressClient client = new StressClient(host, port, rps, new StubHttpRequest());
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
            if (pause) {
                e.getChannel().close();
                return;
            }

            ChannelBuffer resp = (ChannelBuffer) e.getMessage();
            if (print) {
                System.out.printf("response: %s%n", resp.toString(Charset.defaultCharset()));
            }
            received.incrementAndGet();
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
            if (pause) {
                e.getChannel().close();
                return;
            }
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

    private void processLimitErrors() {
        if (pause || (be.get() + ce.get() < 10)) return;

        pause = true;

        int oldRate = dynamicRate.get();
        int newRate = dynamicRate.get() > 1 ? tuneRate() : calculateInitRate();

        int newRps = (MILLION / newRate);
        int oldRps = (MILLION / oldRate);

        System.err.printf("ERROR: reached port limit! Decreasing rps: %d->%d (rate: %d->%d micros)%n",
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
}
