package nablarch.fw.handler.action;


import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.TransactionEventCallback;

/**
 * テスト用のバッチアクション
 * 本処理で{@link StackOverflowError}が発生し、
 * エラー時のコールバック処理で{@link OutOfMemoryError}が発生する。
 */
public class RuntimeExceptionOnFailureCallbackHandler1 implements TransactionEventCallback<String>, Handler<String, Result> {
    @Override
    public Result handle(String input, ExecutionContext context) {
        throw new NullPointerException();
    }

    @Override
    public void transactionNormalEnd(String input, ExecutionContext ctx) {
        // NOP
    }

    @Override
    public void transactionAbnormalEnd(Throwable e, String input, ExecutionContext ctx) {
        throw new NumberFormatException();
    }
}
