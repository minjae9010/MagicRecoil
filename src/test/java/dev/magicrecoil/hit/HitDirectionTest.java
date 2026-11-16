package dev.magicrecoil.hit;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HitDirectionTest {
    @Test void allSixSidesAreRelativeToVictimAtEveryCardinalYaw() {
        for (float yaw : new float[]{0, 90, 180, 270, -90}) {
            double radians = Math.toRadians(yaw);
            Vector forward = new Vector(-Math.sin(radians), 0, Math.cos(radians));
            Vector right = new Vector(-Math.cos(radians), 0, -Math.sin(radians));
            assertEquals(HitDirection.FRONT, HitDirection.classify(forward.clone().multiply(-1), yaw, 45));
            assertEquals(HitDirection.BACK, HitDirection.classify(forward, yaw, 45));
            assertEquals(HitDirection.RIGHT, HitDirection.classify(right.clone().multiply(-1), yaw, 45));
            assertEquals(HitDirection.LEFT, HitDirection.classify(right, yaw, 45));
            assertEquals(HitDirection.ABOVE, HitDirection.classify(new Vector(0, -3, 0), yaw, 45));
            assertEquals(HitDirection.BELOW, HitDirection.classify(new Vector(0, 3, 0), yaw, 45));
        }
    }

    @Test void shallowAngleKeepsItsHorizontalSide() {
        assertEquals(HitDirection.BACK, HitDirection.classify(new Vector(0, -0.2, 1), 0, 45));
        assertEquals(HitDirection.ABOVE, HitDirection.classify(new Vector(0, -2, 1), 0, 45));
        assertThrows(IllegalArgumentException.class, () -> HitDirection.classify(new Vector(), 0, 45));
    }
}
