package nablarch.fw.mock;

import nablarch.core.date.BusinessDateProvider;

import java.util.Map;

public class MockBusinessDateProvider implements BusinessDateProvider {
    private String date = "20111201";


    @Override
    public String getDate() {
        return date;
    }

    @Override
    public String getDate(String segment) {
        return date;
    }

    @Override
    public Map<String, String> getAllDate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDate(String segment, String date) {
        this.date = date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
