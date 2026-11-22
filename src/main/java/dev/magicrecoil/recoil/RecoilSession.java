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
    private double plannedRise;

    public RecoilSession(String weapon, RecoilSettings settings) {
        this.weapon = weapon;
        this.settings = settings;
    }

    public void shoot(long now, RandomGenerator random) {
        shoot(now, random, 1);
    }

    public void shoot(long now, RandomGenerator random, double aimMultiplier) {
        shoot(now, random, aimMultiplier, false);
    }

    void shoot(long now, RandomGenerator random, double aimMultiplier, boolean moving) {
        if (!Double.isFinite(aimMultiplier) || aimMultiplier < 0 || aimMultiplier > 1) throw new IllegalArgumentException("Recoil multiplier must be in [0, 1]");
        if (shots > 0 && expired(now)) {
            buffer.clear();
            shots = 0;
            returning = false;
            deliveredYaw = deliveredPitch = 0;
            plannedRise = 0;
        }
        RotationDelta delta = settings.pattern().sampleUnbounded(settings, shots, random);
        if (moving) delta = delta.scale(settings.movingRecoilMultiplier());
        if (settings.pattern() == RecoilPattern.VALORANT && delta.pitch() < 0) {
            double rise = Math.min(-delta.pitch(), Math.max(0, settings.valorantMaxRise() - plannedRise));
            delta = new RotationDelta(delta.yaw(), -rise);
        }
        delta = delta.limit(settings.maxRecoil());
        if (settings.pattern() == RecoilPattern.VALORANT) plannedRise += Math.max(0, -delta.pitch());
        // Aiming scales the bounded shot without changing the burst's rise budget.
        buffer.add(delta.scale(aimMultiplier), settings.steps());
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
