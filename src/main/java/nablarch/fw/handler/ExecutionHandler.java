package nablarch.fw.handler;

import java.util.ArrayList;
import java.util.List;

import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.fw.DataReaderFactory;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;

/**
 * ハンドラキューの並列実行に関する共通処理を実装するハンドラ。
 *
 * 基本的なセマンティクスとしては、このハンドラが開始された時点での実行コンテキストの
 * 内容を直列化(同一プロセス内で実行する分はクローン)し、各並行実行単位に転送する。
 * 転送先のノードでは、実行コンテキスト内のハンドラキューを順次実行し、その内容を
 * マスターノードに返却する。
 *
 * 主要な並行実行ハンドラは以下のとおり。
 * <pre>
 * - マルチスレッド並行実行制御 {@see MultiThreadExecutionHandler}
 * </pre>
 *
 * 現時点では、単一ノード・単一プロセスで動作するマルチスレッド並行制御ハンドラのみが
 * 実装されている段階であり、このクラスは単なるマーカインタフェースとなっているが、
 * 将来的には、他実装が提供された段階で共通処理をプルアップし、
 * テンプレートパターンによる実装となる可能性もある。
 *
 * @param <TData>   ハンドラに対する入力データ型
 * @param <TResult> ハンドラの処理結果型
 * @param <TSelf>   具象クラスの型
 *
 * @author Iwauo Tajima
 */
@SuppressWarnings("rawtypes")
interface ExecutionHandler<TData, TResult, TSelf extends ExecutionHandler>
extends Handler<TData, TResult> {
    /**
     * {@see ExecutionHandler} の各実装で利用される共通ロジックを提供する
     * ユーティリティ。
     */
    public final class Support<TData, TResult> {
        /**
         * ハンドラキューの内容を走査し、
         * {@link ExecutionHandlerCallback}を実装した後続ハンドラを返す。
         *
         * @param data 本ハンドラに対する入力オブジェクト
         * @param ctx  実行コンテキスト
         * @return     {@link nablarch.fw.TransactionEventCallback}を実装した後続ハンドラ
         */
        public List<ExecutionHandlerCallback>
        prepareListeners(TData data, ExecutionContext ctx) {
            return ctx.selectHandlers(data, ExecutionHandlerCallback.class, ExecutionHandler.class);
        }

        /**
         * 後続ハンドラ実行前の初期処理用コールバックを呼び出す。
         *
         * コールバック実行中に例外が発生した場合は、後続のコールバックの実行は行わずに
         * 当該の例外をそのまま送出する。
         *
         * @param listeners 後続ハンドラのうち{@link ExecutionHandlerCallback}を実装しているもの。
         * @param data      本ハンドラに対する入力オブジェクト
         * @param ctx       実行コンテキスト
         */
        public void
        callPreExecution(List<ExecutionHandlerCallback> listeners,
                         TData data,
                         ExecutionContext ctx) {
            for (ExecutionHandlerCallback<TData, TResult> listener : listeners) {
                listener.preExecution(data, ctx);
            }
        }

        /**
         * 後続ハンドラ実行中にエラーが発生した場合のコールバックを呼び出す。
         *
         * コールバック実行中に発生した例外はワーニングログに出力し、
         * 再送出はせずに後続のコールバックを呼び出す。
         *
         * @param listeners 後続ハンドラのうち{@link ExecutionHandlerCallback}を実装しているもの。
         * @param e   後続ハンドラの実行中に送出されたエラー
         * @param ctx 実行コンテキスト
         */
        public void
        callErrorInExecution(List<ExecutionHandlerCallback> listeners,
                             Throwable e,
                             ExecutionContext ctx) {
            for (ExecutionHandlerCallback<TData, TResult> listener : listeners) {
                try {
                    listener.errorInExecution(e, ctx);

                } catch (Throwable t) {
                    LOGGER.logWarn(
                        "An error occurred while processing an error callback."
                      , t
                    );
                }
            }
        }

        /**
         * 後続ハンドラの処理終了時のコールバックを呼び出す。
         *
         * コールバック実行中に発生した例外は、一旦ワーニングログに出力し、
         * 後続のコールバックを全て実行した後で送出する。
         * (なお、複数のコールバックでエラーが発生した場合は、
         * 最初に発生した例外を送出する。)
         *
         * @param listeners 後続ハンドラのうち{@link ExecutionHandlerCallback}を実装しているもの。
         * @param result 処理結果オブジェクト
         * @param ctx    実行コンテキスト
         */
        public void
        callPostExecution(List<ExecutionHandlerCallback> listeners,
                          TResult result,
                          ExecutionContext ctx) {
            List<Throwable> raisedErrors = new ArrayList<Throwable>();
            for (ExecutionHandlerCallback<TData, TResult> listener : listeners) {
                try {
                    listener.postExecution(result, ctx);

                } catch (RuntimeException e) {
                    raisedErrors.add(e);
                    LOGGER.logWarn(
                        "An error occurred while processing an postExecution callback."
                      , e
                    );
                } catch (Error e) {
                    raisedErrors.add(e);
                    LOGGER.logWarn(
                        "An error occurred while processing an postExecution callback."
                      , e
                    );
                }
                if (!raisedErrors.isEmpty()) {
                    Throwable t = raisedErrors.get(0);
                    if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    } else {
                        throw (Error) t;
                    }
                }
            }
        }

        /**
         * 後続処理で使用するデータリーダを以下の要領で準備する。
         *
         * 1. 実行コンテキスト上にデータリーダもしくはデータリーダファクトリが既に
         *    設定されている場合はそれを利用する。(何もしない。)
         *
         * 2. データリーダおよびデータリーダファクトリが実行コンテキストから
         *    取得できなかった場合は、後続のハンドラの中でデータリーダファクトリを実装
         *    したものを走査し、そのファクトリが生成したデータリーダを取得する。
         *
         * @param data    入力データ
         * @param context 実行コンテキスト
         *
         */
        public void prepareDataReader(Object data, ExecutionContext context) {
            // データリーダが取得できれば何もしない。
            if (context.getDataReader() != null) {
                return;
            }
            DataReaderFactory<?> readerFactory = context.findHandler(
                data, DataReaderFactory.class, ExecutionHandler.class
            );
            if (readerFactory == null) {
                throw new IllegalStateException(
                  "Any DataReader or DataReaderFactory is not available."
                );
            }
            context.setDataReaderFactory(readerFactory)
                   .getDataReader();
        }

        /** ロガー */
        private static final Logger
            LOGGER = LoggerManager.get(ExecutionHandler.class);
    }
}
