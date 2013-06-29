package ru.yetanothercoder.tests.stress;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import java.net.BindException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Mikhail Baturov,  6/28/13 10:52 PM
 */
public class ConnectionLimitFinder implements Callable<Integer> {
    private final ClientBootstrap bootstrap;
    private final ChannelHandler handler = new ErrorHandler();
    public final AtomicInteger connected = new AtomicInteger(0);
    private final AtomicInteger errors = new AtomicInteger(0);
    private final String host;
    private final int port;
    private final boolean debug;
    public volatile boolean stop = false;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    public final ChannelGroup opened = new DefaultChannelGroup();

    public ConnectionLimitFinder(String host, int port) {
        this.host = host;
        this.port = port;
        this.debug = false;

        bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(executor, executor, 1));


        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("errors", handler);
                return pipeline;
            }
        });
    }

    private boolean handleLimitErrors(Throwable e) {
        if (debug) {
            e.printStackTrace(System.err);
        }
        return  (errors.incrementAndGet() > 10);
    }

    @Override
    public Integer call() throws Exception {
        InetSocketAddress addr = new InetSocketAddress(host, port);

        while (!Thread.currentThread().isInterrupted() && !stop) {
            try {
                ChannelFuture future = bootstrap.connect(addr);
                if (connected.incrementAndGet() % 1000 == 0) {
                    System.out.println(connected.get());
                }
            } catch (ChannelException e) {
                if (e.getCause() instanceof SocketException) {
                    stop = handleLimitErrors(e.getCause());
                }
            }

//            TimeUnit.MICROSECONDS.sleep(1);
        }
        opened.close();
        executor.shutdownNow();
        bootstrap.shutdown();
        return connected.get();
    }

    private class ErrorHandler extends SimpleChannelUpstreamHandler {
        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            opened.add(e.getChannel());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            Throwable exc = e.getCause();
//            e.getChannel().close();

            if (exc instanceof BindException || exc instanceof ConnectException) {
                stop = handleLimitErrors(exc);
            }
        }
    }


    public static void main(String[] args) throws ExecutionException, InterruptedException {
        final ConnectionLimitFinder finder = new ConnectionLimitFinder("localhost", 8080);

        /*Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
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
        });*/


        ExecutorService exec = Executors.newSingleThreadExecutor();

        long start = System.currentTimeMillis();
        Future<Integer> result = exec.submit(finder);
        int max = result.get();
        long time = System.currentTimeMillis() - start;
        System.out.printf("%d in %d ms, rate: %d micros %n", max, time, max * 1000 / time);

        exec.shutdownNow();
    }

}
