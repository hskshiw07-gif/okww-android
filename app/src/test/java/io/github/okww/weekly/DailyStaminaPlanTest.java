package io.github.okww.weekly;

import org.junit.Test;
import static org.junit.Assert.*;

public class DailyStaminaPlanTest {
    @Test public void prioritizes240Then180WithoutRoundingUpInsufficientResources(){
        for(int[] row:new int[][]{{240,0,240,0},{150,90,240,90},{130,110,240,110},
                {130,109,180,50},{180,0,180,0},{0,180,180,180},{200,20,180,0}}){
            DailyStaminaPlan p=DailyStaminaPlan.create(row[0],row[1],0,0);
            assertEquals(row[2],p.plannedBudget);assertEquals(row[3],p.exchangeAmount);
        }
        for(int[] row:new int[][]{{0,0},{179,0},{0,179},{130,49}})
            assertThrows(IllegalStateException.class,()->DailyStaminaPlan.create(row[0],row[1],0,0));
    }
    @Test public void ordinaryIsUsedBeforeReserveAndOnlyTheFrozenDeficitIsExchanged(){
        DailyStaminaPlan p=DailyStaminaPlan.create(130,423,0,0);
        assertEquals(240,p.plannedBudget);assertEquals(110,p.exchangeAmount);
        assertEquals(130,p.ordinaryBefore);assertEquals(423,p.reserveBefore);assertEquals(0,p.confirmedAtStart);
        assertEquals(240,DailyStaminaPlan.create(9999,0,0,0).plannedBudget);
        assertEquals(0,DailyStaminaPlan.create(9999,9999,0,0).exchangeAmount);
    }
    @Test public void dailyCapClipsPlanAndExchangeRatherThanResettingExistingSpending(){
        for(int[] row:new int[][]{{60,180,80},{120,120,20},{180,60,0}}){
            DailyStaminaPlan p=DailyStaminaPlan.create(100,423,row[0],0);
            assertEquals(row[1],p.plannedBudget);assertEquals(row[2],p.exchangeAmount);
            assertEquals(row[1],p.remaining(row[0]));assertEquals(0,p.remaining(240));
        }
        DailyStaminaPlan complete=DailyStaminaPlan.create(0,0,240,0);
        assertEquals(0,complete.plannedBudget);assertEquals(0,complete.exchangeAmount);assertEquals(0,complete.remaining(240));
    }
    @Test public void pendingClaimsBlockEvenWhenResourcesAreSufficient(){
        for(int confirmed:new int[]{0,60,120,180})
            assertThrows(IllegalStateException.class,()->DailyStaminaPlan.create(240,423,confirmed,60));
        for(int confirmed:new int[]{0,60,120})
            assertThrows(IllegalStateException.class,()->DailyStaminaPlan.create(240,423,confirmed,120));
        assertThrows(IllegalArgumentException.class,()->DailyStaminaPlan.create(240,423,180,120));
    }
    @Test public void doubleProgressKeepsTheFrozen180And240TargetsAndTheFinalSixty() {
        DailyStaminaPlan daily240=DailyStaminaPlan.create(240,0,0,0);
        assertEquals(120,daily240.remaining(120));assertEquals(0,daily240.remaining(240));
        DailyStaminaPlan daily180=DailyStaminaPlan.create(180,0,0,0);
        assertEquals(60,daily180.remaining(120));assertEquals(0,daily180.remaining(180));
        assertThrows(IllegalStateException.class,()->daily180.remaining(240));
        DailyStaminaPlan resumed=DailyStaminaPlan.create(60,0,120,0);
        assertEquals(60,resumed.plannedBudget);assertEquals(0,resumed.exchangeAmount);assertEquals(0,resumed.remaining(180));
    }
    @Test public void frozenAllowanceDoesNotReapplyThe180FloorAfterEachClaim(){
        DailyStaminaPlan p=DailyStaminaPlan.create(180,0,0,0);
        assertEquals(180,p.remaining(0));assertEquals(120,p.remaining(60));
        assertEquals(60,p.remaining(120));assertEquals(0,p.remaining(180));
        assertEquals(180,p.plannedBudget);assertEquals(0,p.exchangeAmount);
        assertEquals(120,DailyStaminaPlan.create(120,0,60,0).plannedBudget);
        // A later resource increase must not silently expand the already-frozen 180 target.
        assertEquals(180,DailyStaminaPlan.create(120,60,60,0).plannedBudget);
        assertEquals(120,p.remaining(60));
    }
    @Test public void resumedRunCountsAlreadyConfirmedClaimsTowardTheSameGameDayTarget(){
        for(int[] row:new int[][]{{120,60,0,60,0},{180,0,0,0,0},{180,30,30,60,30},
                {120,60,59,60,0},{120,60,60,120,60},{60,120,0,120,0}}){
            DailyStaminaPlan p=DailyStaminaPlan.create(row[1],row[2],row[0],0);
            assertEquals(row[3],p.plannedBudget);assertEquals(row[4],p.exchangeAmount);
            assertEquals(row[0],p.confirmedAtStart);assertEquals(row[1],p.ordinaryBefore);assertEquals(row[2],p.reserveBefore);
        }
    }
    @Test public void rejectsMalformedBalancesAndLedgerRatherThanSilentlyClamping(){
        for(int[] row:new int[][]{{-1,240,0,0},{10000,0,0,0},{240,-1,0,0},{0,10000,0,0},
                {240,0,-60,0},{240,0,30,0},{240,0,300,0},{240,0,0,-1},{240,0,0,30},{240,0,240,60}})
            assertThrows(IllegalArgumentException.class,()->DailyStaminaPlan.create(row[0],row[1],row[2],row[3]));
    }
    @Test public void frozenPlanRejectsLedgerRollbackOverrunAndMalformedProgress(){
        DailyStaminaPlan p=DailyStaminaPlan.create(180,0,60,0);
        assertThrows(IllegalStateException.class,()->p.remaining(0));
        for(int n:new int[]{-1,30,300})assertThrows(IllegalArgumentException.class,()->p.remaining(n));
        DailyStaminaPlan smaller=DailyStaminaPlan.create(180,0,0,0);
        assertThrows(IllegalStateException.class,()->smaller.remaining(240));
    }
}
