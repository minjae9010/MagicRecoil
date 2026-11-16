package dev.magicrecoil.hit;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pose;

import java.util.List;

public record HitResult(boolean headshot, HitDirection direction, double distance, double multiplier, double damage) {
    public static HitResult evaluate(HitSettings settings, EntityType type, BoundingBox bounds,
                                     Vector impact, boolean preciseImpact, Vector origin, Vector incoming,
                                     float victimYaw, float power) {
        return evaluate(settings, type, bounds, impact, preciseImpact, origin, incoming, victimYaw, power,
                Pose.STANDING, Double.NaN);
    }

    public static HitResult evaluate(HitSettings settings, EntityType type, BoundingBox bounds,
                                     Vector impact, boolean preciseImpact, Vector origin, Vector incoming,
                                     float victimYaw, float power, Pose pose, double eyeY) {
        impact.checkFinite();
        origin.checkFinite();
        if (!Float.isFinite(power) || power < 0) throw new IllegalArgumentException("Invalid spell power");
        double epsilon = 1e-6;
        boolean inside = impact.getX() >= bounds.getMinX() - epsilon && impact.getX() <= bounds.getMaxX() + epsilon
                && impact.getY() >= bounds.getMinY() - epsilon && impact.getY() <= bounds.getMaxY() + epsilon
                && impact.getZ() >= bounds.getMinZ() - epsilon && impact.getZ() <= bounds.getMaxZ() + epsilon;
        boolean head = preciseImpact && inside && bounds.getHeight() > 0 && settings.headshotEntities().contains(type)
                && impact.getY() >= HitGeometry.headMinimumY(type, pose, bounds, eyeY, settings.headshotHeight()) - epsilon;
        HitDirection direction = HitDirection.classify(incoming, victimYaw, settings.verticalAngle());
        double distance = origin.distance(impact);
        double multiplier = settings.distanceMultiplier(distance) * settings.directionMultipliers().get(direction)
                * (head ? settings.headshotMultiplier() : 1);
        double damage = settings.damage() * multiplier * (settings.powerAffectsDamage() ? power : 1);
        return new HitResult(head, direction, distance, multiplier, damage);
    }

    /** All three hooks run, in this order, when configured. */
    public List<String> hookKeys() {
        return List.of("spell-on-hit", headshot ? "spell-on-headshot" : "spell-on-bodyshot", "spell-on-" + direction.key());
    }
}
