package io.github.okww.weekly;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GameAccessibilityService extends AccessibilityService {
    static volatile GameAccessibilityService instance;
    final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final RedeemPasteLease pasteLease=new RedeemPasteLease();
    private volatile Future<?> job;
    private LinearLayout panel;
    private TextView status;
    private WindowManager windows;
    private volatile long generation;
    private volatile long runGeneration;
    private volatile boolean closing;
    private volatile String currentMessage = "尚未启动";
    private volatile boolean combatCapture;
    // Stroke and lease are confined to the main thread; cleanup sends only UP, never a new tap.
    private GestureDescription.StrokeDescription heldStroke;
    private GestureDescription.StrokeDescription pendingHeld;
    private Path heldPath;
    private java.util.concurrent.CompletableFuture<Boolean> releaseResult = java.util.concurrent.CompletableFuture.completedFuture(true);
    private boolean pointerFault;
    private volatile boolean heldActive;
    private final HeldTouchLease heldLease = new HeldTouchLease();
    private final Runnable heldWatchdog = new Runnable() {
        @Override public void run() {
            if (heldStroke == null) return;
            long now=SystemClock.elapsedRealtime();
            if (heldLease.hardExpired(now) || !gameInFront() || CaptureService.instance == null)
                stop("持续触点失去新鲜画面或游戏前台，已释放并停止");
            else if(heldLease.expired(now)) { android.util.Log.i("WWTouch","suspend hold: observation lease stale");releaseHeld(); }
            else main.postDelayed(this, 100);
        }
    };
    private AutomationTask selectedTask = AutomationTask.GARDEN;

    boolean isRunning() { return busy.get(); }
    String statusMessage() { return currentMessage; }

    @Override protected void onServiceConnected() {
        instance = this;
        windows = getSystemService(WindowManager.class);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && busy.get()
                && event.getPackageName() != null && !event.getPackageName().equals(getPackageName())
                && !event.getPackageName().equals(GardenRules.GAME_PACKAGE)
                && !completedWindowEvent(event)
                && !RedeemTextInput.expectedIme(this,event.getPackageName())
                && !expectedPasteOverlay(event.getPackageName().toString(),event.getWindowId())
                && !expectedBarrageOverlay(event.getPackageName().toString(),event.getWindowId())) {
            String reason="已切离游戏，停止："+event.getPackageName()+" / "+event.getClassName()+" / window="+event.getWindowId();
            if("miui.systemui.plugin".contentEquals(event.getPackageName())){
                AccessibilityEvent copy=AccessibilityEvent.obtain(event);long observedGeneration=generation;
                // MIUI's closing island can briefly precede the focus/window update.
                // Capture and every input still independently require current game focus.
                main.postDelayed(()->{try{
                    if(busy.get()&&generation==observedGeneration&&!completedWindowEvent(copy))stop(reason);
                }finally{copy.recycle();}},300);
            }else stop(reason);
        }
    }

    private boolean completedWindowEvent(AccessibilityEvent event) {
        int id=event.getWindowId();
        if(id<0||!gameInFront())return false;
        for(AccessibilityWindowInfo window:getWindows())if(window.getId()==id){
            android.graphics.Rect bounds=new android.graphics.Rect();window.getBoundsInScreen(bounds);
            AccessibilityNodeInfo root=window.getRoot();
            try {
                // MIUI keeps its closed DynamicIslandWindow registered with height zero.
                return !window.isActive()&&!window.isFocused()&&(bounds.isEmpty()||root!=null&&!root.isVisibleToUser());
            } finally {if(root!=null)root.recycle();}
        }
        // Screen-sharing consent can report its closing window after the game already regained focus.
        return true;
    }

    private boolean expectedPasteOverlay(String eventPackage,int eventWindowId){
        long now=SystemClock.elapsedRealtime();
        if(!RedeemPasteLease.PACKAGE.equals(eventPackage)||!pasteLease.recent(now)||!gameInFront())return false;
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(metrics);
        if(metrics.widthPixels!=2400||metrics.heightPixels!=1080)return false;
        for(AccessibilityWindowInfo window:getWindows()){
            if(!RedeemPasteLease.eventWindow(eventWindowId,window.getId()))continue;
            AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
            try{
                android.graphics.Rect bounds=new android.graphics.Rect();window.getBoundsInScreen(bounds);
                if(root.getPackageName()!=null&&pasteLease.expected(SystemClock.elapsedRealtime(),root.getPackageName().toString(),
                        gameInFront(),window.getType()==AccessibilityWindowInfo.TYPE_SYSTEM,window.isFocused(),window.isActive(),
                        bounds.left,bounds.top,bounds.right,bounds.bottom)){
                    android.util.Log.i("WWPaste","Expected non-focusable clipboard card; game remains focused");return true;
                }
            }finally{root.recycle();}
        }
        return false;
    }

    private boolean expectedBarrageOverlay(String eventPackage,int eventWindowId){
        if(!BarrageOverlayRule.PACKAGE.equals(eventPackage)||!gameInFront())return false;
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(metrics);
        for(AccessibilityWindowInfo window:getWindows()){
            if(!RedeemPasteLease.eventWindow(eventWindowId,window.getId()))continue;
            AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
            try{
                android.graphics.Rect bounds=new android.graphics.Rect();window.getBoundsInScreen(bounds);
                boolean ok=root.getPackageName()!=null&&BarrageOverlayRule.expected(root.getPackageName().toString(),gameInFront(),
                        window.getType()==AccessibilityWindowInfo.TYPE_SYSTEM,window.isFocused(),window.isActive(),
                        metrics.widthPixels,metrics.heightPixels,bounds.left,bounds.top,bounds.right,bounds.bottom);
                android.util.Log.i("WWBarrage",(ok?"Expected":"Rejected")+" barrage window type="+window.getType()+" focused="+window.isFocused()
                        +" active="+window.isActive()+" bounds="+bounds.toShortString()+" display="+metrics.widthPixels+"x"+metrics.heightPixels);
                if(ok)return true;
            }finally{root.recycle();}
        }
        android.util.Log.i("WWBarrage","Barrage event window not matched: window="+eventWindowId);
        return false;
    }

    boolean gameInFront() {
        for (AccessibilityWindowInfo window : getWindows()) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused()) {
                AccessibilityNodeInfo root = window.getRoot();
                return root != null && root.getPackageName() != null
                        && GardenRules.GAME_PACKAGE.contentEquals(root.getPackageName());
            }
        }
        return false;
    }

    void selectTask(AutomationTask task) {
        if (selectedTask != task) { stop("已切换任务，请重新确认开始"); selectedTask = task; }
        message("ok-ww · " + task.title + "\n拖动此处移动浮窗");
    }

    void showPanel(AutomationTask task) {
        selectTask(task);
        if (panel != null) { panel.setVisibility(View.VISIBLE); return; }
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackground(ConsoleStyle.surface(this, ConsoleStyle.SIDE, 13, true));
        status = new TextView(this);
        status.setTextColor(ConsoleStyle.TEXT);
        status.setTextSize(12);
        status.setMaxLines(4);
        status.setText(currentMessage);
        panel.addView(status);
        LinearLayout actions = new LinearLayout(this);
        addButton(actions, "识别", () -> submit(false, selectedTask));
        addButton(actions, "开跑", () -> {
            if (getSharedPreferences("weekly", MODE_PRIVATE).getBoolean("readOnly", true)) { submit(false, selectedTask); return; }
            if (busy.get()) { message("已有任务运行，请先停止"); return; }
            AutomationTask requestedTask = selectedTask;
            long requestedGeneration = generation;
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("自动 · " + requestedTask.title)
                    .setMessage(requestedTask!=AutomationTask.ECHO_FARM&&TestEnvironment.enabled(getSharedPreferences("weekly",MODE_PRIVATE))
                            ?requestedTask==AutomationTask.DAILY?TestEnvironment.DAILY_DESCRIPTION:requestedTask==AutomationTask.REDEEM?"测试环境 · 非开发请勿打开：每次重新获取公开码，并使用一条明确标记的合成测试码走完整设置、粘贴、提交、游戏结果和邮件流程。填码会覆盖系统剪贴板为当前合成码，不保存原内容；粘贴后两帧核对完整码才提交。测试码不代表奖励；真实记录保留，待核验仍阻止重试。不切号、不购买、不消耗波片。"
                            :"此任务尚未适配测试环境，自动执行将停止。请关闭测试环境后正常运行；不会清空真实波片或领奖记录。"
                            :requestedTask.confirmation)
                    .setPositiveButton("开始", (d, which) -> {
                        main.postDelayed(() -> {
                            if (requestedGeneration == generation && requestedTask == selectedTask && !closing && gameInFront())
                                submit(true, requestedTask);
                        }, 400);
                    })
                    .setNegativeButton("取消", null).create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            dialog.show();
        });
        addButton(actions, "停", () -> stop("用户停止"));
        panel.addView(actions);
        LinearLayout tools = new LinearLayout(this);
        addButton(tools, "保存现场", this::saveScene);
        panel.addView(tools);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(dp(220), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        windows.addView(panel, params);
        status.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int originalX, originalY;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getRawX(); downY = event.getRawY();
                    originalX = params.x; originalY = params.y;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = Math.max(0, originalX + Math.round(event.getRawX() - downX));
                    params.y = Math.max(0, originalY + Math.round(event.getRawY() - downY));
                    windows.updateViewLayout(panel, params);
                } else if (event.getAction() == MotionEvent.ACTION_UP) view.performClick();
                return true;
            }
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void addButton(LinearLayout row, String label, Runnable action) {
        Button button = new Button(this);
        ConsoleStyle.button(button, label.equals("开跑"));
        button.setText(label);
        button.setTextSize(12);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        if (label.equals("停")) button.setTextColor(ConsoleStyle.RED);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMargins(dp(2), dp(4), dp(2), dp(2));
        row.addView(button, params);
        button.setOnClickListener(view -> action.run());
    }

    void startPreparedDaily() {
        DailyStartGate gate = new DailyStartGate(generation, SystemClock.elapsedRealtime());
        main.postDelayed(new Runnable() {
            @Override public void run() {
                DailyStartGate.Result result = gate.next(SystemClock.elapsedRealtime(), generation,
                        selectedTask == AutomationTask.DAILY, !closing && CaptureService.instance != null
                        && !getSharedPreferences("weekly", MODE_PRIVATE).getBoolean("readOnly", true), busy.get(), gameInFront());
                if (result == DailyStartGate.Result.START) submit(true, AutomationTask.DAILY);
                else if (result == DailyStartGate.Result.WAIT) main.postDelayed(this, 250);
            }
        }, 400);
    }

    private void submit(boolean automate, AutomationTask task) {
        boolean inputAllowed = automate && !getSharedPreferences("weekly", MODE_PRIVATE).getBoolean("readOnly", true);
        if (closing || !busy.compareAndSet(false, true)) { message("已有任务尚未结束，请稍候"); return; }
        queue(() -> {
            try (ScreenReader reader = new ScreenReader()) {
                TestEnvironment.requireSupported(getSharedPreferences("weekly",MODE_PRIVATE),inputAllowed,task);
                if (task == AutomationTask.REDEEM || task == AutomationTask.MULTI_REDEEM) new RedeemEngine(this,reader,task).run(inputAllowed);
                else if (task == AutomationTask.MULTI_DAILY) new MultiAccountEngine(this, reader).run(inputAllowed);
                else if (task == AutomationTask.DAILY) new DailyEngine(this, reader).run(inputAllowed);
                else if (task == AutomationTask.ECHO_FARM) new EchoFarmEngine(this, reader).run(inputAllowed);
                else new GardenEngine(this, reader).run(inputAllowed);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                recordError(error);
                message("停止：" + error.getMessage());
                CaptureService capture = CaptureService.instance;
                if (capture != null && gameInFront()) try {
                    Bitmap frame = capture.freshFrame(0);
                    try { capture.saveEvidence(frame, "failure"); } finally { frame.recycle(); }
                } catch (Exception ignored) { /* Capture may already have ended. */ }
            } finally { main.post(this::releaseHeld); combatCapture = false; main.post(() -> { if (panel != null) panel.setVisibility(View.VISIBLE); }); }
        });
    }

    private void saveScene() {
        if (closing || !busy.compareAndSet(false, true)) { message("先停止任务再保存现场"); return; }
        queue(() -> {
            try {
                Bitmap frame = capture();
                try { CaptureService.instance.saveEvidence(frame, "manual"); message("已保存应用内 files/evidence/manual.png"); }
                finally { frame.recycle(); }
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            catch (Exception error) { recordError(error); message(error.getMessage()); }
        });
    }

    private void recordError(Exception error) {
        android.util.Log.e("WWGarden", "Task stopped", error);
        try (FileOutputStream output = new FileOutputStream(new File(getFilesDir(), "last-error.txt"))) {
            output.write((error.getClass().getSimpleName() + ": " + error.getMessage()).getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException ignored) { /* Android log remains available if private storage is full. */ }
    }

    private void queue(Runnable operation) {
        runGeneration = generation;
        FutureTask<Void> task = new FutureTask<Void>(operation, null) {
            @Override public void run() {
                try { super.run(); } finally { busy.set(false); }
            }
        };
        job = task;
        worker.execute(task);
    }

    void message(String text) {
        currentMessage = text;
        main.post(() -> { if (status != null) status.setText(text); });
    }

    void stop(String reason) {
        android.util.Log.i("WWTouch","stop: "+reason);
        generation++;
        pasteLease.stop();
        RedeemTextInput.revoke(this);
        combatCapture = false;
        main.post(this::releaseHeld);
        Future<?> running = job;
        if (running != null) running.cancel(true);
        main.post(() -> { if (panel != null) panel.setVisibility(View.VISIBLE); message(reason); });
    }

    Bitmap capture() throws Exception {
        return capture(null);
    }
    Bitmap capture(ScreenReader.DuringRead duringCombatCapture) throws Exception {
        if (!gameInFront()) throw new IllegalStateException("请保持鸣潮游戏在前台");
        CaptureService capture = CaptureService.instance;
        if (capture == null) throw new IllegalStateException("请先授权屏幕采集");
        if (combatCapture) {
            long after = System.nanoTime(), deadline = SystemClock.elapsedRealtime() + 1500;
            while (true) {
                Thread.sleep(40);
                if (!gameInFront() || CaptureService.instance != capture) throw new IllegalStateException("战斗采集已失去前台");
                if(duringCombatCapture!=null)duringCombatCapture.run();
                try { return capture.freshFrame(after); }
                catch (IllegalStateException notReady) { if (SystemClock.elapsedRealtime() >= deadline) throw notReady; }
            }
        }
        CountDownLatch hidden = new CountDownLatch(1);
        main.post(() -> {
            if (panel != null) panel.setVisibility(View.INVISIBLE);
            hidden.countDown();
        });
        try {
            if (!hidden.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("浮窗隐藏超时");
            Thread.sleep(350);
            // Reject frames composed before the visibility change settled, not just before setVisibility.
            long hiddenAt = System.nanoTime();
            long deadline = SystemClock.elapsedRealtime() + 15000;
            while (true) {
                Thread.sleep(150);
                if (!gameInFront()) throw new IllegalStateException("采集期间已切离游戏");
                try { return capture.freshFrame(hiddenAt); }
                catch (IllegalStateException notReady) {
                    if (SystemClock.elapsedRealtime() >= deadline || CaptureService.instance != capture) throw notReady;
                }
            }
        } finally { main.post(() -> { if (panel != null) panel.setVisibility(View.VISIBLE); }); }
    }

    void goBack() throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        long requestedGeneration = runGeneration;
        long requested = SystemClock.elapsedRealtime();
        CountDownLatch complete = new CountDownLatch(1);
        boolean[] success = {false};
        main.post(() -> {
            if (requestedGeneration == generation && SystemClock.elapsedRealtime() - requested <= 500 && gameInFront())
                success[0] = performGlobalAction(GLOBAL_ACTION_BACK);
            complete.countDown();
        });
        if (!complete.await(1, TimeUnit.SECONDS) || !success[0]) throw new IllegalStateException("返回操作被取消");
    }

    void tap(float x, float y, int width, int height) throws Exception {
        performGesture(CombatGesture.tapPixels(x, y, width, height));
    }

    void pasteRedeemCode(String code,float x,float y,int width,int height,long captured)throws Exception{
        String value=RedeemCodes.normalize(code);
        performGesture(CombatGesture.tapPixels(x,y,width,height),()->{
            if(!RedeemRules.pasteDispatchAllowed(captured,SystemClock.elapsedRealtime(),keyboardVisible()))
                throw new IllegalStateException("粘贴画面已过期或键盘仍显示，未写剪贴板");
            // Write only after the same cancellation/front/read-only gates as the immediately following tap.
            pasteLease.start(SystemClock.elapsedRealtime());
            getSystemService(android.content.ClipboardManager.class).setPrimaryClip(android.content.ClipData.newPlainText("ok-ww redeem code",value));
            if(!RedeemRules.pasteDispatchAllowed(captured,SystemClock.elapsedRealtime(),keyboardVisible()))
                throw new IllegalStateException("剪贴板已写入，但粘贴帧过期或键盘出现，未点图标");
        });
    }

    boolean keyboardVisible(){
        for(AccessibilityWindowInfo window:getWindows())if(window.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD)return true;
        return false;
    }

    void combatCapture(boolean enabled) throws Exception {
        combatCapture = enabled;
        CountDownLatch updated = new CountDownLatch(1);
        main.post(() -> { if (panel != null) panel.setVisibility(enabled ? View.INVISIBLE : View.VISIBLE); updated.countDown(); });
        if (!updated.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("战斗浮窗切换超时");
        if (enabled) Thread.sleep(350);
    }

    boolean renewHeldObservation(long observed) throws Exception {
        java.util.concurrent.CompletableFuture<Boolean> state=new java.util.concurrent.CompletableFuture<>();
        main.post(() -> {
            if(heldLease.hardExpired(SystemClock.elapsedRealtime())) { stop("持续触点超过时限，停止并释放");state.complete(false);return; }
            try { heldLease.observed(observed); }
            catch (IllegalStateException expired) { releaseHeld(); }
            if(heldActive)state.complete(true);
            else releaseHeld().whenComplete((released,error)->{
                if(error!=null||!released)state.completeExceptionally(new IllegalStateException("持续触点释放未确认"));
                else state.complete(false);
            });
        });
        return state.get(1,TimeUnit.SECONDS);
    }
    boolean heldActive() { return heldActive; }

    void holdAttack(boolean down, CombatTouchLayout layout) throws Exception {
        holdControl(CombatTouchLayout.Control.ATTACK,down,layout);
    }
    void holdControl(CombatTouchLayout.Control control, boolean down, CombatTouchLayout layout) throws Exception {
        // Release must remain available even when the caller's control/layout is no longer valid.
        CombatGesture.Contact point = down ? layout.heldPoint(control) : null;
        if (!down) {
            java.util.concurrent.CompletableFuture<Boolean> done = new java.util.concurrent.CompletableFuture<>();
            main.post(() -> releaseHeld().whenComplete((released, error) -> done.complete(error == null && released)));
            if (!done.get(1, TimeUnit.SECONDS)) throw new IllegalStateException("持续触点释放未确认，禁止后续输入");
            return;
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        CountDownLatch done = new CountDownLatch(1); boolean[] success = {false};
        long requestedGeneration = runGeneration, requested = SystemClock.elapsedRealtime();
        main.post(() -> {
            if (heldStroke != null || !inputAllowed(layout.width, layout.height, requestedGeneration, requested)) { done.countDown(); return; }
            CombatGesture.Contact p = point;
            heldPath = new Path(); heldPath.moveTo(p.x * layout.width, p.y * layout.height);
            try {
            heldStroke = new GestureDescription.StrokeDescription(heldPath, 0, 80, true);
            heldLease.start(SystemClock.elapsedRealtime());
            android.util.Log.i("WWTouch","hold down requested");
            boolean queued = dispatchGesture(new GestureDescription.Builder().addStroke(heldStroke).build(), new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription description) { android.util.Log.i("WWTouch","hold down completed");success[0] = requestedGeneration == generation; done.countDown(); }
                @Override public void onCancelled(GestureDescription description) { android.util.Log.e("WWTouch","hold down cancelled");done.countDown(); }
            }, main);
            if (!queued) { releaseHeld(); done.countDown(); }
            else { heldActive=true;main.postDelayed(heldWatchdog, 100); }
            } catch (RuntimeException error) {
                android.util.Log.e("WWGarden", "Hold dispatch failed", error); releaseHeld(); done.countDown();
            }
        });
        try {
            if (!done.await(1, TimeUnit.SECONDS) || !success[0]) throw new IllegalStateException("持续触点启动失败");
        } catch (Exception error) { main.post(this::releaseHeld); throw error; }
    }

    private java.util.concurrent.CompletableFuture<Boolean> releaseHeld() {
        heldActive=false;
        if (!releaseResult.isDone() || pointerFault) return releaseResult;
        main.removeCallbacks(heldWatchdog);
        GestureDescription.StrokeDescription stroke = pendingHeld != null ? pendingHeld : heldStroke;
        if (stroke == null) return releaseResult;
        releaseResult = new java.util.concurrent.CompletableFuture<>();
        android.util.Log.i("WWTouch","hold up requested");
        try {
            boolean queued = dispatchGesture(new GestureDescription.Builder().addStroke(stroke.continueStroke(heldPath, 0, 80, false)).build(), new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription description) { finishRelease(true); }
                @Override public void onCancelled(GestureDescription description) { finishRelease(false); }
            }, main);
            if (!queued) finishRelease(false);
        } catch (RuntimeException error) { android.util.Log.e("WWGarden", "Pointer release failed", error); finishRelease(false); }
        return releaseResult;
    }

    private void finishRelease(boolean success) {
        android.util.Log.i("WWTouch","hold up completed="+success);
        heldStroke = pendingHeld = null; heldLease.stop(); pointerFault = !success; releaseResult.complete(success);
        if (!success) stop("持续触点释放未确认；请重新连接无障碍服务，后续输入已禁用");
    }

    private boolean inputAllowed(int width, int height, long requestedGeneration, long requested) {
        if (closing || pointerFault || !releaseResult.isDone() || CaptureService.instance == null || !gameInFront() || requestedGeneration != generation
                || SystemClock.elapsedRealtime() - requested > 500
                || getSharedPreferences("weekly", MODE_PRIVATE).getBoolean("readOnly", true)) return false;
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        windows.getDefaultDisplay().getRealMetrics(metrics);
        return metrics.widthPixels == width && metrics.heightPixels == height;
    }

    // Finite non-combat gestures remain bounded to 2s; continuous combat holds have a separate release lease.
    void performGesture(CombatGesture command) throws Exception {
        performGesture(command,null);
    }

    private void performGesture(CombatGesture command,Runnable beforeDispatch) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (command == null) throw new IllegalArgumentException("无效触控指令");
        int width = command.width, height = command.height;
        long requestedGeneration = runGeneration;
        CountDownLatch complete = new CountDownLatch(1);
        boolean[] success = {false};
        long requested = SystemClock.elapsedRealtime();
        main.post(() -> {
            if (panel != null) panel.setVisibility(View.INVISIBLE);
            main.postDelayed(() -> {
                if (!inputAllowed(width, height, requestedGeneration, requested)) {
                    complete.countDown(); return;
                }
                android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                windows.getDefaultDisplay().getRealMetrics(metrics);
                if (metrics.widthPixels != width || metrics.heightPixels != height) { complete.countDown(); return; }
                try {
                    GestureDescription.Builder builder = new GestureDescription.Builder();
                    GestureDescription.StrokeDescription nextHeld = null;
                    if (heldStroke != null) {
                        nextHeld = heldStroke.continueStroke(heldPath, 0, command.gestureDurationMs(true), true);
                        builder.addStroke(nextHeld);
                    }
                    for (int i=0;i<command.contacts.size();i++) {
                        CombatGesture.Contact contact=command.contacts.get(i);
                        Path path = new Path(); path.moveTo(contact.x * width, contact.y * height);
                        if(contact.x!=contact.endX || contact.y!=contact.endY)path.lineTo(contact.endX * width,contact.endY * height);
                        builder.addStroke(new GestureDescription.StrokeDescription(path, command.pointerStartMs(i,nextHeld!=null), contact.durationMs));
                    }
                    GestureDescription.StrokeDescription continued=nextHeld;
                    if(beforeDispatch!=null){
                        beforeDispatch.run();
                        if(!inputAllowed(width,height,requestedGeneration,requested)){complete.countDown();return;}
                    }
                    boolean queued = dispatchGesture(builder.build(), new GestureResultCallback() {
                        @Override public void onCompleted(GestureDescription description) {
                            if(continued!=null){heldStroke=continued;pendingHeld=null;}
                            success[0] = true; complete.countDown();
                        }
                        @Override public void onCancelled(GestureDescription description) {
                            pendingHeld=null;android.util.Log.e("WWTouch","gesture cancelled; continuing="+(continued!=null));complete.countDown();
                        }
                    }, main);
                    if (queued && continued != null) pendingHeld=continued;
                    if (!queued) complete.countDown();
                } catch (RuntimeException error) {
                    android.util.Log.e("WWGarden", "Gesture dispatch failed", error); complete.countDown();
                }
            }, combatCapture ? 0 : 80);
        });
        try {
            if (!complete.await(command.timeoutMs(), TimeUnit.MILLISECONDS) || !success[0] || requestedGeneration != generation)
                throw new IllegalStateException("触控未完成或游戏失去焦点，已停止");
        } finally { main.post(() -> { if (panel != null && !combatCapture) panel.setVisibility(View.VISIBLE); }); }
    }

    @Override public void onInterrupt() { stop("无障碍服务中断"); }
    @Override public void onDestroy() {
        closing = true;
        stop("服务关闭");
        instance = null;
        worker.shutdownNow();
        if (panel != null) { windows.removeView(panel); panel = null; }
        super.onDestroy();
    }
}
