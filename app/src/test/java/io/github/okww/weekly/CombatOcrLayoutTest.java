package io.github.okww.weekly;

import org.junit.Test;
import static org.junit.Assert.*;

public class CombatOcrLayoutTest {
    @Test public void projectsAllDetailTilesAndDropsReplacedFullScreenWords() {
        for(int[] t:CombatOcrLayout.TILES) {
            assertArrayEquals(new int[]{t[0],t[1],t[0]+t[2],t[1]+t[3]},CombatOcrLayout.project(
                    t[4],t[5],t[4]+t[2]*t[6],t[5]+t[3]*t[6]));
            assertNull(CombatOcrLayout.project(t[0],t[1],t[0]+t[2],t[1]+t[3]));
        }
    }
    @Test public void preservesOutcomeUidAndCooldownButRejectsGuttersAndJoinedTiles() {
        for(int[] r:new int[][]{{1095,294,1306,345},{2107,1051,2280,1069},{1748,748,1821,775}})
            assertArrayEquals(r,CombatOcrLayout.project(r[0],r[1],r[2],r[3]));
        assertNull(CombatOcrLayout.project(1500,1100,1700,1140));
        assertNull(CombatOcrLayout.project(0,1080,100,1090));
        assertNull(CombatOcrLayout.project(-1,10,30,40));
    }
}
