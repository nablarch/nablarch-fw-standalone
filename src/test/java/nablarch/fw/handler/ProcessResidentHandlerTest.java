package nablarch.fw.handler;

import nablarch.core.ThreadContext;
import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlResultSet;
import nablarch.core.db.transaction.SimpleDbTransactionExecutor;
import nablarch.core.db.transaction.SimpleDbTransactionManager;

import nablarch.core.log.basic.LogWriterSupport;
import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.initialization.Initializable;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.Result.Success;
import nablarch.fw.handler.retry.CountingRetryContextFactory;
import nablarch.fw.handler.retry.RetryUtil;
import nablarch.fw.handler.retry.RetryableException;
import nablarch.fw.launcher.ProcessAbnormalEnd;
import nablarch.fw.results.TransactionAbnormalEnd;

import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import nablarch.test.support.log.app.OnMemoryLogWriter;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.channels.IllegalSelectorException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * {@link ProcessResidentHandler}のテストクラス。
 *
 * @author hisaaki sioiri
 */
@RunWith(DatabaseTestRunner.class)
public class ProcessResidentHandlerTest {

    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource(
            "nablarch/fw/handler/ProcessResidentHandlerTest.xml");

    /** セットアップ処理。 */
    @BeforeClass
    public static void setUpClass() {
        VariousDbTestHelper.createTable(HandlerBatchRequest.class);
    }

    /** テストケース毎の事前処理。 */
    @Before
    public void setUp() {
        // テスト用テーブルのセットアップ
        VariousDbTestHelper.setUpTable(new HandlerBatchRequest("12345", "リクエスト０１", "0", "1", "1", 0L));

        // ログの初期化
        LogWriter.logClear();
        OnMemoryLogWriter.clear();
    }

    /**
     * {@link ProcessResidentHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * デフォルト設定の場合。
     * 処理停止ハンドラが、処理停止例外を送出した場合、正常に処理が終了すること。
     */
    @Test
    public void testHandle1() {
        ThreadContext.setRequestId("12345");
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();

        // デフォルト設定の常駐化ハンドラを設定
        handlers.add(
                SystemRepository.<Handler<?, ?>>get(
                        "defaultProcessResidentHandler"));

        // 処理停止チェックハンドラ
        handlers.add(createProcessStopHandler());

        // ダミーのアクションハンドラ
        // １０回呼び出されたタイミングで、処理停止フラグをonにする。
        DummyHandler dummyHandler = new DummyHandler(3);
        handlers.add(dummyHandler);

        long start = System.currentTimeMillis();
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        Object o = context.handleNext(null);
        long executeTime = System.currentTimeMillis() - start;
        assertTrue("データ監視間隔 * 処理件数(3)よりも、処理時間は大きいこと",
                executeTime >= 1000 * 3);
        assertThat("Successが返却されること。", o,
                instanceOf(Success.class));
        assertThat("アクションハンドラが10回呼び出されていることを確認",
                dummyHandler.nowCount, is(3));

        // ログのアサート
        assertThat(LogWriter.logMessages, LogMatcher.Log(
                "stop the resident process."));
        assertThat(LogWriter.logMessages, LogMatcher.Log(
                "DATA WATCH INTERVAL = [1000ms]"));

        //**********************************************************************
        // 既に処理停止フラグが立っている状態で実行した場合
        //**********************************************************************
        setUp();

        // 処理停止フラグを立てる。
        VariousDbTestHelper.setUpTable(new HandlerBatchRequest("12345", "リクエスト０１", "1", "1", "1", 0L));

        handlers.clear();
        handlers.add(
                SystemRepository.<Handler<?, ?>>get(
                        "defaultProcessResidentHandler"));

        // 処理停止チェックハンドラ
        handlers.add(createProcessStopHandler());

        // ダミーのアクションハンドラ
        // １０回呼び出されたタイミングで、処理停止フラグをonにする。
        dummyHandler = new DummyHandler(5);
        handlers.add(dummyHandler);

        context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        o = context.handleNext(null);

        assertThat("Successが返却されること。", o, instanceOf(Success.class));
        assertThat("処理停止フラグがたっているため、アクションは呼び出されないこと",
                dummyHandler.nowCount, is(0));
    }

    /**
     * {@link ProcessResidentHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * 正常に処理を終了する例外クラスを設定した場合、対象の例外が発生してもエラーとならずに正常に処理が終わること。
     */
    @Test
    public void testHandle2() {

        //**********************************************************************
        // 処理停止例外が発生した場合、正常に処理が終了すること。
        //**********************************************************************
        ThreadContext.setRequestId("12345");
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(
                SystemRepository.<Handler<?, ?>>get(
                        "settingsProcessResidentHandler"));

        handlers.add(createProcessStopHandler());
        DummyHandler dummyHandler = new DummyHandler(5);
        handlers.add(dummyHandler);

        long start = System.nanoTime();
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        Object o = context.handleNext(null);
        long executeTime = System.nanoTime() - start;

        assertTrue("データ監視間隔 * 処理件数(5)よりも、処理時間は大きいこと",
                executeTime >= TimeUnit.MICROSECONDS.toNanos(5000 * 5));
        assertThat("Successが返却されること。", o, instanceOf(Success.class));
        assertThat("アクションハンドラが5回呼び出されていることを確認",
                dummyHandler.nowCount, is(5));

        // ログのアサート
        assertThat(LogWriter.logMessages, LogMatcher.Log(
                "stop the resident process."));
        assertThat(LogWriter.logMessages, LogMatcher.Log(
                "DATA WATCH INTERVAL = [5000ms]"));

        //**********************************************************************
        // java.lang.IllegalArgumentExceptionのサブ例外が発生した場合、正常に処理が終了すること。
        //**********************************************************************
        setUp();
        handlers.clear();
        handlers.add(
                SystemRepository.<Handler<?, ?>>get(
                        "settingsProcessResidentHandler"));
        handlers.add(createProcessStopHandler());
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                throw new IllegalSelectorException();
            }
        });

        context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        o = context.handleNext(null);
        assertThat("Successが返却されること。", o,
                instanceOf(Success.class));
    }

    /**
     * {@link ProcessResidentHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * 閉局されている場合のケース
     */
    @Test
    public void testHandle3() {

        //**********************************************************************
        // 開始時閉局→開局の流れ
        //**********************************************************************
        // 閉局状態
        changeServiceStatus("0");
        ThreadContext.setRequestId("12345");
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "fastProcessResidentHandler"));

        handlers.add(createProcessStopHandler());
        handlers.add(new Handler<Object, Object>() {
            private int count;

            public Object handle(Object o, ExecutionContext context) {
                count++;
                if (count == 10) {
                    changeServiceStatus("1");
                }
                return context.handleNext(o);
            }
        });
        // 開閉局ハンドラ
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "serviceAvailabilityCheckHandler"));
        DummyHandler dummyHandler = new DummyHandler(10);
        handlers.add(dummyHandler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        Object o = context.handleNext(null);


        assertThat("閉局中ログが出力されていることを確認", LogWriter.logMessages,
                LogMatcher.Log("this process is asleep because the service temporarily unavailable.",
                        "TRACE"));
        assertThat(dummyHandler.nowCount, is(10));

        //**********************************************************************
        // 開始時開局→閉局の流れ
        //**********************************************************************
        setUp();
        changeServiceStatus("1");
        ThreadContext.setRequestId("12345");
        handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "fastProcessResidentHandler"));

        handlers.add(createProcessStopHandler());
        handlers.add(new Handler<Object, Object>() {
            private int count;

            public Object handle(Object o, ExecutionContext context) {
                count++;
                if (count == 5) {
                    // 閉局にする。
                    changeServiceStatus("0");
                }
                Object o1;
                try {
                    o1 = context.handleNext(o);
                } finally {
                    // 処理停止フラグをたてる。
                    if (count == 5) {
                        stopProcess();
                    }
                }

                return o1;
            }
        });
        // 開閉局ハンドラ
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "serviceAvailabilityCheckHandler"));
        dummyHandler = new DummyHandler(10);
        handlers.add(dummyHandler);

        context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        o = context.handleNext(null);


        assertThat("閉局中ログが出力されていることを確認", LogWriter.logMessages,
                LogMatcher.Log("this process is asleep because the service temporarily unavailable.",
                        "TRACE"));
        assertThat(dummyHandler.nowCount, is(4));
    }

    /**
     * {@link ProcessResidentHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * ハンドラから{@link nablarch.fw.launcher.ProcessAbnormalEnd}が送出される場合
     * ※本テストは、デフォルト設定にて行う。
     */
    @Test
    public void testHandle4() {
        ThreadContext.setRequestId("12345");
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "fastProcessResidentHandler"));
        handlers.add(createProcessStopHandler());
        // 開閉局ハンドラ
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "serviceAvailabilityCheckHandler"));
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                throw new ProcessAbnormalEnd(100, "aaaa");
            }
        });

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        try {
            context.handleNext(null);
            fail("does not run.");
        } catch (ProcessAbnormalEnd e) {
            assertThat(e.getMessageId(), is("aaaa"));
            assertThat(e.getStatusCode(), is(100));
        }
    }

    /**
     * {@link ProcessResidentHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * 設定ファイルに記載した例外を送出した場合異常終了すること。
     */
    @Test
    public void testHandle5() {

        //**********************************************************************
        // 設定ファイルに記載された例外を送出した場合１
        //**********************************************************************
        ThreadContext.setRequestId("12345");
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "settingsProcessResidentHandler"));
        handlers.add(createProcessStopHandler());
        // 開閉局ハンドラ
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "serviceAvailabilityCheckHandler"));
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                throw new ProcessAbnormalEnd(101, "abcde");
            }
        });

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        try {
            context.handleNext(null);
            fail("does not run.");
        } catch (ProcessAbnormalEnd e) {
            assertThat(e.getMessageId(), is("abcde"));
            assertThat(e.getStatusCode(), is(101));
        }

        //**********************************************************************
        // 設定ファイルに記載された例外を送出した場合２
        //**********************************************************************
        handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "settingsProcessResidentHandler"));
        handlers.add(createProcessStopHandler());
        // 開閉局ハンドラ
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "serviceAvailabilityCheckHandler"));
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                throw new NullPointerException();
            }
        });

        context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        try {
            context.handleNext(null);
            fail("does not run.");
        } catch (NullPointerException e) {
            assertTrue(true);
        }
    }

    /**
     * {@link ProcessResidentHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * 処理を続行する例外が発生した場合、処理が繰り返し実行されること。
     * また、障害通知ログが出力されること。
     * <p/>
     * 例外(NumberFormatException)が、５件目で発生するが処理が停止されるまで(10件目まで)
     * 処理されていることを確認する。
     */
    @Test
    public void testHandle6() {

        ThreadContext.setRequestId("12345");
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "retryHandler"));
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "fastProcessResidentHandler"));
        handlers.add(createProcessStopHandler());
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "serviceAvailabilityCheckHandler"));
        AbnormalEndHandler abnormalEndHandler = new AbnormalEndHandler(10);
        handlers.add(abnormalEndHandler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        Object o = context.handleNext(null);

        assertThat("例外は発生するが、最終的に処理は正常に終わる", o,
                instanceOf(Success.class));
        assertThat(abnormalEndHandler.currentExecCount, is(10));
        assertThat(OnMemoryLogWriter.getMessages("writer.monitorLog"),
                LogMatcher.Log(
                        "MSG", "FATAL"));
    }

    /**
     * {@link ProcessResidentHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * 処理を続行する例外が発生した場合、処理が繰り返し実行されること。
     * また、障害通知ログが出力されること。
     * <p/>
     * nablarch.fw.Result.InternalErrorが5件目で発生するが、処理が停止されるまで(20件目まで)
     * 処理がされていることを確認する。
     */
    @Test
    public void testHandle7() {
        ThreadContext.setRequestId("12345");
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "retryHandler"));
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "fastProcessResidentHandler"));
        handlers.add(createProcessStopHandler());
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "serviceAvailabilityCheckHandler"));
        AbnormalEndHandler2 abnormalEndHandler = new AbnormalEndHandler2(20);
        handlers.add(abnormalEndHandler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        Object o = context.handleNext(null);

        assertThat("例外は発生するが、最終的に処理は正常に終わる", o,
                instanceOf(Success.class));
        assertThat(abnormalEndHandler.currentExecCount, is(20));
        assertThat(OnMemoryLogWriter.getMessages("writer.monitorLog"),
                LogMatcher.Log(
                        "MSG", "FATAL"));
    }

    /**
     * {@link ProcessResidentHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * 処理を続行する例外が発生した場合、処理が繰り返し実行されること。
     * また、障害通知ログが出力されること。
     * <p/>
     * nablarch.fw.TransactionAbnormalEndが5件目で発生するが、処理が停止されるまで(30件目まで)
     * 処理がされていることを確認する。
     */
    @Test
    public void testHandle8() {
        ThreadContext.setRequestId("12345");
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "retryHandler"));
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "fastProcessResidentHandler"));
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "retryHandler"));
        handlers.add(createProcessStopHandler());
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "serviceAvailabilityCheckHandler"));
        TransactionAbnormalEndHandler transactionAbnormalEndHandler = new TransactionAbnormalEndHandler(
                30);
        handlers.add(transactionAbnormalEndHandler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        Object o = context.handleNext(null);

        assertThat("例外は発生するが、最終的に処理は正常に終わる", o,
                instanceOf(Success.class));
        assertThat(transactionAbnormalEndHandler.currentExecCount, is(30));
        assertThat(OnMemoryLogWriter.getMessages("writer.monitorLog"),
                LogMatcher.Log(
                        "MSG0001", "FATAL"));
    }

    /**
     * エラーの連続発生件数がリトライ上限を超過した場合はプロセス停止命令を送出する
     * ことのテスト。
     */
    @Test
    public void testHaltBathchBecauseRetryMaxCountWasExceeded() {
        ThreadContext.setRequestId("12345");
        RetryHandler retryHandler = SystemRepository.get("retryHandler");
        CountingRetryContextFactory retry = new CountingRetryContextFactory();
        retry.setRetryCount(10);
        retryHandler.setRetryContextFactory(retry);

        final List<Integer> execCountHolder = new ArrayList<Integer>(1);
        execCountHolder.add(0);

        Handler<Object, Object> junkHandler = new Handler<Object, Object>() {
            int execCount = 0;

            public Object handle(Object data, ExecutionContext context) {
                execCount++;
                execCountHolder.set(0, execCount);
                data = null;
                return data.toString(); // !!
            }
        };

        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(retryHandler);
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "fastProcessResidentHandler"));
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "retryHandler"));
        handlers.add(createProcessStopHandler());
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "serviceAvailabilityCheckHandler"));
        handlers.add(junkHandler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        try {
            context.handleNext(null);
            fail();
        } catch (Exception e) {
            assertThat("リトライ上限まで再実行した後、起因例外を送出して終了する。", e,
                    instanceOf(ProcessAbnormalEnd.class));
            assertThat(execCountHolder.get(0), is(11));
            assertThat(((ProcessAbnormalEnd) e).getStatusCode(), is(180)); // リトライ失敗
        }
    }


    /**
     * リトライ単位間隔内のエラー連続発生件数がリトライ上限以下に収まれば、
     * 処理が継続される。
     */
    @Test
    public void testContinuingProcessIfRetryCountLowersItsLimit() {
        ThreadContext.setRequestId("12345");
        RetryHandler retryHandler = SystemRepository.get("retryHandler");
        CountingRetryContextFactory retry = new CountingRetryContextFactory();
        retry.setRetryCount(3);      // 3秒間で4回以上リトライが発生したらプロセスを落とす
        retry.setMaxRetryTime(3000); // 3秒でリセット
        retryHandler.setRetryContextFactory(retry);


        final List<Integer> execCountHolder = new ArrayList<Integer>(1);
        execCountHolder.add(0);
        execCountHolder.add(0);
        execCountHolder.add(0);

        Handler<Object, Object> junkHandler = new Handler<Object, Object>() {
            int execCount = 0;

            int totalCount = 0;

            public Object handle(Object data, ExecutionContext context) {
                execCount++;
                totalCount++;
                execCountHolder.set(0, execCount);
                execCountHolder.set(1, totalCount);
                if (execCount == 3) {
                    // 3回めのリトライで成功
                    execCount = 0;
                    try {
                        Thread.sleep(3000); //リトライカウントのリセットを待つ。
                    } catch (InterruptedException e) {
                        new RuntimeException(e);
                    }

                    return context.handleNext(data);
                }
                data = null;
                return data.toString(); // !! N.P.E

            }
        };

        Handler<Object, Object> action = new Handler<Object, Object>() {
            int execCount = 0;

            public Object handle(Object data, ExecutionContext context) {
                execCount++;
                execCountHolder.set(2, execCount);

                if (execCount == 5) {
                    throw new ProcessAbnormalEnd(199, "MSG0001");
                }
                return new Result.Success();
            }
        };

        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(retryHandler);
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "fastProcessResidentHandler"));
        handlers.add(createProcessStopHandler());
        handlers.add(junkHandler);
        handlers.add(action);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        try {
            context.handleNext(null);
            fail();
        } catch (Exception e) {
            assertThat("リトライ上限まで再実行した後、起因例外を送出して終了する。", e,
                    instanceOf(ProcessAbnormalEnd.class));
            assertThat(execCountHolder.get(0), is(3));
            assertThat(execCountHolder.get(1), is(15)); // 3 x 5
            assertThat(execCountHolder.get(2), is(5));
            assertThat(((ProcessAbnormalEnd) e).getStatusCode(), is(199));
        }
    }


    /**
     * {@link ProcessResidentHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * データ監視間隔よりも、後続ハンドラの処理時間が長い場合
     */
    @Test
    public void testHandle9() {
        ThreadContext.setRequestId("12345");
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "fastProcessResidentHandler"));
        handlers.add(createProcessStopHandler());
        handlers.add(SystemRepository.<Handler<?, ?>>get(
                "serviceAvailabilityCheckHandler"));
        handlers.add(new Handler<Object, Object>() {
            private int count = 0;

            public Object handle(Object o, ExecutionContext context) {
                count++;
                if (count == 2) {
                    stopProcess();
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException();
                }
                return new Success();
            }
        });

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        Object o = context.handleNext(null);
        assertThat(o, instanceOf(Success.class));

    }

    /** {@link ProcessResidentHandler#setDataWatchInterval(int)}のテスト。 */
    @Test
    public void testSetDataWatchInterval() {

        ProcessResidentHandler handler = new ProcessResidentHandler();
        // 1はエラーにならない。
        handler.setDataWatchInterval(1);

        // 0はエラー
        try {
            handler.setDataWatchInterval(0);
            fail("does not run.");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString(
                    "data watch interval time was invalid."
                            + " please set a value greater than 1."
                            + " specified value is:0"));
        }

        // -1はエラー
        try {
            handler.setDataWatchInterval(-1);
            fail("does not run.");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString(
                    "data watch interval time was invalid."
                            + " please set a value greater than 1."
                            + " specified value is:-1"));
        }
    }

    /** {@link ProcessResidentHandler#setNormalEndExceptions(java.util.List)}のテスト */
    @Test
    public void testSetNormalEndExceptions() {
        ProcessResidentHandler handler = new ProcessResidentHandler();

        List<String> errors1 = new ArrayList<String>();
        errors1.add("java.lang.NullPointerException");
        errors1.add("nablarch.fw.results.InternalError");
        handler.setNormalEndExceptions(errors1);

        // 存在しないクラスを設定した場合。
        List<String> errors2 = new ArrayList<String>();
        errors2.add("java.lang.IllegalArgumentException");
        errors2.add("hogehoge");
        try {
            handler.setNormalEndExceptions(errors2);
            fail("does not run.");
        } catch (RuntimeException e) {
            assertThat("causeは、ClassNotFoundExceptionであること。", e.getCause(),
                    instanceOf(ClassNotFoundException.class));
        }

        // RuntimeExceptionのサブクラス以外を指定した場合
        List<String> errors3 = new ArrayList<String>();
        errors3.add("java.lang.IllegalArgumentException");
        errors3.add("java.lang.OutOfMemoryError");
        try {
            handler.setNormalEndExceptions(errors3);
            fail("does not run.");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString(
                    "this class isn't a subclass of java.lang.RuntimeException."));
        }
    }

    /**
     * リトライ可能例外が送出された場合、常駐化ハンドラでは障害通知ログが出力されないことを確認する。
     */
    @Test
    public void testThrowRetryError() {
        ThreadContext.setRequestId("12345");
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();

        // デフォルト設定の常駐化ハンドラを設定
        handlers.add(
                SystemRepository.<Handler<?, ?>>get(
                        "defaultProcessResidentHandler"));

        // 処理停止チェックハンドラ
        handlers.add(createProcessStopHandler());

        // リトライ可能例外を送出するハンドラを追加する。
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                // リトライ可能例外を送出する
                throw new RetryableException("retry error!!!");
            }
        });

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        try {
            context.handleNext(null);
            fail("ここはとおらない。");
        } catch (RetryableException e) {
            assertTrue(true);
        }
        // FATALログが出力されていないことを確認する。
        List<String> monitorLog = OnMemoryLogWriter.getMessages("writer.monitorLog");
        assertThat("リトライ可能例外が送出されるので障害通知ログは出力されないこと。", monitorLog.size(), is(0));

        // WARNログも出力されないこと
        List<String> appLog = OnMemoryLogWriter.getMessages("writer.appLog");
        for (String log : appLog) {
            System.out.println("log = " + log);
            assertThat(log, is(not(Matchers.startsWith("WARN"))));
        }
    }

    @Test
    public void testRetryErrorTimeout() {
        ThreadContext.setRequestId("12345");
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();

        RetryHandler retryHandler = new RetryHandler();
        CountingRetryContextFactory retryContextFactory = new CountingRetryContextFactory();
        retryContextFactory.setRetryCount(10);
        retryContextFactory.setRetryIntervals(10);
        retryHandler.setRetryContextFactory(retryContextFactory);
        handlers.add(retryHandler);

        // デフォルト設定の常駐化ハンドラを設定
        handlers.add(
                SystemRepository.<Handler<?, ?>>get(
                        "defaultProcessResidentHandler"));

        // リトライ可能例外を送出するハンドラを追加する。
        handlers.add(new Handler<Object, Object>() {
            private int count = 0;

            public Object handle(Object o, ExecutionContext context) {
                // リトライ可能例外を送出する
                count++;
                if (count % 2 == 0) {
                    throw new RetryableException("retry error!!!");
                } else {
                    throw new RuntimeException("runtime error!!!",
                            new RetryableException("retry error!!!"));
                }
            }
        });

        ExecutionContext context = new ExecutionContext();
        context.addHandlers(handlers);
        try {
            context.handleNext(null);
            fail("ここはとおらない。");
        } catch (ProcessAbnormalEnd e) {
            assertThat("プロセス停止例外のcauseには、リトライ可能例外が格納されていること。",
                    RetryUtil.isRetryable(e.getCause()), is(true));
        }

        // FATALログが出力されていないことを確認する。
        List<String> monitorLog = OnMemoryLogWriter.getMessages("writer.monitorLog");
        assertThat("リトライ可能例外のため障害通知ログは出力されないこと。", monitorLog.size(), is(0));

        List<String> appLog = OnMemoryLogWriter.getMessages("writer.appLog");

        assertThat("リトライ上限に達したことを示すワーニングレベルのログが出力されること。",
                appLog, LogMatcher.Log("retry process failed. retry limit was exceeded.", "WARN"));
        assertThat("1回目のリトライを示すワーニングログが出力されること。",
                appLog, LogMatcher.Log("caught a exception to retry. start retry. retryCount[1]", "WARN"));
        assertThat("2回目のリトライを示すワーニングログが出力されること。",
                appLog, LogMatcher.Log("caught a exception to retry. start retry. retryCount[2]", "WARN"));
        assertThat("10回目のリトライを示すワーニングログが出力されること。",
                appLog, LogMatcher.Log("caught a exception to retry. start retry. retryCount[10]", "WARN"));

        // ワーニングログの件数をアサート
        int warningCount = 0;
        for (String log : appLog) {
            if (log.startsWith("WARN")) {
                warningCount++;
            }
        }
        assertThat("ワーニングログの件数は、リトライ時2件とリトライリミット時1件の合計3件であること。", warningCount, is(11));
    }

    /**
     * 実行時例外が送出された場合リトライ可能例外に付け替えられて再送出されること。
     * また、再送出時に障害通知ログが出力されること。
     */
    @Test
    public void testRuntimeException() {
        ThreadContext.setRequestId("12345");
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();

        // デフォルト設定の常駐化ハンドラを設定
        handlers.add(
                SystemRepository.<Handler<?, ?>>get(
                        "defaultProcessResidentHandler"));

        // 実行時例外を送出するハンドラを追加
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                throw new NullPointerException("error!!!");
            }
        });
        ExecutionContext context = new ExecutionContext();
        context.addHandlers(handlers);
        try {
            context.handleNext(null);
            fail("ここはとおらない。");
        } catch (RetryableException e) {
            assertThat("causeには、元例外が設定されていること", e.getCause(), is(instanceOf(NullPointerException.class)));
            assertThat(e.getCause()
                    .getMessage(), is("error!!!"));
        }
        List<String> monitorLog = OnMemoryLogWriter.getMessages("writer.monitorLog");
        assertThat("実行事例がが送出されたので障害通知ログが1件出力されていること。", monitorLog.size(), is(1));

        // WARNログも出力されないこと
        List<String> appLog = OnMemoryLogWriter.getMessages("writer.appLog");
        for (String log : appLog) {
            System.out.println("log = " + log);
            assertThat(log, is(not(Matchers.startsWith("WARN"))));
        }
    }

    /**
     * 実行時例外が発生し、リトライリミットに達した場合は実行時例外が送出される度に障害通知ログが出力されること。
     */
    @Test
    public void testRuntimeExceptionRetryLimit() {
        ThreadContext.setRequestId("12345");
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();

        // リトライハンドラの設定
        RetryHandler retryHandler = new RetryHandler();
        CountingRetryContextFactory retryContextFactory = new CountingRetryContextFactory();
        retryContextFactory.setRetryCount(3);
        retryContextFactory.setRetryIntervals(10);
        retryHandler.setRetryContextFactory(retryContextFactory);
        handlers.add(retryHandler);

        // デフォルト設定の常駐化ハンドラを設定
        handlers.add(
                SystemRepository.<Handler<?, ?>>get(
                        "defaultProcessResidentHandler"));

        // 実行時例外を送出するハンドラを追加
        handlers.add(new Handler<Object, Object>() {
            public Object handle(Object o, ExecutionContext context) {
                throw new NullPointerException("error!!!");
            }
        });

        ExecutionContext context = new ExecutionContext();
        context.addHandlers(handlers);

        try {
            context.handleNext(null);
            fail("ここはとおらない。");
        } catch (ProcessAbnormalEnd e) {
            assertThat("causeはリトライ可能例外であること", e.getCause(), is(instanceOf(RetryableException.class)));
            assertThat("リトライ可能例外のcauseはNullPointerExceptionであること",
                    e.getCause()
                            .getCause(), is(instanceOf(NullPointerException.class)));
        }
        List<String> monitorLog = OnMemoryLogWriter.getMessages("writer.monitorLog");
        assertThat("リトライを繰り返す度に障害通知ログが出力されること。", monitorLog.size(), is(4));

        // リトライの度にワーニングレベルのログが出力されること。
        int warningCount = 0;
        List<String> appLog = OnMemoryLogWriter.getMessages("writer.appLog");
        for (String log : appLog) {
            if (log.startsWith("WARN")) {
                warningCount++;
            }
        }
        assertThat("リトライの度にワーニングレベルのログが出力されること", warningCount, is(4));
    }

    //--------------------------------------------------------------------------
    // 以下は、テストで使用するハンドラ等のやメソッドたち。
    //--------------------------------------------------------------------------

    /** プロセス停止用のハンドラを生成する。 */
    private ProcessStopHandler createProcessStopHandler() {
        BasicProcessStopHandler handler = new BasicProcessStopHandler();
        handler.setDbTransactionManager(
                SystemRepository.<SimpleDbTransactionManager>get("tran"));
        handler.setTableName("handler_batch_request");
        handler.setRequestIdColumnName("request_id");
        handler.setProcessHaltColumnName("process_halt_flg");
        handler.setCheckInterval(1);
        handler.initialize();
        return handler;
    }

    /**
     * テスト用のハンドラ。
     * <p/>
     * 本ハンドラを、{@link ProcessResidentHandler}の後続ハンドラに設定し、
     * 繰り返しハンドラが呼ばれることを確認する。
     */
    private class DummyHandler implements Handler<Object, Object> {

        /** 処理件数 */
        private int maxExecCount;

        /** 現在件数 */
        private int nowCount;

        /**
         * コンストラクタ。
         * <p/>
         * 処理件数に達したら、処理停止フラグをonに変更し処理を停止する。
         *
         * @param maxExecCount 処理件数
         */
        private DummyHandler(int maxExecCount) {
            this.maxExecCount = maxExecCount;
        }

        /** {@inheritDoc} */
        public Object handle(Object o, ExecutionContext context) {
            if (maxExecCount == ++nowCount) {
                stopProcess();
            }
            return new Success();
        }
    }

    /** テスト用のログライタ。 */
    public static class LogWriter extends LogWriterSupport {

        /** ログ出力されたメッセージを保持するオブジェクト */
        private static List<String> logMessages = new ArrayList<String>();

        /** ログに出力されたメッセージを削除する。 */
        private static void logClear() {
            logMessages.clear();
        }

        @Override
        protected void onWrite(String formattedMessage) {
            logMessages.add(formattedMessage);
        }
    }

    /** ログアサート用Matcher。 */
    private static class LogMatcher extends TypeSafeMatcher<List<String>> {

        /** 期待するログ */
        private String expectedLog;

        /** ログレベル */
        private String logLevel;

        private static LogMatcher Log(String expectedLog) {
            return Log(expectedLog, "INFO");
        }

        private static LogMatcher Log(String expectedLog, String logLevel) {
            LogMatcher matcher = new LogMatcher();
            matcher.expectedLog = expectedLog;
            matcher.logLevel = logLevel;
            return matcher;
        }

        public void describeTo(Description description) {
            description.appendText("LOG_LEVEL = [");
            description.appendText(logLevel);
            description.appendText("]");
            description.appendText(":");
            description.appendText("LOG_MESSAGE = [");
            description.appendText(expectedLog);
            description.appendText("]");
        }

        @Override
        public boolean matchesSafely(List<String> log) {
            for (String message : log) {
                if (message.contains(expectedLog)
                        && message.contains(logLevel)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 開閉局状態を変更する。 */
    private void changeServiceStatus(final String status) {
        VariousDbTestHelper.setUpTable(new HandlerBatchRequest("12345", "リクエスト０１", "0", "1", status, 0L));
    }

    /** 異常終了するハンドラ */
    private class AbnormalEndHandler implements Handler<Object, Object> {

        /** 処理最大件数 */
        private int maxExecCount;

        /** 現在の処理件数 */
        private int currentExecCount;

        private AbnormalEndHandler(int maxExecCount) {
            this.maxExecCount = maxExecCount;
        }

        public Object handle(Object o, ExecutionContext context) {
            currentExecCount++;
            if (currentExecCount == maxExecCount / 2) {
                throw new NumberFormatException();
            } else if (currentExecCount == maxExecCount) {
                stopProcess();
            }
            return new Success();
        }
    }

    /** 異常終了するハンドラ */
    private class AbnormalEndHandler2 implements Handler<Object, Object> {

        /** 処理最大件数 */
        private int maxExecCount;

        /** 現在の処理件数 */
        private int currentExecCount;

        private AbnormalEndHandler2(int maxExecCount) {
            this.maxExecCount = maxExecCount;
        }

        public Object handle(Object o, ExecutionContext context) {
            currentExecCount++;
            if (currentExecCount == maxExecCount / 2) {
                throw new nablarch.fw.results.InternalError("InternalError");
            } else if (currentExecCount == maxExecCount) {
                stopProcess();
            }
            return new Success();
        }
    }

    private class TransactionAbnormalEndHandler implements Handler<Object, Object> {

        /** 処理最大件数 */
        private int maxExecCount;

        /** 現在の処理件数 */
        private int currentExecCount;

        private TransactionAbnormalEndHandler(int maxExecCount) {
            this.maxExecCount = maxExecCount;
        }

        public Object handle(Object o, ExecutionContext context) {
            currentExecCount++;
            if (currentExecCount == maxExecCount / 2) {
                throw new TransactionAbnormalEnd(100, "MSG0001");
            } else if (currentExecCount == maxExecCount) {
                stopProcess();
            }
            return new Success();
        }
    }

    private static void stopProcess() {
        VariousDbTestHelper.setUpTable(new HandlerBatchRequest(ThreadContext.getRequestId(), "リクエスト０１", "1", "1", "1",
                0L));
    }

    @SuppressWarnings("unchecked")
    private class BasicProcessStopHandler implements ProcessStopHandler, Initializable {

        private int checkInterval = 1;
        private SimpleDbTransactionManager dbTransactionManager;
        private String tableName;
        private String requestIdColumnName;
        private String processHaltColumnName;
        private String query;
        private int exitCode = 1;
        private final ThreadLocal<Integer> count = new ThreadLocal() {
            @Override
            protected Integer initialValue() {
                return Integer.valueOf(0);
            }
        };

        public BasicProcessStopHandler() {
        }

        @Override
        public Object handle(Object o, ExecutionContext context) {
            int nowCount = count.get();
            if (nowCount++ % checkInterval == 0) {
                if (isProcessStop(ThreadContext.getRequestId())) {
                    throw new ProcessStop(exitCode);
                }
                nowCount = 1;
            }
            count.set(nowCount);
            return context.handleNext(o);
        }

        @Override
        public boolean isProcessStop(final String requestId) {
            return (Boolean) (new SimpleDbTransactionExecutor(dbTransactionManager) {
                public Boolean execute(AppDbConnection connection) {
                    SqlPStatement statement = connection.prepareStatement(query);
                    statement.setString(1, requestId);
                    statement.setString(2, "1");
                    SqlResultSet retrieve = statement.retrieve();
                    return !retrieve.isEmpty();
                }
            }).doTransaction();
        }

        @Override
        public void setCheckInterval(int checkInterval) {
            this.checkInterval = checkInterval <= 0 ? 1 : checkInterval;
        }

        @Override
        public void initialize() {
            String query = "SELECT " + requestIdColumnName + " FROM " + tableName + " WHERE " + requestIdColumnName + " = ?" + " AND " + processHaltColumnName + " = ?";
            this.query = query;
        }

        @Override
        public void setExitCode(int exitCode) {
            if (exitCode > 0 && exitCode < 256) {
                this.exitCode = exitCode;
            } else {
                throw new IllegalArgumentException("exit code was invalid range. please set it in the range of 255 from 1. specified value was:" + exitCode);
            }
        }

        public void setDbTransactionManager(SimpleDbTransactionManager dbTransactionManager) {
            this.dbTransactionManager = dbTransactionManager;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public void setRequestIdColumnName(String requestIdColumnName) {
            this.requestIdColumnName = requestIdColumnName;
        }

        public void setProcessHaltColumnName(String processHaltColumnName) {
            this.processHaltColumnName = processHaltColumnName;
        }
    }
}
