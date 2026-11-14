package io.github.okww.weekly;

/** A continued pointer must be renewed by fresh observations, not just by a running thread. */
final class HeldTouchLease {
    private long started = -1, observed = -1;
    void start(long now) { if (started >= 0) throw new IllegalStateException("已有持续触点"); started = observed = now; }
    void observed(long now) {
        if (started < 0) return;
        if (now < observed || expired(now)) throw new IllegalStateException("持续触点画面租约失效");
        observed = now;
    }
    boolean hardExpired(long now) { return started >= 0 && (now < observed || now - started >= 30000); }
    boolean expired(long now) { return hardExpired(now) || started >= 0 && now - observed > 1800; }
    void stop() { started = observed = -1; }
}
