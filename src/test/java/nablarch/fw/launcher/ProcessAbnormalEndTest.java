package nablarch.fw.launcher;

import nablarch.core.ThreadContext;
import nablarch.core.log.LogUtil;
import nablarch.core.log.Logger;

import nablarch.core.message.MockStringResourceHolder;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.log.app.OnMemoryLogWriter;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * {@link ProcessAbnormalEnd}のテストクラス。
 *
 * @author hisaaki sioiri
 */
public class ProcessAbnormalEndTest {


    /** {@link ProcessAbnormalEnd#ProcessAbnormalEnd(int, String, Object...)}のテスト */
    @Test
    public void testConstructor1() {
        ProcessAbnormalEnd exception = new ProcessAbnormalEnd(100, "msgid", "option");

        assertThat("終了コードは、100であること", exception.getStatusCode(), is(100));
        assertThat("メッセージの確認", exception.getMessage(),
                containsString("An error happened with messageId = [msgid]"));
        assertThat("メッセージID", exception.getMessageId(), is("msgid"));
        assertThat("メッセージオプション", exception.getMessageParams(), is(
                new Object[]{"option"}));
    }

    /** {@link ProcessAbnormalEnd#ProcessAbnormalEnd(int, Throwable, String, Object...)}のテスト。 */
    @Test
    public void testConstructor2() {

        NullPointerException nullPon = new NullPointerException();

        ProcessAbnormalEnd exception = new ProcessAbnormalEnd(
                111, nullPon, "msgid",
                "option1", "option2", "option3"
        );

        assertThat("終了コードは、111", exception.getStatusCode(), is(111));
        assertThat("メッセージID", exception.getMessageId(), is("msgid"));
        assertThat("メッセージオプション", exception.getMessageParams(), is(
                new Object[]{"option1", "option2", "option3"}));

        assertThat("メッセージの確認", exception.getMessage(),
                containsString("An error happened with messageId = [msgid]"));
        assertThat("原因例外はNullPointerExceptionであること。",
                exception.getCause(),
                is((Throwable) nullPon));

    }

    /** 終了コードの設定テスト。 */
    @Test
    public void testInvalidExitCode() {

        try {
            new ProcessAbnormalEnd(99, "");
            fail("does not run.");
        } catch (Exception e) {
            System.out.println("e.getMessage() = " + e.getMessage());
            assertThat("99は範囲外のためエラー", e.getMessage(), containsString(
                    "Exit code was invalid range. Please set it in the range of 199 from 100."));
        }

        assertThat(new ProcessAbnormalEnd(100, "").getStatusCode(), is(100));
    }

    @ClassRule
    public static SystemRepositoryResource repositoryResource = new SystemRepositoryResource(
            "nablarch/fw/launcher/message-resource-initialload-test.xml");

    /**
     * デフォルト設定でログが出力されること。
     */
    @Test
    public void testDefaultLog() {
        String[][] MESSAGES = {
                { "FW000001", "ja", "FW000001メッセージ{0}", "en","FW000001Message{0}"},
                { "FW000002", "ja", "FW000002メッセージ{0}", "en","FW000002Message{0}" },
        };
        repositoryResource.getComponentByType(MockStringResourceHolder.class).setMessages(MESSAGES);

        OnMemoryLogWriter.clear();
        LogUtil.removeAllObjectsBoundToContextClassLoader();

        System.setProperty("nablarch.appLog.filePath", "classpath:nablarch/core/log/app/app-log-default.properties");
        ThreadContext.setLanguage(Locale.JAPANESE);

        new ProcessAbnormalEnd(100, "FW000001", new Object[] {"fatal_ex_msg_short_messageOption"}).writeLog(null);
        new ProcessAbnormalEnd(100, "FW000002", new Object[] {"fatal_ex_msg_full_messageOption"}).writeLog(null);

        List<String> monitorFile = OnMemoryLogWriter.getMessages("writer.monitorLog");
        List<String> appFile = OnMemoryLogWriter.getMessages("writer.appLog");

        // fatal_ex_msg_short
        assertThat(monitorFile.get(0), is("FATAL fail_code = [FW000001] FW000001メッセージfatal_ex_msg_short_messageOption" + Logger.LS));
        assertThat(appFile.get(0), containsString("FATAL ROOT fail_code = [FW000001] FW000001メッセージfatal_ex_msg_short_messageOption"));

        // fatal_ex_msg_full
        assertThat(monitorFile.get(1), is("FATAL fail_code = [FW000002] FW000002メッセージfatal_ex_msg_full_messageOption" + Logger.LS));
        assertThat(appFile.get(1), containsString("FATAL ROOT fail_code = [FW000002] FW000002メッセージfatal_ex_msg_full_messageOption"));
    }
}
