package io.github.okww.weekly;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureDemandTest {
    @Test public void idleFramesDoNotConvertAndPollingCoalesces() {
        CaptureDemand d = new CaptureDemand();
        for(int i=0;i<600;i++) assertFalse(d.take());
        for(int i=0;i<20;i++) d.request();
        assertTrue(d.take());
        assertFalse(d.take());
    }
    @Test public void requestDuringConversionIsNotLost() {
        CaptureDemand d = new CaptureDemand();
        d.request();
        assertTrue(d.take());
        d.request();
        assertTrue(d.take());
        assertFalse(d.take());
    }
}
