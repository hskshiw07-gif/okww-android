package io.github.okww.weekly;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Uses the APK's actual ML Kit recognizer on existing screenshots; sends no input to the game. */
public final class GardenFixtureRunner extends Instrumentation {
    private volatile boolean finished;
    private boolean uiOnly;
    private String expectedLocalResultScore;
    private String expectedLocalBalance;
    private boolean recoveryOnly;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        uiOnly = arguments != null && "true".equals(arguments.getString("uiOnly"));
        expectedLocalResultScore = arguments == null ? null : arguments.getString("resultScore");
        expectedLocalBalance = arguments == null ? null : arguments.getString("dailyBalance");
        recoveryOnly=arguments!=null&&"true".equals(arguments.getString("dailyRecovery"));
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(20000); } catch (InterruptedException ignored) { return; }
            if (finished) return;
            StringBuilder stacks = new StringBuilder("Fixture watchdog timeout\n");
            for (java.util.Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                stacks.append(entry.getKey().getName()).append('\n');
                for (StackTraceElement line : entry.getValue()) stacks.append("  ").append(line).append('\n');
            }
            android.util.Log.e("GardenFixtures", stacks.toString());
            try (FileOutputStream output = new FileOutputStream(new File(getTargetContext().getFilesDir(), "fixture-timeout.txt"))) {
                output.write(stacks.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception error) { android.util.Log.e("GardenFixtures", "Save timeout", error); }
            Bundle failure = new Bundle();
            failure.putString("stream", "Fixture timeout; inspect files/fixture-timeout.txt");
            finish(Activity.RESULT_CANCELED, failure);
        }, "fixture-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        if (uiOnly) { verifyConsole(result); return; }
        if(expectedLocalBalance!=null){verifyDailyBalance(result);return;}
        if(recoveryOnly){verifyRecovery(result);return;}
        JSONArray cases = new JSONArray();
        int failures = 0;
        String[][] fixtures = {{"26.png", "new-stage"}, {"27.png", "stage-confirm"},
                {"28.png", "shop-close"}, {"29.png", "next-day"}, {"30.png", "card-select"},
                {"32.png", "event-select"}, {"33.png", "result-retry"},
                {"36.png", "entrance-cap"}, {"38.png", "unpause"}, {"40.png", "resume"}};
        try (ScreenReader reader = new ScreenReader()) {
            for (String[] sample : new String[][]{{"90/6000", "0/6000"}, {"9300/6000", "300/6000"}, {"91600/6000", "1600/6000"}}) {
                ScreenReader.Snapshot full = new ScreenReader.Snapshot(new java.util.ArrayList<>(java.util.Arrays.asList(
                        new ScreenReader.Word("本周游历值", new android.graphics.Rect(0, 0, 100, 30)),
                        new ScreenReader.Word(sample[0], new android.graphics.Rect(10, 40, 100, 60)))));
                ScreenReader.Snapshot crop = new ScreenReader.Snapshot(java.util.Collections.singletonList(
                        new ScreenReader.Word(sample[1], new android.graphics.Rect(0, 0, 90, 20))));
                GardenEngine.mergeScore(full, crop, new android.graphics.Rect(10, 40, 100, 60));
                StringBuilder text = new StringBuilder();
                for (ScreenReader.Word word : full.words) text.append(word.text).append('|');
                if (!Integer.valueOf(sample[1].split("/")[0]).equals(GardenRules.score(text.toString())))
                    throw new AssertionError("Cropped score conflicts with stale full-frame OCR: " + text);
            }
            android.util.Log.i("GardenFixtures", "Recognizer created");
            for (String[] fixture : fixtures) {
                android.util.Log.i("GardenFixtures", "Reading " + fixture[0]);
                JSONObject report = new JSONObject();
                report.put("fixture", fixture[0]);
                report.put("expected", fixture[1]);
                try (InputStream input = getContext().getAssets().open(fixture[0])) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap == null) throw new IllegalStateException("Invalid fixture");
                    try {
                        ScreenReader.Snapshot snapshot = GardenEngine.readSnapshot(reader, bitmap);
                        android.util.Log.i("GardenFixtures", "Read finished " + fixture[0]);
                        GardenRules.Action action = new GardenRules().next(GardenEngine.toScreen(snapshot, bitmap));
                        report.put("actual", action.scene);
                        report.put("passed", fixture[1].equals(action.scene));
                        JSONArray words = new JSONArray();
                        for (ScreenReader.Word word : snapshot.words) words.put(word.text);
                        report.put("ocr", words);
                        if (!fixture[1].equals(action.scene)) failures++;
                    } finally { bitmap.recycle(); }
                } catch (Exception error) { failures++; report.put("error", error.toString()); }
                cases.put(report);
            }
            if (expectedLocalResultScore != null) {
                // Local account screenshot stays in the app's private evidence folder, never in APK assets.
                Bitmap bitmap = BitmapFactory.decodeFile(new File(getTargetContext().getFilesDir(), "evidence/failure.png").getPath());
                if (bitmap == null) throw new IllegalStateException("Missing local failure evidence");
                try {
                    ScreenReader.Snapshot snapshot = GardenEngine.readSnapshot(reader, bitmap);
                    GardenRules.Screen screen = GardenEngine.toScreen(snapshot, bitmap);
                    Integer points = GardenRules.resultScore(screen);
                    GardenRules.Action action = new GardenRules().next(screen);
                    int expected = Integer.parseInt(expectedLocalResultScore);
                    String scene = expected == GardenRules.TARGET ? "result-cap" : "result-retry";
                    boolean passed = Integer.valueOf(expected).equals(points) && scene.equals(action.scene);
                    JSONObject local = new JSONObject().put("fixture", "private/failure.png").put("expected_score", expected)
                            .put("actual_score", points).put("actual", action.scene).put("passed", passed);
                    JSONArray words = new JSONArray();
                    for (ScreenReader.Word word : snapshot.words) words.put(word.text);
                    local.put("ocr", words); cases.put(local);
                    if (!passed) failures++;
                } finally { bitmap.recycle(); }
            }
            JSONObject report = new JSONObject().put("failures", failures).put("cases", cases);
            File output = new File(getTargetContext().getFilesDir(), "garden-fixtures.json");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            result.putString("stream", report.toString(2));
            finished = true;
            finish(failures == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        } catch (Exception error) {
            finished = true;
            result.putString("stream", "Fixture runner failed: " + error);
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyRecovery(Bundle result){
        Bitmap bitmap=null;
        try(ScreenReader reader=new ScreenReader()){
            bitmap=BitmapFactory.decodeFile(new File(getTargetContext().getFilesDir(),"evidence/daily-recovery-fixture.png").getPath());
            if(bitmap==null)throw new IllegalStateException("Missing private recovery fixture");
            // Warm ML Kit separately; the replay asserts actual OCR + party bars, with no fabricated HP text.
            DailyEngine.readSnapshot(reader,bitmap,true);
            long start=android.os.SystemClock.elapsedRealtime();
            ScreenReader.Snapshot snapshot=DailyEngine.readSnapshot(reader,bitmap,true);
            GardenRules.Screen screen=GardenEngine.toScreen(snapshot,bitmap);
            boolean passed=PartyHealth.fullParty(MobileCombatReader.pixels(bitmap),screen);
            JSONArray words=new JSONArray();for(ScreenReader.Word word:snapshot.words)words.put(word.text);
            result.putString("stream",new JSONObject().put("passed",passed).put("world",GardenRules.worldHud(screen))
                    .put("recognition_ms",android.os.SystemClock.elapsedRealtime()-start).put("ocr",words).toString());
            finished=true;finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }catch(Exception error){finished=true;result.putString("stream","Recovery replay failed: "+error);finish(Activity.RESULT_CANCELED,result);}
        finally{if(bitmap!=null)bitmap.recycle();}
    }
    private void verifyDailyBalance(Bundle result){
        Bitmap bitmap=null;
        try(ScreenReader reader=new ScreenReader()){
            bitmap=BitmapFactory.decodeFile(new File(getTargetContext().getFilesDir(),"evidence/daily-settle-fixture.png").getPath());
            if(bitmap==null)throw new IllegalStateException("Missing private daily settlement fixture");
            ScreenReader.Snapshot snapshot=DailyEngine.readSnapshot(reader,bitmap,false);
            Integer balance=DailyTacet.balance(GardenEngine.toScreen(snapshot,bitmap));
            JSONArray words=new JSONArray();for(ScreenReader.Word word:snapshot.words)words.put(word.text);
            boolean passed=Integer.valueOf(expectedLocalBalance).equals(balance);
            result.putString("stream",new JSONObject().put("passed",passed).put("actual_balance",balance).put("ocr",words).toString());
            finished=true;finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }catch(Exception error){finished=true;result.putString("stream","Daily balance replay failed: "+error);finish(Activity.RESULT_CANCELED,result);}
        finally{if(bitmap!=null)bitmap.recycle();}
    }
    private void verifyConsole(Bundle result) {
        android.content.SharedPreferences preferences = getTargetContext().getSharedPreferences("weekly", 0);
        boolean original = preferences.getBoolean("readOnly", true);
        String originalTask = preferences.getString("task", "GARDEN");
        MainActivity[] activity = {null};
        int[] overlayChecks = {0};
        int outcome = Activity.RESULT_CANCELED;
        try {
            android.content.Intent intent = new android.content.Intent(getTargetContext(), MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
            try {
                getTargetContext().startActivity(intent);
                activity[0] = (MainActivity) monitor.waitForActivityWithTimeout(5000);
            } finally { removeMonitor(monitor); }
            if (activity[0] == null) throw new AssertionError("Console activity did not open within 5 seconds");
            Throwable[] uiFailure = {null};
            runOnMainSync(() -> {
                try {
                MainActivity main = activity[0];
                if (main.findViewById(android.R.id.content).findViewWithTag("page-0") == null)
                    throw new AssertionError("Missing task page");
                for (AutomationTask task : new AutomationTask[]{AutomationTask.GARDEN, AutomationTask.DAILY}) {
                    main.findViewById(android.R.id.content).findViewWithTag("task-" + task.name()).performClick();
                    if (!task.name().equals(preferences.getString("task", "GARDEN")))
                        throw new AssertionError("Task selection not persisted: " + task);
                    GameAccessibilityService service = GameAccessibilityService.instance;
                    if (service != null) {
                        java.lang.reflect.Field selected = GameAccessibilityService.class.getDeclaredField("selectedTask");
                        selected.setAccessible(true);
                        if (selected.get(service) != task) throw new AssertionError("Overlay task differs from console");
                        overlayChecks[0]++;
                    }
                }
                android.widget.Switch toggle = main.findViewById(android.R.id.content).findViewWithTag("mode-switch");
                toggle.performClick();
                if (preferences.getBoolean("readOnly", true) == original) throw new AssertionError("Mode not saved");
                for (int page : new int[]{1, 2, 0}) {
                    main.findViewById(android.R.id.content).findViewWithTag("nav-" + page).performClick();
                    if (main.findViewById(android.R.id.content).findViewWithTag("page-" + page) == null)
                        throw new AssertionError("Navigation failed: " + page);
                }
                android.widget.Switch restored = main.findViewById(android.R.id.content).findViewWithTag("mode-switch");
                if (restored.isChecked() == original) throw new AssertionError("Mode lost after navigation");
                android.view.View launch = main.findViewById(android.R.id.content).findViewWithTag("launch-game");
                if (launch.getMinimumHeight() < ConsoleStyle.dp(main, 44)) throw new AssertionError("Small touch target");
                if (CaptureService.instance == null) {
                    if (!((android.widget.Button) launch).getText().toString().contains("一键准备"))
                        throw new AssertionError("Missing capture must offer preparation, not direct launch");
                }
                } catch (Throwable error) { uiFailure[0] = error; }
            });
            if (uiFailure[0] != null) throw new AssertionError("UI assertion", uiFailure[0]);
            result.putString("stream", "Console UI passed: 3 pages, task selection persistence, mode persistence, touch target, preparation entry; no game input. Overlay task consistency: "
                    + (overlayChecks[0] == 0 ? "skipped (service disconnected)" : "checked " + overlayChecks[0] + " selections") + ".\n");
            outcome = Activity.RESULT_OK;
        } catch (Throwable error) {
            result.putString("stream", "Console UI failed: " + error);
        } finally {
            if (!preferences.edit().putBoolean("readOnly", original).putString("task", originalTask).commit()) {
                outcome = Activity.RESULT_CANCELED;
                result.putString("stream", "Console UI failed to restore readOnly preference");
            }
            if (activity[0] != null) runOnMainSync(() -> {
                if (GameAccessibilityService.instance != null) GameAccessibilityService.instance.selectTask(AutomationTask.restore(originalTask));
                activity[0].finish();
            });
        }
        finished = true;
        finish(outcome, result);
    }
}
