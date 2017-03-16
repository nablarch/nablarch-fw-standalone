package nablarch.fw.handler.action;

import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlRow;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Result;
import nablarch.fw.action.BatchAction;
import nablarch.fw.reader.DatabaseRecordReader;

/**
 * テスト用のバッチアクション
 * 本処理で{@link StackOverflowError}が発生し、
 * エラー時のコールバック処理で{@link OutOfMemoryError}が発生する。
 */
public class ErrorOnFailureCallbackBatchAction1 extends BatchAction<SqlRow> {

    @Override
    public Result handle(SqlRow inputData, ExecutionContext ctx) {
        throw new StackOverflowError();
    }

    @Override
    public DataReader<SqlRow> createReader(ExecutionContext ctx) {

        SqlPStatement statement = getSqlPStatement("GET_INPUT_DATA");
        DatabaseRecordReader reader = new DatabaseRecordReader();
        reader.setStatement(statement);
        return reader;
    }

    @Override
    protected void transactionFailure(SqlRow inputData, ExecutionContext context) {
        throw new OutOfMemoryError("!!!");
    }
}
