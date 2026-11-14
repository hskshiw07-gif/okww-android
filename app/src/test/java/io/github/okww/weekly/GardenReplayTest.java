package io.github.okww.weekly;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Real PC screenshot OCR geometry; mobile ML Kit is separately tested by GardenFixtureRunner. */
public class GardenReplayTest {
    @Test public void replayTenExistingGardenScreenshots() throws Exception {
        Map<String, List<GardenRules.Text>> screens = new LinkedHashMap<>();
        Map<String, String> expected = new LinkedHashMap<>();
        InputStream resource = getClass().getResourceAsStream("/garden-replay.tsv");
        assertNotNull(resource);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] row = line.split("\t", 7);
                assertEquals(7, row.length);
                screens.computeIfAbsent(row[0], name -> new ArrayList<>()).add(new GardenRules.Text(row[6],
                        Float.parseFloat(row[2]), Float.parseFloat(row[3]), Float.parseFloat(row[4]), Float.parseFloat(row[5])));
                expected.put(row[0], row[1]);
            }
        }
        assertEquals(10, screens.size());
        for (Map.Entry<String, List<GardenRules.Text>> entry : screens.entrySet()) {
            GardenRules.Action action = new GardenRules().next(new GardenRules.Screen(entry.getValue(), new int[3], 1920f / 1080));
            assertEquals(entry.getKey(), expected.get(entry.getKey()), action.scene);
        }
    }
}
