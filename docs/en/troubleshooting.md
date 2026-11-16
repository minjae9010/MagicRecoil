# Troubleshooting by symptom

[Documentation](README.md) · [한국어](../ko/troubleshooting.md)

First check whether the problem also occurs with a minimal standalone recoil spell. Use the table to find the relevant settings and diagnosis order.

<!-- toc:start -->
## On this page

- [Find your symptom](#find-your-symptom)
- [No sideways motion](#no-sideways-motion)
- [No yaw on single shots or short bursts](#no-yaw-on-single-shots-or-short-bursts)
- [No recoil at all](#no-recoil-at-all)
- [Recoil fails only when connected to a gun](#recoil-fails-only-when-connected-to-a-gun)
- [Offsets stay on one side or the view keeps rising](#offsets-stay-on-one-side-or-the-view-keeps-rising)
- [Unexpected strength](#unexpected-strength)
- [Slow or uncancelled recoil](#slow-or-uncancelled-recoil)
- [Unexpected trajectory or spread](#unexpected-trajectory-or-spread)
- [Reporting a reproduction](#reporting-a-reproduction)
<!-- toc:end -->

## Find your symptom

| Symptom | Start here |
| --- | --- |
| Upward motion but no sideways recoil | [Horizontal motion](#no-sideways-motion) |
| No sideways motion on single shots, but some during bursts | [Pattern and reset gap](#no-yaw-on-single-shots-or-short-bursts) |
| Even the minimal spell does nothing | [Installation and loading](#no-recoil-at-all) |
| Minimal spell works, gun integration fails | [Successful-fire guard](#recoil-fails-only-when-connected-to-a-gun) |
| Weak or inconsistent recoil | [Strength and coefficients](#unexpected-strength) |
| Slow recoil or a tail during reload | [Splitting and cancellation](#slow-or-uncancelled-recoil) |
| Bullets ignore aim or spread differently | [Trajectory and spread](#unexpected-trajectory-or-spread) |

## No sideways motion

Inspect the recoil spell's pattern, horizontal multiplier, and horizontal bounds.

```yaml
recoil: 0.5
horizontal: 1.5
pattern: vertical
```

With default bias/bounds, this produces **zero yaw**: vertical's horizontal coefficient zero remains zero after multiplication by `1.5 × 0.5`. To enable random sideways motion from the first shot:

```yaml
recoil: 0.5
vertical: 1.0
horizontal: 1.5
pattern: random
vertical-min: 1.0
vertical-max: 1.0
horizontal-min: -1.0
horizontal-max: 1.0
growth: 0.0
```

Calculated per-shot deltas are 0.5° upward and -0.75°..+0.75° horizontally. Also check for recoil zero, horizontal scaling zero, both horizontal bounds zero, or zero-horizontal custom points. Apply YAML changes with `/ms reload` and inspect loading errors.

## No yaw on single shots or short bursts

Default valorant has zero yaw on the first **four shots**, then sway starts on shot five. Gaps of 400 ms or more with default `reset-delay: 400` restart every shot at index zero. Repeated short bursts may never reach the sideways phase.

Use random or nonzero-horizontal custom points if sideways motion is required on single shots. To retain a burst pattern longer, adjust the reset gap with the actual firing interval in mind. See [Pattern rules](recoil-logic.md#horizontal-behavior-by-pattern).

## No recoil at all

1. Confirm Paper 1.21.4+, MagicSpells Beta 17+, and the Java required by that server. Check the startup compatibility diagnostic and [version guide](compatibility.md).
2. Check that exactly one addon JAR is installed in plugins and the full spell class is correct.
3. Inspect startup/reload errors for the spell. Quoted numbers and out-of-range settings are rejected.
4. Try the [minimal spell](getting-started.md#2-install-the-plugin-and-a-minimal-spell) from the server console without a success guard.
5. As an administrator, compare `/magicrecoil status` packet counts before and after casting. Active players returning to zero after an impulse is normal.

## Recoil fails only when connected to a gun

Compare the firing list with the guard reference:

```yaml
spells:
  - MyGun_Beam
  - MyGun_Recoil
```

The recoil spell needs `require-success-of: MyGun_Beam`. Firing a beam while retaining a PP reference rejects all recoil. Calls must be consecutive in the same tick, and a receipt can only be consumed once. See [Casting modes](integration.md#casting-mode-and-success-limitations) for direct/delayed calls that may not provide the required successful event.

## Offsets stay on one side or the view keeps rising

Use built-in valorant from 0.1.0-alpha.3 or later. The old code added a sine every shot and produced a one-sided cumulative offset. Current code sends target-position differences around center. Bias, asymmetric bounds, noise, or clipping can deliberately alter symmetry.

Valorant-max-rise is an angle budget, not a bullet count. Gaps at least reset-delay or repeated session resets start a fresh rise each time. Vertical pattern ignores this cap. See [current calculations](recoil-logic.md#built-in-valorant-rise-cap-and-horizontal-center).

## Unexpected strength

- Recoil is base strength, vertical/horizontal are multipliers, and min/max are coefficient bounds. Comments do not change behavior.
- Random's default vertical interval is 0..2; set equal bounds of 1 for constant upward strength.
- Positive growth increases burst strength. Max-recoil caps the combined vector, so enlarging one axis can reduce the other axis's delivered angle too.
- Pitch clips at the view limit. Random horizontal ranges including zero can produce small values or repeat one direction.

See [Recoil logic](recoil-logic.md) for order of operations and [Configuration](configuration.md) for accepted ranges.

## Slow or uncancelled recoil

Explicit recoil-steps overrides smoothness. Start with two or three ticks and check TPS. Stopping fire alone does not discard the pending tail. Point stop-on-spells at the actual reload-start spell and stop-on-buff-end at the firing buff.

Reset-delay does not return the view. Keep auto-return false when recovery is unwanted. Use `/magicrecoil reset PlayerName` or StopRecoilSpell for immediate cancellation.

## Unexpected trajectory or spread

Beam-horizontal-spread and projectile-horizontal-spread affect projectile dispersion, not camera yaw. The current projectile launches before that shot's new recoil. Firing loops that cache old positions/directions may ignore updated aim. See [Aim and shot order](integration.md#aim-and-shot-order).

## Reporting a reproduction

Include server, Java, MagicSpells, and addon versions, the smallest relevant YAML, exact steps, console errors, TPS/latency, and other camera/anti-cheat plugins. Omit credentials, private player data, and entire production server folders.

---

[Previous: Integration](integration.md) · [Documentation](README.md) · [Next: Testing](testing.md)
