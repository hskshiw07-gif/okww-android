package io.github.okww.weekly;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class GardenRulesTest {
    private GardenRules.Screen savedEntrance(String points){return new GardenRules.Screen(Arrays.asList(
            t("幻梦游园。狂想",.14f,.07f),t("本周游历值",.14f,.15f),t(points,.12f,.20f),
            t("|关卡目标",.70f,.20f),t("回已存档",.89f,.865f),t("|继续游戏",.89f,.915f)),new int[3],2400f/1080);}
    @Test public void liveSavedEntranceResumesWithoutOverwriting(){
        GardenRules.Action action=new GardenRules().next(savedEntrance("0/6000"));
        assertEquals("resume-entrance",action.scene);assertEquals(.89f,action.x,.001f);
        assertNotNull(GardenRules.scoreCrop(savedEntrance("0/6000")));
        assertEquals("entrance",new GardenRules().next(savedEntrance("6000")).scene);
        assertEquals("entrance-cap",new GardenRules().next(savedEntrance("6000/6000")).scene);
    }
    @Test public void savedEntranceNeedsEveryContextAnchor(){
        GardenRules.Screen full=savedEntrance("0/6000");
        for(int remove:new int[]{0,1,3,4,5}){
            java.util.List<GardenRules.Text> words=new java.util.ArrayList<>(full.words);words.remove(remove);
            assertEquals(GardenRules.Kind.WAIT,new GardenRules().next(new GardenRules.Screen(words,new int[3],2400f/1080)).kind);
        }
    }
    private GardenRules.Text t(String value, float x, float y) {
        return new GardenRules.Text(value, x - .02f, y - .01f, x + .02f, y + .01f);
    }
    private GardenRules.Screen screen(GardenRules.Text... words) {
        return new GardenRules.Screen(Arrays.asList(words), new int[]{0, 0, 0});
    }
    private GardenRules.Screen entrance(String score) {
        return screen(t("幻梦游园·狂想", .14f, .07f), t("本周游历值", .10f, .15f),
                t(score, .12f, .20f), t("开始游戏", .88f, .92f), t("祝福·幻梦机遇", .48f, .85f));
    }
    private GardenRules.Screen result(String score) {
        return screen(t("挑战失败", .6f, .23f), t("本周游历值", .49f, .58f),
                t(score, .79f, .58f), t("重新挑战", .33f, .85f), t("返回首页", .66f, .85f));
    }
    private GardenRules.Action apply(GardenRules rules, GardenRules.Screen screen, String scene) {
        GardenRules.Action action = rules.next(screen);
        assertEquals(scene, action.scene);
        rules.applied(action);
        return action;
    }

    @Test public void traditionalAndWhitespaceNormalize() {
        assertEquals("本周游历值6000/6000", GardenRules.normalize("本週遊歷值 ６０００／６０００"));
        assertEquals("进入新阶段", GardenRules.normalize("進入新階段"));
        assertEquals("确认继续购买关闭", GardenRules.normalize("確認繼續購買關閉"));
    }

    @Test public void completionRequiresLabelAndNumeratorNotDenominator() {
        assertEquals(Integer.valueOf(0), GardenRules.score("本周游历值 0/6000"));
        assertEquals(Integer.valueOf(2000), GardenRules.score("本週遊歷值2000/6000"));
        assertEquals(Integer.valueOf(6000), GardenRules.score("本周游历值6000/6000"));
        assertEquals(Integer.valueOf(6000), GardenRules.score("本周游历值已达到上限"));
        assertNull(GardenRules.score("关卡收益6000"));
        assertNull(GardenRules.score("6000"));
        assertNull(GardenRules.score("本周游历值"));
        assertNull(GardenRules.score("本周游历值0/6000 6000/6000"));
        assertNull(GardenRules.score("本周游历值16000/6000"));
        assertNull(GardenRules.score("本周游历值6000/60001"));
    }

    @Test public void normalEntranceStartsWithoutWaveplateOrCombatConfig() {
        GardenRules.Action action = new GardenRules().next(entrance("0/6000"));
        assertEquals("start", action.scene);
        assertEquals(.88f, action.x, .001f);
    }

    @Test public void unknownEntranceScoreDoesNotStart() {
        assertEquals(GardenRules.Kind.WAIT, new GardenRules().next(entrance("6000")).kind);
    }

    @Test public void fullEntranceReturnsInsteadOfStartingAnotherGame() {
        assertEquals(GardenRules.Kind.BACK, new GardenRules().next(entrance("6000/6000")).kind);
        assertEquals(GardenRules.Kind.BACK, new GardenRules().next(entrance("已达到上限")).kind);
        GardenRules.Screen mobile = new GardenRules.Screen(entrance("已达到上限").words, new int[3], 2400f / 1080);
        GardenRules.Action close = new GardenRules().next(mobile);
        assertEquals("entrance-cap", close.scene);
        assertEquals(GardenRules.Kind.TAP, close.kind);
        assertEquals(.925f, close.x, .001f);
        assertEquals(.06f, close.y, .001f);
    }

    @Test public void resultRetriesUntilNumeratorReachesCap() {
        assertEquals("result-retry", new GardenRules().next(result("2000/6000")).scene);
        assertEquals("result-cap", new GardenRules().next(result("6000/6000")).scene);
        assertEquals("result-verify-home", new GardenRules().next(result("6000")).scene);
    }

    @Test public void successContinuesEndlessUntilVerifiedCapNotInstructionText() {
        GardenRules rules = new GardenRules();
        GardenRules.Text[] words = {t("挑战成功", .6f, .23f), t("本周游历值", .49f, .53f),
                t("|无尽挑战", .37f, .85f), t("返回主页", .63f, .85f), t("通关阶段12可获得6000游历值", .6f, .63f)};
        java.util.List<GardenRules.Text> lines = new java.util.ArrayList<>(Arrays.asList(words));
        lines.add(t("93000/6000", .74f, .53f));
        assertEquals("result-verify-home", rules.next(new GardenRules.Screen(lines, new int[3])).scene);
        lines.add(t("3000/6000", .74f, .53f));
        assertEquals("result-endless", rules.next(new GardenRules.Screen(lines, new int[3])).scene);
        lines.remove(lines.size() - 1);
        lines.add(t("6000/6000", .74f, .53f));
        assertEquals("result-cap", rules.next(new GardenRules.Screen(lines, new int[3])).scene);
    }

    @Test public void gardenCardChoicePrefersGoldThenPurpleAndConfirmsOnNextFrame() {
        GardenRules rules = new GardenRules();
        GardenRules.Screen offer = new GardenRules.Screen(Arrays.asList(t("声骸", .32f, .25f),
                t("声骸", .55f, .25f), t("道具", .78f, .25f),
                t("跳过", .58f, .80f), t("确认", .78f, .80f)), new int[]{1, 2, 0});
        GardenRules.Action selected = apply(rules, offer, "card-select");
        assertEquals(.5f, selected.x, .001f);
        apply(rules, offer, "card-confirm");
        apply(rules, offer, "card-select");
        assertEquals(.73f, GardenRules.bestCard(new int[]{0, 0, 1}), .001f);
        assertEquals(.27f, GardenRules.bestCard(new int[]{0, 0, 0}), .001f);
    }

    @Test public void noStateMutationUntilGestureAcknowledged() {
        GardenRules rules = new GardenRules();
        GardenRules.Screen offer = screen(t("声骸", .3f, .25f), t("声骸", .55f, .25f), t("道具", .78f, .25f),
                t("跳过", .58f, .80f), t("确认", .78f, .80f));
        assertEquals("card-select", rules.next(offer).scene);
        assertEquals("card-select", rules.next(offer).scene);
    }

    @Test public void eventDeclinesBeforeConfirming() {
        GardenRules rules = new GardenRules();
        GardenRules.Screen event = screen(t("奇缘异遇", .6f, .2f),
                t("你对此不感兴趣", .52f, .55f), t("确认", .75f, .65f));
        apply(rules, event, "event-select");
        apply(rules, event, "event-confirm");
        apply(rules, event, "event-select");
    }

    @Test public void genericConfirmNeverClickedOnUnknownScreen() {
        assertEquals(GardenRules.Kind.WAIT, new GardenRules().next(screen(t("确认", .6f, .7f))).kind);
    }

    @Test public void stageTransitionAndDailyLoop() {
        GardenRules rules = new GardenRules();
        apply(rules, screen(t("乐园阶段2/7目标", .5f, .2f), t("确认", .5f, .65f)), "stage-confirm");
        apply(rules, screen(t("今日收入", .1f, .3f), t("阶段2/7", .1f, .12f),
                t("进入下一天", .86f, .88f)), "next-day");
        apply(rules, screen(t("进入新阶段", .6f, .65f)), "new-stage");
    }

    @Test public void optionalShopClosedWithoutPurchaseOrRefresh() {
        GardenRules.Screen shop = screen(t("乐园商店", .2f, .06f), t("购买", .75f, .7f),
                t("刷新", .28f, .7f), t("关闭", .82f, .06f));
        assertEquals("shop-close", new GardenRules().next(shop).scene);
    }

    @Test public void resumeSavedProgressInsteadOfDeletingIt() {
        assertEquals("resume", new GardenRules().next(screen(t("当前保存进度：阶段1", .5f, .45f),
                t("新游戏", .35f, .65f), t("继续游戏", .65f, .65f))).scene);
    }

    @Test public void blessingOpenSelectAndConfirmAreSeparateObservations() {
        GardenRules rules = new GardenRules();
        GardenRules.Screen entrance = screen(t("幻梦游园·狂想", .14f, .07f), t("本周游历值", .1f, .15f),
                t("0/6000", .12f, .20f), t("开始游戏", .88f, .92f), t("选择祝福", .48f, .85f));
        apply(rules, entrance, "blessing-open");
        GardenRules.Screen list = screen(t("选择祝福", .5f, .15f), t("幻梦机遇", .35f, .6f), t("确认", .8f, .83f));
        apply(rules, list, "blessing-select");
        apply(rules, list, "blessing-confirm");
        apply(rules, entrance("0/6000"), "start");
    }

    @Test public void wrongBossAndPremiumScreensStop() {
        for (String text : new String[]{"单人挑战", "结晶波片", "星声", "唤取10次"})
            assertEquals(text, GardenRules.Kind.STOP, new GardenRules().next(screen(t(text, .5f, .5f))).kind);
    }

    @Test public void duplicateButtonsFailClosed() {
        assertThrows(IllegalStateException.class, () -> new GardenRules().next(screen(
                t("返回首页", .3f, .8f), t("返回首页", .7f, .8f))));
    }

    @Test public void reenteringGuideAfterCapRequiresActualCurrentScore() {
        GardenRules rules = new GardenRules();
        apply(rules, result("6000/6000"), "result-cap");
        GardenRules.Screen milestones = screen(t("周度游历", .3f, .1f), t("幻梦游园·狂想", .23f, .4f),
                t("0", .19f, .85f), t("游历值", .20f, .92f), t("6000", .88f, .9f));
        assertEquals(GardenRules.Kind.STOP, rules.next(milestones).kind);
        GardenRules.Screen complete = screen(t("周度游历", .3f, .1f), t("幻梦游园·狂想", .23f, .4f),
                t("6000", .19f, .85f), t("游历值", .20f, .92f), t("1000", .35f, .9f));
        assertEquals(GardenRules.Kind.DONE, rules.next(complete).kind);
    }

    @Test public void knownScreenSequenceCanReachCompletion() {
        GardenRules rules = new GardenRules();
        apply(rules, screen(t("终端", .1f, .1f), t("索拉指南", .73f, .5f)), "terminal-guide");
        apply(rules, screen(t("活跃度", .2f, .1f), t("周度游历", .3f, .1f)), "guide-weekly");
        apply(rules, screen(t("周度游历", .3f, .1f), t("幻梦游园·狂想", .23f, .4f)), "guide-garden");
        apply(rules, entrance("0/6000"), "start");
        apply(rules, result("3000/6000"), "result-retry");
        apply(rules, result("6000/6000"), "result-cap");
        apply(rules, entrance("已达到上限"), "entrance-cap");
        GardenRules.Action completed = rules.next(screen(t("周度游历", .3f, .1f), t("幻梦游园·狂想", .23f, .4f),
                t("6000", .19f, .85f), t("游历值", .20f, .92f)));
        assertEquals(GardenRules.Kind.DONE, completed.kind);
    }

    @Test public void retryLimitNeverMisreportedAsCompletion() {
        GardenRules rules = new GardenRules();
        apply(rules, entrance("0/6000"), "start");
        for (int attempt = 1; attempt < 12; attempt++) apply(rules, result("1000/6000"), "result-retry");
        assertEquals(GardenRules.Kind.STOP, rules.next(result("1000/6000")).kind);
        assertEquals("result-cap", rules.next(result("6000/6000")).scene);
    }

    @Test public void blankFramesWaitInsteadOfGuessing() {
        assertEquals(GardenRules.Kind.WAIT, new GardenRules().next(screen()).kind);
    }

    @Test public void onlyObservedMobileGeometryCanOpenIconOnlyTerminal() {
        java.util.List<GardenRules.Text> hud = Arrays.asList(t("Lv.90", .57f, .94f), t("特征码：123", .9f, .98f));
        GardenRules rules = new GardenRules();
        assertEquals("world-terminal", rules.next(new GardenRules.Screen(hud, new int[3], 2400f / 1080)).scene);
        assertEquals(GardenRules.Kind.WAIT, rules.next(new GardenRules.Screen(hud, new int[3], 1920f / 1080)).kind);
        assertEquals(GardenRules.Kind.WAIT, rules.next(screen(t("特征码：123", .9f, .98f))).kind);
        java.util.List<GardenRules.Text> mobileOcr = Arrays.asList(t("t90", .57f, .94f), t(",特征码:333333333.", .9f, .98f));
        assertEquals("world-terminal", rules.next(new GardenRules.Screen(mobileOcr, new int[3], 2400f / 1080)).scene);
        java.util.List<GardenRules.Text> unreadableLevel = Arrays.asList(t("ta90", .57f, .94f),
                t("3144K/31443", .5f, .94f), t("令94ms", .92f, .115f), t("特征码:333333333", .9f, .98f));
        assertEquals("world-terminal", rules.next(new GardenRules.Screen(unreadableLevel, new int[3], 2400f / 1080)).scene);
    }

    @Test public void mobileChestsOnlyAfterVerifiedCapAndThenStop() {
        GardenRules rules = new GardenRules();
        apply(rules, entrance("6000/6000"), "entrance-cap");
        GardenRules.Screen guide = new GardenRules.Screen(Arrays.asList(t("周度游历", .3f, .1f),
                t("幻梦游园·狂想", .23f, .4f), t("6000", .19f, .85f), t("游历值", .2f, .92f)), new int[3], 2400f / 1080);
        for (int index = 0; index < 6; index++) {
            GardenRules.Action action = apply(rules, guide, "reward-chest-" + index);
            assertTrue(action.x >= .35f && action.x < .92f);
        }
        assertEquals(GardenRules.Kind.DONE, rules.next(guide).kind);
    }

    @Test public void rewardPopupCannotBecomeAnotherCardOrPaidAction() {
        GardenRules rules = new GardenRules();
        apply(rules, entrance("6000/6000"), "entrance-cap");
        GardenRules.Screen guide = new GardenRules.Screen(Arrays.asList(t("周度游历", .3f, .1f),
                t("幻梦游园·狂想", .23f, .4f), t("6000", .19f, .85f), t("游历值", .2f, .92f)), new int[3], 2400f / 1080);
        apply(rules, guide, "reward-chest-0");
        assertEquals("rewards-dismiss", rules.next(screen(t("获得奖励", .5f, .2f),
                t("星声", .5f, .5f), t("点击空白处关闭", .5f, .9f))).scene);
        assertEquals(GardenRules.Kind.WAIT, rules.next(screen(t("获得奖励", .5f, .2f))).kind);
    }

    @Test public void observedRewardPopupCanRecoverAfterAppRestart() {
        GardenRules rules = new GardenRules();
        assertEquals("rewards-dismiss", rules.next(screen(t("获得", .5f, .26f), t("x160", .33f, .52f),
                t("x70000", .4f, .52f), t("点击空白处继续", .5f, .88f))).scene);
        assertEquals(GardenRules.Kind.WAIT, rules.next(screen(t("获得", .5f, .26f),
                t("点击空白处继续", .5f, .88f))).kind);
    }

    @Test public void observedMobileOcrErrorsNeedTheirSceneContext() {
        GardenRules rules = new GardenRules();
        assertEquals("shop-close", rules.next(screen(t("豐樂園商店", .2f, .06f), t("關閉下", .82f, .06f))).scene);
        assertEquals("next-day", rules.next(screen(t("日收入", .1f, .3f), t("階段:2/7", .1f, .12f),
                t("進入下一天", .86f, .88f))).scene);
        assertEquals("next-day", rules.next(screen(t("今日收入", .1f, .3f), t("阶段:1/7", .1f, .12f),
                t("进入下-天", .86f, .88f))).scene);
        assertEquals("next-day", rules.next(screen(t("本周游园", .1f, .06f), t("阶段:1/7", .1f, .12f),
                t("白第5天", .1f, .16f), t("今妇收入", .1f, .3f), t("进入下ー天", .86f, .88f))).scene);
        assertEquals("event-select", rules.next(screen(t("奇缘異過", .6f, .2f),
                t("你對此不感興趣", .52f, .55f), t("確認", .75f, .65f))).scene);
        assertEquals("event-select", rules.next(screen(t("奇緣异遇", .6f, .2f),
                t("|你对此不感兴趣", .52f, .55f), t("确认", .75f, .65f))).scene);
        assertEquals("result-retry", rules.next(screen(t("挑戦矢敗", .6f, .23f), t("本週遊歷值", .49f, .58f),
                t("2000/6000", .79f, .58f), t("重新挑戦", .33f, .85f), t("返回首頁", .66f, .85f))).scene);
        assertEquals("unpause", rules.next(screen(t("暫停", .5f, .25f), t("重新開始", .5f, .55f), t("袋續", .73f, .55f))).scene);
        assertEquals(GardenRules.Kind.WAIT, rules.next(screen(t("袋續", .73f, .55f))).kind);
        assertEquals(GardenRules.Kind.WAIT, rules.next(screen(t("關閉下", .82f, .06f))).kind);
    }

    @Test public void disabledCardConfirmMayBeUnreadableBeforeSelection() {
        GardenRules rules = new GardenRules();
        GardenRules.Screen offer = screen(t("声骸", .3f, .25f), t("|声骸", .55f, .25f), t("声骸", .78f, .25f), t("跳过", .58f, .8f));
        apply(rules, offer, "card-select");
        assertEquals(GardenRules.Kind.WAIT, rules.next(offer).kind);
    }

    @Test public void mobileGoldCardCategoryOcrNeedsThreeHeadersAndSkip() {
        GardenRules rules = new GardenRules();
        assertEquals("card-select", rules.next(screen(t("声敦", .3f, .25f), t("声敦", .55f, .25f),
                t("声敦", .78f, .25f), t("跳过", .58f, .8f))).scene);
        assertEquals(GardenRules.Kind.WAIT, rules.next(screen(t("声敦", .3f, .25f),
                t("跳过", .58f, .8f))).kind);
    }

    @Test public void mobileTerminalTitleMayBeMissingButMenuContextIsRequired() {
        GardenRules rules = new GardenRules();
        assertEquals("terminal-guide", rules.next(screen(t("索拉指南", .73f, .5f), t("数据坞", .5f, .5f),
                t("地图", .8f, .75f), t("联觉经验", .1f, .6f))).scene);
        assertEquals(GardenRules.Kind.WAIT, rules.next(screen(t("索拉指南", .73f, .5f))).kind);
    }

    @Test public void terminalGuideObservedSuMisreadRequiresMenuAndButtonRegion() {
        GardenRules rules = new GardenRules();
        GardenRules.Action action = rules.next(screen(t("终端,", .1f, .06f), t("素拉指南", .73f, .55f)));
        assertEquals("terminal-guide", action.scene);
        assertEquals(.73f, action.x, .001f);
        assertEquals("terminal-guide", rules.next(screen(t("素拉指南", .73f, .55f),
                t("数据坞", .52f, .55f), t("地图", .83f, .8f), t("联觉经验", .1f, .77f))).scene);
        assertEquals(GardenRules.Kind.WAIT, rules.next(screen(t("素拉指南", .73f, .55f))).kind);
        assertEquals(GardenRules.Kind.WAIT, rules.next(screen(t("终端", .1f, .06f), t("素拉指南", .1f, .4f))).kind);
        assertThrows(IllegalStateException.class, () -> rules.next(screen(t("终端", .1f, .06f),
                t("素拉指南", .7f, .5f), t("索拉指南", .77f, .5f))));
    }

    @Test public void mobileGardenCardIncludesDecorativeBarAndTrailingPunctuation() {
        GardenRules rules = new GardenRules();
        assertEquals("guide-garden", rules.next(screen(t("周度游历", .3f, .1f),
                t("活跃度", .2f, .1f), t("|幻梦游园·狂想。", .23f, .4f))).scene);
    }

    @Test public void reconnectOnlyOnObservedGameTitleScreen() {
        GardenRules rules = new GardenRules();
        apply(rules, screen(t("点击连接", .5f, .93f),
                t("CN Android Product_3.7.0", .8f, .06f), t("CADPA", .93f, .92f)), "game-connect");
        assertEquals("game-loading", rules.next(screen()).scene);
        apply(rules, screen(t("终端", .1f, .1f), t("索拉指南", .73f, .5f)), "terminal-guide");
        assertEquals(GardenRules.Kind.WAIT, rules.next(screen(t("点击连接", .5f, .93f))).kind);
        assertEquals("unknown", rules.next(screen()).scene);
    }
}
