package dev.magicrecoil.aiming;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;

/** Literal, per-weapon ADS settings. Zoom is an explicitly opted-in client FOV effect. */
public record AimSettings(boolean enabled, boolean toggle, boolean cancelOnItemSwap, boolean cancelOnHandSwap,
                          double movementSpeedMultiplier, double recoilMultiplier, List<String> recoilSpells,
                          List<String> stopOnSpells, List<String> stopOnBuffEnd, boolean zoomEnabled, double fovFactor) {
    public AimSettings {
        range("movement-speed-multiplier", movementSpeedMultiplier, 0.01, 1);
        range("recoil-multiplier", recoilMultiplier, 0, 1);
        range("zoom.fov-factor", fovFactor, 0.55, 1);
        recoilSpells = List.copyOf(recoilSpells);
        stopOnSpells = List.copyOf(stopOnSpells);
        stopOnBuffEnd = List.copyOf(stopOnBuffEnd);
        if (enabled && recoilSpells.isEmpty()) throw new IllegalArgumentException("recoil-spells must name this weapon's RecoilSpell(s)");
    }

    public static AimSettings from(ConfigurationSection config) {
        if (config == null) throw new IllegalArgumentException("Missing spell configuration");
        if (config.contains("trigger")) throw new IllegalArgumentException("Use MagicSpells right-click-cast-item, not trigger");
        if (!bool(config, "cancel-on-death", true) || !bool(config, "cancel-on-logout", true)) {
            throw new IllegalArgumentException("Death and logout cleanup cannot be disabled");
        }
        boolean zoom = bool(config, "zoom.enabled", false);
        if (config.contains("zoom.level")) throw new IllegalArgumentException("zoom.level is not a magnification contract; use zoom.mode: FOV_EFFECT and zoom.fov-factor");
        Object mode = config.get("zoom.mode", "");
        if (!(mode instanceof String) || (zoom && !"FOV_EFFECT".equalsIgnoreCase((String) mode))) throw new IllegalArgumentException("Enabled zoom requires zoom.mode: FOV_EFFECT; spyglass zoom is not supported");
        return new AimSettings(bool(config, "enabled", true), bool(config, "toggle", true),
                bool(config, "cancel-on-item-swap", true), bool(config, "cancel-on-hand-swap", true),
                number(config, "movement-speed-multiplier", 0.65), number(config, "recoil-multiplier", 0.5),
                names(config, "recoil-spells"), names(config, "stop-on-spells"), names(config, "stop-on-buff-end"),
                zoom, number(config, "zoom.fov-factor", 0.65));
    }

    private static List<String> names(ConfigurationSection config, String key) {
        Object value = config.get(key);
        if (value == null) return List.of();
        if (!(value instanceof List<?> list) || list.size() > 128) throw new IllegalArgumentException(key + " must be a list of at most 128 spell names");
        return list.stream().map(entry -> {
            if (!(entry instanceof String name) || name.isBlank()) throw new IllegalArgumentException(key + " requires nonempty spell names");
            return name.trim().toLowerCase(Locale.ROOT);
        }).distinct().toList();
    }

    private static boolean bool(ConfigurationSection config, String key, boolean fallback) {
        Object value = config.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Boolean flag)) throw new IllegalArgumentException(key + " must be true or false");
        return flag;
    }

    private static double number(ConfigurationSection config, String key, double fallback) {
        Object value = config.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException(key + " must be a finite YAML number");
        return n.doubleValue();
    }

    private static void range(String key, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException(key + " must be in [" + min + ", " + max + "]");
    }
}
