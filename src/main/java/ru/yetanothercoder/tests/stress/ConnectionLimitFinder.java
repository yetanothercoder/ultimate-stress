package ru.yetanothercoder.tests.stress;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
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
    private final AtomicInteger connected = new AtomicInteger(0);
    private final AtomicInteger errors = new AtomicInteger(0);
    private final String host;
    private final int port;
    private final boolean debug;
    private volatile boolean stop = false;
    private final ExecutorService executor = Executors.newCachedThreadPool();

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
                connected.incrementAndGet();
            } catch (ChannelException e) {
                if (e.getCause() instanceof SocketException) {
                    stop = handleLimitErrors(e.getCause());
                }
            }

            TimeUnit.MICROSECONDS.sleep(1);
        }
        executor.shutdownNow();
        bootstrap.shutdown();
        return connected.get();
    }

    private class ErrorHandler extends SimpleChannelUpstreamHandler {
        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            e.getChannel().close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            Throwable exc = e.getCause();
            e.getChannel().close();

            if (exc instanceof BindException || exc instanceof ConnectException) {
                stop = handleLimitErrors(exc);
            }
        }
    }


    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ConnectionLimitFinder finder = new ConnectionLimitFinder("localhost", 8080);
        ExecutorService exec = Executors.newSingleThreadExecutor();
        long start = System.currentTimeMillis();
        Future<Integer> result = exec.submit(finder);
        int max = result.get();
        long time = System.currentTimeMillis() - start;
        System.out.printf("%d in %d ms, rate: %d micros %n", max, time, max * 1000 / time);

        exec.shutdownNow();
    }

}
