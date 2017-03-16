package nablarch.fw.batch.sample;

import nablarch.core.db.statement.SqlRow;
import nablarch.core.db.support.DbAccessSupport;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Interceptor;
import nablarch.fw.Result;
import nablarch.fw.action.BatchAction;
import nablarch.fw.launcher.CommandLine;
import nablarch.fw.reader.DatabaseRecordReader;

import java.lang.annotation.*;
import java.util.HashMap;
import java.util.Map;


public class TestActionWithCallback extends BatchAction<SqlRow> {

    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Interceptor(BeforeLog.Impl.class)
    public @interface BeforeLog {
        public static class Impl extends Interceptor.Impl<SqlRow, Result, BeforeLog>{
            public Result handle(SqlRow record, ExecutionContext ctx) {
                if (Integer.valueOf(record.getString("id")) % 10 == 0) {
                    Map<String, Object> log = new HashMap<String, Object>();
                    log.put("id",       record.get("id"));
                    log.put("activity", "beforelog.handle");
                    new DbAccessSupport(TestActionWithCallback.class)
                       .getParameterizedSqlStatement("RECORD_RESULT")
                       .executeUpdateByMap(log);
                }
                return getOriginalHandler().handle(record, ctx);
            }
        }
    }
    
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Interceptor(AfterLog.Impl.class)
    public @interface AfterLog {
        public static class Impl extends Interceptor.Impl<SqlRow, Result, AfterLog>{
            public Result handle(SqlRow record, ExecutionContext ctx) {
                Result result = getOriginalHandler().handle(record, ctx);
                if (Integer.valueOf(record.getString("id")) % 10 == 0) {
                    Map<String, Object> log = new HashMap<String, Object>();
                    log.put("id",       record.get("id"));
                    log.put("activity", "afterlog.handle");
                    new DbAccessSupport(TestActionWithCallback.class)
                       .getParameterizedSqlStatement("RECORD_RESULT")
                       .executeUpdateByMap(log);
                }
                return result;
            }
        }
    }
    
    private DbAccessSupport dbAccess = new DbAccessSupport(TestActionWithCallback.class);
    
    @Override
    @BeforeLog
    @AfterLog
    public Result handle(SqlRow record, ExecutionContext ctx) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id",       record.get("id"));
        result.put("activity", "action.handle");
        dbAccess.getParameterizedSqlStatement("RECORD_RESULT")
                .executeUpdateByMap(result);
        return new Result.Success();
    }
    
    @Override
    public DataReader<SqlRow> createReader(ExecutionContext ctx) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id",       "0");
        result.put("activity", "action.createReader");
        dbAccess.getParameterizedSqlStatement("RECORD_RESULT")
                .executeUpdateByMap(result);
        
        return new DatabaseRecordReader()
              .setStatement(dbAccess.getSqlPStatement("READ_INPUT_DATA"));
    }
    
    @Override
    public void transactionSuccess(SqlRow record, ExecutionContext ctx) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id",       record.get("id"));
        result.put("activity", "action.transactionSuccess");
        dbAccess.getParameterizedSqlStatement("RECORD_RESULT")
                .executeUpdateByMap(result);
    }
    
    @Override
    public void transactionFailure(SqlRow record, ExecutionContext ctx) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id",       record.get("id"));
        result.put("activity", "action.transactionFailure");
        dbAccess.getParameterizedSqlStatement("RECORD_RESULT")
                .executeUpdateByMap(result);
    }

    @Override
    protected void initialize(CommandLine command, ExecutionContext context) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id",       "0");
        result.put("activity", "action.initialize");
        dbAccess.getParameterizedSqlStatement("RECORD_RESULT")
                .executeUpdateByMap(result);
    }
    
    @Override
    protected void error(Throwable e, ExecutionContext context) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id",       "0");
        result.put("activity", "action.error");
        dbAccess.getParameterizedSqlStatement("RECORD_RESULT")
                .executeUpdateByMap(result);
    }

    @Override
    protected void terminate(Result resultData, ExecutionContext context) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id",       "0");
        result.put("activity", "action.terminate");
        dbAccess.getParameterizedSqlStatement("RECORD_RESULT")
                .executeUpdateByMap(result);
    }
}
