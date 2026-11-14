package io.github.okww.weekly;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.okww.weekly.GardenRules.*;

public class DailyPendingSettlementTest {
    private static final String UID="333333333";
    private static final long NOW=java.time.Instant.parse("2026-11-11T04:00:00Z").toEpochMilli();
    private static final class Memory implements DailyStaminaLedger.Store {
        String value;boolean fail;int writes;
        public String read(){return value;}
        public void write(String next)throws IOException{if(fail)throw new IOException("test write failed");value=next;writes++;}
    }
    private static final class Harness {
        final Memory memory=new Memory();final DailyStaminaLedger ledger;final DailyPendingSettlement pending;
        Harness()throws Exception{this(120,128);}
        Harness(int cost,int before)throws Exception {
            DailyStaminaLedger old=new DailyStaminaLedger(memory,UID,NOW);
            if(cost==120){old.reserve(240,120,false,NOW);old.confirm(120);}
            old.reserve(before,cost,false,NOW);
            ledger=new DailyStaminaLedger(memory,UID,NOW);pending=new DailyPendingSettlement(ledger);
        }
        Action frame(Screen s,long offset){return pending.next(s,UID,NOW+offset,NOW+offset+10);}
    }
    private static Text t(String text,float x,float y){return new Text(text,x-.005f,y-.005f,x+.005f,y+.005f);}
    private static Screen screen(Text... words){return new Screen(new ArrayList<>(Arrays.asList(words)),new int[3],2400f/1080);}
    private static Screen result(String balance){return screen(t("挑战成功",.5f,.30f),t("退出副本",.395f,.854f),
            t("重新挑战",.604f,.854f),t("剩余"+balance,.59f,.916f),t("特征码:"+UID,.913f,.981f),t("X8",.65f,.50f));}
    private static Screen world(){return screen(t("Lv.90",.58f,.94f),t("特征码:"+UID,.913f,.981f));}

    @Test public void actualPending120From128NeedsTwoIndependentEightFramesThenOnlyExits()throws Exception {
        Harness h=new Harness();assertEquals(8,h.ledger.expectedAfter());assertTrue(h.memory.value.endsWith("120:120:128"));
        assertEquals(Kind.WAIT,h.frame(result("8"),100).kind);assertEquals(120,h.ledger.reserved());
        int writes=h.memory.writes;Action exit=h.frame(result("8"),200);
        assertEquals("daily-pending-exit",exit.scene);assertEquals(.395f,exit.x,.0001f);
        assertEquals(240,h.ledger.confirmed());assertEquals(0,h.ledger.reserved());
        assertTrue(h.memory.value.endsWith("240:0:-1"));assertEquals(writes+1,h.memory.writes);
        h.pending.applied(exit);assertEquals(Kind.WAIT,h.frame(result("8"),300).kind);
        assertEquals(writes+1,h.memory.writes);assertNull(h.frame(world(),400));assertNull(h.frame(world(),500));
    }
    @Test public void oldSingleReservationAlsoReconcilesWithoutResubmitting()throws Exception {
        Harness h=new Harness(60,68);h.frame(result("8"),100);
        assertEquals("daily-pending-exit",h.frame(result("8"),200).scene);
        assertEquals(60,h.ledger.confirmed());assertEquals(0,h.ledger.reserved());
    }
    @Test public void missingDigitWrongDeltaAndRewardAmountsNeverReleasePending()throws Exception {
        Harness h=new Harness();String saved=h.memory.value;long time=100;
        for(String balance:new String[]{"","7","9","68","128"})for(int i=0;i<2;i++)
            assertEquals(Kind.WAIT,h.frame(result(balance),time+=50).kind);
        assertEquals(saved,h.memory.value);assertEquals(120,h.ledger.reserved());assertEquals(120,h.ledger.confirmed());
        assertEquals(Kind.WAIT,h.frame(result("8"),1000).kind);
        assertEquals("daily-pending-exit",h.frame(result("8"),1100).scene);
    }
    @Test public void unknownUidOverlayLayoutOrIncompleteResultsBreakThePair()throws Exception {
        for(int kind=0;kind<7;kind++) {
            Harness h=new Harness();h.frame(result("8"),100);Screen bad=result("8");String confirmed=UID;
            if(kind==0)bad.words.remove(4);
            if(kind==1)bad.words.set(4,t("特征码:000000000",.913f,.981f));
            if(kind==2)confirmed="000000000";
            if(kind==3){bad.words.add(t("保存现场",.2f,.2f));bad.words.add(t("开跑",.3f,.2f));bad.words.add(t("识别",.4f,.2f));}
            if(kind==4)bad=new Screen(bad.words,new int[3],16f/9);
            if(kind==5)bad.words.remove(2);
            if(kind==6)bad.words.add(t("确认",.6f,.6f));
            assertEquals(Kind.WAIT,h.pending.next(bad,confirmed,NOW+200,NOW+210).kind);
            assertEquals(Kind.WAIT,h.frame(result("8"),300).kind);assertEquals(120,h.ledger.reserved());
            assertEquals("daily-pending-exit",h.frame(result("8"),400).scene);
        }
    }
    @Test public void reusedStaleOrWidelySeparatedFramesNeedACompletelyNewPair()throws Exception {
        Harness h=new Harness();h.frame(result("8"),100);
        assertEquals(Kind.WAIT,h.pending.next(result("8"),UID,NOW+100,NOW+210).kind);
        assertEquals(Kind.WAIT,h.frame(result("8"),300).kind);
        assertEquals(Kind.WAIT,h.pending.next(result("8"),UID,NOW+400,NOW+3901).kind);
        assertEquals(Kind.WAIT,h.frame(result("8"),4000).kind);
        assertEquals(Kind.WAIT,h.frame(result("8"),8000).kind);
        assertEquals("daily-pending-exit",h.frame(result("8"),8100).scene);
    }
    @Test public void backwardClockAndChangedDayStopWithoutFreeingReservation()throws Exception {
        Harness backward=new Harness();backward.frame(result("8"),100);String saved=backward.memory.value;
        assertEquals(Kind.STOP,backward.pending.next(result("8"),UID,NOW+90,NOW+100).kind);
        assertEquals(Kind.STOP,backward.frame(result("8"),200).kind);assertEquals(saved,backward.memory.value);
        Harness tomorrow=new Harness();tomorrow.frame(result("8"),100);saved=tomorrow.memory.value;
        assertEquals(Kind.STOP,tomorrow.pending.next(result("8"),UID,NOW+86400000,NOW+86400010).kind);
        assertEquals(saved,tomorrow.memory.value);
    }
    @Test public void worldBeforeReconciliationAndWriteFailureNeverGuessCompletion()throws Exception {
        Harness world=new Harness();String saved=world.memory.value;
        assertEquals(Kind.STOP,world.frame(world(),100).kind);assertEquals(saved,world.memory.value);
        Harness failed=new Harness();failed.frame(result("8"),100);saved=failed.memory.value;failed.memory.fail=true;
        assertEquals(Kind.STOP,failed.frame(result("8"),200).kind);assertEquals(saved,failed.memory.value);
        assertEquals(120,failed.ledger.reserved());assertEquals(120,failed.ledger.confirmed());
    }
    @Test public void noPendingIsANoOpAndInterruptedRecoveryNeverWrites()throws Exception {
        Memory memory=new Memory();DailyStaminaLedger clean=new DailyStaminaLedger(memory,UID,NOW);
        assertNull(new DailyPendingSettlement(clean).next(result("8"),UID,NOW,NOW));assertEquals(0,memory.writes);
        Harness h=new Harness();String saved=h.memory.value;
        try{Thread.currentThread().interrupt();assertEquals(Kind.STOP,h.frame(result("8"),100).kind);}
        finally{Thread.interrupted();}
        assertEquals(saved,h.memory.value);
    }
}
