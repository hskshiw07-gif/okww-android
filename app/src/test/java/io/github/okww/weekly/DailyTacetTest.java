package io.github.okww.weekly;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
import static io.github.okww.weekly.GardenRules.*;

public class DailyTacetTest {
    private static final long NOW=java.time.Instant.parse("2026-11-11T04:00:00Z").toEpochMilli();
    private static class Memory implements DailyStaminaLedger.Store {
        String value; public String read(){return value;} public void write(String next){value=next;}
    }
    private Text t(String text, float x, float y) { return new Text(text,x-.01f,y-.01f,x+.01f,y+.01f); }
    private Screen screen(Text... text) { return new Screen(new java.util.ArrayList<>(Arrays.asList(text)),new int[3],2400f/1080); }
    @Test public void scopedEntryNeverActsOnAnUnselectedChallenge() {
        DailyTacet f=new DailyTacet();
        Screen entry=screen(t("单人挑战",.86f,.90f));
        assertNull(f.next(entry));
        f.selected();
        assertEquals(Kind.WAIT,f.next(entry).kind);
        assertEquals(Kind.WAIT,f.next(screen(t("素材获取",.08f,.06f))).kind);
    }
    @Test public void directEntryRequiresConfirmedTeamAccountAndCompleteList() throws Exception {
        DailyTacet f=new DailyTacet();f.attachLedger(new DailyStaminaLedger(new Memory(),"333333333",NOW));f.freezePlan(240,0);
        DailyRules r=new DailyRules(f,new AccountDailyConfig.Profile("333333333","test-account",3,java.util.Collections.emptyMap()));
        Screen list=screen(t("素材获取",.08f,.06f),t("240/240",.72f,.06f),t("无音区",.45f,.30f),
                t("前往",.89f,.33f),t("前往",.89f,.47f),t("直接挑战",.89f,.61f),t("直接挑战",.89f,.75f));
        assertEquals(Kind.BACK,r.next(list).kind);
        r.teamConfirmed(); assertEquals("daily-account-wait",r.next(list).scene);
        Screen uid=screen(t("特征码:333333333",.9f,.98f));r.observeUid(uid);r.observeUid(uid);
        Action a=r.next(list);assertEquals("daily-tacet-direct-entry",a.scene);assertEquals(.61f,a.y,.001f);
        r.applied(a);assertEquals("daily-tacet-entry-loading",r.next(list).scene);
    }
    @Test public void entryTeamIsReidentifiedAndStartIsNeverResubmitted() throws Exception {
        DailyTacet f=new DailyTacet();f.attachLedger(new DailyStaminaLedger(new Memory(),"123456789",NOW));f.freezePlan(240,0);f.selected();
        Screen s=screen(t("开启挑战",.86f,.91f),t("快速编队",.73f,.91f));
        assertEquals("daily-tacet-team",f.next(s).scene);
        f.entryTeamConfirmed();Action a=f.next(s);assertEquals("daily-tacet-start",a.scene);
        assertEquals("daily-tacet-start",f.next(screen(t("开启挑战",.86f,.91f),t("快速骗队",.73f,.91f))).scene);
        f.applied(a);assertEquals(Kind.WAIT,f.next(s).kind);
        assertEquals("daily-tacet-loading",f.next(screen()).scene);
    }
    private DailyTacet fighting(Memory memory) throws Exception {
        return fighting(memory,240);
    }
    private DailyTacet fighting(Memory memory,int ordinary) throws Exception {
        DailyTacet f=new DailyTacet();f.attachLedger(new DailyStaminaLedger(memory,"123456789",NOW));f.freezePlan(ordinary,0);f.selected();f.entryTeamConfirmed();
        f.applied(f.next(screen(t("开启挑战",.86f,.91f),t("快速编队",.73f,.91f))));
        assertEquals("daily-tacet-combat",f.next(CombatHudTest.battle()).scene);return f;
    }
    private Screen result(int balance, boolean offer) {
        Screen s=screen(t("挑战成功",.5f,.3f),t("退出副本",.39f,.85f),t("重新挑战",.60f,.85f),t("剩余"+balance,.60f,.91f),t("特征码:000000000",.91f,.98f));
        if(offer){s.words.add(t("单倍领取",.40f,.70f));s.words.add(t("60",.33f,.70f));}return s;
    }
    @Test public void ownedFightCanEndAtNearbyMobileInteractionWithoutSuccessBanner() throws Exception {
        DailyTacet f=fighting(new Memory());
        Screen near=screen(t("L.90",.58f,.94f),t("特征码:000000000",.91f,.98f),t("领取奖励",1613f/2400,459f/1080));
        GardenRunGuard guard=new GardenRunGuard(0);
        Action first=f.next(near);assertEquals("daily-tacet-open-claim",first.scene);assertEquals("CLAIM",f.stage());
        assertFalse(guard.ready(near,first,100));Action second=f.next(near);assertTrue(guard.ready(near,second,200));
        f.applied(second);assertEquals("daily-tacet-open-claim",f.next(near).scene);assertEquals(0,f.spent());assertEquals(0,f.reserved());
        assertEquals("daily-tacet-price-check",f.next(result(240,true)).scene);
        assertEquals("daily-tacet-claim-60",f.next(result(240,true)).scene);
        DailyTacet unowned=new DailyTacet();assertNull(unowned.next(near));
        DailyTacet wrong=fighting(new Memory());near.words.set(1,t("特征码:111111111",.91f,.98f));
        assertEquals(Kind.STOP,wrong.next(near).kind);
    }
    @Test public void ignoredRewardPromptTouchesRetryWithoutReservingAndStopAtFour() throws Exception {
        DailyTacet f=fighting(new Memory());
        Screen near=screen(t("L.90",.58f,.94f),t("特征码:000000000",.91f,.98f),t("领取奖励",1613f/2400,459f/1080));
        GardenRunGuard guard=new GardenRunGuard(0);
        for(int n=0;n<4;n++) {
            Action a=f.next(near);assertEquals("daily-tacet-open-claim",a.scene);
            assertFalse(guard.ready(near,a,n*200));assertTrue(guard.ready(near,f.next(near),n*200+100));f.applied(a);
            assertEquals(0,f.reserved());assertEquals(0,f.spent());
        }
        assertFalse(guard.ready(near,f.next(near),900));
        assertThrows(IllegalStateException.class,()->guard.ready(near,f.next(near),1000));
    }
    @Test public void reserveBeforeTouchAndVerifyTwiceBeforeRepeatingFourTimes() throws Exception {
        Memory memory=new Memory();DailyTacet f=fighting(memory);
        for(int n=0;n<4;n++) {
            Screen offer=result(240-60*n,true);
            assertEquals("daily-tacet-price-check",f.next(offer).scene);
            Action claim=f.next(offer);assertEquals("daily-tacet-claim-60",claim.scene);
            f.beforeInput(claim,NOW);assertEquals(60,new DailyStaminaLedger(memory,"123456789",NOW).reserved());f.applied(claim);
            assertEquals(Kind.WAIT,f.next(offer).kind);
            assertEquals(Kind.WAIT,f.next(result(180-60*n,false)).kind);
            Action follow=f.next(result(180-60*n,false));assertEquals(n==3?"daily-tacet-exit":"daily-tacet-repeat",follow.scene);
            assertEquals(60*(n+1),f.spent());assertEquals(0,f.reserved());f.applied(follow);
            if(n<3)assertEquals("daily-tacet-combat",f.next(CombatHudTest.battle()).scene);
        }
        assertTrue(f.exhausted());assertEquals(240,new DailyStaminaLedger(memory,"123456789",NOW).confirmed());
    }
    @Test public void ambiguousPriceWrongBalanceAndRestartNeverDuplicateClaim() throws Exception {
        Memory memory=new Memory();DailyTacet f=fighting(memory);Screen s=result(240,true);
        s.words.add(t("120",.36f,.70f));assertEquals(Kind.WAIT,f.next(s).kind);
        s.words.remove(s.words.size()-1);f.next(s);Action a=f.next(s);f.beforeInput(a,NOW);f.applied(a);
        assertEquals(Kind.WAIT,f.next(result(120,false)).kind);assertEquals(60,f.reserved());assertEquals(0,f.spent());
        DailyTacet restarted=new DailyTacet();restarted.attachLedger(new DailyStaminaLedger(memory,"123456789",NOW));
        assertTrue(restarted.pendingSettlement());assertEquals(Kind.WAIT,restarted.next(s).kind);
    }
    @Test public void failureRetriesBoundedWithoutSpending() throws Exception {
        DailyTacet f=fighting(new Memory());Screen fail=screen(t("挑战失败",.5f,.3f),t("退出副本",.39f,.85f),t("重新挑战",.60f,.85f));
        for(int n=0;n<2;n++) {Action a=f.next(fail);assertEquals("daily-tacet-retry",a.scene);f.applied(a);assertEquals(Kind.WAIT,f.next(fail).kind);f.next(CombatHudTest.battle());}
        assertEquals("daily-tacet-exit",f.next(fail).scene);assertEquals(0,f.spent());
    }
    @Test public void freshRunInsideArenaBacksOutRatherThanUsingAnUnverifiedTeam() {
        Action a=new DailyRules(new DailyTacet()).next(CombatHudTest.battle());
        assertEquals("daily-unowned-tacet-back",a.scene);assertEquals(Kind.BACK,a.kind);
    }
    @Test public void recordedArenaExitPromptNeedsAllAnchorsAndConfirmedAccount() {
        DailyRules r=new DailyRules(new DailyTacet());Screen s=screen(t("提示",.303f,.32f),t("确认离开",.50f,.478f),
                t("重新挑战",.37f,.623f),t("确认",.627f,.624f),t("特征码:123456789",.91f,.98f));
        assertEquals(Kind.WAIT,r.next(s).kind);assertEquals("daily-unowned-tacet-exit",r.next(s).scene);
        s.words.remove(1);assertEquals(Kind.WAIT,r.next(s).kind);
    }
    @Test public void firstFailureAfterLoadingCanRetryEvenIfBattleHudWasMissed() throws Exception {
        DailyTacet f=new DailyTacet();f.attachLedger(new DailyStaminaLedger(new Memory(),"123456789",NOW));f.freezePlan(240,0);f.selected();f.entryTeamConfirmed();
        f.applied(f.next(screen(t("开启挑战",.86f,.91f),t("快速编队",.73f,.91f))));
        Action retry=f.next(screen(t("挑战失败",.5f,.3f),t("退出副本",.39f,.85f),t("重新挑战",.60f,.85f)));
        assertEquals("daily-tacet-retry",retry.scene);
    }
    private Screen revival(String uid) {
        return screen(t("选择复苏物品",.30f,.195f),t("确认",.627f,.624f),t("特征码:"+uid,.91f,.98f));
    }
    private Screen exitPrompt(String uid) {
        return screen(t("提示",.303f,.32f),t("确认离开",.50f,.478f),t("重新挑战",.37f,.623f),
                t("确认",.627f,.624f),t("特征码:"+uid,.91f,.98f));
    }
    private Screen outside(String uid) {
        return screen(t("Lv.90",.58f,.937f),t("31443/31443",.495f,.937f),t("65ms",.92f,.116f),
                t("特征码:"+uid,.914f,.98f));
    }
    private void cancelAndExit(DailyTacet f) {
        Action cancel=f.next(revival("000000000"));
        assertEquals(Kind.BACK,cancel.kind);assertEquals("daily-tacet-recover-cancel",cancel.scene);f.applied(cancel);
        assertTrue(f.recovering());assertEquals("RECOVER_EXIT",f.stage());
        Action exit=f.next(exitPrompt("000000000"));
        assertEquals(Kind.TAP,exit.kind);assertEquals("daily-tacet-recover-exit",exit.scene);f.applied(exit);
        assertEquals("RECOVER_HEAL",f.stage());
    }
    private void heal(DailyTacet f) {
        assertEquals("daily-tacet-recover-heal",f.next(outside("000000000"),true).scene);
        assertEquals("daily-tacet-recovered",f.next(outside("000000000"),true).scene);
        assertEquals("NAVIGATION",f.stage());assertFalse(f.finished());assertFalse(f.recovering());
    }
    private void reenter(DailyTacet f) {
        f.selected();Screen entry=screen(t("开启挑战",.86f,.91f),t("快速编队",.73f,.91f));
        assertEquals("daily-tacet-team",f.next(entry).scene);
        f.entryTeamConfirmed();f.applied(f.next(entry));
        assertEquals("daily-tacet-combat",f.next(CombatHudTest.battle()).scene);
    }
    @Test public void confirmedAutomaticDeathSwitchUsesExistingRecoveryAndBound() throws Exception {
        DailyTacet f=fighting(new Memory());
        assertThrows(IllegalStateException.class,()->f.confirmedPartyDeath("111111111"));
        f.confirmedPartyDeath("000000000");assertTrue(f.recovering());
        assertEquals("daily-tacet-recover-menu",f.next(CombatHudTest.battle()).scene);
        assertThrows(IllegalStateException.class,()->f.confirmedPartyDeath("000000000"));
    }
    @Test public void ownedDeathCancelsWithoutItemsExitsAndRequiresRealPartyHealing() throws Exception {
        DailyTacet f=fighting(new Memory());
        Action cancel=f.next(revival("000000000"));assertEquals(Kind.BACK,cancel.kind);f.applied(cancel);
        assertEquals("daily-tacet-recover-menu",f.next(CombatHudTest.battle()).scene);
        Screen prompt=exitPrompt("000000000");prompt.words.add(t("Lv.90",.58f,.937f));
        assertEquals("daily-tacet-recover-exit",f.next(prompt,true).scene);
        f.applied(f.next(prompt));
        // A full current-character HP number alone is not a healed three-member party.
        assertEquals("daily-tacet-recover-heal",f.next(outside("000000000")).scene);
        assertEquals("daily-tacet-recover-heal",f.next(outside("000000000"),true).scene);
        assertEquals("daily-tacet-recover-heal",f.next(outside("000000000"),false).scene);
        heal(f);assertTrue(f.consumeRecovered());assertFalse(f.consumeRecovered());reenter(f);
    }
    @Test public void recoveryNeverUsesUnknownConfirmationOrArenaAsHealingEvidence() throws Exception {
        DailyTacet f=fighting(new Memory());f.applied(f.next(revival("000000000")));
        Screen partial=exitPrompt("000000000");partial.words.remove(1);
        assertEquals(Kind.WAIT,f.next(partial,true).kind);assertEquals("RECOVER_EXIT",f.stage());
        f.applied(f.next(exitPrompt("000000000")));
        assertEquals("daily-tacet-recover-heal",f.next(CombatHudTest.battle(),true).scene);
        assertEquals("RECOVER_HEAL",f.stage());
        Screen oneLabel=outside("000000000");oneLabel.words.add(t("03:56:58",.51f,.14f));
        assertEquals("daily-tacet-recover-heal",f.next(oneLabel,true).scene);
        assertEquals("daily-tacet-recover-wait",f.next(screen(),true).scene);
        heal(f);
    }
    @Test public void recoveryRejectsUnownedMissingOrChangedUidAndPaidPrompts() throws Exception {
        DailyTacet f=fighting(new Memory());
        assertEquals(Kind.STOP,new DailyRules(new DailyTacet()).next(revival("000000000")).kind);
        DailyTacet entry=new DailyTacet();entry.selected();assertEquals(Kind.STOP,entry.next(revival("000000000")).kind);
        Screen noUid=revival("000000000");noUid.words.remove(2);
        assertEquals(Kind.STOP,f.next(noUid).kind);
        Screen wrongRegion=revival("000000000");wrongRegion.words.set(0,t("选择复苏物品",.5f,.5f));
        wrongRegion.words.add(t("挑战失败",.5f,.3f));wrongRegion.words.add(t("重新挑战",.60f,.85f));
        assertEquals(Kind.STOP,f.next(wrongRegion).kind);
        assertEquals(Kind.STOP,f.next(revival("123456789")).kind);
        Screen purchase=revival("000000000");purchase.words.add(t("购买药品",.5f,.5f));
        assertEquals(Kind.STOP,f.next(purchase).kind);
        f.applied(f.next(revival("000000000")));
        assertEquals(Kind.STOP,f.next(exitPrompt("123456789")).kind);
        f.applied(f.next(exitPrompt("000000000")));
        assertEquals(Kind.STOP,f.next(outside("123456789"),true).kind);
        Screen refill=outside("000000000");refill.words.add(t("补充波片",.5f,.5f));
        assertEquals(Kind.STOP,f.next(refill,true).kind);
    }
    @Test public void deathWithOutstandingClaimNeverCancelsOrRetries() throws Exception {
        DailyTacet f=fighting(new Memory());Screen offer=result(240,true);f.next(offer);
        Action claim=f.next(offer);f.beforeInput(claim,NOW);f.applied(claim);
        assertEquals(Kind.STOP,f.next(revival("000000000")).kind);assertEquals(60,f.reserved());
        assertFalse(f.recovering());assertEquals(0,f.spent());
    }
    @Test public void deathRecoveryLimitIsTwoForTheWholeRun() throws Exception {
        DailyTacet f=fighting(new Memory());
        for(int n=0;n<2;n++){cancelAndExit(f);heal(f);reenter(f);}
        assertEquals(Kind.STOP,f.next(revival("000000000")).kind);
        assertFalse(f.recovering());assertEquals(0,f.spent());
    }
    @Test public void recoveryPreservesLedgerAndFailureBudgetInsteadOfBindingAgain() throws Exception {
        Memory memory=new Memory();DailyTacet f=fighting(memory);
        Screen fail=screen(t("挑战失败",.5f,.3f),t("退出副本",.39f,.85f),t("重新挑战",.60f,.85f));
        f.applied(f.next(fail));f.next(CombatHudTest.battle());
        Screen offer=result(240,true);f.next(offer);Action claim=f.next(offer);f.beforeInput(claim,NOW);f.applied(claim);
        f.next(result(180,false));f.applied(f.next(result(180,false)));f.next(CombatHudTest.battle());
        String saved=memory.value;cancelAndExit(f);heal(f);
        assertEquals(saved,memory.value);assertEquals(60,f.spent());assertEquals(60,f.dailySpent());assertEquals(0,f.reserved());
        assertEquals(180,f.remainingPlan());assertEquals(240,f.plan().plannedBudget);
        assertThrows(IllegalStateException.class,()->f.freezePlan(240,0));
        assertThrows(IllegalStateException.class,()->f.attachLedger(new DailyStaminaLedger(new Memory(),"123456789",NOW)));
        reenter(f);Action retry=f.next(fail);assertEquals("daily-tacet-retry",retry.scene);f.applied(retry);f.next(CombatHudTest.battle());
        assertEquals("daily-tacet-exit",f.next(fail).scene);
    }
    @Test public void dailyRecoveryClearsNavigationAndRequiresFreshRosterWithoutStartingRewards() throws Exception {
        DailyTacet f=fighting(new Memory());DailyRules r=new DailyRules(f);r.teamConfirmed();
        r.observeUid(outside("000000000"));r.observeUid(outside("000000000"));
        r.applied(r.next(revival("000000000")));r.applied(r.next(exitPrompt("000000000")));
        assertEquals("daily-tacet-recover-heal",r.next(outside("000000000"),true).scene);
        assertEquals("daily-tacet-recovered",r.next(outside("000000000"),true).scene);
        assertFalse(r.rewards.started());assertFalse(f.finished());
        Screen terminal=screen(t("终端",.1f,.1f),t("地图",.2f,.2f),t("编队",.5f,.5f),t("索拉指南",.7f,.5f));
        assertEquals("daily-open-team",r.next(terminal).scene);
        assertEquals("daily-team",r.next(screen(t("编队",.1f,.1f),t("出战",.8f,.8f))).scene);
    }
    @Test public void skippedOverlayLoadingAndUnsupportedFramesBreakConsecutiveHealing() throws Exception {
        DailyTacet f=fighting(new Memory());DailyRules r=new DailyRules(f);r.teamConfirmed();
        r.observeUid(outside("000000000"));r.observeUid(outside("000000000"));
        r.applied(r.next(revival("000000000")));r.applied(r.next(exitPrompt("000000000")));
        Screen overlay=outside("000000000");overlay.words.add(t("保存现场",.1f,.1f));
        overlay.words.add(t("开跑",.2f,.2f));overlay.words.add(t("识别",.3f,.3f));
        Screen loading=screen(t("50%",.93f,.94f),t("载入中的画面不包含完整账号与队伍证据所以继续等待",.4f,.85f));
        Screen layout=new Screen(outside("000000000").words,new int[3],1.8f);
        for(Screen bad:Arrays.asList(overlay,loading,layout)){
            assertEquals("daily-tacet-recover-heal",r.next(outside("000000000"),true).scene);
            r.next(bad,true);
            assertEquals("daily-tacet-recover-heal",r.next(outside("000000000"),true).scene);
            r.next(outside("000000000"),false);
        }
        assertEquals("daily-tacet-recover-heal",r.next(outside("000000000"),true).scene);
        assertEquals("daily-tacet-recovered",r.next(outside("000000000"),true).scene);
    }
    @Test public void dailyUidConflictStopsRecoveryAndBreaksHealingSequence() throws Exception {
        DailyTacet f=fighting(new Memory());DailyRules r=new DailyRules(f);r.teamConfirmed();
        r.observeUid(outside("000000000"));r.observeUid(outside("000000000"));
        r.applied(r.next(revival("000000000")));r.applied(r.next(exitPrompt("000000000")));
        assertEquals("daily-tacet-recover-heal",r.next(outside("000000000"),true).scene);
        assertEquals(Kind.STOP,r.next(outside("123456789"),true).kind);
        assertEquals("daily-tacet-recover-heal",r.next(outside("000000000"),true).scene);
        assertEquals("daily-tacet-recovered",r.next(outside("000000000"),true).scene);
    }
    @Test public void unhealedPartyOpensMapThenTravelsOnceAndVerifiesRealHealing() throws Exception {
        DailyTacet f=fighting(new Memory());cancelAndExit(f);Screen world=outside("000000000");
        for(int n=0;n<2;n++)assertEquals("daily-tacet-recover-heal",f.next(world).scene);
        Action open=f.next(world);assertEquals("daily-tacet-recover-map",open.scene);f.applied(open);
        Screen map=screen(t("切换地图",.82f,.93f),t("特征码:000000000",.91f,.98f));
        f.recoveryFrame(new PortraitMatcher.Match("nexus",.99,-1,.2f,.4f));
        Action nexus=f.next(map);assertEquals("daily-tacet-recover-nexus",nexus.scene);assertEquals(.2f,nexus.x,.001);f.applied(nexus);
        // Pixel evidence is consumed per frame; an old match cannot survive into another map observation.
        assertEquals("daily-tacet-recover-zoom",f.next(map).scene);
        Screen detail=screen(t("中枢信标",.80f,.05f),t("快速旅行",.84f,.92f),t("特征码:000000000",.91f,.98f));
        Action travel=f.next(detail);assertEquals("daily-tacet-recover-travel",travel.scene);f.applied(travel);
        assertEquals(Kind.WAIT,f.next(detail).kind);
        for(int n=0;n<5;n++)assertEquals("daily-tacet-recover-heal",f.next(world).scene);
        assertEquals(0,f.spent());assertEquals(0,f.reserved());assertTrue(f.recovering());heal(f);reenter(f);
    }
    @Test public void recoveryMapSearchIsBoundedAndTravelRequiresNamedNexusAndSameUid() throws Exception {
        DailyTacet f=fighting(new Memory());cancelAndExit(f);
        Screen map=screen(t("切换地图",.82f,.93f),t("特征码:000000000",.91f,.98f));
        for(int n=0;n<2;n++){Action zoom=f.next(map);assertEquals("daily-tacet-recover-zoom",zoom.scene);f.applied(zoom);}
        assertEquals(Kind.WAIT,f.next(map).kind);
        Screen beacon=screen(t("小型信标",.80f,.05f),t("快速旅行",.84f,.92f),t("特征码:000000000",.91f,.98f));
        assertEquals(Kind.WAIT,f.next(beacon).kind);
        beacon.words.set(0,t("中枢信标",.80f,.05f));beacon.words.set(2,t("特征码:123456789",.91f,.98f));
        assertEquals(Kind.STOP,f.next(beacon).kind);
        beacon.words.remove(2);assertEquals(Kind.WAIT,f.next(beacon).kind);
        assertTrue(f.recovering());assertFalse(f.finished());assertEquals(0,f.reserved());
    }
    private Screen lootOffer(int balance) {
        Screen s=result(balance,true);s.words.remove(0);return s;
    }
    private Screen lootResult(int balance) {
        Screen s=result(balance,false);s.words.remove(0);return s;
    }
    @Test public void treasureHandoffRequiresThisRunsObservedBattleSameUidAndNoPendingClaim() throws Exception {
        DailyTacet unowned=new DailyTacet();
        assertThrows(IllegalStateException.class,()->unowned.treasureConfirmed("000000000"));
        unowned.selected();assertThrows(IllegalStateException.class,()->unowned.treasureConfirmed("000000000"));
        DailyTacet loading=new DailyTacet();loading.attachLedger(new DailyStaminaLedger(new Memory(),"123456789",NOW));loading.freezePlan(240,0);loading.selected();loading.entryTeamConfirmed();
        loading.applied(loading.next(screen(t("开启挑战",.86f,.91f),t("快速编队",.73f,.91f))));
        assertThrows(IllegalStateException.class,()->loading.treasureConfirmed("000000000"));
        DailyTacet f=fighting(new Memory());
        assertThrows(IllegalStateException.class,()->f.treasureConfirmed(null));
        assertThrows(IllegalStateException.class,()->f.treasureConfirmed("123456789"));
        f.treasureConfirmed("000000000");assertTrue(f.seekingTreasure());assertFalse(f.finished());
        assertEquals("daily-tacet-treasure",f.next(outside("000000000")).scene);
        assertEquals(0,f.spent());assertEquals(0,f.reserved());
        Screen offer=lootOffer(240);f.next(offer);Action claim=f.next(offer);f.beforeInput(claim,NOW);
        assertThrows(IllegalStateException.class,()->f.treasureConfirmed("000000000"));assertEquals(60,f.reserved());
        DailyStaminaLedger pending=new DailyStaminaLedger(new Memory(),"123456789",NOW);
        DailyTacet blocked=new DailyTacet();blocked.attachLedger(pending);blocked.freezePlan(240,0);reenter(blocked);pending.reserve(240,60,false,NOW);
        assertThrows(IllegalStateException.class,()->blocked.treasureConfirmed("000000000"));
        assertEquals("FIGHT",blocked.stage());assertEquals(60,blocked.reserved());
    }
    @Test public void disappearingObjectiveAloneNeverAuthorizesTreasureOrClaim() throws Exception {
        DailyTacet f=fighting(new Memory());
        assertEquals(Kind.WAIT,f.next(outside("000000000")).kind);assertFalse(f.seekingTreasure());
        assertEquals(Kind.WAIT,f.next(lootOffer(240)).kind);assertEquals("FIGHT",f.stage());
        assertFalse(f.finished());assertEquals(0,f.reserved());
    }
    @Test public void ownedTreasureClaimsWithoutSuccessTextAndRepeatsOnlyAfterSettlementAndBothButtons() throws Exception {
        DailyTacet f=fighting(new Memory());f.treasureConfirmed("000000000");
        Screen offer=lootOffer(240);
        assertEquals("daily-tacet-price-check",f.next(offer).scene);assertFalse(f.seekingTreasure());
        Action claim=f.next(offer);assertEquals("daily-tacet-claim-60",claim.scene);assertFalse(f.finished());
        f.beforeInput(claim,NOW);f.applied(claim);
        assertEquals("daily-tacet-settle-wait",f.next(lootResult(240)).scene);assertEquals(60,f.reserved());
        Screen partial=lootResult(180);partial.words.remove(1);
        assertEquals(Kind.WAIT,f.next(partial).kind);
        assertEquals("daily-tacet-result-wait",f.next(partial).scene);
        assertEquals(60,f.spent());assertEquals(0,f.reserved());assertFalse(f.finished());
        Screen otherPartial=lootResult(180);otherPartial.words.remove(0);
        assertEquals("daily-tacet-result-wait",f.next(otherPartial).scene);
        Action repeat=f.next(lootResult(180));assertEquals("daily-tacet-repeat",repeat.scene);f.applied(repeat);
        assertEquals("LOADING",f.stage());assertFalse(f.seekingTreasure());assertFalse(f.finished());
        assertEquals(Kind.WAIT,f.next(lootOffer(180)).kind);assertEquals(0,f.reserved());
    }
    @Test public void treasureAndClaimNeverAcceptMissingUidWrongCostOrForeignAccount() throws Exception {
        DailyTacet f=fighting(new Memory());f.treasureConfirmed("000000000");
        Screen incomplete=lootOffer(240);incomplete.words.remove(3);
        assertEquals("daily-tacet-account-wait",f.next(incomplete).scene);assertTrue(f.seekingTreasure());
        Screen wrong=lootOffer(240);wrong.words.set(5,t("120",.33f,.70f));
        assertEquals("daily-tacet-treasure",f.next(wrong).scene);assertEquals(0,f.reserved());
        Screen foreign=lootOffer(240);foreign.words.set(3,t("特征码:123456789",.91f,.98f));
        assertEquals(Kind.STOP,f.next(foreign).kind);
        assertEquals("daily-tacet-price-check",f.next(lootOffer(240)).scene);
        assertEquals("daily-tacet-account-wait",f.next(incomplete).scene);
        assertEquals("daily-tacet-price-check",f.next(lootOffer(240)).scene);
    }
    @Test public void noFrozenPlanCannotStartBattleOrAuthorizeClaimInput() throws Exception {
        DailyTacet f=new DailyTacet();f.selected();f.entryTeamConfirmed();
        Screen entry=screen(t("开启挑战",.86f,.91f),t("快速编队",.73f,.91f));
        Action start=new Action(Kind.TAP,"daily-tacet-start","",.86f,.91f);
        Action claim=new Action(Kind.TAP,"daily-tacet-claim-60","",.4f,.7f);
        assertEquals(Kind.STOP,f.next(entry).kind);assertThrows(IllegalStateException.class,()->f.beforeInput(start,NOW));
        assertThrows(IllegalStateException.class,()->f.beforeInput(claim,NOW));
        f.attachLedger(new DailyStaminaLedger(new Memory(),"123456789",NOW));
        assertNull(f.plan());assertEquals(0,f.remainingPlan());assertFalse(f.exhausted());
        assertEquals(Kind.STOP,f.next(entry).kind);
        f.applied(start);assertEquals(Kind.STOP,f.next(CombatHudTest.battle()).kind);assertEquals(0,f.reserved());
    }
    @Test public void startRetryAndRepeatRecheckTheFourAmGameDayAtInputTime() throws Exception {
        long beforeReset=java.time.Instant.parse("2026-11-10T19:59:59.999Z").toEpochMilli();
        Memory memory=new Memory();
        DailyTacet f=new DailyTacet();f.attachLedger(new DailyStaminaLedger(memory,"000000000",beforeReset));f.freezePlan(240,0);
        DailyStaminaPlan frozen=f.plan();
        for(String scene:new String[]{"daily-tacet-start","daily-tacet-retry","daily-tacet-repeat"}){
            Action action=new Action(Kind.TAP,scene,"",.6f,.85f);
            f.beforeInput(action,beforeReset);
            assertThrows(scene,IllegalStateException.class,()->f.beforeInput(action,beforeReset+1));
            assertThrows(scene,IllegalStateException.class,()->f.beforeInput(action,beforeReset-86400000));
            assertSame(frozen,f.plan());assertEquals(240,f.remainingPlan());
            assertEquals(0,f.spent());assertEquals(0,f.reserved());assertNull(memory.value);
        }
    }
    @Test public void planFreezesOnlyOnceAfterLedgerAndNeverConsumesOrDiscardsPending() throws Exception {
        DailyTacet f=new DailyTacet();assertThrows(IllegalStateException.class,()->f.freezePlan(240,0));
        DailyStaminaLedger ledger=new DailyStaminaLedger(new Memory(),"123456789",NOW);f.attachLedger(ledger);
        ledger.reserve(240,60,false,NOW);
        assertThrows(IllegalStateException.class,()->f.freezePlan(240,0));assertNull(f.plan());assertEquals(60,f.reserved());
        DailyTacet ready=fighting(new Memory());DailyStaminaPlan frozen=ready.plan();
        assertThrows(IllegalStateException.class,()->ready.freezePlan(180,0));assertSame(frozen,ready.plan());
        assertEquals(240,ready.remainingPlan());assertEquals(0,ready.spent());
    }
    @Test public void frozen180StopsAfterThreeClaimsEvenWithSixtyOrdinaryAndDailyAllowanceLeft() throws Exception {
        DailyTacet f=fighting(new Memory(),180);
        for(int n=0;n<3;n++){
            f.treasureConfirmed("000000000");Screen offer=lootOffer(240-60*n);
            assertEquals("daily-tacet-price-check",f.next(offer).scene);
            Action claim=f.next(offer);assertEquals("daily-tacet-claim-60",claim.scene);f.beforeInput(claim,NOW);f.applied(claim);
            f.next(lootResult(180-60*n));Action result=f.next(lootResult(180-60*n));
            assertEquals(n==2?"daily-tacet-exit":"daily-tacet-repeat",result.scene);
            if(n<2){f.beforeInput(result,NOW);f.applied(result);assertEquals("daily-tacet-combat",f.next(CombatHudTest.battle()).scene);}
        }
        assertEquals(180,f.spent());assertEquals(180,f.dailySpent());assertEquals(0,f.remainingPlan());assertTrue(f.exhausted());
        assertEquals(0,f.reserved());assertEquals(180,f.plan().plannedBudget);
        Action repeat=new Action(Kind.TAP,"daily-tacet-repeat","",.60f,.85f);
        assertThrows(IllegalStateException.class,()->f.beforeInput(repeat,NOW));
    }
    @Test public void resumedDailyTargetUsesExistingConfirmedSpendingWithoutResettingLedger() throws Exception {
        Memory memory=new Memory();DailyStaminaLedger ledger=new DailyStaminaLedger(memory,"123456789",NOW);
        for(int balance:new int[]{240,180}){ledger.reserve(balance,60,false,NOW);ledger.confirm(balance-60);}
        String saved=memory.value;DailyTacet resumed=new DailyTacet();resumed.attachLedger(new DailyStaminaLedger(memory,"123456789",NOW));
        resumed.freezePlan(60,0);assertEquals(60,resumed.plan().plannedBudget);assertEquals(60,resumed.remainingPlan());
        assertEquals(120,resumed.dailySpent());assertEquals(0,resumed.spent());assertEquals(saved,memory.value);
        ledger.reserve(120,60,false,NOW);ledger.confirm(60);
        DailyTacet complete=new DailyTacet();complete.attachLedger(new DailyStaminaLedger(memory,"123456789",NOW));complete.freezePlan(0,0);
        assertEquals(0,complete.remainingPlan());assertTrue(complete.exhausted());assertEquals(180,complete.dailySpent());
    }
    private DailyTacet unfunded() throws Exception {
        DailyTacet f=new DailyTacet();f.attachLedger(new DailyStaminaLedger(new Memory(),"000000000",NOW));
        f.freezePlan(130,110);f.selected();f.entryTeamConfirmed();return f;
    }
    private Screen entry(){return screen(t("开启挑战",.86f,.91f),t("快速编队",.73f,.91f));}
    private void fund(DailyTacet f){
        f.beforeReserveExchange("000000000",110,NOW);
        assertFalse(f.observeFunding("000000000",240,0,NOW+100,NOW+120));
        assertTrue(f.observeFunding("000000000",240,0,NOW+200,NOW+220));
    }
    @Test public void plannedReserveAloneCannotStartOrClaimUntilTwoPostExchangeBalanceFrames() throws Exception {
        DailyTacet f=unfunded();assertFalse(f.funded());assertFalse(f.exhausted());assertEquals(Kind.STOP,f.next(entry()).kind);
        Action start=new Action(Kind.TAP,"daily-tacet-start","",.86f,.91f);
        Action claim=new Action(Kind.TAP,"daily-tacet-claim-60","",.4f,.7f);
        assertThrows(IllegalStateException.class,()->f.beforeInput(start,NOW));
        assertThrows(IllegalStateException.class,()->f.beforeInput(claim,NOW));
        assertFalse(f.observeFunding("000000000",240,0,NOW-100,NOW-80));
        fund(f);assertTrue(f.funded());assertEquals(0,f.spent());assertEquals(0,f.reserved());
        Action allowed=f.next(entry());assertEquals("daily-tacet-start",allowed.scene);f.beforeInput(allowed,NOW);f.applied(allowed);
        assertEquals("daily-tacet-combat",f.next(CombatHudTest.battle()).scene);f.treasureConfirmed("000000000");
        f.next(lootOffer(240));Action allowedClaim=f.next(lootOffer(240));f.beforeInput(allowedClaim,NOW);assertEquals(60,f.reserved());
        assertTrue(f.funded());assertThrows(IllegalStateException.class,()->f.beforeInput(start,NOW));
    }
    @Test public void fundingRequiresExactBothBalancesAndResetsAfterEitherMismatch() throws Exception {
        DailyTacet f=unfunded();f.beforeReserveExchange("000000000",110,NOW);
        assertFalse(f.observeFunding("000000000",240,0,NOW+100,NOW+110));
        assertFalse(f.observeFunding("000000000",240,1,NOW+200,NOW+210));
        assertFalse(f.observeFunding("000000000",240,0,NOW+300,NOW+310));
        assertFalse(f.observeFunding("000000000",241,0,NOW+400,NOW+410));
        assertFalse(f.observeFunding("000000000",240,0,NOW+500,NOW+510));
        assertTrue(f.observeFunding("000000000",240,0,NOW+600,NOW+610));
    }
    @Test public void fundingRejectsPreSubmissionDuplicateStaleFutureAndUnknownFrames() throws Exception {
        DailyTacet f=unfunded();f.beforeReserveExchange("000000000",110,NOW+100);
        assertFalse(f.observeFunding("000000000",240,0,NOW+99,NOW+120));
        assertFalse(f.observeFunding("000000000",240,0,NOW+150,NOW+200));
        assertFalse(f.observeFunding("000000000",240,0,NOW+150,NOW+210));
        assertFalse(f.observeFunding("000000000",240,0,NOW+220,NOW+230));
        assertFalse(f.observeFunding(null,240,0,NOW+240,NOW+250));
        assertFalse(f.observeFunding("000000000",240,0,NOW+260,NOW+270));f.unconfirmedFundingFrame();
        assertFalse(f.observeFunding("000000000",240,0,NOW+280,NOW+290));
        assertFalse(f.observeFunding("000000000",240,0,NOW+300,NOW+4000));
        assertFalse(f.observeFunding("000000000",240,0,NOW+4001,NOW+4000));
        assertThrows(IllegalStateException.class,()->f.observeFunding("000000000",240,0,NOW+3990,NOW+3999));
        assertFalse(f.observeFunding("000000000",240,0,NOW+4100,NOW+4100));
        assertTrue(f.observeFunding("000000000",240,0,NOW+4200,NOW+4200));
    }
    @Test public void exchangeConfirmationIsSingleExactSameAccountAndSameGameDay() throws Exception {
        DailyTacet f=unfunded();
        assertThrows(IllegalStateException.class,()->f.beforeReserveExchange("123456789",110,NOW));
        for(int amount:new int[]{0,109,111,240})assertThrows(IllegalStateException.class,()->f.beforeReserveExchange("000000000",amount,NOW));
        assertThrows(IllegalStateException.class,()->f.beforeReserveExchange("000000000",110,NOW+86400000));
        f.beforeReserveExchange("000000000",110,NOW);
        assertThrows(IllegalStateException.class,()->f.beforeReserveExchange("000000000",110,NOW));
        assertFalse(f.observeFunding("000000000",240,0,NOW+100,NOW+120));
        assertThrows(IllegalStateException.class,()->f.observeFunding("123456789",240,0,NOW+200,NOW+220));
        assertFalse(f.observeFunding("000000000",240,0,NOW+300,NOW+320));
        assertThrows(IllegalStateException.class,()->f.observeFunding("000000000",240,0,NOW+400,NOW+86400000));
        assertFalse(f.funded());assertEquals(0,f.reserved());assertEquals(0,f.spent());
    }
    @Test public void pendingOrLedgerChangesCannotBeBypassedDuringReserveFunding() throws Exception {
        DailyStaminaLedger ledger=new DailyStaminaLedger(new Memory(),"000000000",NOW);
        DailyTacet f=new DailyTacet();f.attachLedger(ledger);f.freezePlan(130,110);
        f.beforeReserveExchange("000000000",110,NOW);ledger.reserve(240,60,false,NOW);
        assertThrows(IllegalStateException.class,()->f.observeFunding(null,240,0,NOW+100,NOW+120));
        assertThrows(IllegalStateException.class,()->f.observeFunding("000000000",240,0,NOW+200,NOW+220));
        assertFalse(f.funded());assertEquals(60,f.reserved());
        ledger.confirm(180);
        assertThrows(IllegalStateException.class,()->f.observeFunding("000000000",240,0,NOW+300,NOW+320));
        assertFalse(f.funded());assertEquals(60,f.dailySpent());
    }
    @Test public void fundingWithoutExchangeIsReadyAndSuccessfulFundingSurvivesDeathRecovery() throws Exception {
        DailyTacet ordinary=fighting(new Memory());assertTrue(ordinary.funded());
        assertThrows(IllegalStateException.class,()->ordinary.beforeReserveExchange("123456789",1,NOW));
        DailyTacet f=unfunded();fund(f);DailyStaminaPlan frozen=f.plan();
        Action start=f.next(entry());f.beforeInput(start,NOW);f.applied(start);f.next(CombatHudTest.battle());
        cancelAndExit(f);heal(f);assertTrue(f.funded());assertSame(frozen,f.plan());assertEquals(240,f.remainingPlan());
        f.unconfirmedFundingFrame();assertTrue(f.funded());reenter(f);
    }
    @Test public void resumedFundingOnlyExchangesTheDailyRemainderNotAnother240() throws Exception {
        DailyStaminaLedger ledger=new DailyStaminaLedger(new Memory(),"000000000",NOW);
        for(int balance:new int[]{240,180}){ledger.reserve(balance,60,false,NOW);ledger.confirm(balance-60);}
        DailyTacet f=new DailyTacet();f.attachLedger(ledger);f.freezePlan(90,30);
        assertEquals(120,f.plan().plannedBudget);assertEquals(30,f.plan().exchangeAmount);assertFalse(f.funded());
        f.beforeReserveExchange("000000000",30,NOW);
        assertFalse(f.observeFunding("000000000",240,0,NOW+100,NOW+120));
        assertFalse(f.observeFunding("000000000",120,0,NOW+200,NOW+220));
        assertTrue(f.observeFunding("000000000",120,0,NOW+300,NOW+320));assertEquals(120,f.dailySpent());
    }

    @Test public void recordedDev38PriceNowPrefersDoubleButItsHistoricalSingleResultDoesNotSettle120() throws Exception {
        Memory memory=new Memory();DailyTacet f=fighting(memory);f.treasureConfirmed("000000000");
        Screen[] frames=dev38ClaimFrames();
        long[] elapsed={0,3032,4398,6242,7385,9136,10821,12122,14202,15194,16829};
        String[] scenes={"daily-tacet-open-claim","daily-tacet-open-claim","daily-tacet-price-wait","daily-tacet-price-wait","daily-tacet-price-check","daily-tacet-claim-120","daily-tacet-claim-120","daily-tacet-settle-wait","daily-tacet-settle-wait","daily-tacet-settle-wait","daily-tacet-settle-wait"};
        GardenRunGuard guard=new GardenRunGuard(0);int opened=0,claimed=0,repeated=0;
        for(int i=0;i<frames.length;i++) {
            Action action=f.next(frames[i]);assertEquals("recorded frame "+i,scenes[i],action.scene);
            if(guard.ready(frames[i],action,elapsed[i])) {
                f.beforeInput(action,NOW+elapsed[i]);
                if(action.scene.equals("daily-tacet-open-claim"))opened++;
                if(action.scene.equals("daily-tacet-claim-120")) {
                    claimed++;assertEquals(120,new DailyStaminaLedger(memory,"123456789",NOW).reserved());
                    assertEquals(0,f.spent());
                    assertEquals(1555.5f,action.x*2400,.01f);assertEquals(672f,action.y*1080,.01f);
                }
                if(action.scene.equals("daily-tacet-repeat"))repeated++;
                f.applied(action);
            }
            assertEquals("confirmed frame "+i,0,f.spent());
            assertEquals("reserved frame "+i,i>=6?120:0,f.reserved());
        }
        // This capture really spent 60 under the old policy: retain its 180 balance verbatim as a negative case.
        assertEquals(1,opened);assertEquals(1,claimed);assertEquals(0,repeated);
        assertEquals("SETTLE",f.stage());
        DailyStaminaLedger saved=new DailyStaminaLedger(memory,"123456789",NOW);
        assertEquals(0,saved.confirmed());assertEquals(120,saved.reserved());
        DailyTacet restarted=new DailyTacet();restarted.attachLedger(saved);
        assertTrue(restarted.pendingSettlement());assertEquals(120,restarted.reserved());
        assertEquals("daily-tacet-settle-wait",f.next(CombatHudTest.battle()).scene);
        assertFalse(f.finished());assertEquals(0,f.spent());
    }

    private Screen doubleOffer(int balance) {
        Screen s=result(balance,true);s.words.add(t("双倍领取",.65f,.70f));s.words.add(t("120",.58f,.70f));return s;
    }
    private void payAndSettle(DailyTacet f,Screen offer,int cost,int after) {
        assertEquals("daily-tacet-price-check",f.next(offer).scene);
        Action claim=f.next(offer);assertEquals("daily-tacet-claim-"+cost,claim.scene);
        f.beforeInput(claim,NOW);assertEquals(cost,f.reserved());f.applied(claim);
        assertEquals("daily-tacet-settle-wait",f.next(result(after,false)).scene);
    }
    @Test public void twoDoubleClaimsConsumeExactly240AndNeverSettleAnObservedSingleDeduction() throws Exception {
        Memory memory=new Memory();DailyTacet f=fighting(memory);
        for(int n=0;n<2;n++) {
            int before=240-120*n;Screen offer=doubleOffer(before);
            assertEquals("daily-tacet-price-check",f.next(offer).scene);
            Action claim=f.next(offer);assertEquals("daily-tacet-claim-120",claim.scene);
            f.beforeInput(claim,NOW);f.applied(claim);
            assertEquals(120,new DailyStaminaLedger(memory,"123456789",NOW).reserved());
            assertEquals("daily-tacet-settle-wait",f.next(result(before-60,false)).scene);
            assertEquals("daily-tacet-settle-wait",f.next(result(before-60,false)).scene);
            assertEquals(120,f.reserved());assertEquals(120*n,f.spent());
            assertEquals("daily-tacet-settle-wait",f.next(result(before-120,false)).scene);
            Action follow=f.next(result(before-120,false));
            assertEquals(n==0?"daily-tacet-repeat":"daily-tacet-exit",follow.scene);
            assertEquals(120*(n+1),f.spent());assertEquals(0,f.reserved());
            f.beforeInput(follow,NOW);f.applied(follow);
            if(n==0)assertEquals("daily-tacet-combat",f.next(CombatHudTest.battle()).scene);
        }
        assertEquals(240,f.dailySpent());assertEquals(0,f.remainingPlan());assertTrue(f.exhausted());
    }
    @Test public void frozen180Prefers120Then60EvenWhenCurrentBalanceAndDailyAllowanceStillPermit120() throws Exception {
        DailyTacet f=fighting(new Memory(),180);
        // A later larger balance never expands the already frozen 180 plan.
        payAndSettle(f,doubleOffer(240),120,120);
        Action repeat=f.next(result(120,false));assertEquals("daily-tacet-repeat",repeat.scene);
        f.beforeInput(repeat,NOW);f.applied(repeat);f.next(CombatHudTest.battle());
        assertEquals(60,f.remainingPlan());assertEquals(120,f.dailySpent());
        payAndSettle(f,doubleOffer(120),60,60);
        assertEquals("daily-tacet-exit",f.next(result(60,false)).scene);
        assertEquals(180,f.spent());assertEquals(0,f.remainingPlan());assertEquals(0,f.reserved());
    }
    @Test public void alreadyConfirmed120OnlyPermitsOneMoreDoubleWithoutResettingDailyLedger() throws Exception {
        Memory memory=new Memory();DailyStaminaLedger old=new DailyStaminaLedger(memory,"123456789",NOW);
        old.reserve(240,120,false,NOW);old.confirm(120);
        DailyTacet f=new DailyTacet();f.attachLedger(new DailyStaminaLedger(memory,"123456789",NOW));f.freezePlan(240,0);reenter(f);
        assertEquals(120,f.remainingPlan());payAndSettle(f,doubleOffer(240),120,120);
        assertEquals("daily-tacet-exit",f.next(result(120,false)).scene);
        assertEquals(120,f.spent());assertEquals(240,f.dailySpent());assertTrue(f.exhausted());
        assertThrows(IllegalStateException.class,()->f.beforeInput(new Action(Kind.TAP,"daily-tacet-repeat","",.60f,.85f),NOW));
    }
    @Test public void quoteChangesResetBothFramesAndStaleCostOrCoordinatesNeverAuthorizeInput() throws Exception {
        DailyTacet f=fighting(new Memory());Screen single=result(240,true),both=doubleOffer(240);
        assertEquals("daily-tacet-price-check",f.next(single).scene);
        Action oldSingle=f.next(single);assertEquals("daily-tacet-claim-60",oldSingle.scene);
        assertEquals("daily-tacet-price-check",f.next(both).scene);
        assertThrows(IllegalStateException.class,()->f.beforeInput(oldSingle,NOW));
        Action oldDouble=f.next(both);assertEquals("daily-tacet-claim-120",oldDouble.scene);
        Screen moved=doubleOffer(240);moved.words.set(7,t("双倍领取",.70f,.70f));moved.words.set(8,t("120",.63f,.70f));
        assertEquals("daily-tacet-price-check",f.next(moved).scene);
        Action fresh=f.next(moved);assertEquals("daily-tacet-claim-120",fresh.scene);
        assertThrows(IllegalStateException.class,()->f.beforeInput(oldDouble,NOW));
        assertEquals("daily-tacet-price-check",f.next(doubleOffer(180)).scene);
        assertEquals("daily-tacet-price-check",f.next(result(180,true)).scene);
        Action last=f.next(result(180,true));assertEquals("daily-tacet-claim-60",last.scene);
        assertThrows(IllegalStateException.class,()->f.beforeInput(fresh,NOW));
        f.beforeInput(last,NOW);assertEquals(60,f.reserved());
    }
    @Test public void ambiguousDoubleNeverFallsBackAndMissingDoubleRequiresCompleteSingleQuote() throws Exception {
        DailyTacet f=fighting(new Memory());
        for(int price:new int[]{60,119,121}) {
            Screen wrong=doubleOffer(240);wrong.words.set(8,t(Integer.toString(price),.58f,.70f));
            assertEquals(Kind.WAIT,f.next(wrong).kind);assertEquals(0,f.reserved());
        }
        Screen duplicatePrice=doubleOffer(240);duplicatePrice.words.add(t("120",.60f,.70f));
        assertEquals(Kind.WAIT,f.next(duplicatePrice).kind);
        Screen duplicateButton=doubleOffer(240);duplicateButton.words.add(t("双倍领取",.72f,.72f));
        assertThrows(IllegalStateException.class,()->f.next(duplicateButton));
        Screen badLabel=doubleOffer(240);badLabel.words.set(7,t("双信领取",.65f,.70f));
        assertEquals(Kind.WAIT,f.next(badLabel).kind);
        Screen orphanPrice=doubleOffer(240);orphanPrice.words.remove(7);
        assertEquals(Kind.WAIT,f.next(orphanPrice).kind);
        assertEquals("daily-tacet-price-check",f.next(result(240,true)).scene);
        assertEquals("daily-tacet-claim-60",f.next(result(240,true)).scene);
        assertEquals(0,f.spent());assertEquals(0,f.reserved());
    }
    @Test public void balanceBelow120MayUseSingleButMissingSingleNeverTriggersDoubleRefill() throws Exception {
        DailyTacet f=fighting(new Memory());Screen offer=doubleOffer(90);
        assertEquals("daily-tacet-price-check",f.next(offer).scene);
        assertEquals("daily-tacet-claim-60",f.next(offer).scene);
        Screen onlyDouble=doubleOffer(90);onlyDouble.words.remove(6);onlyDouble.words.remove(5);
        assertEquals(Kind.WAIT,f.next(onlyDouble).kind);assertEquals(0,f.reserved());
        Screen fundedDouble=doubleOffer(240);fundedDouble.words.remove(6);fundedDouble.words.remove(5);
        assertEquals("daily-tacet-price-check",f.next(fundedDouble).scene);
        assertEquals("daily-tacet-claim-120",f.next(fundedDouble).scene);
    }

    private Screen[] dev38ClaimFrames() {
        // Real dev38-tail.jsonl OCR boxes, 16:24:01.790..16:24:18.619; only UID is mapped to the existing test battle scope.
        // The first two price labels are genuinely malformed; waiting for later exact labels must remain mandatory.
        return new Screen[]{
            // 1794385441790 daily-tacet-open-claim
            screen(
                    raw("LJIL",487,44,511,59),
                    raw("-2,-2,3 2026-1 1 -11 16:24:01 +8 * HD",116,1045,501,1067),
                    raw("1750677506",1137,1005,1255,1018),
                    raw("Lv.90",1371,1002,1417,1026),
                    raw("领取奖励",1552,442,1673,473),
                    raw("|特征码: 000000000",2107,1051,2280,1069)
            ),
            // 1794385444822 daily-tacet-open-claim
            screen(
                    raw("|●e.",134,966,172,978),
                    raw("-1,-1,3 2026-11 -11 16:24:04+8 * HD",115,1045,501,1067),
                    raw("领取奖励",1553,444,1673,473),
                    raw("特征码: 000000000",2107,1051,2280,1069),
                    raw("1750617506",1136,1005,1255,1018),
                    raw("Lv.90",1372,1004,1416,1022)
            ),
            // 1794385446188 daily-tacet-price-wait
            screen(
                    raw("领取奖励",695,332,821,362),
                    raw("60",773,664,803,684),
                    raw("可消耗结晶波片领取对应奖励,请确认是否领取?",897,501,1494,529),
                    raw("单信领取",882,659,997,687),
                    raw("120",1391,664,1437,684),
                    raw("双信领取",1499,659,1614,688),
                    raw("357",1675,55,1725,77),
                    raw("240/240 十",1897,53,2081,77),
                    raw("特征码: 000000000",2107,1051,2280,1069)
            ),
            // 1794385448032 daily-tacet-price-wait
            screen(
                    raw("领取奖励",695,332,821,362),
                    raw("60",773,664,803,684),
                    raw("可消耗结晶波片领取对应奖励,请确认是否领取?",897,501,1494,529),
                    raw("单倍颌取",882,658,997,688),
                    raw("120",1391,664,1436,684),
                    raw("双信领取",1499,656,1612,688),
                    raw("357",1675,55,1725,77),
                    raw("240/240",1897,55,2016,77),
                    raw("特征码:000000000",2096,1050,2285,1070)
            ),
            // 1794385449175 daily-tacet-price-check
            screen(
                    raw("领取奖励",694,330,820,362),
                    raw("60",773,664,803,684),
                    raw("可消耗结晶波片领取对应奖励,请确认是否领取?",897,501,1494,529),
                    raw("单倍领取",883,659,998,687),
                    raw("120",1391,664,1436,684),
                    raw("双倍领取",1499,656,1612,688),
                    raw("357",1675,55,1725,77),
                    raw("240/240 十",1897,53,2081,77),
                    raw("特征码:000000000",2097,1050,2286,1070)
            ),
            // 1794385450926 daily-tacet-claim-60
            screen(
                    raw("领取奖励",695,332,821,362),
                    raw("60",773,664,804,684),
                    raw("可消耗结晶波片领取对应奖励,请确认是否领取?",897,501,1494,529),
                    raw("单倍领取",882,659,998,688),
                    raw("120",1391,664,1436,684),
                    raw("双倍领取",1499,656,1612,688),
                    raw("357",1675,55,1725,77),
                    raw("240/240 十",1897,53,2081,77),
                    raw("特征码:000000000",2097,1050,2286,1070)
            ),
            // 1794385452611 daily-tacet-claim-60
            screen(
                    raw("领取奖励",694,329,819,362),
                    raw("60",773,664,803,684),
                    raw("可消耗结晶波片领取对应奖励,请确认是否领取?",897,501,1494,529),
                    raw("单倍領取",882,659,997,688),
                    raw("120",1391,664,1436,684),
                    raw("双倍领取",1499,656,1612,688),
                    raw("357",1675,55,1725,77),
                    raw("240/240 十",1897,53,2081,77),
                    raw("特征码: 000000000",2107,1051,2280,1069)
            ),
            // 1794385453912 daily-tacet-settle-wait
            screen(
                    raw("●0-s256",139,743,323,775),
                    raw("特征码: 000000000",2107,1051,2280,1069)
            ),
            // 1794385455992 daily-tacet-settle-wait
            screen(
                    raw("X450",606,536,669,556),
                    raw("X20",623,706,669,726),
                    raw("x180",776,536,838,556),
                    raw("X4",809,706,839,725),
                    raw("X5250",929,536,1007,556),
                    raw("退出副本",891,908,1007,937),
                    raw("298秒后自动退出",859,980,1040,1003),
                    raw("挑战成功",1095,294,1303,345),
                    raw("重新挑战",1393,908,1509,936),
                    raw("剩余180",1366,976,1472,1002),
                    raw("X3",1826,537,1855,556),
                    raw("|特征码:000000000",2096,1050,2286,1071)
            ),
            // 1794385456984 daily-tacet-repeat
            screen(
                    raw("X450",606,536,669,556),
                    raw("X20",623,706,669,726),
                    raw("x180",776,536,838,556),
                    raw("X4",809,706,839,725),
                    raw("X5250",929,536,1007,556),
                    raw("退出副本",891,908,1007,937),
                    raw("297秒后自动退出",859,980,1040,1003),
                    raw("挑战成功",1095,294,1303,345),
                    raw("重新挑战",1393,908,1509,936),
                    raw("剩余180",1375,978,1464,1003),
                    raw("X3",1826,537,1855,556),
                    raw("|特征码:000000000",2096,1050,2286,1070)
            ),
            // 1794385458619 daily-tacet-repeat
            screen(
                    raw("X450",606,536,669,556),
                    raw("X20",623,705,669,726),
                    raw("x180",776,536,838,556),
                    raw("X4",808,704,837,726),
                    raw("X5250",928,534,1006,555),
                    raw("退出副本",891,908,1007,937),
                    raw("296秒后自动退出",848,978,1049,1005),
                    raw("挑战成功",1095,294,1303,345),
                    raw("重新挑战",1392,907,1508,937),
                    raw("剩余180",1366,976,1472,1002),
                    raw("X3",1826,537,1855,556),
                    raw("|特征码:000000000",2096,1050,2286,1070)
            )
        };
    }
    private Text raw(String value,int left,int top,int right,int bottom) {
        return new Text(value,left/2400f,top/1080f,right/2400f,bottom/1080f);
    }
}
