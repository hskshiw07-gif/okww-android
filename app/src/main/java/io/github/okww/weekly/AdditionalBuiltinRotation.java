package io.github.okww.weekly;

import java.util.Set;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import static io.github.okww.weekly.CombatTouchLayout.Control.*;

/** Original src/char control flow; helpers still require a calibrated, game-confirming mobile Port. */
final class AdditionalBuiltinRotation {
    static final Set<String> CHARACTERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "Aalto", "Baizhi", "Calcharo", "Chixia", "Danjin", "Jianxin", "Lingyang", "Lumi",
            "Mortefi", "Sanhua", "Taoqi", "Xiangliyao", "Yangyang", "Yinlin", "Youhu", "Yuanwu")));

    interface Port extends TeamBuiltinRotation.Port {
        void requireActiveCharacter(String canonicalName) throws Exception;
        // Retain original helper options; never replace animation/concerto checks with unconditional taps.
        boolean resonance(boolean sendNormal, long timeoutMs) throws Exception; // 0 = original helper default timeout.
        boolean liberation(double concertoLimit, boolean sendNormal, long waitIfCdReadyMs) throws Exception; // -1 = no concerto limit.
        boolean echo(long timeoutMs) throws Exception;
        void waitDown(boolean sendNormal) throws Exception;
        void waitPartyHudOptional(long timeoutMs) throws Exception;
        void normalAttack(long durationMs, long intervalMs, boolean interruptOnResonance) throws Exception;
        void normalTap() throws Exception;
        void normalClick(long intervalMs) throws Exception;
        void repeatResonance(long durationMs, long intervalMs) throws Exception;
        boolean mouseForteFull() throws Exception;
        boolean heavyMouseForte() throws Exception; // Hold until mouse-forte disappears or 2s; always release on failure.
        void defaultHeavyAttack() throws Exception; // Original BaseChar default: 600ms, with cancellation-safe release.
    }

    private final String character;
    private long liberationAt = -1;
    private long wallStart;

    AdditionalBuiltinRotation(String character) {
        if (!CHARACTERS.contains(character == null ? "" : character))
            throw new IllegalArgumentException("该角色尚无安卓控制流：" + character);
        this.character = character;
    }

    void run(Port p) throws Exception {
        wallStart = p.realTimeMs();
        check(p);
        switch (character) {
            // These two PC classes actually inherit BaseChar.do_perform; not a fallback for unknown characters.
            case "Chixia", "Youhu" -> base(p);
            case "Calcharo" -> {
                if (p.hasIntro()) { p.pause(1000); check(p); p.waitPartyHudOptional(3000); }
                base(p);
            }
            case "Baizhi" -> {
                if (p.hasIntro()) normal(p, 1200, true);
                lib(p, 1, false, 100); res(p, true, 0); echo(p, 1000);
            }
            case "Jianxin" -> {
                if (p.hasIntro()) normal(p, 1000, false);
                lib(p, -1, false, 100);
                if (p.available(RESONANCE)) res(p, true, 0);
                if (p.available(ECHO)) echo(p, 1000);
            }
            case "Mortefi" -> {
                p.waitDown(true);
                boolean liberated = lib(p, -1, false, 100);
                res(p, true, 0); echo(p, 1000);
                if (!liberated) lib(p, -1, false, 1000);
            }
            case "Taoqi" -> {
                if (p.hasIntro()) { p.waitDown(true); normal(p, 2500, false); }
                else { lib(p, -1, false, 100); res(p, true, 0); echo(p, 1000); }
            }
            case "Yuanwu" -> {
                if (lib(p, 1, false, 100)) res(p, true, 0);
                else if (p.hasIntro()) normal(p, 1200, false);
                else { res(p, true, 0); echo(p, 1000); }
            }
            case "Danjin" -> danjin(p);
            case "Yinlin" -> yinlin(p);
            case "Sanhua" -> sanhua(p);
            case "Aalto" -> aalto(p);
            case "Yangyang" -> yangyang(p);
            case "Lingyang" -> lingyang(p);
            case "Lumi" -> lumi(p);
            case "Xiangliyao" -> xiangliyao(p);
            default -> throw new IllegalStateException("未分派内置角色");
        }
        check(p); p.switchNext();
    }

    private void base(Port p) throws Exception {
        p.waitIntro(1200); echo(p, 0); lib(p, -1, false, 100);
        if (!res(p, true, 0)) { check(p); p.heavyMouseForte(); }
    }

    private void danjin(Port p) throws Exception {
        if (lib(p, -1, false, 100)) { p.pause(1200); echo(p, 2000); return; }
        if (p.forteFull() && p.hasIntro()) {
            p.heavyAttack(800); p.pause(200); check(p); p.normalTap(); p.pause(100); return;
        }
        if (p.hasIntro()) normal(p, 1100, false);
        else { p.waitDown(true); normal(p, 400, false); }
        check(p); p.repeatResonance(1100, 200);
    }

    private void yinlin(Port p) throws Exception {
        if (p.hasIntro()) p.pause(400);
        boolean liberation = lib(p, -1, false, 100);
        if (p.mouseForteFull()) {
            if (!p.hasIntro() && !liberation) p.normalTap();
            check(p); p.defaultHeavyAttack(); p.pause(400);
        } else if (res(p, false, 0)) p.pause(100);
        else if (p.available(ECHO)) echo(p, 1000);
        else { check(p); p.defaultHeavyAttack(); }
    }

    private void sanhua(Port p) throws Exception {
        boolean liberated = false;
        long duration = 850;
        p.pause(20);
        long start = p.activeTimeMs();
        try {
            p.holdAttack(true); p.waitDown(false);
            if (lib(p, -1, false, 100)) { liberated = true; duration += 100; p.pause(150); }
            else if (p.available(RESONANCE)) {
                p.holdAttack(false); res(p, false, 0); start = p.activeTimeMs();
                p.holdAttack(true); p.pause(100);
            }
            long remaining = duration - elapsed(p.activeTimeMs(), start);
            if (remaining > 0) { check(p); p.pause(remaining); }
        } finally { p.holdAttack(false); }
        p.pause(800);
        if (liberated) { res(p, false, 0); p.pause(300); }
        if (p.concertoFull()) echo(p, 1000);
    }

    private void aalto(Port p) throws Exception {
        if (p.hasIntro()) normal(p, 400, false);
        p.waitDown(true);
        long start = p.activeTimeMs();
        boolean skill = res(p, true, 1000);
        echo(p, 0);
        boolean liberation = lib(p, -1, true, 0);
        while (elapsed(p.activeTimeMs(), start) < 4500) {
            check(p);
            if (p.concertoFull()) break;
            if (!skill && p.available(RESONANCE)) { skill = res(p, true, 1000); continue; }
            if (!liberation && p.available(LIBERATION)) { liberation = lib(p, -1, true, 0); continue; }
            check(p); p.normalClick(100); p.pause(50);
        }
    }

    private void yangyang(Port p) throws Exception {
        if (p.hasIntro()) normal(p, 600, false);
        p.waitDown(true); res(p, true, 1000);
        if (p.mouseForteFull()) { check(p); p.heavyMouseForte(); }
        echo(p, 0);
        if (lib(p, -1, true, 0)) normal(p, 500, false);
        else normal(p, 800, true);
    }

    private void lingyang(Port p) throws Exception {
        if (p.hasIntro()) normal(p, 800, false);
        p.waitDown(true); echo(p, 0);
        if (p.available(LIBERATION) && lib(p, -1, true, 100)) normal(p, 1000, false);
        long start = p.activeTimeMs();
        while (elapsed(p.activeTimeMs(), start) < 8000) {
            check(p);
            if (p.concertoFull() && elapsed(p.activeTimeMs(), start) > 2000) break;
            if (p.mouseForteFull()) {
                if (p.heavyMouseForte()) normal(p, 400, false);
                continue;
            }
            if (p.available(RESONANCE) && res(p, true, 1000)) { normal(p, 350, false); continue; }
            if (p.available(LIBERATION)) { lib(p, -1, true, 0); continue; }
            check(p); p.normalClick(100); p.pause(50);
        }
    }

    private void lumi(Port p) throws Exception {
        if (p.hasIntro()) normal(p, 500, false);
        p.waitDown(true);
        long start = p.activeTimeMs();
        echo(p, 0); lib(p, -1, true, 0);
        while (elapsed(p.activeTimeMs(), start) < 5000) {
            check(p);
            if (p.concertoFull() && elapsed(p.activeTimeMs(), start) > 1500) break;
            if (p.available(RESONANCE) && res(p, true, 1000)) { normal(p, 400, false); continue; }
            if (p.mouseForteFull()) { p.heavyMouseForte(); continue; }
            if (p.available(LIBERATION)) { lib(p, -1, true, 0); continue; }
            check(p); p.normalClick(100); p.pause(50);
        }
    }

    private void xiangliyao(Port p) throws Exception {
        p.waitDown(true);
        if (lib(p, -1, false, 100)) liberationAt = p.activeTimeMs();
        if (liberationAt >= 0 && elapsed(p.activeTimeMs(), liberationAt) < 25000) {
            while (!res(p, true, 0)) normal(p, 1000, false);
        } else if (p.available(ECHO)) echo(p, 1000);
        else res(p, true, 0);
    }

    private void normal(Port p, long ms, boolean interrupt) throws Exception {
        check(p); p.normalAttack(ms, 100, interrupt);
    }
    private boolean res(Port p, boolean normal, long timeout) throws Exception {
        check(p); return p.resonance(normal, timeout);
    }
    private boolean lib(Port p, double limit, boolean normal, long wait) throws Exception {
        check(p); return p.liberation(limit, normal, wait);
    }
    private void echo(Port p, long timeout) throws Exception { check(p); p.echo(timeout); }
    private void check(Port p) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        p.checkCombat();
        if (elapsed(p.realTimeMs(), wallStart) >= 30000)
            throw new IllegalStateException("单次角色控制流超过30秒，停止而非无限等待");
    }
    private static long elapsed(long now, long before) {
        if (now < before) throw new IllegalStateException("战斗时钟倒退");
        return now - before;
    }
}
