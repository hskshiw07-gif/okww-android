package io.github.okww.weekly;

import android.graphics.Bitmap;
import android.os.SystemClock;
import org.json.*;
import java.util.*;
import static io.github.okww.weekly.GardenRules.*;

final class RedeemEngine implements RedeemBatch.Port {
    private final GameAccessibilityService service;
    private final ScreenReader reader;
    private final AutomationTask task;
    private final MultiAccountEngine navigator;
    private RedeemLedger ledger;
    private String currentUid;
    private final boolean development;
    private int publicCount;
    private long fetchedAt;
    private String testCode;
    RedeemEngine(GameAccessibilityService service,ScreenReader reader,AutomationTask task)throws Exception{
        this.service=service;this.reader=reader;this.task=task;navigator=new MultiAccountEngine(service,reader,task);
        development=TestEnvironment.replay(service.getSharedPreferences("weekly",0),task);
    }
    void run(boolean automate)throws Exception{
        if(!automate){
            Bitmap frame=service.capture();long captured=SystemClock.elapsedRealtime();
            try{ScreenReader.Snapshot snapshot=reader.read(frame);Action action=Action.waitFor("redeem-inspect","只读采样：未填码、未兑换、未切号");
                evidence(frame,snapshot,action,captured,"redeem-inspect",false,null);record(snapshot,action,false,captured);
            }finally{frame.recycle();}return;
        }
        TestEnvironment.requireSupported(service.getSharedPreferences("weekly",0),true,task);
        RedeemCodes catalog=RedeemCodes.parse(RedeemStore.codes(service).read());
        ledger=new RedeemLedger(development?RedeemStore.developmentLedger(service):RedeemStore.ledger(service));
        if(development){
            RedeemDevelopment.requireNoPending(new RedeemLedger(RedeemStore.ledger(service)),ledger);
            publishStage("redeem-fetch-public","开发验收：重新获取公开码；真实码不会自动启用或重复兑换");
            String original=catalog.save();List<RedeemCodes.Code> remote=RedeemFeed.fetch();check();
            fetchedAt=System.currentTimeMillis();catalog.merge(remote,fetchedAt);RedeemStore.saveCodes(service,original,catalog);
            publicCount=remote.size();testCode=RedeemDevelopment.testCode(fetchedAt,catalog,ledger);
            publishStage("redeem-replay-start","已获取"+publicCount+"个公开码；本轮合成测试码 "+testCode+"，不代表有效礼包或奖励");
            restartDevelopment();
        }
        List<String> codes=development?List.of(testCode):catalog.selected(System.currentTimeMillis());
        boolean multi=task==AutomationTask.MULTI_REDEEM;
        MultiAccountPlan plan=multi?MultiAccountPlan.parse(MultiAccountFiles.plan(service).read()):new MultiAccountPlan(List.of());
        boolean mail=RedeemBatch.run(multi,plan,codes,ledger,this);
        JSONObject state=new JSONObject().put("task",task.id).put("timestamp",System.currentTimeMillis())
                .put("scene",mail?"redeem-finished":"redeem-mail-unverified").put("mail_verified",mail)
                .put("description",mail?(development?"开发测试流程结束：已获取公开码、提交合成测试码并核验邮件；测试结果不代表兑换奖励":"本轮兑换结果与邮件检查已结束；逐码状态见兑换记录"):"兑换结果已保存，邮件未核验；未继续切号")
                .put("redemptions",records());
        developmentState(state);
        GardenEngine.writeState(service,task.stateFile,state);service.message(state.getString("description"));
    }
    @Override public String current()throws Exception{
        currentUid=navigator.identifyCurrent();
        if(development)new RedeemLedger(RedeemStore.ledger(service)).requireNoPending(currentUid);
        return currentUid;
    }
    private void developmentState(JSONObject state)throws Exception{
        state.put("development_replay",development).put("public_code_count",publicCount).put("public_fetched_at",fetchedAt)
                .put("synthetic_test_code",testCode).put("ledger_scope",development?"development":"real").put("input_method","game-paste");
    }
    private void publishStage(String scene,String description)throws Exception{
        JSONObject state=new JSONObject().put("task",task.id).put("timestamp",System.currentTimeMillis())
                .put("scene",scene).put("description",description).put("mail_verified",false);
        developmentState(state);GardenEngine.writeState(service,task.stateFile,state);service.message(description);
    }
    private void restartDevelopment()throws Exception{
        GardenRunGuard guard=new GardenRunGuard(SystemClock.elapsedRealtime());DailyRules login=new DailyRules();
        while(true){
            check();Bitmap frame=service.capture();long captured=SystemClock.elapsedRealtime();
            try{
                ScreenReader.Snapshot snapshot=readRedeem(frame,true);Screen screen=GardenEngine.toScreen(snapshot,frame);
                Action action=RedeemDevelopment.restart(screen);
                if(action.kind==Kind.WAIT&&!screen.automationPanelVisible()){
                    Action connect=login.next(screen);
                    if(connect.scene.equals("game-connect")||connect.scene.equals("game-loading"))action=connect;
                }
                record(snapshot,action,true,captured);
                if(action.kind==Kind.DONE)return;
                if(action.kind==Kind.STOP)throw new IllegalStateException(action.description);
                if(guard.ready(screen,action,SystemClock.elapsedRealtime())){
                    check();if(SystemClock.elapsedRealtime()-captured>3500)throw new IllegalStateException("开发重跑准备帧已过期");
                    if(action.kind==Kind.BACK)service.goBack();else tap(action,frame);login.applied(action);
                }
            }finally{frame.recycle();}
            Thread.sleep(200);
        }
    }
    private ScreenReader.Snapshot readRedeem(Bitmap frame,boolean mail)throws Exception{
        ScreenReader.Snapshot snapshot=DailyEngine.readSnapshot(reader,frame,false);
        if(mail&&frame.getWidth()==2400&&frame.getHeight()==1080){
            reader.enlarge(snapshot,frame,105,35,240,60);
            if(DailyRewards.title(GardenEngine.toScreen(snapshot,frame),"邮件"))reader.enlarge(snapshot,frame,850,170,750,85);
        }
        return snapshot;
    }
    @Override public void checkWaveplateLedger(String uid)throws Exception{navigator.checkLedger(uid);}
    @Override public String switchTo(String source,MultiAccountPlan.Account target)throws Exception{return currentUid=navigator.switchAccount(source,target);}
    @Override public boolean redeem(String uid,List<String> codes,RedeemLedger ledger)throws Exception{
        currentUid=uid;
        for(String code:codes){
            check();RedeemLedger.Entry prior=ledger.get(uid,code);if(prior!=null&&prior.terminal())continue;
            RedeemRules rules=new RedeemRules(uid,code,SystemClock.elapsedRealtime());GardenRunGuard guard=new GardenRunGuard(SystemClock.elapsedRealtime());
            boolean saved=false;
            while(true){
                check();Bitmap frame=service.capture();long captured=SystemClock.elapsedRealtime();ScreenReader.Snapshot snapshot=null;
                try{
                    snapshot=DailyEngine.readSnapshot(reader,frame,false);Screen screen=GardenEngine.toScreen(snapshot,frame);
                    PortraitMatcher.Pixels pixels=pixelsIfKnown(frame,screen);
                    Text settings=RedeemVision.settings(screen,pixels),paste=RedeemVision.paste(screen,pixels);
                    Action action=rules.next(screen,labels(snapshot),captured,SystemClock.elapsedRealtime(),settings,paste);
                    if(rules.result()!=null&&!saved){ledger.result(uid,code,rules.result(),System.currentTimeMillis(),(development?"开发合成测试码，不代表奖励；":"")+"已连续两帧核验游戏结果："+rules.result());saved=true;}
                    record(snapshot,action,true,captured);
                    if(action.kind==Kind.STOP)throw new IllegalStateException(action.description);
                    if(action.kind==Kind.DONE){if(rules.result()==RedeemLedger.Status.RETRYABLE)throw new IllegalStateException("兑换过于频繁或网络异常，已记录可重试，停止本轮");break;}
                    if(guard.ready(screen,action,SystemClock.elapsedRealtime())){
                        check();rules.beforeInput(action,SystemClock.elapsedRealtime());
                        if(action.scene.equals("redeem-submit")&&service.keyboardVisible())throw new IllegalStateException("键盘仍显示，未提交兑换");
                        if(action.scene.equals("redeem-submit"))ledger.submit(uid,code,System.currentTimeMillis());
                        rules.beforeInput(action,SystemClock.elapsedRealtime());
                        if(action.scene.equals("redeem-fill")){
                            service.pasteRedeemCode(code,action.x*frame.getWidth(),action.y*frame.getHeight(),frame.getWidth(),frame.getHeight(),captured);
                        }else if(action.scene.equals("redeem-settings-scroll"))
                            service.performGesture(new CombatGesture(frame.getWidth(),frame.getHeight(),
                                    new CombatGesture.Contact(.50f,.82f,.50f,.30f,0,600)));
                        else if(action.kind==Kind.BACK)service.goBack();else tap(action,frame);
                        rules.applied(action,SystemClock.elapsedRealtime());
                    }
                }catch(Exception failure){saveFailure(frame,snapshot,captured,code,failure);throw failure;}
                finally{frame.recycle();}
                Thread.sleep(150);
            }
        }
        return claimMail(uid);
    }
    private boolean claimMail(String uid)throws Exception{
        RedeemMailRules rules=new RedeemMailRules();GardenRunGuard guard=new GardenRunGuard(SystemClock.elapsedRealtime());long started=SystemClock.elapsedRealtime();
        while(true){
            check();Bitmap frame=service.capture();long captured=SystemClock.elapsedRealtime();ScreenReader.Snapshot snapshot=null;
            try{
                snapshot=readRedeem(frame,true);Screen screen=GardenEngine.toScreen(snapshot,frame);
                if(SystemClock.elapsedRealtime()-started>90000)throw new IllegalStateException("兑换邮件检查超时，未记为已领取");
                String seen=AccountDailyConfig.detectUid(screen);if(seen!=null&&!seen.equals(uid))throw new IllegalStateException("兑换邮件UID变化");
                if(SystemClock.elapsedRealtime()-captured>3500)throw new IllegalStateException("兑换邮件帧已过期");
                if(screen.automationPanelVisible())continue;
                Action action=rules.next(screen);record(snapshot,action,true,captured);
                if(action.kind==Kind.STOP)throw new IllegalStateException(action.description);
                if(action.kind==Kind.DONE)return rules.verified();
                if(guard.ready(screen,action,SystemClock.elapsedRealtime())){
                    check();if(SystemClock.elapsedRealtime()-captured>3500)throw new IllegalStateException("兑换邮件输入帧过期");
                    if(action.kind==Kind.BACK)service.goBack();else tap(action,frame);rules.applied(action);
                }
            }catch(Exception failure){saveFailure(frame,snapshot,captured,"MAIL",failure);throw failure;}
            finally{frame.recycle();}
            Thread.sleep(200);
        }
    }
    private void tap(Action action,Bitmap frame)throws Exception{service.tap(action.x*frame.getWidth(),action.y*frame.getHeight(),frame.getWidth(),frame.getHeight());}
    private void check()throws InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException();}
    private PortraitMatcher.Pixels pixelsIfKnown(Bitmap frame,Screen screen){
        if(!AccountSwitchVision.terminal(screen)&&!RedeemRules.dialog(screen))return null;
        int[] pixels=new int[frame.getWidth()*frame.getHeight()];frame.getPixels(pixels,0,frame.getWidth(),0,0,frame.getWidth(),frame.getHeight());
        return new PortraitMatcher.Pixels(frame.getWidth(),frame.getHeight(),pixels);
    }
    private List<AccountSwitchRules.Label> labels(ScreenReader.Snapshot snapshot){
        List<AccountSwitchRules.Label> result=new ArrayList<>();for(ScreenReader.Word word:snapshot.words)result.add(new AccountSwitchRules.Label(word.text,
                new Text(word.text,word.bounds.left/(float)snapshot.width,word.bounds.top/(float)snapshot.height,word.bounds.right/(float)snapshot.width,word.bounds.bottom/(float)snapshot.height)));return result;
    }
    private JSONArray records()throws Exception{
        JSONArray out=new JSONArray();if(ledger!=null)for(RedeemLedger.Entry entry:ledger.entries())out.put(new JSONObject().put("uid",entry.uid)
                .put("code",entry.code).put("status",entry.status.name()).put("detail",entry.detail).put("updated_at",entry.at));return out;
    }
    private void record(ScreenReader.Snapshot snapshot,Action action,boolean automate,long captured)throws Exception{
        JSONObject state=GardenEngine.persist(service,task,snapshot,action,automate,SystemClock.elapsedRealtime()-captured,null);
        developmentState(state);
        state.put("uid",currentUid).put("redemptions",records()).put("mail_verified",false);GardenEngine.writeState(service,task.stateFile,state);
        service.message(task.title+" · "+action.description);
    }
    private void saveFailure(Bitmap frame,ScreenReader.Snapshot snapshot,long captured,String code,Exception failure){
        try{evidence(frame,snapshot,Action.stop(failure.toString()),captured,"redeem-failure",true,code);}catch(Exception write){failure.addSuppressed(write);}
    }
    private void evidence(Bitmap frame,ScreenReader.Snapshot snapshot,Action action,long captured,String label,boolean automate,String code)throws Exception{
        CaptureService capture=CaptureService.instance;if(capture==null)throw new IllegalStateException("采集已停止，缺少兑换证据");capture.saveEvidence(frame,label);
        JSONObject state=new JSONObject().put("task",task.id).put("mode",automate?"automatic":"read_only").put("uid",currentUid).put("code",code)
                .put("scene",action.scene).put("description",action.description).put("captured_elapsed_ms",captured)
                .put("image_width",frame.getWidth()).put("image_height",frame.getHeight()).put("timestamp",System.currentTimeMillis());
        JSONArray words=new JSONArray();if(snapshot!=null)for(ScreenReader.Word word:snapshot.words)words.put(new JSONObject().put("text",word.text)
                .put("left",word.bounds.left).put("top",word.bounds.top).put("right",word.bounds.right).put("bottom",word.bounds.bottom));
        byte[] png=java.nio.file.Files.readAllBytes(new java.io.File(service.getFilesDir(),"evidence/"+label+".png").toPath());
        StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(png))hash.append(String.format(Locale.ROOT,"%02x",b&255));
        state.put("ocr_regions",words).put("ocr_available",snapshot!=null).put("evidence_png",label+".png").put("evidence_png_sha256",hash.toString());
        GardenEngine.writeState(service,label+".json",state);
    }
}
