package io.github.okww.weekly;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Native editable node only; no clipboard overwrite, shell input, custom keyboard or password input. */
final class RedeemTextInput implements AutoCloseable {
    static final class Lease {
        private final long started;
        private final AtomicBoolean allowed=new AtomicBoolean(true);
        Lease(long started){this.started=started;}
        boolean valid(long now){return allowed.get()&&now>=started&&now-started<=6000;}
        void cancel(){allowed.set(false);}
    }
    private static volatile RedeemTextInput active;
    private final GameAccessibilityService service;
    private final long started=SystemClock.elapsedRealtime();
    private final Lease lease=new Lease(started);
    private volatile int doneRequests;
    RedeemTextInput(GameAccessibilityService service){this.service=service;active=this;}
    static boolean expectedIme(GameAccessibilityService service,CharSequence eventPackage){
        try{
            RedeemTextInput input=active;
            return input!=null&&input.service==service&&input.lease.valid(SystemClock.elapsedRealtime())
                    &&service.gameInFront()&&eventPackage!=null&&eventPackage.toString().equals(imePackage(service));
        }catch(RuntimeException unavailable){return false;}
    }
    private static String imePackage(GameAccessibilityService service){
        String id=Settings.Secure.getString(service.getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);
        ComponentName name=id==null?null:ComponentName.unflattenFromString(id);return name==null?"":name.getPackageName();
    }
    boolean keyboardVisible(){
        for(AccessibilityWindowInfo window:service.getWindows())if(window.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD)return true;
        return false;
    }
    void finishInput(String code)throws Exception{
        for(int i=0;i<10&&!keyboardVisible();i++){check();Thread.sleep(100);}
        check();
        if(!keyboardVisible())throw new IllegalStateException("原生输入已写入，等待键盘未就绪，未提交");
        try{
            completeEditor(this::keyboardVisible,()->editWhenReady(code,true),this::check);
            recordCompletion("keyboard-closed");
            // Keep the existing short IME lease until its closing accessibility events drain.
            Thread.sleep(250);check();
        }catch(Exception failure){
            try{recordCompletion("stopped");}catch(Exception write){failure.addSuppressed(write);}
            throw failure;
        }
    }
    static void completeEditor(BooleanSupplier visible,Callable<Boolean> finish,Runnable check)throws Exception{
        for(int attempt=0;attempt<2;attempt++){
            check.run();if(!visible.getAsBoolean())return;
            // DONE is asynchronous: retry only through a fresh, fully validated native editor lookup.
            if(!finish.call())throw new IllegalStateException("原生输入确认节点已消失，未提交");
            for(int i=0;i<15&&visible.getAsBoolean();i++){check.run();Thread.sleep(100);}
            check.run();if(!visible.getAsBoolean())return;
        }
        throw new IllegalStateException("两次原生DONE后键盘仍未关闭，未提交");
    }
    private void recordCompletion(String phase)throws Exception{
        GardenEngine.writeState(service,"redeem-input-state.json",new org.json.JSONObject()
                .put("timestamp",System.currentTimeMillis()).put("phase",phase).put("done_requests",doneRequests)
                .put("elapsed_ms",SystemClock.elapsedRealtime()-started).put("keyboard_visible",keyboardVisible())
                .put("lease_valid",lease.valid(SystemClock.elapsedRealtime())));
    }
    void set(String code)throws Exception{
        for(int attempt=0;attempt<8;attempt++){
            if(editWhenReady(code,false))return;
            Thread.sleep(150);
        }
        throw new IllegalStateException("兑换原生输入框等待后仍未暴露，保留现场");
    }
    static boolean doneEditor(String packageName,int imeOptions){return GardenRules.GAME_PACKAGE.equals(packageName)
            &&(imeOptions&android.view.inputmethod.EditorInfo.IME_MASK_ACTION)==android.view.inputmethod.EditorInfo.IME_ACTION_DONE;}
    private boolean editWhenReady(String code,boolean finish)throws Exception{
        String value=RedeemCodes.normalize(code);CompletableFuture<Boolean> done=new CompletableFuture<>();
        service.main.post(()->{
            List<AccessibilityNodeInfo> nodes=new ArrayList<>();
            try{
                check();
                for(AccessibilityWindowInfo window:service.getWindows())if(window.getType()==AccessibilityWindowInfo.TYPE_APPLICATION&&window.isFocused()){
                    AccessibilityNodeInfo root=window.getRoot();if(root!=null)collect(root,nodes,new int[]{0},0);
                }
                if(nodes.isEmpty()){done.complete(false);return;}
                if(nodes.size()!=1)throw new IllegalStateException("兑换原生输入框不唯一，未输入");
                AccessibilityNodeInfo node=nodes.get(0);String old=node.getText()==null?"":node.getText().toString().trim();
                if(finish){
                    if(!old.equals(value)||android.os.Build.VERSION.SDK_INT<33)
                        throw new IllegalStateException("兑换编辑完成需要Android13以上及全文回读，未提交");
                    android.accessibilityservice.InputMethod ime=service.getInputMethod();
                    android.view.inputmethod.EditorInfo info=ime==null?null:ime.getCurrentInputEditorInfo();
                    android.accessibilityservice.InputMethod.AccessibilityInputConnection connection=ime==null?null:ime.getCurrentInputConnection();
                    if(info==null||connection==null||!ime.getCurrentInputStarted()||!doneEditor(info.packageName,info.imeOptions))
                        throw new IllegalStateException("游戏原生DONE编辑连接尚未就绪，未提交兑换");
                    check();
                    // ACTION_IME_ENTER sends actionId=0; this editor explicitly advertises IME_ACTION_DONE=6.
                    doneRequests++;
                    connection.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
                    recordCompletion("done-requested");
                    service.message("结束原生输入：第"+doneRequests+"次DONE，尚未提交兑换");
                    done.complete(true);return;
                }
                if(!old.isEmpty()&&!old.equals(value)&&!old.matches("请输入(?:兑换码|礼包码)|输入兑换码"))
                    throw new IllegalStateException("输入框已有未知文本，未覆盖");
                check();Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
                boolean set=node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
                if(!set||!node.refresh()||!value.contentEquals(node.getText()==null?"":node.getText()))
                    throw new IllegalStateException("兑换文本写入未确认，未重复写入");
                done.complete(true);
            }catch(Exception failure){done.completeExceptionally(failure);}
            finally{for(AccessibilityNodeInfo node:nodes)node.recycle();}
        });
        return done.get(2,TimeUnit.SECONDS);
    }
    private void collect(AccessibilityNodeInfo node,List<AccessibilityNodeInfo> out,int[] count,int depth){
        boolean keep=false;
        try{
            if(++count[0]>512||depth>32)throw new IllegalStateException("可访问节点数量异常，停止输入");
            if(node.getPackageName()==null||!GardenRules.GAME_PACKAGE.contentEquals(node.getPackageName()))return;
            if(node.isEditable()&&node.isFocused()&&node.isVisibleToUser()&&node.isEnabled()&&!node.isPassword()){
                out.add(node);keep=true;return;
            }
            for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)collect(child,out,count,depth+1);}
        }finally{if(!keep)node.recycle();}
    }
    private void check(){
        if(!lease.valid(SystemClock.elapsedRealtime())||!service.isRunning()||!service.gameInFront()
                ||service.getSharedPreferences("weekly",0).getBoolean("readOnly",true))throw new IllegalStateException("兑换文本输入租约或前台失效");
    }
    static void revoke(GameAccessibilityService service){RedeemTextInput input=active;if(input!=null&&input.service==service)input.close();}
    @Override public void close(){lease.cancel();if(active==this)active=null;}
}
