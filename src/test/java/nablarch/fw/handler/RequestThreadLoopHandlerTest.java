package nablarch.fw.handler;


import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.hamcrest.CoreMatchers;

import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.StandaloneExecutionContext;
import nablarch.fw.handler.retry.RetryableException;
import nablarch.fw.results.ServiceUnavailable;
import nablarch.test.support.log.app.OnMemoryLogWriter;

import org.junit.Test;

/**
 * {@link RequestThreadLoopHandler}のテスト。
 *
 * @author Kiyohito Itoh
 */
public class RequestThreadLoopHandlerTest {

    @Test
    public void testHandleNextWithNoData() {
        ExecutionContext ctx = new ExecutionContext();
        RequestThreadLoopHandler handler = new RequestThreadLoopHandler();
        assertNull(handler.handle(new Object(), ctx));
    }

    @Test
    public void testHandleNextWithData() {

        ExecutionContext ctx = new ExecutionContext();

        List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>();
        handlerQueue.add(
                new Handler<String, Result>() {
                    public Result handle(String request, ExecutionContext context) {
                        return new Result.Success(context.readNextData().toString());
                    }
                }
        );
        ctx.addHandlers(handlerQueue);

        ctx.setDataReader(new TestDataReader(createReadData(5)));

        RequestThreadLoopHandler handler = new RequestThreadLoopHandler();
        Result result = handler.handle("test", ctx);

        assertThat(result.getMessage(), is("5"));
    }

    @Test
    public void testServiceUnavailableWithNoRetryInterval() {

        ExecutionContext ctx = new ExecutionContext();

        List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>();
        handlerQueue.add(
                new Handler<String, Result>() {
                    public Result handle(String request, ExecutionContext context) {
                        String val = context.readNextData().toString();
                        if (val.equals("3")) {
                            throw new ServiceUnavailable("test_testServiceUnavailableWithNoRetryInterval");
                        }
                        return new Result.Success(val);
                    }
                }
        );
        ctx.addHandlers(handlerQueue);

        ctx.setDataReader(new TestDataReader(createReadData(5)));

        RequestThreadLoopHandler handler = new RequestThreadLoopHandler();
        handler.setServiceUnavailabilityRetryInterval(-1);

        OnMemoryLogWriter.clear();
        Result result = handler.handle("test", ctx);
        assertTrue(OnMemoryLogWriter.getMessages("writer.appLog").isEmpty());

        assertThat(result.getMessage(), is("5"));
    }

    @Test
    public void testServiceError() {

        ExecutionContext ctx = new ExecutionContext();

        List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>();
        handlerQueue.add(
                new Handler<String, Result>() {
                    public Result handle(String request, ExecutionContext context) {
                        String val = context.readNextData().toString();
                        if (val.equals("3")) {
                            throw new nablarch.fw.results.InternalError("test_testServiceError");
                        }
                        return new Result.Success(val);
                    }
                }
        );
        ctx.addHandlers(handlerQueue);

        ctx.setDataReader(new TestDataReader(createReadData(5)));

        RequestThreadLoopHandler handler = new RequestThreadLoopHandler();

        OnMemoryLogWriter.clear();
        try {
             handler.handle("test", ctx);
             fail();
        } catch (RetryableException e) {
            assertThat(e.getCause().getMessage(), is("test_testServiceError"));
        }
        String log = OnMemoryLogWriter.getMessages("writer.appLog").get(0);
        assertTrue(log.contains("FATAL"));
        assertTrue(log.contains("test_testServiceError"));
    }

    @Test
    public void testThreadDeath() {

        ExecutionContext ctx = new ExecutionContext();

        List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>();
        handlerQueue.add(
                new Handler<String, Result>() {
                    public Result handle(String request, ExecutionContext context) {
                        String val = context.readNextData().toString();
                        if (val.equals("3")) {
                            throw new ThreadDeath();
                        }
                        return new Result.Success(val);
                    }
                }
        );
        ctx.addHandlers(handlerQueue);

        ctx.setDataReader(new TestDataReader(createReadData(5)));

        RequestThreadLoopHandler handler = new RequestThreadLoopHandler();

        OnMemoryLogWriter.clear();
        try {
             handler.handle("test", ctx);
             fail();
        } catch (ThreadDeath e) {
            assertThat(e.getClass().getSimpleName(), is(ThreadDeath.class.getSimpleName()));
        }
        String log = OnMemoryLogWriter.getMessages("writer.appLog").get(0);
        assertTrue(log.contains("INFO"));
        assertTrue(log.contains("Uncaught error: "));
    }

    @Test
    public void testStackOverflowError() {

        ExecutionContext ctx = new ExecutionContext();

        List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>();
        handlerQueue.add(
                new Handler<String, Result>() {
                    public Result handle(String request, ExecutionContext context) {
                        String val = context.readNextData().toString();
                        if (val.equals("3")) {
                            throw new StackOverflowError("test_testStackOverflowError");
                        }
                        return new Result.Success(val);
                    }
                }
        );
        ctx.addHandlers(handlerQueue);

        ctx.setDataReader(new TestDataReader(createReadData(5)));

        RequestThreadLoopHandler handler = new RequestThreadLoopHandler();

        OnMemoryLogWriter.clear();
        try {
             handler.handle("test", ctx);
             fail();
        } catch (RetryableException e) {
            assertThat(e.getCause().getMessage(), is("test_testStackOverflowError"));
        }
        String log = OnMemoryLogWriter.getMessages("writer.appLog").get(0);
        assertTrue(log.contains("FATAL"));
        assertTrue(log.contains("test_testStackOverflowError"));
    }

    @Test
    public void testOutOfMemoryError() {

        ExecutionContext ctx = new ExecutionContext();

        List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>();
        handlerQueue.add(
                new Handler<String, Result>() {
                    public Result handle(String request, ExecutionContext context) {
                        String val = context.readNextData().toString();
                        if (val.equals("3")) {
                            throw new OutOfMemoryError("test_testOutOfMemoryError");
                        }
                        return new Result.Success(val);
                    }
                }
        );
        ctx.addHandlers(handlerQueue);

        ctx.setDataReader(new TestDataReader(createReadData(5)));

        RequestThreadLoopHandler handler = new RequestThreadLoopHandler();

        OnMemoryLogWriter.clear();
        try {
             handler.handle("test", ctx);
             fail();
        } catch (RetryableException e) {
            assertThat(e.getCause().getMessage(), is("test_testOutOfMemoryError"));
        }
        String log = OnMemoryLogWriter.getMessages("writer.appLog").get(0);
        assertTrue(log.contains("FATAL"));
        assertTrue(log.contains("test_testOutOfMemoryError"));
    }

    @Test
    public void testVirtualMachineError() {

        ExecutionContext ctx = new ExecutionContext();

        List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>();
        handlerQueue.add(
                new Handler<String, Result>() {
                    public Result handle(String request, ExecutionContext context) {
                        String val = context.readNextData().toString();
                        if (val.equals("3")) {
                            throw new UnknownError("test_testVirtualMachineError");
                        }
                        return new Result.Success(val);
                    }
                }
        );
        ctx.addHandlers(handlerQueue);

        ctx.setDataReader(new TestDataReader(createReadData(5)));

        RequestThreadLoopHandler handler = new RequestThreadLoopHandler();

        OnMemoryLogWriter.clear();
        try {
             handler.handle("test", ctx);
             fail();
        } catch (UnknownError e) {
            assertThat(e.getClass().getSimpleName(), is(UnknownError.class.getSimpleName()));
        }
        assertTrue(OnMemoryLogWriter.getMessages("writer.appLog").isEmpty());
    }

    @Test
    public void testError() {

        ExecutionContext ctx = new ExecutionContext();

        List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>();
        handlerQueue.add(
                new Handler<String, Result>() {
                    public Result handle(String request, ExecutionContext context) {
                        String val = context.readNextData().toString();
                        if (val.equals("3")) {
                            throw new Error("test_testError");
                        }
                        return new Result.Success(val);
                    }
                }
        );
        ctx.addHandlers(handlerQueue);

        ctx.setDataReader(new TestDataReader(createReadData(5)));

        RequestThreadLoopHandler handler = new RequestThreadLoopHandler();

        OnMemoryLogWriter.clear();
        try {
             handler.handle("test", ctx);
             fail();
        } catch (RetryableException e) {
            assertThat(e.getCause().getMessage(), is("test_testError"));
        }
        String log = OnMemoryLogWriter.getMessages("writer.appLog").get(0);
        assertTrue(log.contains("FATAL"));
        assertTrue(log.contains("test_testError"));
    }

    @Test
    public void 後続のハンドラにはこのハンドラのインプットとなるExecutionContextのコピーが渡されること() {

        final StandaloneExecutionContext originalContext = new StandaloneExecutionContext();
        originalContext.setDataReader(new TestDataReader(createReadData(1)));

        originalContext.addHandler(new DataReadHandler());
        originalContext.addHandler(new Handler<Object, Object>() {
            @Override
            public Object handle(final Object o, final ExecutionContext context) {
                assertThat("RequestThreadLoopHandlerに指定したExecutionContextと同じクラスが後続のハンドラに渡されること",
                        context, CoreMatchers.<ExecutionContext>instanceOf(originalContext.getClass()));
                return new Result.Success();
            }
        });

        final RequestThreadLoopHandler sut = new RequestThreadLoopHandler();
        sut.handle(null, originalContext);
    }

    /** テスト用のデータを作成する。 */
    private static List<String> createReadData(int dataCount) {
        List<String> data = new ArrayList<String>();
        for (int i = 1; i <= dataCount; i++) {
            data.add(String.valueOf(i));
        }
        return data;
    }

    /** テスト用のデータリーダ */
    private class TestDataReader implements DataReader<String> {

        private List<String> list = new LinkedList<String>();

        private TestDataReader(List<String> list) {
            this.list = list;
        }

        public synchronized boolean hasNext(ExecutionContext ctx) {
            return !list.isEmpty();
        }

        public synchronized String read(ExecutionContext ctx) {
            if (list.isEmpty()) {
                return null;
            }
            return list.remove(0);
        }
        
        public void close(ExecutionContext ctx) {
            // nop
        }
    }
}
