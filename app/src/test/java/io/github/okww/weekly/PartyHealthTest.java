package io.github.okww.weekly;

import org.junit.Assume;
import org.junit.Test;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import static io.github.okww.weekly.GardenRules.*;
import static org.junit.Assert.*;

public class PartyHealthTest {
    @Test public void recordedFallenStandbyRequiresEntireEmptyBar() throws Exception {
        int[] colors=new int[2400*1080];ChisaHudTest.paste(colors,ChisaHudTest.read("/priority-mobile/hiyuki-dead-standby.png"),2120,225);
        var image=new PortraitMatcher.Pixels(2400,1080,colors);
        assertTrue(PartyHealth.deadStandby(image,0));assertFalse(PartyHealth.deadStandby(image,1));
        colors[319*2400+2135]=0xffd5d5d5;assertFalse(PartyHealth.deadStandby(image,0));
    }
    private static Text t(String text,float x,float y){return new Text(text,x-.005f,y-.005f,x+.005f,y+.005f);}
    private static Screen world(String hp) {
        return new Screen(new ArrayList<>(Arrays.asList(t(hp,.495f,.937f),t("Lv.90",.58f,.938f),
                t("63ms",.92f,.116f),t("特征码:000000000",.914f,.98f))),new int[3],2400f/1080);
    }
    private static PortraitMatcher.Pixels full() {
        PortraitMatcher.Pixels p=new PortraitMatcher.Pixels(2400,1080,new int[2400*1080]);
        fill(p,319,2135,2223,0xffd5d5d5);fill(p,458,2135,2224,0xffd5d5d5);return p;
    }
    private static void fill(PortraitMatcher.Pixels p,int top,int left,int right,int value) {
        for(int y=top;y<top+2;y++)for(int x=left;x<right;x++)p.argb[y*p.width+x]=value;
    }
    private static Screen overlay(Screen s) {
        s.words.add(t("保存现场",.15f,.26f));s.words.add(t("开跑",.15f,.16f));s.words.add(t("识别",.08f,.16f));return s;
    }

    @Test public void requiresPositiveEqualUnambiguousActiveHpInItsMeasuredRegion() {
        PortraitMatcher.Pixels p=full();assertTrue(PartyHealth.fullParty(p,world("31443/31443")));
        for(String hp:Arrays.asList("10365/31443","0/31443","0/0","31444/31443","31443/0","31443/314431",
                "31443/3I443","3144331443","31443/31443/31443","-1/-1","031443/031443","99999999/99999999","HP31443/31443"))
            assertFalse(hp,PartyHealth.fullParty(p,world(hp)));
        Screen s=world("31443/31443");s.words.add(t("31443/31443",.50f,.94f));
        assertFalse(PartyHealth.fullParty(p,s));
        s=world("31443/31443");s.words.add(t("bad/31443",.50f,.94f));assertFalse(PartyHealth.fullParty(p,s));
        s=world("31443/31443");s.words.set(0,t("31443/31443",.50f,.50f));assertFalse(PartyHealth.fullParty(p,s));
    }

    @Test public void rejectsEmptyPartialTintedAndMissingRightEndEvenWithFullActiveHp() {
        for(int[] bar:new int[][]{{319,2223},{458,2224}}) {
            PortraitMatcher.Pixels p=full();fill(p,bar[0],2135,bar[1],0xff2c2c2c);
            assertFalse(PartyHealth.fullParty(p,world("31443/31443")));
            p=full();fill(p,bar[0],2209,bar[1],0xff2c2c2c);assertFalse(PartyHealth.fullParty(p,world("31443/31443")));
            p=full();fill(p,bar[0],bar[1]-1,bar[1],0xff2c2c2c);assertFalse(PartyHealth.fullParty(p,world("31443/31443")));
            p=full();p.argb[bar[0]*2400+2140]=0xffd5c0d5;assertFalse(PartyHealth.fullParty(p,world("31443/31443")));
        }
        PortraitMatcher.Pixels p=full();p.argb[319*2400+2140]=0xffc3c3c3;
        assertTrue(PartyHealth.fullParty(p,world("31443/31443")));
        p.argb[319*2400+2140]=0xffc2c2c2;assertFalse(PartyHealth.fullParty(p,world("31443/31443")));
        p.argb[319*2400+2140]=0xffe6e6e6;assertTrue(PartyHealth.fullParty(p,world("31443/31443")));
        p.argb[319*2400+2140]=0xffe7e7e7;assertFalse(PartyHealth.fullParty(p,world("31443/31443")));
        p.argb[319*2400+2140]=0xffd5cdd5;assertTrue(PartyHealth.fullParty(p,world("31443/31443")));
        p.argb[319*2400+2140]=0xffd5ccd5;assertFalse(PartyHealth.fullParty(p,world("31443/31443")));
    }

    @Test public void requiresMeasuredWorldHudAndRejectsOverlayModalAndEitherArenaLabel() {
        PortraitMatcher.Pixels p=full();Screen s=world("31443/31443");s.words.remove(3);assertFalse(PartyHealth.fullParty(p,s));
        s=world("31443/31443");s.words.set(3,t("特征码:unknown",.914f,.98f));assertFalse(PartyHealth.fullParty(p,s));
        s=world("31443/31443");s.words.remove(1);s.words.remove(1);assertFalse(PartyHealth.fullParty(p,s));
        assertFalse(PartyHealth.fullParty(p,overlay(world("31443/31443"))));
        for(String modal:Arrays.asList("提示","确认","取消","复苏","挑战失败","领取奖励","暂停","确认离开")) {
            s=world("31443/31443");s.words.add(t(modal,.50f,.50f));assertFalse(modal,PartyHealth.fullParty(p,s));
        }
        s=world("31443/31443");s.words.add(t("@清理无普区中涌现的残象:0/4",.147f,.29f));assertFalse(PartyHealth.fullParty(p,s));
        s=world("31443/31443");s.words.add(t("G02:50:76",.51f,.14f));assertFalse(PartyHealth.fullParty(p,s));
        assertFalse(PartyHealth.fullParty(new PortraitMatcher.Pixels(1920,1080,new int[1920*1080]),world("31443/31443")));
        assertFalse(PartyHealth.fullParty(p,new Screen(world("31443/31443").words,new int[3],16f/9)));
        assertFalse(PartyHealth.fullParty(p,null));assertFalse(PartyHealth.fullParty(null,world("31443/31443")));
    }

    @Test public void privateMeasuredHealedBarsPassWhilePartialEmptyAndConsoleFail() throws Exception {
        Path root=Path.of("../../.android-tools/daily-combat");
        if(!Files.isDirectory(root))root=Path.of("../.android-tools/daily-combat");
        Assume.assumeTrue("Private healed capture not present",Files.isRegularFile(root.resolve("dev30-user-healed.png")));
        PortraitMatcher.Pixels healed=read(root.resolve("dev30-user-healed.png"));
        // Pixel-only replay with a synthetic clean OCR screen, not proof that this overlay-containing capture passed the HUD guard.
        assertTrue(PartyHealth.fullParty(healed,world("31443/31443")));
        assertFalse(PartyHealth.fullParty(healed,overlay(world("31443/31443"))));
        assertFalse(PartyHealth.fullParty(healed,world("10365/31443")));
        for(String file:Arrays.asList("dev30-outside-recovery.png","arena-shore.png","dev29-live-fight-05.png",
                "dev29-live-fight-10.png","dev28-live-fight-05.png")) {
            Assume.assumeTrue(Files.isRegularFile(root.resolve(file)));
            assertFalse(file,PartyHealth.fullParty(read(root.resolve(file)),world("31443/31443")));
        }
        PortraitMatcher.Pixels nearFull=read(root.resolve("arena-shore.png"));
        fill(nearFull,319,2135,2223,0xffd5d5d5);
        assertFalse("Slot1 dark tail must fail even after repairing slot0",PartyHealth.fullParty(nearFull,world("31443/31443")));
    }

    private static PortraitMatcher.Pixels read(Path path) throws Exception {
        try(InputStream in=Files.newInputStream(path)) {
            // AGP excludes java.desktop at compile time; image decoding is JVM-test-only.
            Object image=Class.forName("javax.imageio.ImageIO").getMethod("read",InputStream.class).invoke(null,in);
            Class<?> type=Class.forName("java.awt.image.BufferedImage");
            int w=(Integer)type.getMethod("getWidth").invoke(image),h=(Integer)type.getMethod("getHeight").invoke(image);
            int[] rgb=(int[])type.getMethod("getRGB",int.class,int.class,int.class,int.class,int[].class,int.class,int.class)
                    .invoke(image,0,0,w,h,null,0,w);
            return new PortraitMatcher.Pixels(w,h,rgb);
        }
    }
}
