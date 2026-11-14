package io.github.okww.weekly;

import java.util.EnumMap;
import java.util.Map;

/** No guessed default coordinates: a layout must come from a verified mobile HUD. */
final class CombatTouchLayout {
    enum Control { ATTACK, RESONANCE, LIBERATION, ECHO, JUMP, DODGE, SLOT_1, SLOT_2, SLOT_3 }
    private final EnumMap<Control, CombatGesture.Contact> positions = new EnumMap<>(Control.class);
    final int width, height;

    CombatTouchLayout(int width, int height, Map<Control, CombatGesture.Contact> positions) {
        if (width <= height || height <= 0) throw new IllegalArgumentException("战斗布局必须对应横屏尺寸");
        if (positions == null || positions.isEmpty()) throw new IllegalArgumentException("尚未标定手机战斗按钮");
        this.width = width; this.height = height;
        this.positions.putAll(positions);
        for (CombatGesture.Contact point : this.positions.values()) {
            if (point == null) throw new IllegalArgumentException("按钮位置缺失");
        }
    }

    static CombatTouchLayout forXiaomi13Pro() {
        EnumMap<Control, CombatGesture.Contact> points = new EnumMap<>(Control.class);
        // Measured from Xiaomi 13 Pro 2400x1080 real combat HUD screenshot (dev5-final.png).
        points.put(Control.ATTACK, new CombatGesture.Contact(1951f / 2400, 825f / 1080, 0, 90));
        points.put(Control.RESONANCE, new CombatGesture.Contact(1780.5f / 2400, 933f / 1080, 0, 90));
        points.put(Control.LIBERATION, new CombatGesture.Contact(1659f / 2400, 841f / 1080, 0, 90));
        points.put(Control.ECHO, new CombatGesture.Contact(1784f / 2400, 756.5f / 1080, 0, 90));
        points.put(Control.JUMP, new CombatGesture.Contact(2117.5f / 2400, 738.5f / 1080, 0, 90));
        points.put(Control.DODGE, new CombatGesture.Contact(2126.5f / 2400, 925.5f / 1080, 0, 90));
        points.put(Control.SLOT_1, new CombatGesture.Contact(2143f / 2400, 293f / 1080, 0, 90));
        points.put(Control.SLOT_2, new CombatGesture.Contact(2148f / 2400, 410f / 1080, 0, 90));
        return new CombatTouchLayout(2400, 1080, points);
    }

    CombatGesture press(Control control, long durationMs, int frameWidth, int frameHeight) {
        requireFrame(frameWidth, frameHeight);
        return new CombatGesture(width, height, contact(control, 0, durationMs));
    }
    CombatGesture holdAndPress(Control held, long holdMs, Control tapped, long tapAtMs, int frameWidth, int frameHeight) {
        requireFrame(frameWidth, frameHeight);
        return new CombatGesture(width, height, contact(held, 0, holdMs), contact(tapped, tapAtMs, 90));
    }
    CombatGesture.Contact point(Control control) {
        CombatGesture.Contact point = positions.get(control);
        if (point == null) throw new IllegalStateException("未标定按钮：" + control);
        return point;
    }
    CombatGesture.Contact heldPoint(Control control) {
        if (control != Control.ATTACK && control != Control.RESONANCE && control != Control.LIBERATION)
            throw new IllegalArgumentException("该按钮不允许持续按住");
        return point(control);
    }
    private CombatGesture.Contact contact(Control control, long startMs, long durationMs) {
        CombatGesture.Contact point = positions.get(control);
        if (point == null) throw new IllegalStateException("未标定按钮：" + control);
        return new CombatGesture.Contact(point.x, point.y, startMs, durationMs);
    }
    private void requireFrame(int frameWidth, int frameHeight) {
        if (width != frameWidth || height != frameHeight)
            throw new IllegalStateException("画面尺寸与标定不符，停止战斗输入");
    }
}
