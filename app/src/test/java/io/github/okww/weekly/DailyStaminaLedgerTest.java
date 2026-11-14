package io.github.okww.weekly;

import org.junit.Test;
import java.io.IOException;
import java.time.Instant;
import static org.junit.Assert.*;

public class DailyStaminaLedgerTest {
    private static final String UID="123456789";
    private static final long NOW=Instant.parse("2026-11-11T04:00:00Z").toEpochMilli();
    private static class Memory implements DailyStaminaLedger.Store {
        String value; boolean fail, discard;
        public String read() { return value; }
        public void write(String next) throws IOException { if(fail)throw new IOException("disk full");if(!discard)value=next; }
    }
    @Test public void fourClaimsPersistAcrossTaskRestartsAndFifthIsDenied() throws Exception {
        Memory s=new Memory();
        for(int n=0;n<4;n++) {
            DailyStaminaLedger l=new DailyStaminaLedger(s,UID,NOW);
            l.reserve(240-n*60,60,false,NOW);
            assertEquals(60,new DailyStaminaLedger(s,UID,NOW).reserved());
            l.confirm(180-n*60);
        }
        DailyStaminaLedger l=new DailyStaminaLedger(s,UID,NOW);
        assertEquals(240,l.confirmed());assertEquals(0,l.spentThisRun());
        assertFalse(l.canClaim(240,60,false));
        assertThrows(IllegalStateException.class,()->l.reserve(240,60,false,NOW));
    }
    @Test public void uncertainClaimSurvivesRestartAndEvenNextGameDay() throws Exception {
        Memory s=new Memory();DailyStaminaLedger l=new DailyStaminaLedger(s,UID,NOW);
        l.reserve(200,60,false,NOW);
        assertThrows(IllegalStateException.class,()->l.confirm(141));
        assertEquals(60,new DailyStaminaLedger(s,UID,NOW).reserved());
        assertFalse(new DailyStaminaLedger(s,UID,NOW).canClaim(140,60,false));
        assertThrows(IllegalStateException.class,()->new DailyStaminaLedger(s,UID,NOW+86400000));
    }
    @Test public void diskFailureOrSilentDiscardNeverAuthorizesTouch() throws Exception {
        Memory s=new Memory();DailyStaminaLedger l=new DailyStaminaLedger(s,UID,NOW);
        s.fail=true;assertThrows(IOException.class,()->l.reserve(240,60,false,NOW));
        assertEquals(0,l.reserved());assertNull(s.value);
        s.fail=false;s.discard=true;assertThrows(IOException.class,()->l.reserve(240,60,false,NOW));
        assertEquals(0,l.reserved());
        s.discard=false;l.reserve(240,60,false,NOW);s.fail=true;
        assertThrows(IOException.class,()->l.confirm(180));
        assertEquals(60,new DailyStaminaLedger(s,UID,NOW).reserved());
    }
    @Test public void gameDayMatchesPcFourAmAndCannotResetDuringRun() throws Exception {
        assertEquals("2026-11-10",DailyStaminaLedger.gameDay(Instant.parse("2026-11-10T19:59:59Z").toEpochMilli()));
        assertEquals("2026-11-11",DailyStaminaLedger.gameDay(Instant.parse("2026-11-10T20:00:00Z").toEpochMilli()));
        Memory s=new Memory();DailyStaminaLedger l=new DailyStaminaLedger(s,UID,NOW);
        l.reserve(240,60,false,NOW);l.confirm(180);
        assertThrows(IllegalStateException.class,()->l.reserve(180,60,false,NOW+86400000));
        assertEquals(0,new DailyStaminaLedger(s,UID,NOW+86400000).confirmed());
        assertThrows(IllegalStateException.class,()->new DailyStaminaLedger(s,UID,NOW-86400000));
    }
    @Test public void corruptionAccountMismatchBackupAndUnknownClaimsStop() throws Exception {
        Memory s=new Memory();DailyStaminaLedger l=new DailyStaminaLedger(s,UID,NOW);
        assertThrows(IllegalStateException.class,()->l.reserve(240,180,false,NOW));
        assertThrows(IllegalStateException.class,()->l.reserve(240,60,true,NOW));
        assertThrows(IllegalStateException.class,()->l.reserve(59,60,false,NOW));
        l.reserve(240,60,false,NOW);
        assertThrows(IllegalStateException.class,()->new DailyStaminaLedger(s,"987654321",NOW));
        s.value="bad";assertThrows(IllegalStateException.class,()->new DailyStaminaLedger(s,UID,NOW));
    }
    @Test public void doubleReservationPersistsExactlyAndCannotResetOrBeReusedAfterRestart() throws Exception {
        Memory s=new Memory();DailyStaminaLedger l=new DailyStaminaLedger(s,UID,NOW);
        l.reserve(240,120,false,NOW);String pending=s.value;
        assertEquals("1\n"+UID+"\n"+l.day()+"\n0:120:240",pending);
        DailyStaminaLedger restored=new DailyStaminaLedger(s,UID,NOW);
        assertEquals(120,restored.reserved());assertFalse(restored.canClaim(240,60,false));assertFalse(restored.canClaim(240,120,false));
        assertThrows(IllegalStateException.class,()->restored.reserve(240,120,false,NOW));
        assertThrows(IllegalStateException.class,()->restored.confirm(180));
        assertThrows(IllegalStateException.class,()->new DailyStaminaLedger(s,UID,NOW+86400000));
        assertEquals(pending,s.value);restored.confirm(120);
        DailyStaminaLedger next=new DailyStaminaLedger(s,UID,NOW);assertEquals(120,next.confirmed());assertEquals(0,next.reserved());
        next.reserve(120,120,false,NOW);next.confirm(0);
        assertEquals(240,new DailyStaminaLedger(s,UID,NOW).confirmed());
    }
    @Test public void doublePersistenceFailureNeverDropsTheActualPendingCost() throws Exception {
        Memory s=new Memory();DailyStaminaLedger l=new DailyStaminaLedger(s,UID,NOW);s.discard=true;
        assertThrows(IOException.class,()->l.reserve(240,120,false,NOW));assertEquals(0,l.reserved());
        s.discard=false;l.reserve(240,120,false,NOW);String pending=s.value;s.fail=true;
        assertThrows(IOException.class,()->l.confirm(120));assertEquals(pending,s.value);
        assertEquals(120,new DailyStaminaLedger(s,UID,NOW).reserved());
    }
}
