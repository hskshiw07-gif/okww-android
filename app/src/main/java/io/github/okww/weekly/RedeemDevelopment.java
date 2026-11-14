package io.github.okww.weekly;

import static io.github.okww.weekly.GardenRules.*;

/** User-requested acceptance replay; real redemptions and their history remain untouched. */
final class RedeemDevelopment {
    static void requireNoPending(RedeemLedger real,RedeemLedger development){
        for(RedeemLedger ledger:new RedeemLedger[]{real,development})for(RedeemLedger.Entry entry:ledger.entries())
            if(entry.status==RedeemLedger.Status.PENDING)throw new IllegalStateException("开发重跑前存在待核验提交，保留游戏现场与两份账本，未返回或重试");
    }
    static String testCode(long now,RedeemCodes catalog,RedeemLedger ledger){
        if(now<0)throw new IllegalArgumentException("测试码时钟异常");
        String code="WWTEST"+now;
        if(catalog.codes.containsKey(code)||ledger.entries().stream().anyMatch(e->e.code.equals(code)))
            throw new IllegalStateException("本轮测试码重复，未重复提交");
        return RedeemCodes.normalize(code);
    }
    static Action restart(Screen s){
        if(s.automationPanelVisible()||!(s.aspect>2.18f&&s.aspect<2.26f))
            return Action.waitFor("redeem-replay-prepare","等待已适配横屏且浮窗隐藏");
        if(s.has("确认删除|是否删除|确认购买|确认支付|补充波片|兑换波片|验证码|输入密码"))
            return Action.stop("开发重跑遇到资源/验证弹框，保留现场");
        if(worldHud(s))return new Action(Kind.DONE,"redeem-replay-world","开发重跑已回到大世界，重新核验UID",0,0);
        if(RedeemRules.dialog(s)||RedeemRules.settings(s)||DailyRewards.title(s,"邮件")||AccountSwitchVision.terminal(s))
            return new Action(Kind.BACK,"redeem-replay-back","仅退出已识别的兑换/设置/邮件/终端，重新开始",0,0);
        return Action.waitFor("redeem-replay-prepare","等待已知页面，不盲退或清除兑换记录");
    }
}
