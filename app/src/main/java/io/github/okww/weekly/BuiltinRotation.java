package io.github.okww.weekly;

import java.util.Set;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

/** First-party control flow from src/char, independent of desktop input and workshop loading. */
final class BuiltinRotation {
    static final Set<String> CHARACTERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("BaseChar", "Verina")));
    interface Port {
        // All helpers must use fresh, identified combat HUD state; unknown state must throw, not guess ready.
        void checkCombat() throws Exception;
        long activeTimeMs(); // Monotonic combat time excluding observed animation freezes.
        void waitIntro(long timeoutMs) throws Exception;
        boolean cast(CombatTouchLayout.Control control) throws Exception; // True only after game-side confirmation.
        void heavyIfForte() throws Exception;
        void normalAttack(long durationMs) throws Exception;
        boolean concertoFull() throws Exception;
        boolean forteFull() throws Exception;
        void waitStanding(long timeoutMs) throws Exception;
        void pause(long durationMs) throws Exception;
        void heavyAttack(long durationMs) throws Exception;
        void jump() throws Exception;
        void switchNext() throws Exception;
    }
    private final String character;
    private Long lastHeavy;

    BuiltinRotation(String character) {
        if (!CHARACTERS.contains(character == null ? "" : character))
            throw new IllegalArgumentException("该内置角色尚未迁移，不回退通用连点：" + character);
        this.character = character;
    }

    void run(Port port) throws Exception {
        check(port);
        if (character.equals("BaseChar")) {
            port.waitIntro(1200);
            cast(port, CombatTouchLayout.Control.ECHO);
            cast(port, CombatTouchLayout.Control.LIBERATION);
            if (!cast(port, CombatTouchLayout.Control.RESONANCE)) { check(port); port.heavyIfForte(); }
        } else performVerina(port);
        check(port); port.switchNext();
    }

    private void performVerina(Port port) throws Exception {
        long started = port.activeTimeMs();
        port.normalAttack(600);
        for (CombatTouchLayout.Control control : new CombatTouchLayout.Control[]{CombatTouchLayout.Control.LIBERATION,
                CombatTouchLayout.Control.RESONANCE, CombatTouchLayout.Control.ECHO}) {
            if (shouldSwitch(port, started)) return;
            cast(port, control);
        }
        if (shouldSwitch(port, started)) return;
        check(port); port.waitStanding(2000);
        check(port); port.pause(800);
        check(port);
        long now = port.activeTimeMs();
        if (lastHeavy != null && now < lastHeavy) throw new IllegalStateException("战斗计时倒退");
        if (port.forteFull() && (lastHeavy == null || now - lastHeavy >= 8000)) {
            check(port); port.heavyAttack(700); lastHeavy = port.activeTimeMs();
        }
        check(port); port.jump();
        check(port); port.normalAttack(500);
    }
    private boolean shouldSwitch(Port port, long started) throws Exception {
        check(port);
        long now = port.activeTimeMs();
        if (now < started) throw new IllegalStateException("战斗计时倒退");
        return port.concertoFull() || now - started >= 6500;
    }
    private boolean cast(Port port, CombatTouchLayout.Control control) throws Exception {
        check(port); return port.cast(control);
    }
    private void check(Port port) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        port.checkCombat();
    }
}
