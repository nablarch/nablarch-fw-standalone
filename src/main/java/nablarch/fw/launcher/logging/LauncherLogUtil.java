package nablarch.fw.launcher.logging;

import java.util.Map;

import nablarch.core.log.LogUtil;
import nablarch.core.log.app.AppLogUtil;
import nablarch.core.util.ObjectUtil;
import nablarch.fw.launcher.CommandLine;

/**
 * {@link nablarch.fw.launcher.Main}でのログ出力をサポートするユーティリティクラス。
 *
 * @author hisaaki sioiri
 */
public final class LauncherLogUtil {

    /** 隠蔽コンストラクタ */
    private LauncherLogUtil() {
    }

    /** {@link LauncherLogFormatter}のクラス名 */
    private static final String PROPS_CLASS_NAME =
            LauncherLogFormatter.PROPS_PREFIX + "className";

    /** {@link LauncherLogFormatter}を生成する{@link nablarch.core.log.LogUtil.ObjectCreator} */
    private static final LogUtil.ObjectCreator<LauncherLogFormatter> LAUNCHER_LOG_FORMATTER_CREATOR
            = new LogUtil.ObjectCreator<LauncherLogFormatter>() {

        public LauncherLogFormatter create() {
            LauncherLogFormatter formatter;
            Map<String, String> props = AppLogUtil.getProps();
            if (props.containsKey(PROPS_CLASS_NAME)) {
                String className = props.get(PROPS_CLASS_NAME);
                formatter = ObjectUtil.createInstance(className);
            } else {
                formatter = new LauncherLogFormatter();
            }
            return formatter;
        }
    };

    /** クラスローダに紐付く{@link LauncherLogFormatter}を生成する。 */
    public static void initialize() {
        getLogFormatter();
    }

    /**
     * クラスローダに紐付く{@link LauncherLogFormatter}を取得する。
     *
     * @return {@link LauncherLogFormatter}
     */
    private static LauncherLogFormatter getLogFormatter() {
        return LogUtil.getObjectBoundToClassLoader(
                LAUNCHER_LOG_FORMATTER_CREATOR);
    }

    /**
     * 起動ログメッセージを生成し、返却する。
     *
     * @param commandLine コマンドライン引数を持つオブジェクト
     * @return 生成したログメッセージ
     */
    public static String getStartLogMsg(CommandLine commandLine) {
        return getLogFormatter().getStartLogMsg(commandLine);
    }

    /**
     * 終了ログのメッセージを生成し返却する。
     *
     * @param exitCode 終了コード
     * @param executeTime 実行時間
     * @return 生成したログメッセージ
     */
    public static String getEndLogMsg(int exitCode, long executeTime) {
        return getLogFormatter().getEndLogMsg(exitCode, executeTime);
    }
}
