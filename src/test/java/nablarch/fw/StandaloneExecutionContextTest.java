package nablarch.fw;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * {@link StandaloneExecutionContext}のテストクラス。
 *
 * @author Masaya Seko
 */
public class StandaloneExecutionContextTest {

    /**
     * StandaloneExecutionContextがコピーできることを確認する。
     */
    @Test
    public void testCopy() {
        ExecutionContext orgCtx = new StandaloneExecutionContext();
        //コピー後について、requestScopeMapについては、新規オブジェクトが生成されていることを確認したい。
        //確認できるようにするため、コピー元に中身の詰まったmapを設定する。
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("hoge","hoge");
        orgCtx.setRequestScopeMap(map);

        //テスト用のダミー実装をおこなったdataReader
        final DataReader<Object> dataReader = new DataReader<Object>() {

            @Override
            public Object read(ExecutionContext ctx) {
                return null;
            }

            @Override
            public boolean hasNext(ExecutionContext ctx) {
                return false;
            }

            @Override
            public void close(ExecutionContext ctx) {

            }
        };

        orgCtx.setDataReaderFactory(new DataReaderFactory<Object>() {
            @Override
            public DataReader<Object> createReader(ExecutionContext context) {
                //同一のDataReaderFactoryのインスタンスを使用すると、同一のdataReaderが返るように実装
                return dataReader;
            }
        });

        ExecutionContext newCtx = orgCtx.copy();
        assertEquals(newCtx.getClass(), StandaloneExecutionContext.class);
        assertThat(newCtx.getHandlerQueue(), is(orgCtx.getHandlerQueue()));
        assertThat(newCtx.getSessionScopeMap(), is(orgCtx.getSessionScopeMap()));
        assertThat(newCtx.getMethodBinder(), is(orgCtx.getMethodBinder()));
        //requestScopeMapについては、新規オブジェクトが生成されているはず。空であることを確認する。
        assertThat(newCtx.getRequestScopeMap().isEmpty(), is(true));
        //readerFactoryの確認（dataReaderが同一であれば、readerFactoryも同一である）
        assertThat(newCtx.getDataReader(), is(orgCtx.getDataReader()));

    }
}