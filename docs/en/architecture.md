# Architecture and protocol investigation

[English index](README.md) · [한국어](../ko/architecture.md)

Implementation evidence for developers. Operators should start with [Configuration](configuration.md); read [Recoil logic](recoil-logic.md) for how numbers become camera motion.

<!-- toc:start -->
## On this page

- [Decision](#decision)
- [Four approaches compared](#four-approaches-compared)
  - [Difference from Beta 18 RotateSpell](#difference-from-beta-18-rotatespell)
- [Class loading](#class-loading)
- [Recoil state](#recoil-state)
- [Compatibility boundaries](#compatibility-boundaries)
- [Primary evidence](#primary-evidence)
- [ADS state boundary](#ads-state-boundary)
- [Firing and FOV boundaries](#firing-and-fov-boundaries)
<!-- toc:end -->

## Decision

Use an isolated NMS adapter for Paper 1.21.11 that calls `ServerPlayer.forceSetRotation(yawDelta, true, pitchDelta, true)`. Inspection of the mapped server bytecode confirms that the server updates yaw, head yaw, pitch, and old rotation, then sends `ClientboundPlayerRotationPacket`. Its payload contains yaw, relative-yaw, pitch, and relative-pitch; it has no position or teleport identifier.

The immediate server update makes this more than a cosmetic camera packet. Returning client movement packets still participate in the normal server rotation pipeline. An instantaneous agreement over a delayed network is impossible: old inbound look packets can temporarily precede the client's acknowledgement of the new angle. The addon does not intercept or rewrite those packets, which avoids inventing a competing input-authority system.

## Four approaches compared

| Approach | 1.21.11 behavior / consequences | Evaluation |
| --- | --- | --- |
| Paper `Player.setRotation(yaw, pitch)` | `CraftPlayer` delegates to `CraftEntity`, which normalizes absolute angles and calls `forceSetRotation(..., false, ..., false)`. It already uses the rotation-only packet, not a position teleport, on this version. Absolute targets can overwrite mouse changes between calculation and delivery. | Official implementation inspected; real server/protocol baseline confirmed absolute flags and immediate aim update. |
| Relative rotation packet | `ClientboundPlayerRotationPacket(float, boolean, float, boolean)` supports independent relative flags. Applying deltas on the receiving client composes with its latest local angle. | Selected packet; real local protocol traffic verified. Do not confuse it with the position-and-look teleport packet. |
| ProtocolLib | Provides a wrapper around Minecraft packets. Sending the same relative packet cannot add client interpolation. A raw send also needs a deliberate server-state update. | With the 5.4.0 `-all` artifact, raw relative packet delivery worked. Immediate server pitch remained 0°, then became -3° after the test client's look response. ProtocolLib warns that 1.21.11 is untested; this is a narrow packet check, not full compatibility certification. No production dependency is added. |
| Direct NMS | The vanilla `forceSetRotation` method updates server aim and sends the relative packet together. Compile-time checks use the actual Paper 1.21.11 development bundle. | Selected implementation; built and exercised on Paper 1.21.11 build 132. Version-coupled code lives in `packet/v1_21_11/`. |

The four entries overlap: a packet describes the wire operation, while API, ProtocolLib, and NMS are ways to produce it. This is not a four-way performance benchmark. The relative position-and-look teleport packet is deliberately excluded from the recoil implementation because it brings coordinate/teleport-confirmation behavior that this feature does not need.

### Difference from Beta 18 RotateSpell

RotateSpell reads an absolute direction, adds angles, and calls `Entity.setRotation`. On Paper 1.21.11 that call is not inherently a coordinate teleport. MagicRecoil instead composes relative increments with the client's current view, splits impulses across ticks, merges overlapping shots, and manages burst patterns and cancellation. Those are concrete implementation differences; **they do not prove a perceptible improvement for every client**.

There is no duration or interpolation curve in the rotation-only packet. Vanilla rendering and packet timing determine how each step appears. A server cannot promise a 144 FPS recoil animation by sending 20 tick updates per second. True frame-level recoil smoothing needs a cooperating client mod. The acceptance checklist requires a human A/B comparison against RotateSpell.

## Class loading

MagicSpells Beta 18 exposes `MagicSpells.getClassLoaders()`. Its spell loader resolves a full class name from that list and requires a public `(MagicConfig, String)` constructor. Leading-dot class names are expanded into `com.nisovin.magicspells.spells`, so `.RecoilSpell` would require putting code in another project's package. This project uses its own full class name.

The Bukkit plugin declares `depend: [MagicSpells]`. During `onLoad`, before MagicSpells enables and constructs spells, the addon adds its class loader. Constructors only parse settings; they do not require a running manager. The addon enables its scheduler/handlers after MagicSpells. During reload it re-registers on the loading event, because MagicSpells clears its loader list on unload. There is one JAR and one defining class loader.

## Recoil state

```text
MagicSpells successful shot → single-use receipt → RecoilSpell
                                                   │
                                        validated immutable settings
                                                   │
                                   player UUID → current weapon session
                                                   │
                                   pattern → scaling → vector cap
                                                   │
                                      fixed 20-slot tick buffer
                                                   │
                                 one shared synchronous scheduler
                                                   │
                            rotation adapter → server aim + relative packet
```

Only one weapon session is active per player. Changing the recoil spell replaces it, so state does not grow with every gun a player has ever held. Settings belong to each spell instance; two players never share burst counters. A maximum of twenty future aggregate impulses is stored per player. An incoming shot adds to those slots instead of spawning another scheduler or an unbounded animation queue.

The scheduler starts on the first shot and cancels itself when no sessions remain. A session is removed once its tail and optional recovery finish after the idle gap. No player objects are retained in sessions; lookups use UUIDs. Success receipts contain at most one entry per player who cast a watched source, and are removed on lifecycle resets, disconnect, and reload. They are consumable only in their original tick.

All Bukkit/NMS operations run on the main server thread. Async recoil calls fail closed. There are no Netty handlers, timer threads, position mutations, synthetic movement acknowledgements, or per-shot tasks. Extreme accumulated impulse is clipped at 45 degrees per tick rather than retained as a long catch-up sequence.

## Compatibility boundaries

- Paper 1.21.11 only; no reflection-based guessing for other versions.
- Exact upstream Beta 18 API and release JAR are the compilation baseline.
- Folia is not supported by this scheduler model.
- Other plugins may still issue absolute rotation/teleport packets, cancel casts, or rewrite movement.
- A future 26.3 adapter must be independently compiled and tested. Keep core patterns/state free of NMS imports; port only the transport and review changed MagicSpells APIs/class loading.

## Primary evidence

- [MagicSpells Beta 18 source, pinned commit](https://github.com/TheComputerGeek2/MagicSpells/tree/1996743cacaac8c5742ac146e7ff905caf270f8a): `MagicSpells.loadSpells`, `getClassLoaders`, `load`, `unload`; `Subspell`; `MultiSpell`; `SpellCastedEvent`; `RotateSpell`.
- [Official Beta 18 release](https://github.com/TheComputerGeek2/MagicSpells/releases/tag/4.0-Beta-18), compiled JAR SHA-256 `3eee4d06cb90088f38ed27b5dc1af4b381eb0eeb74ba0b6c14a4454e901b9fce`.
- [Paper CraftEntity source](https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftEntity.java) and [CraftPlayer source](https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java).
- Actual mapped Paper 1.21.11 server bytecode inspected with `javap -c`: `Entity.forceSetRotation` and `ServerPlayer.forceSetRotation`. This establishes server state updates and the packet construction used by the adapter.
- [ProtocolLib packet declarations](https://github.com/dmulloy2/ProtocolLib/blob/master/src/main/java/com/comphenix/protocol/PacketType.java) and [5.4.0 release notes](https://github.com/dmulloy2/ProtocolLib/releases/tag/5.4.0). The optional comparison uses Maven Central's `ProtocolLib-5.4.0-all.jar`, SHA-256 `db046e206bd3c4f8df2d8e994352c0f283b53f4f0e0589abfcf2ec5c3d4b011e`.
- [Paper Java requirements](https://docs.papermc.io/paper/getting-started/): consult the historical version table for 1.21.11 (Java 21), rather than the current release's Java requirement.

---

[Previous: Testing](testing.md) · [Documentation](README.md) · [Next: Development](development.md)

## ADS state boundary

`aiming/AimSettings` validates immutable ADS options. `aiming/AimManager` owns UUID sessions, original main-hand signatures, one synchronous reconciliation task and the transient speed key. AimSpell uses normal MagicSpells casting, and StopAimSpell explicitly ends ADS. RecoilSpell queries the manager only after the existing success gate; RecoilSession scales completed new-shot impulses without modifying shared settings or queued shots. See [ADS lifecycle and ownership](aim-and-input.md#weapon-ownership-and-restoration).

## Firing and FOV boundaries

`firing/AutoFireSettings` validates the input mode, whole firing entry and tick intervals. `AutoFireManager` owns registered definitions, per-player input latches, native-use/repeat pulses and one synchronous task. `UseItemSupport` prepares only supported idle-use materials and restores its marked temporary components. `AutoFireSpell` hard-casts an ordinary firing entry; `StopAutoFireSpell` supports eventless reload chains. The existing projectile/ammo/reload code stays in the gun configuration. A top-level positive cast time is rejected; independently scheduled nested effects are not cancelled.

`aiming/ZoomAdapter` separates ADS lifecycle from the Paper 1.21.11 abilities packet implementation. `Paper12111ZoomAdapter` copies current abilities, derives a finite outgoing walking-speed field and restores current real abilities on clear. It never changes server walk speed or potion effects. Client rendering and FOV Effects remain outside the server contract.

`BUFFERED_CLICK` maintains a bounded per-player queue of at most five upcoming opportunities. Repeated events refill rather than append complete bursts. The synchronous scanner consumes due work, applies interval limits and calls the same complete firing entry. Stop hooks latch input until its queue drains. `aiming/SlownessZoom` owns short potion leases and prior active-effect backups; AimManager delegates potion-change events so external replacements/removals suppress further scope refresh.
