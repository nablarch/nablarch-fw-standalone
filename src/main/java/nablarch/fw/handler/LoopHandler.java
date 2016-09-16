package nablarch.fw.handler;

import java.util.ArrayList;
import java.util.List;

import nablarch.core.log.app.CommitLogger;
import nablarch.core.transaction.Transaction;
import nablarch.core.transaction.TransactionContext;
import nablarch.core.transaction.TransactionExecutor;
import nablarch.core.transaction.TransactionFactory;
import nablarch.core.util.StringUtil;
import nablarch.fw.DataReader.NoMoreRecord;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.Result.Success;
import nablarch.fw.TransactionEventCallback;

/**
 * ループ制御ハンドラークラス。
 * <p/>
 * 本ハンドラは、アプリケーションが処理すべきデータが存在する間、後続のハンドラに対して繰り返し処理を委譲する。
 * 処理すべきデータが存在するかは、{@link nablarch.fw.ExecutionContext#hasNextData()}により判断する。
 * <p/>
 * また、本ハンドラではトランザクション制御もあわせて行う。
 * トランザクションは、指定間隔({@link #setCommitInterval(int)}毎にコミット処理を行う。
 * 後続ハンドラから例外が送出された場合には、未コミットのトランザクションを全てロールバックし、例外を再送出する。
 * <p/>
 * 本ハンドラの事前ハンドラとして、{@link nablarch.common.handler.DbConnectionManagementHandler}を登録すること。
 *
 * @author Hisaaki Sioiri
 * @see NoMoreRecord
 * @see CommitLogger
 * @see TransactionContext
 * @see TransactionFactory
 * @see TransactionExecutor
 */
public class LoopHandler extends TransactionEventCallback.Provider<Object>
implements Handler<Object, Result> {

    /** コミットログ出力オブジェクト */
    private CommitLogger commitLogger;

    /** コミット実施予告フラグを保持するリクエストスコープ変数名 */
    private static final String IS_ABOUT_TO_COMMIT_FLAG_KEY = ExecutionContext.FW_PREFIX + "LoopHandler_is_about_to_commit";

    /**
     * {@inheritDoc}
     * この実装では、特定の条件を満たすまで、以降のハンドラキューの内容を
     * 繰り返し処理する。
     */
    @Override
    @SuppressWarnings("rawtypes")
    public Result handle(Object data, ExecutionContext context) {

        commitLogger = context.getSessionScopedVar(
                CommitLogger.SESSION_SCOPE_KEY);

        // トランザクションの生成
        Transaction transaction = transactionFactory.getTransaction(
                transactionName);
        TransactionContext.setTransaction(transactionName, transaction);

        List<TransactionEventCallback> listeners = prepareListeners(data, context);

        try {
            // トランザクション制御及びくり返し処理の実行
            new TransactionExecutorImpl(transaction, context, data, listeners).execute();
        } finally {
            // トランザクションを削除する。
            TransactionContext.removeTransaction(transactionName);
        }
        return new Success();
    }

    /**
     * トランザクションをコミットする。
     * <p/>
     * 以下の条件に合致する場合にコミット処理を行う。
     * <ul>
     * <li>未コミット件数が、コミット間隔と一致した場合</li>
     * <li>これ以上処理するデータが存在しない場合</li>
     * </ul>
     *
     * @param transaction トランザクションオブジェクト
     * @param count 処理件数
     * @param context 実行コンテキスト
     * @return 未コミットの処理件数
     */
    private long commit(Transaction transaction, long count,
            ExecutionContext context) {
        if (commitInterval <= 1 || count >= commitInterval
                || (!context.hasNextData() && count != 0L)) {
            transaction.commit();
            if (commitLogger != null) {
                commitLogger.increment(count);
            }
            return 0;
        }
        return count;
    }

    /**
     * エラー発生時のコールバック処理。
     *
     * @param transaction トランザクション
     * @param e 発生したエラー
     * @param context 実行コンテキスト
     * @param listeners 後続ハンドラのうち {@link TransactionEventCallback}
     *                   を実装しているもの。
     */
    @SuppressWarnings("rawtypes")
    private void errorCallback(Transaction transaction,
                               final Throwable e,
                               final ExecutionContext context,
                               final List<TransactionEventCallback> listeners) {
        final Object data = getTransactionData(context);
        // トランザクションデータが存在しない場合はなにもしない。
        if (data == null) {
            return;
        }

        new TransactionExecutor<Void>(transaction) {
            @Override
            protected Void doInTransaction(Transaction transaction) {
                callAbnormalEndHandlers(listeners, e, data, context);
                return null;
            }
        }
        .execute();
    }

    /**
     * リクエストスコープからトランザクションデータを取得する。
     *
     * @param context 実行コンテキスト
     * @return トランザクションデータ
     */
    private static Object getTransactionData(ExecutionContext context) {
        return context.getRequestScopedVar(
                TransactionEventCallback.REQUEST_DATA_REQUEST_SCOPE_KEY);
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

    /**
     * 現在の処理終了後にループを止める場合にtrueを返す。
     * <p/>
     * デフォルトの実装では、実行コンテキスト上のデータリーダのデータが
     * 空になるまで繰り返し処理を行う。
     * <p/>
     * これと異なる条件でループを停止させたい場合は、本メソッドをオーバライドすること。
     *
     * @param context 実行コンテキスト
     * @return ループを止める場合はtrue
     */
    public boolean shouldStop(ExecutionContext context) {
        return !context.hasNextData();
    }

    /**
     * トランザクションオブジェクトを取得するためのファクトリを設定する。
     *
     * @param transactionFactory トランザクションオブジェクトを取得するためのファクトリ
     * @return このハンドラ自体
     */
    public LoopHandler setTransactionFactory(
            TransactionFactory transactionFactory) {
        assert transactionFactory != null;
        this.transactionFactory = transactionFactory;
        return this;
    }

    /** トランザクションオブジェクトを取得するためのファクトリ */
    private TransactionFactory transactionFactory;

    /**
     * このハンドラが管理するトランザクションの、スレッドコンテキスト上での登録名を設定する。
     * <pre>
     * デフォルトでは既定のトランザクション名
     * ({@link TransactionContext#DEFAULT_TRANSACTION_CONTEXT_KEY})を使用する。
     *
     * </pre>
     *
     * @param transactionName データベース接続のスレッドコンテキスト上の登録名
     */
    public void setTransactionName(String transactionName) {
        assert !StringUtil.isNullOrEmpty(transactionName);
        this.transactionName = transactionName;
    }

    /** トランザクションが使用するコネクションの登録名 */
    private String transactionName = TransactionContext.DEFAULT_TRANSACTION_CONTEXT_KEY;

    /**
     * コミット間隔を設定する。
     * <p/>
     * コミット間隔を指定した場合、指定した間隔でコミットが行われる。
     * なお、0以下の値を設定した場合や、設定を省略した場合のコミット間隔は1となる。
     *
     * @param commitInterval コミット間隔
     * @return このハンドラ自体
     */
    public LoopHandler setCommitInterval(int commitInterval) {
        this.commitInterval = commitInterval;
        return this;
    }

    /** コミット間隔 */
    private int commitInterval;

    /**
     * 現在のリクエストループの業務アクション実行後にLoopHandlerによるコミットが行われるか否か。
     *
     * 本メソッドは DataReadHandler よりも後ろで呼び出された場合のみ正しい結果が得られる。
     *
     * @param ctx 実行コンテキスト
     * @return 業務アクション実行後にコミットが行われる場合は true
     */
    public static final boolean isAboutToCommit(ExecutionContext ctx) {
        Boolean isAboutToCommit = ctx.getRequestScopedVar(LoopHandler.IS_ABOUT_TO_COMMIT_FLAG_KEY);
        if (isAboutToCommit == null) {
            isAboutToCommit = true;
        }
        return isAboutToCommit || !ctx.getDataReader().hasNext(ctx);
    }

    /**
     * トランザクション実行クラス。
     */
    private final class TransactionExecutorImpl extends TransactionExecutor<Void> {

        /** 実行コンテキスト */
        private final ExecutionContext context;

        /** 入力データ */
        private final Object data;

        /** 後続ハンドラのうち {@link TransactionEventCallback} を実装しているもの。*/
        @SuppressWarnings("rawtypes")
        private final List<TransactionEventCallback> listeners;

        /**
         * コンストラクタ。
         * @param transaction トランザクションオブジェクト
         * @param context 実行コンテキスト
         * @param data 入力データ
         * @param listeners 後続ハンドラのうち、{@link TransactionEventCallback}
         *                   を実装しているもの。
         */
        @SuppressWarnings("rawtypes")
        private TransactionExecutorImpl(Transaction transaction,
                                        ExecutionContext context,
                                        Object data,
                                        List<TransactionEventCallback> listeners) {
            super(transaction);
            this.context   = context;
            this.data      = data;
            this.listeners = listeners;
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected Void doInTransaction(Transaction transaction) {

            List<Handler> snapshot = new ArrayList<Handler>();
            snapshot.addAll(context.getHandlerQueue());

            long executeCount = 0;
            context.setRequestScopedVar(IS_ABOUT_TO_COMMIT_FLAG_KEY, false);

            do {
                // トランザクションデータ格納部をnull初期化
                context.setRequestScopedVar(
                        TransactionEventCallback.REQUEST_DATA_REQUEST_SCOPE_KEY, null);
                // 今回のループ実行後にコミットを行う場合は、コミット実施予告フラグをtrueに設定する。
                context.setRequestScopedVar(
                    IS_ABOUT_TO_COMMIT_FLAG_KEY,
                    (commitInterval <= 1 || executeCount == commitInterval - 1)
                );
                Object result = restoreHandlerQueue(context, snapshot)
                        .handleNext(data);

                if (!(result instanceof NoMoreRecord)) {
                    Object transactionData = getTransactionData(context);
                    if (transactionData != null) {
                        // トランザクションデータが正常に存在していた場合は、
                        // 正常終了のコールバックを呼び出す
                        callNormalEndHandlers(listeners, transactionData, context);
                    }
                    executeCount++;
                }
                executeCount = commit(transaction, executeCount, context);
                // 正常終了時は最後に読み込んだデータオブジェクトをクリアする
                context.clearLastReadData();
            } while (!shouldStop(context));
            commit(transaction, executeCount, context);
            return null;
        }

        @Override
        protected void onError(Transaction transaction,
                Throwable throwable) {
            super.onError(transaction, throwable);
            errorCallback(transaction, throwable, context, listeners);
        }
    }
}

