package nablarch.fw.launcher.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nablarch.core.date.BusinessDateProvider;
import nablarch.core.log.LogItem;
import nablarch.core.log.LogTestSupport;
import nablarch.core.repository.ObjectLoader;
import nablarch.core.repository.SystemRepository;
import nablarch.fw.launcher.CommandLine;

import nablarch.fw.launcher.logging.LauncherLogFormatter;
import nablarch.fw.launcher.logging.LauncherLogUtil;

import nablarch.fw.mock.MockBusinessDateProvider;
import org.hamcrest.text.IsEqualIgnoringWhiteSpace;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.text.IsEqualIgnoringWhiteSpace.equalToIgnoringWhiteSpace;
import static org.junit.Assert.assertThat;

/**
 * {@link LauncherLogUtil}のテストクラス。
 *
 * @author hisaaki sioiri
 */
public class LauncherLogUtilTest extends LogTestSupport {


    /** テスト用のセットアップ処理 */
    @BeforeClass
    public static void setUpClass() {
        SystemRepository.load(new ObjectLoader() {
            public Map<String, Object> load() {
                Map<String, Object> loadData = new HashMap<String, Object>();
                loadData.put("businessDateProvider", new MockBusinessDateProvider());
                loadData.put("file.encoding", "utf-8");
                loadData.put("threadCount", "1000");
                loadData.put("key1", "name1");
                loadData.put("key2", "name2");
                loadData.put("key3", "name3");
                return loadData;
            }
        });
    }

    private void init() {
        System.setProperty("nablarch.appLog.filePath",
                "classpath:nablarch/fw/launcher/logging/empty-app-log.properties");
    }

    @AfterClass
    public static void tearDownClass() {
        SystemRepository.clear();
    }

    private static final Pattern BEGIN_LOG_PATTERN = Pattern.compile(
            "(@@@@ BEGIN @@@@)(\r\n|\r|\n)"
                    + "(\tcommand line options = \\{)(\r\n|\r|\n)"
                    + "((\t\t[^ ]+ = \\[.*\\](\r\n|\r|\n))*)"
                    + "\t}(\r\n|\r|\n)"
                    + "(\tcommand line arguments = \\{)(\r\n|\r|\n)"
                    + "((\t\t[0-9]+ = \\[.*\\](\r\n|\r|\n))*)"
                    + "\t}");

    private static final Pattern APP_LOG_PATTERN = Pattern.compile(
            "(@@@@ APPLICATION SETTINGS @@@@)(\r\n|\r|\n)"
                    + "(\tsystem settings = \\{)(\r\n|\r|\n)"
                    + "((\t\t[^ ]+ = \\[.*\\](\r\n|\r|\n))*)"
                    + "\t}(\r\n|\r|\n)"
                    + "(\tbusinessDate = \\[[0-9]+\\])");


    /**
     * {@link LauncherLogUtil#getStartLogMsg(nablarch.fw.launcher.CommandLine)}のテスト。
     * <p/>
     * デフォルト設定でのケースを実施する。
     */
    @Test
    public void getStartLog() {
        init();

        String requestPath = "nablarch.hoge.HogeAction/RBHOGEHOGE";
        String userId = "testUser";
        String diConfig = "test.xml";

        CommandLine commandLine = new CommandLine(
                "-diConfig", diConfig,
                "-userId", userId,
                "-requestPath", requestPath,
                "-param1", "param1",
                "-param2", "param2",
                "h", "o", "g", "e", "hoge",
                "-param3", "param3",
                "fuga"
        );

        String log = LauncherLogUtil.getStartLogMsg(commandLine);

        System.out.println(
                "==================== actual log ====================\n" + log);

        // コマンドラインオプション
        Matcher matcher = BEGIN_LOG_PATTERN.matcher(log);
        assertThat(matcher.find(), is(true));
        assertThat(matcher.group(1), is("@@@@ BEGIN @@@@"));
        assertThat(matcher.group(3), is("\tcommand line options = {"));

        String commandLineOpts = matcher.group(5);
        assertThat(commandLineOpts, allOf(
                containsString("\t\tdiConfig = [" + diConfig + "]"),
                containsString("\t\tuserId = [" + userId + "]"),
                containsString("\t\trequestPath = [" + requestPath + "]"),
                containsString("\t\tparam1 = [param1]"),
                containsString("\t\tparam2 = [param2]"),
                containsString("\t\tparam3 = [param3]")));

        // コマンドライン引数
        assertThat(matcher.group(9), containsString(
                "\tcommand line arguments = {"));

        String commandLinArgs = matcher.group(11);
        assertThat(commandLinArgs, containsString("\t\t01 = [h]"));
        assertThat(commandLinArgs, containsString("\t\t02 = [o]"));
        assertThat(commandLinArgs, containsString("\t\t03 = [g]"));
        assertThat(commandLinArgs, containsString("\t\t04 = [e]"));
        assertThat(commandLinArgs, containsString("\t\t05 = [hoge]"));
        assertThat(commandLinArgs, containsString("\t\t06 = [fuga]"));
    }

    /**
     * {@link LauncherLogUtil#getStartLogMsg(nablarch.fw.launcher.CommandLine)}のテスト。
     * <p/>
     * フォーマットをデフォルト設定から上書きした場合のテスト。
     */
    @Test
    public void getStartLogOverrideFormat() {
        init();

        System.setProperty("launcherLogFormatter.startFormat",
                "@@@@ BEGIN @@@@\\n\\tcommand line arguments = [$commandLineArguments$]");

        String requestPath = "nablarch.hoge.HogeAction/RBHOGEHOGE";
        String userId = "testUser";
        String diConfig = "test.xml";

        CommandLine commandLine = new CommandLine(
                "-diConfig", diConfig,
                "-userId", userId,
                "-requestPath", requestPath,
                "-param1", "param1",
                "-param2", "param2",
                "h", "o", "g", "e",
                "-param3", "param3"
        );

        String log = LauncherLogUtil.getStartLogMsg(commandLine);
        System.out.println(
                "==================== actual log ====================\n" + log);

        assertThat(log, equalToIgnoringWhiteSpace(
                "@@@@ BEGIN @@@@\n\t" + "command line arguments"
                        + " = [\n\t\t01 = [h]\n\t\t02 = [o]\n\t\t03 = [g]\n\t\t04 = [e]]"));
    }

    /** {@link LauncherLogUtil#getEndLogMsg(int, long)}のテスト。 */
    @Test
    public void testGetEndLogMsg() {
        init();
        assertThat(LauncherLogUtil.getEndLogMsg(0, 100), is(
                "@@@@ END @@@@ exit code = [0] execute time(ms) = [100]"));
        assertThat(LauncherLogUtil.getEndLogMsg(101, 100000000), is(
                "@@@@ END @@@@ exit code = [101] execute time(ms) = [100000000]"));
        assertThat(LauncherLogUtil.getEndLogMsg(1, Long.MAX_VALUE), is(
                "@@@@ END @@@@ exit code = [1] execute time(ms) = ["
                        + Long.MAX_VALUE + "]"));
    }

    /**
     * {@link LauncherLogUtil#getEndLogMsg(int, long)}のテスト。
     * <p/>
     * フォーマットをデフォルト設定から変更した場合のテスト
     */
    @Test
    public void testGetEndLogMsgOverrideFormat() {
        init();
        System.setProperty("launcherLogFormatter.endFormat",
                "@@@@ END @@@@ execute time(ms) = [$executeTime$], exit code = [$exitCode$]");

        assertThat(LauncherLogUtil.getEndLogMsg(0, 100), is(
                "@@@@ END @@@@ execute time(ms) = [100], exit code = [0]"));
        assertThat(LauncherLogUtil.getEndLogMsg(101, 100000000), is(
                "@@@@ END @@@@ execute time(ms) = [100000000], exit code = [101]"));
        assertThat(LauncherLogUtil.getEndLogMsg(1, Long.MAX_VALUE), is(
                "@@@@ END @@@@ execute time(ms) = [" + Long.MAX_VALUE
                        + "], exit code = [1]"));
    }

    /**
     * {@link LauncherLogFormatter}を拡張した場合のテスト。
     * <p/>
     * 拡張したクラスのフォーマット、{@link LogItem}定義を使用してログフォーマットが行われていることを確認する。
     */
    @Test
    public void testOverrideFormatClass() {
        init();

        // フォーマットクラスを変更
        System.setProperty("launcherLogFormatter.className",
                "nablarch.fw.launcher.logging.LauncherLogUtilTest$TestFormatter");

        String requestPath = "nablarch.hoge.HogeAction/RBHOGEHOGE";
        String userId = "testUser";
        String diConfig = "test.xml";
        CommandLine commandLine = new CommandLine(
                "-diConfig", diConfig,
                "-userId", userId,
                "-requestPath", requestPath,
                "-param1", "param1",
                "-param2", "param2",
                "h", "o", "g", "e",
                "-param3", "param3"
        );

        assertThat(LauncherLogUtil.getStartLogMsg(commandLine), is(
                "@@@@ begin @@@@ start = start!!!"));
        assertThat(LauncherLogUtil.getEndLogMsg(100, 200), is(
                "@@@@ end @@@@ end = end!!!"));
    }

    public static class TestFormatter extends LauncherLogFormatter {

        @Override
        protected Map<String, LogItem<LauncherLogContext>> getStartLogItems() {
            HashMap<String, LogItem<LauncherLogContext>> logItem = new HashMap<String, LogItem<LauncherLogContext>>();
            logItem.put("$start$", new LogItem<LauncherLogContext>() {
                public String get(LauncherLogContext context) {
                    return "start!!!";
                }
            });
            return logItem;
        }

        @Override
        protected String getStartLogFormat() {
            return "@@@@ begin @@@@ start = $start$";
        }

        @Override
        protected Map<String, LogItem<LauncherLogContext>> getEndLogItems() {
            HashMap<String, LogItem<LauncherLogContext>> logItem = new HashMap<String, LogItem<LauncherLogContext>>();
            logItem.put("$end$", new LogItem<LauncherLogContext>() {
                public String get(LauncherLogContext context) {
                    return "end!!!";
                }
            });
            return logItem;
        }

        @Override
        protected String getEndLogFormat() {
            return "@@@@ end @@@@ end = $end$";
        }
    }
}
