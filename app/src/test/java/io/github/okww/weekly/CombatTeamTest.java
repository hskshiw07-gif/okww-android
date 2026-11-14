package io.github.okww.weekly;

import org.junit.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

public class CombatTeamTest {
    @Test public void agreesWithActualOriginalPythonPolicyAcross600MixedTeams()throws Exception{
        int count=0;
        try(java.io.BufferedReader in=new java.io.BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/switch-parity.tsv"),StandardCharsets.UTF_8))){
            String line;
            while((line=in.readLine())!=null){
                if(line.startsWith("#")||line.isEmpty())continue;
                String[] f=line.split("\t");
                CombatTeam t=team(f[0].split(","));
                long[][] destinations={t.switchedIn,t.switchedOut,t.fullConOut,t.buffAt};
                for(int j=0;j<4;j++){
                    String[] values=f[5+j].split(",");
                    for(int i=0;i<3;i++)destinations[j][i]=Long.parseLong(values[i]);
                }
                int[] priorities=java.util.Arrays.stream(f[4].split(",")).mapToInt(Integer::parseInt).toArray();
                assertEquals("scenario "+count+" "+line,Integer.parseInt(f[9]),
                        t.choose(Integer.parseInt(f[1]),f[2].equals("1"),priorities,Long.parseLong(f[3])));
                count++;
            }
        }
        assertEquals(600,count);
    }
    private CombatTeam team(String...names)throws Exception{
        try(InputStreamReader in=new InputStreamReader(getClass().getResourceAsStream("/builtin-combat.tsv"), StandardCharsets.UTF_8)){
            return new CombatTeam(new BuiltinCombatCatalog(in), Arrays.asList(names));
        }
    }
    private final int[] normal={200,200,200};
    @Test public void actualDoubleMainTeamUsesHealerThenOldestMain()throws Exception{
        CombatTeam t=team("ShoreKeeper","YangYangSp","Aemeath");
        assertEquals(0,t.choose(1,false,normal,0));
        t.supportSkillConfirmed(0,100);t.switchConfirmed(0,1,false,100);
        assertEquals(2,t.choose(0,false,normal,1100));
        assertTrue(t.hasBuff(0,1200));assertFalse(t.hasBuff(0,28100));
    }
    @Test public void unbuffedHealerPrecedesSubDpsWithIntroThenMainWhenBothBuffed()throws Exception{
        CombatTeam t=team("ShoreKeeper","Sanhua","Aemeath");
        assertEquals(0,t.choose(2,true,normal,0));
        t.supportSkillConfirmed(0,1);
        assertEquals(1,t.choose(2,true,normal,2));
        t.supportSkillConfirmed(1,3);
        assertEquals(2,t.choose(0,true,normal,4));
    }
    @Test public void mainRefreshesLowestRemainingSupportBuff()throws Exception{
        CombatTeam t=team("ShoreKeeper","Sanhua","Aemeath");
        t.supportSkillConfirmed(0,0);t.supportSkillConfirmed(1,0);
        assertEquals(1,t.choose(2,false,normal,1000));
    }
    @Test public void priorityNoIsNeverOverriddenByHealerFallback()throws Exception{
        CombatTeam t=team("ShoreKeeper","YangYangSp","Aemeath");
        assertEquals(2,t.choose(1,true,new int[]{0,200,200},0));
        assertEquals(1,t.choose(1,true,new int[]{0,200,0},0));
        assertEquals(2,t.choose(0,true,new int[]{200,200,400},0));
    }
    @Test public void fullConcertoLocksHealerFor16SecondsButHasDeadlockFallback()throws Exception{
        CombatTeam t=team("ShoreKeeper","Sanhua","Aemeath");
        t.switchConfirmed(0,2,true,0);
        assertEquals(1,t.choose(2,true,normal,1000));
        assertEquals(0,t.choose(2,true,new int[]{200,0,200},1000));
        assertEquals(1,t.choose(2,true,normal,16001));
        assertEquals(0,t.choose(2,true,normal,28001));
    }
    @Test public void switchStateOnlyChangesWhenObservedSwitchIsConfirmed()throws Exception{
        CombatTeam t=team("ShoreKeeper","Sanhua","Aemeath");
        int chosen=t.choose(1,true,normal,10);
        assertEquals(0,chosen);assertEquals(-1,t.switchedOut[1]);assertFalse(t.intro[0]);
        t.switchConfirmed(1,0,true,20);
        assertTrue(t.intro[0]);assertTrue(t.subDpsIntro[0]);assertTrue(t.hasBuff(1,20));
        assertFalse(t.allTeammateBuffs(0,20));
        t.supportSkillConfirmed(2,20);assertTrue(t.allTeammateBuffs(0,20));
        assertThrows(IllegalArgumentException.class,()->t.choose(0,false,normal,19));
        assertThrows(IllegalArgumentException.class,()->t.switchConfirmed(0,0,false,21));
    }
    @Test public void unknownObservationCannotKeepBuffsOrHealerTimingAlive()throws Exception{
        CombatTeam t=team("ShoreKeeper","Sanhua","Aemeath");
        t.supportSkillConfirmed(1,100);t.switchConfirmed(0,2,true,100);
        assertTrue(t.hasBuff(0,100));assertTrue(t.hasBuff(1,100));
        t.forgetBuffTiming();assertFalse(t.hasBuff(0,100));assertFalse(t.hasBuff(1,100));
        assertEquals(-1,t.fullConOut[0]);assertEquals(100,t.switchedIn[2]);
    }
}
