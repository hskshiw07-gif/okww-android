package io.github.okww.weekly;

import java.util.regex.Pattern;

/** Evidence for the no-waveplate, same-arena echo loop. */
final class EchoFarmRules {
    static final String BOSS_KEY = "echoFarmBoss";
    private static final String ABSORB = "(?i:吸收|Absorb)";
    private static final String CLAIM = "(?i:领取奖励|領取獎勵|Claim(?:Rewards?)?)";
    private static final Pattern MODAL = Pattern.compile("(?:^|\\|)(?:提示|确认|取消)\\|");
    private static final String LEVEL = "(?i:Lv[.]?[0-9]{1,3})";
    enum Step { WAIT, FIGHT, ABSORB, ABSORB_CHECKED }
    private enum Phase { BOSS, DROP, VERIFY }

    private final String bossLine;
    final String uid;
    private Phase phase = Phase.BOSS;
    private long lastFrame = -1, candidateAt = -1;
    private Step candidate = Step.WAIT;
    private GardenRules.Text button;
    private boolean rewardTextSeen;

    EchoFarmRules(String boss, String uid) {
        String name = GardenRules.normalize(boss);
        if (name.isEmpty() || name.length() > 32)
            throw new IllegalArgumentException("请输入首领血条上显示的完整名称");
        this.uid = uid;
        String literal = Pattern.quote(name);
        bossLine = "(?:" + LEVEL + "[:：]?)?" + literal + "(?:[:：]?" + LEVEL + ")?";
    }

    static boolean blocked(GardenRules.Screen s) {
        if (s.automationPanelVisible() || s.has("补充波片|使用波片|消耗波片|波片不足|兑换波片|挑战失败|复苏|复活|购买|背包已满|声骸已满")
                || MODAL.matcher(s.text(.25f, .25f, .75f, .82f)).find()) return true;
        for (GardenRules.Text word : s.words)
            if (word.value.matches(CLAIM) && (word.x() < .59f || word.x() > .80f || word.y() < .34f || word.y() > .62f))
                return true;
        return false;
    }

    boolean hud(GardenRules.Screen s) {
        return uid != null && uid.equals(AccountDailyConfig.detectUid(s))
                && s.aspect > 2.18f && s.aspect < 2.26f && CombatHud.mobileStatus(s) && !blocked(s);
    }

    boolean bossLabel(GardenRules.Screen s) {
        return s.hasOne(bossLine, .33f, .005f, .67f, .10f);
    }

    static boolean healthBar(PortraitMatcher.Pixels p) {
        if (p.width != 2400 || p.height != 1080) return false;
        // shortcut: PC boss-bar colour and top HUD region; confirm geometry on phone before expanding supported layouts.
        int rows = 0;
        for (int y = 29; y < 100; y++) {
            int run = 0;
            boolean filled = false;
            for (int x = 790; x < 1610; x++) {
                int c = p.argb[y * p.width + x], r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
                run = r >= 235 && g >= 30 && g <= 185 && b <= 85 ? run + 1 : 0;
                if (run >= 16) { filled = true; break; }
            }
            rows = filled ? rows + 1 : 0;
            if (rows >= 3) return true;
        }
        return false;
    }

    GardenRules.Text prompt(GardenRules.Screen s) {
        if (!hud(s)) return null;
        // shortcut: use the mobile interaction column, not the PC F-key location; widen only from phone fixtures.
        GardenRules.Text absorb = s.one(ABSORB, .63f, .37f, .74f, .59f);
        if (absorb == null) return null;
        GardenRules.Text claim = s.one(CLAIM, .59f, .34f, .80f, .62f);
        return claim != null && Math.abs(absorb.y() - claim.y()) < .05f ? null : absorb;
    }

    Step observe(GardenRules.Screen s, boolean bar, long captured, long now) {
        long previous = lastFrame;
        if (captured > previous && captured <= now) lastFrame = captured;
        if (captured < 0 || captured <= previous || now < captured || now - captured > 1500 || !hud(s)) {
            candidate = Step.WAIT; button = null; return Step.WAIT;
        }
        GardenRules.Text target = phase == Phase.BOSS ? null : prompt(s);
        boolean label = bossLabel(s);
        if (phase == Phase.VERIFY && s.hasOne("获得声骸|獲得聲骸", .30f, .08f, .70f, .35f)) rewardTextSeen = true;
        Step next = switch (phase) {
            case BOSS -> label && bar ? Step.FIGHT : Step.WAIT;
            case DROP -> target != null && !label && !bar ? Step.ABSORB : Step.WAIT;
            case VERIFY -> !label && !bar && !s.has("(?i)(?:^|\\|)(?:吸收|Absorb)(?:\\||$)") ? Step.ABSORB_CHECKED : Step.WAIT;
        };
        boolean stable = candidate == next && captured - candidateAt <= 2500
                && (target == null || button != null && Math.abs(target.x() - button.x()) < .015f
                        && Math.abs(target.y() - button.y()) < .015f);
        candidate = next; candidateAt = captured; button = target;
        return stable ? next : Step.WAIT;
    }

    GardenRules.Text button() { return button; }
    boolean rewardTextSeen() { return rewardTextSeen; }
    void combatEnded() { phase = Phase.DROP; candidate = Step.WAIT; }
    void absorbSent() { phase = Phase.VERIFY; candidate = Step.WAIT; rewardTextSeen = false; }
    void awaitRespawn() { phase = Phase.BOSS; candidate = Step.WAIT; }
}
