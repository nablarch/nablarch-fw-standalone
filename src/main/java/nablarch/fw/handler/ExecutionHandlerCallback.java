package nablarch.fw.handler;

import nablarch.core.util.annotation.Published;
import nablarch.fw.ExecutionContext;

/**
 * 実行制御ハンドラ内の処理状況に応じて呼び出される各種コールバックを定義する
 * インターフェース。
 * 
 * @param <TData>   ハンドラへの入力データの型
 * @param <TResult> ハンドラの処理結果データの型
 * 
 * @author Iwauo Tajima
 */
@Published(tag = "architect")
public interface ExecutionHandlerCallback<TData, TResult> { 
    /**
     * 実行制御ハンドラが後続処理を実行する前にコールバックされる。
     * 一括処理実行前に、なんらかの初期処理を行う場合に実装する。
     * 
     * @param  data    入力データ
     * @param  context 実行コンテキスト
     */
    void preExecution(TData data, ExecutionContext context);
    
    /**
     * 実行制御ハンドラが後続処理を実行した後、
     * 後続のハンドラでエラーが発生した場合に呼ばれる。
     * 
     * @param error   後続ハンドラの処理中に発生した実行時例外/エラー
     * @param context 実行コンテキスト
     */
    void errorInExecution(Throwable error, ExecutionContext context);
    
    /**
     * 実行制御ハンドラが後続処理を実行した後、正常、異常終了を問わず
     * 処理が全て完了した直後に呼ばれる。
     * 
     * すなわち、正常終了時には、{@lik #preExecution(Object, ExecutionContext)}
     * の後、異常終了時には {@link #errorInExecution(Throwable, ExecutionContext)}
     * の後で本メソッドが呼ばれる。
     * 
     * @param result ハンドラの戻り値となるオブジェクト
     * @param context 実行コンテキスト
     */
     void postExecution(TResult result, ExecutionContext context);
}
