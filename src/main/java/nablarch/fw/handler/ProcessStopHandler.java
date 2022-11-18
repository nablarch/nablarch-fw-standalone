package nablarch.fw.handler;

import nablarch.core.util.annotation.Published;
import nablarch.fw.Handler;

/**
 * 処理中のプロセスを停止するためのハンドラ。
 * <p/>
 * 本ハンドラは、{@link LoopHandler}や{@link ProcessResidentHandler}の後続ハンドラに設定することにより、
 * 処理中に安全にプロセスを停止することが可能となる。
 * <p/>
 * なお、プロセスを停止するために{@link ProcessStop}を送出するため、障害通知ログが出力されプロセスは異常終了する。
 * 異常終了する際に終了コードは、{@link #setExitCode(int)}によって設定することが出来る。
 * 終了コードの設定を省略した場合のデフォルト動作として終了コードは1となる。
 * <b>また、未コミットのトランザクションは全てロールバックされることに注意すること。</b>
 * <p/>
 * ※処理を異常終了するかどうかは、前段に設定されたハンドラによって決定される。
 * <p/>
 * 処理を停止するか否かのチェックは、リクエストテーブルにて行う。
 * 本ハンドラが使用するリクエストテーブルの定義情報を下記に示す。
 * <p/>
 * <pre>
 * -----------------------------+----------------------------------------------------------
 * カラム名                     | 説明
 * -----------------------------+----------------------------------------------------------
 * リクエストID                 | プロセスを特定するためのリクエストID
 * 処理停止フラグ               | 処理を停止するか否かの情報
 *                              | 本フラグの値が'1'の場合に処理を停止する。
 *                              |
 *                              | <b>本フラグの値は、自動的に'0'には変更されないため再実行する際には、
 *                              | 手動で'0'に変更する必要がある。</b>
 * -----------------------------+----------------------------------------------------------
 * </pre>
 *
 * @author hisaaki sioiri
 */
public interface ProcessStopHandler extends Handler<Object, Object> {

    boolean isProcessStop(String requestId);

    /**
     * チェック間隔（{@link #handle(Object, nablarch.fw.ExecutionContext)}が
     * 何回呼び出されるごとに停止フラグを確認するか？）を設定する。
     * <p/>
     *
     * @param checkInterval チェック間隔(0以下の値が設定された場合は1)
     */
    public void setCheckInterval(int checkInterval);


    /**
     * 終了コードを設定する。
     * <p/>
     * 終了コードの設定がない場合、デフォルトで{@link nablarch.fw.results.InternalError#STATUS_CODE}が使用される。
     *
     * @param exitCode 終了コード
     */
    public void setExitCode(int exitCode);

    /** プロセス停止を示す例外。 */
    @Published(tag = "architect")
    public static final class ProcessStop extends nablarch.fw.results.InternalError {

        /** 終了コード */
        private final int exitCode;

        /**
         * コンストラクタ。
         *
         * @param exitCode 終了コード
         */
        // CHANGE: Basic実装からnewするためスコープを広げる。
        public ProcessStop(int exitCode) {
            super("kill process.");
            this.exitCode = exitCode;
        }

        @Override
        public int getStatusCode() {
            return exitCode;
        }
    }
}

