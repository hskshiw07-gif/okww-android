package io.github.okww.weekly;

import java.util.ArrayList;
import java.util.List;

/** PC semantics: current account first, one pass in configured order, incomplete is retryable next run. */
final class MultiAccountDaily {
    interface Port {
        long now();
        void checkLedger(String uid) throws Exception;
        String identifyCurrent() throws Exception;
        String switchAccount(String currentUid,MultiAccountPlan.Account target) throws Exception;
        DailyResult runDaily(MultiAccountPlan.Account account) throws Exception;
        void progress(MultiAccountProgress progress) throws Exception;
    }
    static boolean run(MultiAccountPlan plan,MultiAccountProgress progress,Port port) throws Exception {
        List<MultiAccountPlan.Account> enabled=plan.enabled();
        if(enabled.isEmpty())throw new IllegalStateException("请先保存并启用至少一个账号");
        check(progress,port);for(MultiAccountPlan.Account account:enabled)port.checkLedger(account.uid);
        List<MultiAccountPlan.Account> pending=new ArrayList<>();
        for(MultiAccountPlan.Account a:enabled)if(!progress.completed(a))pending.add(a);
        if(pending.isEmpty()){port.progress(progress);return true;}
        String current=port.identifyCurrent();
        if(current==null||!current.matches("[0-9]{9}"))throw new IllegalStateException("当前账号尚未核验，未退出登录");
        port.checkLedger(current);
        for(int i=0;i<pending.size();i++)if(pending.get(i).uid.equals(current)){pending.add(0,pending.remove(i));break;}
        for(MultiAccountPlan.Account a:pending){
            check(progress,port);progress.mark(a,MultiAccountProgress.Status.RUNNING,port.now());port.progress(progress);
            try{
                if(!a.uid.equals(current))current=port.switchAccount(current,a);
                if(!a.uid.equals(current))throw new IllegalStateException("登录后 UID 与目标绑定不符，未执行每日");
                check(progress,port);
                DailyResult result=port.runDaily(a);
                check(progress,port);
                if(result==null||!a.uid.equals(result.uid)||!progress.day.equals(result.day)||result.reserved!=0)
                    throw new IllegalStateException("每日结果账号/游戏日不符或有未核销扣费，停止切号");
                progress.mark(a,result.complete?MultiAccountProgress.Status.COMPLETED:MultiAccountProgress.Status.PENDING,port.now());
                port.progress(progress);
            }catch(Exception failure){
                try{progress.mark(a,failure instanceof InterruptedException?MultiAccountProgress.Status.CANCELLED:MultiAccountProgress.Status.FAILED,port.now());port.progress(progress);}
                catch(Exception saveFailure){failure.addSuppressed(saveFailure);}
                throw failure;
            }
        }
        return enabled.stream().allMatch(progress::completed);
    }
    private static void check(MultiAccountProgress progress,Port port)throws InterruptedException{
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();progress.requireDay(port.now());
    }
}
