package nablarch.fw.batch.sample;

import nablarch.core.db.connection.AppDbConnection;
import nablarch.core.db.connection.DbConnectionContext;
import nablarch.core.db.statement.SqlPStatement;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Result;
import nablarch.fw.action.BatchAction;
import nablarch.reader.StandardInputRecordReader;
import nablarch.test.support.tool.Hereis;

import java.util.List;

public class RegisteringBookDataTask extends BatchAction<List<String>> {

    public DataReader<List<String>> createReader(ExecutionContext ctx) {
        return new StandardInputRecordReader()
                .setFieldSeparator(",\\s*");
    }

    public Result handle(List<String> bookData, ExecutionContext ctx) {
        if (bookData.size() != 3) {
            throw new IllegalArgumentException();
        }
        String title     = bookData.get(0);
        String publisher = bookData.get(1);
        String authors   = bookData.get(2);

        AppDbConnection conn = DbConnectionContext.getConnection();
        SqlPStatement stmt = conn.prepareStatement(Hereis.string());
        /******************
         INSERT INTO Book
         VALUES (
         ?, -- title
         ?, -- publisher
         ?  -- authors
         )
         ******************/
        stmt.setString(1, title    );
        stmt.setString(2, publisher);
        stmt.setString(3, authors  );
        stmt.execute();

        return new Result.Success();
    }
}
