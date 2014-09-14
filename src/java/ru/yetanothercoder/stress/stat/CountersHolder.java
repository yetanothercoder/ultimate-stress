package ru.yetanothercoder.stress.stat;

import java.util.concurrent.atomic.AtomicInteger;

final public class CountersHolder {
    /**
     * Number of open ports
     */
    public final AtomicInteger connected = new AtomicInteger(0);
    /**
     * Number of requests sent per second
     */
    public final AtomicInteger sent = new AtomicInteger(0);
    /**
     * Number of responses received per second
     */
    public final AtomicInteger received = new AtomicInteger(0);
    /**
     * Total number of sent requests
     */
    public final AtomicInteger total = new AtomicInteger(0);
    /**
     * Total size of responses
     */
    public final AtomicInteger receivedBytes = new AtomicInteger(0);
    /**
     * Total size of requests
     */
    public final AtomicInteger sentBytes = new AtomicInteger(0);
    /**
     * Number of connection/read and write timeout errors per second
     */
    public final AtomicInteger te = new AtomicInteger(0);
    /**
     * Number of exhausted port errors (when no available ports) per second
     */
    public final AtomicInteger be = new AtomicInteger(0);
    /**
     * Number of connection errors (when can't connect to host) per second
     */
    public final AtomicInteger ce = new AtomicInteger(0);
    /**
     * Number of IO errors
     */
    public final AtomicInteger ie = new AtomicInteger(0);

    /**
     * Other errors per sec
     */
    public final AtomicInteger oe = new AtomicInteger(0);

    /**
     * Total error num
     */
    public final AtomicInteger errors = new AtomicInteger(0);
}
