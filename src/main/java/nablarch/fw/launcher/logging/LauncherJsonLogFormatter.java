package nablarch.fw.launcher.logging;

import nablarch.core.log.app.AppLogUtil;
import nablarch.core.log.app.JsonLogFormatterSupport;
import nablarch.core.log.basic.JsonLogObjectBuilder;
import nablarch.core.text.json.BasicJsonSerializationManager;
import nablarch.core.text.json.JsonSerializationManager;
import nablarch.core.text.json.JsonSerializationSettings;
import nablarch.core.util.StringUtil;
import nablarch.fw.launcher.CommandLine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 起動ログのメッセージをJSON形式でフォーマットするクラス。
 *
 * @author Shuji Kitamura
 */
public class LauncherJsonLogFormatter extends LauncherLogFormatter {

    /** ラベルの項目名 */
    private static final String TARGET_NAME_LABEL = "label";
    /** コマンドラインオプションの項目名 */
    private static final String TARGET_NAME_COMMAND_LINE_OPTIONS = "commandLineOptions";
    /** コマンドライン引数の項目名 */
    private static final String TARGET_NAME_COMMAND_LINE_ARGUMENTS = "commandLineArguments";
    /** 終了コードの項目名 */
    private static final String TARGET_NAME_EXIT_CODE = "exitCode";
    /** 処理時間の項目名 */
    private static final String TARGET_NAME_EXECUTE_TIME = "executeTime";

    /** 開始ログの出力項目を取得する際に使用するプロパティ名 */
    private static final String PROPS_START_LOG_TARGETS = PROPS_PREFIX + "startTargets";
    /** 終了ログの出力項目を取得する際に使用するプロパティ名 */
    private static final String PROPS_END_LOG_TARGETS = PROPS_PREFIX + "endTargets";

    /** 開始ログのラベルのプロパティ名 */
    private static final String PROPS_START_LOG_MSG_LABEL = PROPS_PREFIX + "startLogMsgLabel";
    /** 終了ログのラベルのプロパティ名 */
    private static final String PROPS_END_LOG_MSG_LABEL = PROPS_PREFIX + "endLogMsgLabel";

    /** 開始ログ出力項目のデフォルト値 */
    private static final String DEFAULT_START_LOG_TARGETS = "label,commandLineOptions,commandLineArguments";
    /** 終了ログ出力項目のデフォルト値 */
    private static final String DEFAULT_END_LOG_TARGETS = "label,exitCode,executeTime";

    /** デフォルトの開始ログメッセージのラベル */
    private static final String DEFAULT_START_LOG_MSG_LABEL = "BEGIN";
    /** デフォルトの州力ログメッセージのラベル */
    private static final String DEFAULT_END_LOG_MSG_LABEL = "END";

    /** 開始ログの出力項目 */
    private List<JsonLogObjectBuilder<LauncherLogContext>> startLogMessageTargets;
    /** 終了ログの出力項目 */
    private List<JsonLogObjectBuilder<LauncherLogContext>> endLogMessageTargets;


    /** 各種ログのJSONフォーマット支援オブジェクト */
    private JsonLogFormatterSupport support;

    /**
     * コンストラクタ。
     */
    public LauncherJsonLogFormatter() {
        initialize(AppLogUtil.getProps());
    }

    /**
     *初期化処理。
     * @param props 各種ログ出力の設定情報
     */
    protected void initialize(Map<String, String> props) {
        JsonSerializationSettings settings = new JsonSerializationSettings(props, PROPS_PREFIX, AppLogUtil.getFilePath());
        JsonSerializationManager serializationManager = createSerializationManager(settings);
        support = new JsonLogFormatterSupport(serializationManager, settings);

        Map<String, JsonLogObjectBuilder<LauncherLogContext>> objectBuilders = getObjectBuilders(props);

        String startMessageLogLabel = getProp(props, PROPS_START_LOG_MSG_LABEL, DEFAULT_START_LOG_MSG_LABEL);
        objectBuilders.put(TARGET_NAME_LABEL, new LabelBuilder(startMessageLogLabel));
        startLogMessageTargets = getStructuredTargets(objectBuilders, props, PROPS_START_LOG_TARGETS, DEFAULT_START_LOG_TARGETS);

        String endMessageLogLabel = getProp(props, PROPS_END_LOG_MSG_LABEL, DEFAULT_END_LOG_MSG_LABEL);
        objectBuilders.put(TARGET_NAME_LABEL, new LabelBuilder(endMessageLogLabel));
        endLogMessageTargets = getStructuredTargets(objectBuilders, props, PROPS_END_LOG_TARGETS, DEFAULT_END_LOG_TARGETS);
    }

    /**
     * 変換処理に使用する{@link JsonSerializationManager}を生成する。
     * @param settings 各種ログ出力の設定情報
     * @return {@link JsonSerializationManager}
     */
    protected JsonSerializationManager createSerializationManager(JsonSerializationSettings settings) {
        return new BasicJsonSerializationManager();
    }

    /**
     * プロパティを取得する。<br>
     * プロパティの指定がない場合はデフォルト値を返す。
     * @param props 各種ログの設定情報
     * @param propName プロパティ名
     * @param defaultValue プロパティのデフォルト値
     * @return プロパティ
     */
    protected String getProp(Map<String, String> props, String propName, String defaultValue) {
        String value = props.get(propName);
        return value != null ? value : defaultValue;
    }

    /**
     * フォーマット対象のログ出力項目を取得する。
     * @param props 各種ログ出力の設定情報
     * @return フォーマット対象のログ出力項目
     */
    protected Map<String, JsonLogObjectBuilder<LauncherLogContext>>getObjectBuilders(Map<String, String> props) {
        Map<String, JsonLogObjectBuilder<LauncherLogContext>> objectBuilders
                = new HashMap<String, JsonLogObjectBuilder<LauncherLogContext>>();

        objectBuilders.put(TARGET_NAME_COMMAND_LINE_OPTIONS, new CommandLineOptionsBuilder());
        objectBuilders.put(TARGET_NAME_COMMAND_LINE_ARGUMENTS, new CommandLineArgumentsBuilder());
        objectBuilders.put(TARGET_NAME_EXIT_CODE, new ExitCodeBuilder());
        objectBuilders.put(TARGET_NAME_EXECUTE_TIME, new ExecuteTimeBuilder());

        return objectBuilders;
    }

    /**
     * ログ出力項目を取得する。
     * @param objectBuilders オブジェクトビルダー
     * @param props 各種ログ出力の設定情報
     * @param targetsPropName 出力項目のプロパティ名
     * @param defaultTargets デフォルトの出力項目
     * @return ログ出力項目
     */
    private List<JsonLogObjectBuilder<LauncherLogContext>> getStructuredTargets(
            Map<String, JsonLogObjectBuilder<LauncherLogContext>> objectBuilders,
            Map<String, String> props,
            String targetsPropName, String defaultTargets) {

        String targetsStr = props.get(targetsPropName);
        if (StringUtil.isNullOrEmpty(targetsStr)) {
            targetsStr = defaultTargets;
        }

        List<JsonLogObjectBuilder<LauncherLogContext>> structuredTargets
                = new ArrayList<JsonLogObjectBuilder<LauncherLogContext>>();

        String[] targets = targetsStr.split(",");
        Set<String> keys = new HashSet<String>(targets.length);
        for (String target: targets) {
            String key = target.trim();
            if (!StringUtil.isNullOrEmpty(key) && !keys.contains(key)) {
                keys.add(key);
                if (objectBuilders.containsKey(key)) {
                    structuredTargets.add(objectBuilders.get(key));
                } else {
                    throw new IllegalArgumentException(
                            String.format("[%s] is unknown target. property name = [%s]", key, targetsPropName));
                }
            }
        }

        return structuredTargets;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStartLogMsg(CommandLine commandLine) {
        LauncherLogContext context = new LauncherLogContext();
        context.setCommandLine(commandLine);
        return support.getStructuredMessage(startLogMessageTargets, context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getEndLogMsg(int exitCode, long executeTime) {
        LauncherLogContext context = new LauncherLogContext();
        context.setExitCode(exitCode);
        context.setExecuteTime(executeTime);
        return support.getStructuredMessage(endLogMessageTargets, context);
    }

    /**
     * 出力項目(ラベル)を処理するクラス。
     */
    public static class LabelBuilder implements JsonLogObjectBuilder<LauncherLogContext> {

        private final String label;

        /**
         * コンストラクタ。
         * @param label ラベル
         */
        public LabelBuilder(String label) {
            this.label = label;
        }

        @Override
        public void build(Map<String, Object> structuredObject, LauncherLogContext context) {
            structuredObject.put(TARGET_NAME_LABEL, label);
        }
    }

    /**
     * 出力項目(コマンドラインオプション)を処理するクラス。
     */
    public static class CommandLineOptionsBuilder implements JsonLogObjectBuilder<LauncherLogContext> {

        @Override
        public void build(Map<String, Object> structuredObject, LauncherLogContext context) {
            structuredObject.put(TARGET_NAME_COMMAND_LINE_OPTIONS, context.getCommandLine().getParamMap());
        }
    }

    /**
     * 出力項目(コマンドライン引数)を処理するクラス。
     */
    public static class CommandLineArgumentsBuilder implements JsonLogObjectBuilder<LauncherLogContext> {

        @Override
        public void build(Map<String, Object> structuredObject, LauncherLogContext context) {
            structuredObject.put(TARGET_NAME_COMMAND_LINE_ARGUMENTS, context.getCommandLine().getArgs());
        }
    }

    /**
     * 出力項目(終了コード)を処理するクラス。
     */
    public static class ExitCodeBuilder implements JsonLogObjectBuilder<LauncherLogContext> {

        @Override
        public void build(Map<String, Object> structuredObject, LauncherLogContext context) {
            structuredObject.put(TARGET_NAME_EXIT_CODE, context.getExitCode());
        }
    }

    /**
     * 出力項目(処理時間)を処理するクラス。
     */
    public static class ExecuteTimeBuilder implements JsonLogObjectBuilder<LauncherLogContext> {

        @Override
        public void build(Map<String, Object> structuredObject, LauncherLogContext context) {
            structuredObject.put(TARGET_NAME_EXECUTE_TIME, context.getExecuteTime());
        }
    }
}
