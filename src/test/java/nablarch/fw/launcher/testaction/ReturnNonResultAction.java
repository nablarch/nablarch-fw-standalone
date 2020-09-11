package nablarch.fw.launcher.testaction;

import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.launcher.CommandLine;

public class ReturnNonResultAction implements Handler<CommandLine, String> {
    @Override
    public String handle(CommandLine commandLine, ExecutionContext context) {
        return "non result";
    }
}
