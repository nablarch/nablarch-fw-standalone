package nablarch.fw;

import org.junit.Test;

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
    public void copy() {
        ExecutionContext orgCtx = new StandaloneExecutionContext();
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
        assertThat(newCtx instanceof StandaloneExecutionContext, is(true));
        assertThat(newCtx.getHandlerQueue(), is(orgCtx.getHandlerQueue()));
        assertThat(newCtx.getSessionScopeMap(), is(orgCtx.getRequestScopeMap()));
        assertThat(newCtx.getMethodBinder(), is(orgCtx.getMethodBinder()));
        //requestScopeMapについては、新規オブジェクトが生成されているはず。空であることを確認する。
        assertThat(newCtx.getRequestScopeMap().isEmpty(), is(true));
        //readerFactoryの確認（dataReaderが同一であれば、readerFactoryも同一である）
        assertThat(newCtx.getDataReader(), is(orgCtx.getDataReader()));

    }
}