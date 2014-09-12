package ru.yetanothercoder.stress.cli;

import ru.yetanothercoder.stress.config.StressConfig;
import ru.yetanothercoder.stress.requests.HttpFileTemplateGenerator;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CliParser {

    public static void main(String[] args) throws Exception {
//        CliParser.parseAndValidate(args);


        URI uri = null;
        try {
            uri = new URI("http://myhost:8080/index.html?id=asdf&bla");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        System.out.println(uri.getHost());
        System.out.println(uri.getPort());
        System.out.println(uri.getPath());
        System.out.println(uri.getRawPath());
        System.out.println(uri.getQuery());
        System.out.println(uri.getRawQuery());

        System.out.println(uri);
    }

    public static StressConfig parseAndValidate(String... args) throws Exception {
        final StressConfig.Builder b = new StressConfig.Builder();
        final int size = args.length;

        if (size < 1) throw new IllegalArgumentException("no required params!");

        for (int i = 0; i < size; i++) {
            final String arg = args[i];

            switch (arg) {
                case "-s":
                    b.server();
                    break;
                case "-debug":
                    b.debug();
                    break;
                case "-pr":
                    b.print();
                    break;
                case "-he":
                    b.httpErrors();
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

        StressConfig config = b.build();
        if (config.url == null && config.dir == null && config.prefix == null) {
            throw new IllegalArgumentException("no required params!");
        }

        if (config.dir != null || config.prefix != null) {
            String hostPort = config.url == null ? null : config.url.getHost() + ":" + config.url.getPort();
            Path dir = config.dir == null ? Paths.get(".") : config.dir;
            b.requestGenerator(new HttpFileTemplateGenerator(dir, config.prefix, hostPort, null));

            config = b.build();
        }

        return config;
    }
}
