# Optional runtime checks

These fixtures are for an **isolated local Paper 1.21.11 server** only and are not part of the plugin JAR. The Node protocol client does not simulate Minecraft rendering; the [visual burst fixture](#visual-burst-fixture) runs with a real Minecraft client. Keep the server bound to `127.0.0.1`, use a disposable world, and do not connect the offline protocol client to a production server.

<!-- toc:start -->
## On this page

- [Protocol checks](#protocol-checks)
- [Visual burst fixture](#visual-burst-fixture)
<!-- toc:end -->

## Protocol checks

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

## Visual burst fixture

Copy [spells-demo.yml](spells-demo.yml) to `plugins/MagicSpells/spells-demo.yml`, run `ms reload` from the console, and join with a real Minecraft 1.21.11 client. The fixture has four English spell identifiers:

| Spell | Purpose |
| --- | --- |
| `Demo_Projectile` | Straight particle projectile with a visible trail; no ammunition or damage system |
| `Demo_Recoil` | Guarded recoil: strength 1.2, growth 0.025, horizontal 0.55, three steps, no auto-return |
| `Demo_Shot` | Runs the projectile followed immediately by its recoil |
| `Demo_Burst` | Runs 18 shots, spaced three ticks apart |

On a disposable flat world, build a marked wall in front of the player. The README recording uses a wall at Z=16 and a player at `[0.5, -60, -5.5]`, facing positive Z. Use your own player's name in these **server-console** commands:

```text
tp RecoilCamera 0.5 -60 -5.5 0 0
item replace entity RecoilCamera weapon.mainhand with minecraft:crossbow
# Begin recording, hold the camera still, then run:
ms cast as RecoilCamera Demo_Burst
# Keep recording after the burst to observe that the view does not return.
```

The crossbow is a visual prop. The console starts the MagicSpells burst; the fixture does not bind firing to mouse clicks. At 20 TPS the first-to-last shot interval is 2.55 seconds, followed by the remaining recoil steps. Do not switch slots or teleport during the burst because those actions cancel pending recoil. Reset the view before each new take.

With the optional probe installed, compare `mrprobe snapshot RecoilCamera` and `magicrecoil status` before and after. For the current built-in pattern, reset yaw/pitch to zero and compare against the configured cumulative rise cap rather than the old recording. The legacy alpha.1 recording produced yaw `0.907°`, pitch `-26.19°`, and 54 packets; those are historical results, not the current baseline. Upward recoil must never exceed the default 15-degree burst cap, and yaw must visit both sides of the initial center when the other settings remain symmetric. Frame rate and server TPS are separate measurements.

For MCP capture, use a separate client with [Minecraft Mod MCP](https://github.com/langyo/minecraft-mod-mcp), match the Minecraft version and process identity, and capture the original game framebuffer. The capture helper is a test tool, not a MagicRecoil dependency. Convert the captured frames to a GIF at normal speed; document the capture method and any trimming in [Testing](../../docs/en/testing.md).

한국어 안내: [화면 녹화 및 검증 문서](../../docs/ko/testing.md)를 참고하세요. 이 구성은 반동 시연용이며 기존 총기의 탄약·재장전·입력 처리를 대체하지 않습니다.
