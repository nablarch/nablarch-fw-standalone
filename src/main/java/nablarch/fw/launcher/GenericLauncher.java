package nablarch.fw.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import nablarch.core.log.app.ApplicationSettingLogUtil;
import nablarch.core.log.app.FailureLogUtil;
import nablarch.core.log.app.LogInitializationHelper;
import nablarch.core.log.app.PerformanceLogUtil;
import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.di.DiContainer;
import nablarch.core.repository.di.config.DuplicateDefinitionPolicy;
import nablarch.core.repository.di.config.xml.XmlComponentDefinitionLoader;
import nablarch.core.util.annotation.Published;

/**
 * 汎用のプログラム起動クラス。
 * 本クラスはプログラム起動の共通的な枠組みのみを提供し、
 * 個別の処理については{@link ProcessLifecycle}実装クラスに委譲される。
 *
 * @author T.Kawasaki
 */
@Published(tag = "architect")
public class GenericLauncher {

    /** {@link ProcessLifecycle}実装クラスを取得するためのキー */
    public static final String PROCESS_LIFECYCLE_KEY = "processLifecycle";

    /** コマンドライン引数 */
    protected final CommandLine commandLine;

     /**
     * mainメソッド。
     *
     * @param args プログラム引数
     */
    public static void main(String[] args) {
        new GenericLauncher(args).launch();
    }


    /**
     * コンストラクタ。
     *
     * @param programArguments プログラム引数
     */
    public GenericLauncher(String[] programArguments) {
        commandLine = new CommandLine(fillDefault(programArguments));
    }

    /**
     * アプリケーションを起動する。
     */
    public void launch() {
        initializeLog();
        initializeRepository();
        final ProcessLifecycle lifecycle = getProcessLifecycle();
        lifecycle.setCommandLine(commandLine);
        try {
            lifecycle.initialize();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    lifecycle.onVirtualMachineShutdown();
                }
            });
            lifecycle.execute();
        } finally {
            lifecycle.terminate();
        }
    }

    /** 各種ログの初期化を行う。 */
    protected void initializeLog() {
        FailureLogUtil.initialize();
        PerformanceLogUtil.initialize();
        ApplicationSettingLogUtil.initialize();
        LogInitializationHelper.initialize();
    }

    /** リポジトリの初期化を行う。 */
    protected void initializeRepository() {
        Map<String, String> options = commandLine.getParamMap();
        String configFilePath = options.get("diConfig");
        setUpSystemRepository(configFilePath);
    }

    /**
     * {@link ProcessLifecycle}を取得する。
     * この実装では、{@link SystemRepository}から取得する。
     *
     * @return {@link ProcessLifecycle}
     */
    protected ProcessLifecycle getProcessLifecycle() {
        ProcessLifecycle lifecycle = SystemRepository.get(PROCESS_LIFECYCLE_KEY);
        if (lifecycle == null) {
            throw new IllegalStateException(
                    "could not find required component. key=[" + PROCESS_LIFECYCLE_KEY + ']');
        }
        return lifecycle;
    }

    /**
     * システムリポジトリの初期化を行う。
     *
     * @param configFilePath コンポーネント設定ファイルのパス
     */
    protected void setUpSystemRepository(String configFilePath) {
        DiContainer container = new DiContainer(
                new XmlComponentDefinitionLoader(configFilePath,
                                                 DuplicateDefinitionPolicy.OVERRIDE));
        SystemRepository.load(container);
    }

    /**
     * プログラム引数にデフォルト値を設定する。
     * 以下の項目について、{@link CommandLine#validateOptions(Map)}にて必須チェックが行われるので
     * 設定されていない場合はデフォルト値を設定する。
     * <ul>
     * <li>-requestPath</li>
     * <li>-userId</li>
     * </ul>
     *
     * @param orig プログラム引数
     * @return デフォルト値設定済みのプログラム引数
     * @see CommandLine#validateOptions(Map)
     */
    protected String[] fillDefault(String[] orig) {
        final String[] params = nullToEmpty(orig);
        final List<String> args = new ArrayList<String>(Arrays.asList(params));
        fillDefault(args, "-requestPath");
        fillDefault(args, "-userId");
        return (params.length == args.size()) ?
                params :
                args.toArray(new String[args.size()]);
    }

    protected String[] nullToEmpty(String[] strings) {
        return strings == null ? new String[0] : strings;
    }

    /**
     * デフォルト値を設定する。
     *
     * @param args プログラム引数
     * @param key キー
     */
    protected void fillDefault(List<String> args, String key) {
        if (args.contains(key)) {
            return;
        }
        args.add(key);
        args.add(""); // empty value (not null).
    }
}
