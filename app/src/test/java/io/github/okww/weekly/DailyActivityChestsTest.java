package io.github.okww.weekly;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.okww.weekly.GardenRules.*;
import static io.github.okww.weekly.ActivityRewardVision.State.*;

public class DailyActivityChestsTest {
    private static final String UID="000000000";
    private long image;
    private Text t(String s,float x,float y){return new Text(s,x-.005f,y-.005f,x+.005f,y+.005f);}
    private Screen page(int points){return new Screen(new ArrayList<>(Arrays.asList(t("活跃行迹",.08f,.06f),
            t("活跃度",.22f,.90f),t(""+points,.20f,.84f),t("特征码:"+UID,.91f,.98f))),new int[3],2400f/1080);}
    private ActivityRewardVision.State[] states(){return new ActivityRewardVision.State[]{AVAILABLE,AVAILABLE,AVAILABLE,AVAILABLE,AVAILABLE};}
    private ActivityRewardVision.Observation icons(int points,ActivityRewardVision.State[] states){
        return new ActivityRewardVision.Observation(UID,++image,points,states);
    }
    private Action next(DailyRewards r,int points,ActivityRewardVision.State[] states){
        r.frameStarted();return r.activity(page(points),points,icons(points,states));
    }
    @Test public void eachChestIsSubmittedOnceThenRequiresTwoClaimedFrames(){
        DailyRewards r=new DailyRewards();r.start();ActivityRewardVision.State[] states=states();int taps=0;
        for(int slot=0;slot<5;slot++){
            Action action=next(r,120,states);
            if(action.kind==Kind.WAIT)action=next(r,120,states);
            assertEquals("daily-activity-chest-"+slot,action.scene);assertEquals(ActivityRewardVision.tapX(slot),action.x,0);
            r.applied(action);taps++;
            for(int i=0;i<3;i++)assertEquals(Kind.WAIT,next(r,120,states).kind);
            states[slot]=CLAIMED;
            assertEquals(Kind.WAIT,next(r,120,states).kind);
            assertEquals(Kind.WAIT,next(r,120,states).kind);
        }
        assertEquals(5,taps);Action exit=next(r,120,states);
        assertEquals("daily-rewards-to-mail",exit.scene);assertEquals("verified",r.status().get("activity"));
        assertFalse(r.complete());
    }
    @Test public void claimingOneMayClaimAllButNeverClicksFiveBlindly(){
        DailyRewards r=new DailyRewards();r.start();ActivityRewardVision.State[] states=states();
        next(r,120,states);r.applied(next(r,120,states));Arrays.fill(states,CLAIMED);
        assertEquals(Kind.WAIT,next(r,120,states).kind);assertEquals(Kind.WAIT,next(r,120,states).kind);
        assertEquals("daily-rewards-to-mail",next(r,120,states).scene);
    }
    @Test public void unknownReplayAndSkippedFrameCannotCompleteThePair(){
        DailyRewards r=new DailyRewards();r.start();ActivityRewardVision.State[] states=states();
        assertEquals(Kind.WAIT,next(r,120,states).kind);
        r.frameStarted();assertEquals(Kind.WAIT,r.activity(page(120),120,new ActivityRewardVision.Observation(UID,image,120,states)).kind);
        assertEquals(Kind.WAIT,next(r,120,states).kind);
        r.frameStarted();assertEquals(Kind.WAIT,next(r,120,states).kind);
        states[4]=UNKNOWN;assertEquals(Kind.WAIT,next(r,120,states).kind);
        states[4]=AVAILABLE;assertEquals(Kind.WAIT,next(r,120,states).kind);
        assertEquals(Kind.TAP,next(r,120,states).kind);
    }
    @Test public void oldImageWrongAccountOrPointsCannotAuthorizeAChest(){
        DailyRewards r=new DailyRewards();r.start();ActivityRewardVision.State[] states=states();
        next(r,120,states);r.frameStarted();
        assertEquals(Kind.WAIT,r.activity(page(120),120,new ActivityRewardVision.Observation("123456789",++image,120,states)).kind);
        r.frameStarted();assertEquals(Kind.WAIT,r.activity(page(120),120,new ActivityRewardVision.Observation(UID,++image,60,states)).kind);
        assertEquals(Kind.WAIT,next(r,120,states).kind);assertEquals(Kind.TAP,next(r,120,states).kind);
    }
    @Test public void pendingTaskStillNeedsActivityIncreaseAndUnstartedBudgetDoesNotClaimIcons(){
        DailyRewards notStarted=new DailyRewards();notStarted.frameStarted();
        assertNull(notStarted.activity(page(120),120,icons(120,states())));
        DailyRewards r=new DailyRewards();r.start();Screen task=page(60);task.words.add(t("领取",.90f,.26f));
        r.frameStarted();Action tap=r.activity(task,60,icons(60,states()));assertEquals("daily-activity-claim",tap.scene);r.applied(tap);
        assertEquals(Kind.WAIT,next(r,60,states()).kind);
        assertEquals(Kind.WAIT,next(r,120,states()).kind);
        assertEquals(Kind.WAIT,next(r,120,states()).kind);
        assertEquals(Kind.TAP,next(r,120,states()).kind);
    }
    @Test public void receiptDismissalAloneDoesNotVerifyChestAndForgedTapIsRejected(){
        DailyRewards r=new DailyRewards();r.start();ActivityRewardVision.State[] states=states();
        next(r,120,states);Action tap=next(r,120,states);
        assertThrows(IllegalStateException.class,()->r.applied(new Action(Kind.TAP,tap.scene,"",.5f,.5f)));
        r.applied(tap);
        Screen receipt=new Screen(Arrays.asList(t("获得奖励",.5f,.3f),t("点击空白处关闭",.5f,.8f)),new int[3],2400f/1080);
        Action close=r.modal(receipt);assertEquals("daily-rewards-dismiss",close.scene);r.applied(close);
        assertEquals(Kind.WAIT,next(r,120,states).kind);assertNotEquals("verified",r.status().get("activity"));
    }
    @Test public void lockedRemainderBelow100IsPartialNotComplete(){
        DailyRewards r=new DailyRewards();r.start();
        ActivityRewardVision.State[] partial={CLAIMED,CLAIMED,CLAIMED,LOCKED,LOCKED};
        assertEquals("daily-rewards-to-mail",next(r,60,partial).scene);assertFalse(r.complete());
        assertNotEquals("verified",r.status().get("activity"));
    }
    @Test public void measuredMailIconAcceptsRecordedGuideOcrVariantButNotMissingAnchors(){
        for(String name:new String[]{"索拉指南","素拉指南"}){
            DailyRewards r=new DailyRewards();r.start();r.frameStarted();r.applied(r.activity(page(120),120));
            Screen terminal=new Screen(new ArrayList<>(Arrays.asList(t("令终端",.08f,.06f),t(name,.73f,.55f),
                    t("数据坞",.53f,.55f),t("联觉经验",.10f,.78f))),new int[3],2400f/1080);
            r.frameStarted();Action a=r.next(terminal,true);assertEquals("daily-open-mail",a.scene);
            assertEquals(1651f/2400,a.x,0);assertEquals(1025f/1080,a.y,0);
            terminal.words.remove(2);r.frameStarted();assertEquals("daily-mail-entry-missing",r.next(terminal,true).scene);
        }
    }
    private DailyRules rules(int spent)throws Exception{
        long now=java.time.Instant.parse("2026-11-11T08:00:00Z").toEpochMilli();
        DailyTacet tacet=new DailyTacet();tacet.attachLedger(new DailyStaminaLedger(new DailyStaminaLedger.Store(){
            String saved="1\n"+UID+"\n2026-11-11\n"+spent+":0:-1";
            public String read(){return saved;}public void write(String s){saved=s;}
        },UID,now));
        DailyRules rules=new DailyRules(tacet);rules.teamConfirmed();rules.observeUid(page(120));rules.observeUid(page(120));return rules;
    }
    @Test public void productionVisionDoesNotSkipBudgetAndNeverReusesAnUnconsumedCertificate()throws Exception{
        DailyRules unspent=rules(0);unspent.activityFrame(icons(120,states()));
        assertEquals("daily-guide-boss-tab",unspent.next(page(120)).scene);assertFalse(unspent.budgetComplete());
        DailyRules complete=rules(240);complete.activityFrame(icons(120,states()));
        assertEquals(Kind.WAIT,complete.next(page(120)).kind);
        assertEquals("daily-activity-icons-wait",complete.next(page(120)).scene);
        complete.activityFrame(icons(120,states()));assertEquals(Kind.WAIT,complete.next(page(120)).kind);
        complete.activityFrame(icons(120,states()));Action claim=complete.next(page(120));
        assertEquals("daily-activity-chest-0",claim.scene);assertTrue(complete.budgetComplete());
        complete.applied(claim);assertEquals(0,complete.tacet.reserved());assertEquals(240,complete.tacet.dailySpent());
        assertFalse(complete.rewards.complete());
    }
    private DailyRules verifiedActivity()throws Exception{
        DailyRules rules=rules(240);ActivityRewardVision.State[] claimed=states();Arrays.fill(claimed,CLAIMED);
        rules.activityFrame(icons(120,claimed));assertEquals(Kind.WAIT,rules.next(page(120)).kind);
        rules.activityFrame(icons(120,claimed));Action back=rules.next(page(120));
        assertEquals("daily-rewards-to-mail",back.scene);assertEquals("verified",rules.rewards.status().get("activity"));
        // Deliberately leave BACK unapplied: its two-observation guard still needs stable authorization.
        return rules;
    }
    private void verifiedBack(Action action){assertEquals(Kind.BACK,action.kind);assertEquals("daily-rewards-to-mail",action.scene);}
    @Test public void verifiedActivityKeepsStableBackWithoutAnyFurtherIconCertificate()throws Exception{
        DailyRules rules=verifiedActivity();
        for(int frame=0;frame<3;frame++)verifiedBack(rules.next(page(120)));
        assertEquals("verified",rules.rewards.status().get("activity"));assertFalse(rules.rewards.complete());
    }
    @Test public void verifiedActivityIgnoresLaterUnknownIconsRatherThanReopeningVerification()throws Exception{
        DailyRules rules=verifiedActivity();ActivityRewardVision.State[] unknown=states();Arrays.fill(unknown,UNKNOWN);
        for(int frame=0;frame<3;frame++){rules.activityFrame(icons(120,unknown));verifiedBack(rules.next(page(120)));}
        assertEquals("verified",rules.rewards.status().get("activity"));
    }
    @Test public void verifiedActivityDoesNotNeedThePointsNumberAgainToLeaveItsKnownPage()throws Exception{
        DailyRules rules=verifiedActivity();Screen fadedPoints=page(120);fadedPoints.words.remove(2);
        assertNull(DailyRules.activityPoints(fadedPoints));
        for(int frame=0;frame<3;frame++)verifiedBack(rules.next(fadedPoints));
        assertEquals("verified",rules.rewards.status().get("activity"));
    }
    @Test public void verifiedDepartureStillRequiresThisUidKnownTitleAndNoOverlay()throws Exception{
        for(int missing=0;missing<4;missing++){
            DailyRules rules=verifiedActivity();Screen bad=page(120);
            if(missing==0)bad.words.set(3,t("特征码:123456789",.91f,.98f));
            if(missing==1)bad.words.remove(3);
            if(missing==2)for(String label:new String[]{"保存现场","开跑","识别"})bad.words.add(t(label,.3f,.3f));
            if(missing==3)bad.words.set(0,t("未知页",.08f,.06f));
            Action action=rules.next(bad);assertNotEquals("daily-rewards-to-mail",action.scene);
            assertNotEquals(Kind.TAP,action.kind);assertEquals("verified",rules.rewards.status().get("activity"));
        }
    }
    @Test public void verifiedButOwnedTaskPendingCannotUseTheFastDeparture()throws Exception{
        DailyRules rules=verifiedActivity();
        rules.rewards.applied(new Action(Kind.TAP,"daily-activity-claim","owned task already submitted",.90f,.26f));
        assertNull(rules.rewards.next(page(120),false));
        assertEquals(Kind.WAIT,rules.next(page(120)).kind);assertEquals("verified",rules.rewards.status().get("activity"));
    }
    @Test public void verifiedAndNoPendingNeverStartsAnotherVisibleTaskClaim()throws Exception{
        DailyRules rules=verifiedActivity();Screen extraTask=page(120);extraTask.words.add(t("领取",.90f,.26f));
        ActivityRewardVision.State[] claimed=states();Arrays.fill(claimed,CLAIMED);
        for(int frame=0;frame<3;frame++){rules.activityFrame(icons(120,claimed));verifiedBack(rules.next(extraTask));}
    }
}
