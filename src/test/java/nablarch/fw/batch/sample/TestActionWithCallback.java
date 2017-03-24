package nablarch.fw.batch.sample;

import nablarch.core.db.statement.ParameterizedSqlPStatement;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlRow;
import nablarch.core.db.support.DbAccessSupport;

import nablarch.fw.DataReader;
import nablarch.fw.DataReaderFactory;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Interceptor;
import nablarch.fw.Result;
import nablarch.fw.TransactionEventCallback;
import nablarch.fw.handler.ExecutionHandlerCallback;
import nablarch.fw.launcher.CommandLine;


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class TestActionWithCallback extends DbAccessSupport implements DataReaderFactory<SqlRow>, ExecutionHandlerCallback<CommandLine, Result>, TransactionEventCallback<SqlRow>, Handler<SqlRow, Result> {

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
    public void preExecution(CommandLine commandLine, ExecutionContext context) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id", "0");
        result.put("activity", "action.initialize");
        dbAccess.getParameterizedSqlStatement("RECORD_RESULT")
                .executeUpdateByMap(result);
    }

    @Override
    public void errorInExecution(Throwable error, ExecutionContext context) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id", "0");
        result.put("activity", "action.error");
        dbAccess.getParameterizedSqlStatement("RECORD_RESULT")
                .executeUpdateByMap(result);
    }

    @Override
    public void postExecution(Result result, ExecutionContext context) {
        Map<String, Object> condition = new HashMap<String, Object>();
        condition.put("id", "0");
        condition.put("activity", "action.terminate");
        dbAccess.getParameterizedSqlStatement("RECORD_RESULT")
                .executeUpdateByMap(condition);
    }

    @Override
    public void transactionNormalEnd(SqlRow record, ExecutionContext ctx) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id", record.get("id"));
        result.put("activity", "action.transactionSuccess");
        dbAccess.getParameterizedSqlStatement("RECORD_RESULT")
                .executeUpdateByMap(result);
    }

    @Override
    public void transactionAbnormalEnd(Throwable e, SqlRow record, ExecutionContext ctx) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id", record.get("id"));
        result.put("activity", "action.transactionFailure");
        dbAccess.getParameterizedSqlStatement("RECORD_RESULT")
                .executeUpdateByMap(result);
    }

    /**
     * データベースの参照結果を1レコードづつ読み込むデータリーダ。
     */
    private static class DatabaseRecordReader implements DataReader<SqlRow> {

        /** 参照結果レコードのイテレータ */
        private Iterator<SqlRow> records;

        /** テーブル参照用SQLステートメント */
        private SqlPStatement statement;

        @Override
        public synchronized SqlRow read(ExecutionContext ctx) {
            if (records == null) {
                readRecords();
            }
            return records.hasNext() ? records.next() : null;
        }

        @Override
        public synchronized boolean hasNext(ExecutionContext ctx) {
            if (records == null) {
                readRecords();
            }
            return records.hasNext();
        }

        @Override
        public synchronized void close(ExecutionContext ctx) {
            if (statement != null) {
                statement.close();
            }
        }

        /**
         * テーブルを参照するSQLステートメントを設定する。
         *
         * @param statement SQLステートメント
         * @return このオブジェクト自体
         */
        public synchronized DatabaseRecordReader setStatement(SqlPStatement statement) {
            this.statement = statement;
            return this;
        }

        /**
         * 参照結果のイテレータをキャッシュする。
         */
        @SuppressWarnings("unchecked")
        private void readRecords() {
            if (statement != null) {
                records = statement.executeQuery().iterator();
            }
        }
    }
}
