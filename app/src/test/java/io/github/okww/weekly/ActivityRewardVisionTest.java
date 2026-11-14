package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import static io.github.okww.weekly.ActivityRewardVision.Status.*;
import static io.github.okww.weekly.GardenRules.*;
import static org.junit.Assert.*;

public class ActivityRewardVisionTest {
    private static final int[] CENTERS={894,1218,1542,1867,2191},LABELS={837,1162,1487,1809,2127};
    // Independent public-UI samples: claimed slot 3 and closed slot 5, not the production first-slot prototypes.
    private static final String[] CHECK={
        "01A6000000002C40","1A30000000000094","A3000000000000BE","70000000000007C1",
        "0000000000005F10","000000000002F400","00006400001D8000","0000GGE400AB0000",
        "00008DGG47E10000","0000007GEG400000","0000000AG7000000","00000007B0000000",
        "6000000210000001","C10000000000000A","2C10000000000067","03B4000000001A60"};
    private static final String[] SHUT={
        "0113000000001920","1300000000000072","4100000000000004","5000000000000001",
        "0003444224444000","004GFGGFBGGFG700","00CGBGGB8GGBGG00","009C3AA348C3AC00",
        "0036144483614400","00CG9GG95GG9GG00","00CGEGGFBGGEGG00","009GGGGGDGGGGD00",
        "4014444444444101","5100000000000008","1210000000000063","0142000000001940"};
    // Third real golden box in dev41-free-68.png; production uses the independently sampled first box.
    private static final String[] READY={
        "000000000000","000000000000","3CCCC95CCCC9","FGFGGGCGGGGG","GGCGGD8GGGDG","CB3CA698C76C",
        "861757C48318","GGAGG97GGFBG","GGEGGFBGGGFG","GGGGGGCGGD7C","9CCCCCCC803B","000000000000"};
    private static Text t(String s,float x,float y){return new Text(s,x-.002f,y-.002f,x+.002f,y+.002f);}
    private static Screen page(int points){
        ArrayList<Text> words=new ArrayList<>(Arrays.asList(t("活跃行迹",.08f,.06f),t("活跃度",.22f,.90f),
                t(""+points,.20f,.84f),t("特征码:000000000",.91f,.98f)));
        for(int i=0;i<5;i++)words.add(t(""+(20*(i+1)),LABELS[i]/2400f,975f/1080));
        return new Screen(words,new int[3],2400f/1080);
    }
    private static PortraitMatcher.Pixels pixels(){
        int[] p=new int[2400*1080];Arrays.fill(p,0xff282828);PortraitMatcher.Pixels image=new PortraitMatcher.Pixels(2400,1080,p);
        for(int i=0;i<5;i++)paint(image,i,CHECK);return image;
    }
    private static void paint(PortraitMatcher.Pixels p,int slot,String[] cells){
        int left=CENTERS[slot]-32;
        for(int gy=0;gy<16;gy++)for(int gx=0;gx<16;gx++){
            int white="0123456789ABCDEFG".indexOf(cells[gy].charAt(gx));
            for(int dy=0;dy<4;dy++)for(int dx=0;dx<4;dx++)p.argb[(918+gy*4+dy)*2400+left+gx*4+dx]=dy*4+dx<white?0xffffffff:0xff282828;
        }
    }
    private static PortraitMatcher.Pixels available(){
        PortraitMatcher.Pixels p=pixels();for(int i=0;i<5;i++)paint(p,i,SHUT);
        for(int i=0;i<3;i++){
            int cx=CENTERS[i];
            for(int gy=0;gy<12;gy++)for(int gx=0;gx<12;gx++){
                int n="0123456789ABCDEFG".indexOf(READY[gy].charAt(gx));
                for(int y=0;y<4;y++)for(int x=0;x<4;x++)p.argb[(926+gy*4+y)*2400+cx-24+gx*4+x]=y*4+x<n?0xffffeb96:0xff282828;
            }
            for(int y=-41;y<=41;y++)for(int x=-41;x<=41;x++)if(x*x+y*y>=36*36&&x*x+y*y<=39*39)p.argb[(950+y)*2400+cx+x]=0xffffe696;
            for(int y=-8;y<=8;y++)for(int x=-8;x<=8;x++)if(Math.abs(x)+Math.abs(y)<=8)p.argb[(912+y)*2400+cx+34+x]=0xffef5570;
        }
        return p;
    }
    private static ActivityRewardVision.Observation observe(PortraitMatcher.Pixels p,Screen s){return new ActivityRewardVision().observe(p,s,"000000000",100,110);}
    @Test public void fiveIndependentlySampledChecksAreOnlyAReadOnlyCompletionCertificate(){
        ActivityRewardVision.Observation o=observe(pixels(),page(110));assertEquals(ALL_CLAIMED,o.status);
        assertEquals("000000000",o.uid);assertEquals(Integer.valueOf(110),o.points);assertEquals(100,o.capturedAt);
        for(double score:o.claimedScores())assertTrue(score>.96);
        double[] mutable=o.claimedScores();mutable[0]=-1;assertTrue(o.claimedScores()[0]>.96);
    }
    @Test public void fourChecksAndOneClosedChestAreNotAllClaimedAndNeverMeanClickReady(){
        for(int slot=0;slot<5;slot++){
            PortraitMatcher.Pixels p=pixels();paint(p,slot,SHUT);assertNotEquals(ALL_CLAIMED,observe(p,page(110)).status);
        }
        PortraitMatcher.Pixels shut=pixels();for(int i=0;i<5;i++)paint(shut,i,SHUT);
        assertEquals(NOT_ALL,observe(shut,page(0)).status);
        ActivityRewardVision.Observation locked=observe(shut,page(0));assertTrue(locked.known());assertEquals(-1,locked.firstAvailable());
        for(ActivityRewardVision.State state:locked.states())assertEquals(ActivityRewardVision.State.LOCKED,state);
        PortraitMatcher.Pixels mixed=pixels();paint(mixed,4,SHUT);ActivityRewardVision.Observation partial=observe(mixed,page(80));
        assertTrue(partial.known());assertTrue(partial.claimed(0));assertEquals(ActivityRewardVision.State.LOCKED,partial.state(4));
    }
    @Test public void blankSolidWhiteAndWrongPositionCannotReplaceCheckShape(){
        String[] blank=new String[16],white=new String[16];Arrays.fill(blank,"0000000000000000");Arrays.fill(white,"GGGGGGGGGGGGGGGG");
        for(String[] noise:new String[][]{blank,white}){
            PortraitMatcher.Pixels p=pixels();paint(p,2,noise);assertEquals(UNKNOWN,observe(p,page(110)).status);
        }
        PortraitMatcher.Pixels shifted=pixels();int[] old=shifted.argb.clone();
        for(int y=918;y<982;y++)for(int x=CENTERS[2]-32;x<CENTERS[2]+32;x++)shifted.argb[y*2400+x]=old[(y-8)*2400+x-8];
        assertEquals(UNKNOWN,observe(shifted,page(110)).status);
    }
    @Test public void pageUidActualPointsAndAllFiveThresholdLabelsAreRequired(){
        PortraitMatcher.Pixels p=pixels();
        Screen journal=page(110);journal.words.set(0,t("漂泊日志",.08f,.06f));assertEquals(UNKNOWN,observe(p,journal).status);
        Screen foreign=page(110);foreign.words.set(3,t("特征码:123456789",.91f,.98f));assertEquals(UNKNOWN,observe(p,foreign).status);
        Screen noUid=page(110);noUid.words.remove(3);assertEquals(UNKNOWN,observe(p,noUid).status);
        Screen wrongPoints=page(110);wrongPoints.words.set(2,t("110",.88f,.91f));assertEquals(UNKNOWN,observe(p,wrongPoints).status);
        for(int i=4;i<9;i++){Screen partial=page(110);partial.words.remove(i);assertEquals(UNKNOWN,observe(p,partial).status);}
        assertEquals(UNKNOWN,observe(p,page(80)).status);
        Screen ambiguous=page(110);ambiguous.words.add(t("110",.21f,.84f));assertEquals(UNKNOWN,observe(p,ambiguous).status);
    }
    @Test public void overlayDimensionsAndAspectCannotProduceCompletion(){
        Screen overlay=page(110);for(String s:new String[]{"保存现场","开跑","识别"})overlay.words.add(t(s,.3f,.3f));
        assertEquals(UNKNOWN,observe(pixels(),overlay).status);
        assertEquals(UNKNOWN,observe(pixels(),new Screen(page(110).words,new int[3],16f/9)).status);
        assertEquals(UNKNOWN,observe(new PortraitMatcher.Pixels(1200,540,new int[1200*540]),page(110)).status);
    }
    @Test public void staleFutureRepeatedAndWidelySeparatedFramesBreakTheCertificateSequence(){
        PortraitMatcher.Pixels p=pixels();Screen s=page(110);ActivityRewardVision v=new ActivityRewardVision();
        assertEquals(UNKNOWN,v.observe(p,s,"000000000",0,1501).status);
        assertEquals(UNKNOWN,v.observe(p,s,"000000000",2000,1999).status);
        assertEquals(ALL_CLAIMED,v.observe(p,s,"000000000",2000,2010).status);
        assertEquals(UNKNOWN,v.observe(p,s,"000000000",2000,2020).status);
        assertEquals(ALL_CLAIMED,v.observe(p,s,"000000000",2100,2110).status);
        assertEquals(UNKNOWN,v.observe(p,s,"000000000",5000,5010).status);
        assertEquals(ALL_CLAIMED,v.observe(p,s,"000000000",5100,5110).status);
    }
    @Test public void changingExpectedAccountCannotJoinTwoDifferentAccountsCompletionFrames(){
        PortraitMatcher.Pixels p=pixels();ActivityRewardVision v=new ActivityRewardVision();
        assertEquals(ALL_CLAIMED,v.observe(p,page(110),"000000000",100,110).status);
        Screen second=page(110);second.words.set(3,t("特征码:123456789",.91f,.98f));
        assertEquals(UNKNOWN,v.observe(p,second,"123456789",200,210).status);
        assertEquals(ALL_CLAIMED,v.observe(p,second,"123456789",300,310).status);
    }
    @Test public void availableNeedsReachedThresholdExactGoldenChestRingAndRedDiamondTogether(){
        ActivityRewardVision.Observation ready=observe(available(),page(60));assertTrue(ready.known());assertEquals(0,ready.firstAvailable());assertFalse(ready.allClaimed());
        for(int i=0;i<3;i++){assertEquals(ActivityRewardVision.State.AVAILABLE,ready.state(i));assertTrue(ready.availableScores()[i]>.97);assertTrue(ready.goldPixels()[i]>=400);assertTrue(ready.redPixels()[i]>=80);}
        for(int i=3;i<5;i++)assertEquals(ActivityRewardVision.State.LOCKED,ready.state(i));
        assertEquals(UNKNOWN,observe(available(),page(40)).status);
        for(int missing=0;missing<3;missing++){
            PortraitMatcher.Pixels p=available();int cx=CENTERS[1];
            if(missing==0)for(int y=898;y<928;y++)for(int x=cx+19;x<cx+50;x++)p.argb[y*2400+x]=0xff282828;
            if(missing==1)for(int y=-41;y<=41;y++)for(int x=-41;x<=41;x++)if(x*x+y*y>=33*33&&x*x+y*y<=41*41)p.argb[(950+y)*2400+cx+x]=0xff282828;
            if(missing==2)for(int y=926;y<974;y++)for(int x=cx-24;x<cx+24;x++)p.argb[y*2400+x]=0xff282828;
            ActivityRewardVision.Observation unknown=observe(p,page(60));assertEquals(ActivityRewardVision.State.UNKNOWN,unknown.state(1));assertFalse(unknown.known());assertEquals(-1,unknown.firstAvailable());
        }
    }
    @Test public void solidRedSquareIsNotTheMeasuredRedDiamondAndCoordinatesAreMeasuredOnly(){
        PortraitMatcher.Pixels p=available();int cx=CENTERS[1];
        for(int y=904;y<=920;y++)for(int x=cx+26;x<=cx+42;x++)p.argb[y*2400+x]=0xffef5570;
        assertEquals(ActivityRewardVision.State.UNKNOWN,observe(p,page(60)).state(1));
        for(int i=0;i<5;i++)assertEquals(CENTERS[i]/2400f,ActivityRewardVision.tapX(i),0);
        assertEquals(950f/1080,ActivityRewardVision.tapY(),0);assertThrows(IllegalArgumentException.class,()->ActivityRewardVision.tapX(5));
    }
    @Test public void packageFixtureConstructorIsReadonlyAndExposesOnlyKnownSlotSelection(){
        ActivityRewardVision.State[] states={ActivityRewardVision.State.CLAIMED,ActivityRewardVision.State.AVAILABLE,ActivityRewardVision.State.AVAILABLE,ActivityRewardVision.State.LOCKED,ActivityRewardVision.State.LOCKED};
        ActivityRewardVision.Observation o=new ActivityRewardVision.Observation("000000000",100,60,states);
        assertTrue(o.known());assertTrue(o.claimed(0));assertEquals(1,o.firstAvailable());assertFalse(o.allClaimed());
        states[0]=ActivityRewardVision.State.UNKNOWN;o.states()[1]=ActivityRewardVision.State.UNKNOWN;
        assertTrue(o.claimed(0));assertEquals(1,o.firstAvailable());
    }
}
