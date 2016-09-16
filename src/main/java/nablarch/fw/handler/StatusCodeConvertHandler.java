package nablarch.fw.handler;

import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;

/**
 * ステータスコードをプロセスの終了コードに変換するハンドラ。
 * <p/>
 * {@link nablarch.fw.launcher.Main}の直後のハンドラとして設定することにより、
 * 本ハンドラ以降のハンドラから戻された処理結果({@link Result})のステータスコードを
 * プロセスの終了コードに変換するハンドラである。
 * <p/>
 * 以下に変換ルールを示す。
 * <table border="1">
 * <tr bgcolor="#CCCCFF">
 * <th>変換前</th>
 * <th>変換後</th>
 * </tr>
 * <tr>
 * <td>マイナス値の場合</td>
 * <td>1</td>
 * </tr>
 * <tr>
 * <td>0</td>
 * <td>0</td>
 * </tr>
 * <tr>
 * <td>1～199</td>
 * <td>変換は行わない</td>
 * </tr>
 * <tr>
 * <td>200～399</td>
 * <td>0</td>
 * </tr>
 * <tr>
 * <td>400</td>
 * <td>10</td>
 * </tr>
 * <tr>
 * <td>401</td>
 * <td>11</td>
 * </tr>
 * <tr>
 * <td>403</td>
 * <td>12</td>
 * </tr>
 * <tr>
 * <td>404</td>
 * <td>13</td>
 * </tr>
 * <tr>
 * <td>409</td>
 * <td>14</td>
 * </tr>
 * <tr>
 * <td>上記以外の400～499</td>
 * <td>15</td>
 * </tr>
 * <tr>
 * <td>500以上</td>
 * <td>20</td>
 * </tr>
 * </table>
 * 注意点:<br/>
 * <b>
 * 本ハンドラは結果オブジェクトを{@link Result}から{@link Integer}に変換して返却するため、
 * 本ハンドラより前に設定されたハンドラオブジェクトは、結果オブジェクト扱う場合はIntegerとして扱う必要がある。
 * </b>
 *
 * @author hisaaki sioiri
 */
public class StatusCodeConvertHandler implements Handler<Object, Integer> {

    /**
     * {@inheritDoc}
     * <p/>
     * 後続のハンドラに処理を委譲し、({@link ExecutionContext#handleNext(Object)})
     * 結果として返却されたステータスコード({@link nablarch.fw.Result#getStatusCode()})を変換し返却する。
     * <p/>
     */
    public Integer handle(Object o, ExecutionContext context) {
        Result result = context.handleNext(o);
        return convert(result.getStatusCode());
    }

    /**
     * ステータスコードを変換する。
     *
     * @param statusCode 変換前ステータスコード
     * @return 変換後ステータスコード
     */
    private static int convert(int statusCode) {
        if (statusCode < 0) {
            return 1;
        } else if (statusCode >= 200 && statusCode <= 399) {
            return 0;
        } else if (statusCode == 400) {
            return 10;
        } else if (statusCode == 401) {
            return 11;
        } else if (statusCode == 403) {
            return 12;
        } else if (statusCode == 404) {
            return 13;
        } else if (statusCode == 409) {
            return 14;
        } else if (400 <= statusCode && statusCode <= 499) {
            return 15;
        } else if (statusCode >= 500) {
            return 20;
        }
        return statusCode;
    }
}

