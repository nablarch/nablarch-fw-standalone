package nablarch.fw.handler.dispatch.test3.ss00A001;

import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.web.HttpRequest;
import nablarch.fw.web.HttpResponse;


public class W11AC002Action implements Handler<HttpRequest, HttpResponse> {
    
    public HttpResponse handle(HttpRequest data, ExecutionContext context) {
        context.setRequestScopedVar("executeAction", "test3.W11AC002Action");
        return new HttpResponse();
    }

}

