package io.github.okww.weekly;

import java.io.IOException;
import java.util.*;

/** Per UID+code, not daily. A pending submission is never silently retried after a crash. */
final class RedeemLedger {
    enum Status { PENDING, SUCCESS, ALREADY_USED, INVALID, EXPIRED, RETRYABLE }
    static final class Entry {
        final String uid,code,detail;final Status status;final long at;
        Entry(String uid,String code,Status status,long at,String detail){this.uid=uid;this.code=code;this.status=status;this.at=at;this.detail=detail;}
        boolean terminal(){return status!=Status.PENDING&&status!=Status.RETRYABLE;}
    }
    private final DailyStaminaLedger.Store store;
    private Map<String,Entry> entries=new LinkedHashMap<>();
    RedeemLedger(DailyStaminaLedger.Store store)throws IOException{
        this.store=store;String saved=store.read();if(saved==null)return;
        if(saved.length()>4000000)throw new IllegalArgumentException("兑换记录文件过大");
        String[] rows=saved.split("\n",-1);if(!rows[0].equals("1")||rows.length>4001)throw new IllegalArgumentException("兑换记录版本或数量异常");
        for(int i=1;i<rows.length;i++){
            String[] f=rows[i].split("\t",-1);if(f.length!=5)throw new IllegalArgumentException("兑换记录格式异常");
            String key=key(f[0],f[1]);long at=Long.parseLong(f[3]);if(at<0)throw new IllegalArgumentException("兑换记录时间异常");
            Entry entry=new Entry(f[0],RedeemCodes.normalize(f[1]),Status.valueOf(f[2]),at,RedeemCodes.field(RedeemCodes.decode(f[4])));
            if(entries.put(key,entry)!=null)throw new IllegalArgumentException("兑换记录重复");
        }
    }
    private static String key(String uid,String code){if(uid==null||!uid.matches("[0-9]{9}"))throw new IllegalArgumentException("兑换记录缺少UID");return uid+"\t"+RedeemCodes.normalize(code);}
    Entry get(String uid,String code){return entries.get(key(uid,code));}
    List<Entry> entries(){return new ArrayList<>(entries.values());}
    void requireNoPending(String uid){for(Entry e:entries.values())if(e.uid.equals(uid)&&e.status==Status.PENDING)throw new IllegalStateException("账号有结果未核验的兑换提交，请在兑换码中心核对后再重试");}
    void submit(String uid,String code,long now)throws IOException{
        Entry prior=get(uid,code);if(prior!=null&&(prior.status==Status.PENDING||prior.terminal()))throw new IllegalStateException("兑换码已提交待核验或已有终态");
        mark(uid,code,Status.PENDING,now,"确认输入前已落盘；等待明确游戏结果");
    }
    void result(String uid,String code,Status status,long now,String detail)throws IOException{
        Entry prior=get(uid,code);if(prior==null||prior.status!=Status.PENDING||status==Status.PENDING)throw new IllegalStateException("结果未归属待核验提交");
        mark(uid,code,status,now,detail);
    }
    void approveRetry(String uid,String code,long now)throws IOException{
        Entry prior=get(uid,code);if(prior==null||prior.status!=Status.PENDING)throw new IllegalStateException("仅结果未知的提交可手动批准重试");
        mark(uid,code,Status.RETRYABLE,now,"用户已核对并确认允许重试；不表示此前兑换失败");
    }
    private void mark(String uid,String code,Status status,long now,String detail)throws IOException{
        String key=key(uid,code);Entry prior=entries.get(key);
        if(now<0||prior!=null&&now<prior.at)throw new IllegalStateException("兑换记录时钟倒退");
        Map<String,Entry> next=new LinkedHashMap<>(entries);next.put(key,new Entry(uid,RedeemCodes.normalize(code),status,now,RedeemCodes.field(detail)));
        if(next.size()>4000)throw new IllegalStateException("兑换记录已达上限，未提交");
        StringBuilder out=new StringBuilder("1");for(Entry e:next.values())out.append('\n').append(e.uid).append('\t').append(e.code)
                .append('\t').append(e.status).append('\t').append(e.at).append('\t').append(RedeemCodes.encode(e.detail));
        store.write(out.toString());if(!out.toString().equals(store.read()))throw new IOException("兑换记录写后核验失败，停止输入");entries=next;
    }
}
