package io.github.okww.weekly;

import android.content.SharedPreferences;

/** One persisted switch; tasks must explicitly support isolated replay before using it. */
final class TestEnvironment {
    static final String KEY="testEnvironment";
    static boolean enabled(SharedPreferences settings){return settings.getBoolean(KEY,false);}
    static boolean replay(SharedPreferences settings,AutomationTask task){return enabled(settings)&&task==AutomationTask.REDEEM;}
    static boolean pasteProbeAllowed(SharedPreferences settings,boolean running){return enabled(settings)&&!running;}
    static void requireSupported(SharedPreferences settings,boolean automate,AutomationTask task){
        if(automate&&enabled(settings)&&task!=AutomationTask.REDEEM)
            throw new IllegalStateException("此任务尚未适配测试环境；请关闭测试环境后正常运行，真实记录未清空");
    }
}
