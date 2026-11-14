package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static io.github.okww.weekly.GardenRules.*;
import static org.junit.Assert.*;

public class AccountSwitchRulesTest {
    long now=100;
    Text t(String s,float x,float y){return new Text(s,x-.01f,y-.01f,x+.01f,y+.01f);}
    Screen screen(Text... text){return new Screen(new ArrayList<>(Arrays.asList(text)),new int[0],2400f/1080);}
    Screen world(String uid){return screen(t("Lv.90",.58f,.94f),t("特征码:"+uid,.90f,.98f));}
    List<AccountSwitchRules.Label> labels(String... values){
        List<AccountSwitchRules.Label> out=new ArrayList<>();int i=0;
        for(String s:values)out.add(new AccountSwitchRules.Label(s,t(s,.5f,.3f+.1f*i++)));return out;
    }
    Action next(AccountSwitchRules r,Screen s,List<AccountSwitchRules.Label> raw){now+=500;return r.next(s,raw,now,now+100);}
    void apply(AccountSwitchRules r,Action a){assertEquals(Kind.TAP,a.kind);r.applied(a,now+100);}
    void verifySource(AccountSwitchRules r){
        assertEquals(Kind.WAIT,next(r,world("111111111"),List.of()).kind);
        apply(r,next(r,world("111111111"),List.of()));
    }
    AccountSwitchRules toLogin(){
        AccountSwitchRules r=new AccountSwitchRules("111111111",MultiAccountDailyTest.B,0);
        verifySource(r);
        apply(r,next(r,screen(t("终端",.1f,.1f),t("索拉指南",.7f,.5f),t("返回登录",.2f,.8f)),List.of()));
        apply(r,next(r,screen(t("提示",.5f,.3f),t("是否返回登录界面",.5f,.45f),t("确认",.65f,.65f),t("取消",.35f,.65f)),List.of()));
        assertEquals(AccountSwitchRules.Stage.LOGIN,r.stage());return r;
    }
    Screen login(){return screen(t("鸣潮",.1f,.1f),t("登录",.6f,.75f),t("切换账号",.8f,.8f));}
    AccountSwitchRules toLoading(){
        AccountSwitchRules r=toLogin();apply(r,next(r,login(),labels("当前账号:"+MultiAccountDailyTest.B.login)));
        assertEquals(AccountSwitchRules.Stage.LOADING,r.stage());return r;
    }
    @Test public void onlyConfirmedTargetLoginThenTwoFreshWorldFramesComplete(){
        AccountSwitchRules r=toLogin();
        apply(r,next(r,login(),labels("wrong-account")));assertEquals(AccountSwitchRules.Stage.LIST,r.stage());
        Screen list=screen(t("选择账号",.5f,.2f));
        apply(r,next(r,list,labels(MultiAccountDailyTest.B.login)));assertEquals(AccountSwitchRules.Stage.SELECTED,r.stage());
        assertEquals(Kind.WAIT,next(r,login(),labels("wrong-account")).kind);
        apply(r,next(r,login(),labels("当前账号:"+MultiAccountDailyTest.B.login)));
        assertEquals(Kind.WAIT,next(r,world("222222222"),List.of()).kind);
        assertEquals(Kind.DONE,next(r,world("222222222"),List.of()).kind);
    }
    @Test public void matchingRowIsNotASelectedFieldAndOpenListCannotSubmitLogin(){
        AccountSwitchRules r=toLogin();
        assertFalse(next(r,login(),labels(MultiAccountDailyTest.B.login,"当前账号:wrong")).scene.startsWith("multi-login-"));
        apply(r,next(r,login(),labels("other")));
        Screen list=screen(t("选择账号",.5f,.2f),t("鸣潮",.1f,.1f),t("登录",.6f,.75f));
        apply(r,next(r,list,labels(MultiAccountDailyTest.B.login)));
        assertEquals(Kind.WAIT,next(r,list,labels("当前账号:"+MultiAccountDailyTest.B.login)).kind);
        assertEquals(Kind.WAIT,next(r,login(),labels(MultiAccountDailyTest.B.login)).kind);
    }
    @Test public void oldWrongUidAfterLoginIsNotAllowedToRunDaily(){
        AccountSwitchRules r=toLoading();next(r,world("111111111"),List.of());
        assertThrows(IllegalStateException.class,()->next(r,world("111111111"),List.of()));
    }
    @Test public void repeatedStaleOrGapFramesDoNotConfirmIdentity(){
        AccountSwitchRules.Identity id=new AccountSwitchRules.Identity();Screen s=world("222222222");
        assertNull(id.observe(s,100,200));assertNull(id.observe(s,100,300));assertNull(id.observe(s,500,600));
        assertEquals("222222222",id.observe(s,1000,1100));
        assertNull(id.observe(s,2000,6000));assertNull(id.observe(s,7000,7100));
        assertNull(id.observe(s,13000,13100));
    }
    @Test public void unknownFrameBetweenUidFramesResetsTheStreak(){
        AccountSwitchRules r=toLoading();next(r,world("222222222"),List.of());next(r,screen(),List.of());
        assertEquals(Kind.WAIT,next(r,world("222222222"),List.of()).kind);
        assertEquals(Kind.DONE,next(r,world("222222222"),List.of()).kind);
    }
    @Test public void noFuzzyAccountMatchingOrDuplicateCandidateSelection(){
        AccountSwitchRules r=toLogin();apply(r,next(r,login(),labels("other")));
        Screen list=screen(t("账号列表",.5f,.2f));
        assertEquals(Kind.WAIT,next(r,list,labels("ab****cd@example.com")).kind);
        assertThrows(IllegalStateException.class,()->next(r,list,labels(MultiAccountDailyTest.B.login,MultiAccountDailyTest.B.login)));
    }
    @Test public void textOnlyExitNeedsMenuContextAndConfirmationBody(){
        AccountSwitchRules r=new AccountSwitchRules("111111111",MultiAccountDailyTest.B,0);
        assertEquals(Kind.WAIT,next(r,screen(t("返回登录",.5f,.5f)),List.of()).kind);
        verifySource(r);
        apply(r,next(r,screen(t("终端",.1f,.1f),t("索拉指南",.7f,.5f),t("返回登录",.2f,.8f)),List.of()));
        assertEquals(Kind.WAIT,next(r,screen(t("提示",.5f,.3f),t("确认",.65f,.65f),t("取消",.35f,.65f)),List.of()).kind);
    }
    @Test public void verificationPurchaseAndPasswordScreensStopWithoutTouch(){
        for(String danger:List.of("短信验证码","输入密码","确认购买","兑换波片")){
            AccountSwitchRules r=toLogin();Screen s=login();s.words.add(t(danger,.5f,.5f));
            assertEquals(Kind.STOP,next(r,s,labels(MultiAccountDailyTest.B.login)).kind);
        }
    }
    @Test public void missingExitIconAndOffscreenAccountHaveBoundedWait(){
        AccountSwitchRules r=new AccountSwitchRules("111111111",MultiAccountDailyTest.B,0);
        verifySource(r);
        assertEquals(Kind.WAIT,next(r,screen(t("终端",.1f,.1f),t("索拉指南",.7f,.5f)),List.of()).kind);
        assertThrows(IllegalStateException.class,()->r.next(screen(),List.of(),31000,31000));
        AccountSwitchRules list=toLogin();apply(list,next(list,login(),labels("other")));
        assertEquals(Kind.WAIT,next(list,screen(t("账号列表",.5f,.2f)),labels("other")).kind);
    }
    @Test public void menuWithoutFreshSourceEvidenceReturnsToWorldRatherThanExiting(){
        AccountSwitchRules r=new AccountSwitchRules("111111111",MultiAccountDailyTest.B,0);
        Screen terminal=screen(t("终端",.1f,.1f),t("索拉指南",.7f,.5f),t("返回登录",.2f,.8f));
        Action back=next(r,terminal,List.of());assertEquals(Kind.BACK,back.kind);r.applied(back,now+100);
        assertEquals(Kind.WAIT,next(r,world("111111111"),List.of()).kind);
        assertEquals(Kind.BACK,next(r,terminal,List.of()).kind);
        verifySource(r);
        assertEquals("multi-request-exit",next(r,terminal,List.of()).scene);
        assertThrows(IllegalStateException.class,()->next(r,world("333333333"),List.of()));
    }
    @Test public void staleOrDifferentActionCannotAdvanceTheState(){
        AccountSwitchRules r=new AccountSwitchRules("111111111",MultiAccountDailyTest.B,0);
        Action a=next(r,world("111111111"),Collections.emptyList());
        next(r,screen(),List.of());assertThrows(IllegalStateException.class,()->r.applied(a,now));
    }
    @Test public void slowPersistenceCannotSubmitAnExpiredLoginAction(){
        AccountSwitchRules r=toLogin();Action login=next(r,login(),labels("当前账号:"+MultiAccountDailyTest.B.login));
        r.beforeInput(login,now+100);
        assertThrows(IllegalStateException.class,()->r.beforeInput(login,now+3501));
        assertEquals(AccountSwitchRules.Stage.LOGIN,r.stage());
        assertThrows(IllegalStateException.class,()->r.beforeInput(login,now-1));
        next(r,screen(),List.of());assertThrows(IllegalStateException.class,()->r.beforeInput(login,now+100));
    }
    @Test public void connectButtonOnlyAfterSubmittedTargetLogin(){
        Screen connect=screen(t("点击连接",.5f,.9f),t("CN_Android_Product",.5f,.1f),t("CADPA",.1f,.9f));
        AccountSwitchRules before=toLogin();assertEquals(Kind.WAIT,next(before,connect,List.of()).kind);
        AccountSwitchRules after=toLoading();assertEquals("multi-connect",next(after,connect,List.of()).scene);
    }
}
