package io.github.okww.weekly;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.io.IOException;

/** Per-account game-day ledger; persistence must succeed before the caller can send a claim. */
final class DailyStaminaLedger {
    interface Store {
        String read() throws IOException;
        void write(String value) throws IOException;
    }
    private final Store store;
    private final String uid, day;
    private DailyStaminaBudget budget;
    private final int initialConfirmed;

    DailyStaminaLedger(Store store, String uid, long now) throws IOException {
        if (store == null || uid == null || !uid.matches("[0-9]{9}"))
            throw new IllegalArgumentException("波片账本需要已核验的账号");
        this.store = store; this.uid = uid; day = gameDay(now);
        String saved = store.read();
        budget = new DailyStaminaBudget(null);
        if (saved != null) {
            String[] fields = saved.split("\\n", -1);
            if (fields.length != 4 || !fields[0].equals("1") || !fields[1].equals(uid))
                throw new IllegalStateException("波片账本版本或账号不符，停止而非重置");
            LocalDate previous = LocalDate.parse(fields[2]);
            DailyStaminaBudget old = new DailyStaminaBudget(fields[3]);
            if (previous.isAfter(LocalDate.parse(day))) throw new IllegalStateException("日期倒退，保留波片账本");
            if (fields[2].equals(day)) budget = old;
            else if (old.reserved() != 0) throw new IllegalStateException("前一游戏日有未核销扣费，保留预留记录");
        }
        initialConfirmed = budget.confirmed();
    }
    // Same Beijing 04:00 boundary as src/task/daily_plan.py; no device-timezone dependency.
    static String gameDay(long now) {
        if (now < 0) throw new IllegalArgumentException("时钟异常");
        return Instant.ofEpochMilli(now).atOffset(ZoneOffset.ofHours(8)).minusHours(4).toLocalDate().toString();
    }
    int confirmed() { return budget.confirmed(); }
    String uid() { return uid; }
    int spentThisRun() { return budget.confirmed() - initialConfirmed; }
    int reserved() { return budget.reserved(); }
    int expectedAfter() { return budget.expectedAfter(); }
    int remaining() { return budget.remaining(); }
    String day() { return day; }
    boolean canClaim(int balance, int cost, boolean backup) { return budget.canClaim(balance, cost, backup); }

    void reserve(int balance, int cost, boolean backup, long now) throws IOException {
        if (!gameDay(now).equals(day)) throw new IllegalStateException("游戏日已切换，请重新核验账号后开始");
        DailyStaminaBudget next = new DailyStaminaBudget(budget.save());
        next.reserve(balance, cost, backup);
        save(next);
    }
    void confirm(int balanceAfter) throws IOException {
        DailyStaminaBudget next = new DailyStaminaBudget(budget.save());
        next.confirm(balanceAfter);
        save(next);
    }
    private void save(DailyStaminaBudget next) throws IOException {
        String value = "1\n" + uid + "\n" + day + "\n" + next.save();
        store.write(value);
        if (!value.equals(store.read())) throw new IOException("波片账本写后核验失败，禁止继续领取");
        budget = next;
    }
}
