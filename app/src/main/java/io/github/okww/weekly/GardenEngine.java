package io.github.okww.weekly;

import android.graphics.Bitmap;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Runs entirely on the phone: capture -> OCR -> guarded Garden policy -> accessibility gesture. */
final class GardenEngine {
    private final GameAccessibilityService service;
    private final ScreenReader reader;
    private final GardenRules policy = new GardenRules();

    GardenEngine(GameAccessibilityService service, ScreenReader reader) {
        this.service = service;
        this.reader = reader;
    }

    void run(boolean automate) throws Exception {
        long started = SystemClock.elapsedRealtime();
        GardenRunGuard guard = new GardenRunGuard(started);
        GardenPacing pacing = new GardenPacing(new java.util.Random());
        service.message(automate ? "幻梦游园：正在识别" : "幻梦游园：只读检查");
        while (!Thread.currentThread().isInterrupted()) {
            long observedAt = SystemClock.elapsedRealtime();
            Bitmap bitmap = service.capture();
            ScreenReader.Snapshot snapshot = null;
            try {
                snapshot = readSnapshot(reader, bitmap);
                if (SystemClock.elapsedRealtime() - observedAt > 3500)
                    throw new IllegalStateException("OCR耗时过长，本帧不操作；重试只读检查预热模型");
                GardenRules.Screen screen = toScreen(snapshot, bitmap);
                GardenRules.Action action = policy.next(screen);
                JSONObject state = persist(service, AutomationTask.GARDEN, snapshot, action, automate, SystemClock.elapsedRealtime() - observedAt, null);
                android.util.Log.i("WWGarden", "observe "+action.scene+" "+action.kind+" elapsed="+(SystemClock.elapsedRealtime()-observedAt));
                service.message((automate ? "自动·" : "只读·") + action.scene + "\n" + action.description);
                CaptureService capture = CaptureService.instance;
                if (capture == null) throw new IllegalStateException("屏幕采集已结束");
                if (!automate || action.kind == GardenRules.Kind.STOP) {
                    capture.saveEvidence(bitmap, "inspect");
                    if (action.kind == GardenRules.Kind.STOP) throw new IllegalStateException(action.description);
                    return;
                }
                if (guard.ready(screen, action, SystemClock.elapsedRealtime())) {
                    if(SystemClock.elapsedRealtime()-observedAt>3500)throw new IllegalStateException("游园输入前画面已过期");
                    if (action.kind == GardenRules.Kind.DONE) {
                        capture.saveEvidence(bitmap, "completed");
                        return;
                    } else if (action.kind == GardenRules.Kind.BACK) service.goBack();
                    else service.tap(action.x * bitmap.getWidth(), action.y * bitmap.getHeight(),
                            bitmap.getWidth(), bitmap.getHeight());
                    policy.applied(action);
                    state.put("input_dispatched",true);writeState(service,AutomationTask.GARDEN.stateFile,state);
                    android.util.Log.i("WWGarden","applied "+action.scene);
                }
            } catch (Exception failure) {
                try { saveFailure(service,"garden",bitmap,snapshot,observedAt,failure); }
                catch(Exception evidenceFailure){failure.addSuppressed(evidenceFailure);}
                throw failure;
            } finally { bitmap.recycle(); }
            int pause=pacing.next();
            android.util.Log.i("WWGarden","pause_ms="+pause);
            Thread.sleep(pause);
        }
        throw new InterruptedException();
    }

    static void saveFailure(GameAccessibilityService service,String label,Bitmap frame,ScreenReader.Snapshot snapshot,long requested,Exception failure)throws Exception{
        CaptureService capture=CaptureService.instance;if(capture==null)return;
        capture.saveEvidence(frame,label+"-failure");
        JSONObject state=new JSONObject().put("task",label).put("timestamp",System.currentTimeMillis())
                .put("observation_requested_elapsed_ms",requested).put("error",failure.toString()).put("ocr_available",snapshot!=null)
                .put("image_width",frame.getWidth()).put("image_height",frame.getHeight());
        JSONArray words=new JSONArray();if(snapshot!=null)for(ScreenReader.Word word:snapshot.words)
            words.put(new JSONObject().put("text",word.text).put("left",word.bounds.left).put("top",word.bounds.top).put("right",word.bounds.right).put("bottom",word.bounds.bottom));
        byte[] png=java.nio.file.Files.readAllBytes(new File(service.getFilesDir(),"evidence/"+label+"-failure.png").toPath());
        StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(png))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        writeState(service,label+"-failure.json",state.put("ocr_regions",words).put("evidence_png_sha256",hash.toString()));
    }

    static GardenRules.Screen toScreen(ScreenReader.Snapshot snapshot, Bitmap bitmap) {
        List<GardenRules.Text> words = new ArrayList<>();
        for (ScreenReader.Word word : snapshot.words) {
            words.add(new GardenRules.Text(word.text, word.bounds.left / (float) bitmap.getWidth(),
                    word.bounds.top / (float) bitmap.getHeight(), word.bounds.right / (float) bitmap.getWidth(),
                    word.bounds.bottom / (float) bitmap.getHeight()));
        }
        return new GardenRules.Screen(words, cardRanks(bitmap), bitmap.getWidth() / (float) bitmap.getHeight());
    }

    static ScreenReader.Snapshot readSnapshot(ScreenReader reader, Bitmap bitmap) throws Exception {
        ScreenReader.Snapshot snapshot = reader.read(bitmap);
        GardenRules.Screen screen = toScreen(snapshot, bitmap);
        float[] bounds = GardenRules.scoreCrop(screen);
        if (bounds != null) {
            // Exclude the currency icon; the score row follows its label, not the success dialog's fixed Y.
            android.graphics.Rect region = new android.graphics.Rect(Math.round(bitmap.getWidth() * bounds[0]),
                    Math.round(bitmap.getHeight() * bounds[1]), Math.round(bitmap.getWidth() * bounds[2]),
                    Math.round(bitmap.getHeight() * bounds[3]));
            Bitmap score = Bitmap.createBitmap(bitmap, region.left, region.top, region.width(), region.height());
            try {
                mergeScore(snapshot, reader.read(score), region);
            } finally { score.recycle(); }
        }
        return snapshot;
    }

    static void mergeScore(ScreenReader.Snapshot snapshot, ScreenReader.Snapshot cropped, android.graphics.Rect region) {
        List<ScreenReader.Word> valid = new ArrayList<>();
        for (ScreenReader.Word word : cropped.words) {
            if (GardenRules.score("本周游历值" + word.text) == null
                    || !GardenRules.normalize(word.text).matches("[0-9]{1,4}/6000")) continue;
            android.graphics.Rect bounds = new android.graphics.Rect(word.bounds);
            bounds.offset(region.left, region.top);
            valid.add(new ScreenReader.Word(word.text, bounds));
        }
        if (valid.isEmpty()) return;
        snapshot.words.removeIf(word -> GardenRules.normalize(word.text).matches(".*[0-9]+/6000.*")
                && region.contains(word.bounds.centerX(), word.bounds.centerY()));
        snapshot.words.addAll(valid);
    }

    private static int[] cardRanks(Bitmap bitmap) {
        int[] ranks = new int[3];
        float[] centers = {.27f, .50f, .73f};
        for (int card = 0; card < 3; card++) {
            int gold = 0, purple = 0;
            // Sample the card background, excluding its name banner and illustration.
            for (float y = .24f; y <= .34f; y += .01f) for (float x = centers[card] - .075f; x <= centers[card] + .075f; x += .01f) {
                int color = bitmap.getPixel(Math.round(x * (bitmap.getWidth() - 1)), Math.round(y * (bitmap.getHeight() - 1)));
                int red = (color >> 16) & 255, green = (color >> 8) & 255, blue = color & 255;
                if (red > 190 && green > 120 && green < 230 && red - blue > 65 && green - blue > 20) gold++;
                if (red > 110 && blue > 130 && blue - green > 10 && red - green > 5) purple++;
            }
            ranks[card] = gold >= 25 ? 2 : purple >= 25 ? 1 : 0;
        }
        return ranks;
    }

    static JSONObject persist(GameAccessibilityService service, AutomationTask task, ScreenReader.Snapshot snapshot,
                        GardenRules.Action action, boolean automate, long recognitionMs, List<String> team) throws Exception {
        return persist(service, task, snapshot, action, automate, recognitionMs, team, null, null);
    }
    static JSONObject persist(GameAccessibilityService service, AutomationTask task, ScreenReader.Snapshot snapshot,
                        GardenRules.Action action, boolean automate, long recognitionMs, List<String> team,
                        String activeCharacter, MobileCombatReader.Observation combat) throws Exception {
        return persist(service, task, snapshot, action, automate, recognitionMs, team, activeCharacter, combat, null);
    }
    static JSONObject persist(GameAccessibilityService service, AutomationTask task, ScreenReader.Snapshot snapshot,
                        GardenRules.Action action, boolean automate, long recognitionMs, List<String> team,
                        String activeCharacter, MobileCombatReader.Observation combat,
                        DailyRules daily) throws Exception {
        JSONObject state = new JSONObject();
        state.put("task", task.id);
        state.put("mode", automate ? "automatic" : "read_only");
        state.put("scene", action.scene);
        state.put("action", action.kind.name());
        state.put("description", action.description);
        state.put("timestamp", System.currentTimeMillis());
        state.put("recognition_ms", recognitionMs);
        state.put("image_width", snapshot.width);
        state.put("image_height", snapshot.height);
        if (daily != null) {
            if (daily.confirmedUid() != null) state.put("uid", daily.confirmedUid());
            AccountDailyConfig.Profile profile = daily.accountProfile();
            if (profile != null) {
                state.put("account_mask", profile.accountMask);
                state.put("target_tacet_index", profile.tacetIndex);
            }
            state.put("daily_followup_stage", daily.rewards.stage());
            state.put("daily_reward_checks", new JSONObject(daily.rewards.status()));
            state.put("daily_activity_points", daily.rewards.points());
            state.put("daily_followup_reason", daily.followupReason());
            state.put("daily_complete", daily.rewards.complete() && daily.budgetComplete());
            state.put("stamina_limit", DailyStaminaBudget.LIMIT);
            state.put("stamina_spent_this_run", daily.tacet == null ? 0 : daily.tacet.spent());
            if (daily.tacet != null) {
                state.put("tacet_stage", daily.tacet.stage());
                state.put("stamina_reserved", daily.tacet.reserved());
                state.put("stamina_spent_game_day", daily.tacet.dailySpent());
                DailyStaminaPlan plan=daily.tacet.plan();
                if(plan!=null){
                    state.put("stamina_planned_budget",plan.plannedBudget);
                    state.put("stamina_planned_remaining",daily.tacet.remainingPlan());
                    state.put("stamina_exchange_needed",plan.exchangeAmount);
                    state.put("stamina_plan_funded",daily.tacet.funded());
                }
            }
        }
        if (team != null) {
            state.put("team", new JSONArray(team));
            JSONArray translated = new JSONArray(), missing = new JSONArray();
            for (String name : team) (BuiltinRotations.supported(name) ? translated : missing).put(name);
            state.put("rotation_control_flow_translated", translated);
            state.put("rotation_control_flow_missing", missing);
            state.put("combat_execution_enabled", false);
        }
        if (activeCharacter != null) state.put("last_observed_active_character", activeCharacter);
        if (combat != null && combat.first != null && combat.second != null) {
            state.put("standby_avatar_1", new JSONObject().put("character", combat.first.character)
                    .put("score", combat.first.score).put("runner_up", combat.first.runnerUp));
            state.put("standby_avatar_2", new JSONObject().put("character", combat.second.character)
                    .put("score", combat.second.score).put("runner_up", combat.second.runnerUp));
            state.put("active_character_confirmed_this_frame", combat.state != null);
        }
        JSONArray text = new JSONArray();
        JSONArray regions = new JSONArray();
        for (ScreenReader.Word word : snapshot.words) {
            text.put(word.text);
            regions.put(new JSONObject().put("text", word.text).put("left", word.bounds.left).put("top", word.bounds.top)
                    .put("right", word.bounds.right).put("bottom", word.bounds.bottom));
        }
        state.put("ocr", text);
        state.put("ocr_regions", regions);
        writeState(service, task.stateFile, state);
        return state;
    }

    static void writeState(GameAccessibilityService service, String filename, JSONObject state) throws Exception {
        File directory = new File(service.getFilesDir(), "evidence");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("日志目录创建失败");
        File temporary = new File(directory, filename + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(state.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        if (!temporary.renameTo(new File(directory, filename)))
            throw new IllegalStateException("状态日志保存失败");
    }
}
