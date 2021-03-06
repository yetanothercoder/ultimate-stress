package ru.yetanothercoder.stress.config;

import ru.yetanothercoder.stress.StressClient;
import ru.yetanothercoder.stress.requests.GetHttpRequestGenerator;
import ru.yetanothercoder.stress.requests.RequestGenerator;
import ru.yetanothercoder.stress.timer.SchedulerType;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static ru.yetanothercoder.stress.timer.SchedulerType.HASHEDWHEEL;

/**
 * Full config for client & server mode
 *
 * @author Mikhail Baturov, http://www.yetanothercoder.ru/search/label/stress
 */
final public class StressConfig {
    public final URI url;
    public final int initRps;
    public final int durationSec;
    public final double tuningFactor;
    public final double initialTuningFactor;
    public final boolean print;
    public final boolean debug;
    public final boolean quiet;
    public final boolean httpStatuses;
    public final int server;
    public final int sample;
    public final SchedulerType type;
    public final int readTimeoutMs, writeTimeoutMs;
    public final RequestGenerator requestGenerator;
    public final Path dir;
    public final String prefix;
    public final int serverRandomDelayMs;
    public final List<String> headers;

    public StressConfig(URI url, int initRps, int durationSec, double tuningFactor, double initialTuningFactor, boolean print, boolean debug, boolean quiet, boolean httpStatuses, int server, int sample, SchedulerType type, int readTimeoutMs, int writeTimeoutMs, Path dir, String prefix, RequestGenerator requestGenerator, int serverRandomDelayMs, List<String> headers) {
        this.url = url;
        this.initRps = initRps;
        this.durationSec = durationSec;
        this.tuningFactor = tuningFactor;
        this.initialTuningFactor = initialTuningFactor;
        this.print = print;
        this.debug = debug;
        this.quiet = quiet;
        this.httpStatuses = httpStatuses;
        this.server = server;
        this.sample = sample;
        this.type = type;
        this.readTimeoutMs = readTimeoutMs;
        this.writeTimeoutMs = writeTimeoutMs;
        this.dir = dir;
        this.prefix = prefix;
        this.requestGenerator = requestGenerator;
        this.serverRandomDelayMs = serverRandomDelayMs;
        this.headers = new ArrayList<>(headers); // def copy
    }

    public String getHost() {
        return url.getHost();
    }

    public int getPort() {
        return url.getPort() < 0 ? 80 : url.getPort();
    }

    public String getHostPort() {
        return String.format("%s:%s", getHost(), getPort());
    }

    @Override
    public String toString() {
        return "{" +
                "url=" + url +
                ", initRps=" + initRps +
                ", durationSec=" + durationSec +
                ", tuningFactor=" + tuningFactor +
                ", initialTuningFactor=" + initialTuningFactor +
                ", print=" + print +
                ", debug=" + debug +
                ", httpStatuses=" + httpStatuses +
                ", server=" + server +
                ", sample=" + sample +
                ", type=" + type +
                ", readTimeoutMs=" + readTimeoutMs +
                ", writeTimeoutMs=" + writeTimeoutMs +
                ", requestGenerator=" + requestGenerator +
                ", dir=" + dir +
                ", prefix=" + prefix +
                ", headers=" + headers +
                '}';
    }

    public static class Builder {
        URI url;

        // defaults >>
        int initRps = 10;
        int durationSec = -1;
        double tuningFactor = 1.1;
        double initialTuningFactor = 1.2;
        boolean print = false;
        boolean debug = false;
        boolean quiet;
        boolean httpErrors = false;
        int server = 0;
        int sample = -1;
        SchedulerType exec = HASHEDWHEEL;
        int readTimeoutMs = 100, writeTimeoutMs = 100;
        Path dir;
        String prefix;
        RequestGenerator requestGenerator;
        int serverRandomDelayMs = -1;
        List<String> headers = new ArrayList<>(2);

        public Builder url(URI url) {
            this.url = url;
            return this;
        }

        public Builder url(String url) {
            try {
                this.url = new URI(url);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            return this;
        }

        public Builder rps(int rps) {
            this.initRps = rps;
            return this;
        }

        public Builder tuningFactor(double factor) {
            this.tuningFactor = factor;
            return this;
        }

        public Builder initialTuningFactor(double factor) {
            this.initialTuningFactor = factor;
            return this;
        }

        public Builder duration(int sec) {
            this.durationSec = sec;
            return this;
        }

        public Builder print() {
            this.print = true;
            return this;
        }

        public Builder quiet() {
            this.quiet = true;
            return this;
        }

        public Builder debug() {
            this.debug = true;
            return this;
        }

        public Builder httpErrors() {
            this.httpErrors = true;
            return this;
        }

        public Builder server(int port) {
            this.server = port;
            return this;
        }

        public Builder sampleNumber(int n) {
            this.sample = n;
            return this;
        }

        public Builder readTimeout(int ms) {
            this.readTimeoutMs = ms;
            return this;
        }

        public Builder serverRandomDelay(int ms) {
            this.serverRandomDelayMs = ms;
            return this;
        }

        public Builder writeTimeout(int ms) {
            this.writeTimeoutMs = ms;
            return this;
        }

        public Builder scheduler(SchedulerType type) {
            this.exec = type;
            return this;
        }

        public Builder requestGenerator(RequestGenerator g) {
            this.requestGenerator = g;
            return this;
        }

        public Builder addHeader(String nameAndValue) {
            this.headers.add(nameAndValue);
            return this;
        }

        public Builder dir(Path dir) {
            this.dir = dir;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public StressConfig build() {
            if (requestGenerator == null && url != null) {
                int port = url.getPort() < 0 ? 80 : url.getPort();
                String query = url.getPath();
                if (url.getQuery() != null) query += "?" + url.getQuery();

                requestGenerator = new GetHttpRequestGenerator(url.getHost(), port, query, headers);
            }
            return new StressConfig(url, initRps, durationSec, tuningFactor, initialTuningFactor, print, debug,
                    quiet, httpErrors, server, sample, exec, readTimeoutMs, writeTimeoutMs, dir, prefix, requestGenerator, serverRandomDelayMs, headers);
        }

        public StressClient buildClient() {
            return new StressClient(build());
        }
    }
}
