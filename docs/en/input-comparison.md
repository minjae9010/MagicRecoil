# Existing gun plugin comparison

[한국어](../ko/input-comparison.md) · [Input and configuration](aim-and-input.md)

The user's recollection is correct: existing server-only gun plugins implement held-right-click firing, often by treating repeated interaction events as evidence that the button is still down. This differs from receiving an exact mouse-release signal. Sources were inspected on 2026-11-15; commit links below identify the inspected code, not a claim that every release behaves identically.

| Plugin | Observed implementation | Consequence |
| --- | --- | --- |
| WeaponMechanics | `PlayerWrapper.rightClicked()` records time. `isRightClicking()` normally accepts the last 215 ms, or `ping + 15 ms` for ping over 215 ms. Blocking is a separate direct check. | Practical repeated-event tracking with a release grace period |
| QualityArmory | `GunUtil.basicShoot()` runs automatic shots and cancels the RMB path after more than 310 ms since `getLastTimeRMB()`. | Releasing is inferred from missing click events |
| CrackShot | Official documentation explicitly recommends right-click for automatic weapons; left-click hold is distinguished from right-click repeat. | Right-click-to-fire and left-click-to-scope is an established control layout; official 0.98.13 bytecode also confirms a short queue of future firing opportunities |

Sources: [WeaponMechanics PlayerWrapper](https://github.com/WeaponMechanics/WeaponMechanics/blob/f9d2c4296e6b8d252ca00657861a7784dbd538f7/weaponmechanics-core/src/main/java/me/deecaad/weaponmechanics/wrappers/PlayerWrapper.java), [QualityArmory GunUtil](https://github.com/Lorenzo0111/QualityArmory/blob/2ba1ef96bc1f6b166a4eea676bf78f4bc51ea153/src/main/java/me/zombie_striker/qg/guns/utils/GunUtil.java), [CrackShot official guide](https://github.com/Shampaggon/CrackShot/wiki/The-Complete-Guide-to-CrackShot#right_click_to_shoot).

<!-- toc:start -->
## On this page

- [CrackShot binary verification](#crackshot-binary-verification)
- [Scoping](#scoping)
- [MagicRecoil's choices](#magicrecoils-choices)
<!-- toc:end -->

## CrackShot binary verification

The official [CrackShot 0.98.13 download](https://dev.bukkit.org/projects/crackshot/files/6635698) has MD5 `2246894bb6a4aa1a212b25ba31c4d1bc`; the downloaded JAR matched. `javap -c -p` inspection of `CSDirector.OnPlayerInteract`, the firing method and `CSDirector$4.run` shows: fully automatic mode sets five one-tick opportunities, tracks pending work with `rpm_shots`, and refills missing opportunities on further input. The task decrements pending work and checks the configured rate before firing. This is a finite future-work buffer, not a universal release packet. Five opportunities do not mean five bullets at every configured fire rate. Slot/ammo/lifecycle checks can stop shots earlier.

The same version's scope path toggles `ironsights` metadata and adds Slowness, preserving/restoring an existing effect. It does not require native spyglass use. This was static inspection, not running CrackShot on Paper 1.21.11. The JAR and bytecode output remain ignored local research artifacts and are not redistributed in MagicRecoil.

## Scoping

WeaponMechanics' [OutAbilitiesListener](https://github.com/WeaponMechanics/WeaponMechanics/blob/f9d2c4296e6b8d252ca00657861a7784dbd538f7/weaponmechanics-core/src/main/java/me/deecaad/weaponmechanics/packetlisteners/OutAbilitiesListener.java) adjusts the abilities packet's FOV modifier. [ScopeHandler](https://github.com/WeaponMechanics/WeaponMechanics/blob/f9d2c4296e6b8d252ca00657861a7784dbd538f7/weaponmechanics-core/src/main/java/me/deecaad/weaponmechanics/weapon/scope/ScopeHandler.java) sends the abilities update when toggling scope. This does not require replacing the gun with a spyglass. Its [scope documentation](https://cjcrafter.gitbook.io/weaponmechanics/weapon-modules/scope) separately describes a pumpkin overlay; an overlay and actual magnification are distinct.

## MagicRecoil's choices

- `BUFFERED_CLICK` (primary): refill at most five upcoming firing opportunities from repeated right-click input, preserving all existing item components. Normal interval, ammo, reload and hard-cast checks remain. Missing input drains the buffer within five ticks; this is not exact release.
- `NATIVE_USE` (optional): prepare supported sticks/rods/hoes for native item use and stop on the real Paper use-release event. Retains the gun's material, model and other metadata, without a missing-event timeout.
- `REPEATED_CLICK`: compatibility mode using repeated right-click events, one server-tick scanner and an explicit `release-timeout-ticks` (default 6, about 300 ms at 20 TPS). It leaves item components untouched and is labelled as release estimation.
- Left-click AimSpell toggles ADS. Optional `zoom.mode: FOV_EFFECT` sends a copied abilities packet and restores the current real abilities when ADS ends. The client FOV Effects setting controls its visible strength; 0% disables the visual change. It is not a native spyglass or an exact optical zoom guarantee.

No upstream gun-plugin code is embedded or added as a dependency. The implementations remain synchronous Paper/MagicSpells addon code. See [runtime evidence](testing.md#right-click-firing-and-fov-verification).

The primary left-click scope now also supports CrackShot-style Slowness through `zoom.mode: SLOWNESS`. It preserves the original gun item and recoil multiplier, backs up the active potion with remaining-time restoration, and gives newer external potion changes priority. See [scope configuration](aim-and-input.md#crackshot-style-slowness-zoom).
