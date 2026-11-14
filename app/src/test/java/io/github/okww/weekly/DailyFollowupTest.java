package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;
import static io.github.okww.weekly.GardenRules.*;

/** Synthetic mobile OCR decision traces, not proof of ML Kit or live touch effects. */
public class DailyFollowupTest {
    private Text t(String value, float x, float y) { return new Text(value, x-.01f, y-.01f, x+.01f, y+.01f); }
    private Screen screen(Text... words) { return new Screen(new ArrayList<>(java.util.Arrays.asList(words)), new int[3], 2400f/1080); }
    private Screen activity(int points, String... buttons) {
        Screen s = screen(t("活跃行迹", .08f, .06f), t("活跃度", .22f, .90f), t(""+points, .21f, .85f));
        for (String button : buttons) s.words.add(t(button, .90f, button.equals("领取") ? .26f : .91f));
        return s;
    }
    private Screen terminal() { return screen(t("终端", .08f,.06f), t("地图", .8f,.8f), t("索拉指南",.73f,.55f),
            t("邮件",.5f,.93f), t("先约公约",.7f,.3f)); }
    private Screen world() { return screen(t("Lv.90",.58f,.94f),t("特征码:000000000",.9f,.98f)); }
    private Screen receipt() { return screen(t("获得奖励",.5f,.3f), t("点击空白处关闭",.5f,.91f)); }
    private Screen mail(String label) {
        boolean status = label.matches("暂无邮件|全部附件已领取");
        return screen(t("邮件",.08f,.06f),t(label,status ? .50f : .30f,status ? .20f : .90f));
    }
    private Screen pass(boolean tasks, String label) {
        Screen s = screen(t("先约公约",.12f,.06f),t("任务",.06f,.3f),t("奖励",.06f,.18f),t(label,.7f,.91f));
        if (tasks) { s.words.add(t("每日任务",.4f,.16f));s.words.add(t("每周任务",.6f,.16f)); }
        else { s.words.add(t("先约凭证",.3f,.4f));s.words.add(t("寰宇特约",.3f,.6f)); }
        return s;
    }
    private DailyRules rules() {
        DailyRules r = new DailyRules(); r.teamConfirmed();
        Screen uid=screen(t("UID:000000000",.9f,.98f));r.observeUid(uid);r.observeUid(uid);
        return r;
    }
    private Action apply(DailyRules rules, Screen s, String scene) {
        Action a = rules.next(s); assertEquals(scene, a.scene);
        assertTrue(a.kind == Kind.TAP || a.kind == Kind.BACK);
        rules.applied(a); return a;
    }
    @Test public void pcOrderDailyMailPassTasksThenPassRewardsUsesOnlyTextTargetsAndVerifiesEachClaim() {
        DailyRules r = rules();
        apply(r, activity(100,"领取"), "daily-activity-claim");
        assertEquals("daily-claim-verifying",r.next(activity(100,"领取")).scene);
        assertEquals("daily-claim-verifying",r.next(activity(120,"一键领取")).scene);
        apply(r, activity(120,"一键领取"), "daily-claim-activity");
        assertEquals("daily-claim-verifying",r.next(activity(120,"一键领取")).scene);
        apply(r, receipt(), "daily-rewards-dismiss");
        assertEquals("daily-claim-verifying",r.next(activity(120,"全部奖励已领取")).scene);
        apply(r, activity(120,"全部奖励已领取"), "daily-rewards-to-mail");
        apply(r, terminal(), "daily-open-mail");
        apply(r, mail("全部领取"), "daily-claim-mail");
        assertEquals("daily-claim-verifying",r.next(mail("全部领取")).scene);
        apply(r, receipt(), "daily-rewards-dismiss");
        assertEquals("daily-claim-verifying",r.next(mail("全部附件已领取")).scene);
        apply(r, mail("全部附件已领取"), "daily-mail-back");
        apply(r, terminal(), "daily-open-pass");
        apply(r, pass(false,"一键领取"), "daily-pass-tasks-tab");
        apply(r, pass(true,"一键领取"), "daily-claim-pass-tasks");
        apply(r, receipt(), "daily-rewards-dismiss");
        assertEquals("daily-claim-verifying",r.next(pass(true,"全部任务奖励已领取")).scene);
        apply(r, pass(true,"全部任务奖励已领取"), "daily-pass-next-rewards");
        apply(r, pass(false,"一键领取"), "daily-claim-pass-rewards");
        apply(r, receipt(), "daily-rewards-dismiss");
        assertEquals("daily-claim-verifying",r.next(pass(false,"全部奖励已领取")).scene);
        apply(r, pass(false,"全部奖励已领取"), "daily-pass-back");
        assertEquals("daily-followup-finished", r.next(terminal()).scene);
        assertEquals(Kind.WAIT,r.next(terminal()).kind);
        assertTrue(r.rewards.finished());
        for (String value : r.rewards.status().values()) assertEquals("verified",value);
    }
    @Test public void allReadyTasksAreClaimedOneAtATimeAndUnreadablePointsNeverUseCache() {
        DailyRules r = rules();
        apply(r, activity(0,"领取"), "daily-activity-claim");
        assertEquals("daily-claim-verifying",r.next(activity(0)).scene);
        Screen unreadable = activity(20,"领取"); unreadable.words.remove(2);
        assertEquals("daily-guide-loading",r.next(unreadable).scene);
        assertEquals("daily-claim-verifying",r.next(activity(20,"领取")).scene);
        apply(r, activity(20,"领取"), "daily-activity-claim");
        assertEquals("daily-claim-verifying",r.next(activity(20,"领取")).scene);
        assertEquals("daily-claim-verifying",r.next(activity(40)).scene);
        assertEquals("daily-guide-boss-tab",r.next(activity(40)).scene);
    }
    @Test public void missingIconsAndMissingProofStayUnverifiedInsteadOfFalseSuccess() {
        DailyRules r = rules();
        apply(r, activity(100), "daily-rewards-to-mail");
        Screen noIcons = screen(t("终端",.08f,.06f),t("地图",.8f,.8f));
        apply(r,noIcons,"daily-mail-entry-missing");
        apply(r,noIcons,"daily-pass-entry-missing");
        assertEquals("daily-followup-finished",r.next(noIcons).scene);
        assertEquals("unverified",r.rewards.status().get("activity"));
        assertEquals("entry-unverified",r.rewards.status().get("mail"));
        assertEquals("entry-unverified",r.rewards.status().get("pass-rewards"));
    }
    @Test public void receiptAloneDoesNotEstablishAllRewardsCollected() {
        DailyRules r=rules(); apply(r,activity(100,"全部领取"),"daily-claim-activity");
        apply(r,receipt(),"daily-rewards-dismiss");
        assertEquals("daily-claim-verifying",r.next(activity(100,"全部领取")).scene);
        apply(r,activity(100),"daily-rewards-to-mail");
        assertEquals("receipt-seen-unverified",r.rewards.status().get("activity"));
    }
    @Test public void missingClaimReceiptTimesOutWithoutRetryOrNavigation() {
        DailyRules r=rules();Screen s=activity(100,"全部领取");apply(r,s,"daily-claim-activity");
        GardenRunGuard guard=new GardenRunGuard(0);
        assertFalse(guard.ready(s,r.next(s),0));
        assertThrows(IllegalStateException.class,()->guard.ready(s,r.next(s),26000));
        assertEquals(Kind.WAIT,r.next(activity(100)).kind);
    }
    @Test public void noDeletionPurchaseOrUnownedModalAndDuplicateClaimTargetsStop() {
        DailyRules r=rules();assertEquals(Kind.WAIT,r.next(receipt()).kind);
        for(String paid:new String[]{"确认购买","补充波片","领取奖励将消耗","确认解锁"}) {
            Screen s=activity(100,"全部领取");s.words.add(t(paid,.5f,.5f));assertEquals(Kind.STOP,r.next(s).kind);
        }
        Screen duplicate=activity(100,"全部领取","一键领取");
        assertThrows(IllegalStateException.class,()->r.next(duplicate));
        apply(r,activity(100),"daily-rewards-to-mail");apply(r,terminal(),"daily-open-mail");
        Action back=r.next(mail("全部删除")); assertEquals(Kind.BACK,back.kind);
        assertEquals("unverified",r.rewards.status().get("mail"));
    }
    @Test public void expiredBattlePassIsNotMarkedClaimed() {
        DailyRules r=rules();apply(r,activity(100),"daily-rewards-to-mail");
        apply(r,terminal(),"daily-open-mail");assertEquals(Kind.WAIT,r.next(mail("暂无邮件")).kind);
        apply(r,mail("暂无邮件"),"daily-mail-back");
        apply(r,terminal(),"daily-open-pass");apply(r,pass(false,"本期公约已结束"),"daily-pass-expired");
        assertEquals("verified",r.rewards.status().get("mail"));assertEquals("not-open",r.rewards.status().get("pass-rewards"));
    }
    @Test public void insufficientStaminaContinuesFreeClaimsLikeOriginalDailyTask() {
        DailyRules r=rules(); Screen boss=screen(t("素材获取",.08f,.06f),t("40/240",.71f,.06f));
        apply(r,boss,"daily-guide-first-tab");apply(r,activity(20),"daily-rewards-to-mail");
        assertTrue(r.followupReason().contains("波片不足"));assertEquals(Integer.valueOf(20),r.rewards.points());
        assertNull(DailyRules.waveplates(screen(t("999/240",.71f,.06f))));
        assertNull(DailyRules.waveplates(screen(t("60/240",.71f,.06f),t("120/240",.8f,.06f))));
    }
    @Test public void lockedMapFromSavedPhoneOcrDoesNotReenterSameTarget() {
        // Core OCR rectangles from daily-live/unrec-run1.json; private UID omitted.
        Screen locked=screen(new Text("沉心域无音区",1827f/2400,43f/1080,2035f/2400,77f/1080),
                new Text("|结晶波片消耗",1753f/2400,423f/1080,1909f/2400,448f/1080),
                new Text("附近信标无法快速到达",1875f/2400,916f/1080,2105f/2400,939f/1080));
        DailyRules r=rules();apply(r,locked,"daily-map-locked");
        Screen boss=screen(t("素材获取",.08f,.06f),t("240/240",.71f,.06f),t("无音区",.5f,.5f),t("前往",.88f,.26f));
        assertEquals("daily-guide-first-tab",r.next(boss).scene);
        apply(r,activity(0),"daily-rewards-to-mail");assertTrue(r.followupReason().contains("未解锁"));
    }
    @Test public void travelRequiresSelectedTacetNotAnArbitraryMapAndDoesNotLoopAfterArrival() {
        DailyRules r=rules();Screen map=screen(t("无音区",.8f,.06f),t("结晶波片消耗",.8f,.4f),t("快速旅行",.85f,.90f));
        assertEquals("daily-map-back",r.next(map).scene);
        Screen boss=screen(t("素材获取",.08f,.06f),t("240/240",.71f,.06f),t("无音区",.5f,.5f),t("前往",.88f,.26f));
        boss.words.add(t("前往",.88f,.44f));boss.words.add(t("前往",.88f,.62f));boss.words.add(t("前往",.88f,.80f));
        apply(r,boss,"daily-wuyin-proceed");apply(r,map,"daily-map-travel");
        assertEquals("daily-travel-loading",r.next(map).scene);
        assertEquals("daily-travel-loading",r.next(screen()).scene);
        GardenRunGuard guard=new GardenRunGuard(0);assertFalse(guard.ready(map,r.next(map),0));
        assertFalse(guard.ready(map,r.next(map),30000));
        assertThrows(IllegalStateException.class,()->guard.ready(map,r.next(map),91000));
        assertEquals("daily-tacet-arrived",r.next(world()).scene);assertEquals(Kind.STOP,r.next(world()).kind);
    }
    @Test public void uidNeedsConsecutiveObservationsAndChangedAccountsStopBeforeAnyInput() {
        DailyRules r=new DailyRules();r.teamConfirmed();Screen a=screen(t("特征码:123456789",.9f,.98f));
        r.observeUid(a);assertNull(r.confirmedUid());r.observeUid(screen());r.observeUid(a);assertNull(r.confirmedUid());
        r.observeUid(a);assertEquals(1,r.targetTacetIndex());assertNull(r.accountProfile());
        Screen changed=screen(t("特征码:999999999",.9f,.98f));
        assertEquals("daily-account-recheck",r.next(changed).scene);assertTrue(r.uidConflict());
        assertEquals(Kind.WAIT,r.next(screen()).kind);assertTrue(r.uidConflict());
        r.observeUid(a);assertFalse(r.uidConflict());
        assertEquals(Kind.WAIT,r.next(changed).kind);
        assertThrows(IllegalStateException.class,()->r.next(changed));
        assertEquals(1,r.targetTacetIndex());
        assertNull(AccountDailyConfig.profileForUid("123456789"));assertTrue(AccountDailyConfig.allProfiles().isEmpty());
        assertNull(AccountDailyConfig.parseUidFromText("UID:123456789 UID:999999999"));
        assertEquals("123456789",AccountDailyConfig.parseUidFromText("特徵碼:T23456789"));
        assertNull(AccountDailyConfig.detectUid(screen(t("UID:123456789",.2f,.4f),t("UID:999999999",.9f,.98f))));
        assertNull(AccountDailyConfig.detectUid(screen(t("UID:123456789 UID:999999999",.2f,.4f),t("UID:123456789",.9f,.98f))));
    }
    @Test public void claimLimitIsCheckedBeforeOfferingAnotherTap() {
        DailyRules r=rules();for(int i=0;i<12;i++) {
            if(i>0) assertEquals(Kind.WAIT,r.next(activity(i*20,"领取")).kind);
            apply(r,activity(i*20,"领取"),"daily-activity-claim");
        }
        assertEquals(Kind.WAIT,r.next(activity(240,"领取")).kind);
        assertEquals(Kind.STOP,r.next(activity(240,"领取")).kind);
    }
    @Test public void transientMissingClaimButtonNeverUnlocksASecondSubmission() {
        DailyRules r=rules();apply(r,activity(100,"全部领取"),"daily-claim-activity");
        apply(r,receipt(),"daily-rewards-dismiss");
        assertEquals("daily-rewards-to-mail",r.next(activity(100)).scene);
        for(int i=0;i<4;i++) assertEquals("daily-claim-verifying",r.next(activity(100,"全部领取")).scene);
        apply(r,activity(100),"daily-rewards-to-mail");
        assertEquals(Kind.BACK,r.next(activity(100,"全部领取")).kind);
    }
    @Test public void oneCompletionFrameCannotPermanentlyVerifyRewardSection() {
        DailyRules r=rules();assertEquals(Kind.WAIT,r.next(activity(100,"全部奖励已领取")).kind);
        apply(r,activity(100),"daily-rewards-to-mail");
        assertEquals("unverified",r.rewards.status().get("activity"));
    }
    @Test public void normalPurchaseCtaDoesNotBlockFreePassClaimAndNeverBecomesTarget() {
        DailyRules r=rules();apply(r,activity(100),"daily-rewards-to-mail");
        apply(r,terminal(),"daily-open-mail");apply(r,mail("仅有已读邮件"),"daily-mail-back");
        apply(r,terminal(),"daily-open-pass");
        Screen s=pass(true,"全部领取");s.words.add(t("购买等级",.85f,.9f));
        Action a=r.next(s);assertEquals("daily-claim-pass-tasks",a.scene);assertEquals(.7f,a.x,.0001f);
    }
    @Test public void ignoredBackIsRetriedBySharedBoundNotReplacedWithAnotherClaim() {
        DailyRules r=rules();apply(r,activity(100),"daily-rewards-to-mail");
        apply(r,terminal(),"daily-open-mail");Screen m=mail("暂无邮件");r.next(m);apply(r,m,"daily-mail-back");
        GardenRunGuard guard=new GardenRunGuard(0);
        for(int i=0;i<4;i++) {
            Action a=r.next(m);assertEquals("daily-mail-back",a.scene);assertEquals(Kind.BACK,a.kind);
            assertFalse(guard.ready(m,a,i*2000));assertTrue(guard.ready(m,a,i*2000+1000));r.applied(a);
        }
        assertFalse(guard.ready(m,r.next(m),8000));
        assertThrows(IllegalStateException.class,()->guard.ready(m,r.next(m),9000));
    }
    @Test public void traditionalFollowupNamesShareTheExistingNormalizer() {
        assertEquals("邮件先约公约每日任务先约凭证暂无奖励没有邮件",normalize("郵件先約公約每日任務先約憑證暫無獎勵沒有郵件"));
        DailyRules r=rules();apply(r,activity(100),"daily-rewards-to-mail");
        Screen s=screen(t("終端",.08f,.06f),t("地圖",.8f,.8f),t("郵件",.5f,.9f));
        apply(r,s,"daily-open-mail");
        assertEquals(Kind.BACK,r.next(screen(t("郵件",.08f,.06f),t("暫無郵件",.4f,.5f))).kind);
        assertNotEquals("verified",r.rewards.status().get("mail"));
        assertEquals(Kind.WAIT,r.next(screen(t("郵件",.08f,.06f),t("暫無郵件",.5f,.2f))).kind);
    }
    @Test public void targetNavigationNeverUsesFallbackBeforeUidConfirmation() {
        DailyRules r=new DailyRules();r.teamConfirmed();
        Screen s=screen(t("素材获取",.08f,.06f),t("240/240",.71f,.06f),t("无音区",.5f,.5f),t("前往",.88f,.26f));
        assertEquals("daily-account-wait",r.next(s).scene);
        s.words.add(t("前往",.88f,.44f));s.words.add(t("前往",.88f,.62f));s.words.add(t("前往",.88f,.80f));
        s.words.add(t("UID:000000000",.9f,.98f));assertEquals("daily-account-wait",r.next(s).scene);
        assertEquals("daily-wuyin-proceed",r.next(s).scene);
    }
    @Test public void interveningUnknownFrameBreaksConsecutiveCompletionEvidence() {
        DailyRules r=rules();Screen complete=activity(100,"全部奖励已领取");
        assertEquals(Kind.WAIT,r.next(complete).kind);assertEquals(Kind.WAIT,r.next(screen()).kind);
        assertEquals(Kind.WAIT,r.next(complete).kind);assertNotEquals("verified",r.rewards.status().get("activity"));
        assertEquals(Kind.BACK,r.next(complete).kind);assertEquals("verified",r.rewards.status().get("activity"));
    }
    @Test public void transientActivityIncreaseCannotUnlockAnotherTaskClaim() {
        DailyRules r=rules();apply(r,activity(20,"领取"),"daily-activity-claim");
        assertEquals(Kind.WAIT,r.next(activity(40,"领取")).kind);
        for(int i=0;i<3;i++) assertEquals(Kind.WAIT,r.next(activity(20,"领取")).kind);
        assertEquals(Kind.WAIT,r.next(activity(40,"领取")).kind);
        assertEquals(Kind.WAIT,r.next(screen()).kind);
        assertEquals(Kind.WAIT,r.next(activity(40,"领取")).kind);
        assertEquals("daily-activity-claim",r.next(activity(40,"领取")).scene);
        for(int i=0;i<3;i++) assertEquals(Kind.WAIT,r.next(activity(20,"领取")).kind);
    }
}
