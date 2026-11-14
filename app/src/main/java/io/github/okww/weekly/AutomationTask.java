package io.github.okww.weekly;

enum AutomationTask {
    REDEEM("单号兑换", "RedeemCodeTask", "redeem-state.json",
            "开发待真机验收：只为当前新鲜核验UID兑换已主动启用的礼包码，再检查邮件。填码会覆盖系统剪贴板为当前兑换码，并点击游戏粘贴图标，不保存原剪贴板。不会切号、购买或消耗波片；提交前保存记录，明确游戏结果才记成功，结果未知停止并保留待核验。"),
    MULTI_REDEEM("多号兑换", "MultiAccountRedeemCodeTask", "multi-redeem-state.json",
            "开发待真机验收：按已启用账号计划，当前号优先逐号兑换已主动启用的礼包码，登录后复核UID并检查邮件。填码会覆盖系统剪贴板为当前兑换码，并点击游戏粘贴图标，不保存原剪贴板。已有未核销波片或待核验兑换记录时停止；未知登录界面不猜位置。不购买、不消耗波片。"),
    MULTI_DAILY("多账号每日", "MultiAccountDailyTask", "multi-account-state.json",
            "离线开发待真机验收：按私有账号计划，先核验当前UID、逐号执行每日、再切号复核UID。只用普通波片，每账号/北京时间04:00游戏日最多240；不补充、不兑换备用、不购买。未核验结果保留待完成，错误/验证码/未知页面停止。先配置并启用账号；图标或列表滚动缺证据时暂停，不套PC坐标。"),
    GARDEN("幻梦游园", "GardenTask", "garden-state.json",
            "由手机代码推进游园、选择卡牌与事件，核实游历值6000后停止。跳过可选商店购买，不消耗波片。"),
    DAILY("每日 · 无音区", "DailyTask", "daily-state.json",
            "开发验证：识别账号与编队，进入已适配的直接挑战无音区并执行内置轴；千咲、弗洛洛、爱弥斯、心、绯雪、秧秧·玄翎、清宵、守岸人、嘉贝莉娜已接执行分派；其中四人仍缺专属手机标定，开战前列明全部缺口并停止，不回退通用连点。仅用当前普通波片，按整60冻结本轮额度（最多240）；不补充、不兑换备用波片、不购买。普通波片不足60跳过无音区进入免费奖励检查。优先明确120波片的双倍领取，余量不足120或双倍未开放时才选明确60的单倍；按账号/北京时间04:00游戏日累计最多240，重启保留扣费账本。随后检查每日免费奖励、邮件与电台，不购买、不删除。战斗隐藏浮窗，切离游戏或通知栏停止可中止；未核验结果不记完成。");

    final String title, id, stateFile, confirmation;
    AutomationTask(String title, String id, String stateFile, String confirmation) {
        this.title = title; this.id = id; this.stateFile = stateFile; this.confirmation = confirmation;
    }
    static AutomationTask restore(String value) {
        for (AutomationTask task : values()) if (task.name().equals(value)) return task;
        throw new IllegalArgumentException("任务配置异常，请重新选择任务");
    }
}
