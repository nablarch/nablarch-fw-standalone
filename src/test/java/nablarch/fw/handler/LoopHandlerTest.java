package nablarch.fw.handler;

import nablarch.common.handler.DbConnectionManagementHandler;
import nablarch.core.ThreadContext;
import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.exception.SqlStatementException;
import nablarch.core.db.transaction.JdbcTransactionFactory;
import nablarch.core.log.app.BasicCommitLogger;

import nablarch.core.log.app.CommitLogger;
import nablarch.core.log.basic.LogWriterSupport;
import nablarch.core.transaction.Transaction;
import nablarch.core.transaction.TransactionContext;
import nablarch.core.transaction.TransactionFactory;
import nablarch.core.util.StringUtil;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.Result.Success;
import nablarch.fw.reader.ResumeDataReader;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import nablarch.test.support.log.app.OnMemoryLogWriter;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.matchers.JUnitMatchers;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * {@link LoopHandler}のテストクラス。
 *
 * @author hisaaki sioiri
 */
@RunWith(DatabaseTestRunner.class)
public class LoopHandlerTest {
	
    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource("nablarch/fw/handler/LoopHandlerTest.xml");
    
    @BeforeClass
    public static void beforeClass() {
    	VariousDbTestHelper.createTable(HandlerTestTable.class);
    	VariousDbTestHelper.createTable(HandlerBatchRequest.class);
    }

    @Before
    public void before() {
    	VariousDbTestHelper.delete(HandlerTestTable.class);
    	VariousDbTestHelper.setUpTable(
    			new HandlerBatchRequest("RW000000", "リクエスト００", "1", "1", "1", 0L),
    			new HandlerBatchRequest("RW000001", "リクエスト０１", "1", "1", "1", 1L),
    			new HandlerBatchRequest("RW000002", "リクエスト０２", "1", "1", "1", 2L),
    			new HandlerBatchRequest("RW000003", "リクエスト０３", "1", "1", "1", 3L),
    			new HandlerBatchRequest("RW000004", "リクエスト０４", "1", "1", "1", 4L));
    }

    /** トランザクション制御のテスト。 */
    @Test
    public void testHandle() {
        ThreadContext.setRequestId("RW000000");

        // コミット間隔0、処理データ20
        executeTest(0, 20);
        // コミット間隔1、処理データ20
        executeTest(1, 20);
        // コミット間隔3、処理データ15
        executeTest(2, 15);
        // コミット間隔3、処理データ20
        executeTest(3, 20);
        // コミット間隔3、処理データ50
        executeTest(4, 50);
    }

    /**
     * トランザクション制御のテスト。
     * トランザクション名を明示的に指定した場合のテスト。
     */
    @Test
    public void testHandleByTransactionName() {
        // コミット間隔0、処理データ20
        executeTest(0, 20, "tranName");
        // コミット間隔1、処理データ20
        executeTest(1, 20, "tranName");
        // コミット間隔3、処理データ15
        executeTest(2, 15, "tranName");
        // コミット間隔3、処理データ20
        executeTest(3, 20, "tranName");
        // コミット間隔3、処理データ50
        executeTest(4, 50, "tranName");
    }

    /**
     * テストを実行する。
     *
     * @param commitInterval
     * @param recordCount
     */
    private void executeTest(int commitInterval, int recordCount) {
        executeTest(commitInterval, recordCount, null);
    }

    /**
     * テストを実行する。
     *
     * @param commitInterval
     * @param recordCount
     */
    private void executeTest(int commitInterval, int recordCount,
            String transactionName) {
        before();
        OnMemoryLogWriter.clear();

        // ハンドラリストを構築する。
        // コミット間隔は3
        List<Handler> handlers = createNormalHandlers(commitInterval,
                new ActionHandler(transactionName), transactionName);

        ExecutionContext context = new ExecutionContext();
        context.setDataReader(
            (transactionName == null)
                ? new ResumeDataReader<String>().setSourceReader(createReader(recordCount))
                : createReader(recordCount) //どういうわけかResumeDataReaderは複数トランザクションで使用することができない仕様になっている。
        );
        context.setHandlerQueue(handlers);
        BasicCommitLogger logger = new BasicCommitLogger();
        logger.setInterval(10);
        logger.initialize();
        context.setSessionScopedVar(CommitLogger.SESSION_SCOPE_KEY, logger);

        OnMemoryLogWriter.getMessages("writer.BasicCommitLogger");

        context.handleNext(null);

        logger.terminate();

        // ログのアサート
        int logInterval;
        if (commitInterval <= 0) {
            logInterval = 10;
        } else {
            if (commitInterval >= 10) {
                logInterval = commitInterval;
            } else {
                if (10 % commitInterval == 0) {
                    logInterval = 10;
                } else {
                    logInterval = 10 + (commitInterval - (10 % commitInterval));
                }
            }
        }
        List<String> log = OnMemoryLogWriter.getMessages("writer.BasicCommitLogger");
        assertThat(log.size(), is(recordCount / logInterval + 1));

        for (int i = 0; i < log.size() - 1; i++) {
            assertThat(log.get(i), containsString(
                    "COMMIT COUNT = [" +
                            (i + 1) * logInterval + "]"));
        }

        assertThat(log.get(log.size() - 1), containsString(
                "TOTAL COMMIT COUNT = [" + recordCount + "]"));

        // 最後に全件コミットされていることをアサートする。
        assertTableRecord(recordCount, context);

    }

    /** マルチスレッドのテスト。 */
    @Test
    public void testMultiThread() {

        // ハンドラリストを構築する。
        // コミット間隔は3
        List<Handler> handlers = createNormalHandlers(3, new ActionHandler());

        int recordCount = 35;
        final ExecutionContext context = new ExecutionContext();
        context.setDataReader(createReader(recordCount));
        context.setHandlerQueue(handlers);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<Object>> futures = new ArrayList<Future<Object>>();
        for (int i = 0; i < 8; i++) {
            futures.add(executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    new ExecutionContext(context).handleNext(null);
                    return null;
                }
            }));
        }

        for (Future<Object> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }
        executor.shutdownNow();
        assertTableRecord(recordCount, context);
    }

    /**
     * 異常系のテスト。
     * <p/>
     * ハンドラ内で{@link RuntimeException}が発生した場合。
     */
    @Test
    public void testHandleError1() {
        ThreadContext.setRequestId("RW000000");
        // ハンドラリストを構築する。
        // コミット間隔は3
        List<Handler> handlers = createNormalHandlers(3, new ErrorActionHandler(
                5, new RuntimeException("runtime error")));
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        context.setDataReader(new ResumeDataReader<String>().setSourceReader(createReader(6)));

        try {
            context.handleNext(null);
            fail("");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), JUnitMatchers.containsString("runtime"));
            assertNotNull("例外発生時は最後に読み込んだデータオブジェクトが残っていること。",
                    context.getLastReadData());
        }

        assertTableRecord(3, context);
    }

    /**
     * 異常系のテスト。
     * <p/>
     * ハンドラ内で{@link Error}が発生した場合。
     */
    @Test
    public void testHandleError2() {
        ThreadContext.setRequestId("RW000000");
        // ハンドラリストを構築する。
        // コミット間隔は10
        List<Handler> handlers = createNormalHandlers(10,
                new ErrorActionHandler(13, new Error("error")));
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        context.setDataReader(new ResumeDataReader<String>().setSourceReader(createReader(15)));

        try {
            context.handleNext(null);
            fail("");
        } catch (Error e) {
            assertThat(e.getMessage(), JUnitMatchers.containsString("error"));
            assertNotNull("例外発生時は最後に読み込んだデータオブジェクトが残っていること。",
                    context.getLastReadData());
        }

        assertTableRecord(10, context);
    }

    /**
     * 異常系のテスト。
     * <p/>
     * ハンドラ内で{@link RuntimeException}が発生し、
     * ロールバック処理で再度{@link RuntimeException}が発生する場合。
     */
    @Test
    public void testHandleError3() {
        ThreadContext.setRequestId("RW000000");
        // ハンドラリストを構築する。
        // コミット間隔は10
        List<Handler> handlers = createErrorHandlers(10,
                new ErrorActionHandler(13, new RuntimeException(
                        "action error")), new RuntimeException(
                "rollback error"));
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        context.setDataReader(new ResumeDataReader<String>().setSourceReader(createReader(15)));

        try {
            context.handleNext(null);
            fail("");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), JUnitMatchers.containsString(
                    "rollback error"));
            assertNotNull("例外発生時は最後に読み込んだデータオブジェクトが残っていること。",
                    context.getLastReadData());
        }

        assertTableRecord(10, context);
    }

    /**
     * 異常系のテスト。
     * <p/>
     * ハンドラ内で{@link RuntimeException}が発生し、
     * ロールバック処理で再度{@link Error}が発生する場合。
     */
    @Test
    public void testHandleError4() {
        ThreadContext.setRequestId("RW000000");
        // ハンドラリストを構築する。
        // コミット間隔は10
        List<Handler> handlers = createErrorHandlers(10,
                new ErrorActionHandler(13, new RuntimeException(
                        "action error")), new Error("rollback error!!!"));
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        context.setDataReader(new ResumeDataReader<String>().setSourceReader(createReader(15)));

        try {
            context.handleNext(null);
            fail("");
        } catch (Error e) {
            assertThat(e.getMessage(), JUnitMatchers.containsString(
                    "rollback error!!!"));
            assertNotNull("例外発生時は最後に読み込んだデータオブジェクトが残っていること。",
                    context.getLastReadData());
        }

        assertTableRecord(10, context);
    }

    /**
     * 異常系のテスト。
     * <p/>
     * ハンドラ内で{@link Error}が発生し、
     * ロールバック処理で再度{@link RuntimeException}が発生する場合。
     */
    @Test
    public void testHandleError5() {
        ThreadContext.setRequestId("RW000000");
        // ハンドラリストを構築する。
        // コミット間隔は12
        List<Handler> handlers = createErrorHandlers(12,
                new ErrorActionHandler(13, new OutOfMemoryError(
                        "out of memory error")), new NullPointerException(
                "null"));
        ExecutionContext context = new ExecutionContext();

        context.setHandlerQueue(handlers);
        context.setDataReader(new ResumeDataReader<String>().setSourceReader(createReader(15)));

        try {
            context.handleNext(null);
            fail("");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), JUnitMatchers.containsString("null"));
            assertNotNull("例外発生時は最後に読み込んだデータオブジェクトが残っていること。",
                    context.getLastReadData());
        }

        assertTableRecord(12, context);
    }

    /**
     * 異常系のテスト。
     * <p/>
     * ハンドラ内で{@link Error}が発生し、
     * ロールバック処理で再度{@link Error}が発生する場合。
     */
    @Test
    public void testHandleError6() {
        ThreadContext.setRequestId("RW000000");
        // ハンドラリストを構築する。
        // コミット間隔は12
        List<Handler> handlers = createErrorHandlers(12,
                new ErrorActionHandler(13, new OutOfMemoryError(
                        "out of memory error")), new Error("error!!!!"));
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        context.setDataReader(new ResumeDataReader<String>().setSourceReader(createReader(15)));

        try {
            context.handleNext(null);
            fail("");
        } catch (Error e) {
            assertThat(e.getMessage(), JUnitMatchers.containsString(
                    "error!!!!"));
            assertNotNull("例外発生時は最後に読み込んだデータオブジェクトが残っていること。",
                    context.getLastReadData());
        }

        assertTableRecord(12, context);
    }

    /**
     * 異常系のテスト。
     * <p/>
     * 特定のスレッドで例外が発生した場合、そのスレッドの処理でーたのみロールバックされ、
     * それ以外の処理データはコミットされること！。
     */
    @Test
    public void testHandleMultiThreadError() {
        // ハンドラリストを構築する。
        // コミット間隔は12
        List<Handler> handlers = createNormalHandlers(50, new ActionHandler() {

            @Override
            public Result handle(String s, ExecutionContext context) {
                Result result = super.handle(s, context);
                if ("00025".equals(s)) {
                    throw new IllegalArgumentException("error:" + s);
                }
                return result;
            }
        });
        final ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlers);
        context.setDataReader(createReader(100));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<Object>> futures = new ArrayList<Future<Object>>();
        for (int i = 0; i < 4; i++) {
            futures.add(executor.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    new ExecutionContext(context).handleNext(null);
                    return null;
                }
            }));
        }

        for (Future<Object> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                //throw new RuntimeException(e.getCause());
            }
        }
        executor.shutdownNow();
        final List<String> rollbackData = context.getSessionScopedVar(
                "ROLLBACK_DATA");

        // データのアサート
        // ロールバックされたデータは、DBに存在しないことを確認
        DataReader<String> reader = createReader(100);
        while (reader.hasNext(context)) {
            String record = reader.read(context);
            boolean deleted = rollbackData.remove(record);

            HandlerTestTable result = VariousDbTestHelper.findById(HandlerTestTable.class, record);
            
            System.out.println("result = " + result);
            if (deleted) {
                assertThat("ロールバックされたデータはDBに存在しないこと。 col1 = " + record, result, is(nullValue()));
            } else {
                assertThat("DBにデータが存在すること。 col1 = " + record, result, is(notNullValue()));
            }
        }
    }


    /**
     * テーブルのレコードをアサートする。
     *
     * @param recordCount
     * @param context
     */
    private static void assertTableRecord(final int recordCount,
            final ExecutionContext context) {
        // 最後に全件コミットされていることをアサートする。
        final DataReader<String> reader = createReader(recordCount);
        
        // 件数の確認
        List<HandlerTestTable> allData = VariousDbTestHelper.findAll(HandlerTestTable.class);
        assertThat(allData.size(), is(recordCount));

        // レジュームポイントの確認
        if (context.getDataReader() instanceof ResumeDataReader) {
        	HandlerBatchRequest result = VariousDbTestHelper.findById(HandlerBatchRequest.class, "RW000000");
            assertThat(result.resumePoint.intValue(), is(recordCount));
        }

        while (reader.hasNext(context)) {
            String record = reader.read(context);

            HandlerTestTable result = VariousDbTestHelper.findById(HandlerTestTable.class, record);
            assertThat("データが存在していること。", result, is(notNullValue()));
        }
    }

    /** データリーダを生成する。 */
    private static DataReader<String> createReader(int recordCount) {
        final List<String> records = new ArrayList<String>();
        for (int i = 1; i <= recordCount; i++) {
            records.add(StringUtil.lpad(String.valueOf(i), 5, '0'));
        }
        return new DataReader<String>() {
            @Override
            public synchronized String read(ExecutionContext ctx) {
                return records.remove(0);
            }

            @Override
            public synchronized boolean hasNext(ExecutionContext ctx) {
                return !records.isEmpty();
            }

            @Override
            public void close(ExecutionContext ctx) {
                // nop
            }
        };
    }

    /**
     * 正常終了用のハンドラリストを構築する。
     *
     * @param commitInterval
     * @param actionHandler
     * @return 構築したハンドラリスト
     */
    private List<Handler> createNormalHandlers(int commitInterval,
                                               Handler<?, ?> actionHandler) {
        return createNormalHandlers(commitInterval, actionHandler, null);
    }

    /**
     * 正常終了用のハンドラリストを構築する。
     *
     * @param commitInterval
     * @param actionHandler
     * @param transactionName
     * @return 構築したハンドラリスト
     */
    private List<Handler> createNormalHandlers(int commitInterval,
                                               Handler<?, ?> actionHandler, String transactionName) {

        List<Handler> handlerList = new ArrayList<Handler>();

        // １．DB接続ハンドラ
		DbConnectionManagementHandler dbConnectionManagementHandler = repositoryResource
				.getComponent("dbConnectionManagementHandler");
        if (transactionName != null) {
            dbConnectionManagementHandler.setConnectionName(transactionName);
        }
        handlerList.add(dbConnectionManagementHandler);

        // ２．ループハンドラ
        // これが本クラスのテスト対象のクラスとなる。
        TransactionFactory factory = repositoryResource.getComponent(
                "jdbcTransactionFactory");
        LoopHandler handler = new LoopHandler();
        handler.setTransactionFactory(factory);
        handler.setCommitInterval(commitInterval);
        if (transactionName != null) {
            handler.setTransactionName(transactionName);
        }
        handlerList.add(handler);

        handlerList.add(new AssertToClearLastReadDataHandler());

        // ３．データリードハンドラ
        handlerList.add(new DataReadHandler());

        // ４．アサート用のクラス
        handlerList.add(new AssertHandler(commitInterval));

        // ５．アクション
        // リクエストパラメータの文字列をDBに登録するハンドラ
        handlerList.add(actionHandler);

        return handlerList;
    }

    /**
     * 正常終了用のハンドラリストを構築する。
     *
     * @param commitInterval
     * @param actionHandler
     * @return 構築したハンドラリスト
     */
    private List<Handler> createErrorHandlers(int commitInterval,
                                              Handler<?, ?> actionHandler, final Throwable throwable) {

        List<Handler> handlerList = new ArrayList<Handler>();

        // １．DB接続ハンドラ
        DbConnectionManagementHandler dbConnectionManagementHandler =
                repositoryResource.getComponent("dbConnectionManagementHandler");
        handlerList.add(dbConnectionManagementHandler);

        // ２．ループハンドラ
        // これが本クラスのテスト対象のクラスとなる。
        TransactionFactory transactionFactory = new TransactionFactory() {
            @Override
            public Transaction getTransaction(String resourceName) {
                JdbcTransactionFactory factory = new JdbcTransactionFactory();
                factory.setIsolationLevel("READ_COMMITTED");
                final Transaction transaction = factory.getTransaction(
                        resourceName);
                return new Transaction() {
                    @Override
                    public void begin() {
                        transaction.begin();
                    }

                    @Override
                    public void commit() {
                        transaction.commit();
                    }

                    @Override
                    public void rollback() {
                        transaction.rollback();
                        if (throwable instanceof RuntimeException) {
                            throw (RuntimeException) throwable;
                        } else if (throwable instanceof Error) {
                            throw (Error) throwable;
                        }
                    }
                };
            }
        };

        LoopHandler handler = new LoopHandler();
        handler.setTransactionFactory(transactionFactory);
        handler.setCommitInterval(commitInterval);
        handlerList.add(handler);

handlerList.add(new AssertToClearLastReadDataHandler());

        // ３．データリードハンドラ
        handlerList.add(new DataReadHandler());

        // ４．アサート用のクラス
        handlerList.add(new AssertHandler(commitInterval));

        // ５．アクション
        // リクエストパラメータの文字列をDBに登録するハンドラ
        handlerList.add(actionHandler);

        return handlerList;
    }

    private static class ActionHandler implements Handler<String, Result> {

        private final String transactionName;

        private ActionHandler() {
            this(TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY);
        }

        public ActionHandler(String name) {
            transactionName = name
                    == null ? TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY : name;
        }


        @Override
        public Result handle(String s, ExecutionContext context) {
            AppDbConnection connection = DbConnectionContext.getConnection(
                    transactionName);
            SqlPStatement statement = connection.prepareStatement(
                    "insert into handler_test_table (col1) values (?)");
            statement.setString(1, s);
            try {
                statement.executeUpdate();
            } catch (SqlStatementException e) {
                throw new RuntimeException("エラー発生。 request = " + s, e);
            }
            return new Success(s);
        }
    }

    /** DBへのコミットを確認するためのハンドラクラス。 */
    private static class AssertHandler implements Handler<Object, Object> {

        /** コミット間隔 */
        private final int commitInterval;

        private final ThreadLocal<List<String>> noCommitData = new ThreadLocal<List<String>>() {
            @Override
            protected List<String> initialValue() {
                return new ArrayList<String>();
            }
        };

        public AssertHandler(int commitInterval) {
            noCommitData.remove();
            this.commitInterval = commitInterval < 1 ? 1 : commitInterval;
        }

        @Override
        public Object handle(Object o, ExecutionContext context) {

        	for (String col1 : noCommitData.get()) {
        		HandlerTestTable htt = VariousDbTestHelper.findById(HandlerTestTable.class, col1);
                boolean isCommitted = htt != null;
                if (commitInterval == noCommitData.get().size()) {
                    assertThat(col1 + "はDBに登録されていること。",
                            isCommitted, is(true));
                } else {
                    assertThat(col1 + "はDBに登録されていないこと。",
                            isCommitted, is(false));
                }
            }
            if (commitInterval == noCommitData.get().size()) {
                noCommitData.get().clear();
            }
        	
            noCommitData.get().add(o.toString());
            try {
                return context.handleNext(o);
            } catch (RuntimeException e) {
                context.setSessionScopedVar("ROLLBACK_DATA",
                        new ArrayList<String>(noCommitData.get()));
                throw e;
            }
        }
    }

    /** エラー用のハンドラ */
    private static class ErrorActionHandler extends ActionHandler {

        private int pos = 0;

        private final int errorPos;

        private RuntimeException exception;

        private Error error;


        private ErrorActionHandler(int errorPos, RuntimeException e) {
            this.errorPos = errorPos;
            this.exception = e;
        }

        private ErrorActionHandler(int errorPos, Error e) {
            this.errorPos = errorPos;
            this.error = e;
        }

        @Override
        public Result handle(String s, ExecutionContext context) {
            Result result = super.handle(s, context);
            if (++pos == errorPos) {
                if (exception != null) {
                    throw exception;
                } else {
                    throw error;
                }
            }
            return result;
        }
    }

    private static class AssertToClearLastReadDataHandler implements Handler<Object, Object> {
        @Override
        public Object handle(Object data, ExecutionContext context) {
            assertNull("正常終了時は最後に読み込んだデータオブジェクトが残っていないこと。",
                    context.getLastReadData());
            return context.handleNext(data);
        }
    }
}
