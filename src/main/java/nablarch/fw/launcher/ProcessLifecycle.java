package nablarch.fw.launcher;

import nablarch.core.util.annotation.Published;

/**
 * アプリケーション起動を実装するためのインタフェース。
 *
 * @author T.Kawasaki
 * @see GenericLauncher#launch()
 */
@Published(tag = "architect")
public interface ProcessLifecycle {

    /**
     * 初期化処理を行う。
     */
    void initialize();

    /**
     * 本処理を行う。
     */
    void execute();

    /**
     * プロセス停止が呼ばれた際のフック。
     * @see Runtime#addShutdownHook(Thread)
     */
    void onVirtualMachineShutdown();

    /**
     * 終了処理を行う。
     */
    void terminate();
    
    /**
     * コマンドライン引数を設定する。
     * @param commandLine コマンドライン引数
     */
    void setCommandLine(CommandLine commandLine);
}
