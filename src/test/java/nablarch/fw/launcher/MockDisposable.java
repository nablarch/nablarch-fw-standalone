package nablarch.fw.launcher;

import nablarch.core.repository.disposal.Disposable;

public class MockDisposable implements Disposable {
    private boolean disposed;

    @Override
    public void dispose() throws Exception {
        disposed = true;
    }

    public boolean isDisposed() {
        return disposed;
    }
}
