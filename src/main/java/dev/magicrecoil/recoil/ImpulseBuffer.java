package dev.magicrecoil.recoil;

import java.util.Arrays;

/** Fixed memory, at most one aggregate rotation per server tick. */
public final class ImpulseBuffer {
    public static final int CAPACITY = 20;
    public static final double MAX_TICK_DEGREES = 45;
    private final double[] yaw = new double[CAPACITY];
    private final double[] pitch = new double[CAPACITY];
    private int cursor;
    private int remaining;

    public void add(RotationDelta delta, int steps) {
        if (steps < 1 || steps > CAPACITY) throw new IllegalArgumentException("steps must be 1..20");
        double denominator = steps * (steps + 1) / 2.0;
        for (int i = 0; i < steps; i++) {
            int slot = (cursor + i) % CAPACITY;
            double weight = (steps - i) / denominator;
            RotationDelta combined = new RotationDelta(yaw[slot] + delta.yaw() * weight, pitch[slot] + delta.pitch() * weight)
                    .limit(MAX_TICK_DEGREES);
            yaw[slot] = combined.yaw();
            pitch[slot] = combined.pitch();
        }
        remaining = Math.max(remaining, steps);
    }

    public RotationDelta poll() {
        if (remaining == 0) return RotationDelta.ZERO;
        RotationDelta result = new RotationDelta(yaw[cursor], pitch[cursor]);
        yaw[cursor] = pitch[cursor] = 0;
        cursor = (cursor + 1) % CAPACITY;
        remaining--;
        return result;
    }

    public boolean isEmpty() { return remaining == 0; }
    public void clear() {
        Arrays.fill(yaw, 0);
        Arrays.fill(pitch, 0);
        remaining = 0;
        cursor = 0;
    }
}
