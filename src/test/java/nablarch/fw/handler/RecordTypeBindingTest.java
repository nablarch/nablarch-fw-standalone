package nablarch.fw.handler;

import nablarch.core.ThreadContext;
import nablarch.core.dataformat.DataRecord;
import nablarch.core.dataformat.DataRecordFormatter;
import nablarch.core.dataformat.FormatterFactory;
import nablarch.core.util.Builder;
import nablarch.core.util.FilePathSetting;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Handler;
import nablarch.fw.Result;
import nablarch.fw.reader.FileDataReader;
import nablarch.test.support.tool.Hereis;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * 固定長ファイルリーダのレコードタイプベースのディスパッチ機能のテスト。
 * 
 * @author Iwauo Tajima
 */
public class RecordTypeBindingTest {

    @BeforeClass
    public static void setUpClass() {
        FormatterFactory.getInstance().setCacheLayoutFileDefinition(false);
    }
    
     
    /**
     * 基本機能のテスト
     */
    @Test
    public void testBasicUsage() {
        final List<String> activities = new ArrayList<String>();
        final List<String> readData   = new ArrayList<String>();
        
        class TestAction {
            public Result doHeader(DataRecord record, ExecutionContext ctx) {
                activities.add("Header: dataKbn = " + record.get("dataKbn"));
                readData.add("date = " + record.get("date"));
                return new Result.Success();
            }
            
            public Result doDataWithEDI(DataRecord record, ExecutionContext ctx) {
                activities.add("DataWithEDI: dataKbn = " + record.get("dataKbn"));
                readData.add("ediInfo = " + record.get("ediInfo"));
                return new Result.Success();
            }
            
            public Result doDataWithoutEDI(DataRecord record, ExecutionContext ctx) {
                activities.add("DataWithoutEDI: dataKbn = " + record.get("dataKbn"));
                readData.add("userCode1 = " + record.get("userCode1"));
                return new Result.Success();
            }
            
            public Result doTrailer(DataRecord record, ExecutionContext ctx) {
                activities.add("Trailer: dataKbn = " + record.get("dataKbn"));
                readData.add("totalRecords = " + record.get("totalRecords"));
                return new Result.Success();
            }
            
            public Result doEnding(DataRecord record, ExecutionContext ctx) {
                activities.add("Ending: dataKbn = " + record.get("dataKbn"));
                readData.add("おしまい。");
                return new Result.Success();
            }
        }
        
        class DumbLoopHandler implements Handler<Object, Object> {
            public Object handle(Object data, ExecutionContext context) {
                while (context.hasNextData()) {
                    new ExecutionContext(context).handleNext(data);
                }
                return new Result.Success();
            }
        }
        
        FilePathSetting.getInstance()
                .addBasePathSetting("input",  "file:./")
                .addBasePathSetting("format", "file:./")
                .addFileExtensions("input", "dat")
                .addFileExtensions("format", "fmt");
        
        ExecutionContext ctx = new ExecutionContext()
            .setDataReader(new FileDataReader()
                              .setLayoutFile("formatFile")
                              .setDataFile("dataFile"))
            .setMethodBinder(new RecordTypeBinding.Binder())
            .addHandler(new DumbLoopHandler())
            .addHandler(new DataReadHandler())
            .addHandler(new TestAction());
        
        ThreadContext.setRequestId("dummyRequestId"); // for ResumePointManager#getInstance method
        
        ctx.handleNext(null);
        
        assertEquals(5, activities.size());
        assertEquals("Header: dataKbn = 1",         activities.get(0));
        assertEquals("DataWithEDI: dataKbn = 2",    activities.get(1));
        assertEquals("DataWithoutEDI: dataKbn = 2", activities.get(2));
        assertEquals("Trailer: dataKbn = 8",        activities.get(3));
        assertEquals("Ending: dataKbn = 9",         activities.get(4));
        
        assertEquals(5,                        readData.size());
        assertEquals("date = 0831",            readData.get(0));
        assertEquals("ediInfo = ﾀｸｼｰﾀﾞｲｷﾝﾃﾞｽ", readData.get(1));
        assertEquals("userCode1 = ﾀｼﾞﾏｲﾜｳｵ",   readData.get(2));
        assertEquals("totalRecords = 5",       readData.get(3));
        assertEquals("おしまい。",              readData.get(4));
    }
    private DataRecordFormatter createFormatter(String filePath) {
        return new FormatterFactory().setCacheLayoutFileDefinition(false).createFormatter(new File(filePath));
    }

    @Before
    public void setUp() throws Exception {



        File formatFile = Hereis.file("./formatFile.fmt");
        /***********************************************************
        #
        # 共通定義部分 
        #
        file-type:         "Fixed"         
        text-encoding:    "ms932" # 文字列型フィールドの文字エンコーディング
        record-length:     120    # 各レコードの長さ

        [Classifier] # レコードタイプ識別フィールド定義
        1   dataKbn   X(1)   # データ区分
                               #    1: ヘッダー、2: データレコード
                               #    8: トレーラー、9: エンドレコード                    
        113 withEdi   X(1)   # EDI情報使用フラグ
                               #    Y: EDIあり、N: なし


        [Header]  # ヘッダーレコード
          dataKbn = "1"
        1   dataKbn     X(1)  "1"      # データ区分
        2   processCode X(2)           # 業務種別コード
        4   codeKbn     X(1)           # コード区分
        5   itakuCode   X(10)          # 振込元の委託者コード
        15  itakuName   X(40)          # 振込元の委託者名
        55  date        X(4)           # 振込指定日
        59 ?unused      X(62) pad("0") # (未使用領域)


        [DataWithEDI] # データレコード (EDI情報あり)
          dataKbn  = "2"
          withEdi  = "Y"
        1    dataKbn       X(1)  "2"      # データ区分
        2    FIcode        X(4)           # 振込先金融機関コード
        6    FIname        X(15)          # 振込先金融機関名称
        21   officeCode    X(3)           # 振込先営業所コード
        24   officeName    X(15)          # 振込先営業所名
        39  ?tegataNum     X(4)  "9999"   # (手形交換所番号:未使用)
        43   syumoku       X(1)           # 預金種目
        44   accountNum    X(7)           # 口座番号
        51   recipientName X(30)          # 受取人名
        81   amount        X(10)          # 振込金額
        91   isNew         X(1)           # 新規コード
        92   ediInfo       X(20)          # EDI情報
        112  transferType  X(1)           # 振込区分
        113  withEdi       X(1)  "Y"      # EDI情報使用フラグ
        114 ?unused        X(7)  pad("0") # (未使用領域)            


        [DataWithoutEDI] < [DataWithEDI]  # データレコード (EDI情報なし)
          dataKbn = "2"                   #   EDI情報なしの場合、振込人情報を
          withEdi = "N"                   #   EDI情報の代わりに付記する。
        92   userCode1     X(10)          # ユーザコード1
        102  userCode2     X(10)          # ユーザコード2
        113  withEdi       X(1)  "N"      # EDI情報使用フラグ

        
        
        [Trailer] # トレーラーレコード (区分コード値:8)
          dataKbn =  "8"
        1   dataKbn      X(1)   "8"      # データ区分
        2   totalRecords X(6)            # 総レコード件数
        8  ?unused       X(113) pad("0") # 未使用領域
        
        
        [Ending] # エンドレコード (区分コード値:9)
          dataKbn = "9"
        1  dataKbn  X(1)   "9"       # データ区分
        2 ?unused   X(119) pad("0")  # 未使用領域
                                       
        ************************************************************/
        formatFile.deleteOnExit();
        
        
        // #1 ヘッダレコード(EDIあり)
        String testdata = Hereis.string().replace(Builder.LS, "");
        //         1         2         3         4         5
        //12345678901234567890123456789012345678901234567890
          /*************************************************
          1660FSE302929 ﾀｼﾞﾏｺﾝﾂｪﾙﾝ ﾛﾝﾄﾞﾝｼﾃﾝ                 
              0831000000000000000000000000000000000000000000
          00000000000000000000*/
        //12345678901234567890123456789012345678901234567890
        
        //         1         2         3         4         5
        // #2 データレコード(EDIあり)
        testdata += Hereis.string().replaceAll(Builder.LS, "");
        //         1         2         3         4         5
        //12345678901234567890123456789012345678901234567890
          /*************************************************
          21234FSEｷﾞﾝｺｳ       ﾏｺ1ﾏﾂﾄﾞｺｶﾞﾈﾊﾗｼﾃﾝ  999917778888
          ﾀﾞｲｱﾅ ﾛｽ                      3020      Nﾀｸｼｰﾀﾞｲｷﾝ
          ﾃﾞｽ        4Y0000000*/                            
        //12345678901234567890123456789012345678901234567890     
        //         1         2         3         4         5
        
        // #3 データレコード(EDIなし)
        testdata += Hereis.string().replaceAll(Builder.LS, "");
        //         1         2         3         4         5
        //12345678901234567890123456789012345678901234567890
          /*************************************************
          21234FSEｷﾞﾝｺｳ       ﾏｺ1ﾏﾂﾄﾞｺｶﾞﾈﾊﾗｼﾃﾝ  999917778888
          ﾀﾞｲｱﾅ ﾛｽ                      3020      Nﾀｼﾞﾏｲﾜｳｵ 
           302929    4N0000000*/                            
        //12345678901234567890123456789012345678901234567890     
        //         1         2         3         4         5
        
        // #4 トレーラーレコード
        testdata += Hereis.string().replaceAll(Builder.LS, "");
        //         1         2         3         4         5
        //12345678901234567890123456789012345678901234567890
          /*************************************************
          85     0000000000000000000000000000000000000000000
          00000000000000000000000000000000000000000000000000
          00000000000000000000*/                           
        //12345678901234567890123456789012345678901234567890     
        //         1         2         3         4         5
        
        // #5 エンドレコード
        testdata += Hereis.string().replaceAll(Builder.LS, "");
        //         1         2         3         4         5
        //12345678901234567890123456789012345678901234567890
          /*************************************************
          90000000000000000000000000000000000000000000000000
          00000000000000000000000000000000000000000000000000
          00000000000000000000*/                           
        //12345678901234567890123456789012345678901234567890     
        //         1         2         3         4         5
        
        File dataFile = new File("./dataFile.dat");
        dataFile.deleteOnExit();
        new FileOutputStream(dataFile, false).write(testdata.getBytes("ms932"));
    }
}
