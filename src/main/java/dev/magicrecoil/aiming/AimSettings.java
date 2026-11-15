package dev.magicrecoil.aiming;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;

/** Literal, per-weapon ADS settings. Zoom is an explicitly opted-in client FOV effect. */
public record AimSettings(boolean enabled, boolean toggle, boolean cancelOnItemSwap, boolean cancelOnHandSwap,
                          double movementSpeedMultiplier, double recoilMultiplier, List<String> recoilSpells,
                          List<String> stopOnSpells, List<String> stopOnBuffEnd, boolean zoomEnabled, double fovFactor,
                          ZoomMode zoomMode, int slownessAmplifier) {
    public enum ZoomMode { SLOWNESS, FOV_EFFECT }
    public AimSettings {
        range("movement-speed-multiplier", movementSpeedMultiplier, 0.01, 1);
        range("recoil-multiplier", recoilMultiplier, 0, 1);
        range("zoom.fov-factor", fovFactor, 0.55, 1);
        if (slownessAmplifier < 0 || slownessAmplifier > 5) throw new IllegalArgumentException("zoom.slowness-amplifier must be an integer in [0, 5]");
        recoilSpells = List.copyOf(recoilSpells);
        stopOnSpells = List.copyOf(stopOnSpells);
        stopOnBuffEnd = List.copyOf(stopOnBuffEnd);
        if (enabled && recoilSpells.isEmpty()) throw new IllegalArgumentException("recoil-spells must name this weapon's RecoilSpell(s)");
    }

    public static AimSettings from(ConfigurationSection config) {
        if (config == null) throw new IllegalArgumentException("Missing spell configuration");
        if (config.contains("trigger")) throw new IllegalArgumentException("Use MagicSpells left-click-cast-item, not trigger");
        if (!bool(config, "cancel-on-death", true) || !bool(config, "cancel-on-logout", true)) {
            throw new IllegalArgumentException("Death and logout cleanup cannot be disabled");
        }
        boolean zoom = bool(config, "zoom.enabled", false);
        if (config.contains("zoom.level")) throw new IllegalArgumentException("zoom.level is not an optical magnification contract; use zoom.mode and its mode-specific settings");
        Object rawMode = config.get("zoom.mode", "");
        if (!(rawMode instanceof String textMode) || (zoom && textMode.isBlank())) throw new IllegalArgumentException("Enabled zoom requires explicit zoom.mode: SLOWNESS or FOV_EFFECT");
        ZoomMode mode;
        try { mode = rawMode.equals("") ? ZoomMode.FOV_EFFECT : ZoomMode.valueOf(((String) rawMode).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("zoom.mode must be SLOWNESS or FOV_EFFECT", e); }
        double amplifier = number(config, "zoom.slowness-amplifier", 2);
        if (amplifier != Math.rint(amplifier) || amplifier < 0 || amplifier > 5) throw new IllegalArgumentException("zoom.slowness-amplifier must be an integer in [0, 5]");
        return new AimSettings(bool(config, "enabled", true), bool(config, "toggle", true),
                bool(config, "cancel-on-item-swap", true), bool(config, "cancel-on-hand-swap", true),
                number(config, "movement-speed-multiplier", mode == ZoomMode.SLOWNESS ? 1 : 0.65),
                number(config, "recoil-multiplier", mode == ZoomMode.SLOWNESS ? 1 : 0.5),
                names(config, "recoil-spells"), names(config, "stop-on-spells"), names(config, "stop-on-buff-end"),
                zoom, number(config, "zoom.fov-factor", 0.65), mode, (int) amplifier);
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
