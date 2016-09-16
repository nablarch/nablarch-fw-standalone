package nablarch.fw.launcher.logging;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nablarch.core.log.LogItem;
import nablarch.core.log.LogUtil;
import nablarch.core.log.app.AppLogUtil;
import nablarch.core.util.StringUtil;
import nablarch.core.util.annotation.Published;
import nablarch.fw.launcher.CommandLine;

/**
 * {@link nablarch.fw.launcher.Main}で出力するログメッセージをフォーマットするクラス。
 *
 * @author hisaaki sioiri
 */
@Published(tag = "architect")
public class LauncherLogFormatter {

    /** プロパティ名のプレフィックス */
    public static final String PROPS_PREFIX = "launcherLogFormatter.";

    /** 開始ログのフォーマット定義 */
    private static final String DEFAULT_START_LOG_FORMAT = "@@@@ BEGIN @@@@"
            + '\n' + '\t' + "command line options = {"
            + "$commandLineOptions$"
            + '\n' + '\t' + '}'
            + '\n' + '\t' + "command line arguments = {"
            + "$commandLineArguments$"
            + '\n' + '\t' + '}';

    /** 終了ログのフォーマット定義 */
    private static final String DEFAULT_END_LOG_FORMAT = "@@@@ END @@@@"
            + " exit code = [$exitCode$] execute time(ms) = [$executeTime$]";

    /** 開始ログの出力項目 */
    private final Map<String, LogItem<LauncherLogContext>> startLogItems = getStartLogItems();

    /** 終了ログの出力項目 */
    private final Map<String, LogItem<LauncherLogContext>> endLogItems = getEndLogItems();

    /**
     * 開始ログを生成する。
     * <p/>
     * {@link #getStartLogFormat()}から取得したログフォーマットに従いログメッセージ生成を行う。
     *
     * @param commandLine {@link CommandLine コマンドラインオブジェクト}
     * @return 生成した開始ログ
     */
    public String getStartLogMsg(CommandLine commandLine) {
        LogItem<LauncherLogContext>[] items = LogUtil.createFormattedLogItems(
                startLogItems, getStartLogFormat());

        LauncherLogContext context = new LauncherLogContext();
        context.setCommandLine(commandLine);
        return LogUtil.formatMessage(items, context);
    }

    /**
     * 終了ログを生成する。
     * <p/>
     * {@link #getEndLogFormat()}から取得したログフォーマットに従いログメッセージの生成を行う。
     *
     * @param exitCode 終了コード
     * @param executeTime 処理時間
     * @return 生成した終了ログ
     */
    public String getEndLogMsg(int exitCode, long executeTime) {
        LogItem<LauncherLogContext>[] items = LogUtil.createFormattedLogItems(
                endLogItems, getEndLogFormat());
        LauncherLogContext context = new LauncherLogContext();
        context.setExitCode(exitCode);
        context.setExecuteTime(executeTime);
        return LogUtil.formatMessage(items, context);
    }

    /**
     * 開始ログのフォーマットを取得する。
     * <p/>
     * 設定ファイル(nablarch.core.log.app.AppLogUtil#getProps())にログフォーマットが指定されている場合は、そのフォーマットを返却する。
     * 設定されていない場合には、デフォルトのフォーマットを使用する。
     * <p/>
     * デフォルトのフォーマットは、以下の設定例のようにフォーマット定義を行うことにより変更可能
     * <pre>
     * {@code
     * launcherLogFormatter.startFormat = @@@@ BEGIN @@@@\n\tcommandLineArguments = [$commandLineArguments$]
     * }
     * </pre>
     *
     * @return 開始ログのフォーマット
     */
    protected String getStartLogFormat() {
        String overrideFormat = AppLogUtil.getProps().get(
                PROPS_PREFIX + "startFormat");
        if (overrideFormat == null) {
            return DEFAULT_START_LOG_FORMAT;
        }
        return overrideFormat;
    }

    /**
     * 開始ログ用のログ出力項目を生成する。
     *
     * @return 生成したログ出力項目
     */
    protected Map<String, LogItem<LauncherLogContext>> getStartLogItems() {
        Map<String, LogItem<LauncherLogContext>> logItem
                = new HashMap<String, LogItem<LauncherLogContext>>();
        logItem.put("$commandLineOptions$", new CommandLineOptions());
        logItem.put("$commandLineArguments$", new CommandLineArguments());
        return logItem;
    }

    /**
     * 終了ログ用のログ出力項目を生成する。
     *
     * @return 生成したログ出力項目
     */
    protected Map<String, LogItem<LauncherLogContext>> getEndLogItems() {
        Map<String, LogItem<LauncherLogContext>> logItem = new HashMap<String, LogItem<LauncherLogContext>>();
        logItem.put("$exitCode$", new ExitCode());
        logItem.put("$executeTime$", new ExecuteTime());
        return logItem;
    }

    /**
     * 終了ログのフォーマットを取得する。
     * <p/>
     * 設定ファイル(nablarch.core.log.app.AppLogUtil#getProps())にログフォーマットが指定されている場合は、
     * そのフォーマットを返却する。
     * 設定されていない場合には、デフォルトのフォーマットを使用する。
     * <p/>
     * デフォルトのフォーマットは、以下の設定例のようにフォーマット定義を行うことにより変更可能
     * <pre>
     * {@code
     * launcherLogFormatter.endFormat = @@@@ END @@@@ execute time(ms) = [$executeTime$], exit code = [$exitCode$]
     * }
     * </pre>
     *
     * @return 開始ログのフォーマット
     */
    protected String getEndLogFormat() {
        String overrideFormat = AppLogUtil.getProps().get(
                PROPS_PREFIX + "endFormat");

        if (overrideFormat == null) {
            return DEFAULT_END_LOG_FORMAT;
        }
        return overrideFormat;
    }

    /**
     * バッチ実行ログコンテキスト
     *
     * @author hisaaki sioiri
     */
    protected static class LauncherLogContext {

        /** コマンドライン引数 */
        private CommandLine commandLine;

        /** 終了コード */
        private int exitCode;

        /** 実行時間 */
        private long executeTime;

        /**
         * コマンドラインを取得する。
         *
         * @return コマンドラインオブジェクト
         */
        protected CommandLine getCommandLine() {
            return commandLine;
        }

        /**
         * コマンドラインを設定する。
         *
         * @param commandLine コマンドラインオブジェクト
         */
        protected void setCommandLine(CommandLine commandLine) {
            this.commandLine = commandLine;
        }

        /**
         * 終了コードを取得する。
         *
         * @return 終了コード
         */
        protected int getExitCode() {
            return exitCode;
        }

        /**
         * 終了コードを設定する。
         *
         * @param exitCode 終了コード
         */
        protected void setExitCode(int exitCode) {
            this.exitCode = exitCode;
        }

        /**
         * 処理時間を取得する。
         *
         * @return 処理時間
         */
        protected long getExecuteTime() {
            return executeTime;
        }

        /**
         * 処理時間を設定する。
         *
         * @param executeTime 処理時間
         */
        protected void setExecuteTime(long executeTime) {
            this.executeTime = executeTime;
        }
    }

    /**
     * コマンドラインオプションを取得する。
     * <p/>
     * コマンドラインオプションは、{@link nablarch.fw.launcher.CommandLine#getParamMap()} から取得した値。
     *
     * @author hisaaki sioiri
     */
    protected static class CommandLineOptions implements LogItem<LauncherLogContext> {

        /**
         * コマンドラインオプションを取得する。
         *
         * @param context ログの出力項目の取得に使用するコンテキスト
         * @return 整形したコマンドラインオプション
         */
        public String get(LauncherLogContext context) {
            CommandLine line = context.getCommandLine();

            StringBuilder result = new StringBuilder();
            Map<String, String> paramMap = line.getParamMap();
            for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                result.append('\n');
                result.append("\t\t");
                result.append(key);
                result.append(" = [");
                result.append(value);
                result.append(']');
            }
            return result.toString();
        }
    }

    /**
     * コマンドライン引数を取得する。
     * <p/>
     * コマンドライン引数は、{@link nablarch.fw.launcher.CommandLine#getArgs()}から取得する。
     *
     * @author hisaaki sioiri
     */
    protected static class CommandLineArguments implements LogItem<LauncherLogContext> {

        /**
         * コマンドライン引数を取得する。
         * <p/>
         * コマンドライン引数は、以下のフォーマットで文字列に変換する。
         * <pre>
         *     \n\t\tインデックス(2桁先頭0埋め) = [引数値]
         *
         *     例：コマンドライン引数が、「param1 param2 param3」の場合
         *     \n\t\t01 = [param1]
         *     \n\t\t02 = [param2]
         *     \n\t\t03 = [param3]
         * </pre>
         *
         * @param context ログの出力項目の取得に使用するコンテキスト
         * @return コマンドライン引数をフォーマットした値
         */
        public String get(LauncherLogContext context) {

            List<String> arguments = context.getCommandLine().getArgs();

            StringBuilder result = new StringBuilder();
            for (int i = 0, size = arguments.size(); i < size; i++) {
                String argument = arguments.get(i);
                result.append('\n');
                result.append("\t\t");
                result.append(StringUtil.lpad(String.valueOf(i + 1), 2, '0'));
                result.append(" = [");
                result.append(argument);
                result.append(']');
            }
            return result.toString();
        }
    }

    /**
     * 終了コードを取得する。
     *
     * @author hisaaki sioiri
     */
    protected static class ExitCode implements LogItem<LauncherLogContext> {

        /**
         * 終了コードを取得する。
         * <p/>
         * {@link LauncherLogContext#getExitCode()}
         * から取得した終了コードを返却する。
         *
         * @param context ログの出力項目の取得に使用するコンテキスト
         * @return 終了コード
         */
        public String get(LauncherLogContext context) {
            return String.valueOf(context.getExitCode());
        }
    }

    /**
     * 実行時間を取得する。
     *
     * @author hisaaki sioiri
     */
    protected static class ExecuteTime implements LogItem<LauncherLogContext> {

        /**
         * 実行時間を取得する。
         * <p/>
         * {@link LauncherLogContext#getExecuteTime()}
         * から取得した実行時間を返却する。
         *
         * @param context ログの出力項目の取得に使用するコンテキスト
         * @return 実行時間
         */
        public String get(LauncherLogContext context) {
            return String.valueOf(context.getExecuteTime());
        }
    }
}

