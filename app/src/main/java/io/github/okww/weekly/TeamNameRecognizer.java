package io.github.okww.weekly;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Conservative OCR path for a calibrated team-setup screen, not an uncalibrated HUD portrait matcher. */
final class TeamNameRecognizer {
    static final class Region {
        final float left, top, right, bottom;
        Region(float left, float top, float right, float bottom) {
            if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(right) || !Float.isFinite(bottom)
                    || left < 0 || top < 0 || right > 1 || bottom > 1 || left >= right || top >= bottom)
                throw new IllegalArgumentException("无效队伍名称区域");
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        boolean contains(GardenRules.Text word) {
            return word.x() >= left && word.x() <= right && word.y() >= top && word.y() <= bottom;
        }
    }
    private final Map<String, String> names;
    private final int width, height;
    private final Region[] slots;
    private List<String> candidate;
    private long lastFrame = -1;

    static TeamNameRecognizer forXiaomi13Pro(Reader catalog, int width, int height) throws IOException {
        if (width != 2400 || height != 1080) throw new IllegalStateException("编队名称目前只标定了小米13 Pro 2400×1080布局");
        return new TeamNameRecognizer(catalog, width, height,
                new Region(.29f, .76f, .39f, .82f), new Region(.53f, .76f, .64f, .82f),
                new Region(.78f, .76f, .88f, .82f));
    }

    TeamNameRecognizer(Reader catalog, int width, int height, Region... slots) throws IOException {
        if (width <= height || height <= 0 || slots == null || slots.length != 3)
            throw new IllegalArgumentException("需要横屏尺寸和三个已标定角色名称区域");
        this.width = width; this.height = height; this.slots = slots.clone();
        for (int i = 0; i < this.slots.length; i++) {
            if (this.slots[i] == null) throw new IllegalArgumentException("角色区域缺失");
            for (int j = 0; j < i; j++) {
                Region a = this.slots[i], b = this.slots[j];
                if (a.left <= b.right && a.right >= b.left && a.top <= b.bottom && a.bottom >= b.top)
                    throw new IllegalArgumentException("角色名称区域重叠");
            }
        }
        names = new HashMap<>();
        BufferedReader input = new BufferedReader(catalog);
        String line;
        while ((line = input.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] fields = line.split("\t", -1);
            if (fields.length < 2 || !fields[0].matches("[A-Za-z][A-Za-z0-9]*"))
                throw new IllegalArgumentException("内置角色目录格式错误");
            for (int i = 1; i < fields.length; i++) {
                String alias = normalize(fields[i]);
                if (alias.isEmpty()) throw new IllegalArgumentException("角色名为空");
                String old = names.put(alias, fields[0]);
                if (old != null && !old.equals(fields[0])) throw new IllegalArgumentException("角色名存在歧义：" + fields[i]);
            }
        }
        if (names.isEmpty()) throw new IllegalArgumentException("内置角色目录为空");
    }

    /** Returns slot order only after two distinct fresh frames. It never reuses a previous team on failure. */
    List<String> observe(GardenRules.Screen screen, int frameWidth, int frameHeight, long capturedAt, long now) {
        if (frameWidth != width || frameHeight != height) {
            candidate = null;
            throw new IllegalStateException("队伍名称标定与画面尺寸不符");
        }
        if (capturedAt < 0 || now < capturedAt || now - capturedAt > 2000 || capturedAt <= lastFrame) {
            candidate = null; return null;
        }
        if (lastFrame >= 0 && capturedAt - lastFrame > 3000) candidate = null;
        lastFrame = capturedAt;
        boolean deployControl = false;
        for (GardenRules.Text word : screen.words)
            if (word.value.matches("出战|出戰|快速[编編骗]队")) deployControl = true;
        boolean entry=screen.one(".?队伍[1-9][0-9]*.?",0,0,.25f,.15f)!=null && screen.one("开启挑战",.80f,.86f,.93f,.96f)!=null;
        boolean normal=screen.one("编队|队伍配置")!=null
                || screen.one(".?队伍[1-9][0-9]*.?",0,0,.25f,.15f)!=null
                &&screen.one("已出战|出战",.80f,.86f,.93f,.96f)!=null;
        if (!(normal || entry) || !deployControl) {
            candidate = null; return null;
        }
        List<String> team = new ArrayList<>();
        for (Region slot : slots) {
            Set<String> found = new HashSet<>();
            for (GardenRules.Text word : screen.words) if (slot.contains(word)) {
                String character = names.get(normalize(word.value));
                if (character != null) found.add(character);
            }
            if (found.size() != 1) { candidate = null; return null; }
            team.add(found.iterator().next());
        }
        if (new HashSet<>(team).size() != 3) { candidate = null; return null; }
        List<String> previous = candidate;
        candidate = Collections.unmodifiableList(team);
        return candidate.equals(previous) ? candidate : null;
    }

    private static String normalize(String text) {
        return GardenRules.normalize(text)
                .replaceFirst("^、", "") // Recorded name-badge border; the remaining alias must still match in full.
                .replaceAll("^[「『(（|丨]+|[」』)）|丨]+$", "").toLowerCase(Locale.ROOT);
    }
}
