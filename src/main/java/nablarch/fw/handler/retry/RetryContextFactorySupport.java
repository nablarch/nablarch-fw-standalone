package nablarch.fw.handler.retry;

import nablarch.fw.handler.RetryHandler.RetryContextFactory;

/**
 * {@link RetryContextFactory}の実装をサポートするクラス。
 * @author Kiyohito Itoh
 */
public abstract class RetryContextFactorySupport implements RetryContextFactory {

    /** 最長リトライ時間(単位:msec) */
    protected long maxRetryTime = 15 * 60 * 1000; // SUPPRESS CHECKSTYLE サブクラスで使用するフィールドのため。

    /** リトライ間隔(単位:msec) */
    protected long retryIntervals; // SUPPRESS CHECKSTYLE サブクラスで使用するフィールドのため。

    /**
     * 最長リトライ時間(単位:msec)を設定する。
     * <p/>
     * ハンドラ構成によっては、処理が正常終了し続ける間、
     * リトライ制御を行う側まで制御が戻ってこないケースが存在する。
     * {@link nablarch.fw.handler.RequestThreadLoopHandler}を使用する場合が該当する。
     * <br/>
     * このような場合に、リトライが成功したか否かをリトライ制御を行う側が判断するために、
     * 最長リトライ時間を設けている。リトライ経過時間が最長リトライ時間を超えている場合は
     * リトライが成功したと判断する。
     * <p/>
     * デフォルトは15分。
     * 
     * @param maxRetryTime 最長リトライ時間(単位:msec)
     */
    public void setMaxRetryTime(long maxRetryTime) {
        this.maxRetryTime = maxRetryTime;
    }

    /**
     * リトライ間隔(単位:msec)を設定する。
     * @param retryIntervals リトライ間隔(単位:msec)
     */
    public void setRetryIntervals(long retryIntervals) {
        this.retryIntervals = retryIntervals;
    }
}
