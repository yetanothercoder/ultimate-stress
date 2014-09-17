package ru.yetanothercoder.stress;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class NettyReusableChannelsTest {
    private Bootstrap bootstrap;
    private ExecutorService requestEx;
    private NioEventLoopGroup workerGroup;

    @Before
    public void setUp() throws Exception {
        workerGroup = new NioEventLoopGroup();


        requestEx = Executors.newFixedThreadPool(10);

    }

    @After
    public void tearDown() throws Exception {
        workerGroup.shutdownGracefully();
        requestEx.shutdownNow();
    }

    @Test
    public void testBB() throws Exception {
        ByteBuf bb = Unpooled.copiedBuffer("foo".getBytes(Charset.defaultCharset()));
        System.out.println(bb.refCnt());

        bb.release();

        System.out.println(bb.refCnt());

    }

    @Test
    public void testYa() throws Exception {

        final CountDownLatch count = new CountDownLatch(20);
        bootstrap = new Bootstrap()
                .group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(
                                new ReadTimeoutHandler(100, MILLISECONDS),
                                new WriteTimeoutHandler(100, MILLISECONDS));

                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                sendToChannel(ctx);
                            }

                            @Override
                            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                                System.out.println("777 read complete");

                                if (ctx.channel().isActive() && ctx.channel().isWritable()) {
                                    System.out.println("777 Active, sending again...");
                                    sendToChannel(ctx);
                                }
                            }

                            @Override
                            public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                                System.out.println("777 writability: " + ctx.channel().isWritable());
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                System.out.println("777 inactive");
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                cause.printStackTrace(System.err);
                            }

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                ByteBuf resp = (ByteBuf) msg;

                                count.countDown();

                                int size = resp.capacity() - 1;
                                //                        System.out.println(size);
                                System.out.printf("****************************************************>>>>> %s%n", Thread.currentThread().getName());
                                System.out.println(resp.toString(Charset.defaultCharset()));
                                System.out.println("<<<<<****************************************************");
//                        System.out.printf("response(%s): %s%n", size, resp.toString(size - 50, 50, Charset.defaultCharset()));

//                        sendToChannel(channel, requestEx);
                                resp.release();
                            }
                        });
                    }
                });


//        bootstrap.setOption("reuseAddress", true);
//        bootstrap.setOption("connectTimeoutMillis", 100);
//        bootstrap.setOption("keepAlive", true);
//        bootstrap.setOption("receiveBufferSize", 1_048_576);

        ChannelFuture future = bootstrap.connect(new InetSocketAddress("ya.ru", 80));
        future.awaitUninterruptibly();
        System.out.println("connected!");

        count.await(5, TimeUnit.SECONDS);
    }

    private void sendToChannel(final ChannelHandlerContext ctx) {
        if (ctx.channel().isOpen()) {
            System.out.println("open, sending...");

            requestEx.execute(new Runnable() {
                @Override
                public void run() {
                    String request = String.format(
                            "GET / HTTP/1.1\n" +
                                    "Host: ya.ru\n" +
                                    "Connection: Keep-Alive\n" +
                                    "Cache-Control: max-age=0\n" +
                                    "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\n" +
                                    "User-Agent: Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.103 Safari/537.36\n" +
//                                    "Accept-Encoding: gzip,deflate,sdch\n" +
                                    "Accept-Language: en-US,en;q=0.8,ru;q=0.6,az;q=0.4\n" +
                                    "\n\n"
                    );

                    ByteBuf req = Unpooled.wrappedBuffer(request.getBytes(Charset.defaultCharset()));
                    ctx.writeAndFlush(req);
                }
            });

        } else {
            System.out.println("closed!");
        }
    }
}
