package ru.yetanothercoder.stress.cli;

import org.junit.*;
import org.junit.rules.ExpectedException;
import ru.yetanothercoder.stress.config.StressConfig;
import ru.yetanothercoder.stress.requests.HttpFileTemplateGenerator;

import java.nio.file.Paths;

public class CliParserTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testDurationError() throws Exception {
        expectedEx.expect(NumberFormatException.class);
        expectedEx.expectMessage("For input string: \"sdf\"");

        String[] args = new String[]{"-t", "sdf", "-s", "http://myhost:8080/myapp?p1=1&p2=2", "500"};
        CliParser.parseAndValidate(args);
    }

    @Test
    public void testWrongDir() throws Exception {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Incorrect path: sdfsadf");

        String[] args = new String[]{"-d", "sdfsadf", "-s", "http://myhost:8080/myapp?p1=1&p2=2", "500"};
        CliParser.parseAndValidate(args);
    }

    @Test
    public void testEmptyArgs() throws Exception {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("no required params!");

        String[] args = new String[]{};
        CliParser.parseAndValidate(args);
    }

    @Test
    public void testNoRequired() throws Exception {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("no required params!");

        String[] args = new String[]{"-t", "7"};
        CliParser.parseAndValidate(args);
    }

    @Test
    public void testFactorError() throws Exception {
        expectedEx.expect(NumberFormatException.class);
        expectedEx.expectMessage("For input string: \"sdf\"");

        String[] args = new String[]{"-k", "sdf", "http://myhost:8080/myapp?p1=1&p2=2"};
        CliParser.parseAndValidate(args);
    }

    @Test
    public void testFactor() throws Exception {
        String[] args = new String[]{"-k", "10.1", "http://myhost:8080/myapp?p1=1&p2=2"};
        StressConfig config = CliParser.parseAndValidate(args);
        Assert.assertEquals(config.tuningFactor, 10.1, 1e-10);
    }

    @Test
    public void testAll() throws Exception {
        String[] args = new String[]{"-s", "-t", "7", "-k", "1.5", "-k0", "1.4", "-d", ".", "-f", "http",
                "-pr", "-debug", "-he", "-rt", "77", "-wt", "88",
                "http://myhost:8080/myapp?p1=1&p2=2", "777"};

        StressConfig config = CliParser.parseAndValidate(args);
        Assert.assertTrue(config.durationSec == 7);
        Assert.assertTrue(config.initRps == 777);
        Assert.assertEquals(config.tuningFactor, 1.5, 1e-10);
        Assert.assertEquals(config.initialTuningFactor, 1.4, 1e-10);
        Assert.assertEquals(config.dir, Paths.get("."));
        Assert.assertEquals(config.prefix, "http");
        Assert.assertTrue(config.server);
        Assert.assertTrue(config.print);
        Assert.assertTrue(config.debug);
        Assert.assertTrue(config.httpErrors);
        Assert.assertTrue(config.requestGenerator instanceof HttpFileTemplateGenerator);
        Assert.assertTrue(config.readTimeoutMs == 77);
        Assert.assertTrue(config.writeTimeoutMs == 88);
        Assert.assertEquals(config.url.getHost(), "myhost");
        Assert.assertEquals(config.url.getPort(), 8080);
        Assert.assertEquals(config.url.getPath(), "/myapp");
    }
}