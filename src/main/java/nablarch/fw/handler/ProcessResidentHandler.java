package nablarch.fw.handler;

import java.util.ArrayList;
import java.util.List;

import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.core.log.app.FailureLogUtil;
import nablarch.core.util.ObjectUtil;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.results.ServiceError;
import nablarch.fw.results.ServiceUnavailable;
import nablarch.fw.Result.Success;
import nablarch.fw.handler.ProcessStopHandler.ProcessStop;
import nablarch.fw.handler.retry.RetryUtil;
import nablarch.fw.handler.retry.RetryableException;
import nablarch.fw.launcher.ProcessAbnormalEnd;

/**
 * プロセスを常駐化するためのハンドラ。
 * <p/>
 * 本ハンドラは、プロセスを常駐化するものであり下記表の条件に従い、
 * 後続処理の呼び出しや処理停止の判断を行う。
 * <pre>
 * -----------------------------+----------------------------------------------------------------------
 * 正常に処理を終了する条件     | {@link #setNormalEndExceptions(java.util.List)}で設定した例外が送出された場合(サブクラス含む)
 *                              |
 *                              | 設定を省略した場合のデフォルト動作として、{@link ProcessStop}が発生した場合に処理を停止する。
 *                              | これは、本ハンドラの後続ハンドラに{@link ProcessStopHandler}を設定することにより、
 *                              | 安全に常駐プロセスを停止することを可能としている。
 * -----------------------------+----------------------------------------------------------------------
 * 異常終了する条件             | {@link #setAbnormalEndExceptions(java.util.List)}で設定した例外が送出された場合(サブクラス含む)
 *                              | または、{@link Error}が送出された場合(サブクラス含む)
 * -----------------------------+----------------------------------------------------------------------
 * 後続ハンドラを呼び出す条件   | 上記に該当しない例外が発生した場合は、障害通知ログを出力後に一定時間待機し、
 *                              | 再度後続ハンドラに処理を委譲する。
 *                              | また、例外が発生せずに後続ハンドラが正常に処理を終了した場合も、
 *                              | 一定時間待機後に再度後続ハンドラに処理を委譲する。
 *                              |
 *                              | 待機時間(データ監視間隔)は、{@link #setDataWatchInterval(int)}によって設定した時間(ms)となる。
 *                              | 設定を省略した場合のデータ監視間隔は1000msとなる。
 *                              |
 *                              | なお、本ハンドラはサービス閉塞中例外({@link ServiceUnavailable})が発生した場合は、
 *                              | 一定時間待機後に後続ハンドラを呼び出す仕様となっている。
 *                              | このため、本ハンドラの後続ハンドラに{@link nablarch.common.handler.ServiceAvailabilityCheckHandler}を設定することにより、
 *                              | プロセスが開局されるまで業務処理(バッチアクション)の実行を抑制することが可能となっている。
 * -----------------------------+----------------------------------------------------------------------
 * </pre>
 * 以下は、本ハンドラの設定例である。
 * <p/>
 * データ監視間隔は、常駐プロセスごとに異なる値を設定する事が想定される。
 * 常駐プロセスごとに異なる値を設定するには、下記例のように設定ファイルを記述し、
 * 常駐プロセス起動時にデータ監視間隔を指定すれば良い。
 * <pre>
 * &lt;!-- 常駐化ハンドラの設定 -->
 * &lt;component class="nablarch.fw.handler.ProcessResidentHandler">
 *   &lt;!--
 *   データ監視間隔
 *   データ監視間隔は、プレースホルダ形式で記述しシステムプロパティの値を埋め込めるようにする。
 *   -->
 *   &lt;property name="dataWatchInterval" value="${data-watch-interval}" />
 * &lt;/component>
 * </pre>
 *
 * <pre>
 * // 常駐プロセス起動時にシステムプロパティ(-Dオプション)にデータ監視間隔を設定する。
 * // 500msを設定する場合の例
 * java -Ddata-watch-interval=500 ・・・
 * </pre>
 * @author hisaaki sioiri
 */
public class ProcessResidentHandler implements Handler<Object, Object> {

    /** ロガー。 */
    private static final Logger LOGGER = LoggerManager.get(
            ProcessResidentHandler.class);


    /** データの監視間隔。 */
    private int dataWatchInterval = 1000;

    /** 正常にプロセスを停止する例外のリスト。 */
    private final List<Class<? extends RuntimeException>> normalEndExceptions =
            new ArrayList<Class<? extends RuntimeException>>() {
                {
                    add(ProcessStop.class);
                }
            };

    /** プロセスを異常終了させる例外のリスト。 */
    private final List<Class<? extends RuntimeException>> abnormalEndExceptions =
            new ArrayList<Class<? extends RuntimeException>>() {
                {
                    add(ProcessAbnormalEnd.class);
                }
            };

    /** {@inheritDoc} */
    public Object handle(Object data, ExecutionContext context) {

        // データ監視間隔をログ出力
        LOGGER.logInfo(String.format("DATA WATCH INTERVAL = [%dms]",
                dataWatchInterval));

        @SuppressWarnings("rawtypes")
        List<Handler> snapshot = new ArrayList<Handler>();

        // スナップショットの取得
        snapshot.addAll(context.getHandlerQueue());

        Object result = new Success();
        while (true) {
            long executeTime = 0;
            long start = System.currentTimeMillis();
            try {
                context.setDataReader(null);
                context.setDataReaderFactory(null);
                result = restoreHandlerQueue(context, snapshot).handleNext(
                        data);
            } catch (ServiceUnavailable e) {
                // サービス閉局中の場合は何もしない
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.logTrace(
                            "this process is asleep because the service "
                                    + "temporarily unavailable.");
                }
            } catch (RuntimeException e) {
                if (RetryUtil.isRetryable(e)) {
                    // リトライ可能例外の場合は、上位のハンドラに処理を委譲するため、
                    // 補足した例外を再送出する。
                    throw e;
                } else if (isProcessAbnormalEnd(e)) {
                    // プロセスを異常終了する場合
                    throw e;
                } else if (isProcessNormalEnd(e)) {
                    // プロセスを正常終了する場合
                    LOGGER.logInfo("stop the resident process.", e);
                    break;
                }

                // 上記に該当しない例外の場合は、障害通知ログを出力する。
                if (e instanceof ServiceError) {
                    ((ServiceError) e).writeLog(context);
                } else {
                    FailureLogUtil.logFatal(e, context.getDataProcessedWhenThrown(e), null);
                }
                // リトライ可能例外として再送出し、リトライハンドラ側で継続判断を行なう。
                throw new RetryableException(e);
            }
            executeTime = System.currentTimeMillis() - start;
            try {
                // 監視間隔(ms)から、実行時間を引いた時間分待機する。
                long currentSleepTime = dataWatchInterval - executeTime;
                Thread.sleep(currentSleepTime <= 0 ? 1 : currentSleepTime);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    /**
     * プロセスを異常終了させるか否か。
     *
     * @param runtimeException エラー情報
     * @return 処理を異常終了させる場合はtrue
     */
    private boolean isProcessAbnormalEnd(RuntimeException runtimeException) {
        for (Class<? extends RuntimeException> exception : abnormalEndExceptions) {
            if (exception.isAssignableFrom(runtimeException.getClass())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 正常に処理を終了させるか否か。
     * <p/>
     * パラメータで指定された{@link RuntimeException}が、
     * {@link #setNormalEndExceptions(java.util.List)}で設定された例外クラスの場合
     * (サブクラス含む)は、処理を正常に終了する。
     *
     * @param runtimeException エラー情報
     * @return 処理を正常に停止させる場合はtrue
     */
    private boolean isProcessNormalEnd(RuntimeException runtimeException) {
        for (Class<? extends RuntimeException> exception : normalEndExceptions) {
            if (exception.isAssignableFrom(runtimeException.getClass())) {
                return true;
            }
        }
        return false;
    }

    /**
     * データ監視間隔(ミリ秒)を設定する。
     *
     * @param dataWatchInterval データ監視間隔(ミリ秒)
     */
    public void setDataWatchInterval(int dataWatchInterval) {
        if (dataWatchInterval <= 0) {
            throw new IllegalArgumentException(
                    "data watch interval time was invalid."
                            + " please set a value greater than 1."
                            + " specified value is:" + dataWatchInterval);
        }

        this.dataWatchInterval = dataWatchInterval;
    }

    /**
     * 処理を正常に終了させる例外クラスを設定する。
     *
     * @param normalEndExceptions 正常に処理を終了させる例外クラス
     */
    public void setNormalEndExceptions(
            List<String> normalEndExceptions) {
        this.normalEndExceptions.clear();
        this.normalEndExceptions.addAll(
                ObjectUtil.createExceptionsClassList(normalEndExceptions));
    }

    /**
     * 処理を異常終了させる例外クラスを設定する。
     *
     * @param abnormalEndExceptions 異常終了させる例外クラス
     */
    public void setAbnormalEndExceptions(List<String> abnormalEndExceptions) {
        this.abnormalEndExceptions.clear();
        this.abnormalEndExceptions.addAll(
                ObjectUtil.createExceptionsClassList(abnormalEndExceptions));
    }


    /**
     * ハンドラキューの内容を、ループ開始前の状態に戻す。
     *
     * @param context 実行コンテキスト
     * @param snapshot ハンドラキューのスナップショット
     * @return 実行コンテキスト(引数と同じインスタンス)
     */
    @SuppressWarnings("rawtypes")
    private ExecutionContext restoreHandlerQueue(ExecutionContext context,
            List<Handler> snapshot) {
        List<Handler> queue = context.getHandlerQueue();
        queue.clear();
        queue.addAll(snapshot);
        return context;
    }
}

