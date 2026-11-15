package io.github.okww.weekly;

import org.junit.Test;
import static org.junit.Assert.*;

public class HiyukiHudTest {
    @Test public void independentLiveForteFramesDistinguishFormsWithoutCastHistory()throws Exception{
        var hud=new HiyukiHud(ChisaHudTest.read("/priority-mobile/hiyuki-normal-forte.png"),
                ChisaHudTest.read("/priority-mobile/hiyuki-liberated-forte.png"));
        for(String name:new String[]{"normal-charged","liberated"}){
            int[] pixels=new int[2400*1080];
            ChisaHudTest.paste(pixels,ChisaHudTest.read("/priority-mobile/hiyuki-"+name+"-forte-real.png"),1015,935);
            assertEquals(name.equals("liberated")?HiyukiHud.Phase.LIBERATED:HiyukiHud.Phase.NORMAL,
                    hud.phase(new PortraitMatcher.Pixels(2400,1080,pixels)));
        }
        assertEquals(HiyukiHud.Phase.UNKNOWN,hud.phase(new PortraitMatcher.Pixels(2400,1080,new int[2400*1080])));
    }
}
