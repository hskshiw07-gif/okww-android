package io.github.okww.weekly;

import org.junit.Assume;
import org.junit.Test;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import static org.junit.Assert.*;

public class TreasureMarkerTest {
    private PortraitMatcher.Pixels template() throws Exception {
        try(InputStream input=getClass().getResourceAsStream("/tacet-features/"+TreasureMarker.FILE)) {
            assertNotNull("Original treasure template must be packaged",input);return pixels(input);
        }
    }

    @Test public void packagedTemplateIsUnchangedPcExportAndHasMeasuredGeometry() throws Exception {
        try(InputStream input=getClass().getResourceAsStream("/tacet-features/"+TreasureMarker.FILE)) {
            assertNotNull(input);StringBuilder hash=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()))hash.append(String.format("%02x",b&255));
            assertEquals("0b79d322320f666d67def9d4b5bd61b4ed1976f83c9defddfab6310c873e7573",hash.toString());
        }
        PortraitMatcher.Pixels t=template();assertEquals(61,t.width);assertEquals(54,t.height);
        assertThrows(IllegalArgumentException.class,()->new TreasureMarker(null));
        assertThrows(IllegalArgumentException.class,()->new TreasureMarker(new PortraitMatcher.Pixels(61,55,new int[61*55])));
    }

    @Test public void syntheticMarkerNeedsMeasuredLayoutAndLiesInsideGameplaySearchRoi() throws Exception {
        PortraitMatcher.Pixels t=template();TreasureMarker marker=new TreasureMarker(t);
        PortraitMatcher.Pixels frame=new PortraitMatcher.Pixels(2400,1080,new int[2400*1080]);
        assertNull(marker.find(frame));assertNull(marker.find(null));
        assertNull(marker.find(new PortraitMatcher.Pixels(1920,1080,new int[1920*1080])));
        paste(t,frame,1400,800);PortraitMatcher.Match hit=marker.find(frame);assertNotNull(hit);
        assertTrue(hit.score>=.8);assertEquals(1415.5,hit.x*2400,2);assertEquals(813.5,hit.y*1080,2);
        Arrays.fill(frame.argb,0);paste(t,frame,10,40);assertNull(marker.find(frame));
        Arrays.fill(frame.argb,0);paste(t,frame,2340,900);assertNull(marker.find(frame));
    }

    @Test public void noisyCoarseWinnerDoesNotHideTheTrueFinePeakAtAnyGridPhase() throws Exception {
        PortraitMatcher.Pixels t=template();TreasureMarker marker=new TreasureMarker(t);
        for(int dy=0;dy<3;dy++)for(int dx=0;dx<3;dx++) {
            PortraitMatcher.Pixels frame=new PortraitMatcher.Pixels(2400,1080,new int[2400*1080]);
            paste(t,frame,501,501);Random random=new Random(49);
            for(int y=0;y<27;y++)for(int x=0;x<31;x++) {
                int index=(501+y)*2400+501+x,value=(frame.argb[index]&255)/2+random.nextInt(128);
                frame.argb[index]=0xff000000|value*0x10101;
            }
            assertNull("Distractor alone must not pass .8",marker.find(frame));
            paste(t,frame,1399+dx,799+dy);
            if(dx==0&&dy==0)assertTrue("Regression must reproduce the previous single-peak miss",
                    PortraitMatcher.match(frame,Collections.singletonList(new PortraitMatcher.Template("treasure",t,2160)),
                            72,108,2328,875,1.0).score<.8);
            PortraitMatcher.Match hit=marker.find(frame);assertNotNull("phase "+dx+","+dy,hit);
            assertEquals(.9318535059,hit.score,.000002);
            assertEquals(1414.5+dx,hit.x*2400,.01);assertEquals(812.5+dy,hit.y*1080,.01);
        }
    }

    @Test public void recordedDev34MarkerMatchesWithoutReducingThresholdAndBattleWorldConsoleDoNot() throws Exception {
        Path root=Path.of("../../.android-tools/daily-combat");
        if(!Files.isDirectory(root))root=Path.of("../.android-tools/daily-combat");
        Assume.assumeTrue("Private dev34 reward capture not present",Files.isRegularFile(root.resolve("dev34-progress-2.png")));
        TreasureMarker marker=new TreasureMarker(template());
        String[] positive={"dev34-progress-2.png","dev34-failure.png"};
        double[] scores={.857715,.920552},x={1423.5,1426.5},y={830.5,829.5};
        for(int i=0;i<positive.length;i++) {
            Assume.assumeTrue(Files.isRegularFile(root.resolve(positive[i])));
            try(InputStream input=Files.newInputStream(root.resolve(positive[i]))) {
                PortraitMatcher.Match match=marker.find(pixels(input));assertNotNull(positive[i],match);
                assertEquals(scores[i],match.score,.000002);assertEquals(x[i],match.x*2400,.01);assertEquals(y[i],match.y*1080,.01);
            }
        }
        // Pixel-only evidence: an overlay-containing positive is never proof that the caller's HUD/UID/action guard passed.
        for(String file:Arrays.asList("dev34-progress-1.png","dev34-live-fight-00.png","dev34-live-fight-01.png",
                "dev34-live-fight-02.png","dev34-live-fight-03.png","dev34-live-fight-04.png","dev34-live-fight-05.png",
                "dev34-live-fight-06.png","dev30-user-healed.png","arena-shore.png","dev33-final-console.png")) {
            Assume.assumeTrue(Files.isRegularFile(root.resolve(file)));
            try(InputStream input=Files.newInputStream(root.resolve(file))){assertNull(file,marker.find(pixels(input)));}
        }
    }

    @Test public void recordedAppCaptureAndAdbCaptureHaveTheSameFineMarkerNotTheBackgroundPeak() throws Exception {
        Path root=Path.of("../../.android-tools/daily-combat");
        if(!Files.isDirectory(root))root=Path.of("../.android-tools/daily-combat");
        Assume.assumeTrue("Private dev36 capture not present",Files.isRegularFile(root.resolve("dev36-app-failure.png")));
        TreasureMarker marker=new TreasureMarker(template());
        String[] names={"dev36-app-failure.png","dev36-progress2.png"};
        double[] scores={.9279530108,.9308668762};
        for(int i=0;i<names.length;i++) {
            Assume.assumeTrue(Files.isRegularFile(root.resolve(names[i])));
            try(InputStream input=Files.newInputStream(root.resolve(names[i]))) {
                PortraitMatcher.Pixels source=pixels(input);
                for(int dy=0;dy<3;dy++)for(int dx=0;dx<3;dx++) {
                    int[] shifted=new int[2400*1080];
                    for(int y=0;y<1080-dy;y++)System.arraycopy(source.argb,y*2400,shifted,(y+dy)*2400+dx,2400-dx);
                    PortraitMatcher.Match hit=marker.find(new PortraitMatcher.Pixels(2400,1080,shifted));
                    assertNotNull(names[i]+" phase "+dx+","+dy,hit);assertEquals(scores[i],hit.score,.000002);
                    assertEquals(565.5+dx,hit.x*2400,.01);assertEquals(711.5+dy,hit.y*1080,.01);
                }
            }
        }
        // The app failure PNG includes our panel: this proves pixels only, never navigation permission.
    }

    private static void paste(PortraitMatcher.Pixels template,PortraitMatcher.Pixels frame,int left,int top) {
        for(int y=0;y<27;y++)for(int x=0;x<31;x++)
            frame.argb[(top+y)*2400+left+x]=template.argb[Math.round(y*53f/26)*61+Math.round(x*60f/30)];
    }
    private static PortraitMatcher.Pixels pixels(InputStream input) throws Exception {
        // Reflection keeps java.desktop image decoding JVM-test-only under Android's unit-test compilation.
        Object image=Class.forName("javax.imageio.ImageIO").getMethod("read",InputStream.class).invoke(null,input);
        Class<?> type=Class.forName("java.awt.image.BufferedImage");
        int w=(Integer)type.getMethod("getWidth").invoke(image),h=(Integer)type.getMethod("getHeight").invoke(image);
        int[] rgb=(int[])type.getMethod("getRGB",int.class,int.class,int.class,int.class,int[].class,int.class,int.class)
                .invoke(image,0,0,w,h,null,0,w);
        return new PortraitMatcher.Pixels(w,h,rgb);
    }
}
