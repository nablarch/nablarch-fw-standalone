package nablarch.fw.launcher;

import nablarch.common.handler.DbConnectionManagementHandler;
import nablarch.core.log.LogUtil;
import nablarch.core.repository.SystemRepository;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.StandaloneExecutionContext;
import nablarch.fw.handler.*;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.log.app.OnMemoryLogWriter;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

/**
 * {@link Main}のテストクラス。
 *
 * @author hisaaki sioiri
 */
@RunWith(DatabaseTestRunner.class)
public class MainTest {

    @BeforeClass
    public static void beforeClass() throws Exception {
        System.clearProperty("nablarch.appLog.filePath");
        LogUtil.removeAllObjectsBoundToContextClassLoader();
    }

    @Before
    public void setUp() throws Exception {
        SystemRepository.clear();
        OnMemoryLogWriter.clear();
    }

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
            future.get(20, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    public void 業務日付コンポーネントが設定されている場合は業務日付有りの設定ログが出力される() {
        CommandLine commandLine = new CommandLine(
                "-diConfig", "nablarch/fw/launcher/main.xml",
                "-requestPath",
                "nablarch.fw.launcher.testaction.NormalEndAction/RS100",
                "-userId", "hoge"
        );

        Main.execute(commandLine);

        OnMemoryLogWriter.assertLogContains("writer.appLog", "@@@ app log with date @@@");
    }

    @Test
    public void 業務日付コンポーネントが設定されていない場合は業務日付無しの設定ログが出力される() {
        CommandLine commandLine = new CommandLine(
                "-diConfig", "nablarch/fw/launcher/main-without-businessdate.xml",
                "-requestPath",
                "nablarch.fw.launcher.testaction.NormalEndAction/RS100",
                "-userId", "hoge"
        );

        Main.execute(commandLine);

        OnMemoryLogWriter.assertLogContains("writer.appLog", "@@@ app log @@@");
    }

    @Test
    public void リポジトリの初期化に失敗した場合障害通知ログが出力される() throws Exception {
        final Main sut = new Main();
        final CommandLine commandLine = new CommandLine("--diConfig", "nablarch/fw/launcher/invalidComponent.xml", "--requestPath", "dummy", "--userId", "dummy");
        sut.handle(commandLine,  new ExecutionContext());
        
        OnMemoryLogWriter.assertLogContains("writer.monitorLog", "fail_code = [MSG99999] an unexpected exception occurred.");
        OnMemoryLogWriter.assertLogContains("writer.appLog", "fail_code = [MSG99999] an unexpected exception occurred.");
    }

    @Test
    public void Main処理中に予期せぬエラーが発生した場合は障害通知ログが出力される() throws Exception {

        final Main sut = new Main();
        final CommandLine commandLine = new CommandLine("--diConfig", "nablarch/fw/launcher/errorHandler.xml", "--requestPath", "dummy", "--userId", "dummy");
        sut.handle(commandLine,  new ExecutionContext());

        OnMemoryLogWriter.assertLogContains("writer.monitorLog", "fail_code = [MSG99999] an unexpected exception occurred.");
        OnMemoryLogWriter.assertLogContains("writer.appLog", "fail_code = [MSG99999] an unexpected exception occurred.");
    }
    
    @Test
    public void Main処理中にResultErrorが発生した場合は障害通知ログが出力される() throws Exception {

        final Main sut = new Main();
        final CommandLine commandLine = new CommandLine("--diConfig", "nablarch/fw/launcher/resultErrorHandler.xml", "--requestPath", "dummy", "--userId", "dummy");
        sut.handle(commandLine,  new ExecutionContext());

        OnMemoryLogWriter.assertLogContains("writer.monitorLog", "fail_code = [MSG99999] an unexpected exception occurred.");
        OnMemoryLogWriter.assertLogContains("writer.appLog", "fail_code = [MSG99999] an unexpected exception occurred.");
    }

    /**
     * 正常終了時に廃棄処理が呼ばれること。
     */
    @Test
    public void testApplicationDisposerIsInvokedAtNormalEnd() {
        CommandLine commandLine = new CommandLine(
                "-diConfig", "nablarch/fw/launcher/disposerTest.xml",
                "-requestPath",
                "nablarch.fw.launcher.testaction.NormalEndAction/RS100",
                "-userId", "hoge"
        );

        Main.execute(commandLine);

        MockDisposable disposable1 = SystemRepository.get("disposable1");
        MockDisposable disposable2 = SystemRepository.get("disposable2");
        MockDisposable disposable3 = SystemRepository.get("disposable3");

        assertThat(disposable1.isDisposed(), is(true));
        assertThat(disposable2.isDisposed(), is(true));
        assertThat(disposable3.isDisposed(), is(true));
    }

    /**
     * 異常終了時も廃棄処理が呼ばれること。
     */
    @Test
    public void testApplicationDisposerIsInvokedAtErrorCase() {
        CommandLine commandLine = new CommandLine(
                "-diConfig", "nablarch/fw/launcher/disposerTestErrorCase.xml",
                "-requestPath", "dummy",
                "-userId", "dummy"
        );

        Main.execute(commandLine);

        MockDisposable disposable1 = SystemRepository.get("disposable1");
        MockDisposable disposable2 = SystemRepository.get("disposable2");
        MockDisposable disposable3 = SystemRepository.get("disposable3");

        assertThat(disposable1.isDisposed(), is(true));
        assertThat(disposable2.isDisposed(), is(true));
        assertThat(disposable3.isDisposed(), is(true));
    }

    /**
     * {@link nablarch.core.repository.disposal.ApplicationDisposer}のコンポーネントが存在するときだけ廃棄処理が呼ばれること。
     */
    @Test
    public void testApplicationDisposerIsInvokedOnlyExistsComponent() {
        CommandLine commandLine = new CommandLine(
                "-diConfig", "nablarch/fw/launcher/disposerTestNotExists.xml",
                "-requestPath", "dummy",
                "-userId", "dummy"
        );

        Main.execute(commandLine);

        MockDisposable disposable1 = SystemRepository.get("disposable1");
        MockDisposable disposable2 = SystemRepository.get("disposable2");
        MockDisposable disposable3 = SystemRepository.get("disposable3");

        assertThat(disposable1.isDisposed(), is(false));
        assertThat(disposable2.isDisposed(), is(false));
        assertThat(disposable3.isDisposed(), is(false));
    }

    /**
     * {@link nablarch.fw.handler.StatusCodeConvertHandler} がハンドラキューに
     * 設定されておらず {@link Result} がそのまま返されたときも戻り値が正常に処理されることのテスト（正常終了時）。
     */
    @Test
    public void testWithoutStatusCodeConvertHandlerInSuccessCase() {
        CommandLine commandLine = new CommandLine(
                "-diConfig", "nablarch/fw/launcher/testWithoutStatusCodeConvertHandler.xml",
                "-requestPath",
                "nablarch.fw.launcher.testaction.NormalEndAction/RS100",
                "-userId", "hoge"
        );

        int exitCode = Main.execute(commandLine);
        assertThat("正常終了なので戻り値は0となる。", exitCode, is(0));
    }

    /**
     * {@link Result} 以外の値が返された場合は正常終了扱いにする。
     */
    @Test
    public void testReturnNonResult() {
        CommandLine commandLine = new CommandLine(
                "-diConfig", "nablarch/fw/launcher/testReturnNonResult.xml",
                "-requestPath",
                "nablarch.fw.launcher.testaction.ReturnNonResultAction/RS100",
                "-userId", "hoge"
        );

        int exitCode = Main.execute(commandLine);
        assertThat("正常終了扱いなので0を返す。", exitCode, is(0));
    }

    /**
     * diConfig パラメータに空文字が設定された場合は異常終了とする。
     */
    @Test
    public void testErrorEndIfDiConfigParameterIsNotSet() {
        CommandLine commandLine = new CommandLine(
                "-diConfig", "",
                "-requestPath",
                "nablarch.fw.launcher.testaction.NormalEndAction/RS100",
                "-userId", "hoge"
        );

        int exitCode = Main.execute(commandLine);
        assertThat("不明なエラー扱いとなる", exitCode, is(127));
    }

    /**
     * ロードされたハンドラキューがgetHandlerQueue()で取得できること。
     */
    @Test
    public void testGetHandlerQueueReturnsLoadedHandlerQueue() {
        CommandLine commandLine = new CommandLine(
                "-diConfig", "nablarch/fw/launcher/main.xml",
                "-requestPath",
                "nablarch.fw.launcher.testaction.NormalEndAction/RS100",
                "-userId", "hoge"
        );

        Main sut = new Main();

        sut.handle(commandLine, new StandaloneExecutionContext());

        assertThat(sut.getHandlerQueue(), contains(
            instanceOf(StatusCodeConvertHandler.class),
            instanceOf(GlobalErrorHandler.class),
            instanceOf(RequestPathJavaPackageMapping.class),
            instanceOf(MultiThreadExecutionHandler.class),
            instanceOf(DbConnectionManagementHandler.class),
            instanceOf(LoopHandler.class),
            instanceOf(DataReadHandler.class)
        ));
    }

    public static class ErrorHandler implements Handler<Object, Object> {

        @Override
        public Object handle(final Object o, final ExecutionContext context) {
            throw new Error("error");
        }
    }

    public static class ResultErrorHandler implements Handler<Object, Object> {

        @Override
        public Object handle(final Object o, final ExecutionContext context) {
            throw new Result.Error("error") {};
        }
    }
}
