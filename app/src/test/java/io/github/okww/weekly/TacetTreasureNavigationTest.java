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
        assertNull(n.next(s,marker(.6f,.7f),true,100,200));
        CombatGesture g=n.next(s,marker(.6f,.7f),true,300,400);
        assertEquals(650,g.durationMs);assertTrue(g.contacts.get(0).endX>g.contacts.get(0).x);
        assertNull(n.next(s,marker(.5f,.7f),true,500,600));
        g=n.next(s,marker(.5f,.7f),true,700,800);assertTrue(g.contacts.get(0).endY<g.contacts.get(0).y);
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
    @Test public void recordedLeftEdgeFailureTurnsCameraBeforeAligningAndWalkingForward() throws Exception {
        TacetTreasureNavigation n=new TacetTreasureNavigation("000000000",0);Screen s=world();
        assertNull(n.next(s,marker(.161f,.483f),true,100,200));
        CombatGesture turn=n.next(s,marker(.1606f,.480f),true,300,400);
        assertEquals(300,turn.durationMs);assertEquals(1500f/2400,turn.contacts.get(0).x,1e-6f);
        assertEquals(1350f/2400,turn.contacts.get(0).endX,1e-6f);
        assertNull(n.next(s,marker(.4f,.28f),true,500,600));
        CombatGesture align=n.next(s,marker(.4f,.28f),true,700,800);
        assertEquals(375f/2400,align.contacts.get(0).x,1e-6f);assertTrue(align.contacts.get(0).endX<align.contacts.get(0).x);
        assertNull(n.next(s,marker(.469f,.317f),true,900,1000));
        CombatGesture walk=n.next(s,marker(.469f,.317f),true,1100,1200);
        assertEquals(walk.contacts.get(0).x,walk.contacts.get(0).endX,1e-6f);assertTrue(walk.contacts.get(0).endY<walk.contacts.get(0).y);
    }
    @Test public void ownedMissingMarkerAllowsOnlyTwoCameraPitchCorrectionsAndNeverBlindWalking() throws Exception {
        TacetTreasureNavigation n=new TacetTreasureNavigation("000000000",0);Screen s=world();
        for(int i=0;i<2;i++) {
            assertNull(n.next(s,null,true,i*1000+100,i*1000+200));
            CombatGesture pitch=n.next(s,null,true,i*1000+300,i*1000+400);
            assertNotNull(pitch);assertEquals(300,pitch.durationMs);
            assertEquals(1500f/2400,pitch.contacts.get(0).x,1e-6f);
            assertEquals(pitch.contacts.get(0).x,pitch.contacts.get(0).endX,1e-6f);
            assertEquals(450f/1080,pitch.contacts.get(0).endY,1e-6f);
        }
        for(int i=0;i<5;i++)assertNull(n.next(s,null,true,2200+i*200,2300+i*200));
        assertNull(n.next(s,marker(.5f,.3f),true,3400,3500));
        assertNotNull(n.next(s,marker(.5f,.3f),true,3600,3700));
        TacetTreasureNavigation unowned=new TacetTreasureNavigation("000000000",0);
        assertNull(unowned.next(s,null,false,100,200));assertNull(unowned.next(s,null,false,300,400));
    }
    @Test public void recordedApproachDoesNotReverseAsSixMetreMarkerMovesBelowScreenCentre() throws Exception {
        TacetTreasureNavigation n=new TacetTreasureNavigation("000000000",0);Screen s=world();
        float[] ys={.49675927f,.53194445f,.54675925f,.5699074f,.61f};
        for(int i=0;i<ys.length;i++) {
            assertNull(n.next(s,marker(.523f,ys[i]),true,1000*i+100,1000*i+200));
            CombatGesture step=n.next(s,marker(.523f,ys[i]),true,1000*i+300,1000*i+400);
            assertNotNull(step);assertEquals(step.contacts.get(0).x,step.contacts.get(0).endX,1e-6f);
            assertTrue(step.contacts.get(0).endY<step.contacts.get(0).y);
        }
        s.words.add(t("领取奖励",1613f/2400,459f/1080));
        assertNull(n.next(s,marker(.523f,.65f),true,5300,5400));
    }
    @Test public void recordedTwelveMetreApproachKeepsMovingForwardThroughSmallHorizontalDrift() throws Exception {
        TacetTreasureNavigation n=new TacetTreasureNavigation("000000000",0);Screen s=world();
        // 10:10:35–10:11:02: the old navigator switched back to strafing at x=.458 and timed out 12m away.
        float[] xs={.43354166f,.46354166f,.469375f,.46604165f,.461875f,.45770833f,.49979165f,.5027083f,.41f};
        for(int i=0;i<xs.length;i++) {
            var m=marker(xs[i],.48f);
            assertNull(n.next(s,m,true,i*1000+100,i*1000+200));
            var step=n.next(s,m,true,i*1000+300,i*1000+400);
            var p=step.contacts.get(0);
            boolean forward=i>0&&i<xs.length-1;
            assertEquals(forward?900:650,step.durationMs);
            assertEquals(forward?-100f/1080:0,p.endY-p.y,1e-6f);
            if(!forward)assertEquals(-100f/2400,p.endX-p.x,1e-6f);
            else if(i==5)assertTrue(p.endX<p.x);
        }
        s.words.add(t("领取奖励",1613f/2400,459f/1080));
        assertNotNull(TacetTreasureNavigation.interaction(s));
        assertNull(n.next(s,marker(.5f,.48f),true,10000,10100));
    }
    @Test public void observedPostCombatLevelGlyphNeedsExactRoiAndOwnedNewMarkers() throws Exception {
        Screen s=world();s.words.set(0,t("以90",.58f,.94f));
        assertFalse(GardenRules.worldHud(s));assertTrue(TacetTreasureNavigation.postWorld(s));
        TacetTreasureNavigation n=new TacetTreasureNavigation("000000000",0);
        assertNull(n.next(s,marker(.6f,.7f),true,100,200));
        CombatGesture step=n.next(s,marker(.6f,.7f),true,300,400);
        assertNotNull(step);assertEquals(808f/1080,step.contacts.get(0).y,1e-6f);
        s.words.set(0,t("以90",.5f,.5f));assertFalse(TacetTreasureNavigation.postWorld(s));
    }
    @Test public void recordedBottomClampedMarkerTurnsCameraInsteadOfWalkingAway() throws Exception {
        TacetTreasureNavigation n=new TacetTreasureNavigation("000000000",0);Screen s=world();
        assertNull(n.next(s,marker(.47145835f,.77916664f),true,100,200));
        CombatGesture turn=n.next(s,marker(.47229168f,.77916664f),true,300,400);
        assertEquals(450,turn.durationMs);assertEquals(1500f/2400,turn.contacts.get(0).x,1e-6f);
        assertEquals(1050f/2400,turn.contacts.get(0).endX,1e-6f);
        assertEquals(turn.contacts.get(0).y,turn.contacts.get(0).endY,1e-6f);
        assertNull(n.next(s,marker(.5f,.4f),true,500,600));
        CombatGesture forward=n.next(s,marker(.5f,.4f),true,700,800);
        assertEquals(375f/2400,forward.contacts.get(0).x,1e-6f);
        assertTrue(forward.contacts.get(0).endY<forward.contacts.get(0).y);
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
