package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import static org.junit.Assert.*;
import static io.github.okww.weekly.CombatTouchLayout.Control.*;
import static io.github.okww.weekly.TeamBuiltinRotation.Feature.*;

public class TeamBuiltinRotationTest {
    static class Port implements TeamBuiltinRotation.Port {
        long real, active;
        boolean intro, buffs, subDps, hud=true, held, freeze, dead, longAction;
        int liberationCasts, resonanceCasts, heavyAttempts;
        boolean failHeld, failRelease, releaseHeavy=true, allowLib, allowRes, lib2;
        long heavyUntil=-1;
        List<String> events = new ArrayList<>();
        EnumSet<TeamBuiltinRotation.Feature> features = EnumSet.noneOf(TeamBuiltinRotation.Feature.class);
        public void checkCombat() { if(dead)throw new IllegalStateException("combat ended"); }
        public long activeTimeMs(){ return active; }
        public long realTimeMs(){ return real; }
        public boolean hasIntro(){ return intro; }
        public boolean allTeammateBuffs(){ return buffs; }
        public boolean hasSubDpsTeammate(){ return subDps; }
        public boolean partyHudVisible(){return hud;}
        public void waitPartyHud(long timeout){events.add("party:"+timeout);hud=true;}
        public boolean available(CombatTouchLayout.Control c){return c==LIBERATION ? allowLib : c==RESONANCE && allowRes;}
        public boolean feature(TeamBuiltinRotation.Feature f){
            if(f==AEMEATH_LIB2)return lib2;
            if(f==AEMEATH_HUMAN_HEAVY && held && releaseHeavy && real>=heavyUntil){features.remove(f);}
            return features.contains(f);
        }
        public boolean longAction(){return longAction;}
        public boolean enhancedResonance(long timeout,long minimumAnimation){
            assertEquals(1500,timeout);assertEquals(500,minimumAnimation);return cast(RESONANCE);
        }
        public void holdAttack(boolean down){
            events.add(down?"down":"up"); held=down;
            if(down){heavyAttempts++;if(failHeld)throw new IllegalStateException("hold failed");}
            if(!down && failRelease)throw new IllegalStateException("release failed");
        }
        public void refresh(){events.add("frame");if(failHeld && held)throw new IllegalStateException("lost HUD");}
        public void markSupportBuff(){events.add("buff");}
        public boolean breakAction(){events.add("break");return false;}
        public void cycleSleep(long started){advance(Math.max(0,100-(real-started)));}
        public void waitIntro(long timeout){events.add("intro:"+timeout);}
        public boolean cast(CombatTouchLayout.Control c){
            events.add(c.name());
            if(c==LIBERATION && allowLib){liberationCasts++;allowLib=false;advance(50);return true;}
            if(c==RESONANCE && allowRes){resonanceCasts++;allowRes=false;advance(50);return true;}
            return c==ECHO;
        }
        void advance(long ms){real+=ms;if(!freeze)active+=ms;}
        public void heavyIfForte(){events.add("heavy-if-forte");}
        public void normalAttack(long ms){events.add("normal:"+ms);advance(ms);}
        public boolean concertoFull(){return false;}
        public boolean forteFull(){return false;}
        public void waitStanding(long timeout){events.add("standing:"+timeout);}
        public void pause(long ms){advance(ms);}
        public void heavyAttack(long ms){events.add("heavy:"+ms);advance(ms);}
        public void jump(){events.add("jump");}
        public void switchNext(){events.add("switch");}
    }
    @Test public void shoreKeeperRecordsActualSupportSkillAndSkipsHeavyWhenResonanceCast() throws Exception {
        Port p=new Port();p.allowLib=true;p.allowRes=true;
        new TeamBuiltinRotation("ShoreKeeper").run(p);
        assertEquals(java.util.Arrays.asList("ECHO","LIBERATION","RESONANCE","buff","switch"),p.events);
    }
    @Test public void shoreKeeperIntroWaitsForHiddenHudWithoutPretendingNormalAttack() throws Exception {
        Port p=new Port();p.intro=true;p.hud=false;
        new TeamBuiltinRotation("ShoreKeeper").run(p);
        assertEquals(java.util.Arrays.asList("party:4000","ECHO","LIBERATION","RESONANCE","heavy-if-forte","switch"),p.events);
        assertFalse(p.events.contains("buff"));
        Port visible=new Port();visible.intro=true;
        new TeamBuiltinRotation("ShoreKeeper").run(visible);
        assertEquals("normal:1200",visible.events.get(0));
    }
    @Test public void yangYangSpHoldsContinuouslyThenReleasesBeforeSwitch() throws Exception {
        Port p=new Port();p.allowRes=true;
        new TeamBuiltinRotation("YangYangSp").run(p);
        assertEquals("down",p.events.get(0));assertEquals(1,p.resonanceCasts);
        assertEquals(1,java.util.Collections.frequency(p.events,"down"));
        assertEquals("up",p.events.get(p.events.size()-2));assertEquals("switch",p.events.get(p.events.size()-1));
        assertFalse(p.held);assertEquals(3300,p.real);
    }
    @Test public void introAndSuccessfulLiberationExtendYangYangFieldTimeOnlyOnConfirmation() throws Exception {
        Port p=new Port();p.intro=true;p.allowLib=true;
        new TeamBuiltinRotation("YangYangSp").run(p);
        assertEquals(1,p.liberationCasts);assertEquals(10100,p.real);assertFalse(p.held);
    }
    @Test public void frozenClockYieldsAtWallDeadlineAfterReleasingHold() throws Exception {
        Port p=new Port();p.freeze=true;
        new TeamBuiltinRotation("YangYangSp").run(p);
        assertEquals(30000,p.real);assertFalse(p.held);
        assertEquals("up",p.events.get(p.events.size()-2));assertEquals("switch",p.events.get(p.events.size()-1));
    }
    @Test public void aemeathFrozenClockAndRepeatedHeavyResetYieldWithoutStoppingDaily() throws Exception {
        for(boolean repeatedHeavy:new boolean[]{false,true}){
            Port p=new Port(){
                @Override public boolean feature(TeamBuiltinRotation.Feature feature){
                    if(feature==AEMEATH_HUMAN_HEAVY&&repeatedHeavy)return !held;
                    return super.feature(feature);
                }
            };
            p.freeze=!repeatedHeavy;p.intro=p.buffs=true;
            new TeamBuiltinRotation("Aemeath").run(p);
            assertTrue(p.real>=30000);assertTrue(p.real<31500);assertFalse(p.held);
            assertEquals(1,java.util.Collections.frequency(p.events,"switch"));
            assertEquals(0,p.liberationCasts);
        }
    }
    @Test public void deadlineNeverSuppressesLostCombatOrFailedRelease() {
        for(boolean releaseFailure:new boolean[]{false,true}){
            Port p=new Port(){
                @Override public void checkCombat(){super.checkCombat();if(real>=30000)throw new IllegalStateException("lost at deadline");}
            };
            p.freeze=true;p.failRelease=releaseFailure;
            assertThrows(IllegalStateException.class,()->new TeamBuiltinRotation("YangYangSp").run(p));
            assertFalse(p.held);assertFalse(p.events.contains("switch"));
        }
        Port p=new Port(){@Override public void checkCombat(){super.checkCombat();if(real>30000)throw new IllegalStateException("lost after deadline");}
            @Override public void holdAttack(boolean down){super.holdAttack(down);if(!down)advance(1);}};
        p.freeze=true;
        assertThrows(IllegalStateException.class,()->new TeamBuiltinRotation("YangYangSp").run(p));
        assertFalse(p.held);assertFalse(p.events.contains("switch"));
    }
    @Test public void lostCombatDuringHoldIsReleasedBeforeErrorEscapes() {
        Port p=new Port(){@Override public void refresh(){dead=true;}};
        assertThrows(IllegalStateException.class,()->new TeamBuiltinRotation("YangYangSp").run(p));
        assertFalse(p.held);assertEquals("up",p.events.get(p.events.size()-1));assertFalse(p.events.contains("switch"));
    }
    @Test public void partiallyFailedHoldAcquisitionStillReleasesForBothLongPressAxes() {
        for(String character:new String[]{"YangYangSp","Aemeath"}){
            Port p=new Port();p.failHeld=true;p.subDps=true;p.features.add(AEMEATH_HUMAN_HEAVY);
            assertThrows(IllegalStateException.class,()->new TeamBuiltinRotation(character).run(p));
            assertFalse(p.held);assertEquals("up",p.events.get(p.events.size()-1));assertFalse(p.events.contains("switch"));
        }
    }
    @Test public void aemeathWithoutBuffedIntroFastSwitchesInAnyTeamLikePcDoPerform() throws Exception {
        for(int flags=0;flags<8;flags++){
            if((flags&3)==3)continue;
            Port p=new Port();p.intro=(flags&1)!=0;p.buffs=(flags&2)!=0;p.subDps=(flags&4)!=0;p.allowLib=true;
            new TeamBuiltinRotation("Aemeath").run(p);
            assertEquals(java.util.Arrays.asList("switch"),p.events);
        }
    }
    @Test public void aemeathBuffedIntroRequiresEnhancedSkillAndSecondLiberation() throws Exception {
        Port p=new Port(){@Override public boolean cast(CombatTouchLayout.Control c){
            boolean used=super.cast(c);
            if(c==RESONANCE && used){lib2=true;allowLib=true;}
            return used;
        }};
        p.intro=true;p.buffs=true;p.subDps=true;p.allowRes=true;p.features.add(AEMEATH_E1);
        new TeamBuiltinRotation("Aemeath").run(p);
        assertEquals(1,p.resonanceCasts);assertEquals(1,p.liberationCasts);
        assertEquals(java.util.Arrays.asList("normal:2100","LIBERATION","RESONANCE","ECHO","frame","LIBERATION","switch"),p.events);
    }
    @Test public void failedEnhancedSkillDoesNotClaimSuccessOrCastEcho() throws Exception {
        Port p=new Port();p.intro=p.buffs=true;p.features.add(AEMEATH_E1);
        new TeamBuiltinRotation("Aemeath").run(p);
        assertEquals(0,p.resonanceCasts);assertFalse(p.events.contains("ECHO"));assertTrue(p.real>=12000);
    }
    @Test public void aemeathFallbackUsesOneClickThenCycleAndFreshFrameNotDurationLoop() {
        Port p=new Port(){
            @Override public void normalAttack(long ms){
                if(ms==2100){advance(ms);return;} // Original intro precedes the fallback loop.
                throw new AssertionError("single click entered duration loop");
            }
            @Override public void normalClick(){checkCombat();events.add("click");advance(65);}
            @Override public void refresh(){super.refresh();dead=true;}
        };
        p.intro=p.buffs=true;
        assertThrows(IllegalStateException.class,()->new TeamBuiltinRotation("Aemeath").run(p));
        assertEquals(java.util.Arrays.asList("LIBERATION","click","frame"),p.events);
        assertEquals(2200,p.real);assertEquals(2200,p.active);
        assertFalse(p.held);assertFalse(p.events.contains("switch"));
    }
    @Test public void singleClickDefaultRetainsExistingFakePortCompatibility() throws Exception {
        Port p=new Port();p.normalClick();
        assertEquals(java.util.Arrays.asList("normal:90"),p.events);assertEquals(90,p.real);
    }
    @Test public void aemeathHeavyWaitsForIconToDisappearAndReleases() throws Exception {
        Port p=new Port();p.subDps=true;p.heavyUntil=300;p.features.add(AEMEATH_HUMAN_HEAVY);
        new TeamBuiltinRotation("Aemeath").run(p);
        assertEquals(1,p.heavyAttempts);assertFalse(p.held);assertTrue(p.real>=600);
        assertEquals("switch",p.events.get(p.events.size()-1));
    }
    @Test public void stuckHeavyIconHasBoundedWaitAndPersistentRetryInterval() throws Exception {
        Port p=new Port();p.subDps=true;p.releaseHeavy=false;p.features.add(AEMEATH_HUMAN_HEAVY);
        TeamBuiltinRotation r=new TeamBuiltinRotation("Aemeath");r.run(p);
        assertEquals(1210,p.real);assertFalse(p.held);assertEquals(1,p.heavyAttempts);
        r.run(p);assertEquals(1,p.heavyAttempts);
        p.advance(2000);r.run(p);assertEquals(2,p.heavyAttempts);
    }
    @Test public void unregisteredWorkshopAndInterruptedRotationsNeverSendInput() {
        assertFalse(TeamBuiltinRotation.supported("workshop:Aemeath"));
        assertThrows(IllegalArgumentException.class,()->new TeamBuiltinRotation("Rover"));
        Port p=new Port();Thread.currentThread().interrupt();
        try{assertThrows(InterruptedException.class,()->new TeamBuiltinRotation("Aemeath").run(p));}
        finally{Thread.interrupted();}assertTrue(p.events.isEmpty());
    }
}
