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
