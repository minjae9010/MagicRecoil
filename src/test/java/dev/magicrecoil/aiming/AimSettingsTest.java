package dev.magicrecoil.aiming;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AimSettingsTest {
    static AimSettings settings(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("recoil-spells: [Gun_Recoil]\n" + yaml);
        return AimSettings.from(config);
    }
    @Test void defaultsAndNames() throws Exception {
        var s = settings("");
        assertTrue(s.enabled()); assertTrue(s.toggle());
        assertTrue(s.cancelOnItemSwap()); assertTrue(s.cancelOnHandSwap());
        assertEquals(0.65, s.movementSpeedMultiplier()); assertEquals(0.5, s.recoilMultiplier());
        assertEquals(java.util.List.of("gun_recoil"), s.recoilSpells());
    }
    @ParameterizedTest @ValueSource(strings = {"movement-speed-multiplier: 0", "movement-speed-multiplier: 1.01",
            "movement-speed-multiplier: '0.65'", "movement-speed-multiplier: .nan", "recoil-multiplier: -1",
            "recoil-multiplier: .inf", "recoil-multiplier: 1.1", "toggle: HOLD", "enabled: yesplease",
            "cancel-on-item-swap: 0", "cancel-on-hand-swap: 'false'", "cancel-on-death: false",
            "cancel-on-logout: false", "zoom.enabled: true", "zoom.level: 2.0", "trigger: RIGHT_CLICK",
            "stop-on-spells: Reload", "stop-on-buff-end: ['']"})
    void invalidSettingsAreRejected(String yaml) { assertThrows(IllegalArgumentException.class, () -> settings(yaml)); }
    @Test void missingScopeRejectedAndDisabledConfigAllowed() {
        assertThrows(IllegalArgumentException.class, () -> AimSettings.from(new YamlConfiguration()));
        var c = new YamlConfiguration(); c.set("enabled", false);
        assertFalse(AimSettings.from(c).enabled());
    }
    @Test void boundsAndNonToggleAccepted() throws Exception {
        var s = settings("toggle: false\nmovement-speed-multiplier: 1\nrecoil-multiplier: 0\nzoom.enabled: false");
        assertFalse(s.toggle()); assertEquals(1, s.movementSpeedMultiplier()); assertEquals(0, s.recoilMultiplier());
    }
    @Test void shippedPaimonExampleUsesSupportedSettings() throws Exception {
        var c = new YamlConfiguration();
        c.load(java.nio.file.Path.of("examples/aim-paimon.yml").toFile());
        var s = AimSettings.from(c.getConfigurationSection("Weapon_파이먼_조준"));
        assertEquals(java.util.List.of("weapon_파이먼_반동"), s.recoilSpells());
        assertEquals(java.util.List.of("weapon_reload_파이먼"), s.stopOnSpells());
        assertTrue(s.enabled()); assertTrue(s.toggle());
    }
    @Test void fovZoomRequiresAnExplicitModeAndFiniteSafeFactor() throws Exception {
        var s = settings("zoom:\n  enabled: true\n  mode: FOV_EFFECT\n  fov-factor: 0.65");
        assertTrue(s.zoomEnabled()); assertEquals(0.65, s.fovFactor());
        for (String value : new String[]{"0.5", "1.1", ".nan", "'0.7'"}) {
            assertThrows(IllegalArgumentException.class, () -> settings("zoom:\n  enabled: true\n  mode: FOV_EFFECT\n  fov-factor: " + value));
        }
        assertThrows(IllegalArgumentException.class, () -> settings("zoom:\n  enabled: true\n  mode: SPYGLASS"));
    }
    @Test void crackshotScopeValidatesItsPotionAmplifierAndPreservesRecoilInTheExample() throws Exception {
        var s = settings("zoom:\n  enabled: true\n  mode: SLOWNESS\n  slowness-amplifier: 2");
        assertEquals(AimSettings.ZoomMode.SLOWNESS, s.zoomMode()); assertEquals(2, s.slownessAmplifier());
        assertEquals(1, s.recoilMultiplier()); assertEquals(1, s.movementSpeedMultiplier());
        for (String value : new String[]{"-1", "6", "1.5", ".nan", "'2'"}) {
            assertThrows(IllegalArgumentException.class, () -> settings("zoom:\n  enabled: true\n  mode: SLOWNESS\n  slowness-amplifier: " + value));
        }
        var c = new YamlConfiguration(); c.load(java.nio.file.Path.of("examples/aim-paimon.yml").toFile());
        s = AimSettings.from(c.getConfigurationSection("Weapon_파이먼_조준"));
        assertEquals(1, s.recoilMultiplier()); assertEquals(1, s.movementSpeedMultiplier());
        assertTrue(s.zoomEnabled()); assertEquals(AimSettings.ZoomMode.SLOWNESS, s.zoomMode());
    }
    @Test void normalizedNamesAreDistinctAndCollectionsAreImmutable() throws Exception {
        var settings = settings("stop-on-spells: [' Reload ', RELOAD]\nstop-on-buff-end: [FiringBuff]");
        assertEquals(List.of("reload"), settings.stopOnSpells());
        assertEquals(List.of("firingbuff"), settings.stopOnBuffEnd());
        assertThrows(UnsupportedOperationException.class, () -> settings.recoilSpells().add("another"));
    }

    @Test void shippedExampleLoadsAndTheStopHookUsesAnExistingSpell() throws Exception {
        var config = new YamlConfiguration();
        config.load(Path.of("examples/aim-integration.yml").toFile());
        var settings = AimSettings.from(config.getConfigurationSection("Weapon_Aim"));
        assertEquals(List.of("weapon_recoil"), settings.recoilSpells());
        assertEquals(List.of("weapon_stopaim"), settings.stopOnSpells());
        assertTrue(config.contains("Weapon_StopAim.spell-class"));
    }
}
