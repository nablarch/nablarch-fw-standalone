package nablarch.fw.handler.retry;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

/**
 * {@link nablarch.fw.handler.retry.RetryableException}のテスト。
 *
 * @author hisaaki sioiri
 */
public class RetryableExceptionTest {

    @Test
    public void testCreateInstance() {
        RetryableException exception = new RetryableException();
        assertThat(exception.getMessage(), is(nullValue()));

        exception = new RetryableException("error");
        assertThat(exception.getMessage(), is("error"));

        exception = new RetryableException("error", new NullPointerException());
        assertThat(exception.getMessage(), is("error"));
        assertThat(exception.getCause(), is(instanceOf(NullPointerException.class)));

        exception = new RetryableException(new NullPointerException());
        assertThat(exception.getMessage(), is("java.lang.NullPointerException"));
        assertThat(exception.getCause(), is(instanceOf(NullPointerException.class)));
    }

}
