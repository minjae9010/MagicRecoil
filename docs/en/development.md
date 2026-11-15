# Development

[English index](README.md) · [한국어](../ko/development.md)

For building, code changes, version ports, and documentation maintenance. For installation only, use [Getting started](getting-started.md); see [Architecture](architecture.md) for the design.

<!-- toc:start -->
## On this page

- [Reproducible entry point](#reproducible-entry-point)
- [Code map](#code-map)
- [Porting to a later Minecraft version](#porting-to-a-later-minecraft-version)
- [CI and releases](#ci-and-releases)
- [Documentation and Wiki maintenance](#documentation-and-wiki-maintenance)
- [ADS Java changes](#ads-java-changes)
- [Firing and FOV Java changes](#firing-and-fov-java-changes)
<!-- toc:end -->

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

## Documentation and Wiki maintenance

Edit repository READMEs and docs/ko plus docs/en as the source of truth. Keep purpose notes and previous/next links, and update both languages. TOCs between toc:start and toc:end are generated; do not edit them manually.

```sh
python scripts/docs.py
python scripts/docs.py --check
```

The first command regenerates TOCs; the second checks TOCs, local links, and heading anchors without changing documents. Only Python standard-library modules are needed.

Wiki is a separate Git repository. If it has no pages yet, create Home once through GitHub. Review the generated diff before publishing, and publish source documentation changes too so repository links resolve.

```sh
git clone https://github.com/minjae9010/MagicRecoil.wiki.git .work/wiki
python scripts/docs.py --export-wiki .work/wiki
git -C .work/wiki diff --check
git -C .work/wiki add -- "*.md"
git -C .work/wiki commit -m "Update bilingual documentation"
git -C .work/wiki push origin HEAD
```

For an existing checkout, use git -C .work/wiki pull --ff-only instead of cloning again. Export generates both languages, Home, sidebar, and footer, rewriting relative document links for Wiki. Unrelated Wiki pages are not deleted. Never commit credentials or .work.

---

[Previous: Architecture](architecture.md) · [Documentation](README.md)

## ADS Java changes

New production files: `aiming/AimSettings.java`, `aiming/AimManager.java`, `spells/AimSpell.java`, `spells/StopAimSpell.java`. Modified production files: `MagicRecoilPlugin.java`, `spells/RecoilSpell.java`, `recoil/RecoilManager.java`, `recoil/RecoilSession.java`. These paths are under `src/main/java/dev/magicrecoil/`. The rotation adapter, pattern/settings implementations, success gate and existing stop spell are unchanged. The optional runtime probe adds ADS snapshots; JUnit additions cover settings, manager behavior and aimed recoil.

## Firing and FOV Java changes

New production files under `src/main/java/dev/magicrecoil/`: `firing/AutoFireSettings.java`, `firing/AutoFireManager.java`, `firing/UseItemSupport.java`, `spells/AutoFireSpell.java`, `spells/StopAutoFireSpell.java`, `aiming/ZoomAdapter.java` and `packet/v1_21_11/Paper12111ZoomAdapter.java`. `MagicRecoilPlugin`, `AimSettings` and `AimManager` register and reconcile these optional features. The rotation adapter, recoil patterns and success gate are unchanged by this follow-up. Tests cover intervals, timeout, reload latch, normal hard-cast invocation, cast-time rejection, FOV math and restoration. See [runtime evidence](testing.md#right-click-firing-and-fov-verification).
