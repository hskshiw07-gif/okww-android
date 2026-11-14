package io.github.okww.weekly;

import java.util.List;
import static io.github.okww.weekly.GardenRules.*;

/** Conservative mobile text navigation. Unlabelled icons and off-screen accounts need phone evidence, not PC coordinates. */
final class AccountSwitchRules {
    enum Stage { EXIT, CONFIRM_EXIT, LOGIN, LIST, SELECTED, LOADING }
    static final class Label {
        final String raw;
        final Text box;
        Label(String raw,Text box){this.raw=raw;this.box=box;}
    }
    static final class Identity {
        private String candidate;
        private long last=-1;
        private int frames;
        String observe(Screen screen,long captured,long now){
            boolean fresh=captured>=0&&captured>last&&captured<=now&&now-captured<=3500;
            String uid=AccountDailyConfig.detectUid(screen);
            boolean consecutive=last>=0&&captured-last<=5000;
            if(captured>last&&captured<=now)last=captured;
            if(!fresh||screen.automationPanelVisible()||!worldHud(screen)||uid==null){candidate=null;frames=0;return null;}
            frames=consecutive&&uid.equals(candidate)?frames+1:1;candidate=uid;
            return frames>=2?uid:null;
        }
    }
    private final String sourceUid;
    private final MultiAccountPlan.Account target;
    private final long started;
    private long phaseAt,lastFrame=-1;
    private Stage stage=Stage.EXIT;
    private boolean sourceVerified;
    private final Identity sourceIdentity=new Identity();
    private final Identity identity=new Identity();
    private Action offered;
    AccountSwitchRules(String sourceUid,MultiAccountPlan.Account target,long now){
        if(sourceUid==null||!sourceUid.matches("[0-9]{9}")||target==null||sourceUid.equals(target.uid))
            throw new IllegalArgumentException("切号须有不同的已核验来源与目标 UID");
        this.sourceUid=sourceUid;this.target=target;started=phaseAt=now;
    }
    Stage stage(){return stage;}
    Action next(Screen s,List<Label> raw,long captured,long now){
        return next(s,raw,captured,now,null);
    }
    Action next(Screen s,List<Label> raw,long captured,long now,PortraitMatcher.Pixels pixels){
        offered=null;
        if(now<phaseAt||now-started>6*60*1000)throw new IllegalStateException("切号时钟异常或超过6分钟");
        if(now-phaseAt>(stage==Stage.LOADING||stage==Stage.LOGIN?180000:30000))
            throw new IllegalStateException("切号页面未推进："+stage+"；保存现场，不猜图标或账号位置");
        if(captured<=lastFrame||captured>now||now-captured>3500){
            sourceIdentity.observe(s,captured,now);identity.observe(s,captured,now);return waitFor("丢弃重复或陈旧切号帧");
        }
        lastFrame=captured;
        if(!(s.aspect>2.18f&&s.aspect<2.26f)||s.automationPanelVisible()){
            sourceIdentity.observe(s,captured,now);identity.observe(s,captured,now);return waitFor("等待已适配横屏和浮窗隐藏后的画面");
        }
        if(s.has("验证码|输入密码|滑动验证|实名认证|扫码登录|确认购买|兑换波片|补充波片"))
            return Action.stop("当前页面需要用户处理或涉及资源消费，多账号未继续输入");
        String uid=AccountDailyConfig.detectUid(s);
        if(stage==Stage.LOADING){
            String stable=identity.observe(s,captured,now);
            if(stable!=null){
                if(!target.uid.equals(stable))throw new IllegalStateException("登录后的 UID 不匹配目标账号，停止而非使用旧配置");
                return new Action(Kind.DONE,"multi-account-verified","登录后连续两帧核验目标 UID",0,0);
            }
            Text connect=s.one("点击连接",.35f,.80f,.65f,1);
            if(connect!=null&&s.has("(?i)CN_?Android_?Product")&&s.has("CADPA"))return offer(Action.tap("multi-connect",connect));
            return Action.waitFor("game-loading","已提交一次登录，等待目标 UID；不重复登录");
        }
        if(uid!=null&&!uid.equals(sourceUid))throw new IllegalStateException("退出前账号已变化，停止切号");
        if(stage==Stage.CONFIRM_EXIT){
            if(s.one("提示|退出登录|返回登录",.15f,.15f,.85f,.65f)!=null
                    &&s.has(".*(?:是否|确认).*(?:退出登录|返回登录|返回登录界面).*")
                    &&s.one("取消",.15f,.35f,.85f,.90f)!=null){
                Text confirm=s.one("确认|确定",.15f,.35f,.85f,.90f);
                if(confirm!=null)return offer(Action.tap("multi-confirm-exit",confirm));
            }
            return waitFor("等待明确的返回登录确认框，未点击其它确认按钮");
        }
        if(stage==Stage.EXIT){
            if(!sourceVerified){
                String stable=sourceIdentity.observe(s,captured,now);
                sourceVerified=sourceUid.equals(stable);
            }
            if(worldHud(s)&&sourceUid.equals(uid)&&sourceVerified){
                Action terminal=new DailyRules().next(s);
                if(terminal.scene.equals("daily-world-terminal"))return offer(new Action(Kind.TAP,"multi-terminal",terminal.description,terminal.x,terminal.y));
            }
            boolean terminal=s.has("终端")&&s.has("索拉指南|数据坞");
            boolean settings=s.one("设置|设定",0,0,.40f,.20f)!=null&&s.has("声音|图像|控制|画面");
            if(terminal||settings){
                if(!sourceVerified)return offer(new Action(Kind.BACK,"multi-source-back","返回大世界，重新连续核验本次切号的来源 UID",0,0));
                Text exit=s.one("退出登录|返回登录|返回登录界面");
                if(exit!=null)return offer(Action.tap("multi-request-exit",exit));
                Text power=AccountSwitchVision.exit(s,pixels);
                if(power!=null)return offer(Action.tap("multi-request-exit",power));
                if(terminal){Text button=s.one("设置|设定");if(button!=null)return offer(Action.tap("multi-settings",button));}
                return waitFor("退出入口若仅有图标，需要手机标定；未套用 PC 退出坐标");
            }
            if(s.one(".*(?:活跃行迹|素材获取|漂泊日志|邮件|先约电台)",0,0,.35f,.18f)!=null)
                return offer(new Action(Kind.BACK,"multi-menu-back","从已知每日菜单返回，再核验大世界",0,0));
            return waitFor("当前不是已识别的大世界/菜单，未退出未知页面");
        }
        boolean listVisible=s.one("选择账号|账号列表|切换账号",0,0,1,.65f)!=null;
        boolean list=stage==Stage.LIST&&listVisible;
        boolean login=s.has("鸣潮|账号登录|用户中心")&&s.one("登录|进入游戏",0,.20f,1,1)!=null;
        Text match=null;
        if(list||login)for(Label item:raw)if(MultiAccountPlan.key(item.raw).equals(MultiAccountPlan.key(target.login))){
            if(match!=null)throw new IllegalStateException("目标账号出现多个候选，未按列表顺序猜测");match=item.box;
        }
        if(list){
            if(match!=null)return offer(Action.tap("multi-select-account",match));
            return waitFor("列表尚未显示完整目标账号；未猜滚动位置或选择相似账号");
        }
        if(listVisible)return waitFor("账号列表仍展开，未将列表行当作已选中账号");
        if(login){
            if(selectedAccount(raw)){
                // Full account text and login button must survive the engine's two fresh-frame guard together.
                Text button=s.one("登录|进入游戏",0,.20f,1,1);
                return offer(Action.tap("multi-login-"+target.uid,button));
            }
            if(stage==Stage.SELECTED)return waitFor("点击账号后尚未核验选中字段，不提交登录");
            Text change=s.one("切换账号|选择账号|账号管理");
            if(change!=null)return offer(Action.tap("multi-open-accounts",change));
        }
        return waitFor("等待明确账号列表/登录页；未知页面保留现场");
    }
    private boolean selectedAccount(List<Label> raw){
        String selected=null;
        for(Label label:raw){
            java.util.regex.Matcher field=java.util.regex.Pattern.compile("^(?:当前账号|登录账号)[:：]\\s*(.+)$").matcher(label.raw);
            if(field.matches()){
                if(selected!=null)throw new IllegalStateException("已选中账号字段不唯一，未提交登录");
                selected=MultiAccountPlan.key(field.group(1));
            }
        }
        // Unlabelled selected-account controls await real phone calibration; an arbitrary matching row is insufficient.
        return MultiAccountPlan.key(target.login).equals(selected);
    }
    void beforeInput(Action action,long now){
        if(offered!=action||lastFrame<0||now<lastFrame||now-lastFrame>3500)
            throw new IllegalStateException("切号识别到输入之间画面已过期，停止并保存同帧证据");
    }
    void applied(Action action,long now){
        if(offered!=action)throw new IllegalStateException("切号动作不是当前识别帧的候选");
        Stage next=switch(action.scene){
            case "multi-request-exit" -> Stage.CONFIRM_EXIT;
            case "multi-confirm-exit" -> Stage.LOGIN;
            case "multi-open-accounts" -> Stage.LIST;
            case "multi-select-account" -> Stage.SELECTED;
            default -> action.scene.equals("multi-login-"+target.uid)?Stage.LOADING:stage;
        };
        if(next!=stage){stage=next;phaseAt=now;}
        offered=null;
    }
    private Action offer(Action action){offered=action;return action;}
    private Action waitFor(String reason){return Action.waitFor("multi-account-"+stage.name().toLowerCase(java.util.Locale.ROOT),reason);}
}
