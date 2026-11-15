package io.github.okww.weekly;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.okww.weekly.GardenRules.*;

public class MobileRecoveryReaderTest {
    @Test public void recordedNexusMatchesAnotherMapLocationButNotSmallBeacon() throws Exception {
        var template=MobileRecoveryReader.template(ChisaHudTest.read("/daily-features/recovery-nexus.png"));
        for(String file:List.of("recovery-nexus-other","recovery-small-beacon")) {
            var crop=ChisaHudTest.read("/priority-mobile/"+file+".png");
            int[] rgb=new int[2400*1080];ChisaHudTest.paste(rgb,crop,500,300);
            var frame=new PortraitMatcher.Pixels(2400,1080,rgb);
            var match=PortraitMatcher.match(frame,List.of(template),500,300,500+crop.width,300+crop.height);
            assertEquals(file,file.equals("recovery-nexus-other"),match.score>=.90);
        }
    }
    private Text t(String value,float x,float y){return new Text(value,x-.01f,y-.01f,x+.01f,y+.01f);}
    @Test public void namedDetailRequiresScopedLayoutUidAndButton() {
        Screen s=new Screen(new ArrayList<>(List.of(t("中枢信标",.8f,.05f),t("快速旅行",.84f,.92f),
                t("特征码:000000000",.91f,.98f))),new int[3],2400f/1080);
        assertNotNull(MobileRecoveryReader.travel(s));assertFalse(MobileRecoveryReader.map(s));
        assertNull(MobileRecoveryReader.travel(new Screen(s.words,new int[3],1.8f)));
        s.words.set(0,t("小型信标",.8f,.05f));assertNull(MobileRecoveryReader.travel(s));
        s.words.set(0,t("中枢信标",.8f,.05f));s.words.remove(2);assertNull(MobileRecoveryReader.travel(s));
    }
}
