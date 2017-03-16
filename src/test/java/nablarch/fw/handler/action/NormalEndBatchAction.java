package nablarch.fw.handler.action;

import nablarch.core.db.statement.ParameterizedSqlPStatement;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlRow;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Result;
import nablarch.fw.action.BatchAction;
import nablarch.fw.reader.DatabaseRecordReader;

/** テスト用のバッチアクション */
public class NormalEndBatchAction extends BatchAction<SqlRow> {

    @Override
    public Result handle(SqlRow inputData, ExecutionContext ctx) {
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
        ParameterizedSqlPStatement statement = getParameterizedSqlStatement(
                "UPDATE_STATUS_NORMAL");
        statement.executeUpdateByMap(inputData);
    }
}
