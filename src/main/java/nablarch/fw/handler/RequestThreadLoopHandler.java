package nablarch.fw.handler;

import nablarch.core.log.Logger;
import nablarch.core.log.LoggerManager;
import nablarch.core.log.app.FailureLogUtil;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.results.ServiceError;
import nablarch.fw.results.ServiceUnavailable;
import nablarch.fw.handler.ProcessStopHandler.ProcessStop;
import nablarch.fw.handler.retry.RetryUtil;
import nablarch.fw.handler.retry.RetryableException;
import nablarch.fw.launcher.ProcessAbnormalEnd;

/**
 * 各サブスレッド上のループ毎にリクエスト処理を実行するハンドラ。
 * <p/>
 * 本クラスは、サーバソケットや受信電文キュー等を監視し、リアルタイム応答を行う
 * サーバ型プロセスで使用するハンドラである。
 * サーバ型プロセスでは、マルチスレッドハンドラが生成する各サブスレッド上で
 * 次のループを繰り返す。
 * <div><b>
 * データリーダによるリクエストの受信 → リクエスト処理の実行 → 次のリクエストの待機
 * </b></div>
 * 本ハンドラではこの形態のループ制御を行う。
 * サーバ型処理では、バッチ処理とは異なり、個々のリクエスト処理は完全に独立しており、
 * 1つのリクエスト処理がエラーとなっても他のリクエスト処理はそのまま継続しなければならない。
 * このため、本ハンドラで捕捉した例外は、プロセス正常停止要求や致命的な一部の例外を除き
 * リトライ可能例外{@link RetryableException}として再送出する。
 * 
 * @author Iwauo Tajima
 */
public class RequestThreadLoopHandler implements Handler<Object, Object> {
    
    /**
     * 後続ハンドラから閉局中例外が送出された場合、
     * このスレッドが次のリクエスト処理を開始するまでに待機する時間。
     */
    private int serviceUnavailabilityRetryInterval = 1000;

    /** {@inheritDoc} */
    public Result handle(Object data, ExecutionContext ctx) {
        Result result = null;
        while (ctx.hasNextData()) {
            try {
                ExecutionContext clonedCtx = new ExecutionContext(ctx);
                result = clonedCtx.handleNext(data);
                
            // サービス閉局エラー発生時:
            //   一定時間待機後にループ再開(ログは出力しない。)
            } catch (ServiceUnavailable e) {
                result = e;                
                int interval = (serviceUnavailabilityRetryInterval <= 0)
                             ? 0
                             : serviceUnavailabilityRetryInterval;
                
                // サービス閉局中の場合は一定時間wait後にループを継続する。
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.logTrace(
                        "request thread waits " 
                      + interval + "msec "
                      + "due to temporary service unavailability.");
                }
                if (interval > 0) {
                    try {
                        Thread.sleep(interval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                }
                
            // プロセス正常停止要求発生時:
            //   INFOログを出力した上で、
            //   Result.Successをリターンしプロセスを終了させる。
            } catch (ProcessStop e) {
                LOGGER.logInfo("the shutdown flag of this process was set. shutting down...");
                return new Result.Success("shut down this process normally.");
            
            // プロセス異常停止要求発生時:
            //   元例外を再送出し、プロセスを異常終了させる。
            } catch (ProcessAbnormalEnd e) {
                throw e;
            
            // サービスエラー発生時:
            //   障害ログを出力後、リトライ可能例外を送出しプロセスは継続させる。
            } catch (ServiceError e) {
                e.writeLog(ctx);
                throw new RetryableException(e);
            
            // ServiceError以外のResult.Error:
            //   障害ログを出力後、リトライ可能例外を出力しプロセスは継続させる。
            } catch (Result.Error e) {
                FailureLogUtil.logFatal(e, ctx.getDataProcessedWhenThrown(e), null);
                throw new RetryableException(e);
    
            // その他の実行時例外:
            //   リトライ可能例外はなにもせずにそのまま再送出。
            //   それ以外の実行時例外は障害ログを出力したのち、リトライ可能例外を
            //   出力しプロセスは継続させる。
            } catch (RuntimeException e) {
                if (RetryUtil.isRetryable(e)) {
                    throw e;
                }
                FailureLogUtil.logFatal(e, ctx.getDataProcessedWhenThrown(e), null);
                throw new RetryableException(e);
    
            // ThreadDeath:
            //   Thread.stop()で止められた場合に発生。
            //   一応Infoログだけ出してからリスロー                
            } catch (ThreadDeath e) {
                LOGGER.logInfo("Uncaught error: ", e);
                throw e;
    
            // StackOverflowError:
            //   業務処理における無限ループバグの可能性が高いので、
            //   障害ログを出力し、プロセスは継続させる。
            } catch (StackOverflowError e) {
                FailureLogUtil.logFatal(e, ctx.getDataProcessedWhenThrown(e), null);
                throw new RetryableException(e);
    
            // OutOfMemoryError:
            //   実行コンテキスト上の変数スコープがGC対象となることによって、
            //   ヒープ容量が回復可能性があるので、障害ログを出力し、プロセスは継続させる。
            //   なお、障害ログ出力前にエラー出力に最小限のメッセージを出力しておく。
            } catch (OutOfMemoryError e) {
                System.err.println("OutOfMemoryError occurred: " + e.getMessage());
                try {
                    FailureLogUtil.logFatal(e, ctx.getDataProcessedWhenThrown(e), null);
    
                } catch (Throwable ignored) {
                    LOGGER.logDebug("couldn't write log. : ", e);
                }
                throw new RetryableException(e);

            // StackOverflowError/OutOfMemoryError以外のVMエラー:
            //   リスローしプロセスを停止させる。
            } catch (VirtualMachineError e) {
                throw e;
    
            // その他のエラー:
            //   障害ログを出力後、プロセスは継続させる。
            } catch (Error e) {
                FailureLogUtil.logFatal(e, ctx.getDataProcessedWhenThrown(e), null);
                throw new RetryableException(e);
            }
        }
        return result;
    }
    /**
     * 後続ハンドラから閉局中例外が送出された場合に、次のリクエスト処理を開始するまでに待機する時間を設定する。
     * 設定値が0以下の場合は、待機せずに即時リトライを行なう。
     * デフォルトの設定値は1000msecである。
     * 
     * @param msec 閉局エラー中の各スレッド待機時間 (単位: msec)
     * @return このオブジェクト自体
     */
    public RequestThreadLoopHandler setServiceUnavailabilityRetryInterval(int msec) {
        serviceUnavailabilityRetryInterval = msec;
        return this;
    }
    
    /** ロガー */
    private static final Logger
        LOGGER = LoggerManager.get(RequestThreadLoopHandler.class);
}
