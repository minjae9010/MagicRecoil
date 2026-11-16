package dev.magicrecoil.hit;

import org.bukkit.util.Vector;

import java.util.Locale;

/** Side from which the attack arrives, relative to the victim's horizontal facing. */
public enum HitDirection {
    FRONT, BACK, LEFT, RIGHT, ABOVE, BELOW;

    public String key() { return name().toLowerCase(Locale.ROOT); }

    public static HitDirection classify(Vector incoming, float victimYaw, double verticalAngle) {
        incoming.checkFinite();
        if (!Float.isFinite(victimYaw) || incoming.lengthSquared() < 1e-12) {
            throw new IllegalArgumentException("A finite facing and nonzero incoming direction are required");
        }
        Vector source = incoming.clone().multiply(-1).normalize();
        double horizontal = Math.hypot(source.getX(), source.getZ());
        if (Math.toDegrees(Math.atan2(Math.abs(source.getY()), horizontal)) >= verticalAngle) {
            return source.getY() > 0 ? ABOVE : BELOW;
        }
        double yaw = Math.toRadians(victimYaw);
        double forward = -Math.sin(yaw) * source.getX() + Math.cos(yaw) * source.getZ();
        double right = -Math.cos(yaw) * source.getX() - Math.sin(yaw) * source.getZ();
        if (Math.abs(forward) >= Math.abs(right)) return forward >= 0 ? FRONT : BACK;
        return right >= 0 ? RIGHT : LEFT;
    }
}
