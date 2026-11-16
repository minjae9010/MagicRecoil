# Changelog

## Unreleased

- Extend compatibility to Paper 1.21.4+ and MagicSpells 4.0-Beta-17+. Accept compatible later beta, RC, and stable releases after numeric version and binary API checks. Discover and cache relative/absolute rotation-only native methods at startup instead of linking to Paper 1.21.11. Keep the current aiming/firing features, discover legacy FOV packet APIs, and reject NATIVE_USE with a clear hint when USE_EFFECTS is absent. Lower the API baseline, add Beta 17/18/19 CI coverage, and retain Java 21 plugin bytecode. Build version: 0.2.0-alpha.2. See the bilingual compatibility guides for verified combinations and limits.

## 0.2.0-alpha.1 — 2026-11-15

- Publish CrackShot-style right-click firing and left-click Slowness zoom. Gun items are unchanged; complete existing firing entries retain ammunition, reload checks, costs, cooldowns and recoil. Slowness scope defaults to recoil multiplier 1.0.
- Verify 115 unit tests, real Paper input/potion/recoil checks and restart cleanup; update both READMEs and the bilingual Wiki. Target remains Paper 1.21.11 / official MagicSpells 4.0-Beta-18 / Java 21.
- Add optional AimSpell / StopAimSpell, scoped per-shot recoil reduction and transient movement modifiers with lifecycle cleanup.
- Make input-refilled five-tick BUFFERED_CLICK firing the primary item-preserving mode. Add left-click SLOWNESS scope with timed restoration and external-effect ownership; the primary example keeps recoil at 1.0.
- Add right-click AutoFireSpell / StopAutoFireSpell: native item-use tracking for sticks, blaze rods and hoes, plus an explicit repeated-click timeout mode. Hard-cast the full existing synchronous firing entry to preserve costs, cooldowns, ammunition, reload checks and guarded recoil. Keep legacy Buff firing available.
- Bind the new Paimon ADS example to left click and preserve the previous right-click example. Add opt-in abilities-packet FOV effects with current-abilities restoration and documented client-setting limits. Native spyglass, exact left-click HOLD and automatic spread remain excluded.
- Add bilingual configuration, CrackShot/WeaponMechanics/QualityArmory comparison and real Paper firing/FOV packet fixtures.

## 0.1.0-alpha.3 — 2026-11-14

- Fix built-in valorant yaw integration so camera offsets cross both sides of the burst center, including with growth. Add a cumulative upward angle limit through `valorant-max-rise` (default 15 degrees), independent of a fixed bullet count. This remains an original approximation, not Riot's weapon data. Build version: 0.1.0-alpha.3.
- Add task-based bilingual documentation navigation, per-page tables of contents, getting-started/recoil-logic/troubleshooting guides, previous/next links, and a checked GitHub Wiki exporter with Home, sidebar, and footer.
- Document recoil formulas, pattern-specific horizontal behavior, fixed-vertical/random-horizontal setup, tick weights, limits, success gating, resets, and recovery. Clarify that default valorant starts sideways motion on shot five.
- Use generic English spell identifiers and `weapon-integration.yml` throughout the examples and bilingual integration guides.
- Add an 18-shot visual test fixture for reproducible recoil recordings.
- Add an actual MCP-captured recoil GIF to both READMEs, with recording settings, measured results, and capture limitations.

## 0.1.0-alpha.1 — 2026-11-14

Initial experimental release for Paper 1.21.11 and MagicSpells 4.0-Beta-18.

- Relative rotation-only NMS adapter, per-player burst state, bounded tick interpolation.
- Four patterns, strict numeric settings, optional additive recovery, successful-shot guard and stop hooks.
- MagicSpells startup/reload class loading, lifecycle cleanup, diagnostics.
- Gradle wrapper, automated tests, optional protocol/trajectory test fixtures, Linux/Windows CI.
- English and Korean READMEs and detailed documentation.

Human camera-feel acceptance and migration of the unavailable original production weapon file remain pending. See the testing documentation for measured evidence and limits.
