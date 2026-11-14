package io.github.okww.weekly;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class GardenPacingTest {
    @Test public void boundedDifferentConsecutivePauses(){
        GardenPacing pacing=new GardenPacing(new Random(1));int prior=-1;Set<Integer> values=new HashSet<>();
        for(int i=0;i<10000;i++){int next=pacing.next();assertTrue(next>=180&&next<=320);assertNotEquals(prior,next);prior=next;values.add(next);}
        assertTrue(values.size()>100);
    }
}
