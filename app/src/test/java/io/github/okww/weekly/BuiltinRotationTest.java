package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class BuiltinRotationTest {
    private static class FakePort implements BuiltinRotation.Port {
        final List<String> events = new ArrayList<>();
        long now;
        boolean resonance = true, forte = true, con, conAfterLiberation, timeoutAfterLiberation;
        String failOn;
        void event(String text) throws Exception {
            if (text.equals(failOn)) throw new IllegalStateException("HUD unknown or game rejected action");
            events.add(text);
        }
        public void checkCombat() throws Exception { if ("check".equals(failOn)) throw new IllegalStateException("Not in verified combat"); }
        public long activeTimeMs() { return now; }
        public void waitIntro(long timeout) throws Exception { event("intro:" + timeout); }
        public boolean cast(CombatTouchLayout.Control control) throws Exception {
            event(control.name()); now += 50;
            if (control == CombatTouchLayout.Control.LIBERATION) {
                if (conAfterLiberation) con = true;
                if (timeoutAfterLiberation) now += 6500;
            }
            return control != CombatTouchLayout.Control.RESONANCE || resonance;
        }
        public void heavyIfForte() throws Exception { event("heavy-if-forte"); }
        public void normalAttack(long duration) throws Exception { event("normal:" + duration); now += duration; }
        public boolean concertoFull() { return con; }
        public boolean forteFull() { return forte; }
        public void waitStanding(long timeout) throws Exception { event("standing:" + timeout); }
        public void pause(long duration) throws Exception { event("pause:" + duration); now += duration; }
        public void heavyAttack(long duration) throws Exception { event("heavy:" + duration); now += duration; }
        public void jump() throws Exception { event("jump"); now += 10; }
        public void switchNext() throws Exception { event("switch"); }
    }
    @Test public void baseRotationPreservesSourceOrderAndOnlyFallsBackWhenResonanceNotCast() throws Exception {
        for (boolean resonance : new boolean[]{true, false}) {
            FakePort port = new FakePort(); port.resonance = resonance;
            new BuiltinRotation("BaseChar").run(port);
            List<String> expected = new ArrayList<>(Arrays.asList("intro:1200", "ECHO", "LIBERATION", "RESONANCE"));
            if (!resonance) expected.add("heavy-if-forte");
            expected.add("switch"); assertEquals(expected, port.events);
        }
    }
    @Test public void verinaPreservesBuiltinRotationRatherThanGenericSkillSpam() throws Exception {
        FakePort port = new FakePort();
        new BuiltinRotation("Verina").run(port);
        assertEquals(Arrays.asList("normal:600", "LIBERATION", "RESONANCE", "ECHO", "standing:2000", "pause:800",
                "heavy:700", "jump", "normal:500", "switch"), port.events);
    }
    @Test public void concertoFullAfterLiberationSkipsRemainingCastsAndSwitches() throws Exception {
        FakePort port = new FakePort(); port.conAfterLiberation = true;
        new BuiltinRotation("Verina").run(port);
        assertEquals(Arrays.asList("normal:600", "LIBERATION", "switch"), port.events);
    }
    @Test public void fieldTimeoutUsesActiveCombatClockAndSwitchesEarly() throws Exception {
        FakePort port = new FakePort(); port.timeoutAfterLiberation = true;
        new BuiltinRotation("Verina").run(port);
        assertEquals(Arrays.asList("normal:600", "LIBERATION", "switch"), port.events);
    }
    @Test public void forteAndHeavyIntervalAreNotReplacedByUnconditionalLongPress() throws Exception {
        FakePort port = new FakePort(); port.forte = false;
        BuiltinRotation rotation = new BuiltinRotation("Verina");
        rotation.run(port); assertFalse(port.events.contains("heavy:700"));
        port.forte = true; port.events.clear(); rotation.run(port); assertTrue(port.events.contains("heavy:700"));
        port.events.clear(); rotation.run(port); assertFalse(port.events.contains("heavy:700"));
        port.now += 8000; port.events.clear(); rotation.run(port); assertTrue(port.events.contains("heavy:700"));
    }
    @Test public void unknownHudOrFailedSkillDoesNotSendTrailingSwitch() {
        for (String fail : new String[]{"check", "LIBERATION", "heavy:700"}) {
            FakePort port = new FakePort(); port.failOn = fail;
            assertThrows(IllegalStateException.class, () -> new BuiltinRotation("Verina").run(port));
            assertFalse(port.events.contains("switch"));
        }
    }
    @Test public void workshopAndUnportedCharactersDoNotFallBackToBaseChar() {
        for (String id : new String[]{"workshop:Verina", "Sanhua", "unknown", "", null})
            assertThrows(IllegalArgumentException.class, () -> new BuiltinRotation(id));
    }
    @Test public void interruptionPreventsAllSubsequentInputs() {
        FakePort port = new FakePort(); Thread.currentThread().interrupt();
        try { assertThrows(InterruptedException.class, () -> new BuiltinRotation("Verina").run(port)); }
        finally { Thread.interrupted(); }
        assertTrue(port.events.isEmpty());
    }
}
