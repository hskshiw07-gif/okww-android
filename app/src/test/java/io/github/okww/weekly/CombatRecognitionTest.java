package io.github.okww.weekly;

import org.junit.Test;
import org.junit.Assume;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class CombatRecognitionTest {
    @Test public void recordedLatencyFailureHasReliablePortraitsButStillRequiresFreshConsecutiveFrames() throws Exception {
        var all=templates(catalog(),new ArrayList<>(catalog().characters.keySet()));
        int[] colors=new int[2400*1080];
        ChisaHudTest.paste(colors,ChisaHudTest.read("/priority-mobile/identity-latency-hiyuki.png"),2125,215);
        ChisaHudTest.paste(colors,ChisaHudTest.read("/priority-mobile/identity-latency-aemeath.png"),2125,355);
        var pair=PortraitMatcher.mobileStandbyPair(new PortraitMatcher.Pixels(2400,1080,colors),all,PortraitMatcher.grayscaleTemplates(all));
        assertEquals("Hiyuki",pair[0].character);assertTrue(pair[0].reliable());
        assertEquals("Aemeath",pair[1].character);assertTrue(pair[1].reliable());
        var active=new ActiveCharacterRecognizer(Arrays.asList("Hiyuki","Aemeath","Hsin"));
        assertNull(active.observe(pair[0],pair[1],2400,1080,100,1642,true));
        assertNull(active.observe(pair[0],pair[1],2400,1080,2000,2800,true));
        assertEquals(2,active.observe(pair[0],pair[1],2400,1080,2900,3700,true).active);
        assertNull(active.observe(pair[0],pair[1],2400,1080,3800,4600,false));
    }
    @Test public void recordedMobileHiyukiCompetesAgainstEveryOriginalPortrait() throws Exception {
        var catalog=catalog();var all=templates(catalog,new ArrayList<>(catalog.characters.keySet()));
        int[] colors=new int[2400*1080];
        ChisaHudTest.paste(colors,ChisaHudTest.read("/priority-mobile/hiyuki-standby.png"),2125,215);
        ChisaHudTest.paste(colors,ChisaHudTest.read("/priority-mobile/hsin-standby.png"),2125,355);
        var frame=new PortraitMatcher.Pixels(2400,1080,colors);
        var pair=PortraitMatcher.mobileStandbyPair(frame,all,PortraitMatcher.grayscaleTemplates(all));
        assertEquals("Hiyuki",pair[0].character);assertTrue(pair[0].reliable());
        assertEquals("Hsin",pair[1].character);assertTrue(pair[1].reliable());
        var active=new ActiveCharacterRecognizer(Arrays.asList("Hiyuki","Aemeath","Hsin"));
        assertNull(active.observe(pair[0],pair[1],2400,1080,100,400,true));
        assertEquals(1,active.observe(pair[0],pair[1],2400,1080,900,1200,true).active);
    }
    private BuiltinCombatCatalog catalog() throws Exception {
        try (InputStreamReader input = new InputStreamReader(getClass().getResourceAsStream("/builtin-combat.tsv"), StandardCharsets.UTF_8)) {
            return new BuiltinCombatCatalog(input);
        }
    }
    private PortraitMatcher.Pixels pixels(InputStream input) throws Exception {
        // AGP compiles unit tests against android.jar; reflection keeps java.desktop strictly test-runtime-only.
        Object image = Class.forName("javax.imageio.ImageIO").getMethod("read", InputStream.class).invoke(null, input);
        Class<?> type = Class.forName("java.awt.image.BufferedImage");
        int width = (Integer) type.getMethod("getWidth").invoke(image), height = (Integer) type.getMethod("getHeight").invoke(image);
        int[] rgb = (int[]) type.getMethod("getRGB", int.class, int.class, int.class, int.class, int[].class, int.class, int.class)
                .invoke(image, 0, 0, width, height, null, 0, width);
        return new PortraitMatcher.Pixels(width, height, rgb);
    }
    private List<PortraitMatcher.Template> templates(BuiltinCombatCatalog catalog, List<String> team) throws Exception {
        List<PortraitMatcher.Template> result = new ArrayList<>();
        for (String name : team) for (String file : catalog.require(name).portraits) {
            try (InputStream input = getClass().getResourceAsStream("/portraits/" + file)) {
                assertNotNull(file, input);
                String[] geometry = file.substring(0, file.length()-4).split("_");
                int height = Integer.parseInt(geometry[geometry.length-3].split("x")[1]);
                result.add(new PortraitMatcher.Template(name, pixels(input), height,file.contains("_mobile_")));
            }
        }
        return result;
    }
    @Test public void grayscaleKeepsEveryTemplateAndDoesNotMutateRgbPixels() throws Exception {
        int[] original={0xffff0000,0xff00ff00,0xff0000ff,0xffffffff,0xff000000};
        int[] saved=original.clone();
        PortraitMatcher.Pixels gray=PortraitMatcher.grayscale(new PortraitMatcher.Pixels(5,1,original));
        assertArrayEquals(new int[]{0xff4d4d4d,0xff959595,0xff1d1d1d,0xffffffff,0xff000000},gray.argb);
        assertArrayEquals(saved,original);
        List<PortraitMatcher.Template> all=templates(catalog(),new ArrayList<>(catalog().characters.keySet()));
        List<PortraitMatcher.Template> converted=PortraitMatcher.grayscaleTemplates(all);
        assertEquals(67,converted.size());
        for(int i=0;i<all.size();i++) {
            assertEquals(all.get(i).character,converted.get(i).character);
            assertEquals(all.get(i).sourceHeight,converted.get(i).sourceHeight);
            assertEquals(all.get(i).nativeSize,converted.get(i).nativeSize);
            assertEquals(all.get(i).pixels.width,converted.get(i).pixels.width);
            assertEquals(all.get(i).pixels.height,converted.get(i).pixels.height);
            assertNotSame(all.get(i).pixels.argb,converted.get(i).pixels.argb);
        }
    }
    @Test public void recordedGrayStandbyRecoversWithoutLoweringThresholdOrChangingReliableRgb() throws Exception {
        Path root=Path.of("../../.android-tools/daily-combat");
        if(!Files.isDirectory(root))root=Path.of("../.android-tools/daily-combat");
        Assume.assumeTrue("Private dev28 combat capture not present",Files.isRegularFile(root.resolve("dev28-battle-now.png")));
        List<PortraitMatcher.Template> all=templates(catalog(),new ArrayList<>(catalog().characters.keySet()));
        List<PortraitMatcher.Template> gray=PortraitMatcher.grayscaleTemplates(all);
        for(String file:Arrays.asList("dev28-battle-now.png","dev28-live-fight-00.png","dev28-live-fight-01.png",
                "dev28-live-fight-02.png","dev28-live-fight-03.png","dev28-live-fight-04.png")) {
            Assume.assumeTrue(Files.isRegularFile(root.resolve(file)));
            PortraitMatcher.Pixels frame;try(InputStream in=Files.newInputStream(root.resolve(file))){frame=pixels(in);}
            PortraitMatcher.Match rgbFirst=PortraitMatcher.mobileStandby(frame,all,0),rgbSecond=PortraitMatcher.mobileStandby(frame,all,1);
            assertTrue(rgbFirst.reliable());assertEquals("Aemeath",rgbSecond.character);assertFalse(rgbSecond.reliable());
            PortraitMatcher.Match[] pair=PortraitMatcher.mobileStandbyPair(frame,all,gray);
            assertEquals(rgbFirst.character,pair[0].character);assertEquals(rgbFirst.score,pair[0].score,0);
            assertEquals(rgbFirst.runnerUp,pair[0].runnerUp,0);assertEquals(rgbFirst.x,pair[0].x,0);assertEquals(rgbFirst.y,pair[0].y,0);
            assertEquals("Aemeath",pair[1].character);assertTrue(pair[1].reliable());assertTrue(pair[1].score>.89);
            assertTrue(pair[1].score-pair[1].runnerUp>.24);
            // Portrait-only replay: an overlay capture is not proof that the complete HUD guard passed.
            ActiveCharacterRecognizer active=new ActiveCharacterRecognizer(Arrays.asList("ShoreKeeper","YangYangSp","Aemeath"));
            assertNull(active.observe(pair[0],pair[1],2400,1080,0,400,true));
            assertEquals(1,active.observe(pair[0],pair[1],2400,1080,1000,1400,true).active);
            assertNull(active.observe(pair[0],pair[1],2400,1080,2000,2400,false));
            ActiveCharacterRecognizer foreign=new ActiveCharacterRecognizer(Arrays.asList("ShoreKeeper","YangYangSp","Verina"));
            assertNull(foreign.observe(pair[0],pair[1],2400,1080,0,400,true));
            assertNull(foreign.observe(pair[0],pair[1],2400,1080,1000,1400,true));
        }
        for(String file:Arrays.asList("arena-shore.png","dev22-fight-03.png","dev22-fight-06.png")) {
            Assume.assumeTrue(Files.isRegularFile(root.resolve(file)));
            PortraitMatcher.Pixels frame;try(InputStream in=Files.newInputStream(root.resolve(file))){frame=pixels(in);}
            // Empty fallback catalog proves reliable RGB does not run the gray path.
            PortraitMatcher.Match[] pair=PortraitMatcher.mobileStandbyPair(frame,all,java.util.Collections.emptyList());
            for(int slot=0;slot<2;slot++) {
                PortraitMatcher.Match rgb=PortraitMatcher.mobileStandby(frame,all,slot);
                assertTrue(rgb.reliable());assertEquals(rgb.character,pair[slot].character);assertEquals(rgb.score,pair[slot].score,0);
            }
        }
    }
    @Test public void recordedConsoleNeverBecomesACharacterThroughGrayFallback() throws Exception {
        Path root=Path.of("../../.android-tools/daily-combat");
        if(!Files.isDirectory(root))root=Path.of("../.android-tools/daily-combat");
        Assume.assumeTrue("Private console negative capture not present",Files.isRegularFile(root.resolve("dev28-live-fight-05.png")));
        List<PortraitMatcher.Template> all=templates(catalog(),new ArrayList<>(catalog().characters.keySet()));
        List<PortraitMatcher.Template> gray=PortraitMatcher.grayscaleTemplates(all);
        PortraitMatcher.Pixels frame;try(InputStream in=Files.newInputStream(root.resolve("dev28-live-fight-05.png"))){frame=pixels(in);}
        for(PortraitMatcher.Match match:PortraitMatcher.mobileStandbyPair(frame,all,gray))assertFalse(match.reliable());
    }
    @Test public void measuredArenaHudAndTargetFeatureReplay() throws Exception {
        Path source=Path.of("../../.android-tools/daily-combat/arena-shore.png");
        if(!Files.isRegularFile(source))source=Path.of("../.android-tools/daily-combat/arena-shore.png");
        Assume.assumeTrue("Private arena capture not present",Files.isRegularFile(source));
        PortraitMatcher.Pixels frame;try(InputStream in=Files.newInputStream(source)){frame=pixels(in);}
        CombatHud hud=new CombatHud(frame,CombatHudTest.battle(),0);
        assertTrue(hud.concertoFull);assertFalse(hud.forteFull);
        assertEquals(CombatHud.Skill.READY,hud.skills.get(CombatTouchLayout.Control.RESONANCE));
        assertEquals(CombatHud.Skill.READY,hud.skills.get(CombatTouchLayout.Control.ECHO));
        assertEquals(CombatHud.Skill.DIM,hud.skills.get(CombatTouchLayout.Control.LIBERATION));
        try(InputStream in=getClass().getResourceAsStream("/combat-features/no_target_3840x2160_2868_1917.png")) {
            assertNotNull(in);PortraitMatcher.Template template=new PortraitMatcher.Template("no_target",pixels(in),2160);
            PortraitMatcher.Match match=PortraitMatcher.match(frame,java.util.Collections.singletonList(template),535,945,620,1025,1.6);
            System.out.println("Measured mobile target indicator: "+match.score);assertTrue(match.score>=.75);
        }
        List<PortraitMatcher.Template> all=templates(catalog(),new ArrayList<>(catalog().characters.keySet()));
        PortraitMatcher.Match first=PortraitMatcher.match(frame,all,2080,205,2280,335),second=PortraitMatcher.match(frame,all,2080,345,2280,480);
        assertEquals("YangYangSp",first.character);assertTrue(first.reliable());assertEquals("Aemeath",second.character);assertTrue(second.reliable());
    }
    @Test public void entireBuiltinRegistryAndOriginalAssetChecksumsArePackaged() throws Exception {
        BuiltinCombatCatalog catalog = catalog();
        assertEquals(57, catalog.characters.size()); assertEquals(67, catalog.hashes.size());
        assertEquals(BuiltinCombatCatalog.Role.MAIN_DPS, catalog.require("YangYangSp").role);
        assertEquals(BuiltinCombatCatalog.Role.HEALER, catalog.require("ShoreKeeper").role);
        assertEquals(28000, catalog.require("ShoreKeeper").buffMs);
        assertEquals(-1, catalog.require("Rover").element);
        assertThrows(IllegalStateException.class, () -> catalog.require("workshop:ShoreKeeper"));
        for (java.util.Map.Entry<String, String> entry : catalog.hashes.entrySet()) {
            try (InputStream input = getClass().getResourceAsStream("/portraits/" + entry.getKey())) {
                assertNotNull(entry.getKey(), input);
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.readAllBytes());
                StringBuilder hex = new StringBuilder(); for (byte value : hash) hex.append(String.format("%02x", value & 255));
                assertEquals(entry.getKey(), entry.getValue(), hex.toString());
            }
        }
    }
    @Test public void measuredStandbyCropPreservesFullSearchWinnerAcrossAllThreeLiveCharacters() throws Exception {
        Path root=Path.of("../../.android-tools/daily-combat");
        if(!Files.isDirectory(root))root=Path.of("../.android-tools/daily-combat");
        Assume.assumeTrue(Files.isRegularFile(root.resolve("dev22-fight-06.png")));
        List<PortraitMatcher.Template> all=templates(catalog(),new ArrayList<>(catalog().characters.keySet()));
        for(String file:Arrays.asList("arena-shore.png","dev22-fight-03.png","dev22-fight-06.png")) {
            PortraitMatcher.Pixels frame;try(InputStream in=Files.newInputStream(root.resolve(file))){frame=pixels(in);}
            for(int slot=0;slot<2;slot++) {
                PortraitMatcher.Match before=PortraitMatcher.match(frame,all,2080,slot==0?205:345,2280,slot==0?335:480);
                PortraitMatcher.Match after=PortraitMatcher.mobileStandby(frame,all,slot);
                assertTrue(after.reliable());assertEquals(before.character,after.character);
                assertEquals(before.score,after.score,.000001);assertEquals(before.x,after.x,.000001);
                assertEquals(before.y,after.y,.000001);
            }
        }
    }
    @Test public void realSavedPhoneHudRecognizesStandbyThenMissingActiveCharacter() throws Exception {
        // Local private account capture is optional and never packaged in the APK or committed as a fixture.
        Path source = Path.of("../.android-tools/daily-live/direct-service-details.png");
        if (!Files.isRegularFile(source)) source = Path.of("../../.android-tools/daily-live/direct-service-details.png");
        Assume.assumeTrue("Private phone capture not present", Files.isRegularFile(source));
        PortraitMatcher.Pixels frame;
        try (InputStream input = Files.newInputStream(source)) { frame = pixels(input); }
        List<String> team = Arrays.asList("ShoreKeeper", "YangYangSp", "Aemeath");
        List<PortraitMatcher.Template> all = templates(catalog(), new ArrayList<>(catalog().characters.keySet()));
        long started = System.nanoTime();
        PortraitMatcher.Match first = PortraitMatcher.match(frame, all, 2080, 205, 2280, 335);
        PortraitMatcher.Match second = PortraitMatcher.match(frame, all, 2080, 345, 2280, 480);
        System.out.println("Real mobile standby match: " + first.character + "=" + first.score + "/" + first.runnerUp
                + ", " + second.character + "=" + second.score + "/" + second.runnerUp
                + ", elapsed_ms=" + (System.nanoTime()-started)/1000000);
        assertEquals("YangYangSp", first.character); assertTrue(first.reliable());
        assertEquals("Aemeath", second.character); assertTrue(second.reliable());
        assertEquals(.931470, first.score, .000001);
        assertEquals(.888859, second.score, .000001);
        ActiveCharacterRecognizer recognizer = new ActiveCharacterRecognizer(team);
        assertNull(recognizer.observe(first, second, 2400, 1080, 0, 500, true));
        ActiveCharacterRecognizer.State result = recognizer.observe(first, second, 2400, 1080, 1000, 1500, true);
        assertNotNull(result); assertEquals(0, result.active); assertArrayEquals(new int[]{1, 2}, result.standby);
    }
    @Test public void unknownAmbiguousForeignCachedAndHiddenHudNeverReuseActiveCharacter() {
        List<String> team = Arrays.asList("ShoreKeeper", "YangYangSp", "Aemeath");
        PortraitMatcher.Match a = new PortraitMatcher.Match("YangYangSp", .97, .70, .9f, .25f);
        PortraitMatcher.Match b = new PortraitMatcher.Match("Aemeath", .94, .72, .9f, .39f);
        for (PortraitMatcher.Match bad : new PortraitMatcher.Match[]{null, a,
                new PortraitMatcher.Match("Aemeath", .84, .80, .9f, .39f),
                new PortraitMatcher.Match("Verina", .98, .70, .9f, .39f)}) {
            ActiveCharacterRecognizer r = new ActiveCharacterRecognizer(team);
            r.observe(a,b,2400,1080,0,100,true);
            assertNull(r.observe(a,bad,2400,1080,1000,1100,true));
            assertNull(r.observe(a,b,2400,1080,2000,2100,true));
            assertNotNull(r.observe(a,b,2400,1080,3000,3100,true));
            assertNull(r.observe(a,b,2400,1080,3000,3150,true));
            assertNull(r.observe(a,b,2400,1080,4000,4100,false));
            assertNull(r.observe(a,b,1920,1080,5000,5100,true));
            assertNull(r.observe(a,b,2400,1080,6000,7501,true));
        }
    }
    @Test public void flatPixelsNeverMatchEvenWithIdenticalFlatTemplate() {
        int[] frame = new int[120*60]; Arrays.fill(frame, 0xffeeeeee);
        PortraitMatcher.Match result = PortraitMatcher.match(new PortraitMatcher.Pixels(120,60,frame),
                Arrays.asList(new PortraitMatcher.Template("Fake", new PortraitMatcher.Pixels(20,20,new int[400]),60)),0,0,120,60);
        assertFalse(result.reliable());
        assertThrows(IllegalArgumentException.class, () -> new PortraitMatcher.Pixels(2, 2, new int[3]));
        assertThrows(IllegalArgumentException.class, () -> new ActiveCharacterRecognizer(Arrays.asList("A","A","B")));
        assertThrows(IllegalArgumentException.class, () -> new BuiltinCombatCatalog(new StringReader("A\tMAIN_DPS\t0\t1\t../x.png\thash")));
    }
    @Test public void freezeAndUnknownFramesDoNotAdvanceCombatClock() {
        CombatClock clock = new CombatClock();
        assertEquals(0,clock.observe(0,false,true));
        assertEquals(100,clock.observe(100,false,true));
        assertEquals(100,clock.observe(200,true,false));
        assertEquals(100,clock.observe(3000,true,false));
        assertEquals(100,clock.observe(3100,false,true));
        assertEquals(200,clock.observe(3200,false,true));
        assertEquals(200,clock.observe(5000,false,true));
        assertTrue(clock.observationGap());
        assertEquals(300,clock.observe(5100,false,true));
        assertFalse(clock.observationGap());
        assertThrows(IllegalArgumentException.class, () -> clock.observe(3200,false,true));
    }
    @Test public void slowFramesEndTheAttackBurstWithoutInventingActiveCombatTime() {
        CombatClock clock=new CombatClock();clock.observe(0,false,true);
        for(long now=1800;now<=7200;now+=1800){
            clock.observe(now,false,true);assertEquals(0,clock.activeTimeMs());
            assertTrue(clock.attackPending(0,0,now,1000));
        }
        assertFalse(clock.attackPending(0,0,8000,1000));assertEquals(0,clock.activeTimeMs());
        clock.observe(7300,false,true);assertEquals(100,clock.activeTimeMs());
        assertFalse(clock.attackPending(0,0,7300,100));
        assertThrows(IllegalArgumentException.class,()->clock.attackPending(0,8000,7300,100));
    }
}
