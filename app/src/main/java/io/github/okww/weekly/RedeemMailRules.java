package io.github.okww.weekly;
import static io.github.okww.weekly.GardenRules.*;

/** Reuse calibrated mail rules only; redemption never claims activity or battle-pass rewards. */
final class RedeemMailRules {
    private final DailyRewards rewards=new DailyRewards();
    RedeemMailRules(){rewards.start();rewards.applied(new Action(Kind.BACK,"daily-rewards-to-mail","仅进入邮件状态",0,0));}
    boolean verified(){return "verified".equals(rewards.status().get("mail"));}
    Action next(Screen s){
        rewards.frameStarted();
        if(s.has("确认删除|是否删除|确认购买|确认支付|兑换波片|补充波片"))return Action.stop("邮件页面含资源或删除确认，未继续");
        boolean terminal=AccountSwitchVision.terminal(s);
        if(!rewards.stage().equals("MAIL")){
            if(terminal||worldHud(s))return new Action(Kind.DONE,"redeem-mail-done",verified()?"邮件附件已核验":"邮件附件未核验",0,0);
            if(DailyRewards.title(s,"邮件"))return new Action(Kind.BACK,"redeem-mail-return","离开邮件，不进入其他奖励",0,0);
            return Action.waitFor("redeem-mail-return","等待返回已知终端");
        }
        Action modal=rewards.modal(s);if(modal!=null)return modal;
        if(worldHud(s)){Action a=new DailyRules().next(s);if(a.scene.equals("daily-world-terminal"))return a;}
        Action action=rewards.next(s,terminal);
        return action==null?Action.waitFor("redeem-mail-wait","等待本轮邮件界面"):action;
    }
    void applied(Action action){rewards.applied(action);}
}
