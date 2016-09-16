package nablarch.fw.handler;

import nablarch.common.handler.threadcontext.ExecutionIdAttribute;
import nablarch.common.handler.threadcontext.ThreadContextAttribute;
import nablarch.core.ThreadContext;
import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.fw.DataReader.NoMoreRecord;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.TransactionEventCallback;

/**
 * 業務コンポーネントで処理するデータを{@link nablarch.fw.DataReader}から読み込む
 * {@link Handler}実装クラス。
 * <p/>
 * {@link nablarch.fw.DataReader}から読み込んだデータをリクエストとして、
 * 後続のハンドラに処理を委譲する。
 * <br/>
 * データが存在しない場合(読み込んだデータがnull)の場合は、
 * 後続のハンドラに処理は移譲せずに{@link NoMoreRecord}を返却する。
 *
 * @author hisaaki sioiri
 */
public class DataReadHandler implements Handler<Object, Result> {

    /** ロガー */
    private static final Logger LOGGER = LoggerManager.get(
            DataReadHandler.class);

    /** {@inheritDoc} */
    public Result handle(Object o, ExecutionContext context) {

        if (maxCount > 0) {
            synchronized (this) {
                countUp(context);
            }
        }
        boolean isNextRecord = context.hasNextData();
        if (!isNextRecord) {
            return new NoMoreRecord();
        }
        Object requestData = context.readNextData();
        if (requestData == null) {
            return new NoMoreRecord();
        }
        
        // 入力データごとに実行時IDを発番する。
        ThreadContext.setExecutionId(
                (String) this.executionIdAttribute.getValue(o, context)
        );

        // データが存在した場合は、後続のハンドラへ処理を委譲する。
        try {
            context.setRequestScopedVar(
                    TransactionEventCallback.REQUEST_DATA_REQUEST_SCOPE_KEY,
                    requestData);
            return context.handleNext(requestData);
        } catch (RuntimeException e) {
            writeWarnLog(requestData, e);
            throw e;
        } catch (Error e) {
            writeWarnLog(requestData, e);
            throw e;
        }
    }

    /**
     * ワーニングログを出力する。
     * @param requestData リクエストデータ
     * @param t 例外情報
     */
    protected void writeWarnLog(Object requestData, Throwable t) {
        LOGGER.logWarn("application was abnormal end." + Logger.LS + '\t'
                + "input data = " + (requestData == null ? "null" : requestData.toString()), t);
    }

    /**
     * データ読み込み回数を計数し、上限を超えていれば、
     * 実行コンテキスト上のリーダを削除する。
     *
     * @param context 実行コンテキスト
     */
    private void countUp(ExecutionContext context) {
        readCount++;
        if (readCount > maxCount) {
            // 最大件数に達した場合は、DataReaderオブジェクトにnullを設定する。
            // これにより、次レコードの読み込み時に存在なしとして処理が終了する。
            context.setDataReaderFactory(null);
            context.setDataReader(null);
        }
    }

    /** データ読み込み回数 */
    private int readCount = 0;

    /**
     * データ読み込みの上限回数を指定する。
     * <p/>
     * 上限に達した段階で、実行コンテキスト上のreaderを除去する。
     * それ以降は、ExecutionContext#hanNextData() の結果は常にfalseを返す。
     * デフォルトの設定値は0 (=無制限)
     * <p/>
     * なお、この値に正数を指定している場合は、読み込み回数のカウントアップの際に
     * 同期処理が行われる。
     *
     * @param maxCount データ読み込みの上限回数。
     * 0もしくは負数を設定した場合は無制限。
     * @return このオブジェクト自体
     */
    public DataReadHandler setMaxCount(int maxCount) {
        this.maxCount = maxCount;
        return this;
    }

    /** データ読み込みの上限回数。 */
    private int maxCount = 0;
    
    
    /**
     * 実行時IDを初期化する際に使用する{@see ThreadContextAttribute}を設定する。
     * @param attribute 実行時IDを初期化する{@see ThreadContextAttribute}
     * @return このオブジェクト自体。executionIdAttribute
     */
    public DataReadHandler
    setExecutionIdAttribute(ExecutionIdAttribute attribute) {
        this.executionIdAttribute = attribute;
        return this;
    }
    
    /** 実行時ID初期化コンポーネント */
    private ThreadContextAttribute<Object>
        executionIdAttribute = new ExecutionIdAttribute();
}
