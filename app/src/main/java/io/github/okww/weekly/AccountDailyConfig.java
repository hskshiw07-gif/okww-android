package io.github.okww.weekly;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Multi-account UID (`特征码`) to daily Tacet Suppression index (`无音区编号`, 1-based)
 * and character resonance-chain map. User-specific defaults are intentionally not bundled.
 */
final class AccountDailyConfig {
    static final class Profile {
        final String uid;
        final String accountMask;
        final int tacetIndex; // 1-based index in 素材获取 -> 无音清剿
        final Map<String, Integer> characterChains;

        Profile(String uid, String accountMask, int tacetIndex, Map<String, Integer> characterChains) {
            if (uid == null || !uid.matches("\\d{9}"))
                throw new IllegalArgumentException("无效特征码：" + uid);
            if (tacetIndex < 1 || tacetIndex > 21)
                throw new IllegalArgumentException("无音区编号越界：" + tacetIndex);
            this.uid = uid;
            this.accountMask = accountMask;
            this.tacetIndex = tacetIndex;
            this.characterChains = Collections.unmodifiableMap(new LinkedHashMap<>(characterChains));
        }

        String summary() {
            return uid + " (" + accountMask + ") → 无音区#" + tacetIndex;
        }
    }

    private static final Pattern UID_PATTERN = Pattern.compile(
            "(?:特征码|特[拉物征]码|UID)[:：.\\s]*([0-9TtOoIl!]{9})(?![0-9])");
    private static final Pattern NINE_DIGITS = Pattern.compile("(?<!\\d)(\\d{9})(?!\\d)");
    private static final Map<String, Profile> PROFILES = Collections.emptyMap();

    static Profile profileForUid(String uid) {
        return uid == null ? null : PROFILES.get(uid);
    }

    static Map<String, Profile> allProfiles() {
        return PROFILES;
    }

    /**
     * Extracts the 9-digit UID from either the Terminal profile card or the bottom-right HUD watermark.
     */
    static String detectUid(GardenRules.Screen screen) {
        if (screen == null) return null;
        // 1. Check Terminal profile card (.15f, .32f, .42f, .46f) and bottom-right watermark (.76f, .92f, 1f, 1f)
        String[] regions = {
                screen.text(.15f, .32f, .42f, .46f),
                screen.text(.76f, .92f, 1f, 1f)
        };
        return parseUidFromText(String.join("|", regions));
    }

    static String parseUidFromText(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        raw = GardenRules.normalize(raw);
        Set<String> candidates = new HashSet<>();
        Matcher tagged = UID_PATTERN.matcher(raw);
        while (tagged.find()) {
            String candidate = normalizeDigits(tagged.group(1));
            if (candidate.matches("\\d{9}")) candidates.add(candidate);
        }
        Matcher plain = NINE_DIGITS.matcher(raw);
        while (plain.find()) candidates.add(plain.group(1));
        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    private static String normalizeDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 'T' || c == 't' || c == 'I' || c == 'l' || c == '!') sb.append('1');
            else if (c == 'O' || c == 'o') sb.append('0');
            else sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Given the sorted list of "前往" buttons on the 无音清剿 page, selects the button or scrollbar tap
     * for the target 1-based Tacet Suppression index.
     */
    static float[] selectTacetTarget(List<GardenRules.Text> proceedButtons, int tacetIndex, boolean scrolled) {
        if (tacetIndex < 1 || tacetIndex > 21)
            throw new IllegalArgumentException("无音区编号越界：" + tacetIndex);
        if (tacetIndex <= 4) {
            if (proceedButtons.size() < tacetIndex) return null;
            GardenRules.Text btn = proceedButtons.get(tacetIndex - 1);
            return new float[]{btn.x(), btn.y(), 0f}; // 0f = direct proceed tap
        }
        // shortcut: original PC scrollbar arithmetic only; mobile caller blocks this until screen calibration exists.
        if (!scrolled) {
            int[] structure = {4, 5, 5, 7};
            int totalNumber = 21;
            int currentSum = 0, crossCount = 0;
            for (int s : structure) {
                currentSum += s;
                if (tacetIndex > currentSum) crossCount++;
                else break;
            }
            float barTop = 424f / 2160f;
            float barBottom = 0.8806f;
            float headerH = 0.028f;
            float containerH = barBottom - barTop - (structure.length - 1) * headerH;
            float itemH = containerH / totalNumber;
            float clickY = Math.min(barTop + itemH * tacetIndex + crossCount * headerH, barBottom);
            return new float[]{0.9730f, clickY, 1f}; // 1f = scrollbar tap
        }
        if (proceedButtons.isEmpty()) return null;
        GardenRules.Text bottom = proceedButtons.get(proceedButtons.size() - 1);
        return new float[]{bottom.x(), bottom.y(), 0f};
    }
}
