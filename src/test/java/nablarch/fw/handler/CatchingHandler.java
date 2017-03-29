package nablarch.fw.handler;

import nablarch.core.ThreadContext;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;

import java.util.ArrayList;
import java.util.List;

public class CatchingHandler implements Handler<Object, Object> {

    private static final List<Object> inputHolder = new ArrayList<Object>();
    
    private static final List<Object> resultHolder = new ArrayList<Object>();
    
    private static final List<String> executionIdHolder = new ArrayList<String>();
    
    public Object handle(Object data, ExecutionContext context) {
        inputHolder.add(data);
        executionIdHolder.add(ThreadContext.getExecutionId());
        try {
            Object result = context.handleNext(data);
            resultHolder.add(result);
            return result;
            
        } catch (RuntimeException e) {
            resultHolder.add(e);
            throw e;
        } catch (Error e) {
            resultHolder.add(e);
            throw e;  
        }
    }
    
    public static void clear() {
        resultHolder.clear();
        inputHolder.clear();
        executionIdHolder.clear();
    }    
    
    @SuppressWarnings("unchecked")
    public static <T> List<T> getResults() {
        return (List<T>) resultHolder;
    }    
    
    @SuppressWarnings("unchecked")
    public static <T> List<T> getInput() {
        return (List<T>) inputHolder;
    }
    
    public static List<String> getExecutionIds() {
        return executionIdHolder;
    }     
}
