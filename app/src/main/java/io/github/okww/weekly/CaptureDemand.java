package io.github.okww.weekly;

/** Coalesce polling requests; consume before conversion so concurrent requests are not lost. */
final class CaptureDemand {
    private boolean pending;
    synchronized void request() { pending = true; }
    synchronized boolean take() {
        boolean result = pending;
        pending = false;
        return result;
    }
}
