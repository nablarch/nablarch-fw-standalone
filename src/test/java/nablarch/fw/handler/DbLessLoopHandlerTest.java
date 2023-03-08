package nablarch.fw.handler;

import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * {@link DbLessLoopHandler}のテストクラス。
 */
public class DbLessLoopHandlerTest {

    @Test
    public void データが存在する間は後続ハンドラが実行される() {
        ExecutionContext context = new ExecutionContext();
        context.setDataReader(new TestDataReader());
        List<Handler<?, ?>> handlers = new ArrayList<Handler<?, ?>>();
        handlers.add(new DbLessLoopHandler());
        handlers.add(new DataReadHandler());
        TestActionHandler actionHandler = new TestActionHandler();
        handlers.add(actionHandler);
        context.setHandlerQueue(handlers);

        context.handleNext(null);

        assertEquals(3, actionHandler.calledCount());
    }

    private static class TestDataReader implements DataReader<String> {

        private final LinkedList<String> data = new LinkedList<String>();

        public TestDataReader() {
            for (int i = 0; i < 3; i++) {
                data.add(String.valueOf(i));
            }
        }

        @Override
        public String read(ExecutionContext ctx) {
            return data.removeFirst();
        }

        @Override
        public boolean hasNext(ExecutionContext ctx) {
            return !data.isEmpty();
        }

        @Override
        public void close(ExecutionContext ctx) {
            // nop
        }
    }

    private static class TestActionHandler implements Handler<String, Result> {

        private final LinkedList<String> data = new LinkedList<String>();

        @Override
        public Result handle(String s, ExecutionContext context) {
            data.add(s);
            return new Result.Success(s);
        }

        public int calledCount() {
            return data.size();
        }
    }
}
