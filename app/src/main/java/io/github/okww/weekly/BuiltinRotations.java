package io.github.okww.weekly;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Select by canonical recognized identity; retain each character's cooldown state across switches. */
final class BuiltinRotations {
    private final Map<String, Object> rotations = new LinkedHashMap<>();

    BuiltinRotations(BuiltinCombatCatalog catalog, List<String> confirmedTeam) {
        List<String> missing = missing(catalog, confirmedTeam);
        if (!missing.isEmpty()) throw new IllegalArgumentException("以下内置角色控制流尚未迁移：" + String.join("、", missing));
        for (String name : confirmedTeam) {
            Object rotation = PriorityBuiltinRotation.CHARACTERS.contains(name) ? new PriorityBuiltinRotation(name)
                    : AdditionalBuiltinRotation.CHARACTERS.contains(name) ? new AdditionalBuiltinRotation(name)
                    : TeamBuiltinRotation.supported(name) ? new TeamBuiltinRotation(name) : new BuiltinRotation(name);
            rotations.put(name, rotation);
        }
    }

    static List<String> missing(BuiltinCombatCatalog catalog, List<String> confirmedTeam) {
        if (catalog == null || confirmedTeam == null || confirmedTeam.size() != 3 || confirmedTeam.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(confirmedTeam).size() != 3)
            throw new IllegalArgumentException("需要本轮识别确认的三个不同内置角色");
        List<String> result = new ArrayList<>();
        for (String name : confirmedTeam) {
            catalog.require(name);
            if (!supported(name)) result.add(name);
        }
        return result;
    }

    static boolean supported(String name) {
        return name != null && !name.equals("BaseChar") && (BuiltinRotation.CHARACTERS.contains(name)
                || TeamBuiltinRotation.supported(name) || AdditionalBuiltinRotation.CHARACTERS.contains(name)
                || PriorityBuiltinRotation.CHARACTERS.contains(name));
    }

    void run(String freshActiveCharacter, AdditionalBuiltinRotation.Port port) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        Object rotation = rotations.get(freshActiveCharacter);
        if (rotation == null) throw new IllegalArgumentException("在场角色不属于本轮已确认队伍");
        // The Port must independently verify this identity against a fresh combat frame before every input.
        port.requireActiveCharacter(freshActiveCharacter);
        if (rotation instanceof PriorityBuiltinRotation) {
            if (!(port instanceof PriorityBuiltinRotation.Port))
                throw new IllegalStateException("该角色控制流已译，手机专属状态和触控尚未标定：" + freshActiveCharacter);
            ((PriorityBuiltinRotation) rotation).run((PriorityBuiltinRotation.Port) port);
        }
        else if (rotation instanceof AdditionalBuiltinRotation) ((AdditionalBuiltinRotation) rotation).run(port);
        else if (rotation instanceof TeamBuiltinRotation) ((TeamBuiltinRotation) rotation).run(port);
        else ((BuiltinRotation) rotation).run(port);
    }

    int[] priorities(String current, boolean intro, long activeNow) {
        if (!rotations.containsKey(current)) throw new IllegalArgumentException("当前角色不属于本轮队伍");
        int[] result = new int[rotations.size()]; int i = 0;
        for (Object rotation : rotations.values()) result[i++] = rotation instanceof PriorityBuiltinRotation
                ? ((PriorityBuiltinRotation) rotation).switchPriority(current, intro, activeNow) : CombatTeam.NORMAL;
        return result;
    }
}
