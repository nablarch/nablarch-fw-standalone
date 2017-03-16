package nablarch.fw.handler;

import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.Result.NotFound;
import nablarch.fw.results.BadRequest;
import nablarch.fw.results.Conflicted;
import nablarch.fw.results.Forbidden;
import nablarch.fw.results.Unauthorized;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * {@link nablarch.fw.handler.StatusCodeConvertHandler}のテストクラス。
 *
 * @author hisaaki sioiri
 */
public class StatusCodeConvertHandlerTest {

    /**
     * {@link nablarch.fw.handler.StatusCodeConvertHandler#convert(int)} のテスト。
     *
     * @throws Exception
     */
    @Test
    public void testConvert() {

        ExecutionContext context = new ExecutionContext();

        //**********************************************************************
        // 正常系
        //**********************************************************************
        context.setHandlerQueue(createHndlerQueue(0));
        assertThat("0 -> 0", (Integer) context.handleNext(null), is(0));

        // 200～399は0に
        for (int i = 200; i <= 399; i++) {
            context.setHandlerQueue(createHndlerQueue(i));
            assertThat(i + "は0に変換される。", (Integer) context.handleNext(null), is(
                    0));
        }

        //**********************************************************************
        // 異常系(400系)
        //**********************************************************************
        context.setHandlerQueue(createHndlerQueue(
                new BadRequest().getStatusCode()));
        assertThat("400 -> 10", (Integer) context.handleNext(null), is(10));

        context.setHandlerQueue(createHndlerQueue(
                new Unauthorized().getStatusCode()));
        assertThat("401 -> 11", (Integer) context.handleNext(null), is(11));

        context.setHandlerQueue(createHndlerQueue(
                new Forbidden().getStatusCode()));
        assertThat("403 -> 12", (Integer) context.handleNext(null), is(12));

        context.setHandlerQueue(createHndlerQueue(
                new NotFound().getStatusCode()));
        assertThat("404 -> 13", (Integer) context.handleNext(null), is(13));

        context.setHandlerQueue(createHndlerQueue(
                new Conflicted().getStatusCode()));
        assertThat("409 -> 14", (Integer) context.handleNext(null), is(14));

        context.setHandlerQueue(createHndlerQueue(402));
        assertThat("402 -> 15", (Integer) context.handleNext(null), is(15));

        context.setHandlerQueue(createHndlerQueue(405));
        assertThat("405 -> 15", (Integer) context.handleNext(null), is(15));

        context.setHandlerQueue(createHndlerQueue(408));
        assertThat("408 -> 15", (Integer) context.handleNext(null), is(15));

        for (int i = 410; i <= 499; i++) {
            context.setHandlerQueue(createHndlerQueue(i));
            assertThat(i + "は15に変換される。", (Integer) context.handleNext(null), is(
                    15));
        }

        //**********************************************************************
        // 異常系(over 500)
        //**********************************************************************
        for (int i = 500; i <= 10000; i++) {
            context.setHandlerQueue(createHndlerQueue(i));
            assertThat("500以上は、20に", (Integer) context.handleNext(null), is(
                    20));
        }

        //**********************************************************************
        // その他
        //**********************************************************************
        //0～199は変換されない。
        for (int i = 0; i <= 199; i++) {
            context.setHandlerQueue(createHndlerQueue(i));
            assertThat(i + "は変換されない。", (Integer) context.handleNext(null), is(
                    i));
        }
        // 負数は、1に
        for (int i = -1; i > -10000; i--) {
            context.setHandlerQueue(createHndlerQueue(i));
            assertThat(i + "は変換されない。", (Integer) context.handleNext(null), is(
                    1));
        }
    }

    /**
     * ハンドラキューを構築する。
     *
     * @param statusCode 変換前のステータスコード
     * @return
     */
    private static List<Handler<?, ?>> createHndlerQueue(final int statusCode) {
        List<Handler<?, ?>> handlerList = new ArrayList<Handler<?, ?>>(2);
        handlerList.add(new StatusCodeConvertHandler());
        handlerList.add(new Handler<Object, Result>() {
            public Result handle(Object o, ExecutionContext context) {
                return new Result() {
                    public int getStatusCode() {
                        return statusCode;
                    }

                    public String getMessage() {
                        return "";
                    }
                    
                    public boolean isSuccess() {
                        return statusCode < 400;
                    }
                };
            }
        });
        return handlerList;
    }
}
