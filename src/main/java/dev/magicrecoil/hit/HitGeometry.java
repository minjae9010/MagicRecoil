package dev.magicrecoil.hit;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pose;

public final class HitGeometry {
    private HitGeometry() {}

    public record Impact(Vector position, boolean precise) {}

    /** Keep the configured upper strip, extending it down to a horizontal player's actual eyes. */
    public static double headMinimumY(EntityType type, Pose pose, BoundingBox bounds, double eyeY, double fraction) {
        double minimum = bounds.getMaxY() - bounds.getHeight() * fraction;
        if (type == EntityType.PLAYER && (pose == Pose.SWIMMING || pose == Pose.FALL_FLYING)
                && Double.isFinite(eyeY) && eyeY >= bounds.getMinY() && eyeY <= bounds.getMaxY()) {
            minimum = Math.min(minimum, eyeY);
        }
        return minimum;
    }

    /** Project a radius-based projectile collision onto its actual travel line. */
    public static Impact projectileImpact(Vector previous, Vector sample, Vector incoming, BoundingBox bounds, double radius) {
        previous.checkFinite();
        sample.checkFinite();
        incoming.checkFinite();
        boolean sampleInside = bounds.contains(sample);
        if (bounds.contains(previous) && sampleInside) return new Impact(sample.clone(), true);
        Vector segment = sample.clone().subtract(previous);
        double length = segment.length();
        Vector direction = length > 1e-8 ? segment : incoming;
        if (direction.lengthSquared() > 1e-12) {
            // The center can trigger a radius hit just before it reaches the real body box.
            var intersection = bounds.rayTrace(previous, direction, length + radius + 1e-6);
            if (intersection != null && (sampleInside || intersection.getHitPosition().distance(sample) <= radius + 1e-6)) {
                return new Impact(intersection.getHitPosition(), true);
            }
        }
        return new Impact(sample.clone(), false);
    }
}
