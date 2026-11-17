package io.github.okww.weekly;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestEnvironmentTest {
    private SharedPreferences settings(Map<String,Boolean> values){
        return (SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.class},(proxy,method,args)->{
            if(!method.getName().equals("getBoolean"))throw new AssertionError(method.getName());
            assertEquals(TestEnvironment.KEY,args[0]);assertEquals(false,args[1]);
            return values.getOrDefault((String)args[0],(Boolean)args[1]);
        });
    }
    @Test public void freshOrUpgradedPreferencesDefaultToNormalAndCanSwitchBack(){
        Map<String,Boolean> values=new HashMap<>();SharedPreferences prefs=settings(values);
        assertFalse(TestEnvironment.enabled(prefs));assertFalse(TestEnvironment.replay(prefs,AutomationTask.REDEEM));
        values.put(TestEnvironment.KEY,true);assertTrue(TestEnvironment.enabled(prefs));assertTrue(TestEnvironment.replay(prefs,AutomationTask.REDEEM));
        values.put(TestEnvironment.KEY,false);assertFalse(TestEnvironment.enabled(prefs));assertFalse(TestEnvironment.replay(prefs,AutomationTask.REDEEM));
    }
    @Test public void unsupportedTestTasksNeverFallThroughToRealAutomation(){
        SharedPreferences on=settings(Map.of(TestEnvironment.KEY,true)),off=settings(Map.of());
        for(AutomationTask task:AutomationTask.values()){
            TestEnvironment.requireSupported(off,true,task);TestEnvironment.requireSupported(on,false,task);
            if(task==AutomationTask.REDEEM||task==AutomationTask.DAILY||task==AutomationTask.ECHO_FARM){
                TestEnvironment.requireSupported(on,true,task);
                assertEquals(task==AutomationTask.REDEEM,TestEnvironment.replay(on,task));
                assertEquals(task==AutomationTask.DAILY,TestEnvironment.dailyReplay(on,task));
                assertFalse(TestEnvironment.dailyReplay(off,task));
            }
            else{assertFalse(TestEnvironment.replay(on,task));assertThrows(IllegalStateException.class,()->TestEnvironment.requireSupported(on,true,task));}
        }
    }
    @Test public void clipboardProbeRequiresTestEnvironmentAndIdleTask(){
        SharedPreferences on=settings(Map.of(TestEnvironment.KEY,true)),off=settings(Map.of());
        assertTrue(TestEnvironment.pasteProbeAllowed(on,false));
        assertFalse(TestEnvironment.pasteProbeAllowed(on,true));
        assertFalse(TestEnvironment.pasteProbeAllowed(off,false));
        assertFalse(TestEnvironment.pasteProbeAllowed(off,true));
    }
}
