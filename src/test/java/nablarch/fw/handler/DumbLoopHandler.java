package nablarch.fw.handler;

import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.handler.ProcessStopHandler.ProcessStop;
import nablarch.fw.launcher.ProcessAbnormalEnd;

public class DumbLoopHandler implements Handler<Object, Object> {
    public Object handle(Object data, ExecutionContext context) {
        Object result = null;
        while (context.hasNextData()) {
            ExecutionContext ctx = new ExecutionContext(context);
            result = ctx.handleNext(data);
            if (result instanceof ProcessStop) {
                return result;
            }
            if (result instanceof ProcessAbnormalEnd) {
                return result;
            }
        }
        return result;
    }
}
