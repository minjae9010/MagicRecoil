# MagicSpells integration

[English index](README.md) · [한국어](../ko/integration.md)

Connect recoil while preserving the existing firing, ammunition, and reload logic. Verify the [minimal spell](getting-started.md), then follow replacement → success gating → stop hooks.

<!-- toc:start -->
## On this page

- [Preserve the gun, replace the recoil](#preserve-the-gun-replace-the-recoil)
- [Why the success guard exists](#why-the-success-guard-exists)
  - [Casting mode and success limitations](#casting-mode-and-success-limitations)
- [Reload and stop integration](#reload-and-stop-integration)
- [Aim and shot order](#aim-and-shot-order)
- [`/ms reload`](#ms-reload)
- [Troubleshooting](#troubleshooting)
- [Adding ADS to a Buff gun](#adding-ads-to-a-buff-gun)
<!-- toc:end -->

## Preserve the gun, replace the recoil

The recoil component does not consume ammunition, spawn projectiles, apply damage or implement reloading. Optional AutoFireSpell handles right-click input and schedules the full existing firing entry; the gun's ammunition, projectiles, damage and reload logic remain in MagicSpells configuration. First identify the exact branch that successfully fires one shot, including its ammunition, cooldown, reload-state, and ON/OFF checks.

Replace only the old recoil spell's class and recoil settings. Do not carry over RotateSpell-specific options such as `rotation-pitch`, `rotation-yaw`, `affect-pitch`, `face`, or target selection: RecoilSpell always affects the **player caster**. Keep any general MagicSpells modifiers and conditions that are still appropriate.

```yaml
Weapon_AR_Recoil:
  spell-class: "dev.magicrecoil.spells.RecoilSpell"
  helper-spell: true
  recoil: 2.5
  require-success-of: Weapon_AR_Projectile
  stop-on-buff-end: [Weapon_AR_FiringBuff]
```

The `Weapon_AR_*` names are generic English placeholders. Match each definition and every reference to your own weapon configuration; renaming only the recoil block leaves the firing loop pointing at the old name.

In `Weapon_AR_FireLoop`, keep the existing successful-shot branch and the order:

```yaml
spells:
  - Weapon_AR_Projectile
  - Weapon_AR_Recoil
```

This is a **fragment**, not a complete replacement for `Weapon_AR_FireLoop`. The original weapon file was not attached. Therefore its ammo conditions, reload spell name, projectile success semantics, and firing buff lifecycle could not be audited. [weapon-integration.yml](../../examples/weapon-integration.yml) is safe to review as a recoil block replacement; it is not a claim of a completed original-file migration.

For optional headshots, six directions, distance damage, and hit callbacks, see [HitSpell integration](hit-detection.md).

## Why the success guard exists

Beta 18 `MultiSpell` iterates its subspells without stopping when a previous subcast fails. Merely listing a projectile before recoil is insufficient if the projectile can fail independently.

With `require-success-of`, MagicRecoil listens for the named projectile's `SpellCastEvent` and `SpellCastedEvent`. A new attempt invalidates an earlier receipt. A completion qualifies only with `SpellCastState.NORMAL` and a post-cast action other than `ALREADY_HANDLED` or `DELAYED`. Recoil consumes the receipt once, and only in the same Bukkit tick. A failed attempt, a duplicate recoil call, a stale tick, or a different projectile produces no recoil.

One latest receipt per player is stored. Keep each projectile/recoil pair consecutive, and use a distinct recoil spell per weapon. Two different recoil spells must not both try to consume one projectile receipt.

### Casting mode and success limitations

- **Partial** is the default subcast mode in Beta 18. It raises cast events but bypasses normal cost/cooldown checks. If the parent firing branch already validates ammunition, preserve it. A “successful” partial cast is not proof that an ammunition check ran.
- **Full/hard** casts run their own normal cast pipeline. Moving to that mode may change your weapon's behavior and costs; do not change modes casually just to enable recoil.
- **Direct** subcasts call `cast()` without these events. A guarded recoil spell then fails closed. Route recoil from an already verified success branch, or use an event-producing cast mode only after reviewing the weapon logic.
- **Delayed** casts have no completed-success receipt until they actually finish. A recoil call immediately after scheduling a delayed projectile will be rejected. Pair recoil with the actual completion tick.
- The guard trusts the projectile's reported result. It does not prove a bullet entity was spawned or that a hit occurred. Some spells can report “handled normally” while intentionally producing no projectile.
- An unconditional recoil spell (`require-success-of` empty) is appropriate only when the calling branch already guarantees a successful shot. The one-number quick start deliberately permits manual testing.

Ammo-empty and reload-state checks must gate the shot itself. The addon cannot infer arbitrary variable names, item lore, custom ammunition stores, or buff meanings. Test all rejected-shot branches with the original YAML before deploying.

## Reload and stop integration

Set the recoil spell's `stop-on-spells` to the actual spell that begins a reload. The handler stops pending recoil on the attempt, including unsuccessful attempts; this is conservative cancellation, not a new reload system. `stop-on-buff-end` cancels pending motion when the named firing buff ends. Confirm that your buff really belongs to the player caster.

Configured stop events invalidate the player's success receipt and reject all further recoil in that same tick. This also covers projectile → reload-start → recoil ordering. It does not prevent shooting in later ticks: that remains the existing gun's responsibility.

If your reload path uses direct calls without events, add this explicit helper at the point where reloading begins:

```yaml
Weapon_StopRecoil:
  spell-class: "dev.magicrecoil.spells.StopRecoilSpell"
  helper-spell: true
```

Call it from the existing reload-start/silent-weapon-replacement path. It clears both impulses and the successful-shot receipt. It does not return the camera to an old direction, and does not block future shots. Your existing firing logic must prevent shooting while reloading.

Normal hotbar selection, offhand swap, item drop, inventory click/drag, death, logout, teleport, and world changes clear state automatically. Inventory interactions are deliberately conservative: even an unrelated accepted inventory click ends the burst. Different recoil spell names also reset the session. A plugin silently replacing a same-slot item while reusing the same recoil spell requires the explicit stop hook.

## Aim and shot order

The current projectile is emitted using the server's aim before the newly queued impulse. Recoil begins on the next shared scheduler tick. Subsequent projectiles see the updated server rotation, subject to ordinary network latency and any locations/directions your projectile configuration caches.

If a repeating buff captures `SpellData.location` once and reuses it, its projectiles may keep the old direction regardless of current player aim. Verify the real projectile's targeting options and trajectory. MagicRecoil does not rewrite another spell's captured data.

At very high fire rates, multiple shots in one server tick share the same pre-tick aim and merge their pending impulses. For a shotgun, call recoil once per shot after the pellet-producing spell, not once per pellet.

## `/ms reload`

MagicSpells Beta 18 unloads old spells, clears its external class loader list, fires its loading event, constructs spells from YAML, and initializes them. MagicRecoil:

1. Clears that spell's active sessions through `RecoilSpell.turnOff()` during unload.
2. Clears all sessions/receipts and re-adds its loader on `MagicSpellsLoadingEvent`.
3. Rebuilds referenced-success-spell watches on `MagicSpellsLoadedEvent`.

This supports numeric changes and newly added recoil spells with `/ms reload`. It does not reload Java classes. Replace JARs only with a full restart. A failed configuration reload can leave individual spells unavailable; read the console before resuming play.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Missing/malformed class | Full class name; single addon JAR in `plugins/`; supported versions; startup errors |
| Minimal spell works, gun does not | Guard reference name; same-tick ordering; direct/delayed casts; actual projectile result |
| Recoil when ammo is empty | Existing parent checks; partial cast mode bypassing costs; guard missing or wrong source |
| Tail continues during reload | Actual reload-start name; whether cast events occur; explicit stop helper |
| Too slow or floaty | Lower `recoil-steps` to 2–3, check TPS; steps do not become faster with more packets per frame |
| No sideways motion | Default vertical has coefficient zero; increasing horizontal alone cannot change that. Default valorant has zero yaw for the first four shots and restarts after reset-delay. Also check whether both horizontal bounds are zero |
| Too much sideways motion | Lower `horizontal`, bias, or randomness; check min/max coefficients |
| Same-slot weapon replacement does not reset | Use separate recoil spell names or invoke StopRecoilSpell |
| Bullets ignore view changes | Check cached origin/direction in the firing loop and use the manual trajectory test |
| Jitter with other plugins | Test without anti-cheat/teleport/camera plugins, then reintroduce them one by one |

---

[Previous: Configuration](configuration.md) · [Documentation](README.md) · [Next: Troubleshooting](troubleshooting.md)

## Adding ADS to a Buff gun

Follow [ADS ownership and Buff integration](aim-and-input.md#recoil-and-existing-buff-guns) and the [Paimon merge example](../../examples/aim-paimon.yml). For the new layout, bind ADS to left click and register AutoFireSpell through use-item. Retain the complete successful-fire path and add StopAimSpell / StopAutoFireSpell to eventless reload chains. See the [right-click firing fragment](../../examples/right-click-paimon.yml). Extract PassiveSpell shot processing into an ordinary synchronous firing entry; do not replace it with a direct Beam call or run both firing loops together.
