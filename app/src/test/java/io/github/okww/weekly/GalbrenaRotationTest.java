package io.github.okww.weekly;

import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.okww.weekly.CombatTouchLayout.Control.*;

public class GalbrenaRotationTest {
    @Test public void nonLevitatorTemplateUsesRealRoiAndRejectsBlankOrWrongLocation() throws Exception {
        Object image;
        try(java.io.InputStream in=getClass().getResourceAsStream("/galbrena-mobile/non-levitator.png")) {
            image=Class.forName("javax.imageio.ImageIO").getMethod("read",java.io.InputStream.class).invoke(null,in);
        }
        int[] colors=(int[])Class.forName("java.awt.image.BufferedImage")
                .getMethod("getRGB",int.class,int.class,int.class,int.class,int[].class,int.class,int.class)
                .invoke(image,0,0,54,49,null,0,54);
        java.util.List<PortraitMatcher.Template> templates=java.util.Collections.singletonList(
                new PortraitMatcher.Template("tool",new PortraitMatcher.Pixels(54,49,colors),1080));
        int[] rgb=new int[2400*1080];
        PortraitMatcher.Pixels p=new PortraitMatcher.Pixels(2400,1080,rgb);
        assertTrue(PortraitMatcher.match(p,templates,1918,639,1986,702,1).score<.92);
        for(int y=0;y<49;y++)System.arraycopy(colors,y*54,rgb,(y+646)*2400+1925,54);
        assertTrue(PortraitMatcher.match(p,templates,1918,639,1986,702,1).score>=.92);
        assertTrue(PortraitMatcher.match(p,templates,100,100,168,163,1).score<.92);
    }
    static class Port extends PriorityBuiltinRotationTest.Port {
        boolean airborne,res,forte,failPause;
        int switches,dodges,landings;
        Port(){super("Galbrena");}
        @Override public boolean signal(PriorityBuiltinRotation.Signal signal){
            return switch(signal){case FLYING -> airborne;case GALBRENA_RES -> res;default -> super.signal(signal);};
        }
        @Override public void waitDown(boolean normal){landings++;airborne=false;}
        public void dodge(long ms){dodges++;advance(ms);}
        public void shorekeeperAutoDodge(){airborne=false;}
        @Override public boolean forteFull(){return forte;}
        @Override public void pause(long ms){if(failPause)throw new IllegalStateException("lost frame");advance(ms);}
        @Override public void switchNext(){switches++;super.switchNext();}
    }
    @Test public void introHoldAlwaysReleasesOnObservationFailure() {
        Port p=new Port();p.intro=true;p.failPause=true;
        assertThrows(IllegalStateException.class,()->new PriorityBuiltinRotation("Galbrena").run(p));
        assertFalse(p.held);assertEquals("up",p.events.get(p.events.size()-1));assertEquals(0,p.switches);
    }
    @Test public void normalFortePathUsesEchoResonanceLiberationThenSwitch() throws Exception {
        Port p=new Port();p.forte=true;p.allowRes=p.allowLib=true;
        new PriorityBuiltinRotation("Galbrena").run(p);
        assertTrue(p.events.indexOf("E:0")<p.events.indexOf("R:1:0"));
        assertTrue(p.events.indexOf("R:1:0")<p.events.indexOf("L:-1:0:100"));
        assertEquals(1,p.switches);
    }
    @Test public void persistentResIndicatorHasOriginalTenSecondBound() throws Exception {
        Port p=new Port();p.res=true;new PriorityBuiltinRotation("Galbrena").run(p);
        assertTrue(p.real-60000>=10000&&p.real-60000<11000);assertEquals(1,p.switches);assertFalse(p.held);
    }
    @Test public void fastIntroReleasesBeforeSwitchAndDoesNotCast() throws Exception {
        Port p=new Port();p.intro=true;p.fast=true;new PriorityBuiltinRotation("Galbrena").run(p);
        assertEquals(61000,p.real);assertFalse(p.held);assertEquals(1,p.switches);
        assertFalse(p.events.stream().anyMatch(e->e.startsWith("E:")));assertEquals(0,p.dodges);
    }
    @Test public void airborneEntryWaitsBeforeSkills() throws Exception {
        Port p=new Port();p.airborne=true;new PriorityBuiltinRotation("Galbrena").run(p);assertEquals(1,p.landings);
    }
    @Test public void entryStillRejectsUncalibratedCharactersAfterDispatchWiring() {
        assertEquals("",TacetCombatPort.missingCalibration("Galbrena"));assertEquals("",TacetCombatPort.missingCalibration("Aemeath"));
        assertEquals("",TacetCombatPort.missingCalibration("Chisa"));
        for(String n:new String[]{"Hsin","Hiyuki","workshop:Galbrena","",null})assertFalse(TacetCombatPort.missingCalibration(n).isEmpty());
    }
    @Test public void recordedPurpleEndpointNotGenericWhiteEndpoint() throws Exception {
        for(String name:new String[]{"galbrena-full","galbrena-full-second","galbrena-spent"}) {
            Object image;
            try(java.io.InputStream in=getClass().getResourceAsStream("/"+name+".png")) {
                image=Class.forName("javax.imageio.ImageIO").getMethod("read",java.io.InputStream.class).invoke(null,in);
            }
            Class<?> type=Class.forName("java.awt.image.BufferedImage");
            int[] rgb=(int[])type.getMethod("getRGB",int.class,int.class,int.class,int.class,int[].class,int.class,int.class)
                    .invoke(image,0,0,48,22,null,0,48);
            PortraitMatcher.Pixels crop=new PortraitMatcher.Pixels(48,22,rgb);
            assertEquals(name,!name.endsWith("spent"),GalbrenaHud.purple(crop,0,0,48,22)>=.12);
        }
        assertThrows(IllegalArgumentException.class,()->GalbrenaHud.full(new PortraitMatcher.Pixels(1,1,new int[1])));
    }
}
