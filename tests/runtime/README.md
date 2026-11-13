# Optional runtime checks

This fixture is for an **isolated local Paper 1.21.11 server** only. It is not part of the plugin JAR and does not simulate vanilla rendering. Keep the server bound to `127.0.0.1`, use a disposable world, and do not connect this offline test client to a production server.

1. Build with Java 21: `./gradlew build runtimeProbeJar`.
2. Prepare Paper 1.21.11 build 132 with MagicSpells 4.0-Beta-18, following Paper's normal server setup and EULA process. Set `server-ip=127.0.0.1`, `server-port=25579`, `online-mode=false`, `enforce-secure-profile=false`, `gamemode=creative`, and `allow-flight=true`. Flat terrain is convenient.
3. Install the addon from `build/libs/` and optional probe from `build/runtime-probe/`. Copy `spells-smoke.yml` into `plugins/MagicSpells/`. Restart.
4. In this directory run `npm ci`, then `node client.cjs`. The client logs packets to ignored `packets.jsonl`.
5. Use the **server console** commands below. Client stdin accepts `look <yaw> <pitch>`, `compensate <degrees-per-received-packet>`, and `quit`. These are synthetic inputs, not in-game chat.

```text
mrprobe snapshot RecoilProbe
ms cast as RecoilProbe MR_Smoke
# Wait for three ticks, then compare snapshots and packet log.
mrprobe snapshot RecoilProbe
magicrecoil status

# No preceding projectile: should not add packets.
ms cast as RecoilProbe MR_Gated

# A real ParticleProjectileSpell, then guarded recoil.
ms cast as RecoilProbe MR_Fire
# After the tail, fire again: probe compares emitted projectile direction with aim at launch.
ms cast as RecoilProbe MR_Fire

# Run alone, with no other target player within one block.
ms cast as RecoilProbe MR_Failed
# Schedules recoil, then cancels it at reload-start in the same tick.
ms cast as RecoilProbe MR_Fire_Stop
# Stop before the recoil call in the same tick: should also add zero packets.
ms cast as RecoilProbe MR_Stop_Before_Recoil

# Long impulse: client 'compensate 0.1' adds 2 degrees across 20 packets.
ms cast as RecoilProbe MR_Long

# API baseline: sends one absolute rotation packet.
mrprobe api RecoilProbe
```

To test reload, change `MR_Smoke.recoil` from 3 to 6 and add a new recoil spell name. Queue `MR_Long`, run `ms reload`, then check that no old tail resumes, the changed strength applies, and the new spell loads. To test pitch clamping, set client input to `look 0 -89`, disable compensation, and cast MR_Smoke. No pitch should exceed -90.

The probe's `LAUNCH` and `PROJECTILE` lines compare the first particle-projectile velocity with the direction captured at cast time. `check-plugins: true` enables MagicSpells tracker events for this fixture. This does not validate an unavailable production gun's stored location/direction settings.

For the optional ProtocolLib comparison, install **`ProtocolLib-5.4.0-all.jar`**, the dependency-inclusive artifact from Maven Central, then restart. The ordinary Maven JAR is a thin library and is not suitable as a standalone plugin. `mrprobe protocol RecoilProbe` sends a relative rotation through ProtocolLib without updating server aim itself; the immediate snapshot and later snapshot show the difference. This narrow test is not a claim that every ProtocolLib 5.4.0 feature supports 1.21.11. MagicRecoil itself does not require it.

Do not ship MagicRecoilProbe or these test spells on a production server. After testing, send `quit` to the client and `stop` to the server. Record the precise builds, checksums, input sequence, packet flags, positions, final rotations, and any errors. See [release test evidence](../../docs/en/testing.md) and [human acceptance checklist](../../docs/en/testing.md#human-acceptance-checklist).
