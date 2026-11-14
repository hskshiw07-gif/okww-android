package io.github.okww.weekly;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.okww.weekly.DailyStartGate.Result.*;

public class DailyStartGateTest {
    @Test public void waitsForForegroundThenStartsOnlyOnce() {
        DailyStartGate gate = new DailyStartGate(7, 100);
        assertEquals(WAIT,gate.next(101,7,true,true,false,false));
        assertEquals(WAIT,gate.next(200,7,true,true,false,true));
        assertEquals(START,gate.next(1700,7,true,true,false,true));
        assertEquals(CANCEL,gate.next(1701,7,true,true,false,true));
    }
    @Test public void foregroundFlickerRestartsStabilityWindow() {
        DailyStartGate gate = new DailyStartGate(7, 100);
        assertEquals(WAIT,gate.next(200,7,true,true,false,true));
        assertEquals(WAIT,gate.next(1000,7,true,true,false,false));
        assertEquals(WAIT,gate.next(1700,7,true,true,false,true));
        assertEquals(WAIT,gate.next(3100,7,true,true,false,true));
        assertEquals(START,gate.next(3200,7,true,true,false,true));
    }
    @Test public void stopTaskChangeLostPermissionBusyOrTimeoutCannotStartLater() {
        for(int reason=0;reason<5;reason++) {
            DailyStartGate gate = new DailyStartGate(7,100);
            assertEquals(CANCEL,gate.next(reason==4?15100:200,reason==0?8:7,
                    reason!=1,reason!=2,reason==3,true));
            assertEquals(CANCEL,gate.next(201,7,true,true,false,true));
        }
    }
    @Test public void confirmedDailySurvivesOnlyThisPermissionFlow() {
        PreparationFlow flow = new PreparationFlow(null); flow.confirmDaily();
        flow.begin(false,false);flow.accessibilityReturned();flow.poll(true,false);
        flow.captureReturned(true);assertEquals(PreparationFlow.Action.ENTER_GAME,flow.poll(true,true));
        assertTrue(flow.consumeDailyStart());assertFalse(flow.consumeDailyStart());
    }
    @Test public void cancelDeniedCaptureRecreationAndOrdinaryPreparationNeverAutoStart() {
        PreparationFlow f = new PreparationFlow(null);assertFalse(f.consumeDailyStart());
        f.confirmDaily();f.cancel();assertFalse(f.consumeDailyStart());
        f.confirmDaily();f.begin(true,false);f.captureReturned(false);assertFalse(f.consumeDailyStart());
        f.confirmDaily();f.begin(true,false);assertFalse(new PreparationFlow(f.save()).consumeDailyStart());
    }
}
