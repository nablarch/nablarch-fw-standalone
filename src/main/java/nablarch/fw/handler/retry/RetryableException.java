package nablarch.fw.handler.retry;

import nablarch.core.util.annotation.Published;


/**
 * RetryHandler によるリトライが可能な実行時例外。
 * 
 * @author Iwauo Tajiama
 */
@Published(tag = "architect")
public class RetryableException extends RuntimeException implements Retryable {

    /**
     * デフォルトコンストラクタ。
     */
    public RetryableException() {
        super();
    }

    /**
     * コンストラクタ。
     * 
     * @param message 例外メッセージ
     * @param cause   起因例外
     */
    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * コンストラクタ。
     * 
     * @param message 例外メッセージ
     */
    public RetryableException(String message) {
        super(message);
    }

    /**
     * コンストラクタ。
     * 
     * @param cause 起因例外
     */
    public RetryableException(Throwable cause) {
        super(cause);
    }
}
