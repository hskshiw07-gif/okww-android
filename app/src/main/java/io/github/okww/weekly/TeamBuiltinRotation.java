package io.github.okww.weekly;

import java.util.Set;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static io.github.okww.weekly.CombatTouchLayout.Control.*;

/** Character-specific control flows; usable in any identified team, not a fixed three-character script. */
final class TeamBuiltinRotation {
    static final Set<String> CHARACTERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("ShoreKeeper", "YangYangSp", "Aemeath")));
    enum Feature { AEMEATH_LIB2, AEMEATH_E1, AEMEATH_E2, AEMEATH_HUMAN_HEAVY }
    interface Port extends BuiltinRotation.Port {
        // Unknown skill/feature/animation state must throw; a completed touch callback is not a successful cast.
        boolean hasIntro();
        boolean allTeammateBuffs();
        boolean hasSubDpsTeammate();
        boolean partyHudVisible() throws Exception;
        void waitPartyHud(long timeoutMs) throws Exception;
        boolean available(CombatTouchLayout.Control control) throws Exception;
        boolean feature(Feature feature) throws Exception;
        boolean longAction() throws Exception;
        void holdAttack(boolean down) throws Exception; // Continuous pointer identity, not repeated short holds; up must survive cancellation.
        void refresh() throws Exception;
        long realTimeMs();
        void markSupportBuff() throws Exception;
        boolean breakAction() throws Exception;
        void cycleSleep(long realCycleStartedAt) throws Exception; // Check break action, then pad the source's 100ms cycle.
        default void normalClick() throws Exception { normalAttack(90); }
    }
    private final String character;
    private long lastHeavy = -1;

    TeamBuiltinRotation(String character) {
        if (!supported(character)) throw new IllegalArgumentException("该角色尚无安卓内置轴：" + character);
        this.character = character;
    }
    static boolean supported(String character) {
        return CHARACTERS.contains(character == null ? "" : character);
    }
    void run(Port port) throws Exception {
        check(port);
        long wallStart = port.realTimeMs();
        switch (character) {
            case "ShoreKeeper" -> shoreKeeper(port);
            case "YangYangSp" -> yangYangSp(port, wallStart);
            case "Aemeath" -> aemeath(port, wallStart);
            default -> throw new IllegalStateException("未注册内置轴");
        }
        check(port); port.switchNext();
    }

    private void shoreKeeper(Port p) throws Exception {
        if (p.hasIntro()) {
            p.pause(100);
            if (!p.partyHudVisible()) p.waitPartyHud(4000);
            else p.normalAttack(1200);
        }
        check(p); p.cast(ECHO);
        check(p); boolean liberation = p.cast(LIBERATION);
        check(p); boolean resonance = p.cast(RESONANCE);
        if (liberation || resonance) p.markSupportBuff();
        if (!resonance) { check(p); p.heavyIfForte(); }
    }

    private void yangYangSp(Port p, long wallStart) throws Exception {
        long duration = p.hasIntro() ? 8000 : 3200;
        long start = p.activeTimeMs(), resonanceSince = -1;
        boolean echoUsed = false;
        try {
            p.holdAttack(true);
            while (elapsed(p.activeTimeMs(), start) < duration) {
                bounded(p, wallStart);
                if (p.available(LIBERATION)) {
                    if (p.cast(LIBERATION)) {
                        duration += 2000;
                        if (duration > 20000) throw new IllegalStateException("解放反复确认异常，停止而非无限延长站场");
                    }
                } else if (p.available(RESONANCE)) {
                    if (resonanceSince < 0) resonanceSince = p.realTimeMs();
                    if (elapsed(p.realTimeMs(), resonanceSince) > 200) p.cast(RESONANCE);
                } else if (!echoUsed) {
                    resonanceSince = -1; echoUsed = p.cast(ECHO);
                } else resonanceSince = -1;
                p.pause(50); p.refresh();
            }
        } finally { p.holdAttack(false); }
        p.pause(100);
    }

    private void aemeath(Port p, long wallStart) throws Exception {
        boolean mustLib2 = p.allTeammateBuffs() && p.hasIntro();
        if (!mustLib2 && p.hasSubDpsTeammate()) {
            if (heavy(p)) p.pause(300);
            return;
        } else if (p.hasIntro()) p.normalAttack(2100);
        long start = p.activeTimeMs();
        boolean enhanced = false, lib2 = false;
        while (elapsed(p.activeTimeMs(), start) < 12000) {
            bounded(p, wallStart);
            long cycleStartedAt = p.realTimeMs();
            if (heavy(p)) {
                start = p.activeTimeMs();
                if (!p.breakAction()) p.pause(100);
                check(p); p.refresh(); continue;
            }
            boolean isLib2 = p.feature(Feature.AEMEATH_LIB2);
            boolean action = p.cast(LIBERATION);
            if (action) lib2 |= isLib2;
            else if (p.feature(Feature.AEMEATH_E1) || p.feature(Feature.AEMEATH_E2)) {
                if (p.cast(RESONANCE)) { enhanced = true; p.cast(ECHO); p.refresh(); }
                isLib2 = p.feature(Feature.AEMEATH_LIB2);
                if (p.cast(LIBERATION)) lib2 |= isLib2;
                action = true;
            } else p.normalClick();
            if (lib2) return;
            if (action) {
                if (p.longAction()) start = p.activeTimeMs();
                else if (!((p.hasIntro() && !enhanced) || (mustLib2 && !lib2))) return;
            }
            p.cycleSleep(cycleStartedAt); p.refresh();
        }
    }

    private boolean heavy(Port p) throws Exception {
        long now = p.realTimeMs();
        if (lastHeavy >= 0 && elapsed(now, lastHeavy) < 2000) return false;
        if (!p.feature(Feature.AEMEATH_HUMAN_HEAVY)) return false;
        lastHeavy = now;
        boolean released;
        try {
            p.holdAttack(true);
            long started = p.realTimeMs();
            while (p.feature(Feature.AEMEATH_HUMAN_HEAVY) && elapsed(p.realTimeMs(), started) < 1200) {
                check(p); p.pause(30); p.refresh();
            }
            released = !p.feature(Feature.AEMEATH_HUMAN_HEAVY);
        } finally { p.holdAttack(false); }
        p.pause(10);
        return released;
    }
    private static long elapsed(long now, long before) {
        if (now < before) throw new IllegalStateException("战斗时钟倒退");
        return now - before;
    }
    private static void bounded(Port p, long wallStart) throws Exception {
        check(p);
        if (elapsed(p.realTimeMs(), wallStart) >= 30000) throw new IllegalStateException("单次角色轴超过30秒，停止并释放触控");
    }
    private static void check(Port p) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        p.checkCombat();
    }
}
