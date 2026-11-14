package io.github.okww.weekly;

/** Reserve the explicit 60/120-waveplate cost; ambiguous settlement never frees the reservation. */
final class DailyStaminaBudget {
    static final int LIMIT = 240, CLAIM_COST = 60, DOUBLE_CLAIM_COST = 120;
    private int confirmed, reserved, balanceBefore = -1;

    DailyStaminaBudget(String saved) {
        if (saved == null) return;
        String[] values = saved.split(":", -1);
        if (values.length != 3) throw new IllegalArgumentException("每日波片记录损坏，停止而非清零");
        try {
            confirmed = Integer.parseInt(values[0]); reserved = Integer.parseInt(values[1]);
            balanceBefore = Integer.parseInt(values[2]);
        } catch (NumberFormatException error) { throw new IllegalArgumentException("每日波片记录损坏", error); }
        if (confirmed < 0 || confirmed > LIMIT || confirmed % CLAIM_COST != 0
                || (reserved != 0 && !validClaimCost(reserved)) || confirmed + reserved > LIMIT
                || (reserved == 0 ? balanceBefore != -1 : balanceBefore < reserved || balanceBefore > 9999))
            throw new IllegalArgumentException("每日波片记录越界，停止而非清零");
    }
    int confirmed() { return confirmed; }
    int reserved() { return reserved; }
    int expectedAfter() { return reserved == 0 ? -1 : balanceBefore - reserved; }
    int remaining() { return LIMIT - confirmed - reserved; }
    String save() { return confirmed + ":" + reserved + ":" + balanceBefore; }
    static boolean validClaimCost(int cost) { return cost == CLAIM_COST || cost == DOUBLE_CLAIM_COST; }

    boolean canClaim(int currentBalance, int displayedCost, boolean usesBackup) {
        return validClaimCost(displayedCost) && currentBalance >= displayedCost && currentBalance <= 9999
                && !usesBackup && reserved == 0 && remaining() >= displayedCost;
    }
    void reserve(int currentBalance, int displayedCost, boolean usesBackup) {
        if (!canClaim(currentBalance, displayedCost, usesBackup))
            throw new IllegalStateException("波片不足、超预算、未核销或领取方式不符，未允许领取");
        reserved = displayedCost; balanceBefore = currentBalance;
    }
    void confirm(int balanceAfter) {
        if (reserved == 0 || balanceAfter < 0 || balanceBefore - balanceAfter != reserved)
            throw new IllegalStateException("扣费未核实，保留预留额度并停止");
        confirmed += reserved; reserved = 0; balanceBefore = -1;
    }
}
