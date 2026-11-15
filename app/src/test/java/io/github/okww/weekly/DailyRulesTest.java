package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class DailyRulesTest {
    private static final long BUDGET_NOW = java.time.Instant.parse("2026-11-11T04:00:00Z").toEpochMilli();
    private DailyStaminaLedger ledger() throws Exception {
        return new DailyStaminaLedger(new DailyStaminaLedger.Store() {
            private String saved;
            public String read() { return saved; }
            public void write(String value) { saved = value; }
        }, "333333333", BUDGET_NOW);
    }
    private GardenRules.Screen budgetBook(int ordinary) {
        GardenRules.Screen page = screen("声骸材料", "无音区");
        page.words.add(text("素材获取", .08f, .06f));
        page.words.add(text(ordinary + "/240", .71f, .06f));
        page.words.add(text("特征码:333333333", .90f, .98f));
        for (float y : new float[]{.26f, .44f, .62f, .80f}) page.words.add(text("前往", .88f, y));
        return page;
    }
    private DailyRules budgetRules(DailyTacet tacet) {
        DailyRules rules = new DailyRules(tacet);
        rules.teamConfirmed();
        GardenRules.Screen uid = budgetBook(240);
        rules.observeUid(uid); rules.observeUid(uid);
        return rules;
    }
    private GardenRules.Screen budgetActivity(int points, boolean claim) {
        GardenRules.Screen page = screen();
        page.words.add(text("活跃行迹", .08f, .06f)); page.words.add(text("活跃度", .22f, .90f));
        page.words.add(text("" + points, .21f, .85f));
        if (claim) page.words.add(text("领取", .90f, .26f));
        return page;
    }
    @Test public void developmentDailyBypassesPointsAndClaimRecognitionBeforeCombat() throws Exception {
        for(int points:new int[]{90,100,110,-1}){
            DailyTacet tacet=new DailyTacet();tacet.attachLedger(ledger());
            DailyRules rules=new DailyRules(tacet,null,true);rules.teamConfirmed();
            rules.observeUid(budgetBook(120));rules.observeUid(budgetBook(120));
            GardenRules.Screen activity=budgetActivity(points,true);
            if(points<0)activity.words.remove(2);
            rules.activityFrame(null); // Missing reward icons must not block a combat replay.
            assertEquals("daily-guide-boss-tab",rules.next(activity).scene);
            assertFalse(rules.rewards.started());assertEquals(0,tacet.dailySpent());
            assertEquals("daily-stamina-plan-check",rules.next(budgetBook(120)).scene);
            rules.next(budgetBook(120));assertEquals(120,tacet.remainingPlan());
            assertEquals("daily-guide-boss-tab",rules.next(activity).scene);
        }
    }
    @Test public void developmentDailyStillHonorsOrdinaryBalanceAndRealDailyLedger() throws Exception {
        for(boolean capped:new boolean[]{false,true}){
            DailyStaminaLedger ledger=ledger();
            if(capped)for(int balance=240;balance>=60;balance-=60){
                ledger.reserve(balance,60,false,BUDGET_NOW);ledger.confirm(balance-60);
            }
            DailyTacet tacet=new DailyTacet();tacet.attachLedger(ledger);
            DailyRules rules=new DailyRules(tacet,null,true);rules.teamConfirmed();
            rules.observeUid(budgetBook(59));rules.observeUid(budgetBook(59));
            if(!capped)assertEquals("daily-stamina-plan-check",rules.next(budgetBook(59)).scene);
            assertEquals("daily-guide-first-tab",rules.next(budgetBook(59)).scene);
            assertTrue(rules.rewards.started());assertEquals(0,tacet.reserved());
            assertEquals(capped?240:0,tacet.dailySpent());
            assertNotEquals("daily-guide-boss-tab",rules.next(budgetActivity(100,false)).scene);
        }
    }
    @Test public void activity100Or110DoesNotSkipAnUnplannedExecutableBudget() throws Exception {
        for (int points : new int[]{100, 110}) {
            DailyTacet tacet = new DailyTacet(); tacet.attachLedger(ledger());
            DailyRules rules = budgetRules(tacet);
            assertEquals("daily-guide-boss-tab", rules.next(budgetActivity(points, false)).scene);
            assertFalse(rules.rewards.started()); assertNull(tacet.plan()); assertFalse(rules.budgetComplete());
            assertEquals("daily-stamina-plan-check", rules.next(budgetBook(240)).scene);
        }
    }
    @Test public void activityCapDoesNotAbandonTheFrozenTargetsRemainingClaims() throws Exception {
        DailyStaminaLedger ledger = ledger(); DailyTacet tacet = new DailyTacet(); tacet.attachLedger(ledger); tacet.freezePlan(240, 0);
        ledger.reserve(240, 60, false, BUDGET_NOW); ledger.confirm(180);
        DailyRules rules = budgetRules(tacet);
        assertEquals("daily-guide-boss-tab", rules.next(budgetActivity(110, false)).scene);
        assertEquals(180, tacet.remainingPlan()); assertFalse(rules.rewards.started()); assertFalse(rules.budgetComplete());
    }
    @Test public void taskClaimIncreasing90To100StillReturnsToExecutableBudget() throws Exception {
        DailyTacet tacet = new DailyTacet(); tacet.attachLedger(ledger()); DailyRules rules = budgetRules(tacet);
        GardenRules.Action claim = rules.next(budgetActivity(90, true)); assertEquals("daily-activity-claim", claim.scene); rules.applied(claim);
        assertEquals("daily-claim-verifying", rules.next(budgetActivity(100, false)).scene);
        assertEquals("daily-guide-boss-tab", rules.next(budgetActivity(100, false)).scene);
        assertFalse(rules.rewards.started()); assertFalse(rules.budgetComplete());
    }
    @Test public void completedFrozen180And240TargetsStartFreeChecksAtActivityCap() throws Exception {
        for (int target : new int[]{180, 240}) {
            DailyStaminaLedger ledger = ledger(); DailyTacet tacet = new DailyTacet(); tacet.attachLedger(ledger); tacet.freezePlan(target, 0);
            for (int balance = target; balance >= 60; balance -= 60) { ledger.reserve(balance, 60, false, BUDGET_NOW); ledger.confirm(balance - 60); }
            DailyRules rules = budgetRules(tacet);
            assertTrue(rules.budgetComplete());
            assertEquals("daily-rewards-to-mail", rules.next(budgetActivity(110, false)).scene);
            assertTrue(rules.rewards.started());
        }
    }
    @Test public void daily240WithoutNewPlanIsCompleteButPartialOrPendingAndSkippedRunsAreNot() throws Exception {
        DailyStaminaLedger ledger = ledger(); DailyTacet tacet = new DailyTacet(); tacet.attachLedger(ledger); DailyRules rules = budgetRules(tacet);
        assertFalse(rules.budgetComplete());
        for (int balance : new int[]{240, 180}) { ledger.reserve(balance, 60, false, BUDGET_NOW); ledger.confirm(balance - 60); }
        rules.applied(new GardenRules.Action(GardenRules.Kind.BACK, "daily-map-locked", "", 0, 0));
        assertTrue(rules.rewards.started()); assertFalse(rules.budgetComplete());
        ledger.reserve(120, 60, false, BUDGET_NOW); assertFalse(rules.budgetComplete()); ledger.confirm(60);
        assertFalse(rules.budgetComplete()); // 180 without a frozen 180 target may still require 240.
        ledger.reserve(60, 60, false, BUDGET_NOW); assertFalse(rules.budgetComplete()); ledger.confirm(0);
        assertTrue(rules.budgetComplete()); assertNull(tacet.plan());
    }
    @Test public void navigationOnlyControllerKeepsItsOriginalActivityCapFollowup() {
        DailyRules rules = new DailyRules(); rules.teamConfirmed();
        assertEquals("daily-rewards-to-mail", rules.next(budgetActivity(100, false)).scene);
        assertTrue(rules.rewards.started()); assertFalse(rules.budgetComplete());
    }
    @Test public void executableNavigationCannotEnterWithoutAccountLedgerOrVerifiedTeam() throws Exception {
        DailyTacet tacet = new DailyTacet();
        DailyRules rules = new DailyRules(tacet);
        assertEquals("daily-guide-back", rules.next(budgetBook(240)).scene);
        assertNull(tacet.plan());
        rules.teamConfirmed();
        GardenRules.Screen noUid = budgetBook(240); noUid.words.remove(4);
        // A new controller still requires two actual UID observations before freezing a plan.
        DailyRules unidentified = new DailyRules(new DailyTacet()); unidentified.teamConfirmed();
        assertEquals("daily-account-wait", unidentified.next(noUid).scene);
        assertEquals("daily-stamina-ledger-wait", rules.next(budgetBook(240)).scene);
        assertNull(tacet.plan());
    }
    @Test public void fullOrdinaryBalanceFreezesOnceAndKeepsConfiguredNavigation() throws Exception {
        DailyTacet tacet = new DailyTacet(); tacet.attachLedger(ledger());
        DailyRules rules = budgetRules(tacet);
        assertEquals("daily-stamina-plan-check", rules.next(budgetBook(240)).scene);
        assertNull(tacet.plan());
        assertEquals("daily-wuyin-proceed", rules.next(budgetBook(240)).scene);
        DailyStaminaPlan frozen = tacet.plan();
        assertNotNull(frozen); assertEquals(240, frozen.plannedBudget); assertEquals(0, frozen.exchangeAmount);
        assertEquals("daily-wuyin-proceed", rules.next(budgetBook(240)).scene);
        assertSame(frozen, tacet.plan());
    }
    @Test public void ordinaryDeficitFreezesOrdinaryOnlyPlanWithoutOpeningReserve() throws Exception {
        for (int ordinary : new int[]{0, 40, 60, 120, 179, 180, 239}) {
            DailyTacet tacet = new DailyTacet(); tacet.attachLedger(ledger());
            DailyRules rules = budgetRules(tacet);
            GardenRules.Action action = rules.next(budgetBook(ordinary));
            assertEquals("ordinary=" + ordinary, "daily-stamina-plan-check", action.scene);
            assertEquals(GardenRules.Kind.WAIT, action.kind);
            GardenRules.Action next = rules.next(budgetBook(ordinary));
            assertNotEquals("daily-reserve-open", next.scene);
            assertNotNull(tacet.plan());
            assertEquals((ordinary / 60) * 60, tacet.plan().plannedBudget);
            assertEquals(0, tacet.plan().exchangeAmount);
            assertEquals(0, tacet.reserved());
            if (ordinary < 60) {
                assertEquals("daily-guide-first-tab", next.scene);
                assertTrue(rules.rewards.started());
            } else {
                assertEquals("daily-wuyin-proceed", next.scene);
                assertFalse(rules.rewards.started());
            }
        }
    }
    @Test public void frozenPlanDoesNotReapply180ThresholdAfterEachClaim() throws Exception {
        DailyStaminaLedger ledger = ledger(); DailyTacet tacet = new DailyTacet(); tacet.attachLedger(ledger);
        DailyRules rules = budgetRules(tacet); rules.next(budgetBook(240)); rules.next(budgetBook(240));
        DailyStaminaPlan frozen = tacet.plan();
        for (int balance : new int[]{240, 180, 120}) {
            ledger.reserve(balance, 60, false, BUDGET_NOW); ledger.confirm(balance - 60);
            assertEquals("daily-wuyin-proceed", rules.next(budgetBook(balance - 60)).scene);
            assertSame(frozen, tacet.plan()); assertEquals(balance - 60, tacet.remainingPlan());
        }
    }
    @Test public void previouslySpentPlusOrdinaryCanCoverDailyRemainderWithoutReserve() throws Exception {
        DailyStaminaLedger ledger = ledger(); ledger.reserve(240, 60, false, BUDGET_NOW); ledger.confirm(180);
        DailyTacet tacet = new DailyTacet(); tacet.attachLedger(ledger);
        DailyRules rules = budgetRules(tacet);
        assertEquals("daily-stamina-plan-check", rules.next(budgetBook(179)).scene);
        assertNull(tacet.plan());
        assertEquals("daily-stamina-plan-check", rules.next(budgetBook(180)).scene);
        assertEquals("daily-wuyin-proceed", rules.next(budgetBook(180)).scene);
        assertEquals(180, tacet.plan().plannedBudget); assertEquals(0, tacet.plan().exchangeAmount);
    }
    @Test public void exhaustedDailyAllowanceReturnsToFreeRewardsInsteadOfFreezingAgain() throws Exception {
        DailyStaminaLedger ledger = ledger();
        for (int balance = 240; balance >= 60; balance -= 60) {
            ledger.reserve(balance, 60, false, BUDGET_NOW); ledger.confirm(balance - 60);
        }
        DailyTacet tacet = new DailyTacet(); tacet.attachLedger(ledger);
        DailyRules rules = budgetRules(tacet);
        assertEquals("daily-guide-first-tab", rules.next(budgetBook(0)).scene);
        assertTrue(rules.rewards.started()); assertNull(tacet.plan());
    }
    @Test public void oneHighOrdinaryReadingFollowedByLowerBalanceNeverFreezesTheHighPlan() throws Exception {
        DailyTacet tacet = new DailyTacet(); tacet.attachLedger(ledger());
        DailyRules rules = budgetRules(tacet);
        assertEquals("daily-stamina-plan-check", rules.next(budgetBook(240)).scene);
        assertEquals("daily-stamina-plan-check", rules.next(budgetBook(180)).scene);
        assertNull(tacet.plan());
        assertEquals("daily-stamina-plan-check", rules.next(budgetBook(240)).scene);
        assertNull(tacet.plan());
    }
    @Test public void missingBalanceUidOverlayAndDifferentPageBreakBudgetConfirmation() throws Exception {
        GardenRules.Screen missingBalance = budgetBook(240); missingBalance.words.remove(3);
        GardenRules.Screen missingUid = budgetBook(240); missingUid.words.remove(4);
        GardenRules.Screen overlay = budgetBook(240);
        for (String label : new String[]{"保存现场", "开跑", "识别"}) overlay.words.add(text(label, .3f, .3f));
        GardenRules.Screen differentPage = budgetBook(240); differentPage.words.set(2, text("素材获取", .5f, .5f));
        GardenRules.Screen foreignUid = budgetBook(240); foreignUid.words.set(4, text("特征码:000000000", .90f, .98f));
        for (GardenRules.Screen gap : new GardenRules.Screen[]{missingBalance, missingUid, overlay, differentPage, foreignUid}) {
            DailyTacet tacet = new DailyTacet(); tacet.attachLedger(ledger());
            DailyRules rules = budgetRules(tacet);
            assertEquals("daily-stamina-plan-check", rules.next(budgetBook(240)).scene);
            rules.next(gap); assertNull(tacet.plan());
            assertEquals("daily-stamina-plan-check", rules.next(budgetBook(240)).scene);
            assertNull(tacet.plan());
            assertEquals("daily-wuyin-proceed", rules.next(budgetBook(240)).scene);
            assertNotNull(tacet.plan());
        }
    }
    @Test public void reserveExchangeStillRequiresOwnedFreshAuthorization() {
        DailyRules rules=new DailyRules();
        for(String scene:new String[]{"daily-reserve-exchange","daily-reserve-drag"})
            assertThrows(IllegalStateException.class,()->rules.beforeResourceInput(
                new GardenRules.Action(GardenRules.Kind.TAP,scene,"",.5f,.5f),BUDGET_NOW));
    }
    @Test public void inheritedSourcePanelBacksOutAndInvalidatesTheOldTeamProof() throws Exception {
        DailyTacet tacet=new DailyTacet();tacet.attachLedger(ledger());DailyRules rules=budgetRules(tacet);
        GardenRules.Action back=rules.next(DailyReservePreparationTest.source(240,355,"333333333"));
        assertEquals("daily-reserve-unowned-back",back.scene);assertEquals(GardenRules.Kind.BACK,back.kind);rules.applied(back);
        assertNull(tacet.plan());assertEquals("daily-guide-back",rules.next(budgetBook(240)).scene);
    }
    @Test public void activityCropOnlyRetriesUnreadableCurrentPointsOnKnownHandbookPage() {
        GardenRules.Screen page = screen();
        page.words.add(text("多活跃行迹", .085f, .06f));
        page.words.add(text("活跃度", .22f, .90f));
        page.words.add(text("100", .88f, .90f));
        assertArrayEquals(new float[]{.17f,.80f,.28f,.88f}, DailyRules.activityCrop(page), .0001f);
        assertNull(DailyRules.activityCrop(new GardenRules.Screen(page.words, new int[3], 16f/9)));
        page.words.add(text("0", .20f, .85f));
        assertNull(DailyRules.activityCrop(page));
        page.words.remove(3); page.words.remove(1);
        assertNull(DailyRules.activityCrop(page));
        assertNull(DailyRules.activityCrop(screen("活跃行迹", "活跃度")));
    }
    @Test public void reproducedPhoneDroppedLevelLetterStillNeedsUidAndLatencyInTheirOwnRegions() {
        List<GardenRules.Text> words = new ArrayList<>();
        words.add(new GardenRules.Text("一33443", 1116f/2400, 999f/1080, 1250f/2400, 1019f/1080));
        words.add(new GardenRules.Text("L.90", 1372f/2400, 1004f/1080, 1415f/2400, 1022f/1080));
        words.add(new GardenRules.Text("104ms", 2202f/2400, 116f/1080, 2264f/2400, 134f/1080));
        words.add(text("特征码:000000000", .91f, .98f));
        GardenRules.Screen frame = new GardenRules.Screen(words, new int[3], 2400f/1080);
        assertTrue(GardenRules.worldHud(frame));
        assertEquals("daily-world-terminal", new DailyRules().next(frame).scene);
        assertEquals("world-terminal", new GardenRules().next(frame).scene);
        words.set(1, new GardenRules.Text(".90", 1378f/2400, 1002f/1080, 1414f/2400, 1022f/1080));
        assertTrue(GardenRules.worldHud(frame));
        assertEquals("daily-world-terminal", new DailyRules().next(frame).scene);
        words.remove(3); assertFalse(GardenRules.worldHud(frame));
        words.add(text("特征码:000000000", .91f, .98f));
        words.remove(2); assertFalse(GardenRules.worldHud(frame));
        words.add(text("104ms", .5f, .5f)); assertFalse(GardenRules.worldHud(frame));
        words.set(3, text("104ms", .92f, .12f)); assertTrue(GardenRules.worldHud(frame));
        for (String noisy : new String[]{"Lw.90", "L90", "w90", "Lv90", "90", "ta90"}) {
            words.set(1, new GardenRules.Text(noisy, 1372f/2400, 1004f/1080, 1416f/2400, 1022f/1080));
            assertTrue(noisy, GardenRules.worldHud(frame));
        }
        words.set(1, text("L.90", .5f, .5f)); assertFalse(GardenRules.worldHud(frame));
    }
    private GardenRules.Screen screen(String... labels) {
        List<GardenRules.Text> words = new ArrayList<>();
        for (String label : labels) words.add(text(label, .5f, .5f));
        return new GardenRules.Screen(words, new int[3], 2400f / 1080);
    }
    private GardenRules.Text text(String value, float x, float y) {
        return new GardenRules.Text(value, x - .01f, y - .01f, x + .01f, y + .01f);
    }
    @Test public void weeklyAndSpendPagesNeverEnterGardenOrSpend() {
        for (String label : new String[]{"幻梦游园", "本周游历值", "战歌重奏", "补充波片", "兑换波片", "结晶溶剂", "单人挑战", "领取奖励将消耗"})
            assertEquals(label, GardenRules.Kind.STOP, new DailyRules().next(screen(label, "确认", "开始游戏")).kind);
    }
    @Test public void terminalRequiresContextAndUniqueTeamButton() {
        assertEquals("daily-open-team", new DailyRules().next(screen("终端", "地图", "编队")).scene);
        assertEquals(GardenRules.Kind.WAIT, new DailyRules().next(screen("编队", "地图")).kind);
        assertEquals(GardenRules.Kind.WAIT, new DailyRules().next(screen("终端", "编队")).kind);
        assertThrows(IllegalStateException.class, () -> new DailyRules().next(screen("终端", "地图", "编队", "队伍")));
    }
    @Test public void actualMobileTerminalMixedTraditionalOcrOpensTeam() {
        List<GardenRules.Text> words = new ArrayList<>();
        words.add(new GardenRules.Text("多终端", 167f/2400, 44f/1080, 276f/2400, 74f/1080));
        words.add(new GardenRules.Text("編队", 996f/2400, 579f/1080, 1050f/2400, 606f/1080));
        words.add(new GardenRules.Text("数据坞", 1221f/2400, 576f/1080, 1307f/2400, 607f/1080));
        words.add(new GardenRules.Text("地图", 1959f/2400, 852f/1080, 2015f/2400, 879f/1080));
        GardenRules.Action action = new DailyRules().next(new GardenRules.Screen(words, new int[3], 2400f/1080));
        assertEquals("daily-open-team", action.scene);
        assertEquals(1023f/2400, action.x, .0001f); assertEquals(592.5f/1080, action.y, .0001f);
    }
    @Test public void teamScreenIsNotClickedOrMistakenForTerminal() {
        GardenRules.Action action = new DailyRules().next(screen("编队", "快速编队", "出战"));
        assertEquals("daily-team", action.scene); assertEquals(GardenRules.Kind.WAIT, action.kind);
    }
    @Test public void oneUnrecognizedTeamFrameDoesNotInvalidateAnAlreadyVerifiedTeam() {
        DailyRules r=new DailyRules();r.teamConfirmed();r.next(screen("正在载入"));
        assertEquals("daily-team-back",r.next(screen("编队","快速编队","出战")).scene);
        r.next(screen("终端","地图","索拉指南"));
        assertEquals("daily-team",r.next(screen("编队","快速编队","出战")).scene);
    }
    @Test public void guideNavigationIsOnlyAllowedAfterCurrentRunTeamConfirmation() {
        DailyRules rules = new DailyRules();
        GardenRules.Screen terminal = screen("终端", "地图", "编队");
        terminal.words.add(text("索拉指南", .73f, .55f));
        assertEquals("daily-open-team", rules.next(terminal).scene);
        rules.teamConfirmed();
        assertEquals(GardenRules.Kind.BACK, rules.next(screen("编队", "快速编队", "已出战")).kind);
        assertEquals("daily-open-guide", rules.next(terminal).scene);
        GardenRules.Screen activity = screen("周度游历", "幻梦游园");
        activity.words.add(text("多活跃行迹", .085f, .06f));
        assertEquals("daily-guide-loading", rules.next(activity).scene);
        assertEquals(GardenRules.Kind.STOP, rules.next(screen("幻梦游园", "本周游历值", "开始游戏")).kind);
    }
    @Test public void reenteringTeamAfterLeavingInvalidatesPreviousRoster() {
        DailyRules rules = new DailyRules();
        GardenRules.Screen team = screen("编队", "快速编队", "已出战");
        rules.teamConfirmed();
        assertEquals("daily-team-back",rules.next(team).scene);
        rules.next(screen("终端","地图","编队"));
        assertEquals("daily-team",rules.next(team).scene);
        rules.teamConfirmed();
        assertEquals("daily-team-back",rules.next(team).scene);
    }
    @Test public void actualJournalStartsWithTeamRecoveryThenUsesCalibratedFirstTab() {
        GardenRules.Screen journal = screen("阶段奖励", "已解锁所有阶段任务");
        journal.words.add(text("阿漂泊日志", .085f, .06f));
        DailyRules rules = new DailyRules();
        assertEquals(GardenRules.Kind.BACK, rules.next(journal).kind);
        rules.teamConfirmed();
        GardenRules.Action action = rules.next(journal);
        assertEquals("daily-guide-first-tab", action.scene);
        assertEquals(.062f, action.x, .0001f); assertEquals(.175f, action.y, .0001f);
        assertEquals(GardenRules.Kind.STOP, rules.next(new GardenRules.Screen(journal.words, new int[3], 16f/9)).kind);
    }
    @Test public void actualActivityPointsCannotBeConfusedWithMilestonesOrPartialLoading() {
        GardenRules.Screen frame = screen("周度游历");
        frame.words.add(new GardenRules.Text("多活跃行迹", 127f/2400, 50f/1080, 285f/2400, 85f/1080));
        frame.words.add(new GardenRules.Text("活跃度", 424f/2400, 956f/1080, 611f/2400, 992f/1080));
        frame.words.add(new GardenRules.Text("100", 2105f/2400, 962f/1080, 2148f/2400, 985f/1080));
        DailyRules rules = new DailyRules(); rules.teamConfirmed();
        assertNull(DailyRules.activityPoints(frame));
        assertEquals("daily-guide-loading", rules.next(frame).scene);
        frame.words.add(new GardenRules.Text("110", 444f/2400, 900f/1080, 534f/2400, 938f/1080));
        assertEquals(Integer.valueOf(110), DailyRules.activityPoints(frame));
        assertEquals("daily-rewards-to-mail", rules.next(frame).scene);
        assertEquals(Integer.valueOf(110), rules.rewards.points());
        assertEquals("unverified", rules.rewards.status().get("activity"));
        frame.words.add(text("100", .22f, .85f));
        assertThrows(IllegalStateException.class, () -> DailyRules.activityPoints(frame));
    }
    @Test public void connectIsOnlyOfferedOnKnownLoginScreen() {
        GardenRules.Screen screen = screen("CN_Android_Product_3.7", "CADPA");
        screen.words.add(text("点击连接", .5f, .90f));
        DailyRules rules = new DailyRules();
        GardenRules.Action action = rules.next(screen);
        assertEquals("game-connect", action.scene);
        assertEquals("daily-ocr-empty", rules.next(screen()).scene);
        rules.applied(action);
        assertEquals("game-loading", rules.next(screen()).scene);
        assertEquals(GardenRules.Kind.WAIT, new DailyRules().next(screen("点击连接")).kind);
    }
    @Test public void unknownScreenIsReadOnlyAndBoundedBySharedGuard() {
        GardenRules.Screen screen = screen("未知画面");
        GardenRules.Action action = new DailyRules().next(screen);
        assertEquals(GardenRules.Kind.WAIT, action.kind);
        GardenRunGuard guard = new GardenRunGuard(0);
        assertFalse(guard.ready(screen, action, 0));
        assertThrows(IllegalStateException.class, () -> guard.ready(screen, action, 26000));
    }
    @Test public void worldQuestTextIsNotMistakenForHandbookContext() {
        GardenRules.Screen world = screen("周度游历", "无音区", "共鸣领域");
        world.words.add(text("活跃行迹", .15f, .35f));
        world.words.add(text("Lv.90", .55f, .90f));
        world.words.add(text("特征码:000000000", .90f, .97f));
        DailyRules rules = new DailyRules();
        assertEquals("daily-world-terminal", rules.next(world).scene);
        rules.teamConfirmed();
        assertEquals("daily-world-terminal", rules.next(world).scene);
    }
    @Test public void taskIdentityHasSeparateRecordsAndRejectsUnknownValues() {
        assertEquals(AutomationTask.DAILY, AutomationTask.restore("DAILY"));
        assertNotEquals(AutomationTask.DAILY.stateFile, AutomationTask.GARDEN.stateFile);
        assertThrows(IllegalArgumentException.class, () -> AutomationTask.restore("DailyTask"));
        assertThrows(IllegalArgumentException.class, () -> AutomationTask.restore(null));
    }
    @Test public void traditionalAndMixedTerminalTextUsesSameKnownMenuGeometry() {
        for(String[] labels:new String[][]{{"終端","地圖","編隊"},{"多終端","數據塢","地圖","編队"}}){
            GardenRules.Action action=new DailyRules().next(screen(labels));
            assertEquals("daily-open-team",action.scene);
        }
        assertEquals("终图数据坞征码级志换区",GardenRules.normalize("終圖數據塢徵碼級誌換區"));
    }
    @Test public void traditionalWorldAccountMarkerAndJournalAreRecognizedWithoutNewCoordinates() {
        GardenRules.Screen world=screen();
        world.words.add(text("Lv.90",.58f,.94f));world.words.add(text("特徵碼：000000000",.90f,.98f));
        assertEquals("daily-world-terminal",new DailyRules().next(world).scene);
        GardenRules.Screen journal=screen("階段獎勵");journal.words.add(text("漂泊日誌",.085f,.06f));
        assertEquals("daily-guide-back",new DailyRules().next(journal).scene);
    }
    @Test public void observedMapLabelsRecoverByBackInsteadOfGuessingGameCoordinates() {
        GardenRules.Screen map=screen();
        map.words.add(text("探索率71%",.13f,.12f));map.words.add(text("切換地圖",.825f,.925f));
        GardenRules.Action action=new DailyRules().next(map);
        assertEquals("daily-map-back",action.scene);assertEquals(GardenRules.Kind.BACK,action.kind);
        GardenRunGuard guard=new GardenRunGuard(0);
        assertFalse(guard.ready(map,action,0));assertTrue(guard.ready(map,action,1000));
        assertEquals(GardenRules.Kind.WAIT,new DailyRules().next(screen("探索率71%","切换地图")).kind);
        GardenRules.Screen partial=screen();partial.words.add(text("切换地图",.825f,.925f));
        assertEquals(GardenRules.Kind.WAIT,new DailyRules().next(partial).kind);
    }
    @Test public void layoutEmptyOcrAndUnsupportedCombatHaveDifferentActionableMessages() {
        GardenRules.Action empty=new DailyRules().next(screen());
        assertEquals("daily-ocr-empty",empty.scene);assertTrue(empty.description.contains("未识别到文字"));
        GardenRules.Action layout=new DailyRules().next(new GardenRules.Screen(screen("文字").words,new int[3],16f/9));
        assertEquals("daily-layout-unsupported",layout.scene);assertEquals(GardenRules.Kind.STOP,layout.kind);
        GardenRules.Action combat=new DailyRules().next(screen("挑战失败","重新挑战"));
        assertEquals("daily-combat-unavailable",combat.scene);assertEquals(GardenRules.Kind.STOP,combat.kind);
        assertTrue(combat.description.contains("尚未接通"));
        for(GardenRules.Action a:new GardenRules.Action[]{empty,layout,new DailyRules().next(screen("未知页面"))})
            assertTrue(DailyRules.unmatched(a));
        assertFalse(DailyRules.unmatched(combat));
        GardenRules.Action garden=new DailyRules().next(screen("挑战失败","重新挑战","本周游历值"));
        assertEquals(GardenRules.Kind.STOP,garden.kind);assertTrue(garden.description.contains("周常界面"));
    }
    @Test public void unknownDiagnosticsDistinguishMissingHudEvidenceAndStillSendNoInput() {
        GardenRules.Screen frame=screen("未知页面");
        GardenRules.Action action=new DailyRules().next(frame);
        assertEquals(GardenRules.Kind.WAIT,action.kind);
        assertTrue(action.description.contains("底部账号标记、等级/血量"));
        frame.words.add(text("特征码:000000000",.90f,.98f));
        assertTrue(new DailyRules().next(frame).description.contains("未确认底部等级/血量"));
        frame.words.remove(frame.words.size()-1);frame.words.add(text("Lv.90",.58f,.94f));
        assertTrue(new DailyRules().next(frame).description.contains("未确认底部账号标记"));
    }
    @Test public void activityUnderCapClaimsReadyTaskThenNavigatesToBossBookAndWuyin() {
        GardenRules.Screen activity = screen("周度游历");
        activity.words.add(new GardenRules.Text("多活跃行迹", 130f/2400, 50f/1080, 283f/2400, 86f/1080));
        activity.words.add(new GardenRules.Text("活跃度", 428f/2400, 955f/1080, 554f/2400, 990f/1080));
        activity.words.add(new GardenRules.Text("0", 444f/2400, 900f/1080, 534f/2400, 938f/1080));
        activity.words.add(new GardenRules.Text("领取", 2084f/2400, 255f/1080, 2130f/2400, 277f/1080));
        DailyRules rules = new DailyRules(); rules.teamConfirmed();
        GardenRules.Screen uid = screen(); uid.words.add(text("UID:000000000",.9f,.98f));
        rules.observeUid(uid); rules.observeUid(uid);
        GardenRules.Action claim = rules.next(activity);
        assertEquals("daily-activity-claim", claim.scene);
        assertEquals(2107f/2400, claim.x, .0001f); assertEquals(266f/1080, claim.y, .0001f);
        rules.applied(claim);
        activity.words.remove(activity.words.size() - 1);
        activity.words.set(3, new GardenRules.Text("20", 444f/2400, 900f/1080, 534f/2400, 938f/1080));
        assertEquals("daily-claim-verifying",rules.next(activity).scene);
        GardenRules.Action bossTab = rules.next(activity);
        assertEquals("daily-guide-boss-tab", bossTab.scene);
        assertEquals(148f/2400, bossTab.x, .0001f); assertEquals(326f/1080, bossTab.y, .0001f);
        GardenRules.Screen bossBook = screen("武器及技能材料");
        bossBook.words.add(text("素材获取", .08f, .06f));
        bossBook.words.add(text("220/240", .71f, .06f));
        bossBook.words.add(text("无音清剿", .22f, .60f));
        bossBook.words.add(text("前往", .88f, .26f));
        GardenRules.Action selectWuyin = rules.next(bossBook);
        assertEquals("daily-boss-wuyin", selectWuyin.scene);
        bossBook.words.set(0, text("声骸材料", .50f, .50f));
        bossBook.words.add(text("无音区", .48f, .52f));
        bossBook.words.add(text("前往", .88f, .44f));
        bossBook.words.add(text("前往", .88f, .62f));
        bossBook.words.add(text("前往", .88f, .80f));
        GardenRules.Action proceed = rules.next(bossBook);
        assertEquals("daily-wuyin-proceed", proceed.scene);
        assertEquals(.88f, proceed.x, .0001f); assertEquals(.26f, proceed.y, .0001f);
        GardenRules.Screen lowStamina = screen("无音区");
        lowStamina.words.add(text("素材获取", .08f, .06f));
        lowStamina.words.add(text("40/240", .71f, .06f));
        assertEquals("daily-guide-first-tab", rules.next(lowStamina).scene);
        assertTrue(rules.followupReason().contains("不足60"));
    }

    @Test public void multiAccountUidSelectsConfiguredTacetSuppressionAndRecoversFromLockedMap() {
        AccountDailyConfig.Profile profile3 = new AccountDailyConfig.Profile("123456789", "test-account-3", 3, java.util.Collections.emptyMap());
        DailyRules rules = new DailyRules(null, profile3);
        rules.teamConfirmed();
        // A profile supplied by the local multi-account plan selects Tacet Suppression #3.
        GardenRules.Screen terminalWithUid = screen("终端", "地图", "索拉指南");
        terminalWithUid.words.add(new GardenRules.Text("特征码:T23456789", 475f/2400, 390f/1080, 763f/2400, 417f/1080));
        terminalWithUid.words.add(text("索拉指南", .73f, .55f));
        assertEquals("daily-open-guide", rules.next(terminalWithUid).scene);
        assertNull(rules.confirmedUid());
        rules.next(terminalWithUid);
        assertEquals("123456789", rules.confirmedUid());
        assertNotNull(rules.accountProfile());
        assertEquals(3, rules.targetTacetIndex());

        // On 素材获取 -> 无音清剿 with 4 visible rows, picks the 3rd row (#3)
        GardenRules.Screen wuyinPage = screen("声骸材料", "无音区");
        wuyinPage.words.add(text("素材获取", .08f, .06f));
        wuyinPage.words.add(text("240/240", .71f, .06f));
        wuyinPage.words.add(text("无音清剿", .22f, .60f));
        wuyinPage.words.add(text("前往", .88f, .26f));
        wuyinPage.words.add(text("前往", .88f, .44f));
        wuyinPage.words.add(text("前往", .88f, .62f));
        wuyinPage.words.add(text("前往", .88f, .80f));
        GardenRules.Action proceed3 = rules.next(wuyinPage);
        assertEquals("daily-wuyin-proceed", proceed3.scene);
        assertEquals(.62f, proceed3.y, .0001f);
        assertTrue(proceed3.description.contains("无音区#3"));

        // Preserve account #5, but PC scroll proportions are not a mobile calibration.
        AccountDailyConfig.Profile profile5 = new AccountDailyConfig.Profile("555555555", "test-account-5", 5, java.util.Collections.emptyMap());
        DailyRules rules5 = new DailyRules(null, profile5);
        rules5.teamConfirmed();
        wuyinPage.words.add(text("特征码: 555555555", .90f, .98f));
        rules5.next(wuyinPage);
        GardenRules.Action scroll5 = rules5.next(wuyinPage);
        assertEquals("daily-wuyin-calibration-required", scroll5.scene);
        assertEquals(GardenRules.Kind.STOP, scroll5.kind);
        assertEquals(5, rules5.targetTacetIndex());

        // Locked map target ("附近信标无法快速到达") backs out instead of waiting as daily-unknown
        GardenRules.Screen lockedMap = screen("沉心域无音区", "结晶波片消耗", "附近信标无法快速到达");
        assertEquals("daily-map-locked", rules.next(lockedMap).scene);
        assertEquals(GardenRules.Kind.BACK, rules.next(lockedMap).kind);
    }
}
