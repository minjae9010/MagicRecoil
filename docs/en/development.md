# Development

[English index](README.md) · [한국어](../ko/development.md)

## Reproducible entry point

Use JDK 21 and the committed Gradle 9.3.0 wrapper. `./gradlew clean build` compiles against the real Paper 1.21.11 development bundle and the official MagicSpells Beta 18 release. The download task verifies MagicSpells' SHA-256. The wrapper distribution is also checksum-pinned. Paper's dev bundle remains a versioned Maven snapshot, so a fully hermetic offline build is not claimed.

Dependencies are compile-only in the shipping JAR. Test dependencies include JUnit, Bukkit configuration support, and Mockito for scheduler isolation. `./gradlew test` runs configuration, math, pattern, buffer, session, scheduler, and success-receipt tests. The test suite includes 100,000 simulated shots and one shared scheduler serving 100 mocked players; these are algorithm tests, not a network load benchmark.

Artifacts are reproducibly ordered and omit timestamp metadata. `build/libs/*-sources.jar` contains the Java source. The plugin JAR manifest declares `paperweight-mappings-namespace: mojang`; the target Paper server uses Mojang names. Do not ship the development server bundle or MagicSpells dependencies with the addon.

## Code map

| Path under `src/main/java/dev/magicrecoil/` | Responsibility |
| --- | --- |
| `MagicRecoilPlugin.java` | Bootstrap, loader registration, events, lifecycle, diagnostics |
| `spells/RecoilSpell.java` | Beta 18 custom spell constructor and casting entry point |
| `spells/StopRecoilSpell.java` | Explicit stop/reset integration hook |
| `recoil/RecoilSettings.java` | Typed, immutable, validated literal settings |
| `recoil/RecoilPattern.java` | Normalized pattern generation and shot scaling |
| `recoil/ImpulseBuffer.java` | Fixed-size aggregate tick queue |
| `recoil/RecoilSession.java` | Burst counter, timeout, optional additive recovery |
| `recoil/RecoilManager.java` | UUID sessions and one synchronous task |
| `integration/SuccessGate.java` | Same-tick, single-use successful-cast receipts |
| `packet/RotationAdapter.java` | Version boundary |
| `packet/v1_21_11/Paper12111RotationAdapter.java` | Current NMS implementation |

Java integrations can call `MagicRecoilPlugin.instance().reset(player.getUniqueId())` on the main thread after checking that the plugin is enabled. Configuration-only integrations should prefer StopRecoilSpell.

## Porting to a later Minecraft version

1. Create a new adapter package and compile it against that version's Paper bundle; select the required Java toolchain from Paper's version-specific requirements.
2. Inspect the rotation packet and server update path again. Do not assume constructor fields, relative flags, or camera behavior stayed the same.
3. Inspect the target MagicSpells version's class-loader list, constructor contract, cast events, and reload sequence.
4. Add explicit version selection. Unsupported versions must fail with a clear message rather than fall back to position teleportation.
5. Repeat the runtime and human acceptance matrix in [Testing](testing.md). Only then extend the support table.

## CI and releases

GitHub Actions runs `clean build` with Java 21 on Linux and Windows for pushes and pull requests. It publishes build artifacts for download. A manual release should attach the plugin JAR, source JAR, and SHA-256 checksums, and state any uncompleted human/server acceptance tests. Never commit local server worlds, credentials, downloaded third-party JARs, or `.work/`.

Contributions should include tests for meaningful state, calculation, or lifecycle changes, and update both English and Korean documentation. Avoid promising frame-level smoothness from server packets.
