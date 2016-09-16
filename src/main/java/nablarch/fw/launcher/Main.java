package nablarch.fw.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.core.log.app.ApplicationSettingLogUtil;
import nablarch.core.log.app.FailureLogUtil;
import nablarch.core.log.app.LogInitializationHelper;
import nablarch.core.log.app.PerformanceLogUtil;
import nablarch.core.repository.SystemRepository;
import nablarch.core.repository.di.DiContainer;
import nablarch.core.repository.di.config.DuplicateDefinitionPolicy;
import nablarch.core.repository.di.config.xml.XmlComponentDefinitionLoader;
import nablarch.core.util.StringUtil;
import nablarch.core.util.annotation.Published;
import nablarch.fw.DataReader;
import nablarch.fw.DataReaderFactory;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.HandlerQueueManager;
import nablarch.fw.Result;
import nablarch.fw.StandaloneExecutionContext;
import nablarch.fw.handler.RecordTypeBinding;
import nablarch.fw.launcher.logging.LauncherLogUtil;

/**
 * 本フレームワークの起動シーケンスの起点となるクラス。
 * <p/>
 * 本クラスをjavaコマンドから直接起動することで、以下の処理を行う。
 * <pre>
 * 1. コマンドライン引数のパース。
 * 2. コンポーネント設定ファイル（xml)の読み込みとハンドラー構成の初期化
 * 3. ハンドラーキューに対する後続処理の委譲。
 * 4. 終了/エラー処理。
 *    (結果コードはハンドラキューの戻り値、または送出された例外をもとに設定する。)
 * </pre>
 * <p/>
 * 以下は、本クラスの使用例である。
 * (起動オプションに設定した値は、{@link CommandLine}オブジェクトから取得できる。)
 * <p/>
 * <b>起動コマンドの例</b>
 * <pre/>
 * java                                                 \
 *     -server                                          \
 *     -Xmx128m                                         \
 *     -Dsample=100                                     \
 * nablarch.fw.launcher.Main                            \
 *     -diConfig    file:./batch-config.xml             \
 *     -requestPath admin.DataUnloadBatchAction/BC0012  \
 *     -userId      testUser                            \
 *     -namedParam  value                               \
 *     value1
 * </pre>
 * <b>起動コマンドの説明</b><br/>
 * <pre>
 *
 * <b>アプリケーション引数（JVMへの設定である起動コマンドの説明は行わない）</b>
 *
 *           -Dsample      コンポーネント設定ファイル中の埋め込みパラメータの値。例では、${sample}に100が設定される。
 *
 * <b>Mainクラスへの引数</b>
 *
 *   (必須)  -diConfig     コンポーネント設定ファイルのファイルパス。クラスパス配下のxmlファイルを指定。
 *   (必須)  -requestPath  実行対象のアクションハンドラクラス名/リクエストIDを指定。
 *   (必須)  -userId       プロセスの実行権限ユーザID。セッション変数とスレッドコンテキストに保持される。
 *           -namedParam   名前付きパラメータ。{@link CommandLine}に使用される属性値を指定。
 *           value1        無名パラメータ。{@link CommandLine}に使用される属性値を指定。
 *</pre>
 *
 * @author Iwauo Tajima
 */
public class Main extends HandlerQueueManager<Main>
        implements Handler<CommandLine, Integer> {

    /** ロガー。 */
    private static final Logger LOGGER = LoggerManager.get(Main.class);

    /** ハンドラ({@link Handler})キュー */
    @SuppressWarnings("rawtypes")
    private List<Handler> handlerQueue = new ArrayList<Handler>();

    @SuppressWarnings("rawtypes")
    @Override
    public List<Handler> getHandlerQueue() {
        return handlerQueue;
    }

    /**
     * メインメソッド。
     * <p/>
     * 本メソッドでは、以下の内容をログ出力する。
     * <ul>
     * <li>起動時</li>
     * 起動オプションや、起動引数(詳細は、{@link LauncherLogUtil#getStartLogMsg(CommandLine)}を参照)
     * <li>終了時</li>
     * 終了コードや処理時間(詳細は、{@link LauncherLogUtil#getEndLogMsg(int, long)}を参照})
     * </ul>
     * 及び処理終了時
     *
     * @param args コマンドライン引数
     */
    public static void main(String... args) {
        CommandLine commandLine = new CommandLine(args);
        LauncherLogUtil.initialize();
        LOGGER.logInfo(LauncherLogUtil.getStartLogMsg(commandLine));

        long executeStartTime = System.currentTimeMillis();
        int exitCode = execute(commandLine);
        long executeEndTime = System.currentTimeMillis();

        LOGGER.logInfo(LauncherLogUtil.getEndLogMsg(exitCode,
                executeEndTime - executeStartTime));
        System.exit(exitCode);
    }

    /**
     * {@inheritDoc}
     * この実装では、ハンドラキューに後続処理を委譲し、その処理結果から
     * このプロセスの終了コードを算出して返す。
     */
    public Integer handle(CommandLine commandLine, ExecutionContext context) {

        Object result;
        try {
            initializeLog();
            setupExecutionContext(commandLine, context);

            // アプリケーション設定ログを出力
            LOGGER.logInfo(
                    ApplicationSettingLogUtil.getAppSettingsWithDateLogMsg());

            result = context.handleNext(commandLine);

        } catch (Result.Error e) {
            LOGGER.logFatal("An unexpected exception occurred.", e);
            result = e;

        } catch (RuntimeException e) {
            LOGGER.logFatal("An unexpected exception occurred.", e);
            return UNKNOWN_ERROR;

        } catch (Error e) {
            LOGGER.logFatal("An unexpected exception occurred.", e);
            return UNKNOWN_ERROR;
        }

        if (result instanceof Integer) {
            return (Integer) result;
        }

        if (result instanceof Result.Success) {
            return 0;
        }

        if (result instanceof Result) {
            int statusCode = ((Result) result).getStatusCode();
            return (statusCode >= 0 && statusCode <= UNKNOWN_ERROR)
                    ? statusCode
                    : UNKNOWN_ERROR;
        }
        return 0;
    }

    /** 既定のエラーコード */
    private static final int UNKNOWN_ERROR = 127;

    /**
     * バッチを実行する。
     * <p/>
     * {@link #handle(CommandLine, ExecutionContext)}に処理を委譲して結果を返す。
     *
     * @param commandLine 起動オプション
     * @return 終了コード
     */
    @Published(tag = "architect")
    public static int execute(CommandLine commandLine) {
        Main launcher = new Main();
        ExecutionContext ctx = new StandaloneExecutionContext();
        return launcher.handle(commandLine, ctx);
    }

    /**
     * バッチコントローラ起動前準備を行う。<br/>
     * 実行コンテキストを生成し、以下の処理を行う。
     * <ul>
     *     <li>コンポーネント設定ファイルに定義したハンドラキューの設定</li>
     *     <li>{@link DataReader}の設定</li>
     *     <li>{@link DataReaderFactory}の設定</li>
     *     <li>ディスパッチハンドラの設定</li>
     *     <li>セッションスコープにプロセスの実行権限ユーザIDと、起動オプションのマップを設定</li>
     * </ul>
     *
     * @param commandLine 起動オプション
     * @param context 実行コンテキスト
     * @return 初期化されたコントローラ
     */
    protected Main setupExecutionContext(CommandLine commandLine,
            ExecutionContext context) {
        Map<String, String> options = commandLine.getParamMap();
        String configFilePath = options.remove("diConfig");

        if (StringUtil.isNullOrEmpty(configFilePath)) {
            throw new IllegalArgumentException(
                    "diConfig option must be specified."
            );
        }
        setUpSystemRepository(configFilePath);
        handlerQueue = SystemRepository.get("handlerQueue");
        DataReader<?> reader = SystemRepository.get("dataReader");
        DataReaderFactory<?> readerFactory = SystemRepository.get(
                "dataReaderFactory");

        context.setHandlerQueue(handlerQueue)
                .setDataReader(reader)
                .setDataReaderFactory(readerFactory)
                .setSessionScopedVar("user.id", commandLine.getParam("userId"))
                .setMethodBinder(new RecordTypeBinding.Binder())
                .getSessionScopeMap().putAll(commandLine.getParamMap());
        return this;
    }

    /**
     * コンポーネント設定ファイルの設定にしたがって、システムリポジトリの初期化を行う。
     *
     * @param configFilePath コンポーネント設定ファイルのパス
     */
    protected void setUpSystemRepository(String configFilePath) {
        DiContainer container = new DiContainer(
                new XmlComponentDefinitionLoader(
                        configFilePath
                        , DuplicateDefinitionPolicy.OVERRIDE
                )
        );
        SystemRepository.load(container);
    }

    /** 各種ログの初期化を行う。 */
    protected void initializeLog() {
        FailureLogUtil.initialize();
        PerformanceLogUtil.initialize();
        ApplicationSettingLogUtil.initialize();
        LogInitializationHelper.initialize();
    }

}

