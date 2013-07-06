package ru.yetanothercoder.stress.requests;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Mikhail Baturov, 7/3/13 9:07 PM
 */
public class HttpFileTemplateSource implements RequestSource {

    public static final String HOSTPORT_PLACEHOLDER = "$hp";

    private final String prefix, hostPort;
    private final File dir;
    private final List<String> templates;
    private final AtomicInteger i = new AtomicInteger(0);
    private final List<Pair> replacementList = new CopyOnWriteArrayList<>();

    public HttpFileTemplateSource(String path, String filePrefix, String hostPort, Map<String, String> replacements) {
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
        Arrays.sort(files);
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
        int index = i.getAndIncrement() % templates.size();
        String tpl = templates.get(index);
        return processOnEachRequest(tpl);
    }

    public void addReplacement(String name, String value) {
        replacementList.add(new Pair(name, value));
    }

    protected ChannelBuffer processOnEachRequest(String template) {
        for (Pair pair : replacementList) {
            template = fastReplace(template, pair.name, pair.value);
        }
        return ChannelBuffers.wrappedBuffer(template.getBytes(UTF_8));
    }

    private String fastReplace(String text, String name, String value) {
        int start;
        if (text == null || (start = text.indexOf(name)) < 0) return text;

        return text.substring(0, start) + value + text.substring(start + name.length());


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
