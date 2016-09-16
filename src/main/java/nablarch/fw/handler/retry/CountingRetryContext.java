package nablarch.fw.handler.retry;

import nablarch.core.util.annotation.Published;

/**
 * リトライ回数によりリトライ処理を制御するクラス。
 * <p/>
 * 本クラスは、指定された回数分、リトライを行う。
 * 
 * @author Kiyohito Itoh
 */
@Published(tag = "architect")
public class CountingRetryContext extends RetryContextSupport {

    /** リトライ回数 */
    private final int retryCount;

    /**
     * コンストラクタ。
     * @param retryCount リトライ回数
     * @param maxRetryTime 最長リトライ時間(単位:msec)
     * @param retryIntervals リトライ間隔(単位:msec)
     */
    protected CountingRetryContext(int retryCount, long maxRetryTime, long retryIntervals) {
        super(maxRetryTime, retryIntervals);
        this.retryCount = retryCount;
        assertMaxRetryTime(maxRetryTime, retryCount * retryIntervals);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * 現在のリトライ回数がリトライ回数プロパティより小さい場合はtrueを返す。
     */
    @Override
    protected boolean onIsRetryable() {
        return getCurrentRetryCount() < retryCount;
    }
}
