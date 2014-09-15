package ru.yetanothercoder.stress.utils;

public class HostPort {
    public final String host;
    public final int port;

    public HostPort(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HostPort hostPort = (HostPort) o;

        if (port != hostPort.port) return false;
        return host.equals(hostPort.host);

    }

    @Override
    public int hashCode() {
        int result = host.hashCode();
        result = 31 * result + port;
        return result;
    }
}
