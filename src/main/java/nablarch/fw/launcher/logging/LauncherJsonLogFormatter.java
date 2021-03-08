package nablarch.fw.launcher.logging;

import nablarch.core.log.app.AppLogUtil;
import nablarch.core.log.app.JsonLogFormatterSupport;
import nablarch.core.util.StringUtil;
import nablarch.fw.launcher.CommandLine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 起動ログのメッセージをJSON形式でフォーマットするクラス。
 *
 * @author Shuji Kitamura
 */
public class LauncherJsonLogFormatter extends LauncherLogFormatter {

    /** 連絡先の項目名 */
    private static final String TARGET_NAME_COMMAND_LINE_OPTIONS = "commandLineOptions";
    /** 連絡先の項目名 */
    private static final String TARGET_NAME_COMMAND_LINE_ARGUMENTS = "commandLineArguments";
    /** 連絡先の項目名 */
    private static final String TARGET_NAME_EXIT_CODE = "exitCode";
    /** 連絡先の項目名 */
    private static final String TARGET_NAME_EXECUTE_TIME = "executeTime";

    /** 開始ログの出力項目を取得する際に使用するプロパティ名 */
    private static final String PROPS_START_LOGTARGETS = PROPS_PREFIX + "startTargets";
    /** 終了ログの出力項目を取得する際に使用するプロパティ名 */
    private static final String PROPS_END_LOG_TARGETS = PROPS_PREFIX + "endTargets";

    /** 開始ログ出力項目のデフォルト値 */
    private static final String DEFAULT_START_LOG_TARGETS = "commandLineOptions,commandLineArguments";
    /** 終了ログ出力項目のデフォルト値 */
    private static final String DEFAULT_END_LOG_TARGETS = "exitCode,executeTime";

    /** 各種ログのJSONフォーマット支援オブジェクト */
    private JsonLogFormatterSupport support = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStartLogMsg(CommandLine commandLine) {
        if (support == null) support = new JsonLogFormatterSupport(PROPS_PREFIX);

        Map<String, Object> structuredObject
                = getStartStructuredObject(AppLogUtil.getProps(), commandLine);
        return support.getStructuredMessage(structuredObject);
    }

    /**
     * 開始ログの構造化オブジェクトを生成する。
     * @param props 各種ログ出力の設定情報
     * @param commandLine {@link CommandLine コマンドラインオブジェクト}
     * @return 構造化オブジェクト
     */
    protected Map<String, Object> getStartStructuredObject(Map<String, String> props, CommandLine commandLine) {
        String targetsStr = props.get(PROPS_START_LOGTARGETS);
        if (StringUtil.isNullOrEmpty(targetsStr)) targetsStr = DEFAULT_START_LOG_TARGETS;

        Map<String, Object> structuredObject = new HashMap<String, Object>();

        String[] targets = targetsStr.split(",");
        Set<String> keys = new HashSet<String>(targets.length);
        for (String target: targets) {
            String key = target.trim();
            if (!StringUtil.isNullOrEmpty(key) && !keys.contains(key)) {
                keys.add(key);
                if (TARGET_NAME_COMMAND_LINE_OPTIONS.equals(key)) {
                    structuredObject.put(
                            TARGET_NAME_COMMAND_LINE_OPTIONS,
                            commandLine.getParamMap());
                } else if (TARGET_NAME_COMMAND_LINE_ARGUMENTS.equals(key)) {
                    structuredObject.put(
                            TARGET_NAME_COMMAND_LINE_ARGUMENTS,
                            commandLine.getArgs());
                }
            }
        }

        return structuredObject;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getEndLogMsg(int exitCode, long executeTime) {
        if (support == null) support = new JsonLogFormatterSupport(PROPS_PREFIX);

        Map<String, Object> structuredObject
                = getEndStructuredObject(AppLogUtil.getProps(), exitCode, executeTime);
        return support.getStructuredMessage(structuredObject);
    }

    /**
     * 終了ログの構造化オブジェクトを生成する。
     * @param props 各種ログ出力の設定情報
     * @param exitCode 終了コード
     * @param executeTime 処理時間
     * @return 構造化オブジェクト
     */
    protected Map<String, Object> getEndStructuredObject(Map<String, String> props, int exitCode, long executeTime) {
        String targetsStr = props.get(PROPS_END_LOG_TARGETS);
        if (StringUtil.isNullOrEmpty(targetsStr)) targetsStr = DEFAULT_END_LOG_TARGETS;

        Map<String, Object> structuredObject = new HashMap<String, Object>();

        String[] targets = targetsStr.split(",");
        Set<String> keys = new HashSet<String>(targets.length);
        for (String target: targets) {
            String key = target.trim();
            if (!StringUtil.isNullOrEmpty(key) && !keys.contains(key)) {
                keys.add(key);
                if (TARGET_NAME_EXIT_CODE.equals(key)) {
                    structuredObject.put(TARGET_NAME_EXIT_CODE, exitCode);
                } else if (TARGET_NAME_EXECUTE_TIME.equals(key)) {
                    structuredObject.put(TARGET_NAME_EXECUTE_TIME, executeTime);
                }
            }
        }

        return structuredObject;
    }

}
