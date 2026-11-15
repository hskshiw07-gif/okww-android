package io.github.okww.weekly;

import android.content.SharedPreferences;

/** One persisted switch; each supported task defines its replay behavior. */
final class TestEnvironment {
    static final String KEY="testEnvironment";
    static final String DAILY_DESCRIPTION="每日测试：忽略活跃度（包括已满100），直接继续无音区副本流程。仍按当前普通波片冻结本轮额度，真实消耗计入原账本，每账号每游戏日最多240；不足60跳过副本，不补充、不兑换、不购买。副本结束后继续检查免费奖励。";
    static boolean enabled(SharedPreferences settings){return settings.getBoolean(KEY,false);}
    static boolean replay(SharedPreferences settings,AutomationTask task){return enabled(settings)&&task==AutomationTask.REDEEM;}
    static boolean dailyReplay(SharedPreferences settings,AutomationTask task){return enabled(settings)&&task==AutomationTask.DAILY;}
    static boolean pasteProbeAllowed(SharedPreferences settings,boolean running){return enabled(settings)&&!running;}
    static void requireSupported(SharedPreferences settings,boolean automate,AutomationTask task){
        if(automate&&enabled(settings)&&task!=AutomationTask.REDEEM&&task!=AutomationTask.DAILY)
            throw new IllegalStateException("此任务尚未适配测试环境；请关闭测试环境后正常运行，真实记录未清空");
    }
}
