package io.github.okww.weekly;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;

/** A bounded touch command; coordinates refer to the captured game frame, not PC keys. */
final class CombatGesture {
    static final long MAX_DURATION_MS = 2000;
    static final class Contact {
        final float x, y, endX, endY;
        final long startMs, durationMs;
        Contact(float x, float y, long startMs, long durationMs) {
            this(x,y,x,y,startMs,durationMs);
        }
        Contact(float x, float y, float endX, float endY, long startMs, long durationMs) {
            if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0 || x >= 1 || y < 0 || y >= 1)
                throw new IllegalArgumentException("触控位置必须在画面内");
            if (!Float.isFinite(endX) || !Float.isFinite(endY) || endX < 0 || endX >= 1 || endY < 0 || endY >= 1)
                throw new IllegalArgumentException("拖动终点必须在画面内");
            if (startMs < 0 || startMs > MAX_DURATION_MS || durationMs < 1 || durationMs > MAX_DURATION_MS
                    || startMs + durationMs > MAX_DURATION_MS)
                throw new IllegalArgumentException("单次触控最长2000ms");
            this.x = x; this.y = y; this.endX=endX; this.endY=endY; this.startMs = startMs; this.durationMs = durationMs;
        }
    }
    final List<Contact> contacts;
    final long durationMs;
    final int width, height;

    CombatGesture(int width, int height, Contact... contacts) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("无效画面尺寸");
        this.width = width; this.height = height;
        if (contacts == null || contacts.length < 1 || contacts.length > 2)
            throw new IllegalArgumentException("仅支持一个或两个触点");
        for (Contact contact : contacts) if (contact == null) throw new IllegalArgumentException("触点缺失");
        this.contacts = Collections.unmodifiableList(Arrays.asList(contacts.clone()));
        long duration = 0;
        for (Contact contact : this.contacts) duration = Math.max(duration, contact.startMs + contact.durationMs);
        if (this.contacts.size() == 2) {
            Contact a = this.contacts.get(0), b = this.contacts.get(1);
            if (a.x == b.x && a.y == b.y) throw new IllegalArgumentException("双指触点重复");
        }
        durationMs = duration;
    }

    static CombatGesture tapPixels(float x, float y, int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("无效画面尺寸");
        return new CombatGesture(width, height, new Contact(x / width, y / height, 0, 90));
    }
    long timeoutMs() { return Math.max(1600, durationMs + 1000); }
    // Android continuation's first sample must contain only the already-down pointers.
    long pointerStartMs(int index, boolean continuing) { return contacts.get(index).startMs + (continuing ? 20 : 0); }
    long gestureDurationMs(boolean continuing) { return durationMs + (continuing ? 20 : 0); }
}
