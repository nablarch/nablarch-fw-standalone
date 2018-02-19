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

    /***
     * 自身の複製を返す。
     * <p/>
     * 複製するオブジェクトの型は、自身とまったく同一の型である。
     *
     * @return 自身の複製
     */
    public StandaloneExecutionContext copy(){
        return new StandaloneExecutionContext(this);
    }


    /**
     * @deprecated セッションストアは使用できません。代わりにセッションスコープを使用してください。
     * {@link #getSessionScopeMap()}
     */
    @Override
    @Deprecated
    public Map<String, Object> getSessionStoreMap() {
        throw new UnsupportedOperationException("Please use \"sessionScope\" instead of \"sessionStore\".");
    }

    /**
     * @deprecated セッションストアは使用できません。代わりにセッションスコープを使用してください。
     * {@link #setSessionScopeMap(Map)}
     */
    @Override
    @Deprecated
    public ExecutionContext setSessionStoreMap(Map<String, Object> map) {
        throw new UnsupportedOperationException("Please use \"sessionScope\" instead of \"sessionStore\".");
    }

    /**
     * @deprecated セッションストアは使用できません。代わりにセッションスコープを使用してください。
     * {@link #getSessionScopedVar(String)}
     */
    @Override
    @Deprecated
    public <T> T getSessionStoredVar(String varName) throws ClassCastException {
        throw new UnsupportedOperationException("Please use \"sessionScope\" instead of \"sessionStore\".");
    }

    /**
     * @deprecated セッションストアは使用できません。代わりにセッションスコープを使用してください。
     * {@link #setSessionScopedVar(String, Object)}
     */
    @Override
    @Deprecated
    public ExecutionContext setSessionStoredVar(String varName, Object varValue) {
        throw new UnsupportedOperationException("Please use \"sessionScope\" instead of \"sessionStore\".");
    }

}
