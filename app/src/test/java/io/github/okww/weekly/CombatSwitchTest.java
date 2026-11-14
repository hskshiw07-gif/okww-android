package io.github.okww.weekly;

import org.junit.Test;
import static io.github.okww.weekly.CombatSwitch.Kind.*;
import static org.junit.Assert.*;

public class CombatSwitchTest {
    private static ActiveCharacterRecognizer.State source() {
        return new ActiveCharacterRecognizer.State(1,new int[]{0,2},new float[]{.90f,.91f},new float[]{.25f,.39f});
    }
    private static ActiveCharacterRecognizer.State target() {
        return new ActiveCharacterRecognizer.State(0,new int[]{1,2},new float[]{.90f,.91f},new float[]{.25f,.39f});
    }

    @Test public void swallowedFirstTapRetriesWithFreshCoordinatesThenConfirms() throws Exception {
        CombatSwitch s=new CombatSwitch(1,0,1000);
        CombatSwitch.Step step=s.next(1000,900,true,true,source());
        assertEquals(TAP,step.kind);assertEquals(.90f,step.x,0);assertEquals(.25f,step.y,0);
        assertEquals(WAIT,s.next(1500,1400,true,true,source()).kind);
        ActiveCharacterRecognizer.State moved=new ActiveCharacterRecognizer.State(1,new int[]{2,0},
                new float[]{.89f,.92f},new float[]{.24f,.38f});
        step=s.next(2000,1900,true,true,moved);
        assertEquals(TAP,step.kind);assertEquals(.92f,step.x,0);assertEquals(.38f,step.y,0);
        assertEquals(2,s.attempts());assertEquals(CONFIRMED,s.next(2500,2400,true,true,target()).kind);
    }

    @Test public void uidConflictAndUnknownHudNeverConfirmOrRetry() throws Exception {
        CombatSwitch s=new CombatSwitch(1,0,1000);assertEquals(TAP,s.next(1000,900,true,true,source()).kind);
        assertEquals(WAIT,s.next(2000,1900,true,false,target()).kind);
        assertEquals(WAIT,s.next(2200,2100,false,true,target()).kind);
        assertEquals(WAIT,s.next(2400,2300,true,false,source()).kind);
        assertEquals(WAIT,s.next(2600,2500,false,true,source()).kind);
        assertEquals(WAIT,s.next(2800,2700,true,true,null).kind);assertEquals(1,s.attempts());
        assertEquals(CONFIRMED,s.next(3000,2900,true,true,target()).kind);
    }

    @Test public void requiresNewRecentPostTapCaptureRatherThanReusingCachedIdentity() throws Exception {
        CombatSwitch s=new CombatSwitch(1,0,1000);assertEquals(TAP,s.next(1000,900,true,true,source()).kind);
        assertEquals(WAIT,s.next(1100,900,true,true,target()).kind);
        assertEquals(WAIT,s.next(1200,1000,true,true,target()).kind);
        assertEquals(WAIT,s.next(2500,1100,true,true,source()).kind);
        assertEquals(WAIT,s.next(2600,2601,true,true,source()).kind);
        assertEquals(TAP,s.next(2800,2700,true,true,source()).kind);
        assertEquals(WAIT,s.next(3900,2700,true,true,source()).kind);
        assertEquals(CONFIRMED,s.next(4000,3900,true,true,target()).kind);
    }

    @Test public void thirdCharacterStopsOnlyWithFreshSameAccountHud() throws Exception {
        CombatSwitch s=new CombatSwitch(1,0,1000);s.next(1000,900,true,true,source());
        ActiveCharacterRecognizer.State third=new ActiveCharacterRecognizer.State(2,new int[]{0,1},
                new float[]{.90f,.91f},new float[]{.25f,.39f});
        assertEquals(WAIT,s.next(2000,1900,false,true,third).kind);
        assertEquals(WAIT,s.next(2200,2100,true,false,third).kind);
        assertThrows(IllegalStateException.class,()->s.next(2400,2300,true,true,third));assertEquals(1,s.attempts());
    }

    @Test public void capsAtThreeTapsAndClosesInputAfterFiveSeconds() throws Exception {
        CombatSwitch s=new CombatSwitch(1,0,1000);
        assertEquals(TAP,s.next(1000,900,true,true,source()).kind);
        assertEquals(WAIT,s.next(1999,1900,true,true,source()).kind);
        assertEquals(TAP,s.next(2000,1950,true,true,source()).kind);
        assertEquals(TAP,s.next(3000,2900,true,true,source()).kind);
        assertEquals(WAIT,s.next(4000,3900,true,true,source()).kind);
        assertEquals(WAIT,s.next(5999,5900,true,true,source()).kind);assertEquals(3,s.attempts());
        assertEquals(WAIT,s.next(6000,5950,true,true,source()).kind);assertEquals(3,s.attempts());
        CombatSwitch unknown=new CombatSwitch(1,0,1000);
        assertEquals(WAIT,unknown.next(5999,5900,false,false,null).kind);
        assertThrows(IllegalStateException.class,()->unknown.next(6000,5950,true,true,source()));assertEquals(0,unknown.attempts());
    }

    @Test public void recordedLateIdentityGetsConfirmationOnlyGraceWithoutAnotherTap() throws Exception {
        CombatSwitch s=new CombatSwitch(1,0,1000);s.next(1000,900,true,true,source());
        assertEquals(WAIT,s.next(6000,5900,true,true,source()).kind);
        assertEquals(WAIT,s.next(6100,5950,true,false,target()).kind);
        assertEquals(WAIT,s.next(6200,6000,false,true,target()).kind);
        assertEquals(WAIT,s.next(6300,6000,true,true,target()).kind);
        assertEquals(CONFIRMED,s.next(6374,6034,true,true,target()).kind);
        assertEquals(1,s.attempts());
    }

    @Test public void confirmationGraceStillHasHardDeadlineAndRejectsThirdCharacter() throws Exception {
        CombatSwitch s=new CombatSwitch(1,0,1000);s.next(1000,900,true,true,source());
        assertEquals(WAIT,s.next(7499,7400,false,false,null).kind);
        assertThrows(IllegalStateException.class,()->s.next(7500,7450,true,true,target()));
        CombatSwitch other=new CombatSwitch(1,0,1000);other.next(1000,900,true,true,source());
        ActiveCharacterRecognizer.State third=new ActiveCharacterRecognizer.State(2,new int[]{0,1},
                new float[]{.90f,.91f},new float[]{.25f,.39f});
        assertThrows(IllegalStateException.class,()->other.next(6000,5900,true,true,third));
    }

    @Test public void invalidTargetCoordinatesAndMissingTargetCannotAuthorizeTap() throws Exception {
        CombatSwitch s=new CombatSwitch(1,0,1000);
        ActiveCharacterRecognizer.State missing=new ActiveCharacterRecognizer.State(1,new int[]{1,2},
                new float[]{.90f,.91f},new float[]{.25f,.39f});
        assertEquals(WAIT,s.next(1000,900,true,true,missing).kind);
        ActiveCharacterRecognizer.State bad=source();bad.x[0]=Float.NaN;
        assertEquals(WAIT,s.next(1200,1100,true,true,bad).kind);assertEquals(0,s.attempts());
        assertEquals(WAIT,s.next(1400,1300,true,true,target()).kind);
        assertEquals(TAP,s.next(1600,1500,true,true,source()).kind);
    }

    @Test public void cancellationStopsBeforeAnotherDecisionWithoutClearingInterrupt() throws Exception {
        CombatSwitch s=new CombatSwitch(1,0,1000);s.next(1000,900,true,true,source());
        try {
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class,()->s.next(2000,1900,true,true,source()));
            assertTrue(Thread.currentThread().isInterrupted());assertEquals(1,s.attempts());
        } finally {Thread.interrupted();}
    }
}
