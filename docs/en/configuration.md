# Configuration reference

[English index](README.md) · [한국어](../ko/configuration.md)

Put recoil settings under the weapon’s `RecoilSpell`, movement while aiming under `AimSpell`, and hit damage under `HitSpell`. For a first installation, follow [Getting started](getting-started.md).

<!-- toc:start -->
## On this page

- [Read a spell by example](#read-a-spell-by-example)
- [Basic options](#basic-options)
- [Advanced options](#advanced-options)
- [Aim settings](#aim-settings)
- [Hit damage and firing display](#hit-damage-and-firing-display)
- [Exact calculation order](#exact-calculation-order)
- [Pattern definitions](#pattern-definitions)
- [Integration options](#integration-options)
- [Optional recovery](#optional-recovery)
- [Optional AimSpell settings](#optional-aimspell-settings)
<!-- toc:end -->

## Read a spell by example

Put this spell in `plugins/MagicSpells/spells-recoil.yml`. The first line names the spell. Each indented line sets one property of that spell. The **default** in the tables is the value used when you leave a property out.

```yaml
Wiki_Recoil:
  spell-class: dev.magicrecoil.spells.RecoilSpell  # Use camera recoil for this spell
  pattern: vertical  # Move the view upward
  recoil: 2          # Raise it by 2° per shot while standing still
  growth: 0          # Keep each shot equally strong during a burst
  recoil-steps: 2    # Spread each kick over two server ticks
```

Run `/ms reload`, then enter `ms cast as PlayerName Wiki_Recoil` in the server console with a player online. Replace `PlayerName` with their name. Stand still and look level. Change only `recoil` to `1`, reload, and cast again to see half the rise.

**Units** — Degrees (°) measure how far the view turns. A multiplier of `1` keeps the original amount and `0.5` halves it. A delay of `400ms` is 0.4 seconds. At 20 TPS, one server tick lasts about 0.05 seconds. Write numbers and `true` or `false` without quotes. Invalid ranges or expressions in numeric fields produce a console error naming the spell.

## Basic options

| Option | Default | Unit / accepted values | Meaning |
| --- | --- | --- | --- |
| `recoil` | `2.5` | degrees, 0..45 | Base strength of each kick. Lower it for less camera movement with the same pattern. Zero removes the kick. |
| `vertical` | `1.0` | multiplier, 0..10 | Scales the pattern’s up/down movement. Zero removes vertical kick. |
| `horizontal` | `0.4` | multiplier, 0..10 | Scales the pattern’s sideways movement. The `vertical` pattern with default bounds and bias has zero sideways movement, so increasing this alone has no visible effect there. |
| `moving-recoil-multiplier` | `1.5` | multiplier, 1..10 | Scales both axes when the player holds a movement direction at the time of the shot. Use `1.0` for base recoil while moving. |
| `smoothness` | `0.5` | fraction, 0..1 | Spreads the same kick over more ticks as you raise it. Values of 0, 0.5 and 1 give 1, 3 and 5 ticks respectively. |
| `growth` | `0.08` | fraction per shot, 0..1 | Adds a fraction of the base strength with each shot in a burst. At the default, the first three use 100%, 108% and 116% of base strength. |
| `reset-delay` | `400` | milliseconds, integer 50..60000 | After a gap this long, the next shot starts the pattern again. Increase it to allow longer pauses within a burst. |
| `pattern` | `valorant` | `valorant`, `vertical`, `horizontal`, `random`, `custom` | Chooses how the view moves on successive shots. See [Pattern definitions](#pattern-definitions). Names are case-insensitive. `horizental` is an alias for `horizontal`. |
| `auto-return` | `false` | true/false | Enable to move the view back by the delivered recoil after firing stops and the last kick finishes. |

`n` is the zero-based shot index: the first shot uses `n = 0`. A shot is an accepted call to this recoil spell, not a mouse click detected by MagicRecoil. Growth stops counting at 1,000,000 for numerical safety. It is normally constrained much earlier by `max-recoil`.

Reset delays use monotonic real time. Animations use server ticks. At 20 TPS, three steps take about 150 ms from a cast; the first packet is queued for the next scheduler tick. At low TPS, the same number of steps takes longer. A resumed shot after the timeout discards an old tail and starts at shot zero.

## Advanced options

| Option | Default | Unit / range | Meaning |
| --- | --- | --- | --- |
| `vertical-min` | `0.0` | coefficient, -10..10 | Lowest vertical pattern value. Lower it below zero to allow downward kicks. |
| `vertical-max` | `2.0` | coefficient, -10..10 | Highest vertical pattern value. Set it equal to the minimum to keep the vertical coefficient constant in `random`. |
| `horizontal-min` | `-1.0` | coefficient, -10..10 | Lowest sideways value. More negative values allow larger leftward kicks. |
| `horizontal-max` | `1.0` | coefficient, -10..10 | Highest sideways value. Larger positive values allow larger rightward kicks. |
| `horizontal-bias` | `0.0` | coefficient, -10..10 | Offsets every sideways pattern value. Positive values favor the right and negative values favor the left. |
| `valorant-max-rise` | `15.0` | degrees, 0..90 | Cumulative scheduled upward recoil per valorant burst; not a fixed bullet count; ignored by other patterns |
| `max-recoil` | `8.0` | degrees, 0.01..45 | Caps the combined up/down and sideways kick per shot. A diagonal kick over the limit shrinks on both axes. |
| `pattern-randomness` | `0.0` | amplitude, 0..1 | Adds random variation to each shot. At 0.2, each affected axis receives a value between -0.2 and +0.2 before its bounds apply. |
| `recoil-steps` | derived | ticks, integer 1..20 | Sets exactly how many ticks deliver one kick. Takes precedence over `smoothness`. |
| `custom-pattern` | empty | 1..1024 entries for `custom` | One entry per shot. Each contains both up/down (`vertical`) and sideways (`horizontal`) coefficients, each from -10 to 10. |
| `pattern-repeat` | `loop` | `loop`, `hold`, `stop` | After the last custom point, restart the list (`loop`), reuse that point (`hold`) or apply no further kick (`stop`). |

Each min must be at most its max. Negative vertical coefficients produce downward recoil; lower `vertical-min` below zero if a custom pattern needs them. Positive horizontal values turn right; positive vertical coefficients look up (negative Minecraft pitch). The bounds are **not degrees** and are **not clamps on the player's absolute view**.

An explicit `recoil-steps` wins over `smoothness`. Every impulse uses the same front-loaded decreasing weights; smoothness is not a spring constant, network rate, or FPS setting. All axis coefficients still pass through scaling and the per-shot cap.

## Aim settings

Put these properties on the `AimSpell` that starts aim. List the **names** of your weapon’s recoil spells in `recoil-spells`. To use the spell above, write `recoil-spells: [Wiki_Recoil]`. [Example aim and stop spells](../../examples/aim-integration.yml)

| Property | Default and accepted values | Effect |
| --- | --- | --- |
| `enabled` | `true` · true/false | Set to false to disable activation of this aim spell. |
| `recoil-spells` | `[]` · spell names | Recoil spells affected by aim. Supply at least one name when aim is enabled. |
| `recoil-multiplier` | `0.5` (SLOWNESS: 1) · 0..1 | Scales kick from the listed spells. 0.5 halves it and 0 removes it while aiming. |
| `movement-speed-multiplier` | `0.65` (SLOWNESS: 1) · 0.01..1 | Scales movement speed while aiming. 0.65 gives 65% of normal speed and 1 preserves normal speed. |
| `toggle` | `true` · true/false | When enabled, casting again ends aim. When disabled, casting again keeps aim active. Use a stop condition or `StopAimSpell` to end it. |
| `cancel-on-item-swap` | `true` · true/false | Ends aim when the player changes the selected hotbar slot. |
| `cancel-on-hand-swap` | `true` · true/false | Ends aim when the player swaps main-hand and off-hand items. |
| `stop-on-spells` | `[]` · spell names | Ends aim when the player attempts a listed spell, such as reload. |
| `stop-on-buff-end` | `[]` · buff names | Ends aim when a listed buff on the player expires or ends. |
| `cancel-on-death` | fixed at `true` | Death ends aim. You can omit this line. |
| `cancel-on-logout` | fixed at `true` | Leaving the server ends aim. You can omit this line. |

If cancellation for a swap is disabled, the modifiers resume when the original slot and item match again. Each name list accepts up to 128 entries. Bind the aim spell through the weapon’s MagicSpells input settings. [Aim integration](integration.md#optional-aim-state)

## Hit damage and firing display

| What you want to change | Where to set it | Properties and examples |
| --- | --- | --- |
| Range, base damage, headshots or distance scaling | `HitSpell` | [Hit properties and defaults](hit-detection.md#damage-and-option-ranges) |
| Follow-up spells for hit locations and directions | `HitSpell` | [Cast spells on impact](hit-detection.md#cast-spells-on-impact) |
| Show the time until the next shot under the crosshair | Single-shot firing spell with a cooldown | `attack-cooldown-indicator` defaults to `false`. Enable it to show the attack gauge when a successful cast starts a cooldown. [Client display setting](integration.md#single-shot-attack-cooldown-indicator) |

## Exact calculation order

1. Reset the session on a different recoil spell or an expired burst gap. Choose point `n` from the pattern.
2. For `random`, sample vertical and horizontal coefficients uniformly from their respective min/max intervals. Other patterns calculate coefficients as described below.
3. The `horizontal` pattern always has zero vertical kick. Other patterns add noise on the vertical axis. Add horizontal noise except for `vertical`, then apply `horizontal-bias` to all patterns.
4. Clamp both coefficients to their configured min/max bounds.
5. Multiply by `recoil × (1 + growth × n)` and the corresponding axis multiplier. If the player is holding a movement direction, multiply both axes by `moving-recoil-multiplier`. Negate vertical for Minecraft pitch.
6. For valorant, reduce upward pitch to the remaining valorant-max-rise budget for the session. This budget is independent of mouse input.
7. If the remaining yaw/pitch vector exceeds max-recoil, scale both axes together. Count this bounded upward amount against the rise budget. Apply the active aim multiplier, then spread the impulse over `s` ticks with weights `(s-i) / (s(s+1)/2)`, for `i = 0..s-1`. Three ticks have weights `1/2, 1/3, 1/6`.
8. Add into the existing fixed-size tick buffer. Each combined slot is capped to 45 degrees in vector length. Extreme simultaneous bursts intentionally lose excess impulse instead of storing an unbounded debt.
9. On the main thread, clamp the outgoing pitch delta to the server's current `[-90°, +90°]` range and apply one relative rotation. Never cache an absolute starting view.

Example: default settings with `recoil: 2.5` produce a first shot of yaw `0°`, pitch `-2.5°`, divided into `-1.25°`, `-0.8333°`, `-0.4167°`. The second shot has strength `2.7°` before splitting and clamping. A player can keep moving their mouse during these steps.

## Pattern definitions

- `valorant`: base vertical coefficient 1; the session stops additional rise at valorant-max-rise. Yaw uses differences of signed sine target offsets. **The first four shots have zero base yaw; later offsets cross both sides of the burst center.** This is an original approximation, not extracted game data. See [rise cap and horizontal center](recoil-logic.md#built-in-valorant-rise-cap-and-horizontal-center).
- `vertical`: coefficients `(1, 0)`; no horizontal random noise. Explicit bias/range settings can still create horizontal motion.
- `horizontal` swings to either side of the burst center starting with the first shot. Pitch stays at zero regardless of the vertical settings.
- `random`: independent uniform draws from configured bounds, followed by the same noise/bias/clamp pipeline. Equal bounds are valid and deterministic.
- `custom`: one normalized point per shot. `loop` restarts at point zero; `hold` keeps the last point; `stop` returns a zero impulse after the last point. Looping does not reset burst growth.

`horizontal` multiplies the pattern coefficient. With default bias/bounds, vertical has zero horizontal coefficient, so increasing its multiplier alone cannot produce sideways motion. To enable random horizontal motion from the first shot while keeping vertical recoil constant, combine `pattern: random`, `vertical-min: 1`, `vertical-max: 1`, and the intended horizontal bounds/scaling. See [Recoil logic](recoil-logic.md) for formulas, the 0.5°/±0.75° example, reset behavior, and recovery.

```yaml
Recoil_Custom:
  spell-class: "dev.magicrecoil.spells.RecoilSpell"
  recoil: 2.0
  pattern: custom
  pattern-repeat: hold
  custom-pattern:
    - {vertical: 1.0, horizontal: 0.0}
    - {vertical: 1.2, horizontal: 0.4}
    - {vertical: 1.0, horizontal: -0.5}
```

## Integration options

| Option | Default | Meaning |
| --- | --- | --- |
| `require-success-of` | empty | Internal name of the immediately preceding successful projectile spell. Requires a single-use receipt in the same server tick. Empty disables this guard. |
| `stop-on-spells` | `[]` | Internal spell names whose **cast attempt** stops the player's matching active recoil session, even if that attempt later fails. Use actual reload-start names. |
| `stop-on-buff-end` | `[]` | Buff names whose end event on the player clears that matching active session. |

Names are case-insensitive; lists accept up to 128 nonempty strings. Missing references produce startup/reload warnings. `require-success-of` is deliberately fail-closed if no successful event arrives. See [Integration](integration.md) for direct/delayed subcast limitations.

A configured stop event also invalidates the player's successful-shot receipt and blocks further recoil calls for that server tick, including an ungated recoil call. This closes a same-tick projectile → reload → recoil race. It is not a persistent reloading flag; the gun's existing logic must block later shots until reloading finishes.

## Optional recovery

With `auto-return: true`, recovery starts only after the reset gap and outgoing tail finish. It subtracts the sum of actually delivered deltas over the same step count; the recorded sum is bounded to ±90° per axis. Pitch discarded at the view limit is not recorded. A new shot after the gap cancels recovery and begins a new burst. Weapon switch, reload, death, logout, and reset discard recovery entirely.

This does not know how much the player compensated with their mouse, so it can overcorrect relative to the intended aim. Leave it off for the requested default behavior.

## Optional AimSpell settings

AimSpell and StopAimSpell have separate [full configuration, defaults and ranges](aim-and-input.md#binding-and-configuration). RecoilSpell requires no new YAML. Use `recoil-spells` on AimSpell to scope reduction to the gun. Exact left-click HOLD and optical zoom.level remain unsupported. Right-click AutoFireSpell has [buffered, native and timeout input modes](aim-and-input.md#right-click-automatic-fire); zoom requires explicit SLOWNESS or FOV_EFFECT mode and its mode-specific settings.

The primary Paimon example now selects BUFFERED_CLICK and SLOWNESS scope, with recoil-multiplier and movement-speed-multiplier both 1.0. Slowness supplies its own movement reduction. FOV_EFFECT remains an alternative.

---

[Previous: Recoil logic](recoil-logic.md) · [Documentation](README.md) · [Next: Integration](integration.md)
