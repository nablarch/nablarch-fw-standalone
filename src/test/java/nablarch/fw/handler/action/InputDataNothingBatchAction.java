package nablarch.fw.handler.action;

import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlRow;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Result;
import nablarch.fw.TransactionEventCallback;
import nablarch.fw.action.BatchAction;
import nablarch.fw.reader.DatabaseRecordReader;

/** テスト用のバッチアクション */
public class InputDataNothingBatchAction extends BatchAction<SqlRow> {

    /** 障害時のコールバックが呼び出されたか否か */
    public static boolean failuerCall = false;

    /** 正常時のコールバックが呼び出されたか否か */
    public static boolean normalEndCall = false;

    /** エラーを発生させる */
    public static boolean onError = false;

    @Override
    public Result handle(SqlRow inputData, ExecutionContext ctx) {
        ctx.setRequestScopedVar(
                TransactionEventCallback.REQUEST_DATA_REQUEST_SCOPE_KEY, null);
        if (onError) {
            throw new IllegalStateException();
        }
        return new Result.Success();
    }

    @Override
    public DataReader<SqlRow> createReader(ExecutionContext ctx) {

        SqlPStatement statement = getSqlPStatement("GET_INPUT_DATA");
        DatabaseRecordReader reader = new DatabaseRecordReader();
        reader.setStatement(statement);
        return reader;
    }

    @Override
    protected void transactionSuccess(SqlRow inputData, ExecutionContext context) {
        normalEndCall = true;
    }

    @Override
    protected void transactionFailure(SqlRow inputData, ExecutionContext context) {
        failuerCall = true;
    }
}
