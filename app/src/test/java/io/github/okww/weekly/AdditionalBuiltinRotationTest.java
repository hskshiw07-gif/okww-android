package io.github.okww.weekly;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import static org.junit.Assert.*;
import static io.github.okww.weekly.CombatTouchLayout.Control.*;

public class AdditionalBuiltinRotationTest {
    static class Port extends TeamBuiltinRotationTest.Port implements AdditionalBuiltinRotation.Port {
        boolean mouse, con, echo;
        String activeName, requiredName;
        Port(int flags) {
            real = active = 60000;
            intro = (flags & 1) != 0; allowLib = (flags & 2) != 0; allowRes = (flags & 4) != 0;
            mouse = (flags & 8) != 0; con = (flags & 16) != 0; echo = (flags & 32) != 0;
        }
        public void requireActiveCharacter(String name) {
            requiredName = name;
            if (!name.equals(activeName)) throw new IllegalStateException("unknown/wrong current identity");
        }
        public boolean resonance(boolean normal, long timeout) {
            events.add("R:" + (normal ? 1 : 0) + ":" + timeout); advance(50);
            boolean result = allowRes; allowRes = false; return result;
        }
        public boolean liberation(double limit, boolean normal, long wait) {
            events.add("L:" + (int) limit + ":" + (normal ? 1 : 0) + ":" + wait); advance(50);
            boolean result = allowLib; allowLib = false; return result;
        }
        public boolean echo(long timeout) { events.add("E:" + timeout); advance(50); return true; }
        public void waitDown(boolean normal) { events.add("D:" + (normal ? 1 : 0)); }
        public void waitPartyHudOptional(long timeout) { events.add("party:" + timeout); }
        public void waitIntro(long timeout) { events.add("I:" + timeout); }
        public void normalAttack(long duration, long interval, boolean interrupt) {
            events.add("N:" + duration + ":" + interval + ":" + (interrupt ? 1 : 0)); advance(duration);
        }
        public void normalTap() { events.add("T"); advance(10); }
        public void normalClick(long interval) { events.add("C:" + interval); advance(interval); }
        public void repeatResonance(long duration, long interval) { events.add("REPEAT:" + duration + ":" + interval); advance(duration); }
        public boolean mouseForteFull() { return mouse; }
        public boolean forteFull() { return mouse; }
        public boolean concertoFull() { return con; }
        public boolean heavyMouseForte() { events.add("M"); mouse = false; advance(200); return true; }
        public void defaultHeavyAttack() { heavyAttack(600); }
        public void heavyAttack(long duration) { events.add("H:" + duration); advance(duration); }
        public void pause(long duration) { events.add("P:" + duration); advance(duration); }
        public boolean available(CombatTouchLayout.Control control) { return control == ECHO ? echo : super.available(control); }
        public void switchNext() { events.add("S"); }
    }

    @Test public void originalPcMethodsMatch1024IntroSkillForteConcertoBranches() throws Exception {
        int count = 0;
        java.util.Set<String> names = new HashSet<>();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/rotation-parity.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] values = line.split("\t", -1);
                Port p = new Port(Integer.parseInt(values[1]));
                if (values[2].equals("!TIMEOUT")) {
                    assertThrows(IllegalStateException.class, () -> new AdditionalBuiltinRotation(values[0]).run(p));
                    assertFalse(p.events.contains("S"));
                } else {
                    new AdditionalBuiltinRotation(values[0]).run(p);
                    assertEquals(values[0] + "/" + values[1], values[2], String.join(",", p.events));
                }
                assertFalse(p.held); names.add(values[0]); count++;
            }
        }
        assertEquals(1024, count); assertEquals(AdditionalBuiltinRotation.CHARACTERS, names);
    }

    @Test public void everyLoopHasWallDeadlineEvenWhenActiveTimeIsFrozen() {
        for (String name : List.of("Aalto", "Lingyang", "Lumi", "Xiangliyao")) {
            Port p = new Port(0); p.freeze = true;
            if (name.equals("Xiangliyao")) p.allowLib = true;
            assertThrows(name, IllegalStateException.class, () -> new AdditionalBuiltinRotation(name).run(p));
            assertFalse(p.events.contains("S"));
        }
    }

    @Test public void sanhuaAlwaysReleasesHoldOnAcquisitionOrSkillFailure() {
        for (boolean duringDown : new boolean[]{true, false}) {
            Port p = new Port(0) {
                @Override public void holdAttack(boolean down) {
                    super.holdAttack(down);
                    if (down && duringDown) throw new IllegalStateException("partial acquisition failure");
                }
                @Override public boolean liberation(double limit, boolean normal, long wait) {
                    throw new IllegalStateException("lost HUD during hold");
                }
            };
            assertThrows(IllegalStateException.class, () -> new AdditionalBuiltinRotation("Sanhua").run(p));
            assertFalse(p.held); assertEquals("up", p.events.get(p.events.size() - 1));
            assertFalse(p.events.contains("S"));
        }
    }

    @Test public void xiangliyaoRetainsLiberationAcrossTurnsButNotForever() throws Exception {
        AdditionalBuiltinRotation rotation = new AdditionalBuiltinRotation("Xiangliyao");
        Port p = new Port(2 | 4 | 32); rotation.run(p);
        p.events.clear(); p.allowRes = true; p.advance(1000); rotation.run(p);
        assertTrue(p.events.contains("R:1:0")); assertFalse(p.events.contains("E:1000"));
        p.events.clear(); p.advance(25000); rotation.run(p);
        assertTrue(p.events.contains("E:1000"));
    }

    @Test public void interruptionAndUnknownCombatNeverSendTrailingInputs() {
        Port p = new Port(0); Thread.currentThread().interrupt();
        try { assertThrows(InterruptedException.class, () -> new AdditionalBuiltinRotation("Baizhi").run(p)); }
        finally { Thread.interrupted(); }
        assertTrue(p.events.isEmpty());
        p.dead = true;
        assertThrows(IllegalStateException.class, () -> new AdditionalBuiltinRotation("Taoqi").run(p));
        assertTrue(p.events.isEmpty());
    }

    private BuiltinCombatCatalog catalog() throws Exception {
        try (InputStreamReader input = new InputStreamReader(getClass().getResourceAsStream("/builtin-combat.tsv"), StandardCharsets.UTF_8)) {
            return new BuiltinCombatCatalog(input);
        }
    }

    @Test public void dispatchSupportsMixedTeamsAndAllOrdersNotJustObservedThree() throws Exception {
        BuiltinCombatCatalog catalog = catalog();
        int ready = 0;
        for (String name : catalog.characters.keySet()) if (BuiltinRotations.supported(name)) ready++;
        assertEquals(26, ready);
        List<String> team = Arrays.asList("Baizhi", "Yuanwu", "Sanhua");
        BuiltinRotations rotations = new BuiltinRotations(catalog, team);
        for (String name : team) {
            Port p = new Port(0); p.activeName = name; rotations.run(name, p);
            assertEquals(name, p.requiredName); assertEquals("S", p.events.get(p.events.size() - 1));
        }
        assertTrue(BuiltinRotations.missing(catalog, Arrays.asList("Aemeath", "Verina", "Mortefi")).isEmpty());
        new BuiltinRotations(catalog, Arrays.asList("Mortefi", "Aemeath", "Verina"));
        BuiltinRotations old = new BuiltinRotations(catalog, Arrays.asList("Verina", "ShoreKeeper", "YangYangSp"));
        for (String name : Arrays.asList("Verina", "ShoreKeeper", "YangYangSp")) {
            Port p = new Port(0); p.activeName = name; old.run(name, p);
            assertEquals("S", p.events.get(p.events.size() - 1));
        }
    }

    @Test public void unsupportedWholeTeamOrUnknownIdentityIsRejectedBeforeAnyInput() throws Exception {
        BuiltinCombatCatalog catalog = catalog();
        List<String> mixed = Arrays.asList("ShoreKeeper", "Carlotta", "Camellya");
        assertEquals(Arrays.asList("Carlotta", "Camellya"), BuiltinRotations.missing(catalog, mixed));
        assertThrows(IllegalArgumentException.class, () -> new BuiltinRotations(catalog, mixed));
        for (List<String> bad : Arrays.asList(Arrays.asList("BaseChar", "Verina", "Sanhua"),
                Arrays.asList("Verina", "Verina", "Sanhua"), Arrays.asList("Verina", null, "Sanhua"),
                Arrays.asList("Verina", "workshop:Baizhi", "Sanhua")))
            assertThrows(RuntimeException.class, () -> new BuiltinRotations(catalog, bad));
        BuiltinRotations rotations = new BuiltinRotations(catalog, Arrays.asList("Baizhi", "Yuanwu", "Sanhua"));
        Port p = new Port(0); p.activeName = "Yuanwu";
        assertThrows(IllegalStateException.class, () -> rotations.run("Baizhi", p));
        assertThrows(IllegalArgumentException.class, () -> rotations.run("ShoreKeeper", p));
        assertTrue(p.events.isEmpty());
    }
}
