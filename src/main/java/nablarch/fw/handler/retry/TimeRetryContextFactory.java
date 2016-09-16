package nablarch.fw.handler.retry;

import nablarch.fw.handler.RetryHandler.RetryContext;

/**
 * {@link TimeRetryContext}を生成するクラス。
 * @author Kiyohito Itoh
 */
public class TimeRetryContextFactory extends RetryContextFactorySupport {

    /** リトライ時間(単位:msec) */
    private int retryTime;

    /**
     * {@inheritDoc}
     * <p/>
     * {@link TimeRetryContext}を生成する。
     */
    public RetryContext createRetryContext() {
        return new TimeRetryContext(retryTime, maxRetryTime, retryIntervals);
    }

    /**
     * リトライ時間(単位:msec)を設定する。
     * @param retryTime リトライ時間(単位:msec)
     */
    public void setRetryTime(int retryTime) {
        this.retryTime = retryTime;
    }
}
