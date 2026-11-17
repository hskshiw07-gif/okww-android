package io.github.okww.weekly;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static io.github.okww.weekly.GardenRules.*;

/** Mobile daily navigation: UID/Account & Team verification -> Guidebook (Activity/Boss/Tacet) -> Teleport. */
final class DailyRules {
    final DailyTacet tacet;
    DailyRules() { this(null); }
    DailyRules(DailyTacet tacet) { this(tacet,null); }
    DailyRules(DailyTacet tacet,AccountDailyConfig.Profile batchProfile) {
        this(tacet,batchProfile,false);
    }
    DailyRules(DailyTacet tacet,AccountDailyConfig.Profile batchProfile,boolean ignoreActivity) {
        this.tacet=tacet;this.batchProfile=batchProfile;this.ignoreActivity=ignoreActivity;
        reservePreparation=new DailyReservePreparation(tacet);
    }
    private final boolean ignoreActivity;
    private final AccountDailyConfig.Profile batchProfile;
    private final DailyReservePreparation reservePreparation;
    private long resourceCapturedAt=-1,resourceNow=-1;
    private ActivityRewardVision.Observation activityIcons;
    private boolean activityVisionEnabled;
    void activityFrame(ActivityRewardVision.Observation icons){activityVisionEnabled=true;activityIcons=icons;}
    void resourceFrame(long capturedAt,long now){resourceCapturedAt=capturedAt;resourceNow=now;}
    void beforeResourceInput(Action action,long now){
        if(action.scene.startsWith("daily-reserve")&&!action.scene.equals("daily-reserve-unowned-back"))
            throw new IllegalStateException("每日模式仅用普通波片，禁止备用兑换");
        reservePreparation.beforeInput(action,now);
    }
    CombatGesture resourceGesture(Action action){return reservePreparation.gesture(action);}
    static final String ACTIVITY_NUMBER = "[0-9]{1,3}-?";
    private static final String ACTIVITY_LABEL = ".*活跃度[。.，,:：g图]?";
    private boolean connecting;
    private boolean teamVerified;
    private boolean teamPageDeparted;
    private boolean tacetSelected;
    private boolean travelSubmitted;
    private boolean targetLocked;
    private String followupReason = "";
    final DailyRewards rewards = new DailyRewards();
    private String confirmedUid;
    private String candidateUid;
    private Integer candidateStamina;
    private String candidateStaminaUid;
    private boolean uidConflict;
    private AccountDailyConfig.Profile accountProfile;

    void teamConfirmed() { teamVerified = true; teamPageDeparted = false; }
    String confirmedUid() { return confirmedUid; }
    boolean uidConflict() { return uidConflict; }
    boolean budgetComplete() {
        return tacet != null && tacet.hasLedger() && tacet.reserved() == 0 && !tacet.previewOnly()
                && (tacet.dailySpent() >= DailyStaminaBudget.LIMIT
                    || tacet.plan() != null && (batchProfile!=null||tacet.dailySpent() >= 180) && tacet.remainingPlan() == 0);
    }
    static boolean needsHudDetail(Screen s) {return needsHudDetail(s,false);}
    static boolean needsHudDetail(Screen s,boolean recovering) {
        return s.aspect>2.18f && s.aspect<2.26f && AccountDailyConfig.detectUid(s)!=null
                && (recovering&&worldHud(s) || !worldHud(s)
                && s.hasOne("(?i)(?:[Lliwvuta]{1,2}[.]?|[.])?[0-9]{1,3}",.55f,.90f,.61f,.96f));
    }
    AccountDailyConfig.Profile accountProfile() { return accountProfile; }
    int targetTacetIndex() { return accountProfile != null ? accountProfile.tacetIndex : 1; }

    void observeUid(Screen screen) {
        String detected = AccountDailyConfig.detectUid(screen);
        if (detected == null) { candidateUid = null; return; }
        if(batchProfile!=null&&!batchProfile.uid.equals(detected))throw new IllegalStateException("多账号每日 UID 与目标绑定不符，未按旧账号配置操作");
        if (confirmedUid != null && !confirmedUid.equals(detected)) {
            uidConflict=true;
            if(detected.equals(candidateUid))throw new IllegalStateException("本轮账号标记连续两帧发生变化，停止并重新识别账号和编队");
            candidateUid=detected;return;
        }
        uidConflict=false;
        AccountDailyConfig.Profile configured = batchProfile!=null?batchProfile:AccountDailyConfig.profileForUid(detected);
        if (detected.equals(candidateUid)) {
            confirmedUid = detected;
            accountProfile = configured;
        } else {
            candidateUid = detected;
        }
    }

    Action next(Screen screen) {return next(screen,false);}
    Action next(Screen screen,boolean partyFullyHealed) {
        ActivityRewardVision.Observation icons=activityIcons;activityIcons=null;
        long resourceCaptured=resourceCapturedAt,resourceTime=resourceNow;
        resourceCapturedAt=resourceNow=-1;
        Integer previousStamina = candidateStamina;
        String previousStaminaUid = candidateStaminaUid;
        // Only the eligible budget branch below may extend this frame's confirmation streak.
        candidateStamina = null;
        candidateStaminaUid = null;
        rewards.frameStarted();
        if(tacet!=null&&(!partyFullyHealed||screen.automationPanelVisible()
                ||!(screen.aspect>2.18f&&screen.aspect<2.26f)||AccountDailyConfig.detectUid(screen)==null))
            tacet.unconfirmedRecoveryFrame();
        if (screen.automationPanelVisible()) {
            reservePreparation.unconfirmedFrame();
            if(tacet!=null&&tacet.pendingSettlement())return tacet.settlePending(screen,confirmedUid,resourceCaptured,resourceTime);
            return Action.waitFor("capture-overlay-visible", "截图仍含浮窗，丢弃本帧并重新采集");
        }
        observeUid(screen);
        if(tacet!=null&&tacet.pendingSettlement()){
            Action pending=tacet.settlePending(screen,confirmedUid,resourceCaptured,resourceTime);
            if(pending!=null)return pending;
        }
        if(uidConflict){
            reservePreparation.unconfirmedFrame();
            if(tacet!=null)tacet.unconfirmedRecoveryFrame();
            if((tacet!=null&&tacet.recovering())||screen.has("选择复苏物品"))return Action.stop("死亡恢复账号冲突，停止且不使用复苏道具");
            return Action.waitFor("daily-account-recheck","账号读数冲突，暂停输入并复核，不沿用旧配置操作");
        }
        if(tacet!=null&&!tacet.hasLedger()&&DailyTacet.settlementPage(screen))
            return Action.waitFor("daily-settlement-ledger-wait","先核验结算页账号与已有账本，不重试或重新领取");
        Action preparation=batchProfile==null&&(tacet==null||!tacet.previewOnly())
                ?reservePreparation.next(screen,confirmedUid,teamVerified,resourceCaptured,resourceTime):null;
        if(preparation!=null)return preparation;
        if(!teamVerified&&screen.has("复苏|复活|使用药品|购买药品|购买复苏"))
            return Action.stop("复苏界面未归属本轮已核验队伍，未使用任何恢复道具");
        if(screen.aspect>2.18f&&screen.aspect<2.26f&&AccountDailyConfig.detectUid(screen)==null
                && screen.hasOne("(?:[0-9]{1,2}|100)%",.90f,.91f,.97f,.97f)
                && screen.text(.05f,.78f,.75f,.92f).length()>24)
            return Action.waitFor("game-loading","场景载入中，等待进度结束，不发送触控（最多90秒）");
        if (tacet != null && !teamVerified && CombatHud.tacetCombat(screen))
            return new Action(Kind.BACK,"daily-unowned-tacet-back","启动时已在挑战内，先返回退出提示，再核验账号与队伍",0,0);
        if (tacet != null && !teamVerified && confirmedUid != null && screen.aspect > 2.18f && screen.aspect < 2.26f
                && screen.one("提示",.28f,.28f,.34f,.36f)!=null
                && screen.one("确认离开",.44f,.43f,.56f,.53f)!=null
                && screen.one("重新挑战",.32f,.57f,.42f,.68f)!=null) {
            Text confirm=screen.one("确认",.58f,.57f,.68f,.68f);
            if(confirm!=null)return Action.tap("daily-unowned-tacet-exit",confirm);
        }
        if (tacet != null && !teamVerified && screen.aspect > 2.18f && screen.aspect < 2.26f
                && screen.one("挑战失败", .32f, .12f, .68f, .42f) != null
                && screen.one("角色等级", .25f, .50f, .40f, .65f) != null) {
            Text leave = screen.one("退出副本", .22f, .72f, .52f, .94f);
            if (leave != null) return Action.tap("daily-unowned-failure-exit", leave);
        }
        if (tacet != null && screen.aspect > 2.18f && screen.aspect < 2.26f) {
            Action combat = tacet.next(screen,partyFullyHealed,resourceCaptured,resourceTime);
            if(tacet.consumeRecovered()){
                teamVerified=teamPageDeparted=tacetSelected=travelSubmitted=targetLocked=false;
                followupReason="";
            }
            if (combat != null) return combat;
            if (tacet.finished()&&tacet.previewOnly())
                return Action.waitFor("daily-tacet-preview-finished",tacet.reason());
            if (tacet.finished()) { rewards.start(); followupReason = tacet.reason(); }
        }
        if(screen.has("复苏|复活|使用药品|购买药品|购买复苏"))return Action.stop("复苏界面未归属本轮战斗，未使用任何恢复道具");
        if (screen.has("补充波片|兑换波片|结晶溶剂|使用结晶|确认购买|确认解锁|单人挑战|领取奖励将消耗"))
            return Action.stop("尚未标定战斗或扣费界面，已保留现场，未点击");
        if (screen.has("挑战成功|挑战失败") && screen.has("重新挑战|再次挑战|离开|退出")
                && !screen.has("幻梦游园|本周游历值|战歌重奏"))
            return new Action(Kind.STOP, "daily-combat-unavailable", "当前是战斗结算页；每日战斗/结算尚未接通，不会点击重试或领奖", 0, 0);
        Text connect = screen.one("点击连接", .35f, .80f, .65f, 1);
        if (connect != null && screen.has("(?i)CN_?Android_?Product") && screen.has("CADPA"))
            return Action.tap("game-connect", connect);
        if (!(screen.aspect > 2.18f && screen.aspect < 2.26f))
            return new Action(Kind.STOP, "daily-layout-unsupported", "画面不是已标定的20:9横屏布局；请保留截图，不使用PC坐标", 0, 0);

        Action rewardModal = rewards.modal(screen);
        if (rewardModal != null) return rewardModal;

        boolean teamPage = screen.has("编队|队伍配置")
                && screen.has("(?:^|\\|)(?:出战|快速编队)(?:\\||$)");
        boolean journal = screen.one(".*漂泊日志", 0, 0, .25f, .15f) != null && screen.has("阶段奖励|[已己]解锁所有阶段任务")
                || AccountDailyConfig.detectUid(screen) != null
                && screen.one("阶段[0-9]{1,2}", .78f, .10f, .90f, .18f) != null
                && screen.one("[已己]解锁所有阶段任务", .75f, .50f, .92f, .59f) != null
                && screen.one("阶段奖励", .78f, .60f, .90f, .69f) != null;
        boolean activity = screen.one(".*活跃行迹", 0, 0, .25f, .15f) != null;
        boolean bossBook = screen.one(".*素材获取", 0, 0, .25f, .15f) != null;
        boolean periodBook = AccountDailyConfig.detectUid(screen) != null
                && screen.one(".*周期挑战", 0, 0, .25f, .15f) != null
                && screen.one("逆境深塔", .10f, .10f, .30f, .22f) != null
                && screen.one("冥歌海墟", .10f, .22f, .30f, .32f) != null;

        boolean terminal = screen.has("终端|数据坞|索拉等级") && screen.has("地图|设置|设定");
        if (teamVerified && !teamPage && (worldHud(screen) || terminal || journal || activity || bossBook || periodBook)) teamPageDeparted = true;
        Action followup = rewards.next(screen, terminal);
        if (followup != null) return followup;
        if (travelSubmitted) {
            if (worldHud(screen)) return new Action(Kind.STOP, "daily-tacet-arrived",
                    "传送后已返回大世界；无音区进场、战斗、结算扣费尚未接通，保留现场，不循环打开指南", 0, 0);
            return Action.waitFor("daily-travel-loading", "已提交一次传送，等待大世界载入；不重复点击传送");
        }

        if (!teamVerified && (journal || activity || bossBook || periodBook))
            return new Action(Kind.BACK, "daily-guide-back", "本轮尚未核验队伍，先返回大世界读取当前编队", 0, 0);
        if (!teamVerified && (DailyRewards.title(screen, "邮件") || DailyRewards.title(screen, "先约(?:公约|电台)")))
            return new Action(Kind.BACK, "daily-rewards-page-back", "从奖励菜单返回，先核验本轮账号与编队，不直接领取", 0, 0);
        if (teamVerified && journal)
            return new Action(Kind.TAP, "daily-guide-first-tab", "从漂泊日志切换指南首个导航页并核验标题", .062f, .175f);
        if (teamVerified && periodBook) {
            if (rewards.started()) return new Action(Kind.TAP, "daily-guide-first-tab", "从周期挑战返回活跃行迹检查免费奖励", .062f, .175f);
            return new Action(Kind.TAP, "daily-guide-boss-tab", "当前为周期挑战，点击指南左侧第2个图标进入素材获取，不进入周期挑战", 148f/2400, 326f/1080);
        }

        // Map screen: handle both standard map (切换地图 / 探索度) and Tacet Suppression detail panel on map (结晶波片消耗 / 附近信标无法快速到达 / 快速旅行)
        Text mapSwitch = screen.one("切换地图", .70f, .72f, .95f, .96f);
        Text mapExplore = screen.one(".*探索[率度].*", .02f, .02f, .35f, .28f);
        boolean mapDetailPanel = !bossBook && screen.has("结晶波片消耗|查看可掉落声骸|附近信标无法快速到达|请先前往[此比]场景");
        if (mapSwitch != null || mapExplore != null || mapDetailPanel) {
            if (screen.has("附近信标无法快速到达|请先前往[此比]场景")) {
                return new Action(Kind.BACK, "daily-map-locked", "当前目标信标未解锁，返回后只检查免费奖励，不反复前往同一目标", 0, 0);
            }
            if (teamVerified && tacetSelected && !targetLocked && mapDetailPanel
                    && screen.one(".*无音区.*", .68f, 0, .98f, .18f) != null) {
                Text travel = screen.one("快速旅行|传送", .68f, .78f, .98f, .98f);
                if (travel != null) {
                    String acctInfo = accountProfile != null ? "（" + accountProfile.summary() + "）" : "";
                    return new Action(Kind.TAP, "daily-map-travel",
                            "点击快速旅行前往无音区" + acctInfo, travel.x(), travel.y());
                }
            }
            if ((mapSwitch != null && mapExplore != null) || mapDetailPanel)
                return new Action(Kind.BACK, "daily-map-back", "识别到地图页，先返回上一级界面", 0, 0);
        }
        if (teamPage) {
            if (teamPageDeparted) teamVerified = false;
            return teamVerified ? new Action(Kind.BACK, "daily-team-back", "队伍已核验，返回终端", 0, 0)
                    : Action.waitFor("daily-team", "正在读取编队名称，等待两个新鲜画面一致");
        }
        if (teamVerified && activity) {
            if(ignoreActivity&&!rewards.started()&&tacet!=null)
                return new Action(Kind.TAP,"daily-guide-boss-tab","每日测试：忽略活跃度，进入素材获取继续无音区副本；仍核验普通波片额度",148f/2400,326f/1080);
            Integer points = activityPoints(screen);
            if (points == null)
                return Action.waitFor("daily-guide-loading", "等待每日页面和当前活跃度载入，不把标题或100刻度当结果");
            if (points >= 100 && tacet == null) rewards.start();
            if (tacet != null && tacet.exhausted()) { rewards.start(); followupReason = "本轮计划或本游戏日波片额度已用完，继续免费奖励检查"; }
            if(activityVisionEnabled&&icons==null)return Action.waitFor("daily-activity-icons-wait","等待本帧活跃宝箱证据，不复用上一张截图");
            Action reward = rewards.activity(screen, points,icons);
            if (reward != null) return reward;
            if (!rewards.started()) {
                String acct = accountProfile != null ? "，" + accountProfile.summary() : "";
                return new Action(Kind.TAP, "daily-guide-boss-tab",
                        "当前活跃度" + points + (points >= 100 ? "（已达标，仍须核验并完成波片目标" : "（未满100")
                                + acct + "），点击指南左侧第2个图标进入素材获取", 148f/2400, 326f/1080);
            }
        }
        if (teamVerified && bossBook) {
            if (rewards.started()) return new Action(Kind.TAP, "daily-guide-first-tab", "返回活跃行迹检查免费奖励", .062f, .175f);
            Integer stamina = waveplates(screen);
            if (stamina == null)
                return Action.waitFor("daily-boss-loading", "等待素材获取右上角当前结晶波片读数");
            if (confirmedUid == null) return Action.waitFor("daily-account-wait", "账号标记尚未连续核验，不用默认无音区代替账号配置");
            if (tacet != null) {
                if (!tacet.hasLedger()) return Action.waitFor("daily-stamina-ledger-wait", "等待本轮已核验账号的波片账本，不在缺少预算时进场");
                if(tacet.exhausted()&&!tacet.previewOnly()&&!tacet.needsPreview(stamina)) {
                    followupReason = "本轮计划或本游戏日波片额度已用完，继续免费奖励检查";
                    rewards.start();
                    return new Action(Kind.TAP, "daily-guide-first-tab", followupReason, .062f, .175f);
                }
                if (tacet.plan() == null) {
                    String currentUid = AccountDailyConfig.detectUid(screen);
                    if (!confirmedUid.equals(currentUid))
                        return Action.waitFor("daily-stamina-account-wait", "等待本帧同账号标记，不用旧账号证据冻结消费计划");
                    candidateStamina = stamina;
                    candidateStaminaUid = currentUid;
                    if (!stamina.equals(previousStamina) || !currentUid.equals(previousStaminaUid))
                        return Action.waitFor("daily-stamina-plan-check", "再次确认同账号当前普通波片余额，再冻结本轮额度");
                    tacet.freezeOrdinaryPlan(stamina);
                }
                if(tacet.exhausted()&&!tacet.previewOnly()){
                    followupReason="普通波片不足60或本日额度已用完，继续免费检查；未补充或兑换";
                    rewards.start();return new Action(Kind.TAP,"daily-guide-first-tab",followupReason,.062f,.175f);
                }
                if(!tacet.funded())return Action.waitFor("daily-stamina-funding-wait","冻结计划尚未核验实际备用兑换到账，不进入挑战");
            } else if (stamina < DailyStaminaBudget.CLAIM_COST) {
                followupReason = "波片不足60，跳过无音区；未补充或兑换";
                rewards.start();
                return new Action(Kind.TAP, "daily-guide-first-tab", followupReason + "，返回活跃行迹", .062f, .175f);
            }
            Text wuyin = screen.one(".*无音清剿.*", .06f, .12f, .36f, .92f);
            String listBody = screen.text(.32f, .12f, .97f, .92f);
            boolean otherCategoryActive = Pattern.compile("武器材料|武器及技能材料|经验材料|突破材料|培养目标|已完成共鸣者培养|声骸培养|高级技能材料|梦魇声骸|声骸套件").matcher(listBody).find();
            boolean wuyinListVisible = !otherCategoryActive && Pattern.compile("无音区|声骸材料|声骸套装").matcher(listBody).find();
            if (wuyin != null && !wuyinListVisible) {
                return Action.tap("daily-boss-wuyin", wuyin);
            }
            List<Text> proceeds = proceedButtons(screen);
            if (!proceeds.isEmpty() && wuyinListVisible) {
                int targetIndex = targetTacetIndex();
                if (targetIndex <= 4 && proceeds.size() != 4)
                    return Action.waitFor("daily-target-incomplete", "无音区首屏四行按钮尚未识别齐，不按残缺列表重排序号");
                if (targetIndex <= 4 && !proceeds.get(targetIndex - 1).value.equals("前往")) {
                    if (tacet != null) return Action.tap("daily-tacet-direct-entry", proceeds.get(targetIndex - 1));
                    followupReason = "无音区第" + targetIndex + "项为直接挑战，战斗尚未接通，本轮只检查免费奖励";
                    return new Action(Kind.TAP, "daily-skip-unavailable-combat", followupReason, .062f, .175f);
                }
                float[] target = AccountDailyConfig.selectTacetTarget(proceeds, targetIndex, false);
                if (target != null) {
                    String acctDesc = accountProfile != null
                            ? "账号" + accountProfile.uid + "(" + accountProfile.accountMask + ")配置无音区#" + targetIndex
                            : "默认无音区#" + targetIndex;
                    if (target[2] > 0.5f) {
                        return new Action(Kind.STOP, "daily-wuyin-calibration-required",
                                acctDesc + "：列表滚动位置尚未用手机画面核验，不套用PC滚动比例", 0, 0);
                    }
                    return new Action(Kind.TAP, "daily-wuyin-proceed",
                            "结晶波片" + stamina + "/240，" + acctDesc + "，点击第" + targetIndex + "个无音区前往",
                            target[0], target[1]);
                }
            }
            if (wuyin != null) return Action.tap("daily-boss-wuyin", wuyin);
            return Action.waitFor("daily-boss-wuyin-wait", "等待无音清剿列表载入");
        }
        if (screen.has("幻梦游园|本周游历值") || screen.one(".*战歌重奏", 0, 0, .30f, .15f) != null
                || screen.has("战歌重奏") && screen.has("开始游戏|开始挑战"))
            return Action.stop("当前是周常界面，每日不会操作周常，请回到大世界后重试");
        if (teamVerified && terminal) {
            Text guide = screen.one("[|丨「]?[索素]拉指南[。.,，]?", .62f, .35f, .82f, .65f);
            return guide == null ? Action.waitFor("daily-find-guide", "等待终端的索拉指南按钮")
                    : Action.tap("daily-open-guide", guide);
        }
        Text team = screen.one("编队|队伍|队伍配置", .35f, .15f, .94f, .85f);
        if (team != null && terminal)
            return Action.tap("daily-open-team", team);
        if (worldHud(screen)) return new Action(Kind.TAP, "daily-world-terminal", "打开终端识别编队入口", .925f, .06f);
        if (connecting) return Action.waitFor("game-loading", "已连接游戏，等待载入（最多90秒）");
        if (screen.words.isEmpty()) return Action.waitFor("daily-ocr-empty", "本帧未识别到文字；等待新画面，不发送触控。持续出现时检查是否授权了整个屏幕");
        return Action.waitFor("daily-unknown", unknownReason(screen));
    }

    void applied(Action action) {
        reservePreparation.applied(action);
        if(action.scene.equals("daily-reserve-unowned-back")){
            teamVerified=teamPageDeparted=tacetSelected=travelSubmitted=targetLocked=false;
            candidateStamina=null;candidateStaminaUid=null;
        }
        rewards.applied(action);
        if (tacet != null) tacet.applied(action);
        if (tacet != null && action.scene.equals("daily-tacet-direct-entry")) tacet.selected();
        connecting = action.scene.equals("game-connect");
        if (action.scene.equals("daily-skip-unavailable-combat")) rewards.start();
        if (action.scene.equals("daily-wuyin-proceed")) tacetSelected = true;
        if (action.scene.equals("daily-map-travel")) travelSubmitted = true;
        if (action.scene.equals("daily-map-locked")) {
            targetLocked = true; tacetSelected = false;
            followupReason = "无音区目标未解锁，未进行战斗";
            rewards.start();
        }
    }

    String followupReason() { return followupReason; }

    static List<Text> proceedButtons(Screen screen) {
        List<Text> list = new ArrayList<>();
        for (Text word : screen.words) {
            if (word.x() >= .80f && word.x() <= .96f && word.y() >= .20f && word.y() <= .88f
                    && word.value.matches("前往|直[接按技]挑战|接挑战")) {
                list.add(word);
            }
        }
        list.sort(Comparator.comparingDouble(Text::y));
        return list;
    }

    static Integer waveplates(Screen screen) {
        String header = screen.text(.58f, 0, .86f, .12f);
        Matcher matcher = Pattern.compile("(?<!\\d)(\\d{1,3})/240(?!\\d)").matcher(header);
        Set<Integer> values = new HashSet<>();
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value > 240) return null;
            values.add(value);
        }
        return values.size() == 1 ? values.iterator().next() : null;
    }

    static boolean unmatched(Action action) {
        return action.scene.equals("daily-unknown") || action.scene.equals("daily-ocr-empty")
                || action.scene.equals("daily-layout-unsupported");
    }

    static String unknownReason(Screen screen) {
        boolean uid = screen.text(.78f, .93f, 1, 1).contains("特征码");
        boolean healthOrLevel = worldStatus(screen);
        String missing = !uid && !healthOrLevel ? "底部账号标记、等级/血量" : !uid ? "底部账号标记"
                : !healthOrLevel ? "底部等级/血量" : "完整的大世界状态标记";
        return "每日页面尚未匹配：未确认" + missing + "；当前仅支持登录、大世界、终端、编队和指南导航。现场将自动保留";
    }

    static Integer activityPoints(Screen screen) {
        if (screen.one(".*活跃行迹", 0, 0, .25f, .15f) == null
                || screen.one(ACTIVITY_LABEL, .16f, .86f, .30f, .94f) == null) return null;
        Text points = screen.one(ACTIVITY_NUMBER, .17f, .80f, .28f, .88f);
        return points == null ? null : Integer.valueOf(points.value.replace("-", ""));
    }

    static float[] activityCrop(Screen screen) {
        if (!(screen.aspect > 2.18f && screen.aspect < 2.26f)
                || screen.one(".*活跃行迹", 0, 0, .25f, .15f) == null
                || screen.one(ACTIVITY_LABEL, .16f, .86f, .30f, .94f) == null
                || activityPoints(screen) != null) return null;
        return new float[]{.17f, .80f, .28f, .88f};
    }
}
