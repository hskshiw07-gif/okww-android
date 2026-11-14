package io.github.okww.weekly;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class GardenResultRecoveryTest {
    private GardenRules.Text t(String value, float x, float y) {
        return new GardenRules.Text(value, x - .02f, y - .015f, x + .02f, y + .015f);
    }
    private GardenRules.Screen result(String points, boolean success) {
        return new GardenRules.Screen(Arrays.asList(t(success ? "挑战成功" : "挑战失败", .60f, .23f),
                t("◆本周游历值", .49f, .58f), t(points, .74f, .58f),
                t(success ? "无尽挑战" : "重新挑战", .37f, .85f), t("返回主页", .63f, .85f)), new int[3], 2400f / 1080);
    }
    @Test public void observedIconPrefixOnFailureReturnsHomeToRecheckInsteadOfTimingOut() {
        GardenRules.Action action = new GardenRules().next(result("91600/6000", false));
        assertEquals("result-verify-home", action.scene);
        assertEquals(GardenRules.Kind.TAP, action.kind);
        assertEquals(.63f, action.x, .001f);
    }
    @Test public void unreadableSuccessAlsoRechecksAtHomeRatherThanAssumingCompletion() {
        assertEquals("result-verify-home", new GardenRules().next(result("6000", true)).scene);
        assertEquals("result-endless", new GardenRules().next(result("1600/6000", true)).scene);
    }
    @Test public void recoveredFailureRetriesAndFullFailureReturnsHome() {
        assertEquals("result-retry", new GardenRules().next(result("1600/6000", false)).scene);
        assertEquals("result-cap", new GardenRules().next(result("6000/6000", false)).scene);
    }
    @Test public void recoveryAtHomeStillRequiresKnownScoreBeforeStarting() {
        GardenRules rules = new GardenRules();
        rules.applied(rules.next(result("91600/6000", false)));
        for (String points : new String[]{"1600/6000", "6000/6000", "6000"}) {
            GardenRules.Screen entrance = new GardenRules.Screen(Arrays.asList(t("幻梦游园·狂想", .14f, .07f),
                    t("本周游历值", .1f, .15f), t(points, .12f, .20f), t("开始游戏", .88f, .92f)), new int[3]);
            GardenRules.Action action = rules.next(entrance);
            assertEquals(points.equals("1600/6000") ? "start" : points.equals("6000/6000") ? "entrance-cap" : "entrance", action.scene);
        }
    }
    @Test public void unrelatedFailureOrPremiumScreenIsNotRetried() {
        GardenRules.Screen unrelated = new GardenRules.Screen(Arrays.asList(t("挑战失败", .60f, .23f),
                t("重新挑战", .37f, .85f), t("返回主页", .63f, .85f)), new int[3]);
        assertEquals(GardenRules.Kind.WAIT, new GardenRules().next(unrelated).kind);
    }
    @Test public void resultCropFollowsScoreLabelInBothSuccessAndFailureLayouts() {
        for (float y : new float[]{.50f, .535f, .58f, .65f}) {
            GardenRules.Screen screen = new GardenRules.Screen(Arrays.asList(t("挑战失败", .60f, .23f),
                    t("◆本周游历值", .49f, y), t("返回主页", .63f, .85f)), new int[3], 2400f / 1080);
            float[] crop = GardenRules.scoreCrop(screen);
            assertNotNull(crop);
            assertEquals(.699f, crop[0], .001f);
            assertEquals(.794f, crop[2], .001f);
            assertTrue(crop[1] < y - .015f);
            assertTrue(crop[3] > y + .015f);
            assertTrue(crop[3] - crop[1] < .08f);
        }
    }
    @Test public void resultCropDoesNotGuessWithoutLabelOrOnOtherAspectRatios() {
        GardenRules.Screen screen = result("1600/6000", false);
        assertNull(GardenRules.scoreCrop(new GardenRules.Screen(screen.words, new int[3], 16f / 9)));
        assertNull(GardenRules.scoreCrop(new GardenRules.Screen(Arrays.asList(t("挑战失败", .60f, .23f),
                t("返回主页", .63f, .85f)), new int[3], 2400f / 1080)));
    }
    @Test public void ambiguousResultLabelsAreRejectedInsteadOfCroppingAnotherRow() {
        GardenRules.Screen screen = new GardenRules.Screen(Arrays.asList(t("挑战失败", .60f, .23f),
                t("本周游历值", .49f, .58f), t("本周游历值", .49f, .64f),
                t("返回主页", .63f, .85f)), new int[3], 2400f / 1080);
        assertThrows(IllegalStateException.class, () -> GardenRules.scoreCrop(screen));
    }
    @Test public void fallbackRequiresTwoFramesAndNeverReportsCompletion() {
        GardenRules.Screen screen = result("91600/6000", false);
        GardenRules.Action action = new GardenRules().next(screen);
        GardenRunGuard guard = new GardenRunGuard(0);
        assertFalse(guard.ready(screen, action, 100));
        assertTrue(guard.ready(screen, action, 1000));
        assertNotEquals(GardenRules.Kind.DONE, action.kind);
    }
    @Test public void anotherFractionOutsideScoreRowCannotOverrideCurrentScore() {
        GardenRules.Screen screen = new GardenRules.Screen(Arrays.asList(t("挑战失败", .60f, .23f),
                t("◆本周游历值", .49f, .58f), t("1600/6000", .74f, .58f),
                t("6000/6000", .74f, .40f), t("重新挑战", .37f, .85f), t("返回主页", .63f, .85f)), new int[3]);
        assertEquals(Integer.valueOf(1600), GardenRules.resultScore(screen));
        assertEquals("result-retry", new GardenRules().next(screen).scene);
    }
}
