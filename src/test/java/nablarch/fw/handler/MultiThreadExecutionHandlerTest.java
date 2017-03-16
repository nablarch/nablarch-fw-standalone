package nablarch.fw.handler;

import nablarch.common.handler.DbConnectionManagementHandler;
import nablarch.core.db.connection.ConnectionFactory;
import nablarch.core.transaction.TransactionFactory;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.action.BatchAction;
import nablarch.fw.launcher.CommandLine;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.log.app.OnMemoryLogWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class MultiThreadExecutionHandlerTest {

    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource("db-default.xml");

    private TransactionFactory transaction;

    private ConnectionFactory connection;

    private final List<String> activities  = Collections.synchronizedList(new ArrayList<String>());
    private final Set<String> threadNames = new HashSet<String>();
    private Result.MultiStatus results = null;

    private MultiThreadExecutionHandler executor = null;

    private BatchAction<String> action = null;

    private CountDownLatch startGate;

    @Before
    public void setup() {
    	connection = repositoryResource.getComponentByType(ConnectionFactory.class);
    	transaction = repositoryResource.getComponentByType(TransactionFactory.class);
    }

    /**
     * エラー発生時のスレッド停止処理の検証
     */
    @Test
    public void testTeminattion() {
        class TestAction extends BatchAction<String> {
            @Override
            public Result handle(String inputData, ExecutionContext ctx) {
                activities.add("handle");
                if (inputData.equals("dummyData1")) {
                    activities.add("stoooop!!");
                    // 最後の1スレッドが実行時例外を送出することにより、
                    // startGate.await()で待機中の全スレッド(99スレッド)がキャンセルされる。
                    // ※確実に他のスレッドがawaitで待機中になるように、
                    // 一定時間待機後に例外を送出する。
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    throw new IllegalArgumentException();
                }
                try {
                    startGate.countDown();
                    startGate.await();
                } catch (InterruptedException ignored) {
                    activities.add("cancelled");
                }
                return new Result.Success();
            }
            @Override
            public DataReader<String> createReader(ExecutionContext ctx) {
                return new DataReader<String>() {
                    private AtomicInteger count = new AtomicInteger(100);
                    @Override
                    public String read(ExecutionContext ctx) {
                        return "dummyData" + count.getAndDecrement();
                    }
                    @Override
                    public boolean hasNext(ExecutionContext ctx) {
                        return true;
                    }
                    @Override
                    public void close(ExecutionContext ctx) {
                        // nop
                    }
                };
            }
        }

        TestAction action = new TestAction();
        startGate = new CountDownLatch(100);
        executor = new MultiThreadExecutionHandler()
                       .setConcurrentNumber(100);

        ExecutionContext context = new ExecutionContext();
        context.setDataReaderFactory(action)
                .addHandler(executor)
                .addHandler(new DataReadHandler())
                .addHandler(action);
        try {
            context.handleNext(new CommandLine(
                    "-diConfig", "file:///dummy"
                    , "-requestPath", "dummy/dumy"
                    , "-userId", "dummyUser"
            ));
            fail();
        } catch (RuntimeException e) {
            assertEquals("発生した例外に関連するデータが親スレッドのExecutionContextに設定されていること",
                         "dummyData1",
                         context.getDataProcessedWhenThrown(e));
        }

        assertEquals(200, activities.size());
        int handled   = 0;
        int cancelled = 0;
        int stopping  = 0;
        for (String activity : activities) {
            if (activity.equals("handle")) {
                handled++;
            } else if (activity.equals("cancelled")) {
                cancelled++;
            } else if (activity.equals("stoooop!!")) {
                stopping++;
            }
        }
        assertEquals (100, handled);  // 実行スレッド数
        assertEquals (1, stopping);   // 異常終了
        assertEquals (99, cancelled); // 割り込みによる途中終了
    }

    /**
     * マルチスレッドによる並行実行機能の検証
     */
    @Test
    public void testMultithreadExecution() {

        class TestAction extends BatchAction<String> {
            @Override
            public Result handle(String item, ExecutionContext ctx) {
                threadNames.add(Thread.currentThread()
                        .getName());
                if (startGate.getCount() == 1) {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                startGate.countDown();
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                activities.add("handle");
                return new Result.Success();
            }

            @Override
            protected void initialize(CommandLine command, ExecutionContext context) {
                activities.add("initialize");
            }

            @Override
            protected void error(Throwable error, ExecutionContext context) {
                activities.add("error");
            }

            @Override
            protected void terminate(Result result, ExecutionContext context) {
                activities.add("terminate");
                results = (Result.MultiStatus) result;
            }

            @Override
            public DataReader<String> createReader(ExecutionContext ctx) {
                return new DataReader<String>() {
                    private final List<String> items = Collections.synchronizedList(new ArrayList<String>() {{
                        for (int i=1; i<=100; i++) {
                            add(String.valueOf(i));
                        }
                    }});
                    @Override
                    public synchronized String read(ExecutionContext ctx) {
                        return items.remove(0);
                    }
                    @Override
                    public synchronized boolean hasNext(ExecutionContext ctx) {
                        return !items.isEmpty();
                    }
                    @Override
                    public void close(ExecutionContext ctx) {
                        // nop
                    }
                };
            }
        }

        action = new TestAction();
        executor = new MultiThreadExecutionHandler();

        ExecutionContext context = new ExecutionContext()
                                  .setDataReaderFactory(action)
                                  .addHandler(executor)
                                  .addHandler(new DataReadHandler())
                                  .addHandler(action);


        // ----------スレッド数 = 1 ------------------
        executor.setConcurrentNumber(1);

        activities.clear();
        threadNames.clear();

        startGate = new CountDownLatch(1);
        results = null;

        context.handleNext(new CommandLine(
                  "-diConfig",    "file:///dummy"
                , "-requestPath", "dummy/dumy"
                , "-userId",      "dummyUser"
        ));

        assertEquals(3, activities.size());
        assertEquals(1, threadNames.size());

        int handle     = 0;
        int initialize = 0;
        int terminate  = 0;
        int error      = 0;
        for (String activity : activities) {
            if (activity.equals("handle")) {
                handle++;
            } else if (activity.equals("initialize")) {
                initialize++;
            } else if (activity.equals("error")) {
                error++;
            } else if (activity.equals("terminate")) {
                terminate++;
            }
        }
        assertEquals (1, handle);
        assertEquals (1, initialize);
        assertEquals (0, error);
        assertEquals (1, terminate);

        assertEquals(1, results.getResults().size());
        assertTrue(results.isSuccess());

        // ----------スレッド数 = 100 ------------------

        executor =  new MultiThreadExecutionHandler()
                       .setConcurrentNumber(100);

        context = new ExecutionContext()
                     .setDataReaderFactory(action)
                     .addHandler(executor)
                     .addHandler(new DataReadHandler())
                     .addHandler(action);

        activities.clear();
        threadNames.clear();
        results = null;

        startGate = new CountDownLatch(100);

        context.handleNext(new CommandLine(
              "-diConfig",    "file:///dummy"
            , "-requestPath", "dummy/dumy"
            , "-userId",      "dummyUser"
        ));

        assertEquals(102, activities.size());
        assertEquals(100, threadNames.size());

        handle     = 0;
        initialize = 0;
        terminate  = 0;
        error      = 0;
        for (String activity : activities) {
            if (activity.equals("handle")) {
                handle++;
            } else if (activity.equals("initialize")) {
                initialize++;
            } else if (activity.equals("error")) {
                error++;
            } else if (activity.equals("terminate")) {
                terminate++;
            }
        }
        assertEquals (100, handle);
        assertEquals (1, initialize);
        assertEquals (0, error);
        assertEquals (1, terminate);

        assertEquals(100, results.getResults().size());
        assertTrue(results.isSuccess());



        // ----------スレッド数 = 20------------------
        executor = new MultiThreadExecutionHandler()
                  .setConcurrentNumber(20);

        context = new ExecutionContext()
                     .setDataReaderFactory(action)
                     .addHandler(executor)
                     .addHandler(new DataReadHandler())
                     .addHandler(action);

        activities.clear();
        threadNames.clear();
        results = null;

        startGate = new CountDownLatch(20);

        context.handleNext(new CommandLine(
              "-diConfig",    "file:///dummy"
            , "-requestPath", "dummy/dumy"
            , "-userId",      "dummyUser"
        ));

        assertEquals(22, activities.size());
        assertEquals(20, threadNames.size());

        handle     = 0;
        initialize = 0;
        terminate  = 0;
        error      = 0;
        for (String activity : activities) {
            if (activity.equals("handle")) {
                handle++;
            } else if (activity.equals("initialize")) {
                initialize++;
            } else if (activity.equals("error")) {
                error++;
            } else if (activity.equals("terminate")) {
                terminate++;
            }
        }
        assertEquals (20, handle);
        assertEquals (1, initialize);
        assertEquals (0, error);
        assertEquals (1, terminate);

        assertEquals(20, results.getResults().size());
        assertTrue(results.isSuccess());


        //-------------- Action内でエラーが発生した。----------------

        class ErroneousAction extends TestAction {
            @Override
            public Result handle(String item, ExecutionContext ctx) {
                 Result result = super.handle(item, ctx);
                 if (activities.size() >= 21) {
                     throw new IllegalStateException(item);
                 }
                 return result;
            }
        }

        executor = new MultiThreadExecutionHandler()
                      .setConcurrentNumber(20);

        action = new ErroneousAction();

        context = new ExecutionContext()
                     .setDataReaderFactory(action)
                     .addHandler(executor)
                     .addHandler(new DataReadHandler())
                     .addHandler(action);

        activities.clear();
        threadNames.clear();

        startGate = new CountDownLatch(20);

        try {
            context.handleNext(new CommandLine(
                  "-diConfig",    "file:///dummy"
                , "-requestPath", "dummy/dumy"
                , "-userId",      "dummyUser"
            ));
            fail();

        } catch (Throwable e) {
            assertTrue(e instanceof IllegalStateException);
            assertEquals("発生した例外に関連するデータが親スレッドのExecutionContextに設定されていること",
                         e.getMessage(),
                         context.getDataProcessedWhenThrown(e));
        }

        assertEquals(23, activities.size());
        assertEquals(20, threadNames.size());

        handle     = 0;
        initialize = 0;
        terminate  = 0;
        error      = 0;
        for (String activity : activities) {
            if (activity.equals("handle")) {
                handle++;
            } else if (activity.equals("initialize")) {
                initialize++;
            } else if (activity.equals("error")) {
                error++;
            } else if (activity.equals("terminate")) {
                terminate++;
            }
        }
        assertEquals (20, handle);
        assertEquals (1, initialize);
        assertEquals (1, error);
        assertEquals (1, terminate);

        assertEquals(20, results.getResults().size());
        assertFalse(results.isSuccess());
    }

    /**
     * マルチスレッドによる並行実行機能において複数スレッドで例外が発生する場合のテスト。
     */
    @Test
    public void testMultiErrors() {

        //-------------- Action内でエラーが発生した。----------------

        final List<Throwable> eList = new ArrayList<Throwable>();

        class TestAction extends BatchAction<Map<String, String>> {
            @Override
            public Result handle(Map<String, String> data, ExecutionContext ctx) {
                threadNames.add(Thread.currentThread()
                        .getName());
                if (startGate.getCount() == 1) {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException ignore) {
                    }
                }
                startGate.countDown();
                activities.add("handle");
                RuntimeException e = new IllegalStateException(data.toString());
                eList.add(e);
                try {
                    startGate.await();
                } catch (InterruptedException ignore) {
                }
                throw e;
            }

            @Override
            protected void initialize(CommandLine command, ExecutionContext context) {
                activities.add("initialize");
            }

            @Override
            protected void error(Throwable error, ExecutionContext context) {
                activities.add("error");
            }

            @Override
            protected void terminate(Result result, ExecutionContext context) {
                activities.add("terminate");
                results = (Result.MultiStatus) result;
            }

            @Override
            public DataReader<Map<String, String>> createReader(ExecutionContext ctx) {
                return new DataReader<Map<String, String>>() {
                    private final List<String> items = Collections.synchronizedList(new ArrayList<String>() {{
                        for (int i=1; i<=100; i++) {
                            add(String.valueOf(i));
                        }
                    }});
                    @Override
                    public Map<String, String> read(ExecutionContext ctx) {
                        final String num = items.remove(0);
                        Map<String, String> data = new TreeMap<String, String>() {
                            {
                                put("preReqId", "preReqId" + num);
                                put("preExeId", "preExeId" + num);
                                put("preUsrId", "preUsrId" + num);
                            }
                        };
                        return data;
                    }
                    @Override
                    public boolean hasNext(ExecutionContext ctx) {
                        return !items.isEmpty();
                    }
                    @Override
                    public void close(ExecutionContext ctx) {
                        // nop
                    }
                };
            }
        }

        BatchAction<Map<String, String>> act = new TestAction();

        executor = new MultiThreadExecutionHandler()
                      .setConcurrentNumber(20);

        ExecutionContext context = new ExecutionContext()
                                     .setDataReaderFactory(act)
                                     .addHandler(executor)
                                     .addHandler(new DataReadHandler())
                                     .addHandler(act);

        activities.clear();
        threadNames.clear();

        startGate = new CountDownLatch(20);

        OnMemoryLogWriter.clear();

        try {
            context.handleNext(new CommandLine(
                  "-diConfig",    "file:///dummy"
                , "-requestPath", "dummy/dumy"
                , "-userId",      "dummyUser"
            ));
            fail();
        } catch (Throwable e) {
//                e.printStackTrace();
            assertTrue(e instanceof IllegalStateException);
            assertEquals("発生した例外に関連するデータが親スレッドのExecutionContextに設定されていること",
                         e.getMessage(),
                         context.getDataProcessedWhenThrown(e).toString());
        }

        for (Throwable e : eList) {
            assertEquals("発生したすべての例外に関連するデータが親スレッドのExecutionContextに設定されていること",
                         e.getMessage(),
                         context.getDataProcessedWhenThrown(e).toString());
        }

        List<String> monitorLogs = OnMemoryLogWriter.getMessages("writer.monitorLog");
        assertEquals(0, monitorLogs.size());

        List<String> appLogs = OnMemoryLogWriter.getMessages("writer.appLog");
        int warnLog = 0;
        Pattern p = Pattern.compile(".*WARN.*fail_code.*");
        for (String appLog : appLogs) {
            if (p.matcher(appLog).find()) {
                warnLog++;
            }
        }
        assertEquals("WARNレベルの障害解析ログが出力されること", 20, warnLog);

        assertEquals(23, activities.size());
        assertEquals(20, threadNames.size());

        int handle     = 0;
        int initialize = 0;
        int terminate  = 0;
        int error      = 0;
        for (String activity : activities) {
            if (activity.equals("handle")) {
                handle++;
            } else if (activity.equals("initialize")) {
                initialize++;
            } else if (activity.equals("error")) {
                error++;
            } else if (activity.equals("terminate")) {
                terminate++;
            }
        }
        assertEquals (20, handle);
        assertEquals (1, initialize);
        assertEquals (1, error);
        assertEquals (1, terminate);

        assertEquals(1, results.getResults().size());
        assertFalse(results.isSuccess());
    }

    /**
     * コールバック機構が正しく動作していることを検証する。
     */
    @Test
    public void testCallbackPoints() {
        class TestAction extends BatchAction<String> {
            @Override
            public Result handle(String inputData, ExecutionContext ctx) {
                activities.add("handle");
                return new Result.Success();
            }
            @Override
            public DataReader<String> createReader(ExecutionContext ctx) {
                return new DataReader<String>() {
                    private final List<String> items = new ArrayList<String>() {{
                            add("hoge");
                            add("fuga");
                            add("piyo");
                    }};
                    @Override
                    public String read(ExecutionContext ctx) {
                        return items.remove(0);
                    }
                    @Override
                    public boolean hasNext(ExecutionContext ctx) {
                        return !items.isEmpty();
                    }
                    @Override
                    public void close(ExecutionContext ctx) {
                        // nop
                    }
                };
            }
            @Override
            protected void initialize(CommandLine command, ExecutionContext context) {
                activities.add("initialize");
            }
            @Override
            protected void error(Throwable error, ExecutionContext context) {
                activities.add("onError");
            }
            @Override
            protected void terminate(Result result, ExecutionContext context) {
                activities.add("terminate");
            }
        };

        action = new TestAction();

        executor = new MultiThreadExecutionHandler()
                      .setConcurrentNumber(1);

        createExecutionContext().handleNext(new CommandLine(
                  "-diConfig",    "file:///dummy"
                , "-requestPath", "dummy/dumy"
                , "-userId",      "dummyUser"
        ));

        assertEquals(5, activities.size());
        assertEquals("initialize", activities.get(0)); // 初期処理
        assertEquals("handle",     activities.get(1));
        assertEquals("handle",     activities.get(2));
        assertEquals("handle",     activities.get(3));
        assertEquals("terminate",  activities.get(4)); // 終了処理

        activities.clear();


        // コールバックのリスナが複数存在する場合。
        createExecutionContext()
        .addHandler(new TestAction())
        .handleNext(new CommandLine(
                "-diConfig",    "file:///dummy"
              , "-requestPath", "dummy/dumy"
              , "-userId",      "dummyUser"
        ));

        assertEquals(7, activities.size());
        assertEquals("initialize", activities.get(0)); // 初期処理
        assertEquals("initialize", activities.get(1)); // 初期処理
        assertEquals("handle",     activities.get(2));
        assertEquals("handle",     activities.get(3));
        assertEquals("handle",     activities.get(4));
        assertEquals("terminate",  activities.get(5)); // 終端処理
        assertEquals("terminate",  activities.get(6)); // 終端処理

        action = new TestAction() {
            @Override
            public Result handle(String inputData, ExecutionContext ctx) {
                activities.add("handle");
                throw new IllegalStateException("error@action");
            }
            @Override
            protected void error(Throwable error, ExecutionContext context) {
                activities.add("onError:" + error.getCause().getClass().getSimpleName());
            }
        };

        executor = new MultiThreadExecutionHandler()
                      .setConcurrentNumber(1);
        activities.clear();
        try {
            createExecutionContext().handleNext(new CommandLine(
                    "-diConfig",    "file:///dummy"
                  , "-requestPath", "dummy/dumy"
                  , "-userId",      "dummyUser"
            ));
            fail();
        } catch (RuntimeException e) {
            // nop
        }

        assertEquals(4, activities.size());
        assertEquals("initialize", activities.get(0)); // 初期処理
        assertEquals("handle",     activities.get(1));
        assertEquals("onError:IllegalStateException",
                                   activities.get(2)); // エラー処理
        assertEquals("terminate",  activities.get(3)); // 終了処理

        activities.clear();


        // 業務処理が異常終了後、エラーコールバック内で再度実行時例外が発生する場合。
        action = new TestAction() {
            @Override
            public Result handle(String inputData, ExecutionContext ctx) {
                activities.add("handle");
                throw new IllegalStateException("error@action");
            }
            @Override
            protected void error(Throwable error, ExecutionContext context) {
                activities.add(
                    "onError:" + error.getCause().getClass().getSimpleName() + " (causes an error!)"
                );
                throw new RuntimeException("error in an error callback.");
            }
        };

        executor = new MultiThreadExecutionHandler()
                      .setConcurrentNumber(1);

        try {
            createExecutionContext().handleNext(new CommandLine(
                "-diConfig",    "file:///dummy"
              , "-requestPath", "dummy/dumy"
              , "-userId",      "dummyUser"
            ));
            fail();

        } catch (RuntimeException e) {
            assertEquals(
                "コールバックメソッド内で例外が発生した場合でも元例外が再送出されること"
              , "error@action"
              , e.getMessage()
            );
        }

        assertEquals(4, activities.size());
        assertEquals("initialize", activities.get(0)); // 初期処理
        assertEquals("handle",     activities.get(1));
        assertEquals("onError:IllegalStateException (causes an error!)",
                                   activities.get(2)); // エラー処理
        assertEquals("terminate",  activities.get(3)); // 終了処理

        activities.clear();


        // 業務処理が正常終了後、ターミネートコールバック内で実行時例外が発生する場合。
        action = new TestAction() {
            @Override
            public Result handle(String inputData, ExecutionContext ctx) {
                activities.add("handle");
                return new Result.Success();
            }
            @Override
            protected void terminate(Result result, ExecutionContext context) {
                activities.add(
                    "terminate (causes an error!)"
                );
                throw new IllegalStateException("error@terminate");
            }
        };

        executor = new MultiThreadExecutionHandler()
                      .setConcurrentNumber(1);

        try {
            createExecutionContext().handleNext(new CommandLine(
                "-diConfig",    "file:///dummy"
              , "-requestPath", "dummy/dumy"
              , "-userId",      "dummyUser"
            ));
            fail();

        } catch (RuntimeException e) {
            assertEquals(
                IllegalStateException.class
              , e.getClass()
            );
            assertEquals(
                "ターミネート処理内で例外が発生した場合は、本処理が正常終了していればその例外を送出する。"
              , "error@terminate"
              , e.getMessage()
            );
        }

        assertEquals(5, activities.size());
        assertEquals("initialize", activities.get(0)); // 初期処理
        assertEquals("handle",     activities.get(1));
        assertEquals("handle",     activities.get(2));
        assertEquals("handle",     activities.get(3));
        assertEquals("terminate (causes an error!)",  activities.get(4)); // 終了処理

        activities.clear();


        // 業務処理が異常終了後、エラーコールバック内で再度実行時例外が発生しても、
        // 全てのエラーコールバックが呼ばれること。

        class BeforeHandler
        implements Handler<String, Result>, ExecutionHandlerCallback<CommandLine, Result> {
            @Override
            public void preExecution(CommandLine data, ExecutionContext context) {
                activities.add("preExecution@BeforeHandler");
            }

            @Override
            public void errorInExecution(Throwable error, ExecutionContext context) {
                activities.add("errorInExecution@BeforeHandler (causes an error)");
                throw new RuntimeException("error in an errorInExecution callback.");
            }

            @Override
            public void postExecution(Result result, ExecutionContext context) {
                activities.add("postExecution@BeforeHandler");
            }

            @Override
            public Result handle(String data, ExecutionContext context) {
                activities.add("handle@BeforeHandler");
                return context.handleNext(data);
            }
        }

        BeforeHandler beforeHandler1 = new BeforeHandler();
        BeforeHandler beforeHandler2 = new BeforeHandler();

        Handler<String, Result> testAction = new TestAction() {
            @Override
            public Result handle(String inputData, ExecutionContext ctx) {
                activities.add("handle@TestAction (causes an error)");
                throw new IllegalStateException("error@TestAction#handle");
            }
            @Override
            protected void initialize(CommandLine command, ExecutionContext context) {
                activities.add("initialize@TestAction");
            }
            @Override
            protected void error(Throwable error, ExecutionContext context) {
                activities.add("error@TestAction:" + error.getCause().getClass().getSimpleName());
            }
            @Override
            protected void terminate(Result result, ExecutionContext context) {
                activities.add("terminate@TestAction");
            }
        };

        executor = new MultiThreadExecutionHandler()
                      .setConcurrentNumber(1);

        ExecutionContext ctx = new ExecutionContext()
                                  .setDataReaderFactory(action)
                                  .addHandler(executor)
                                  .addHandler(
                                       new DbConnectionManagementHandler()
                                          .setConnectionFactory(connection)
                                   )
                                  .addHandler(
                                       new LoopHandler()
                                          .setTransactionFactory(transaction)
                                   )
                                  .addHandler(new DataReadHandler())
                                  .addHandler(beforeHandler1)
                                  .addHandler(beforeHandler2)
                                  .addHandler(testAction);

        try {
            ctx.handleNext(new CommandLine(
                "-diConfig",    "file:///dummy"
              , "-requestPath", "dummy/dumy"
              , "-userId",      "dummyUser"
            ));
            fail();

        } catch (RuntimeException e) {
            assertEquals(
                "エラーコールバックメソッド内で例外が発生した場合でも元例外が再送出されること"
              , "error@TestAction#handle"
              , e.getMessage()
            );
            assertEquals(
                IllegalStateException.class
              , e.getClass()
            );
        }

        assertEquals(12, activities.size());
        assertEquals("preExecution@BeforeHandler",                       activities.get(0)); // 初期処理
        assertEquals("preExecution@BeforeHandler",                       activities.get(1));
        assertEquals("initialize@TestAction",                            activities.get(2));
        assertEquals("handle@BeforeHandler",                             activities.get(3)); // 本処理
        assertEquals("handle@BeforeHandler",                             activities.get(4));
        assertEquals("handle@TestAction (causes an error)",              activities.get(5));
        assertEquals("errorInExecution@BeforeHandler (causes an error)", activities.get(6)); // エラー処理
        assertEquals("errorInExecution@BeforeHandler (causes an error)", activities.get(7));
        assertEquals("error@TestAction:IllegalStateException",           activities.get(8));
        assertEquals("postExecution@BeforeHandler",                      activities.get(9)); // 終端処理
        assertEquals("postExecution@BeforeHandler",                      activities.get(10));
        assertEquals("terminate@TestAction",                             activities.get(11));

        activities.clear();
    }


    /**
     * データリーダインスタンスの取得を失敗した場合
     */
    @Test
    public void testRaisesAnErrorIfAnyDataReaderFactoryWasNotAvailable() {
        try {
            new ExecutionContext()
            .addHandler(new MultiThreadExecutionHandler())
            .handleNext(new CommandLine(
                "-diConfig",    "file:///dummy"
              , "-requestPath", "dummy/dumy"
              , "-userId",      "dummyUser"
            ));
            fail();

        } catch(Throwable t) {
            assertEquals(IllegalStateException.class, t.getClass());
            assertEquals(
                "Any DataReader or DataReaderFactory is not available."
              , t.getMessage()
            );
        }
    }

    /**
     * スレッド数に不正な値が設定された場合。
     */
    @Test
    public void testRaisesAnErrorWhenConcurrentNumberIsSmallerThanOrEqualsToZero() {
        try {
            new MultiThreadExecutionHandler().setConcurrentNumber(0);
            fail();

        } catch (Throwable t) {
            assertEquals(IllegalArgumentException.class, t.getClass());
            assertEquals(
                "concurrentNumber must be greater than or equal to 1."
              , t.getMessage()
            );
        }

    }


    private ExecutionContext createExecutionContext() {
        return new ExecutionContext()
              .setDataReaderFactory(action)
              .addHandler(executor)
              .addHandler(new DbConnectionManagementHandler().setConnectionFactory(connection))
              .addHandler(new LoopHandler().setTransactionFactory(transaction))
              .addHandler(new DataReadHandler())
              .addHandler(action);
    }
}
