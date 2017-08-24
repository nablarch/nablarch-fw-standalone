package nablarch.fw.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nablarch.core.util.Builder;
import nablarch.core.util.annotation.Published;
import nablarch.fw.Request;
import nablarch.fw.launcher.CommandLineParser.Result;
import nablarch.fw.results.BadRequest;

/**
 * コマンドラインオプション、コマンドライン引数をパースして格納するクラス。
 * <p/>
 *
 * @author Iwauo Tajima
 * @see Main
 * @see CommandLineParser
 */
@Published(tag = "architect")
public class CommandLine implements Request<String> {

    /**
     * デフォルトコンストラクタ
     *
     * 与えられたコマンドライン文字列を{@link CommandLineParser}で解析し保持する。
     *
     * @param commandline コマンドライン文字列
     */
    public CommandLine(String... commandline) {

        final CommandLineParser parser = new CommandLineParser();
        final Result result = parser.parse(commandline);
        validateOptions(result.getOpts());
        opts = result.getOpts();
        args = result.getArgs();
    }

    /**
     * テスト用に使用するコンストラクタ。
     *
     * @param opts コマンドラインオプションのMap
     * @param args コマンドラインオプションのList
     */
    public CommandLine(Map<String, String> opts, List<String> args) {
        validateOptions(opts);
        this.opts = opts;
        this.args = args;
    }
    
    /**
     * コマンドラインパラメータの内容をバリデーションする。
     * 
     * @param options コマンドラインパラメータ
     * @throws IllegalArgumentException
     *     コマンドラインパラメータの内容が不正だった場合。
     */
    private void validateOptions(Map<String, String> options)
    throws IllegalArgumentException {
        
         List<String> errorMessages = new ArrayList<String>();

        if (!options.containsKey("diConfig")) {
            errorMessages.add("parameter [-diConfig] must be specified.");
        }

        if (!options.containsKey("requestPath")) {
            errorMessages.add("parameter [-requestPath] must be specified.");
        }

        if (!options.containsKey("userId")) {
            errorMessages.add("parameter [-userId] must be specified.");
        }

        if (!errorMessages.isEmpty()) {
            throw new BadRequest(Builder.join(errorMessages, " / "));
        }
    }

    /** コマンドラインオプションのMap */
    private Map<String, String> opts;

    /** コマンドライン引数のList */
    private List<String> args;

    /**
     * {@inheritDoc}
     * <p/>
     * リクエストパスを返す。
     * デフォルトでは実行されたコマンドのフルパス文字列を返す。
     */
    @Override
    public String getRequestPath() {
        return opts.get("requestPath");
    }

    @Override
    public CommandLine setRequestPath(String requestPath) {
        opts.put("requestPath", requestPath.trim());
        return this;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * コマンドラインオプションの値を返す。
     * 値が指定されていない場合は空文字を返す。
     * <pre>
     * 例
     * -requestPath test.SampleAction/BC001 --> "test.SampleAction/BC001"を返す。
     * -server --> ""を返す。
     * </pre>
     *
     */
    @Override
    @Published
    public String getParam(String name) {
        return opts.get(name);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * コマンドラインオプションのMapを取得する。
     */
    @Override
    @Published
    public Map<String, String> getParamMap() {
        return opts;
    }
    
    /**
     * コマンドラインオプションのMapを設定する。
     *
     * @param opts 名前付きコマンドライン引数のMap
     * @return このオブジェクト自体
     */
    public CommandLine setParamMap(Map<String, String> opts) {
        validateOptions(opts);
        this.opts = opts;
        return this;
    }
    
    /**
     * コマンドライン引数のリストを返す。
     * @return 引数リスト
     */
    @Published
    public List<String> getArgs() {
        return args;
    }
}
