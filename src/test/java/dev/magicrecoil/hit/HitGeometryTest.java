package dev.magicrecoil.hit;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HitGeometryTest {
    private final BoundingBox target = new BoundingBox(-0.3, 0, 9.7, 0.3, 1.8, 10.3);

    @Test void reconstructsHeadEntryWhenCollisionRadiusTriggersBeforeTheCenterEnters() {
        var impact = HitGeometry.projectileImpact(new Vector(0, 1.6, 8), new Vector(0, 1.6, 9.6),
                new Vector(0, 0, 2), target, 0.2);
        assertTrue(impact.precise());
        assertEquals(9.7, impact.position().getZ(), 1e-8);
        assertEquals(1.6, impact.position().getY(), 1e-8);
    }

    @Test void radiusOnlyGrazingHitDoesNotInventAHeadshot() {
        var sample = new Vector(0.4, 1.6, 10);
        var impact = HitGeometry.projectileImpact(new Vector(0.4, 1.6, 8), sample, new Vector(0, 0, 2), target, 0.2);
        assertFalse(impact.precise());
        assertEquals(sample, impact.position());
    }

    @Test void aFastSlopingShotUsesBodyEntryRatherThanTheLaterHeadSample() {
        var impact = HitGeometry.projectileImpact(new Vector(0, 0.6, 9), new Vector(0, 1.6, 10),
                new Vector(0, 1, 1), target, 0.2);
        assertTrue(impact.precise());
        assertEquals(9.7, impact.position().getZ(), 1e-8);
        assertEquals(1.3, impact.position().getY(), 1e-8);
        assertTrue(impact.position().getY() < target.getMaxY() - target.getHeight() * 0.25);
    }

    @Test void stationaryProjectileOverlappingABodyUsesTheKnownSample() {
        Vector sample = new Vector(0, 1, 10);
        var impact = HitGeometry.projectileImpact(sample, sample, new Vector(0, 0, 1), target, 0.2);
        assertTrue(impact.precise());
        assertEquals(sample, impact.position());
    }
}
