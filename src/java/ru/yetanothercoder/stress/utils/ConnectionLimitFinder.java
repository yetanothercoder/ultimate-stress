package ru.yetanothercoder.stress.utils;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.GlobalEventExecutor;
import ru.yetanothercoder.stress.StressClient;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static ru.yetanothercoder.stress.StressClient.NUM_OF_CORES;

/**
 * @author Mikhail Baturov,  6/28/13 10:52 PM
 */
public class ConnectionLimitFinder implements Runnable {
    public static final int THRESHOLD = 10;

    private final Bootstrap bootstrap;
    public final AtomicInteger connected = new AtomicInteger(0);
    private final AtomicInteger errors = new AtomicInteger(0);
    private final boolean debug = false;
    private final byte[] requestBytes;
    public volatile boolean stop = false;
    private final ExecutorService executor = Executors.newFixedThreadPool(NUM_OF_CORES);
    public final ChannelGroup opened = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private InetSocketAddress addr;

    public ConnectionLimitFinder(String host, int port) {

        requestBytes = String.format(
                "GET / HTTP/1.1\n" +
                        "Host: %s:%s\n" +
                        "Connection: Keep-Alive\n" +
                        "User-Agent: Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.103 Safari/537.36\n" +
//                                    "Accept-Encoding: gzip,deflate,sdch\n" +
                        "\n\n", host, port).getBytes(Charset.defaultCharset());

        this.addr = new InetSocketAddress(host, port);
        bootstrap = StressClient.initNetty(0, 0, 1000, new CounterHandler());
    }

    private void handleLimitErrors(Throwable e) {
        if (debug) {
            e.printStackTrace(System.err);
        }
//        System.err.println(e.getClass());
        stop = errors.incrementAndGet() > THRESHOLD;
    }

    @Override
    public void run() {
        if (stop) return;

        try {
            ChannelFuture future = bootstrap.connect(addr);
        } catch (ChannelException e) {
            if (e.getCause() instanceof IOException) {
                handleLimitErrors(e.getCause());
            }
        }
    }


    public void stop() throws InterruptedException, ExecutionException, TimeoutException {
        opened.close();
        bootstrap.group().shutdownGracefully().get(5, TimeUnit.SECONDS);
    }

    @ChannelHandler.Sharable
    private class CounterHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            if (stop) return;

            if (connected.incrementAndGet() % 100 == 0) {
                System.out.println(connected.get());
            }

            ByteBuf req = Unpooled.wrappedBuffer(requestBytes);
            ctx.writeAndFlush(req);

            opened.add(ctx.channel());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf resp = (ByteBuf) msg;
            if (debug) System.out.println(resp.toString(Charset.defaultCharset()));
            resp.release();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (!stop) connected.decrementAndGet();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable exc) throws Exception {
//            e.getChannel().close();

            if (exc instanceof IOException) {
                if (!stop) {
                    connected.decrementAndGet();
                    handleLimitErrors(exc);
                }
            } else {
                System.err.println("err: " + exc.toString());
            }
        }
    }


    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {

        String host = args.length < 1 ? "localhost" : args[0];
        int port = args.length < 2 ? 80 : Integer.parseInt(args[1]);

        final ConnectionLimitFinder finder = new ConnectionLimitFinder(host, port);

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                finder.stop = true;
                finder.opened.close();
                if (e instanceof InternalError && e.getCause() instanceof FileNotFoundException) {
                    finder.stop = true;
                    System.out.printf("%d connected, vm error: %s%n", finder.connected.get(), e.getCause().getMessage());
                    e.printStackTrace(System.err);
                }
            }
        });


        ExecutorService exec = Executors.newFixedThreadPool(NUM_OF_CORES, new DefaultThreadFactory("ConnectionCreator", true));


        long start = System.currentTimeMillis();
        while (!Thread.currentThread().isInterrupted() && !finder.stop) {
            exec.submit(finder);
        }
        long time = System.currentTimeMillis() - start;
        exec.shutdownNow();
        exec.awaitTermination(THRESHOLD, TimeUnit.SECONDS);
        finder.stop();

        int max = finder.connected.get() - THRESHOLD;
        System.out.printf("%d in %d ms, rate: %d micros %n", max, time, max * 1000 / time);


    }

}
