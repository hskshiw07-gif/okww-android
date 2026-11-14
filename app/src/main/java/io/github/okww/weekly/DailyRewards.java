package io.github.okww.weekly;

import java.util.LinkedHashMap;
import java.util.Map;
import static io.github.okww.weekly.GardenRules.*;

/** DailyTask.claim_daily -> claim_mail -> claim_battle_pass, using mobile text targets only. */
final class DailyRewards {
    enum Stage { ACTIVITY, MAIL, PASS_TASKS, PASS_REWARDS, FINISHED }
    private Stage stage = Stage.ACTIVITY;
    private boolean started;
    private String pending = "";
    private boolean receipt;
    private int beforePoints;
    private Integer increasedPoints;
    private int increaseFrame = -1;
    private int confirmedPoints = -1;
    private int claims;
    private String completionSection = "";
    private int completionFrames;
    private int frame;
    private int completionFrame = -1;
    private Integer points;
    private Action departure;
    private Action chestOffer;
    private int chestCandidate=-1,chestCandidateFrame=-1,pendingChest=-1,chestVerifyFrame=-1,chestVerifyFrames;
    private long lastActivityImage=-1;
    private String activityUid;
    private final Map<String, String> status = new LinkedHashMap<>();

    DailyRewards() {
        for (String name : new String[]{"activity", "mail", "pass-tasks", "pass-rewards"})
            status.put(name, "not-visited");
    }
    boolean started() { return started; }
    boolean finished() { return started && stage == Stage.FINISHED; }
    boolean complete() { return finished() && points != null && points >= 100
            && status.values().stream().allMatch(s -> s.equals("verified") || s.equals("not-open")); }
    String stage() { return started ? stage.name() : "NAVIGATION"; }
    Map<String, String> status() { return new LinkedHashMap<>(status); }
    Integer points() { return points; }
    void start() { started = true; }
    void frameStarted() { frame++; }

    Action modal(Screen screen) {
        if (screen.one("提示", .25f, .25f, .40f, .40f) != null
                && screen.one("当前先约电台各频道暂未开播[,，]请于下期先约电台频道开启时间段内进入[。.]?",
                    .28f, .40f, .72f, .56f) != null) {
            Text confirm = screen.one("确认", .52f, .55f, .72f, .72f);
            return confirm == null ? Action.waitFor("daily-pass-unavailable-wait", "电台暂未开播，等待提示框确认按钮")
                    : Action.tap("daily-pass-unavailable-confirm", confirm);
        }
        if (screen.one("获得奖励|获得", .25f, .10f, .75f, .55f) == null) return null;
        if (pending.isEmpty()) return Action.waitFor("daily-reward-unowned", "奖励弹窗并非本轮领取产生，保留现场");
        Text close = screen.one(".*点击.*(?:空白|任意).*(?:继续|关闭).*", .1f, .55f, .95f, 1);
        return close == null ? Action.waitFor("daily-reward-close-wait", "等待奖励弹窗的文字关闭提示，不猜关闭坐标")
                : Action.tap("daily-rewards-dismiss", close);
    }

    Action activity(Screen screen, int currentPoints) {
        return activity(screen,currentPoints,null);
    }
    Action activity(Screen screen,int currentPoints,ActivityRewardVision.Observation icons) {
        if (stage != Stage.ACTIVITY) return departure != null && departure.scene.equals("daily-rewards-to-mail")
                ? departure : awaiting("已提交离开活跃行迹，等待返回终端，不重新领取");
        points = currentPoints;
        if (currentPoints < confirmedPoints) return awaiting("活跃度低于本轮已核验读数，保留领取状态并等待新画面");
        if (pending.equals("task")) {
            if (currentPoints <= beforePoints) {
                increasedPoints = null;
                return awaiting("领取任务后等待活跃度增加，不重复点击");
            }
            if (increasedPoints == null || increasedPoints != currentPoints || increaseFrame != frame - 1) {
                increasedPoints = currentPoints; increaseFrame = frame;
                return awaiting("等待连续两帧确认活跃度增加，不重复领取任务");
            }
            confirmedPoints = currentPoints; increasedPoints = null;
            pending = ""; receipt = false;
        }
        Text task = firstTaskClaim(screen);
        if (task != null && pending.isEmpty()) return claim("daily-activity-claim", task);
        if (!started) return null;
        Text all = claimAll(screen, .70f, .82f, .98f, .98f);
        boolean claimed = screen.one("(?:今日|全部|所有)?(?:活跃度)?奖励已(?:全部)?领取|今日已领取",
                .30f, .72f, .98f, .98f) != null;
        if(icons!=null){
            Action chest=activityChests(screen,currentPoints,icons);
            if(chest!=null)return chest;
            claimed|=icons.allClaimed();
        }
        Action action = claimSection("activity", all, claimed);
        return action != null ? action : back("daily-rewards-to-mail", "每日奖励检查结束，继续邮件；没有文字证据的宝箱不记已领取");
    }

    Action next(Screen screen, boolean terminal) {
        if (!started) return null;
        if (stage == Stage.ACTIVITY) {
            // Already verified chests need only a safe departure, not another numeric/icon OCR pass.
            if (status.get("activity").equals("verified") && pending.isEmpty() && pendingChest < 0
                    && activityUid != null && activityUid.equals(AccountDailyConfig.detectUid(screen))
                    && !screen.automationPanelVisible() && screen.aspect > 2.18f && screen.aspect < 2.26f
                    && title(screen,"活跃行迹"))
                return back("daily-rewards-to-mail","活跃奖励已核验，返回终端继续邮件");
            return null;
        }
        if (departure != null) {
            if (departure.scene.equals("daily-mail-back") && title(screen, "邮件")
                    || departure.scene.startsWith("daily-pass-") && title(screen, "先约(?:公约|电台)")) return departure;
            if (terminal || worldHud(screen)) departure = null;
        }
        if (stage == Stage.FINISHED) return terminal || worldHud(screen)
                ? Action.waitFor("daily-followup-finished", "后续检查结束；活跃度=" + points + "，" + status
                    + (complete() ? "；每日奖励已核验" : "；仍有未完成或未核验项，不将检查结束当作每日完成")) : null;
        if (terminal) {
            String name = stage == Stage.MAIL ? "邮件" : "先约(?:公约|电台)";
            Text entry = screen.one("[|丨「]?" + name + "[。.,，]?", .08f, .15f, .98f, .98f);
            if (entry != null) return Action.tap(stage == Stage.MAIL ? "daily-open-mail" : "daily-open-pass", entry);
            // Measured envelope icon in dev7-terminal-inspect.png, Xiaomi 13 Pro 2400x1080; require the same menu anchors.
            if (stage == Stage.MAIL && title(screen,"终端")
                    && screen.one("[索素]拉指南",.62f,.35f,.82f,.65f) != null
                    && screen.one("数据坞",.45f,.35f,.60f,.65f) != null
                    && screen.one("联觉经验|联党经验",.05f,.55f,.35f,.86f) != null)
                return new Action(Kind.TAP,"daily-open-mail","已标定终端信封入口",1651f/2400,1025f/1080);
            return back(stage == Stage.MAIL ? "daily-mail-entry-missing" : "daily-pass-entry-missing",
                    "终端未识别到" + name + "文字入口，记为未核验；不套用PC图标坐标");
        }
        if (stage == Stage.MAIL && title(screen, "邮件")) {
            Text all = screen.one("一键领取|全部领取|全都领取|领取全部|领取奖励", .05f, .75f, .95f, .98f);
            // The dev10/dev11 completion toast is above the mail body; quoted body text is not status evidence.
            boolean empty = screen.one("暂无邮件|没有邮件|暂无可领取(?:的)?附件|全部附件已领取|全部邮件已领取|全部邮件的附件[已己]经领取完毕"
                    + (pending.equals("mail") ? "|.{0,12}附件[已己]经领取完[毕生]" : ""),
                    .35f, .15f, .65f, .24f) != null;
            Action action = claimSection("mail", all, empty);
            return action != null ? action : back("daily-mail-back", "邮件检查结束，继续先约公约");
        }
        if ((stage == Stage.PASS_TASKS || stage == Stage.PASS_REWARDS) && title(screen, "先约(?:公约|电台)")) {
            if (screen.has("本期已结束|本期公约已结束|公约尚未开启"))
                return back("daily-pass-expired", "先约公约本期未开放，记录后返回");
            String section = stage == Stage.PASS_TASKS ? "pass-tasks" : "pass-rewards";
            String body = screen.text(.10f, .05f, .95f, .75f);
            boolean taskPage = java.util.regex.Pattern.compile("每日任务|每周任务|本期任务").matcher(body).find();
            boolean rewardPage = java.util.regex.Pattern.compile("先约凭证|寰宇特约|公约等级").matcher(body).find();
            boolean expectedPage = stage == Stage.PASS_TASKS ? taskPage && !rewardPage : rewardPage && !taskPage;
            if (!expectedPage) {
                Text tab = screen.one(stage == Stage.PASS_TASKS ? "任务" : "奖励", 0, .10f, .20f, .80f);
                return tab == null ? Action.waitFor("daily-pass-tab-wait", "等待先约公约任务/奖励页锚点与文字页签")
                        : Action.tap(stage == Stage.PASS_TASKS ? "daily-pass-tasks-tab" : "daily-pass-rewards-tab", tab);
            }
            Text all = claimAll(screen, .20f, .78f, .97f, .98f);
            boolean empty = screen.has(stage == Stage.PASS_TASKS
                    ? "暂无可领取(?:的)?任务奖励|全部任务奖励已领取" : "暂无可领取(?:的)?奖励|全部奖励已领取");
            Action action = claimSection(section, all, empty);
            if (action != null) return action;
            if (stage == Stage.PASS_TASKS) {
                Text tab = screen.one("奖励", 0, .10f, .20f, .80f);
                return tab == null ? Action.waitFor("daily-pass-tab-wait", "等待奖励页签，未完成的任务不记为已领取")
                        : Action.tap("daily-pass-next-rewards", tab);
            }
            return back("daily-pass-back", "先约公约检查结束，返回并保留各项核验状态");
        }
        return null;
    }

    private Action claimSection(String section, Text all, boolean complete) {
        if (status.get(section).equals("verified")) return null;
        if (!section.equals(completionSection) || completionFrame != frame - 1) completionFrames = 0;
        completionSection = section;
        completionFrame = frame;
        completionFrames = complete ? completionFrames + 1 : 0;
        if (complete && completionFrames < 2) return awaiting("等待两个新鲜画面确认领取状态");
        if (pending.equals(section)) {
            if (!complete && !(receipt && all == null)) return awaiting("等待领取结果或明确已领取状态，不重复提交");
            status.put(section, complete ? "verified" : "receipt-seen-unverified");
            return null;
        }
        if (complete) { status.put(section, "verified"); return null; }
        if (all != null) return claim("daily-claim-" + section, all);
        if (status.get(section).equals("not-visited")) status.put(section, "unverified");
        return null;
    }

    void applied(Action action) {
        if(action.scene.startsWith("daily-activity-chest-")){
            if(chestOffer==null||!pending.isEmpty()||action.kind!=Kind.TAP||!action.scene.equals(chestOffer.scene)
                    ||Float.compare(action.x,chestOffer.x)!=0||Float.compare(action.y,chestOffer.y)!=0)
                throw new IllegalStateException("活跃宝箱动作未匹配本帧核验目标");
            if(++claims>12)throw new IllegalStateException("本轮免费领取达到12次上限，保留现场");
            pendingChest=chestCandidate;pending="activity-chest-"+pendingChest;receipt=false;
            chestOffer=null;chestCandidate=-1;chestVerifyFrames=0;
            return;
        }
        if (action.scene.equals("daily-pass-unavailable-confirm") && started
                && (stage == Stage.PASS_TASKS || stage == Stage.PASS_REWARDS || stage == Stage.FINISHED)) {
            status.put("pass-tasks", "not-open"); status.put("pass-rewards", "not-open");
            stage = Stage.FINISHED; departure = null; pending = ""; receipt = false;
        }
        if (action.scene.equals("daily-activity-claim") || action.scene.startsWith("daily-claim-")) {
            if (!pending.isEmpty()) throw new IllegalStateException("上次领取尚未核验");
            if (++claims > 12) throw new IllegalStateException("本轮免费领取达到12次上限，保留现场");
            pending = action.scene.equals("daily-activity-claim") ? "task" : action.scene.substring("daily-claim-".length());
            beforePoints = points == null ? -1 : points;
            increasedPoints = null;
            receipt = false;
        } else if (action.scene.equals("daily-rewards-dismiss")) receipt = true;
        else if (action.scene.equals("daily-rewards-to-mail")) stage = Stage.MAIL;
        else if (action.scene.equals("daily-mail-back") || action.scene.equals("daily-mail-entry-missing")) {
            if (action.scene.endsWith("missing")) status.put("mail", "entry-unverified");
            stage = Stage.PASS_TASKS;
        } else if (action.scene.equals("daily-pass-next-rewards")) stage = Stage.PASS_REWARDS;
        else if (action.scene.equals("daily-pass-back") || action.scene.equals("daily-pass-expired")
                || action.scene.equals("daily-pass-entry-missing")) {
            if (!action.scene.equals("daily-pass-back")) {
                String result = action.scene.equals("daily-pass-expired") ? "not-open" : "entry-unverified";
                status.put("pass-tasks", result); status.put("pass-rewards", result);
            }
            stage = Stage.FINISHED;
        }
        if (action.scene.equals("daily-rewards-to-mail") || action.scene.equals("daily-mail-back")
                || action.scene.equals("daily-pass-next-rewards") || action.scene.equals("daily-pass-back")
                || action.scene.equals("daily-pass-expired")) {
            pending = ""; receipt = false;
            if (!action.scene.equals("daily-pass-next-rewards")) departure = action;
        }
    }

    private Action activityChests(Screen screen,int currentPoints,ActivityRewardVision.Observation icons){
        chestOffer=null;
        boolean fresh=icons.capturedAt>lastActivityImage;
        lastActivityImage=Math.max(lastActivityImage,icons.capturedAt);
        String uid=AccountDailyConfig.detectUid(screen);
        if(!fresh||!icons.known()||uid==null||!uid.equals(icons.uid)||icons.points==null||icons.points!=currentPoints){
            chestCandidate=-1;chestVerifyFrames=0;completionFrames=0;
            return awaiting("等待同账号新鲜完整的五档宝箱图标，不依据活跃度猜测领取状态");
        }
        if(activityUid!=null&&!activityUid.equals(uid))return Action.stop("活跃奖励核验期间账号变化，未继续领取");
        activityUid=uid;
        if(pendingChest>=0){
            boolean checked=icons.claimed(pendingChest);
            chestVerifyFrames=checked?(chestVerifyFrame==frame-1?chestVerifyFrames+1:1):0;
            chestVerifyFrame=frame;
            if(chestVerifyFrames<2)return awaiting("已点一次第"+(pendingChest+1)+"档，等待连续两帧变为已领取勾选，不重复点");
            pendingChest=-1;pending="";receipt=false;chestVerifyFrames=0;
        }
        if(!pending.isEmpty())return icons.allClaimed()?null:awaiting("已有免费领取等待核验，不叠加宝箱点击");
        int slot=icons.firstAvailable();
        if(slot<0){chestCandidate=-1;return null;}
        if(claims>=12)return Action.stop("本轮免费领取达到12次上限，保留现场");
        boolean stable=chestCandidate==slot&&chestCandidateFrame==frame-1;
        chestCandidate=slot;chestCandidateFrame=frame;
        if(!stable)return awaiting("再次核验第"+(slot+1)+"档金色宝箱、红点和活跃度，不按PC坐标盲领");
        chestOffer=new Action(Kind.TAP,"daily-activity-chest-"+slot,"领取第"+(slot+1)+"档免费活跃奖励",
                ActivityRewardVision.tapX(slot),ActivityRewardVision.tapY());
        return chestOffer;
    }
    private static Text firstTaskClaim(Screen screen) {
        Text best = null;
        for (Text word : screen.words) if (word.value.equals("领取") && word.x() >= .82f && word.x() <= .94f
                && word.y() >= .18f && word.y() <= .80f && (best == null || word.y() < best.y())) best = word;
        return best;
    }
    private Action claim(String scene, Text button) {
        return claims >= 12 ? Action.stop("本轮免费领取达到12次上限，保留现场") : Action.tap(scene, button);
    }
    private static Text claimAll(Screen screen, float l, float t, float r, float b) {
        return screen.one("一键领取|全部领取|领取全部|领取奖励", l, t, r, b);
    }
    static boolean title(Screen screen, String title) {
        // The leading menu icon becomes different single OCR glyphs (回/凹); never accept extra title words.
        return screen.one(".?" + title, 0, 0, .32f, .15f) != null;
    }
    private static Action back(String scene, String description) { return new Action(Kind.BACK, scene, description, 0, 0); }
    private static Action awaiting(String reason) { return Action.waitFor("daily-claim-verifying", reason); }
}
