package io.github.okww.weekly;

import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.okww.weekly.PreparationFlow.Action.*;

public class PreparationFlowTest {
    @Test public void cancelledRequestCannotStartCaptureOrConsumeNewRequest() {
        PreparationFlow flow = new PreparationFlow(null);
        flow.begin(true, false);
        int old = flow.request(REQUEST_CAPTURE);
        flow.cancel();
        assertEquals(NONE, flow.consumeResult(old));
        flow.begin(true, false);
        int current = flow.request(REQUEST_CAPTURE);
        assertNotEquals(old, current);
        assertEquals(NONE, flow.consumeResult(old));
        assertEquals(REQUEST_CAPTURE, flow.consumeResult(current));
        assertEquals(NONE, flow.consumeResult(current));
    }
    @Test public void manualCaptureAndSettingsResultsAreAlsoSingleUse() {
        PreparationFlow flow = new PreparationFlow(null);
        int settings = flow.request(OPEN_ACCESSIBILITY);
        assertEquals(OPEN_ACCESSIBILITY, flow.consumeResult(settings));
        int capture = flow.request(REQUEST_CAPTURE);
        assertEquals(REQUEST_CAPTURE, flow.consumeResult(capture));
        assertFalse(flow.active());
    }
    @Test public void readyPermissionsSkipAllPrompts() {
        PreparationFlow flow = new PreparationFlow(null);
        assertEquals(ENTER_GAME, flow.begin(true, true));
        assertFalse(flow.active());
    }
    @Test public void requestsEachMissingPermissionOnlyOnce() {
        PreparationFlow flow = new PreparationFlow(null);
        assertEquals(OPEN_ACCESSIBILITY, flow.begin(false, false));
        assertEquals(NONE, flow.begin(false, false));
        assertEquals(NONE, flow.poll(true, false));
        flow.accessibilityReturned();
        assertEquals(WAIT, flow.poll(false, false));
        assertEquals(REQUEST_CAPTURE, flow.poll(true, false));
        assertEquals(NONE, flow.poll(true, false));
        flow.captureReturned(true);
        assertEquals(WAIT, flow.poll(true, false));
        assertEquals(ENTER_GAME, flow.poll(true, true));
        assertEquals(NONE, flow.poll(true, true));
    }
    @Test public void connectedAccessibilityOnlyNeedsCapture() {
        PreparationFlow flow = new PreparationFlow(null);
        assertEquals(REQUEST_CAPTURE, flow.begin(true, false));
        flow.captureReturned(false);
        assertFalse(flow.active());
        assertEquals(NONE, flow.poll(true, false));
    }
    @Test public void captureAloneDoesNotSkipAccessibility() {
        PreparationFlow flow = new PreparationFlow(null);
        assertEquals(OPEN_ACCESSIBILITY, flow.begin(false, true));
        flow.accessibilityReturned();
        assertEquals(ENTER_GAME, flow.poll(true, true));
    }
    @Test public void stoppedOrLatePermissionResultsDoNotLaunch() {
        PreparationFlow flow = new PreparationFlow(null);
        flow.begin(true, false); flow.cancel(); flow.captureReturned(true);
        assertEquals(NONE, flow.poll(true, true));
        flow.begin(false, false); flow.cancel(); flow.accessibilityReturned();
        assertEquals(NONE, flow.poll(true, true));
    }
    @Test public void revokedAccessibilityCancelsCaptureContinuation() {
        PreparationFlow flow = new PreparationFlow(null);
        flow.begin(true, false); flow.captureReturned(true);
        assertEquals(CANCELLED, flow.poll(false, true));
        assertFalse(flow.active());
    }
    @Test public void recreationPreservesStepButNotUnpromptedLaunch() {
        PreparationFlow original = new PreparationFlow(null);
        original.begin(true, false);
        PreparationFlow restored = new PreparationFlow(original.save());
        assertEquals(NONE, restored.poll(true, true));
        restored.captureReturned(true);
        assertEquals(ENTER_GAME, restored.poll(true, true));
        assertFalse(new PreparationFlow("invalid").active());
    }
}
