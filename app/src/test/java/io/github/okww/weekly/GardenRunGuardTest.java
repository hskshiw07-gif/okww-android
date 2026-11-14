package io.github.okww.weekly;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class GardenRunGuardTest {
    private GardenRules.Text text(String value, float x, float y) {
        return new GardenRules.Text(value, x - .02f, y - .01f, x + .02f, y + .01f);
    }
    private GardenRules.Screen board(int day, int timestamp) {
        return new GardenRules.Screen(Arrays.asList(text("阶段：2/7", .1f, .12f),
                text("第" + day + "天", .1f, .18f), text("今日收入", .1f, .3f),
                text("进入下一天", .85f, .89f), text("10:52:" + timestamp, .1f, .98f)), new int[]{0, 0, 0});
    }

    @Test public void requireTwoObservationsBeforeInput() {
        GardenRunGuard guard = new GardenRunGuard(0);
        GardenRules.Screen screen = board(1, 10);
        GardenRules.Action action = new GardenRules().next(screen);
        assertFalse(guard.ready(screen, action, 100));
        assertTrue(guard.ready(screen, action, 1000));
        assertFalse(guard.ready(screen, action, 2000));
    }

    @Test public void tickingWatermarkDoesNotResetRetryLimit() {
        GardenRunGuard guard = new GardenRunGuard(0);
        GardenRules rules = new GardenRules();
        for (int observation = 0; observation < 8; observation++) {
            GardenRules.Screen screen = board(1, observation);
            guard.ready(screen, rules.next(screen), observation * 1000L);
        }
        GardenRules.Screen screen = board(1, 9);
        assertFalse(guard.ready(screen, rules.next(screen), 9000));
        assertThrows(IllegalStateException.class, () -> guard.ready(screen, rules.next(screen), 10000));
    }

    @Test public void changingActualDayResetsRetries() {
        GardenRunGuard guard = new GardenRunGuard(0);
        GardenRules rules = new GardenRules();
        for (int day = 1; day < 30; day++) {
            GardenRules.Screen screen = board(day, day);
            assertFalse(guard.ready(screen, rules.next(screen), day * 2000L));
            assertTrue(guard.ready(screen, rules.next(screen), day * 2000L + 900));
        }
    }

    @Test public void unknownScreenNoiseDoesNotPreventTimeout() {
        GardenRunGuard guard = new GardenRunGuard(0);
        GardenRules.Screen screen = board(1, 1);
        GardenRules.Action wait = GardenRules.Action.waitFor("unknown", "wait");
        assertFalse(guard.ready(screen, wait, 1));
        assertThrows(IllegalStateException.class, () -> guard.ready(board(1, 55), wait, 26001));
    }

    @Test public void absoluteLimitSurvivesGenuineProgress() {
        GardenRunGuard guard = new GardenRunGuard(0);
        GardenRules.Screen screen = board(1, 1);
        assertThrows(IllegalStateException.class, () -> guard.ready(screen, new GardenRules().next(screen), 2400001));
    }

    @Test public void coldGameLoadGetsBoundedLongerWaitWithoutInput() {
        GardenRunGuard guard = new GardenRunGuard(0);
        GardenRules.Screen screen = board(1, 1);
        GardenRules.Action wait = GardenRules.Action.waitFor("game-loading", "load");
        assertFalse(guard.ready(screen, wait, 1));
        assertFalse(guard.ready(screen, wait, 30001));
        assertThrows(IllegalStateException.class, () -> guard.ready(screen, wait, 90002));
    }

    @Test public void eventOcrNoiseDoesNotCancelSameButtonConfirmationOrRetryLimit() {
        GardenRunGuard guard = new GardenRunGuard(0);
        for (int i = 0; i < 8; i++) {
            GardenRules.Screen screen = new GardenRules.Screen(Arrays.asList(text("奇缘异遇", .6f, .2f),
                    text("晶体" + i, .5f, .3f), text(i % 2 == 0 ? "|你对此不感兴趣" : "「你对此不感兴趣", .52f, .55f)), new int[3]);
            assertEquals(i % 2 == 1, guard.ready(screen, new GardenRules().next(screen), i * 1000L));
        }
        GardenRules.Screen screen = board(1, 1);
        GardenRules.Action action = new GardenRules.Action(GardenRules.Kind.TAP, "event-select", "不感兴趣", .52f, .55f);
        assertFalse(guard.ready(screen, action, 9000));
        assertThrows(IllegalStateException.class, () -> guard.ready(screen, action, 10000));
    }

    @Test public void differentCardTargetsStillNeedTwoMatchingObservations() {
        GardenRunGuard guard = new GardenRunGuard(0);
        GardenRules.Screen screen = board(1, 1);
        GardenRules.Action first = new GardenRules.Action(GardenRules.Kind.TAP, "card-select", "pick", .27f, .3f);
        GardenRules.Action second = new GardenRules.Action(GardenRules.Kind.TAP, "card-select", "pick", .50f, .3f);
        assertFalse(guard.ready(screen, first, 100));
        assertFalse(guard.ready(screen, second, 1000));
        assertTrue(guard.ready(screen, second, 2000));
    }
}
