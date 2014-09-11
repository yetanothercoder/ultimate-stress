package ru.yetanothercoder.stress.requests;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Http file request parser.
 * It reads text files from specified dir in local filesystem and
 * replaces placeholders in them on each request (except $hp, it's done once reading file)
 *
 * @author Mikhail Baturov, http://www.yetanothercoder.ru/search/label/stress
 */
public class HttpFileTemplateGenerator implements RequestGenerator {

    public static final String HOSTPORT_PLACEHOLDER = "$hp";

    private final String prefix, hostPort;
    private final File dir;
    private final List<String> templates;
    private final AtomicInteger i = new AtomicInteger(0);
    private final List<Pair> replacementList = new CopyOnWriteArrayList<>();

    /**
     * Construct source, read file contents in memory
     *
     * @param path         directory where request files
     * @param filePrefix   prefix for file names
     * @param hostPort     host and port
     * @param replacements placeholder names with values which substitutes on each request
     */
    public HttpFileTemplateGenerator(String path, String filePrefix, String hostPort, Map<String, String> replacements) {
        this.dir = new File(path);
        if (!dir.exists()) throw new IllegalArgumentException("Incorrect path: " + path);
        this.prefix = filePrefix;

        if (replacements != null) {
            replacementList.addAll(fromMap(replacements));
        }

        try {
            templates = readFiles(hostPort);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed reading files from " + dir.getAbsolutePath(), e);
        }

        if (templates.size() == 0) {
            throw new IllegalArgumentException(format(
                    "no templates found in `%s` with prefix `%s`", dir.getAbsolutePath(), prefix));
        }

        this.hostPort = hostPort;
    }

    private List<Pair> fromMap(Map<String, String> replacements) {
        List<Pair> result = new ArrayList<>(replacementList.size());
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result.add(new Pair(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private List<String> readFiles(String hostPort) throws IOException {
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return prefix == null || name.startsWith(prefix);
            }
        };

        List<String> result = new ArrayList<>();
        File[] files = dir.listFiles(filter);
        for (File file : files) {
            result.add(compileTemplate(file, hostPort));
        }

        return result;
    }

    private String compileTemplate(File file, String hostPort) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(file.getPath()));
        String contents = new String(bytes, UTF_8);
        String template = fastReplace(contents, HOSTPORT_PLACEHOLDER, hostPort);
        template += "\n\n";  // add empty lines for sure
        return template;
    }

    @Override
    public ChannelBuffer next() {
        String tpl = nextTemplate();
        return serializeResponse(replaceOnEachRequest(tpl));
    }

    protected String nextTemplate() {
        int index = i.getAndIncrement() % templates.size();
        return templates.get(index);
    }

    public void addReplacement(String name, String value) {
        replacementList.add(new Pair(name, value));
    }

    protected String replaceOnEachRequest(String template) {
        for (Pair pair : replacementList) {
            template = fastReplace(template, pair.name, pair.value);
        }
        return template;
    }

    protected ChannelBuffer serializeResponse(String response) {
        return ChannelBuffers.wrappedBuffer(response.getBytes(UTF_8));
    }

    public static String fastReplace(String text, String name, String value) {
        int start;
        if (text == null || (start = text.indexOf(name)) < 0) return text;

        return new StringBuilder(text.substring(0, start))
                .append(value)
                .append(text.substring(start + name.length())).toString();


    }

    class Pair {
        final String name;
        final String value;

        Pair(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
