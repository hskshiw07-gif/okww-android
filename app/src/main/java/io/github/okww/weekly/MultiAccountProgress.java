package io.github.okww.weekly;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Completion checkpoints are separate from the per-UID spending ledger; corrupt data never resets either. */
final class MultiAccountProgress {
    enum Status { RUNNING, PENDING, COMPLETED, FAILED, CANCELLED }
    static final class Entry {
        final String signature;
        final Status status;
        final long updated;
        Entry(String signature,Status status,long updated){this.signature=signature;this.status=status;this.updated=updated;}
    }
    private final DailyStaminaLedger.Store store;
    final String day;
    private Map<String,Entry> entries=new LinkedHashMap<>();
    MultiAccountProgress(DailyStaminaLedger.Store store,long now) throws IOException {
        this.store=store;day=DailyStaminaLedger.gameDay(now);
        String saved=store.read();if(saved==null)return;
        if(saved.length()>65536)throw new IllegalStateException("多账号进度文件过大，保留文件");
        String[] lines=saved.split("\n",-1);
        if(lines.length<2||!lines[0].equals("1"))throw new IllegalStateException("多账号进度版本异常");
        LocalDate prior=LocalDate.parse(lines[1]);
        if(prior.isAfter(LocalDate.parse(day)))throw new IllegalStateException("游戏日期倒退，保留多账号记录");
        for(int i=2;i<lines.length;i++){
            String[] f=lines[i].split("\t",-1);
            if(f.length!=5||!f[0].matches("[0-9]{9}"))throw new IllegalStateException("多账号进度格式异常");
            long at=Long.parseLong(f[2]);if(at<0||at>now)throw new IllegalStateException("多账号记录时钟异常");
            new MultiAccountPlan.Account(f[3],f[0],Integer.parseInt(f[4]),true);
            Entry e=new Entry(f[3]+"\t"+f[4],Status.valueOf(f[1]),at);
            if(entries.put(f[0],e)!=null)throw new IllegalStateException("多账号进度 UID 重复");
        }
        if(!lines[1].equals(day))entries.clear();
    }
    Map<String,Entry> entries(){return new LinkedHashMap<>(entries);}
    boolean completed(MultiAccountPlan.Account a){
        Entry e=entries.get(a.uid);return e!=null&&e.status==Status.COMPLETED&&e.signature.equals(a.signature());
    }
    void mark(MultiAccountPlan.Account a,Status status,long now) throws IOException {
        requireDay(now);
        Entry old=entries.get(a.uid);if(old!=null&&now<old.updated)throw new IllegalStateException("进度时钟倒退");
        Map<String,Entry> next=new LinkedHashMap<>(entries);next.put(a.uid,new Entry(a.signature(),status,now));
        StringBuilder value=new StringBuilder("1\n").append(day);
        for(Map.Entry<String,Entry> item:next.entrySet()){
            Entry e=item.getValue();value.append('\n').append(item.getKey()).append('\t').append(e.status)
                    .append('\t').append(e.updated).append('\t').append(e.signature);
        }
        store.write(value.toString());
        if(!value.toString().equals(store.read()))throw new IOException("多账号进度写后核验失败，停止后续输入");
        entries=next;
    }
    void requireDay(long now){if(!day.equals(DailyStaminaLedger.gameDay(now)))throw new IllegalStateException("游戏日已改变，停止并重新核验账号/账本");}
}
