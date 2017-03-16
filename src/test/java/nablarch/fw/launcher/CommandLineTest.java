package nablarch.fw.launcher;

import junit.framework.TestCase;
import nablarch.fw.results.BadRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandLineTest extends TestCase {

    public void testParsing() {
        CommandLine cmd = new CommandLine(
            "-requestPath", "www.example.com/hoge"
          , "-userId"     , "someone"
          , "-diConfig"   , "file://./config.xml"
          , "-param1"     , "value1"
          , "-param2"     , "value2"
          , "arg1"
          , "arg2"
          , "--param3"     , "value3"
          , "arg3"
          , "--param4"
        );
        
        assertEquals(cmd.getRequestPath(), "www.example.com/hoge");
        
        Map<String, String> params = cmd.getParamMap();
        
        assertEquals(7, params.size());
        assertEquals("www.example.com/hoge", params.get("requestPath"));
        assertEquals("someone",              params.get("userId"));
        assertEquals("file://./config.xml",  params.get("diConfig"));
        assertEquals("value1",               params.get("param1"));
        assertEquals("value2",               params.get("param2"));
        assertEquals("value3",               params.get("param3"));
        assertEquals("",                     params.get("param4"));
        assertNull(params.get("unknown"));
        
        List<String> args = cmd.getArgs();
        assertEquals(3, args.size());
        assertEquals("arg1", args.get(0));
        assertEquals("arg2", args.get(1));
        assertEquals("arg3", args.get(2));
    }
    
    public void testThrowErrorWhenMandatoryProperiesWereNotSet() {
        
        try {
            new CommandLine(
            //  "-requestPath", "www.example.com/hoge"
                "-userId"     , "someone"
              , "-diConfig"   , "file://./config.xml"
              , "-param1"     , "value1"
            );
            fail();
        } catch(Exception e) {
            assertTrue(e instanceof BadRequest);
        }
        
        try {
            new CommandLine(
                "-requestPath", "www.example.com/hoge"
              , "-userId"     , "someone"
            //  , "-diConfig"   , "file://./config.xml"
              , "-param1"     , "value1"
            );
            fail();
        } catch(Exception e) {
            assertTrue(e instanceof BadRequest);
        }
        
        CommandLine cmd = new CommandLine(
            "-requestPath", "www.example.com/hoge"
          , "-userId"     , "someone"
          , "-diConfig"   , "file://./config.xml"
          , "-param1"     , "value1"
        );
        
        try {
            cmd.setParamMap(new HashMap<String, String>());
            fail();
        } catch(Exception e) {
            assertTrue(e instanceof BadRequest);
        }
    }
}
