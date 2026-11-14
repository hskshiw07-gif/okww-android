package io.github.okww.weekly;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import static io.github.okww.weekly.BuiltinCombatCatalog.Role.*;

/** Builtin role/buff selector from BaseCombatTask; no fixed/workshop rotation overrides. */
final class CombatTeam {
    static final int NO = 0, NORMAL = 200, MUST = 400;
    final List<BuiltinCombatCatalog.Character> chars;
    final long[] switchedOut = {-1,-1,-1}, switchedIn = {-1,-1,-1}, fullConOut = {-1,-1,-1}, buffAt = {-1,-1,-1};
    final boolean[] intro = new boolean[3], subDpsIntro = new boolean[3];
    private long lastTime;

    CombatTeam(BuiltinCombatCatalog catalog, List<String> names) {
        if (names == null || names.size() != 3 || new java.util.HashSet<>(names).size() != 3)
            throw new IllegalArgumentException("需要三名不同的已核验角色");
        List<BuiltinCombatCatalog.Character> characters = new ArrayList<>();
        for (String name : names) characters.add(catalog.require(name));
        chars = java.util.Collections.unmodifiableList(characters);
    }
    boolean hasBuff(int index, long now) { return remaining(index, now) > 0; }
    long remaining(int index, long now) {
        validate(index, now);
        return buffAt[index] < 0 ? 0 : Math.max(0, chars.get(index).buffMs - (now - buffAt[index]));
    }
    boolean allTeammateBuffs(int current, long now) {
        validate(current, now);
        if (!intro[current]) return false;
        for (int i=0;i<3;i++) if (i != current && !hasBuff(i,now)) return false;
        return true;
    }
    void supportSkillConfirmed(int current, long now) { validate(current,now); buffAt[current]=now; }
    void forgetBuffTiming() {
        java.util.Arrays.fill(buffAt,-1);java.util.Arrays.fill(fullConOut,-1);
    }

    int choose(int current, boolean hasIntro, int[] priorities, long now) {
        validate(current,now);
        if (priorities == null || priorities.length != 3) throw new IllegalArgumentException("需要三名角色的内置切人优先级");
        List<Integer> candidates = new ArrayList<>(), locked = new ArrayList<>();
        int highest = NO;
        for (int i=0;i<3;i++) if (i != current && priorities[i] > NO) {
            if (chars.get(i).role == HEALER && fullConOut[i] >= 0 && now-fullConOut[i] < 16000) locked.add(i);
            else { candidates.add(i); highest = Math.max(highest, priorities[i]); }
        }
        if (candidates.isEmpty()) {
            if (!locked.isEmpty() && (chars.get(current).role != HEALER || hasIntro))
                return locked.stream().min(Comparator.comparingLong((Integer i) -> 16000-(now-fullConOut[i]))
                        .thenComparingLong(i -> switchedIn[i]).thenComparingInt(i -> i)).get();
            return current;
        }
        final int priority = highest;
        candidates.removeIf(i -> priorities[i] != priority);
        if (hasIntro) {
            if (highest >= MUST) return oldest(candidates);
            for (BuiltinCombatCatalog.Role role : new BuiltinCombatCatalog.Role[]{HEALER,SUB_DPS}) {
                List<Integer> unbuffed = select(candidates, i -> chars.get(i).role==role && chars.get(i).buffMs>0 && !hasBuff(i,now));
                if (!unbuffed.isEmpty()) return oldest(unbuffed);
            }
            for (BuiltinCombatCatalog.Role role : new BuiltinCombatCatalog.Role[]{MAIN_DPS,SUB_DPS,HEALER}) {
                List<Integer> matches = select(candidates,i -> chars.get(i).role==role);
                if (!matches.isEmpty()) return oldest(matches);
            }
        }
        List<Integer> ready = select(candidates,i -> switchedOut[i]<0 || now-switchedOut[i]>1000);
        if (!ready.isEmpty()) candidates=ready;
        if (chars.get(current).role==MAIN_DPS) {
            List<Integer> supports = select(candidates,i -> chars.get(i).role!=MAIN_DPS && chars.get(i).buffMs>0);
            if (!supports.isEmpty()) return supports.stream().min(Comparator.comparingLong((Integer i)->remaining(i,now))
                    .thenComparingLong(i->switchedIn[i]).thenComparingInt(i->i)).get();
        }
        if (chars.get(current).role!=MAIN_DPS && chars.get(current).buffMs>0) {
            List<Integer> supports = select(candidates,i->chars.get(i).role!=MAIN_DPS && chars.get(i).buffMs>0 && !hasBuff(i,now));
            if (!supports.isEmpty()) return oldest(supports);
        }
        List<Integer> main = select(candidates,i->chars.get(i).role==MAIN_DPS);
        if (chars.get(current).role!=MAIN_DPS && !main.isEmpty()) return oldest(main);
        for (BuiltinCombatCatalog.Role role : new BuiltinCombatCatalog.Role[]{HEALER,SUB_DPS}) {
            List<Integer> unbuffed = select(candidates,i->chars.get(i).role==role && chars.get(i).buffMs>0 && !hasBuff(i,now));
            if (!unbuffed.isEmpty()) return oldest(unbuffed);
        }
        return !main.isEmpty() ? oldest(main) : oldest(candidates);
    }
    void switchConfirmed(int source, int target, boolean fullConcerto, long now) {
        validate(source,now); validate(target,now);
        if (source==target) throw new IllegalArgumentException("切人目标与当前相同");
        intro[source]=false; subDpsIntro[source]=false;
        switchedOut[source]=now; switchedIn[target]=now;
        intro[target]=fullConcerto; subDpsIntro[target]=fullConcerto && chars.get(source).role==SUB_DPS;
        if (fullConcerto) { fullConOut[source]=now; if (chars.get(source).buffMs>0) buffAt[source]=now; }
    }
    private List<Integer> select(List<Integer> candidates, java.util.function.IntPredicate condition) {
        List<Integer> selected = new ArrayList<>(); for (int i:candidates) if(condition.test(i)) selected.add(i); return selected;
    }
    private int oldest(List<Integer> candidates) {
        return candidates.stream().min(Comparator.comparingLong((Integer i)->switchedIn[i]).thenComparingInt(i->i)).get();
    }
    private void validate(int index,long now) {
        if(index<0||index>=3||now<lastTime) throw new IllegalArgumentException("角色槽位或战斗时钟异常");
        lastTime=now;
    }
}
