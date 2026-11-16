package dev.magicrecoil.hit;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pose;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static dev.magicrecoil.hit.HitSettingsTest.settings;
import static org.junit.jupiter.api.Assertions.*;

class HitResultTest {
    private final BoundingBox standing = new BoundingBox(-0.3, 0, -0.3, 0.3, 1.8, 0.3);

    @Test void multipliesHeadDirectionDistanceAndPowerExactlyOnce() throws Exception {
        var config = settings("damage: 10\ndirection-multipliers: {back: 1.5}\ndistance-damage: [{distance: 0, multiplier: 1}, {distance: 20, multiplier: 0.5}]");
        Vector hit = new Vector(0, 1.6, -0.3);
        var result = HitResult.evaluate(config, EntityType.PLAYER, standing, hit, true,
                hit.clone().add(new Vector(0, 0, -10)), new Vector(0, 0, 1), 0, 2);
        assertTrue(result.headshot());
        assertEquals(HitDirection.BACK, result.direction());
        assertEquals(10, result.distance());
        assertEquals(2.25, result.multiplier());
        assertEquals(45, result.damage());
        assertEquals(List.of("spell-on-hit", "spell-on-headshot", "spell-on-back"), result.hookKeys());
    }

    @Test void missingPreciseLocationAndNonHumanoidsNeverGetAutomaticHeadshots() throws Exception {
        var config = settings("");
        for (var entry : List.of(EntityType.PLAYER, EntityType.COW)) {
            var result = HitResult.evaluate(config, entry, standing, new Vector(0, 1.7, 0), entry != EntityType.PLAYER,
                    new Vector(0, 1.7, -10), new Vector(0, 0, 1), 0, 1);
            assertFalse(result.headshot());
            assertEquals(4, result.damage());
        }
    }

    @Test void headRegionFollowsTheCurrentPoseBoundingBoxAndAcceptsSurfacePoints() throws Exception {
        var config = settings("");
        var crouching = new BoundingBox(-0.3, 0, -0.3, 0.3, 1.5, 0.3);
        Vector hit = new Vector(0, 1.2, -0.3);
        var crouch = HitResult.evaluate(config, EntityType.PLAYER, crouching, hit, true,
                new Vector(0, 1.2, -10), new Vector(0, 0, 1), 0, 1);
        var stand = HitResult.evaluate(config, EntityType.PLAYER, standing, hit, true,
                new Vector(0, 1.2, -10), new Vector(0, 0, 1), 0, 1);
        assertTrue(crouch.headshot());
        assertFalse(stand.headshot());
    }

    @Test void zeroDamageSupportsEffectOnlyHitsAndPowerCanBeIgnored() throws Exception {
        var config = settings("damage: 0");
        var result = HitResult.evaluate(config, EntityType.PLAYER, standing, new Vector(0, 1, 0), true,
                new Vector(0, 1, -10), new Vector(0, 0, 1), 0, 2);
        assertEquals(0, result.damage());
        assertEquals("spell-on-bodyshot", result.hookKeys().get(1));
        config = settings("power-affects-damage: false");
        assertEquals(4, HitResult.evaluate(config, EntityType.PLAYER, standing, new Vector(0, 1, 0), true,
                new Vector(0, 1, -10), new Vector(0, 0, 1), 0, 10).damage());
    }

    @Test void invalidPowersCannotHealOrPoisonDamage() throws Exception {
        var config = settings("");
        for (float power : new float[]{-1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> HitResult.evaluate(config, EntityType.PLAYER, standing,
                    new Vector(0, 1, 0), true, new Vector(0, 1, -10), new Vector(0, 0, 1), 0, power));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Pose.class, names = {"SWIMMING", "FALL_FLYING"})
    void horizontalPlayerEyesAreHeadshotsWhileLowerBodyStaysBody(Pose pose) throws Exception {
        var config = settings("damage: 2");
        // Measurements from the live Player failures, including the float-sized Bukkit dimensions.
        var bounds = new BoundingBox(-0.3, 70, -0.3, 0.3, 70.60000002384186, 0.3);
        double eyeY = 70.40000000596046;
        for (double offset : new double[]{0, -4.8e-9}) {
            var result = HitResult.evaluate(config, EntityType.PLAYER, bounds, new Vector(0, eyeY + offset, 0.3),
                    true, new Vector(0, eyeY, 6), new Vector(0, 0, -1), 0, 1, pose, eyeY);
            assertTrue(result.headshot(), "Eye-height hits, including projectile float rounding, must hit the head");
            assertEquals(4, result.damage());
            assertEquals(List.of("spell-on-hit", "spell-on-headshot", "spell-on-front"), result.hookKeys());
        }
        var body = HitResult.evaluate(config, EntityType.PLAYER, bounds, new Vector(0, 70.21, 0.3),
                true, new Vector(0, 70.21, 6), new Vector(0, 0, -1), 0, 1, pose, eyeY);
        assertFalse(body.headshot());
        assertEquals(2, body.damage());
        assertEquals("spell-on-bodyshot", body.hookKeys().get(1));
        var justBelow = HitResult.evaluate(config, EntityType.PLAYER, bounds, new Vector(0, eyeY - 0.001, 0.3),
                true, new Vector(0, eyeY, 6), new Vector(0, 0, -1), 0, 1, pose, eyeY);
        assertFalse(justBelow.headshot(), "The correction must not make the entire horizontal box a head");
    }

    @Test void standingAndCrouchingRetainTheirConfiguredUpperRegions() throws Exception {
        var config = settings("");
        for (Pose pose : new Pose[]{Pose.STANDING, Pose.SNEAKING}) {
            var bounds = pose == Pose.STANDING ? standing : new BoundingBox(-0.3, 0, -0.3, 0.3, 1.5, 0.3);
            var result = HitResult.evaluate(config, EntityType.PLAYER, bounds, new Vector(0, 1.2, 0.3),
                    true, new Vector(0, 1.2, 6), new Vector(0, 0, -1), 0, 1, pose, 1.2);
            assertEquals(pose == Pose.SNEAKING, result.headshot());
        }
    }

    @ParameterizedTest
    @EnumSource(value = Pose.class, names = {"SWIMMING", "FALL_FLYING"})
    void horizontalCorrectionStillRequiresAPreciseHitOnAnEnabledPlayer(Pose pose) throws Exception {
        var config = settings("headshot-entities: [PLAYER, ZOMBIE]");
        var bounds = new BoundingBox(-0.3, 0, -0.3, 0.3, 0.6, 0.3);
        for (EntityType type : new EntityType[]{EntityType.PLAYER, EntityType.ZOMBIE}) {
            var result = HitResult.evaluate(config, type, bounds, new Vector(0, 0.4, 0.3), type != EntityType.PLAYER,
                    new Vector(0, 0.4, 6), new Vector(0, 0, -1), 0, 1, pose, 0.4);
            assertFalse(result.headshot());
        }
        config = settings("headshot-entities: []");
        assertFalse(HitResult.evaluate(config, EntityType.PLAYER, bounds, new Vector(0, 0.4, 0.3), true,
                new Vector(0, 0.4, 6), new Vector(0, 0, -1), 0, 1, pose, 0.4).headshot());
    }

    @Test void invalidEyePositionsDoNotExpandTheHeadRegion() throws Exception {
        var config = settings("");
        var bounds = new BoundingBox(-0.3, 0, -0.3, 0.3, 0.6, 0.3);
        for (double eyeY : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -0.1, 0.7}) {
            assertFalse(HitResult.evaluate(config, EntityType.PLAYER, bounds, new Vector(0, 0.4, 0.3), true,
                    new Vector(0, 0.4, 6), new Vector(0, 0, -1), 0, 1, Pose.SWIMMING, eyeY).headshot());
        }
    }

    @Test void anExplicitlyWiderHeadRegionIsPreserved() throws Exception {
        var config = settings("headshot-height: 0.9");
        var bounds = new BoundingBox(-0.3, 0, -0.3, 0.3, 0.6, 0.3);
        assertTrue(HitResult.evaluate(config, EntityType.PLAYER, bounds, new Vector(0, 0.21, 0.3), true,
                new Vector(0, 0.21, 6), new Vector(0, 0, -1), 0, 1, Pose.SWIMMING, 0.4).headshot());
    }
}
