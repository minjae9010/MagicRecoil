package dev.magicrecoil.compat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"4.0-Beta-17", "4.0-Beta-18", "4.0-Beta-19", "4.0-Beta-100", "4.0-Beta-17-SNAPSHOT",
            "4.0 beta 17", "4.0-RC-1", "4.0", "4.0.0", "4.0.1", "4.1-Alpha-1", "5.0", "4.0-Beta-17+build.1"})
    void acceptsMinimumAndNewerMagicSpells(String version) {
        assertDoesNotThrow(() -> VersionPolicy.requireMagicSpells(version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.5-Release", "3.5", "4.0-Beta-9", "4.0-Beta-16", "4.0-Alpha-99", "4.0-SNAPSHOT",
            "unknown", "", "4.0-Beta-17-malformed", "9999999999999999999999.0", "4.0-Beta-17.1"})
    void rejectsOlderOrUnrecognizedMagicSpells(String version) {
        assertThrows(IllegalArgumentException.class, () -> VersionPolicy.requireMagicSpells(version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.21.4", "1.21.5", "1.21.8", "1.21.10", "1.21.11", "26.1.2", "26.2", "26.3"})
    void acceptsNewerMinecraftWithoutAnExactVersionLock(String version) {
        assertDoesNotThrow(() -> VersionPolicy.requireMinecraft(version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.20.6", "1.21", "1.21.1", "1.21.3", "unknown"})
    void rejectsUnsupportedMinecraft(String version) {
        assertThrows(IllegalArgumentException.class, () -> VersionPolicy.requireMinecraft(version));
    }

    @Test void checksRealUpstreamBinaryApi() {
        assertDoesNotThrow(MagicSpellsCompatibility::verifyApi);
    }
}
