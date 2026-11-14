package io.github.okww.weekly;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.okww.weekly.GardenRules.*;

public class DailyJournalTest {
    private Text t(String s,float x,float y){return new Text(s,x-.005f,y-.005f,x+.005f,y+.005f);}
    private Screen page(){return new Screen(new ArrayList<>(Arrays.asList(t("阶段9",.83f,.14f),
            t("己解锁所有阶段任务",.83f,.55f),t("阶段奖励",.83f,.64f),
            t("特征码:000000000",.91f,.98f))),new int[3],2400f/1080);}
    @Test public void titleMissingUsesThreeMeasuredAnchorsOnlyForNavigation(){
        DailyRules r=new DailyRules();r.teamConfirmed();
        assertEquals("daily-guide-first-tab",r.next(page()).scene);
        assertEquals("daily-guide-back",new DailyRules().next(page()).scene);
    }
    @Test public void absentOrMisplacedAnchorsAndMissingUidNeverAuthorizeTab(){
        for(int i=0;i<4;i++){
            Screen s=page();s.words.remove(i);DailyRules r=new DailyRules();r.teamConfirmed();
            assertNotEquals("daily-guide-first-tab",r.next(s).scene);
        }
        Screen s=page();s.words.set(2,t("阶段奖励",.40f,.64f));DailyRules r=new DailyRules();r.teamConfirmed();
        assertNotEquals("daily-guide-first-tab",r.next(s).scene);
    }
    @Test public void overlayAndWrongLayoutStillBlockNavigation(){
        Screen s=page();for(String v:new String[]{"保存现场","开跑","识别"})s.words.add(t(v,.3f,.3f));
        DailyRules r=new DailyRules();r.teamConfirmed();assertEquals(Kind.WAIT,r.next(s).kind);
        assertEquals(Kind.STOP,r.next(new Screen(page().words,new int[3],1)).kind);
    }
}
