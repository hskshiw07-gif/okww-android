package io.github.okww.weekly;

import java.util.*;
import static io.github.okww.weekly.GardenRules.*;

/** One code: navigate, fill, prove exact text, submit once, require an explicit post-submit result. */
final class RedeemRules {
    enum Stage { NAVIGATE, INPUT, SUBMITTED, DISMISS, RETURN, DONE }
    final String uid,code;
    private final long started;
    private long frame=-1,submittedAt=-1;
    private Stage stage=Stage.NAVIGATE;
    private boolean filled;
    private Action offered;
    private RedeemLedger.Status result,candidate;
    private int resultFrames;
    private int settingsScrolls;
    private int inputFrames;
    RedeemRules(String uid,String code,long now){
        if(uid==null||!uid.matches("[0-9]{9}"))throw new IllegalArgumentException("兑换缺少已核验UID");
        this.uid=uid;this.code=RedeemCodes.normalize(code);started=now;
    }
    Stage stage(){return stage;}
    RedeemLedger.Status result(){return result;}
    boolean submitted(){return submittedAt>=0;}
    static boolean dialog(Screen s){return s.one("兑换码|礼包兑换|兑换礼包码",.15f,.10f,.85f,.55f)!=null
            &&s.one("取消",.1f,.3f,.9f,.9f)!=null&&s.one("确认|确定|兑换",.1f,.3f,.9f,.9f)!=null;}
    Action next(Screen s,List<AccountSwitchRules.Label> raw,long captured,long now,Text settingsIcon,Text pasteIcon){
        offered=null;
        int previousInputFrames=inputFrames;inputFrames=0;
        if(now<started||now-started>120000||submittedAt>=0&&result==null&&now-submittedAt>20000)
            throw new IllegalStateException("兑换页面或结果超时；不把未知结果记为成功");
        if(captured<=frame||captured>now||now-captured>3500){candidate=null;resultFrames=0;return waitFor("丢弃陈旧兑换帧");}
        long previous=frame;frame=captured;
        if(!(s.aspect>2.18f&&s.aspect<2.26f)||s.automationPanelVisible()){candidate=null;resultFrames=0;return waitFor("等待已适配横屏及浮窗隐藏");}
        String observed=AccountDailyConfig.detectUid(s);
        if(observed!=null&&!uid.equals(observed))throw new IllegalStateException("兑换期间UID变化，停止使用旧账号");
        if(s.has("验证码|输入密码|确认购买|补充波片|兑换波片|支付|删除邮件"))return Action.stop("兑换页出现验证或资源操作，未继续");
        RedeemLedger.Status status=result(s);
        if(stage==Stage.SUBMITTED){
            if(status==null){candidate=null;resultFrames=0;return waitFor("已提交一次，等待明确兑换结果");}
            resultFrames=previous>=0&&captured-previous<=5000&&status==candidate?resultFrames+1:1;candidate=status;
            if(resultFrames<2)return waitFor("再次核验兑换结果，不重复确认");
            result=status;stage=Stage.DISMISS;
        }else if(stage==Stage.NAVIGATE||stage==Stage.INPUT){
            if(status!=null)return Action.stop("已有兑换结果不属于本次提交，请先核对后从大世界重启");
        }
        if(stage==Stage.DISMISS){
            if(status!=null){Text close=dialog(s)?null:s.one("确认|确定|关闭",.15f,.25f,.85f,.90f);
                return close==null?offer(new Action(Kind.BACK,"redeem-result-back","关闭本轮已核验结果",0,0)):offer(Action.tap("redeem-result-dismiss",close));}
            stage=Stage.RETURN;
        }
        if(stage==Stage.RETURN){
            if(AccountSwitchVision.terminal(s)||worldHud(s)){stage=Stage.DONE;return new Action(Kind.DONE,"redeem-code-done","本码结果已核验，已返回已知页面",0,0);}
            if(settings(s)||dialog(s))return offer(new Action(Kind.BACK,"redeem-return","返回终端再处理下一码",0,0));
            return waitFor("等待明确设置/终端，不盲退未知页面");
        }
        if(dialog(s)){
            stage=Stage.INPUT;
            if(!filled){
                Text input=s.one("请输入(?:兑换码|礼包码)|输入兑换码",.15f,.20f,.85f,.75f);
                if(input==null)return waitFor("缺少可核验的空兑换输入框，未覆盖已有文本");
                if(pasteIcon==null||pasteIcon.x()<.68f||pasteIcon.x()>.70f||pasteIcon.y()<.44f||pasteIcon.y()>.49f)
                    return waitFor("等待空输入框右侧粘贴图标匹配，不点输入框或猜坐标");
                return offer(Action.tap("redeem-fill",pasteIcon));
            }
            int exact=0;
            for(AccountSwitchRules.Label label:raw)if(label.raw.replaceAll("\\s+","").equalsIgnoreCase(code)&&label.box.x()>.29f&&label.box.x()<.70f&&label.box.y()>.43f&&label.box.y()<.51f)exact++;
            if(exact!=1)return waitFor("等待输入框完整兑换码，不把模糊字母/数字当正确文本");
            inputFrames=previous>=0&&captured-previous<=5000?previousInputFrames+1:1;
            if(inputFrames<2)return waitFor("再次核验粘贴后的完整码，尚未提交");
            return offer(Action.tap("redeem-submit",s.one("确认|确定|兑换",.1f,.3f,.9f,.9f)));
        }
        if(worldHud(s)){
            if(observed==null)return waitFor("等待本账号UID");
            Action terminal=new DailyRules().next(s);
            if(terminal.scene.equals("daily-world-terminal"))return offer(new Action(terminal.kind,"redeem-terminal",terminal.description,terminal.x,terminal.y));
        }
        if(AccountSwitchVision.terminal(s)){
            Text setting=s.one("设置|设定");if(setting==null)setting=settingsIcon;
            return setting==null?waitFor("等待已标定手机设置入口"):offer(Action.tap("redeem-settings",setting));
        }
        if(soundSettings(s))
            return offer(new Action(Kind.TAP,"redeem-other-settings","从已核验手机声音页进入左侧其它设置页签",150f/2400,865f/1080));
        if(settings(s)){
            Text label=s.one("兑换码|礼包码",.05f,.1f,.9f,.95f);
            if(label!=null){
                Text button=null;for(Text item:s.words)if(item.value.matches("兑换|前往兑换")&&item.x()>label.x()&&Math.abs(item.y()-label.y())<.05f){
                    if(button!=null)throw new IllegalStateException("同一兑换码行有多个按钮");button=item;}
                if(button!=null)return offer(Action.tap("redeem-open-dialog",button));
            }
            if(mobileOtherSettings(s)&&label==null)
                return settingsScrolls>=4?Action.stop("其它设置滚动四次仍未找到兑换码行，保留现场")
                        :offer(new Action(Kind.TAP,"redeem-settings-scroll","在已核验其它设置列表内短距滚动查找兑换码",.5f,.8f));
            Text other=s.one("其他设置|其它设置");
            if(other!=null&&label==null)return offer(Action.tap("redeem-other-settings",other));
            return waitFor("兑换码行尚不可见；未套用PC滚动条或默认坐标");
        }
        return waitFor("当前不是已识别兑换页面");
    }
    static boolean pasteDispatchAllowed(long captured,long now,boolean keyboardVisible){
        return now>=captured&&now-captured<=3500&&!keyboardVisible;
    }
    static boolean soundSettings(Screen s){
        return s.aspect>2.18f&&s.aspect<2.26f&&AccountDailyConfig.detectUid(s)!=null
                &&s.one(".?声音",.04f,.02f,.18f,.10f)!=null
                &&s.one("主音量",.10f,.18f,.28f,.27f)!=null
                &&s.one("语音音量",.10f,.28f,.28f,.37f)!=null
                &&s.one("音乐音量",.10f,.39f,.28f,.47f)!=null;
    }
    static boolean settings(Screen s){return soundSettings(s)
            ||s.one(".?设置|.?设定",0,0,.4f,.18f)!=null&&s.has("图像|声音|控制|其他设置|其它设置")
            ||mobileOtherSettings(s);}
    static boolean mobileOtherSettings(Screen s){return s.aspect>2.18f&&s.aspect<2.26f
            &&AccountDailyConfig.detectUid(s)!=null&&s.one(".?其[他它](?:设置)?",0,0,.25f,.15f)!=null
            &&(s.one("兑换码|礼包码",.10f,.10f,.90f,.95f)!=null
                ||s.one("系统消息推[送迭]",.10f,.18f,.28f,.27f)!=null&&(s.has("沉浸模式")&&s.has("显示联机玩家名")
                    ||s.has("沉浸模式|显示联机玩家名")&&s.has("切换漂泊者")));}
    static RedeemLedger.Status result(Screen s){
        Set<RedeemLedger.Status> found=new HashSet<>();
        for(Text text:s.words)if(text.x()>.15f&&text.x()<.85f&&text.y()>.15f&&text.y()<.85f){
            String value=text.value;
            if(value.matches("(?:兑换成功[!！。.]?|请前往邮箱领取奖励[。.]?|兑换成功[,，]请前往邮箱领取奖励[。.]?)"))found.add(RedeemLedger.Status.SUCCESS);
            if(value.matches("(?:该)?兑换码(?:[已己]使用|已被使用|已兑换)[。.]?|(?:该)?礼包(?:已领取|已兑换)[。.]?"))found.add(RedeemLedger.Status.ALREADY_USED);
            if(value.matches("(?:无效兑换码|兑换码无效|兑换码不存在)[。.]?"))found.add(RedeemLedger.Status.INVALID);
            if(value.matches("(?:该)?兑换码已过期[。.]?"))found.add(RedeemLedger.Status.EXPIRED);
            if(value.matches(".*(?:兑换过于频繁|操作过于频繁|稍后再试|网络异常).*"))found.add(RedeemLedger.Status.RETRYABLE);
        }
        if(found.size()>1)throw new IllegalStateException("同帧兑换结果冲突，保留待核验记录");
        return found.isEmpty()?null:found.iterator().next();
    }
    void beforeInput(Action action,long now){
        if(action!=offered||now<frame||now-frame>3500)throw new IllegalStateException("兑换输入候选或新鲜度失效");
        if(action.scene.equals("redeem-submit")&&(stage!=Stage.INPUT||!filled||submittedAt>=0))throw new IllegalStateException("兑换确认重复或缺少输入核验");
    }
    void applied(Action action,long now){
        if(action!=offered)throw new IllegalStateException("兑换动作不属于本帧");
        if(action.scene.equals("redeem-fill"))filled=true;
        if(action.scene.equals("redeem-settings-scroll"))settingsScrolls++;
        if(action.scene.equals("redeem-submit")){submittedAt=now;stage=Stage.SUBMITTED;}
        if(action.scene.equals("redeem-result-dismiss")||action.scene.equals("redeem-result-back"))stage=Stage.RETURN;
        offered=null;
    }
    private Action offer(Action action){offered=action;return action;}
    private Action waitFor(String reason){return Action.waitFor("redeem-"+stage.name().toLowerCase(Locale.ROOT),reason);}
}
