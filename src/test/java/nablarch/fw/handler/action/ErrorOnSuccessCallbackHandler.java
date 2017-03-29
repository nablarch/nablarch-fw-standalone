package nablarch.fw.handler.action;

import nablarch.core.db.statement.ParameterizedSqlPStatement;
import nablarch.core.db.support.DbAccessSupport;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.Result.Success;
import nablarch.fw.TransactionEventCallback;

import java.util.HashMap;
import java.util.Map;

/** テスト用のバッチアクション */
public class ErrorOnSuccessCallbackHandler extends DbAccessSupport implements TransactionEventCallback<String>, Handler<String, Result> {

    @Override
    public Result handle(String input, ExecutionContext ctx) {
        return new Success();
    }

    @Override
    public void transactionNormalEnd(String input, ExecutionContext ctx) {
        // 正常終了時の処理で例外！！
        throw new NullPointerException("null");
    }

    @Override
    public void transactionAbnormalEnd(Throwable e, String input, ExecutionContext ctx) {
        ParameterizedSqlPStatement statement = getParameterizedSqlStatement("UPDATE_STATUS_ERROR");
        Map<String, String> condition = new HashMap<String, String>();
        condition.put("id", input);
        statement.executeUpdateByMap(condition);
    }
}
