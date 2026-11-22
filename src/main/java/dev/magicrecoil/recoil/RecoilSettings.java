package dev.magicrecoil.recoil;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable settings, validated once when MagicSpells constructs a spell. */
public record RecoilSettings(
        double recoil, double vertical, double horizontal, double smoothness, double growth,
        double movingRecoilMultiplier,
        long resetDelayMillis, RecoilPattern pattern, boolean autoReturn,
        double verticalMin, double verticalMax, double horizontalMin, double horizontalMax,
        double horizontalBias, double maxRecoil, double valorantMaxRise, double randomness, int steps,
        List<PatternPoint> customPattern, Repeat repeat,
        String requireSuccessOf, List<String> stopOnSpells, List<String> stopOnBuffEnd) {

    public enum Repeat { LOOP, HOLD, STOP }
    public record PatternPoint(double vertical, double horizontal) {}

    public RecoilSettings {
        range("recoil", recoil, 0, 45);
        range("vertical", vertical, 0, 10);
        range("horizontal", horizontal, 0, 10);
        range("smoothness", smoothness, 0, 1);
        range("growth", growth, 0, 1);
        range("moving-recoil-multiplier", movingRecoilMultiplier, 1, 10);
        range("reset-delay", resetDelayMillis, 50, 60000);
        range("vertical-min", verticalMin, -10, 10);
        range("vertical-max", verticalMax, -10, 10);
        range("horizontal-min", horizontalMin, -10, 10);
        range("horizontal-max", horizontalMax, -10, 10);
        range("horizontal-bias", horizontalBias, -10, 10);
        range("max-recoil", maxRecoil, 0.01, 45);
        range("valorant-max-rise", valorantMaxRise, 0, 90);
        range("pattern-randomness", randomness, 0, 1);
        range("recoil-steps", steps, 1, 20);
        if (verticalMin > verticalMax || horizontalMin > horizontalMax) {
            throw new IllegalArgumentException("min must not exceed max on either axis");
        }
        customPattern = List.copyOf(customPattern);
        if (customPattern.size() > 1024 || (pattern == RecoilPattern.CUSTOM && customPattern.isEmpty())) {
            throw new IllegalArgumentException("custom-pattern requires 1..1024 points when pattern is custom");
        }
        if (pattern != RecoilPattern.CUSTOM && !customPattern.isEmpty()) {
            throw new IllegalArgumentException("custom-pattern requires pattern: custom");
        }
        for (PatternPoint point : customPattern) {
            range("custom-pattern.vertical", point.vertical(), -10, 10);
            range("custom-pattern.horizontal", point.horizontal(), -10, 10);
        }
        stopOnSpells = List.copyOf(stopOnSpells);
        stopOnBuffEnd = List.copyOf(stopOnBuffEnd);
    }

    public static RecoilSettings from(ConfigurationSection config) {
        if (config == null) throw new IllegalArgumentException("Missing spell configuration");
        double smoothness = number(config, "smoothness", 0.5);
        int steps = integer(config, "recoil-steps", 1 + (int) Math.round(4 * smoothness));
        List<PatternPoint> points = new ArrayList<>();
        Object rawPoints = config.get("custom-pattern");
        if (rawPoints != null) {
            if (!(rawPoints instanceof List<?> list)) throw new IllegalArgumentException("custom-pattern must be a list of maps");
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> point) || point.size() != 2
                        || !(point.get("vertical") instanceof Number v) || !(point.get("horizontal") instanceof Number h)) {
                    throw new IllegalArgumentException("Each custom-pattern entry needs numeric vertical and horizontal only");
                }
                points.add(new PatternPoint(v.doubleValue(), h.doubleValue()));
            }
        }
        Object rawPattern = config.get("pattern");
        RecoilPattern pattern = rawPattern instanceof String name && name.trim().equalsIgnoreCase("horizental")
                ? RecoilPattern.HORIZONTAL
                : enumeration(config, "pattern", "valorant", RecoilPattern.class);
        if (config.contains("pattern-repeat") && pattern != RecoilPattern.CUSTOM) {
            throw new IllegalArgumentException("pattern-repeat requires pattern: custom");
        }
        return new RecoilSettings(number(config, "recoil", 2.5), number(config, "vertical", 1),
                number(config, "horizontal", 0.4), smoothness, number(config, "growth", 0.08),
                number(config, "moving-recoil-multiplier", 1.5),
                integer(config, "reset-delay", 400), pattern, bool(config, "auto-return", false),
                number(config, "vertical-min", 0), number(config, "vertical-max", 2),
                number(config, "horizontal-min", -1), number(config, "horizontal-max", 1),
                number(config, "horizontal-bias", 0), number(config, "max-recoil", 8),
                number(config, "valorant-max-rise", 15), number(config, "pattern-randomness", 0), steps, points,
                enumeration(config, "pattern-repeat", "loop", Repeat.class),
                string(config, "require-success-of", "").toLowerCase(Locale.ROOT),
                names(config, "stop-on-spells"), names(config, "stop-on-buff-end"));
    }

    private static List<String> names(ConfigurationSection config, String key) {
        Object value = config.get(key);
        if (value == null) return List.of();
        if (!(value instanceof List<?> list) || list.size() > 128) throw new IllegalArgumentException(key + " must be a list of at most 128 spell names");
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof String name) || name.isBlank()) throw new IllegalArgumentException(key + " needs nonempty spell names");
            result.add(name.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static double number(ConfigurationSection config, String key, double fallback) {
        Object value = config.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException(key + " must be a finite YAML number");
        return n.doubleValue();
    }

    private static int integer(ConfigurationSection config, String key, int fallback) {
        double value = number(config, key, fallback);
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException(key + " must be an integer");
        return (int) value;
    }

    private static boolean bool(ConfigurationSection config, String key, boolean fallback) {
        Object value = config.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Boolean flag)) throw new IllegalArgumentException(key + " must be true or false");
        return flag;
    }

    private static String string(ConfigurationSection config, String key, String fallback) {
        Object value = config.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String text)) throw new IllegalArgumentException(key + " must be text");
        return text.trim();
    }

    private static <T extends Enum<T>> T enumeration(ConfigurationSection config, String key, String fallback, Class<T> type) {
        try { return Enum.valueOf(type, string(config, key, fallback).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid " + key + ": " + config.get(key), e); }
    }

    private static void range(String key, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException(key + " must be in [" + min + ", " + max + "]");
    }
}
