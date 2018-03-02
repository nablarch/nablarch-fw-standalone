package nablarch.fw;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * standaloneで使用する{@link ExecutionContext}の継承クラス。<br/>
 * 制約
 * <ul>
 *     <li>セッションスコープがスレッドセーフである</li>
 *     <li>セッションスコープにnullを指定できない</li>
 *     <li>セッションストアが使用できない</li>
 * </ul>
 *
 * @author  shohei ukawa
 */
public class StandaloneExecutionContext extends ExecutionContext {

    public StandaloneExecutionContext() {
        setSessionScopeMap(new ConcurrentHashMap<String, Object>());
    }

    /**
     * 元となる実行コンテキストから、新たな実行コンテキストのオブジェクトを作成する。
     * コピーの仕様は、{@link ExecutionContext}に準じる。
     * @param original 元となる実行コンテキスト
     */
    public StandaloneExecutionContext(StandaloneExecutionContext original) {
        super(original);
    }

    @Override
    protected StandaloneExecutionContext copyInternal() {
        return new StandaloneExecutionContext(this);
    }


    /**
     * 本メソッドは利用できない。
     * 
     * 呼び出した場合、{@link UnsupportedOperationException}を送出する。
     */
    @Override
    public Map<String, Object> getSessionStoreMap() {
        throw new UnsupportedOperationException("Please use \"sessionScope\" instead of \"sessionStore\".");
    }

    /**
     * 本メソッドは利用できない。
     *
     * 呼び出した場合、{@link UnsupportedOperationException}を送出する。
     */
    @Override
    public ExecutionContext setSessionStoreMap(Map<String, Object> map) {
        throw new UnsupportedOperationException("Please use \"sessionScope\" instead of \"sessionStore\".");
    }

    /**
     * 本メソッドは利用できない。
     *
     * 呼び出した場合、{@link UnsupportedOperationException}を送出する。
     */
    @Override
    public <T> T getSessionStoredVar(String varName) throws ClassCastException {
        throw new UnsupportedOperationException("Please use \"sessionScope\" instead of \"sessionStore\".");
    }

    /**
     * 本メソッドは利用できない。
     *
     * 呼び出した場合、{@link UnsupportedOperationException}を送出する。
     */
    @Override
    public ExecutionContext setSessionStoredVar(String varName, Object varValue) {
        throw new UnsupportedOperationException("Please use \"sessionScope\" instead of \"sessionStore\".");
    }

}
