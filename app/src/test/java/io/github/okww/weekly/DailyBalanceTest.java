package io.github.okww.weekly;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.okww.weekly.GardenRules.*;

public class DailyBalanceTest {
    private Text t(String s,int l,int y,int r,int b){return new Text(s,l/2400f,y/1080f,r/2400f,b/1080f);}
    private Screen result(Text... balance){
        ArrayList<Text> words=new ArrayList<>(Arrays.asList(
                t("挑战成功",1095,294,1303,351),t("退出副本",891,908,1007,937),
                t("重新挑战",1392,907,1508,937),t("特征码:000000000",2097,1050,2286,1070)));
        words.addAll(Arrays.asList(balance));return new Screen(words,new int[3],2400f/1080);
    }
    @Test public void redSingleDigitMayBeMergedOrSeparateButNeverInferred(){
        assertNull(DailyTacet.balance(result(t("剩余",1391,980,1435,1003))));
        assertEquals(Integer.valueOf(8),DailyTacet.balance(result(t("剩余8",1391,980,1454,1003))));
        assertEquals(Integer.valueOf(8),DailyTacet.balance(result(t("剩余",1391,980,1435,1003),t("8",1439,980,1454,1003))));
        assertEquals(Integer.valueOf(0),DailyTacet.balance(result(t("剩余",1391,980,1435,1003),t("0",1439,980,1454,1003))));
    }
    @Test public void nearbyLootCountdownAndConflictingNumbersAreNotBalances(){
        Text label=t("剩余",1391,980,1435,1003),digit=t("8",1439,980,1454,1003);
        assertNull(DailyTacet.balance(result(label,t("8",1439,850,1454,875))));
        assertNull(DailyTacet.balance(result(label,t("120",1600,980,1650,1003))));
        assertNull(DailyTacet.balance(result(label,digit,t("9",1460,980,1475,1003))));
        assertNull(DailyTacet.balance(result(label,t("241",1439,980,1480,1003))));
        assertNull(DailyTacet.balance(result(label,digit,t("9/240",1700,50,1790,76))));
        Screen incomplete=result(label,digit);incomplete.words.remove(0);
        assertFalse(DailyTacet.settlementPage(incomplete));assertNull(DailyTacet.balance(incomplete));
    }
    @Test public void pendingLedgerInterceptsNavigationAndOnlyExitsAfterTwoBalances()throws Exception{
        long now=java.time.Instant.parse("2026-11-11T08:00:00Z").toEpochMilli();
        DailyStaminaLedger ledger=new DailyStaminaLedger(new DailyStaminaLedger.Store(){
            String saved="1\n000000000\n2026-11-11\n120:120:128";
            public String read(){return saved;}public void write(String s){saved=s;}
        },"000000000",now);
        DailyTacet tacet=new DailyTacet();tacet.attachLedger(ledger);DailyRules rules=new DailyRules(tacet);
        Screen page=result(t("剩余8",1391,980,1454,1003));
        for(int i=1;i<=2;i++){
            rules.resourceFrame(now+i*100,now+i*100+10);
            assertEquals(Kind.WAIT,rules.next(page).kind);assertEquals(120,ledger.reserved());
        }
        rules.resourceFrame(now+300,now+310);Action exit=rules.next(page);
        assertEquals("daily-pending-exit",exit.scene);assertEquals(240,ledger.confirmed());assertEquals(0,ledger.reserved());
        assertTrue(rules.budgetComplete());rules.applied(exit);
        rules.resourceFrame(now+400,now+410);assertEquals("daily-pending-exit-wait",rules.next(page).scene);
    }
    @Test public void resultPageWaitsForAccountLedgerBeforeLegacyUnknownStop(){
        DailyRules rules=new DailyRules(new DailyTacet());
        assertEquals("daily-settlement-ledger-wait",rules.next(result(t("剩余8",1391,980,1454,1003))).scene);
    }
}
