package io.github.okww.weekly;

import org.junit.Test;
import static org.junit.Assert.*;

public class RedeemPasteLeaseTest {
    boolean observed(RedeemPasteLease lease,long now){
        return lease.expected(now,RedeemPasteLease.PACKAGE,true,true,false,false,1877,192,2399,492);
    }
    @Test public void observedCardRequiresOurRecentWriteAndStopsAtExpiry(){
        RedeemPasteLease l=new RedeemPasteLease();assertFalse(observed(l,100));
        l.start(100);assertFalse(observed(l,99));assertTrue(observed(l,100));assertTrue(observed(l,6100));assertFalse(observed(l,6101));
        l.stop();assertFalse(observed(l,200));l.start(7000);assertTrue(observed(l,7000));
        assertThrows(IllegalArgumentException.class,()->l.start(-1));
    }
    @Test public void otherPackagesFocusOrApplicationWindowsNeverQualify(){
        RedeemPasteLease l=new RedeemPasteLease();l.start(100);
        for(String pkg:new String[]{null,"com.other.app","com.android.systemui","com.miui.contentextension.fake"})
            assertFalse(l.expected(200,pkg,true,true,false,false,1877,192,2399,492));
        assertFalse(l.expected(200,RedeemPasteLease.PACKAGE,false,true,false,false,1877,192,2399,492));
        assertFalse(l.expected(200,RedeemPasteLease.PACKAGE,true,false,false,false,1877,192,2399,492));
        assertFalse(l.expected(200,RedeemPasteLease.PACKAGE,true,true,true,false,1877,192,2399,492));
        assertFalse(l.expected(200,RedeemPasteLease.PACKAGE,true,true,false,true,1877,192,2399,492));
    }
    @Test public void expandedMovedAndInputCoveringOverlaysAreRejected(){
        RedeemPasteLease l=new RedeemPasteLease();l.start(100);
        for(int[] b:new int[][]{{1600,192,2399,492},{1877,0,2399,492},{1877,192,2500,492},
                {1877,192,2200,492},{1877,192,2399,700},{2399,192,1877,492},{1877,192,2399,191}})
            assertFalse(l.expected(200,RedeemPasteLease.PACKAGE,true,true,false,false,b[0],b[1],b[2],b[3]));
    }
    @Test public void anOldSmallCardCannotWhitelistAnotherWindowOrUnknownEvent(){
        assertTrue(RedeemPasteLease.eventWindow(27099,27099));
        assertFalse(RedeemPasteLease.eventWindow(27100,27099));
        assertFalse(RedeemPasteLease.eventWindow(-1,27099));
        assertFalse(RedeemPasteLease.eventWindow(-1,-1));
    }
}
