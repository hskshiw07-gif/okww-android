package io.github.okww.weekly;

import org.junit.Test;
import static org.junit.Assert.*;

public class DailyStaminaBudgetTest {
    @Test public void fourNormalClaimsConsumeAtMost240() {
        DailyStaminaBudget budget = new DailyStaminaBudget(null);
        for (int balance = 240; balance >= 60; balance -= 60) {
            assertTrue(budget.canClaim(balance, 60, false));
            budget.reserve(balance, 60, false);
            assertFalse(budget.canClaim(balance, 60, false));
            budget.confirm(balance - 60);
        }
        assertEquals(240, budget.confirmed());
        assertEquals(0, budget.remaining());
        assertFalse(budget.canClaim(240, 60, false));
    }
    @Test public void insufficientWaveplatesDoesNotUseBackupOrRefill() {
        DailyStaminaBudget budget = new DailyStaminaBudget(null);
        assertFalse(budget.canClaim(59, 60, false));
        assertFalse(budget.canClaim(-1, 60, false));
        assertFalse(budget.canClaim(240, 60, true));
        assertThrows(IllegalStateException.class, () -> budget.reserve(59, 60, false));
        assertEquals(240, budget.remaining());
    }
    @Test public void unknownClaimCostIsNotAuthorized() {
        DailyStaminaBudget budget = new DailyStaminaBudget(null);
        for (int cost : new int[]{0, -1, 40, 61, 119, 121, 180, 240}) assertFalse(budget.canClaim(240, cost, false));
    }
    @Test public void twoDoubleClaimsReserveAndConsumeExactly240() {
        DailyStaminaBudget budget = new DailyStaminaBudget(null);
        for (int balance : new int[]{240, 120}) {
            assertTrue(budget.canClaim(balance, 120, false)); budget.reserve(balance, 120, false);
            assertEquals(120, budget.reserved()); assertFalse(budget.canClaim(balance, 60, false));
            budget.confirm(balance - 120);
        }
        assertEquals(240, budget.confirmed()); assertEquals(0, budget.remaining());
        assertFalse(budget.canClaim(240, 120, false)); assertFalse(budget.canClaim(240, 60, false));
    }
    @Test public void doubleNeedsItsActualBalanceAndRemainingAllowance() {
        DailyStaminaBudget budget = new DailyStaminaBudget(null);
        assertFalse(budget.canClaim(119, 120, false)); assertFalse(budget.canClaim(240, 120, true));
        budget.reserve(240, 120, false); budget.confirm(120);
        budget.reserve(120, 60, false); budget.confirm(60);
        assertEquals(180, budget.confirmed()); assertTrue(budget.canClaim(240, 60, false));
        assertFalse(budget.canClaim(240, 120, false));
        assertThrows(IllegalStateException.class, () -> budget.reserve(240, 120, false));
    }
    @Test public void oldSixtyAndNewDoubleReservationsRestoreWithoutChangingTheFormat() {
        DailyStaminaBudget old = new DailyStaminaBudget("60:60:180");
        assertEquals("60:60:180", old.save()); old.confirm(120); assertEquals(120, old.confirmed());
        DailyStaminaBudget doubleClaim = new DailyStaminaBudget(null); doubleClaim.reserve(180, 120, false);
        assertEquals("0:120:180", doubleClaim.save());
        DailyStaminaBudget restored = new DailyStaminaBudget(doubleClaim.save());
        assertEquals(120, restored.reserved()); assertEquals(120, restored.remaining());
        assertFalse(restored.canClaim(180, 60, false)); assertFalse(restored.canClaim(180, 120, false));
        for (int after : new int[]{180, 120, 61, 59, 0, -1}) {
            assertThrows(IllegalStateException.class, () -> restored.confirm(after));
            assertEquals("0:120:180", restored.save());
        }
        restored.confirm(60); assertEquals(120, restored.confirmed()); assertEquals(0, restored.reserved());
    }
    @Test public void pendingReservationSurvivesRestoreAndCannotBeSpentTwice() {
        DailyStaminaBudget original = new DailyStaminaBudget(null);
        original.reserve(180, 60, false);
        DailyStaminaBudget restored = new DailyStaminaBudget(original.save());
        assertEquals(60, restored.reserved());
        assertEquals(180, restored.remaining());
        assertFalse(restored.canClaim(180, 60, false));
        restored.confirm(120);
        assertEquals(60, restored.confirmed());
        assertEquals(0, restored.reserved());
    }
    @Test public void ambiguousOrUnexpectedSettlementRetainsReservation() {
        for (int after : new int[]{240, 181, 179, 120, -1}) {
            DailyStaminaBudget budget = new DailyStaminaBudget(null);
            budget.reserve(240, 60, false);
            assertThrows(IllegalStateException.class, () -> budget.confirm(after));
            assertEquals(60, budget.reserved());
            assertFalse(budget.canClaim(240, 60, false));
        }
    }
    @Test public void duplicateConfirmationCannotConsumeAnotherSixty() {
        DailyStaminaBudget budget = new DailyStaminaBudget(null);
        budget.reserve(240, 60, false); budget.confirm(180);
        assertThrows(IllegalStateException.class, () -> budget.confirm(120));
        assertEquals(60, budget.confirmed());
    }
    @Test public void malformedOrOverBudgetPersistenceNeverResetsSilently() {
        for (String state : new String[]{"", "bad", "0:60", "-60:0:-1", "300:0:-1", "10:0:-1",
                "240:60:240", "180:120:240", "0:120:119", "0:180:240", "0:60:-1", "0:0:240", "0:60:10000", "0:60:2147483648"})
            assertThrows(state, IllegalArgumentException.class, () -> new DailyStaminaBudget(state));
    }
}
