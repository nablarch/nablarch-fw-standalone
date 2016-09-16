package nablarch.fw.handler.retry;


/**
 * リトライ処理に使用するユーティリティクラス。
 * @author Kiyohito Itoh
 */
public final class RetryUtil {

    /** 隠蔽コンストラクタ */
    private RetryUtil() {
    }

    /**
     * 指定された例外がリトライ可能であるか否かを判定する。
     * <p/>
     * {@link Retryable}インタフェースを実装した例外をリトライ可能な例外と判断する。
     * 起因となる例外も含めて判定する。
     * 
     * @param e 例外
     * @return リトライ可能である場合はtrue
     */
    public static boolean isRetryable(Throwable e) {
        if (e instanceof Retryable) {
            return true;
        }
        Throwable cause = e.getCause();
        return cause != null && isRetryable(cause);
    }
}
