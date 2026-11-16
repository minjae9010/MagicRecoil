package dev.magicrecoil.hit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HitSettingsTest {
    static HitSettings settings(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return HitSettings.from(config);
    }

    @Test void interpolatesAndClampsDistanceCurveInBothDirections() throws Exception {
        var settings = settings("distance-damage: [{distance: 10, multiplier: 1}, {distance: 30, multiplier: 0.5}, {distance: 50, multiplier: 2}]");
        assertEquals(1, settings.distanceMultiplier(0));
        assertEquals(1, settings.distanceMultiplier(10));
        assertEquals(0.75, settings.distanceMultiplier(20));
        assertEquals(0.5, settings.distanceMultiplier(30));
        assertEquals(1.25, settings.distanceMultiplier(40));
        assertEquals(2, settings.distanceMultiplier(500));
    }

    @Test void defaultsAreConstantWithoutDistanceConfiguration() throws Exception {
        var settings = settings("");
        assertEquals(4, settings.damage());
        assertEquals(1, settings.distanceMultiplier(200));
        assertEquals(1, settings.directionMultipliers().get(HitDirection.BACK));
    }

    @ParameterizedTest
    @ValueSource(strings = {"damage: .nan", "damage: .inf", "damage: -1", "damage: '4'", "range: 0", "range: 513",
            "headshot-height: 0", "headshot-height: 1.1", "headshot-multiplier: -1", "vertical-angle: 0", "vertical-angle: 91",
            "power-affects-damage: 'false'", "headshot-entities: PLAYER", "headshot-entities: [typo]",
            "distance-damage: []", "distance-damage: 2", "distance-damage: [1]",
            "distance-damage: [{distance: 1}]", "distance-damage: [{distance: -1, multiplier: 1}]",
            "distance-damage: [{distance: 0, multiplier: .nan}]",
            "distance-damage: [{distance: 1, multiplier: 1}, {distance: 1, multiplier: 0}]",
            "distance-damage: [{distance: 2, multiplier: 1}, {distance: 1, multiplier: 0}]",
            "direction-multipliers: []", "direction-multipliers: {behind: 2}", "direction-multipliers: {BACK: 2}",
            "direction-multipliers: {back: .inf}", "spell-on-hit: [SomeSpell]", "spell-on-headhsot: Typo"})
    void invalidSettingsFailWithAnActionableError(String yaml) {
        assertThrows(IllegalArgumentException.class, () -> settings(yaml));
    }

    @Test void allDistributedHitExamplesParse() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(Files.readString(Path.of("examples/hit-integration.yml")));
        int count = 0;
        for (String key : config.getKeys(false)) {
            if (config.getString(key + ".spell-class", "").endsWith(".HitSpell")) {
                assertDoesNotThrow(() -> HitSettings.from(config.getConfigurationSection(key)), key);
                count++;
            }
        }
        assertTrue(count >= 2, "The hitscan and existing-projectile examples must both be present");
    }
}
