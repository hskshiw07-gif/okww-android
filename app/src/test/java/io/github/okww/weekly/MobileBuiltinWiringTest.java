package io.github.okww.weekly;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class MobileBuiltinWiringTest {
    @Test public void dimOrExistingCooldownIsNotEvidenceOfANewCast() {
        for(CombatHud.Skill before:CombatHud.Skill.values())for(CombatHud.Skill after:CombatHud.Skill.values())
            assertEquals(before==CombatHud.Skill.READY&&after==CombatHud.Skill.COOLDOWN,CombatHud.castConfirmed(before,after));
        assertFalse(CombatHud.castConfirmed(CombatHud.Skill.READY,null));
    }
    private static final String[] NINE={"Chisa","Phrolova","Aemeath","Hsin","Hiyuki","YangYangSp","Qingxiao","ShoreKeeper","Galbrena"};
    @Test public void allRequestedCharactersHaveMobileDispatchButNoWorkshopFallback() {
        for(String name:NINE)assertTrue(name,TacetCombatPort.wired(name));
        for(String name:new String[]{"workshop:Chisa","BaseChar","Unknown",null,""})assertFalse(TacetCombatPort.wired(name));
        assertTrue(PriorityBuiltinRotation.Port.class.isAssignableFrom(TacetCombatPort.class));
    }
    @Test public void entryReportsEveryUncalibratedTeamMemberRatherThanOnlyFirstOne() {
        String message=TacetCombatPort.entryProblem(Arrays.asList("Chisa","Hsin","Hiyuki"));
        for(String name:new String[]{"Hsin","Hiyuki"})assertTrue(message.contains(name));
        assertTrue(message.contains("手机"));
        assertEquals("",TacetCombatPort.entryProblem(Arrays.asList("Galbrena","YangYangSp","ShoreKeeper")));
        assertEquals("",TacetCombatPort.entryProblem(Arrays.asList("Galbrena","YangYangSp","Chisa")));
    }
    @Test public void offlineWiringNeverPretendsSpecialStateReadersAreCalibrated() {
        for(String name:new String[]{"Phrolova","Hsin","Hiyuki","Qingxiao"})
            assertFalse(name,TacetCombatPort.missingCalibration(name).isEmpty());
        assertFalse(TacetCombatPort.missingCalibration(null).isEmpty());
    }
    @Test public void everyRequestedAxisWasAlreadyInBuiltinRegistry() {
        for(String name:NINE)assertTrue(name,BuiltinRotations.supported(name));
    }
}
