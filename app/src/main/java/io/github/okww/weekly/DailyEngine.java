package io.github.okww.weekly;

import android.graphics.Bitmap;
import android.os.SystemClock;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Real capture/OCR/gesture loop; daily task never falls through to GardenEngine. */
final class DailyEngine {
    private final GameAccessibilityService service;
    private final ScreenReader reader;
    DailyEngine(GameAccessibilityService service, ScreenReader reader) { this.service = service; this.reader = reader; }

    void run(boolean automate) throws Exception {
        execute(automate,null);
    }
    DailyResult runAccount(AccountDailyConfig.Profile account) throws Exception {
        if(account==null)throw new IllegalArgumentException("多账号每日缺少目标账号");
        return execute(true,account);
    }
    private DailyResult execute(boolean automate,AccountDailyConfig.Profile account) throws Exception {
        String day=DailyStaminaLedger.gameDay(System.currentTimeMillis());
        DailyRules policy = new DailyRules(new DailyTacet(),account);
        GardenRunGuard guard = new GardenRunGuard(SystemClock.elapsedRealtime());
        String previousScene = "";
        String inspectDescription = "";
        int inspectFrames = 0;
        TeamNameRecognizer recognizer = null;
        List<String> team = null;
        MobileCombatReader combatReader = null;
        String activeCharacter = null;
        TacetCombatPort port = null;
        MobileTreasureReader treasures = new MobileTreasureReader(service.getAssets());
        TacetTreasureNavigation treasureNavigation = null;
        boolean treasureCapture=false;
        ActivityRewardVision activityVision=new ActivityRewardVision();
        try {
        while (!Thread.currentThread().isInterrupted()) {
            if(treasureCapture!=policy.tacet.seekingTreasure()) {
                treasureCapture=policy.tacet.seekingTreasure();service.combatCapture(treasureCapture);
            }
            long started = SystemClock.elapsedRealtime();
            Bitmap bitmap = service.capture();
            long capturedAt = SystemClock.elapsedRealtime();
            ScreenReader.Snapshot snapshot = null;
            boolean delegatedCombat = false;
            try {
                snapshot = policy.tacet.seekingTreasure() ? reader.combat(bitmap)
                        : readSnapshot(reader, bitmap, policy.tacet.recovering());
                if (SystemClock.elapsedRealtime() - capturedAt > 3500)
                    throw new IllegalStateException("OCR耗时过长，本帧不操作；先运行只读检查预热模型");
                GardenRules.Screen screen = GardenEngine.toScreen(snapshot, bitmap);
                PortraitMatcher.Match treasure = TacetTreasureNavigation.postWorld(screen) ? treasures.find(bitmap) : null;
                MobileCombatReader.Observation healing = null;
                boolean partyFullyHealed = false;
                if (policy.tacet.recovering() && combatReader != null && GardenRules.worldHud(screen)) {
                    healing = combatReader.read(bitmap, screen, capturedAt);
                    if (healing.state != null) {
                        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
                        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
                        partyFullyHealed = PartyHealth.fullParty(new PortraitMatcher.Pixels(bitmap.getWidth(), bitmap.getHeight(), pixels), screen)
                                && SystemClock.elapsedRealtime() - capturedAt <= 1500;
                    }
                }
                long resourceNow=System.currentTimeMillis();
                ActivityRewardVision.Observation activityIcons=null;
                if(screen.one(".*活跃行迹",0,0,.25f,.15f)!=null){
                    int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];
                    bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
                    activityIcons=activityVision.observe(new PortraitMatcher.Pixels(bitmap.getWidth(),bitmap.getHeight(),pixels),
                            screen,policy.confirmedUid(),capturedAt,SystemClock.elapsedRealtime());
                }
                policy.activityFrame(activityIcons);
                policy.resourceFrame(resourceNow-(SystemClock.elapsedRealtime()-capturedAt),resourceNow);
                GardenRules.Action action = policy.next(screen, partyFullyHealed);
                if((treasure!=null||TacetTreasureNavigation.interaction(screen)!=null)&&policy.tacet.stage().equals("NAVIGATION")&&policy.confirmedUid()!=null
                        &&policy.confirmedUid().equals(AccountDailyConfig.detectUid(screen))&&!policy.uidConflict())
                    action=new GardenRules.Action(GardenRules.Kind.BACK,"daily-unowned-treasure-back",
                            "发现此前挑战的宝箱，先退出并重新核验本轮任务，不直接消费",0,0);
                if (policy.confirmedUid() != null && !policy.tacet.hasLedger()) {
                    policy.tacet.attachLedger(new DailyStaminaLedger(new DailyLedgerFile(
                            new java.io.File(service.getFilesDir(), "daily-ledger"), policy.confirmedUid()),
                            policy.confirmedUid(), System.currentTimeMillis()));
                    if(policy.tacet.pendingSettlement())action=policy.tacet.settlePending(screen,policy.confirmedUid(),
                            resourceNow-(SystemClock.elapsedRealtime()-capturedAt),System.currentTimeMillis());
                }
                if (action.scene.equals("daily-team") || action.scene.equals("daily-tacet-team")) {
                    boolean entryTeam = action.scene.equals("daily-tacet-team");
                    if (team != null) { team = null; combatReader = null; activeCharacter = null; recognizer = null; }
                    if (recognizer == null) try (InputStreamReader catalog = new InputStreamReader(
                            service.getAssets().open("builtin-characters.tsv"), StandardCharsets.UTF_8)) {
                        recognizer = TeamNameRecognizer.forXiaomi13Pro(catalog, bitmap.getWidth(), bitmap.getHeight());
                    }
                    team = recognizer.observe(screen, bitmap.getWidth(), bitmap.getHeight(), capturedAt, SystemClock.elapsedRealtime());
                    if (team != null) {
                        if (entryTeam) {
                            String missing=TacetCombatPort.entryProblem(team);
                            if(!missing.isEmpty())throw new IllegalStateException("队伍已识别，角色接线/标定缺口："+missing+"；未开启挑战");
                        }
                        if (entryTeam) policy.tacet.entryTeamConfirmed();
                        else policy.teamConfirmed();
                        combatReader = new MobileCombatReader(service.getAssets(), team);
                        action = new GardenRules.Action(GardenRules.Kind.WAIT, "daily-team-identified",
                                "已按槽位识别：" + String.join(" → ", team) + "；尚未进入战斗", 0, 0);
                        captureTeam(bitmap);
                    }
                }
                MobileCombatReader.Observation combat = null;
                if(policy.tacet.seekingTreasure())activeCharacter=null;
                if (combatReader != null && !policy.tacet.seekingTreasure() && !action.scene.equals("daily-tacet-combat")
                        && GardenRules.worldHud(screen)) {
                    combat = healing != null ? healing : combatReader.read(bitmap, screen, capturedAt);
                    activeCharacter = combat.state == null ? null : team.get(combat.state.active);
                    // Opening menus needs a verified world HUD, not a combat-ready active character.
                    if (activeCharacter != null) action = new GardenRules.Action(action.kind, action.scene,
                            "当前上场：" + activeCharacter + "；" + action.description, action.x, action.y);
                } else if (combatReader != null && !policy.tacet.seekingTreasure() && !action.scene.equals("daily-tacet-combat")) combatReader.read(bitmap, screen, capturedAt);
                CombatGesture treasureStep = null;
                if(automate&&policy.tacet.seekingTreasure()&&action.scene.equals("daily-tacet-treasure")) {
                    if(treasureNavigation==null)treasureNavigation=new TacetTreasureNavigation(policy.confirmedUid(),SystemClock.elapsedRealtime());
                    // Pure walking depends on owned battle/UID/marker, not the active character's attack identity.
                    treasureStep=treasureNavigation.next(screen,treasure,!policy.uidConflict(),capturedAt,SystemClock.elapsedRealtime());
                    if(treasureStep!=null)action=GardenRules.Action.waitFor("daily-tacet-treasure-move","按新鲜宝箱位置短步靠近，每步释放并重新识别");
                } else if(!policy.tacet.seekingTreasure())treasureNavigation=null;
                else if(treasureNavigation!=null)treasureNavigation.unconfirmedFrame();
                org.json.JSONObject recorded = GardenEngine.persist(service, AutomationTask.DAILY, snapshot, action, automate, SystemClock.elapsedRealtime() - started, team,
                        activeCharacter, combat, policy);
                if(treasure!=null){recorded.put("treasure_score",treasure.score);recorded.put("treasure_x",treasure.x);recorded.put("treasure_y",treasure.y);
                    GardenEngine.writeState(service,AutomationTask.DAILY.stateFile,recorded);}
                if(activityIcons!=null){
                    org.json.JSONArray states=new org.json.JSONArray();
                    for(int i=0;i<5;i++)states.put(activityIcons.state(i).name());
                    recorded.put("activity_chest_states",states);
                    recorded.put("activity_chest_capture_elapsed",activityIcons.capturedAt);
                    recorded.put("activity_chest_claimed_scores",new org.json.JSONArray(activityIcons.claimedScores()));
                    recorded.put("activity_chest_closed_scores",new org.json.JSONArray(activityIcons.closedScores()));
                    recorded.put("activity_chest_available_scores",new org.json.JSONArray(activityIcons.availableScores()));
                    recorded.put("activity_chest_gold_pixels",new org.json.JSONArray(activityIcons.goldPixels()));
                    recorded.put("activity_chest_red_pixels",new org.json.JSONArray(activityIcons.redPixels()));
                    GardenEngine.writeState(service,AutomationTask.DAILY.stateFile,recorded);
                }
                String uidPrefix = policy.accountProfile() != null
                        ? "账号:" + policy.accountProfile().accountMask + "(#" + policy.accountProfile().tacetIndex + ") "
                        : policy.confirmedUid() != null ? "特征码:" + policy.confirmedUid() + " " : "";
                service.message((automate ? "自动·每日·" : "只读·每日·") + action.scene + "\n" + uidPrefix + action.description);
                CaptureService capture = CaptureService.instance;
                if (capture == null) throw new IllegalStateException("屏幕采集已结束");
                if (action.scene.equals("daily-followup-finished")) {
                    inspectFrames = action.description.equals(inspectDescription) ? inspectFrames + 1 : 1;
                    inspectDescription = action.description;
                } else { inspectFrames = 0; inspectDescription = ""; }
                if (!previousScene.equals(action.scene)) {
                    capture.saveEvidence(bitmap, "daily-scene");
                    if (DailyRules.unmatched(action)) {
                        capture.saveEvidence(bitmap, "daily-unrecognized");
                        GardenEngine.writeState(service, "daily-unrecognized.json", recorded);
                    }
                    previousScene = action.scene;
                }
                if (!automate || action.kind == GardenRules.Kind.STOP || inspectFrames >= 2) {
                    capture.saveEvidence(bitmap, "daily-inspect");
                    if (action.kind == GardenRules.Kind.STOP) throw new IllegalStateException(action.description);
                    if(account==null)return null;
                    if(!day.equals(DailyStaminaLedger.gameDay(System.currentTimeMillis())))throw new IllegalStateException("每日运行跨过游戏日，保留账本并重新核验");
                    return new DailyResult(policy.confirmedUid(),day,policy.rewards.complete()&&policy.budgetComplete(),
                            policy.tacet.spent(),policy.tacet.reserved());
                }
                if (action.scene.equals("daily-tacet-combat")) {
                    if (team == null || policy.confirmedUid() == null) throw new IllegalStateException("战斗缺少本轮账号或编队核验");
                    if (port == null) port = new TacetCombatPort(service, reader, policy, team);
                    delegatedCombat = true;
                    try { while (true) port.round(); }
                    catch (TacetCombatPort.Ended ended) { port.close(); port = null; }
                    continue;
                }
                if(treasureStep!=null) {
                    if(SystemClock.elapsedRealtime()-capturedAt>1500)continue;
                    android.util.Log.i("WWTreasure","step marker="+treasure.x+","+treasure.y+" score="+treasure.score);
                    service.performGesture(treasureStep);
                    continue;
                }
                // The seeker's own 45s/16-step bound measures movement, unlike the text-only menu guard.
                if(policy.tacet.seekingTreasure()&&action.scene.equals("daily-tacet-treasure")) {Thread.sleep(250);continue;}
                if (guard.ready(screen, action, SystemClock.elapsedRealtime())) {
                    if(SystemClock.elapsedRealtime()-capturedAt>3500)
                        throw new IllegalStateException("输入前画面已过期，未发送点击；保留现有账本");
                    policy.beforeResourceInput(action,System.currentTimeMillis());
                    policy.tacet.beforeInput(action, System.currentTimeMillis());
                    if (action.kind == GardenRules.Kind.BACK) service.goBack();
                    else {
                        CombatGesture resourceGesture=policy.resourceGesture(action);
                        if(resourceGesture!=null)service.performGesture(resourceGesture);
                        else service.tap(action.x * bitmap.getWidth(), action.y * bitmap.getHeight(), bitmap.getWidth(), bitmap.getHeight());
                    }
                    policy.applied(action);
                }
            } catch(Exception failure) {
                try { if(!delegatedCombat)GardenEngine.saveFailure(service,"daily",bitmap,snapshot,started,failure); }
                catch(Exception evidenceFailure){failure.addSuppressed(evidenceFailure);}
                throw failure;
            } finally { bitmap.recycle(); }
            Thread.sleep(250);
        }
        throw new InterruptedException();
        } finally { if (port != null) port.close(); if(treasureCapture)service.combatCapture(false); }
    }

    private void captureTeam(Bitmap bitmap) throws Exception {
        CaptureService capture = CaptureService.instance;
        if (capture == null) throw new IllegalStateException("屏幕采集已结束");
        capture.saveEvidence(bitmap, "daily-team");
    }

    static ScreenReader.Snapshot readSnapshot(ScreenReader reader, Bitmap bitmap, boolean recovering) throws Exception {
        ScreenReader.Snapshot snapshot = reader.read(bitmap);
        GardenRules.Screen raw=GardenEngine.toScreen(snapshot,bitmap);
        if(bitmap.getWidth()==2400 && bitmap.getHeight()==1080 && DailyTacet.settlementPage(raw))
            reader.enlarge(snapshot,bitmap,1340,965,190,55);
        if(bitmap.getWidth()==2400 && bitmap.getHeight()==1080 && raw.one(".*清理无.*区.*残.*",.02f,.16f,.40f,.42f)!=null)
            reader.combatDetail(snapshot,bitmap);
        if (bitmap.getWidth()==2400 && bitmap.getHeight()==1080 && DailyRules.needsHudDetail(GardenEngine.toScreen(snapshot,bitmap),recovering)) {
            // 15px mobile HP/level text was repeatedly lost in full-screen OCR after leaving the arena.
            reader.enlarge(snapshot,bitmap,1050,990,390,42);
        }
        float[] bounds = DailyRules.activityCrop(GardenEngine.toScreen(snapshot, bitmap));
        if (bounds == null) return snapshot;
        int left = Math.round(bounds[0] * bitmap.getWidth()), top = Math.round(bounds[1] * bitmap.getHeight());
        int width = Math.round(bounds[2] * bitmap.getWidth()) - left;
        int height = Math.round(bounds[3] * bitmap.getHeight()) - top;
        Bitmap crop = Bitmap.createBitmap(bitmap, left, top, width, height);
        Bitmap enlarged = Bitmap.createScaledBitmap(crop, width * 3, height * 3, true);
        try {
            ScreenReader.Snapshot detail = reader.read(enlarged);
            ScreenReader.Word number = null;
            for (ScreenReader.Word word : detail.words) if (GardenRules.normalize(word.text).matches(DailyRules.ACTIVITY_NUMBER)) {
                if (number != null) return snapshot;
                number = word;
            }
            if (number != null) {
                snapshot.words.removeIf(word -> word.bounds.centerX() >= left && word.bounds.centerX() <= left + width
                        && word.bounds.centerY() >= top && word.bounds.centerY() <= top + height);
                snapshot.words.add(new ScreenReader.Word(number.text, new android.graphics.Rect(
                        left + Math.round(number.bounds.left / 3f), top + Math.round(number.bounds.top / 3f),
                        left + Math.round(number.bounds.right / 3f), top + Math.round(number.bounds.bottom / 3f))));
            }
        } finally { enlarged.recycle(); crop.recycle(); }
        return snapshot;
    }
}
