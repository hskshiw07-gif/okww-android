package io.github.okww.weekly;

import org.junit.Test;
import java.util.EnumMap;
import java.util.Collections;
import static io.github.okww.weekly.CombatTouchLayout.Control.*;
import static org.junit.Assert.*;

public class CombatInputTest {
    @Test public void continuousSkillUsesItsOwnCalibratedPointNotAttack() {
        CombatTouchLayout l=CombatTouchLayout.forXiaomi13Pro();
        assertEquals(1780.5f/2400,l.heldPoint(RESONANCE).x,.0001f);
        assertEquals(933f/1080,l.heldPoint(RESONANCE).y,.0001f);
        assertNotEquals(l.heldPoint(ATTACK).x,l.heldPoint(RESONANCE).x,.01f);
        assertEquals(l.point(LIBERATION),l.heldPoint(LIBERATION));
        for(CombatTouchLayout.Control c:new CombatTouchLayout.Control[]{DODGE,JUMP,ECHO,SLOT_1,SLOT_2,SLOT_3})
            assertThrows(IllegalArgumentException.class,()->l.heldPoint(c));
        assertThrows(IllegalArgumentException.class,()->l.heldPoint(null));
    }
    private CombatTouchLayout layout() {
        EnumMap<CombatTouchLayout.Control, CombatGesture.Contact> points = new EnumMap<>(CombatTouchLayout.Control.class);
        // Synthetic coordinates test geometry only, not a calibrated Wuthering Waves HUD.
        points.put(ATTACK, new CombatGesture.Contact(.8f, .8f, 0, 90));
        points.put(LIBERATION, new CombatGesture.Contact(.7f, .8f, 0, 90));
        return new CombatTouchLayout(2400, 1080, points);
    }
    @Test public void weeklyTapStillMeansNinetyMillisecondsAtSamePixel() {
        CombatGesture command = CombatGesture.tapPixels(1200, 540, 2400, 1080);
        assertEquals(1, command.contacts.size());
        assertEquals(.5f, command.contacts.get(0).x, .00001f);
        assertEquals(.5f, command.contacts.get(0).y, .00001f);
        assertEquals(90, command.durationMs);
        assertEquals(1600, command.timeoutMs());
        assertEquals(2400, command.width);
        assertEquals(1080, command.height);
    }
    @Test public void heavyUsesOneHeldTouchAndSufficientCallbackTimeout() {
        CombatGesture command = layout().press(ATTACK, 700, 2400, 1080);
        assertEquals(700, command.durationMs);
        assertEquals(1700, command.timeoutMs());
        assertEquals(1, command.contacts.size());
    }
    @Test public void finiteDragPreservesTapGeometryAndRejectsInvalidEndpoints() {
        CombatGesture.Contact tap=new CombatGesture.Contact(.2f,.8f,0,90);
        assertEquals(tap.x,tap.endX,0);assertEquals(tap.y,tap.endY,0);
        CombatGesture.Contact drag=new CombatGesture.Contact(.2f,.8f,.24f,.8f,0,650);
        assertEquals(.24f,drag.endX,0);assertEquals(650,new CombatGesture(2400,1080,drag).durationMs);
        for(float bad:new float[]{-1,1,Float.NaN,Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->new CombatGesture.Contact(.2f,.8f,bad,.8f,0,650));
            assertThrows(IllegalArgumentException.class,()->new CombatGesture.Contact(.2f,.8f,.24f,bad,0,650));
        }
    }
    @Test public void skillCanOverlapHeavyWithoutReleasingFirstFinger() {
        CombatGesture command = layout().holdAndPress(ATTACK, 850, LIBERATION, 150, 2400, 1080);
        assertEquals(2, command.contacts.size());
        assertEquals(850, command.contacts.get(0).durationMs);
        assertEquals(150, command.contacts.get(1).startMs);
        assertEquals(90, command.contacts.get(1).durationMs);
        assertEquals(850, command.durationMs);
    }
    @Test public void continuingHeldPointerStartsAloneBeforeNewSkillPointers() {
        CombatGesture c=layout().press(LIBERATION,90,2400,1080);
        assertEquals(0,c.pointerStartMs(0,false));assertEquals(90,c.gestureDurationMs(false));
        assertEquals(20,c.pointerStartMs(0,true));assertEquals(110,c.gestureDurationMs(true));
        c=layout().holdAndPress(ATTACK,850,LIBERATION,150,2400,1080);
        for(int i=0;i<c.contacts.size();i++) {
            assertTrue(c.pointerStartMs(i,true)>0);
            assertTrue(c.pointerStartMs(i,true)+c.contacts.get(i).durationMs<=c.gestureDurationMs(true));
        }
    }
    @Test public void unknownButtonOrWrongDimensionsStopsInsteadOfGuessing() {
        assertThrows(IllegalStateException.class, () -> layout().press(ECHO, 90, 2400, 1080));
        assertThrows(IllegalStateException.class, () -> layout().press(ATTACK, 90, 1920, 1080));
        assertThrows(IllegalArgumentException.class, () -> new CombatTouchLayout(1080, 2400, Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class, () -> new CombatTouchLayout(2400, 1080, Collections.emptyMap()));
    }
    @Test public void pointsRejectOutOfFrameAndNonFiniteCoordinates() {
        for (float value : new float[]{-1, 1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new CombatGesture.Contact(value, .5f, 0, 90));
            assertThrows(IllegalArgumentException.class, () -> new CombatGesture.Contact(.5f, value, 0, 90));
        }
        assertThrows(IllegalArgumentException.class, () -> CombatGesture.tapPixels(1, 1, 0, 1080));
    }
    @Test public void durationAndOffsetAreBoundedIncludingOverflowInputs() {
        for (long value : new long[]{0, -1, 2001, Long.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new CombatGesture.Contact(.5f, .5f, 0, value));
        assertThrows(IllegalArgumentException.class, () -> new CombatGesture.Contact(.5f, .5f, -1, 90));
        assertThrows(IllegalArgumentException.class, () -> new CombatGesture.Contact(.5f, .5f, Long.MAX_VALUE, 90));
        assertThrows(IllegalArgumentException.class, () -> new CombatGesture.Contact(.5f, .5f, 1950, 90));
    }
    @Test public void contactsAreImmutableAndDuplicateFingersAreRejected() {
        CombatGesture.Contact a = new CombatGesture.Contact(.5f, .5f, 0, 90);
        CombatGesture.Contact[] source = {a};
        CombatGesture command = new CombatGesture(2400, 1080, source); source[0] = null;
        assertSame(a, command.contacts.get(0));
        assertThrows(UnsupportedOperationException.class, () -> command.contacts.clear());
        assertThrows(IllegalArgumentException.class, () -> new CombatGesture(2400, 1080, a, a));
        assertThrows(IllegalArgumentException.class, () -> new CombatGesture(2400, 1080));
        assertThrows(IllegalArgumentException.class, () -> new CombatGesture(2400, 1080, (CombatGesture.Contact) null));
        assertThrows(IllegalArgumentException.class, () -> new CombatGesture(0, 1080, a));
    }
    @Test public void xiaomi13ProCalibratedCombatLayoutMatchesMeasuredButtons() {
        CombatTouchLayout calibrated = CombatTouchLayout.forXiaomi13Pro();
        assertEquals(1951f / 2400, calibrated.point(ATTACK).x, .00001f);
        assertEquals(825f / 1080, calibrated.point(ATTACK).y, .00001f);
        assertEquals(1780.5f / 2400, calibrated.point(RESONANCE).x, .00001f);
        assertEquals(1659f / 2400, calibrated.point(LIBERATION).x, .00001f);
        assertEquals(1784f / 2400, calibrated.point(ECHO).x, .00001f);
        assertEquals(2117.5f / 2400, calibrated.point(JUMP).x, .00001f);
        assertEquals(2126.5f / 2400, calibrated.point(DODGE).x, .00001f);
        assertEquals(2143f / 2400, calibrated.point(SLOT_1).x, .00001f);
        assertEquals(2148f / 2400, calibrated.point(SLOT_2).x, .00001f);
        assertThrows(IllegalStateException.class, () -> calibrated.point(SLOT_3));
    }
}
