package nablarch.fw.launcher;

/**
 * TODO write document comment.
 *
 * @author T.Kawasaki
 */
public class MockLifecycle implements ProcessLifecycle {

    boolean init;
    boolean exec;
    boolean vmShutdown;
    boolean term;
    CommandLine commandLine;

    @Override
    public void initialize() {
        init = true;
    }

    @Override
    public void execute() {
        exec = true;
    }

    @Override
    public void onVirtualMachineShutdown() {
        vmShutdown = true;
    }

    @Override
    public void terminate() {
        term = true;
    }

    @Override
    public void setCommandLine(CommandLine commandLine) {
        this.commandLine = commandLine;
    }
}
