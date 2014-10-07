package ru.yetanothercoder.stress;

import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;
import java.util.List;

public class ThreadLocalConnectionHolder {

    private final ThreadLocal<List<ChannelHandlerContext>> connections;
//    private final AtomicInteger


    public ThreadLocalConnectionHolder(final int perThreadSize) {
        connections = new ThreadLocal<List<ChannelHandlerContext>>() {


            @Override
            protected List<ChannelHandlerContext> initialValue() {
                return new ArrayList<>(perThreadSize);
            }
        };
    }

    public ChannelHandlerContext retrieveConnection() {
        return connections.get().iterator().next();
    }
}
