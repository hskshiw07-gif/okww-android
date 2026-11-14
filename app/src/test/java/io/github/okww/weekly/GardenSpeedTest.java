package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;

public class GardenSpeedTest {
    private GardenRules.Text t(String value, float x, float y) {
        return new GardenRules.Text(value, x - .015f, y - .01f, x + .015f, y + .01f);
    }
    private GardenRules.Screen board(String speed, GardenRules.Text... extras) {
        ArrayList<GardenRules.Text> words = new ArrayList<>(Arrays.asList(
                t("本周游园", .10f, .06f), t("阶段:1/7", .10f, .12f),
                t("白第5天", .10f, .16f), t("今日收入", .10f, .28f)));
        if (speed != null) words.add(t(speed, .707f, .058f));
        words.addAll(Arrays.asList(extras));
        return new GardenRules.Screen(words, new int[3], 2400f / 1080);
    }

    @Test public void observedMobileOneSpeedIsChangedBeforeNextDay() {
        GardenRules.Action action = new GardenRules().next(board("x1.0", t("进入下一天", .86f, .88f)));
        assertEquals("speed-max", action.scene);
        assertEquals(GardenRules.Kind.TAP, action.kind);
        assertEquals(.707f, action.x, .001f);
        assertEquals(.058f, action.y, .001f);
    }
    @Test public void currentAnimationDoesNotNeedNextDayButtonToChangeSpeed() {
        assertEquals("speed-max", new GardenRules().next(board("×1.0")).scene);
    }
    @Test public void knownIntermediateSpeedContinuesUntilVisibleMax() {
        GardenRules rules = new GardenRules();
        GardenRules.Action one = rules.next(board("x1.0")); rules.applied(one);
        GardenRules.Action two = rules.next(board("x2.0"));
        assertEquals("speed-max", two.scene); rules.applied(two);
        assertEquals(GardenRules.Kind.WAIT, rules.next(board("MAX", t("进入下一天", .86f, .88f))).kind);
        assertEquals("next-day", rules.next(board("MAX", t("进入下一天", .86f, .88f))).scene);
    }
    @Test public void maxIsNeverClickedBackToOne() {
        for (String value : new String[]{"MAX", "Max", "×MAX", "MAXW", "MAX M", "MAX ►"})
            assertEquals("next-day", new GardenRules().next(board(value, t("进入下一天", .86f, .88f))).scene);
    }
    @Test public void missingSpeedAfterClickBlocksAdvancingAndDoesNotGuessMax() {
        GardenRules rules = new GardenRules();
        rules.applied(rules.next(board("x1.0")));
        GardenRules.Action action = rules.next(board(null, t("进入下一天", .86f, .88f)));
        assertEquals("speed-max", action.scene);
        assertEquals(GardenRules.Kind.WAIT, action.kind);
    }
    @Test public void speedNeedsBoardContextAndTopButtonRegion() {
        GardenRules.Screen unrelated = new GardenRules.Screen(Arrays.asList(t("x1.0", .707f, .058f)), new int[3]);
        assertNotEquals("speed-max", new GardenRules().next(unrelated).scene);
        assertNotEquals("speed-max", new GardenRules().next(board(null, t("x1.0", .5f, .5f))).scene);
    }
    @Test public void popupAndPaidInterfaceHavePriorityOverUnderlyingSpeed() {
        assertEquals("event-select", new GardenRules().next(board("x1.0", t("奇缘异遇", .6f, .2f),
                t("不感兴趣", .52f, .55f))).scene);
        assertEquals(GardenRules.Kind.STOP, new GardenRules().next(board("x1.0", t("领取奖励将消耗", .5f, .5f))).kind);
    }
    @Test public void duplicateSpeedLabelsStopRatherThanGuessAButton() {
        assertThrows(IllegalStateException.class, () -> new GardenRules().next(board("x1.0", t("MAX", .75f, .06f))));
    }
    @Test public void failedChangesAreBoundedAcrossMissingOcr() {
        GardenRules rules = new GardenRules();
        for (int i = 0; i < 4; i++) {
            GardenRules.Action action = rules.next(board("x1.0"));
            assertEquals(GardenRules.Kind.TAP, action.kind); rules.applied(action);
            assertEquals(GardenRules.Kind.WAIT, rules.next(board(null)).kind);
        }
        assertEquals(GardenRules.Kind.STOP, rules.next(board("x1.0")).kind);
    }
    @Test public void existingTwoFrameGuardAlsoAppliesToSpeedChanges() {
        GardenRules.Screen screen = board("x1.0");
        GardenRules.Action action = new GardenRules().next(screen);
        assertEquals("speed-max", action.scene);
        GardenRunGuard guard = new GardenRunGuard(0);
        assertFalse(guard.ready(screen, action, 100));
        assertTrue(guard.ready(screen, action, 1000));
    }
    @Test public void missingHeaderCannotBypassPendingMaxVerification() {
        GardenRules rules = new GardenRules();
        rules.applied(rules.next(board("x1.0")));
        GardenRules.Screen partial = new GardenRules.Screen(Arrays.asList(t("阶段:1/7", .1f, .12f),
                t("今日收入", .1f, .28f), t("进入下一天", .86f, .88f)), new int[3]);
        assertEquals(GardenRules.Kind.WAIT, rules.next(partial).kind);
    }
    @Test public void maxVerificationMustBeConsecutiveAndChineseSpeedIsRecognized() {
        GardenRules rules = new GardenRules();
        GardenRules.Action action = rules.next(board("1倍速"));
        assertEquals("speed-max", action.scene); rules.applied(action);
        assertEquals(GardenRules.Kind.WAIT, rules.next(board("MAX")).kind);
        assertEquals(GardenRules.Kind.WAIT, rules.next(board(null)).kind);
        assertEquals(GardenRules.Kind.WAIT, rules.next(board("MAX", t("进入下一天", .86f, .88f))).kind);
        assertEquals("next-day", rules.next(board("MAX", t("进入下一天", .86f, .88f))).scene);
    }
    @Test public void observedMobileFiveSpeedIncludesFastForwardGlyph() {
        GardenRules rules = new GardenRules();
        rules.applied(rules.next(board("x1.0")));
        GardenRules.Action five = rules.next(board("x5.0 ►", t("进入下一天", .86f, .88f)));
        assertEquals("speed-max", five.scene);
        assertEquals(GardenRules.Kind.TAP, five.kind);
        rules.applied(five);
        assertEquals(GardenRules.Kind.WAIT, rules.next(board("MAX ▶")).kind);
        assertEquals("next-day", rules.next(board("MAX ▶", t("进入下一天", .86f, .88f))).scene);
    }
    @Test public void everyNonMaxButtonLabelIsClickedWithoutEnumeratingSpeeds() {
        for (String value : new String[]{"x1.0", "x2.0", "x4.0", "x5.0 ►", "x10.0", "M4X", "极速", "?"}) {
            GardenRules.Action action = new GardenRules().next(board(value, t("进入下一天", .86f, .88f)));
            assertEquals(value, "speed-max", action.scene);
            assertEquals(value, GardenRules.Kind.TAP, action.kind);
        }
    }
}
