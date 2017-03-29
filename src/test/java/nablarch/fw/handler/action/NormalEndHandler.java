package nablarch.fw.handler.action;

import nablarch.core.db.statement.ParameterizedSqlPStatement;
import nablarch.core.db.support.DbAccessSupport;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.TransactionEventCallback;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link nablarch.fw.handler.LoopHandlerTransactionCallbackTest}用の正常終了するハンドラ。
 */
public class NormalEndHandler extends DbAccessSupport implements TransactionEventCallback<String>, Handler<String, Result> {

    @Override
    public Result handle(String input, ExecutionContext context) {
        return new Result.Success();
    }

    @Override
    public void transactionNormalEnd(String input, ExecutionContext ctx) {
        ParameterizedSqlPStatement statement = getParameterizedSqlStatement(
                "UPDATE_STATUS_NORMAL");

        Map<String, String> condition = new HashMap<String, String>();
        condition.put("id", input);

        statement.executeUpdateByMap(condition);
    }

    @Override
    public void transactionAbnormalEnd(Throwable e, String input, ExecutionContext ctx) {
        // nop
    }
}
