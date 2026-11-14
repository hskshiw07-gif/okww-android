package io.github.okww.weekly;

import org.junit.Test;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import static io.github.okww.weekly.GardenRules.*;
import static org.junit.Assert.*;

public class AccountSwitchVisionTest {
    @Test public void recordedGuideOcrAliasStillRequiresAllTerminalAnchors(){
        Screen s=terminal();s.words.set(3,text("素拉指南",.728f,.55f));assertTrue(AccountSwitchVision.terminal(s));
        for(int i=0;i<4;i++){Screen missing=terminal();missing.words.set(3,text("素拉指南",.728f,.55f));
            missing.words.remove(i);assertFalse(AccountSwitchVision.terminal(missing));}
        s.words.set(3,text("素拉指南",.4f,.8f));assertFalse(AccountSwitchVision.terminal(s));
    }
    private Text text(String value,float x,float y){return new Text(value,x-.01f,y-.01f,x+.01f,y+.01f);}
    private Screen terminal(){return new Screen(new ArrayList<>(List.of(text("令终端",.09f,.055f),text("编队",.427f,.55f),
            text("数据坞",.527f,.55f),text("索拉指南",.728f,.55f))),new int[0],2400f/1080);}
    private PortraitMatcher.Pixels icon(int x,int y)throws Exception{
        byte[] rgb;try(InputStream in=getClass().getResourceAsStream("/account-switch-power.rgb")){assertNotNull(in);rgb=in.readAllBytes();}
        assertEquals(60*64*3,rgb.length);int[] frame=new int[2400*1080];
        for(int dy=0;dy<64;dy++)for(int dx=0;dx<60;dx++){
            int i=(dy*60+dx)*3;frame[(y+dy)*2400+x+dx]=0xff000000|((rgb[i]&255)<<16)|((rgb[i+1]&255)<<8)|(rgb[i+2]&255);
        }
        return new PortraitMatcher.Pixels(2400,1080,frame);
    }
    @Test public void recordedPublicIconLocatesMeasuredMobileExit()throws Exception{
        Text exit=AccountSwitchVision.exit(terminal(),icon(120,996));assertNotNull(exit);
        assertEquals(150f/2400,exit.x(),.001f);assertEquals(1028f/1080,exit.y(),.001f);
        Text shifted=AccountSwitchVision.exit(terminal(),icon(124,999));assertNotNull(shifted);
        assertEquals(154f/2400,shifted.x(),.001f);
    }
    @Test public void wrongPositionBlankOrWrongSizeNeverBecomesExit()throws Exception{
        assertNull(AccountSwitchVision.exit(terminal(),icon(300,900)));
        int[] frame=new int[2400*1080];
        assertNull(AccountSwitchVision.exit(terminal(),new PortraitMatcher.Pixels(2400,1080,frame)));
        java.util.Arrays.fill(frame,0xffffffff);
        assertNull(AccountSwitchVision.exit(terminal(),new PortraitMatcher.Pixels(2400,1080,frame)));
        assertNull(AccountSwitchVision.exit(terminal(),new PortraitMatcher.Pixels(1,1,new int[1])));
    }
    @Test public void allTerminalAnchorsAndNoOverlayOrDialogAreRequired()throws Exception{
        PortraitMatcher.Pixels pixels=icon(120,996);
        for(int i=0;i<4;i++){Screen s=terminal();s.words.remove(i);assertNull(AccountSwitchVision.exit(s,pixels));}
        Screen dialog=terminal();dialog.words.add(text("确认",.5f,.6f));assertNull(AccountSwitchVision.exit(dialog,pixels));
        Screen overlay=terminal();for(String word:List.of("开跑","识别","保存现场"))overlay.words.add(text(word,.1f,.3f));
        assertNull(AccountSwitchVision.exit(overlay,pixels));
    }
    @Test public void iconStillRequiresFreshSourceTwoActionFramesAndExplicitConfirmation()throws Exception{
        AccountSwitchRules r=new AccountSwitchRules("111111111",MultiAccountDailyTest.B,0);
        PortraitMatcher.Pixels pixels=icon(120,996);Screen menu=terminal();
        assertEquals(Kind.BACK,r.next(menu,List.of(),100,200,pixels).kind);
        Screen world=new Screen(List.of(text("Lv.90",.58f,.94f),text("特征码:111111111",.90f,.98f)),new int[0],2400f/1080);
        assertEquals(Kind.WAIT,r.next(world,List.of(),500,600).kind);r.next(world,List.of(),1000,1100);
        GardenRunGuard guard=new GardenRunGuard(0);
        Action first=r.next(menu,List.of(),1500,1600,pixels);assertEquals("multi-request-exit",first.scene);
        assertFalse(guard.ready(menu,first,1600));Action second=r.next(menu,List.of(),2000,2100,pixels);
        assertTrue(guard.ready(menu,second,2100));r.applied(second,2100);
        assertEquals(AccountSwitchRules.Stage.CONFIRM_EXIT,r.stage());
        assertEquals(Kind.WAIT,r.next(menu,List.of(),2500,2600,pixels).kind);
        Screen wrong=new Screen(List.of(text("确认退出游戏",.5f,.4f),text("确认",.65f,.6f),text("取消",.35f,.6f)),new int[0],2400f/1080);
        assertEquals(Kind.WAIT,r.next(wrong,List.of(),3000,3100,pixels).kind);
    }
}
