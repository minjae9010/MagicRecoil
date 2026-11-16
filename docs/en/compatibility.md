# Version compatibility

[Documentation](README.md) · [한국어](../ko/compatibility.md)

The minimum is **Paper 1.21.4 / MagicSpells 4.0-Beta-17**. Version equality locks are removed. Later beta, RC, and stable MagicSpells releases are accepted if the binary APIs used by the addon remain compatible. Server versions above the minimum are accepted if a supported native rotation method exists. This is a compatibility policy, not a promise that unknown future API changes need no maintenance.

<!-- toc:start -->
## On this page

- [Automatic selection](#automatic-selection)
- [Verification on 2026-11-15](#verification-on-2026-11-15)
- [Integration with aiming and firing](#integration-with-aiming-and-firing)
- [Build and repeat checks](#build-and-repeat-checks)
- [Upstream evidence](#upstream-evidence)
<!-- toc:end -->

## Automatic selection

At startup, the addon checks MagicSpells' class-loader API, spell constructor/cast contracts, configuration access, event methods, and required enum constants. An incompatible release produces a startup error before the addon registers its spell loader.

The Paper adapter resolves the Mojang-mapped player handle, connection, angle getters, and rotation method once. It prefers `forceSetRotation(float, boolean, float, boolean)` for relative yaw/pitch. Older servers use `forceSetRotation(float, float)` with absolute target angles calculated from current server aim. Both paths update server aim and send rotation-only packets, without position teleportation. Absolute packets can overwrite mouse changes made while the packet is in transit; the startup warning and `/magicrecoil status` identify this mode.

Folia is unsupported because the scheduler assumes one server thread. Protocol translators, modified server/plugin distributions, anti-cheat, and competing camera plugins require separate checks. Use the Java version required by the server: Java 21 for the tested 1.21.x combinations and Java 25 for the tested 26.2 combination. A newer MagicSpells release may also raise its own Java/server minimum.

## Verification on 2026-11-15

One addon JAR compiled against Paper 1.21.4 and Beta 17 was used in all three isolated local servers:

| Paper | MagicSpells | Java | Result |
| --- | --- | --- | --- |
| 1.21.4 build 232 | 4.0-Beta-17 | 21 | Startup, custom spells, status, reload, and two 3° recoil casts passed. Six absolute rotation packets; only the initial login position packet. |
| 1.21.11 build 132 | 4.0-Beta-18 | 21 | Same checks passed with six relative rotation packets and only the initial login position packet. |
| 26.2 build 135 | 4.0-Beta-19 | 25 | Startup, custom spell loading, status, and reload passed. No 26.2 protocol client was available, so in-game rotation was not tested. |

The protocol checks are headless, without rendering or physical mouse input. Human camera feel, latency/anti-cheat combinations, and every intermediate server version have not been certified. The earlier recorded GIF and detailed measurements in [Testing](testing.md) remain historical evidence for 1.21.11/Beta 18.

Automated tests cover numeric prerelease ordering, minimum-version rejection, the real MagicSpells API, both native invocation shapes, pitch/tick limits, disconnected/invalid state, async rejection, and native failures, alongside the existing recoil tests. CI builds the Beta 17 baseline on Linux/Windows and Beta 18/19 APIs on Linux. Java 25-compatible Mockito is a test dependency only.

## Integration with aiming and firing

The `0.2.0-alpha.2` source build also retains the aiming, buffered right-click fire, and Slowness scope features from `0.2.0-alpha.1`. Legacy abilities-packet zoom now discovers the native API and copies current abilities without linking to 1.21.11 classes. Its transport tests verify flight flags, original-state isolation, and restoration of current server values.

On 2026-11-16 the integrated build passed 161 automated tests and the three server startup/reload checks above. The 1.21.4 and 1.21.11 protocol checks again confirmed two recoil casts across reload with only rotation packets after login.

Optional `NATIVE_USE` requires Paper's `USE_EFFECTS` component API. If absent, the addon rejects that gun binding with a `BUFFERED_CLICK` configuration hint. Buffered/repeated click modes and recoil remain available. It does not add unsupported item behavior to an old protocol.

## Build and repeat checks

The default `./gradlew build runtimeProbeJar` uses the oldest supported API and emits Java 21 bytecode. No server development bundle or production ProtocolLib dependency is required. Known official Beta 17/18/19 JARs are SHA-256 pinned.

```sh
./gradlew build runtimeProbeJar -PmagicSpellsVersion=4.0-Beta-18 -PpaperVersion=1.21.11-R0.1-SNAPSHOT
# Use a JDK 25 for this API check; output classes still target Java 21.
./gradlew build runtimeProbeJar -PmagicSpellsVersion=4.0-Beta-19 -PcompilerJava=25
```

In PowerShell, quote each property argument, for example `'-PmagicSpellsVersion=4.0-Beta-19'`. For another official release, provide `-PmagicSpellsSha256=<verified-sha256>` as well. Changing a build override does not change the runtime minimum. Server and MagicSpells must support each other too; installing Beta 19 on an older server is not implied by the addon minimum.

Use the [runtime fixtures](../../tests/runtime/README.md) with `RECOIL_TEST_VERSION` and `RECOIL_TEST_PORT` for the matching protocol/server. Repeat successful/failed firing, stop hooks, pitch clamping, reload/new spells, and lifecycle cleanup. See [Testing](testing.md) for the human acceptance checklist.

## Upstream evidence

- [Beta 17 release and supported server versions](https://github.com/TheComputerGeek2/MagicSpells/releases/tag/4.0-Beta-17), [Beta 18](https://github.com/TheComputerGeek2/MagicSpells/releases/tag/4.0-Beta-18), [Beta 19](https://github.com/TheComputerGeek2/MagicSpells/releases/tag/4.0-Beta-19).
- [Beta 17 spell API](https://github.com/TheComputerGeek2/MagicSpells/blob/4.0-Beta-17/core/src/main/java/com/nisovin/magicspells/Spell.java) and [Beta 19 spell API](https://github.com/TheComputerGeek2/MagicSpells/blob/4.0-Beta-19/core/src/main/java/com/nisovin/magicspells/Spell.java).
- [Paper 1.21.4 CraftPlayer rotation](https://github.com/PaperMC/Paper/blob/ver/1.21.4/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java), [Paper 1.21.10 CraftEntity rotation](https://github.com/PaperMC/Paper/blob/ver/1.21.10/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftEntity.java).
