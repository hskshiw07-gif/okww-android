package io.github.okww.weekly;

/** Permission ordering only; never grants permissions or starts game input. */
final class PreparationFlow {
    enum State { IDLE, ACCESSIBILITY, CONSENT, CONNECT_ACCESSIBILITY, CONNECT_CAPTURE }
    enum Action { NONE, OPEN_ACCESSIBILITY, REQUEST_CAPTURE, WAIT, ENTER_GAME, CANCELLED }
    private State state = State.IDLE;
    private boolean dailyConfirmed;
    int pendingRequest, nextRequest = 1000;
    Action pendingAction = Action.NONE;

    PreparationFlow(String saved) {
        try { if (saved != null) state = State.valueOf(saved); }
        catch (IllegalArgumentException ignored) { state = State.IDLE; }
    }
    boolean active() { return state != State.IDLE; }
    boolean waiting() { return state == State.CONNECT_ACCESSIBILITY || state == State.CONNECT_CAPTURE; }
    String save() { return state.name(); }
    void cancel() { state = State.IDLE; pendingRequest = 0; pendingAction = Action.NONE; dailyConfirmed = false; }
    void confirmDaily() { if (active()) throw new IllegalStateException("正在准备任务"); dailyConfirmed = true; }
    boolean consumeDailyStart() { boolean confirmed = dailyConfirmed; dailyConfirmed = false; return confirmed; }

    int request(Action action) {
        if (nextRequest >= 65535) throw new IllegalStateException("请重新打开控制台后授权");
        pendingAction = action; pendingRequest = nextRequest++; return pendingRequest;
    }
    Action consumeResult(int code) {
        if (code != pendingRequest || pendingRequest == 0) return Action.NONE;
        Action action = pendingAction; pendingRequest = 0; pendingAction = Action.NONE; return action;
    }

    Action begin(boolean accessibility, boolean capture) {
        if (active()) return Action.NONE;
        if (!accessibility) { state = State.ACCESSIBILITY; return Action.OPEN_ACCESSIBILITY; }
        if (!capture) { state = State.CONSENT; return Action.REQUEST_CAPTURE; }
        return Action.ENTER_GAME;
    }
    void accessibilityReturned() {
        if (state == State.ACCESSIBILITY) state = State.CONNECT_ACCESSIBILITY;
    }
    void captureReturned(boolean granted) {
        if (state == State.CONSENT) { if (granted) state = State.CONNECT_CAPTURE; else cancel(); }
    }
    Action poll(boolean accessibility, boolean capture) {
        if (state == State.CONNECT_ACCESSIBILITY) {
            if (!accessibility) return Action.WAIT;
            state = State.IDLE; return begin(true, capture);
        }
        if (state == State.CONNECT_CAPTURE) {
            if (!accessibility) { cancel(); return Action.CANCELLED; }
            if (!capture) return Action.WAIT;
            state = State.IDLE; return Action.ENTER_GAME;
        }
        return Action.NONE;
    }
}
