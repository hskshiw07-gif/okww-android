package io.github.okww.weekly;

import java.io.IOException;
import static io.github.okww.weekly.GardenRules.*;

/** Reconcile an existing reservation from an explicit result page, never resume or resubmit its claim. */
final class DailyPendingSettlement {
    private final DailyStaminaLedger ledger;
    private final int cost,expected,confirmedBefore;
    private long lastFrame=-1,lastNow=-1;
    private int matchingFrames;
    private boolean reconciled,exitSubmitted,finished;
    private Action stopped,exitOffer;

    DailyPendingSettlement(DailyStaminaLedger ledger) {
        if(ledger==null)throw new IllegalArgumentException("核销缺少原始账号账本");
        this.ledger=ledger;cost=ledger.reserved();expected=ledger.expectedAfter();confirmedBefore=ledger.confirmed();
    }

    Action next(Screen screen,String confirmedUid,long capturedAt,long now) {
        if(cost==0||finished)return null;
        if(stopped!=null)return stopped;
        if(Thread.currentThread().isInterrupted())return stop("核销已取消，保留账本，不发送游戏操作");
        if(now<0||now<lastNow||!ledger.day().equals(DailyStaminaLedger.gameDay(now)))
            return stop("核销时钟倒退或游戏日变化，保留原账本");
        lastNow=now;
        if(reconciled ? ledger.reserved()!=0||ledger.confirmed()!=confirmedBefore+cost
                : ledger.reserved()!=cost||ledger.expectedAfter()!=expected||ledger.confirmed()!=confirmedBefore)
            return stop("核销期间原账本发生变化，停止而非重置或重复确认");
        boolean fresh=capturedAt>=0&&capturedAt>lastFrame&&capturedAt<=now&&now-capturedAt<=3500;
        boolean consecutive=lastFrame>=0&&capturedAt-lastFrame<=3500;
        if(capturedAt>=0&&capturedAt>lastFrame&&capturedAt<=now)lastFrame=capturedAt;
        if(!fresh)return waitFor("等待递增的新鲜结果帧，不重用截图核销");
        if(screen==null||!(screen.aspect>2.18f&&screen.aspect<2.26f)||screen.automationPanelVisible())
            return waitFor("等待已标定布局且无浮窗的结果画面");
        String uid=AccountDailyConfig.detectUid(screen);
        if(!ledger.uid().equals(confirmedUid)||!ledger.uid().equals(uid))
            return waitFor("等待本帧与已核验账号均匹配原账本，不沿用旧账号证据");
        if(screen.has("单倍领取|双倍领取|领取奖励|补充波片|兑换|购买|确认|取消|复苏|复活|暂停|教程"))
            return waitFor("当前仍是领取或其他弹窗，仅保留预留，不重提或盲退");
        boolean result=screen.hasOne("挑战成功",.32f,.12f,.68f,.42f)
                &&screen.hasOne("退出副本|离开副本",.22f,.72f,.52f,.94f)
                &&screen.hasOne("重新挑战|再次挑战",.50f,.72f,.78f,.94f);
        if(!result) {
            boolean world=worldHud(screen)&&!screen.has("挑战成功|挑战失败|退出副本|离开副本|重新挑战|再次挑战|清理无.*区")
                    &&!screen.hasOne("[^0-9]{0,2}[0-9]{2}:[0-9]{2}:[0-9]{2}",.42f,.08f,.59f,.18f);
            if(world) {
                if(!reconciled)return stop("已回大世界但结果余额尚未核销，保留预留，不按余额猜测完成");
                finished=true;return null;
            }
            return waitFor("等待同账号完整挑战成功与退出/重试组合，不操作未知页面");
        }
        if(!reconciled) {
            Integer balance=DailyTacet.balance(screen);
            if(balance==null||balance!=expected)return waitFor("等待独立识别的结果余额精确匹配原预留，不放宽差额");
            matchingFrames=consecutive?matchingFrames+1:1;
            if(matchingFrames<2)return Action.waitFor("daily-pending-settlement","再次核验同账号结果余额，未释放预留");
            try {ledger.confirm(balance);}
            catch(IOException error){return stop("核销落盘或写后核验失败，停止并保留账本现场");}
            reconciled=true;
        }
        if(exitSubmitted)return Action.waitFor("daily-pending-exit-wait","已发送退出，等待大世界；不再次挑战或领取");
        exitOffer=Action.tap("daily-pending-exit",screen.one("退出副本|离开副本",.22f,.72f,.52f,.94f));
        return exitOffer;
    }

    void applied(Action action) {
        if(!action.scene.equals("daily-pending-exit"))return;
        if(!reconciled||exitOffer==null||action.kind!=Kind.TAP||Float.compare(action.x,exitOffer.x)!=0
                ||Float.compare(action.y,exitOffer.y)!=0)throw new IllegalStateException("退出动作未匹配已核销结果页");
        exitSubmitted=true;exitOffer=null;
    }
    private Action waitFor(String reason) {
        matchingFrames=0;exitOffer=null;
        return Action.waitFor("daily-pending-settlement",reason);
    }
    private Action stop(String reason) {matchingFrames=0;exitOffer=null;stopped=Action.stop(reason);return stopped;}
}
