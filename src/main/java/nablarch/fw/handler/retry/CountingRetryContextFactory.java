package nablarch.fw.handler.retry;

import nablarch.fw.handler.RetryHandler.RetryContext;

/**
 * {@link CountingRetryContext}を生成するクラス。
 * @author Kiyohito Itoh
 */
public class CountingRetryContextFactory extends RetryContextFactorySupport {

    /** リトライ回数 */
    private int retryCount;

    /**
     * {@inheritDoc}
     * <p/>
     * {@link CountingRetryContext}を生成する。
     */
    public RetryContext createRetryContext() {
        return new CountingRetryContext(retryCount, maxRetryTime, retryIntervals);
    }

    /**
     * リトライ回数を設定する。
     * @param retryCount リトライ回数
     */
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
}
