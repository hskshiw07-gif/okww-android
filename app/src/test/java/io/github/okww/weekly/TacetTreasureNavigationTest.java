package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import static io.github.okww.weekly.GardenRules.*;
import static org.junit.Assert.*;

public class TacetTreasureNavigationTest {
    private static Text t(String text,float x,float y){return new Text(text,x-.005f,y-.005f,x+.005f,y+.005f);}
    private static Screen world(){return new Screen(new ArrayList<>(Arrays.asList(t("Lv.90",.58f,.94f),
            t("特征码:000000000",.91f,.98f))),new int[3],2400f/1080);}
    private static PortraitMatcher.Match marker(float x,float y){return new PortraitMatcher.Match("treasure",.9,-1,x,y);}
    @Test public void twoFreshObservationsAuthorizeOneFiniteStepAndThenRequireNewPair() throws Exception {
        TacetTreasureNavigation n=new TacetTreasureNavigation("000000000",0);Screen s=world();
        assertNull(n.next(s,marker(.6f,.77f),true,100,200));
        CombatGesture g=n.next(s,marker(.6f,.77f),true,300,400);
        assertEquals(650,g.durationMs);assertTrue(g.contacts.get(0).endX>g.contacts.get(0).x);
        assertNull(n.next(s,marker(.5f,.77f),true,500,600));
        g=n.next(s,marker(.5f,.77f),true,700,800);assertTrue(g.contacts.get(0).endY>g.contacts.get(0).y);
    }
    @Test public void staleMissingMarkerAndUnownedBattleNeverReuseOldDirection() throws Exception {
        TacetTreasureNavigation n=new TacetTreasureNavigation("000000000",0);Screen s=world();
        assertNull(n.next(s,marker(.6f,.7f),true,100,200));
        assertNull(n.next(s,marker(.6f,.7f),true,100,300));
        assertNull(n.next(s,marker(.6f,.7f),true,400,500));
        assertNull(n.next(s,null,true,600,700));
        assertNull(n.next(s,marker(.6f,.7f),true,800,900));
        assertNull(n.next(s,marker(.6f,.7f),false,1000,1100));
        assertNull(n.next(s,marker(.6f,.7f),true,1200,2800));
    }
    @Test public void observedPostCombatLevelGlyphNeedsExactRoiAndOwnedNewMarkers() throws Exception {
        Screen s=world();s.words.set(0,t("以90",.58f,.94f));
        assertFalse(GardenRules.worldHud(s));assertTrue(TacetTreasureNavigation.postWorld(s));
        TacetTreasureNavigation n=new TacetTreasureNavigation("000000000",0);
        assertNull(n.next(s,marker(.6f,.77f),true,100,200));
        CombatGesture step=n.next(s,marker(.6f,.77f),true,300,400);
        assertNotNull(step);assertEquals(808f/1080,step.contacts.get(0).y,1e-6f);
        s.words.set(0,t("以90",.5f,.5f));assertFalse(TacetTreasureNavigation.postWorld(s));
    }
    @Test public void skippedFramesAndLongGapsResetMarkerPairWithoutExtendingDeadline() throws Exception {
        TacetTreasureNavigation n=new TacetTreasureNavigation("000000000",0);Screen s=world();
        assertNull(n.next(s,marker(.5f,.3f),true,100,200));
        n.unconfirmedFrame();
        assertNull(n.next(s,marker(.5f,.3f),true,300,400));
        assertNull(n.next(s,marker(.5f,.3f),true,3100,3200));
        CombatGesture g=n.next(s,marker(.5f,.3f),true,3400,3500);
        assertNotNull(g);assertTrue(g.contacts.get(0).endY<g.contacts.get(0).y);
        n.unconfirmedFrame();
        assertThrows(IllegalStateException.class,()->n.next(s,marker(.5f,.3f),true,46000,46100));
    }
    @Test public void repeatedVisualMotionStillStopsAtSixteenFiniteSteps() throws Exception {
        TacetTreasureNavigation n=new TacetTreasureNavigation("000000000",0);Screen s=world();
        for(int i=0;i<16;i++) {
            assertNull(n.next(s,marker(.6f,.7f),true,i*1000+100,i*1000+200));
            assertNotNull(n.next(s,marker(.6f,.7f),true,i*1000+300,i*1000+400));
        }
        assertThrows(IllegalStateException.class,()->n.next(s,marker(.6f,.7f),true,17000,17100));
    }
    @Test public void mobileInteractionRequiresMeasuredTextRoiAndPostCombatHud() {
        Screen s=world();s.words.add(t("领取奖励",1613f/2400,459f/1080));
        assertNotNull(TacetTreasureNavigation.interaction(s));assertFalse(TacetTreasureNavigation.postWorld(s));
        s.words.add(t("03:20:01",.5f,.14f));assertNull(TacetTreasureNavigation.interaction(s));
        s=world();s.words.add(t("领取奖励",.5f,.7f));assertNull(TacetTreasureNavigation.interaction(s));
        s=world();s.words.add(t("领取奖励",1613f/2400,459f/1080));s.words.add(t("提示",.3f,.3f));
        assertNull(TacetTreasureNavigation.interaction(s));
    }
    @Test public void battleOrModalNeverMovesAndTimeoutAccountCancellationStop() throws Exception {
        Screen s=world();s.words.add(t("清理无音区中的残象:4/5",.2f,.3f));assertFalse(TacetTreasureNavigation.postWorld(s));
        s=world();s.words.add(t("03:20:01",.5f,.14f));assertFalse(TacetTreasureNavigation.postWorld(s));
        s=world();s.words.add(t("领取奖励",.7f,.5f));assertFalse(TacetTreasureNavigation.postWorld(s));
        TacetTreasureNavigation n=new TacetTreasureNavigation("000000000",0);
        assertThrows(IllegalStateException.class,()->n.next(world(),marker(.6f,.7f),true,46000,46001));
        Screen other=world();other.words.set(1,t("特征码:111111111",.91f,.98f));
        assertThrows(IllegalStateException.class,()->n.next(other,marker(.6f,.7f),true,100,200));
        try {Thread.currentThread().interrupt();assertThrows(InterruptedException.class,()->n.next(world(),marker(.6f,.7f),true,100,200));}
        finally {Thread.interrupted();}
    }
}
