package io.github.okww.weekly;
import org.junit.Test;
import java.time.Instant;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

public class RedeemDataTest {
    static final long NOW=Instant.parse("2026-11-11T08:00:00Z").toEpochMilli();
    static final class Memory implements DailyStaminaLedger.Store {
        String data;boolean fail,drop;
        public String read(){return data;}
        public void write(String text)throws IOException{if(fail)throw new IOException("disk full");if(!drop)data=text;}
    }
    @Test public void manualParsingIsBoundedExactAndOrdered(){
        RedeemCodes codes=new RedeemCodes();codes.manual("Abcd, EFGH\nABCD；IJKL");
        assertEquals(List.of("ABCD","EFGH","IJKL"),codes.selected(NOW));
        String before=codes.save();assertThrows(IllegalArgumentException.class,()->codes.manual("VALID1 bad$input"));assertEquals(before,codes.save());
        assertThrows(IllegalArgumentException.class,()->codes.manual("ＡＢＣＤ"));
        assertNotEquals(RedeemCodes.normalize("AB0D"),RedeemCodes.normalize("ABOD"));
    }
    @Test public void fetchedCodesStayDisabledAndIgnoredChoicesSurviveRefresh(){
        RedeemCodes codes=new RedeemCodes();codes.manual("OLD1");codes.codes.put("OLD1",codes.codes.get("OLD1").enabled(false));
        codes.merge(List.of(new RedeemCodes.Code("NEW1","星声100","",true,false,true),new RedeemCodes.Code("OLD1","","",true,false,true)),NOW);
        assertTrue(codes.selected(NOW).isEmpty());assertFalse(codes.codes.get("NEW1").enabled);assertFalse(codes.codes.get("OLD1").enabled);
        codes.codes.put("NEW1",codes.codes.get("NEW1").enabled(true));assertEquals(List.of("NEW1"),codes.selected(NOW));
    }
    @Test public void invalidAndExpiredCodesNeverRunEvenIfPreviouslyEnabled(){
        RedeemCodes codes=new RedeemCodes();codes.manual("MINGCHAO EXPIRED FAILED GOOD1");
        codes.merge(List.of(new RedeemCodes.Code("EXPIRED","","11月10日23点59分",false,false,true),new RedeemCodes.Code("FAILED","","",false,true,true)),NOW);
        assertEquals(List.of("GOOD1"),codes.selected(NOW));
        assertTrue(RedeemCodes.expired("2月30日",NOW));assertFalse(RedeemCodes.expired("2027-01-01 12:30",NOW));
        assertFalse(RedeemCodes.expired("长期有效",NOW));
    }
    @Test public void catalogRoundTripCorruptionAndDuplicateFeedDoNotResetData(){
        RedeemCodes codes=new RedeemCodes();codes.manual("TEST1");String before=codes.save();
        assertEquals(before,RedeemCodes.parse(before).save());
        RedeemCodes.Code c=new RedeemCodes.Code("DUP1","","",false,false,true);
        assertThrows(IllegalArgumentException.class,()->codes.merge(List.of(c,c),NOW));assertEquals(before,codes.save());
        for(String invalid:List.of("", "2\t0", "1\t0\nABC\t1\t0\t0\t\t", "1\t-1"))assertThrows(RuntimeException.class,()->RedeemCodes.parse(invalid));
    }
    @Test public void submissionPersistsBeforeResultAndSurvivesRestart()throws Exception{
        Memory store=new Memory();RedeemLedger ledger=new RedeemLedger(store);ledger.submit("111111111","TEST1",NOW);
        RedeemLedger restart=new RedeemLedger(store);assertThrows(IllegalStateException.class,()->restart.requireNoPending("111111111"));
        assertThrows(IllegalStateException.class,()->restart.submit("111111111","TEST1",NOW));
        restart.result("111111111","TEST1",RedeemLedger.Status.SUCCESS,NOW+1,"兑换成功");
        assertTrue(new RedeemLedger(store).get("111111111","TEST1").terminal());
        assertThrows(IllegalStateException.class,()->restart.submit("111111111","TEST1",NOW+2));
    }
    @Test public void sameCodeDifferentUidHasIndependentOutcome()throws Exception{
        RedeemLedger ledger=new RedeemLedger(new Memory());ledger.submit("111111111","TEST1",NOW);ledger.submit("222222222","TEST1",NOW);
        ledger.result("111111111","TEST1",RedeemLedger.Status.ALREADY_USED,NOW,"已使用");
        assertEquals(RedeemLedger.Status.PENDING,ledger.get("222222222","TEST1").status);
        assertThrows(IllegalStateException.class,()->ledger.result("333333333","TEST1",RedeemLedger.Status.SUCCESS,NOW,"wrong uid"));
    }
    @Test public void storageFailureCannotAdvanceInMemoryOrErasePending()throws Exception{
        for(boolean drop:List.of(false,true)){
            Memory store=new Memory();RedeemLedger ledger=new RedeemLedger(store);store.drop=drop;store.fail=!drop;
            assertThrows(IOException.class,()->ledger.submit("111111111","TEST1",NOW));assertNull(ledger.get("111111111","TEST1"));
        }
        Memory store=new Memory();RedeemLedger ledger=new RedeemLedger(store);ledger.submit("111111111","TEST1",NOW);store.fail=true;
        assertThrows(IOException.class,()->ledger.result("111111111","TEST1",RedeemLedger.Status.SUCCESS,NOW,"ok"));
        assertEquals(RedeemLedger.Status.PENDING,ledger.get("111111111","TEST1").status);
    }
    @Test public void onlyExplicitReviewUnlocksUnknownSubmission()throws Exception{
        Memory store=new Memory();RedeemLedger ledger=new RedeemLedger(store);ledger.submit("111111111","TEST1",NOW);
        ledger.approveRetry("111111111","TEST1",NOW+1);ledger.requireNoPending("111111111");
        assertFalse(ledger.get("111111111","TEST1").terminal());ledger.submit("111111111","TEST1",NOW+2);
        assertThrows(IllegalStateException.class,()->ledger.approveRetry("111111111","TEST1",NOW));
        store.data="garbage";assertThrows(IllegalArgumentException.class,()->new RedeemLedger(store));assertEquals("garbage",store.data);
    }
    @Test public void jsonpWrapperIsDataOnlyAndMalformedBodiesFail(){
        assertEquals("[]",RedeemFeed.payload("var codes=[];"));
        assertEquals("[]",RedeemFeed.payload("callback([]);"));
        assertThrows(IllegalArgumentException.class,()->RedeemFeed.payload("<html>maintenance</html>"));
    }
}
