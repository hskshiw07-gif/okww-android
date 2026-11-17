package io.github.okww.weekly;

import org.junit.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.function.Predicate;
import static io.github.okww.weekly.CombatTouchLayout.Control.*;
import static io.github.okww.weekly.PriorityBuiltinRotation.Signal.*;
import static org.junit.Assert.*;

public class PriorityBuiltinRotationTest {
    @Test public void twentyQuickPathsMatchOriginalPcAstNotHandWrittenExpectedActions() throws Exception {
        int count = 0;
        try (java.io.BufferedReader input = new java.io.BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/priority-rotation-parity.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("#") || line.isEmpty()) continue;
                String[] fields = line.split("\t"); int flags = Integer.parseInt(fields[1]);
                Port p = new Port(fields[0]); p.intro = (flags & 1) != 0;
                p.own = p.fast = (flags & 2) != 0; p.mouse = (flags & 4) != 0;
                p.con = true; p.allowLib = p.allowRes = true;
                if ((flags & 2) != 0) p.on(QINGXIAO_E);
                p.on(HSIN_LIB2);
                new PriorityBuiltinRotation(fields[0]).run(p);
                assertEquals(fields[0] + "/" + flags, fields[2], String.join(",", p.events));
                count++;
            }
        }
        assertEquals(20, count);
    }

    static class Port extends AdditionalBuiltinRotationTest.Port implements PriorityBuiltinRotation.Port {
        boolean calibrated = true, own, fast, dps, libEnabled = true, heldLibConfirmed;
        String outro;
        long libCd = 99999, resCd = 99999;
        final EnumSet<CombatTouchLayout.Control> controls = EnumSet.noneOf(CombatTouchLayout.Control.class);
        final EnumMap<PriorityBuiltinRotation.Signal, Predicate<Port>> signals = new EnumMap<>(PriorityBuiltinRotation.Signal.class);
        Port(String name) { super(0); activeName = name; }
        public void requirePriorityCalibration(String name) { if (!calibrated) throw new IllegalStateException("uncalibrated"); }
        public boolean signal(PriorityBuiltinRotation.Signal signal) { return signals.getOrDefault(signal, p -> false).test(this); }
        void on(PriorityBuiltinRotation.Signal s) { signals.put(s, p -> true); }
        public String outroSource() { return outro; }
        public boolean ownBuff() { return own; }
        public boolean fastPerform() { return fast; }
        public boolean chisaDps() { return dps; }
        public long cooldownMs(CombatTouchLayout.Control c) { return c == LIBERATION ? libCd : resCd; }
        public boolean useLiberation() { return libEnabled; }
        public void hold(CombatTouchLayout.Control c, boolean down) {
            events.add((down ? "down:" : "up:") + c);
            if (down) controls.add(c); else controls.remove(c);
        }
        public void dodge(long duration) { events.add("dodge:" + duration); advance(duration); }
        public void dodgeClick(long interval) { events.add("dodge-click:" + interval); advance(Math.max(10, interval)); }
        public void shorekeeperAutoDodge() { events.add("support-dodge"); advance(100); }
        public boolean enhancedResonance(long timeout, long minimum) {
            events.add("enhanced:" + timeout + ":" + minimum); advance(100); return allowRes;
        }
        public void tapResonance() { events.add("tap:E"); }
        public void recordResonanceConfirmed() { events.add("confirmed:E"); }
        public boolean confirmHeldLiberation(long started, long timeout) {
            events.add("confirm-held:" + timeout); return heldLibConfirmed;
        }
        @Override public void normalClick() { events.add("click"); advance(100); }
        @Override public void refresh() { super.refresh(); }
    }

    @Test public void allSixDispatchThroughExistingRegistryWithoutInventingLiveSupport() throws Exception {
        BuiltinCombatCatalog catalog = catalog();
        String[][] teams = {{"Chisa", "Hiyuki", "Qingxiao"}, {"Phrolova", "Hsin", "Galbrena"}};
        for (String[] team : teams) {
            BuiltinRotations rotations = new BuiltinRotations(catalog, Arrays.asList(team));
            for (String name : team) {
                Port p = new Port(name); rotations.run(name, p);
                assertEquals("S", p.events.get(p.events.size() - 1));
                assertFalse(p.held); assertTrue(p.controls.isEmpty());
                assertTrue(BuiltinRotations.supported(name));
                assertFalse("Do not silently enable phone combat", TeamBuiltinRotation.supported(name));
            }
        }
    }

    @Test public void missingCalibrationIsRejectedBeforeEvenTheIntroInput() throws Exception {
        for (String name : PriorityBuiltinRotation.CHARACTERS) {
            Port p = new Port(name); p.calibrated = false; p.intro = true;
            assertThrows(IllegalStateException.class, () -> new PriorityBuiltinRotation(name).run(p));
            assertTrue(name, p.events.isEmpty());
        }
        BuiltinRotations rotations = new BuiltinRotations(catalog(), Arrays.asList("Chisa", "ShoreKeeper", "Aemeath"));
        AdditionalBuiltinRotationTest.Port old = new AdditionalBuiltinRotationTest.Port(0); old.activeName = "Chisa";
        assertThrows(IllegalStateException.class, () -> rotations.run("Chisa", old));
        assertTrue(old.events.isEmpty());
    }

    @Test public void wrongIdentityAndCancellationHaveNoInputs() {
        Port wrong = new Port("Hsin");
        assertThrows(IllegalStateException.class, () -> new PriorityBuiltinRotation("Chisa").run(wrong));
        assertTrue(wrong.events.isEmpty());
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedException.class, () -> new PriorityBuiltinRotation("Hsin").run(wrong)); }
        finally { Thread.interrupted(); }
        assertTrue(wrong.events.isEmpty());
    }

    @Test public void chisaForteUsesMobileContinuousResonanceThen3500msAttack() throws Exception {
        Port p = new Port("Chisa") {
            @Override public boolean forteFull() { return !events.contains("up:RESONANCE"); }
        };
        new PriorityBuiltinRotation("Chisa").run(p);
        assertTrue(p.events.contains("down:RESONANCE")); assertTrue(p.events.contains("up:RESONANCE"));
        assertTrue(p.events.indexOf("up:RESONANCE") < p.events.indexOf("down"));
        assertEquals(60000 + 50 + 1200 + 3500, p.real);
        assertFalse(p.held); assertTrue(p.controls.isEmpty());
    }

    @Test public void chisaDoesNotHeavyAttackIfResonanceFailedToConsumeForte() throws Exception {
        Port p = new Port("Chisa"); p.mouse = true;
        new PriorityBuiltinRotation("Chisa").run(p);
        assertTrue(p.events.contains("down:RESONANCE")); assertFalse(p.events.contains("down"));
    }

    @Test public void chisaHoldOrderAndDurationsMatchActualPcForteMethod()throws Exception{
        int count=0;
        try(var input=new java.io.BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/chisa-forte-parity.tsv"),StandardCharsets.UTF_8))){
            String line;
            while((line=input.readLine())!=null){
                if(line.startsWith("#")||line.isEmpty())continue;
                String[] values=line.split("\t");boolean consumed=values[0].equals("1");
                var holds=new java.util.ArrayList<String>();
                Port p=new Port("Chisa"){
                    long resonanceAt,attackAt;
                    @Override public boolean chisaForteConsumed(){return consumed;}
                    @Override public void hold(CombatTouchLayout.Control c,boolean down){
                        if(down)resonanceAt=real;else holds.add("H:"+c+":"+(real-resonanceAt));
                        super.hold(c,down);
                    }
                    @Override public void holdAttack(boolean down){
                        if(down)attackAt=real;else holds.add("H:ATTACK:"+(real-attackAt));
                        super.holdAttack(down);
                    }
                };
                p.mouse=true;new PriorityBuiltinRotation("Chisa").run(p);
                assertEquals(values[2],String.join(",",holds));
                assertEquals(values[1].equals("1"),p.events.contains("down"));
                assertFalse(p.held);assertTrue(p.controls.isEmpty());count++;
            }
        }
        assertEquals(2,count);
    }

    @Test public void chisaSupportWindowDependsOnIntroAndExistingBuff() throws Exception {
        Port shortTurn = new Port("Chisa"); shortTurn.intro = true; shortTurn.own = true;
        Port longTurn = new Port("Chisa"); longTurn.intro = true;
        new PriorityBuiltinRotation("Chisa").run(shortTurn); new PriorityBuiltinRotation("Chisa").run(longTurn);
        assertTrue(longTurn.real - shortTurn.real >= 8000);
        Port con = new Port("Chisa"); con.con = true;
        new PriorityBuiltinRotation("Chisa").run(con);
        assertFalse(con.events.contains("click"));
    }

    @Test public void chisaDpsHasItsOwnIntroAndBoundedWindow() throws Exception {
        Port p = new Port("Chisa"); p.dps = true; p.intro = true;
        new PriorityBuiltinRotation("Chisa").run(p);
        assertTrue(p.events.contains("N:800:100:0"));
        assertTrue(p.real < 64000);
    }

    @Test public void qingxiaoQuickTurnOnlyUsesEnhancedResonance() throws Exception {
        Port p = new Port("Qingxiao"); p.on(QINGXIAO_E); p.allowRes = true;
        new PriorityBuiltinRotation("Qingxiao").run(p);
        assertEquals(Arrays.asList("enhanced:1500:500", "S"), p.events);
    }

    @Test public void qingxiaoHeavyRequiresStableDarkNotOneMissingFrame() throws Exception {
        Port p = new Port("Qingxiao"); p.intro = p.buffs = true; p.allowLib = true;
        p.signals.put(QINGXIAO_H2, q -> q.real < 60100 || (q.real >= 60200 && q.real < 60400));
        new PriorityBuiltinRotation("Qingxiao").run(p);
        assertTrue(p.real >= 60650); assertEquals(1, p.heavyAttempts);
        assertTrue(p.events.indexOf("up") < p.events.indexOf("L:-1:0:100"));
        assertTrue(p.events.contains("party:1000")); assertFalse(p.held);
    }

    @Test public void qingxiaoPermanentHeavyNeverFakesReleaseOrLiberation() throws Exception {
        Port p = new Port("Qingxiao"); p.intro = p.buffs = true; p.on(QINGXIAO_H2);
        new PriorityBuiltinRotation("Qingxiao").run(p);
        assertFalse(p.events.stream().anyMatch(e -> e.startsWith("L:"))); assertFalse(p.held);
        assertTrue(p.real < 82000);
    }

    @Test public void hsinConfirmedH2WaitsForR2WithoutResumingEchoOrResonance() throws Exception {
        Port p = new Port("Hsin") {
            @Override public boolean available(CombatTouchLayout.Control c) {
                if (c == LIBERATION && real >= 62500) allowLib = true;
                return super.available(c);
            }
        };
        p.signals.put(HSIN_H2, q -> q.real < 60100);
        new PriorityBuiltinRotation("Hsin").run(p);
        assertEquals(1, p.heavyAttempts);
        assertTrue(p.events.contains("L:-1:0:0"));
        assertFalse(p.events.stream().anyMatch(e -> e.startsWith("R:") || e.startsWith("E:")));
        assertFalse(p.held);
    }

    @Test public void hsinMissingR2YieldsRatherThanWaitingWholeFinisherWindow() throws Exception {
        Port p = new Port("Hsin");
        p.signals.put(HSIN_H2, q -> q.real < 60100);
        long started=p.real;
        new PriorityBuiltinRotation("Hsin").run(p);
        assertEquals(1,p.heavyAttempts);
        assertTrue(p.real-started<5000);
        assertEquals("S",p.events.get(p.events.size()-1));
        assertFalse(p.events.stream().anyMatch(e -> e.startsWith("R:") || e.startsWith("E:")));
        assertFalse(p.held);
    }

    @Test public void hsinFrozenAnimationAllowsLateR2ButMissingR2StillHasWallBound() throws Exception {
        for(boolean lateR2 : new boolean[]{true,false}) {
            Port p = new Port("Hsin") {
                @Override public boolean available(CombatTouchLayout.Control c) {
                    if(c==LIBERATION&&lateR2&&real>=64500)allowLib=true;
                    return super.available(c);
                }
            };
            p.freeze=true;
            p.signals.put(HSIN_H2,q -> q.real<60100);
            long start=p.real;
            new PriorityBuiltinRotation("Hsin").run(p);
            assertTrue(p.real-start<8000);
            assertEquals(lateR2,p.events.contains("L:-1:0:0"));
            assertFalse(p.held);
        }
    }

    @Test public void hsinR1StartsFresh18SecondFinisherWindow() throws Exception {
        Port p = new Port("Hsin"); p.on(HSIN_LIB1); p.allowLib = true;
        new PriorityBuiltinRotation("Hsin").run(p);
        assertTrue(p.real >= 78050); assertTrue(p.real < 78500);
        assertFalse(p.events.stream().anyMatch(e -> e.startsWith("E:")));
    }

    @Test public void hsinEnhancedPromptMayReturnAfterConfirmedConsumption() throws Exception {
        Port p = new Port("Hsin"); p.on(HSIN_LIB1); p.allowLib = true;
        p.signals.put(ENHANCED_E, q -> q.real < 60300 || (q.real >= 61200 && q.real < 61500));
        new PriorityBuiltinRotation("Hsin").run(p);
        assertEquals(2, p.events.stream().filter(e -> e.equals("confirmed:E")).count());
        assertTrue(p.events.stream().filter(e -> e.equals("tap:E")).count() >= 2);
    }

    @Test public void unknownSignalStopsRatherThanMeaningConsumed() {
        Port p = new Port("Hsin");
        p.signals.put(HSIN_H2, q -> { if (q.held) throw new IllegalStateException("unknown HUD"); return true; });
        assertThrows(IllegalStateException.class, () -> new PriorityBuiltinRotation("Hsin").run(p));
        assertFalse(p.held); assertFalse(p.events.contains("S"));
        assertFalse(p.events.stream().anyMatch(e -> e.startsWith("L:")));
    }

    @Test public void frozenActiveClockHasAnIndependentWallDeadline() {
        for (String name : Arrays.asList("Chisa", "Hsin", "Qingxiao")) {
            Port p = new Port(name); p.freeze = true; p.intro = p.buffs = true;
            if (name.equals("Hiyuki")) p.on(LONG_ACTION_2);
            assertThrows(name, IllegalStateException.class, () -> new PriorityBuiltinRotation(name).run(p));
            assertFalse(p.held); assertTrue(p.controls.isEmpty()); assertFalse(p.events.contains("S"));
            assertTrue(p.real < 106000);
        }
    }
    @Test public void hiyukiStandardWithoutReadyLiberationStillSwitchesWithinOriginalFieldWindow() throws Exception {
        Port p=new Port("Hiyuki");p.freeze=true;p.libCd=0;p.on(LONG_ACTION);
        new PriorityBuiltinRotation("Hiyuki").run(p);
        assertTrue(p.events.contains("S"));assertTrue(p.real<78000);assertFalse(p.held);
    }
    @Test public void hiyukiSecondPhaseWithMissingActiveTimeStillFinishesItsFieldWindow() throws Exception {
        Port p=new Port("Hiyuki");p.freeze=true;p.on(LONG_ACTION_2);
        new PriorityBuiltinRotation("Hiyuki").run(p);
        assertTrue(p.events.contains("S"));assertTrue(p.real<78000);assertFalse(p.held);
    }
    @Test public void slowVerifiedHiyukiFieldReleasesAndYieldsInsteadOfStoppingDaily() throws Exception {
        Port p=new Port("Hiyuki"){
            @Override public void checkCombat(){advance(1100);}
        };
        p.on(LONG_ACTION_2);p.on(HIYUKI_HEAVY);
        new PriorityBuiltinRotation("Hiyuki").run(p);
        assertTrue(p.events.contains("S"));assertFalse(p.held);assertTrue(p.controls.isEmpty());
        assertTrue(p.real<125000);
    }

    @Test public void galbrenaFastIntroReleasesBeforeSwitchAndSkipsRemainingAxis() throws Exception {
        Port p = new Port("Galbrena"); p.intro = p.fast = true;
        new PriorityBuiltinRotation("Galbrena").run(p);
        assertEquals(Arrays.asList("down", "P:1000", "up", "S"), p.events);
    }

    @Test public void galbrenaFullIntroPreservesHoldAndDodgeTiming() throws Exception {
        Port p = new Port("Galbrena"); p.intro = true;
        new PriorityBuiltinRotation("Galbrena").run(p);
        assertEquals(Arrays.asList("down", "P:1000", "P:440", "up", "dodge:600"), p.events.subList(0, 5));
    }

    @Test public void phrolovaConfirmedLiberationLocksReentryExceptCantarellaOutroAfter14s() throws Exception {
        PriorityBuiltinRotation rotation = new PriorityBuiltinRotation("Phrolova");
        assertEquals(CombatTeam.MUST, rotation.switchPriority("Cantarella", true, 60000));
        assertEquals(CombatTeam.NORMAL, rotation.switchPriority("Cantarella", false, 60000));
        Port p = new Port("Phrolova"); p.allowLib = true; rotation.run(p);
        long cast = p.active;
        assertEquals(CombatTeam.NO, rotation.switchPriority("ShoreKeeper", true, cast + 20000));
        assertEquals(CombatTeam.NO, rotation.switchPriority("Cantarella", true, cast + 14000));
        assertEquals(CombatTeam.MUST, rotation.switchPriority("Cantarella", true, cast + 14001));
        assertEquals(CombatTeam.NORMAL, rotation.switchPriority("ShoreKeeper", true, cast + 24000));
    }

    @Test public void hsinAndHiyukiHeavyWallDeadlinesStillReleaseDuringFreeze() {
        for (String name : Arrays.asList("Hsin", "Hiyuki")) {
            long limit = name.equals("Hsin") ? 1200 : 2000;
            Port p = new Port(name) {
                long downAt;
                @Override public void holdAttack(boolean down) {
                    super.holdAttack(down);
                    if (down) downAt = real;
                    else {
                        assertTrue("hold extended by freeze", real - downAt <= limit);
                        throw new IllegalStateException("stop after first release");
                    }
                }
            };
            p.freeze = true; p.on(HSIN_H2); p.on(LONG_ACTION_2); p.on(HIYUKI_HEAVY);
            assertThrows(IllegalStateException.class, () -> new PriorityBuiltinRotation(name).run(p));
            assertEquals(1, p.heavyAttempts); assertFalse(p.held);
        }
    }

    @Test public void hiyukiHeldLiberationIsReleasedAt8sAndDoesNotFakeSuccess() throws Exception {
        Port p = new Port("Hiyuki"); p.on(LONG_ACTION_2); p.allowLib = true; p.libCd = 0;
        p.signals.put(LONG_ACTION_2, q -> q.real < 60001);
        new PriorityBuiltinRotation("Hiyuki").run(p);
        assertTrue(p.events.contains("up:LIBERATION")); assertTrue(p.controls.isEmpty());
        assertTrue(p.events.contains("confirm-held:3000")); assertFalse(p.events.contains("P:500"));
    }

    @Test public void hiyukiDisabledLiberationNeverHoldsTheButton() throws Exception {
        Port p = new Port("Hiyuki"); p.on(LONG_ACTION_2); p.allowLib = true; p.libEnabled = false;
        new PriorityBuiltinRotation("Hiyuki").run(p);
        assertFalse(p.events.contains("down:LIBERATION"));
    }

    @Test public void hiyukiRightPromptIsOneDodgeWithIntervalNotContinuousDodge() throws Exception {
        Port p = new Port("Hiyuki"); p.signals.put(LONG_ACTION_2, q -> q.real < 60250); p.on(HIYUKI_RIGHT);
        new PriorityBuiltinRotation("Hiyuki").run(p);
        assertTrue(p.events.contains("dodge-click:1000")); assertFalse(p.events.contains("dodge:1000"));
    }

    @Test public void partialHoldFailureAndInterruptionAlwaysRelease() {
        for (String name : Arrays.asList("Hsin", "Qingxiao", "Galbrena")) {
            Port p = new Port(name) {
                @Override public void holdAttack(boolean down) {
                    super.holdAttack(down); if (down) throw new IllegalStateException("partial down failure");
                }
            };
            p.intro = p.buffs = true; p.on(HSIN_H2); p.on(QINGXIAO_H2);
            assertThrows(IllegalStateException.class, () -> new PriorityBuiltinRotation(name).run(p));
            assertFalse(p.held); assertEquals("up", p.events.get(p.events.size() - 1));
        }
        Port interrupted = new Port("Chisa") {
            @Override public void hold(CombatTouchLayout.Control c, boolean down) {
                super.hold(c, down); if (down) Thread.currentThread().interrupt();
            }
        };
        interrupted.mouse = true;
        try { assertThrows(InterruptedException.class, () -> new PriorityBuiltinRotation("Chisa").run(interrupted)); }
        finally { Thread.interrupted(); }
        assertTrue(interrupted.controls.isEmpty()); assertFalse(interrupted.events.contains("S"));
    }

    @Test public void priorityOrderFollowsRecognizedTeamNotHistoricalSlots() throws Exception {
        BuiltinRotations rotations = new BuiltinRotations(catalog(), Arrays.asList("Qingxiao", "Hiyuki", "Chisa"));
        assertArrayEquals(new int[]{200, 200, 200}, rotations.priorities("Chisa", true, 60000));
        PriorityBuiltinRotation hiyuki = new PriorityBuiltinRotation("Hiyuki");
        assertEquals(CombatTeam.MUST, hiyuki.switchPriority("Lucilla", true, 60000));
        assertEquals(CombatTeam.NORMAL, hiyuki.switchPriority("Lucilla", false, 60000));
        assertThrows(IllegalArgumentException.class, () -> rotations.priorities("Linnai", true, 60000));
    }

    private BuiltinCombatCatalog catalog() throws Exception {
        try (InputStreamReader input = new InputStreamReader(getClass().getResourceAsStream("/builtin-combat.tsv"), StandardCharsets.UTF_8)) {
            return new BuiltinCombatCatalog(input);
        }
    }
}
