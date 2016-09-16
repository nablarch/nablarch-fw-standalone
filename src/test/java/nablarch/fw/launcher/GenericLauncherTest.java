package nablarch.fw.launcher;

import nablarch.core.repository.SystemRepository;
import nablarch.fw.results.BadRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link GenericLauncherTest}のテストクラス。
 *
 * @author T.Kawasaki
 */
public class GenericLauncherTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    @After
    public void clear() {
        SystemRepository.clear();
    }

    String[] args = {
            "-diConfig", "nablarch/fw/launcher/GenericLauncherTest.xml",
            "-userId", "test",
            "-requestPath", "path"
    };

    /** メインの起動テスト */
    @Test
    public void testMain() {

        GenericLauncher.main(args);
        MockLifecycle mock = SystemRepository.get(GenericLauncher.PROCESS_LIFECYCLE_KEY);
        assertTrue(mock.init);
        assertTrue(mock.exec);
        assertTrue(mock.term);
        assertFalse(mock.vmShutdown);
    }

    /** LifeCycleが定義されていない場合、例外が発生すること。 */
    @Test
    public void testLifecycleNotFound() {
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("could not find required component. key=[processLifecycle]");

        GenericLauncher.main(new String[]{"-diConfig", "nablarch/fw/launcher/empty.xml"});
    }

    /** 引数が不正の場合、例外が発生すること。*/
    @Test
    public void testInvalidArgs() {
        expectedException.expect(BadRequest.class);
        expectedException.expectMessage("parameter [-diConfig] must be specified.");

        GenericLauncher.main(null);
    }

    @Test
    public void testFillDefault() {
        GenericLauncher sut = new GenericLauncher(new String[]{
                "-diConfig", "nablarch/fw/launcher/GenericLauncherTest.xml",
                "-userId", "test"
        });
    }
}