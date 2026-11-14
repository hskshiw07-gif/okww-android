package io.github.okww.weekly;

import android.graphics.Bitmap;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static io.github.okww.weekly.GardenRules.*;

/** Actual on-device capture and DailyEngine execution; no ADB, remote runtime or alternate input service. */
final class MultiAccountEngine implements MultiAccountDaily.Port {
    private final GameAccessibilityService service;
    private final ScreenReader reader;
    private final AutomationTask traceTask;
    private final MultiAccountPlan plan;
    private MultiAccountProgress checkpoint;
    MultiAccountEngine(GameAccessibilityService service,ScreenReader reader)throws Exception{
        this(service,reader,AutomationTask.MULTI_DAILY);
    }
    MultiAccountEngine(GameAccessibilityService service,ScreenReader reader,AutomationTask traceTask)throws Exception{
        this.service=service;this.reader=reader;this.traceTask=traceTask;
        plan=traceTask==AutomationTask.MULTI_DAILY?MultiAccountPlan.parse(MultiAccountFiles.plan(service).read()):new MultiAccountPlan(List.of());
    }
    void run(boolean automate)throws Exception{
        if(!automate){inspect();return;}
        if(plan.enabled().isEmpty())throw new IllegalStateException("请在多账号任务卡片保存并启用账号计划");
        checkpoint=new MultiAccountProgress(MultiAccountFiles.progress(service),now());
        boolean complete=MultiAccountDaily.run(plan,checkpoint,this);
        publish(complete?"multi-finished":"multi-pending",complete?"计划内所有账号每日结果已核验":"本轮账号遍历结束，仍有未完成/未核验项，下次继续",complete);
    }
    @Override public long now(){return System.currentTimeMillis();}
    @Override public void checkLedger(String uid)throws Exception{
        DailyStaminaLedger ledger=new DailyStaminaLedger(new DailyLedgerFile(new File(service.getFilesDir(),"daily-ledger"),uid),uid,now());
        if(ledger.reserved()!=0)throw new IllegalStateException("账号有未核销扣费，停止且不清空账本："+uid);
    }
    @Override public String identifyCurrent()throws Exception{
        AccountSwitchRules.Identity identities=new AccountSwitchRules.Identity();long start=SystemClock.elapsedRealtime();
        while(true){
            check();Bitmap frame=service.capture();long captured=SystemClock.elapsedRealtime();
            ScreenReader.Snapshot snapshot=null;
            try{
                snapshot=DailyEngine.readSnapshot(reader,frame,false);Screen screen=GardenEngine.toScreen(snapshot,frame);
                String uid=identities.observe(screen,captured,SystemClock.elapsedRealtime());
                record(snapshot,Action.waitFor("multi-identify",uid==null?"从当前大世界连续核验 UID；尚未退出登录":"当前 UID 已核验"),true,SystemClock.elapsedRealtime()-captured);
                if(uid!=null)return uid;
                if(SystemClock.elapsedRealtime()-start>=25000)
                    throw new IllegalStateException("当前账号识别超时，请从大世界启动；未为识别账号而退出登录");
            }catch(Exception failure){
                saveFailure(frame,snapshot,captured,"multi-identify-failure","IDENTIFY",null,null,failure);
                throw failure;
            }finally{frame.recycle();}
            Thread.sleep(250);
        }
    }
    @Override public String switchAccount(String currentUid,MultiAccountPlan.Account target)throws Exception{
        check();service.holdAttack(false,CombatTouchLayout.forXiaomi13Pro());service.combatCapture(false);
        AccountSwitchRules rules=new AccountSwitchRules(currentUid,target,SystemClock.elapsedRealtime());
        GardenRunGuard guard=new GardenRunGuard(SystemClock.elapsedRealtime());
        while(true){
            check();Bitmap frame=service.capture();long captured=SystemClock.elapsedRealtime();
            ScreenReader.Snapshot snapshot=null;
            try{
                snapshot=DailyEngine.readSnapshot(reader,frame,false);Screen screen=GardenEngine.toScreen(snapshot,frame);
                List<AccountSwitchRules.Label> raw=new ArrayList<>();
                for(ScreenReader.Word word:snapshot.words)raw.add(new AccountSwitchRules.Label(word.text,new Text(word.text,
                        word.bounds.left/(float)frame.getWidth(),word.bounds.top/(float)frame.getHeight(),
                        word.bounds.right/(float)frame.getWidth(),word.bounds.bottom/(float)frame.getHeight())));
                PortraitMatcher.Pixels pixels=null;
                if(rules.stage()==AccountSwitchRules.Stage.EXIT&&AccountSwitchVision.terminal(screen)){
                    int[] colors=new int[frame.getWidth()*frame.getHeight()];
                    frame.getPixels(colors,0,frame.getWidth(),0,0,frame.getWidth(),frame.getHeight());
                    pixels=new PortraitMatcher.Pixels(frame.getWidth(),frame.getHeight(),colors);
                }
                Action action=rules.next(screen,raw,captured,SystemClock.elapsedRealtime(),pixels);
                record(snapshot,action,true,SystemClock.elapsedRealtime()-captured);
                if(action.kind==Kind.STOP)throw new IllegalStateException(action.description);
                if(action.kind==Kind.DONE)return target.uid;
                if(guard.ready(screen,action,SystemClock.elapsedRealtime())){
                    check();
                    rules.beforeInput(action,SystemClock.elapsedRealtime());
                    if(action.kind==Kind.BACK)service.goBack();
                    else service.tap(action.x*frame.getWidth(),action.y*frame.getHeight(),frame.getWidth(),frame.getHeight());
                    rules.applied(action,SystemClock.elapsedRealtime());
                }
            }catch(Exception failure){
                saveFailure(frame,snapshot,captured,"multi-switch-failure",rules.stage().name(),currentUid,target.uid,failure);
                throw failure;
            }finally{frame.recycle();}
            Thread.sleep(250);
        }
    }
    @Override public DailyResult runDaily(MultiAccountPlan.Account account)throws Exception{
        check();publish("multi-running","正在执行账号 "+account.login+" 的每日；每号最多240普通波片，不补充/兑换",false);
        return new DailyEngine(service,reader).runAccount(account.profile());
    }
    @Override public void progress(MultiAccountProgress progress)throws Exception{
        checkpoint=progress;publish("multi-progress","账号进度已保存；仅 COMPLETED 表示本次每日核验通过",false);
    }
    private void inspect()throws Exception{
        Bitmap frame=service.capture();long captured=SystemClock.elapsedRealtime();
        try{
            ScreenReader.Snapshot snapshot=reader.read(frame);
            Action action=Action.waitFor("multi-inspect","只读检查：已启用"+plan.enabled().size()+"个账号；未切号、未执行每日、未写完成进度");
            saveFrame(frame,snapshot,action,captured,"multi-inspect","INSPECT",null,null,false,null);
            record(snapshot,action,false,SystemClock.elapsedRealtime()-captured);
        }finally{frame.recycle();}
    }
    private void check()throws InterruptedException{
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        if(checkpoint!=null)checkpoint.requireDay(now());
    }
    private void saveFailure(Bitmap frame,ScreenReader.Snapshot snapshot,long captured,String label,String phase,
                             String sourceUid,String targetUid,Exception failure){
        try{
            saveFrame(frame,snapshot,Action.stop(failure.toString()),captured,label,phase,sourceUid,targetUid,true,failure.toString());
        }catch(Exception evidenceFailure){failure.addSuppressed(evidenceFailure);}
    }
    private void saveFrame(Bitmap frame,ScreenReader.Snapshot snapshot,Action action,long captured,String label,String phase,
                           String sourceUid,String targetUid,boolean automate,String error)throws Exception{
        CaptureService capture=CaptureService.instance;
        if(capture==null)throw new IllegalStateException("屏幕采集已结束，未保存同帧证据");
        capture.saveEvidence(frame,label);
        JSONObject state=new JSONObject().put("task",traceTask.id).put("mode",automate?"automatic":"read_only")
                .put("scene",action.scene).put("action",action.kind.name()).put("description",action.description);
        if(snapshot!=null){
            JSONArray words=new JSONArray(),regions=new JSONArray();
            for(ScreenReader.Word word:snapshot.words){
                words.put(word.text);regions.put(new JSONObject().put("text",word.text).put("left",word.bounds.left)
                        .put("top",word.bounds.top).put("right",word.bounds.right).put("bottom",word.bounds.bottom));
            }
            state.put("ocr",words).put("ocr_regions",regions);
        }
        byte[] png=java.nio.file.Files.readAllBytes(new File(service.getFilesDir(),"evidence/"+label+".png").toPath());
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(png);StringBuilder hex=new StringBuilder();
        for(byte b:hash)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        state.put("evidence_png",label+".png").put("evidence_png_sha256",hex.toString())
                .put("image_width",frame.getWidth()).put("image_height",frame.getHeight()).put("timestamp",now())
                .put("captured_elapsed_ms",captured).put("phase",phase).put("ocr_available",snapshot!=null)
                .put("source_uid",sourceUid).put("target_uid",targetUid).put("error",error).put("multi_complete",false);
        // Separate from progress publication, which intentionally replaces the current task summary.
        GardenEngine.writeState(service,label+".json",state);
    }
    private void record(ScreenReader.Snapshot snapshot,Action action,boolean automate,long duration)throws Exception{
        JSONObject state=GardenEngine.persist(service,traceTask,snapshot,action,automate,duration,null);
        state.put("accounts",accounts());state.put("multi_complete",false);GardenEngine.writeState(service,traceTask.stateFile,state);
        service.message(traceTask.title+" · "+action.description);
    }
    private JSONArray accounts()throws Exception{
        Map<String,MultiAccountProgress.Entry> entries=checkpoint==null?java.util.Collections.emptyMap():checkpoint.entries();
        JSONArray out=new JSONArray();
        for(MultiAccountPlan.Account a:plan.accounts){
            MultiAccountProgress.Entry e=entries.get(a.uid);
            boolean matching=e!=null&&e.signature.equals(a.signature());
            out.put(new JSONObject().put("account",a.login).put("uid",a.uid).put("enabled",a.enabled).put("tacet_index",a.tacetIndex)
                    .put("status",matching?e.status.name():"PENDING").put("updated_at",matching?e.updated:0));
        }
        return out;
    }
    private void publish(String scene,String description,boolean complete)throws Exception{
        JSONObject state=new JSONObject().put("task",traceTask.id).put("mode","automatic")
                .put("scene",scene).put("description",description).put("timestamp",now()).put("multi_complete",complete)
                .put("game_day",checkpoint.day).put("accounts",accounts()).put("daily_detail_file",AutomationTask.DAILY.stateFile);
        GardenEngine.writeState(service,traceTask.stateFile,state);service.message(description);
    }
}
