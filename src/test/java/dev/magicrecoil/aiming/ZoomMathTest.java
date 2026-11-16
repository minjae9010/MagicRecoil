package dev.magicrecoil.aiming;

import dev.magicrecoil.packet.PaperZoomAdapter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZoomMathTest {
    @Test void packetSpeedProducesRequestedVanillaFovFactorWithoutChangingTheMovementAttribute() {
        for (double speed : new double[]{0.065, 0.1, 0.12}) for (double factor : new double[]{0.55, 0.65, 0.9, 1}) {
            float packet = PaperZoomAdapter.walkingSpeed(speed, factor);
            assertTrue(Float.isFinite(packet)); assertTrue(packet > 0);
            assertEquals(factor, (speed / packet + 1) / 2, 1e-6);
        }
        assertThrows(IllegalArgumentException.class, () -> PaperZoomAdapter.walkingSpeed(0.1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> PaperZoomAdapter.walkingSpeed(Double.NaN, 0.65));
    }
}
