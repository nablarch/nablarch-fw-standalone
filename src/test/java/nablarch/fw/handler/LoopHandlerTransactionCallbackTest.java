package nablarch.fw.handler;

import nablarch.core.repository.SystemRepository;

import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;

import nablarch.fw.handler.action.AbnormalEndHandler;
import nablarch.fw.handler.action.ErrorOnFailureCallbackHandler1;
import nablarch.fw.handler.action.ErrorOnFailureCallbackHandler2;
import nablarch.fw.handler.action.ErrorOnSuccessCallbackHandler;
import nablarch.fw.handler.action.InputDataNothingHandler;
import nablarch.fw.handler.action.NormalEndHandler;
import nablarch.fw.handler.action.RuntimeExceptionOnFailureCallbackHandler1;
import nablarch.fw.handler.action.RuntimeExceptionOnFailureCallbackHandler2;
import nablarch.test.support.SystemRepositoryResource;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import org.junit.*;
import org.junit.runner.RunWith;


import java.util.ArrayList;
import java.util.Iterator;
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

    @BeforeClass
    public static void classSetup() {
        VariousDbTestHelper.createTable(HandlerBatchInput.class);
    }

    @Before
    public void setUp() {
        VariousDbTestHelper.setUpTable(
                new HandlerBatchInput("0000000000", "0"),
                new HandlerBatchInput("0000000002", "0"),
                new HandlerBatchInput("0000000003", "0"),
                new HandlerBatchInput("0000000004", "0"),
                new HandlerBatchInput("0000000005", "0"),
                new HandlerBatchInput("0000000006", "0"),
                new HandlerBatchInput("0000000007", "0"));
    }

    private static class Reader implements DataReader<String> {
        private Iterator<String> iterator = new ArrayList<String>() {{
            add("0000000000");
            add("0000000002");
            add("0000000003");
            add("0000000004");
            add("0000000005");
            add("0000000006");
            add("0000000007");
        }}.iterator();

        public String read(ExecutionContext ctx) {
            if (iterator.hasNext()) {
                return iterator.next();
            } else {
                return null;
            }
        }

        public boolean hasNext(ExecutionContext ctx) {
            return iterator.hasNext();
        }

        public void close(ExecutionContext ctx) {
            // nop
        }
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
    public void testHandle1() throws Exception {

        initRepository(1);

        List<Handler<?, ?>> handlerList = SystemRepository.get("handlerQueue");
        NormalEndHandler normalEndHandler = new NormalEndHandler();
        handlerList.add(normalEndHandler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerList);
        context.setDataReader(new Reader());
        context.handleNext(null);

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

        List<Handler<?, ?>> handlerList = SystemRepository.get("handlerQueue");
        NormalEndHandler handler = new NormalEndHandler();
        handlerList.add(handler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerList);
        context.setDataReader(new Reader());
        context.handleNext(null);

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

        List<Handler<?, ?>> handlerList = SystemRepository.get("handlerQueue");
        AbnormalEndHandler handler = new AbnormalEndHandler();
        handlerList.add(handler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerList);
        context.setDataReader(new Reader());
        try {
            context.handleNext(null);
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

        List<Handler<?, ?>> handlerList = SystemRepository.get("handlerQueue");
        AbnormalEndHandler handler = new AbnormalEndHandler();
        handlerList.add(handler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerList);
        context.setDataReader(new Reader());
        try {
            context.handleNext(null);
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

        List<Handler<?, ?>> handlerList = SystemRepository.get("handlerQueue");
        ErrorOnSuccessCallbackHandler handler = new ErrorOnSuccessCallbackHandler();
        handlerList.add(handler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerList);
        context.setDataReader(new Reader());
        try {
            context.handleNext(null);
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

        List<Handler<?, ?>> handlerList = SystemRepository.get("handlerQueue");
        RuntimeExceptionOnFailureCallbackHandler1 handler = new RuntimeExceptionOnFailureCallbackHandler1();
        handlerList.add(handler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerList);
        context.setDataReader(new Reader());
        try {
            context.handleNext(null);
            fail("");
        } catch (NumberFormatException e) {
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

        List<Handler<?, ?>> handlerList = SystemRepository.get("handlerQueue");
        RuntimeExceptionOnFailureCallbackHandler2 handler = new RuntimeExceptionOnFailureCallbackHandler2();
        handlerList.add(handler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerList);
        context.setDataReader(new Reader());
        try {
            context.handleNext(null);
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

        List<Handler<?, ?>> handlerList = SystemRepository.get("handlerQueue");
        ErrorOnFailureCallbackHandler1 handler = new ErrorOnFailureCallbackHandler1();
        handlerList.add(handler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerList);
        context.setDataReader(new Reader());
        try {
            context.handleNext(null);
            fail("");
        } catch (StackOverflowError e) {
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

        List<Handler<?, ?>> handlerList = SystemRepository.get("handlerQueue");
        ErrorOnFailureCallbackHandler2 handler = new ErrorOnFailureCallbackHandler2();
        handlerList.add(handler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerList);
        context.setDataReader(new Reader());
        try {
            context.handleNext(null);
            fail("");
        } catch (StackOverflowError e) {
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
     * インプットデータが{@code null}なので、コールバックメソッドは呼び出されないこと。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testHandle10() {

        initRepository(1);

        List<Handler<?, ?>> handlerList = SystemRepository.get("handlerQueue");
        InputDataNothingHandler handler = new InputDataNothingHandler();
        handlerList.add(handler);

        ExecutionContext context = new ExecutionContext();
        context.setHandlerQueue(handlerList);
        context.setDataReader(new Reader());

        assertFalse(InputDataNothingHandler.normalEndCall);
        context.handleNext(null);
        // ハンドラ呼び出し後も、正常終了のコールバックが呼び出されないこと。
        assertFalse(InputDataNothingHandler.normalEndCall);

        context = new ExecutionContext();
        context.setHandlerQueue(handlerList);
        context.setDataReader(new Reader());

        InputDataNothingHandler.onError = true;
        assertFalse(InputDataNothingHandler.failuerCall);
        try {
            context.handleNext(null);
            fail("");
        } catch (IllegalStateException e) {
        }
        // ハンドラ呼び出し後も、正常終了のコールバックが呼び出されないこと。
        assertFalse(InputDataNothingHandler.failuerCall);

    }
}
