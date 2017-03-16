package nablarch.fw.handler;

import nablarch.core.ThreadContext;

import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.Result.NotFound;
import nablarch.test.support.log.app.OnMemoryLogWriter;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * {@link DataReadHandler}のテスト。
 *
 * @author hisaaki sioiri
 */
public class DataReadHandlerTest {

    /** {@link DataReadHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。 */
    @Test
    public void testWriteWarnLog() {

        DataReadHandler target = new DataReadHandler();
        Throwable t = new RuntimeException("DataReadHandlerTest.testWarnLogWithData()");

        /* リクエストデータあり */
        Object requestData = String.valueOf("test");

        OnMemoryLogWriter.clear();
        target.writeWarnLog(requestData, t);
        String log = OnMemoryLogWriter.getMessages("writer.appLog").get(0);

        // 1L: 2013-05-30 14:58:37.814 -WARN- ROO [201305301458320740001] req_id = [null] usr_id = [null] application was abnormal end.
        // 2L:  input data = 1
        assertTrue(log.contains("WARN"));
        assertTrue(log.contains("application was abnormal end."));
        assertTrue(log.contains("input data = test"));

        /* リクエストデータなし */
        requestData = null;

        OnMemoryLogWriter.clear();
        target.writeWarnLog(requestData, t);
        log = OnMemoryLogWriter.getMessages("writer.appLog").get(0);

        // 1L: 2013-05-30 14:58:37.814 -WARN- ROO [201305301458320740001] req_id = [null] usr_id = [null] application was abnormal end.
        // 2L:  input data = 1
        assertTrue(log.contains("WARN"));
        assertTrue(log.contains("application was abnormal end."));
        assertTrue(log.contains("input data = null"));
    }

    /** {@link DataReadHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。 */
    @Test
    public void testHandle() {
        final List<String> execIds = new ArrayList<String>();
        List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>();
        // リクエストデータをResultに詰めて返却するハンドラを後続ハンドラに設定する。
        // テストでは、後続のハンドラが返却したResultオブジェクトの中身をアサートする。
        handlerQueue.add(
                new Handler<String, Result>() {
                    public Result handle(String request, ExecutionContext context) {
                        execIds.add(ThreadContext.getExecutionId());
                        return new Result.Success(request);
                    }
                }
        );
        
        // データリーダ用のインプットデータを定義する。
        List<String> data = createReadData(5);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerQueue);
        context.setDataReader(new TestDataReader(data));

        DataReadHandler target = new DataReadHandler();
        Result result = target.handle(null, context);
        assertThat("1レコード目", result.getMessage(), is("1"));

        context.setHandlerQueue(handlerQueue);
        result = target.handle(null, context);
        assertThat("2レコード目", result.getMessage(), is("2"));

        context.setHandlerQueue(handlerQueue);
        result = target.handle(null, context);
        assertThat("3レコード目", result.getMessage(), is("3"));

        context.setHandlerQueue(handlerQueue);
        result = target.handle(null, context);
        assertThat("4レコード目", result.getMessage(), is("4"));

        context.setHandlerQueue(handlerQueue);
        result = target.handle(null, context);
        assertThat("5レコード目", result.getMessage(), is("5"));

        context.setHandlerQueue(handlerQueue);
        result = target.handle(null, context);
        assertThat("対象レコードなしの場合", result.getMessage(), is(
                "all data has been processed."));
        assertThat(result, is(instanceOf(NotFound.class)));
        
        // 実行時IDが正しく採番されていることを検証する。
        assertEquals(5, execIds.size());
        Set<String> ids = new HashSet<String>();
        for (String execId : execIds) {
            assertTrue(execId.matches("\\d{21}"));
            ids.add(execId);
        }
        assertEquals("採番されたIDはデータごとに全て異なる", 5, ids.size());
    }

    /** 最大件数を指定した場合のテスト。 */
    @Test
    public void testSpecifiedMaxCount() {
        List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>();
        // リクエストデータをResultに詰めて返却するハンドラを後続ハンドラに設定する。
        // テストでは、後続のハンドラが返却したResultオブジェクトの中身をアサートする。
        handlerQueue.add(
                new Handler<String, Result>() {
                    public Result handle(String request,
                                         ExecutionContext context) {
                        return new Result.Success(request);
                    }
                });

        // データリーダ用のインプットデータを定義する。
        List<String> data = createReadData(5);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerQueue);
        context.setDataReader(new TestDataReader(data));

        DataReadHandler target = new DataReadHandler();
        target.setMaxCount(3);
        Result result = target.handle(null, context);
        assertThat("1レコード目", result.getMessage(), is("1"));

        context.setHandlerQueue(handlerQueue);
        result = target.handle(null, context);
        assertThat("2レコード目", result.getMessage(), is("2"));

        context.setHandlerQueue(handlerQueue);
        result = target.handle(null, context);
        assertThat("3レコード目", result.getMessage(), is("3"));

        // 最大処理件数が3なので、4レコード目では対象レコードなしとなる。
        context.setHandlerQueue(handlerQueue);
        result = target.handle(null, context);
        assertThat("最大レコードまで処理が終わった場合", result.getMessage(), is(
                "all data has been processed."));
        assertThat(result, is(instanceOf(NotFound.class)));

    }

    /**
     * 最大件数を指定した場合のテスト。
     * マルチスレッドのテスト
     */
    @Test
    public void testMultiThread() {

        final List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>();
        // リクエストデータをResultに詰めて返却するハンドラを後続ハンドラに設定する。
        // テストでは、後続のハンドラが返却したResultオブジェクトの中身をアサートする。
        handlerQueue.add(
                new Handler<String, Result>() {
                    public Result handle(String request,
                                         ExecutionContext context) {
                        System.out.println("request = " + request);
                        if (request == null) {
                            throw new IllegalArgumentException("must not null");
                        }
                        return new Result.Success(request);
                    }
                });

        // データリーダ用のインプットデータを定義する。
        final List<String> data = createReadData(5000);

        final ExecutionContext context = new ExecutionContext();
        context.setDataReader(new TestDataReader(data));

        final DataReadHandler target = new DataReadHandler();

        final List<String> result = Collections.synchronizedList(
                new ArrayList<String>(5000));

        int initialCapacity = 20;
        List<Future<Void>> futures = new ArrayList<Future<Void>>(
                initialCapacity);
        ExecutorService service = Executors.newFixedThreadPool(initialCapacity);
        for (int i = 0; i < initialCapacity; i++) {
            Future<Void> submit = service.submit(new Callable<Void>() {
                public Void call() throws Exception {
                    ExecutionContext copyContext = new ExecutionContext(
                            context);
                    while (copyContext.hasNextData()) {
                        copyContext.setHandlerQueue(handlerQueue);
                        Result handle = target.handle(null, copyContext);
                        if (!handle.getMessage().contains("all")) {
                            result.add(handle.getMessage());
                        }
                    }
                    return null;
                }
            });
            futures.add(submit);
        }
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        service.shutdownNow();

        assertThat(result, new TypeSafeMatcher<List<String>>() {

            private List<String> expected = createReadData(5000);

            public void describeTo(Description description) {
                description.appendValue(expected);
            }

            @Override
            public boolean matchesSafely(List<String> strings) {
                if (strings.size() != 5000) {
                    return false;
                }
                for (String s : strings) {
                    if (!expected.contains(s)) {
                        return false;
                    }
                }
                return true;
            }
        });
    }


    /** 最大件数を指定した場合のテスト。 */
    @Test
    public void testSpecifiedMaxCountForMultiThread() {

        final List<Handler<?, ?>> handlerQueue = new ArrayList<Handler<?, ?>>();
        // リクエストデータをResultに詰めて返却するハンドラを後続ハンドラに設定する。
        // テストでは、後続のハンドラが返却したResultオブジェクトの中身をアサートする。
        handlerQueue.add(
                new Handler<String, Result>() {
                    public Result handle(String request,
                                         ExecutionContext context) {
                        System.out.println("request = " + request);
                        if (request == null) {
                            throw new IllegalArgumentException("must not null");
                        }
                        return new Result.Success(request);
                    }
                });

        // データリーダ用のインプットデータを定義する。
        final List<String> data = createReadData(5000);

        final ExecutionContext context = new ExecutionContext();
        context.setDataReader(new TestDataReader(data));

        final DataReadHandler target = new DataReadHandler();
        target.setMaxCount(100);

        final List<String> result = Collections.synchronizedList(
                new ArrayList<String>(100));

        int initialCapacity = 20;
        List<Future<Void>> futures = new ArrayList<Future<Void>>(
                initialCapacity);
        ExecutorService service = Executors.newFixedThreadPool(initialCapacity);
        for (int i = 0; i < initialCapacity; i++) {
            Future<Void> submit = service.submit(new Callable<Void>() {
                public Void call() throws Exception {
                    ExecutionContext copyContext = new ExecutionContext(
                            context);
                    while (copyContext.hasNextData()) {
                        copyContext.setHandlerQueue(handlerQueue);
                        Result handle = target.handle(null, copyContext);
                        if (!handle.getMessage().contains("all")) {
                            result.add(handle.getMessage());
                        }
                    }
                    return null;
                }
            });
            futures.add(submit);
        }
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        service.shutdownNow();

        assertThat(result, new TypeSafeMatcher<List<String>>() {

            private List<String> expected = createReadData(100);

            public void describeTo(Description description) {
                description.appendValue(expected);
            }

            @Override
            public boolean matchesSafely(List<String> strings) {
                if (strings.size() != 100) {
                    return false;
                }
                for (String s : strings) {
                    if (!expected.contains(s)) {
                        return false;
                    }
                }
                return true;
            }
        });
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

    /** テスト用のデータを作成する。 */
    private static List<String> createReadData(int dataCount) {
        List<String> data = new ArrayList<String>();
        for (int i = 1; i <= dataCount; i++) {
            data.add(String.valueOf(i));
        }
        return data;
    }
}
