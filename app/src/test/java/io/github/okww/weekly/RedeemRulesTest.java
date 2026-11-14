package io.github.okww.weekly;
import org.junit.Test;
import java.util.*;
import static io.github.okww.weekly.GardenRules.*;
import static org.junit.Assert.*;

public class RedeemRulesTest {
    Screen mobileSound(){return screen(text("h声音",.085f,.062f),text("主音量",.145f,.224f),
            text("语音音量",.15f,.326f),text("音乐音量",.15f,.428f),text("特征码:111111111",.91f,.98f));}
    @Test public void recordedMobileSoundPageNavigatesOnlyWithFullContext(){
        RedeemRules r=new RedeemRules("111111111","TEST10",0);Screen s=mobileSound();
        Action a=next(r,s,List.of());assertEquals("redeem-other-settings",a.scene);
        assertEquals(150f/2400,a.x,0);assertEquals(865f/1080,a.y,0);apply(r,a);
        for(int i=0;i<5;i++){Screen missing=mobileSound();missing.words.remove(i);
            assertEquals(Kind.WAIT,next(r,missing,List.of()).kind);}
        Screen body=mobileSound();body.words.set(0,text("声音",.5f,.4f));
        assertEquals(Kind.WAIT,next(r,body,List.of()).kind);
    }
    @Test public void mobileOtherTitleDoesNotNeedPcParentSettingsLabel(){
        RedeemRules r=new RedeemRules("111111111","TEST10",0);
        Screen s=screen(text("其他设置",.08f,.06f),text("兑换码",.2f,.55f),text("兑换",.85f,.55f),text("特征码:111111111",.91f,.98f));
        assertEquals("redeem-open-dialog",next(r,s,List.of()).scene);
    }
    @Test public void knownMobileOtherListScrollIsBoundedAndNeverChangesSettings(){
        RedeemRules r=new RedeemRules("111111111","TEST10",0);
        Screen s=screen(text("其他设置",.08f,.06f),text("系统消息推送",.2f,.25f),text("沉浸模式",.2f,.4f),
                text("切换漂泊者",.2f,.8f),text("特征码:111111111",.91f,.98f));
        s.words.set(2,text("显示联机玩家名",.2f,.5f));
        for(int i=0;i<4;i++){Action a=next(r,s,List.of());assertEquals("redeem-settings-scroll",a.scene);apply(r,a);}
        assertEquals(Kind.STOP,next(r,s,List.of()).kind);assertFalse(r.submitted());
        s.words.remove(4);assertFalse(RedeemRules.mobileOtherSettings(s));
    }
    @Test public void recordedOtherPageWithMisreadRoverRequiresTwoOtherBodyAnchors(){
        Screen s=screen(text("其他设置",.08f,.06f),text("系统消息推送",.2f,.25f),text("沉浸模式",.2f,.4f),
                text("显示联机玩家名",.2f,.5f),text("切换標泊者",.2f,.8f),text("特征码:111111111",.91f,.98f));
        assertTrue(RedeemRules.mobileOtherSettings(s));
        for(int i:new int[]{0,1,2,3,5}){Screen missing=screen(s.words.toArray(new Text[0]));missing.words.remove(i);
            assertFalse(RedeemRules.mobileOtherSettings(missing));}
    }
    Screen recordedOtherPushOcr() throws Exception {
        List<Text> words=new ArrayList<>();
        try(java.io.InputStream in=getClass().getResourceAsStream("/redeem-other-push-ocr.tsv")){
            assertNotNull(in);
            for(String row:new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("\\R")){
                if(row.isBlank()||row.startsWith("#"))continue;
                String[] c=row.split("\\t");assertEquals(5,c.length);
                words.add(new Text(c[0],Float.parseFloat(c[1])/2400,Float.parseFloat(c[2])/1080,
                        Float.parseFloat(c[3])/2400,Float.parseFloat(c[4])/1080));
            }
        }
        return new Screen(words,new int[0],2400f/1080);
    }
    @Test public void recordedPushTypoScrollsOnlyWithTitleUidAndTwoBodyAnchors() throws Exception {
        Screen s=recordedOtherPushOcr();assertTrue(RedeemRules.mobileOtherSettings(s));
        RedeemRules r=new RedeemRules("111111111","TEST10",0);
        for(int i=0;i<4;i++){Action a=next(r,s,List.of());assertEquals("redeem-settings-scroll",a.scene);apply(r,a);}
        assertEquals(Kind.STOP,next(r,s,List.of()).kind);assertFalse(r.submitted());
        for(String required:List.of("其他设置","系统消息推迭","显示联机玩家名","切换漂泊者","特征码:111111111")){
            Screen missing=recordedOtherPushOcr();missing.words.removeIf(w->w.value.equals(required));
            assertFalse(required,RedeemRules.mobileOtherSettings(missing));
            assertEquals(Kind.WAIT,next(new RedeemRules("111111111","TEST10",0),missing,List.of()).kind);
        }
        Screen misplaced=recordedOtherPushOcr();misplaced.words.removeIf(w->w.value.equals("系统消息推迭"));
        misplaced.words.add(text("系统消息推迭",.65f,.7f));assertFalse(RedeemRules.mobileOtherSettings(misplaced));
        Screen unrelated=recordedOtherPushOcr();unrelated.words.removeIf(w->w.value.equals("系统消息推迭"));
        unrelated.words.add(text("系统消息推迟",.16f,.216f));assertFalse(RedeemRules.mobileOtherSettings(unrelated));
        assertFalse(RedeemRules.mobileOtherSettings(new Screen(s.words,new int[0],16f/9)));
    }
    long time=100;
    Text text(String value,float x,float y){return new Text(value,x-.01f,y-.01f,x+.01f,y+.01f);}
    Screen screen(Text... words){return new Screen(new ArrayList<>(Arrays.asList(words)),new int[0],2400f/1080);}
    Screen dialog(){return screen(text("兑换码",.5f,.2f),text("请输入兑换码",.5f,.45f),text("取消",.35f,.65f),text("确认",.65f,.65f));}
    List<AccountSwitchRules.Label> code(String value){return List.of(new AccountSwitchRules.Label(value,text(value,.5f,.45f)));}
    Action next(RedeemRules rules,Screen screen,List<AccountSwitchRules.Label> raw){time+=500;return rules.next(screen,raw,time,time+80,null,text("粘贴图标",1658.5f/2400,506f/1080));}
    void apply(RedeemRules rules,Action action){rules.beforeInput(action,time+100);rules.applied(action,time+100);}
    RedeemRules submitted(){
        RedeemRules rules=new RedeemRules("111111111","TEST10",0);
        Action fill=next(rules,dialog(),List.of());assertEquals("redeem-fill",fill.scene);apply(rules,fill);
        assertEquals(Kind.WAIT,next(rules,dialog(),code("TEST10")).kind);
        Action submit=next(rules,dialog(),code("TEST10"));assertEquals("redeem-submit",submit.scene);apply(rules,submit);return rules;
    }
    Screen result(String value){return screen(text("兑换提示",.5f,.2f),text(value,.5f,.45f),text("确认",.65f,.65f));}
    @Test public void recordedUsedOcrAliasNeedsTwoPostSubmitFramesInResultArea(){
        RedeemRules r=submitted();Screen s=result("兑换码己使用");
        assertEquals(Kind.WAIT,next(r,s,List.of()).kind);assertNull(r.result());
        next(r,s,List.of());assertEquals(RedeemLedger.Status.ALREADY_USED,r.result());
        assertEquals(Kind.STOP,next(new RedeemRules("111111111","TEST10",0),s,List.of()).kind);
        assertNull(RedeemRules.result(screen(text("兑换码己使用",.1f,.1f))));
        assertNull(RedeemRules.result(result("己使用")));
        assertNull(RedeemRules.result(result("兑换码未使用")));
    }
    @Test public void inlineInvalidResultClosesWithBackNotDisabledSubmit(){
        RedeemRules r=submitted();Screen s=dialog();s.words.add(text("无效兑换码",.5f,.42f));
        assertEquals(Kind.WAIT,next(r,s,List.of()).kind);
        Action close=next(r,s,List.of());assertEquals(Kind.BACK,close.kind);assertEquals("redeem-result-back",close.scene);
        assertEquals(RedeemLedger.Status.INVALID,r.result());apply(r,close);
        Screen other=screen(text("其他设置",.08f,.06f),text("兑换码",.2f,.55f),text("特征码:111111111",.91f,.98f));
        assertEquals("redeem-return",next(r,other,List.of()).scene);
    }
    @Test public void inputMustBeExactlyProvenBeforeOneSubmission(){
        RedeemRules rules=new RedeemRules("111111111","TEST10",0);apply(rules,next(rules,dialog(),List.of()));
        assertEquals(Kind.WAIT,next(rules,dialog(),code("TEST1O")).kind);
        List<AccountSwitchRules.Label> duplicate=new ArrayList<>(code("TEST10"));duplicate.addAll(code("TEST10"));
        assertEquals(Kind.WAIT,next(rules,dialog(),duplicate).kind);
        assertEquals(Kind.WAIT,next(rules,dialog(),code("test10")).kind);
        Action submit=next(rules,dialog(),code("test10"));apply(rules,submit);
        assertEquals(Kind.WAIT,next(rules,dialog(),code("TEST10")).kind);
        assertNull(rules.result());
    }
    @Test public void onlyOcrWhitespaceMayBeRemovedAfterPasteWasRequested(){
        RedeemRules r=new RedeemRules("111111111","WWTEST1794410052327",0);
        assertEquals("redeem-fill",next(r,dialog(),code("WWTEST179441 0052327")).scene);
        apply(r,next(r,dialog(),List.of()));
        assertEquals(Kind.WAIT,next(r,dialog(),code("WWTEST179441 0052327")).kind);
        assertEquals("redeem-submit",next(r,dialog(),code("WWTEST179441 0052327")).scene);
        assertEquals(Kind.WAIT,next(r,dialog(),code("WWTEST179441 OO52327")).kind);
        assertEquals(Kind.WAIT,next(r,dialog(),code("WWTEST179441 005232")).kind);
    }
    @Test public void noErrorOrGenericPromptIsNeverSuccess(){
        RedeemRules rules=submitted();assertEquals(Kind.WAIT,next(rules,result("兑换提示"),List.of()).kind);assertNull(rules.result());
        assertThrows(IllegalStateException.class,()->rules.next(dialog(),List.of(),25000,25000,null,null));
    }
    @Test public void explicitResultsRequireTwoFreshPostSubmitFrames(){
        for(String value:List.of("兑换成功","兑换码已使用","兑换码无效","兑换码已过期","操作过于频繁，请稍后再试")){
            RedeemRules rules=submitted();assertEquals(Kind.WAIT,next(rules,result(value),List.of()).kind);
            assertNull(rules.result());Action dismiss=next(rules,result(value),List.of());assertEquals("redeem-result-dismiss",dismiss.scene);
            assertNotNull(rules.result());apply(rules,dismiss);
            Screen world=screen(text("Lv.90",.58f,.94f),text("特征码:111111111",.9f,.98f));
            assertEquals(Kind.DONE,next(rules,world,List.of()).kind);
        }
    }
    @Test public void oldResultsWrongUidConflictingResultsAndPurchasesStop(){
        RedeemRules fresh=new RedeemRules("111111111","TEST10",0);assertEquals(Kind.STOP,next(fresh,result("兑换成功"),List.of()).kind);
        RedeemRules rules=submitted();Screen wrong=result("兑换成功");wrong.words.add(text("特征码:222222222",.9f,.98f));
        assertThrows(IllegalStateException.class,()->next(rules,wrong,List.of()));
        Screen conflict=result("兑换成功");conflict.words.add(text("兑换码无效",.5f,.5f));assertThrows(IllegalStateException.class,()->next(rules,conflict,List.of()));
        assertEquals(Kind.STOP,next(rules,result("确认购买"),List.of()).kind);
    }
    @Test public void staleOrMissingResultFrameBreaksConfirmation(){
        RedeemRules rules=submitted();Screen success=result("兑换成功");next(rules,success,List.of());
        assertEquals(Kind.WAIT,rules.next(success,List.of(),time,time+100,null,null).kind);
        assertEquals(Kind.WAIT,next(rules,success,List.of()).kind);next(rules,screen(),List.of());
        assertEquals(Kind.WAIT,next(rules,success,List.of()).kind);assertNull(rules.result());
        next(rules,success,List.of());assertEquals(RedeemLedger.Status.SUCCESS,rules.result());
    }
    @Test public void slowWriteCannotDriveExpiredConfirmation(){
        RedeemRules rules=new RedeemRules("111111111","TEST10",0);apply(rules,next(rules,dialog(),List.of()));
        assertEquals(Kind.WAIT,next(rules,dialog(),code("TEST10")).kind);
        Action submit=next(rules,dialog(),code("TEST10"));assertThrows(IllegalStateException.class,()->rules.beforeInput(submit,time+3501));
        assertFalse(rules.submitted());
    }
    @Test public void onlySameRedeemCodeRowCanSelectRedeemButton(){
        RedeemRules rules=new RedeemRules("111111111","TEST10",0);
        Screen settings=screen(text("设置",.1f,.06f),text("其他设置",.15f,.4f),text("兑换码",.45f,.6f),text("兑换",.8f,.3f));
        assertEquals(Kind.WAIT,next(rules,settings,List.of()).kind);
        settings.words.add(text("兑换",.8f,.6f));assertEquals("redeem-open-dialog",next(rules,settings,List.of()).scene);
    }
    @Test public void mailOnlyReusesFreeMailRulesAndDoesNotDeleteOrVisitPass(){
        RedeemMailRules rules=new RedeemMailRules();Screen mail=screen(text("邮件",.08f,.06f),text("全部领取",.2f,.9f),text("全部删除",.4f,.9f));
        Action claim=rules.next(mail);assertEquals("daily-claim-mail",claim.scene);rules.applied(claim);
        Screen done=screen(text("邮件",.08f,.06f),text("全部附件已领取",.5f,.2f));
        assertEquals(Kind.WAIT,rules.next(done).kind);Action back=rules.next(done);assertEquals("daily-mail-back",back.scene);rules.applied(back);
        assertTrue(rules.verified());Screen world=screen(text("Lv.90",.58f,.94f),text("特征码:111111111",.9f,.98f));
        assertEquals(Kind.DONE,rules.next(world).kind);
    }
    @Test public void missingMailCompletionDoesNotBecomeClaimed(){
        RedeemMailRules rules=new RedeemMailRules();Screen mail=screen(text("邮件",.08f,.06f));
        rules.applied(rules.next(mail));assertFalse(rules.verified());
    }
    @Test public void pasteRequiresMatchedIconAndNeverUsesInputPlaceholderAsTapTarget(){
        RedeemRules r=new RedeemRules("111111111","TEST10",0);Screen s=dialog();
        assertEquals(Kind.WAIT,r.next(s,List.of(),100,180,null,null).kind);
        assertEquals(Kind.WAIT,r.next(s,List.of(),600,680,null,text("粘贴图标",.5f,.45f)).kind);
        time=600;
        Action a=next(r,s,List.of());assertEquals("redeem-fill",a.scene);assertEquals(1658.5f/2400,a.x,0);
        assertEquals(506f/1080,a.y,0);apply(r,a);
        assertEquals(Kind.WAIT,next(r,s,List.of()).kind);assertFalse(r.submitted());
        assertEquals(Kind.WAIT,next(r,s,code("TEST10")).kind);
        next(r,screen(),List.of());
        assertEquals(Kind.WAIT,next(r,s,code("TEST10")).kind);
        assertEquals("redeem-submit",next(r,s,code("TEST10")).scene);
    }
    @Test public void pasteConfirmationNeedsTwoFreshInFieldFramesNotOldOrOutsideText(){
        RedeemRules r=new RedeemRules("111111111","TEST10",0);apply(r,next(r,dialog(),List.of()));
        assertEquals(Kind.WAIT,next(r,dialog(),code("TEST10")).kind);
        assertEquals(Kind.WAIT,r.next(dialog(),code("TEST10"),time,time+80,null,null).kind);
        assertEquals(Kind.WAIT,next(r,dialog(),code("TEST10")).kind);
        List<AccountSwitchRules.Label> outside=List.of(new AccountSwitchRules.Label("TEST10",text("TEST10",.5f,.7f)));
        assertEquals(Kind.WAIT,next(r,dialog(),outside).kind);
        assertEquals(Kind.WAIT,next(r,dialog(),code("TEST10")).kind);
        assertEquals("redeem-submit",next(r,dialog(),code("TEST10")).scene);
    }
    @Test public void clipboardSideEffectRequiresFreshFrameAndNoKeyboard(){
        assertTrue(RedeemRules.pasteDispatchAllowed(100,100,false));
        assertTrue(RedeemRules.pasteDispatchAllowed(100,3600,false));
        assertFalse(RedeemRules.pasteDispatchAllowed(100,3601,false));
        assertFalse(RedeemRules.pasteDispatchAllowed(100,99,false));
        assertFalse(RedeemRules.pasteDispatchAllowed(100,200,true));
    }
}
