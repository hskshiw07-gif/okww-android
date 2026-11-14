package io.github.okww.weekly;

import java.util.HashSet;
import java.util.List;

/** Mobile shows two standby avatars, not PC's three numeric slots. Confirm the missing team member twice. */
final class ActiveCharacterRecognizer {
    static final class State {
        final int active;
        final int[] standby;
        final float[] x, y;
        State(int active, int[] standby, float[] x, float[] y) {
            this.active = active; this.standby = standby; this.x = x; this.y = y;
        }
    }
    private final List<String> team;
    private int candidate = -1;
    private long lastFrame = -1;
    ActiveCharacterRecognizer(List<String> team) {
        if (team == null || team.size() != 3 || new HashSet<>(team).size() != 3 || team.contains(null))
            throw new IllegalArgumentException("需要三个不同的已确认队伍角色");
        this.team = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(team));
    }
    State observe(PortraitMatcher.Match first, PortraitMatcher.Match second, int width, int height,
                  long capturedAt, long now, boolean gameHud) {
        boolean fresh = capturedAt >= 0 && capturedAt > lastFrame && now >= capturedAt && now - capturedAt <= 1500;
        boolean consecutive = lastFrame >= 0 && capturedAt - lastFrame <= 2500;
        if (fresh) lastFrame = capturedAt;
        if (!fresh || !gameHud || width != 2400 || height != 1080 || first == null || second == null
                || !first.reliable() || !second.reliable() || first.character.equals(second.character)) {
            candidate = -1; return null;
        }
        int a = team.indexOf(first.character), b = team.indexOf(second.character);
        if (a < 0 || b < 0) { candidate = -1; return null; }
        int current = 3 - a - b;
        int old = candidate; candidate = current;
        return consecutive && old == current ? new State(current, new int[]{a, b},
                new float[]{first.x, second.x}, new float[]{first.y, second.y}) : null;
    }
}
