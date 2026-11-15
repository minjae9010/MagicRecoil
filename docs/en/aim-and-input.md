# Aiming and input limits

[한국어](../ko/aim-and-input.md) · [Documentation](README.md)

MagicRecoil adds `dev.magicrecoil.spells.AimSpell` and `dev.magicrecoil.spells.StopAimSpell` for **Paper 1.21.11 / official MagicSpells 4.0-Beta-18**. ADS is optional; existing firing, commands, recoil settings and success gates retain their behavior when no AimSpell is cast. There is no new runtime dependency. The current control layout is right-click firing and left-click ADS; legacy bindings remain available.

<!-- toc:start -->
## On this page

- [Supported scope](#supported-scope)
- [Binding and configuration](#binding-and-configuration)
- [Weapon ownership and restoration](#weapon-ownership-and-restoration)
- [Recoil and existing Buff guns](#recoil-and-existing-buff-guns)
- [Right-click automatic fire](#right-click-automatic-fire)
- [CrackShot-style Slowness zoom](#crackshot-style-slowness-zoom)
- [Optional FOV zoom](#optional-fov-zoom)
- [Left-click and spread limits](#left-click-and-spread-limits)
- [Verification](#verification)
<!-- toc:end -->

## Supported scope

| Feature | Result |
| --- | --- |
| Left-click ADS | One toggle per successful MagicSpells cast, using its native item binding |
| Non-toggle ADS | `toggle: false` activates idempotently; StopAimSpell or lifecycle hooks end it |
| Movement | One transient, keyed `MOVEMENT_SPEED` modifier; only this modifier is removed |
| Recoil | Per-player, per-recoil-spell scaling of new shots; existing pattern phase and growth retained |
| Left-click HOLD autofire | Excluded: no general attack-button release signal in the vanilla protocol |
| Optional FOV zoom | CrackShot-style Slowness scope or optional abilities-packet FOV effect; not native spyglass |
| Right-click automatic fire | Five-tick input-refilled buffer by default; optional native-use and timeout modes |
| Automatic spread reduction | Excluded: no generic validated per-cast spread override in this addon |

## Binding and configuration

Use the [Paimon merge fragment](../../examples/aim-paimon.yml). MagicSpells must already know the magic item `파이먼` and the existing `Weapon_파이먼_반동` RecoilSpell. For a plain test item:

```yaml
Gun_Aim:
  spell-class: "dev.magicrecoil.spells.AimSpell"
  always-granted: true
  left-click-cast-item: stick
  ignore-global-cooldown: false
  cooldown: 0.25
  recoil-spells: [Gun_Recoil]
  movement-speed-multiplier: 0.65
  recoil-multiplier: 0.5

Gun_StopAim:
  spell-class: "dev.magicrecoil.spells.StopAimSpell"
  helper-spell: true
```

The binding uses MagicSpells' normal spellbook/permission checks, `hardCast`, cooldown, reagents and modifiers. AimSpell adds no click listener. AutoFireSpell has its own registered use-item input and calls the full existing firing entry; it does not implement projectiles. Match only the main-hand gun; Beta 18's item-binding listener does not filter the event hand. Avoid another matching off-hand item and keep `ignore-global-cooldown: false` to suppress duplicate same-click events. Legacy right-click ADS can repeat when the button is held; use left-click ADS alongside right-click firing. ADS remains **cast-to-toggle, not mouse press/release tracking**. Rapid clicks can be suppressed by MagicSpells cooldowns. Test the chosen item, interactable blocks and both hands on your server.

| AimSpell setting | Default | Values and behavior |
| --- | --- | --- |
| `enabled` | `true` | Boolean. `false` rejects activation; reload first clears existing ADS |
| `toggle` | `true` | Boolean. `false` activates without turning off on repeat casts; end explicitly |
| `cancel-on-item-swap` | `true` | Boolean. Hotbar changes end ADS; `false` retains a suspended state |
| `cancel-on-hand-swap` | `true` | Boolean. Successful F-key swaps end ADS; `false` suspends until the original item returns |
| `cancel-on-death` | `true` | Only `true` is accepted; death cleanup is mandatory |
| `cancel-on-logout` | `true` | Only `true` is accepted; logout cleanup is mandatory |
| `movement-speed-multiplier` | `0.65`; `1` for SLOWNESS mode | Finite YAML number in `[0.01, 1]`; `1` adds no speed modifier |
| `recoil-multiplier` | `0.5`; `1` for SLOWNESS mode | Finite YAML number in `[0, 1]`; `0` delivers no new-shot rotation |
| `recoil-spells` | Required when enabled | List of 1–128 RecoilSpell internal names for this gun; case insensitive |
| `stop-on-spells` | `[]` | Up to 128 spell names; their cast attempts end ADS, including failed attempts |
| `stop-on-buff-end` | `[]` | Up to 128 buff names; BuffEndEvent for the affected player ends ADS |
| `zoom.enabled` | `false` | Boolean; enables the optional FOV effect |
| `zoom.mode` | Unset | Explicit `SLOWNESS` or `FOV_EFFECT` when zoom is enabled |
| `zoom.slowness-amplifier` | `2` | Integer `[0, 5]`, SLOWNESS only; 2 means Slowness III, not optical 3x |
| `zoom.fov-factor` | `0.65` | Finite number `[0.55, 1]`, target FOV factor at 100% FOV Effects, grounded and without other FOV effects |
| `zoom.level` | Unsupported | Use fov-factor instead; this is not an optical magnification guarantee |
| `trigger` | Unsupported | Use MagicSpells' `left-click-cast-item` for ADS |

All flags are literal YAML booleans and numeric options are literal YAML numbers. Unknown MagicSpells options remain MagicSpells' responsibility. StopAimSpell has no addon-specific options: it ends that player's ADS, including suspended ADS, without changing recoil or firing buffs. Existing StopRecoilSpell and `/magicrecoil reset` retain their recoil-only behavior.

## Weapon ownership and restoration

ADS remembers the **original hotbar slot and a cloned main-hand ItemStack**. Matching uses `ItemStack.isSimilar`, so quantity changes alone do not end ownership. Metadata changes (including lore, durability and custom data) suspend effects. Identical stacks in the same slot are indistinguishable; this is an item signature, not a unique physical weapon identity.

With a cancellation option disabled, changing to another slot/item retains the session but applies neither speed reduction nor recoil reduction to that item. Returning the matching item to the original slot resumes effects. A different AimSpell replaces the session; modifiers never stack. To bind a changed weapon, toggle off and on again (or call StopAimSpell, then activate). Inventory edits and plugin-driven item changes are reconciled by one shared synchronous task each tick. Slot/F events remove speed immediately; other item changes may take one tick. Recoil checks ownership on every cast. An inventory click alone does not cancel ADS.

The manager adds `magicrecoil:aim_speed` with amount `multiplier - 1` and operation `MULTIPLY_SCALAR_1` using Paper's `addTransientModifier`. The attribute's base value, equipment modifiers, potion effects and other plugins' modifiers are not overwritten or restored from a stale snapshot. With a baseline attribute value of `0.1`, ADS gives `0.065`; if an external effect changes the baseline to `0.12`, it gives `0.078`, and ending ADS leaves `0.12`. Attribute clamping and vanilla movement states mean this is not a guarantee of exact blocks per second, swimming speed or flight speed. Other plugins must not reuse this namespaced key. Removing all modifiers externally is detected and the active ADS modifier is restored on the next tick.

Death, logout, item drop, teleport, world change, MagicSpells reload/disable, AimSpell unload and addon shutdown clear ADS. Transient modifiers are not saved to player NBT; join cleanup also removes a stale addon key. The task stops when no sessions remain. Bukkit and MagicSpells state is never accessed asynchronously.

## Recoil and existing Buff guns

The full existing chain remains `Weapon_파이먼_Buff → Weapon_파이먼_Left_Ticks → Weapon_파이먼_Beam → Weapon_파이먼_반동`. Keep ammunition variable `Paimon`, reload modifiers, sounds, particles and the successful-fire branch. For the new controls, remove the old firing Buff mouse bindings, register AutoFireSpell with `use-item`, and bind ADS to left click. Keep legacy Buff definitions available but do not run both fire loops simultaneously. See [the right-click Paimon merge fragment](../../examples/right-click-paimon.yml). The previous right-click ADS example is preserved in [aim-paimon-legacy.yml](../../examples/aim-paimon-legacy.yml). Disable ADS with `enabled: false` and `/ms reload`; remove/disable AutoFireSpell to restore the old controls.

Only names listed in `recoil-spells` receive reduction. Pattern sampling, `vertical`, `horizontal`, `growth`, random sequence, reset timing, steps and `require-success-of` remain in their original order. Each completed shot impulse is multiplied on both axes before it enters the existing smoothing buffer. Pattern progress and rise-cap accounting use the original unscaled pattern: sustained `0.5` ADS halves the total rise too. Changing ADS mid-burst keeps its phase. Previously queued impulses retain the multiplier used when fired; auto-return accounts for actual delivered rotation. No shared RecoilSettings or BeamSpell values are mutated.

Add the real reload-start spell to `stop-on-spells`. If a configuration uses direct calls without cast events, put StopAimSpell at the beginning of its reload chain beside the existing StopRecoilSpell. Stopping ADS does not stop the firing Buff: its existing reload/ammo gates must continue to do that.

MagicSpells Beta 18 [`Subspell`](https://github.com/TheComputerGeek2/MagicSpells/blob/4.0-Beta-18/core/src/main/java/com/nisovin/magicspells/Subspell.java) defaults to PARTIAL: it emits cast events/modifiers, but begins with NORMAL state and zero cost/cooldown. DIRECT calls `spell.cast(data)` with no such events. HARD/FULL for ordinary untargeted subcasts uses [`Spell.hardCast`](https://github.com/TheComputerGeek2/MagicSpells/blob/4.0-Beta-18/core/src/main/java/com/nisovin/magicspells/Spell.java) and the normal pre/post pipeline. AutoFireSpell uses `hardCast` for the ordinary full firing entry, retaining its permission, modifier, cost and cooldown checks. Positive top-level cast time is rejected before scheduling. It does not use DIRECT/PARTIAL to bypass those checks. Do not bypass `Left_Ticks` to call only Beam; a PassiveSpell is an event-driven configuration and is not automatically a reusable full-cost firing entry point.

## Right-click automatic fire

`dev.magicrecoil.spells.AutoFireSpell` registers `use-item` (a MagicSpells magic-item name or item string). Do not add `right-click-cast-item`: MagicSpells cancels that native interaction. AutoFireSpell is activated through its normal MagicSpells casting checks once per new input session. An ordinary synchronous `fire-spell` is then hard-cast at `interval-ticks`, without directly implementing bullets or damage.

| Setting | Default | Allowed behavior |
| --- | --- | --- |
| `enabled` | `true` | Literal boolean; false removes the binding at reload |
| `use-item` | Required | Named magic item or item string; follows MagicSpells cast-item matching options |
| `fire-spell` | Required | Ordinary synchronous firing entry; PassiveSpell and AutoFireSpell targets are rejected |
| `interval-ticks` | `2` | Integer `[1, 1200]`; actual source cooldowns still apply |
| `input-mode` | `BUFFERED_CLICK` | `BUFFERED_CLICK`, `NATIVE_USE` or `REPEATED_CLICK` |
| `release-timeout-ticks` | `6` | Only for REPEATED_CLICK; integer `[5, 40]` |
| `stop-on-spells` | `[]` | Up to 128 reload/end spell names; attempts stop and latch firing until release |
| `stop-on-buff-end` | `[]` | Up to 128 BuffEnd references |

`BUFFERED_CLICK` is the primary CrackShot-style mode. Each accepted main-hand right-click refills missing firing opportunities within the next five server ticks. At most five are pending; duplicate events do not stack bursts or produce multiple calls in one tick. Each due opportunity still respects interval-ticks and the full firing entry checks. With no further input, the queue drains within five ticks (about 0.25 seconds at 20 TPS), so release is not instantaneous or directly detected. Reload, item/hand change and lifecycle hooks stop the session immediately. The item material, model, lore, attributes and custom data remain untouched; there is no material whitelist for this mode. Use a named magic item with its existing matching metadata.

The optional `NATIVE_USE` supports STICK, BLAZE_ROD and wooden/stone/iron/golden/diamond/netherite hoes. It keeps the material and existing model/name/lore/attributes, adds temporary Consumable/UseEffects components with no animation/particles/sound and no extra use slowdown, and checks the real main-hand native use every tick. Paper's PlayerStopUsingItemEvent stops firing upon the received release. It does not wait for repeated clicks. Existing custom Consumable/UseEffects overrides are refused. Preparation is removed on switching, inventory handling, death/drop/logout/reload/shutdown and recovered at join. One native use has a one-hour duration; consumption is cancelled to prevent losing the gun. Items moved by other plugins into unloaded containers cannot be swept globally; return such marked items to a player with the addon enabled for cleanup before removing the addon.

`REPEATED_CLICK` leaves item components unchanged and permits other item materials, while suppressing the registered gun's normal right-click action. It observes repeated main-hand right-click interactions. Missing input for 6 ticks stops firing (about 0.30 seconds at 20 TPS); this is **release estimation**, not exact mouse-release tracking. The scanner runs once per server tick. Scanning faster cannot make the client send input sooner, and packet gaps can stop the estimated session. A subsequent click may start another session. Use a unique named magic item rather than binding an entire common material unintentionally.

All firing runs on one synchronous shared task. Item/hand changes, death, logout, teleport, reload and shutdown stop firing. Reload stop hooks latch the current held input until the input buffer drains, native release occurs or the repeat timeout expires. `dev.magicrecoil.spells.StopAutoFireSpell` explicitly stops eventless/direct reload chains. Put it first, alongside StopAimSpell and StopRecoilSpell when appropriate. A delayed/burst subspell can continue its own scheduled work after the input session ends; keep this entry's shot processing synchronous. Already launched projectiles retain their existing behavior.

To preserve a Paimon gun, extract the successful-fire modifiers, Paimon decrement, Beam/recoil calls, sounds, particles and cooldown into an ordinary `.MultiSpell` firing entry. Keep `buffactive` on the legacy Passive wrapper, which may hard-cast that shared entry when the legacy mode is used. Never enable the legacy Buff loop and AutoFire together. Source cooldowns, costs and reload/ammo checks remain effective even when interval-ticks is shorter. No zero-second fire interval is supported.

## CrackShot-style Slowness zoom

The primary Paimon example enables left-click `zoom.mode: SLOWNESS` with `zoom.slowness-amplifier: 2`. It leaves `recoil-multiplier: 1.0` and `movement-speed-multiplier: 1.0` so recoil stays unchanged and no additional ADS attribute multiplier is added. Slowness itself reduces actual movement speed and narrows the client FOV, as in CrackShot. This is not native spyglass and the client FOV Effects setting still controls visibility.

The addon refreshes a ten-tick Slowness lease while ADS is active. Ending ADS removes its current effect and restores the previous active Slowness with elapsed ticks subtracted, including an infinite duration when applicable. A stronger/equal or layered existing Slowness is left intact. An external replacement or removal takes priority and suppresses further scope refresh for that ADS session. Other potion types are untouched. Cancelled potion events are respected. Normal slot/F/death/logout/reload/shutdown hooks clear scope; the short lease bounds a leftover addon slowdown to ten server ticks after refresh stops. This is not a crash recovery guarantee for a previously backed-up effect.

## Optional FOV zoom

Enable `zoom.enabled: true`, `zoom.mode: FOV_EFFECT` and a `zoom.fov-factor` only if this client-dependent effect is desired. The Paper 1.21.11 adapter copies the current real player abilities and changes only the outgoing walking-speed field used by the vanilla FOV formula. It never sets server walk speed, changes the attribute base, applies Slowness or modifies potion effects. Flight/creative flags are copied. Scoped packets are refreshed each active tick; unscoping restores current real abilities, including changes by other plugins.

At client FOV Effects **100%**, the grounded movement part of the vanilla formula is `(movementAttribute / packetWalkingSpeed + 1) / 2`. The adapter derives a positive finite packet field from the requested factor and current movement attribute. At **0%** this FOV effect is invisible. Flying, sprinting, client mods and other FOV influences change the final view. This is not the native spyglass effect, a spyglass overlay, or guaranteed optical 2x magnification. No ProtocolLib dependency is added. Other plugins sending abilities packets may compete with the effect; client rendering/anti-cheat acceptance still requires game testing.

## Left-click and spread limits

Exact general left-click HOLD is still excluded. Paper Input has no attack-button state, and block-dig or item-use release actions are not universal attack-release signals. Native right-click use has a usable release signal; buffered clicks and timeout tracking are separately labelled repeat-input modes. See [the actual gun-plugin comparison](input-comparison.md), including CrackShot's queued firing and Slowness-based scope.

BeamSpell's private ConfigData spread can be configured dynamically by weapon authors, but the addon adds no generic per-player override and never rewrites shared spread fields. Automatic spread reduction remains excluded.

## Verification

See [ADS test evidence](testing.md#ads-extension-verification) and the [runtime fixture](../../tests/runtime/README.md#ads-integration-fixture). Unit tests, protocol-driven checks on real Paper, human mouse/rendering checks and production gun acceptance are reported separately.
