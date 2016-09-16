package nablarch.fw.handler;

import java.lang.reflect.Method;

import nablarch.core.dataformat.DataRecord;
import nablarch.core.util.annotation.Published;
import nablarch.fw.ExecutionContext;
import nablarch.fw.HandlerWrapper;
import nablarch.fw.MethodBinder;
import nablarch.fw.Result;

/**
 * データレコードのレコードタイプ名に応じて委譲先のメソッドを決定するディスパッチハンドラ。
 * 
 * このディスパッチャでは、次のシグニチャに一致するメソッドに対して後続処理を委譲する。
 * <pre>
 *   public Result "do" + [レコードタイプ名](DataRecord record, ExecutionContext ctx);
 * </pre>
 * 
 * なお、メソッド名の一致判定において大文字小文字は同一視される。
 * 
 * @author Iwauo Tajima
 */
@Published(tag = "architect")
public class RecordTypeBinding extends MethodBinding<DataRecord, Result> {
    /**
     * HttpMethodBindingのファクトリクラス
     */
    public static class Binder implements MethodBinder<DataRecord, Result> {
        /** {@inheritDoc} */
        public HandlerWrapper<DataRecord, Result> bind(Object delegate) {
            return new RecordTypeBinding(delegate);
        }
    }
    
    /**
     * コンストラクタ
     * @param delegate 処理委譲対象のオブジェクト
     */
    public RecordTypeBinding(Object delegate) {
        super(delegate);
    }

    /** {@inheritDoc}
     * この実装では、引数のデータレコードに格納されたデータレイアウト（レコードタイプ）に従って、メソッドバインディングを行う。
     * @param record データレコード
     */
    @Override
    protected Method getMethodBoundTo(DataRecord record, ExecutionContext ctx) {
        String methodName = "do" + record.getRecordType();
        return getHandleMethod(methodName);
    }
}
