package io.github.okww.weekly;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Native editor in the existing console; no credentials or character selections. */
final class MultiAccountEditor {
    static void show(Activity activity){
        if(busy(activity))return;
        try{
            MultiAccountPlan plan=MultiAccountPlan.parse(MultiAccountFiles.plan(activity).read());
            String[] labels=new String[plan.accounts.size()+1];
            for(int i=0;i<plan.accounts.size();i++){
                MultiAccountPlan.Account a=plan.accounts.get(i);labels[i]=(a.enabled?"✓ ":"○ ")+a.login+" · UID "+a.uid+" · 无音区#"+a.tacetIndex;
            }
            labels[labels.length-1]="＋ 添加账号";
            new AlertDialog.Builder(activity).setTitle("多账号计划 · 当前号优先，其余按列表顺序")
                    .setItems(labels,(dialog,index)->edit(activity,plan,index)).setNegativeButton("关闭",null).show();
        }catch(Exception failure){error(activity,failure);}
    }
    private static void edit(Activity activity,MultiAccountPlan plan,int index){
        boolean adding=index==plan.accounts.size();MultiAccountPlan.Account a=adding?null:plan.accounts.get(index);
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);
        int padding=Math.round(20*activity.getResources().getDisplayMetrics().density);box.setPadding(padding,padding,padding,padding);
        EditText login=field(activity,box,"登录列表完整账号文本（可保留星号；不填密码）",a==null?"":a.login,false);
        EditText uid=field(activity,box,"游戏右下角9位 UID",a==null?"":a.uid,true);
        EditText tacet=field(activity,box,"无音区编号（1—21；第5项起滚动仍待标定）",a==null?"1":""+a.tacetIndex,true);
        CheckBox enabled=new CheckBox(activity);enabled.setText("启用此账号");enabled.setChecked(a!=null&&a.enabled);box.addView(enabled);
        TextView note=new TextView(activity);note.setText("仅保存到本机。新账号默认停用，队伍自动识别。\n每号最多240普通波片，不补充、不兑换。账号须已存在于游戏登录列表；手机切号待真机验收。");box.addView(note);
        android.widget.ScrollView scroll=new android.widget.ScrollView(activity);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(adding?"添加账号":"编辑账号")
                .setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null)
                .setNeutralButton(adding?"返回":"删除",null).create();
        dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(busy(activity))return;
                try{
                    List<MultiAccountPlan.Account> next=new ArrayList<>(plan.accounts);
                    MultiAccountPlan.Account entry=new MultiAccountPlan.Account(login.getText().toString().trim(),uid.getText().toString().trim(),Integer.parseInt(tacet.getText().toString().trim()),enabled.isChecked());
                    if(adding)next.add(entry);else next.set(index,entry);
                    save(activity,plan,new MultiAccountPlan(next));dialog.dismiss();show(activity);
                }catch(Exception failure){error(activity,failure);}
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                if(adding){dialog.dismiss();show(activity);return;}
                if(busy(activity))return;
                new AlertDialog.Builder(activity).setMessage("仅移除计划项；不删除游戏账号、完成记录或波片账本。").setNegativeButton("取消",null)
                        .setPositiveButton("移除",(d,which)->{
                            try{if(busy(activity))return;List<MultiAccountPlan.Account> next=new ArrayList<>(plan.accounts);next.remove(index);
                                save(activity,plan,new MultiAccountPlan(next));dialog.dismiss();show(activity);}
                            catch(Exception failure){error(activity,failure);}
                        }).show();
            });
        });dialog.show();
    }
    private static EditText field(Activity a,LinearLayout box,String title,String value,boolean numeric){
        TextView label=new TextView(a);label.setText(title);box.addView(label);
        EditText input=new EditText(a);input.setSingleLine(true);input.setText(value);input.setContentDescription(title);
        input.setInputType(numeric?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        box.addView(input);return input;
    }
    private static void save(Activity activity,MultiAccountPlan original,MultiAccountPlan plan)throws Exception{
        DailyStaminaLedger.Store store=MultiAccountFiles.plan(activity);
        if(!MultiAccountPlan.parse(store.read()).save().equals(original.save()))throw new IllegalStateException("账号计划已变化，请关闭后重新编辑");
        store.write(plan.save());if(!plan.save().equals(store.read()))throw new java.io.IOException("账号计划写后核验失败");
    }
    private static boolean busy(Activity activity){
        if(GameAccessibilityService.instance!=null&&GameAccessibilityService.instance.isRunning()){
            Toast.makeText(activity,"先停止当前任务再编辑账号计划",Toast.LENGTH_LONG).show();return true;
        }return false;
    }
    private static void error(Activity activity,Exception error){Toast.makeText(activity,"未保存/未加载："+error.getMessage(),Toast.LENGTH_LONG).show();}
}
