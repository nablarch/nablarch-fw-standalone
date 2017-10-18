package nablarch.fw.launcher;

import nablarch.test.support.db.helper.DatabaseTestRunner;
import nablarch.test.support.db.helper.VariousDbTestHelper;
import nablarch.test.support.tool.Hereis;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

@Ignore("CI環境でjavaのプロセスを起動できないため")
@RunWith(DatabaseTestRunner.class)
public class LaunchMainFromShellTest{
    
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void setUpClass() throws Exception {
        VariousDbTestHelper.createTable(Book.class);
        VariousDbTestHelper.createTable(BookBackup.class);
    }

    @Before
    public void setUp() throws Exception {
        VariousDbTestHelper.delete(Book.class);
        VariousDbTestHelper.delete(BookBackup.class);
    }

    @Ignore
    @Test(timeout=300000)
    public void testLaunchATinyBatch() throws Exception {
        File diConfig = Hereis.file(new File(temporaryFolder.getRoot(), "./batch-config.xml").getAbsolutePath());
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
            
            <!-- データベース接続管理ハンドラ -->
            <component
                name="dbConnectionManagementHandler" 
                class="nablarch.common.handler.DbConnectionManagementHandler">
            </component>

            <!-- 業務アクションディスパッチハンドラ -->
            <component class="nablarch.fw.handler.RequestPathJavaPackageMapping">
              <property name="basePackage" value="nablarch.fw.batch.sample"/>
              <property name="immediate" value="false" />
            </component>
            
            <!-- マルチスレッド実行制御ハンドラ -->
            <component class="nablarch.fw.handler.MultiThreadExecutionHandler">
              <property name="concurrentNumber" value="3" />
              <property name="terminationTimeout" value="600" />
            </component>
            
            <!-- データベース接続管理ハンドラ -->
            <component class="nablarch.common.handler.DbConnectionManagementHandler">
            </component>
            
            <!-- ループハンドラ -->
            <component class="nablarch.fw.handler.LoopHandler" />
            
            <!-- データリードハンドラ -->
            <component class="nablarch.fw.handler.DataReadHandler">
              <property name="maxCount" value="${maxExecutionCount}"/>
            </component>
          </list>
          <!-- ハンドラーキュー構成(END) -->
        </component-configuration>
        ************************************************************************/

        final String configFilePath = diConfig.getAbsolutePath();
        Process job = Hereis.shell(null, configFilePath);
        /*******************************************************************
          echo hoge fuga piyo
        | java
            -DmaxExecutionCount=100000
             nablarch.fw.launcher.Main
            -diConfig    file:${configFilePath}
            -requestPath DuplicateLineTask/req00001
            -userId      superHacker001
        ********************************************************************/
        BufferedReader stdout = new BufferedReader(
            new InputStreamReader(job.getInputStream())
        );
        String firstLine;
        while ((firstLine = stdout.readLine()) != null) {
            if (firstLine.indexOf("result:") != -1) {
                break;
            }
        }
        printStream(job.getInputStream());
        
        String secondLine;
        while ((secondLine = stdout.readLine()) != null) {
            if (secondLine.indexOf("result:") != -1) {
                break;
            }
        }
        assertEquals("result:[hoge, fuga, piyo]", firstLine);
        assertEquals("result:[hoge, fuga, piyo]", secondLine);
        
        job.waitFor();
        assertEquals(0, job.exitValue());
    }
    
    
    /**
     * テキストファイル(標準入力経由)のデータをデータベースに登録するバッチ。
     */
    @Test(timeout=300000)
    public void testFileToDbBatch() throws Exception {
        File diConfig = Hereis.file(new File(temporaryFolder.getRoot(), "./batch-config.xml").getAbsolutePath());
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
            
            <!-- 業務アクションディスパッチハンドラ -->
            <component class="nablarch.fw.handler.RequestPathJavaPackageMapping">
              <property name="basePackage" value="nablarch.fw.batch.sample"/>
              <property name="immediate" value="false" />
            </component>
             
            <!-- マルチスレッド制御ハンドラ -->
            <component class="nablarch.fw.handler.MultiThreadExecutionHandler" />
            
            <!-- データベース接続管理ハンドラ -->
            <component
                name="dbConnectionManagementHandler" 
                class="nablarch.common.handler.DbConnectionManagementHandler">
            </component>
            
            <!-- ループハンドラ -->
            <component class="nablarch.fw.handler.LoopHandler" />

            <!-- データリードハンドラ -->
            <component class="nablarch.fw.handler.DataReadHandler">
              <property name="maxCount" value="${maxExecutionCount}"/>
            </component>
          </list>
          <!-- ハンドラーキュー構成(END) -->
        </component-configuration>
        ************************************************************************/
        
        File inputData = Hereis.file("./inputData.txt");
        /*************************************************************************
        Learning the vi and vim Editors, OReilly,         Robbins Hanneah and Lamb
        Programming with POSIX Threads,  Addison-Wesley,  David R. Butenhof
        HACKING (2nd ed),                no starch press, Jon Erickson
        **************************************************************************/

        final String configFilePath = diConfig.getAbsolutePath();
        Process job = Hereis.shell(null, configFilePath);
        /***********************************************************************
        java 
            -DmaxExecutionCount=100000
             nablarch.fw.launcher.Main
            -diConfig    file:${configFilePath}
            -requestPath RegisteringBookDataTask/req9999
            -userId      user7777l
            <    inputData.txt
            2>&1 execution.log
        ***********************************************************************/
        printStream(job.getInputStream());
        job.waitFor();
        assertEquals(0, job.exitValue());
        
        Iterator<Book> records = VariousDbTestHelper.findAll(Book.class, "title").iterator();

        assertTrue(records.hasNext());
        Book record = records.next();
        assertEquals("HACKING (2nd ed)", record.title);
        assertEquals("Jon Erickson",     record.authors);
        
        assertTrue(records.hasNext());
        record = records.next();
        assertEquals("Learning the vi and vim Editors", record.title);
        assertEquals("Robbins Hanneah and Lamb",        record.authors);

        assertTrue(records.hasNext());
        record = records.next();
        assertEquals("Programming with POSIX Threads", record.title);
        assertEquals("David R. Butenhof",              record.authors);
        
        assertFalse(records.hasNext());
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
    
    /**
     * データベース上のあるテーブル上にあるデータを
     * 同じデータベース上の別のテーブルに登録するバッチ。
     */
    @Test(timeout=300000)
    public void testDbToDbBatch() throws Exception {
        File diConfig = Hereis.file("./batch-config.xml");
        /***********************************************************************
        <?xml version="1.0" encoding="UTF-8"?>
        <component-configuration
          xmlns = "http://tis.co.jp/nablarch/component-configuration">
          
          <!-- データベース接続構成 -->
          <import file="db-default.xml"/>
          
          <!-- ハンドラーキュー構成 -->
          <list name="handlerQueue">
            <!-- バッチ終了コード指定ハンドラ -->
            <component class="nablarch.fw.handler.StatusCodeConvertHandler" />
          
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
            
            <!-- データベース接続管理ハンドラ(データベースリーダ用) -->
            <component class="nablarch.common.handler.DbConnectionManagementHandler" />

            <!-- 業務アクションディスパッチハンドラ -->
            <component class="nablarch.fw.handler.RequestPathJavaPackageMapping">
              <property name="basePackage" value="nablarch.fw.batch.sample"/>
              <property name="immediate" value="false" />
            </component>
            
            <!-- マルチスレッド実行制御ハンドラ -->
            <component class="nablarch.fw.handler.MultiThreadExecutionHandler">
              <property name="concurrentNumber" value="3" />
              <property name="terminationTimeout" value="600" />
            </component>
            
            <!-- データベース接続管理ハンドラ(業務処理用) -->
            <component class="nablarch.common.handler.DbConnectionManagementHandler" />
            
            <!-- ループハンドラ -->
            <component class="nablarch.fw.handler.LoopHandler" />
            
            <!-- データリードハンドラ -->
            <component class="nablarch.fw.handler.DataReadHandler">
              <property name="maxCount" value="${maxExecutionCount}"/>
            </component>
          </list>
          <!-- ハンドラーキュー構成(END) -->
        </component-configuration>
        ************************************************************************/
        
        // -------------- 正常実行 -------------------------

        VariousDbTestHelper.delete(BookBackup.class);
        VariousDbTestHelper.setUpTable(
                new Book("Learning the vi and vim Editors", "OReilly", "Robbins Hanneah and Lamb"),
                new Book("Programming with POSIX Threads", "Addison-Wesley", "David R. Butenhof"),
                new Book("HACKING (2nd ed)", "no starch press", "Jon Erickson"),
                new Book("Learning the vi and vim Editors2", "OReilly", "Robbins Hanneah and Lamb"),
                new Book("Programming with POSIX Threads2", "Addison-Wesley", "David R. Butenhof"),
                new Book("HACKING (3rd ed)", "no starch press", "Jon Erickson"));

        Process job = Hereis.shell(null);
        /***********************************************************************
        java 
            -DmaxExecutionCount=100000
             nablarch.fw.launcher.Main
            -diConfig    file:./batch-config.xml
            -requestPath ReplicateBookDataTask/req9999
            -userId      user7777
            2>&1 /execution.log
        ***********************************************************************/
        printStream(job.getInputStream());
        job.waitFor();
        assertEquals(0, job.exitValue());
        
        List<BookBackup> records = VariousDbTestHelper.findAll(BookBackup.class, "title");
        
        assertEquals(6, records.size());
        assertEquals("HACKING (2nd ed)", records.get(0).title);
        assertEquals("Jon Erickson", records.get(0).authors);
        assertEquals("user7777", records.get(0).lastUpdateUser);
        assertEquals("HACKING (3rd ed)", records.get(1).title);
        assertEquals("Jon Erickson", records.get(1).authors);
        assertEquals("user7777", records.get(1).lastUpdateUser);
        assertEquals("Learning the vi and vim Editors", records.get(2).title);
        assertEquals("Robbins Hanneah and Lamb", records.get(2).authors);
        assertEquals("user7777", records.get(2).lastUpdateUser);
        assertEquals("Learning the vi and vim Editors2", records.get(3).title);
        assertEquals("Robbins Hanneah and Lamb", records.get(3).authors);
        assertEquals("user7777", records.get(3).lastUpdateUser);
        assertEquals("Programming with POSIX Threads", records.get(4).title);
        assertEquals("David R. Butenhof", records.get(4).authors);
        assertEquals("user7777", records.get(4).lastUpdateUser);
        assertEquals("Programming with POSIX Threads2", records.get(5).title);
        assertEquals("David R. Butenhof", records.get(5).authors);
        assertEquals("user7777", records.get(5).lastUpdateUser);
        
        // ------業務アクションが4回目で実行時例外を送出して停止する場合。---------
        // エラー発生時に実行していた業務処理は正常終了しコミットされるが、
        // それ以降の処理は行わずにFatalログを出力した上でエラー終了する。
        // (終了コードは20)
        //
        VariousDbTestHelper.delete(BookBackup.class);
        VariousDbTestHelper.setUpTable(
                new Book("Learning the vi and vim Editors", "OReilly", "Robbins Hanneah and Lamb"),
                new Book("Programming with POSIX Threads", "Addison-Wesley", "David R. Butenhof"),
                new Book("HACKING (2nd ed)", "no starch press", "Jon Erickson"),
                new Book("Learning the vi and vim Editors2", "OReilly", "Robbins Hanneah and Lamb"),
                new Book("Programming with POSIX Threads2", "Addison-Wesley", "David R. Butenhof"),
                new Book("HACKING (3rd ed)", "no starch press", "Jon Erickson"));
        
        job = Hereis.shell(null);
        /***********************************************************************
        java 
            -DmaxExecutionCount=100000
             nablarch.fw.launcher.Main
            -diConfig      file:./batch-config.xml
            -requestPath   ReplicateBookDataTask/reqXXXX
            -errorOnHandle 4
            -userId        user7777
            2>&1 /execution.log
        ***********************************************************************/
        printStream(job.getInputStream());
        job.waitFor();
        assertEquals(20, job.exitValue()); // 未捕捉実行時例外が発生した場合の終了コード
        
        records = VariousDbTestHelper.findAll(BookBackup.class, "title");
        assertTrue(3 <= records.size()); // 強制停止したため、3件以下しか処理されない。
        
        
        // ---- 指定したリクエストIDに対応する業務処理が存在しなかった場合。 -----
        // ディスパッチャでResult.NotFoundが送出される。
        // その結果、業務処理は一件も実行されずにFatalログを出力した上でエラー終了する。
        // (終了コードは12)
        VariousDbTestHelper.delete(BookBackup.class);
        VariousDbTestHelper.setUpTable(
                new Book("Learning the vi and vim Editors", "OReilly", "Robbins Hanneah and Lamb"),
                new Book("Programming with POSIX Threads", "Addison-Wesley", "David R. Butenhof"),
                new Book("HACKING (2nd ed)", "no starch press", "Jon Erickson"),
                new Book("Learning the vi and vim Editors2", "OReilly", "Robbins Hanneah and Lamb"),
                new Book("Programming with POSIX Threads2", "Addison-Wesley", "David R. Butenhof"),
                new Book("HACKING (3rd ed)", "no starch press", "Jon Erickson"));
        
        job = Hereis.shell(null);
        /***********************************************************************
        java 
            -DmaxExecutionCount=100000
             nablarch.fw.launcher.Main
            -diConfig    file:./batch-config.xml
            -requestPath UnknownAction/reqXXXX
            -errorOnInit true
            -userId      user7777
            2>&1 /execution.log
        ***********************************************************************/
        printStream(job.getInputStream());
        job.waitFor();
        assertEquals(13, job.exitValue()); // 指定したリクエストIDに対応する業務処理が存在しなかった場合の終了コード
        
        records = VariousDbTestHelper.findAll(BookBackup.class, "title");
        assertEquals(0, records.size()); // 初期処理中に強制停止されたため、本処理は実行されない。
        
        // ------業務アクションが4回目で業務例外を送出して停止する場合。---------
        // エラー発生時に実行していた業務処理は正常終了しコミットされるが、
        // それ以降の処理は行わずにFatalログを出力した上でエラー終了(終了コード20)する。
        //
        VariousDbTestHelper.delete(BookBackup.class);
        VariousDbTestHelper.setUpTable(
                new Book("Learning the vi and vim Editors", "OReilly", "Robbins Hanneah and Lamb"),
                new Book("Programming with POSIX Threads", "Addison-Wesley", "David R. Butenhof"),
                new Book("HACKING (2nd ed)", "no starch press", "Jon Erickson"),
                new Book("Learning the vi and vim Editors2", "OReilly", "Robbins Hanneah and Lamb"),
                new Book("Programming with POSIX Threads2", "Addison-Wesley", "David R. Butenhof"),
                new Book("HACKING (3rd ed)", "no starch press", "Jon Erickson"));
        
        job = Hereis.shell(null);
        /***********************************************************************
        java 
            -DmaxExecutionCount=100000
             nablarch.fw.launcher.Main
            -diConfig      file:./batch-config.xml
            -requestPath   ReplicateBookDataTask/reqXXXX
            -errorOnHandle 4
            -userId        user7777
            2>&1 /execution.log
        ***********************************************************************/
        printStream(job.getInputStream());
        job.waitFor();
        assertEquals(20, job.exitValue()); // 未捕捉の実行時例外が発生した場合の終了コード
        
        records = VariousDbTestHelper.findAll(BookBackup.class, "title");
        assertTrue(3 <= records.size()); // 強制停止したため、3件以下しか処理されない。
        
        // ----- 初期処理内で入力形式エラー(BadRequest)を送出した場合 -----------
        // 業務処理は一件も実行されずにFatalログを出力した上でエラー終了(終了コード)する。
        //
        VariousDbTestHelper.delete(BookBackup.class);
        VariousDbTestHelper.setUpTable(
                new Book("Learning the vi and vim Editors", "OReilly", "Robbins Hanneah and Lamb"),
                new Book("Programming with POSIX Threads", "Addison-Wesley", "David R. Butenhof"),
                new Book("HACKING (2nd ed)", "no starch press", "Jon Erickson"),
                new Book("Learning the vi and vim Editors2", "OReilly", "Robbins Hanneah and Lamb"),
                new Book("Programming with POSIX Threads2", "Addison-Wesley", "David R. Butenhof"),
                new Book("HACKING (3rd ed)", "no starch press", "Jon Erickson"));
        
        job = Hereis.shell(null);
        /***********************************************************************
        java 
            -DmaxExecutionCount=100000
             nablarch.fw.launcher.Main
            -diConfig    file:./batch-config.xml
            -requestPath ReplicateBookDataTask/reqXXXX
            -errorOnInit true
            -userId      user7777
            2>&1 /execution.log
        ***********************************************************************/
        printStream(job.getInputStream());
        job.waitFor();
        assertEquals(10, job.exitValue()); // Result.BadRequestが発生した場合の
                                           // 終了コード
        
        records = VariousDbTestHelper.findAll(BookBackup.class, "title");
        assertEquals(0, records.size()); // 初期処理中に強制停止されたため、本処理は実行されない。

        
        // ----- 終端処理内で整合性エラー(Result.Conflicted)が発生した場合 -------
        // 業務処理は全件正常終了しコミットされる。
        // Fatalログを出力した上でエラー終了(終了コード127)する。
        //
        VariousDbTestHelper.delete(BookBackup.class);
        VariousDbTestHelper.setUpTable(
                new Book("Learning the vi and vim Editors", "OReilly", "Robbins Hanneah and Lamb"),
                new Book("Programming with POSIX Threads", "Addison-Wesley", "David R. Butenhof"),
                new Book("HACKING (2nd ed)", "no starch press", "Jon Erickson"),
                new Book("Learning the vi and vim Editors2", "OReilly", "Robbins Hanneah and Lamb"),
                new Book("Programming with POSIX Threads2", "Addison-Wesley", "David R. Butenhof"),
                new Book("HACKING (3rd ed)", "no starch press", "Jon Erickson"));
        
        job = Hereis.shell(null);
        /***********************************************************************
        java 
            -DmaxExecutionCount=100000
             nablarch.fw.launcher.Main
            -diConfig    file:./batch-config.xml
            -requestPath ReplicateBookDataTask/reqXXXX
            -errorOnEnd  true
            -userId      user7777
            2>&1 /execution.log
        ***********************************************************************/
        printStream(job.getInputStream());
        job.waitFor();
        assertEquals(14, job.exitValue()); // 整合性エラーが発生した場合の終了コード
        
        records = VariousDbTestHelper.findAll(BookBackup.class, "title");
        assertEquals(6, records.size()); // 本処理自体は正常終了している。
    }
}
