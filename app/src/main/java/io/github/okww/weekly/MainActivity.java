package io.github.okww.weekly;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.util.Date;
import static io.github.okww.weekly.ConsoleStyle.*;

/** Native phone console; changing pages never starts game input. */
public final class MainActivity extends Activity {
    private final Handler refresh = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { refreshStatus(); refresh.postDelayed(this, 1000); }
    };
    private LinearLayout content;
    private ScrollView scroller;
    private final LinearLayout[] navigation = new LinearLayout[3];
    private final TextView[] navigationLabels = new TextView[3];
    private final ImageView[] navigationIcons = new ImageView[3];
    private TextView heading, subtitle, connectionBadge, accessibilityState, captureState;
    private TextView recentTitle, recentDescription, recentTime, diagnostic;
    private Button enterButton, captureButton;
    private int page;
    private String version, lastDiagnostic = "";
    private PreparationFlow preparation;
    private long preparationDeadline;
    private boolean resumed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preparation = new PreparationFlow(state == null ? null : state.getString("preparation"));
        if (state != null) {
            preparation.pendingRequest = state.getInt("permissionRequest", 0);
            preparation.nextRequest = state.getInt("nextPermissionRequest", 1000);
            try { preparation.pendingAction = PreparationFlow.Action.valueOf(state.getString("permissionAction", "NONE")); }
            catch (IllegalArgumentException ignored) { preparation.cancel(); }
        }
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException error) { throw new IllegalStateException(error); }
        LinearLayout root = row();
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return WindowInsets.CONSUMED;
            }
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root);
        root.requestApplyInsets();
        root.addView(sidebar(), new LinearLayout.LayoutParams(d(148), -1));
        LinearLayout main = column();
        main.setPadding(d(22), d(12), d(22), d(10));
        root.addView(main, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column();
        heading = text("", 23, TEXT, true);
        subtitle = text("", 11, MUTED, false);
        titles.addView(heading); titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        connectionBadge = text("", 11, ACCENT, false);
        connectionBadge.setPadding(d(12), d(7), d(12), d(7));
        connectionBadge.setBackground(surface(this, ACCENT_BG, 16, false));
        header.addView(connectionBadge);
        Button stop = button("停止 / 结束采集", false, this::stopCapture);
        stop.setTextColor(RED);
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(-2, d(46));
        stopParams.leftMargin = d(12);
        header.addView(stop, stopParams);
        main.addView(header);
        scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        content = column(); content.setPadding(0, d(16), 0, d(12));
        scroller.addView(content);
        main.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        selectPage(state == null ? 0 : state.getInt("page", 0));
    }

    private LinearLayout sidebar() {
        LinearLayout side = column();
        side.setPadding(d(12), d(16), d(12), d(12)); side.setBackgroundColor(SIDE);
        LinearLayout brand = row(); brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.okww_logo); logo.setContentDescription("ok-ww");
        brand.addView(logo, new LinearLayout.LayoutParams(d(39), d(43)));
        LinearLayout wordmark = column(); wordmark.setPadding(d(8), 0, 0, 0);
        wordmark.addView(text("ok-ww", 20, TEXT, true)); wordmark.addView(text("ANDROID", 9, MUTED, false));
        brand.addView(wordmark); side.addView(brand); space(side, 23);
        String[] labels = {"任务", "运行记录", "设置"};
        int[] icons = {R.drawable.ic_tasks, R.drawable.ic_records, R.drawable.ic_settings};
        for (int index = 0; index < labels.length; index++) {
            final int target = index;
            LinearLayout item = row(); item.setTag("nav-" + index);
            item.setGravity(Gravity.CENTER_VERTICAL); item.setPadding(d(13), 0, d(9), 0);
            item.setFocusable(true); item.setContentDescription(labels[index]);
            ImageView icon = new ImageView(this); icon.setImageResource(icons[index]);
            item.addView(icon, new LinearLayout.LayoutParams(d(20), d(20)));
            TextView label = text(labels[index], 13, TEXT, false); label.setPadding(d(12), 0, 0, 0);
            item.addView(label); item.setOnClickListener(view -> { preparation.cancel(); selectPage(target); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, d(48)); params.bottomMargin = d(6);
            side.addView(item, params);
            navigation[index] = item; navigationLabels[index] = label; navigationIcons[index] = icon;
        }
        side.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        side.addView(button("使用指南", false, this::showHelp), new LinearLayout.LayoutParams(-1, d(46)));
        space(side, 8); side.addView(text("v" + version + "  ·  手机本地运行", 9, MUTED, false));
        return side;
    }

    void selectPage(int target) {
        page = target >= 0 && target < 3 ? target : 0;
        for (int i = 0; i < 3; i++) {
            navigation[i].setBackground(surface(this, i == page ? ACCENT_BG : SIDE, 9, false));
            navigation[i].setSelected(i == page);
            navigationLabels[i].setTextColor(i == page ? ACCENT : MUTED);
            navigationIcons[i].setImageTintList(ColorStateList.valueOf(i == page ? ACCENT : MUTED));
        }
        content.removeAllViews();
        accessibilityState = captureState = recentTitle = recentDescription = recentTime = diagnostic = null;
        enterButton = captureButton = null;
        content.setTag("page-" + page);
        heading.setText(new String[]{"任务中心", "运行记录", "设置"}[page]);
        subtitle.setText(new String[]{"周常与每日的手机控制台", "查看真实识别结果与停止原因", "权限、运行模式与本地诊断"}[page]);
        if (page == 0) tasksPage(); else if (page == 1) recordsPage(); else settingsPage();
        scroller.post(() -> scroller.scrollTo(0, 0));
        refreshStatus();
    }

    private void tasksPage() {
        taskPicker();
        AutomationTask selected = selectedTask();
        boolean redeem=selected==AutomationTask.REDEEM||selected==AutomationTask.MULTI_REDEEM;
        LinearLayout columns = row(); columns.setBaselineAligned(false);
        LinearLayout task = card();
        task.addView(text(redeem?"礼包码迁移验证  /  REDEEM":selected == AutomationTask.GARDEN ? "周常任务  /  GARDEN" : "每日迁移验证  /  DAILY", 10, ACCENT, true)); space(task, 9);
        task.addView(text(redeem?selected.title:selected == AutomationTask.MULTI_DAILY ? "多账号 · 无音区每日" : selected == AutomationTask.GARDEN ? "幻梦游园 · 狂想" : "每日全流程 · 无音区与免费奖励", 22, TEXT, true));
        TextView description = text(TestEnvironment.enabled(preferences())?(selected==AutomationTask.REDEEM?"测试环境 · 每次从头获取公开码 → 新合成测试码输入/提交 → 核验结果 → 检查邮件。\n测试记录与真实记录隔离；无效测试码不代表奖励。设置中可关闭测试环境，恢复正常流程。":"测试环境已开启，但此任务尚未适配测试重跑，自动执行会停止。\n请先在设置关闭测试环境；真实波片、领奖及待核验记录始终保留。")
                :redeem?"正常环境 · 仅兑换已主动启用的真实礼包码，已处理码按记录跳过，再检查邮件。\n不生成测试码，不重复提交待核验记录。":selected == AutomationTask.GARDEN
                ? "自动选卡、处理奇遇、推进阶段与结算重试。\n核验周游历值 6000 后结束。"
                : selected == AutomationTask.MULTI_DAILY ? "当前账号优先，其余按计划顺序；登录后复核 UID，每号重新识别队伍。\n按游戏日保存完成/待完成/失败记录；手机切号流程尚待真机验证。"
                : "启动游戏 → 账号与队伍 → 无音区 → 活跃奖励 → 邮件 → 先约电台。\n九人内置轴已接执行分派；未标定的专属读数会在开战前列出，不代表九人都可实战。仅用普通波片最多240，不补差不兑换，双倍优先；未核验不记完成。", 12, MUTED, false);
        description.setLineSpacing(d(3), 1); space(task, 7); task.addView(description);
        if(selected==AutomationTask.MULTI_DAILY||selected==AutomationTask.MULTI_REDEEM)task.addView(button("编辑账号计划（本机私有）",false,()->MultiAccountEditor.show(this)));
        if(redeem)task.addView(button("兑换码中心（获取/粘贴/启用/记录）",false,()->RedeemEditor.show(this)));
        space(task, 15); divider(task); space(task, 5); task.addView(modeRow());
        enterButton = button("一键准备并进入游戏", true, this::prepareGame); enterButton.setTag("launch-game");
        task.addView(enterButton, new LinearLayout.LayoutParams(-1, d(47))); space(task, 5);
        task.addView(text(selected == AutomationTask.DAILY ? "自动模式确认一次后启动全流程；系统授权仍需确认。" : "自动检查授权；进入游戏后在浮窗确认开始。", 10, MUTED, false));
        columns.addView(task, new LinearLayout.LayoutParams(0, -2, 2));
        LinearLayout permissions = card(); permissions.addView(text("运行准备", 14, TEXT, true)); space(permissions, 10);
        accessibilityState = text("", 12, MUTED, false); captureState = text("", 12, MUTED, false);
        permissions.addView(accessibilityState); space(permissions, 10); permissions.addView(captureState); space(permissions, 15);
        permissions.addView(button("管理权限", false, () -> { preparation.cancel(); selectPage(2); }), new LinearLayout.LayoutParams(-1, d(46)));
        space(permissions, 12); permissions.addView(text(redeem?"新抓取兑换码默认忽略\n主动启用后才执行兑换\n不购买、不删除、不消耗波片。":selected == AutomationTask.GARDEN
                ? "前台运行 · 本地识别\n不切账号，不消耗波片。\n可选商店购买暂时跳过。"
                : selected == AutomationTask.MULTI_DAILY ? "每号最多240普通波片\n不补充、不兑换、不购买\n未知账号/页面停止，保留断点。"
                : "每账号/游戏日累计最多240普通波片\n不补充、不兑换备用、不购买\n保留消费账本，未核验结果不记完成。", 10, MUTED, false));
        LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(0, -2, 1); permissionParams.leftMargin = d(14);
        columns.addView(permissions, permissionParams); content.addView(columns); space(content, 14); recentCard();
        space(content, 12); content.addView(text("每日仍在真机适配，不是可完整刷本的发布版本。切换任务不会自动开始。", 10, MUTED, false));
    }

    private AutomationTask selectedTask() { return AutomationTask.restore(preferences().getString("task", "GARDEN")); }

    private void taskPicker() {
        LinearLayout choices = row();
        for (AutomationTask task : AutomationTask.values()) {
            Button choice = button(task.title, selectedTask() == task, () -> {
                if (selectedTask() == task) return;
                preparation.cancel();
                GameAccessibilityService service = GameAccessibilityService.instance;
                if (service != null) service.selectTask(task);
                preferences().edit().putString("task", task.name()).apply();
                selectPage(page);
            });
            choice.setTag("task-" + task.name());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, d(48), 1); params.rightMargin = d(8);
            choices.addView(choice, params);
        }
        content.addView(choices); space(content, 12);
    }

    private void recentCard() {
        LinearLayout recent = card();
        recentTitle = text("", 13, TEXT, true); recentDescription = text("", 12, MUTED, false); recentTime = text("", 10, MUTED, false);
        recent.addView(recentTitle); space(recent, 6); recent.addView(recentDescription); space(recent, 6); recent.addView(recentTime);
        content.addView(recent);
    }

    private void recordsPage() {
        taskPicker();
        recentCard(); space(content, 14);
        LinearLayout tools = row(); tools.addView(button("刷新记录", false, this::refreshStatus));
        Button copy = button("复制诊断", false, () -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("ok-ww 本地诊断", lastDiagnostic));
            toast("已复制诊断（含识别文本，请留意账号信息）");
        });
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-2, -2); copyParams.leftMargin = d(10);
        tools.addView(copy, copyParams); content.addView(tools); space(content, 12);
        LinearLayout log = card(); log.addView(text("最近一次识别 / 停止记录", 13, TEXT, true)); space(log, 8);
        diagnostic = text("", 11, MUTED, false); diagnostic.setTypeface(Typeface.MONOSPACE);
        diagnostic.setTextIsSelectable(true); diagnostic.setLineSpacing(d(4), 1); log.addView(diagnostic); content.addView(log);
        space(content, 10); content.addView(text("记录只留在本机；展示的是最后一次快照，不是完整历史。", 10, MUTED, false));
    }

    private void settingsPage() {
        LinearLayout permissions = card(); permissions.addView(text("运行权限", 15, TEXT, true)); space(permissions, 10);
        accessibilityState = text("", 12, MUTED, false);
        permissions.addView(settingRow("无障碍服务", "用于浮窗与游戏内触控", accessibilityState,
                button("打开设置", false, () -> { preparation.cancel(); startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); })));
        space(permissions, 8); divider(permissions); space(permissions, 8); captureState = text("", 12, MUTED, false);
        captureButton = button("授权采集", false, this::authorizeCapture); captureButton.setTag("authorize-capture");
        permissions.addView(settingRow("整个屏幕采集", "只在授权后读取游戏画面", captureState, captureButton));
        content.addView(permissions); space(content, 14);
        LinearLayout options = card(); options.addView(text("运行模式", 15, TEXT, true)); options.addView(modeRow());
        options.addView(text("关闭只读后仍需在游戏浮窗确认开始；切离游戏会停止后续输入。", 11, MUTED, false));
        content.addView(options); space(content, 14);
        LinearLayout development=card();development.addView(text("开发设置",15,TEXT,true));space(development,8);
        LinearLayout testRow=row();testRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout testLabels=column();testLabels.addView(text("测试环境",13,TEXT,true));
        testLabels.addView(text("非开发请勿打开 · 默认关闭",11,ConsoleStyle.RED,true));
        testRow.addView(testLabels,new LinearLayout.LayoutParams(0,-2,1));
        Switch testToggle=new Switch(this);testToggle.setTag("test-environment-switch");
        testToggle.setContentDescription("测试环境，非开发请勿打开，默认关闭");testToggle.setChecked(TestEnvironment.enabled(preferences()));
        testToggle.setMinHeight(d(48));testToggle.setMinimumWidth(d(64));
        testToggle.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{ACCENT,MUTED}));
        testToggle.setOnCheckedChangeListener((button,checked)->{
            preparation.cancel();
            if(GameAccessibilityService.instance!=null)GameAccessibilityService.instance.stop("测试环境已切换，请重新确认开始");
            preferences().edit().putBoolean(TestEnvironment.KEY,checked).apply();refreshStatus();
            toast(checked?"测试环境已开启：单号兑换每次从头测试；其它任务暂不自动执行":"测试环境已关闭：恢复正常记录与跳过逻辑");
        });
        testRow.addView(testToggle,new LinearLayout.LayoutParams(d(64),d(48)));development.addView(testRow);space(development,6);
        development.addView(text("当前适配：单号兑换。每次重新抓取公开码，以新合成测试码从头走输入、提交、结果和邮件；不代表真实奖励。\n未适配任务停止自动执行。不会清空真实兑换、波片消耗、领奖或待核验记录；切换环境会停止当前任务。",11,MUTED,false));
        space(development,8);
        Button pasteProbe=button("复制粘贴测试码（不兑换）",false,()->{
            GameAccessibilityService service=GameAccessibilityService.instance;
            if(!TestEnvironment.pasteProbeAllowed(preferences(),service!=null&&service.isRunning())){
                toast("仅测试环境开启且任务停止后可用");return;
            }
            new AlertDialog.Builder(this).setTitle("粘贴入口测试 · 不提交兑换")
                    .setMessage("将当前系统剪贴板替换为固定合成文本 WWTESTPASTE，不读取或保存原内容，不代表有效兑换码。此按钮只复制，不点击游戏、不运行兑换；返回游戏后可单独验证输入框右侧粘贴图标。")
                    .setNegativeButton("取消",null).setPositiveButton("复制测试文本",(dialog,which)->{
                        GameAccessibilityService current=GameAccessibilityService.instance;
                        if(!TestEnvironment.pasteProbeAllowed(preferences(),current!=null&&current.isRunning())){
                            toast("状态已变化，未复制");return;
                        }
                        ClipboardManager clipboard=getSystemService(ClipboardManager.class);
                        clipboard.setPrimaryClip(ClipData.newPlainText("ok-ww paste probe","WWTESTPASTE"));
                        ClipData actual=clipboard.getPrimaryClip();
                        boolean verified=actual!=null&&actual.getItemCount()==1&&"WWTESTPASTE".contentEquals(actual.getItemAt(0).getText());
                        toast(verified?"已核对测试文本；尚未粘贴或兑换":"剪贴板写入未核验，请勿继续测试");
                        android.util.Log.i("WWPasteProbe",verified?"Fixed synthetic clipboard verified; no game input":"Clipboard not verified; do not paste");
                    }).show();
        });
        pasteProbe.setTag("copy-paste-probe");development.addView(pasteProbe);
        content.addView(development);space(content,14);
        LinearLayout about = card(); about.addView(text("ok-ww · Android  " + version, 14, TEXT, true)); space(about, 6);
        about.addView(text(android.os.Build.MODEL + "  /  Android " + android.os.Build.VERSION.RELEASE
                + "\n手机独立识别与操作，不依赖电脑或无线调试。", 11, MUTED, false)); content.addView(about);
    }

    private LinearLayout settingRow(String name, String detail, TextView status, Button action) {
        LinearLayout line = row(); line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = column(); labels.addView(text(name, 13, TEXT, true)); labels.addView(text(detail, 10, MUTED, false));
        space(labels, 4); labels.addView(status); line.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(action, new LinearLayout.LayoutParams(d(112), d(46))); return line;
    }

    private LinearLayout modeRow() {
        LinearLayout line = row(); line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = column(); labels.addView(text("只读模式", 12, TEXT, true));
        labels.addView(text("仅识别画面，不发送游戏触控", 10, MUTED, false)); line.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this); toggle.setTag("mode-switch");
        toggle.setContentDescription("只读模式：仅识别，不发送游戏触控");
        toggle.setChecked(preferences().getBoolean("readOnly", true)); toggle.setMinHeight(d(48)); toggle.setMinimumWidth(d(64));
        toggle.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{ACCENT, MUTED}));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            preparation.cancel();
            preferences().edit().putBoolean("readOnly", checked).apply();
            if (checked && GameAccessibilityService.instance != null) GameAccessibilityService.instance.stop("已切换只读，停止自动输入");
            refreshStatus();
        });
        line.addView(toggle, new LinearLayout.LayoutParams(d(64), d(48))); return line;
    }

    private void refreshStatus() {
        if (content == null) return;
        GameAccessibilityService service = GameAccessibilityService.instance;
        boolean accessible = service != null, capture = CaptureService.instance != null, running = accessible && service.isRunning();
        connectionBadge.setText(running ? "●  运行中" : accessible && capture ? "●  已就绪" : "○  待授权");
        if (accessibilityState != null) {
            accessibilityState.setText(accessible ? "●  无障碍已连接" : "○  无障碍未连接"); accessibilityState.setTextColor(accessible ? ACCENT : MUTED);
        }
        if (captureState != null) {
            captureState.setText(capture ? "●  屏幕采集已授权" : "○  屏幕采集未授权"); captureState.setTextColor(capture ? ACCENT : MUTED);
        }
        if (captureButton != null) { captureButton.setEnabled(!capture); captureButton.setAlpha(capture ? .5f : 1); }
        if (enterButton != null) enterButton.setText(preparation.active() ? "准备中 · 点击取消"
                : selectedTask() == AutomationTask.DAILY && !preferences().getBoolean("readOnly", true) ? "启动每日全流程" : accessible && capture
                ? preferences().getBoolean("readOnly", true) ? "进入游戏 · 只读检查" : "进入游戏 · 自动模式" : "一键准备并进入游戏");
        AutomationTask selected = selectedTask();
        String raw = read("evidence/" + selected.stateFile, 65536), error = read("last-error.txt", 8192);
        JSONObject state;
        try { state = raw.isEmpty() ? new JSONObject() : new JSONObject(raw); }
        catch (Exception invalid) { state = new JSONObject(); raw = "记录格式异常，请重新运行识别。"; }
        if (recentTitle != null) {
            recentTitle.setText(running ? "当前任务 · 正在运行" : "上次记录 · " + (state.length() == 0 ? "暂无记录"
                    : "completed".equals(state.optString("scene")) ? "识别到满分（快照）" : "最近识别"));
            recentDescription.setText(running ? service.statusMessage() : "completed".equals(state.optString("scene"))
                    ? "最近画面识别到6000游历值；快照本身不代表任务最终状态。"
                    : state.optString("description", "准备好权限后，进入游戏进行一次只读检查。"));
            recentTime.setText(state.has("timestamp") ? "记录时间  " + time(state.optLong("timestamp"))
                    + "    采集与识别 " + state.optLong("recognition_ms") + " ms" : "尚未生成识别快照");
        }
        JSONArray team = state.optJSONArray("team");
        if (recentDescription != null && !running && team != null) {
            recentDescription.append("\n本次识别队伍：");
            for (int i = 0; i < team.length(); i++) recentDescription.append((i == 0 ? "" : " → ") + team.optString(i));
        }
        if (recentDescription != null && !running && state.has("last_observed_active_character"))
            recentDescription.append("\n最近观察在场：" + state.optString("last_observed_active_character") + "（不是当前实时状态）");
        if (recentDescription != null && state.has("rotation_control_flow_missing")) {
            JSONArray missing = state.optJSONArray("rotation_control_flow_missing");
            recentDescription.append("\n角色轴尚未翻译：" + (missing == null || missing.length() == 0 ? "无（翻译完成不等于手机实战适配）" : missing.toString()));
        }
        StringBuilder lines = new StringBuilder("场景：").append(state.optString("scene", "—")).append("\n模式：")
                .append(state.optString("mode", "—")).append("\n计划动作：").append(state.optString("action", "—"));
        if (state.has("image_width")) lines.append("\n画面尺寸：").append(state.optInt("image_width")).append('×').append(state.optInt("image_height"));
        lines.append("\n识别说明：").append(state.optString("description", "—"));
        JSONArray accounts=state.optJSONArray("accounts");
        if(accounts!=null){
            lines.append("\n多账号全部完成：").append(state.optBoolean("multi_complete")?"已核验":"未核验/仍有待完成");
            for(int i=0;i<accounts.length();i++){
                JSONObject a=accounts.optJSONObject(i);if(a==null)continue;
                lines.append("\n").append(a.optString("account")).append(" · ").append(a.optBoolean("enabled")?a.optString("status"):"停用")
                        .append(" · 无音区#").append(a.optInt("tacet_index"));
            }
        }
        if (state.has("daily_followup_stage")) {
            lines.append("\n每日后续阶段：").append(state.optString("daily_followup_stage"));
            lines.append("\n免费奖励核验：").append(state.optJSONObject("daily_reward_checks"));
            lines.append("\n跳过刷本原因：").append(state.optString("daily_followup_reason", "—"));
            lines.append("\n本轮波片消耗：").append(state.optInt("stamina_spent_this_run"))
                    .append('/').append(state.has("stamina_planned_budget") ? state.optInt("stamina_planned_budget") : "待核验");
            lines.append("\n本游戏日已核销：").append(state.optInt("stamina_spent_game_day"))
                    .append('/').append(state.optInt("stamina_limit"));
            if(state.has("stamina_exchange_needed")) lines.append("\n计划备用补差（非已兑换）：").append(state.optInt("stamina_exchange_needed"));
            lines.append(state.optBoolean("daily_complete") ? "\n每日完成：该记录中预算与免费奖励均已核验"
                    : "\n每日完成：未确认；后续检查结束不等于每日全流程完成");
        }
        if (state.has("rotation_control_flow_missing")) {
            lines.append("\n已译主要控制流：").append(state.optJSONArray("rotation_control_flow_translated"));
            lines.append("\n待迁移角色：").append(state.optJSONArray("rotation_control_flow_missing"));
            lines.append("\n记录帧实战执行：").append(state.optBoolean("combat_execution_enabled") ? "已进入内置轴" : "未处于内置轴执行")
                    .append("；翻译清单不等于所有角色实战支持");
        }
        JSONArray words = state.optJSONArray("ocr"); lines.append("\n\n最近识别文本\n");
        if (words == null || words.length() == 0) lines.append("暂无识别文本");
        else for (int i = 0; i < words.length(); i++) lines.append(words.optString(i)).append('\n');
        File errorFile = new File(getFilesDir(), "last-error.txt");
        lines.append("\n历史停止原因（可能早于上述记录）\n").append(error.isEmpty() ? "暂无错误记录" : time(errorFile.lastModified()) + "\n" + error);
        String report = lines.toString();
        if (diagnostic != null && !report.contentEquals(diagnostic.getText())) diagnostic.setText(report);
        lastDiagnostic = "ok-ww Android " + version + "\n" + raw + "\n\n" + report;
    }

    private void enterGame() {
        GameAccessibilityService service = GameAccessibilityService.instance;
        if (service == null || CaptureService.instance == null) { preparation.cancel(); selectPage(2); toast("请先完成无障碍与整个屏幕采集授权"); return; }
        Intent game = getPackageManager().getLaunchIntentForPackage(GardenRules.GAME_PACKAGE);
        if (game == null) { preparation.cancel(); toast("未找到鸣潮应用"); return; }
        boolean startDaily = preparation.consumeDailyStart() && selectedTask() == AutomationTask.DAILY
                && !preferences().getBoolean("readOnly", true);
        service.showPanel(selectedTask()); startActivity(game);
        if (startDaily) service.startPreparedDaily();
    }
    private void prepareGame() {
        if (preparation.active()) { preparation.cancel(); refreshStatus(); toast("已取消准备"); return; }
        if (selectedTask() == AutomationTask.DAILY && !preferences().getBoolean("readOnly", true)) {
            GameAccessibilityService service = GameAccessibilityService.instance;
            if (service != null && service.isRunning()) { toast("已有任务运行，请先停止"); return; }
            new AlertDialog.Builder(this).setTitle("启动每日全流程")
                    .setMessage("启动游戏 → 核验账号与队伍 → 无音区 → 活跃奖励 → 邮件 → 先约电台。\n\n" + AutomationTask.DAILY.confirmation)
                    .setPositiveButton("确认并开始", (dialog, which) -> {
                        if (selectedTask() != AutomationTask.DAILY || preferences().getBoolean("readOnly", true)) return;
                        preparation.confirmDaily(); beginPreparation();
                    }).setNegativeButton("取消", null).show();
            return;
        }
        beginPreparation();
    }
    private void beginPreparation() {
        preparationDeadline = 0;
        applyPreparation(preparation.begin(GameAccessibilityService.instance != null, CaptureService.instance != null));
    }
    private void applyPreparation(PreparationFlow.Action action) {
        try {
            switch (action) {
                case OPEN_ACCESSIBILITY -> {
                    toast("在已下载的应用中启用本应用无障碍，然后返回；后续步骤自动继续");
                    startActivityForResult(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), preparation.request(PreparationFlow.Action.OPEN_ACCESSIBILITY));
                }
                case REQUEST_CAPTURE -> { preparationDeadline = 0; requestCapture(); }
                case ENTER_GAME -> enterGame();
                case CANCELLED -> toast("无障碍连接已结束，准备已取消");
                default -> { }
            }
        } catch (RuntimeException error) { preparation.cancel(); toast("准备停止：" + error.getMessage()); }
        refreshStatus();
    }
    private void continuePreparation() {
        if (!resumed || !preparation.waiting()) return;
        if (preparationDeadline == 0) preparationDeadline = SystemClock.elapsedRealtime() + 5000;
        if (SystemClock.elapsedRealtime() >= preparationDeadline) {
            preparation.cancel(); refreshStatus(); toast("服务尚未连接，准备已停止；可检查权限后重试"); return;
        }
        PreparationFlow.Action action = preparation.poll(GameAccessibilityService.instance != null, CaptureService.instance != null);
        applyPreparation(action);
        if (preparation.waiting()) refresh.postDelayed(this::continuePreparation, 200);
    }
    private void authorizeCapture() {
        preparation.cancel(); requestCapture();
    }
    private void requestCapture() {
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        startActivityForResult(manager.createScreenCaptureIntent(), preparation.request(PreparationFlow.Action.REQUEST_CAPTURE));
    }
    private void stopCapture() {
        preparation.cancel();
        GameAccessibilityService service = GameAccessibilityService.instance;
        if (service != null) service.stop("用户停止");
        stopService(new Intent(this, CaptureService.class)); refresh.postDelayed(this::refreshStatus, 200); toast("任务和屏幕采集已停止");
    }
    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("开始手机任务")
                .setMessage("1. 选择每日任务并关闭只读模式。\n\n2. 点「启动每日全流程」，确认预算与任务范围。\n\n3. 按系统提示完成无障碍和整个屏幕采集授权，已授权步骤自动跳过；随后自动启动游戏并执行每日。\n\n4. 完成后在运行记录查看逐项核验结果。只读模式不操作游戏，其它任务仍在浮窗确认开始。\n\n取消授权、切换任务或停止会取消待启动。结束采集请点「停止 / 结束采集」。")
                .setPositiveButton("知道了", null).show();
    }
    private String read(String path, int limit) {
        File file = new File(getFilesDir(), path);
        if (!file.isFile()) return "";
        if (file.length() > limit) return "记录超过展示上限";
        try { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); }
        catch (Exception error) { return "记录读取失败：" + error.getMessage(); }
    }
    private String time(long timestamp) { return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date(timestamp)); }
    private SharedPreferences preferences() { return getSharedPreferences("weekly", MODE_PRIVATE); }
    private int d(int value) { return dp(this, value); }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); return view; }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout card() { LinearLayout view = column(); view.setPadding(d(18), d(16), d(18), d(16)); view.setBackground(surface(this, CARD, 13, true)); return view; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return view;
    }
    private Button button(String label, boolean primary, Runnable action) {
        Button button = new Button(this); button.setText(label); ConsoleStyle.button(button, primary); button.setOnClickListener(view -> action.run()); return button;
    }
    private void space(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, d(height))); }
    private void divider(LinearLayout parent) { View line = new View(this); line.setBackgroundColor(BORDER); parent.addView(line, new LinearLayout.LayoutParams(-1, d(1))); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    @Override protected void onResume() {
        super.onResume(); resumed = true; refresh.removeCallbacks(tick); refresh.post(tick);
        if (preparation.waiting()) refresh.post(this::continuePreparation);
    }
    @Override protected void onPause() { resumed = false; refresh.removeCallbacksAndMessages(null); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("page", page); state.putString("preparation", preparation.save());
        state.putInt("permissionRequest", preparation.pendingRequest); state.putInt("nextPermissionRequest", preparation.nextRequest);
        state.putString("permissionAction", preparation.pendingAction.name()); super.onSaveInstanceState(state);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        PreparationFlow.Action permission = preparation.consumeResult(request);
        if (permission == PreparationFlow.Action.NONE) return;
        if (permission == PreparationFlow.Action.OPEN_ACCESSIBILITY) {
            preparation.accessibilityReturned(); preparationDeadline = 0;
            if (resumed) refresh.post(this::continuePreparation);
            return;
        }
        preparation.captureReturned(result == RESULT_OK && data != null); preparationDeadline = 0;
        if (result == RESULT_OK && data != null) {
            Intent service = new Intent(this, CaptureService.class); service.putExtra("result", result); service.putExtra("permission", data);
            startForegroundService(service); refresh.postDelayed(this::refreshStatus, 400);
        } else toast("已取消采集授权，未启动任务");
        if (resumed && preparation.waiting()) refresh.post(this::continuePreparation);
    }
}
