package dev.magicrecoil.recoil;

import java.util.random.RandomGenerator;

public enum RecoilPattern {
    VALORANT, VERTICAL, RANDOM, CUSTOM;

    /** shot is zero-based; this is an original pattern, not extracted game data. */
    public RotationDelta sample(RecoilSettings settings, int shot, RandomGenerator random) {
        return sampleUnbounded(settings, shot, random).limit(settings.maxRecoil());
    }

    RotationDelta sampleUnbounded(RecoilSettings settings, int shot, RandomGenerator random) {
        double v = 1;
        double h = 0;
        switch (this) {
            case VALORANT -> {
                if (shot > 3) {
                    double phase = (shot - 3) * Math.PI / 6;
                    double previousGrowth = (1 + settings.growth() * (shot - 1)) / (1 + settings.growth() * shot);
                    // Differences of signed target offsets, not a sine added to aim every shot.
                    // Compensating for growth keeps the center crossings centered as strength changes.
                    h = 2 * (Math.sin(phase) - Math.sin(phase - Math.PI / 6) * previousGrowth);
                }
            }
            case VERTICAL -> { }
            case RANDOM -> {
                v = between(random, settings.verticalMin(), settings.verticalMax());
                h = between(random, settings.horizontalMin(), settings.horizontalMax());
            }
            case CUSTOM -> {
                int size = settings.customPattern().size();
                if (shot >= size && settings.repeat() == RecoilSettings.Repeat.STOP) return RotationDelta.ZERO;
                int index = settings.repeat() == RecoilSettings.Repeat.LOOP ? shot % size : Math.min(shot, size - 1);
                var point = settings.customPattern().get(index);
                v = point.vertical();
                h = point.horizontal();
            }
        }
        v += between(random, -settings.randomness(), settings.randomness());
        if (this != VERTICAL) h += between(random, -settings.randomness(), settings.randomness());
        h += settings.horizontalBias();
        v = Math.clamp(v, settings.verticalMin(), settings.verticalMax());
        h = Math.clamp(h, settings.horizontalMin(), settings.horizontalMax());
        double strength = settings.recoil() * (1 + settings.growth() * shot);
        return new RotationDelta(h * settings.horizontal() * strength, -v * settings.vertical() * strength);
    }

    private static double between(RandomGenerator random, double min, double max) {
        return min == max ? min : min + random.nextDouble() * (max - min);
    }
}
