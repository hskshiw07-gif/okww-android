package io.github.okww.weekly;

import org.junit.Test;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class MultiAccountDailyTest {
    static final long NOW=Instant.parse("2026-11-11T08:00:00Z").toEpochMilli();
    static final MultiAccountPlan.Account A=new MultiAccountPlan.Account("demo-a","111111111",2,true);
    static final MultiAccountPlan.Account B=new MultiAccountPlan.Account("Ab****cd@example.com","222222222",3,true);
    static class Memory implements DailyStaminaLedger.Store {
        String data;boolean fail,drop;
        public String read(){return data;}
        public void write(String value)throws IOException{if(fail)throw new IOException("disk full");if(!drop)data=value;}
    }
    static class Port implements MultiAccountDaily.Port {
        long time=NOW;String current=B.uid;
        List<String> events=new ArrayList<>();boolean incomplete,wrong,fail,cancel,midnight,pendingLedger;
        public long now(){return time;}
        public void checkLedger(String uid){events.add("ledger:"+uid);if(pendingLedger)throw new IllegalStateException("pending charge");}
        public String identifyCurrent(){events.add("identify");return current;}
        public String switchAccount(String source,MultiAccountPlan.Account a){events.add("switch:"+a.uid);current=wrong?"999999999":a.uid;return current;}
        public DailyResult runDaily(MultiAccountPlan.Account a)throws Exception{
            events.add("daily:"+a.uid);if(fail)throw new IOException("daily failed");if(cancel)throw new InterruptedException();
            if(midnight)time+=24*60*60*1000L;
            return new DailyResult(a.uid,DailyStaminaLedger.gameDay(NOW),!incomplete,120,0);
        }
        public void progress(MultiAccountProgress p){events.add("saved");}
    }
    static MultiAccountPlan plan(){return new MultiAccountPlan(Arrays.asList(A,B));}

    @Test public void currentFirstThenOrderAndFreshResultCompletesEachAccount()throws Exception{
        Memory store=new Memory();MultiAccountProgress p=new MultiAccountProgress(store,NOW);Port port=new Port();
        assertTrue(MultiAccountDaily.run(plan(),p,port));
        assertTrue(port.events.indexOf("daily:"+B.uid)<port.events.indexOf("daily:"+A.uid));
        assertFalse(port.events.contains("switch:"+B.uid));assertTrue(p.completed(A));assertTrue(p.completed(B));
        MultiAccountProgress restarted=new MultiAccountProgress(store,NOW+1);Port next=new Port();
        assertTrue(MultiAccountDaily.run(plan(),restarted,next));assertEquals(Arrays.asList("ledger:"+A.uid,"ledger:"+B.uid,"saved"),next.events);
    }
    @Test public void pendingIsNotCompletedAndIsRetryableAfterRestart()throws Exception{
        Memory store=new Memory();MultiAccountProgress p=new MultiAccountProgress(store,NOW);Port port=new Port();port.incomplete=true;
        assertFalse(MultiAccountDaily.run(plan(),p,port));assertFalse(p.completed(A));
        assertEquals(MultiAccountProgress.Status.PENDING,p.entries().get(A.uid).status);
        assertTrue(MultiAccountDaily.run(plan(),new MultiAccountProgress(store,NOW+1),new Port()));
    }
    @Test public void disabledAccountsAreNeverSwitchedOrRun()throws Exception{
        MultiAccountPlan plan=new MultiAccountPlan(Arrays.asList(A,new MultiAccountPlan.Account(B.login,B.uid,3,false)));
        Port port=new Port();assertTrue(MultiAccountDaily.run(plan,new MultiAccountProgress(new Memory(),NOW),port));
        assertFalse(port.events.contains("daily:"+B.uid));
    }
    @Test public void wrongUidAfterLoginNeverStartsThatDaily()throws Exception{
        Port port=new Port();port.current="999999999";port.wrong=true;
        MultiAccountProgress p=new MultiAccountProgress(new Memory(),NOW);
        assertThrows(IllegalStateException.class,()->MultiAccountDaily.run(plan(),p,port));
        assertFalse(port.events.stream().anyMatch(e->e.startsWith("daily:")));
        assertEquals(MultiAccountProgress.Status.FAILED,p.entries().get(A.uid).status);
    }
    @Test public void failedOrCancelledChildStopsBeforeNextAccount()throws Exception{
        for(boolean cancel:new boolean[]{false,true}){
            Port port=new Port();port.fail=!cancel;port.cancel=cancel;MultiAccountProgress p=new MultiAccountProgress(new Memory(),NOW);
            assertThrows(Exception.class,()->MultiAccountDaily.run(plan(),p,port));
            assertFalse(port.events.contains("switch:"+A.uid));
            assertEquals(cancel?MultiAccountProgress.Status.CANCELLED:MultiAccountProgress.Status.FAILED,p.entries().get(B.uid).status);
        }
    }
    @Test public void runningCheckpointIsNotSuccessfulAfterProcessDeath()throws Exception{
        Memory store=new Memory();MultiAccountProgress p=new MultiAccountProgress(store,NOW);p.mark(A,MultiAccountProgress.Status.RUNNING,NOW);
        assertFalse(new MultiAccountProgress(store,NOW+1).completed(A));
    }
    @Test public void persistenceFailurePreventsFirstAccountAction()throws Exception{
        for(boolean drop:new boolean[]{false,true}){
            Memory store=new Memory();store.fail=!drop;store.drop=drop;Port port=new Port();
            MultiAccountProgress p=new MultiAccountProgress(store,NOW);
            assertThrows(IOException.class,()->MultiAccountDaily.run(plan(),p,port));
            assertFalse(port.events.stream().anyMatch(e->e.startsWith("switch:")||e.startsWith("daily:")));
        }
    }
    @Test public void pendingLedgerBlocksEvenIfCompletionCheckpointSaysCompleted()throws Exception{
        Memory store=new Memory();MultiAccountProgress p=new MultiAccountProgress(store,NOW);
        p.mark(A,MultiAccountProgress.Status.COMPLETED,NOW);p.mark(B,MultiAccountProgress.Status.COMPLETED,NOW);
        Port port=new Port();port.pendingLedger=true;
        assertThrows(IllegalStateException.class,()->MultiAccountDaily.run(plan(),p,port));assertEquals(List.of("ledger:"+A.uid),port.events);
    }
    @Test public void unconfiguredSourceWithPendingChargeIsNeverLoggedOut()throws Exception{
        Port port=new Port(){@Override public void checkLedger(String uid){super.checkLedger(uid);if(uid.equals("999999999"))throw new IllegalStateException("source charge");}};
        port.current="999999999";MultiAccountProgress p=new MultiAccountProgress(new Memory(),NOW);
        assertThrows(IllegalStateException.class,()->MultiAccountDaily.run(plan(),p,port));
        assertFalse(port.events.stream().anyMatch(e->e.startsWith("switch:")||e.startsWith("daily:")));
    }
    @Test public void gameDayBoundaryStopsInsteadOfSavingSuccessIntoTheWrongDay()throws Exception{
        Port port=new Port();port.midnight=true;MultiAccountProgress p=new MultiAccountProgress(new Memory(),NOW);
        assertThrows(IllegalStateException.class,()->MultiAccountDaily.run(plan(),p,port));assertFalse(p.completed(B));
        assertFalse(port.events.contains("switch:"+A.uid));
    }
    @Test public void changedTargetDoesNotReuseOldCompletionAndNewDayStartsPending()throws Exception{
        Memory store=new Memory();MultiAccountProgress p=new MultiAccountProgress(store,NOW);p.mark(A,MultiAccountProgress.Status.COMPLETED,NOW);
        assertFalse(p.completed(new MultiAccountPlan.Account(A.login,A.uid,4,true)));
        assertFalse(new MultiAccountProgress(store,NOW+86400000).completed(A));
        assertThrows(IllegalStateException.class,()->new MultiAccountProgress(store,NOW-86400000));
    }
    @Test public void corruptProgressFailsClosedWithoutOverwriting() {
        for(String value:List.of("garbage","1\n2026-11-11\n111111111\tCOMPLETED\t0\tx\t0")){
            Memory store=new Memory();store.data=value;assertThrows(RuntimeException.class,()->new MultiAccountProgress(store,NOW));assertEquals(value,store.data);
        }
    }
    @Test public void invalidResultNeverBecomesComplete()throws Exception{
        for(int variant=0;variant<3;variant++){
            final int mode=variant;
            Port port=new Port(){@Override public DailyResult runDaily(MultiAccountPlan.Account a){
                return new DailyResult(mode==0?A.uid:a.uid,mode==1?"2026-11-10":"2026-11-11",false,0,mode==2?60:0);
            }};
            MultiAccountProgress p=new MultiAccountProgress(new Memory(),NOW);
            assertThrows(IllegalStateException.class,()->MultiAccountDaily.run(plan(),p,port));assertFalse(p.completed(B));
        }
    }
    @Test public void interruptedRunMakesNoInputs()throws Exception{
        Port port=new Port();MultiAccountProgress p=new MultiAccountProgress(new Memory(),NOW);Thread.currentThread().interrupt();
        try{assertThrows(InterruptedException.class,()->MultiAccountDaily.run(plan(),p,port));}finally{Thread.interrupted();}
        assertTrue(port.events.isEmpty());
    }
    @Test public void privatePlanRoundTripsAndIdentityIsNotFuzzy(){
        assertEquals(plan().save(),MultiAccountPlan.parse(plan().save()).save());assertEquals("demo-a",MultiAccountPlan.key(A.login));
        assertNotEquals(MultiAccountPlan.key("Ab0.con"),MultiAccountPlan.key("abO.com"));
        assertNotEquals(MultiAccountPlan.key("Some Name"),MultiAccountPlan.key("SomeName"));
        assertEquals("1",MultiAccountPlan.parse(null).save());
    }
    @Test public void duplicateIdentityUidAndInvalidPlanAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new MultiAccountPlan(Arrays.asList(A,new MultiAccountPlan.Account("demo-a",B.uid,1,false))));
        assertThrows(IllegalArgumentException.class,()->new MultiAccountPlan(Arrays.asList(A,new MultiAccountPlan.Account("other",A.uid,1,true))));
        for(String value:List.of("","2","1\n","1\n1\tx\t12345678\t1","1\n1\tx\t123456789\t22"))
            assertThrows(RuntimeException.class,()->MultiAccountPlan.parse(value));
        assertThrows(IllegalArgumentException.class,()->new MultiAccountPlan.Account("*\nx","123456789",1,true));
    }
}
