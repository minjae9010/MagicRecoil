package dev.magicrecoil.firing;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AutoFireSettingsTest {
    static AutoFireSettings settings(String extra) throws Exception {
        var c = new YamlConfiguration(); c.loadFromString("use-item: Paimon\nfire-spell: Paimon_Fire\n" + extra);
        return AutoFireSettings.from(c);
    }
    @Test void defaultsAndSupportedTypes() throws Exception {
        var s = settings(""); assertEquals(2, s.intervalTicks()); assertTrue(s.enabled());
        assertEquals("Paimon", s.useItem()); assertEquals("paimon_fire", s.fireSpell());
        assertTrue(UseItemSupport.supports(Material.DIAMOND_HOE)); assertTrue(UseItemSupport.supports(Material.STICK));
        assertFalse(UseItemSupport.supports(Material.BOW)); assertFalse(UseItemSupport.supports(Material.SPYGLASS));
    }
    @ParameterizedTest @ValueSource(strings = {"interval-ticks: 0", "interval-ticks: 1201", "interval-ticks: 1.5",
            "interval-ticks: '1'", "interval-ticks: .nan", "enabled: 'true'", "trigger: RIGHT_CLICK",
            "fire-mode: HOLD", "release-timeout-ticks: 4", "right-click-cast-item: stick", "stop-on-spells: Reload",
            "stop-on-buff-end: ['']"})
    void rejectsMisleadingAndInvalidConfiguration(String yaml) { assertThrows(IllegalArgumentException.class, () -> settings(yaml)); }
    @Test void disabledCanOmitReferences() {
        var c = new YamlConfiguration(); c.set("enabled", false); assertFalse(AutoFireSettings.from(c).enabled());
    }
    @Test void repeatedClickModeExplicitlyExposesItsReleaseDelay() throws Exception {
        var s = settings("input-mode: REPEATED_CLICK\nrelease-timeout-ticks: 6");
        assertEquals(AutoFireSettings.InputMode.REPEATED_CLICK, s.inputMode()); assertEquals(6, s.releaseTimeoutTicks());
        assertThrows(IllegalArgumentException.class, () -> settings("input-mode: REPEATED_CLICK\nrelease-timeout-ticks: 1"));
    }
}
