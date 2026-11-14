# Configuration reference

[English index](README.md) · [한국어](../ko/configuration.md)

All options below live in an individual MagicSpells spell. Values are literal YAML numbers/booleans, not MagicSpells expressions. Quoted numbers, NaN, infinity, invalid enum values, inverted bounds, and fractional integers are rejected at spell construction with the spell name in the error. An invalid spell does not silently fall back to a different pattern.

A lookup reference for tuning a working spell. Start with [Getting started](getting-started.md) for installation, [Recoil logic](recoil-logic.md) for calculations, or [Troubleshooting](troubleshooting.md) for diagnosis.

<!-- toc:start -->
## On this page

- [Basic options](#basic-options)
- [Advanced options](#advanced-options)
- [Exact calculation order](#exact-calculation-order)
- [Pattern definitions](#pattern-definitions)
- [Integration options](#integration-options)
- [Optional recovery](#optional-recovery)
<!-- toc:end -->

## Basic options

| Option | Default | Unit / accepted values | Meaning |
| --- | --- | --- | --- |
| `recoil` | `2.5` | degrees, 0..45 | Base impulse strength; 0 produces no camera motion |
| `vertical` | `1.0` | multiplier, 0..10 | Vertical scaling |
| `horizontal` | `0.4` | multiplier, 0..10 | Horizontal scaling |
| `smoothness` | `0.5` | fraction, 0..1 | Derives steps as `1 + round(4 × smoothness)` |
| `growth` | `0.08` | fraction per shot, 0..1 | Linear burst growth: `1 + growth × n` |
| `reset-delay` | `400` | milliseconds, integer 50..60000 | Gap since last accepted recoil cast that resets burst state |
| `pattern` | `valorant` | `valorant`, `vertical`, `random`, `custom` | Case-insensitive selector |
| `auto-return` | `false` | boolean | After the gap and pending impulses finish, subtract recorded delivered recoil |

`n` is the zero-based shot index: the first shot uses `n = 0`. A shot is an accepted call to this recoil spell, not a mouse click detected by MagicRecoil. Growth stops counting at 1,000,000 for numerical safety. It is normally constrained much earlier by `max-recoil`.

Reset delays use monotonic real time. Animations use server ticks. At 20 TPS, three steps take about 150 ms from a cast; the first packet is queued for the next scheduler tick. At low TPS, the same number of steps takes longer. A resumed shot after the timeout discards an old tail and starts at shot zero.

## Advanced options

| Option | Default | Unit / range | Meaning |
| --- | --- | --- | --- |
| `vertical-min` | `0.0` | normalized pattern coefficient, -10..10 | Lower vertical bound before strength scaling |
| `vertical-max` | `2.0` | normalized coefficient, -10..10 | Upper vertical bound |
| `horizontal-min` | `-1.0` | normalized coefficient, -10..10 | Lower horizontal bound |
| `horizontal-max` | `1.0` | normalized coefficient, -10..10 | Upper horizontal bound |
| `horizontal-bias` | `0.0` | normalized coefficient, -10..10 | Added to the pattern's horizontal coefficient before clamping |
| `valorant-max-rise` | `15.0` | degrees, 0..90 | Cumulative scheduled upward recoil per valorant burst; not a fixed bullet count; ignored by other patterns |
| `max-recoil` | `8.0` | degrees, 0.01..45 | Maximum **combined vector length per shot**, not accumulated camera angle |
| `pattern-randomness` | `0.0` | normalized amplitude, 0..1 | Independent uniform additive noise in `[-value, +value]` |
| `recoil-steps` | derived | ticks, integer 1..20 | Overrides the step count derived from `smoothness` |
| `custom-pattern` | empty | 1..1024 maps for `custom` | Each entry contains exactly numeric `vertical` and `horizontal` coefficients |
| `pattern-repeat` | `loop` | `loop`, `hold`, `stop` | Available only for `custom` |

Each min must be at most its max. Negative vertical coefficients produce downward recoil; lower `vertical-min` below zero if a custom pattern needs them. Positive horizontal values turn right; positive vertical coefficients look up (negative Minecraft pitch). The bounds are **not degrees** and are **not clamps on the player's absolute view**.

An explicit `recoil-steps` wins over `smoothness`. Every impulse uses the same front-loaded decreasing weights; smoothness is not a spring constant, network rate, or FPS setting. All axis coefficients still pass through scaling and the per-shot cap.

## Exact calculation order

1. Reset the session on a different recoil spell or an expired burst gap. Choose point `n` from the pattern.
2. For `random`, sample vertical and horizontal coefficients uniformly from their respective min/max intervals. Other patterns calculate coefficients as described below.
3. Add uniform noise to vertical, and to horizontal except for `vertical`. Add `horizontal-bias` to horizontal.
4. Clamp both coefficients to their configured min/max bounds.
5. Multiply by `recoil × (1 + growth × n)` and the corresponding axis multiplier. Negate vertical for Minecraft pitch.
6. For valorant, reduce upward pitch to the remaining valorant-max-rise budget for the session. This budget is independent of mouse input.
7. If the remaining yaw/pitch vector exceeds max-recoil, scale both axes together. Count only this bounded upward amount against the rise budget, then spread the impulse over `s` ticks with weights `(s-i) / (s(s+1)/2)`, for `i = 0..s-1`. Three ticks have weights `1/2, 1/3, 1/6`.
8. Add into the existing fixed-size tick buffer. Each combined slot is capped to 45 degrees in vector length. Extreme simultaneous bursts intentionally lose excess impulse instead of storing an unbounded debt.
9. On the main thread, clamp the outgoing pitch delta to the server's current `[-90°, +90°]` range and apply one relative rotation. Never cache an absolute starting view.

Example: default settings with `recoil: 2.5` produce a first shot of yaw `0°`, pitch `-2.5°`, divided into `-1.25°`, `-0.8333°`, `-0.4167°`. The second shot has strength `2.7°` before splitting and clamping. A player can keep moving their mouse during these steps.

## Pattern definitions

- `valorant`: base vertical coefficient 1; the session stops additional rise at valorant-max-rise. Yaw uses differences of signed sine target offsets. **The first four shots have zero base yaw; later offsets cross both sides of the burst center.** This is an original approximation, not extracted game data. See [rise cap and horizontal center](recoil-logic.md#built-in-valorant-rise-cap-and-horizontal-center).
- `vertical`: coefficients `(1, 0)`; no horizontal random noise. Explicit bias/range settings can still create horizontal motion.
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

---

[Previous: Recoil logic](recoil-logic.md) · [Documentation](README.md) · [Next: Integration](integration.md)
