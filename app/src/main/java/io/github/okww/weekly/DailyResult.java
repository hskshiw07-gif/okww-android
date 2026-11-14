package io.github.okww.weekly;

/** Returned by this invocation, never inferred from yesterday's JSON or a normal method return. */
final class DailyResult {
    final String uid, day;
    final boolean complete;
    final int spent, reserved;
    DailyResult(String uid, String day, boolean complete, int spent, int reserved) {
        if(uid==null||!uid.matches("[0-9]{9}")||day==null||spent<0||spent>240||reserved<0||reserved>240||spent+reserved>240)
            throw new IllegalArgumentException("每日结果缺少有效账号或消费核验");
        java.time.LocalDate.parse(day);
        if(complete&&reserved!=0)throw new IllegalArgumentException("未核销扣费不作为完成结果");
        this.uid=uid;this.day=day;this.complete=complete;this.spent=spent;this.reserved=reserved;
    }
}
