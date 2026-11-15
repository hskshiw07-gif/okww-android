package io.github.okww.weekly;

import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.okww.weekly.CombatSkillRetry.Step.*;

public class CombatSkillRetryTest {
    @Test public void ignoredTouchRetriesFreshReadySkillThenYieldsInsteadOfIdleSevenSeconds() {
        CombatSkillRetry retry=new CombatSkillRetry(1000);
        assertEquals(WAIT,retry.next(1249,1200,true,false));
        assertEquals(TAP,retry.next(1300,1250,true,false));retry.sent(1300);
        assertEquals(TAP,retry.next(1600,1550,true,false));retry.sent(1600);
        assertEquals(FINISHED,retry.next(1900,1850,true,false));
    }
    @Test public void cooldownAnimationStaleOrPreTouchFrameNeverRetries() {
        CombatSkillRetry retry=new CombatSkillRetry(1000);
        assertEquals(WAIT,retry.next(1500,1400,false,false));
        assertEquals(WAIT,retry.next(1500,1400,true,true));
        assertEquals(WAIT,retry.next(1500,999,true,false));
        assertEquals(WAIT,retry.next(4000,2000,true,false));
        assertEquals(WAIT,retry.next(1300,1400,true,false));
    }
}
