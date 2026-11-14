package io.github.okww.weekly;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.okww.weekly.GardenRules.*;

public class DailyGuideNavigationTest {
    private Screen period() throws Exception {
        ArrayList<Text> words=new ArrayList<>();
        try(BufferedReader in=new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/daily-period-guide-ocr.tsv"),StandardCharsets.UTF_8))) {
            for(String line;(line=in.readLine())!=null;) {
                if(line.startsWith("#"))continue;
                String[] p=line.split("\t");
                words.add(new Text(p[0],Float.parseFloat(p[1])/2400,Float.parseFloat(p[2])/1080,
                        Float.parseFloat(p[3])/2400,Float.parseFloat(p[4])/1080));
            }
        }
        return new Screen(words,new int[3],2400f/1080);
    }
    private DailyRules verified() {DailyRules r=new DailyRules();r.teamConfirmed();return r;}
    private void materialTab(Action a) {
        assertEquals(Kind.TAP,a.kind);assertEquals("daily-guide-boss-tab",a.scene);
        assertEquals(148f/2400,a.x,.002f);assertEquals(326f/1080,a.y,.002f);
    }
    @Test public void activitySelectsMeasuredSecondIconNotThirdPeriodIcon() throws Exception {
        Screen s=period();s.words.clear();
        s.words.add(new Text("多活跃行迹",.05f,.04f,.12f,.08f));
        s.words.add(new Text("活跃度g",.18f,.88f,.24f,.92f));
        s.words.add(new Text("20-",458f/2400,902f/1080,526f/2400,944f/1080));
        materialTab(verified().next(s));
    }
    @Test public void recordedPeriodPageRecoversOnlyThroughMaterialTab() throws Exception {
        materialTab(verified().next(period()));
        assertEquals("daily-guide-back",new DailyRules().next(period()).scene);
        assertEquals(Kind.BACK,new DailyRules().next(period()).kind);
    }
    @Test public void freeRewardsReturnToActivityWithoutOpeningMaterialList() throws Exception {
        DailyRules r=verified();r.rewards.start();
        Action a=r.next(period());
        assertEquals("daily-guide-first-tab",a.scene);assertEquals(Kind.TAP,a.kind);
        assertEquals(.175f,a.y,.0001f);
    }
    @Test public void changedAccountBlocksPeriodRecovery() throws Exception {
        DailyRules r=verified();Screen s=period();r.observeUid(s);r.observeUid(s);
        s.words.set(8,new Text("UID:111111111",.88f,.97f,.98f,.995f));
        assertEquals("daily-account-recheck",r.next(s).scene);
        assertThrows(IllegalStateException.class,()->r.next(s));
    }
    @Test public void incompleteOrMisplacedPeriodEvidenceNeverAuthorizesTouch() throws Exception {
        for(int i:new int[]{0,2,4,8}) {
            Screen missing=period();missing.words.remove(i);
            assertEquals(Kind.WAIT,verified().next(missing).kind);
            Screen moved=period();Text old=moved.words.get(i);
            moved.words.set(i,new Text(old.value,.5f,.5f,.6f,.6f));
            assertEquals(Kind.WAIT,verified().next(moved).kind);
        }
    }
    @Test public void overlayAndWrongAspectStillBlockRecovery() throws Exception {
        Screen s=period();
        for(String v:new String[]{"保存现场","开跑","识别"})s.words.add(new Text(v,.2f,.2f,.3f,.3f));
        assertEquals(Kind.WAIT,verified().next(s).kind);
        assertEquals(Kind.STOP,verified().next(new Screen(period().words,new int[3],16f/9)).kind);
    }
}
