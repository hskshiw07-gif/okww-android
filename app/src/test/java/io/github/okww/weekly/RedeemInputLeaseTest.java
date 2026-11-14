package io.github.okww.weekly;
import org.junit.Test;
import static org.junit.Assert.*;
public class RedeemInputLeaseTest {
    @Test public void completionRequiresGameOwnedEditorAdvertisingDone(){
        assertTrue(RedeemTextInput.doneEditor(GardenRules.GAME_PACKAGE,0x12000006));
        assertTrue(RedeemTextInput.doneEditor(GardenRules.GAME_PACKAGE,6));
        for(int action:new int[]{0,1,2,3,4,5,7})assertFalse(RedeemTextInput.doneEditor(GardenRules.GAME_PACKAGE,action));
        assertFalse(RedeemTextInput.doneEditor("com.other.app",6));assertFalse(RedeemTextInput.doneEditor(null,6));
    }
    @Test public void stoppedQueuedInputIsInvalidEvenWhileWorkerStillRuns(){
        RedeemTextInput.Lease lease=new RedeemTextInput.Lease(100);assertTrue(lease.valid(200));
        lease.cancel();assertFalse(lease.valid(200));assertFalse(lease.valid(300));lease.cancel();assertFalse(lease.valid(400));
    }
    @Test public void clockReversalExpiryAndNewLeaseDoNotReviveOldInput(){
        RedeemTextInput.Lease old=new RedeemTextInput.Lease(100);assertFalse(old.valid(99));assertTrue(old.valid(6100));assertFalse(old.valid(6101));old.cancel();
        RedeemTextInput.Lease next=new RedeemTextInput.Lease(7000);assertTrue(next.valid(7000));assertFalse(old.valid(7000));
    }
}
