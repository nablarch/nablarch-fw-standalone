package nablarch.fw.handler.dispatch.base.ss00A002;

import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.web.HttpRequest;
import nablarch.fw.web.HttpResponse;

public class W11AC001Action implements Handler<HttpRequest, HttpResponse> {
    
    public HttpResponse handle(HttpRequest data, ExecutionContext context) {
        context.setRequestScopedVar("executeAction", "base.W11AC001Action");
        return new HttpResponse();
    }

}
