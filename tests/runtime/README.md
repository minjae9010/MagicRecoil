# Optional runtime checks

These fixtures are for an **isolated local Paper 1.21.11 server** only and are not part of the plugin JAR. The Node protocol client does not simulate Minecraft rendering; the [visual burst fixture](#visual-burst-fixture) runs with a real Minecraft client. Keep the server bound to `127.0.0.1`, use a disposable world, and do not connect the offline protocol client to a production server.

The default fixture remains 1.21.11/Beta 18. For another matching server/client combination, set `RECOIL_TEST_VERSION` and `RECOIL_TEST_PORT` (defaults: `1.21.11`, `25579`). The same client was also exercised on 1.21.4/Beta 17; its rotation packets are absolute. The installed protocol library does not support 26.2. See the [compatibility matrix](../../docs/en/compatibility.md) for tested combinations and limits.

<!-- toc:start -->
## On this page

- [Protocol checks](#protocol-checks)
- [Visual burst fixture](#visual-burst-fixture)
- [ADS integration fixture](#ads-integration-fixture)
- [Right-click firing and FOV fixture](#right-click-firing-and-fov-fixture)
- [CrackShot controls fixture](#crackshot-controls-fixture)
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

## ADS integration fixture

This starts a real, disposable Paper process and two synthetic clients; it does not test a human mouse or renderer. Use **Java 21**, Paper **1.21.11 build 132**, the official **MagicSpells 4.0-Beta-18**, the freshly built addon and optional probe (`./gradlew build runtimeProbeJar`). ProtocolLib is not needed.

1. Prepare a separate server directory with `paper.jar` and its normal EULA acceptance. Never use a production world. In `server.properties` set `server-ip=127.0.0.1`, an unused port (the recorded run used `25581`), `online-mode=false`, `enforce-secure-profile=false`, `gamemode=survival`, `allow-flight=true`, `spawn-protection=0`, `view-distance=2` and `simulation-distance=2`. Use normal world-generation settings or a valid flat preset.
2. Install exactly one addon JAR, MagicSpells JAR and probe JAR in `plugins/`. Copy `spells-aim.yml` to `plugins/MagicSpells/`. Do not install other test gun bindings. Set `default-all-perms-false: false` in MagicSpells `general.yml`. Start with unprivileged `ADS_A` / `ADS_B`; do not grant no-cost/no-cooldown permissions. Stop the server before running the harness.
3. In `tests/runtime/`, run `npm ci`, set `JAVA_HOME` to your JDK 21 directory, then run `node aim-check.cjs <absolute-isolated-server-directory>`. The harness launches Paper with `windowsHide: true`, performs assertions through the real console and actual protocol packets, and stops it. Full server output is in the test server's ignored `ads-test.log`.
4. Run it again against the same disposable server to check restart/join restoration. Confirm `ALL ADS RUNTIME CHECKS PASSED` and inspect server logs. The initial recorded flat-world generation emitted `No key layers in MapLike[{}]`; that world-preset diagnostic was separate from the plugin checks.

Checks cover click toggles, speed and recoil, real hotbar/F actions, suspension, non-toggle activation, missing/charged reagents, cooldowns, modifiers, external speed effects, two-player isolation, reload/BuffEnd/death/logout cleanup, `/ms reload`, and a representative Paimon Buff/Passive/Beam/ammunition/reload chain. `mrprobe aim ADS_A` prints the attribute, scoped recoil multiplier, session count, cobblestone count, ammo and server rotation. The representative gun omits production damage types, resource-pack sounds and external commands; it is not a claim that a complete production pack passed. Existing smoke and visual fixtures are preserved.

한국어: [조준 설정](../../docs/ko/aim-and-input.md)과 [검증 범위](../../docs/ko/testing.md#ads-확장-검증)를 참고하세요.

## Right-click firing and FOV fixture

The same `spells-aim.yml` / `aim-check.cjs` fixture includes native-use diamond-hoe AutoFire, an unmodified blaze-rod repeated-click mode (six-tick release timeout), left-click ADS/FOV packets, reload latching, eventless stop, slot/F/death cleanup and `/ms reload`. Rebuild and install both the addon and probe before running. The probe prints current/base movement speed, server walk speed, ADS state, active firing, native use, prepared-item marker, ammo and rotation. The harness inspects outgoing abilities packets and leaves ADS + firing active for normal shutdown cleanup; run it twice on the same disposable server. Existing saved items, selected slot and reagent values are reset by the fixture. This does not render FOV or test human mouse behavior, native spyglass, a JVM crash or the CrackShot plugin. See [verification](../../docs/en/testing.md#right-click-firing-and-fov-verification) / [한국어](../../docs/ko/testing.md#우클릭-연사와-fov-검증).

## CrackShot controls fixture

The same fixture now includes BUFFERED_CLICK on a golden hoe with custom model/data and left-click SLOWNESS scope. It asserts a three-round five-tick buffer at interval 2, full 1.5-degree recoil while scoped, repeated refills/draining, unchanged item hash, no component preparation/native-use state, potion ON/OFF, previous-duration restoration, newer external-effect preservation and slot/reload cleanup. The primary Paimon examples use this layout; NATIVE_USE and REPEATED_CLICK remain explicit alternatives. See [evidence](../../docs/en/testing.md#crackshot-controls-verification) / [한국어](../../docs/ko/testing.md#crackshot-조작-검증).
