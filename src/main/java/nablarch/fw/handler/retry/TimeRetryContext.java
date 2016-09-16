package nablarch.fw.handler.retry;

import nablarch.core.util.annotation.Published;


/**
 * リトライ時間によりリトライ処理を制御するクラス。
 * <p/>
 * 本クラスは、指定された時間の間、リトライを行う。
 * @author Kiyohito Itoh
 */
@Published(tag = "architect")
public class TimeRetryContext extends RetryContextSupport {

    /** リトライ時間(単位:msec) */
    private final long retryTime;

    /** リトライ開始時間 */
    private long retryStartTime;

    /**
     * 1回目のリトライであるか否か。
     * 1回目のリトライである場合はtrue
     */
    private boolean firstRetry = true;

    /**
     * コンストラクタ。
     * @param retryTime リトライ時間(単位:msec)
     * @param maxRetryTime 最長リトライ時間(単位:msec)
     * @param retryIntervals リトライ間隔(単位:msec)
     */
    protected TimeRetryContext(long retryTime, long maxRetryTime, long retryIntervals) {
        super(maxRetryTime, retryIntervals);
        this.retryTime = retryTime;
        assertMaxRetryTime(maxRetryTime, retryTime);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * 1回目のリトライである場合はtrueを返す。
     * 2回目以降のリトライでは、
     * リトライ開始後の経過時間がリトライ時間プロパティ以下の場合にtrueを返す。
     */
    @Override
    protected boolean onIsRetryable() {
        if (firstRetry) {
            return true;
        }
        return (System.currentTimeMillis() - retryStartTime) <= retryTime;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * 1回目のリトライである場合は、親クラスの処理に加えて、リトライ開始時間の設定を行う。
     */
    @Override
    public void prepareRetry() {
        if (firstRetry) {
            firstRetry = false;
            retryStartTime = System.currentTimeMillis();
        }
        super.prepareRetry();
    }

    /**
     * {@inheritDoc}
     * <p/>
     * 親クラスの処理に加えて、リトライ開始時間をリセットする。
     */
    @Override
    public void reset() {
        super.reset();
        firstRetry = true;
        retryStartTime = 0;
    }
}
