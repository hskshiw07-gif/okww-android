package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;
import static io.github.okww.weekly.GardenRules.*;

public class MultiAccountBudgetTest {
    private Screen book(int balance,String uid){
        ArrayList<Text> words=new ArrayList<>();words.add(t("素材获取",.1f,.06f));words.add(t("声骸材料",.5f,.2f));
        words.add(t("无音区",.6f,.3f));words.add(t(balance+"/240",.72f,.06f));words.add(t("特征码:"+uid,.90f,.98f));
        for(float y:new float[]{.26f,.44f,.62f,.80f})words.add(t("直接挑战",.88f,y));
        return new Screen(words,new int[0],2400f/1080);
    }
    private Text t(String word,float x,float y){return new Text(word,x-.01f,y-.01f,x+.01f,y+.01f);}
    private DailyRules rules(DailyTacet tacet)throws Exception{
        tacet.attachLedger(new DailyStaminaLedger(new MultiAccountDailyTest.Memory(),"111111111",MultiAccountDailyTest.NOW));
        DailyRules r=new DailyRules(tacet,MultiAccountDailyTest.A.profile());r.teamConfirmed();
        r.observeUid(book(120,"111111111"));r.observeUid(book(120,"111111111"));return r;
    }
    @Test public void ordinaryBudgetUsesOnlyAvailableWholeClaimsAndDailyRemainder(){
        for(int[] row:new int[][]{{0,0,0},{59,0,0},{60,0,60},{119,0,60},{120,0,120},{239,0,180},{240,0,240},{240,180,60},{240,240,0}}){
            DailyStaminaPlan p=DailyStaminaPlan.ordinaryOnly(row[0],row[1],0);
            assertEquals(row[2],p.plannedBudget);assertEquals(0,p.exchangeAmount);assertEquals(0,p.reserveBefore);
        }
        assertThrows(IllegalStateException.class,()->DailyStaminaPlan.ordinaryOnly(240,0,60));
        assertThrows(IllegalArgumentException.class,()->DailyStaminaPlan.ordinaryOnly(-1,0,0));
    }
    @Test public void batchProfileOverridesStaticLookupWithoutMutatingSingleAccountDefaults()throws Exception{
        DailyRules r=rules(new DailyTacet());assertEquals(2,r.targetTacetIndex());
        DailyRules single=new DailyRules();single.observeUid(book(120,"111111111"));single.observeUid(book(120,"111111111"));
        assertEquals(1,single.targetTacetIndex());assertNull(single.accountProfile());
    }
    @Test public void twoSameAccountReadingsFreeze120WithoutReserveAndKeepConfiguredTarget()throws Exception{
        DailyTacet tacet=new DailyTacet();DailyRules r=rules(tacet);
        assertEquals("daily-stamina-plan-check",r.next(book(120,"111111111")).scene);
        Action entry=r.next(book(120,"111111111"));assertEquals("daily-tacet-direct-entry",entry.scene);assertEquals(.44f,entry.y,.001f);
        assertEquals(120,tacet.plan().plannedBudget);assertEquals(0,tacet.plan().exchangeAmount);assertTrue(tacet.funded());
    }
    @Test public void insufficientOrdinarySkipsCombatWithoutOpeningOrExchangingReserve()throws Exception{
        DailyTacet tacet=new DailyTacet();DailyRules r=rules(tacet);
        r.next(book(59,"111111111"));Action a=r.next(book(59,"111111111"));
        assertEquals("daily-guide-first-tab",a.scene);assertTrue(r.rewards.started());assertEquals(0,tacet.plan().plannedBudget);
        assertFalse(r.rewards.complete());
        assertThrows(IllegalStateException.class,()->r.beforeResourceInput(new Action(Kind.TAP,"daily-reserve-confirm","",.5f,.5f),MultiAccountDailyTest.NOW));
    }
    @Test public void batchStopsAtExchangePageAndMismatchedUidBeforeAnyInput()throws Exception{
        DailyRules r=rules(new DailyTacet());Screen exchange=book(120,"111111111");exchange.words.add(t("兑换波片",.5f,.5f));
        assertEquals(Kind.STOP,r.next(exchange).kind);
        assertThrows(IllegalStateException.class,()->r.next(book(120,"222222222")));
    }
    @Test public void taskSelectionHasOwnStateAndNeverReusesDailySnapshot(){
        assertEquals(AutomationTask.MULTI_DAILY,AutomationTask.restore("MULTI_DAILY"));
        assertNotEquals(AutomationTask.DAILY.stateFile,AutomationTask.MULTI_DAILY.stateFile);
        assertTrue(AutomationTask.MULTI_DAILY.confirmation.contains("不兑换"));
    }
}
