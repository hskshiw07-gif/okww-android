package io.github.okww.weekly;

import org.junit.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class TeamNameRecognizerTest {
    @Test public void liveTeamSevenKeepsSlotOrderAndRequiresTwoFreshFrames() throws Exception {
        TeamNameRecognizer r;
        try (InputStreamReader catalog = new InputStreamReader(getClass().getResourceAsStream("/builtin-characters.tsv"), StandardCharsets.UTF_8)) {
            r = TeamNameRecognizer.forXiaomi13Pro(catalog, 2400, 1080);
        }
        GardenRules.Screen frame = new GardenRules.Screen(new ArrayList<>(Arrays.asList(
                new GardenRules.Text("武队伍7L", 136f/2400, 55f/1080, 296f/2400, 85f/1080),
                new GardenRules.Text("、绯雪", 821f/2400, 835f/1080, 901f/2400, 864f/1080),
                new GardenRules.Text("「爱弥斯", 1366f/2400, 835f/1080, 1460f/2400, 862f/1080),
                new GardenRules.Text("心", 1982f/2400, 837f/1080, 2026f/2400, 862f/1080),
                new GardenRules.Text("快速骗队", 1691f/2400, 963f/1080, 1806f/2400, 991f/1080),
                new GardenRules.Text("已出战", 2023f/2400, 961f/1080, 2108f/2400, 989f/1080))), new int[3], 2400f/1080);
        assertNull(r.observe(frame, 2400, 1080, 0, 750));
        assertEquals(Arrays.asList("Hiyuki", "Aemeath", "Hsin"), r.observe(frame, 2400, 1080, 1000, 1750));
        frame.words.set(0, t("武队伍7", .08f, .06f));
        frame.words.set(5, t("开启挑战", .86f, .91f));
        assertNotNull(r.observe(frame, 2400, 1080, 2000, 2750));
        frame.words.remove(5);
        assertNull(r.observe(frame, 2400, 1080, 3000, 3750));
    }

    @Test public void liveEntryCommaBorderKeepsExactCharacterAndTwoFrames()throws Exception{
        GardenRules.Screen frame=screen(false,"、嘉贝莉娜","秧秧·玄翎","千咲");
        frame.words.add(t("武队伍4",.08f,.06f));frame.words.add(t("快速编队",.73f,.91f));frame.words.add(t("开启挑战",.86f,.91f));
        TeamNameRecognizer r=recognizer();assertNull(r.observe(frame,2400,1080,0,750));
        assertEquals(Arrays.asList("Galbrena","YangYangSp","Chisa"),r.observe(frame,2400,1080,1000,1750));
        for(String invalid:Arrays.asList("、嘉贝莉","、嘉贝莉娜专用","、、嘉贝莉娜")){
            TeamNameRecognizer bad=recognizer();GardenRules.Screen wrong=screen(true,invalid,"秧秧·玄翎","千咲");
            assertNull(bad.observe(wrong,2400,1080,0,750));assertNull(bad.observe(wrong,2400,1080,1000,1750));
        }
        frame.words.remove(frame.words.size()-1);assertNull(r.observe(frame,2400,1080,2000,2750));
    }
    @Test public void exactLiveEntryBoxesReplayOnCalibratedXiaomiSlots()throws Exception{
        List<GardenRules.Text> words=new ArrayList<>();
        try(java.io.BufferedReader reader=new java.io.BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/daily-entry-names.tsv"),StandardCharsets.UTF_8))){
            String row;while((row=reader.readLine())!=null){String[] f=row.split("\t");words.add(new GardenRules.Text(f[0],Float.parseFloat(f[1])/2400,Float.parseFloat(f[2])/1080,Float.parseFloat(f[3])/2400,Float.parseFloat(f[4])/1080));}
        }
        TeamNameRecognizer r;try(InputStreamReader catalog=new InputStreamReader(getClass().getResourceAsStream("/builtin-characters.tsv"),StandardCharsets.UTF_8)){r=TeamNameRecognizer.forXiaomi13Pro(catalog,2400,1080);}
        GardenRules.Screen frame=new GardenRules.Screen(words,new int[3],2400f/1080);
        assertNull(r.observe(frame,2400,1080,0,750));
        assertEquals(Arrays.asList("Galbrena","YangYangSp","Chisa"),r.observe(frame,2400,1080,1000,1750));
    }
    @Test public void everyBuiltinAliasWorksAfterSharedOcrNormalizationInAllThreeSlots() throws Exception {
        int classes = 0, observations = 0;
        try (java.io.BufferedReader catalog = new java.io.BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/builtin-characters.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = catalog.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] row = line.split("\t");
                List<String> fillers = new ArrayList<>(Arrays.asList("Verina", "Sanhua", "ShoreKeeper"));
                fillers.remove(row[0]);
                for (int alias = 1; alias < row.length; alias++) for (int slot = 0; slot < 3; slot++) {
                    String[] labels = {fillers.get(0), fillers.get(1), fillers.get(0)};
                    List<String> expected = new ArrayList<>(); int filler = 0;
                    for (int i = 0; i < 3; i++) {
                        labels[i] = i == slot ? "「" + row[alias] + "」" : fillers.get(filler++);
                        expected.add(i == slot ? row[0] : labels[i]);
                    }
                    TeamNameRecognizer r = recognizer(); GardenRules.Screen frame = screen(true, labels);
                    assertNull(r.observe(frame, 2400, 1080, 0, 750));
                    assertEquals(row[0] + "/" + row[alias] + "/slot" + slot, expected,
                            r.observe(frame, 2400, 1080, 1000, 1750));
                    observations++;
                }
                classes++;
            }
        }
        assertEquals(57, classes); assertTrue(observations >= 57 * 3);
    }
    // Synthetic name-label geometry: these are not claimed to be actual phone slot coordinates.
    private TeamNameRecognizer.Region[] slots() {
        return new TeamNameRecognizer.Region[]{new TeamNameRecognizer.Region(.05f, .2f, .30f, .7f),
                new TeamNameRecognizer.Region(.35f, .2f, .60f, .7f), new TeamNameRecognizer.Region(.65f, .2f, .90f, .7f)};
    }
    private GardenRules.Text t(String value, float x, float y) {
        return new GardenRules.Text(value, x - .015f, y - .015f, x + .015f, y + .015f);
    }
    private GardenRules.Screen screen(boolean context, String... names) {
        List<GardenRules.Text> words = new ArrayList<>();
        if (context) words.addAll(Arrays.asList(t("编队", .1f, .05f), t("出战", .9f, .92f), t("快速编队", .4f, .92f)));
        for (int i = 0; i < names.length; i++) words.add(t(names[i], .175f + i * .30f, .5f));
        return new GardenRules.Screen(words, new int[3], 2400f / 1080);
    }
    private TeamNameRecognizer recognizer() throws Exception {
        InputStream input = getClass().getResourceAsStream("/builtin-characters.tsv");
        assertNotNull("Generated builtin catalog must be included", input);
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return new TeamNameRecognizer(reader, 2400, 1080, slots());
        }
    }
    @Test public void actualCatalogRecognizesChineseTraditionalAndEnglishWithoutUserTeamConfig() throws Exception {
        for (String[] names : new String[][]{{"维里奈", "散华", "守岸人"}, {"維里奈", "散華", "守岸人"},
                {"Ｖｅｒｉｎａ", "Sanhua", "Shore Keeper"}}) {
            TeamNameRecognizer r = recognizer(); GardenRules.Screen frame = screen(true, names);
            assertNull(r.observe(frame, 2400, 1080, 0, 750));
            List<String> team = r.observe(frame, 2400, 1080, 1000, 1750);
            assertEquals(Arrays.asList("Verina", "Sanhua", "ShoreKeeper"), team);
            assertThrows(UnsupportedOperationException.class, () -> team.clear());
        }
    }
    @Test public void recordedEntryParenthesisAndQuickTeamGlyphDoNotLoseExactNames() throws Exception {
        TeamNameRecognizer r=recognizer();GardenRules.Screen frame=screen(false,"(守岸人」","秧秧·玄翎","爱弥斯");
        frame.words.add(t("式队伍3",.08f,.06f));frame.words.add(t("快速骗队",.73f,.91f));frame.words.add(t("开启挑战",.86f,.91f));
        assertNull(r.observe(frame,2400,1080,0,750));
        assertEquals(Arrays.asList("ShoreKeeper","YangYangSp","Aemeath"),r.observe(frame,2400,1080,1000,1750));
        frame.words.remove(frame.words.size()-1);assertNull(r.observe(frame,2400,1080,2000,2750));
        assertNull(recognizer().observe(screen(true,"(守人」","秧秧·玄翎","爱弥斯"),2400,1080,0,750));
    }
    @Test public void slotOrderIsPreservedRatherThanSortedByHeroName() throws Exception {
        TeamNameRecognizer r = recognizer(); GardenRules.Screen frame = screen(true, "散华", "守岸人", "维里奈");
        r.observe(frame, 2400, 1080, 0, 750);
        assertEquals(Arrays.asList("Sanhua", "ShoreKeeper", "Verina"), r.observe(frame, 2400, 1080, 1000, 1750));
    }
    @Test public void unknownOrDuplicateNameClearsPreviousCandidateAndNeverReusesOldTeam() throws Exception {
        for (GardenRules.Screen bad : new GardenRules.Screen[]{screen(true, "维里奈", "散华", "未知角色"),
                screen(true, "维里奈", "散华", "散华"), screen(true, "维里奈", "散华")}) {
            TeamNameRecognizer r = recognizer(); GardenRules.Screen frame = screen(true, "维里奈", "散华", "守岸人");
            r.observe(frame, 2400, 1080, 0, 750);
            assertNull(r.observe(bad, 2400, 1080, 1000, 1750));
            assertNull(r.observe(frame, 2400, 1080, 2000, 2750));
            assertNotNull(r.observe(frame, 2400, 1080, 3000, 3750));
        }
    }
    @Test public void repeatedCachedOrStaleFramesCannotSatisfyTwoFrameGate() throws Exception {
        TeamNameRecognizer r = recognizer(); GardenRules.Screen frame = screen(true, "维里奈", "散华", "守岸人");
        r.observe(frame, 2400, 1080, 0, 750);
        assertNull(r.observe(frame, 2400, 1080, 0, 900));
        assertNull(r.observe(frame, 2400, 1080, 1000, 1750));
        assertNull(r.observe(frame, 2400, 1080, 2000, 5000));
        assertNull(r.observe(frame, 2400, 1080, 3000, 3750));
        assertNotNull(r.observe(frame, 2400, 1080, 4000, 4750));
    }
    @Test public void longConfirmationGapOrWrongScreenRequiresTwoNewObservations() throws Exception {
        TeamNameRecognizer r = recognizer(); GardenRules.Screen frame = screen(true, "维里奈", "散华", "守岸人");
        r.observe(frame, 2400, 1080, 0, 750);
        assertNull(r.observe(frame, 2400, 1080, 4000, 4750));
        assertNull(r.observe(screen(false, "维里奈", "散华", "守岸人"), 2400, 1080, 5000, 5750));
        assertNull(r.observe(frame, 2400, 1080, 6000, 6750));
        assertNotNull(r.observe(frame, 2400, 1080, 7000, 7750));
    }
    @Test public void twoDifferentCharacterNamesInOneSlotAreAmbiguous() throws Exception {
        GardenRules.Screen base = screen(true, "维里奈", "散华", "守岸人");
        List<GardenRules.Text> words = new ArrayList<>(base.words); words.add(t("今汐", .20f, .6f));
        TeamNameRecognizer r = recognizer();
        assertNull(r.observe(new GardenRules.Screen(words, new int[3]), 2400, 1080, 0, 750));
    }
    @Test public void overlappingRegionsAndAliasCollisionsAreRejected() throws Exception {
        TeamNameRecognizer.Region[] regions = slots(); regions[1] = regions[0];
        assertThrows(IllegalArgumentException.class, () -> new TeamNameRecognizer(new StringReader("Verina\t维里奈\n"), 2400, 1080, regions));
        assertThrows(IllegalArgumentException.class, () -> new TeamNameRecognizer.Region(Float.NaN, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new TeamNameRecognizer(new StringReader("Verina\t维里奈\nOther\t维里奈\n"), 2400, 1080, slots()));
        assertThrows(IllegalArgumentException.class, () -> new TeamNameRecognizer(new StringReader(""), 2400, 1080, slots()));
    }
    @Test public void resolutionChangeInvalidatesPendingRecognition() throws Exception {
        TeamNameRecognizer r = recognizer(); GardenRules.Screen frame = screen(true, "维里奈", "散华", "守岸人");
        r.observe(frame, 2400, 1080, 0, 750);
        assertThrows(IllegalStateException.class, () -> r.observe(frame, 1920, 1080, 1000, 1750));
        assertNull(r.observe(frame, 2400, 1080, 2000, 2750));
        assertNotNull(r.observe(frame, 2400, 1080, 3000, 3750));
    }

    @Test public void actualXiaomiTeamNamesAndOcrBorderAreRecognizedInSlotOrder() throws Exception {
        InputStream input = getClass().getResourceAsStream("/builtin-characters.tsv");
        assertNotNull(input);
        TeamNameRecognizer r;
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            r = TeamNameRecognizer.forXiaomi13Pro(reader, 2400, 1080);
        }
        GardenRules.Screen frame = new GardenRules.Screen(Arrays.asList(
                new GardenRules.Text("武队伍3L", 136f/2400, 55f/1080, 295f/2400, 85f/1080),
                new GardenRules.Text("「守岸人", 802f/2400, 836f/1080, 885f/2400, 863f/1080),
                new GardenRules.Text("秧秧·玄翎", 1325f/2400, 835f/1080, 1469f/2400, 863f/1080),
                new GardenRules.Text("爱弥斯", 1948f/2400, 839f/1080, 2016f/2400, 861f/1080),
                new GardenRules.Text("快速编队", 1691f/2400, 963f/1080, 1806f/2400, 991f/1080),
                new GardenRules.Text("已出战", 2023f/2400, 961f/1080, 2108f/2400, 989f/1080)), new int[3], 2400f/1080);
        assertNull(r.observe(frame, 2400, 1080, 0, 800));
        assertEquals(Arrays.asList("ShoreKeeper", "YangYangSp", "Aemeath"), r.observe(frame, 2400, 1080, 1500, 2300));
        assertThrows(IllegalStateException.class, () -> TeamNameRecognizer.forXiaomi13Pro(new StringReader(""), 1920, 1080));
    }

    @Test public void borderNormalizationDoesNotAcceptPartialCharacterNames() throws Exception {
        TeamNameRecognizer r = recognizer();
        assertNull(r.observe(screen(true, "守岸人专用", "散华", "维里奈"), 2400, 1080, 0, 750));
        assertNull(r.observe(screen(true, "守岸人专用", "散华", "维里奈"), 2400, 1080, 1000, 1750));
    }
}
