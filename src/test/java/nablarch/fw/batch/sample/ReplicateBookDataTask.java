package nablarch.fw.batch.sample;

import nablarch.core.ThreadContext;
import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.core.db.statement.SqlRow;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Result;
import nablarch.fw.action.BatchAction;
import nablarch.fw.launcher.CommandLine;
import nablarch.fw.reader.DatabaseRecordReader;
import nablarch.fw.results.BadRequest;
import nablarch.fw.results.Conflicted;

public class ReplicateBookDataTask extends BatchAction<SqlRow> {

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


    @Override
    protected void initialize(CommandLine cmd, ExecutionContext context) {
        command = cmd;
        if (command.getParamMap()
                .containsKey("errorOnInit") && Boolean.valueOf(command.getParam("errorOnInit"))) {
            throw new BadRequest("error on init");
        }
        super.initialize(command, context);
    }

    private static CommandLine command = null;


    @Override
    protected void terminate(Result result, ExecutionContext context) {
        if (command.getParamMap()
                .containsKey("errorOnEnd") && Boolean.valueOf(command.getParam("errorOnEnd"))) {
            throw new Conflicted("error on end");
        }
        super.terminate(result, context);
    }

}
