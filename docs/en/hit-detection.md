# Headshots, hit directions, distance damage, and hit callbacks

[English index](README.md) · [한국어](../ko/hit-detection.md)

`dev.magicrecoil.spells.HitSpell` is an optional attack spell. Existing `RecoilSpell` and `StopRecoilSpell` behavior and configuration remain available. Your gun still handles ammunition, firing conditions, and reloads. Adapt the names in the [complete YAML example](../../examples/hit-integration.yml).

<!-- toc:start -->
## On this page

- [Instant ray shots](#instant-ray-shots)
- [Connect an existing particle projectile](#connect-an-existing-particle-projectile)
- [Connect an existing BeamSpell](#connect-an-existing-beamspell)
- [Direction and headshot rules](#direction-and-headshot-rules)
- [Damage and option ranges](#damage-and-option-ranges)
- [Cast spells on impact](#cast-spells-on-impact)
- [Reload and validation](#reload-and-validation)
<!-- toc:end -->

## Instant ray shots

```yaml
MyGun_Hit:
  spell-class: dev.magicrecoil.spells.HitSpell
  helper-spell: true
  can-target: [players, nonplayers]
  range: 64
  damage: 6
  headshot-entities: [PLAYER, ZOMBIE, SKELETON]
  headshot-multiplier: 2
  direction-multipliers:
    back: 1.5
  distance-damage:
    - {distance: 0, multiplier: 1}
    - {distance: 15, multiplier: 1}
    - {distance: 50, multiplier: 0.5}
```

In your successful-fire branch, call `"MyGun_Hit(targeting=none)"`, then recoil consecutively in the same tick. Set recoil's `require-success-of` to `MyGun_Hit`. The ray starts at the caster's current eyes and stops at the nearest solid block or eligible living entity. Fluids, passable blocks, and the caster are excluded. A miss or wall impact still counts as a successfully fired shot and permits recoil. Invalid casters or server state fail the cast.

## Connect an existing particle projectile

Replace the damage callback of your existing `.instant.ParticleProjectileSpell`:

```yaml
# Inside the existing projectile spell
spell-on-hit-entity: "MyGun_Hit(targeting=entity_from_location)"
```

Use the same HitSpell definition above. The `targeting=entity_from_location` argument is required: normal MagicSpells entity calls discard the location and will not execute hit handling. Check `can-target` on both spells.

Connect **directly to HitSpell**, without a delay or MultiSpell wrapper. The projectile event saves its own launch origin and incoming direction for one callback in the same tick. Moving the shooter before impact does not change this snapshot. Flight, speed, spread, lifetime, and piercing remain configured on the existing projectile. `range` applies only to ray shots; existing projectiles retain their `max-distance`. Distance means straight-line launch-to-impact distance, not accumulated curved-path length.

Replace the old damage callback to avoid applying damage twice. Recoil runs **immediately after projectile launch**, and its `require-success-of` continues to name the launch spell. Recoil attached to a hit callback would wait for impact.

The callback reconstructs the travel line's entry into the real body box. A radius-only grazing collision counts as a bodyshot. Other projectile classes and arbitrary location casts are unsupported; callbacks without a captured launch origin apply neither damage nor follow-up spells.

## Connect an existing BeamSpell

Update the class and the entity-hit callback on your existing beam.

```yaml
MyGun_Beam:
  spell-class: dev.magicrecoil.spells.BeamHitSpell
  can-target: [players, nonplayers]
  relative-offset: 0, 1.62, 0
  max-distance: 64
  stop-on-hit-entity: false
  stop-on-hit-ground: true
  spell: "MyGun_Hit(targeting=entity_from_location)"
```

`BeamHitSpell` extends MagicSpells' native `BeamSpell`, which still runs its effects, ground callback, range, target conditions, and piercing. Point `spell` directly at a synchronous `HitSpell` callback. The callback uses the sampled beam segment to recover entry into the target's body box. A radius-only collision counts as a bodyshot. Damage falloff uses the straight-line distance from the beam's origin to the impact. Set recoil's `require-success-of` to `MyGun_Beam`.

See the [complete Paimon beam configuration](../../examples/spells-paimon-beam.yml).

## Direction and headshot rules

Direction is the side **from which the attack arrives relative to the victim's horizontal facing**. Looking up or down does not rotate these horizontal axes.

| Key | Meaning |
| --- | --- |
| `front` / `back` | Attack arrives from the victim's front / rear |
| `left` / `right` | Attack arrives from the victim's left / right |
| `above` / `below` | Incoming elevation reaches `vertical-angle` from above / below |

Vertical directions take precedence. Otherwise, the larger forward/back or left/right component wins; exact horizontal diagonal ties favor front/back. Each hit has one direction, independently combined with its headshot status.

The head region starts as the upper `headshot-height` fraction of the current bounding box, 25% by default. Pose changes resize this region. **Swimming/gliding players extend its lower edge down to their actual eyes**, so eye-height impacts count as headshots. For a typical 0.6-high box, this moves the minimum from approximately 0.45 to the eyes at approximately 0.40. A configured region that is already wider stays intact. Boundary checks allow 0.000001 blocks of tolerance for projectile coordinate rounding.

Standing/crouching players and mobs retain their configured upper-box fraction. Only `PLAYER` is enabled by default; add appropriate humanoid entities through `headshot-entities`. An empty list disables headshots. This is an approximation based on server bounding boxes and eye positions. See [PvP and pose results](testing.md#player-pvp-and-swimminggliding-validation).

## Damage and option ranges

```text
Requested damage = damage × distance multiplier × direction multiplier × headshot multiplier × power
```

`damage` accepts a number or a quoted MagicSpells expression. It evaluates the expression with the spell data for each hit, then applies the multipliers.

```yaml
damage: "10 + ( 0.8 * %var:Orias_Charge:0% )"
power-affects-damage: false
```

With `Orias_Charge` at 0 or 5, base damage is 10 or 14 health points, respectively. In this placeholder, `:0` rounds the variable to zero decimal places. Use `%castervar:Orias_Charge%` to explicitly read the caster's unrounded value. An invalid expression syntax fails configuration loading. If an expression cannot return a number in the valid range on a hit, base damage is 0 and the hit hooks still run.

Bodyshots use a headshot multiplier of 1. `power-affects-damage: false` also replaces the power term with 1. The distance curve interpolates linearly and clamps outside its endpoints. At 32.5 blocks, the example curve gives 0.75. Increasing curves are supported too. Duplicate, descending, or nonfinite points fail configuration loading.

| Property | Default | Range and effect |
| --- | --- | --- |
| `range` | 64 | 0.1..512 blocks. Farthest distance an instant ray searches for a target. Beams and particle projectiles use their own `max-distance`. |
| `damage` | 4 | Number or quoted MagicSpells expression. The base value must be 0..1,000,000 health points before multipliers and defenses. Four points equal two hearts. Use 0 for impact effects alone. |
| `damage-type` | `minecraft:arrow` | Registered Minecraft damage type key, including data packs |
| `power-affects-damage` | `true` | Multiplies damage by the incoming spell power when enabled. Disable to treat the power factor as 1. |
| `headshot-entities` | `[PLAYER]` | Bukkit entity types eligible for headshots. Use `[PLAYER, ZOMBIE]` for both types or `[]` to classify all hits as bodyshots. |
| `headshot-height` | 0.25 | 0.01..1. Fraction of the target’s collision box counted from the top. 0.25 means the top quarter. Increase it to extend the head region downward. Swimming and gliding players also include eye height. |
| `headshot-multiplier` | 2 | 0..100. Applies only to a headshot. At 2, it deals twice the damage of an otherwise identical bodyshot. |
| `vertical-angle` | 45 | 1..90°. Minimum incoming elevation angle for above/below classification. Raise it to require a steeper approach. |
| `direction-multipliers` | 1 per direction | 0..100 per direction. One of `front`, `back`, `left`, `right`, `above`, or `below` applies from the victim’s perspective. For example, `back: 1.5` gives 1.5× damage from behind. |
| `distance-damage` | `[{distance: 0, multiplier: 1}]` | 1..128 points. Each `distance` is measured from the shot origin (0..1,000,000 blocks). Its `multiplier` scales damage there (0..100). List points in increasing distance order. |

With the example above, a headshot from behind at 32.5 blocks and spell power 1 deals `6 × 0.75 × 1.5 × 2 = 13.5` damage before defenses.

Damage passes through `SpellApplyDamageEvent` and Paper's normal damage API. Both receive the configured damage type, and the damage is credited to the caster. Install the data pack before server startup to use a custom key such as `shardra:shoot_dot`. An unregistered key disables the spell and produces a configuration error. Actual health loss can vary with the damage type's tags, armor, effects, shields, and other plugins. Target events, `can-target`, and world PvP restrictions still apply.

## Cast spells on impact

Add just the lines you need under `HitSpell`. Omitted properties run no follow-up spell. Replace the example names with spells you have defined.

```yaml
spell-on-hit: MyGun_HitEffect  # Every valid hit
spell-on-headshot: MyGun_HeadshotEffect  # Head hits only
spell-on-bodyshot: MyGun_BodyEffect  # Body hits only
spell-on-front: MyGun_FrontEffect  # From the victim’s front
spell-on-back: MyGun_BackEffect  # From behind the victim
spell-on-left: MyGun_LeftEffect  # From the victim’s left
spell-on-right: MyGun_RightEffect  # From the victim’s right
spell-on-above: MyGun_AboveEffect  # From above the victim
spell-on-below: MyGun_BelowEffect  # From below the victim
```

Each impact runs configured hooks once in this order: **common → headshot or bodyshot → selected direction**. Hooks receive the victim, impact location, and original power. Location-targeted effects keep the impact point instead of switching to the victim's feet. MagicSpells removes the location for ordinary entity-targeted subspells. Hit multipliers are not multiplied into callback power again.

MagicSpells subspell arguments work normally. The default partial mode does not guarantee all ordinary cost/cooldown checks. Each subspell retains its own targeting checks and events.

```yaml
spell-on-headshot: "MyGun_HeadshotEffect(power=1.5)"
```

Use `damage: 0` for classification and effects without primary damage. Hooks represent **valid collisions**, so shields, invulnerability, or damage cancellation may prevent health loss while hooks still run. Misses, walls, and canceled targeting do not invoke hit hooks. Missing subspells or direct self-references disable that HitSpell and log an error. Synchronous callback cycles are blocked; do not configure delayed cycles.

## Reload and validation

Apply YAML changes with `/ms reload`; restart after replacing the addon JAR. Reload clears old projectile receipts and subspell references. Existing guns that do not use HitSpell need no changes.

Automated tests cover six directions, rotated victims, head boundaries and pose boxes, curve interpolation, combined multipliers, rejected settings, projectile intersections, immutable launch snapshots, and single-use receipts. See [Testing](testing.md#hit-feature-validation) for full-suite results and runtime evidence.
