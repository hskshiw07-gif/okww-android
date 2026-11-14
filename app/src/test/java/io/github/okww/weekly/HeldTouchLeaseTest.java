package io.github.okww.weekly;
import org.junit.Test;
import static org.junit.Assert.*;
public class HeldTouchLeaseTest {
    @Test public void staleOrLongRunningHoldsExpireEvenWithNoNewCommand() {
        HeldTouchLease l=new HeldTouchLease();l.start(10);
        assertFalse(l.expired(1810));assertTrue(l.expired(1811));assertFalse(l.hardExpired(1811));
        assertThrows(IllegalStateException.class,()->l.observed(1811));
        l.stop();assertFalse(l.expired(99999));l.start(100000);
        for(int i=1;i<30;i++)l.observed(100000+i*1000);
        assertTrue(l.expired(130000));assertTrue(l.hardExpired(130000));
    }
    @Test public void duplicateStartAndBackwardsClockAreRejected() {
        HeldTouchLease l=new HeldTouchLease();l.start(100);
        assertThrows(IllegalStateException.class,()->l.start(101));
        assertThrows(IllegalStateException.class,()->l.observed(99));
        assertTrue(l.hardExpired(99));
        l.stop();l.start(200);l.observed(300);assertFalse(l.expired(400));
    }
}
