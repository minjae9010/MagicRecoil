# Validation and acceptance

[English index](README.md) · [한국어](../ko/testing.md)

Status: **experimental / human acceptance pending**. Compilation, local protocol behavior, and subjective camera feel are different claims. This document deliberately separates them.

## Environment

- Date: 2026-11-14, Asia/Seoul.
- Windows 11, Azul Zulu JDK 21.0.8, Gradle wrapper 9.3.0.
- Paper 1.21.11 build 132, commit `c5eb0790f199da6c38d0a650e1e5cd5415b28185`.
- Official MagicSpells 4.0-Beta-18, source commit `1996743cacaac8c5742ac146e7ff905caf270f8a`.
- Disposable flat-world server, bound to `127.0.0.1:25579`, offline authentication for the local fixture only.
- Headless `minecraft-protocol` 1.68.0 client speaking protocol 1.21.11. It applies relative rotation fields, reports normal look packets, and records traffic. It does **not** run the vanilla renderer or physical mouse input.

## Automated checks

`./gradlew clean build runtimeProbeJar` succeeds with the real Paper/MagicSpells artifacts. **44 automated tests** pass:

- Strict numeric/type/range validation and all distributed recoil examples.
- First-shot rise, growth, vector caps, left/right sway, random bounds, and custom loop/hold/stop rules.
- Impulse conservation across 1..20 steps, 100,000 consecutive simulated shots, extreme aggregate clipping, and full buffer cancellation.
- Burst timeout reset, default no-return, optional recovery using only delivered deltas.
- Single-use, same-tick success receipts; failure, wrong spell, stale tick, player isolation, logout/reset, reload invalidation, and same-tick stop suppression.
- One scheduler shared by 100 mocked players and correct pending-impulse replacement on weapon switch.

The generated HTML report is `build/reports/tests/test/index.html`. Mocked player tests establish scheduler structure, not capacity for 100 real network clients. The optional probe is built separately and is not included in the shipping plugin JAR.

## Running-server observations

| Check | Observed result |
| --- | --- |
| Cold startup/class loading | RecoilSpell and StopRecoilSpell loaded from the addon JAR through the official loader list, before addon enable; no missing-class errors |
| Relative packet shape | Both relative flags true; only yaw/pitch changes; no extra position packet during basic recoil |
| 3° vertical recoil in 3 steps | Received pitch deltas `-1.5`, `-1.0`, `-0.5`; final server and simulated-client pitch `-3°` from zero |
| Position preservation | Position `[10.5, -60, -9.5]` identical before/after the basic recoil test |
| Ungranted success receipt | Calling guarded recoil without its projectile added zero packets |
| Actual projectile + recoil | ParticleProjectileSpell followed by guarded recoil produced one three-step recoil |
| Failed preceding projectile | A targeted dummy with no valid nearby player did not produce guarded recoil |
| Reload-start cancellation | Projectile → recoil → reload-start produced no queued recoil packet |
| Same-tick stop race | Projectile → reload-start → recoil also produced zero recoil packets on the final build |
| `/ms reload` cleanup | A queued cast followed immediately by reload left zero active sessions and no resumed tail |
| Changed YAML | `recoil: 3` changed to `6` via `/ms reload`; next deltas were `-3`, `-2`, `-1` |
| New YAML spell | Newly added `MR_New` loaded on reload and sent its configured single `-1°` delta |
| Synthetic downward input during recoil | Over 20 ticks: `-3°` recoil plus twenty `+0.1°` inputs; initial pitch 7°, final simulated-client ≈6° and server 6° |
| Projectile direction after recoil | Consecutive real particle projectiles launched at yaw 20°, pitches 6° then 3°. Their normalized velocity matched the captured server aim with vector distance `0.0` in both observations |
| Paper API baseline | `Player.setRotation` emitted absolute flags false/false, changed server pitch immediately, and preserved coordinates |
| ProtocolLib comparison | 5.4.0 dependency-inclusive artifact delivered true/true relative flags; raw send left server pitch at 0° immediately, then -3° after the client response. The library warned that 1.21.11 was untested |
| Pitch boundary | With the simulated client looking at -89°, recoil ended at -90° on both client and server; no coordinate change |
| Logout during pending recoil | A queued long impulse followed by a kick left zero active sessions |
| Shutdown | Tasks and plugin state cleaned up on orderly server stop; test server/client are not left running |

The particle-projectile check uses `check-plugins: true` and the separate runtime probe to observe the first tracker velocity. It validates the supplied fixture's launch direction; it does not validate a production weapon that caches a different origin or aim.

Reproduce using [tests/runtime](../../tests/runtime/README.md). The fixture includes a dependency-pinned client, spell YAML, and optional `runtimeProbeJar` task. ProtocolLib is only an optional comparison tool, not a MagicRecoil requirement.

## Human acceptance checklist

Use a real vanilla 1.21.11 client with the original weapon YAML. Compare identical server TPS, sensitivity, FOV, firing cadence, recoil strength, and network conditions against the old RotateSpell configuration.

1. **Feel:** single shots and 30-shot bursts should have a noticeably better controllable response than the old setup. Record both versions. If there is no useful improvement, do not accept this as meeting the camera-feel requirement.
2. **Mouse control:** pull down and move sideways while firing. Aim must remain responsive; releasing the trigger must not snap back with auto-return disabled. Test near pitch ±90° and yaw wraparound.
3. **Shot gating:** empty magazine, failed cast, cancelled cast, cooldown, reload in progress, and cancelled firing buff must not create unintended recoil. Confirm the actual partial/full/direct/delayed casting mode.
4. **Projectile alignment:** single-shot at a marked wall, then burst and compensate. Compare actual projectile trajectories with the view. Check for a cached location/direction in the original loop.
5. **Lifecycle:** change hotbar slot, swap hands, drop a gun, replace an item in the same slot, die, respawn, disconnect, reconnect, change world, and teleport during a long tail. No old tail should resume.
6. **Reload:** while firing, edit a number and run `/ms reload`; add a new spell; repeat several times. No stale motion or duplicate scheduler should remain. Restart after JAR changes.
7. **Network/TPS:** test representative ping, jitter, and degraded TPS. Look for corrections, camera stepping, rubber-banding, or delayed shots. Localhost results do not establish behavior at 100–200 ms RTT.
8. **Plugin combinations:** repeat with the actual anti-cheat, proxy/protocol translator, and camera/teleport plugins. Test riders and special movement modes if your server uses them.
9. **Load:** profile the intended number of simultaneously firing players with spark. Check main-thread time, packet rate, memory after logout, and TPS. Expect at most one recoil packet per active player per server tick, not one packet per animation object.

## Not established by this release

- Perceptual superiority over RotateSpell or a native VALORANT-like renderer.
- Real mouse comfort, high-latency synchronization, anti-cheat compatibility, or a production concurrency limit.
- Correctness of the missing original Paimon gun, its ammunition variables, reload conditions, or custom `ms-*.jar` binaries.
- Minecraft 26.3, Folia, and alternative MagicSpells releases.

A client mod with frame-based additive recoil is the next step if the vanilla client cannot meet the feel requirement. This alpha should be evaluated, not described as having completed that acceptance test.
