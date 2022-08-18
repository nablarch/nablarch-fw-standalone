package nablarch.fw.handler;

import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;

import java.util.ArrayList;
import java.util.List;

/**
 * トランザクション制御をせず処理するループ制御ハンドラークラス。
 * <p/>
 * 本ハンドラは、アプリケーションが処理すべきデータが存在する間、後続のハンドラに対して繰り返し処理を委譲する。
 * 処理すべきデータが存在するかは、{@link nablarch.fw.ExecutionContext#hasNextData()}により判断する。
 *
 * @author Shinya Hijiri
 */
public class DbLessLoopHandler implements Handler<Object, Result> {

    @SuppressWarnings("rawtypes")
    @Override
    public Result handle(Object data, ExecutionContext context) {
        List<Handler> snapshot = new ArrayList<Handler>();
        snapshot.addAll(context.getHandlerQueue());
        do {
            restoreHandlerQueue(context, snapshot)
                    .handleNext(data);

            // 正常終了時は最後に読み込んだデータオブジェクトをクリアする
            context.clearLastReadData();
        } while (!shouldStop(context));

        return new Result.Success();
    }

    /**
     * 現在の処理終了後にループを止める場合にtrueを返す。
     * <p/>
     * デフォルトの実装では、実行コンテキスト上のデータリーダのデータが
     * 空になるまで繰り返し処理を行う。
     * <p/>
     * これと異なる条件でループを停止させたい場合は、本メソッドをオーバライドすること。
     * 
     * @param context 実行コンテキスト
     * @return ループを止める場合はtrue
     */
    public boolean shouldStop(ExecutionContext context) {
        return !context.hasNextData();
    }


    /**
     * ハンドラキューの内容を、ループ開始前の状態に戻す。
     * 
     * @param context  実行コンテキスト
     * @param snapshot ハンドラキューのスナップショット
     * @return 実行コンテキスト(引数と同じインスタンス)
     */
    @SuppressWarnings("rawtypes")
    private ExecutionContext restoreHandlerQueue(ExecutionContext context,
                                                 List<Handler> snapshot) {
        List<Handler> queue = context.getHandlerQueue();
        queue.clear();
        queue.addAll(snapshot);
        return context;
    }
}
