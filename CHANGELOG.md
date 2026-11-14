# Changelog

## Unreleased

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
