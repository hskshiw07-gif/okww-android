package io.github.okww.weekly;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static io.github.okww.weekly.GardenRules.*;

/** Scoped Tacet entry -> builtin combat -> reserve/verify explicit 60/120-waveplate claim -> repeat/exit. */
final class DailyTacet {
    enum Stage { ENTRY, LOADING, FIGHT, TREASURE, CLAIM, PREVIEW_LEAVE, SETTLE, REPEAT, EXIT, RECOVER_EXIT, RECOVER_HEAL, DONE }
    private final boolean singleClaimOnly;
    private Stage stage=Stage.ENTRY;
    private boolean selected,entryTeam,claimOpened,retrySubmitted,reentrySubmitted;
    private long reentryFrame=-1;
    private int failures,claims,balanceBefore=-1,lastBalance=-1,offerFrames,settleFrames,offerCost;
    private Integer candidateBalance;
    private Text offer;
    private DailyStaminaLedger ledger;
    private DailyPendingSettlement pendingSettlement;
    private DailyStaminaPlan plan;
    private boolean funded,reserveExchangeSubmitted;
    private long reserveExchangeAt=-1,lastFundingFrame=-1,lastFundingNow=-1;
    private int fundingFrames;
    private String reason="";
    private String battleUid;
    private int recoveries,healedFrames;
    private boolean recovered;
    private int unhealedFrames,recoveryZooms;
    private boolean recoveryTravelSubmitted;
    private PortraitMatcher.Match recoveryNexus;
    private PortraitMatcher.Match treasureCandidate;
    private long lastTreasureFrame=-1;
    DailyTacet(){this(false);}
    DailyTacet(boolean singleClaimOnly){this.singleClaimOnly=singleClaimOnly;}
    void recoveryFrame(PortraitMatcher.Match nexus){recoveryNexus=nexus;}
    private void resetRecovery(){healedFrames=unhealedFrames=recoveryZooms=0;recoveryTravelSubmitted=false;recoveryNexus=null;}
    void selected(){selected=true;}
    void entryTeamConfirmed(){entryTeam=true;}
    void treasureConfirmed(String uid){
        if(!selected||stage!=Stage.FIGHT||battleUid==null||!battleUid.equals(uid)||reserved()!=0)
            throw new IllegalStateException("宝箱证据未归属本轮已观测战斗，保留现场与账本");
        stage=Stage.TREASURE;claimOpened=false;offer=null;offerFrames=settleFrames=0;candidateBalance=null;
        treasureCandidate=null;
    }
    boolean observeTreasure(Screen screen,PortraitMatcher.Match marker,long captured,long now){
        String uid=AccountDailyConfig.detectUid(screen);
        if(!selected||stage!=Stage.FIGHT||battleUid==null||!battleUid.equals(uid)||reserved()!=0
                ||marker==null||marker.score<.8||!TacetTreasureNavigation.postWorld(screen)
                ||captured<=lastTreasureFrame||captured>now||now-captured>1500){
            treasureCandidate=null;return false;
        }
        boolean stable=treasureCandidate!=null&&captured-lastTreasureFrame<=2500
                &&Math.abs(marker.x-treasureCandidate.x)<=.025f&&Math.abs(marker.y-treasureCandidate.y)<=.025f;
        lastTreasureFrame=captured;treasureCandidate=marker;
        if(stable)treasureConfirmed(uid);
        return stable;
    }
    boolean seekingTreasure(){return selected&&stage==Stage.TREASURE;}
    boolean finished(){return selected&&stage==Stage.DONE;}
    boolean hasLedger(){return ledger!=null;}
    void freezePlan(int ordinary,int reserve){
        if(ledger==null)throw new IllegalStateException("消费计划缺少本轮账号账本");
        if(plan!=null)throw new IllegalStateException("本轮消费计划已冻结，不重新计算或扩容");
        plan=DailyStaminaPlan.create(ordinary,reserve,ledger.confirmed(),ledger.reserved());
        funded=plan.exchangeAmount==0;
    }
    void freezeOrdinaryPlan(int ordinary){
        if(ledger==null||plan!=null)throw new IllegalStateException("普通波片计划缺少账号账本或已经冻结");
        plan=DailyStaminaPlan.ordinaryOnly(ordinary,ledger.confirmed(),ledger.reserved());funded=true;
    }
    DailyStaminaPlan plan(){return plan;}
    boolean funded(){return plan!=null&&funded;}
    int remainingPlan(){return plan==null?0:plan.remaining(ledger.confirmed());}
    // Both timestamps use epoch milliseconds, including capturedAt, so submission freshness and game day share one clock.
    void beforeReserveExchange(String uid,int amount,long now){
        if(plan==null||funded||reserveExchangeSubmitted||amount<=0||amount!=plan.exchangeAmount)
            throw new IllegalStateException("备用兑换未匹配未执行的精确计划，未允许确认");
        requireFundingAccount(uid,now);
        if(ledger.confirmed()!=plan.confirmedAtStart){unconfirmedFundingFrame();throw new IllegalStateException("兑换前消费账本已变化，停止而非重算兑换");}
        if(now<lastFundingNow)throw new IllegalStateException("备用兑换时钟倒退，未允许确认");
        reserveExchangeSubmitted=true;reserveExchangeAt=lastFundingNow=now;unconfirmedFundingFrame();
    }
    boolean observeFunding(String uid,int ordinary,int reserve,long capturedAt,long now){
        if(plan==null)throw new IllegalStateException("尚未冻结消费计划，未核验备用兑换");
        requireFundingLedger(now);
        if(now<lastFundingNow){unconfirmedFundingFrame();throw new IllegalStateException("备用到账核验时钟倒退");}
        lastFundingNow=now;
        if(uid==null){unconfirmedFundingFrame();return false;}
        requireFundingAccount(uid,now);
        if(funded)return true;
        if(ledger.confirmed()!=plan.confirmedAtStart){unconfirmedFundingFrame();throw new IllegalStateException("兑换期间消费账本变化，停止并保留计划");}
        boolean fresh=reserveExchangeSubmitted&&capturedAt>reserveExchangeAt&&capturedAt>lastFundingFrame
                &&capturedAt<=now&&now-capturedAt<=1500;
        boolean consecutive=lastFundingFrame>=0&&capturedAt-lastFundingFrame<=2500;
        if(capturedAt>=0&&capturedAt<=now&&capturedAt>lastFundingFrame)lastFundingFrame=capturedAt;
        if(!fresh||ordinary!=plan.ordinaryBefore+plan.exchangeAmount||reserve!=plan.reserveBefore-plan.exchangeAmount){
            unconfirmedFundingFrame();return false;
        }
        fundingFrames=consecutive?fundingFrames+1:1;
        if(fundingFrames>=2)funded=true;
        return funded;
    }
    void unconfirmedFundingFrame(){fundingFrames=0;}
    private void requireFundingAccount(String uid,long now){
        requireFundingLedger(now);
        if(!ledger.uid().equals(uid)){
            unconfirmedFundingFrame();throw new IllegalStateException("备用兑换账号不符，停止并保留账本");
        }
    }
    private void requireFundingLedger(long now){
        if(ledger==null||reserved()!=0||!ledger.day().equals(DailyStaminaLedger.gameDay(now))){
            unconfirmedFundingFrame();throw new IllegalStateException("备用兑换账号、游戏日或未核销扣费不符，停止并保留账本");
        }
    }
    void attachLedger(DailyStaminaLedger ledger){
        if(this.ledger!=null)throw new IllegalStateException("波片账本重复绑定");
        this.ledger=ledger;
        if(ledger.reserved()!=0)pendingSettlement=new DailyPendingSettlement(ledger);
    }
    boolean pendingSettlement(){return pendingSettlement!=null;}
    Action settlePending(Screen screen,String uid,long capturedAt,long now){
        if(pendingSettlement==null)return null;
        Action action=pendingSettlement.next(screen,uid,capturedAt,now);
        if(action==null)pendingSettlement=null;
        return action;
    }
    int spent(){return ledger==null?0:ledger.spentThisRun();}
    int reserved(){return ledger==null?0:ledger.reserved();}
    int dailySpent(){return ledger==null?0:ledger.confirmed();}
    boolean exhausted(){return ledger!=null&&(ledger.remaining()<60||plan!=null&&remainingPlan()<60);}
    boolean needsPreview(int ordinary){return singleClaimOnly&&ordinary<DailyStaminaBudget.CLAIM_COST;}
    boolean previewOnly(){return plan!=null&&plan.exchangeAmount==0&&needsPreview(plan.ordinaryBefore);}
    private boolean hasBattleAllowance(){return hasAllowance()||previewOnly()&&reserved()==0;}
    private boolean hasAllowance(){return hasAllowance(60);}
    private boolean hasAllowance(int cost){return !previewOnly()&&(!singleClaimOnly||cost==DailyStaminaBudget.CLAIM_COST)
            &&ledger!=null&&funded()&&remainingPlan()>=cost&&ledger.remaining()>=cost&&reserved()==0;}
    String stage(){return selected?stage.name():"NAVIGATION";}
    String reason(){return reason;}
    boolean recovering(){return stage==Stage.RECOVER_EXIT||stage==Stage.RECOVER_HEAL;}
    void confirmedPartyDeath(String uid){
        if(stage!=Stage.FIGHT||battleUid==null||!battleUid.equals(uid)||reserved()!=0||recoveries>=2)
            throw new IllegalStateException("倒地恢复未归属本轮战斗、存在未核销扣费或已达恢复上限");
        recoveries++;stage=Stage.RECOVER_EXIT;resetRecovery();
    }
    void unconfirmedRecoveryFrame(){healedFrames=0;}
    boolean consumeRecovered(){boolean result=recovered;recovered=false;return result;}
    void beforeInput(Action action,long now){
        if(action.scene.equals("daily-tacet-start")||action.scene.equals("daily-tacet-retry")||action.scene.equals("daily-tacet-repeat")){
            if(!hasBattleAllowance()||previewOnly()&&!action.scene.equals("daily-tacet-start"))
                throw new IllegalStateException("测试预览不重复挑战，或消费计划与扣费账本不允许开始战斗");
            if(!ledger.day().equals(DailyStaminaLedger.gameDay(now)))throw new IllegalStateException("游戏日已变化，请重新核验账号与消费计划，未开始战斗");
            return;
        }
        if(!action.scene.equals("daily-tacet-claim-60")&&!action.scene.equals("daily-tacet-claim-120"))return;
        if(stage!=Stage.CLAIM||offer==null||offerFrames<2||(offerCost!=60&&offerCost!=120)||!hasAllowance(offerCost)
                ||action.kind!=Kind.TAP||!action.scene.equals("daily-tacet-claim-"+offerCost)
                ||Float.compare(action.x,offer.x())!=0||Float.compare(action.y,offer.y())!=0)
            throw new IllegalStateException("领取证据、按钮或冻结消费额度不完整");
        try {ledger.reserve(balanceBefore,offerCost,false,now);}
        catch(IOException error){throw new IllegalStateException("波片预留落盘失败，未发送领取",error);}
    }
    void applied(Action action){
        if(pendingSettlement!=null)pendingSettlement.applied(action);
        switch(action.scene){
            case "daily-tacet-start" -> {stage=Stage.LOADING;reentrySubmitted=false;reentryFrame=-1;}
            case "daily-tacet-retry" -> {failures++;retrySubmitted=true;stage=Stage.LOADING;reentrySubmitted=true;reentryFrame=-1;}
            case "daily-tacet-open-claim" -> claimOpened=true;
            case "daily-tacet-preview-cancel" -> stage=Stage.PREVIEW_LEAVE;
            case "daily-tacet-claim-60", "daily-tacet-claim-120" -> {stage=Stage.SETTLE;offer=null;candidateBalance=null;settleFrames=0;}
            case "daily-tacet-repeat" -> {stage=Stage.LOADING;reentrySubmitted=true;reentryFrame=-1;claimOpened=false;offer=null;offerFrames=settleFrames=0;candidateBalance=null;balanceBefore=lastBalance=-1;}
            case "daily-tacet-exit" -> stage=Stage.EXIT;
            case "daily-tacet-recover-cancel" -> {if(stage==Stage.FIGHT)recoveries++;stage=Stage.RECOVER_EXIT;resetRecovery();}
            case "daily-tacet-recover-exit" -> {stage=Stage.RECOVER_HEAL;resetRecovery();}
            case "daily-tacet-recover-zoom" -> recoveryZooms++;
            case "daily-tacet-recover-travel" -> recoveryTravelSubmitted=true;
            default -> { }
        }
    }
    Action next(Screen s){return next(s,false);}
    Action next(Screen s,boolean partyFullyHealed){return next(s,partyFullyHealed,-1,-1);}
    Action next(Screen s,boolean partyFullyHealed,long captured,long now){
        long previousReentryFrame=reentryFrame;
        reentryFrame=-1;
        PortraitMatcher.Match nexus=recoveryNexus;recoveryNexus=null;
        if(pendingSettlement!=null)return Action.waitFor("daily-pending-settlement","中断的领取仅核销，不重提、不启动战斗");
        if(!selected||stage==Stage.DONE)return null;
        String uid=AccountDailyConfig.detectUid(s);
        if(previewOnly()&&(stage==Stage.PREVIEW_LEAVE||stage==Stage.CLAIM&&claimOpened)){
            if(uid==null)return Action.waitFor("daily-tacet-preview-wait","等待同账号画面再取消或退出，不提交扣费");
            if(!uid.equals(battleUid)||reserved()!=0)return Action.stop("预览未归属本轮同账号战斗或有待核销扣费，未操作");
            if(previewDialog(s)){
                return stage==Stage.CLAIM?new Action(Kind.BACK,"daily-tacet-preview-cancel","测试到达领取预览，取消；不提交扣费",0,0)
                        :Action.waitFor("daily-tacet-preview-wait","已取消一次，等待领取窗口关闭");
            }
            if(stage==Stage.PREVIEW_LEAVE){
                Text exit=exitConfirmation(s);
                if(exit!=null){
                    reason="已验证到达并取消领奖预览，未消耗波片";
                    return Action.tap("daily-tacet-exit",exit);
                }
                if(settlementPage(s)){
                    reason="已验证到达并取消领奖预览，未消耗波片";
                    return Action.tap("daily-tacet-exit",s.one("退出副本|离开副本",.22f,.72f,.52f,.94f));
                }
                if(TacetTreasureNavigation.postWorld(s)||TacetTreasureNavigation.interaction(s)!=null)
                    return new Action(Kind.BACK,"daily-tacet-preview-leave","预览已关闭，打开退出副本提示",0,0);
                return Action.waitFor("daily-tacet-preview-wait","等待关闭后的场景或明确退出提示，未领取");
            }
        }
        if(s.has("补充波片|兑换波片|结晶溶剂|使用结晶|购买|买药|确认解锁|使用药品|使用复苏"))return Action.stop("战斗或领奖期间不临时补充、不购买使用药品；备用资源仅由开战前专用流程核验");
        boolean revival=s.one("选择复苏物品",.24f,.16f,.36f,.23f)!=null;
        if(revival||recovering()){
            if(reserved()!=0)return Action.stop("死亡恢复仍有未核销扣费，保留账本并停止");
            if(battleUid==null||uid!=null&&!battleUid.equals(uid))return Action.stop("死亡恢复账号与本轮战斗不符，未操作");
            if(revival){
                if(stage!=Stage.FIGHT&&stage!=Stage.RECOVER_EXIT)return Action.stop("不在本轮可恢复战斗，未操作复苏界面");
                if(uid==null)return Action.stop("复苏界面未核验本轮账号，未操作");
                if(stage==Stage.FIGHT&&recoveries>=2)return Action.stop("已达到两次死亡恢复上限，不再重新进入");
                return new Action(Kind.BACK,"daily-tacet-recover-cancel","取消复苏物品弹窗，不消耗任何恢复道具",0,0);
            }
            if(uid==null){healedFrames=0;return Action.waitFor("daily-tacet-recover-wait","等待退出后同账号画面，不使用未核验坐标");}
            boolean world=worldHud(s)&&!arenaLabels(s)
                    &&!s.has("提示|确认离开|挑战成功|挑战失败|复苏|复活");
            if(stage==Stage.RECOVER_EXIT){
                if(world)stage=Stage.RECOVER_HEAL;
                else {
                    Text confirm=exitConfirmation(s);
                    if(confirm!=null)return Action.tap("daily-tacet-recover-exit",confirm);
                    Text leave=s.one("退出副本|离开副本",.22f,.72f,.52f,.94f);
                    if(leave!=null&&s.one("挑战失败",.32f,.12f,.68f,.42f)!=null)
                        return Action.tap("daily-tacet-recover-exit",leave);
                    if(CombatHud.tacetCombat(s))return new Action(Kind.BACK,"daily-tacet-recover-menu","打开退出副本提示，不重新挑战",0,0);
                    return Action.waitFor("daily-tacet-recover-wait","等待明确退出提示，不点击复苏或未知确认按钮");
                }
            }
            if(stage==Stage.RECOVER_HEAL){
                healedFrames=world&&partyFullyHealed?healedFrames+1:0;
                unhealedFrames=world&&!partyFullyHealed?unhealedFrames+1:0;
                if(!recoveryTravelSubmitted&&!s.automationPanelVisible()) {
                    Text travel=MobileRecoveryReader.travel(s);
                    if(travel!=null)return Action.tap("daily-tacet-recover-travel",travel);
                    if(MobileRecoveryReader.map(s)) {
                        if(nexus!=null&&nexus.score>=.90)
                            return new Action(Kind.TAP,"daily-tacet-recover-nexus","选择已识别的中枢信标",nexus.x,nexus.y);
                        if(recoveryZooms<2)return new Action(Kind.TAP,"daily-tacet-recover-zoom","缩小地图查找中枢信标",2226f/2400,735f/1080);
                    }
                    if(unhealedFrames>=3&&s.aspect>2.18f&&s.aspect<2.26f)
                        return new Action(Kind.TAP,"daily-tacet-recover-map","打开地图，免费传送中枢信标恢复全队",.11f,.125f);
                }
                if(healedFrames<2)return Action.waitFor("daily-tacet-recover-heal","已退出挑战，等待全队真实复活满血；不假设出本会自动回血");
                selected=entryTeam=claimOpened=retrySubmitted=false;
                offer=null;offerFrames=settleFrames=0;candidateBalance=null;balanceBefore=lastBalance=-1;
                battleUid=null;stage=Stage.ENTRY;resetRecovery();recovered=true;
                reason="全队恢复已连续核验，重新识别编队后进场；保留波片账本与重试次数";
                return Action.waitFor("daily-tacet-recovered",reason);
            }
        }
        if(s.has("复苏|复活|使用药品"))return Action.stop("出现未明确归属的倒地/复活界面，未自动消耗恢复道具");
        if(stage==Stage.TREASURE||stage==Stage.CLAIM||stage==Stage.SETTLE||stage==Stage.REPEAT){
            if(battleUid==null||uid!=null&&!battleUid.equals(uid))return Action.stop("领奖账号与本轮战斗不符，保留账本并停止");
            if(uid==null){offer=null;offerFrames=settleFrames=0;candidateBalance=null;return Action.waitFor("daily-tacet-account-wait","等待本轮账号重新可见，不使用旧帧领取或重复挑战");}
        }
        if(stage==Stage.EXIT&&(worldHud(s)||s.has("终端")&&s.has("索拉指南"))){
            stage=Stage.DONE;return null;
        }
        boolean success=s.one("挑战成功",.32f,.12f,.68f,.42f)!=null;
        boolean failure=s.one("挑战失败",.32f,.12f,.68f,.42f)!=null;
        Text leave=s.one("退出副本|离开副本",.22f,.72f,.52f,.94f);
        Text repeat=s.one("重新挑战|再次挑战",.50f,.72f,.78f,.94f);
        if(failure){
            if(reserved()!=0)return Action.stop("失败页仍有未核销扣费，不重试");
            if(stage==Stage.LOADING&&retrySubmitted)return Action.waitFor("daily-tacet-loading","已提交重试，等待离开失败页");
            if(!previewOnly()&&stage!=Stage.EXIT&&failures<2&&repeat!=null){
                if(!hasAllowance())return Action.stop("消费计划未冻结或剩余额度不足，不重新开启挑战");
                return Action.tap("daily-tacet-retry",repeat);
            }
            reason=previewOnly()?"测试战斗失败，未到达领奖预览，未消耗波片":"连续三次挑战失败，停止刷取并继续免费奖励检查";
            return leave==null?Action.waitFor("daily-tacet-exit-wait",reason):Action.tap("daily-tacet-exit",leave);
        }
        if(stage==Stage.EXIT)return leave==null?Action.waitFor("daily-tacet-exit-wait","等待退出无音区，不重复消费"):Action.tap("daily-tacet-exit",leave);
        Text interaction=TacetTreasureNavigation.interaction(s);
        if((stage==Stage.FIGHT||stage==Stage.TREASURE)&&interaction!=null){
            if(battleUid==null||!battleUid.equals(uid)||!hasBattleAllowance())
                return Action.stop("近距离领奖交互未归属本轮已核验战斗或预算，未操作");
            stage=Stage.CLAIM;
        }
        if(success&&stage==Stage.FIGHT&&battleUid!=null){
            if(!battleUid.equals(uid))return uid==null?Action.waitFor("daily-tacet-account-wait","等待成功页账号核验"):Action.stop("成功页账号与本轮战斗不符");
            stage=Stage.CLAIM;
        }
        if(stage==Stage.TREASURE){
            if(balance(s)==null||ledger==null||quote(s,balance(s))==null)
                return Action.waitFor("daily-tacet-treasure","本轮战后宝箱已确认，等待手机宝箱交互或完整60/120波片领取弹窗；未记为完成");
            stage=Stage.CLAIM;
        }
        if(stage==Stage.CLAIM||stage==Stage.SETTLE||stage==Stage.REPEAT){
            Integer balance=balance(s);
            if(balance!=null)lastBalance=balance;
            if(stage==Stage.CLAIM){
                Quote quote=balance==null||ledger==null||previewOnly()?null:quote(s,balance);
                if(quote!=null){
                    if(!hasAllowance(quote.cost)||!ledger.canClaim(balance,quote.cost,false))return Action.stop("波片余额、冻结计划、每日预算或领取状态不符，未提交扣费");
                    boolean same=balance.equals(candidateBalance)&&offer!=null&&offerCost==quote.cost
                            &&offer.value.equals(quote.button.value)&&Math.abs(offer.x()-quote.button.x())<=.015f
                            &&Math.abs(offer.y()-quote.button.y())<=.015f;
                    offerFrames=same?offerFrames+1:1;candidateBalance=balance;
                    balanceBefore=balance;offer=quote.button;offerCost=quote.cost;
                    return offerFrames<2?Action.waitFor("daily-tacet-price-check","再次确认"+offerCost+"波片、余额与同一领取按钮"):
                            Action.tap("daily-tacet-claim-"+offerCost,offer);
                }
                candidateBalance=null;offerFrames=0;offer=null;
                // Opening the quote is reversible. A completed touch is not proof that the game opened it.
                // The run guard requires two current observations and caps unchanged-scene inputs at four.
                if(interaction!=null)return Action.tap("daily-tacet-open-claim",interaction);
                Text claim=s.one("领取奖励",.15f,.50f,.85f,.94f);
                if(success&&!claimOpened&&claim!=null)return Action.tap("daily-tacet-open-claim",claim);
                return Action.waitFor("daily-tacet-price-wait",previewOnly()?"等待明确的领奖预览，只取消，不扣费"
                        :singleClaimOnly?"等待明确的单倍领取按钮、60波片费用与余额"
                        :"等待明确同按钮费用与余额；额度够则优先双倍120，剩余60或无双倍才单倍");
            }
            if(stage==Stage.SETTLE){
                int submitted=reserved();
                if((submitted==60||submitted==120)&&balance!=null&&balance==balanceBefore-submitted){
                    settleFrames=balance.equals(candidateBalance)?settleFrames+1:1;candidateBalance=balance;
                    if(settleFrames>=2){
                        try{ledger.confirm(balance);}catch(IOException error){throw new IllegalStateException("扣费确认落盘失败，保留预留",error);}
                        claims++;stage=Stage.REPEAT;
                    }
                }else{settleFrames=0;candidateBalance=null;}
                if(stage==Stage.SETTLE){
                    Text close=s.one(".*点击.*(?:空白|任意).*(?:继续|关闭).*",.10f,.55f,.95f,1);
                    if(s.one("获得奖励|获得",.25f,.10f,.75f,.55f)!=null&&close!=null)return Action.tap("daily-tacet-dismiss-receipt",close);
                    return Action.waitFor("daily-tacet-settle-wait","领取已提交一次，等待两帧确认余额减少"+submitted+"；未核实不再领取");
                }
            }
            if(stage==Stage.REPEAT){
                boolean more=hasAllowance()&&lastBalance>=60&&claims<4;
                reason=more?"已核实本次波片扣费，继续同一无音区":"本轮刷取结束，返回检查每日奖励";
                Text button=more?repeat:leave;
                return leave==null||repeat==null?Action.waitFor("daily-tacet-result-wait","等待完整退出副本与再次挑战组合按钮；"+reason):Action.tap(more?"daily-tacet-repeat":"daily-tacet-exit",button);
            }
        }
        if(s.one(".*素材获取",0,0,.25f,.15f)!=null)return Action.waitFor("daily-tacet-entry-loading","已选择无音区，等待进入，不重复提交挑战");
        Text start=s.one("开启挑战",.80f,.86f,.93f,.96f);
        if(start!=null&&s.one("快速[编骗]队",.65f,.86f,.78f,.96f)!=null){
            if(stage!=Stage.ENTRY)return Action.waitFor("daily-tacet-loading","已提交开启挑战，等待载入");
            if(entryTeam&&!hasBattleAllowance())return Action.stop("消费计划未冻结、额度不足或存在未核销扣费，未开启挑战");
            return entryTeam?Action.tap("daily-tacet-start",start):Action.waitFor("daily-tacet-team","再次核验副本出战三人，不沿用不同编队");
        }
        if(stage==Stage.LOADING||stage==Stage.FIGHT){
            if(!hasBattleAllowance())return Action.stop("战斗缺少有效冻结计划或剩余额度，停止并保留账本");
            // On re-challenge the game can omit the objective even though the next battle is running.
            if(stage==Stage.LOADING&&reentrySubmitted&&battleUid!=null&&battleUid.equals(uid)
                    &&captured>=0&&captured<=now&&now-captured<=1500&&CombatHud.tacetReentry(s)
                    &&captured>previousReentryFrame)reentryFrame=captured;
            if(CombatHud.tacetCombat(s)||reentryFrame>=0&&previousReentryFrame>=0&&reentryFrame-previousReentryFrame<=2500){
                if(battleUid!=null&&!battleUid.equals(uid))return Action.stop("战斗账号变化，停止并保留账本");
                battleUid=uid;stage=Stage.FIGHT;retrySubmitted=reentrySubmitted=false;reentryFrame=-1;
                return Action.waitFor("daily-tacet-combat","无音区战斗中，执行已核验角色的内置轴");
            }
            return Action.waitFor("daily-tacet-loading","等待无音区目标、倒计时和战斗HUD载入");
        }
        return Action.waitFor("daily-tacet-entry-wait","等待副本完整标题与按钮，不依据缺字画面操作");
    }
    private static boolean previewDialog(Screen s){
        return (s.hasOne("领取奖励",.26f,.28f,.36f,.35f)
                &&s.hasOne(".*消耗结晶波片.*领取.*奖励.*",.33f,.43f,.68f,.53f))
                ||(s.hasOne("补充结晶波片",.24f,.16f,.34f,.23f)
                &&s.hasOne("请选择补充方式",.44f,.24f,.55f,.31f)
                &&s.hasOne("取消",.32f,.68f,.37f,.76f));
    }
    private static Text exitConfirmation(Screen s){
        return s.hasOne("提示",.28f,.28f,.34f,.36f)
                &&s.hasOne("确认离开",.44f,.43f,.56f,.53f)
                &&s.hasOne("重新挑战",.32f,.57f,.42f,.68f)?s.one("确认",.58f,.57f,.68f,.68f):null;
    }
    private static boolean arenaLabels(Screen s){
        return s.one(".*清理无[音普]区.*残[象像].*",.02f,.16f,.40f,.42f)!=null
                ||s.one("[^0-9]{0,2}[0-9]{2}:[0-9]{2}:[0-9]{2}",.42f,.08f,.59f,.18f)!=null;
    }
    private static final class Quote {
        final Text button;final int cost;
        Quote(Text button,int cost){this.button=button;this.cost=cost;}
    }
    private Quote quote(Screen s,int balance){
        Text single=s.one("单倍领取",.15f,.45f,.78f,.90f);
        if(singleClaimOnly)return singleQuote(s,single);
        Text twice=s.one("双倍领取",.15f,.45f,.78f,.90f);
        if(twice!=null){
            // An ambiguous or changed double price is unknown, never permission to silently fall back to single.
            if(sameButtonCost(s,twice)!=120)return null;
            if(balance>=120&&hasAllowance(120))return new Quote(twice,120);
        }else{
            // Recorded OCR can read 双信领取/双倍颌取. A partial label or orphan 120 is not evidence of absence.
            for(Text t:s.words)if(t.x()>=.15f&&t.x()<=.78f&&t.y()>=.45f&&t.y()<=.90f
                    &&(t.value.matches("双.*(?:领|取).*")||t.value.equals("120")))return null;
        }
        return singleQuote(s,single);
    }
    private static Quote singleQuote(Screen s,Text button){
        return button!=null&&sameButtonCost(s,button)==DailyStaminaBudget.CLAIM_COST
                ?new Quote(button,DailyStaminaBudget.CLAIM_COST):null;
    }
    static Integer balance(Screen s){
        Set<Integer> values=new HashSet<>();Integer header=DailyRules.waveplates(s);if(header!=null)values.add(header);
        Matcher m=Pattern.compile("(?:^|\\|)剩余([0-9]{1,3})(?:\\||$)").matcher(s.text(.45f,.78f,.80f,.96f));
        while(m.find()){int n=Integer.parseInt(m.group(1));if(n>240)return null;values.add(n);}
        // Red single digits are sometimes a separate OCR line from the white label.
        Text label=s.one("剩余",.56f,.89f,.63f,.94f);
        if(label!=null&&settlementPage(s)){
            int count=0;
            for(Text t:s.words)if(t.value.matches("[0-9]{1,3}")&&t.left>=label.right-.002f
                    &&t.left-label.right<.025f&&Math.abs(t.y()-label.y())<.015f){
                if(++count>1)return null;
                int n=Integer.parseInt(t.value);if(n>240)return null;values.add(n);
            }
        }
        return values.size()==1?values.iterator().next():null;
    }
    static boolean settlementPage(Screen s){
        return s.aspect>2.18f&&s.aspect<2.26f&&AccountDailyConfig.detectUid(s)!=null
                &&s.one("挑战成功",.32f,.12f,.68f,.42f)!=null
                &&s.one("退出副本|离开副本",.22f,.72f,.52f,.94f)!=null
                &&s.one("重新挑战|再次挑战",.50f,.72f,.78f,.94f)!=null;
    }
    static int sameButtonCost(Screen s,Text button){
        int count=0,cost=-1;
        for(Text t:s.words)if(t.value.matches("[0-9]{1,3}")&&t.x()<button.x()&&button.x()-t.x()<.11f&&Math.abs(t.y()-button.y())<.025f){count++;cost=Integer.parseInt(t.value);}
        return count==1?cost:-1;
    }
}
