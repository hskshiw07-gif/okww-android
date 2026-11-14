package io.github.okww.weekly;

import java.util.*;

final class RedeemBatch {
    interface Port {
        String current()throws Exception;
        void checkWaveplateLedger(String uid)throws Exception;
        String switchTo(String source,MultiAccountPlan.Account target)throws Exception;
        boolean redeem(String uid,List<String> codes,RedeemLedger ledger)throws Exception;
    }
    static boolean run(boolean multi,MultiAccountPlan plan,List<String> codes,RedeemLedger ledger,Port port)throws Exception{
        if(codes.isEmpty())throw new IllegalStateException("没有已启用且有效的兑换码，请先在兑换码中心选择");
        List<MultiAccountPlan.Account> accounts=multi?plan.enabled():List.of();
        if(multi&&accounts.isEmpty())throw new IllegalStateException("请先启用多账号计划");
        check();String current=port.current();
        if(current==null||!current.matches("[0-9]{9}"))throw new IllegalStateException("兑换前UID未核验");
        if(!multi){ledger.requireNoPending(current);boolean mail=port.redeem(current,codes,ledger);verify(current,codes,ledger);return mail;}
        port.checkWaveplateLedger(current);ledger.requireNoPending(current);
        for(MultiAccountPlan.Account a:accounts){ledger.requireNoPending(a.uid);port.checkWaveplateLedger(a.uid);}
        List<MultiAccountPlan.Account> order=new ArrayList<>(accounts);
        for(int i=0;i<order.size();i++)if(order.get(i).uid.equals(current)){order.add(0,order.remove(i));break;}
        for(MultiAccountPlan.Account a:order){
            check();if(!a.uid.equals(current))current=port.switchTo(current,a);
            if(!a.uid.equals(current))throw new IllegalStateException("兑换目标UID不匹配，未沿用旧账号");
            check();boolean mail=port.redeem(a.uid,codes,ledger);verify(a.uid,codes,ledger);
            if(!mail)return false;
        }
        return true;
    }
    private static void verify(String uid,List<String> codes,RedeemLedger ledger){
        ledger.requireNoPending(uid);
        for(String code:codes){RedeemLedger.Entry entry=ledger.get(uid,code);
            if(entry==null||!entry.terminal())throw new IllegalStateException("本账号仍有码未得到明确终态，未继续切号");}
    }
    private static void check()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException();}
}
