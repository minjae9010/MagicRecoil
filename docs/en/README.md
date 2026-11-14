# MagicRecoil documentation

[Project overview](../../README.md) · [한국어](../ko/README.md) · [GitHub Wiki](https://github.com/minjae9010/MagicRecoil/wiki)

For operators connecting recoil to existing guns and developers inspecting the implementation. Choose a task below and follow the relevant reading path.

<!-- toc:start -->
## On this page

- [First-time setup](#first-time-setup)
- [Find a task](#find-a-task)
- [Operator and developer reading paths](#operator-and-developer-reading-paths)
- [Using these guides](#using-these-guides)
<!-- toc:end -->

## First-time setup

1. **[Installation and first shot](getting-started.md)** — verify the environment and minimal spell.
2. **[Recoil logic](recoil-logic.md)** — understand axis multipliers and patterns.
3. **[Gun integration](integration.md)** — connect successful fire, ammunition checks, and reload stops.
4. **[Acceptance testing](testing.md)** — check mouse compensation, bursts, and weapon switching.

A working recoil spell does not establish that ammunition or reload integration is complete. Preserve and verify the existing gun's successful-fire branch.

## Find a task

| Goal | Guide | What to find |
| --- | --- | --- |
| Install and run the addon | [Getting started](getting-started.md) | JAR location, minimal YAML, console checks |
| Enable horizontal recoil | [Recoil logic](recoil-logic.md) | Zero yaw in vertical, random example, first four valorant shots |
| Look up an option | [Configuration](configuration.md) | Defaults, units, valid ranges, operation order |
| Connect an existing gun | [Integration](integration.md) | Beam/PP guard names, shot order, reload cancellation |
| Diagnose a failure | [Troubleshooting](troubleshooting.md) | Missing yaw, no recoil, resets, strength and spread |
| Review evidence and limits | [Testing](testing.md) | Automated, running-server, and human acceptance boundaries |
| Understand packets and state | [Architecture](architecture.md) | Relative rotation, class loading, per-player state |
| Build or port the addon | [Development](development.md) | Gradle, code map, CI, Wiki synchronization |

## Operator and developer reading paths

**Operators:** getting started → recoil logic → integration → testing. Use configuration while tuning numbers and troubleshooting when behavior is unexpected.

**Developers:** recoil logic → architecture → development → testing. Update both languages and the validation scope when behavior changes.

## Using these guides

Each page starts with a linked table of contents. Language links open the same topic in the other language. Previous/next links follow the recommended reading sequence.

Repository docs are the source of truth and are also published to GitHub Wiki. See [Documentation maintenance](development.md#documentation-and-wiki-maintenance) for editing, TOC regeneration, and Wiki export.

---

[Documentation](README.md) · [Next: Getting started](getting-started.md)
