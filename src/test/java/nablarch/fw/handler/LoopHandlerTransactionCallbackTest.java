package nablarch.fw.handler;

import nablarch.core.repository.SystemRepository;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.handler.action.InputDataNothingBatchAction;
import nablarch.fw.launcher.CommandLine;

import nablarch.test.SystemPropertyResource;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * {@link LoopHandler}のテストクラス。
 * <p/>
 * 本クラスでは、トランザクションデータに対するコールバック部分に着目してテストを行う。
 *
 * @author hisaaki sioiri
 */
@RunWith(DatabaseTestRunner.class)
public class LoopHandlerTransactionCallbackTest {
	
    @Rule
    public SystemRepositoryResource repositoryResource = new SystemRepositoryResource("nablarch/fw/handler/LoopHandlerTransactionCallbackTest.xml");
    
    @Rule
    public final SystemPropertyResource systemPropertyResource = new SystemPropertyResource();

    @BeforeClass
    public static void classSetup() {
    	VariousDbTestHelper.createTable(HandlerBatchInput.class);
   }
    
    @Before
    public void setup() {
    	VariousDbTestHelper.setUpTable(
    			new HandlerBatchInput("0000000000", "0"),
    			new HandlerBatchInput("0000000002", "0"),
    			new HandlerBatchInput("0000000003", "0"),
    			new HandlerBatchInput("0000000004", "0"),
    			new HandlerBatchInput("0000000005", "0"),
    			new HandlerBatchInput("0000000006", "0"),
    			new HandlerBatchInput("0000000007", "0"));
    }

    /**
     * リポジトリの初期化処理を行う。
     *
     * @param commitCount コミット間隔
     */
    public void initRepository(int commitCount) {
    	repositoryResource.getComponentByType(LoopHandler.class).setCommitInterval(commitCount);
    }

    /**
     * {@link LoopHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * ステータスが'0'のデータを対象として、全てのトランザクションデータを正常に処理できる場合
     * <p/>
     * 正常処理時にコールバックされる処理では、ステータスを'1'に更新する。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHandle1() {

        initRepository(1);

        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("diConfig", "");
        opts.put("userId", "user");
        opts.put("requestPath", "action.NormalEndBatchAction/TEST");
        CommandLine commandLine = new CommandLine(opts,
                new ArrayList<String>());

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(
                (Collection<? extends Handler>) SystemRepository.get(
                        "handlerQueue"));
        context.handleNext(commandLine);

        List<HandlerBatchInput> result = VariousDbTestHelper.findAll(HandlerBatchInput.class);
        for (HandlerBatchInput entity : result) {
            assertThat(entity.status, is("1"));
        }
    }

    /**
     * {@link LoopHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * ステータスが'0'のデータを対象として、全てのトランザクションデータを正常に処理できる場合
     * <p/>
     * 正常処理時にコールバックされる処理では、ステータスを'1'に更新する。
     * ※コミット間隔を1以外で実施
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHandle2() {

        initRepository(3);

        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("diConfig", "");
        opts.put("userId", "user");
        opts.put("requestPath", "action.NormalEndBatchAction/TEST");
        CommandLine commandLine = new CommandLine(opts,
                new ArrayList<String>());

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(
                (Collection<? extends Handler>) SystemRepository.get(
                        "handlerQueue"));
        context.handleNext(commandLine);

        List<HandlerBatchInput> result = VariousDbTestHelper.findAll(HandlerBatchInput.class);
        for (HandlerBatchInput entity : result) {
            assertThat(entity.status, is("1"));
        }
    }

    /**
     * {@link LoopHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * ステータスが'0'のデータを対象として、ID='0000000004'でエラーが発生する場合
     * <p/>
     * 異常終了となったレコード別トランザクションで処理が行われるので、statusが'9'に変更されていること
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHandle3() {

        initRepository(1);

        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("diConfig", "");
        opts.put("userId", "user");
        opts.put("requestPath", "action.AbnormalEndBatchAction/TEST");
        CommandLine commandLine = new CommandLine(opts,
                new ArrayList<String>());

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(
                (Collection<? extends Handler>) SystemRepository.get(
                        "handlerQueue"));
        try {
            context.handleNext(commandLine);
            fail("");
        } catch (IllegalArgumentException e) {
            // nop
        }

        List<HandlerBatchInput> result = VariousDbTestHelper.findAll(HandlerBatchInput.class);
        for (HandlerBatchInput entity : result) {
        	if ("0000000000".equals(entity.id) || "0000000002".equals(entity.id) || "0000000003".equals(entity.id)) {
        		// エラーが発生するレコードまでは処理済(ステータス='1')になっていること
        		assertThat(entity.status, is("1"));
        	} else if ("0000000004".equals(entity.id)) {
        		// エラーが発生したレコードのステータスはエラー(ステータス='9')になっていること。
        		assertThat(entity.status, is("9"));
        	} else {
        		// エラーが発生して処理が終わるのでエラーが発生したレコードより後は、未処理(ステータス='0')になっていること
        		assertThat(entity.status, is("0"));
        	}
        }
    }

    /**
     * {@link LoopHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * コミット間隔が'5'のため、エラー発生データ('0000000004')以前に処理されたデータは全てロールバックされること。
     * <p/>
     * 異常終了となったレコード別トランザクションで処理が行われるので、statusが'9'に変更されていること
     * <p/>
     * ※コミット間隔が5の場合
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHandle4() {

        initRepository(5);

        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("diConfig", "");
        opts.put("userId", "user");
        opts.put("requestPath", "action.AbnormalEndBatchAction/TEST");
        CommandLine commandLine = new CommandLine(opts,
                new ArrayList<String>());

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(
                (Collection<? extends Handler>) SystemRepository.get(
                        "handlerQueue"));
        try {
            context.handleNext(commandLine);
            fail("");
        } catch (IllegalArgumentException e) {
            // nop
        }

        List<HandlerBatchInput> result = VariousDbTestHelper.findAll(HandlerBatchInput.class);
        for (HandlerBatchInput entity : result) {
        	if ("0000000004".equals(entity.id)) {
        		// エラーが発生したレコードのステータスはエラー(ステータス='9')になっていること。
        		assertThat(entity.status, is("9"));
        	} else {
        		// エラーが発生していないレコードのステータスは未処理(ステータス='0')になっていること
        		assertThat(entity.status, is("0"));
        	}
        }
    }

    /**
     * {@link LoopHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * 正常終了のコールバックで例外が発生した場合も、エラー用のコールバックが呼ばれること。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHandle5() {

        initRepository(1);
        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("diConfig", "");
        opts.put("userId", "user");
        opts.put("requestPath",
                "action.ErrorOnSuccessCallbackBatchAction/TEST");
        CommandLine commandLine = new CommandLine(opts,
                new ArrayList<String>());
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(
                (Collection<? extends Handler>) SystemRepository.get(
                        "handlerQueue"));
        try {
            context.handleNext(commandLine);
            fail("");
        } catch (NullPointerException e) {
            // nop
        }

        List<HandlerBatchInput> result = VariousDbTestHelper.findAll(HandlerBatchInput.class);
        for (HandlerBatchInput entity : result) {
        	if ("0000000000".equals(entity.id)) {
        		// エラーが発生したレコードのステータスはエラー(ステータス='9')になっていること。
        		assertThat(entity.status, is("9"));
        	} else {
        		// エラーが発生していないレコードのステータスは未処理(ステータス='0')になっていること
        		assertThat(entity.status, is("0"));
        	}
        }
    }

    /**
     * {@link LoopHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * 本処理で{@link RuntimeException}が発生し、
     * 障害時のコールバックでも再度{@link RuntimeException}が発生する場合。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHandle6() {

        initRepository(1);

        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("diConfig", "");
        opts.put("userId", "user");
        opts.put("requestPath",
                "action.RuntimeExceptionOnFailureCallbackBatchAction1/TEST");
        CommandLine commandLine = new CommandLine(opts,
                new ArrayList<String>());
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(
                (Collection<? extends Handler>) SystemRepository.get(
                        "handlerQueue"));
        try {
            context.handleNext(commandLine);
            fail("");
        } catch (NullPointerException e) {
            // nop
        }

        List<HandlerBatchInput> result = VariousDbTestHelper.findAll(HandlerBatchInput.class);
        for (HandlerBatchInput entity : result) {
        	// 全てのレコードのステータスは'0'のままであること
        	assertThat(entity.status, is("0"));
        }
    }

    /**
     * {@link LoopHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * 本処理で{@link Error}が発生し、
     * 障害時のコールバックでも再度{@link RuntimeException}が発生する場合。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHandle7() {

        initRepository(1);

        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("diConfig", "");
        opts.put("userId", "user");
        opts.put("requestPath",
                "action.RuntimeExceptionOnFailureCallbackBatchAction2/TEST");
        CommandLine commandLine = new CommandLine(opts,
                new ArrayList<String>());
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(
                (Collection<? extends Handler>) SystemRepository.get(
                        "handlerQueue"));
        try {
            context.handleNext(commandLine);
            fail("");
        } catch (NullPointerException e) {
            // nop
        }

        List<HandlerBatchInput> result = VariousDbTestHelper.findAll(HandlerBatchInput.class);
        for (HandlerBatchInput entity : result) {
        	// 全てのレコードのステータスは'0'のままであること
        	assertThat(entity.status, is("0"));
        }
    }

    /**
     * {@link LoopHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * 本処理で{@link Error}が発生し、
     * 障害時のコールバックでも再度{@link Error}が発生する場合。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHandle8() {

        initRepository(1);

        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("diConfig", "");
        opts.put("userId", "user");
        opts.put("requestPath",
                "action.ErrorOnFailureCallbackBatchAction1/TEST");
        CommandLine commandLine = new CommandLine(opts,
                new ArrayList<String>());
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(
                (Collection<? extends Handler>) SystemRepository.get(
                        "handlerQueue"));
        try {
            context.handleNext(commandLine);
            fail("");
        } catch (OutOfMemoryError e) {
            // nop
        }

        List<HandlerBatchInput> result = VariousDbTestHelper.findAll(HandlerBatchInput.class);
        for (HandlerBatchInput entity : result) {
        	// 全てのレコードのステータスは'0'のままであること
        	assertThat(entity.status, is("0"));
        }

    }

    /**
     * {@link LoopHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * 本処理で{@link RuntimeException}が発生し、
     * 障害時のコールバックでも再度{@link StackOverflowError}が発生する場合。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHandle9() {

        initRepository(1);

        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("diConfig", "");
        opts.put("userId", "user");
        opts.put("requestPath",
                "action.ErrorOnFailureCallbackBatchAction2/TEST");
        CommandLine commandLine = new CommandLine(opts,
                new ArrayList<String>());
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(
                (Collection<? extends Handler>) SystemRepository.get(
                        "handlerQueue"));
        try {
            context.handleNext(commandLine);
            fail("");
        } catch (OutOfMemoryError e) {
            // nop
        }

        List<HandlerBatchInput> result = VariousDbTestHelper.findAll(HandlerBatchInput.class);
        for (HandlerBatchInput entity : result) {
        	// 全てのレコードのステータスは'0'のままであること
        	assertThat(entity.status, is("0"));
        }
    }

    /**
     * {@link LoopHandler#handle(Object, nablarch.fw.ExecutionContext)}のテスト。
     * <p/>
     * インプットデータがnullなので、コールバックメソッドは呼び出されないこと。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHandle10() {

        initRepository(1);

        HashMap<String, String> opts = new HashMap<String, String>();
        opts.put("diConfig", "");
        opts.put("userId", "user");
        opts.put("requestPath",
                "action.InputDataNothingBatchAction/TEST");
        CommandLine commandLine = new CommandLine(opts,
                new ArrayList<String>());
        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(
                (Collection<? extends Handler>) SystemRepository.get(
                        "handlerQueue"));

        assertFalse(InputDataNothingBatchAction.normalEndCall);
        context.handleNext(commandLine);
        // アクション呼び出し後も、正常終了のコールバックが呼び出されないこと。
        assertFalse(InputDataNothingBatchAction.normalEndCall);

        context = new ExecutionContext();
        context.setHandlerQueue(
                (Collection<? extends Handler>) SystemRepository.get(
                        "handlerQueue"));
        InputDataNothingBatchAction.onError = true;
        assertFalse(InputDataNothingBatchAction.failuerCall);
        try {
            context.handleNext(commandLine);
            fail("");
        } catch (IllegalStateException e) {
        }
        // アクション呼び出し後も、正常終了のコールバックが呼び出されないこと。
        assertFalse(InputDataNothingBatchAction.failuerCall);

    }
}
