# MagicRecoil

[English](README.md) · [한국어](README.ko.md) · [Documentation](docs/en/README.md) · [한국어 문서](docs/ko/README.md)

**Relative camera recoil for existing MagicSpells guns on Paper 1.21.11.**

MagicRecoil adds a custom `RecoilSpell`. Keep your projectile, ammunition, damage, reload, sound, and firing logic; replace the recoil spell. Each shot adds small relative yaw/pitch impulses over server ticks. Players can compensate with normal mouse movement, and the default configuration never returns them to a saved camera angle.

> **Experimental release.** This is a server-side approximation, not a VALORANT camera animation. A vanilla client receives discrete rotations; there is no frame-rate interpolation contract. Local server/protocol checks and automated tests are documented in [Testing](docs/en/testing.md). Human camera-feel acceptance, real weapon integration, and latency/anti-cheat testing remain necessary.

## Requirements

| Component | Supported target |
| --- | --- |
| Server | Paper **1.21.11**, tested on build **132** |
| Java | **21** for both build and server |
| MagicSpells | Official **4.0-Beta-18** release |
| Build | Gradle **9.3.0**, wrapper included |
| ProtocolLib | Not required |
| Client | Vanilla Java 1.21.11; no client mod required for basic recoil |

The supplied `ms-MagicSpells-4.0-Beta-18.jar` filename alone does not establish binary identity: this project compiles against the checksum-pinned official release. That release includes its own shaded EffectLib. MagicRecoil has no separate EffectLib dependency; keep the dependencies required by your existing MagicSpells distribution. A separately supplied `ms-EffectLib-9.0.jar` was not available for testing.

Other Minecraft versions, Folia, protocol translators, and modified MagicSpells builds are not validated. The plugin rejects server versions other than 1.21.11 and MagicSpells versions other than 4.0-Beta-18. The isolated rotation adapter makes a future 26.3 port possible; **26.3 support is not included**.

## Install

1. Download `MagicRecoil-0.1.0-alpha.1.jar` from [Releases](https://github.com/minjae9010/MagicRecoil/releases), or build it below.
2. Stop the server. Put the JAR in `plugins/` alongside MagicSpells. Do not place another copy inside `plugins/MagicSpells/`.
3. Put the following spell in `plugins/MagicSpells/spells-recoil.yml`:

   ```yaml
   MyGun_Recoil:
     spell-class: "dev.magicrecoil.spells.RecoilSpell"
     recoil: 2.5
   ```

4. Start the server. Check for `Enabled relative rotation` and no missing/malformed spell classes.
5. From the console, try `ms cast as PlayerName MyGun_Recoil`. With appropriate MagicSpells permissions/spell ownership, players can use `/ms cast MyGun_Recoil`.
6. For a real gun, follow [Integration](docs/en/integration.md) to gate recoil on successful shots and stop pending recoil on reload.

Use the **full class name**. Beta 18 expands `.RecoilSpell` into MagicSpells' own package, not the addon's package. MagicRecoil registers its class loader through MagicSpells' public API. No MagicSpells JAR patching or duplicate classes are needed.

## A practical gun preset

```yaml
MyGun_Recoil:
  spell-class: "dev.magicrecoil.spells.RecoilSpell"
  helper-spell: true
  recoil: 2.5
  vertical: 1.0
  horizontal: 0.4
  smoothness: 0.8
  growth: 0.08
  reset-delay: 400
  pattern: valorant
  auto-return: false
  require-success-of: MyGun_Projectile
  stop-on-spells: [MyGun_Reload_Start]
  stop-on-buff-end: [MyGun_Firing_Buff]
```

Replace the three referenced names with actual spell names. In the existing successful-fire branch, execute `MyGun_Projectile` immediately before `MyGun_Recoil`, in the same tick. `require-success-of` consumes a single successful cast receipt; it prevents an unrelated recoil call from following a failed projectile attempt. It cannot repair ammunition checks that your gun configuration never performs. In particular, MagicSpells' default partial subcasts do not run all normal cost/cooldown checks.

The projectile fires before the new shot's recoil, and subsequent shots use the updated aim known to the server. Do not put one recoil call after each pellet unless you intentionally want recoil per pellet.

## Features

- Per-player burst growth and four patterns: `valorant`, `vertical`, `random`, `custom`.
- Numbers-only presets for AR, SMG, LMG, SR, DMR, SG, pistols, or any custom weapon. Categories are not encoded in Java.
- Explicit custom pattern `loop`, `hold`, or `stop` behavior.
- Relative rotation-only packets: no coordinate update, velocity reset, or teleport-confirmation cycle.
- One shared, synchronous scheduler; fixed 20-slot impulse buffer per active player; at most one recoil packet per player per tick.
- Strict finite-number/range validation, per-shot and per-tick limits, pitch clamping.
- Cleanup on weapon/spell switch, inventory interactions, death, logout, teleport, world change, MagicSpells reload, and plugin disable.
- Optional additive recovery, disabled by default. It subtracts delivered recoil and does not restore an absolute aim point.

## Configuration and examples

See the [full configuration reference](docs/en/configuration.md) for units, ranges, formulas, precedence, and advanced options.

| File | Purpose |
| --- | --- |
| [recoil-examples.yml](examples/recoil-examples.yml) | Minimal example, seven weapon presets, custom pattern, stop spell |
| [paimon-recoil.yml](examples/paimon-recoil.yml) | Replacement for `Weapon_파이먼_반동` only |
| [Integration guide](docs/en/integration.md) | Success gating, reload hooks, Paimon migration, troubleshooting |

The original `spells-[Weapon_3AR] 8파이먼.yml` was **not supplied**. The Paimon example uses only the names in the request. It is a replacement fragment, not a verified edit of the complete weapon file. Existing projectile, ammunition, damage, reload, effects, and ON/OFF logic must be preserved and tested against the original.

## Reload and commands

- `/ms reload`: re-read settings, load new recoil spells, cancel pending impulses, reset burst state, and re-register the addon class loader.
- Restart the server after changing Java classes or replacing a JAR. Do not use Bukkit `/reload`, PlugMan, or other hot-unload tools.
- `/magicrecoil status`: adapter, active player count, and lifetime recoil packet count.
- `/magicrecoil reset <player>`: clear pending recoil and the player's success receipt.
- Alias: `/mrecoil`. Both commands require `magicrecoil.admin` (default: operator).

There is no global recoil `config.yml`: each MagicSpells spell owns its settings.

## Build from source

Install a JDK 21 and set `JAVA_HOME` to it. The first build needs internet access for Gradle, Paper's development bundle, the pinned MagicSpells release, and test dependencies.

```sh
# Linux / macOS
./gradlew clean build
```

```powershell
# Windows PowerShell
.\gradlew.bat clean build
```

Output: `build/libs/MagicRecoil-0.1.0-alpha.1.jar`. The `-sources.jar` is source code, not a server plugin. The plugin JAR does not bundle Paper, Minecraft, MagicSpells, ProtocolLib, or EffectLib. It declares Mojang mappings for Paper's runtime. `reobfJar` is not needed for this target.

`./gradlew test` runs automated tests; the HTML report is `build/reports/tests/test/index.html`. GitHub Actions builds and tests on Linux and Windows and uploads the plugin and source JARs. See [Development](docs/en/development.md).

## Limits you should evaluate

- A 20 TPS server supplies roughly 50 ms steps; low TPS increases animation duration. `smoothness` changes the distribution across ticks, not client FPS.
- Network delay means server and client aim cannot be instantaneously identical. Already-in-flight mouse packets may briefly overwrite the server's estimate before the client reports its updated angle. This addon deliberately does not rewrite inbound movement packets.
- The server cannot distinguish arbitrary gun metadata changes in the same slot without integration. Different recoil spells reset the active pattern; silent in-place item replacements should call `StopRecoilSpell` or the reset API.
- Pending impulses finish after firing stops. Burst growth resets after `reset-delay`; a reload/stop hook cancels the tail immediately.
- `auto-return: true` can counteract player compensation. Keep it off for the intended FPS behavior.
- There is no promised elimination of jitter, rubber-banding, or conflicts with other plugins. Compare against RotateSpell on the target server before deployment.

If this does not feel materially better than RotateSpell in your tests, treat acceptance as failed. Frame-time smoothing and stronger input integration require a client mod or a client/server protocol designed for recoil. See [Architecture and investigation](docs/en/architecture.md).

## License

GPL-3.0-only; see [LICENSE](LICENSE). MagicRecoil is independent of MagicSpells, PaperMC, Mojang, Microsoft, and Riot Games. “Valorant” describes the broad rise-then-sway idea, not copied game patterns or an official affiliation.
