package nablarch.fw.handler.dispatch.test3.ss00A001;

import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.Result.Success;
import nablarch.fw.launcher.CommandLine;

public class B11AC002Action implements Handler<CommandLine, Result> {
    
    public Result handle(CommandLine data, ExecutionContext context) {
        context.setRequestScopedVar("executeAction", "test3.B11AC002Action");
        return new Success();
    }

}
