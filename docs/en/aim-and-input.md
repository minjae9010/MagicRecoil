# Aiming and input limits

[한국어](../ko/aim-and-input.md) · [Documentation](README.md)

MagicRecoil adds `dev.magicrecoil.spells.AimSpell` and `dev.magicrecoil.spells.StopAimSpell` for **Paper 1.21.11 / official MagicSpells 4.0-Beta-18**. ADS is optional; existing firing, commands, recoil settings and success gates retain their behavior when no AimSpell is cast. There is no new runtime dependency.

<!-- toc:start -->
## On this page

- [Supported scope](#supported-scope)
- [Binding and configuration](#binding-and-configuration)
- [Weapon ownership and restoration](#weapon-ownership-and-restoration)
- [Recoil and existing Buff guns](#recoil-and-existing-buff-guns)
- [Why HOLD, zoom and automatic spread are excluded](#why-hold-zoom-and-automatic-spread-are-excluded)
- [Verification](#verification)
<!-- toc:end -->

## Supported scope

| Feature | Result |
| --- | --- |
| Right-click ADS | One toggle per successful MagicSpells cast, using its native item binding |
| Non-toggle ADS | `toggle: false` activates idempotently; StopAimSpell or lifecycle hooks end it |
| Movement | One transient, keyed `MOVEMENT_SPEED` modifier; only this modifier is removed |
| Recoil | Per-player, per-recoil-spell scaling of new shots; existing pattern phase and growth retained |
| Left-click HOLD autofire | Excluded: no general attack-button release signal in the vanilla protocol |
| Zoom | Excluded: no verified, item-independent precise FOV API |
| Automatic spread reduction | Excluded: no generic validated per-cast spread override in this addon |

## Binding and configuration

Use the [Paimon merge fragment](../../examples/aim-paimon.yml). MagicSpells must already know the magic item `파이먼` and the existing `Weapon_파이먼_반동` RecoilSpell. For a plain test item:

```yaml
Gun_Aim:
  spell-class: "dev.magicrecoil.spells.AimSpell"
  always-granted: true
  right-click-cast-item: stick
  ignore-global-cooldown: false
  cooldown: 0.25
  recoil-spells: [Gun_Recoil]
  movement-speed-multiplier: 0.65
  recoil-multiplier: 0.5

Gun_StopAim:
  spell-class: "dev.magicrecoil.spells.StopAimSpell"
  helper-spell: true
```

The binding uses MagicSpells' normal spellbook/permission checks, `hardCast`, cooldown, reagents and modifiers. The addon does not listen for clicks or call a projectile spell directly. Match only the main-hand gun; Beta 18's right-click-item listener does not filter the event hand. Avoid another matching off-hand item and keep `ignore-global-cooldown: false` to suppress duplicate same-click events. Holding right click can cause repeated vanilla interactions and later toggles; this is **cast-to-toggle, not mouse press/release tracking**. Rapid clicks can be suppressed by MagicSpells cooldowns. Test the chosen item, interactable blocks and both hands on your server.

| AimSpell setting | Default | Values and behavior |
| --- | --- | --- |
| `enabled` | `true` | Boolean. `false` rejects activation; reload first clears existing ADS |
| `toggle` | `true` | Boolean. `false` activates without turning off on repeat casts; end explicitly |
| `cancel-on-item-swap` | `true` | Boolean. Hotbar changes end ADS; `false` retains a suspended state |
| `cancel-on-hand-swap` | `true` | Boolean. Successful F-key swaps end ADS; `false` suspends until the original item returns |
| `cancel-on-death` | `true` | Only `true` is accepted; death cleanup is mandatory |
| `cancel-on-logout` | `true` | Only `true` is accepted; logout cleanup is mandatory |
| `movement-speed-multiplier` | `0.65` | Finite YAML number in `[0.01, 1]`; `1` adds no speed modifier |
| `recoil-multiplier` | `0.5` | Finite YAML number in `[0, 1]`; `0` delivers no new-shot rotation |
| `recoil-spells` | Required when enabled | List of 1–128 RecoilSpell internal names for this gun; case insensitive |
| `stop-on-spells` | `[]` | Up to 128 spell names; their cast attempts end ADS, including failed attempts |
| `stop-on-buff-end` | `[]` | Up to 128 buff names; BuffEndEvent for the affected player ends ADS |
| `zoom.enabled` | `false` | Only `false` is supported |
| `zoom.level` | Unsupported | Supplying it rejects the configuration, even with zoom disabled |
| `trigger` | Unsupported | Use MagicSpells' `right-click-cast-item` instead |

All flags are literal YAML booleans and numeric options are literal YAML numbers. Unknown MagicSpells options remain MagicSpells' responsibility. StopAimSpell has no addon-specific options: it ends that player's ADS, including suspended ADS, without changing recoil or firing buffs. Existing StopRecoilSpell and `/magicrecoil reset` retain their recoil-only behavior.

## Weapon ownership and restoration

ADS remembers the **original hotbar slot and a cloned main-hand ItemStack**. Matching uses `ItemStack.isSimilar`, so quantity changes alone do not end ownership. Metadata changes (including lore, durability and custom data) suspend effects. Identical stacks in the same slot are indistinguishable; this is an item signature, not a unique physical weapon identity.

With a cancellation option disabled, changing to another slot/item retains the session but applies neither speed reduction nor recoil reduction to that item. Returning the matching item to the original slot resumes effects. A different AimSpell replaces the session; modifiers never stack. To bind a changed weapon, toggle off and on again (or call StopAimSpell, then activate). Inventory edits and plugin-driven item changes are reconciled by one shared synchronous task each tick. Slot/F events remove speed immediately; other item changes may take one tick. Recoil checks ownership on every cast. An inventory click alone does not cancel ADS.

The manager adds `magicrecoil:aim_speed` with amount `multiplier - 1` and operation `MULTIPLY_SCALAR_1` using Paper's `addTransientModifier`. The attribute's base value, equipment modifiers, potion effects and other plugins' modifiers are not overwritten or restored from a stale snapshot. With a baseline attribute value of `0.1`, ADS gives `0.065`; if an external effect changes the baseline to `0.12`, it gives `0.078`, and ending ADS leaves `0.12`. Attribute clamping and vanilla movement states mean this is not a guarantee of exact blocks per second, swimming speed or flight speed. Other plugins must not reuse this namespaced key. Removing all modifiers externally is detected and the active ADS modifier is restored on the next tick.

Death, logout, item drop, teleport, world change, MagicSpells reload/disable, AimSpell unload and addon shutdown clear ADS. Transient modifiers are not saved to player NBT; join cleanup also removes a stale addon key. The task stops when no sessions remain. Bukkit and MagicSpells state is never accessed asynchronously.

## Recoil and existing Buff guns

The full existing chain remains `Weapon_파이먼_Buff → Weapon_파이먼_Left_Ticks → Weapon_파이먼_Beam → Weapon_파이먼_반동`. Keep ammunition variable `Paimon`, reload modifiers, sounds, particles and the successful-fire branch. **Do not bind both the firing Buff and ADS to the same right-click item.** Opt in by assigning right click to ADS and retaining left click for the existing Buff On/Off system. To disable ADS, remove its binding or set `enabled: false` and run `/ms reload`; restore the old right-click Buff binding if desired.

Only names listed in `recoil-spells` receive reduction. Pattern sampling, `vertical`, `horizontal`, `growth`, random sequence, reset timing, steps and `require-success-of` remain in their original order. Each completed shot impulse is multiplied on both axes before it enters the existing smoothing buffer. Pattern progress and rise-cap accounting use the original unscaled pattern: sustained `0.5` ADS halves the total rise too. Changing ADS mid-burst keeps its phase. Previously queued impulses retain the multiplier used when fired; auto-return accounts for actual delivered rotation. No shared RecoilSettings or BeamSpell values are mutated.

Add the real reload-start spell to `stop-on-spells`. If a configuration uses direct calls without cast events, put StopAimSpell at the beginning of its reload chain beside the existing StopRecoilSpell. Stopping ADS does not stop the firing Buff: its existing reload/ammo gates must continue to do that.

MagicSpells Beta 18 [`Subspell`](https://github.com/TheComputerGeek2/MagicSpells/blob/4.0-Beta-18/core/src/main/java/com/nisovin/magicspells/Subspell.java) defaults to PARTIAL: it emits cast events/modifiers, but begins with NORMAL state and zero cost/cooldown. DIRECT calls `spell.cast(data)` with no such events. HARD/FULL for ordinary untargeted subcasts uses [`Spell.hardCast`](https://github.com/TheComputerGeek2/MagicSpells/blob/4.0-Beta-18/core/src/main/java/com/nisovin/magicspells/Spell.java) and the normal pre/post pipeline. This addon introduces no autofire caller and changes none of those semantics. Do not bypass `Left_Ticks` to call only Beam; a PassiveSpell is an event-driven configuration and is not automatically a reusable full-cost firing entry point.

## Why HOLD, zoom and automatic spread are excluded

Paper's [`Input`](https://jd.papermc.io/paper/1.21.11/org/bukkit/Input.html) exposes movement, jump, sneak and sprint, with no attack button state. Vanilla swing/attack packets report actions; block-dig start/abort/stop packets concern a particular block, and `RELEASE_USE_ITEM` concerns item use. They do not supply a universal left-click release, particularly while pointing into air or at entities. A repeated PlayerInteractEvent or swing timeout cannot prove mouse hold. ProtocolLib cannot recover information the client never sent. Therefore there is no AutoFireSpell, interval option, inferred timeout mode or replacement for existing Buff On/Off firing. Existing repeating spell tasks still run at server ticks; 1 tick is about 0.05 seconds at 20 TPS, with no zero-second guarantee.

Paper has no general precise player FOV setter in this target API. Movement-speed effects may influence FOV according to client settings, including FOV Effects, but do not provide `zoom.level: 2.0` or spyglass magnification. Abilities-packet walk-speed spoofing changes client predictions and competes with speed updates; it is excluded. A real spyglass requires its corresponding active item; spoofing equipment/use metadata would change item behavior and is not a verified arbitrary-gun zoom. Paper's [`startUsingItem`](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/LivingEntity.html#startUsingItem(org.bukkit.inventory.EquipmentSlot)) is experimental and does not guarantee the effect for players/items. No ProtocolLib dependency or FOV packet adapter is added.

Beta 18 [`BeamSpell`](https://github.com/TheComputerGeek2/MagicSpells/blob/4.0-Beta-18/core/src/main/java/com/nisovin/magicspells/spells/instant/BeamSpell.java) evaluates private `ConfigData` spread suppliers per cast. Weapon authors can configure dynamic values using existing MagicSpells mechanisms, but a generic addon override with correct per-player ADS ownership has not been validated. Automatic spread reduction is excluded; global spread fields and variables are not rewritten.

## Verification

See [ADS test evidence](testing.md#ads-extension-verification) and the [runtime fixture](../../tests/runtime/README.md#ads-integration-fixture). Unit tests, protocol-driven checks on real Paper, human mouse/rendering checks and production gun acceptance are reported separately.
