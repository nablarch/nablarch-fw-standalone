package nablarch.fw.handler.action;

import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.TransactionEventCallback;

/** テスト用のバッチアクション */
public class InputDataNothingHandler implements TransactionEventCallback<String>, Handler<String, Result> {

    /** 障害時のコールバックが呼び出されたか否か */
    public static boolean failuerCall;

    /** 正常時のコールバックが呼び出されたか否か */
    public static boolean normalEndCall;

    /** エラーを発生させる */
    public static boolean onError;

    @Override
    public Result handle(String input, ExecutionContext context) {
        context.setRequestScopedVar(
                TransactionEventCallback.REQUEST_DATA_REQUEST_SCOPE_KEY, null);
        if (onError) {
            throw new IllegalStateException();
        }
        return new Result.Success();
    }

    @Override
    public void transactionNormalEnd(String input, ExecutionContext ctx) {
        normalEndCall = true;
    }

    @Override
    public void transactionAbnormalEnd(Throwable e, String input, ExecutionContext ctx) {
        failuerCall = true;
    }
}
