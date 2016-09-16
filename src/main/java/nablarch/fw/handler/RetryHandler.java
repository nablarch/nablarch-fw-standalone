package nablarch.fw.handler;

import java.util.ArrayList;

import java.util.List;

import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.core.util.annotation.Published;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.handler.retry.RetryUtil;
import nablarch.fw.launcher.ProcessAbnormalEnd;

/**
 * リトライ可能な例外を捕捉した場合に後続ハンドラの処理をリトライするハンドラ。
 * <p/>
 * {@link nablarch.fw.handler.retry.Retryable}インタフェースを実装した例外をリトライ可能な例外と判断する。
 * <p/>
 * リトライ処理の制御は{@link RetryContext}を実装したクラスに委譲する。
 * {@link RetryContext}を実装したクラスは{@link RetryContextFactory}から取得するので、
 * 本クラスを使用する場合は{@link RetryContextFactory}オブジェクトをプロパティに設定すること。
 * 
 * @author Kiyohito Itoh
 */
public class RetryHandler implements Handler<Object, Object> {

    /** ロガー */
    private static final Logger LOGGER = LoggerManager.get(RetryHandler.class);

    /** リトライコンテキストを生成する{@link RetryContextFactory}オブジェクト */
    private RetryContextFactory retryContextFactory;

    /** リトライ上限を超えた場合に使用する終了コード(プロセスを終了({@link System#exit(int)})する際に設定する値) */
    private int retryLimitExceededExitCode = 180;

    /** リトライ上限を超えた場合に使用する障害コード */
    private String retryLimitExceededFailureCode;

    /** リトライ時に実行コンテキスト上の{@link nablarch.fw.DataReader}を破棄するか否か */
    private boolean destroyReader;

    /**
     * {@inheritDoc}
     * <p/>
     * リトライ対象でない例外を捕捉した場合は、補足した例外を再送出する。
     * <br/>
     * リトライ可能な例外を捕捉した場合、かつリトライ上限を超えていない場合は
     * 後続ハンドラの処理をリトライする。
     * リトライ可能な例外を捕捉した場合、かつリトライ上限を超えている場合は
     * {@link #retryLimitExceededExitCode}プロパティと
     * {@link #retryLimitExceededFailureCode}プロパティを使用して
     * {@link ProcessAbnormalEnd}を送出する。
     */
    @SuppressWarnings("rawtypes")
    public Object handle(Object data, ExecutionContext context) {

        List<Handler> snapshot = new ArrayList<Handler>();
        snapshot.addAll(context.getHandlerQueue());

        RetryContext retryContext = retryContextFactory.createRetryContext();

        while (true) {
            try {
                Object result = context.handleNext(data);
                retryContext.reset();
                return result;
            } catch (RuntimeException e) {

                if (!RetryUtil.isRetryable(e)) { // リトライ対象の例外でない場合
                    retryContext.reset();
                    throw e;
                }

                if (retryContext.isRetryable()) { // リトライ可能な場合
                    LOGGER.logWarn(
                            String.format("caught a exception to retry. start retry. retryCount[%s]",
                                          retryContext.getCurrentRetryCount() + 1), e);
                    retryContext.prepareRetry();
                    context.getHandlerQueue().clear();
                    context.getHandlerQueue().addAll(snapshot);

                    if (destroyReader) {
                        destroyDataReader(context);
                    }
                } else { // リトライ上限の場合
                    LOGGER.logWarn(
                            String.format("retry process failed. retry limit was exceeded."), e);
                    retryContext.reset();
                    throw new ProcessAbnormalEnd(retryLimitExceededExitCode, e, retryLimitExceededFailureCode);
                }
            }
        }
    }

    /**
     * 実行コンテキスト上に設定された{@link nablarch.fw.DataReader}及び{@link nablarch.fw.DataReaderFactory}を破棄する。
     *
     * @param context 実行コンテキスト
     */
    private static void destroyDataReader(ExecutionContext context) {
        context.setDataReader(null);
        context.setDataReaderFactory(null);
    }

    /**
     * リトライ時に{@link ExecutionContext}上に設定された{@link nablarch.fw.DataReader}を破棄するか否かを設定する。。
     * <p/>
     * 本設定値に{@code true}を設定した場合、リトライ時に{@link ExecutionContext}上に設定された{@link nablarch.fw.DataReader}を破棄（削除）する。
     * これにより、後続ハンドラで{@link nablarch.fw.DataReader}が再生成される。
     *
     * @param destroyReader リトライ時にリーダを破棄するか否か
     */
    public void setDestroyReader(boolean destroyReader) {
        this.destroyReader = destroyReader;
    }

    /**
     * リトライ処理を制御するインタフェース。
     * @author Kiyohito Itoh
     */
    @Published(tag = "architect")
    public static interface RetryContext {
        /**
         * 現在のリトライ回数を取得する。
         * @return 現在のリトライ回数
         */
        int getCurrentRetryCount();
        /**
         * リトライ可能か否かをリトライ状態から判定する。
         * @return リトライ可能な場合はtrue
         */
        boolean isRetryable();
        /**
         * リトライ前の準備を行う。
         */
        void prepareRetry();
        /**
         * リトライ状態をリセットする。
         */
        void reset();
    }

    /**
     * リトライコンテキストを生成するインタフェース。
     * @author Kiyohito Itoh
     */
    @Published(tag = "architect")
    public static interface RetryContextFactory {
        /**
         * リトライコンテキストを生成する。
         * @return リトライコンテキスト
         */
        RetryContext createRetryContext();
    }

    /**
     * リトライコンテキストを生成する{@link RetryContextFactory}オブジェクトを設定する。
     * @param retryContextFactory リトライコンテキストを生成する{@link RetryContextFactory}オブジェクト
     */
    public void setRetryContextFactory(RetryContextFactory retryContextFactory) {
        this.retryContextFactory = retryContextFactory;
    }

    /**
     * リトライ上限を超えた場合に使用する終了コード(プロセスを終了({@link System#exit(int)})する際に設定する値)を設定する。
     * @param retryLimitExceededExitCode リトライ上限を超えた場合に使用する終了コード(プロセスを終了({@link System#exit(int)})する際に設定する値)
     */
    public void setRetryLimitExceededExitCode(int retryLimitExceededExitCode) {
        this.retryLimitExceededExitCode = retryLimitExceededExitCode;
    }

    /**
     * リトライ上限を超えた場合に使用する障害コードを設定する。
     * @param retryLimitExceededFailureCode リトライ上限を超えた場合に使用する障害コード
     */
    public void setRetryLimitExceededFailureCode(String retryLimitExceededFailureCode) {
        this.retryLimitExceededFailureCode = retryLimitExceededFailureCode;
    }
}
