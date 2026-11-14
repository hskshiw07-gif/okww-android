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
            case "Hsin" -> "心重击一/二、解放一/二、强化共鸣手机标记";
            case "Hiyuki" -> "绯雪重击/左右提示、长动作两态、强化普攻及消耗手机标记";
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
    private final List<String> names;
    private final MobileCombatReader identities;
    private final MobileSkillFeatures features;
    private final MobileTreasureReader treasures;
    private final CombatTouchLayout layout=CombatTouchLayout.forXiaomi13Pro();
    private final CombatClock clock=new CombatClock();
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
    private long liberationHoldStarted=-1;
    private boolean liberationHoldReady;
    private boolean held,holdDesired;
    private CombatTouchLayout.Control heldControl=ATTACK;
    private int treasureFrames;
    private long lastTreasureFrame=-1;
    // Device dev81: OCR lost both arena labels for one switch; only a recent full arena frame may extend HUD trust.
    static final long ARENA_LABEL_GRACE_MS=8000;
    private long lastArenaFrame=-1;
    private boolean combatFrame;

    TacetCombatPort(GameAccessibilityService service,ScreenReader reader,DailyRules policy,List<String> names) throws Exception {
        this.service=service;this.reader=reader;this.policy=policy;this.names=names;
        for(String name:names) {
            if(!wired(name))throw new IllegalStateException("该队伍还缺手机战斗状态标定："+name+"；未回退通用连点");
            if(!missingCalibration(name).isEmpty())throw new IllegalStateException(name+" 已接执行接口，仍缺 "+missingCalibration(name));
        }
        BuiltinCombatCatalog catalog;
        try(InputStreamReader input=new InputStreamReader(service.getAssets().open("builtin-combat.tsv"),StandardCharsets.UTF_8)){catalog=new BuiltinCombatCatalog(input);}
        rotations=new BuiltinRotations(catalog,names);
        team=new CombatTeam(catalog,names);identities=new MobileCombatReader(service.getAssets(),names);features=new MobileSkillFeatures(service.getAssets());
        treasures=new MobileTreasureReader(service.getAssets());
        service.combatCapture(true);
    }
    void round() throws Exception {
        try {
            expected=null;checkCombat();expected=names.get(identity.active);
            rotations.run(expected,this);
        } catch(Ended ended) {throw ended;}
        catch(Exception failure) {
            if(pixels!=null&&observedSnapshot!=null)try {
                Bitmap observed=Bitmap.createBitmap(pixels.argb,pixels.width,pixels.height,Bitmap.Config.ARGB_8888);
                try {GardenEngine.saveFailure(service,"daily-combat",observed,observedSnapshot,observedRequestedAt,failure);}
                finally {observed.recycle();}
            }catch(Exception evidenceFailure){failure.addSuppressed(evidenceFailure);}
            throw failure;
        }
    }
    @Override public void refresh() throws Exception {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        if(realTimeMs()-battleStarted>6*60*1000)throw new IllegalStateException("单场战斗超过6分钟，停止并保存现场");
        long captureStarted=realTimeMs();Bitmap frame=service.capture();capturedAt=realTimeMs();
        try {
            ScreenReader.Snapshot snapshot=reader.combat(frame);long ocrEnded=realTimeMs();screen=GardenEngine.toScreen(snapshot,frame);
            if(screen.automationPanelVisible())throw new IllegalStateException("战斗帧含浮窗，未发送输入");
            policy.observeUid(screen);
            if(screen.has("挑战成功|挑战失败|领取奖励|复苏|复活|补充波片|兑换波片"))throw new Ended();
            boolean postWorld=TacetTreasureNavigation.postWorld(screen);
            boolean arena=CombatHud.tacetActive(screen);
            boolean grace=!arena&&lastArenaFrame>=0&&capturedAt-lastArenaFrame<=ARENA_LABEL_GRACE_MS&&CombatHud.tacetContinued(screen);
            MobileCombatReader.Observation observed=identities.read(frame,screen,capturedAt,!postWorld);identity=observed.state;
            int[] colors=new int[frame.getWidth()*frame.getHeight()];frame.getPixels(colors,0,frame.getWidth(),0,0,frame.getWidth(),frame.getHeight());
            pixels=new PortraitMatcher.Pixels(frame.getWidth(),frame.getHeight(),colors);
            observedSnapshot=snapshot;observedRequestedAt=captureStarted;
            PortraitMatcher.Match marker=postWorld&&!grace&&identity!=null&&!policy.uidConflict()?treasures.find(pixels):null;
            if(marker!=null&&realTimeMs()-capturedAt<=1500&&capturedAt>lastTreasureFrame) {
                treasureFrames=lastTreasureFrame>=0&&capturedAt-lastTreasureFrame<=2500?treasureFrames+1:1;
                lastTreasureFrame=capturedAt;
                if(treasureFrames>=2) {
                    policy.tacet.treasureConfirmed(AccountDailyConfig.detectUid(screen));
                    CaptureService.instance.saveEvidence(frame,"daily-treasure");
                    android.util.Log.i("WWCombat","owned treasure handoff score="+marker.score+" xy="+marker.x+","+marker.y);
                    throw new Ended();
                }
            } else {treasureFrames=0;lastTreasureFrame=-1;}
            hud=!policy.uidConflict() && identity!=null && (arena||grace) ? new CombatHud(pixels,screen,team.chars.get(identity.active).element):null;
            readAt=realTimeMs();if(readAt-capturedAt>1500){identity=null;hud=null;}
            if(hud!=null&&arena)lastArenaFrame=capturedAt;
            combatFrame=hud!=null;
            if(hud!=null&&grace)android.util.Log.i("WWCombat","arena labels unread; HP-line continuation "+(capturedAt-lastArenaFrame)+"ms after full arena frame");
            clock.observe(capturedAt,hud==null,identity!=null);featureCache.clear();
            if(clock.observationGap()||hud==null)team.forgetBuffTiming();
            held=service.heldActive();
            if (hud != null) {
                if(holdDesired&&heldControl==LIBERATION&&hud.skills.get(LIBERATION)==CombatHud.Skill.READY)
                    liberationHoldReady=true;
                held=service.renewHeldObservation(capturedAt);
                if (holdDesired && !held && expected != null && expected.equals(names.get(identity.active))) {
                    service.holdControl(heldControl,true,layout);held=true;
                }
            } else if (held) { android.util.Log.i("WWTouch","suspend hold: HUD/identity lost");service.holdAttack(false,layout);held=false; }
            GardenRules.Action action=GardenRules.Action.waitFor("daily-tacet-combat",hud==null?"等待战斗HUD/在场身份恢复":
                    names.get(identity.active)+" "+hud.skills+"，协奏="+hud.concertoFull+"，回路="+hud.forteFull);
            org.json.JSONObject state=GardenEngine.persist(service,AutomationTask.DAILY,snapshot,action,true,readAt-capturedAt,names,
                    identity==null?null:names.get(identity.active),observed,policy);
            state.put("combat_execution_enabled",true);state.put("active_character_confirmed_this_frame",identity!=null);
            GardenEngine.writeState(service,AutomationTask.DAILY.stateFile,state);
            android.util.Log.d("WWCombatTiming","capture="+(capturedAt-captureStarted)+" ocr="+(ocrEnded-capturedAt)
                    +" identity="+(readAt-ocrEnded)+" persist="+(realTimeMs()-readAt));
        } finally {frame.recycle();}
    }
    @Override public void checkCombat() throws Exception {
        long end=realTimeMs()+5000;
        while(true) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            if(readAt<0||realTimeMs()-readAt>200||realTimeMs()-capturedAt>1200)refresh();
            if(hud!=null&&combatFrame) {
                if(expected!=null&&!expected.equals(names.get(identity.active)))throw new IllegalStateException("战斗在场身份发生未确认变化，停止而非按旧角色输出");
                return;
            }
            if(realTimeMs()>end)throw new IllegalStateException("无音区战斗状态或在场角色连续5秒未确认");
            Thread.sleep(60);refresh();
        }
    }
    @Override public boolean available(CombatTouchLayout.Control control) throws Exception {
        checkCombat();CombatHud.Skill state=hud.skills.get(control);
        if(state==null)throw new IllegalArgumentException("不是技能按钮");
        return state==CombatHud.Skill.READY;
    }
    @Override public boolean cast(CombatTouchLayout.Control control) throws Exception {
        if(!available(control))return false;
        service.performGesture(layout.press(control,90,2400,1080));
        long until=realTimeMs()+(control==LIBERATION?7000:1800);boolean animation=false;
        do {
            Thread.sleep(60);refresh();
            if(hud==null) {animation=true;continue;}
            if(expected!=null&&!expected.equals(names.get(identity.active)))throw new IllegalStateException("施法后角色变化");
            if(CombatHud.castConfirmed(CombatHud.Skill.READY,hud.skills.get(control))) {
                android.util.Log.i("WWCombat","confirmed "+expected+" "+control+" animation="+animation);
                return true;
            }
        } while(realTimeMs()<until);
        android.util.Log.i("WWCombat","unconfirmed "+expected+" "+control);
        return false;
    }
    @Override public void normalClick() throws Exception {
        checkCombat();
        service.performGesture(layout.press(ATTACK,65,2400,1080));
    }
    @Override public void normalAttack(long durationMs) throws Exception {
        long start=activeTimeMs(),end=realTimeMs()+durationMs+7000;
        do {
            checkCombat();if(!held)service.performGesture(layout.press(ATTACK,65,2400,1080));
            pause(80);
            if(realTimeMs()>end)throw new IllegalStateException("普攻期间战斗计时未推进");
        } while(activeTimeMs()-start<durationMs);
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
        while(realTimeMs()<end){Thread.sleep(Math.max(1,Math.min(80,end-realTimeMs())));if(realTimeMs()-readAt>200||realTimeMs()-capturedAt>1200)refresh();}
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
        signal(PriorityBuiltinRotation.Signal.FLYING);
    }
    @Override public boolean signal(PriorityBuiltinRotation.Signal value) throws Exception {
        checkCombat();
        return switch(value) {
            case FLYING -> flying();
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
            if(available(control))return cast(control);
            if(timeout==0)return false;
            if(sendNormal)normalClick();
            pause(50);
        }while(realTimeMs()-start<timeout);
        return false;
    }
    @Override public long cooldownMs(CombatTouchLayout.Control control) throws Exception {
        checkCombat();
        if(hud.skills.get(control)==CombatHud.Skill.READY)return 0;
        if(hud.skills.get(control)!=CombatHud.Skill.COOLDOWN)throw new IllegalStateException("技能变暗但冷却读数未知");
        CombatGesture.Contact c=layout.point(control);float x=c.x,y=c.y;
        GardenRules.Text number=screen.one("[0-9]{1,2}(?:[.,][0-9])?[sS]?",x-40f/2400,y-32f/1080,x+40f/2400,y+32f/1080);
        if(number==null)throw new IllegalStateException("冷却读数已丢失");
        return Math.round(Double.parseDouble(number.value.replace(',','.').replaceAll("[sS]$",""))*1000);
    }
    @Override public void normalAttack(long duration,long interval,boolean interruptOnResonance) throws Exception {
        if(duration<0||duration>20000||interval<0||interval>1000)throw new IllegalArgumentException("普攻节拍越界");
        long activeStart=activeTimeMs(),wallStart=realTimeMs();
        while(activeTimeMs()-activeStart<duration) {
            checkCombat();
            if(realTimeMs()-wallStart>duration+7000)throw new IllegalStateException("普攻冻结等待超时");
            if(interruptOnResonance&&available(RESONANCE))return;
            normalClick();pause(Math.max(20,interval));
        }
    }
    @Override public void normalTap() throws Exception {normalClick();}
    @Override public void normalClick(long interval) throws Exception {
        if(interval<0||interval>1000)throw new IllegalArgumentException("普攻间隔越界");
        normalClick();pause(Math.max(20,interval));
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
    @Override public boolean mouseForteFull(){throw new IllegalStateException("强化普攻图标未标定，未当作普通回路");}
    @Override public boolean heavyMouseForte(){throw new IllegalStateException("强化普攻消失状态未标定");}
    @Override public void defaultHeavyAttack() throws Exception {heavyAttack(600);}
    @Override public void dodgeClick(long interval) throws Exception {
        if(interval<0||interval>3000)throw new IllegalArgumentException("闪避节拍越界");
        checkCombat();long now=realTimeMs();
        if(lastDodge>=0&&now-lastDodge<interval)return;
        service.performGesture(layout.press(DODGE,90,2400,1080));lastDodge=now;
    }
    @Override public boolean enhancedResonance(long timeout,long minimumAnimation) throws Exception {
        if(minimumAnimation<0||minimumAnimation>3000)throw new IllegalArgumentException("强化共鸣动画时限越界");
        long start=realTimeMs();boolean confirmed=castObserved(RESONANCE,false,timeout);
        if(confirmed)pause(Math.max(0,minimumAnimation-(realTimeMs()-start)));
        return confirmed;
    }
    @Override public void tapResonance() throws Exception {
        checkCombat();service.performGesture(layout.press(RESONANCE,90,2400,1080));lastResonanceTap=capturedAt;
    }
    @Override public void recordResonanceConfirmed() throws Exception {
        checkCombat();
        if(lastResonanceTap<0||capturedAt<=lastResonanceTap||hud.skills.get(RESONANCE)!=CombatHud.Skill.COOLDOWN)
            throw new IllegalStateException("缺少共鸣输入后的新鲜冷却证据");
        lastResonanceTap=-1;
    }
    @Override public boolean confirmHeldLiberation(long started,long timeout) throws Exception {
        if(timeout<0||timeout>7000||started>realTimeMs())throw new IllegalArgumentException("持续解放核验时限越界");
        if(liberationHoldStarted<started||!liberationHoldReady)return false;
        long until=realTimeMs()+timeout;
        do {
            refresh();
            if(hud!=null&&identity!=null&&names.get(identity.active).equals(expected)&&capturedAt>started
                    &&hud.skills.get(LIBERATION)==CombatHud.Skill.COOLDOWN) {
                liberationHoldStarted=-1;liberationHoldReady=false;return true;
            }
            if(realTimeMs()<until)pause(50);
        }while(realTimeMs()<until);
        return false;
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
        checkCombat();
        if(features.normalTargetIndicator(pixels))return false;
        throw new IllegalStateException("变形/长动作标记未确认，保留战斗现场，不用墙钟假定动作结束");
    }
    @Override public boolean breakAction() throws Exception {
        checkCombat();GardenRules.Text text=screen.one("震谐响应|谐度破坏",.60f,.35f,.88f,.72f);
        if(text==null)return false;
        service.tap(text.x()*2400,text.y()*1080,2400,1080);refresh();return true;
    }
    @Override public void cycleSleep(long started) throws Exception {if(!breakAction())pause(Math.max(0,100-(realTimeMs()-started)));}
    @Override public boolean partyHudVisible() throws Exception {refresh();return hud!=null;}
    @Override public void waitPartyHud(long timeoutMs) throws Exception {
        long end=realTimeMs()+timeoutMs;do {refresh();if(hud!=null)return;}while(realTimeMs()<end);
        throw new IllegalStateException("等待技能动画后HUD恢复超时");
    }
    @Override public void waitIntro(long timeoutMs) throws Exception {if(hasIntro())waitPartyHud(timeoutMs);}
    @Override public void waitStanding(long timeoutMs) {throw new IllegalStateException("当前手机角色尚未标定落地状态");}
    @Override public void jump() throws Exception {checkCombat();service.performGesture(layout.press(JUMP,90,2400,1080));}
    @Override public void switchNext() throws Exception {
        long started=realTimeMs();
        checkCombat();int source=identity.active;boolean con=hud.concertoFull;
        int target=team.choose(source,con,rotations.priorities(names.get(source),con,activeTimeMs()),activeTimeMs());
        if(source==target)return;
        String uid=policy.confirmedUid();
        if(uid==null)throw new IllegalStateException("切人账号尚未确认");
        CombatSwitch switching=new CombatSwitch(source,target,started);
        while(true) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            long now=realTimeMs();
            boolean sameUid=!policy.uidConflict()&&uid.equals(AccountDailyConfig.detectUid(screen));
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
    @Override public void close() throws Exception {try{holdAttack(false);}finally{service.combatCapture(false);}}
}
