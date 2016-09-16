package nablarch.fw.handler.retry;

import java.text.SimpleDateFormat;
import java.util.Date;

import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.fw.handler.RetryHandler.RetryContext;

/**
 * {@link RetryContext}の実装をサポートするクラス。
 * @author Kiyohito Itoh
 */
public abstract class RetryContextSupport implements RetryContext {

    /** ロガー */
    private static final Logger LOGGER = LoggerManager.get(RetryContextSupport.class);

    /** 最長リトライ時間(単位:msec) */
    private final long maxRetryTime;

    /** リトライ間隔(単位:msec) */
    private final long retryIntervals;

    /** リトライ開始時間 */
    private Long startTime = null;

    /** 現在のリトライ回数 */
    private int currentRetryCount = 0;

    /**
     * コンストラクタ。
     * @param maxRetryTime 最長リトライ時間(単位:msec)
     * @param retryIntervals リトライ間隔(単位:msec)
     */
    protected RetryContextSupport(long maxRetryTime, long retryIntervals) {
        this.maxRetryTime = maxRetryTime;
        this.retryIntervals = retryIntervals;
    }

    /**
     * 最長リトライ時間の設定値が正しいことを表明する。
     * <p/>
     * 最短リトライ時間以下の値が最長リトライ時間に指定された場合は実行時例外を送出する。
     * <p/>
     * 本メソッドはサブクラスの最長リトライ時間の設定処理において使用すること。
     * 本メソッド呼び出し時はリトライを制御する方法に応じた最短リトライ時間を算出する必要がある。
     * 例えば、リトライ回数によりリトライ制御する場合は、リトライ回数×リトライ間隔が最短リトライ時間となる。
     * 
     * @param maxRetryTime 最長リトライ時間(単位:msec)
     * @param minRetryTime 最短リトライ時間(単位:msec)
     */
    protected void assertMaxRetryTime(long maxRetryTime, long minRetryTime) {
        if (maxRetryTime <= minRetryTime) {
            throw new IllegalArgumentException(
                String.format("maxRetryTime was too short. "
                            + "must be set value greater than minRetryTime to maxRetryTime. "
                            + "minRetryTime = [%s], maxRetryTime = [%s]",
                              minRetryTime, maxRetryTime));
        }
    }

    /**
     * {@inheritDoc}
     */
    public int getCurrentRetryCount() {
        return currentRetryCount;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * 現在のリトライ経過時間が最長リトライ時間を超えている場合は、
     * {@link #reset()}メソッドを呼び出し、リトライ状態をリセットする。
     * <p/>
     * 判定処理は{@link #onIsRetryable()}メソッドに委譲する。
     */
    public boolean isRetryable() {
        if (startTime != null) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            if (LOGGER.isTraceEnabled()) {
                LOGGER.logTrace(
                    String.format("startTime = [%s], maxRetryTime = [%s], elapsedTime = [%s]",
                                  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(startTime)),
                                  maxRetryTime, elapsedTime));
            }
            if (maxRetryTime < elapsedTime) {
                reset();
            }
        }
        return onIsRetryable();
    }

    /**
     * リトライ可能か否かをリトライ状態から判定する。
     * @return リトライ可能な場合はtrue
     */
    protected abstract boolean onIsRetryable();

    /**
     * {@inheritDoc}
     * <pre>
     * 下記の処理を行う。
     * 
     *   リトライ開始時間が設定されていなければ設定する。
     *   リトライ間隔(単位:msec)プロパティの値が0より大きい場合は、指定された時間だけ待機する。
     *   待機中にInterruptedExceptionを捕捉した場合は、WARNレベルのログ出力のみ行い、
     *   呼び出し元に制御を返す。
     *   現在のリトライ回数をカウントアップする。
     * 
     * </pre>
     */
    public void prepareRetry() {
        if (startTime == null) {
            startTime = System.currentTimeMillis();
        }
        if (0 < retryIntervals) {
            try {
                Thread.sleep(retryIntervals);
            } catch (InterruptedException e) {
                LOGGER.logWarn("interrupted while waiting for retry.", e);
            }
        }
        currentRetryCount++;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * リトライ開始時間を未設定にする。
     * 現在のリトライ回数に0を設定する。
     */
    public void reset() {
        startTime = null;
        currentRetryCount = 0;
    }
}
