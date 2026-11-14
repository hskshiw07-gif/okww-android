package io.github.okww.weekly;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class RedeemBatchTest {
    static final MultiAccountPlan.Account A=new MultiAccountPlan.Account("one****", "111111111",1,true),B=new MultiAccountPlan.Account("two****","222222222",2,true);
    static MultiAccountPlan plan(){return new MultiAccountPlan(List.of(A,B));}
    static class Port implements RedeemBatch.Port {
        String uid=B.uid;boolean wrong,mail=true,failure,unproven;final List<String> calls=new ArrayList<>();
        public String current(){return uid;}
        public void checkWaveplateLedger(String uid){calls.add("ledger:"+uid);}
        public String switchTo(String source,MultiAccountPlan.Account target){calls.add("switch:"+target.uid);return wrong?"999999999":target.uid;}
        public boolean redeem(String uid,List<String> codes,RedeemLedger ledger)throws Exception{
            calls.add("redeem:"+uid);if(failure)throw new IllegalStateException("stop");
            if(!unproven)for(String code:codes){RedeemLedger.Entry old=ledger.get(uid,code);if(old!=null&&old.terminal())continue;
                ledger.submit(uid,code,RedeemDataTest.NOW);ledger.result(uid,code,RedeemLedger.Status.SUCCESS,RedeemDataTest.NOW,"ok");}
            return mail;
        }
    }
    @Test public void multiUsesCurrentFirstAndDisabledAccountsNeverRun()throws Exception{
        Port p=new Port();RedeemLedger ledger=new RedeemLedger(new RedeemDataTest.Memory());
        assertTrue(RedeemBatch.run(true,plan(),List.of("TEST1"),ledger,p));
        assertTrue(p.calls.indexOf("redeem:"+B.uid)<p.calls.indexOf("switch:"+A.uid));
        p=new Port();MultiAccountPlan disabled=new MultiAccountPlan(List.of(A,new MultiAccountPlan.Account(B.login,B.uid,2,false)));
        assertTrue(RedeemBatch.run(true,disabled,List.of("TEST1"),ledger,p));assertFalse(p.calls.contains("redeem:"+B.uid));
    }
    @Test public void singleDoesNotRequireBindingOrSwitchOrWaveplateWork()throws Exception{
        Port p=new Port();p.uid="999999999";
        assertTrue(RedeemBatch.run(false,new MultiAccountPlan(List.of()),List.of("TEST1"),new RedeemLedger(new RedeemDataTest.Memory()),p));
        assertEquals(List.of("redeem:999999999"),p.calls);
    }
    @Test public void wrongUidOrUnprovenNormalReturnNeverBecomesComplete()throws Exception{
        Port wrong=new Port();wrong.wrong=true;wrong.uid="999999999";
        assertThrows(IllegalStateException.class,()->RedeemBatch.run(true,plan(),List.of("TEST1"),new RedeemLedger(new RedeemDataTest.Memory()),wrong));
        assertFalse(wrong.calls.stream().anyMatch(v->v.startsWith("redeem:")));
        Port p=new Port();p.unproven=true;
        assertThrows(IllegalStateException.class,()->RedeemBatch.run(false,plan(),List.of("TEST1"),new RedeemLedger(new RedeemDataTest.Memory()),p));
    }
    @Test public void unknownSubmissionInAnyEnabledAccountBlocksBeforeSwitching()throws Exception{
        RedeemLedger ledger=new RedeemLedger(new RedeemDataTest.Memory());ledger.submit(A.uid,"OTHER1",RedeemDataTest.NOW);Port p=new Port();
        assertThrows(IllegalStateException.class,()->RedeemBatch.run(true,plan(),List.of("TEST1"),ledger,p));
        assertFalse(p.calls.stream().anyMatch(v->v.startsWith("switch:")||v.startsWith("redeem:")));
    }
    @Test public void failureOrUnverifiedMailStopsTheRemainingAccounts()throws Exception{
        Port p=new Port();p.failure=true;assertThrows(IllegalStateException.class,()->RedeemBatch.run(true,plan(),List.of("TEST1"),new RedeemLedger(new RedeemDataTest.Memory()),p));
        assertFalse(p.calls.contains("switch:"+A.uid));
        Port noMail=new Port();noMail.mail=false;assertFalse(RedeemBatch.run(true,plan(),List.of("TEST1"),new RedeemLedger(new RedeemDataTest.Memory()),noMail));
        assertFalse(noMail.calls.contains("switch:"+A.uid));
    }
    @Test public void unconfiguredSourcePendingWaveplatesBlockEveryInput()throws Exception{
        Port p=new Port(){@Override public void checkWaveplateLedger(String uid){super.checkWaveplateLedger(uid);
            if(uid.equals("999999999"))throw new IllegalStateException("source pending");}};p.uid="999999999";
        assertThrows(IllegalStateException.class,()->RedeemBatch.run(true,plan(),List.of("TEST1"),new RedeemLedger(new RedeemDataTest.Memory()),p));
        assertEquals(List.of("ledger:999999999"),p.calls);
    }
    @Test public void enabledTargetLedgerFailureBlocksBeforeCurrentAccountRedemption()throws Exception{
        Port p=new Port(){@Override public void checkWaveplateLedger(String uid){super.checkWaveplateLedger(uid);
            if(uid.equals(A.uid))throw new IllegalStateException("target unreadable or pending");}};
        assertThrows(IllegalStateException.class,()->RedeemBatch.run(true,plan(),List.of("TEST1"),new RedeemLedger(new RedeemDataTest.Memory()),p));
        assertFalse(p.calls.stream().anyMatch(v->v.startsWith("switch:")||v.startsWith("redeem:")));
    }
    @Test public void cancellationAndEmptySelectionHaveNoInputs()throws Exception{
        Port p=new Port();assertThrows(IllegalStateException.class,()->RedeemBatch.run(true,plan(),List.of(),new RedeemLedger(new RedeemDataTest.Memory()),p));
        Thread.currentThread().interrupt();try{assertThrows(InterruptedException.class,()->RedeemBatch.run(true,plan(),List.of("TEST1"),new RedeemLedger(new RedeemDataTest.Memory()),p));}finally{Thread.interrupted();}
        assertTrue(p.calls.isEmpty());
    }
}
