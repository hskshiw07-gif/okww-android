package io.github.okww.weekly;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.Toast;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Existing task-center dialogs, private cache, explicit per-code enablement before automation. */
final class RedeemEditor {
    private static final AtomicBoolean fetching=new AtomicBoolean();
    static void show(Activity activity){
        if(busy(activity))return;
        try{
            RedeemCodes catalog=RedeemCodes.parse(RedeemStore.codes(activity).read());List<RedeemCodes.Code> codes=new ArrayList<>(catalog.codes.values());
            String[] labels=new String[codes.size()];long now=System.currentTimeMillis();
            for(int i=0;i<codes.size();i++){
                RedeemCodes.Code c=codes.get(i);String status=c.invalid()?"无效":RedeemCodes.expired(c.label,now)?"过期/日期异常":c.enabled?"启用":"忽略";
                labels[i]="["+status+"] "+c.code+(c.remote?" · 公共来源":" · 手动")+"\n"+c.reward+" "+c.label;
            }
            String title="兑换码中心 · 可执行 "+catalog.selected(now).size()+"/"+codes.size()+"（点击条目启用/忽略）";
            new AlertDialog.Builder(activity).setTitle(title).setItems(labels,(d,index)->edit(activity,catalog,codes.get(index)))
                    .setPositiveButton("添加/粘贴",(d,w)->add(activity,catalog))
                    .setNeutralButton("获取公开码",(d,w)->fetch(activity,catalog))
                    .setNegativeButton("查看兑换记录",(d,w)->records(activity)).show();
        }catch(Exception error){error(activity,error);}
    }
    private static void edit(Activity activity,RedeemCodes catalog,RedeemCodes.Code code){
        String original=catalog.save();
        new AlertDialog.Builder(activity).setTitle(code.code).setMessage(code.reward+"\n"+code.label+"\n抓取信息不是有效性保证；由游戏明确返回结果。移除码不删除历史兑换记录。")
                .setPositiveButton(code.enabled?"忽略":"启用",(d,w)->{
                    if(busy(activity))return;
                    try{
                        if(!code.enabled&&(code.invalid()||RedeemCodes.expired(code.label,System.currentTimeMillis())))throw new IllegalStateException("已知无效/过期码未启用");
                        catalog.codes.put(code.code,code.enabled(!code.enabled));RedeemStore.saveCodes(activity,original,catalog);show(activity);
                    }catch(Exception e){error(activity,e);}
                }).setNeutralButton("移除",(d,w)->{
                    if(busy(activity))return;try{catalog.codes.remove(code.code);RedeemStore.saveCodes(activity,original,catalog);show(activity);}catch(Exception e){error(activity,e);}
                }).setNegativeButton("返回",(d,w)->show(activity)).show();
    }
    private static void add(Activity activity,RedeemCodes catalog){
        EditText text=new EditText(activity);text.setHint("粘贴兑换码，逗号、空格或换行分隔；不填账号密码");text.setMinLines(4);text.setContentDescription("兑换码列表");
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("添加新码并启用；已有码保留选择").setView(text).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(busy(activity))return;
            try{String original=catalog.save();RedeemCodes next=RedeemCodes.parse(original);next.manual(text.getText().toString());
                RedeemStore.saveCodes(activity,original,next);dialog.dismiss();show(activity);
            }catch(Exception e){error(activity,e);}
        }));dialog.show();
    }
    private static void fetch(Activity activity,RedeemCodes catalog){
        new AlertDialog.Builder(activity).setTitle("获取公开兑换码")
                .setMessage("仅访问PC项目使用的4399公开码源，不上传账号/UID。新抓取的码默认忽略，请核对后主动启用；不会自动运行游戏兑换。")
                .setNegativeButton("取消",null).setPositiveButton("获取",(d,w)->{
                    if(busy(activity)||!fetching.compareAndSet(false,true))return;
                    Toast.makeText(activity,"正在获取公开码，失败时保留原缓存",Toast.LENGTH_LONG).show();String original=catalog.save();
                    new Thread(()->{
                        try{
                            List<RedeemCodes.Code> remote=RedeemFeed.fetch();
                            activity.runOnUiThread(()->{
                                if(activity.isFinishing()||activity.isDestroyed()||busy(activity))return;
                                try{RedeemCodes next=RedeemCodes.parse(original);next.merge(remote,System.currentTimeMillis());
                                    RedeemStore.saveCodes(activity,original,next);show(activity);
                                }catch(Exception e){error(activity,e);}
                            });
                        }catch(Exception e){activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())error(activity,e);});}
                        finally{fetching.set(false);}
                    },"redeem-public-feed").start();
                }).show();
    }
    private static void records(Activity activity){records(activity,false);}
    private static void records(Activity activity,boolean development){
        if(busy(activity))return;
        try{
            RedeemLedger ledger=new RedeemLedger(development?RedeemStore.developmentLedger(activity):RedeemStore.ledger(activity));List<RedeemLedger.Entry> entries=ledger.entries();Collections.reverse(entries);
            String[] lines=new String[entries.size()];for(int i=0;i<entries.size();i++){RedeemLedger.Entry e=entries.get(i);lines[i]=e.uid+" · "+e.code+" · "+e.status+"\n"+e.detail;}
            new AlertDialog.Builder(activity).setTitle(development?"开发合成测试记录 · 不代表兑换奖励":"真实兑换记录 · PENDING须先核对")
                    .setItems(lines,(d,index)->{
                        RedeemLedger.Entry e=entries.get(index);
                        if(e.status!=RedeemLedger.Status.PENDING){new AlertDialog.Builder(activity).setMessage(e.detail).setPositiveButton("关闭",null).show();return;}
                        new AlertDialog.Builder(activity).setTitle("允许重试结果未知的兑换？")
                                .setMessage("UID "+e.uid+"\n"+e.code+"\n先核对游戏结果或邮件。此操作仅允许下次重试，不表示此前失败，不清波片账本。")
                                .setNegativeButton("保留待核验",null).setPositiveButton("已核对，允许重试",(a,b)->{
                                    if(busy(activity))return;try{new RedeemLedger(development?RedeemStore.developmentLedger(activity):RedeemStore.ledger(activity)).approveRetry(e.uid,e.code,System.currentTimeMillis());records(activity,development);}catch(Exception failure){error(activity,failure);}
                                }).show();
                    }).setPositiveButton("返回兑换码中心",(d,w)->show(activity)).setNeutralButton(development?"真实记录":"开发测试记录",(d,w)->records(activity,!development)).setNegativeButton("关闭",null).show();
        }catch(Exception e){error(activity,e);}
    }
    private static boolean busy(Activity activity){
        if(GameAccessibilityService.instance!=null&&GameAccessibilityService.instance.isRunning()){
            Toast.makeText(activity,"请先停止当前任务再编辑或刷新兑换码",Toast.LENGTH_LONG).show();return true;
        }return false;
    }
    private static void error(Activity activity,Exception error){Toast.makeText(activity,"兑换码操作未完成："+error.getMessage(),Toast.LENGTH_LONG).show();}
}
