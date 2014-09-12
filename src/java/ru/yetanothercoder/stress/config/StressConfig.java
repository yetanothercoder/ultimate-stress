package ru.yetanothercoder.stress.config;

import ru.yetanothercoder.stress.StressClient;
import ru.yetanothercoder.stress.requests.RequestGenerator;
import ru.yetanothercoder.stress.requests.StubHttpGenerator;
import ru.yetanothercoder.stress.timer.SchedulerType;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

import static ru.yetanothercoder.stress.timer.SchedulerType.HASHEDWHEEL;

final public class StressConfig {
    public final URI url;
    public final int initRps;
    public final int durationSec;
    public final double tuningFactor;
    public final double initialTuningFactor;
    public final boolean print;
    public final boolean debug;
    public final boolean httpErrors;
    public final boolean server;
    public final int sample;
    public final SchedulerType type;
    public final int readTimeoutMs, writeTimeoutMs;
    public final RequestGenerator requestGenerator;
    public final Path dir;
    public final String prefix;

    public StressConfig(URI url, int initRps, int durationSec, double tuningFactor, double initialTuningFactor, boolean print, boolean debug, boolean httpErrors, boolean server, int sample, SchedulerType type, int readTimeoutMs, int writeTimeoutMs, Path dir, String prefix, RequestGenerator requestGenerator) {
        this.url = url;
        this.initRps = initRps;
        this.durationSec = durationSec;
        this.tuningFactor = tuningFactor;
        this.initialTuningFactor = initialTuningFactor;
        this.print = print;
        this.debug = debug;
        this.httpErrors = httpErrors;
        this.server = server;
        this.sample = sample;
        this.type = type;
        this.readTimeoutMs = readTimeoutMs;
        this.writeTimeoutMs = writeTimeoutMs;
        this.dir = dir;
        this.prefix = prefix;
        this.requestGenerator = requestGenerator;
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
        boolean httpErrors = false;
        boolean server = false;
        int sample = -1;
        SchedulerType exec = HASHEDWHEEL;
        int readTimeoutMs = 1000, writeTimeoutMs = 1000;
        Path dir;
        String prefix;
        RequestGenerator requestGenerator = new StubHttpGenerator();


        public Builder host(String host) {
            try {
                this.url = new URI("http", host, null, null);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            return this;
        }

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

        public Builder debug() {
            this.debug = true;
            return this;
        }

        public Builder httpErrors() {
            this.httpErrors = true;
            return this;
        }

        public Builder server() {
            this.server = true;
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

        public Builder writeTimeout(int ms) {
            this.writeTimeoutMs = ms;
            return this;
        }

        public Builder withExec(SchedulerType type) {
            this.exec = type;
            return this;
        }

        public Builder requestGenerator(RequestGenerator g) {
            this.requestGenerator = g;
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
            return new StressConfig(url, initRps, durationSec, tuningFactor, initialTuningFactor, print, debug,
                    httpErrors, server, sample, exec, readTimeoutMs, writeTimeoutMs, dir, prefix, requestGenerator);
        }

        public StressClient buildClient() {
            return new StressClient(build());
        }
    }
}
