package dev.magicrecoil.recoil;

import java.util.random.RandomGenerator;

/** One active weapon per player. No stored absolute camera angle. */
public final class RecoilSession {
    private final String weapon;
    private final RecoilSettings settings;
    private final ImpulseBuffer buffer = new ImpulseBuffer();
    private long lastShotNanos;
    private int shots;
    private boolean returning;
    private double deliveredYaw;
    private double deliveredPitch;

    public RecoilSession(String weapon, RecoilSettings settings) {
        this.weapon = weapon;
        this.settings = settings;
    }

    public void shoot(long now, RandomGenerator random) {
        if (shots > 0 && expired(now)) {
            buffer.clear();
            shots = 0;
            returning = false;
            deliveredYaw = deliveredPitch = 0;
        }
        buffer.add(settings.pattern().sample(settings, shots, random), settings.steps());
        shots = Math.min(shots + 1, 1_000_000);
        lastShotNanos = now;
    }

    public RotationDelta next(long now) {
        if (buffer.isEmpty() && expired(now) && settings.autoReturn() && !returning) {
            returning = true;
            buffer.add(new RotationDelta(-deliveredYaw, -deliveredPitch), settings.steps());
        }
        return buffer.poll();
    }

    public void recordApplied(RotationDelta actual) {
        if (returning) return;
        // Bound optional recovery independently of burst duration and user mouse motion.
        deliveredYaw = Math.clamp(deliveredYaw + actual.yaw(), -90, 90);
        deliveredPitch = Math.clamp(deliveredPitch + actual.pitch(), -90, 90);
    }

    public boolean finished(long now) {
        return expired(now) && buffer.isEmpty() && (!settings.autoReturn() || returning);
    }

    public boolean expired(long now) { return now - lastShotNanos >= settings.resetDelayMillis() * 1_000_000L; }
    public String weapon() { return weapon; }
    public RecoilSettings settings() { return settings; }
    public int shots() { return shots; }
}
