package dev.magicrecoil.firing;

import org.bukkit.configuration.ConfigurationSection;
import java.util.List;
import java.util.Locale;

/** Buffered right-click firing by default; native use and timeout tracking remain optional. */
public record AutoFireSettings(boolean enabled, String useItem, String fireSpell, int intervalTicks,
                               InputMode inputMode, int releaseTimeoutTicks,
                               List<String> stopOnSpells, List<String> stopOnBuffEnd) {
    public enum InputMode { BUFFERED_CLICK, NATIVE_USE, REPEATED_CLICK }
    public AutoFireSettings {
        if (enabled && (useItem.isBlank() || fireSpell.isBlank())) throw new IllegalArgumentException("use-item and fire-spell are required");
        if (intervalTicks < 1 || intervalTicks > 1200) throw new IllegalArgumentException("interval-ticks must be in [1, 1200]");
        if (releaseTimeoutTicks < 5 || releaseTimeoutTicks > 40) throw new IllegalArgumentException("release-timeout-ticks must be in [5, 40]");
        stopOnSpells = List.copyOf(stopOnSpells); stopOnBuffEnd = List.copyOf(stopOnBuffEnd);
    }
    public static AutoFireSettings from(ConfigurationSection c) {
        if (c == null) throw new IllegalArgumentException("Missing spell configuration");
        for (String unsupported : List.of("trigger", "fire-mode", "right-click-cast-item", "right-click-cast-items")) {
            if (c.contains(unsupported)) throw new IllegalArgumentException(unsupported + " is unsupported: register the gun with use-item");
        }
        Object enabled = c.get("enabled", true);
        if (!(enabled instanceof Boolean flag)) throw new IllegalArgumentException("enabled must be a YAML boolean");
        Object interval = c.get("interval-ticks", 2);
        if (!(interval instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() != Math.rint(n.doubleValue())) throw new IllegalArgumentException("interval-ticks must be an integer");
        if (n.doubleValue() < 1 || n.doubleValue() > 1200) throw new IllegalArgumentException("interval-ticks must be in [1, 1200]");
        InputMode mode;
        try { mode = InputMode.valueOf(c.getString("input-mode", "BUFFERED_CLICK").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("input-mode must be BUFFERED_CLICK, NATIVE_USE or REPEATED_CLICK", e); }
        Object timeout = c.get("release-timeout-ticks", 6);
        if (!(timeout instanceof Number t) || !Double.isFinite(t.doubleValue()) || t.doubleValue() != Math.rint(t.doubleValue())
                || t.doubleValue() < 5 || t.doubleValue() > 40) throw new IllegalArgumentException("release-timeout-ticks must be an integer in [5, 40]");
        if (mode != InputMode.REPEATED_CLICK && c.contains("release-timeout-ticks")) throw new IllegalArgumentException("release-timeout-ticks requires input-mode: REPEATED_CLICK");
        return new AutoFireSettings(flag, text(c, "use-item"), text(c, "fire-spell").toLowerCase(Locale.ROOT), n.intValue(), mode, t.intValue(),
                names(c, "stop-on-spells"), names(c, "stop-on-buff-end"));
    }
    private static String text(ConfigurationSection c, String key) {
        Object raw = c.get(key, "");
        if (!(raw instanceof String s)) throw new IllegalArgumentException(key + " must be text");
        return s.trim();
    }
    private static List<String> names(ConfigurationSection c, String key) {
        Object raw = c.get(key, List.of());
        if (!(raw instanceof List<?> list) || list.size() > 128) throw new IllegalArgumentException(key + " must be a list of at most 128 names");
        return list.stream().map(value -> {
            if (!(value instanceof String s) || s.isBlank()) throw new IllegalArgumentException(key + " requires nonempty spell names");
            return s.trim().toLowerCase(Locale.ROOT);
        }).distinct().toList();
    }
}
