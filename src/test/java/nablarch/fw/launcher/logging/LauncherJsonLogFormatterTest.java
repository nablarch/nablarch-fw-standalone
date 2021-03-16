package nablarch.fw.launcher.logging;

import nablarch.core.log.LogTestSupport;
import nablarch.fw.launcher.CommandLine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;

/**
 * {@link LauncherJsonLogFormatter}のテストクラス。
 *
 * @author Shuji Kitamura
 */
public class LauncherJsonLogFormatterTest extends LogTestSupport {

    @Before
    public void setup() {
        System.clearProperty("performanceLogFormatter.targets");
    }

    @After
    public void teardown() {
        System.clearProperty("performanceLogFormatter.targets");
    }

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
        assertThat(message.substring(6), isJson(allOf(
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
        assertThat(message.substring(6), isJson(allOf(
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
     * 不正なターゲットのテスト。
     */
    @Test
    public void testGetStartLogMsgWithIllegalTargets() {
        System.setProperty("launcherLogFormatter.startTargets", "commandLineOptions ,, ,dummy,commandLineOptions");

        LauncherLogFormatter formatter = new LauncherJsonLogFormatter();

        String requestPath = "nablarch.hoge.HogeAction/RBHOGEHOGE";
        String userId = "testUser";
        String diConfig = "test.xml";

        CommandLine commandLine = new CommandLine(
                "-diConfig", diConfig,
                "-userId", userId,
                "-requestPath", requestPath,
                "h", "o", "g", "e", "hoge"
        );

        String message = formatter.getStartLogMsg(commandLine);

        assertThat(message.startsWith("$JSON$"), is(true));
        assertThat(message.substring(6), isJson(allOf(
                withJsonPath("$.commandLineOptions", hasEntry("diConfig", "test.xml")),
                withJsonPath("$.commandLineOptions", hasEntry("userId", "testUser")),
                withJsonPath("$.commandLineOptions", hasEntry("requestPath", "nablarch.hoge.HogeAction/RBHOGEHOGE")),
                withoutJsonPath("$.commandLineArguments"))));
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
        assertThat(message.substring(6), isJson(allOf(
                withJsonPath("$", hasEntry("exitCode", 0)),
                withJsonPath("$", hasEntry("executeTime", 100)))));

        message = formatter.getEndLogMsg(101, 100000000);
        assertThat(message.startsWith("$JSON$"), is(true));
        assertThat(message.substring(6), isJson(allOf(
                withJsonPath("$", hasEntry("exitCode", 101)),
                withJsonPath("$", hasEntry("executeTime", 100000000)))));

        message = formatter.getEndLogMsg(1, Long.MAX_VALUE);
        assertThat(message.startsWith("$JSON$"), is(true));
        assertThat(message.substring(6), isJson(allOf(
                withJsonPath("$", hasEntry("exitCode", 1)),
                withJsonPath("$", hasEntry("executeTime", Long.MAX_VALUE)))));
    }

    /**
     * {@link LauncherJsonLogFormatter#getEndLogMsg(int, long)}のテスト。
     * 不正なターゲットのテスト。
     */
    @Test
    public void testGetEndLogMsgWithIllegalTargets() {
        System.setProperty("launcherLogFormatter.endTargets", "exitCode ,, ,dummy,exitCode");

        LauncherLogFormatter formatter = new LauncherJsonLogFormatter();

        String message = formatter.getEndLogMsg(0, 100);
        assertThat(message.startsWith("$JSON$"), is(true));
        assertThat(message.substring(6), isJson(allOf(
                withJsonPath("$", hasEntry("exitCode", 0)),
                withoutJsonPath("$.executeTime"))));
    }

}
