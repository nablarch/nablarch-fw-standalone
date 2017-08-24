package nablarch.fw.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * コマンドライン引数のパーサクラス。
 * コマンドラインオプション<br/>
 * <p>
 * 許容するコマンドライン引数の形式は以下の通り。
 * <p>
 * オプションの書式は以下となる。
 * ※「-(ハイフン)」は一つまたは二つ付与する。どちらでも処理に影響しない。
 * <ul>
 * <li>-オプション名 値 (例：java -requestPath test.SampleAction/BC001)</li>
 * <li>--オプション名 値 (例：java --requestPath test.SampleAction/BC001)</li>
 * <li>-オプション名 (例：java -server)</li>
 * <li>--オプション名 (例：java --server)</li>
 * </ul>
 * <p>
 * コマンドライン引数<br/>
 * 引数の書式は以下となる。
 * <ul>
 * <li>値 (例：java someValue)</li>
 * </ul>
 * <p/>
 *
 * @author siosio
 */
public class CommandLineParser {

    /**
     * パースする。
     *
     * @param commandLine コマンドライン引数
     * @return パース結果
     */
    public Result parse(final String... commandLine) {
        final Map<String, String> opts = new HashMap<String, String>();
        final List<String> args = new ArrayList<String>();

        final Iterator<String> itr = Arrays.asList(commandLine)
                                           .iterator();
        while (itr.hasNext()) {
            final String arg = itr.next();
            final String optName = arg.replaceAll("^--?", "");
            if (optName.length() != arg.length()) {
                final String optValue = itr.hasNext() ? itr.next() : "";
                opts.put(optName, optValue);
            } else {
                args.add(arg);
            }
        }

        return new Result(opts, args);
    }

    /**
     * パース結果
     */
    public static class Result {

        /** オプション引数 */
        private final Map<String, String> opts;

        /** コマンドライン引数 */
        private final List<String> args;

        /**
         * オプションと引数からパース結果を構築する。
         *
         * @param opts オプション引数
         * @param args コマンドライン引数
         */
        private Result(final Map<String, String> opts, final List<String> args) {
            this.opts = opts;
            this.args = args;
        }

        /**
         * オプション引数を返す。
         *
         * @return オプション引数
         */
        public Map<String, String> getOpts() {
            return opts;
        }

        /**
         * コマンドライン引数を返す。
         *
         * @return コマンドライン引数
         */
        public List<String> getArgs() {
            return args;
        }
    }
}
