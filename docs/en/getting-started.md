# From installation to the first shot

[Documentation](README.md) · [한국어](../ko/getting-started.md)

For server operators installing the addon for the first time. Verify a minimal recoil spell before connecting an existing gun so installation failures and integration failures are easier to distinguish.

<!-- toc:start -->
## On this page

- [1. Check the supported environment](#1-check-the-supported-environment)
- [2. Install the plugin and a minimal spell](#2-install-the-plugin-and-a-minimal-spell)
- [3. Check minimal recoil on the server](#3-check-minimal-recoil-on-the-server)
- [4. Choose horizontal behavior](#4-choose-horizontal-behavior)
- [5. Connect the gun's successful-fire branch](#5-connect-the-guns-successful-fire-branch)
- [6. Apply changes and finish acceptance](#6-apply-changes-and-finish-acceptance)
<!-- toc:end -->

## 1. Check the supported environment

| Component | Target |
| --- | --- |
| Server | Paper 1.21.4+ (compatible rotation API) |
| Java | 21+ (26.x: 25) |
| MagicSpells | 4.0-Beta-17+ |
| MagicRecoil | One plugin JAR; do not install the sources JAR |

ProtocolLib is unnecessary. See [Version compatibility](compatibility.md) for newer versions, older absolute rotation behavior, and tested combinations. Folia is unsupported; protocol translators need separate testing. See [Requirements](../../README.md#requirements) for details.

## 2. Install the plugin and a minimal spell

Stop the server and put the plugin JAR from [Releases](https://github.com/minjae9010/MagicRecoil/releases) in `plugins/`, alongside MagicSpells. Do not put another addon JAR inside `plugins/MagicSpells/`.

Create `plugins/MagicSpells/spells-recoil.yml`:

```yaml
Test_Recoil:
  spell-class: "dev.magicrecoil.spells.RecoilSpell"
  recoil: 0.5
  pattern: vertical
  growth: 0.0
  recoil-steps: 2
```

Use the full class name. This spell deliberately tests upward recoil only; it has no gun or ammunition integration yet.

## 3. Check minimal recoil on the server

Start the server and look for `Enabled rotation:` and completion of MagicSpells loading. With a player connected, use the **server console**:

```text
ms cast as PlayerName Test_Recoil
magicrecoil status
```

Facing straight ahead without mouse input, the player should receive a total 0.5° upward across two ticks. Replace `PlayerName` with the connected player's actual name. Player commands require appropriate MagicSpells permissions and spell ownership.

## 4. Choose horizontal behavior

| Goal | Configuration |
| --- | --- |
| Upward-only check | Keep `pattern: vertical` |
| Sideways motion from the first shot | Random with horizontal bounds/scaling; see the [constant vertical/random horizontal example](recoil-logic.md#horizontal-motion-from-the-first-shot-with-constant-vertical-recoil) |
| Sway after initial shots | Valorant; default yaw begins on shot five |
| A chosen sequence | Custom with a point list |

Increasing horizontal scaling does not make every pattern move sideways. Read [Recoil logic](recoil-logic.md) first.

## 5. Connect the gun's successful-fire branch

Preserve ammunition and reload checks. Run the **firing spell then recoil** consecutively in the same tick, and point `require-success-of` at the firing spell actually used. Switching from a particle projectile to a beam also requires updating this reference.

Connect reload-start and firing-buff-end stop hooks. Adding a success guard to the isolated minimal spell prevents it from running without that firing receipt. See [Integration](integration.md) for a complete connection example.

## 6. Apply changes and finish acceptance

| Change | Apply it with |
| --- | --- |
| YAML values, patterns, or new spells | `/ms reload`, then inspect spell-loading errors |
| Java code or plugin JAR | Full server restart |
| Clear pending recoil | `/magicrecoil reset PlayerName` |

Once recoil works, check empty ammunition, reload, weapon switching, and mouse compensation. Follow [Testing](testing.md) for acceptance, or use [Troubleshooting](troubleshooting.md) for unexpected behavior.

---

[Previous: Documentation](README.md) · [Documentation](README.md) · [Next: Recoil logic](recoil-logic.md)
