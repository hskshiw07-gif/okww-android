package io.github.okww.weekly;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
public class RedeemVisionTest {
    static GardenRules.Screen terminal(){
        return new GardenRules.Screen(List.of(new GardenRules.Text("终端",.08f,.03f,.15f,.08f),
                new GardenRules.Text("编队",.4f,.5f,.45f,.55f),new GardenRules.Text("索拉指南",.7f,.5f,.75f,.55f),
                new GardenRules.Text("数据坞",.5f,.5f,.55f,.55f)),new int[0],2400f/1080);
    }
    static int[] frame()throws Exception{
        byte[] rgb;try(InputStream in=RedeemVisionTest.class.getResourceAsStream("/redeem-settings.rgb")){assertNotNull(in);rgb=in.readAllBytes();}
        assertEquals(70*52*3,rgb.length);int[] frame=new int[2400*1080];
        for(int y=0;y<52;y++)for(int x=0;x<70;x++){int i=(y*70+x)*3;
            frame[(993+y)*2400+2154+x]=0xff000000|((rgb[i]&255)<<16)|((rgb[i+1]&255)<<8)|(rgb[i+2]&255);}
        return frame;
    }
    @Test public void publicGearCropMatchesWithoutAnyUidWatermark()throws Exception{
        GardenRules.Text hit=RedeemVision.settings(terminal(),new PortraitMatcher.Pixels(2400,1080,frame()));
        assertNotNull(hit);assertEquals(2189f/2400,hit.x(),.005f);assertEquals(1019f/1080,hit.y(),.005f);
    }
    @Test public void changedUidPixelsDoNotAffectMatch()throws Exception{
        int[] pixels=frame();GardenRules.Text before=RedeemVision.settings(terminal(),new PortraitMatcher.Pixels(2400,1080,pixels));
        Random random=new Random(1);for(int y=1046;y<1080;y++)for(int x=0;x<2400;x++)pixels[y*2400+x]=random.nextInt();
        GardenRules.Text after=RedeemVision.settings(terminal(),new PortraitMatcher.Pixels(2400,1080,pixels));
        assertNotNull(before);assertNotNull(after);assertEquals(before.x(),after.x(),0);assertEquals(before.y(),after.y(),0);
    }
    @Test public void noGearWrongContextAndUnknownResolutionHaveNoTarget()throws Exception{
        assertNull(RedeemVision.settings(terminal(),new PortraitMatcher.Pixels(2400,1080,new int[2400*1080])));
        GardenRules.Screen unknown=new GardenRules.Screen(List.of(),new int[0],2400f/1080);
        assertNull(RedeemVision.settings(unknown,new PortraitMatcher.Pixels(2400,1080,frame())));
        assertNull(RedeemVision.settings(terminal(),new PortraitMatcher.Pixels(1920,1080,new int[1920*1080])));
    }
    static GardenRules.Screen pasteDialog(){
        return new GardenRules.Screen(new ArrayList<>(List.of(
                new GardenRules.Text("兑换码",.29f,.30f,.33f,.34f),
                new GardenRules.Text("请输入兑换码",.30f,.45f,.37f,.48f),
                new GardenRules.Text("取消",.35f,.60f,.39f,.65f),
                new GardenRules.Text("确认",.61f,.60f,.65f,.65f),
                new GardenRules.Text("特征码:111111111",.87f,.97f,.95f,1))),new int[0],2400f/1080);
    }
    static PortraitMatcher.Pixels pasteFrame(String resource,int left,int top)throws Exception{
        byte[] rgb;try(InputStream in=RedeemVisionTest.class.getResourceAsStream(resource)){assertNotNull(in);rgb=in.readAllBytes();}
        assertEquals(37*40*3,rgb.length);int[] argb=new int[2400*1080];
        for(int y=0;y<40;y++)for(int x=0;x<37;x++){int i=(y*37+x)*3;
            argb[(top+y)*2400+left+x]=0xff000000|((rgb[i]&255)<<16)|((rgb[i+1]&255)<<8)|(rgb[i+2]&255);}
        return new PortraitMatcher.Pixels(2400,1080,argb);
    }
    @Test public void observedPasteIconNeedsEmptyDialogAndCalibratedRoi()throws Exception{
        PortraitMatcher.Pixels pixels=pasteFrame("/redeem-paste.rgb",1640,486);
        GardenRules.Text hit=RedeemVision.paste(pasteDialog(),pixels);assertNotNull(hit);
        assertEquals(1658.5f/2400,hit.x(),.003f);assertEquals(506f/1080,hit.y(),.003f);
        assertNull(RedeemVision.paste(pasteDialog(),pasteFrame("/redeem-clear.rgb",1640,486)));
        assertNull(RedeemVision.paste(pasteDialog(),pasteFrame("/redeem-paste.rgb",1500,600)));
        for(int i=0;i<5;i++){GardenRules.Screen s=pasteDialog();s.words.remove(i);assertNull(RedeemVision.paste(s,pixels));}
        assertNull(RedeemVision.paste(terminal(),pixels));
        assertNull(RedeemVision.paste(pasteDialog(),new PortraitMatcher.Pixels(1920,1080,new int[1920*1080])));
        GardenRules.Screen filled=pasteDialog();filled.words.set(1,new GardenRules.Text("WWTESTPASTE",.3f,.45f,.4f,.48f));
        assertNull(RedeemVision.paste(filled,pixels));
        GardenRules.Screen overlay=pasteDialog();
        for(String label:List.of("保存现场","开跑","识别"))overlay.words.add(new GardenRules.Text(label,.1f,.1f,.2f,.2f));
        assertNull(RedeemVision.paste(overlay,pixels));
    }
}
