package dev.magicrecoil.recoil;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static dev.magicrecoil.recoil.RecoilSettingsTest.settings;
import static org.junit.jupiter.api.Assertions.*;

class AimedRecoilTest {
    @Test void scalesBothAxesForAllPatternsWithoutChangingPhaseGrowthOrCap() throws Exception {
        for (String yaml : new String[] {"pattern: valorant", "pattern: vertical", "pattern: random",
                "pattern: custom\ncustom-pattern:\n  - {vertical: 1, horizontal: -0.5}\n  - {vertical: 0.2, horizontal: 1}"}) {
            var config = settings(yaml + "\nrecoil-steps: 3\ngrowth: 0.2");
            var normal = new RecoilSession("gun", config); var aimed = new RecoilSession("gun", config);
            var a = new Random(13); var b = new Random(13);
            for (int shot = 0; shot < 100; shot++) {
                long now = shot * 150_000_000L;
                normal.shoot(now, a); aimed.shoot(now, b, 0.5);
                for (int tick = 0; tick < 3; tick++) {
                    var x = normal.next(now); var y = aimed.next(now);
                    assertEquals(x.yaw() * 0.5, y.yaw(), 1e-9);
                    assertEquals(x.pitch() * 0.5, y.pitch(), 1e-9);
                }
                assertEquals(normal.shots(), aimed.shots());
            }
        }
    }
    @Test void multiplierCanChangeMidBurstAndRecoveryUsesDeliveredScaledRotation() throws Exception {
        var config = settings("pattern: vertical\nrecoil: 2\ngrowth: 0\nrecoil-steps: 1\nauto-return: true");
        var session = new RecoilSession("gun", config); var rng = new Random(1);
        session.shoot(0, rng, 0.5); var first = session.next(0); session.recordApplied(first);
        session.shoot(50_000_000, rng, 1); var second = session.next(50_000_000); session.recordApplied(second);
        assertEquals(-1, first.pitch()); assertEquals(-2, second.pitch()); assertEquals(2, session.shots());
        assertEquals(3, session.next(500_000_000).pitch());
    }
    @Test void zeroMultiplierStillAdvancesPatternAndInvalidMultipliersAreRejected() throws Exception {
        var session = new RecoilSession("gun", settings(""));
        session.shoot(0, new Random(1), 0); assertTrue(session.next(0).isZero()); assertEquals(1, session.shots());
        for (double value : new double[]{-1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> session.shoot(0, new Random(1), value));
        }
    }
}
