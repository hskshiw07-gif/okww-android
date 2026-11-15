package io.github.okww.weekly;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Visual policy ported from GardenTask, not WeeklyBossTask. Coordinates are screenshot fractions. */
final class GardenRules {
    static final String GAME_PACKAGE = "com.kurogame.mingchao";
    static final int TARGET = 6000;
    private static final String FROM = "夢遊園歷週達擇確認進階獲聲購買關閉繼續興趣啟暫離儲彈變數當擁樂劵開戲標對緣異註釋領鍵躍跡獎剩餘機頁總戰敗戦終圖據塢徵碼級誌換區勵無勦討傳雙單體補兌劑郵約務憑證沒";
    private static final String TO   = "梦游园历周达择确认进阶获声购买关闭继续兴趣启暂离储弹变数当拥乐券开戏标对缘异注释领键跃迹奖剩余机页总战败战终图据坞征码级志换区励无剿讨传双单体补兑剂邮约务凭证没";

    static String normalize(String text) {
        String value = Normalizer.normalize(text, Normalizer.Form.NFKC).replaceAll("\\s+", "")
                .replace('編', '编').replace('隊', '队');
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            int index = FROM.indexOf(c);
            out.append(index < 0 ? c : TO.charAt(index));
        }
        return out.toString();
    }

    static final class Text {
        final String value;
        final float left, top, right, bottom;
        Text(String text, float left, float top, float right, float bottom) {
            this.value = normalize(text);
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        float x() { return (left + right) / 2; }
        float y() { return (top + bottom) / 2; }
    }

    static final class Screen {
        final List<Text> words;
        final int[] cardRanks;
        final float aspect;
        Screen(List<Text> words, int[] cardRanks) { this(words, cardRanks, Float.NaN); }
        Screen(List<Text> words, int[] cardRanks, float aspect) {
            this.words = words; this.cardRanks = cardRanks; this.aspect = aspect;
        }

        String text(float left, float top, float right, float bottom) {
            StringBuilder result = new StringBuilder();
            for (Text word : words) if (inside(word, left, top, right, bottom)) result.append(word.value).append('|');
            return result.toString();
        }
        String all() { return text(0, 0, 1, 1); }
        boolean has(String regex) { return Pattern.compile(regex).matcher(all()).find(); }
        boolean hasOne(String regex,float left,float top,float right,float bottom) {
            Pattern pattern=Pattern.compile(regex);int count=0;
            for(Text word:words)if(inside(word,left,top,right,bottom)&&pattern.matcher(word.value).matches())count++;
            return count==1; // Ambiguous status is an unknown frame, not an ambiguous button to tap.
        }
        boolean automationPanelVisible() {
            return has("(?:^|\\|)保存现场(?:\\||$)") && has("(?:^|\\|)开跑(?:\\||$)")
                    && has("(?:^|\\|)识别(?:\\||$)");
        }
        Text one(String regex, float left, float top, float right, float bottom) {
            Text found = null;
            Pattern pattern = Pattern.compile(regex);
            for (Text word : words) if (inside(word, left, top, right, bottom) && pattern.matcher(word.value).matches()) {
                if (found != null) throw new IllegalStateException("按钮识别不唯一：" + regex);
                found = word;
            }
            return found;
        }
        Text one(String regex) { return one(regex, 0, 0, 1, 1); }
        private boolean inside(Text word, float left, float top, float right, float bottom) {
            return word.x() >= left && word.x() <= right && word.y() >= top && word.y() <= bottom;
        }
    }

    enum Kind { TAP, BACK, WAIT, DONE, STOP }
    static final class Action {
        final Kind kind;
        final String scene, description;
        final float x, y;
        Action(Kind kind, String scene, String description, float x, float y) {
            this.kind = kind; this.scene = scene; this.description = description; this.x = x; this.y = y;
        }
        static Action tap(String scene, Text word) {
            return new Action(Kind.TAP, scene, word.value, word.x(), word.y());
        }
        static Action waitFor(String scene, String reason) { return new Action(Kind.WAIT, scene, reason, 0, 0); }
        static Action stop(String reason) { return new Action(Kind.STOP, "stopped", reason, 0, 0); }
    }

    private boolean selectedCard, selectedEvent, selectedBlessing, openingBlessing;
    private boolean returningAtCap;
    private boolean connecting;
    private int runs;
    private int rewardChest;
    private boolean awaitingMaxSpeed;
    private int speedTaps;
    private int maxSpeedFrames;

    /** Only label-bound scores count. A lone 6000 milestone is never completion evidence. */
    static Integer score(String text) {
        String value = normalize(text);
        if (!value.matches(".*本周游历值.*")) return null;
        if (value.matches(".*已达(?:到)?上限.*")) return TARGET;
        Matcher matcher = Pattern.compile("(?<!\\d)(\\d{1,4})/6000(?!\\d)").matcher(value);
        Set<Integer> scores = new HashSet<>();
        while (matcher.find()) {
            int current = Integer.parseInt(matcher.group(1));
            if (current > TARGET) return null;
            scores.add(current);
        }
        return scores.size() == 1 ? scores.iterator().next() : null;
    }

    static Integer entranceScore(Screen screen) {
        return score(screen.text(0, 0, .30f, .30f));
    }

    static Integer resultScore(Screen screen) {
        Text label = resultScoreLabel(screen);
        if (label == null) return null;
        float halfHeight = Math.max(.025f, (label.bottom - label.top) * .75f);
        return score(screen.text(.35f, label.y() - halfHeight, .88f, label.y() + halfHeight));
    }

    private static Text resultScoreLabel(Screen screen) {
        return screen.one(".*本周游历值", .35f, .30f, .65f, .74f);
    }

    /** Normalized left/top/right/bottom; only the observed Xiaomi 20:9 score layout is calibrated. */
    static float[] scoreCrop(Screen screen) {
        if (!(screen.aspect > 2.18f && screen.aspect < 2.26f)) return null;
        if (screen.has("挑战成功|挑战失败") && screen.one("返回首页|返回主页") != null) {
            Text label = resultScoreLabel(screen);
            if (label == null) return null;
            float halfHeight = Math.max(.025f, (label.bottom - label.top) * .75f);
            return new float[]{.699f, Math.max(.30f, label.y() - halfHeight),
                    .794f, Math.min(.74f, label.y() + halfHeight)};
        }
        if (screen.has("幻梦游园") && screen.one("开始游戏|[|丨]?继续游戏") != null && screen.has("本周游历值"))
            return new float[]{.084f, .175f, .169f, .24f};
        return null;
    }

    static Integer guideScore(Screen screen) {
        String region = screen.text(.07f, .80f, .29f, .96f);
        if (!region.contains("游历值")) return null;
        Matcher matcher = Pattern.compile("(?<!\\d)(\\d{1,4})(?!\\d)").matcher(region);
        Set<Integer> values = new HashSet<>();
        while (matcher.find()) values.add(Integer.valueOf(matcher.group(1)));
        return values.size() == 1 && values.iterator().next() <= TARGET ? values.iterator().next() : null;
    }

    Action next(Screen screen) {
        int previousMaxFrames = maxSpeedFrames;
        maxSpeedFrames = 0;
        if (screen.automationPanelVisible()) return Action.waitFor("capture-overlay-visible", "截图仍含浮窗，丢弃本帧并重新采集");
        Text rewardContinue = screen.one("点击空白处继续", .35f, .80f, .65f, 1);
        int rewardAmounts = 0;
        for (Text word : screen.words) if (word.y() > .35f && word.y() < .65f && word.value.matches("[xX×][0-9]+")) rewardAmounts++;
        if (rewardContinue != null && screen.one("获得|获得奖励", .4f, .15f, .6f, .35f) != null && rewardAmounts >= 2)
            return Action.tap("rewards-dismiss", rewardContinue);
        if (returningAtCap && rewardChest > 0 && screen.has("获得奖励|獲得奖励")) {
            Text close = screen.one(".*(?:点击|點擊).*(?:空白|任意).*关闭.*|.*(?:点击|點擊).*(?:空白|任意).*關闭.*");
            if (close != null) return Action.tap("rewards-dismiss", close);
            return Action.waitFor("rewards", "等待奖励弹窗的关闭提示，不猜确认位置");
        }
        if (screen.has("星声|星聲|结晶波片|結晶波片|单人挑战|领取奖励将消耗|唤取1次|唤取10次"))
            return Action.stop("不在幻梦游园流程或出现付费/波片界面，未点击");
        if (screen.has("名词注释|教程|教学"))
            return Action.waitFor("help", "检测到帮助弹窗，请保存现场以补充专用识别");

        Text connect = screen.one("点击连接", .35f, .80f, .65f, 1);
        if (connect != null && screen.has("(?i)CN_?Android_?Product") && screen.has("CADPA"))
            return Action.tap("game-connect", connect);

        Text retry = screen.one("重新挑战"), home = screen.one("返回首页|返回主页");
        Text endless = screen.one("[|丨「]?无尽挑战");
        if (endless != null && home != null && screen.has("挑战成功") && screen.has("本周游历值")) {
            resetChoices();
            Integer points = resultScore(screen);
            if (points == null) return Action.tap("result-verify-home", home);
            if (points == TARGET) { returningAtCap = true; return Action.tap("result-cap", home); }
            return Action.tap("result-endless", endless);
        }
        if (retry != null && home != null && screen.has("本周游历值")) {
            resetChoices();
            Integer points = resultScore(screen);
            if (points == null) return Action.tap("result-verify-home", home);
            if (points == TARGET) { returningAtCap = true; return Action.tap("result-cap", home); }
            if (runs >= 12) return Action.stop("已达到12局上限，游历值仍未满6000，保留现场");
            return Action.tap("result-retry", retry);
        }

        // Handle dialogs before their underlying board or entrance labels.
        if (screen.has("当前保存进度|當前保存進度") && screen.one("继续游戏") != null)
            return Action.tap("resume", screen.one("继续游戏"));

        Text saved = screen.one("[|丨]?继续游戏", .80f, .87f, .96f, .96f);
        if (saved != null && screen.hasOne(".*幻梦游园.*", .04f, .02f, .25f, .12f)
                && screen.hasOne("[|丨]?关卡目标", .65f, .15f, .92f, .28f)
                && screen.hasOne("[回口]?已存档", .83f, .83f, .95f, .90f)) {
            Integer points = entranceScore(screen);
            if (points == null) return Action.waitFor("entrance", "续玩前等待唯一周游历读数，不覆盖存档");
            if (points == TARGET) {
                returningAtCap = true;
                if (screen.aspect > 2.18f && screen.aspect < 2.26f)
                    return new Action(Kind.TAP, "entrance-cap", "已满6000，关闭游园核验周奖励", .925f, .06f);
                return new Action(Kind.BACK, "entrance-cap", "返回周度游历核验", 0, 0);
            }
            if (returningAtCap) return Action.stop("结算满分与续玩入口分数不一致");
            return Action.tap("resume-entrance", saved);
        }

        Text uninterested = screen.one("[|丨「]?(?:你对此|你對此)?不感兴趣");
        if (uninterested != null && screen.has("奇缘异[遇过過]")) {
            if (!selectedEvent) return Action.tap("event-select", uninterested);
            return required(screen, "event-confirm", "确认");
        }

        Text skip = screen.one("跳过|跳過"), confirm = screen.one("确认");
        int cardLabels = 0;
        for (Text word : screen.words) if (word.y() > .18f && word.y() < .36f && word.value.matches("[|丨]?(?:声[骸敦啟]|道具)")) cardLabels++;
        if (skip != null && skip.y() > .65f && cardLabels == 3) {
            if (selectedCard) return confirm != null && confirm.y() > .65f ? Action.tap("card-confirm", confirm)
                    : Action.waitFor("card", "已选择卡牌，等待亮起的确认按钮");
            return new Action(Kind.TAP, "card-select", "选金卡优先，其次紫卡，否则第一张",
                    bestCard(screen.cardRanks), .30f);
        }

        Text shopClose = screen.one("关闭.{0,1}", .65f, 0, .95f, .16f);
        if (screen.has("乐园商店") && shopClose != null) {
            resetChoices();
            // shortcut: skip optional purchases to avoid unverified mobile shop price/selection state.
            return Action.tap("shop-close", shopClose);
        }
        if (screen.has("进入新阶段|进入下一阶段")) { resetChoices(); return required(screen, "new-stage", "进入新阶段|进入下一阶段"); }
        if (confirm != null && screen.has("乐园阶段.*目标|本阶段天数|本阶段乐园收益目标|樂园階段"))
            return Action.tap("stage-confirm", confirm);

        if (openingBlessing && confirm != null && screen.has("祝福")) {
            if (selectedBlessing) return Action.tap("blessing-confirm", confirm);
            Text first = screen.one("(?:祝福[·・:]?)?幻梦机遇|(?:祝福[·・:]?)?幻梦機遇|附加.*|Add-on");
            if (first != null) return Action.tap("blessing-select", first);
            return Action.waitFor("blessing", "祝福列表尚未识别到第一项，不猜坐标");
        }

        if (screen.has("暂停") && screen.has("重新开始|结算并离开|結算並离开")) {
            Text resume = screen.one("继续|袋续", .62f, .4f, .84f, .65f);
            if (resume != null) return Action.tap("unpause", resume);
        }

        Text start = screen.one("开始游戏|開始游戏");
        if (start != null && screen.has("幻梦游园")) {
            selectedCard = false; selectedEvent = false;
            Integer points = entranceScore(screen);
            if (points == null) return Action.waitFor("entrance", "等待本周游历值；未读到分数不开始");
            if (points == TARGET) {
                returningAtCap = true;
                if (screen.aspect > 2.18f && screen.aspect < 2.26f)
                    return new Action(Kind.TAP, "entrance-cap", "已满6000，关闭游园返回周度游历核验", .925f, .06f);
                return new Action(Kind.BACK, "entrance-cap", "已满6000，返回周度游历核验", 0, 0);
            }
            if (returningAtCap) return Action.stop("结算曾显示满分但入口未满，停止并保留现场");
            if (runs >= 12) return Action.stop("达到12局上限");
            Text blessing = screen.one("选择祝福");
            if (blessing != null) return Action.tap("blessing-open", blessing);
            return Action.tap("start", start);
        }

        String boardHeader = screen.text(0, 0, .25f, .22f);
        if (boardHeader.contains("本周游园") && Pattern.compile("阶段[:：]?[1-7]/7").matcher(boardHeader).find()) {
            Text speed = screen.one(".+", .65f, .015f, .75f, .10f);
            // The fast-forward icon is OCR'd as W by PC OCR and M by phone ML Kit in 29.png.
            if (speed != null && speed.value.matches("(?i)[x×]?MAX(?:[^a-z0-9]|[MW])*")) {
                if (awaitingMaxSpeed && (maxSpeedFrames = previousMaxFrames + 1) < 2)
                    return Action.waitFor("speed-max", "已识别MAX，等待下一帧复核");
                awaitingMaxSpeed = false; speedTaps = 0;
            } else if (speed != null) {
                if (speedTaps >= 4) return Action.stop("四次倍速切换后仍未识别到MAX，停止并保留现场");
                return new Action(Kind.TAP, "speed-max", "当前" + speed.value + "，切换并核验MAX倍速", speed.x(), speed.y());
            }
        }
        if (awaitingMaxSpeed) return Action.waitFor("speed-max", "等待倍速按钮显示MAX，不按点击次数推定成功");
        Text nextDay = screen.one("进入下[一1—ー‐–-]天");
        if (nextDay != null && screen.has("阶段|階段")
                && (screen.has("(?:今日|日)收入|今日收益")
                    || (boardHeader.contains("本周游园") && Pattern.compile("第[0-9]+天").matcher(boardHeader).find()))) {
            resetChoices();
            return Action.tap("next-day", nextDay);
        }

        if (screen.has("周度游历|周度游歷") && screen.has("幻梦游园")) {
            if (returningAtCap) {
                Integer points = guideScore(screen);
                if (points == null) return Action.waitFor("guide-verify", "等待周度游历当前分数核验（不使用右侧6000刻度）");
                if (points != TARGET) return Action.stop("周度游历分数与满分结算不一致，停止");
                Text claim = screen.one("一键领取|全部领取|一鍵领取");
                if (claim != null) return Action.tap("claim-all", claim);
                if (screen.aspect > 2.18f && screen.aspect < 2.26f && rewardChest < 6) {
                    // Calibrated against the Xiaomi 13 Pro 2400x1080 weekly-guide screenshot, not PC coordinates.
                    return new Action(Kind.TAP, "reward-chest-" + rewardChest,
                            "检查已解锁的第" + (rewardChest + 1) + "个周游历宝箱", .35f + .112f * rewardChest, .88f);
                }
                return new Action(Kind.DONE, "completed", "已核验周度游历6000/6000；宝箱点击已尝试，物品领取状态尚待核实", 0, 0);
            }
            Text garden = screen.one("[|丨]?幻梦游园[·・:：。,，]?狂想[。.]?", .05f, .15f, .40f, .80f);
            if (garden != null) return Action.tap("guide-garden", garden);
        }
        if (screen.has("活跃行迹|活躍行跡|活跃度|活躍度") && screen.one("周度游历|周度游歷") != null)
            return required(screen, "guide-weekly", "周度游历|周度游歷");
        Text guide = screen.one("[|丨「]?[索素]拉指南[。.,，]?", .62f, .35f, .82f, .65f);
        if (guide != null && (screen.has("终端|終端")
                || (screen.has("数据坞|數據塢") && screen.has("地图|地圖") && screen.has("联觉经验|联党等级|联觉等级"))))
            return Action.tap("terminal-guide", guide);
        if (worldHud(screen))
            return new Action(Kind.TAP, "world-terminal", "打开终端", .925f, .06f);
        if (connecting) return Action.waitFor("game-loading", "已连接游戏，等待载入（最多90秒）");
        return Action.waitFor("unknown", "等待可识别的幻梦游园界面");
    }

    static boolean worldHud(Screen screen) {
        return screen.aspect > 2.18f && screen.aspect < 2.26f
                && worldStatus(screen)
                && screen.text(.78f, .93f, 1, 1).contains("特征码");
    }

    static boolean worldStatus(Screen screen) {
        return screen.hasOne("(?i)(?:Lv[.]?|t)\\d{1,3}", .35f, .86f, .72f, .98f)
                || ((screen.hasOne("[0-9][0-9A-Za-z]{1,6}/[0-9]{2,7}", .35f, .86f, .72f, .98f)
                     // Actual phone OCR reads Lv.90 as L.90, u.90, .90, Lw.90, L90, w90, ta90, or 90; require a separate latency anchor.
                     || screen.hasOne("(?i)(?:[Lliwvuta]{1,2}[.]?|[.])?\\d{1,3}", .55f, .90f, .61f, .96f))
                    && screen.hasOne(".{0,2}[0-9]{1,4}ms", .84f, .08f, .98f, .17f));
    }

    /** Change state only after the system reports a completed gesture. */
    void applied(Action action) {
        connecting = action.scene.equals("game-connect");
        if (action.scene.startsWith("reward-chest-")) rewardChest++;
        switch (action.scene) {
            case "speed-max" -> { awaitingMaxSpeed = true; speedTaps++; }
            case "card-select" -> selectedCard = true;
            case "card-confirm" -> selectedCard = false;
            case "event-select" -> selectedEvent = true;
            case "event-confirm" -> selectedEvent = false;
            case "blessing-open" -> openingBlessing = true;
            case "blessing-select" -> selectedBlessing = true;
            case "blessing-confirm" -> { openingBlessing = false; selectedBlessing = false; }
            case "start", "result-retry" -> { runs++; resetChoices(); }
            default -> { }
        }
    }

    private void resetChoices() { selectedCard = false; selectedEvent = false; }
    private Action required(Screen screen, String scene, String regex) {
        Text button = screen.one(regex);
        return button == null ? Action.waitFor(scene, "等待按钮：" + regex) : Action.tap(scene, button);
    }

    static float bestCard(int[] ranks) {
        if (ranks == null || ranks.length != 3) throw new IllegalArgumentException("需要三张卡的稀有度");
        int best = 0;
        for (int index = 1; index < 3; index++) if (ranks[index] > ranks[best]) best = index;
        return new float[]{.27f, .50f, .73f}[best];
    }

    static String progressKey(Screen screen, Action action) {
        String scene = action.scene.replaceAll("-(?:select|confirm)$", "");
        if (scene.equals("next-day")) {
            String header = screen.text(0, 0, .25f, .22f);
            Matcher day = Pattern.compile("第[0-9]+天|阶段[:：]?[0-9]+/7").matcher(header);
            StringBuilder progress = new StringBuilder(scene);
            while (day.find()) progress.append(':').append(day.group());
            return progress.toString();
        }
        if (scene.startsWith("result")) return "result:" + resultScore(screen);
        if (scene.equals("daily-guide-boss-tab")) return scene;
        if (scene.startsWith("daily-boss") || scene.equals("daily-wuyin-proceed") || scene.equals("daily-wuyin-scroll"))
            return scene + ":" + DailyRules.waveplates(screen);
        if (scene.startsWith("guide")) return scene + ":" + guideScore(screen);
        if (scene.equals("card")) return scene + ":" + screen.text(.12f, .12f, .86f, .23f);
        // Options contain animated decorations; their noisy OCR is not evidence of progress.
        if (scene.equals("event")) return scene;
        if (scene.equals("stage")) {
            Matcher stage = Pattern.compile("乐园阶段[0-9]+/7目标").matcher(screen.all());
            return scene + ":" + (stage.find() ? stage.group() : "unknown");
        }
        return scene;
    }
}
