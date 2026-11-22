# MagicRecoil documentation

[Project overview](../../README.md) · [한국어](../ko/README.md) · [GitHub Wiki](https://github.com/minjae9010/MagicRecoil/wiki)

For a first installation, follow [Getting started](getting-started.md) to see the camera move in game. To adjust a working spell, [read a spell by example](configuration.md#read-a-spell-by-example) and change one value to see what it does.

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
| Check supported versions | [Version compatibility](compatibility.md) | Minimum versions, relative/absolute mode, validation matrix |
| Enable horizontal recoil | [Recoil logic](recoil-logic.md) | Zero yaw in vertical, random example, first four valorant shots |
| Change kick strength or the gap between bursts | [Recoil properties](configuration.md#basic-options) | What changes on screen, defaults and units |
| Adjust movement speed and kick while aiming | [Aim properties](configuration.md#aim-settings) | Affected spells, multipliers and conditions that end aim |
| Connect an existing gun | [Integration](integration.md) | Beam/PP guard names, shot order, reload cancellation |
| Add optional ADS and right-click firing | [Aiming and input](aim-and-input.md) | Full settings, Paimon integration, native/repeated input and FOV limits |
| Compare existing gun controls | [Gun-plugin comparison](input-comparison.md) | CrackShot firing queue, repeated-click timeouts and scope implementations |
| Add headshots and directional/distance damage | [Hit detection and callbacks](hit-detection.md) | HitSpell, existing-projectile callbacks, follow-up spells |
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
