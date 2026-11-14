package io.github.okww.weekly;

import org.junit.Test;
import static org.junit.Assert.*;

public class BarrageOverlayRuleTest {
    static boolean ok(String pkg,boolean game,boolean sys,boolean focused,boolean active,int w,int h,int l,int t,int r,int b){
        return BarrageOverlayRule.expected(pkg,game,sys,focused,active,w,h,l,t,r,b);
    }
    @Test public void observedTopBandWhileGameFocusedIsAllowed(){
        assertTrue(ok(BarrageOverlayRule.PACKAGE,true,true,false,false,2400,1080,0,76,2400,496));
        assertTrue(ok(BarrageOverlayRule.PACKAGE,true,true,false,false,2400,1080,600,76,1800,300));
    }
    @Test public void otherPackagesOrLostGameFocusStop(){
        for(String pkg:new String[]{null,"com.other.app","com.android.systemui","com.xiaomi.barrage.fake"})
            assertFalse(ok(pkg,true,true,false,false,2400,1080,0,76,2400,496));
        assertFalse(ok(BarrageOverlayRule.PACKAGE,false,true,false,false,2400,1080,0,76,2400,496));
        assertFalse(ok(BarrageOverlayRule.PACKAGE,true,false,false,false,2400,1080,0,76,2400,496));
        assertFalse(ok(BarrageOverlayRule.PACKAGE,true,true,true,false,2400,1080,0,76,2400,496));
        assertFalse(ok(BarrageOverlayRule.PACKAGE,true,true,false,true,2400,1080,0,76,2400,496));
    }
    @Test public void otherDisplaysOrCoveringBoundsStop(){
        assertFalse(ok(BarrageOverlayRule.PACKAGE,true,true,false,false,1080,2400,0,76,2400,496));
        assertFalse(ok(BarrageOverlayRule.PACKAGE,true,true,false,false,2560,1440,0,76,2400,496));
        for(int[] b:new int[][]{{0,76,2400,541},{0,0,2400,1080},{-1,76,2400,496},{0,76,2401,496},{0,-1,2400,496},
                {2400,76,0,496},{0,496,2400,76},{0,600,2400,900}})
            assertFalse(ok(BarrageOverlayRule.PACKAGE,true,true,false,false,2400,1080,b[0],b[1],b[2],b[3]));
    }
}
