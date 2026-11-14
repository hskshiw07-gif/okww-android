package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;
import static io.github.okww.weekly.GardenRules.*;

public class DailyReservePreparationTest {
    private static final String UID="333333333";
    private static final long NOW=java.time.Instant.parse("2026-11-11T04:00:00Z").toEpochMilli();
    private static Text t(String text,int x,int y){return new Text(text,(x-2)/2400f,(y-2)/1080f,(x+2)/2400f,(y+2)/1080f);}
    static Screen source(int ordinary,int reserve,String uid){
        return new Screen(new ArrayList<>(Arrays.asList(t("补充结晶波片",690,207),t("请选择补充方式",1196,299),
                t("取消",818,776),t("确认",1574,777),t("提取等量结晶单质",1196,593),
                t(""+reserve,1061,543),t(""+reserve,1411,66),t(ordinary+"/240",1665,65),
                t("80",1245,541),t("4352",1408,541),t("特征码:"+uid,2191,1060))),new int[3],2400f/1080);
    }
    private static class Harness {
        final DailyStaminaLedger ledger;
        final DailyTacet tacet=new DailyTacet();
        final DailyReservePreparation preparation=new DailyReservePreparation(tacet);
        Harness() throws Exception {
            ledger=new DailyStaminaLedger(new DailyStaminaLedger.Store(){
                String saved;public String read(){return saved;}public void write(String next){saved=next;}
            },UID,NOW);tacet.attachLedger(ledger);
        }
        void open(){preparation.applied(preparation.open(UID));}
        void select(){Action action=preparation.next(source(130,110,UID),UID,true);
            assertEquals("daily-reserve-select",action.scene);assertEquals(1018f/2400,action.x,.0001f);preparation.applied(action);}
        void quantity(){open();select();preparation.next(source(120,357,UID),UID,true);preparation.applied(preparation.next(source(120,357,UID),UID,true));}
        Action frame(Screen screen,long time){return preparation.next(screen,UID,true,NOW+time,NOW+time+10);}
        void send(Action action,long time){preparation.beforeInput(action,NOW+time+20);preparation.applied(action);}
    }
    @Test public void unownedPanelOnlyBacksOutAndNeverReadsItAsPermission(){
        DailyTacet tacet=new DailyTacet();DailyReservePreparation p=new DailyReservePreparation(tacet);
        assertEquals(Kind.BACK,p.next(source(240,355,UID),UID,true).kind);assertNull(tacet.plan());
        assertThrows(IllegalStateException.class,()->p.open(UID));
        Screen partial=source(240,355,UID);partial.words.remove(0);
        assertEquals(Kind.BACK,p.next(partial,UID,false).kind);
    }
    @Test public void selectsMeasuredGreenCardThenRequiresTwoMatchingResourceFramesWithoutExchanging() throws Exception {
        Harness h=new Harness();h.open();assertTrue(h.preparation.owned());h.select();
        assertEquals("daily-reserve-plan-check",h.preparation.next(source(130,110,UID),UID,true).scene);assertNull(h.tacet.plan());
        Action action=h.preparation.next(source(130,110,UID),UID,true);
        assertEquals("daily-reserve-source-confirm",action.scene);assertEquals(1574f/2400,action.x,.0001f);
        assertEquals(240,h.tacet.plan().plannedBudget);assertEquals(110,h.tacet.plan().exchangeAmount);
        assertFalse(h.tacet.funded());assertEquals(0,h.tacet.reserved());assertEquals(0,h.tacet.spent());
        h.preparation.applied(action);
        assertEquals("daily-reserve-quantity-wait",h.preparation.next(source(130,110,UID),UID,true).scene);
        Screen quantity=new Screen(new ArrayList<>(Arrays.asList(t("确认",1574,777),t("兑换次数110",900,625),t("特征码:"+UID,2191,1060))),new int[3],2400f/1080);
        for(int n=0;n<3;n++)assertEquals(Kind.WAIT,h.preparation.next(quantity,UID,true).kind);
        assertFalse(h.tacet.funded());assertEquals(0,h.tacet.reserved());
    }
    @Test public void partialSourceAndWrongSelectedResourceNeverAuthorizeSelectionOrConfirmation() throws Exception {
        Harness h=new Harness();h.open();Screen partial=source(130,110,UID);partial.words.remove(3);
        assertEquals(Kind.WAIT,h.preparation.next(partial,UID,true).kind);h.select();
        Screen star=source(130,110,UID);star.words.set(4,t("消耗星声",1196,593));
        assertEquals("daily-reserve-selection-wait",h.preparation.next(star,UID,true).scene);assertNull(h.tacet.plan());
    }
    @Test public void cardAndHeaderMustAgreeAndAnyBadFrameBreaksTheStreak() throws Exception {
        for(int gap=0;gap<5;gap++){
            Harness h=new Harness();h.open();h.select();h.preparation.next(source(130,110,UID),UID,true);
            Screen bad=source(130,110,UID);
            if(gap==0)bad.words.set(5,t("111",1061,543));
            if(gap==1)bad.words.remove(7);
            if(gap==2)bad.words.remove(10);
            if(gap==3)bad.words.set(0,t("补充结晶波片",50,50));
            if(gap==4){bad.words.add(t("保存现场",300,300));bad.words.add(t("开跑",400,300));bad.words.add(t("识别",500,300));}
            assertEquals(Kind.WAIT,h.preparation.next(bad,UID,true).kind);assertNull(h.tacet.plan());
            assertEquals("daily-reserve-plan-check",h.preparation.next(source(130,110,UID),UID,true).scene);
            assertEquals("daily-reserve-source-confirm",h.preparation.next(source(130,110,UID),UID,true).scene);
        }
    }
    @Test public void accountTeamAndOutstandingClaimConflictsNeverFreezeAPlan() throws Exception {
        Harness h=new Harness();h.open();h.select();
        assertEquals(Kind.STOP,h.preparation.next(source(130,110,"000000000"),UID,true).kind);
        assertEquals(Kind.STOP,h.preparation.next(source(130,110,UID),UID,false).kind);
        assertEquals(Kind.STOP,h.preparation.next(source(130,110,UID),"000000000",true).kind);
        h.ledger.reserve(240,60,false,NOW);
        assertEquals(Kind.STOP,h.preparation.next(source(130,110,UID),UID,true).kind);
        assertNull(h.tacet.plan());assertEquals(60,h.tacet.reserved());
    }
    @Test public void zeroDeficitCancelsAndRequiresSourceModalToReallyDisappear() throws Exception {
        Harness h=new Harness();h.open();h.select();h.preparation.next(source(240,355,UID),UID,true);
        Action cancel=h.preparation.next(source(240,355,UID),UID,true);assertEquals("daily-reserve-cancel",cancel.scene);
        assertTrue(h.tacet.funded());assertEquals(0,h.tacet.plan().exchangeAmount);h.preparation.applied(cancel);
        Screen partial=source(240,355,UID);partial.words.remove(0);partial.words.add(t("素材获取",180,65));
        assertEquals(Kind.WAIT,h.preparation.next(partial,UID,true).kind);assertTrue(h.preparation.owned());
        Screen book=new Screen(new ArrayList<>(Arrays.asList(t("素材获取",180,65),t("特征码:"+UID,2191,1060))),new int[3],2400f/1080);
        assertNull(h.preparation.next(book,UID,true));assertFalse(h.preparation.owned());
    }
    @Test public void actualReserveSelects180OrStopsBelow180WithoutAnyConversion() throws Exception {
        Harness enough=new Harness();enough.open();enough.select();enough.preparation.next(source(130,60,UID),UID,true);
        assertEquals("daily-reserve-source-confirm",enough.preparation.next(source(130,60,UID),UID,true).scene);
        assertEquals(180,enough.tacet.plan().plannedBudget);assertEquals(50,enough.tacet.plan().exchangeAmount);assertFalse(enough.tacet.funded());
        Harness low=new Harness();low.open();low.select();low.preparation.next(source(130,49,UID),UID,true);
        assertThrows(IllegalStateException.class,()->low.preparation.next(source(130,49,UID),UID,true));
        assertNull(low.tacet.plan());assertEquals(0,low.tacet.reserved());
    }
    private static Screen quantity(int amount){
        return new Screen(new ArrayList<>(Arrays.asList(t("兑换结晶波片",690,207),t("结晶单质x"+amount,951,500),
                t("结晶波片x"+amount,1451,500),t("兑换次数:"+amount,810,621),t(""+amount,1480,623),
                t("240",1538,659),t("取消",818,778),t("确认",1574,778),t("特征码:"+UID,2191,1060),
                t("1357",1410,65),t("240357",1730,65),t("121/240)X",2103,63))),new int[3],2400f/1080);
    }
    @Test public void oneMeasuredDragThenFineAdjustmentAndTwoExactFramesAuthorizeOnlyOneSubmission() throws Exception {
        Harness h=new Harness();h.quantity();Action drag=h.frame(quantity(60),100);assertEquals("daily-reserve-drag",drag.scene);
        h.preparation.beforeInput(drag,NOW+120);CombatGesture.Contact stroke=h.preparation.gesture(drag).contacts.get(0);
        assertEquals(928,stroke.x*2400,1);assertEquals(1117,stroke.endX*2400,1);assertEquals(450,stroke.durationMs);h.preparation.applied(drag);
        Action plus=h.frame(quantity(119),200);assertEquals("daily-reserve-increment",plus.scene);assertEquals(1595,plus.x*2400,.01);h.send(plus,200);
        assertEquals("daily-reserve-quantity-check",h.frame(quantity(120),300).scene);
        Action submit=h.frame(quantity(120),400);assertEquals("daily-reserve-exchange",submit.scene);h.send(submit,400);
        assertThrows(IllegalStateException.class,()->h.preparation.beforeInput(submit,NOW+430));
        assertEquals(Kind.WAIT,h.frame(quantity(120),500).kind);assertFalse(h.tacet.funded());
        assertEquals(Kind.WAIT,h.frame(source(240,237,UID),600).kind);assertFalse(h.tacet.funded());
        Action cancel=h.frame(source(240,237,UID),700);assertEquals("daily-reserve-cancel",cancel.scene);assertTrue(h.tacet.funded());
        assertEquals(0,h.tacet.spent());assertEquals(0,h.tacet.reserved());
    }
    @Test public void staleUnknownWrongUidAndInconsistentQuantityNeverAuthorizeFinalConfirmation() throws Exception {
        Harness h=new Harness();h.quantity();assertEquals(Kind.WAIT,h.frame(quantity(120),100).kind);
        assertEquals(Kind.WAIT,h.frame(quantity(120),100).kind);
        assertEquals(Kind.WAIT,h.frame(quantity(120),200).kind);
        Screen wrong=quantity(120);wrong.words.set(2,t("结晶波片x119",1451,500));assertEquals(Kind.WAIT,h.frame(wrong,300).kind);
        assertEquals(Kind.WAIT,h.frame(quantity(120),400).kind);
        Screen foreign=quantity(120);foreign.words.set(8,t("特征码:000000000",2191,1060));assertEquals(Kind.STOP,h.frame(foreign,500).kind);
        assertEquals(Kind.WAIT,h.frame(quantity(120),600).kind);
        Action submit=h.frame(quantity(120),700);assertEquals("daily-reserve-exchange",submit.scene);
        assertThrows(IllegalStateException.class,()->h.preparation.beforeInput(submit,NOW+2201));
        assertFalse(h.tacet.funded());assertEquals(0,h.tacet.reserved());
    }
    @Test public void neverRepeatsDragAndBoundsFineAdjustmentsAtFive() throws Exception {
        Harness h=new Harness();h.quantity();h.send(h.frame(quantity(60),100),100);
        assertEquals(Kind.STOP,h.frame(quantity(100),200).kind);
        Harness fine=new Harness();fine.quantity();
        for(int n=0;n<5;n++){Action minus=fine.frame(quantity(121),100+n*100);assertEquals("daily-reserve-decrement",minus.scene);assertEquals(669,minus.x*2400,.01);fine.send(minus,100+n*100);}
        assertEquals(Kind.STOP,fine.frame(quantity(121),700).kind);assertFalse(fine.tacet.funded());
    }
    @Test public void quantityParserRequiresFourMatchingCountsCorrectResourcesAndMeasuredMaximum(){
        assertEquals(120,DailyReservePreparation.quantity(quantity(120)).amount);
        for(int field:new int[]{0,1,2,3,4,5,6,7}){
            Screen partial=quantity(120);partial.words.remove(field);assertNull("field="+field,DailyReservePreparation.quantity(partial));
        }
        Screen solvent=quantity(120);solvent.words.set(1,t("结晶溶剂x120",951,500));assertNull(DailyReservePreparation.quantity(solvent));
        Screen wrongCost=quantity(120);wrongCost.words.set(4,t("119",1480,623));assertNull(DailyReservePreparation.quantity(wrongCost));
        Screen max=quantity(120);max.words.set(5,t("119",1538,659));assertNull(DailyReservePreparation.quantity(max));
        assertNull(DailyReservePreparation.quantity(quantity(0)));
    }
    @Test public void postSubmissionQuantityHeadersNeverProveFundingEvenIfTheyResembleExpectedBalances() throws Exception {
        Harness h=new Harness();h.quantity();h.frame(quantity(120),100);h.send(h.frame(quantity(120),200),200);
        Screen blurred=quantity(120);blurred.words.set(9,t("237",1411,66));blurred.words.set(10,t("240/240",1665,65));
        for(int n=0;n<3;n++)assertEquals(Kind.WAIT,h.frame(blurred,300+n*100).kind);
        assertFalse(h.tacet.funded());assertEquals(0,h.tacet.reserved());
        assertEquals(Kind.WAIT,h.frame(source(240,237,UID),600).kind);
        assertEquals("daily-reserve-cancel",h.frame(source(240,237,UID),700).scene);assertTrue(h.tacet.funded());
    }
    @Test public void materialPageHasItsOwnMeasuredHeaderAndCanConfirmExactFunding() throws Exception {
        Harness h=new Harness();h.quantity();h.frame(quantity(120),100);h.send(h.frame(quantity(120),200),200);
        Screen material=new Screen(new ArrayList<>(Arrays.asList(t("素材获取",180,65),t("240/240+",1825,64),
                t("237",1539,65),t("特征码:"+UID,2191,1060))),new int[3],2400f/1080);
        assertEquals(Kind.WAIT,h.frame(material,300).kind);assertFalse(h.tacet.funded());
        Screen unknown=new Screen(new ArrayList<>(Arrays.asList(t("特征码:"+UID,2191,1060))),new int[3],2400f/1080);
        assertEquals(Kind.WAIT,h.frame(unknown,400).kind);
        assertEquals(Kind.WAIT,h.frame(material,500).kind);assertFalse(h.tacet.funded());
        assertNull(h.frame(material,600));assertTrue(h.tacet.funded());assertFalse(h.preparation.owned());
    }
    @Test public void recordedQuantityOcrParses60And120WithoutUsingBlurredHeader() throws Exception {
        java.nio.file.Path root=java.nio.file.Paths.get("").toAbsolutePath();
        while(root!=null&&!java.nio.file.Files.exists(root.resolve(".android-tools/daily-combat/reserve-quantity-ocr.json")))root=root.getParent();
        org.junit.Assume.assumeTrue("本机实测OCR工件存在时回放",root!=null);
        for(String file:new String[]{"reserve-quantity-ocr.json","reserve-quantity-120-ocr.json"}){
            String json=new String(java.nio.file.Files.readAllBytes(root.resolve(".android-tools/daily-combat/"+file)),java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\{\\s*\"text\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"left\"\\s*:\\s*([0-9]+)\\s*,\\s*\"top\"\\s*:\\s*([0-9]+)\\s*,\\s*\"right\"\\s*:\\s*([0-9]+)\\s*,\\s*\"bottom\"\\s*:\\s*([0-9]+)").matcher(json);
            java.util.List<Text> words=new ArrayList<>();while(m.find())words.add(new Text(m.group(1).replace("\\/","/"),Integer.parseInt(m.group(2))/2400f,Integer.parseInt(m.group(3))/1080f,Integer.parseInt(m.group(4))/2400f,Integer.parseInt(m.group(5))/1080f));
            Screen replay=new Screen(words,new int[3],2400f/1080);DailyReservePreparation.Quantity parsed=DailyReservePreparation.quantity(replay);
            assertNotNull(file,parsed);assertEquals(file.contains("120")?120:60,parsed.amount);assertEquals(240,parsed.maximum);
        }
    }
    @Test public void wrongConfirmationCoordinatesCannotSubmitTheOtherwiseValidQuantity() throws Exception {
        Harness h=new Harness();h.quantity();h.frame(quantity(120),100);Action good=h.frame(quantity(120),200);
        Action bad=new Action(Kind.TAP,good.scene,"",.4f,.4f);
        assertThrows(IllegalStateException.class,()->h.preparation.beforeInput(bad,NOW+220));
        assertThrows(IllegalStateException.class,()->h.preparation.beforeInput(new Action(Kind.TAP,good.scene,"",Float.NaN,good.y),NOW+220));
        assertThrows(IllegalStateException.class,()->h.preparation.beforeInput(new Action(Kind.BACK,good.scene,"",good.x,good.y),NOW+220));
        assertFalse(h.tacet.funded());h.send(good,200);
        assertEquals(Kind.WAIT,h.frame(quantity(120),300).kind);assertFalse(h.tacet.funded());
    }
    @Test public void oneUnitMaximumSupportsAnExactOneUnitDeficitWithoutDragging() throws Exception {
        Harness h=new Harness();h.open();h.select();h.preparation.next(source(179,1,UID),UID,true);
        h.preparation.applied(h.preparation.next(source(179,1,UID),UID,true));
        Screen one=quantity(1);one.words.set(5,t("1",1538,659));
        assertEquals(1,DailyReservePreparation.quantity(one).maximum);
        assertEquals("daily-reserve-quantity-check",h.frame(one,100).scene);
        Action submit=h.frame(one,200);assertEquals("daily-reserve-exchange",submit.scene);assertNull(h.preparation.gesture(submit));h.send(submit,200);
        assertFalse(h.tacet.funded());assertEquals(1,h.tacet.plan().exchangeAmount);
        Screen zero=quantity(1);zero.words.set(5,t("0",1538,659));assertNull(DailyReservePreparation.quantity(zero));
    }
}
