package nablarch.fw.launcher.logging;

import nablarch.core.log.LogTestSupport;
import nablarch.core.text.json.BasicJsonSerializationManager;
import nablarch.core.text.json.JsonSerializationManager;
import nablarch.core.text.json.JsonSerializationSettings;
import nablarch.core.text.json.JsonSerializer;
import nablarch.fw.launcher.CommandLine;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.io.IOException;
import java.io.Writer;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.isJson;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;

/**
 * {@link LauncherJsonLogFormatter}のテストクラス。
 *
 * @author Shuji Kitamura
 */
public class LauncherJsonLogFormatterTest extends LogTestSupport {

    /**
     * {@link LauncherJsonLogFormatter#getStartLogMsg(CommandLine)}のテスト。
     * デフォルトの出力項目で正しくフォーマットされること。
     */
    @Test
    public void testGetStartLogMsg() {

        LauncherLogFormatter formatter = new LauncherJsonLogFormatter();

        String requestPath = "nablarch.hoge.HogeAction/RBHOGEHOGE";
        String userId = "testUser";
        String diConfig = "test.xml";

        CommandLine commandLine = new CommandLine(
                "-diConfig", diConfig,
                "-userId", userId,
                "-requestPath", requestPath
        );

        String message = formatter.getStartLogMsg(commandLine);
        assertThat(message.startsWith("$JSON$"), is(true));
        assertThat(message.substring("$JSON$".length()), isJson(allOf(
                withJsonPath("$", hasEntry("label", "BATCH BEGIN")),
                withJsonPath("$.commandLineOptions", hasEntry("diConfig", "test.xml")),
                withJsonPath("$.commandLineOptions", hasEntry("userId", "testUser")),
                withJsonPath("$.commandLineOptions", hasEntry("requestPath", "nablarch.hoge.HogeAction/RBHOGEHOGE")),
                withJsonPath("$.commandLineArguments", hasSize(0)))));

        commandLine = new CommandLine(
                "-diConfig", diConfig,
                "-userId", userId,
                "-requestPath", requestPath,
                "-param1", "param1",
                "-param2", "param2",
                "h", "o", "g", "e", "hoge",
                "-param3", "param3",
                "fuga"
        );

        message = formatter.getStartLogMsg(commandLine);
        assertThat(message.startsWith("$JSON$"), is(true));
        assertThat(message.substring("$JSON$".length()), isJson(allOf(
                withJsonPath("$", hasEntry("label", "BATCH BEGIN")),
                withJsonPath("$.commandLineOptions", hasEntry("diConfig", "test.xml")),
                withJsonPath("$.commandLineOptions", hasEntry("userId", "testUser")),
                withJsonPath("$.commandLineOptions", hasEntry("requestPath", "nablarch.hoge.HogeAction/RBHOGEHOGE")),
                withJsonPath("$.commandLineOptions", hasEntry("param1", "param1")),
                withJsonPath("$.commandLineOptions", hasEntry("param2", "param2")),
                withJsonPath("$.commandLineOptions", hasEntry("param3", "param3")),
                withJsonPath("$.commandLineArguments", hasSize(6)),
                withJsonPath("$.commandLineArguments[0]", is("h")),
                withJsonPath("$.commandLineArguments[1]", is("o")),
                withJsonPath("$.commandLineArguments[2]", is("g")),
                withJsonPath("$.commandLineArguments[3]", is("e")),
                withJsonPath("$.commandLineArguments[4]", is("hoge")),
                withJsonPath("$.commandLineArguments[5]", is("fuga")))));
    }

    /**
     * {@link LauncherJsonLogFormatter#getStartLogMsg(CommandLine)}のテスト。
     * targets を指定した場合のテスト。
     */
    @Test
    public void testGetStartLogMsgWithTargets() {
        System.setProperty("launcherLogFormatter.startTargets", "commandLineOptions");
        LauncherLogFormatter formatter = new LauncherJsonLogFormatter();

        String requestPath = "nablarch.hoge.HogeAction/RBHOGEHOGE";
        String userId = "testUser";
        String diConfig = "test.xml";

        CommandLine commandLine = new CommandLine(
                "-diConfig", diConfig,
                "-userId", userId,
                "-requestPath", requestPath
        );

        String message = formatter.getStartLogMsg(commandLine);
        assertThat(message.startsWith("$JSON$"), is(true));
        assertThat(message.substring("$JSON$".length()), isJson(allOf(
                withJsonPath("$.*", hasSize(1)),
                withJsonPath("$.commandLineOptions", hasEntry("diConfig", "test.xml")),
                withJsonPath("$.commandLineOptions", hasEntry("userId", "testUser")),
                withJsonPath("$.commandLineOptions", hasEntry("requestPath", "nablarch.hoge.HogeAction/RBHOGEHOGE"))
        )));
    }

    /**
     * {@link LauncherJsonLogFormatter#getStartLogMsg(CommandLine)}のテスト。
     * labelの値を指定した場合。
     */
    @Test
    public void testGetStartLogMsgWithLabelValue() {
        System.setProperty("launcherLogFormatter.startTargets", "label");
        System.setProperty("launcherLogFormatter.startLogMsgLabel", "begin-label");
        LauncherLogFormatter formatter = new LauncherJsonLogFormatter();

        String requestPath = "nablarch.hoge.HogeAction/RBHOGEHOGE";
        String userId = "testUser";
        String diConfig = "test.xml";

        CommandLine commandLine = new CommandLine(
                "-diConfig", diConfig,
                "-userId", userId,
                "-requestPath", requestPath
        );

        String message = formatter.getStartLogMsg(commandLine);
        assertThat(message.startsWith("$JSON$"), is(true));
        assertThat(message.substring("$JSON$".length()), isJson(allOf(
                withJsonPath("$.*", hasSize(1)),
                withJsonPath("$", hasEntry("label", "begin-label"))
        )));
    }

    /**
     * {@link LauncherJsonLogFormatter#getStartLogMsg(CommandLine)}のテスト。
     * 不正なターゲットがあった場合はエラーになること。
     */
    @Test
    public void testGetStartLogMsgWithIllegalTargets() {
        System.setProperty("launcherLogFormatter.startTargets", "commandLineOptions ,, ,dummy,commandLineOptions");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
            @Override
            public void run() {
                new LauncherJsonLogFormatter();
            }
        });

        assertThat(exception.getMessage(), is("[dummy] is unknown target. property name = [launcherLogFormatter.startTargets]"));
    }

    /**
     * {@link LauncherJsonLogFormatter#getEndLogMsg(int, long)}のテスト。
     * デフォルトの出力項目で正しくフォーマットされること。
     */
    @Test
    public void testGetEndLogMsg() {

        LauncherLogFormatter formatter = new LauncherJsonLogFormatter();

        String message = formatter.getEndLogMsg(0, 100);
        assertThat(message.startsWith("$JSON$"), is(true));
        assertThat(message.substring("$JSON$".length()), isJson(allOf(
                withJsonPath("$", hasEntry("label", "BATCH END")),
                withJsonPath("$", hasEntry("exitCode", 0)),
                withJsonPath("$", hasEntry("executeTime", 100)))));

        message = formatter.getEndLogMsg(101, 100000000);
        assertThat(message.startsWith("$JSON$"), is(true));
        assertThat(message.substring("$JSON$".length()), isJson(allOf(
                withJsonPath("$", hasEntry("label", "BATCH END")),
                withJsonPath("$", hasEntry("exitCode", 101)),
                withJsonPath("$", hasEntry("executeTime", 100000000)))));

        message = formatter.getEndLogMsg(1, Long.MAX_VALUE);
        assertThat(message.startsWith("$JSON$"), is(true));
        assertThat(message.substring("$JSON$".length()), isJson(allOf(
                withJsonPath("$", hasEntry("label", "BATCH END")),
                withJsonPath("$", hasEntry("exitCode", 1)),
                withJsonPath("$", hasEntry("executeTime", Long.MAX_VALUE)))));
    }

    /**
     * {@link LauncherJsonLogFormatter#getEndLogMsg(int, long)}のテスト。
     * targets を指定した場合。
     */
    @Test
    public void testGetEndLogMsgWithTargets() {
        System.setProperty("launcherLogFormatter.endTargets", "exitCode");
        LauncherLogFormatter formatter = new LauncherJsonLogFormatter();

        String message = formatter.getEndLogMsg(0, 100);
        assertThat(message.startsWith("$JSON$"), is(true));
        assertThat(message.substring("$JSON$".length()), isJson(allOf(
            withJsonPath("$.*", hasSize(1)),
            withJsonPath("$", hasEntry("exitCode", 0))
        )));
    }

    /**
     * {@link LauncherJsonLogFormatter#getEndLogMsg(int, long)}のテスト。
     * labelの値を指定した場合。
     */
    @Test
    public void testGetEndLogMsgWithLabelValue() {
        System.setProperty("launcherLogFormatter.endTargets", "label");
        System.setProperty("launcherLogFormatter.endLogMsgLabel", "end-label");
        LauncherLogFormatter formatter = new LauncherJsonLogFormatter();

        String message = formatter.getEndLogMsg(0, 100);
        assertThat(message.startsWith("$JSON$"), is(true));
        assertThat(message.substring("$JSON$".length()), isJson(allOf(
                withJsonPath("$.*", hasSize(1)),
                withJsonPath("$", hasEntry("label", "end-label"))
        )));
    }

    /**
     * {@link LauncherJsonLogFormatter#getEndLogMsg(int, long)}のテスト。
     * 不正なターゲットがあった場合はエラーになること。
     */
    @Test
    public void testGetEndLogMsgWithIllegalTargets() {
        System.setProperty("launcherLogFormatter.endTargets", "exitCode ,, ,dummy,exitCode");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
            @Override
            public void run() {
                new LauncherJsonLogFormatter();
            }
        });

        assertThat(exception.getMessage(), is("[dummy] is unknown target. property name = [launcherLogFormatter.endTargets]"));
    }

    /**
     * structuredMessagePrefix を指定できることのテスト。
     */
    @Test
    public void testStructuredMessagePrefix() {
        System.setProperty("launcherLogFormatter.structuredMessagePrefix", "@JSON@");

        String requestPath = "nablarch.hoge.HogeAction/RBHOGEHOGE";
        String userId = "testUser";
        String diConfig = "test.xml";
        CommandLine commandLine = new CommandLine(
                "-diConfig", diConfig,
                "-userId", userId,
                "-requestPath", requestPath
        );

        LauncherJsonLogFormatter sut = new LauncherJsonLogFormatter();

        assertThat(sut.getStartLogMsg(commandLine), startsWith("@JSON@"));
        assertThat(sut.getEndLogMsg(10, 20), startsWith("@JSON@"));
    }

    /**
     * {@link nablarch.core.text.json.JsonSerializationManager}を変更できることをテスト。
     */
    @Test
    public void testChangeJsonSerializationManager() {
        System.setProperty("launcherLogFormatter.endTargets", "exitCode");

        LauncherJsonLogFormatter sut = new LauncherJsonLogFormatter() {
            @Override
            protected JsonSerializationManager createSerializationManager(JsonSerializationSettings settings) {
                assertThat(settings.getProp("endTargets"), is("exitCode"));
                return new MockJsonSerializationManager();
            }
        };

        String requestPath = "nablarch.hoge.HogeAction/RBHOGEHOGE";
        String userId = "testUser";
        String diConfig = "test.xml";
        CommandLine commandLine = new CommandLine(
                "-diConfig", diConfig,
                "-userId", userId,
                "-requestPath", requestPath
        );

        assertThat(sut.getStartLogMsg(commandLine), is("$JSON$mock serialization"));
        assertThat(sut.getEndLogMsg(10, 20), is("$JSON$mock serialization"));
    }

    /**
     * {@link nablarch.core.text.json.JsonSerializationManager}のモッククラス。
     */
    private static class MockJsonSerializationManager extends BasicJsonSerializationManager {
        @Override
        public JsonSerializer getSerializer(Object value) {
            return new JsonSerializer() {

                @Override
                public void serialize(Writer writer, Object value) throws IOException {
                    writer.write("mock serialization");
                }

                @Override
                public void initialize(JsonSerializationSettings settings) {
                }

                @Override
                public boolean isTarget(Class<?> valueClass) {
                    return false;
                }
            };
        }
    }
}
