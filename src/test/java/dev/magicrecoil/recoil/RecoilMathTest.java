package dev.magicrecoil.recoil;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static dev.magicrecoil.recoil.RecoilSettingsTest.settings;
import static org.junit.jupiter.api.Assertions.*;

class RecoilMathTest {
    private final Random random = new Random(7);

    @Test void firstShotRisesAndGrowthIsBounded() throws Exception {
        var s = settings("recoil: 2.5");
        assertEquals(new RotationDelta(0, -2.5), s.pattern().sample(s, 0, random));
        assertEquals(-2.7, s.pattern().sample(s, 1, random).pitch(), 1e-9);
        assertEquals(8, Math.hypot(s.pattern().sample(s, 999999, random).yaw(), s.pattern().sample(s, 999999, random).pitch()), 1e-6);
    }

    @Test void valorantSwaysBothWaysAndVerticalStaysVertical() throws Exception {
        var s = settings("pattern: valorant");
        assertTrue(s.pattern().sample(s, 6, random).yaw() > 0);
        assertTrue(s.pattern().sample(s, 12, random).yaw() < 0);
        var vertical = settings("pattern: vertical\npattern-randomness: 1");
        for (int i = 0; i < 50; i++) assertEquals(0, vertical.pattern().sample(vertical, i, random).yaw());
    }

    @Test void randomBoundsAndOrderAreRespected() throws Exception {
        var s = settings("pattern: random\nrecoil: 2\nvertical: 0.5\nhorizontal: 2\nvertical-min: 0.5\nvertical-max: 1\nhorizontal-min: -0.5\nhorizontal-max: 0.5\nhorizontal-bias: 0.1\ngrowth: 0\npattern-randomness: 0.2");
        for (int i = 0; i < 1000; i++) {
            var value = s.pattern().sample(s, i, random);
            assertTrue(value.pitch() >= -1 && value.pitch() <= -0.5);
            assertTrue(value.yaw() >= -2 && value.yaw() <= 2);
        }
    }

    @Test void customRepeatPolicies() throws Exception {
        String base = "pattern: custom\nrecoil: 1\nhorizontal: 1\ngrowth: 0\ncustom-pattern:\n - {vertical: 1, horizontal: -1}\n - {vertical: 2, horizontal: 1}\n";
        var loop = settings(base + "pattern-repeat: loop");
        var hold = settings(base + "pattern-repeat: hold");
        var stop = settings(base + "pattern-repeat: stop");
        assertEquals(new RotationDelta(-1, -1), loop.pattern().sample(loop, 2, random));
        assertEquals(new RotationDelta(1, -2), hold.pattern().sample(hold, 2, random));
        assertEquals(RotationDelta.ZERO, stop.pattern().sample(stop, 2, random));
    }

    @Test void interpolationConservesImpulseAndFinishes() {
        for (int steps = 1; steps <= 20; steps++) {
            var buffer = new ImpulseBuffer();
            buffer.add(new RotationDelta(2, -5), steps);
            double yaw = 0, pitch = 0, previousMagnitude = Double.POSITIVE_INFINITY;
            for (int i = 0; i < steps; i++) {
                var d = buffer.poll();
                yaw += d.yaw(); pitch += d.pitch();
                assertTrue(Math.abs(d.pitch()) <= previousMagnitude);
                previousMagnitude = Math.abs(d.pitch());
            }
            assertEquals(2, yaw, 1e-9); assertEquals(-5, pitch, 1e-9);
            assertTrue(buffer.isEmpty()); assertEquals(RotationDelta.ZERO, buffer.poll());
        }
    }

    @Test void sustainedFireMergesWithoutLostImpulseOrGrowingTail() {
        var buffer = new ImpulseBuffer();
        double total = 0;
        for (int i = 0; i < 100_000; i++) { buffer.add(new RotationDelta(0, -0.1), 20); total += buffer.poll().pitch(); }
        for (int i = 0; i < 20; i++) total += buffer.poll().pitch();
        assertEquals(-10_000, total, 1e-6); assertTrue(buffer.isEmpty());
    }

    @Test void extremeBurstsAreClippedAndClearDropsAllPendingWork() {
        var buffer = new ImpulseBuffer();
        for (int i = 0; i < 1000; i++) buffer.add(new RotationDelta(45, -45), 1);
        var d = buffer.poll(); assertEquals(45, Math.hypot(d.yaw(), d.pitch()), 1e-9);
        buffer.add(new RotationDelta(2, -3), 20); buffer.clear();
        assertTrue(buffer.isEmpty()); assertEquals(RotationDelta.ZERO, buffer.poll());
    }

    @Test void stopGapResetsGrowthAndCancelsOldTail() throws Exception {
        var s = settings("recoil-steps: 1\npattern: vertical");
        var session = new RecoilSession("ar", s);
        session.shoot(0, random); assertEquals(-2.5, session.next(0).pitch());
        session.shoot(100_000_000, random); assertEquals(-2.7, session.next(100_000_000).pitch(), 1e-9);
        session.shoot(500_000_000, random); assertEquals(-2.5, session.next(500_000_000).pitch());
        assertEquals(1, session.shots()); assertTrue(session.finished(900_000_000));
    }

    @Test void defaultDoesNotReturnAndOptInUsesDeliveredDelta() throws Exception {
        var normal = new RecoilSession("ar", settings("recoil-steps: 1"));
        normal.shoot(0, random); normal.recordApplied(normal.next(0));
        assertEquals(RotationDelta.ZERO, normal.next(500_000_000));
        var recovery = new RecoilSession("ar", settings("auto-return: true\nrecoil-steps: 1"));
        recovery.shoot(0, random); recovery.next(0);
        recovery.recordApplied(new RotationDelta(0, -0.5)); // pitch clamp applied only 0.5 degrees
        assertEquals(new RotationDelta(0, 0.5), recovery.next(500_000_000));
        assertTrue(recovery.finished(500_000_000));
    }

    @Test void rejectsNonfiniteRotations() {
        assertThrows(IllegalArgumentException.class, () -> new RotationDelta(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new RotationDelta(0, Double.POSITIVE_INFINITY));
    }
}
