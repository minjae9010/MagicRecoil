# Changelog

## Unreleased

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
