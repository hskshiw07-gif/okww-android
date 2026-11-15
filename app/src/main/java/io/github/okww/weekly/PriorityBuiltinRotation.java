package io.github.okww.weekly;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import static io.github.okww.weekly.CombatTouchLayout.Control.*;

/** First-party priority characters. Control-flow translation, NOT a mobile calibration claim. */
final class PriorityBuiltinRotation {
    private static final class FieldLimit extends IllegalStateException {}
    static final Set<String> CHARACTERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "Chisa", "Hiyuki", "Qingxiao", "Phrolova", "Hsin", "Galbrena")));
    enum Signal {
        FLYING, LONG_ACTION, LONG_ACTION_2, GALBRENA_RES,
        QINGXIAO_E, QINGXIAO_H1, QINGXIAO_H2,
        HSIN_H1, HSIN_H2, HSIN_LIB1, HSIN_LIB2, ENHANCED_E,
        HIYUKI_HEAVY, HIYUKI_LEFT, HIYUKI_RIGHT
    }
    interface Port extends AdditionalBuiltinRotation.Port {
        // Must verify ALL required readers/touches before the first input, including rarely taken branches.
        void requirePriorityCalibration(String character) throws Exception;
        boolean signal(Signal signal) throws Exception; // Unknown/stale is an exception, never false.
        String outroSource(); // Canonical identity from a confirmed full-concerto switch, not team order.
        boolean ownBuff();
        boolean fastPerform() throws Exception;
        boolean chisaDps();
        long cooldownMs(CombatTouchLayout.Control control) throws Exception;
        boolean useLiberation();
        void hold(CombatTouchLayout.Control control, boolean down) throws Exception; // Idempotent down; up survives cancellation.
        void dodge(long durationMs) throws Exception;
        void dodgeClick(long intervalMs) throws Exception; // One tap with a minimum repeat interval; 0 = source default.
        void shorekeeperAutoDodge() throws Exception; // Only with confirmed ShoreKeeper outro/buff.
        boolean enhancedResonance(long timeoutMs, long minimumAnimationMs) throws Exception;
        void tapResonance() throws Exception;
        void recordResonanceConfirmed() throws Exception;
        boolean confirmHeldLiberation(long startedAt, long timeoutMs) throws Exception;
        default boolean chisaForteConsumed()throws Exception{return !forteFull();}
    }

    private final String character;
    private long wallStart, activeStart, lastLiberation = -1;
    private boolean resReady, specialPhrolova;
    private long finisherStart;
    private boolean h2Completed, lib2Cast;
    private int kendoCount;

    PriorityBuiltinRotation(String character) {
        if (!CHARACTERS.contains(character == null ? "" : character))
            throw new IllegalArgumentException("未注册优先内置角色：" + character);
        this.character = character;
    }

    void run(Port p) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        p.requireActiveCharacter(character);
        p.requirePriorityCalibration(character);
        wallStart = p.realTimeMs(); activeStart = p.activeTimeMs();
        check(p);
        try {switch (character) {
            case "Chisa" -> { if (p.chisaDps()) chisaDps(p); else chisaSupport(p); }
            case "Hiyuki" -> hiyuki(p);
            case "Qingxiao" -> qingxiao(p);
            case "Phrolova" -> phrolova(p);
            case "Hsin" -> hsin(p);
            case "Galbrena" -> galbrena(p);
            default -> throw new IllegalStateException("内置角色分派缺失");
        }}catch(FieldLimit limit){
            // A verified Hiyuki field that runs out of time yields to the team;
            // gesture helpers release in finally, and unknown HUD/identity still aborts.
            if(!character.equals("Hiyuki"))throw limit;
        }
        p.requireActiveCharacter(character);p.checkCombat();p.switchNext();kendoCount = 0;
    }

    private void chisaSupport(Port p) throws Exception {
        boolean longActions = p.hasIntro() && !p.ownBuff();
        if (p.hasIntro()) normal(p, 2000);
        landIfSkillsUnavailable(p);
        p.echo(0);
        long start = p.activeTimeMs(), duration = longActions ? 10000 : 1200;
        while (elapsed(p.activeTimeMs(), start) < duration) {
            check(p); long cycle = p.realTimeMs();
            if (p.concertoFull()) return;
            if (p.available(LIBERATION)) p.liberation(-1, false, 0);
            else if (p.forteFull()) { if (chisaForte(p)) return; }
            else if (!p.resonance(true, 0)) p.normalClick();
            cycle(p, cycle);
        }
    }

    private void chisaDps(Port p) throws Exception {
        long timeout = p.hasIntro() ? 2300 : 2500;
        if (p.hasIntro()) normal(p, 800);
        landIfSkillsUnavailable(p); p.echo(1000);
        long start = p.realTimeMs();
        while (elapsed(p.realTimeMs(), start) < timeout) {
            check(p);
            if (elapsed(p.realTimeMs(), start) < 500 && p.liberation(-1, false, 100)) {
                start = p.realTimeMs(); timeout = 10000; p.pause(200);
            }
            if (elapsed(p.realTimeMs(), start) < 500 && !p.forteFull() && p.resonance(true, 0)) {
                start = p.realTimeMs(); if (timeout != 10000) timeout = 1700;
            }
            if (p.forteFull() && chisaForte(p)) return;
            p.normalClick(); tick(p, 20);
        }
    }

    private void landIfSkillsUnavailable(Port p) throws Exception {
        if (signal(p, Signal.FLYING) && !p.available(LIBERATION) && !p.available(RESONANCE)) p.waitDown(true);
    }

    private boolean chisaForte(Port p) throws Exception {
        if (signal(p, Signal.FLYING)) p.waitDown(true);
        holdFor(p, RESONANCE, 1200);
        if (!p.chisaForteConsumed()) return false;
        holdFor(p, ATTACK, 3500);
        return true;
    }

    private void qingxiao(Port p) throws Exception {
        if (!(p.allTeammateBuffs() && p.hasIntro())) { qingxiaoResonance(p); return; }
        long start = p.activeTimeMs(); boolean h2 = false;
        while (elapsed(p.activeTimeMs(), start) < 18000) {
            check(p); long cycle = p.realTimeMs();
            int heavy = qingxiaoHeavy(p);
            if (heavy != 0) {
                p.breakAction();
                if (heavy == 2) {
                    waitAvailable(p, LIBERATION, 1500, false);
                    p.liberation(-1, false, 100); h2 = true; break;
                }
            } else if (!qingxiaoResonance(p)) p.normalClick();
            cycle(p, cycle);
        }
        if (!h2) qingxiaoHeavy(p);
        else p.waitPartyHudOptional(1000);
    }

    private boolean qingxiaoResonance(Port p) throws Exception {
        return signal(p, Signal.QINGXIAO_E) && p.enhancedResonance(1500, 500);
    }
    private int qingxiaoHeavy(Port p) throws Exception {
        int type = signal(p, Signal.QINGXIAO_H1) ? 1 : signal(p, Signal.QINGXIAO_H2) ? 2 : 0;
        if (type == 0) return 0;
        return releaseHeavy(p, Signal.QINGXIAO_H1, Signal.QINGXIAO_H2, 2000, 250, true) ? type : 0;
    }

    private void hsin(Port p) throws Exception {
        finisherStart = -1; h2Completed = lib2Cast = false;
        long start = p.activeTimeMs(), duration = p.hasIntro() ? 12000 : 6000;
        while (elapsed(p.activeTimeMs(), finisherStart >= 0 ? finisherStart : start) < (finisherStart >= 0 ? 18000 : duration)) {
            check(p); long cycle = p.realTimeMs();
            if (h2Completed) {
                if (hsinLib(p, true)) return;
                cycle(p, cycle); continue;
            }
            if (signal(p, Signal.HSIN_LIB2)) {
                hsinLib(p, false); if (lib2Cast) return;
            }
            int heavy = signal(p, Signal.HSIN_H2) ? 2 : signal(p, Signal.HSIN_H1) ? 1 : 0;
            if (heavy == 2) {
                if (hsinHeavy(p, heavy)) {
                    long wait = p.realTimeMs();
                    do {
                        if (hsinLib(p, true)) return;
                        tick(p, 50);
                    } while (elapsed(p.realTimeMs(), wait) < 1500);
                }
                cycle(p, cycle); continue;
            }
            if (finisherStart >= 0) {
                if (signal(p, Signal.ENHANCED_E)) hsinEnhancedE(p);
                else p.normalClick();
                cycle(p, cycle); continue;
            }
            if (p.available(RESONANCE) && p.resonance(true, 0)) { /* confirmed E */ }
            else if (hsinLib(p, false)) { if (lib2Cast) return; }
            else if (heavy != 0) hsinHeavy(p, heavy);
            else if (finisherStart >= 0 || !p.echo(0)) p.normalClick();
            cycle(p, cycle);
        }
    }

    private boolean hsinLib(Port p, boolean afterHeavy) throws Exception {
        boolean second = afterHeavy || signal(p, Signal.HSIN_LIB2);
        if (!(second || signal(p, Signal.HSIN_LIB1)) || !p.available(LIBERATION)) return false;
        if (!p.liberation(-1, false, 0)) return false;
        if (second) lib2Cast = true;
        else if (finisherStart < 0) finisherStart = p.activeTimeMs();
        return true;
    }
    private boolean hsinHeavy(Port p, int type) throws Exception {
        boolean released = releaseHeavy(p, Signal.HSIN_H1, Signal.HSIN_H2, 1200, 250, false);
        if (released && type == 2) h2Completed = true;
        return released;
    }
    private void hsinEnhancedE(Port p) throws Exception {
        long start = p.realTimeMs(), lastSend = -1, goneSince = -1;
        while (elapsed(p.realTimeMs(), start) < 1500) {
            check(p); long now = p.realTimeMs();
            if (signal(p, Signal.ENHANCED_E)) {
                goneSince = -1;
                if (lastSend < 0 || elapsed(now, lastSend) >= 250) { p.tapResonance(); lastSend = now; }
            } else if (lastSend >= 0) {
                if (goneSince < 0) goneSince = now;
                else if (elapsed(now, goneSince) >= 250) { p.recordResonanceConfirmed(); return; }
            }
            tick(p, 50);
        }
    }

    private void galbrena(Port p) throws Exception {
        if (p.hasIntro()) {
            try {
                p.holdAttack(true); p.pause(1000); check(p);
                if (p.fastPerform()) return;
                p.pause(440);
            } finally { p.holdAttack(false); }
            p.dodge(600);
        } else if (signal(p, Signal.FLYING)) p.waitDown(true);
        p.echo(0);
        if (p.forteFull() && !p.fastPerform()) {
            p.resonance(true, 0);
            if (p.liberation(-1, false, 100)) normal(p, 1000);
        }
        if (signal(p, Signal.GALBRENA_RES) && !p.fastPerform()) {
            p.liberation(-1, false, 100); long start = p.realTimeMs();
            while (signal(p, Signal.GALBRENA_RES) && elapsed(p.realTimeMs(), start) < 10000) {
                if (signal(p, Signal.FLYING)) p.shorekeeperAutoDodge();
                check(p); p.normalClick(); tick(p, 100);
            }
            return;
        }
        normal(p, 1000); p.resonance(true, 0);
    }

    private void phrolova(Port p) throws Exception {
        lastLiberation = -1; specialPhrolova = false;
        boolean outro = p.hasIntro() && "Cantarella".equals(p.outroSource());
        if (p.hasIntro()) { resReady = false; normal(p, 1700); p.dodge(100); }
        if (signal(p, Signal.FLYING)) p.waitDown(true);
        if (phrolovaLib(p) || phrolovaHeavy(p)) return;
        if (phrolovaResReady(p) || resReady) {
            normal(p, 100); p.resonance(true, 0); normal(p, 100);
            waitPhrolovaResConsumed(p);
            if (!p.echo(1000)) p.dodge(100);
        }
        resReady = false; specialPhrolova = outro;
        long start = p.realTimeMs();
        while (outro ? elapsed(p.activeTimeMs(), activeStart) < 16000 : elapsed(p.realTimeMs(), start) < 4000) {
            check(p);
            if (phrolovaLib(p)) return;
            if (signal(p, Signal.FLYING)) p.shorekeeperAutoDodge();
            if (phrolovaHeavy(p)) return;
            if (phrolovaResReady(p) && elapsed(p.realTimeMs(), start) > 1000) {
                if (!outro) { resReady = true; break; }
                normal(p, 300);
                if (p.resonance(true, 0)) {
                    normal(p, 100); waitPhrolovaResConsumed(p);
                    if (!p.echo(1000)) p.dodge(100);
                }
            }
            p.normalClick(); tick(p, 20);
        }
    }
    private boolean phrolovaResReady(Port p) throws Exception {
        return specialPhrolova ? !signal(p, Signal.FLYING) && p.cooldownMs(RESONANCE) == 0 : p.available(RESONANCE);
    }
    private void waitPhrolovaResConsumed(Port p) throws Exception {
        long start = p.realTimeMs();
        while (phrolovaResReady(p) && elapsed(p.realTimeMs(), start) < 300) { p.normalClick(); tick(p, 20); }
    }
    private boolean phrolovaLib(Port p) throws Exception {
        check(p);
        if (!p.available(LIBERATION) || !p.liberation(-1, false, 0)) return false;
        lastLiberation = p.activeTimeMs(); return true;
    }
    private boolean phrolovaHeavy(Port p) throws Exception {
        if (!p.mouseForteFull() || !p.heavyMouseForte()) return false;
        long start = p.realTimeMs();
        do { if (phrolovaLib(p)) break; tick(p, 50); } while (elapsed(p.realTimeMs(), start) < 3000);
        return true;
    }

    int switchPriority(String currentCharacter, boolean hasIntro, long activeNow) {
        if (character.equals("Hiyuki") && hasIntro && ("Linnai".equals(currentCharacter) || "Lucilla".equals(currentCharacter)))
            return CombatTeam.MUST;
        if (character.equals("Phrolova")) {
            long since = lastLiberation < 0 ? Long.MAX_VALUE : elapsed(activeNow, lastLiberation);
            if (since > 14000 && hasIntro && "Cantarella".equals(currentCharacter)) return CombatTeam.MUST;
            if (since < 24000) return CombatTeam.NO;
        }
        return CombatTeam.NORMAL;
    }

    private void hiyuki(Port p) throws Exception {
        kendoCount = 0;
        if (p.hasIntro()) normal(p, 1000);
        long duration = p.hasIntro() && "Linnai".equals(p.outroSource()) ? 18000 : 16000;
        if (signal(p, Signal.LONG_ACTION) && p.cooldownMs(LIBERATION) <= 3500) hiyukiStandard(p, duration);
        if (signal(p, Signal.LONG_ACTION_2)) {
            if (hiyukiLib(p, duration) || (p.available(LIBERATION) && holdLiberation(p))) p.pause(500);
        }
    }
    private void hiyukiStandard(Port p, long duration) throws Exception {
        long deadline=wallStart+duration;
        while (signal(p, Signal.LONG_ACTION) && elapsed(p.activeTimeMs(), activeStart) < duration && p.realTimeMs()<deadline) {
            p.echo(0); p.resonance(false, 0);
            if (p.available(LIBERATION) && p.liberation(-1, false, 100)) return;
            if (p.mouseForteFull()) {
                p.dodgeClick(0); p.heavyMouseForte();
                waitAvailable(p, LIBERATION, Math.max(0,Math.min(16000,deadline-p.realTimeMs())), true);
                if (p.liberation(-1, false, 100)) return;
            } else p.normalClick(100);
            tick(p, 50);
        }
    }
    private boolean hiyukiLib(Port p, long duration) throws Exception {
        long start = p.activeTimeMs(), wall=p.realTimeMs();
        while (signal(p, Signal.LONG_ACTION_2) && elapsed(p.activeTimeMs(), start) < duration && elapsed(p.realTimeMs(),wall)<duration) {
            check(p); p.breakAction(); p.echo(0);
            boolean nearEnd = Math.max(elapsed(p.activeTimeMs(), start),elapsed(p.realTimeMs(),wall)) >= duration - 500;
            if (p.available(LIBERATION) && (kendoCount >= 4 || nearEnd) && holdLiberation(p)) return true;
            if (p.resonance(false, 0)) {
                if (nearEnd) break;
                normal(p, 300);
            } else if (signal(p, Signal.HIYUKI_HEAVY)) {
                if (nearEnd) break;
                if (waitLocked(p)) releaseHeavy(p, Signal.HIYUKI_HEAVY, Signal.HIYUKI_HEAVY, 2000, 0, false);
                if (waitAvailable(p, LIBERATION, 500, true) && waitLocked(p) && holdLiberation(p)) return true;
                kendoCount++; p.pause(100);
            } else if (signal(p, Signal.HIYUKI_LEFT)) {
                long left = p.realTimeMs();
                while (signal(p, Signal.HIYUKI_LEFT) && elapsed(p.realTimeMs(), left) < 3000) { p.normalClick(); tick(p, 20); }
                if (nearEnd) break;
                p.pause(100);
            } else if (signal(p, Signal.HIYUKI_RIGHT)) { p.dodgeClick(1000); p.pause(100); }
            else p.normalClick();
            tick(p, 50);
        }
        return p.available(LIBERATION) && holdLiberation(p);
    }
    private boolean waitLocked(Port p) throws Exception {
        long start = p.realTimeMs();
        do {
            if (signal(p, Signal.LONG_ACTION_2)) return true;
            p.normalClick(); tick(p, 50);
        } while (elapsed(p.realTimeMs(), start) < 2000);
        return false;
    }
    private boolean holdLiberation(Port p) throws Exception {
        if (!p.useLiberation()) return false;
        long start = p.realTimeMs(); boolean sent = false;
        try {
            while (p.partyHudVisible() && (p.available(LIBERATION) || p.cooldownMs(LIBERATION) <= 1500)
                    && elapsed(p.realTimeMs(), start) < 8000) {
                check(p); p.hold(LIBERATION, true); sent = true; tick(p, 50);
            }
        } finally { p.hold(LIBERATION, false); }
        // Unlike the PC helper's unconditional true, the mobile Port must prove the cast.
        return sent && p.confirmHeldLiberation(start, 3000);
    }

    private boolean releaseHeavy(Port p, Signal one, Signal two, long timeout, long settle, boolean freezeAware) throws Exception {
        // Only Qingxiao uses freeze-adjusted hold time; Hsin/Hiyuki use wall-bounded wait_until.
        long start = freezeAware ? p.activeTimeMs() : p.realTimeMs(), dark = -1;
        try {
            p.holdAttack(true);
            while (elapsed(freezeAware ? p.activeTimeMs() : p.realTimeMs(), start) < timeout) {
                check(p);
                if (signal(p, one) || (one != two && signal(p, two))) dark = -1;
                else {
                    if (dark < 0) dark = p.realTimeMs();
                    if (elapsed(p.realTimeMs(), dark) >= settle) return true;
                }
                tick(p, 50);
            }
            return false;
        } finally { p.holdAttack(false); }
    }
    private void holdFor(Port p, CombatTouchLayout.Control control, long ms) throws Exception {
        try {
            if (control == ATTACK) p.holdAttack(true); else p.hold(control, true);
            long start = p.realTimeMs();
            while (elapsed(p.realTimeMs(), start) < ms) tick(p, Math.min(50, ms - elapsed(p.realTimeMs(), start)));
        } finally { if (control == ATTACK) p.holdAttack(false); else p.hold(control, false); }
    }
    private boolean waitAvailable(Port p, CombatTouchLayout.Control control, long timeout, boolean normal) throws Exception {
        long start = p.realTimeMs();
        do {
            check(p); if (p.available(control)) return true;
            if (normal) p.normalClick();
            tick(p, 50);
        } while (elapsed(p.realTimeMs(), start) < timeout);
        return false;
    }
    private void normal(Port p, long ms) throws Exception { check(p); p.normalAttack(ms, 100, false); }
    private boolean signal(Port p, Signal signal) throws Exception { check(p); return p.signal(signal); }
    private void tick(Port p, long ms) throws Exception { check(p); p.pause(ms); p.poll(); check(p); }
    private void cycle(Port p, long start) throws Exception { check(p); p.cycleSleep(start); p.poll(); check(p); }
    private void check(Port p) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        p.requireActiveCharacter(character); p.checkCombat();
        if (elapsed(p.realTimeMs(), wallStart) >= 45000)
            throw new FieldLimit();
    }
    private static long elapsed(long now, long before) {
        if (now < before) throw new IllegalStateException("角色战斗时钟倒退");
        return now - before;
    }
}
