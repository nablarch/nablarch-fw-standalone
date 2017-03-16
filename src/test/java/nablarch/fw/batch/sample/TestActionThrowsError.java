package nablarch.fw.batch.sample;

import nablarch.core.db.statement.SqlRow;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Result;

public class TestActionThrowsError extends TestActionWithCallback {
    @Override
    @BeforeLog
    @AfterLog
    public Result handle(SqlRow record, ExecutionContext ctx) {
        int id = Integer.valueOf(record.getString("id"));
        if (id == 50) {
            throw new nablarch.fw.results.InternalError();
        }
        return super.handle(record, ctx);
    }
}
