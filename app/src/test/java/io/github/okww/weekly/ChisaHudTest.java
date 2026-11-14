package io.github.okww.weekly;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
public class ChisaHudTest {
    static PortraitMatcher.Pixels read(String path)throws Exception{
        try(InputStream in=ChisaHudTest.class.getResourceAsStream(path)){
            assertNotNull(path,in);Object image=Class.forName("javax.imageio.ImageIO").getMethod("read",InputStream.class).invoke(null,in);
            Class<?> type=Class.forName("java.awt.image.BufferedImage");int width=(int)type.getMethod("getWidth").invoke(image),height=(int)type.getMethod("getHeight").invoke(image);
            int[] colors=(int[])type.getMethod("getRGB",int.class,int.class,int.class,int.class,int[].class,int.class,int.class).invoke(image,0,0,width,height,null,0,width);
            return new PortraitMatcher.Pixels(width,height,colors);
        }
    }
    static ChisaHud hud()throws Exception{return new ChisaHud(read("/chisa-mobile/normal-attack.png"),read("/chisa-mobile/powered-attack.png"),read("/chisa-mobile/resonance-attack.png"));}
    static PortraitMatcher.Pixels frame(String name)throws Exception{
        int[] pixels=new int[2400*1080];paste(pixels,read("/chisa/"+name+"-attack.png"),1907,791);paste(pixels,read("/chisa/"+name+"-forte.png"),1290,973);
        return new PortraitMatcher.Pixels(2400,1080,pixels);
    }
    static void paste(int[] dst,PortraitMatcher.Pixels crop,int x,int y){for(int row=0;row<crop.height;row++)System.arraycopy(crop.argb,row*crop.width,dst,(y+row)*2400+x,crop.width);}
    @Test public void recordedPartialFullAndPoweredStatesDiffer()throws Exception{
        ChisaHud hud=hud();for(String name:List.of("idle","normal-1","normal-2","normal-3","after-tap-resonance"))assertEquals(name,ChisaHud.State.NORMAL,hud.read(frame(name)));
        assertEquals(ChisaHud.State.FULL,hud.read(frame("full-before-hold")));
        for(String name:List.of("after-e-hold","after-e-settled"))assertEquals(name,ChisaHud.State.POWERED,hud.read(frame(name)));
        assertThrows(IllegalStateException.class,()->hud.read(frame("after-heavy"))); // Death/auto-switch is not an empty Chisa gauge.
    }
    @Test public void unknownShapeAndWrongResolutionDoNotBecomeEmptyForte()throws Exception{
        ChisaHud hud=hud();assertThrows(IllegalStateException.class,()->hud.read(new PortraitMatcher.Pixels(2400,1080,new int[2400*1080])));
        assertThrows(IllegalStateException.class,()->hud.read(new PortraitMatcher.Pixels(1920,1080,new int[1920*1080])));
        PortraitMatcher.Pixels good=frame("full-before-hold");int[] moved=new int[good.argb.length];paste(moved,read("/chisa-mobile/normal-attack.png"),100,100);
        assertThrows(IllegalStateException.class,()->hud.read(new PortraitMatcher.Pixels(2400,1080,moved)));
    }
    @Test public void actualEmptyToolIsNotLevitatorAndBlankDoesNotMatch()throws Exception{
        var template=new PortraitMatcher.Template("empty",read("/chisa-mobile/empty-tool.png"),1080);var templates=List.of(template);int[] pixels=new int[2400*1080];
        assertFalse(new MobileToolHud(templates).knownNonLevitator(new PortraitMatcher.Pixels(2400,1080,pixels)));
        paste(pixels,template.pixels,1925,646);assertTrue(new MobileToolHud(templates).knownNonLevitator(new PortraitMatcher.Pixels(2400,1080,pixels)));
        int[] misplaced=new int[pixels.length];paste(misplaced,template.pixels,100,100);assertFalse(new MobileToolHud(templates).knownNonLevitator(new PortraitMatcher.Pixels(2400,1080,misplaced)));
        assertThrows(IllegalStateException.class,()->new MobileToolHud(templates).knownNonLevitator(new PortraitMatcher.Pixels(1920,1080,new int[1920*1080])));
    }
    @Test public void combatBackgroundDoesNotHideKnownEmptyTool()throws Exception{
        var templates=List.of(new PortraitMatcher.Template("empty",read("/chisa-mobile/empty-tool.png"),1080));
        int[] pixels=new int[2400*1080];
        for(String file:List.of("tool-combat-effect.png","tool-combat-dim.png")){
            paste(pixels,read("/chisa/"+file),1918,639);
            assertTrue(file,new MobileToolHud(templates).knownNonLevitator(new PortraitMatcher.Pixels(2400,1080,pixels)));
        }
        paste(pixels,read("/chisa/tool-other-control.png"),1918,639);
        assertFalse(new MobileToolHud(templates).knownNonLevitator(new PortraitMatcher.Pixels(2400,1080,pixels)));
    }
    @Test public void poweredConsumptionRequiresTwoDistinctConsecutiveFreshFrames(){
        var confirmed=new ChisaHud.PoweredConfirmation();
        assertFalse(confirmed.observe(ChisaHud.State.POWERED,100));
        assertFalse(confirmed.observe(ChisaHud.State.POWERED,100));
        assertFalse(confirmed.observe(ChisaHud.State.NORMAL,200));
        assertFalse(confirmed.observe(ChisaHud.State.POWERED,300));
        assertFalse(confirmed.observe(ChisaHud.State.POWERED,1900));
        assertTrue(confirmed.observe(ChisaHud.State.POWERED,2000));
    }
    @Test public void ordinaryResonanceCannotAuthorizeTheChargedHeavyAttack()throws Exception{
        var hud=hud();var confirmation=new ChisaHud.PoweredConfirmation();
        var state=hud.read(frame("after-tap-resonance"));
        assertEquals(ChisaHud.State.NORMAL,state);
        assertFalse(confirmation.observe(state,100));assertFalse(confirmation.observe(state,200));
        var p=new PriorityBuiltinRotationTest.Port("Chisa"){
            @Override public boolean chisaForteConsumed(){return confirmation.observe(state,300);}
        };
        p.mouse=true;new PriorityBuiltinRotation("Chisa").run(p);
        assertTrue(p.events.contains("down:RESONANCE"));assertFalse(p.events.contains("down"));
        assertFalse(p.held);assertTrue(p.controls.isEmpty());
    }
    @Test public void unknownImageAfterResonanceReleasesBeforeStopping()throws Exception{
        var reader=hud();var p=new PriorityBuiltinRotationTest.Port("Chisa"){
            @Override public boolean chisaForteConsumed(){reader.read(new PortraitMatcher.Pixels(2400,1080,new int[2400*1080]));return true;}
        };
        p.mouse=true;assertThrows(IllegalStateException.class,()->new PriorityBuiltinRotation("Chisa").run(p));
        assertTrue(p.events.contains("up:RESONANCE"));assertFalse(p.events.contains("down"));
        assertFalse(p.events.contains("S"));assertFalse(p.held);assertTrue(p.controls.isEmpty());
    }
    @Test public void grayWrongGlyphAndUniformGrayAreNotKnownUtilities()throws Exception{
        var tools=new MobileToolHud(List.of(new PortraitMatcher.Template("empty",read("/chisa-mobile/empty-tool.png"),1080)));
        int[] pixels=new int[2400*1080];Arrays.fill(pixels,0xff808080);
        assertFalse(tools.knownNonLevitator(new PortraitMatcher.Pixels(2400,1080,pixels)));
        var wrong=read("/chisa/tool-other-control.png");int[] gray=new int[wrong.argb.length];
        for(int i=0;i<gray.length;i++){int c=wrong.argb[i];gray[i]=0xff000000|((c>>16&255)/2<<16)|((c>>8&255)/2<<8)|(c&255)/2;}
        paste(pixels,new PortraitMatcher.Pixels(wrong.width,wrong.height,gray),1918,639);
        assertFalse(tools.knownNonLevitator(new PortraitMatcher.Pixels(2400,1080,pixels)));
    }
}
