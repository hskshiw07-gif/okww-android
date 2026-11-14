package io.github.okww.weekly;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class ScreenSnapshotTest {
    @Test public void realDimensionsArePreservedWhileSyntheticFixturesRemainCompatible() {
        ScreenReader.Snapshot real=new ScreenReader.Snapshot(Collections.emptyList(),2400,1080);
        assertEquals(2400,real.width);assertEquals(1080,real.height);
        ScreenReader.Snapshot synthetic=new ScreenReader.Snapshot(Collections.emptyList());
        assertEquals(0,synthetic.width);assertEquals(0,synthetic.height);
        assertThrows(IllegalArgumentException.class,()->new ScreenReader.Snapshot(Collections.emptyList(),2400,0));
        assertThrows(IllegalArgumentException.class,()->new ScreenReader.Snapshot(Collections.emptyList(),-1,1080));
    }
}
