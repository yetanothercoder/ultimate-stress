package ru.yetanothercoder.stress.config;

import ru.yetanothercoder.stress.requests.RequestGenerator;
import ru.yetanothercoder.stress.requests.StubHttpGenerator;
import ru.yetanothercoder.stress.timer.SchedulerType;

import static ru.yetanothercoder.stress.timer.SchedulerType.HASHEDWHEEL;

final public class StressConfig {
    public final String host;
    public final int port;
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

    public StressConfig(String host, int initRps, int port, int durationSec, double tuningFactor, double initialTuningFactor, boolean print, boolean debug, boolean httpErrors, boolean server, int sample, SchedulerType type, int readTimeoutMs, int writeTimeoutMs, RequestGenerator requestGenerator) {
        this.host = host;
        this.initRps = initRps;
        this.port = port;
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
        this.requestGenerator = requestGenerator;
    }


    public static class Builder {
        final String host;

        // defaults >>
        int port = 80;
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
        RequestGenerator requestGenerator = new StubHttpGenerator();

        public Builder(String host) {
            this.host = host;
        }

        public Builder port(int port) {
            this.port = port;
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

        public Builder initialTuningFactor(int factor) {
            this.initialTuningFactor = factor;
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

        public Builder withRequestGenerator(RequestGenerator g) {
            this.requestGenerator = g;
            return this;
        }

        public StressConfig build() {
            return new StressConfig(host, initRps, port, durationSec, tuningFactor, initialTuningFactor, print, debug,
                    httpErrors, server, sample, exec, readTimeoutMs, writeTimeoutMs, requestGenerator);
        }
    }
}
