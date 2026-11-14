package io.github.okww.weekly;

import static io.github.okww.weekly.GardenRules.*;

/** Owned mobile reserve preparation, bounded quantity adjustment, one submission and verified funding. */
final class DailyReservePreparation {
    private enum Stage { IDLE, SOURCE, CANCELING, QUANTITY, SUBMITTED }
    private final DailyTacet tacet;
    private Stage stage=Stage.IDLE;
    private String proposedUid,ownerUid;
    private boolean selectionSubmitted;
    private Integer candidateOrdinary,candidateReserve;
    private int quantityFrames,drags,adjustments;
    private long lastFrame=-1,lastInputAt=-1,authorizedCaptured=-1;
    private String authorizedScene;
    private Action authorizedAction;
    private CombatGesture authorizedGesture;
    DailyReservePreparation(DailyTacet tacet){this.tacet=tacet;}
    boolean owned(){return stage!=Stage.IDLE;}
    private void clearCandidates(){candidateOrdinary=candidateReserve=null;quantityFrames=0;}
    void unconfirmedFrame(){clearCandidates();authorizedScene=null;authorizedAction=null;authorizedGesture=null;if(tacet!=null)tacet.unconfirmedFundingFrame();}
    void beforeInput(Action action,long now){
        if(!action.scene.matches("daily-reserve-(?:drag|increment|decrement|exchange)"))return;
        if(authorizedAction==null||!action.scene.equals(authorizedScene)||action.kind!=authorizedAction.kind||!Float.isFinite(action.x)||!Float.isFinite(action.y)
                ||Math.abs(action.x-authorizedAction.x)>.015f||Math.abs(action.y-authorizedAction.y)>.015f
                ||authorizedCaptured<=lastInputAt||now<authorizedCaptured||now-authorizedCaptured>1500)
            throw new IllegalStateException("资源调整输入缺少本帧新鲜授权，未发送");
        if(stage!=Stage.QUANTITY||tacet.plan()==null||tacet.reserved()!=0)throw new IllegalStateException("数量页不在本轮可提交状态");
        if(action.scene.equals("daily-reserve-exchange")){
            if(quantityFrames<2)throw new IllegalStateException("精确兑换数量未连续核验");
            tacet.beforeReserveExchange(ownerUid,tacet.plan().exchangeAmount,now);
            stage=Stage.SUBMITTED;
        }
        lastInputAt=now;
    }
    CombatGesture gesture(Action action){
        if(!action.scene.equals("daily-reserve-drag"))return null;
        if(!action.scene.equals(authorizedScene)||authorizedGesture==null)throw new IllegalStateException("资源拖动缺少本帧路径，不回退为滑条点击");
        return authorizedGesture;
    }
    Action open(String uid){
        if(owned()||tacet==null||!tacet.hasLedger()||tacet.plan()!=null||tacet.reserved()!=0||uid==null)
            throw new IllegalStateException("备用资源准备尚未满足本轮账号、账本与未冻结计划约束");
        proposedUid=uid;
        return new Action(Kind.TAP,"daily-reserve-open","读取已标定补充入口中的实际备用资源；此步不兑换",1907f/2400,65f/1080);
    }
    void applied(Action action){
        switch(action.scene){
            case "daily-reserve-open" -> {
                if(proposedUid==null||owned())throw new IllegalStateException("资源窗口打开动作未归属本轮");
                ownerUid=proposedUid;stage=Stage.SOURCE;selectionSubmitted=false;unconfirmedFrame();
            }
            case "daily-reserve-select" -> {selectionSubmitted=true;unconfirmedFrame();}
            case "daily-reserve-cancel" -> {stage=Stage.CANCELING;unconfirmedFrame();}
            case "daily-reserve-source-confirm" -> {stage=Stage.QUANTITY;unconfirmedFrame();}
            case "daily-reserve-drag" -> {drags++;clearCandidates();}
            case "daily-reserve-increment", "daily-reserve-decrement" -> {adjustments++;clearCandidates();}
            default -> { }
        }
    }
    Action next(Screen s,String confirmedUid,boolean teamVerified){return next(s,confirmedUid,teamVerified,-1,-1);}
    Action next(Screen s,String confirmedUid,boolean teamVerified,long capturedAt,long now){
        Integer previousOrdinary=candidateOrdinary,previousReserve=candidateReserve;
        int previousQuantityFrames=quantityFrames;
        clearCandidates();authorizedScene=null;authorizedAction=null;authorizedGesture=null;
        boolean layout=s.aspect>2.18f&&s.aspect<2.26f;
        boolean title=layout&&at(s,"补充结晶波片",590,180,790,235)!=null;
        boolean instruction=layout&&at(s,"请选择补充方式",1080,270,1310,325)!=null;
        boolean source=title&&instruction;
        boolean quantityHint=layout&&at(s,"兑换结晶波片",590,180,790,235)!=null;
        if(!owned())return title||instruction||quantityHint?new Action(Kind.BACK,"daily-reserve-unowned-back","遗留资源窗口未获本轮操作权限，返回后重新核验队伍",0,0):null;
        if(s.automationPanelVisible()){unconfirmedFrame();return Action.waitFor("capture-overlay-visible","资源帧含浮窗，不使用本帧余额或按钮");}
        if(!layout){unconfirmedFrame();return Action.stop("资源准备画面不是已标定20:9布局，未操作");}
        String uid=AccountDailyConfig.detectUid(s);
        if(!teamVerified||!ownerUid.equals(confirmedUid)||uid!=null&&!ownerUid.equals(uid)){
            unconfirmedFrame();return Action.stop("资源准备账号或队伍发生变化，保留计划且不兑换");
        }
        if(tacet==null||!tacet.hasLedger()||tacet.reserved()!=0){unconfirmedFrame();return Action.stop("资源准备缺少本轮账本或有未核销扣费，未兑换");}
        if(uid==null){unconfirmedFrame();return Action.waitFor("daily-reserve-account-wait","等待本轮同账号标记，不沿用旧账号资源证据");}
        if(stage==Stage.QUANTITY||stage==Stage.SUBMITTED){
            boolean fresh=capturedAt>=0&&capturedAt>lastFrame&&capturedAt>lastInputAt&&capturedAt<=now&&now-capturedAt<=1500;
            boolean consecutive=lastFrame>=0&&capturedAt-lastFrame<=2500;
            if(capturedAt>=0&&capturedAt<=now&&capturedAt>lastFrame)lastFrame=capturedAt;
            if(!fresh){unconfirmedFrame();return Action.waitFor("daily-reserve-quantity-wait","等待提交后递增的新鲜资源画面，不重复使用旧截图");}
            if(stage==Stage.SUBMITTED){
                if(quantityHint||s.has("兑换次数|结晶单质[xX×]")){unconfirmedFrame();return Action.waitFor("daily-reserve-funding-wait","兑换已提交一次，数量页尚未离开；不重确认、不盲退");}
                int[] balances=null;
                if(source&&at(s,"取消",775,750,860,805)!=null&&at(s,"确认",1518,748,1630,808)!=null
                        &&at(s,"提取等量结晶单质",1078,568,1314,620)!=null){
                    balances=headerBalances(s);Integer card=number(s,1035,525,1089,562);
                    if(balances!=null&&(card==null||card!=balances[1]))balances=null;
                }else if(!title&&!instruction&&s.one(".*素材获取",0,0,.25f,.15f)!=null
                        &&at(s,"取消",775,750,860,805)==null&&at(s,"确认",1518,748,1630,808)==null)balances=materialBalances(s);
                if(balances==null){unconfirmedFrame();return Action.waitFor("daily-reserve-funding-wait","仅从完整来源页双处备用或明确素材页双余额核验到账");}
                if(!tacet.observeFunding(uid,balances[0],balances[1],capturedAt,now))return Action.waitFor("daily-reserve-funding-wait","等待精确普通增加、备用等量减少连续两帧；未记为消费");
                if(source){stage=Stage.CANCELING;return Action.tap("daily-reserve-cancel",at(s,"取消",775,750,860,805));}
                stage=Stage.IDLE;ownerUid=null;return null;
            }
            Quantity quantity=quantity(s);
            if(quantity==null){unconfirmedFrame();return Action.waitFor("daily-reserve-quantity-wait","等待完整数量页及四处一致的1:1兑换数量，不用顶部模糊旧余额");}
            int needed=tacet.plan().exchangeAmount;
            if(needed<1||needed>quantity.maximum)return Action.stop("冻结兑换差额超出实测滑条上限，未确认");
            if(quantity.amount==needed){
                quantityFrames=consecutive?previousQuantityFrames+1:1;
                if(quantityFrames<2)return Action.waitFor("daily-reserve-quantity-check","再次确认四处数量均为精确差额，再允许单次兑换");
                return authorize(Action.tap("daily-reserve-exchange",at(s,"确认",1518,748,1630,808)),capturedAt);
            }
            int difference=needed-quantity.amount;
            if(Math.abs(difference)<=3){
                if(adjustments>=5)return Action.stop("数量微调已达五次上限，未确认兑换");
                return authorize(new Action(Kind.TAP,difference>0?"daily-reserve-increment":"daily-reserve-decrement","按实测±按钮微调一次后重读数量",(difference>0?1595f:669f)/2400,659f/1080),capturedAt);
            }
            if(drags>=1||quantity.maximum<=1)return Action.stop("一次拖动后数量偏差仍超过3或滑条无有效范围，未再次盲拖或确认");
            float start=slider(quantity.amount,quantity.maximum),end=slider(needed,quantity.maximum);
            Action drag=authorize(new Action(Kind.TAP,"daily-reserve-drag","按本帧数量与最大值执行一次450ms拖动，再精读数量",end/2400,659f/1080),capturedAt);
            authorizedGesture=new CombatGesture(2400,1080,new CombatGesture.Contact(start/2400,659f/1080,end/2400,659f/1080,0,450));
            return drag;
        }
        boolean sourceFragments=title||instruction||s.has("提取等量结晶单质")
                ||at(s,"取消",775,750,860,805)!=null||at(s,"确认",1518,748,1630,808)!=null;
        if(stage==Stage.CANCELING&&!sourceFragments&&s.one(".*素材获取",0,0,.25f,.15f)!=null){stage=Stage.IDLE;ownerUid=null;return null;}
        if(!source)return Action.waitFor("daily-reserve-source-wait","等待本轮完整资源来源窗口，不操作其他页面");
        Text cancel=at(s,"取消",775,750,860,805),confirm=at(s,"确认",1518,748,1630,808);
        if(cancel==null||confirm==null)return Action.waitFor("daily-reserve-source-wait","等待来源标题、选择方式、取消与确认组合完整，不点击残缺窗口");
        if(stage==Stage.CANCELING)return Action.tap("daily-reserve-cancel",cancel);
        if(!selectionSubmitted)return new Action(Kind.TAP,"daily-reserve-select","仅选择实测绿色备用结晶单质卡，不选择星声或溶剂",1018f/2400,472f/1080);
        if(at(s,"提取等量结晶单质",1078,568,1314,620)==null)
            return Action.waitFor("daily-reserve-selection-wait","等待绿色备用卡实际选中说明，不以点击成功代替资源类型核验");
        Integer card=number(s,1035,525,1089,562),reserve=number(s,1377,48,1445,84);
        Text ordinaryText=at(s,"[0-9]{1,3}/240",1598,46,1733,85);
        Integer ordinary=ordinaryText==null?null:Integer.valueOf(ordinaryText.value.split("/")[0]);
        if(card==null||reserve==null||!card.equals(reserve)||ordinary==null||ordinary>240)
            return Action.waitFor("daily-reserve-balance-wait","等待备用卡与顶部备用余额一致，及顶部普通波片余额；不混用其他资源数字");
        candidateOrdinary=ordinary;candidateReserve=reserve;
        if(!ordinary.equals(previousOrdinary)||!reserve.equals(previousReserve))
            return Action.waitFor("daily-reserve-plan-check","再次核验同账号普通与备用两处余额，尚未兑换");
        if(tacet.plan()==null)tacet.freezePlan(ordinary,reserve);
        DailyStaminaPlan plan=tacet.plan();
        if(ordinary!=plan.ordinaryBefore||reserve!=plan.reserveBefore)return Action.stop("冻结后资源余额变化，未继续提交来源确认");
        return plan.exchangeAmount==0?Action.tap("daily-reserve-cancel",cancel):Action.tap("daily-reserve-source-confirm",confirm);
    }
    private static Text at(Screen s,String regex,int l,int t,int r,int b){return s.one(regex,l/2400f,t/1080f,r/2400f,b/1080f);}
    private static Integer number(Screen s,int l,int t,int r,int b){
        if(!s.hasOne("[0-9]{1,4}",l/2400f,t/1080f,r/2400f,b/1080f))return null;
        return Integer.valueOf(at(s,"[0-9]{1,4}",l,t,r,b).value);
    }
    private Action authorize(Action action,long captured){authorizedScene=action.scene;authorizedAction=action;authorizedCaptured=captured;return action;}
    private static float slider(int value,int maximum){return 740f+(value-1f)/(maximum-1f)*(1498-740);}
    private static int[] headerBalances(Screen s){
        Integer reserve=number(s,1377,48,1445,84);Text ordinary=at(s,"[0-9]{1,3}/240",1598,46,1733,85);
        if(reserve==null||ordinary==null)return null;int n=Integer.parseInt(ordinary.value.split("/")[0]);return n<=240?new int[]{n,reserve}:null;
    }
    private static int[] materialBalances(Screen s){
        Integer reserve=number(s,1500,45,1580,85),ordinary=DailyRules.waveplates(s);
        return reserve==null||ordinary==null?null:new int[]{ordinary,reserve};
    }
    static final class Quantity {final int amount,maximum;Quantity(int amount,int maximum){this.amount=amount;this.maximum=maximum;}}
    static Quantity quantity(Screen s){
        if(at(s,"兑换结晶波片",590,180,790,235)==null||at(s,"取消",775,750,860,805)==null||at(s,"确认",1518,748,1630,808)==null)return null;
        Text left=at(s,"结晶单质[xX×][0-9]{1,3}",855,475,1045,525),right=at(s,"结晶波片[xX×][0-9]{1,3}",1355,475,1548,525);
        Text count=at(s,"兑换次数[:：]?[0-9]{1,3}",725,598,905,642);Integer cost=number(s,1452,605,1508,640),maximum=number(s,1508,642,1570,679);
        if(left==null||right==null||count==null||cost==null||maximum==null||maximum<1||maximum>240)return null;
        int a=Integer.parseInt(left.value.replaceAll("[^0-9]","")),b=Integer.parseInt(right.value.replaceAll("[^0-9]","")),c=Integer.parseInt(count.value.replaceAll("[^0-9]",""));
        return a>=1&&a<=maximum&&a==b&&a==c&&a==cost?new Quantity(a,maximum):null;
    }
}
