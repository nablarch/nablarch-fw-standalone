package nablarch.fw.launcher.testaction;



import nablarch.fw.DataReader;
import nablarch.fw.DataReaderFactory;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link nablarch.fw.launcher.MainTest}で使用するアクションクラス。
 * <p/>
 * 正常終了を示す{@link Result.Success}を返却する。
 *
 * @author hisaaki sioiri
 */
public class NormalEndAction implements DataReaderFactory<String>, Handler<String, Result> {

    @Override
    public Result handle(String inputData, ExecutionContext ctx) {
        System.out.println("inputData = " + inputData);
        return new Result.Success();
    }

    @Override
    public DataReader<String> createReader(ExecutionContext ctx) {
        final List<String> data = new ArrayList<String>();
        data.add("11111");
        return new DataReader<String>() {

            public String read(ExecutionContext ctx) {
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
}
