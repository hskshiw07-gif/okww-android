package io.github.okww.weekly;

/** Monotonic bounds and two-observation confirmation, independent of clocks or watermark OCR. */
final class GardenRunGuard {
    private final long started;
    private long lastProgress;
    private String progress = "";
    private GardenRules.Action candidate;
    private int attempts;

    GardenRunGuard(long now) { started = now; lastProgress = now; }

    boolean ready(GardenRules.Screen screen, GardenRules.Action action, long now) {
        if (now - started > 40 * 60 * 1000L) throw new IllegalStateException("到达40分钟上限，未记为完成");
        String key = GardenRules.progressKey(screen, action);
        if (!progress.equals(key)) {
            progress = key;
            lastProgress = now;
            attempts = 0;
            candidate = null;
        }
        long timeout = action.scene.equals("game-loading") || action.scene.equals("daily-travel-loading")
                || action.scene.equals("daily-tacet-loading") ? 90000 : 25000;
        if (now - lastProgress > timeout) throw new IllegalStateException("界面" + timeout / 1000 + "秒无有效进展：" + action.scene);
        if (action.kind == GardenRules.Kind.WAIT) { candidate = null; return false; }
        if (candidate == null || candidate.kind != action.kind || !candidate.scene.equals(action.scene)
                || Math.abs(candidate.x - action.x) > .015f || Math.abs(candidate.y - action.y) > .015f) {
            candidate = action;
            return false;
        }
        candidate = null;
        if (++attempts > 4) throw new IllegalStateException("相同场景四次操作未推进：" + action.scene);
        return true;
    }
}
