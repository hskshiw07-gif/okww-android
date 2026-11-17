package io.github.okww.weekly;

import android.graphics.Bitmap;
import android.os.SystemClock;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static io.github.okww.weekly.GardenRules.Action;
import static io.github.okww.weekly.GardenRules.Kind;

/** Starts near a user-selected boss; never opens the guide or claims a chest. */
final class EchoFarmEngine {
    private final GameAccessibilityService service;
    private final ScreenReader reader;
    EchoFarmEngine(GameAccessibilityService service, ScreenReader reader) { this.service = service; this.reader = reader; }

    void run(boolean automate) throws Exception {
        String boss = service.getSharedPreferences("weekly", 0).getString(EchoFarmRules.BOSS_KEY, "");
        EchoFarmRules preview = new EchoFarmRules(boss, null);
        EchoFarmRules farm = null;
        String uidCandidate = null;
        long uidAt = -1;
        GardenRunGuard teamGuard = new GardenRunGuard(SystemClock.elapsedRealtime());
        TeamNameRecognizer names = null;
        List<String> team = null;
        boolean atArena = false;
        long started = SystemClock.elapsedRealtime(), deadline = started + 45000;
        int checked = 0, rewardNotices = 0;
        boolean reportDrop = false;
        Action status = Action.waitFor("echo-team", "在首领附近的非战斗状态开始，先识别当前编队");
        try {
            if (automate) service.combatCapture(true);
            while (!Thread.currentThread().isInterrupted()) {
                Bitmap frame = service.capture();
                long captured = SystemClock.elapsedRealtime();
                ScreenReader.Snapshot snapshot = null;
                try {
                    if (frame.getWidth() != 2400 || frame.getHeight() != 1080)
                        throw new IllegalStateException("刷4C试验版只适配2400×1080布局");
                    snapshot = atArena || !automate ? reader.combat(frame) : reader.read(frame);
                    GardenRules.Screen screen = GardenEngine.toScreen(snapshot, frame);
                    long now = SystemClock.elapsedRealtime();
                    if (now - started > 40 * 60000L)
                        throw new IllegalStateException("到达40分钟运行上限");
                    if (now > deadline) throw new IllegalStateException(status.description + "：等待超时");
                    if (now - captured > 1500) continue;
                    if (!automate) {
                        status = Action.waitFor("echo-inspect", preview.bossLabel(screen) ? "识别到目标首领名" : "本帧未识别到目标首领名");
                        GardenEngine.persist(service, AutomationTask.ECHO_FARM, snapshot, status, false, now - captured, null);
                        CaptureService.instance.saveEvidence(frame, "echo-farm-inspect");
                        service.message(status.description);
                        return;
                    }
                    String seenUid = AccountDailyConfig.detectUid(screen);
                    if (farm != null && seenUid != null && !farm.uid.equals(seenUid))
                        throw new IllegalStateException("刷4C过程中账号发生变化");
                    if (atArena && EchoFarmRules.blocked(screen))
                        throw new IllegalStateException("刷4C遇到弹窗、失败或复活界面，未点击");
                    if (!atArena) {
                        if (farm == null) {
                            if (seenUid != null && seenUid.equals(uidCandidate) && captured - uidAt <= 2500)
                                farm = new EchoFarmRules(boss, seenUid);
                            uidCandidate = seenUid;
                            uidAt = captured;
                            if (farm == null) continue;
                        }
                        Action menu = statusForTeam(screen, team, farm);
                        status = menu;
                        if (menu.scene.equals("echo-read-team")) {
                            if (names == null) try (InputStreamReader catalog = new InputStreamReader(
                                    service.getAssets().open("builtin-characters.tsv"), StandardCharsets.UTF_8)) {
                                names = TeamNameRecognizer.forXiaomi13Pro(catalog, frame.getWidth(), frame.getHeight());
                            }
                            team = names.observe(screen, frame.getWidth(), frame.getHeight(), captured, now);
                            if (team != null) {
                                String missing = TacetCombatPort.entryProblem(team);
                                if (!missing.isEmpty()) throw new IllegalStateException("队伍仍缺战斗标定：" + missing);
                            }
                        } else if (names != null && team == null) {
                            names.observe(screen, frame.getWidth(), frame.getHeight(), captured, now);
                        }
                        if (menu.scene.equals("echo-await-boss")) {
                            atArena = true;
                            deadline = now + 30000;
                            report(snapshot, status, team, checked, rewardNotices, now - captured);
                        } else if (teamGuard.ready(screen, menu, now) && SystemClock.elapsedRealtime() - captured <= 1500) {
                            if (menu.kind == Kind.BACK) service.goBack();
                            else tap(menu.x, menu.y);
                        }
                        continue;
                    }
                    EchoFarmRules.Step step = farm.observe(screen,
                            EchoFarmRules.healthBar(MobileCombatReader.pixels(frame)), captured, now);
                    if (reportDrop) {
                        report(snapshot, status, team, checked, rewardNotices, now - captured);
                        reportDrop = false;
                    }
                    switch (step) {
                        case FIGHT -> {
                            status = Action.waitFor("echo-fight", "已确认同一首领，执行已标定的队伍输出轴");
                            report(snapshot, status, team, checked, rewardNotices, now - captured);
                            try (TacetCombatPort port = new TacetCombatPort(service, reader, farm, team)) {
                                try { while (true) port.round(); }
                                catch (TacetCombatPort.Ended ended) { farm.combatEnded(); }
                            }
                            service.combatCapture(true);
                            deadline = SystemClock.elapsedRealtime() + 18000;
                            status = Action.waitFor("echo-wait-drop", "战斗操作已结束，等待可触及的吸收交互；不寻宝箱、不盲走");
                            reportDrop = true;
                            continue;
                        }
                        case ABSORB -> {
                            if (SystemClock.elapsedRealtime() - captured > 1500) continue;
                            GardenRules.Text button = farm.button();
                            tap(button.x(), button.y());
                            farm.absorbSent();
                            deadline = SystemClock.elapsedRealtime() + 8000;
                            status = Action.waitFor("echo-verify-absorb", "已点一次吸收，等待提示消失，不重复点击");
                        }
                        case ABSORB_CHECKED -> {
                            checked++;
                            if (farm.rewardTextSeen()) rewardNotices++;
                            farm.awaitRespawn();
                            deadline = now + 90000;
                            status = Action.waitFor("echo-await-respawn", "吸收交互已消失，原地等待同一首领重新出现");
                        }
                        case WAIT -> { continue; }
                    }
                    report(snapshot, status, team, checked, rewardNotices, SystemClock.elapsedRealtime() - captured);
                } catch (Exception failure) {
                    if (!(failure instanceof InterruptedException)) try {
                        GardenEngine.saveFailure(service, "echo-farm", frame, snapshot, captured, failure);
                    } catch (Exception evidenceFailure) { failure.addSuppressed(evidenceFailure); }
                    throw failure;
                } finally { frame.recycle(); }
            }
            throw new InterruptedException();
        } finally {
            if (automate) service.combatCapture(false);
        }
    }

    private static Action statusForTeam(GardenRules.Screen screen, List<String> team, EchoFarmRules farm) {
        if (EchoFarmRules.blocked(screen)) return Action.waitFor("echo-menu-blocked", "编队准备遇到阻塞界面，未点击");
        boolean terminal = screen.has("终端|数据坞|索拉等级") && screen.has("地图|设置|设定");
        if (terminal) {
            if (!farm.uid.equals(AccountDailyConfig.detectUid(screen))) return Action.waitFor("echo-uid", "核验终端账号");
            if (team != null) return new Action(Kind.BACK, "echo-return-arena", "返回首领所在地", 0, 0);
            GardenRules.Text button = screen.one("编队|队伍|队伍配置", .35f, .15f, .94f, .85f);
            if (button != null) return Action.tap("echo-open-team", button);
        }
        if (screen.has("快速编队|队伍配置|已出战")) {
            return team == null ? Action.waitFor("echo-read-team", "识别当前三个槽位")
                    : new Action(Kind.BACK, "echo-team-back", "编队已确认，返回终端", 0, 0);
        }
        if (farm.hud(screen)) {
            if (team != null) return Action.waitFor("echo-await-boss", "等待同一首领的名字与血条；需要处于触发范围");
            if (!farm.bossLabel(screen))
                return new Action(Kind.TAP, "echo-open-terminal", "打开终端读取当前编队", .925f, .06f);
        }
        return Action.waitFor("echo-team-unknown", "等待游戏主界面或编队状态");
    }
    private void tap(float x, float y) throws Exception { service.tap(x * 2400, y * 1080, 2400, 1080); }
    private void report(ScreenReader.Snapshot snapshot, Action status, List<String> team,
                        int checked, int notices, long age) throws Exception {
        org.json.JSONObject state = GardenEngine.persist(service, AutomationTask.ECHO_FARM, snapshot, status, true, age, team);
        GardenEngine.writeState(service, AutomationTask.ECHO_FARM.stateFile,
                state.put("absorb_prompt_cleared", checked).put("acquisition_notices", notices));
        service.message(status.description + "；已核对交互 " + checked + " 次");
        android.util.Log.i("WWEchoFarm", status.scene + " absorbChecked=" + checked + " notices=" + notices);
    }
}
