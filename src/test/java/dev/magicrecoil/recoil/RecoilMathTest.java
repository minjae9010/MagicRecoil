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
        var peak = s.pattern().sample(s, 999990, random);
        assertEquals(8, Math.hypot(peak.yaw(), peak.pitch()), 1e-6);
    }

    @Test void valorantSwaysBothWaysAndVerticalStaysVertical() throws Exception {
        var s = settings("pattern: valorant");
        assertTrue(s.pattern().sample(s, 6, random).yaw() > 0);
        assertTrue(s.pattern().sample(s, 12, random).yaw() < 0);
        var vertical = settings("pattern: vertical\npattern-randomness: 1");
        for (int i = 0; i < 50; i++) assertEquals(0, vertical.pattern().sample(vertical, i, random).yaw());
    }

    @Test void valorantRiseTapersThenStaysHorizontalBeyondOneMagazine() throws Exception {
        var s = settings("recoil: 1.5\ngrowth: 0\nrecoil-steps: 2\nvalorant-max-rise: 14.4");
        var session = new RecoilSession("ar", s);
        double totalPitch = 0;
        boolean left = false, right = false;
        for (int shot = 0; shot < 100; shot++) {
            long now = shot * 100_000_000L;
            session.shoot(now, random);
            var first = session.next(now);
            var second = session.next(now + 50_000_000L);
            double pitch = first.pitch() + second.pitch();
            if (shot == 0) assertEquals(-1.5, pitch, 1e-9);
            if (shot == 2) assertEquals(-1.5, pitch, 1e-9);
            if (shot < 4) assertEquals(0, first.yaw() + second.yaw(), 1e-9);
            if (shot >= 10) assertEquals(0, pitch, 1e-9);
            left |= first.yaw() < 0;
            right |= first.yaw() > 0;
            totalPitch += pitch;
        }
        assertEquals(-14.4, totalPitch, 1e-9);
        assertTrue(left && right);
        // A burst gap restarts the rise rather than retaining the horizontal-only phase.
        session.shoot(10_300_000_000L, random);
        assertEquals(1, session.shots());
        assertEquals(-1.5, session.next(10_300_000_000L).pitch()
                + session.next(10_350_000_000L).pitch(), 1e-9);
    }

    @Test void valorantRiseCapWorksWithVerticalNoiseAndPositiveFloor() throws Exception {
        var s = settings("vertical-min: 1\npattern-randomness: 1\ngrowth: 1\nrecoil-steps: 1");
        var session = new RecoilSession("ar", s);
        double pitch = 0;
        for (int shot = 0; shot < 1000; shot++) {
            long now = shot * 100_000_000L;
            session.shoot(now, random);
            var delta = session.next(now);
            pitch += delta.pitch();
            if (shot >= 50) assertEquals(0, delta.pitch(), 0);
        }
        assertEquals(-15, pitch, 1e-9);
    }

    @Test void valorantYawCrossesBothSidesOfBurstCenterWithBalancedExcursions() throws Exception {
        var s = settings("recoil: 1\nvertical: 0\nhorizontal: 1\ngrowth: 0");
        double yaw = 0;
        for (int shot = 0; shot < 100; shot++) {
            yaw += s.pattern().sample(s, shot, random).yaw();
            if (shot == 6 || shot == 18) assertEquals(2, yaw, 1e-9);
            if (shot == 12 || shot == 24) assertEquals(-2, yaw, 1e-9);
            if (shot >= 9 && (shot - 9) % 6 == 0) assertEquals(0, yaw, 1e-9);
        }
    }

    @Test void valorantYawKeepsCenterCrossingsWhenGrowthChangesStrength() throws Exception {
        var s = settings("recoil: 1\nvertical: 0\nhorizontal: 1\ngrowth: 0.05\nmax-recoil: 45");
        double yaw = 0;
        for (int shot = 0; shot < 60; shot++) {
            yaw += s.pattern().sample(s, shot, random).yaw();
            if (shot == 6) assertEquals(2.6, yaw, 1e-9);
            if (shot == 12) assertEquals(-3.2, yaw, 1e-9);
            if (shot >= 9 && (shot - 9) % 6 == 0) assertEquals(0, yaw, 1e-9);
        }
    }

    @Test void valorantRiseLimitIsAnAngleRatherThanAFixedBulletCount() throws Exception {
        for (double recoil : new double[]{1, 2}) {
            var s = settings("recoil: " + recoil + "\ngrowth: 0\nrecoil-steps: 1\nvalorant-max-rise: 6");
            var session = new RecoilSession("ar", s);
            int risingShots = 0;
            double pitch = 0;
            for (int shot = 0; shot < 30; shot++) {
                long now = shot * 100_000_000L;
                session.shoot(now, random);
                var delta = session.next(now);
                if (delta.pitch() < 0) risingShots++;
                pitch += delta.pitch();
            }
            assertEquals(6 / recoil, risingShots);
            assertEquals(-6, pitch, 1e-9);
        }
    }

    @Test void discardedValorantRiseDoesNotAttenuateHorizontalRecoil() throws Exception {
        var s = settings("recoil: 2\nvertical: 10\nhorizontal: 0.4\ngrowth: 0\nrecoil-steps: 1\nvalorant-max-rise: 0");
        var session = new RecoilSession("ar", s);
        for (int shot = 0; shot <= 4; shot++) {
            long now = shot * 100_000_000L;
            session.shoot(now, random);
            var delta = session.next(now);
            assertEquals(0, delta.pitch(), 0);
            if (shot == 4) assertEquals(0.8, delta.yaw(), 1e-9);
        }
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
