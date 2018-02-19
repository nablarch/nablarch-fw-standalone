package nablarch.fw.handler;

import static nablarch.core.log.Logger.LS;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import nablarch.core.ThreadContext;
import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.core.log.app.CommitLogger;
import nablarch.core.log.app.FailureLogUtil;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Result;
import nablarch.fw.Result.MultiStatus;

/**
 * 後続ハンドラの処理を子スレッドを用いて実行するハンドラ。
 * <p/>
 * 本ハンドラ以降の処理は、新たに作成する子スレッド上で実行される。
 * これにより、後続スレッドの処理に対するタイムアウトの設定や、
 * 停止要求(graceful-termination)を行うことが可能となる。
 * <p/>
 * また、並行実行数を設定することにより、後続処理を複数のスレッド上で並行実行する
 * ことができる。(デフォルトの並行実行数は1)
 * <p/>
 * このハンドラでは、全てのスレッドで単一のデータリーダインスタンスを共有する。
 * 従って、データリーダがアクセスするリソースに対する同期制御は各データリーダ側
 * で担保されている必要がある。
 *
 * @author Iwauo Tajima
 */
public class MultiThreadExecutionHandler
implements ExecutionHandler<Object, MultiStatus, MultiThreadExecutionHandler> {

    //------------------------------------------------------------- Settings
    /** 並行実行スレッド数 (デフォルト: 1スレッド) */
    private int concurrentNumber = 1;

    /** 処理停止要求のタイムアウト秒数 (デフォルト: 600秒) */
    private long terminationTimeout = 600;

    /** コミットログ */
    private CommitLogger commitLogger;

    //-------------------------------------------------- Internal structure
    /** スレッド実行管理モジュール */
    private ThreadPoolExecutor taskExecutor = null;

    /** スレッド実行状況監視モジュール */
    private CompletionService<Result> taskStatus = null;

    /** 各スレッドの処理実行状況を保持するリスト */
    private List<Future<Result>> taskTracker = null;

    /** ユーティリティ */
    private final ExecutionHandler.Support<Object, MultiStatus>
        support = new ExecutionHandler.Support<Object, MultiStatus>();

    //------------------------------------------- Main routine and its helpers
    /**
     * {@inheritDoc}
     * この実装では、実行コンテキストのクローンを作成し、後続のハンドラ処理を並列実行する。
     * <p/>
     * 後続処理のいずれかのスレッドにおいて例外が発生した場合は、
     * 処理中の全スレッドに対して中止要求(interruption)をかけ、その完了を待つ。
     * スレッド停止後、各スレッドでの処理状況の詳細をログに出力した後、
     * 元例外をラップした Result.InternalError を送出する。
     * <p/>
     * ただし、中止要求後、terminationTimeout値に指定された秒数を過ぎても完了しない
     * スレッドがあった場合は、当該スレッドの停止を断念しリターンする。
     */
    @Override
    @SuppressWarnings("rawtypes")
    public MultiStatus handle(Object data, ExecutionContext context) {

        // スレッドコンテキストに並行実行スレッド数を格納する
        ThreadContext.setConcurrentNumber(concurrentNumber);

        // 後続ハンドラのうち、コールバックメソッドを実装したもの。
        List<ExecutionHandlerCallback> listeners = support.prepareListeners(data, context);

        // 初期処理用コールバックを呼び出す。
        support.callPreExecution(listeners, data, context);

        // 後続処理で使用するデータリーダを準備する。
        support.prepareDataReader(data, context);

        if (commitLogger != null) {
            commitLogger.initialize();
            context.setSessionScopedVar(
                CommitLogger.SESSION_SCOPE_KEY, commitLogger
            );
        }

        // スレッドプールを初期化する。
        initializeThreadPool();

        for (int i = 0; i < concurrentNumber; i++) {
            taskTracker.add(
                taskStatus.submit(createTaskFor(data, context))
            );
        }

        MultiStatus results = new MultiStatus();
        Throwable error = null; // 処理実行中に送出された元例外
        try {
            for (int i = 0; i < concurrentNumber; i++) {
                results.addResults(taskStatus.take().get());
            }
            terminate(context); //正常終了

        } catch (ExecutionException e) {
            // 子スレッド内で未補足の例外が送出された場合は、
            // 実行中の子スレッドに割り込み要求をかけ停止させた後、
            // 実行時例外を送出する。
            error = e;

            try {
                terminate(context);
            } catch (Throwable t) {
                LOGGER.logWarn(
                    "an error occurred while terminating (or waiting to end) subthreads."
                  , t
                );
            }

            // エラー処理用コールバックを呼び出す。
            support.callErrorInExecution(listeners, e, context);

            Throwable cause = e.getCause();
            if (cause instanceof Result.Error) {
                Result.Error err = (Result.Error) cause;
                results.addResults(err);
                throw err;
            }
            if (cause instanceof RuntimeException) {
                results.addResults(new nablarch.fw.results.InternalError(cause));
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                results.addResults(new nablarch.fw.results.InternalError(cause));
                throw (Error) cause;
            }
            // ここにはこないはず。
            throw new RuntimeException(
                "application thread ended abnormally.", cause
            );

        } catch (InterruptedException e) {
            // カレントスレッドに対する割り込み要求が発生した場合は、
            // 実行中の子スレッドに割り込み要求をかけ停止させた後、
            // 実行時例外を送出する。
            // (このハンドラはメインスレッド上で使用する想定なので、
            //  ここにはこないはず。)
            error = e;

            try {
                terminate(context);
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                LOGGER.logWarn(
                    "an error occurred while terminating (or waiting to end) subthreads."
                  , t
                );
            }
           // エラー処理用コールバックを呼び出す。
            support.callErrorInExecution(listeners, e, context);
            throw new RuntimeException("execution was canceled.", e);

        } catch (RuntimeException e) {
            error = e;
            try {
                // 実行中のスレッドを停止してリスローする。
                // (基本的には発生しない。)
                terminate(context);
            } catch (Throwable t) {
                LOGGER.logWarn(
                    "an error occurred while terminating (or waiting to end)  subthreads."
                  , t
                );
            }
            // エラー処理用コールバックを呼び出す。
            support.callErrorInExecution(listeners, e, context);
            throw e;

        } catch (Error e) {
            error = e;
            try {
                // 実行中のスレッドを停止してリスローする。
                // (基本的には発生しない。)
                terminate(context);
            } catch (Throwable t) {
                LOGGER.logWarn(
                    "an error occurred while terminating (or waiting to end) subthreads."
                  , t
                );
            }
            // エラー処理用コールバックを呼び出す。
            support.callErrorInExecution(listeners, e, context);
            throw e;

        } finally {
            try {
                if (commitLogger != null) {
                    commitLogger.terminate();
                }
                // 終端処理用コールバックを呼び出す。
                support.callPostExecution(listeners, results, context);

            // 以下、本処理が正常終了している場合のみ例外を送出する。
            } catch (RuntimeException e) {
                if (error == null) {
                    throw e;
                }
            } catch (Error e) {
                if (error == null) {
                    throw e;
                }
            }
        }
        return results;
    }

    /**
     * 使用中のデータリーダを閉じ、現在実行中の全てのスレッドに対して停止要求をかける。
     * <p/>
     * terminationTimeoutに設定された時間内にスレッドが停止しなかった場合、
     * その内容をログに出力する。
     * @param context 実行コンテキスト
     */
    private void terminate(ExecutionContext context) {
        context.closeReader();
        if (taskExecutor.isShutdown()) {
            return;
        }
        taskExecutor.shutdownNow();

        boolean terminatedInTime = false;
        try {
            terminatedInTime = taskExecutor.awaitTermination(
                 terminationTimeout, TimeUnit.SECONDS
            );
        } catch (InterruptedException ie) {
            // 停止待ちの間に割り込みが発生した場合はワーニングレベルのログを出力した後、
            // 割り込みフラグを立て直して終了する。
            LOGGER.logWarn("termination was cancelled.", ie);
            Thread.currentThread().interrupt();
        }

        if (terminatedInTime) {
            // 全スレッドを正常に停止させることができた場合は、
            // 終了時点での各子スレッドの状態をInfoログに出力する。
            reportThreadStatus(context);

        } else {
            // 所定時間内にスレッドが停止しなかった場合。
            LOGGER.logWarn(
                "some running tasks could not stop in time. "
              + "terminationTimeout: " + terminationTimeout + " msec."
            );
            Thread.currentThread().interrupt();
        }
    }

    /**
     * スレッドプール上の各スレッドの終了状態をログに出力する。
     * @param context 実行コンテキスト
     */
    private void reportThreadStatus(ExecutionContext context) {
        StringBuilder report = new StringBuilder(LS);
        for (Future<Result> future : taskTracker) {
            try {
                Result result = future.get();
                report.append("Thread Status: normal end." + LS)
                      .append("Thread Result:" + String.valueOf(result) + LS);
            } catch (ExecutionException e) {
                FailureLogUtil.logWarn(e, context.getDataProcessedWhenThrown(e.getCause()), null);
            } catch (InterruptedException e) {
                FailureLogUtil.logWarn(e, context.getDataProcessedWhenThrown(e), null);
            }
        }
        LOGGER.logInfo(report.toString());
    }

    /**
     * スレッドプールを初期化する。
     *
     * スレッドプールの通常値と上限値を一致させスレッドサイズを固定化する。
     * また、タスクキューの上限は設けないので、
     * タスク追加の時点で拒否されたり、ブロックされたりすることは無い。
     *
     * なお、本ハンドラが複数回実行された場合、前回実行時に作成したスレッドプールが
     * アクティブであるかをチェックし、もしそうであれば再作成せずに流用する。
     */
    private void initializeThreadPool() {
        if (taskExecutor == null || taskExecutor.isTerminated()) {
            taskExecutor = new ThreadPoolExecutor(
                concurrentNumber,                     // 通常プールサイズ
                concurrentNumber,                     // 最大プールサイズ
                Long.MAX_VALUE, TimeUnit.NANOSECONDS, // 余剰スレッドの維持期間
                new LinkedBlockingQueue<Runnable>()   // ワーキングタスクキュー(上限なし)
            );
        } else {
            taskExecutor.purge(); // 念のため
        }
        taskStatus  = new ExecutorCompletionService<Result>(taskExecutor);
        taskTracker = new ArrayList<Future<Result>>();
    }

    /**
     * 実行コンテキストのクローンを作成し、そのコンテキストを使用して
     * 後続処理を行うタスクを作成する。
     *
     * @param data 入力データオブジェクト
     * @param context 実行コンテキスト
     * @return タスク
     */
    private Callable<Result> createTaskFor(final Object data,
                                           final ExecutionContext context) {
        final ExecutionContext clonedContext = context.copy();
        return new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                try {
                    return clonedContext.handleNext(data);
                } catch (RuntimeException e) {
                    setDataOnException(e, context, clonedContext);
                    throw e;
                } catch (Error e) {
                    setDataOnException(e, context, clonedContext);
                    throw e;
                }
            }
            private void setDataOnException(Throwable e, ExecutionContext parent, ExecutionContext child) {
                parent.putDataOnException(e, child.getLastReadData());
            }
        };
    }

    //------------------------------------------------------------- Accessors
    /**
     * 並行実行スレッド数を設定する。
     * <p/>
     * デフォルト値は1である。
     *
     * @param concurrentNumber 並行実行スレッド数
     * @return このハンドラ自体
     */
    public MultiThreadExecutionHandler setConcurrentNumber(int concurrentNumber) {
        if (concurrentNumber < 1) {
            throw new IllegalArgumentException(
                    "concurrentNumber must be greater than or equal to 1."
            );
        }
        this.concurrentNumber = concurrentNumber;
        return this;
    }

    /**
     * 処理停止要求のタイムアウト秒数を設定する。
     * <p/>
     * デフォルト値は 600秒 である。
     *
     * @param terminationTimeout 処理停止要求のタイムアウト秒数
     * @return このハンドラ自体。
     */
    public MultiThreadExecutionHandler setTerminationTimeout(int terminationTimeout) {
        this.terminationTimeout = terminationTimeout;
        return this;
    }

    /**
     * コミットログ出力オブジェクトを設定する。
     * @param commitLogger コミットログ出力オブジェクト
     */
    public void setCommitLogger(CommitLogger commitLogger) {
        this.commitLogger = commitLogger;
    }

    /** ロガー */
    private static final Logger
        LOGGER = LoggerManager.get(MultiThreadExecutionHandler.class);
}
