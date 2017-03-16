package nablarch.reader;

import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * 標準入力上のキャラクタストリームをレコード(行)単位で読み込むデータリーダ。
 * @author Iwauo Tajima
 */
public class StandardInputRecordReader implements DataReader<List<String>> {
    /**{@inheritDoc}
     * この実装では、標準入力から行単位のデータを1行づつ読み込む。
     * 各行のデータはフィールド区切り文字で分割された後、
     * 文字列のリストとして返される。
     */
    public synchronized List<String> read(ExecutionContext ctx) {
        if (scanner == null) {
            setupScanner();
        }
        return (scanner.hasNext())
                ? Arrays.asList(fieldSeparator.split(scanner.next()))
                : null;
    }

    /** {@inheritDoc}
     * この実装では、標準入力のストリームがEOFに到達するまでtrueを返す。
     */
    public synchronized boolean hasNext(ExecutionContext ctx) {
        if (scanner == null) {
            setupScanner();
        }
        return scanner.hasNext();
    }

    /** {@inheritDoc}
     * この実装では、使用している標準入力ストリームを開放する。
     */
    public synchronized void close(ExecutionContext ctx) {
        scanner.close();
    }

    /**
     * 標準入力を読み込むスキャナーオブジェクトを作成する。
     */
    private void setupScanner() {
        InputStream in = System.in;
        scanner = new Scanner(in, charset)
                .useDelimiter(recordSeparator);
    }
    /** 標準入力ストリーム */
    private Scanner scanner = null;

    /**
     * フィールド境界文字列を表す正規表現を設定する。
     * <pre>
     * デフォルトでは連続する空白文字(\s+)をフィールド境界とみなす。
     * </pre>
     * @param separator フィールド境界文字列を表す正規表現
     * @return このオブジェクト自体
     */
    public StandardInputRecordReader setFieldSeparator(String separator) {
        fieldSeparator = Pattern.compile(separator);
        return this;
    }
    /** フィールド境界文字列を表す正規表現 */
    private Pattern fieldSeparator = Pattern.compile("\\s+");

    /**
     * レコード境界文字列を表す正規表現を設定する。
     * <pre>
     * デフォルトではCR(\r), LF(\n), CRLF(\r\n)をフィールド境界とみなす。
     * </pre>
     * @param separator 行境界文字列を表す正規表現
     * @return このオブジェクト自体
     */
    public StandardInputRecordReader setRecordSeparator(String separator) {
        recordSeparator = Pattern.compile(separator);
        return this;
    }
    /** レコード境界文字列を表す正規表現。 */
    private Pattern recordSeparator = Pattern.compile("\\r\\n|[\\n\\r]");

    /**
     * ストリームを読み込む際に使用する文字エンコーディングを設定する。
     * <pre>
     * デフォルトではUTF-8を使用する。
     * </pre>
     * @param charset エンコーディング名
     * @return このオブジェクト自体
     */
    public StandardInputRecordReader setCharset(String charset) {
        this.charset = charset;
        return this;
    }

    /** 使用する文字エンコーディング */
    private String charset = "UTF-8";
}
