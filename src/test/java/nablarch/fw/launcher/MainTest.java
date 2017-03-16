package nablarch.fw.launcher;

import nablarch.test.support.db.helper.DatabaseTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * {@link Main}のテストクラス。
 *
 * @author hisaaki sioiri
 */
@RunWith(DatabaseTestRunner.class)
public class MainTest {

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が正常終了する場合のテスト
     */
    @Test
    public void testExecuteNormalEnd() {
        Main main = new Main();

        CommandLine commandLine = new CommandLine(
                "-diConfig", "nablarch/fw/launcher/main.xml",
                "-requestPath",
                "nablarch.fw.launcher.testaction.NormalEndAction/RS100",
                "-userId", "hoge"
        );

        int execute = Main.execute(commandLine);
        assertThat("正常終了なので戻り値は0となる。", execute, is(0));

    }

    /**
     * {@link Main#execute(CommandLine)}のテスト。
     * <p/>
     * 処理が異常終了する場合
     */
    @Test
    public void testExecuteAbnormalEnd() {
        Main main = new Main();

        CommandLine commandLine = new CommandLine(
                "-diConfig", "nablarch/fw/launcher/main.xml",
                "-requestPath",
                "nablarch.fw.launcher.testaction.AbnormalEndAction/RS100",
                "-userId", "hoge"
        );

        int exitCode = Main.execute(commandLine);
        assertThat("アプリケーションで指定した100が終了コードとなること。",
                exitCode, is(100));
        commandLine = new CommandLine(
                "-diConfig", "nablarch/fw/launcher/main.xml",
                "-requestPath",
                "nablarch.fw.launcher.testaction.AbnormalEndAction2/RS100",
                "-userId", "hoge"
        );

        exitCode = Main.execute(commandLine);
        assertThat("java.lang.RuntimeExceptionが送出されるので20となること。",
                exitCode, is(20));
    }

    @Test
    public void testMultithread() throws Exception {
        final CommandLine commandLine = new CommandLine(
                "-diConfig", "nablarch/fw/launcher/main-multithread.xml",
                "-requestPath", "nablarch.fw.launcher.testaction.MultithreadAction/RS100",
                "-userId", "hoge"
        );
        final ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            Future<Object> future = executorService.submit(
                    new Runnable() {
                        @Override
                        public void run() {
                            int exitCode = Main.execute(commandLine);
                        }
                    }, null
            );
            future.get(10, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
    }
}
