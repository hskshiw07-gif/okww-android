package io.github.okww.weekly;

/** Frozen remainder of the game-day target, not permission to exchange resources or submit a reward claim. */
final class DailyStaminaPlan {
    final int plannedBudget,exchangeAmount,ordinaryBefore,reserveBefore,confirmedAtStart;

    private DailyStaminaPlan(int plannedBudget,int exchangeAmount,int confirmedAtStart,int ordinaryBefore,int reserveBefore){
        this.plannedBudget=plannedBudget;this.exchangeAmount=exchangeAmount;this.confirmedAtStart=confirmedAtStart;
        this.ordinaryBefore=ordinaryBefore;this.reserveBefore=reserveBefore;
    }
    static DailyStaminaPlan create(int ordinary,int reserve,int ledgerConfirmed,int pending){
        if(ordinary<0||ordinary>9999||reserve<0||reserve>9999)
            throw new IllegalArgumentException("波片或备用资源读数越界，未制定消费计划");
        validateConfirmed(ledgerConfirmed);
        if(pending!=0&&!DailyStaminaBudget.validClaimCost(pending)||ledgerConfirmed+pending>DailyStaminaBudget.LIMIT)
            throw new IllegalArgumentException("波片预留账本异常，未制定消费计划");
        if(pending!=0)throw new IllegalStateException("存在未核销扣费，保留预留并停止兑换与开跑");
        int dailyRemaining=DailyStaminaBudget.LIMIT-ledgerConfirmed;
        if(dailyRemaining==0)return new DailyStaminaPlan(0,0,ledgerConfirmed,ordinary,reserve);
        int total=ledgerConfirmed+ordinary+reserve;
        int tier=total>=240?240:total>=180?180:0;
        if(tier==0)throw new IllegalStateException("本游戏日已用与普通备用波片合计不足180，停止；不使用星声或结晶溶剂");
        int planned=tier-ledgerConfirmed;
        return new DailyStaminaPlan(planned,Math.max(0,planned-ordinary),ledgerConfirmed,ordinary,reserve);
    }
    static DailyStaminaPlan ordinaryOnly(int ordinary,int ledgerConfirmed,int pending){
        if(ordinary<0||ordinary>9999)throw new IllegalArgumentException("普通波片读数越界");
        validateConfirmed(ledgerConfirmed);
        if(pending!=0)throw new IllegalStateException("存在未核销扣费，禁止重算普通波片计划");
        int budget=Math.min(ordinary/60*60,DailyStaminaBudget.LIMIT-ledgerConfirmed);
        return new DailyStaminaPlan(budget,0,ledgerConfirmed,ordinary,0);
    }
    int remaining(int ledgerConfirmed){
        validateConfirmed(ledgerConfirmed);
        int spent=ledgerConfirmed-confirmedAtStart;
        if(spent<0||spent>plannedBudget)throw new IllegalStateException("计划期间账本倒退或已超本轮额度，停止而非重算计划");
        return plannedBudget-spent;
    }
    private static void validateConfirmed(int confirmed){
        if(confirmed<0||confirmed>DailyStaminaBudget.LIMIT||confirmed%DailyStaminaBudget.CLAIM_COST!=0)
            throw new IllegalArgumentException("已核销波片账本越界，未制定消费计划");
    }
}
