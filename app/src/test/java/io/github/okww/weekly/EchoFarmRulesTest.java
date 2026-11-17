package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static io.github.okww.weekly.EchoFarmRules.Step.*;
import static org.junit.Assert.*;

public class EchoFarmRulesTest {
    private static final String UID = "000000001";
    private static GardenRules.Text text(String word, float x, float y) {
        return new GardenRules.Text(word,x-.005f,y-.005f,x+.005f,y+.005f);
    }
    private static GardenRules.Screen hud(GardenRules.Text... words) {
        return hudFor(UID, words);
    }
    private static GardenRules.Screen hudFor(String uid, GardenRules.Text... words) {
        List<GardenRules.Text> all = new ArrayList<>(List.of(words));
        all.add(text("特征码:"+uid,.91f,.98f));
        all.add(text("Lv.90",.58f,.94f));
        return new GardenRules.Screen(all,new int[3],2400f/1080);
    }

    @Test public void fightPickupVerificationAndSameBossRespawnNeedFreshPairs() {
        EchoFarmRules farm = new EchoFarmRules("异构武装", UID);
        GardenRules.Screen boss = hud(text("Lv.89 异构武装",.5f,.055f));
        GardenRules.Screen drop = hud(text("吸收",.68f,.43f),text("领取奖励",.68f,.51f));
        assertEquals(WAIT,farm.observe(drop,false,100,200));
        assertEquals(WAIT,farm.observe(boss,true,300,350));
        assertEquals(FIGHT,farm.observe(boss,true,500,550));
        farm.combatEnded();
        assertEquals(WAIT,farm.observe(drop,false,700,750));
        assertEquals(ABSORB,farm.observe(drop,false,900,950));
        assertEquals("吸收",farm.button().value);
        farm.absorbSent();
        assertEquals(WAIT,farm.observe(drop,false,1100,1150));
        assertEquals(WAIT,farm.observe(hud(),false,1300,1350));
        assertEquals(ABSORB_CHECKED,farm.observe(hud(),false,1500,1550));
        assertFalse("消失不代表声骸入包",farm.rewardTextSeen());
        farm.awaitRespawn();
        assertEquals(WAIT,farm.observe(boss,true,1700,1750));
        assertEquals(FIGHT,farm.observe(boss,true,1900,1950));
    }

    @Test public void rejectsLookalikesAndMissingBossBar() {
        EchoFarmRules farm = new EchoFarmRules("异构武装", UID);
        assertFalse(farm.bossLabel(hud(text("异构武装残影",.5f,.055f))));
        assertFalse(farm.bossLabel(hud(text("异构武装",.5f,.20f))));
        assertFalse(farm.bossLabel(hud(text("异构武装",.5f,.055f), text("异构武装",.49f,.055f))));
        GardenRules.Screen boss = hud(text("异构武装",.5f,.055f));
        assertEquals(WAIT,farm.observe(boss,false,100,120));
        assertEquals(WAIT,farm.observe(boss,false,300,330));
        assertEquals(WAIT,farm.observe(boss,true,500,530));
    }

    @Test public void neverAuthorizesChestPopupOpponentOrRepeatedFrame() {
        EchoFarmRules farm = new EchoFarmRules("异构武装", UID);
        GardenRules.Text absorb = text("吸收",.68f,.43f);
        farm.combatEnded();
        assertNull(farm.prompt(hud(text("领取奖励",.68f,.43f))));
        assertNull(farm.prompt(hud(absorb, text("领取奖励",.69f,.44f))));
        assertNull(farm.prompt(hud(absorb, text("确认",.5f,.6f), text("取消",.43f,.6f))));
        assertNull(farm.prompt(hud(absorb, text("领取奖励",.50f,.30f))));
        assertNull(farm.prompt(hudFor("000000002",absorb)));
        assertNull(farm.prompt(hud(text("已吸收",.68f,.43f))));
        assertEquals(WAIT,farm.observe(hud(absorb,text("异构武装",.5f,.06f)),false,100,120));
        assertEquals(WAIT,farm.observe(hud(absorb),true,300,320));
        assertEquals(WAIT,farm.observe(hud(absorb),false,400,420));
        assertEquals(WAIT,farm.observe(hud(absorb),false,400,520));
        assertEquals(WAIT,farm.observe(hud(absorb),false,600,2300));
        assertEquals(WAIT,farm.observe(hud(absorb),false,2500,2600));
        assertEquals(WAIT,farm.observe(hud(text("吸收",.68f,.51f)),false,2700,2800));
        assertEquals(ABSORB,farm.observe(hud(text("吸收",.68f,.51f)),false,2900,3000));
    }

    @Test public void verificationIgnoresUnknownHudAndOnlyRecordsExplicitAcquisitionText() {
        EchoFarmRules farm = new EchoFarmRules("异构武装", UID);
        farm.combatEnded();
        farm.absorbSent();
        assertEquals(WAIT,farm.observe(hudFor("000000002",text("获得声骸",.50f,.25f)),false,100,200));
        assertEquals(WAIT,farm.observe(hud(text("获得声骸",.50f,.25f)),false,300,350));
        assertEquals(WAIT,farm.observe(hudFor("000000002"),false,500,550));
        assertEquals(WAIT,farm.observe(hud(),false,700,750));
        assertEquals(ABSORB_CHECKED,farm.observe(hud(),false,900,950));
        assertTrue(farm.rewardTextSeen());
    }

    @Test public void redStripMustBeInBossHudNotAtPlayerOrWorldCentre() {
        int[] pixels = new int[2400 * 1080];
        PortraitMatcher.Pixels frame = new PortraitMatcher.Pixels(2400,1080,pixels);
        for (int y=500;y<510;y++) java.util.Arrays.fill(pixels,y*2400+1100,y*2400+1300,0xffff5b32);
        assertFalse(EchoFarmRules.healthBar(frame));
        for (int y=52;y<56;y++) java.util.Arrays.fill(pixels,y*2400+810,y*2400+1010,0xffff5b32);
        assertTrue(EchoFarmRules.healthBar(frame));
    }
}
