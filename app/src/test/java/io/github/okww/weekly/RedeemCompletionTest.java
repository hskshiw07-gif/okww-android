package io.github.okww.weekly;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RedeemCompletionTest {
    @Test public void closedAfterFirstDoneNeverSendsAgain() throws Exception {
        AtomicBoolean visible=new AtomicBoolean(true);AtomicInteger sent=new AtomicInteger();
        RedeemTextInput.completeEditor(visible::get,()->{sent.incrementAndGet();visible.set(false);return true;},()->{});
        assertEquals(1,sent.get());
    }
    @Test public void stillOpenAllowsExactlyOneFreshlyCheckedDone() throws Exception {
        AtomicBoolean visible=new AtomicBoolean(true);AtomicInteger checked=new AtomicInteger();
        RedeemTextInput.completeEditor(visible::get,()->{if(checked.incrementAndGet()==2)visible.set(false);return true;},()->{});
        assertEquals(2,checked.get());
    }
    @Test public void stillOpenAfterTwoDoneRequestsStops() {
        AtomicInteger sent=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->RedeemTextInput.completeEditor(()->true,()->{sent.incrementAndGet();return true;},()->{}));
        assertEquals(2,sent.get());
    }
    @Test public void cancellationAfterFirstRequestPreventsRetry() {
        AtomicBoolean allowed=new AtomicBoolean(true);AtomicInteger sent=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->RedeemTextInput.completeEditor(()->true,()->{
            sent.incrementAndGet();allowed.set(false);return true;
        },()->{if(!allowed.get())throw new IllegalStateException("cancelled");}));
        assertEquals(1,sent.get());
    }
    @Test public void missingCurrentEditorStopsWithoutAnotherRequest() {
        AtomicInteger checked=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->RedeemTextInput.completeEditor(()->true,()->{checked.incrementAndGet();return false;},()->{}));
        assertEquals(1,checked.get());
    }
    @Test public void changedEditorOnRetryStopsRatherThanAssumingCompletion() {
        AtomicInteger checked=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->RedeemTextInput.completeEditor(()->true,()->checked.incrementAndGet()==1,()->{}));
        assertEquals(2,checked.get());
    }
}
