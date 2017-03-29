package nablarch.fw.batch.sample;

import nablarch.core.ThreadContext;
import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlRow;

import nablarch.fw.DataReader;
import nablarch.fw.DataReaderFactory;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.handler.ExecutionHandlerCallback;
import nablarch.fw.launcher.CommandLine;
import nablarch.fw.results.BadRequest;
import nablarch.fw.results.Conflicted;

import java.util.Iterator;

public class ReplicateBookDataTask implements DataReaderFactory<SqlRow>, ExecutionHandlerCallback<CommandLine, Result>, Handler<SqlRow, Result> {

    /**
     * バックアップ元テーブルからレコードを1行づつ読み込む。
     */
    public DataReader<SqlRow> createReader(ExecutionContext ctx) {
        SqlPStatement statement = DbConnectionContext
                .getConnection()
                .prepareStatement("SELECT * FROM BOOK");

        return new DatabaseRecordReader().setStatement(statement);
    }

    private static int count = 0;

    /**
     * レコード1件分をバックアップテーブルに登録する。
     */
    public Result handle(SqlRow bookData, ExecutionContext ctx) {
        if (bookData.size() != 3) {
            throw new IllegalArgumentException();
        }
        if (command.getParamMap()
                .containsKey("errorOnHandle")) {
            int errorCount = Integer.valueOf(command.getParam("errorOnHandle"));
            synchronized (this.getClass()) {
                count++;
                if (count >= errorCount) {
                    throw new RuntimeException("error on handle");
                }
            }
        }
        AppDbConnection conn = DbConnectionContext.getConnection();
        SqlPStatement stmt = conn.prepareStatement("INSERT INTO Book_backup "
                + " VALUES (?, ?, ?, ?)");

        stmt.setString(1, bookData.getString("title"));
        stmt.setString(2, bookData.getString("publisher"));
        stmt.setString(3, bookData.getString("authors"));
        stmt.setString(4, ThreadContext.getUserId());
        stmt.execute();

        return new Result.Success();
    }

    private static CommandLine command = null;

    @Override
    public void preExecution(CommandLine commandLine, ExecutionContext context) {
        command = commandLine;
        if (command.getParamMap()
                .containsKey("errorOnInit") && Boolean.valueOf(command.getParam("errorOnInit"))) {
            throw new BadRequest("error on init");
        }
    }

    @Override
    public void errorInExecution(Throwable error, ExecutionContext context) {
        // nop
    }

    @Override
    public void postExecution(Result result, ExecutionContext context) {
        if (command.getParamMap()
                .containsKey("errorOnEnd") && Boolean.valueOf(command.getParam("errorOnEnd"))) {
            throw new Conflicted("error on end");
        }
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
