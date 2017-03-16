package nablarch.fw.handler.dispatch.base.ss00A002;

import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.Result.Success;
import nablarch.fw.launcher.CommandLine;

public class B11AC001Action implements Handler<CommandLine, Result> {
    
    public Result handle(CommandLine data, ExecutionContext context) {
        context.setRequestScopedVar("executeAction", "base.B11AC001Action");
        return new Success();
    }

}
