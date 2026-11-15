package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static io.github.okww.weekly.CombatTouchLayout.Control.*;
import static org.junit.Assert.*;

public class PrioritySkillHudTest {
    @Test public void recordedMobileAemeathHeavySharesMeasuredAttackScale()throws Exception{
        var source=templates();source.add(new PortraitMatcher.Template("aemeath_human_heavy",
                ChisaHudTest.read("/combat-features/aemeath_human_heavy_3840x2160_2818_1881.png"),2160));
        var hud=hud(source);int[] pixels=new int[2400*1080];
        ChisaHudTest.paste(pixels,ChisaHudTest.read("/priority-mobile/aemeath-heavy-real.png"),1840,715);
        var frame=new PortraitMatcher.Pixels(2400,1080,pixels);
        assertTrue(hud.controlScore(frame,"aemeath_human_heavy",ATTACK)>=.78);
        assertFalse(hud.feature(frame,"hsin_h1",ATTACK));
        assertFalse(hud.feature(frame,"hsin_h2",ATTACK));
    }
    @Test public void recordedMobileHsinH1UsesActualAttackIconScale()throws Exception{
        var hud=hud(templates());
        for(String name:List.of("hsin-h1-real","hsin-h1-effects-real")){
            int[] pixels=new int[2400*1080];
            ChisaHudTest.paste(pixels,ChisaHudTest.read("/priority-mobile/"+name+".png"),1840,715);
            var frame=new PortraitMatcher.Pixels(2400,1080,pixels);
            assertTrue(hud.feature(frame,"hsin_h1",ATTACK));
            assertFalse(hud.feature(frame,"hsin_h2",ATTACK));
            assertFalse(hud.feature(frame,"hsin_lib1",LIBERATION));
        }
    }
    private List<PortraitMatcher.Template> templates() throws Exception {
        List<PortraitMatcher.Template> result=new ArrayList<>();
        for(String file:List.of("hsin_h1_3840x2160_2826_1893", "hsin_h2_3840x2160_2825_1890",
                "hsin_lib1_3840x2160_3588_1894", "hsin_lib2_3840x2160_3587_1891",
                "hiyuki_left_3840x2160_2628_1891", "hiyuki_right_3840x2160_2819_1893",
                "has_target_3840x2160_2868_1917", "no_target_3840x2160_2868_1917")) {
            String name=file.substring(0,file.indexOf("_3840"));
            result.add(new PortraitMatcher.Template(name,ChisaHudTest.read("/combat-features/"+file+".png"),2160));
        }
        return result;
    }
    private PrioritySkillHud hud(List<PortraitMatcher.Template> source) throws Exception {
        return new PrioritySkillHud(source);
    }
    @Test public void originalHsinAndHiyukiSpritesAreSearchedAtTheirMobileControls() throws Exception {
        var source=templates();var hud=hud(source);
        for(var template:source.subList(0,6)) {
            var control=template.character.contains("lib")?LIBERATION:template.character.equals("hiyuki_right")?DODGE:ATTACK;
            var point=CombatTouchLayout.forXiaomi13Pro().point(control);
            int width=Math.round(template.pixels.width*.7f),height=Math.round(template.pixels.height*.7f);
            int[] frame=new int[2400*1080];
            int left=Math.round(point.x*2400)-width/2,top=Math.round(point.y*1080)-height/2;
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)frame[(top+y)*2400+left+x]=
                    template.pixels.argb[Math.round(y*(template.pixels.height-1f)/(height-1))*template.pixels.width
                            +Math.round(x*(template.pixels.width-1f)/(width-1))];
            var image=new PortraitMatcher.Pixels(2400,1080,frame);
            assertTrue(template.character,hud.feature(image,template.character,control));
            assertFalse(template.character,hud.feature(image,template.character,control==LIBERATION?ATTACK:LIBERATION));
        }
    }
    @Test public void recordedNormalAttackIsNotAChargedPrompt() throws Exception {
        int[] frame=new int[2400*1080];ChisaHudTest.paste(frame,ChisaHudTest.read("/priority-mobile/hiyuki-normal-control.png"),1907,781);
        var image=new PortraitMatcher.Pixels(2400,1080,frame);
        assertFalse(PrioritySkillHud.highlighted(image,ATTACK));
    }
    @Test public void recordedMobileHighlightRequiresBothTriangleAndRing() throws Exception {
        int[] frame=new int[2400*1080];ChisaHudTest.paste(frame,ChisaHudTest.read("/priority-mobile/highlighted-attack.png"),1841,715);
        var image=new PortraitMatcher.Pixels(2400,1080,frame);
        assertTrue(PrioritySkillHud.highlighted(image,ATTACK));assertFalse(PrioritySkillHud.highlighted(image,RESONANCE));
        for(int y=725;y<758;y++)for(int x=1930;x<1975;x++)frame[y*2400+x]=0;
        assertFalse(PrioritySkillHud.highlighted(image,ATTACK));
        assertFalse(hud(templates()).actionIndicator(new PortraitMatcher.Pixels(2400,1080,new int[frame.length])));
    }
    @Test public void mobileActionIndicatorRetainsOriginalPcFeatureThreshold() throws Exception {
        int[] colors=new int[2400*1080];ChisaHudTest.paste(colors,ChisaHudTest.read("/priority-mobile/unlocked-target.png"),535,945);
        assertTrue(hud(templates()).actionIndicator(new PortraitMatcher.Pixels(2400,1080,colors)));
    }
    @Test public void recordedHiyukiIceBluePromptIsRecognized() throws Exception {
        int[] frame=new int[2400*1080];ChisaHudTest.paste(frame,ChisaHudTest.read("/priority-mobile/hiyuki-blue-highlight.png"),1841,715);
        var image=new PortraitMatcher.Pixels(2400,1080,frame);
        assertTrue(PrioritySkillHud.highlighted(image,ATTACK));
        for(int y=725;y<758;y++)for(int x=1930;x<1975;x++)frame[y*2400+x]=0;
        assertFalse(PrioritySkillHud.highlighted(image,ATTACK));
    }
}
