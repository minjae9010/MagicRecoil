package dev.magicrecoil.hit;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record HitSettings(double range, double damage, NamespacedKey damageType, boolean powerAffectsDamage,
                          double headshotHeight, double headshotMultiplier, Set<EntityType> headshotEntities,
                          double verticalAngle, Map<HitDirection, Double> directionMultipliers,
                          List<DistancePoint> distanceDamage, Map<String, String> hooks) {
    public record DistancePoint(double distance, double multiplier) {}

    public HitSettings {
        headshotEntities = Set.copyOf(headshotEntities);
        directionMultipliers = Map.copyOf(directionMultipliers);
        distanceDamage = List.copyOf(distanceDamage);
        hooks = Map.copyOf(hooks);
    }

    public static HitSettings from(ConfigurationSection config) {
        if (config == null) throw new IllegalArgumentException("Missing hit spell configuration");
        Set<EntityType> entities = new HashSet<>();
        Object rawEntities = config.get("headshot-entities", List.of("PLAYER"));
        if (!(rawEntities instanceof List<?> list)) throw invalid("headshot-entities", "must be a list");
        for (Object entry : list) {
            if (!(entry instanceof String name)) throw invalid("headshot-entities", "must contain entity names");
            try { entities.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException error) { throw invalid("headshot-entities", "unknown entity: " + name); }
        }

        Map<HitDirection, Double> directions = new EnumMap<>(HitDirection.class);
        if (config.contains("direction-multipliers") && !config.isConfigurationSection("direction-multipliers")) {
            throw invalid("direction-multipliers", "must be a section");
        }
        var section = config.getConfigurationSection("direction-multipliers");
        if (section != null) for (String key : section.getKeys(false)) {
            try { HitDirection.valueOf(key.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException error) { throw invalid("direction-multipliers", "unknown direction: " + key); }
            if (!key.equals(key.toLowerCase(Locale.ROOT))) throw invalid("direction-multipliers", "use lowercase keys");
        }
        for (HitDirection direction : HitDirection.values()) {
            directions.put(direction, number(config.get("direction-multipliers." + direction.key(), 1D),
                    "direction-multipliers." + direction.key(), 0, 100));
        }

        List<DistancePoint> points = new ArrayList<>();
        Object rawPoints = config.get("distance-damage", List.of(Map.of("distance", 0, "multiplier", 1)));
        if (!(rawPoints instanceof List<?> distances) || distances.isEmpty() || distances.size() > 128) {
            throw invalid("distance-damage", "must contain 1..128 distance/multiplier points");
        }
        double last = -1;
        for (Object entry : distances) {
            if (!(entry instanceof Map<?, ?> point)) throw invalid("distance-damage", "each point must be a map");
            double distance = number(point.get("distance"), "distance-damage.distance", 0, 1_000_000);
            double multiplier = number(point.get("multiplier"), "distance-damage.multiplier", 0, 100);
            if (distance <= last) throw invalid("distance-damage", "distances must be strictly increasing");
            points.add(new DistancePoint(distance, multiplier));
            last = distance;
        }

        Map<String, String> hooks = new HashMap<>();
        List<String> keys = new ArrayList<>(List.of("spell-on-hit", "spell-on-headshot", "spell-on-bodyshot"));
        for (HitDirection direction : HitDirection.values()) keys.add("spell-on-" + direction.key());
        for (String key : config.getKeys(false)) {
            if (key.startsWith("spell-on-") && !keys.contains(key)
                    && !Set.of("spell-on-fail", "spell-on-interrupt").contains(key)) {
                throw invalid(key, "unknown hit hook");
            }
        }
        for (String key : keys) {
            Object raw = config.get(key, "");
            if (!(raw instanceof String name)) throw invalid(key, "must be a subspell string");
            if (!name.isBlank()) hooks.put(key, name.trim());
        }

        return new HitSettings(number(config.get("range", 64), "range", 0.1, 512),
                number(config.get("damage", 4), "damage", 0, 1_000_000),
                damageType(config),
                bool(config, "power-affects-damage", true),
                number(config.get("headshot-height", 0.25), "headshot-height", 0.01, 1),
                number(config.get("headshot-multiplier", 2), "headshot-multiplier", 0, 100), entities,
                number(config.get("vertical-angle", 45), "vertical-angle", 1, 90), directions, points, hooks);
    }

    private static NamespacedKey damageType(ConfigurationSection config) {
        Object raw = config.get("damage-type", "minecraft:arrow");
        if (raw instanceof String name) {
            NamespacedKey key = NamespacedKey.fromString(name);
            if (key != null) return key;
        }
        throw invalid("damage-type", "must be a valid key, such as minecraft:arrow");
    }

    /** Linear interpolation, clamped to the first/last configured multiplier. */
    public double distanceMultiplier(double distance) {
        if (!Double.isFinite(distance) || distance < 0) throw invalid("distance", "must be finite and nonnegative");
        DistancePoint previous = distanceDamage.getFirst();
        if (distance <= previous.distance()) return previous.multiplier();
        for (int i = 1; i < distanceDamage.size(); i++) {
            DistancePoint next = distanceDamage.get(i);
            if (distance <= next.distance()) {
                double fraction = (distance - previous.distance()) / (next.distance() - previous.distance());
                return previous.multiplier() + fraction * (next.multiplier() - previous.multiplier());
            }
            previous = next;
        }
        return previous.multiplier();
    }

    private static double number(Object raw, String key, double min, double max) {
        if (!(raw instanceof Number value) || !Double.isFinite(value.doubleValue())
                || value.doubleValue() < min || value.doubleValue() > max) {
            throw invalid(key, "must be a finite number in [" + min + ", " + max + "]");
        }
        return value.doubleValue();
    }

    private static boolean bool(ConfigurationSection config, String key, boolean fallback) {
        Object raw = config.get(key, fallback);
        if (!(raw instanceof Boolean value)) throw invalid(key, "must be a boolean");
        return value;
    }

    private static IllegalArgumentException invalid(String key, String reason) {
        return new IllegalArgumentException(key + " " + reason);
    }
}
