package io.github.okww.weekly;

import android.content.SharedPreferences;

/** One persisted switch; each supported task defines its replay behavior. */
final class TestEnvironment {
    static final String KEY="testEnvironment";
    static final String DAILY_DESCRIPTION="每日测试：忽略活跃度，直接进入无音区。普通波片够60时每次只按单倍领取，扣费计入原账本，每账号每游戏日最多240。开测时不足60则只测试战斗与领奖预览，取消并退出，不扣费。不补充、不兑换、不购买。";
    static boolean enabled(SharedPreferences settings){return settings.getBoolean(KEY,false);}
    static boolean replay(SharedPreferences settings,AutomationTask task){return enabled(settings)&&task==AutomationTask.REDEEM;}
    static boolean dailyReplay(SharedPreferences settings,AutomationTask task){return enabled(settings)&&task==AutomationTask.DAILY;}
    static boolean pasteProbeAllowed(SharedPreferences settings,boolean running){return enabled(settings)&&!running;}
    static void requireSupported(SharedPreferences settings,boolean automate,AutomationTask task){
        if(automate&&enabled(settings)&&task!=AutomationTask.REDEEM&&task!=AutomationTask.DAILY&&task!=AutomationTask.ECHO_FARM)
            throw new IllegalStateException("此任务尚未适配测试环境；请关闭测试环境后正常运行，真实记录未清空");
    }
}
