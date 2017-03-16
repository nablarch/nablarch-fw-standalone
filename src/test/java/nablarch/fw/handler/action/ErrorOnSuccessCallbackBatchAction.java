package nablarch.fw.handler.action;

import nablarch.core.db.statement.ParameterizedSqlPStatement;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlRow;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Result;
import nablarch.fw.Result.Success;
import nablarch.fw.action.BatchAction;
import nablarch.fw.reader.DatabaseRecordReader;

/** テスト用のバッチアクション */
public class ErrorOnSuccessCallbackBatchAction extends BatchAction<SqlRow> {

    @Override
    public Result handle(SqlRow inputData, ExecutionContext ctx) {
        return new Success();
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
        // 正常終了時の処理で例外！！
        throw new NullPointerException("null");
    }

    @Override
    protected void transactionFailure(SqlRow inputData,
            ExecutionContext context) {
        ParameterizedSqlPStatement statement = getParameterizedSqlStatement("UPDATE_STATUS_ERROR");
        statement.executeUpdateByMap(inputData);
    }
}
