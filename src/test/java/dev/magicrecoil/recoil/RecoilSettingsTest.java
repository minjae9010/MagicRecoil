package dev.magicrecoil.recoil;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RecoilSettingsTest {
    static RecoilSettings settings(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return RecoilSettings.from(config);
    }

    @Test void oneNumberWorks() throws Exception {
        var s = settings("recoil: 2.5");
        assertEquals(2.5, s.recoil());
        assertFalse(s.autoReturn());
        assertEquals(3, s.steps());
        assertEquals(RecoilPattern.VALORANT, s.pattern());
    }

    @ParameterizedTest
    @ValueSource(strings = {"recoil: .nan", "recoil: .inf", "recoil: -1", "recoil: '2.5'", "recoil: 46",
            "vertical: -1", "horizontal: 11", "smoothness: 1.1", "growth: -1", "reset-delay: 0",
            "reset-delay: 400.5", "reset-delay: 60001", "recoil-steps: 0", "recoil-steps: 21",
            "max-recoil: 0", "pattern-randomness: 2", "auto-return: 'false'", "pattern: typo",
            "vertical-min: 3\nvertical-max: 2", "horizontal-min: 1\nhorizontal-max: -1",
            "pattern: custom", "custom-pattern: [1, 2]", "stop-on-spells: reload",
            "pattern-repeat: loop", "pattern: custom\ncustom-pattern: [{vertical: .nan, horizontal: 0}]"})
    void rejectsInvalidConfiguration(String yaml) { assertThrows(IllegalArgumentException.class, () -> settings(yaml)); }

    @Test void explicitStepsOverrideSmoothness() throws Exception {
        assertEquals(2, settings("smoothness: 1\nrecoil-steps: 2").steps());
    }

    @Test void allDistributedRecoilExamplesParse() throws Exception {
        for (String file : new String[]{"recoil-examples.yml", "paimon-recoil.yml"}) {
            var config = new YamlConfiguration();
            config.loadFromString(Files.readString(Path.of("examples", file)));
            for (String key : config.getKeys(false)) {
                if (config.getString(key + ".spell-class", "").endsWith(".RecoilSpell")) {
                    assertDoesNotThrow(() -> RecoilSettings.from(config.getConfigurationSection(key)), key);
                }
            }
        }
    }
}
