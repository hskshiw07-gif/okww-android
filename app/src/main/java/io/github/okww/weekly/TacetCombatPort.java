package io.github.okww.weekly;

import android.graphics.Bitmap;
import android.os.SystemClock;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import static io.github.okww.weekly.CombatTouchLayout.Control.*;

/** Mobile execution of builtin axes; entry calibration is checked separately from dispatch wiring. */
final class TacetCombatPort implements PriorityBuiltinRotation.Port, AutoCloseable {
    static boolean wired(String name){return TeamBuiltinRotation.supported(name)||PriorityBuiltinRotation.CHARACTERS.contains(name);}
    static String missingCalibration(String name) {
        if(name==null)return "角色身份未知";
        return switch(name) {
            case "Phrolova" -> "弗洛洛长动作两态、强化普攻及消耗手机标记";
            case "Qingxiao" -> "清宵强化共鸣、重击一/二手机标记";
            default -> wired(name)?"":"未接入的内置角色";
        };
    }
    static String entryProblem(List<String> names) {
        java.util.List<String> missing=new java.util.ArrayList<>();
        for(String name:names) {
            String reason=missingCalibration(name);
            if(!reason.isEmpty())missing.add(name+": "+reason);
        }
        return String.join("；",missing);
    }
    static final class Ended extends Exception { }
    private final GameAccessibilityService service;
    private final ScreenReader reader;
    private final DailyRules policy;
    private final EchoFarmRules echo;
    private final AutomationTask task;
    private final List<String> names;
    private final MobileCombatReader identities;
    private final MobileSkillFeatures features;
    private final MobileTreasureReader treasures;
    private final CombatTouchLayout layout=CombatTouchLayout.forXiaomi13Pro();
    private final CombatClock clock=new CombatClock();
    private final CombatNormalAttack normalAttack=new CombatNormalAttack();
    private final CombatTeam team;
    private final BuiltinRotations rotations;
    private final EnumMap<TeamBuiltinRotation.Feature,Boolean> featureCache=new EnumMap<>(TeamBuiltinRotation.Feature.class);
    private GardenRules.Screen screen;
    private PortraitMatcher.Pixels pixels;
    private ScreenReader.Snapshot observedSnapshot;
    private long observedRequestedAt;
    private ActiveCharacterRecognizer.State identity;
    private CombatHud hud;
    private long capturedAt=-1, readAt=-1, battleStarted=SystemClock.elapsedRealtime();
    private String expected;
    private String confirmedOutro;
    private long lastDodge=-1,lastResonanceTap=-1;
    private boolean lastResonancePrompt;
    private long liberationHoldStarted=-1;
    private boolean liberationHoldReady;
    private boolean held,holdDesired;
    private CombatTouchLayout.Control heldControl=ATTACK;
    // Device dev81: OCR lost both arena labels for one switch; only a recent full arena frame may extend HUD trust.
    static final long ARENA_LABEL_GRACE_MS=8000;
    private long lastArenaFrame=-1;
    private long echoQuietAt=-1;
    private boolean combatFrame;
    private static final long OBSERVATION_INTERVAL_MS=250;
    private HiyukiHud.Phase hiyukiPhase=HiyukiHud.Phase.UNKNOWN;

    TacetCombatPort(GameAccessibilityService service,ScreenReader reader,DailyRules policy,List<String> names) throws Exception {
        this(service,reader,policy,null,names);
    }
    TacetCombatPort(GameAccessibilityService service,ScreenReader reader,EchoFarmRules echo,List<String> names) throws Exception {
        this(service,reader,null,echo,names);
    }
    private TacetCombatPort(GameAccessibilityService service,ScreenReader reader,DailyRules policy,EchoFarmRules echo,
                            List<String> names) throws Exception {
        this.service=service;this.reader=reader;this.policy=policy;this.echo=echo;this.names=names;
        task=echo==null?AutomationTask.DAILY:AutomationTask.ECHO_FARM;
        for(String name:names) {
            if(!wired(name))throw new IllegalStateException("该队伍还缺手机战斗状态标定："+name+"；未回退通用连点");
            if(!missingCalibration(name).isEmpty())throw new IllegalStateException(name+" 已接执行接口，仍缺 "+missingCalibration(name));
        }
        BuiltinCombatCatalog catalog;
        try(InputStreamReader input=new InputStreamReader(service.getAssets().open("builtin-combat.tsv"),StandardCharsets.UTF_8)){catalog=new BuiltinCombatCatalog(input);}
        rotations=new BuiltinRotations(catalog,names);
        team=new CombatTeam(catalog,names);identities=new MobileCombatReader(service.getAssets(),names);features=new MobileSkillFeatures(service.getAssets());
        treasures=echo==null?new MobileTreasureReader(service.getAssets()):null;
        service.combatCapture(true);
    }
    void round() throws Exception {
        try {
            normalAttack.stop();
            expected=null;checkCombat();expected=names.get(identity.active);
            rotations.run(expected,this);
        } catch(Ended ended) {throw ended;}
        catch(Exception failure) {
            if(pixels!=null&&observedSnapshot!=null)try {
                Bitmap observed=Bitmap.createBitmap(pixels.argb,pixels.width,pixels.height,Bitmap.Config.ARGB_8888);
                try {GardenEngine.saveFailure(service,echo==null?"daily-combat":"echo-combat",observed,observedSnapshot,observedRequestedAt,failure);}
                finally {observed.recycle();}
            }catch(Exception evidenceFailure){failure.addSuppressed(evidenceFailure);}
            throw failure;
        }
    }
    @Override public void refresh() throws Exception {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        if(realTimeMs()-battleStarted>6*60*1000)throw new IllegalStateException("单场战斗超过6分钟，停止并保存现场");
        long captureStarted=realTimeMs();Bitmap frame=service.capture(this::continueNormalAttack);capturedAt=realTimeMs();
        java.util.concurrent.CompletableFuture<PortraitMatcher.Match[]> avatars=null;
        try {
            if(frame.getWidth()!=2400||frame.getHeight()!=1080)throw new IllegalStateException("战斗头像目前只标定了2400×1080布局");
            int[] colors=new int[frame.getWidth()*frame.getHeight()];frame.getPixels(colors,0,frame.getWidth(),0,0,frame.getWidth(),frame.getHeight());
            PortraitMatcher.Pixels currentPixels=new PortraitMatcher.Pixels(frame.getWidth(),frame.getHeight(),colors);
            // OCR and portrait matching read the same immutable capture in parallel.
            // Only this worker commits identity after both finish and freshness is checked.
            avatars=identities.matchAsync(currentPixels);
            ScreenReader.Snapshot snapshot=reader.combat(frame,this::continueNormalAttack);long ocrEnded=realTimeMs();screen=GardenEngine.toScreen(snapshot,frame);
            if(screen.automationPanelVisible())throw new IllegalStateException("战斗帧含浮窗，未发送输入");
            if(echo==null) {
                policy.observeUid(screen);
                if(CombatHud.tacetVictoryBanner(screen)&&!policy.uidConflict()&&policy.confirmedUid()!=null
                        &&policy.confirmedUid().equals(AccountDailyConfig.detectUid(screen))&&realTimeMs()-capturedAt<=1500){
                    android.util.Log.i("WWCombat","tacet victory banner; release combat and reobserve reward UI");
                    throw new Ended();
                }
                if(screen.has("挑战成功|挑战失败|领取奖励|复苏|复活|补充波片|兑换波片"))throw new Ended();
            } else {
                if(uidConflict() || EchoFarmRules.blocked(screen))
                    throw new IllegalStateException("刷4C战斗遇到未知账号或阻塞弹窗，未点击");
            }
            boolean postWorld=echo==null&&TacetTreasureNavigation.postWorld(screen);
            boolean echoHud=echo!=null&&echo.hud(screen)&&ocrEnded-capturedAt<=1500;
            boolean bossLabel=echoHud&&echo.bossLabel(screen);
            boolean bossBar=echoHud&&EchoFarmRules.healthBar(currentPixels);
            boolean arena=echo==null?CombatHud.tacetActive(screen):bossLabel&&bossBar;
            boolean grace=!arena&&lastArenaFrame>=0&&capturedAt-lastArenaFrame<=ARENA_LABEL_GRACE_MS
                    &&(echo==null?CombatHud.tacetContinued(screen):bossLabel);
            if(echo!=null) {
                if(echoHud&&!bossLabel&&!bossBar) {
                    if(echoQuietAt<0)echoQuietAt=capturedAt;
                    if(echo.prompt(screen)!=null || capturedAt-echoQuietAt>=2000)throw new Ended();
                } else echoQuietAt=-1;
            }
            MobileCombatReader.Observation observed=echo==null
                    ?identities.observe(avatars.get(),screen,capturedAt,!postWorld)
                    :identities.observeVerified(avatars.get(),capturedAt,arena||grace);
            identity=observed.state;
            pixels=currentPixels;
            observedSnapshot=snapshot;observedRequestedAt=captureStarted;
            PortraitMatcher.Match marker=postWorld&&!grace&&identity!=null&&!policy.uidConflict()?treasures.find(pixels):null;
            if(echo==null&&policy.tacet.observeTreasure(screen,marker,capturedAt,realTimeMs())) {
                CaptureService.instance.saveEvidence(frame,"daily-treasure");
                android.util.Log.i("WWCombat","owned treasure handoff score="+marker.score+" xy="+marker.x+","+marker.y);
                throw new Ended();
            }
            hud=!uidConflict() && identity!=null && (arena||grace)
                    ? new CombatHud(pixels,screen,team.chars.get(identity.active).element,echo!=null||CombatHud.tacetContinued(screen)):null;
            readAt=realTimeMs();if(readAt-capturedAt>1500){identity=null;hud=null;}
            // Starting OCR is not a new verified observation; only a fully accepted result renews normal input.
            if(hud!=null)normalAttack.observed(capturedAt);
            HiyukiHud.Phase phase=hud!=null&&"Hiyuki".equals(names.get(identity.active))
                    ?features.hiyuki.phase(pixels):HiyukiHud.Phase.UNKNOWN;
            if(phase!=hiyukiPhase)android.util.Log.i("WWCombat","Hiyuki observed forte phase="+phase);
            hiyukiPhase=phase;
            if(hud!=null&&arena)lastArenaFrame=capturedAt;
            combatFrame=hud!=null;
            if(echo==null&&hud!=null&&grace)android.util.Log.i("WWCombat","arena labels unread; HP-line continuation "+(capturedAt-lastArenaFrame)+"ms after full arena frame");
            clock.observe(capturedAt,hud==null,identity!=null);featureCache.clear();
            // Intro/liberation animations hide HUD and freeze the active clock; they do not erase outro buffs.
            if(clock.observationGap()||uidConflict())team.forgetBuffTiming();
            held=service.heldActive();
            if (hud != null) {
                if(holdDesired&&heldControl==LIBERATION&&hud.skills.get(LIBERATION)==CombatHud.Skill.READY)
                    liberationHoldReady=true;
                held=service.renewHeldObservation(capturedAt);
                if (holdDesired && !held && expected != null && expected.equals(names.get(identity.active))) {
                    service.holdControl(heldControl,true,layout);held=true;
                }
            } else {
                normalAttack.suspend(realTimeMs());
                if(held){android.util.Log.i("WWTouch","suspend hold: HUD/identity lost");service.holdAttack(false,layout);held=false;}
            }
            GardenRules.Action action=GardenRules.Action.waitFor(echo==null?"daily-tacet-combat":"echo-fight",hud==null?"等待战斗HUD/在场身份恢复":
                    names.get(identity.active)+" "+hud.skills+"，协奏="+hud.concertoFull+"，回路="+hud.forteFull);
            GardenEngine.persist(service,task,snapshot,action,true,readAt-capturedAt,names,
                    identity==null?null:names.get(identity.active),observed,policy);
            android.util.Log.d("WWCombatTiming","capture="+(capturedAt-captureStarted)+" ocr="+(ocrEnded-capturedAt)
                    +" identity="+(readAt-ocrEnded)+" persist="+(realTimeMs()-readAt));
        } finally {if(avatars!=null)avatars.cancel(true);frame.recycle();}
    }
    @Override public void checkCombat() throws Exception {
        long end=realTimeMs()+5000;int fallenCandidate=-1;
        while(true) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            if(readAt<0||realTimeMs()-readAt>OBSERVATION_INTERVAL_MS||realTimeMs()-capturedAt>1200)refresh();
            if(hud!=null&&combatFrame) {
                if(expected!=null&&!expected.equals(names.get(identity.active))){
                    boolean dead=expectedDead();
                    if(dead&&fallenCandidate==identity.active){
                            if(echo!=null)throw new IllegalStateException("刷4C已有队员倒下，停止");
                            policy.tacet.confirmedPartyDeath(AccountDailyConfig.detectUid(screen));
                            android.util.Log.i("WWCombat","confirmed fallen "+expected+"; handing off to exit/heal recovery");
                            throw new Ended();
                    }
                    fallenCandidate=dead?identity.active:-1;
                    android.util.Log.d("WWCombat","unexpected switch expected="+expected+" active="+names.get(identity.active)+" emptyBar="+dead);
                    // The incoming portrait settles before the fallen member's HP bar.
                    if(realTimeMs()<end){Thread.sleep(60);refresh();continue;}
                    throw new IllegalStateException("战斗在场身份发生未确认变化，停止而非按旧角色输出");
                }
                normalAttack.resume(realTimeMs(),expected!=null&&expected.equals(names.get(identity.active)));
                return;
            }
            if(realTimeMs()>end)throw new IllegalStateException((echo==null?"无音区":"刷4C")+"战斗状态或在场角色连续5秒未确认");
            Thread.sleep(60);refresh();
        }
    }
    private String confirmedUid() { return echo==null?policy.confirmedUid():echo.uid; }
    private boolean uidConflict() {
        if(echo==null)return policy.uidConflict();
        String seen=AccountDailyConfig.detectUid(screen);
        return seen!=null&&!echo.uid.equals(seen);
    }
    private boolean expectedDead(){
        if(identity==null||expected==null)return false;
        for(int slot=0;slot<identity.standby.length;slot++)
            if(expected.equals(names.get(identity.standby[slot])))return PartyHealth.deadStandby(pixels,slot);
        return false;
    }
    @Override public boolean available(CombatTouchLayout.Control control) throws Exception {
        checkCombat();CombatHud.Skill state=hud.skills.get(control);
        if(state==null)throw new IllegalArgumentException("不是技能按钮");
        return state==CombatHud.Skill.READY;
    }
    @Override public boolean cast(CombatTouchLayout.Control control) throws Exception {
        return cast(control,false);
    }
    private boolean cast(CombatTouchLayout.Control control,boolean sendNormal) throws Exception {
        normalAttack.stop();
        if(!available(control))return false;
        boolean hiyukiR=control==LIBERATION&&"Hiyuki".equals(expected);
        HiyukiHud.Phase before=hiyukiR?requireHiyukiPhase():HiyukiHud.Phase.UNKNOWN;
        CombatSkillRetry retry=new CombatSkillRetry(realTimeMs());
        service.performGesture(layout.press(control,90,2400,1080));
        if(sendNormal)normalAttack.request();
        try {
        long until=realTimeMs()+(control==LIBERATION?7000:1800);boolean animation=false;int transformedFrames=0;
        do {
            Thread.sleep(60);refresh();
            if(hud==null) {animation=true;continue;}
            if(expected!=null&&!expected.equals(names.get(identity.active)))checkCombat();
            if(!hiyukiR&&CombatHud.castConfirmed(CombatHud.Skill.READY,hud.skills.get(control))) {
                android.util.Log.i("WWCombat","confirmed "+expected+" "+control+" animation="+animation);
                return true;
            }
            if(hiyukiR&&hiyukiPhase!=HiyukiHud.Phase.UNKNOWN&&hiyukiPhase!=before){
                if(++transformedFrames>=2){
                    android.util.Log.i("WWCombat","confirmed Hiyuki LIBERATION forte="+before+" -> "+hiyukiPhase);
                    return true;
                }
                continue; // Wait for the second transformed frame before any retry can consume R2.
            } else transformedFrames=0;
            CombatSkillRetry.Step next=retry.next(realTimeMs(),capturedAt,hud.skills.get(control)==CombatHud.Skill.READY,animation);
            if(next==CombatSkillRetry.Step.FINISHED)break;
            if(next==CombatSkillRetry.Step.TAP) {
                service.performGesture(layout.press(control,90,2400,1080));retry.sent(realTimeMs());
                android.util.Log.i("WWCombat","retry ignored "+expected+" "+control);
            }
        } while(realTimeMs()<until);
        android.util.Log.i("WWCombat","unconfirmed "+expected+" "+control);
        return false;
        } finally {normalAttack.stop();}
    }
    @Override public void normalClick() throws Exception {
        checkCombat();
        normalAttack.request();continueNormalAttack();
    }
    private void continueNormalAttack() throws Exception {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        long now=realTimeMs();
        boolean permitted=hud!=null&&identity!=null&&expected!=null&&!uidConflict()&&!holdDesired
                &&expected.equals(names.get(identity.active));
        if(!normalAttack.due(now,permitted))return;
        service.performGesture(layout.press(ATTACK,65,2400,1080));normalAttack.sent(now);
        android.util.Log.d("WWCombatTiming","normal tap frameAge="+(now-capturedAt));
    }
    @Override public void poll() throws Exception {checkCombat();continueNormalAttack();}
    @Override public void normalAttack(long durationMs) throws Exception {
        normalAttack(durationMs,80,false);
    }
    @Override public void heavyIfForte() throws Exception {if(forteFull())heavyAttack(600);}
    @Override public void heavyAttack(long durationMs) throws Exception {
        if(durationMs<1||durationMs>3500)throw new IllegalArgumentException("重击时长越界");
        try {holdAttack(true);pause(durationMs);}finally{holdAttack(false);}
    }
    @Override public void holdAttack(boolean down) throws Exception {
        hold(ATTACK,down);
    }
    @Override public void hold(CombatTouchLayout.Control control, boolean down) throws Exception {
        normalAttack.stop();
        if(down){
            layout.heldPoint(control);checkCombat();
            if(holdDesired) {
                if(heldControl!=control)throw new IllegalStateException("已有其它持续触点，须先释放");
                return;
            }
            if(control==LIBERATION) {
                liberationHoldStarted=realTimeMs();liberationHoldReady=hud.skills.get(LIBERATION)==CombatHud.Skill.READY;
            }
            service.holdControl(control,true,layout);heldControl=control;held=true;holdDesired=true;
        }
        else{holdDesired=false;service.holdAttack(false,layout);held=false;}
    }
    @Override public void pause(long durationMs) throws Exception {
        long end=realTimeMs()+durationMs;
        while(realTimeMs()<end){Thread.sleep(Math.max(1,Math.min(20,end-realTimeMs())));if(realTimeMs()-readAt>OBSERVATION_INTERVAL_MS||realTimeMs()-capturedAt>1200)refresh();continueNormalAttack();}
    }
    @Override public boolean concertoFull() throws Exception {checkCombat();return hud.concertoFull;}
    @Override public boolean forteFull() throws Exception {
        checkCombat();return switch(names.get(identity.active)){
            case "Galbrena" -> GalbrenaHud.full(pixels);
            case "Chisa" -> features.chisa.read(pixels)==ChisaHud.State.FULL;
            default -> hud.forteFull;
        };
    }
    @Override public boolean chisaForteConsumed()throws Exception{
        requireActiveCharacter("Chisa");long start=realTimeMs();var confirmed=new ChisaHud.PoweredConfirmation();
        do{
            checkCombat();ChisaHud.State state=features.chisa.read(pixels);
            if(confirmed.observe(state,capturedAt)){android.util.Log.i("WWCombat","confirmed Chisa forte -> powered attack");return true;}
            pause(60);refresh();
        }while(realTimeMs()-start<2500);
        return false;
    }
    @Override public void requireActiveCharacter(String character) throws Exception {
        checkCombat();
        if(!names.get(identity.active).equals(character))throw new IllegalStateException("角色轴与新鲜在场身份不符");
    }
    @Override public void requirePriorityCalibration(String character) throws Exception {
        requireActiveCharacter(character);
        String missing=missingCalibration(character);
        if(!missing.isEmpty())throw new IllegalStateException("角色已接线但仍缺手机标定："+missing);
        if(!character.equals("Hiyuki")&&!character.equals("Hsin"))signal(PriorityBuiltinRotation.Signal.FLYING);
    }
    @Override public boolean signal(PriorityBuiltinRotation.Signal value) throws Exception {
        checkCombat();
        return switch(value) {
            case FLYING -> flying();
            case HSIN_H1, HSIN_H2, HSIN_LIB1, HSIN_LIB2 -> {
                requireActiveCharacter("Hsin");
                String name=switch(value){case HSIN_H1 -> "hsin_h1";case HSIN_H2 -> "hsin_h2";
                    case HSIN_LIB1 -> "hsin_lib1";default -> "hsin_lib2";};
                yield features.priority.feature(pixels,name,value==PriorityBuiltinRotation.Signal.HSIN_H1
                        ||value==PriorityBuiltinRotation.Signal.HSIN_H2?ATTACK:LIBERATION);
            }
            case ENHANCED_E -> {
                requireActiveCharacter("Hsin");yield PrioritySkillHud.highlighted(pixels,RESONANCE);
            }
            case HIYUKI_HEAVY -> {
                requireActiveCharacter("Hiyuki");yield PrioritySkillHud.highlighted(pixels,ATTACK);
            }
            case HIYUKI_LEFT, HIYUKI_RIGHT -> {
                requireActiveCharacter("Hiyuki");yield features.priority.feature(pixels,
                        value==PriorityBuiltinRotation.Signal.HIYUKI_LEFT?"hiyuki_left":"hiyuki_right",
                        value==PriorityBuiltinRotation.Signal.HIYUKI_LEFT?ATTACK:DODGE);
            }
            case LONG_ACTION, LONG_ACTION_2 -> {
                requireActiveCharacter("Hiyuki");
                if(!features.priority.actionIndicator(pixels))yield false;
                HiyukiHud.Phase phase=requireHiyukiPhase();
                yield features.priority.actionIndicator(pixels)&&(value==PriorityBuiltinRotation.Signal.LONG_ACTION_2)
                        ==(phase==HiyukiHud.Phase.LIBERATED);
            }
            case GALBRENA_RES -> {
                requireActiveCharacter("Galbrena");yield galbrenaRes();
            }
            default -> throw new IllegalStateException("手机专属状态尚未标定："+names.get(identity.active)+" / "+value);
        };
    }
    @Override public String outroSource(){return hasIntro()?confirmedOutro:null;}
    @Override public boolean ownBuff() {
        if(identity==null)throw new IllegalStateException("增益读取缺少在场身份");
        return team.hasBuff(identity.active,activeTimeMs());
    }
    @Override public boolean chisaDps(){return false;} // Original default is support; no custom DPS configuration is imported.
    @Override public boolean useLiberation(){return true;}
    @Override public boolean resonance(boolean sendNormal,long timeout) throws Exception {return castObserved(RESONANCE,sendNormal,timeout);}
    @Override public boolean echo(long timeout) throws Exception {return castObserved(ECHO,false,timeout);}
    @Override public boolean liberation(double concertoLimit,boolean sendNormal,long waitIfCdReady) throws Exception {
        if(concertoLimit>=0)throw new IllegalStateException("当前九人内置轴未要求协奏比例阈值，不猜测比例");
        if(waitIfCdReady<0||waitIfCdReady>3500)throw new IllegalArgumentException("解放等待越界");
        long start=realTimeMs();
        while(!available(LIBERATION)&&realTimeMs()-start<waitIfCdReady)pause(50);
        return castObserved(LIBERATION,sendNormal,0);
    }
    private boolean castObserved(CombatTouchLayout.Control control,boolean sendNormal,long timeout) throws Exception {
        if(timeout<0||timeout>3000)throw new IllegalArgumentException("技能尝试时限越界");
        long start=realTimeMs();
        do {
            if(available(control))return cast(control,sendNormal);
            if(timeout==0)return false;
            if(sendNormal)normalClick();
            pause(50);
        }while(realTimeMs()-start<timeout);
        return false;
    }
    @Override public long cooldownMs(CombatTouchLayout.Control control) throws Exception {
        checkCombat();
        if(hud.skills.get(control)==CombatHud.Skill.READY)return 0;
        // PC get_cd() returns zero without a cooldown number. A dim R can still lack energy.
        // Keep available() separate: this only permits Hiyuki's normal charging phase.
        if(control==LIBERATION&&hud.skills.get(control)==CombatHud.Skill.DIM&&"Hiyuki".equals(names.get(identity.active)))return 0;
        if(hud.skills.get(control)!=CombatHud.Skill.COOLDOWN)throw new IllegalStateException("技能变暗但冷却读数未知");
        CombatGesture.Contact c=layout.point(control);float x=c.x,y=c.y;
        GardenRules.Text number=screen.one("[0-9]{1,2}(?:[.,][0-9])?[sS]?",x-40f/2400,y-32f/1080,x+40f/2400,y+32f/1080);
        if(number==null)throw new IllegalStateException("冷却读数已丢失");
        return Math.round(Double.parseDouble(number.value.replace(',','.').replaceAll("[sS]$",""))*1000);
    }
    @Override public void normalAttack(long duration,long interval,boolean interruptOnResonance) throws Exception {
        if(duration<0||duration>20000||interval<0||interval>1000)throw new IllegalArgumentException("普攻节拍越界");
        long activeStart=activeTimeMs(),wallStart=realTimeMs();
        try {
        while(clock.attackPending(activeStart,wallStart,realTimeMs(),duration)) {
            checkCombat();
            if(interruptOnResonance&&available(RESONANCE))return;
            if(!held){normalAttack.request(interval);continueNormalAttack();}pause(Math.max(20,interval));
        }
        checkCombat();
        } finally {normalAttack.stop();}
    }
    @Override public void normalTap() throws Exception {normalClick();}
    @Override public void normalClick(long interval) throws Exception {
        if(interval<0||interval>1000)throw new IllegalArgumentException("普攻间隔越界");
        checkCombat();normalAttack.request(interval);continueNormalAttack();pause(Math.max(20,interval));
    }
    @Override public void waitDown(boolean sendNormal) throws Exception {waitStanding(2500);}
    @Override public void waitPartyHudOptional(long timeout) throws Exception {
        if(timeout<0||timeout>4000)throw new IllegalArgumentException("动画等待越界");
        long start=realTimeMs();
        do {refresh();if(hud!=null)return;}while(realTimeMs()-start<timeout);
        // Optional return never manufactures a valid HUD; the next input still calls checkCombat().
    }
    @Override public void repeatResonance(long duration,long interval) throws Exception {
        if(duration<0||duration>10000||interval<0||interval>1000)throw new IllegalArgumentException("共鸣循环越界");
        long start=realTimeMs();
        while(realTimeMs()-start<duration){cast(RESONANCE);pause(Math.max(50,interval));}
    }
    @Override public boolean mouseForteFull() throws Exception {
        requireActiveCharacter("Hiyuki");return PrioritySkillHud.highlighted(pixels,ATTACK);
    }
    @Override public boolean heavyMouseForte() throws Exception {
        if(!mouseForteFull())return false;
        long start=realTimeMs(),gone=-1;
        try{
            holdAttack(true);
            do{
                refresh();checkCombat();
                if(mouseForteFull())gone=-1;
                else if(gone<0)gone=capturedAt;
                else if(capturedAt-gone>=250)return true;
                pause(50);
            }while(realTimeMs()-start<2000);
            return false;
        }finally{holdAttack(false);}
    }
    @Override public void defaultHeavyAttack() throws Exception {heavyAttack(600);}
    @Override public void dodgeClick(long interval) throws Exception {
        if(interval<0||interval>3000)throw new IllegalArgumentException("闪避节拍越界");
        checkCombat();long now=realTimeMs();
        if(lastDodge>=0&&now-lastDodge<interval)return;
        normalAttack.stop();
        service.performGesture(layout.press(DODGE,90,2400,1080));lastDodge=now;
    }
    @Override public boolean enhancedResonance(long timeout,long minimumAnimation) throws Exception {
        if(minimumAnimation<0||minimumAnimation>3000)throw new IllegalArgumentException("强化共鸣动画时限越界");
        long start=realTimeMs();boolean confirmed=castObserved(RESONANCE,true,timeout);
        if(confirmed)pause(Math.max(0,minimumAnimation-(realTimeMs()-start)));
        return confirmed;
    }
    @Override public void tapResonance() throws Exception {
        normalAttack.stop();
        checkCombat();lastResonancePrompt="Hsin".equals(names.get(identity.active))&&PrioritySkillHud.highlighted(pixels,RESONANCE);
        service.performGesture(layout.press(RESONANCE,90,2400,1080));lastResonanceTap=capturedAt;
    }
    @Override public void recordResonanceConfirmed() throws Exception {
        checkCombat();
        boolean promptConsumed=lastResonancePrompt&&"Hsin".equals(names.get(identity.active))&&!PrioritySkillHud.highlighted(pixels,RESONANCE);
        if(lastResonanceTap<0||capturedAt<=lastResonanceTap||hud.skills.get(RESONANCE)!=CombatHud.Skill.COOLDOWN&&!promptConsumed)
            throw new IllegalStateException("缺少共鸣输入后的新鲜冷却证据");
        lastResonanceTap=-1;lastResonancePrompt=false;
    }
    @Override public boolean confirmHeldLiberation(long started,long timeout) throws Exception {
        if(timeout<0||timeout>7000||started>realTimeMs())throw new IllegalArgumentException("持续解放核验时限越界");
        if(liberationHoldStarted<started||!liberationHoldReady)return false;
        long until=realTimeMs()+timeout;int normalFrames=0;
        do {
            refresh();
            if(hud!=null&&identity!=null&&names.get(identity.active).equals(expected)&&capturedAt>started
                    &&("Hiyuki".equals(expected)?hiyukiPhase==HiyukiHud.Phase.NORMAL
                        :hud.skills.get(LIBERATION)==CombatHud.Skill.COOLDOWN)) {
                if("Hiyuki".equals(expected)&&++normalFrames<2)continue;
                android.util.Log.i("WWCombat","confirmed held LIBERATION "+expected);
                liberationHoldStarted=-1;liberationHoldReady=false;return true;
            }else normalFrames=0;
            if(realTimeMs()<until)pause(50);
        }while(realTimeMs()<until);
        return false;
    }
    private HiyukiHud.Phase requireHiyukiPhase()throws Exception{
        long until=realTimeMs()+2000;
        while(hiyukiPhase==HiyukiHud.Phase.UNKNOWN&&realTimeMs()<until){refresh();checkCombat();}
        if(hiyukiPhase==HiyukiHud.Phase.UNKNOWN)throw new IllegalStateException("绯雪回路形态连续未确认，未按旧阶段输出");
        return hiyukiPhase;
    }
    private boolean flying() throws Exception {
        checkCombat();
        // Same PC rule: flying() is false when the equipped utility is not the levitator.
        if(features.knownNonLevitator(pixels))return false;
        throw new IllegalStateException("当前工具不是已标定的非控物工具，未猜测空中状态");
    }
    private boolean galbrenaRes() throws Exception {checkCombat();return features.normalTargetIndicator(pixels);}
    @Override public boolean fastPerform() throws Exception {
        checkCombat();
        for(int priority:rotations.priorities(names.get(identity.active),false,activeTimeMs()))if(priority>=CombatTeam.MUST)return true;
        return false;
    }
    @Override public void dodge(long duration) throws Exception {
        normalAttack.stop();
        if(duration<1||duration>1000)throw new IllegalArgumentException("闪避时长越界");
        long end=realTimeMs()+duration;
        do {checkCombat();service.performGesture(layout.press(DODGE,90,2400,1080));pause(100);}while(realTimeMs()<end);
    }
    @Override public void shorekeeperAutoDodge(){throw new IllegalStateException("控物工具延奏闪避尚未标定");}
    @Override public long activeTimeMs(){return clock.activeTimeMs();}
    @Override public long realTimeMs(){return SystemClock.elapsedRealtime();}
    @Override public boolean hasIntro(){return identity!=null&&team.intro[identity.active];}
    @Override public boolean allTeammateBuffs(){return identity!=null&&team.allTeammateBuffs(identity.active,activeTimeMs());}
    @Override public boolean hasSubDpsTeammate(){for(int i=0;i<3;i++)if(i!=identity.active&&team.chars.get(i).role==BuiltinCombatCatalog.Role.SUB_DPS)return true;return false;}
    @Override public void markSupportBuff() throws Exception {checkCombat();team.supportSkillConfirmed(identity.active,activeTimeMs());}
    @Override public boolean feature(TeamBuiltinRotation.Feature feature) throws Exception {
        checkCombat();if(!names.get(identity.active).equals("Aemeath"))throw new IllegalStateException("角色特征与在场身份不符");
        if(!featureCache.containsKey(feature))featureCache.put(feature,features.aemeath(pixels,feature));
        return featureCache.get(feature);
    }
    @Override public boolean longAction() throws Exception {
        normalAttack.stop();
        long deadline=realTimeMs()+2000;
        do {
            checkCombat();
            if(features.normalTargetIndicator(pixels))return false;
            pause(100);
        } while(realTimeMs()<deadline);
        throw new IllegalStateException("变形/长动作标记未确认，保留战斗现场，不用墙钟假定动作结束");
    }
    @Override public boolean breakAction() throws Exception {
        checkCombat();GardenRules.Text text=screen.one("震谐响应|谐度破坏",.60f,.35f,.88f,.72f);
        if(text==null)return false;
        normalAttack.stop();service.tap(text.x()*2400,text.y()*1080,2400,1080);refresh();return true;
    }
    @Override public void cycleSleep(long started) throws Exception {if(!breakAction())pause(Math.max(0,100-(realTimeMs()-started)));}
    @Override public boolean partyHudVisible() throws Exception {
        if(readAt<0||realTimeMs()-readAt>OBSERVATION_INTERVAL_MS||realTimeMs()-capturedAt>1200)refresh();
        return hud!=null;
    }
    @Override public void waitPartyHud(long timeoutMs) throws Exception {
        long end=realTimeMs()+timeoutMs;do {refresh();if(hud!=null)return;}while(realTimeMs()<end);
        throw new IllegalStateException("等待技能动画后HUD恢复超时");
    }
    @Override public void waitIntro(long timeoutMs) throws Exception {if(hasIntro())waitPartyHud(timeoutMs);}
    @Override public void waitStanding(long timeoutMs) {throw new IllegalStateException("当前手机角色尚未标定落地状态");}
    @Override public void jump() throws Exception {normalAttack.stop();checkCombat();service.performGesture(layout.press(JUMP,90,2400,1080));}
    @Override public void switchNext() throws Exception {
        normalAttack.stop();
        long started=realTimeMs();
        checkCombat();int source=identity.active;boolean con=hud.concertoFull;
        int target=team.choose(source,con,rotations.priorities(names.get(source),con,activeTimeMs()),activeTimeMs());
        if(source==target)return;
        String uid=confirmedUid();
        if(uid==null)throw new IllegalStateException("切人账号尚未确认");
        CombatSwitch switching=new CombatSwitch(source,target,started);
        while(true) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            long now=realTimeMs();
            boolean sameUid=!uidConflict()&&uid.equals(AccountDailyConfig.detectUid(screen));
            android.util.Log.d("WWCombat","switch observed source="+names.get(source)+" target="+names.get(target)
                    +" attempts="+switching.attempts()+" active="+(identity==null?"unknown":names.get(identity.active))
                    +" hud="+(hud!=null)+" sameUid="+sameUid+" frameAge="+(now-capturedAt));
            CombatSwitch.Step step=switching.next(now,capturedAt,hud!=null,sameUid,identity);
            if(step.kind==CombatSwitch.Kind.CONFIRMED) {
                confirmedOutro=con?names.get(source):null;
                team.switchConfirmed(source,target,con,activeTimeMs());expected=names.get(target);
                android.util.Log.i("WWCombat","switch confirmed "+names.get(source)+" -> "+expected+" intro="+con);return;
            }
            if(step.kind==CombatSwitch.Kind.TAP) {
                android.util.Log.i("WWCombat","switch tap source="+names.get(source)+" target="+names.get(target)
                        +" attempt="+switching.attempts()+" xy="+(step.x*2400)+","+(step.y*1080));
                service.tap(step.x*2400,step.y*1080,2400,1080);
            }
            Thread.sleep(100);refresh();
        }
    }
    @Override public void close() throws Exception {normalAttack.stop();try{holdAttack(false);}finally{service.combatCapture(false);}}
}
