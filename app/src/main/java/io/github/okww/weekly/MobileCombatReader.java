package io.github.okww.weekly;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** On-device avatar recognition from original first-party exports; never reads process memory. */
final class MobileCombatReader {
    static final class Observation {
        final ActiveCharacterRecognizer.State state;
        final PortraitMatcher.Match first, second;
        Observation(ActiveCharacterRecognizer.State state, PortraitMatcher.Match first, PortraitMatcher.Match second) {
            this.state = state; this.first = first; this.second = second;
        }
    }
    private final List<PortraitMatcher.Template> portraits = new ArrayList<>();
    private final List<PortraitMatcher.Template> grayPortraits;
    private final ActiveCharacterRecognizer active;
    MobileCombatReader(AssetManager assets, List<String> team) throws Exception {
        active = new ActiveCharacterRecognizer(team);
        BuiltinCombatCatalog catalog;
        try (InputStreamReader source = new InputStreamReader(assets.open("builtin-combat.tsv"), StandardCharsets.UTF_8)) {
            catalog = new BuiltinCombatCatalog(source);
        }
        for (String name : team) catalog.require(name);
        // Compete against the whole builtin catalog before checking team membership, not just three guesses.
        for (String name : catalog.characters.keySet()) for (String file : catalog.require(name).portraits) {
            byte[] data;
            try (InputStream input = assets.open("portraits/" + file)) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
                data = out.toByteArray();
            }
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(); for (byte value : hash) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            if (!catalog.hashes.get(file).equals(hex.toString())) throw new IllegalStateException("头像资源校验失败：" + file);
            Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (image == null) throw new IllegalStateException("头像资源解码失败：" + file);
            try {
                String[] geometry = file.substring(0, file.length()-4).split("_");
                int sourceHeight = Integer.parseInt(geometry[geometry.length-3].split("x")[1]);
                portraits.add(new PortraitMatcher.Template(name, pixels(image), sourceHeight));
            } finally { image.recycle(); }
        }
        grayPortraits=PortraitMatcher.grayscaleTemplates(portraits);
    }
    Observation read(Bitmap frame, GardenRules.Screen screen, long capturedAt) {
        return read(frame,screen,capturedAt,false);
    }
    Observation read(Bitmap frame, GardenRules.Screen screen, long capturedAt, boolean ownedTacet) {
        if (frame.getWidth() != 2400 || frame.getHeight() != 1080)
            throw new IllegalStateException("战斗头像目前只标定了2400×1080布局");
        boolean hud = (ownedTacet ? CombatHud.tacetActive(screen) : GardenRules.worldHud(screen) || CombatHud.tacetCombat(screen)
                || TacetTreasureNavigation.postWorld(screen))
                && !screen.has("复苏|复活|挑战失败|使用药品|补充波片");
        if (!hud) return new Observation(active.observe(null, null, 2400, 1080, capturedAt,
                SystemClock.elapsedRealtime(), false), null, null);
        PortraitMatcher.Pixels image = pixels(frame);
        PortraitMatcher.Match[] pair=PortraitMatcher.mobileStandbyPair(image,portraits,grayPortraits);
        PortraitMatcher.Match first=pair[0],second=pair[1];
        return new Observation(active.observe(first, second, 2400, 1080, capturedAt,
                SystemClock.elapsedRealtime(), true), first, second);
    }
    private static PortraitMatcher.Pixels pixels(Bitmap bitmap) {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return new PortraitMatcher.Pixels(bitmap.getWidth(), bitmap.getHeight(), pixels);
    }
}
