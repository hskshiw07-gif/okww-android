package io.github.okww.weekly;

import org.junit.Test;
import static org.junit.Assert.*;

public class CombatNormalAttackTest {
    @Test public void transientHudLossResumesOnlyWithSameCharacterAndFreshObservation() {
        CombatNormalAttack attacks=new CombatNormalAttack();
        attacks.request();attacks.observed(1000);attacks.suspend(1100);
        attacks.observed(1500);
        assertFalse(attacks.due(1600,true));
        attacks.resume(1600,true);
        assertTrue(attacks.due(1600,true));
        attacks.suspend(1700);attacks.observed(1800);attacks.resume(1900,false);
        assertFalse(attacks.due(1900,true));
    }
    @Test public void expiryAndExplicitStopNeverResurrectSuspendedAttack() {
        CombatNormalAttack attacks=new CombatNormalAttack();
        attacks.request();attacks.suspend(1000);attacks.observed(3501);attacks.resume(3501,true);
        assertFalse(attacks.due(3501,true));
        attacks.request();attacks.suspend(3600);attacks.stop();
        attacks.observed(3700);attacks.resume(3700,true);
        assertFalse(attacks.due(3700,true));
    }
    @Test public void halfSecondOcrWaitKeepsOneHundredMsAttacksThenExpiresWithoutNewHud() {
        CombatNormalAttack attacks=new CombatNormalAttack();attacks.request();attacks.observed(1000);
        int count=0;
        for(long now=1000;now<1500;now+=20)if(attacks.due(now,true)){attacks.sent(now);count++;}
        assertEquals(5,count);
        assertFalse(attacks.due(2201,true));
        // A new verified capture resumes rhythm; no invented active time or permanent input thread.
        attacks.observed(2200);assertTrue(attacks.due(2220,true));
    }
    @Test public void identityLossHoldOrForegroundGateStopsOutputAndNeverBackfillsStaleFrames() {
        CombatNormalAttack attacks=new CombatNormalAttack();attacks.request();attacks.sent(1000);attacks.observed(1000);
        assertFalse(attacks.due(1099,true));assertTrue(attacks.due(1100,true));
        assertFalse(attacks.due(1100,false));assertFalse(attacks.due(999,true));
        attacks.observed(-1);assertFalse(attacks.due(1100,true));
        attacks.observed(1000);assertFalse(attacks.due(3000,true));
        attacks.stop();attacks.observed(1200);assertFalse(attacks.due(1200,true));
        // Skill, heavy hold, switch and cancellation stop intent; a fresh frame alone cannot restart it.
        attacks.observed(5000);assertFalse(attacks.due(5000,true));attacks.request();assertTrue(attacks.due(5000,true));
    }
    @Test public void newReadCannotRenewOldIdentityAndCharacterSpecificIntervalsArePreserved() {
        CombatNormalAttack attacks=new CombatNormalAttack();attacks.request(500);attacks.observed(1000);attacks.sent(1000);
        assertFalse(attacks.due(1499,true));assertTrue(attacks.due(1500,true));attacks.sent(1500);
        // The next read is pending: its capture timestamp cannot renew this accepted-HUD lease.
        assertFalse(attacks.due(2201,true));attacks.observed(2200);assertTrue(attacks.due(2201,true));
        attacks.stop();attacks.observed(2300);assertFalse(attacks.due(2300,true));
    }
}
