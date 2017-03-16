package nablarch.fw.launcher.testaction;

import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Result;
import nablarch.fw.action.BatchAction;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link nablarch.fw.launcher.MainTest}で使用するアクションクラス。
 * <p/>
 * マルチスレッドでの動作をテストするためのアクションクラス。
 *
 * @author shohei ukawa
 */
public class MultithreadAction extends BatchAction<Integer> {

    final static int KEY_NUM = 100000;

    @Override
    public Result handle(Integer inputData, ExecutionContext ctx) {
        String key = Integer.toString(inputData);
        ctx.setSessionScopedVar(key, "hoge");
        ctx.getSessionScopedVar(key);
        return new Result.Success();
    }

    @Override
    public DataReader<Integer> createReader(ExecutionContext ctx) {
        final List<Integer> data = prepareData();

        return new DataReader<Integer>() {
            public Integer read(ExecutionContext ctx) {
                return data.remove(0);
            }
            public boolean hasNext(ExecutionContext ctx) {
                return !data.isEmpty();
            }
            public void close(ExecutionContext ctx) {
                // nop
            }
        };
    }

    private List<Integer> prepareData() {
        List<Integer> data = new ArrayList<Integer>();
        for (int i = 0; i < KEY_NUM; i++) {
            data.add(Integer.valueOf(i));
        }
        return data;
    }
}
