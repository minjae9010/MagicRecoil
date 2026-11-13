package dev.magicrecoil.recoil;

/** Degrees: positive yaw turns right; negative pitch looks up. */
public record RotationDelta(double yaw, double pitch) {
    public static final RotationDelta ZERO = new RotationDelta(0, 0);

    public RotationDelta {
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch)) {
            throw new IllegalArgumentException("Rotation must be finite");
        }
    }

    public RotationDelta limit(double magnitude) {
        double length = Math.hypot(yaw, pitch);
        return length > magnitude ? new RotationDelta(yaw * magnitude / length, pitch * magnitude / length) : this;
    }

    public boolean isZero() { return Math.abs(yaw) < 1.0e-7 && Math.abs(pitch) < 1.0e-7; }
}
