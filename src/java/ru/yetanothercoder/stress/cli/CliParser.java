package ru.yetanothercoder.stress.cli;

import ru.yetanothercoder.stress.config.StressConfig;
import ru.yetanothercoder.stress.requests.HttpFileTemplateGenerator;
import ru.yetanothercoder.stress.utils.HostPort;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CliParser {

    public static void main(String[] args) {
//        CliParser.parseAndValidate(args);

        URI uri = URI.create("http://myhost:8080/index.html?id=asdf&bla");

        System.out.println(uri.getHost());
        System.out.println(uri.getPort());
        System.out.println(uri.getPath());
        System.out.println(uri.getRawPath());
        System.out.println(uri.getQuery());
        System.out.println(uri.getRawQuery());

        System.out.println(uri);
    }

    public static StressConfig parseAndValidate(String... args) throws Exception {
        final StressConfig.Builder b = parse(args);

        StressConfig config = b.build();
        if (config.server == 0) {
            if (config.url == null && config.dir == null && config.prefix == null) {
                throw noParamsExeption();
            }

            if (config.dir != null || config.prefix != null) {
                String hostPort = config.url == null ? null : config.getHostPort();
                Path dir = config.dir == null ? Paths.get(".") : config.dir;
                HttpFileTemplateGenerator generator = new HttpFileTemplateGenerator(dir, config.prefix, hostPort, null);
                if (config.url == null) {
                    List<HostPort> hosts = generator.parseHosts();
                    if (hosts.isEmpty()) throw noParamsExeption();

                    // take just first for now
                    HostPort first = hosts.get(0);
                    b.url(URI.create(String.format("http://%s:%s/", first.host, first.port)));
                }

                b.requestGenerator(generator);

                config = b.build();
            }
        }

        return config;
    }

    private static RuntimeException noParamsExeption() {
        throw new IllegalArgumentException("no required params: either of url/-d/-f must be set!");
    }

    private static StressConfig.Builder parse(String[] args) {
        final StressConfig.Builder b = new StressConfig.Builder();
        final int size = args.length;

        if (size < 1) throw noParamsExeption();

        for (int i = 0; i < size; i++) {
            final String arg = args[i];

            switch (arg) {
                case "-debug":
                    b.debug();
                    break;
                case "-q":
                    b.quiet();
                    break;
                case "-pr":
                    b.print();
                    break;
                case "-he":
                    b.httpErrors();
                    break;
                case "-s":
                    if (++i == size) throw new IllegalArgumentException("`-s` no value!");
                    b.server(Integer.parseInt(args[i]));
                    break;
                case "-srd":
                    if (++i == size) throw new IllegalArgumentException("`-srd` no value!");
                    b.serverRandomDelay(Integer.parseInt(args[i]));
                    break;
                case "-t":
                    if (++i == size) throw new IllegalArgumentException("`-t` no value!");
                    b.duration(Integer.parseInt(args[i]));
                    break;
                case "-rt":
                    if (++i == size) throw new IllegalArgumentException("`-rt` no value!");
                    b.readTimeout(Integer.parseInt(args[i]));
                    break;
                case "-wt":
                    if (++i == size) throw new IllegalArgumentException("`-wt` no value!");
                    b.writeTimeout(Integer.parseInt(args[i]));
                    break;
                case "-d":
                    if (++i == size) throw new IllegalArgumentException("`-d` no value!");

                    b.dir(Paths.get(args[i]));
                    break;
                case "-f":
                    if (++i == size) throw new IllegalArgumentException("`-f` no value!");

                    b.prefix(args[i]);
                    break;
                case "-k":
                    if (++i == size) throw new IllegalArgumentException("`-k` no value!");

                    b.tuningFactor(Double.parseDouble(args[i]));
                    break;
                case "-k0":
                    if (++i == size) throw new IllegalArgumentException("`-k0` no value!");
                    b.initialTuningFactor(Double.parseDouble(args[i]));
                    break;
                case "-h":
                    if (++i == size) throw new IllegalArgumentException("`-h` no value!");

                    b.addHeader(args[i]);
                    break;
                case "!":
                    b.rps(-1);
                    break;
                default: // either url or rps

                    try {
                        int rps = Integer.parseInt(arg);
                        b.rps(rps);
                        continue;
                    } catch (NumberFormatException nfe) {
                        // ignore
                    }

                    try {
                        URI url = new URI(arg);
                        b.url(url);
                    } catch (URISyntaxException e) {
                        // ignore
                    }

            }
        }
        return b;
    }
}
