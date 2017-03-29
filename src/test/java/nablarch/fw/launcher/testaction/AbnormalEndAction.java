package nablarch.fw.launcher.testaction;


import nablarch.fw.DataReader;
import nablarch.fw.DataReaderFactory;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.launcher.ProcessAbnormalEnd;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link nablarch.fw.launcher.MainTest}で使用するアクションクラス。
 * <p/>
 * 異常終了を示す例外({@link nablarch.fw.launcher.ProcessAbnormalEnd})を送出する。
 *
 * @author hisaaki sioiri
 */
public class AbnormalEndAction implements DataReaderFactory<String>, Handler<String, Result> {

    @Override
    public Result handle(String inputData, ExecutionContext ctx) {
        throw new ProcessAbnormalEnd(100, "msgid");
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
