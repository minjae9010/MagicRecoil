# Recoil calculations and configuration rules

[Documentation](README.md) · [한국어](../ko/recoil-logic.md)

Understand why horizontal recoil can be zero, then follow one shot through calculation, splitting, overlap, and reset. Start with the pattern rules before choosing a preset.

<!-- toc:start -->
## On this page

- [How one shot reaches the camera](#how-one-shot-reaches-the-camera)
- [`horizontal` is a multiplier, not an angle](#horizontal-is-a-multiplier-not-an-angle)
- [Horizontal behavior by pattern](#horizontal-behavior-by-pattern)
- [Horizontal motion from the first shot with constant vertical recoil](#horizontal-motion-from-the-first-shot-with-constant-vertical-recoil)
- [Smoothing, overlapping shots, resets, and recovery](#smoothing-overlapping-shots-resets-and-recovery)
<!-- toc:end -->

## How one shot reaches the camera

1. Run the projectile spell in the gun's existing branch that checks ammunition and reload state.
2. Call `RecoilSpell` immediately afterward in the same tick. If configured, `require-success-of` consumes one successful cast receipt for that spell. Success means **successful execution of the firing spell**, not a bullet hitting a target.
3. Select vertical and horizontal pattern coefficients using the player's current recoil spell and burst count. A different recoil spell or a gap of at least `reset-delay` starts again at the first shot.
4. Add noise and horizontal bias, clamp coefficients, then multiply by base strength, burst growth, and axis scaling. Apply the per-shot limit and schedule the impulse across ticks.
5. The shared scheduler merges impulses due in the same tick and sends at most one relative rotation per player per tick. Server aim changes too, while the player remains free to move their mouse.

## `horizontal` is a multiplier, not an angle

`n` is the zero-based accepted recoil-call index. Let `v` and `h` be coefficients after pattern selection, noise, horizontal bias, and min/max clamping. Before the per-shot limit:

```text
strength = recoil × (1 + growth × n)
horizontal delta (yaw) = h × horizontal × strength
vertical delta (pitch) = -v × vertical × strength
```

Positive horizontal turns right; negative turns left. A positive vertical coefficient becomes negative Minecraft pitch to look up. With `recoil: 0.5`, `vertical: 1.0`, `growth: 0`, and `v = 1`, each shot rises **0.5°**. A YAML comment saying 0.2 degrees does not override the actual value 0.5.

**If `h` is zero, increasing `horizontal` to 1.5 still produces zero horizontal motion.** `horizontal: 1.5` does not itself mean 1.5° per shot. `recoil: 0` disables both axes; `horizontal: 0` disables horizontal motion alone.

| Setting | Role and constraints |
| --- | --- |
| `recoil` | Base strength multiplying both axes |
| `vertical`, `horizontal` | Axis multipliers; increasing a multiplier cannot make a zero coefficient move |
| `vertical-min/max`, `horizontal-min/max` | Bounds on **coefficients before scaling**, not degrees. Sampling ranges for random, and final coefficient clamps for every pattern |
| `horizontal-bias` | Added to the horizontal coefficient; positive biases right, negative biases left |
| `pattern-randomness` | Independent additive coefficient noise; vertical pattern receives no horizontal noise |
| `growth` | Increases strength per accepted shot; zero keeps strength constant. Splitting into `recoil-steps` does not increment growth |
| `max-recoil` | Per-shot yaw/pitch vector-length cap; scales both axes together when exceeded. Does not cap accumulated aim |

## Horizontal behavior by pattern

| `pattern` | Behavior before noise, bias, and coefficient clamps |
| --- | --- |
| `vertical` | Vertical coefficient 1, horizontal 0. With default bias and bounds, **no horizontal recoil**; increasing `horizontal` or `pattern-randomness` alone does not enable it |
| `valorant` | Vertical coefficient 1. For zero-based `n`, horizontal is 0 when `n < 3`, otherwise `sin((n-3)π/6)`. **With defaults, the first four shots have zero yaw; sideways motion starts on shot five** |
| `random` | Independent coefficient draws from each axis's min/max interval on every shot. Can produce sideways motion from the first shot; alternating directions or noticeable movement on every shot are not guaranteed |
| `custom` | One `custom-pattern` point per shot. A point with `horizontal: 0` has zero base horizontal coefficient. `loop` repeats, `hold` keeps the last point, `stop` produces zero on both axes after the list ends |

Explicit `horizontal-bias` or horizontal bounds excluding zero can still make a vertical pattern move sideways. Conversely, setting both horizontal bounds to zero suppresses horizontal motion for any pattern. The first-four-shots statement for valorant assumes default randomness 0, bias 0, and bounds.

With `reset-delay: 400`, a gap of 400 ms or more between every shot repeatedly resets the pattern to its first point. Default valorant never reaches its sideways phase in that case, regardless of the horizontal multiplier. For horizontal motion on single shots, use random, nonzero custom points, or deliberate bias/noise.

## Horizontal motion from the first shot with constant vertical recoil

```yaml
MyGun_Recoil:
  spell-class: "dev.magicrecoil.spells.RecoilSpell"
  helper-spell: true
  recoil: 0.5
  vertical: 1.0
  horizontal: 1.5
  pattern: random
  vertical-min: 1.0
  vertical-max: 1.0
  horizontal-min: -1.0
  horizontal-max: 1.0
  growth: 0.0
  recoil-steps: 2
  reset-delay: 400
  auto-return: false
  require-success-of: MyGun_Beam
```

With default noise, bias, and per-shot cap, this produces **0.5° upward per shot and a horizontal delta in -0.75°..+0.75°**. Each horizontal value is random within the range, not a fixed ±0.75° kick. Equal vertical bounds fix that coefficient at 1. Switching to random without these two options would sample the default vertical range 0..2 and vary upward strength too. Reducing horizontal scaling to 0.5 gives ±0.25° under the same conditions.

The firing list must run `MyGun_Beam` followed by `MyGun_Recoil`. Set `require-success-of` to the **firing spell actually called**: changing from a particle projectile to a beam while retaining the old PP name prevents all recoil because no matching success receipt exists. Renaming the recoil spell or changing `beam-horizontal-spread` does not enable camera recoil. Projectile spread and camera recoil are separate settings. Preserve reload and buff-end stop hooks with their actual spell names.

## Smoothing, overlapping shots, resets, and recovery

- Explicit `recoil-steps` overrides `smoothness`. Otherwise steps are `1 + round(4 × smoothness)`; default smoothness 0.5 gives three ticks.
- Splitting uses decreasing weights rather than equal shares: two ticks use `2/3, 1/3`; three use `1/2, 1/3, 1/6`. The example's pitch steps are approximately `-0.3333°, -0.1667°`, with yaw split in the same ratio.
- The first application is the next scheduler tick. At 20 TPS, two ticks take approximately 100 ms; lower TPS takes longer. New shots add to existing future tick slots. Splitting does not reduce total strength or return aim automatically.
- Total impulse is conserved unless a limit applies: per-shot `max-recoil`, a combined 45° per tick, or the absolute pitch range ±90°. At the vertical view limit, pitch can be clipped while yaw still applies.
- `reset-delay` resets burst pattern and growth. Pending recoil normally finishes after firing stops; the timeout alone does not restore aim. A new shot after the timeout discards the previous tail and restarts at shot zero.
- `auto-return: false` disables recovery. True subtracts actually delivered recoil after the reset gap and outgoing tail finish. It does not restore a saved absolute angle or know how much the player compensated, so it can overcorrect.
- A different recoil spell, slot/inventory interactions, death, logout, teleport, world change, `/ms reload`, and explicit stops clear pending state. `stop-on-spells` cancels at the configured spell's attempt; `stop-on-buff-end` cancels when the configured buff ends.

Put these settings under each gun's recoil spell, not in a global config. Apply YAML changes with `/ms reload` and check console errors; replacing a JAR requires restarting the server. See [Configuration](configuration.md) for all accepted ranges and [Integration](integration.md) for success-event and stop-hook details.

---

[Previous: Getting started](getting-started.md) · [Documentation](README.md) · [Next: Configuration](configuration.md)
