package nablarch.fw.handler;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.SQLException;

import nablarch.common.handler.threadcontext.ThreadContextHandler;
import nablarch.core.db.connection.exception.DbConnectionException;
import nablarch.core.db.statement.SqlRow;
import nablarch.fw.DataReader;
import nablarch.fw.DataReaderFactory;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.handler.retry.CountingRetryContextFactory;
import nablarch.fw.handler.retry.RetryableException;
import nablarch.fw.handler.retry.TimeRetryContextFactory;
import nablarch.fw.launcher.ProcessAbnormalEnd;

import org.junit.Test;

/**
 * {@link RetryHandler}のテスト。
 * @author Kiyohito Itoh
 */
public class RetryHandlerTest {

    /**
     * 時間指定のリトライが正しく動作すること。
     */
    @Test
    public void testTimeRetry() {

        TimeRetryContextFactory retryContextFactory = new TimeRetryContextFactory();
        retryContextFactory.setRetryTime(3000); // リトライ時間は3秒
        retryContextFactory.setRetryIntervals(510); // 間隔は0.51秒

        RetryHandler handler = new RetryHandler();
        handler.setRetryContextFactory(retryContextFactory);

        // 時間内でリトライ成功
        MockHandler nextHandler = new MockHandler(7); // 7回目の実行で成功
        Object ret = handler.handle("test_data", new ExecutionContext().addHandler(nextHandler));
        assertThat(ret.toString(), is("成功[7]回目の実行"));

        // 時間内でリトライ成功せず
        // 最後までリトライ対象の例外
        nextHandler = new MockHandler(8); // 8回目の実行で成功
        try {
            handler.setRetryLimitExceededExitCode(123);
            handler.setRetryLimitExceededFailureCode("ABCD");
            handler.handle("test_data", new ExecutionContext().addHandler(nextHandler));
            fail("ProcessAbnormalEnd");
        } catch (ProcessAbnormalEnd e) {
            assertThat(e.getCause().getMessage(), is("リトライ対象の例外発生[7]回目の実行"));
            assertThat(e.getStatusCode(), is(123));
            assertThat(e.getMessageId(), is("ABCD"));
        }

        // 時間内にリトライ成功せず
        // リトライ対象でない例外が途中で発生
        nextHandler = new MockHandler(11, 3); // 3回目の実行でリトライ対象でない例外発生
        try {
            handler.handle("test_data", new ExecutionContext().addHandler(nextHandler));
            fail("RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("リトライ対象でない例外発生[3]回目の実行"));
        }
    }

    /**
     * 回数指定のリトライが正しく動作すること。
     */
    @Test
    public void testCountingRetry() {

        CountingRetryContextFactory retryContextFactory = new CountingRetryContextFactory();
        retryContextFactory.setRetryCount(5); // リトライ回数は5回
        retryContextFactory.setRetryIntervals(500); // 間隔は0.5秒

        RetryHandler handler = new RetryHandler();
        handler.setRetryLimitExceededFailureCode("DUMMY");
        handler.setRetryContextFactory(retryContextFactory);

        // 回数内でリトライ成功
        MockHandler nextHandler = new MockHandler(6); // 6回目の実行で成功
        Object ret = handler.handle("test_data", new ExecutionContext().addHandler(nextHandler));
        assertThat(ret.toString(), is("成功[6]回目の実行"));

        // 回数内にリトライ成功せず
        // 最後までリトライ対象の例外
        nextHandler = new MockHandler(7); // 7回目の実行で成功
        try {
            handler.handle("test_data", new ExecutionContext().addHandler(nextHandler));
            fail("ProcessAbnormalEnd");
        } catch (ProcessAbnormalEnd e) {
            assertThat(e.getCause().getMessage(), is("リトライ対象の例外発生[6]回目の実行"));
        }

        // 回数内にリトライ成功せず
        // リトライ対象でない例外が途中で発生
        nextHandler = new MockHandler(6, 3); // 3回目の実行でリトライ対象でない例外発生
        try {
            handler.handle("test_data", new ExecutionContext().addHandler(nextHandler));
            fail("RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("リトライ対象でない例外発生[3]回目の実行"));
        }
    }

    /**
     * リトライ間隔が正しく動作すること。
     */
    @Test
    public void testRetryIntervals() {

        CountingRetryContextFactory retryContextFactory = new CountingRetryContextFactory();
        retryContextFactory.setRetryCount(5);
        retryContextFactory.setRetryIntervals(500);

        RetryHandler handler = new RetryHandler();
        handler.setRetryLimitExceededFailureCode("DUMMY");
        handler.setRetryContextFactory(retryContextFactory);

        // 回数内にリトライ成功せず
        // 最後までリトライ対象の例外
        MockHandler nextHandler = new MockHandler(7); // 7回目の実行で成功
        long start = System.currentTimeMillis();
        try {
            handler.handle("test_data", new ExecutionContext().addHandler(nextHandler));
            fail("ProcessAbnormalEnd");
        } catch (ProcessAbnormalEnd e) {
            long end = System.currentTimeMillis();
            assertTrue((end - start) > 2400);
            assertThat(e.getCause().getMessage(), is("リトライ対象の例外発生[6]回目の実行"));
        }

        retryContextFactory = new CountingRetryContextFactory();
        retryContextFactory.setRetryCount(5);
        retryContextFactory.setRetryIntervals(0);

        handler = new RetryHandler();
        handler.setRetryLimitExceededFailureCode("DUMMY");
        handler.setRetryContextFactory(retryContextFactory);

        // 回数内にリトライ成功せず
        // 最後までリトライ対象の例外
        nextHandler = new MockHandler(7); // 7回目の実行で成功
        start = System.currentTimeMillis();
        try {
            handler.handle("test_data", new ExecutionContext().addHandler(nextHandler));
            fail("ProcessAbnormalEnd");
        } catch (ProcessAbnormalEnd e) {
            long end = System.currentTimeMillis();
            assertTrue((end - start) < 100);
            assertThat(e.getCause().getMessage(), is("リトライ対象の例外発生[6]回目の実行"));
        }
    }

    /**
     * リトライ時のリーダ破棄設定がされている場合、リトライ後にリーダが削除されていること。
     */
    @Test
    public void testDestroyedReader() throws Exception {
        RetryHandler sut = new RetryHandler();
        CountingRetryContextFactory retryContextFactory = new CountingRetryContextFactory();
        retryContextFactory.setRetryCount(1);
        retryContextFactory.setRetryIntervals(100);
        sut.setRetryContextFactory(retryContextFactory);

        ExecutionContext context = new ExecutionContext();

        context.addHandler(new Handler<Object, Object>() {
            int count = 0;
            @Override
            public Object handle(Object o, ExecutionContext context) {
                count++;
                if (count == 1) {
                    assertThat("リトライ前はリーダが設定されていること",
                            context.getDataReader(), is(notNullValue()));
                    throw new RetryableException("retry!!!");
                }

                assertThat("リトライ後はリーダが設定されていないこと", context.getDataReader(), is(nullValue()));
                return "ok";
            }
        });

        // データベースリーダを設定してテストを実施
        context.setDataReaderFactory(new DataReaderFactory<SqlRow>() {
            @Override
            public DataReader<SqlRow> createReader(ExecutionContext context) {
                return new DatabaseRecordReader();
            }
        });

        // データリーダの破棄設定をオンに
        sut.setDestroyReader(true);

        sut.handle("input", context);
    }

    /**
     * リトライ時のリーダ破棄設定がされていない場合、リトライ後にもリトライ前と同じリーダが設定されていること。
     * @throws Exception
     */
    @Test
    public void testNotDestroyedReader() throws Exception {
        RetryHandler sut = new RetryHandler();

        CountingRetryContextFactory retryContextFactory = new CountingRetryContextFactory();
        retryContextFactory.setRetryCount(1);
        retryContextFactory.setRetryIntervals(100);
        sut.setRetryContextFactory(retryContextFactory);

        ExecutionContext context = new ExecutionContext();
        context.addHandler(new Handler<Object, Object>() {
            int count = 0;
            DataReader<Object> reader;
            @Override
            public Object handle(Object o, ExecutionContext context) {
                count++;
                if (count == 1) {
                    reader = context.getDataReader();
                    assertThat("リトライ前はリーダが設定されていること",
                            reader, is(notNullValue()));
                    throw new RetryableException("retry!!!");
                }

                assertThat("リトライ後もリトライ前と同じリーダが設定されていること", context.getDataReader(), is(reader));
                return "ok";
            }
        });

        // データベースリーダを設定してテストを実施
        context.setDataReaderFactory(new DataReaderFactory<SqlRow>() {
            @Override
            public DataReader<SqlRow> createReader(ExecutionContext context) {
                return new DatabaseRecordReader();
            }
        });

        sut.setDestroyReader(false);
        sut.handle("input", context);
    }

    /**
     * リトライ時のリーダ破棄設定がデフォルトの場合、リトライ時にリーダは破棄されないこと
     */
    @Test
    public void testDefaultDestroyedReaderSetting() throws Exception {
        RetryHandler sut = new RetryHandler();

        CountingRetryContextFactory retryContextFactory = new CountingRetryContextFactory();
        retryContextFactory.setRetryCount(1);
        retryContextFactory.setRetryIntervals(100);
        sut.setRetryContextFactory(retryContextFactory);

        ExecutionContext context = new ExecutionContext();
        context.addHandler(new Handler<Object, Object>() {
            int count = 0;
            DataReader<Object> reader;
            @Override
            public Object handle(Object o, ExecutionContext context) {
                count++;
                if (count == 1) {
                    reader = context.getDataReader();
                    assertThat("リトライ前はリーダが設定されていること",
                            reader, is(notNullValue()));
                    throw new RetryableException("retry!!!");
                }

                assertThat("リトライ後もリトライ前と同じリーダが設定されていること", context.getDataReader(), is(reader));
                return "ok";
            }
        });

        // データベースリーダを設定してテストを実施
        context.setDataReaderFactory(new DataReaderFactory<SqlRow>() {
            @Override
            public DataReader<SqlRow> createReader(ExecutionContext context) {
                return new DatabaseRecordReader();
            }
        });

        sut.handle("input", context);
    }


    private static class MockHandler implements Handler<Object, Object> {
        public final int successCount;
        public final int runtimeExceptionCount;
        public int count;
        MockHandler(int successCount) {
            this(successCount, -1);
        }
        MockHandler(int successCount, int runtimeExceptionCount) {
            this.successCount = successCount;
            this.runtimeExceptionCount = runtimeExceptionCount;
        }
        public Object handle(Object data, ExecutionContext context) {

            count++;

            if (count == runtimeExceptionCount) {
                throw new IllegalStateException("リトライ対象でない例外発生[" + count + "]回目の実行",
                                                 new IllegalArgumentException("cause1"));
            }

            if (count == successCount) {
                return "成功[" + count + "]回目の実行";
            }

            Throwable cause = new DbConnectionException("リトライ対象の例外発生[" + count + "]回目の実行",
                                                        new SQLException("test_reason"));
            if (count % 2 == 0) {
                throw (DbConnectionException) cause;
            } else {
                throw new RuntimeException("リトライ対象の例外発生[" + count + "]回目の実行", cause);
            }
        }
    }

    /**
     * 後続ハンドラの処理が最長リトライ時間を超えた場合のみ、リトライ処理がリセットされること。
     */
    @Test
    public void testReset() {

        CountingRetryContextFactory retryContextFactory = new CountingRetryContextFactory();
        retryContextFactory.setRetryCount(5); // リトライ回数は5回
        retryContextFactory.setRetryIntervals(500); // リトライ間隔は0.5秒
        retryContextFactory.setMaxRetryTime(3000); // 最長リトライ時間は3秒

        RetryHandler handler = new RetryHandler();
        handler.setRetryLimitExceededFailureCode("DUMMY");
        handler.setRetryContextFactory(retryContextFactory);

        // 回数内でリトライ成功
        // 成功処理で最長リトライ時間を超える場合
        MockHandlerForReset nextHandler = new MockHandlerForReset(6, 3, 700); // 6回目の実行で成功
        
        class TestExecutionContext extends ExecutionContext {

            public TestExecutionContext() {
            }

            public TestExecutionContext(final TestExecutionContext original) {
                super(original);
            }

            @Override
            protected ExecutionContext copyInternal() {
                return new TestExecutionContext(this);
            }

            @Override
            public boolean hasNextData() {
                return true;
            }
        }
        try {
            handler.handle("test_data", new TestExecutionContext()
                                        .addHandler(new RequestThreadLoopHandler())
                                        .addHandler(new ThreadContextHandler())
                                        .addHandler(nextHandler));
            fail("ProcessAbnormalEnd");
        } catch (ProcessAbnormalEnd e) { // 止めるためにProcessAbnormalEndをスローしている。
            assertThat(nextHandler.resetCount, is(3));
        }

        // 回数内にリトライ成功せず
        // 最後までリトライ対象の例外
        nextHandler = new MockHandlerForReset(7, 3, 0); // 7回目の実行で成功
        try {
            handler.handle("test_data", new TestExecutionContext()
                                        .addHandler(new RequestThreadLoopHandler())
                                        .addHandler(new ThreadContextHandler())
                                        .addHandler(nextHandler));
            fail("ProcessAbnormalEnd");
        } catch (ProcessAbnormalEnd e) {
            assertThat(e.getCause().getMessage(), is("リトライ対象の例外発生[6]回目の実行"));
        }

        // 不正な最長リトライ時間の設定

        retryContextFactory = new CountingRetryContextFactory();
        retryContextFactory.setRetryCount(5); // リトライ回数は5回
        retryContextFactory.setRetryIntervals(500); // リトライ間隔は0.5秒
        retryContextFactory.setMaxRetryTime(2501); // 最長リトライ時間は2.501秒
        retryContextFactory.createRetryContext(); // OK

        retryContextFactory = new CountingRetryContextFactory();
        retryContextFactory.setRetryCount(5); // リトライ回数は5回
        retryContextFactory.setRetryIntervals(500); // リトライ間隔は0.5秒
        retryContextFactory.setMaxRetryTime(2500); // 最長リトライ時間は2.5秒
        try {
            retryContextFactory.createRetryContext(); // NG
            fail("IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(),
                       is("maxRetryTime was too short. "
                        + "must be set value greater than minRetryTime to maxRetryTime. "
                        + "minRetryTime = [2500], maxRetryTime = [2500]"));
        }
    }

    private static final class MockHandlerForReset extends MockHandler {
        public final int stopResetCount;
        public final long successWait;
        public int resetCount;
        MockHandlerForReset(int successCount, int stopResetCount, long successWait) {
            super(successCount);
            this.stopResetCount = stopResetCount;
            this.successWait = successWait;
        }
        @Override
        public Object handle(Object data, ExecutionContext context) {
            if (resetCount == stopResetCount) {
                throw new ProcessAbnormalEnd(100, "unknown");
            }
            if (count == successCount) {
                resetCount++;
                count = 0;
            }
            super.handle(data, context);
            try {
                Thread.sleep(successWait);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return new Result.Success("成功[" + count + "]回目の実行");
        }
    }

    private static class DatabaseRecordReader implements DataReader<SqlRow> {
        @Override
        public SqlRow read(ExecutionContext ctx) {
            return null;
        }

        @Override
        public boolean hasNext(ExecutionContext ctx) {
            return false;
        }

        @Override
        public void close(ExecutionContext ctx) {
            // nop
        }
    }
}
