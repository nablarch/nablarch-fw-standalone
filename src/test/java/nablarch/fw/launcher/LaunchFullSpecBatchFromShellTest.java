package nablarch.fw.launcher;

import nablarch.fw.batch.sample.TestActionWithCallback;
import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import nablarch.test.support.tool.Hereis;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * マルチスレッド、各種コールバック、インターセプタを利用した
 * 都度起動バッチの挙動を検証するテスト。
 * 
 * @see TestActionWithCallback テスト用アクションクラス
 * @author Iwauo Tajima
 */
@RunWith(DatabaseTestRunner.class)
public class LaunchFullSpecBatchFromShellTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        VariousDbTestHelper.createTable(InputData.class);
        VariousDbTestHelper.createTable(ResultData.class);
    }
    
    @Before
    public void setUp() throws Exception {
        InputData[] data = new InputData[150];
        for (int i = 0; i < 150; i++) {
            data[i] = new InputData(String.valueOf(i));
        }
        VariousDbTestHelper.setUpTable(data);
        VariousDbTestHelper.delete(ResultData.class);
    }

    // アプリケーション構成
    private File diConfig = Hereis.file("./batch-config.xml");
    /***********************************************************************
    <?xml version="1.0" encoding="UTF-8"?>
    <component-configuration
      xmlns = "http://tis.co.jp/nablarch/component-configuration">
      
      <!-- データベース接続構成 -->
      <import file="db-default.xml"/>
      
      <!-- ハンドラーキュー構成 -->
      <list name="handlerQueue">
      
        <!-- 共通エラーハンドラー -->
        <component class="nablarch.fw.handler.GlobalErrorHandler" />
          
        <!-- スレッドコンテキスト管理ハンドラ-->
        <component class="nablarch.common.handler.threadcontext.ThreadContextHandler">
          <property name="attributes">
            <list>
            <!-- ユーザID -->
            <component class="nablarch.common.handler.threadcontext.UserIdAttribute">
              <property name="sessionKey" value="user.id" />
              <property name="anonymousId" value="9999999999" />
            </component>
            <!-- リクエストID -->
            <component class="nablarch.common.handler.threadcontext.RequestIdAttribute" />
            <!-- 言語 -->
            <component class="nablarch.common.handler.threadcontext.LanguageAttribute">
              <property name="defaultLanguage" value="ja" />
            </component>
            <!-- 実行時ID -->
            <component class="nablarch.common.handler.threadcontext.ExecutionIdAttribute" />
            </list>
          </property>
        </component>
        
        <!-- データベース接続管理ハンドラ(メインスレッド用) -->
        <component
            name="dbConnectionManagementHandler" 
            class="nablarch.common.handler.DbConnectionManagementHandler">
        </component>

        <!-- トランザクション制御ハンドラ(メインスレッド用) -->
        <component name="transactionManagementHandler"
            class="nablarch.common.handler.TransactionManagementHandler">
        </component>

        <!-- 業務アクションディスパッチハンドラ -->
        <component class="nablarch.fw.handler.RequestPathJavaPackageMapping">
          <property name="basePackage" value="nablarch.fw.batch.sample"/>
          <property name="immediate" value="false" />
        </component>
        
        <!-- マルチスレッド実行制御ハンドラ -->
        <component class="nablarch.fw.handler.MultiThreadExecutionHandler">
          <property name="concurrentNumber" value="15" />
          <property name="terminationTimeout" value="600" />
        </component>
        
        <!-- データベース接続管理ハンドラ(サブスレッド用) -->
        <component class="nablarch.common.handler.DbConnectionManagementHandler">
        </component>
        
        <!-- トランザクションループハンドラ -->
        <component class="nablarch.fw.handler.LoopHandler">
          <property name="commitInterval" value="3" />
        </component>
        
        <!-- データリードハンドラ -->
        <component class="nablarch.fw.handler.DataReadHandler" />
      </list>
      <!-- ハンドラーキュー構成(END) -->
      
        <component name="initializer" class="nablarch.core.repository.initialization.BasicApplicationInitializer">
        <!--
          BasicApplicationInitializerのinitializeListプロパティ。
          ここで記述した順序で初期化が実行される。
        -->
      </component>
    </component-configuration>
    ************************************************************************/
    
    /**
     * バッチ正常起動→全件正常終了時の挙動確認
     * - 入力処理データ150件
     * - 並行実行スレッド数15
     * - コミット間隔は3件ごと
     */
    @Test(timeout = 60000)
    public void testNormalEnd() throws Exception {
        Process job = Hereis.shell(null);
        /***********************************************************************
        java 
            -DmaxExecutionCount=100000
             nablarch.fw.launcher.Main
            -diConfig    file:./batch-config.xml
            -requestPath TestActionWithCallback/req9999
            -userId      user7777l
            2>&1 execution.log
        ***********************************************************************/
        printStream(job.getInputStream());
        job.waitFor();
        assertEquals(0, job.exitValue());
        
        List<ResultData> resultList = VariousDbTestHelper.findAll(ResultData.class);
        
        int actionCreateReader = 0;
        int actionHandle = 0;
        int actionTransactionSuccess = 0;
        int actionTransactionFailure = 0;
        int actionInitialize = 0;
        int actionError = 0;
        int actionTerminate = 0;
        int beforelogHandle = 0;
        int afterlogHandle = 0;
        for (ResultData result : resultList) {
            if ("action.createReader".equals(result.activity)) { actionCreateReader++; };
            if ("action.handle".equals(result.activity)) { actionHandle++; };
            if ("action.transactionSuccess".equals(result.activity)) { actionTransactionSuccess++; };
            if ("action.transactionFailure".equals(result.activity)) { actionTransactionFailure++; };
            if ("action.initialize".equals(result.activity)) { actionInitialize++; };
            if ("action.error".equals(result.activity)) { actionError++; };
            if ("action.terminate".equals(result.activity)) { actionTerminate++; };
            if ("beforelog.handle".equals(result.activity)) { beforelogHandle++; };
            if ("afterlog.handle".equals(result.activity)) { afterlogHandle++; };
        }
        
        assertEquals(1, actionCreateReader);
        assertEquals(150, actionHandle);
        assertEquals(150, actionTransactionSuccess);
        assertEquals(0, actionTransactionFailure);
        assertEquals(1, actionInitialize);
        assertEquals(0, actionError);
        assertEquals(15, beforelogHandle);
        assertEquals(1, actionTerminate);
        assertEquals(15, afterlogHandle);
    }
    
    /**
     * バッチ正常起動→業務処理にて異常終了時の挙動確認
     * - 入力データ150件
     * - 並行実行スレッド数15
     * - コミット間隔は3件ごと
     * - 50件目のデータを処理する業務アクションがシステムエラー(Result.InternalError)
     *   を送出
     * @throws Exception
     */
    @Test(timeout = 60000)
    public void testAbnormalEnd() throws Exception {
        Process job = Hereis.shell(null);
        /***********************************************************************
        java 
            -DmaxExecutionCount=100000
             nablarch.fw.launcher.Main
            -diConfig    file:./batch-config.xml
            -requestPath TestActionThrowsError/req9999
            -userId      user7777l
            2>&1 execution.log
        ***********************************************************************/
        printStream(job.getInputStream());
        job.waitFor();
        assertEquals(127, job.exitValue()); // Result.InternalError
        
        List<ResultData> resultList = VariousDbTestHelper.findAll(ResultData.class);
        
        int actionCreateReader = 0;
        int actionHandle = 0;
        int actionTransactionSuccess = 0;
        int actionTransactionFailure = 0;
        int actionInitialize = 0;
        int actionError = 0;
        int actionTerminate = 0;
        int beforelogHandle = 0;
        int afterlogHandle = 0;
        for (ResultData result : resultList) {
            if ("action.createReader".equals(result.activity)) { actionCreateReader++; };
            if ("action.handle".equals(result.activity)) { actionHandle++; };
            if ("action.transactionSuccess".equals(result.activity)) { actionTransactionSuccess++; };
            if ("action.transactionFailure".equals(result.activity)) { actionTransactionFailure++; };
            if ("action.initialize".equals(result.activity)) { actionInitialize++; };
            if ("action.error".equals(result.activity)) { actionError++; };
            if ("action.terminate".equals(result.activity)) { actionTerminate++; };
            if ("beforelog.handle".equals(result.activity)) { beforelogHandle++; };
            if ("afterlog.handle".equals(result.activity)) { afterlogHandle++; };
        }
        
        assertEquals(0, actionInitialize); // メインスレッド用のトランザクションはロールバックされてしまう。
        assertEquals(0, actionCreateReader); // メインスレッド用のトランザクションはロールバックされてしまう。
        assertTrue(1 <= beforelogHandle); // マルチスレッド実行なのでどこまで処理されるかは分からない。
        assertTrue(1 <= actionHandle); // マルチスレッド実行なのでどこまで処理されるかは分からない。
        assertTrue(1 <= afterlogHandle); // マルチスレッド実行なのでどこまで処理されるかは分からない。
        assertTrue(1 <= actionTransactionSuccess); // マルチスレッド実行なのでどこまで処理されるかは分からない。
        assertTrue(1  <= actionTransactionFailure); // 巻き添えをくったスレッドでもロールバックが発生する可能性がある。
        assertTrue(15 >= actionTransactionFailure);
        assertEquals(0, actionError); // メインスレッド用のトランザクションはロールバックされてしまう。
        assertEquals(0, actionTerminate); // メインスレッド用のトランザクションはロールバックされてしまう。
    }
    
    public void printStream(InputStream stream) throws Exception {
        BufferedReader stdout = new BufferedReader(
            new InputStreamReader(stream)
        );
        while (true) {
            String line = stdout.readLine();
            if (line == null) {
                break;
            }
            System.out.println(line);
        }
    }
}
