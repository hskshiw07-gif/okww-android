package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;
import static io.github.okww.weekly.GardenRules.*;

public class DailyLiveRegressionTest {
    private Text t(String text,float x,float y) { return new Text(text,x-.01f,y-.01f,x+.01f,y+.01f); }
    private Screen screen(Text... words) { return new Screen(new ArrayList<>(Arrays.asList(words)),new int[3],2400f/1080); }
    private DailyRules rules() {
        DailyRules r=new DailyRules(null,new AccountDailyConfig.Profile("333333333","test-account",3,java.util.Collections.emptyMap()));r.teamConfirmed();
        Screen uid=screen(t("UID:333333333",.9f,.98f));r.observeUid(uid);r.observeUid(uid);return r;
    }
    private Screen list() {
        return screen(t("素材获取",.08f,.06f),t("240/240",.72f,.06f),t("产出武器及技能材料",.20f,.42f),
                t("产出高级技能材料",.20f,.58f),t("产出声骸套件",.20f,.82f),t("无音清剿",.20f,.71f),
                t("沉心域无音区",.46f,.30f),t("声骸套装",.46f,.36f),
                new Text("前往",2113f/2400,345f/1080,2157f/2400,368f/1080),
                new Text("前往",2113f/2400,498f/1080,2153f/2400,521f/1080),
                new Text("直接挑战",2089f/2400,651f/1080,2180f/2400,674f/1080),
                new Text("直按挑战",2089f/2400,802f/1080,2179f/2400,827f/1080));
    }
    @Test public void recordedExitLoadingPercentWaitsNinetySecondsWithoutTouch() {
        Screen s=screen(t("超频·其一",.09f,.80f),t("共鸣者精神与异能长时超负荷，易诱发名为超频的特殊病症。虽可抑制，仍无根治手段。",.31f,.85f),
                t("75%",.934f,.941f));
        Action a=rules().next(s);assertEquals("game-loading",a.scene);assertEquals(Kind.WAIT,a.kind);
        GardenRunGuard g=new GardenRunGuard(0);assertFalse(g.ready(s,a,0));assertFalse(g.ready(s,a,30000));
        assertThrows(IllegalStateException.class,()->g.ready(s,a,91000));
        s.words.set(2,t("75%",.5f,.5f));assertNotEquals("game-loading",rules().next(s).scene);
    }
    @Test public void eightDigitUidMustNotConsumeScreenDelimiterAsDigitOne() {
        assertNull(AccountDailyConfig.parseUidFromText("特征码:2345678|"));
        assertNull(AccountDailyConfig.detectUid(screen(t("特征码:2345678",.9f,.98f))));
        DailyRules r=rules();r.observeUid(screen(t("特征码:2345678",.9f,.98f)));
        assertEquals("333333333",r.confirmedUid());assertEquals(3,r.targetTacetIndex());
    }
    @Test public void mixedProceedAndDirectRowsKeepOriginalListOrderAndSidebarDoesNotSelectCategoryAgain() {
        DailyRules r=rules();Screen s=list();
        assertEquals(4,DailyRules.proceedButtons(s).size());
        assertEquals("直接挑战",DailyRules.proceedButtons(s).get(2).value);
        Action a=r.next(s);assertEquals("daily-skip-unavailable-combat",a.scene);
        assertEquals(.062f,a.x,.0001f);assertEquals(.175f,a.y,.0001f);
        assertFalse(r.rewards.started());r.applied(a);assertTrue(r.rewards.started());
        assertTrue(r.followupReason().contains("第3项"));
    }
    @Test public void missingRowNeverSilentlyRenumbersTheRemainingTargets() {
        DailyRules r=rules();Screen s=list();s.words.remove(8);
        assertEquals("daily-target-incomplete",r.next(s).scene);assertEquals(Kind.WAIT,r.next(s).kind);
    }
    @Test public void realTerminalPodcastLabelAndMailIconUseObservedMobileLayoutOnly() {
        DailyRules r=rules();
        Screen activity=screen(t("活跃行迹",.08f,.06f),t("活跃度",.22f,.90f),t("100",.21f,.85f));
        r.applied(r.next(activity));
        Screen terminal=screen(t("终端",.08f,.06f),t("地图",.83f,.80f),t("索拉指南",.73f,.55f),
                t("数据坞",.53f,.55f),t("联觉经验",.11f,.78f),t("先约电台",.63f,.29f));
        Action mail=r.next(terminal);assertEquals("daily-open-mail",mail.scene);
        assertEquals(1651f/2400,mail.x,.0001f);assertEquals(1025f/1080,mail.y,.0001f);
        Screen missing=screen(t("终端",.08f,.06f),t("地图",.83f,.80f));
        assertEquals("daily-mail-entry-missing",r.next(missing).scene);
        r.applied(r.next(missing));assertEquals("daily-open-pass",r.next(terminal).scene);
    }
    @Test public void overlayContaminatedFrameNeverDrivesGamePolicyOrAccountDetection() {
        Screen s=list();s.words.add(t("识别",.09f,.20f));s.words.add(t("开跑",.16f,.20f));
        s.words.add(t("保存现场",.16f,.34f));s.words.add(t("特征码:999999999",.9f,.98f));
        DailyRules r=rules();
        assertEquals("capture-overlay-visible",r.next(s).scene);
        assertEquals(Kind.WAIT,new GardenRules().next(s).kind);
        assertEquals("333333333",r.confirmedUid());
    }
    @Test public void weeklyBossSidebarWithoutPageTitleIsNotAWeeklyPage() {
        Screen s=list();s.words.remove(0);s.words.add(t("战歌重奏",.17f,.60f));
        assertEquals("daily-unknown",new DailyRules().next(s).scene);
        s.words.add(t("战歌重奏",.08f,.06f));
        assertEquals(Kind.STOP,new DailyRules().next(s).kind);
    }
    @Test public void mailEnvelopeOcrPrefixStillRequiresMailTitleRegionAndOnlyClaims() {
        DailyRules r=rules();r.rewards.start();
        r.applied(r.next(screen(t("活跃行迹",.08f,.06f),t("活跃度",.22f,.90f),t("20",.21f,.85f))));
        Screen mail=screen(new Text("回邮件",127f/2400,51f/1080,231f/2400,84f/1080),
                new Text("全部领取",278f/2400,966f/1080,394f/2400,998f/1080),t("删除已读",.30f,.91f));
        Action a=r.next(mail);assertEquals("daily-claim-mail",a.scene);assertEquals(.14f,a.x,.001f);
        r.applied(a);assertEquals(Kind.WAIT,r.next(mail).kind);
        mail.words.set(0,t("回邮件",.5f,.5f));assertEquals("daily-unknown",r.next(mail).scene);
    }
    @Test public void measuredActivityNumberDecorationDoesNotForceRepeatedCropAndNavigationWaits() {
        Screen s=screen(t("多活跃行迹",.08f,.06f),t("活跃度g",.22f,.90f),
                new Text("20-",458f/2400,902f/1080,526f/2400,944f/1080),t("100",.88f,.90f));
        assertEquals(Integer.valueOf(20),DailyRules.activityPoints(s));assertNull(DailyRules.activityCrop(s));
        s.words.set(1,t("活跃度:",.22f,.90f));assertEquals(Integer.valueOf(20),DailyRules.activityPoints(s));
        s.words.set(2,t("-20",.21f,.85f));assertNull(DailyRules.activityPoints(s));
        s.words.set(2,t("20/100",.21f,.85f));assertNull(DailyRules.activityPoints(s));
        s.words.set(2,t("20-",.5f,.5f));assertNull(DailyRules.activityPoints(s));
    }
    @Test public void startingInsideMailReturnsToVerifyTeamWithoutClaiming() {
        Screen mail=screen(t("回邮件",.08f,.06f),t("全部领取",.14f,.91f));
        Action a=new DailyRules().next(mail);
        assertEquals("daily-rewards-page-back",a.scene);assertEquals(Kind.BACK,a.kind);
        assertEquals(Kind.WAIT,new DailyRules().next(screen(t("回邮件",.5f,.5f))).kind);
    }
    @Test public void recordedMailToastVerifiesAfterTwoFramesWithoutReclaiming() {
        DailyRules r=rules();r.rewards.start();
        r.applied(r.next(screen(t("活跃行迹",.08f,.06f),t("活跃度",.22f,.90f),t("20",.21f,.85f))));
        Screen m=screen(t("邮件",.08f,.06f),t("全部领取",.14f,.91f));r.applied(r.next(m));
        m.words.add(t("全部邮件的附件己经领取完毕",.6f,.45f));
        assertEquals(Kind.WAIT,r.next(m).kind);assertNotEquals("verified",r.rewards.status().get("mail"));
        m.words.set(2,new Text("全部邮件的附件己经领取完毕",1015f/2400,194f/1080,1390f/2400,230f/1080));
        assertEquals(Kind.WAIT,r.next(m).kind);assertNotEquals("verified",r.rewards.status().get("mail"));
        assertEquals("daily-mail-back",r.next(m).scene);assertEquals("verified",r.rewards.status().get("mail"));
        m.words.remove(2);assertEquals("daily-mail-back",r.next(m).scene);
    }
    @Test public void recordedDirectChallengeOcrVariationDoesNotDropLastRow() {
        Screen s=list();s.words.set(11,t("直技挑战",.89f,.75f));
        assertEquals(4,DailyRules.proceedButtons(s).size());
        assertEquals("daily-skip-unavailable-combat",rules().next(s).scene);
    }
    @Test public void titleAllowsOneOcrIconGlyphNotAnArbitraryPhraseOrBodyText() {
        assertTrue(DailyRewards.title(screen(t("凹邮件",.08f,.06f)),"邮件"));
        assertTrue(DailyRewards.title(screen(t("回邮件",.08f,.06f)),"邮件"));
        assertFalse(DailyRewards.title(screen(t("全部邮件",.08f,.06f)),"邮件"));
        assertFalse(DailyRewards.title(screen(t("邮件说明",.08f,.06f)),"邮件"));
        assertFalse(DailyRewards.title(screen(t("凹邮件",.5f,.5f)),"邮件"));
    }
    @Test public void actualPassOffAirDialogClosesAndRecordsNotOpenOnlyInPassStage() {
        Screen dialog=screen(t("提示",.30f,.32f),
                t("当前先约电台各频道暂未开播，请于下期先约电台频道开启时间段内进入。",.50f,.48f),t("确认",.63f,.62f));
        DailyRules fresh=new DailyRules();Action close=fresh.next(dialog);
        assertEquals("daily-pass-unavailable-confirm",close.scene);assertEquals(Kind.TAP,close.kind);
        fresh.applied(close);assertFalse(fresh.rewards.started());
        DailyRules r=rules();r.rewards.start();
        r.applied(r.next(screen(t("活跃行迹",.08f,.06f),t("活跃度",.22f,.90f),t("20",.21f,.85f))));
        r.applied(r.next(screen(t("邮件",.08f,.06f))));
        Action a=r.next(dialog);assertEquals("daily-pass-unavailable-confirm",a.scene);assertFalse(r.rewards.finished());
        r.applied(a);assertTrue(r.rewards.finished());assertEquals("not-open",r.rewards.status().get("pass-tasks"));
        assertEquals("not-open",r.rewards.status().get("pass-rewards"));
        assertEquals("daily-pass-unavailable-confirm",r.next(dialog).scene);
        assertEquals("daily-followup-finished",r.next(screen(t("终端",.08f,.06f),t("地图",.8f,.8f))).scene);
        assertEquals(Kind.WAIT,new DailyRules().next(screen(t("提示",.30f,.32f),t("确认",.63f,.62f))).kind);
    }
}
