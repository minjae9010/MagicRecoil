"""Run hit/recoil integration checks in a disposable, loopback-only Paper server.

Requires an existing Paper 1.21.11 test installation with an accepted EULA,
MagicSpells Beta 18, Java 21, Node, and `npm ci` in tests/runtime.
Only the disposable --work-dir is changed; the base installation is read-only.
"""

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-server', required=True, type=Path)
parser.add_argument('--work-dir', type=Path, default=root / '.work/hit/server')
parser.add_argument('--java', default='java')
parser.add_argument('--port', type=int, default=25587)
parser.add_argument('--addon-jar', type=Path)
parser.add_argument('--magic-spells-jar', type=Path)
args = parser.parse_args()
server = args.work_dir.resolve()
baseline = args.base_server.resolve()
if baseline == server or baseline in server.parents or server in baseline.parents:
    parser.error('Choose separate base and disposable server directories')
addon = args.addon_jar or max((p for p in (root / 'build/libs').glob('MagicRecoil-*.jar')
                             if not p.name.endswith('-sources.jar')), key=lambda p: p.stat().st_mtime)
magic_spells = args.magic_spells_jar or baseline / 'plugins/MagicSpells.jar'
creationflags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
server.mkdir(parents=True, exist_ok=True)
for name in ('cache', 'libraries', 'versions'):
    if (baseline / name).exists() and not (server / name).exists():
        shutil.copytree(baseline / name, server / name)
for name in ('paper.jar', 'eula.txt'):
    shutil.copy2(baseline / name, server / name)
plugins = server / 'plugins'
(plugins / 'MagicSpells').mkdir(parents=True, exist_ok=True)
shutil.copy2(addon, plugins / 'MagicRecoil.jar')
shutil.copy2(magic_spells, plugins / 'MagicSpells.jar')
shutil.copy2(baseline / 'plugins/MagicSpells/general.yml', plugins / 'MagicSpells/general.yml')
shutil.copy2(root / 'examples/hit-integration.yml', plugins / 'MagicSpells/spells-example.yml')

shutil.copy2(root / 'tests/runtime/spells-hit.yml', plugins / 'MagicSpells/spells-hit.yml')
(server / 'server.properties').write_text(f'''server-ip=127.0.0.1
server-port={args.port}
online-mode=false
enforce-secure-profile=false
gamemode=creative
allow-flight=true
level-type=minecraft:flat
generator-settings={{"layers":[{{"block":"minecraft:bedrock","height":1}},{{"block":"minecraft:dirt","height":2}},{{"block":"minecraft:grass_block","height":1}}],"biome":"minecraft:plains"}}
generate-structures=false
spawn-protection=0
view-distance=2
simulation-distance=2
max-players=2
network-compression-threshold=-1
''', encoding='utf-8')
log_path = server / 'console.log'
log = log_path.open('w', encoding='utf-8')
proc = subprocess.Popen([args.java, '-Xms256M', '-Xmx1G', '-XX:ActiveProcessorCount=2', '-jar', 'paper.jar', '--nogui'],
                        cwd=server, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                        text=True, creationflags=creationflags)
client = None
def output():
    return log_path.read_text(encoding='utf-8', errors='replace')
def wait_for(token, start=0, timeout=45):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if token in output()[start:]:
            return
        if proc.poll() is not None:
            raise RuntimeError(output()[-6000:])
        time.sleep(.2)
    raise TimeoutError(f'Missing {token}:\n' + output()[-5000:])
def command(value):
    proc.stdin.write(value + '\n')
    proc.stdin.flush()
def setup(z=4.5, yaw=0, pitch=0):
    command('kill @e[tag=MRTarget]')
    command(f'summon villager 0.5 -60 {z} {{NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:["MRTarget"],Rotation:[{float(yaw)}f,0f]}}')
    command(f'tp RecoilProbe 0.5 -60 -5.5 0 {pitch}')
    for name in ('hit', 'head', 'body', 'back', 'front'):
        command(f'scoreboard players set {name} mrhooks 0')
    time.sleep(.6)
def check(label, health, hooks):
    # Health is recorded as thousandths of a health point by the real server.
    offset = len(output())
    command('execute store result score health mrhooks run data get entity @e[tag=MRTarget,limit=1] Health 1000')
    lo, hi = health
    conditions = f'if score health mrhooks matches {lo}..{hi} '
    for name in ('hit', 'head', 'body', 'back', 'front'):
        conditions += f'if score {name} mrhooks matches {hooks.get(name, 0)} '
    command(f'execute {conditions}run say MR_ASSERT_{label}_PASS')
    command('scoreboard players get health mrhooks')
    wait_for(f'[Server] MR_ASSERT_{label}_PASS', offset, 5)
    print('PASS ' + label, flush=True)

def check_speed(label, expected):
    offset = len(output())
    command('execute store result score speed mrhooks run attribute RecoilProbe minecraft:movement_speed get 100000')
    command(f'execute if score speed mrhooks matches {expected - 1}..{expected + 1} run say MR_ASSERT_{label}_PASS')
    wait_for(f'[Server] MR_ASSERT_{label}_PASS', offset, 5)
    print('PASS ' + label, flush=True)

try:
    print(f'Starting isolated Paper 1.21.11 / MagicSpells Beta 18 on 127.0.0.1:{args.port}', flush=True)
    wait_for('Done (', timeout=120)
    assert 'Enabled rotation:' in output(), output()[-6000:]
    assert 'invalid spell class' not in output().lower(), output()[-6000:]
    command('gamerule doMobSpawning false')
    command('gamerule doDaylightCycle false')
    command('scoreboard objectives add mrhooks dummy')
    (server / 'packets.jsonl').write_text('', encoding='utf-8')
    env = dict(os.environ, RECOIL_TEST_PORT=str(args.port), RECOIL_TEST_VERSION='1.21.11')
    client = subprocess.Popen(['node', str(root / 'tests/runtime/client.cjs')], cwd=server, env=env,
                              stdin=subprocess.PIPE, stdout=(server / 'client.log').open('w', encoding='utf-8'),
                              stderr=subprocess.STDOUT, text=True, creationflags=creationflags)
    wait_for('RecoilProbe joined the game', timeout=25)
    time.sleep(2)
    setup()
    command('ms cast as RecoilProbe MR_Hit')
    time.sleep(.6)
    check('ray_head_back', (7998, 8002), {'hit': 1, 'head': 1, 'back': 1})
    setup(yaw=180, pitch=5)
    command('ms cast as RecoilProbe MR_Hit')
    time.sleep(.6)
    check('ray_body_front', (15998, 16002), {'hit': 1, 'body': 1, 'front': 1})
    setup(z=24.5)
    command('ms cast as RecoilProbe MR_Hit')
    time.sleep(.6)
    check('distance_linear', (13905, 13915), {'hit': 1, 'head': 1, 'back': 1})
    setup()
    command('setblock 0 -59 0 stone')
    command('ms cast as RecoilProbe MR_Fire')
    time.sleep(.6)
    check('wall_blocks_hit', (19998, 20002), {})
    command('setblock 0 -59 0 air')
    setup()
    command('tp RecoilProbe 0.5 -60 -5.5 90 0')
    time.sleep(.2)
    command('ms cast as RecoilProbe MR_Fire')
    time.sleep(.6)
    check('miss_has_no_hooks', (19998, 20002), {})
    packets = [json.loads(line) for line in (server / 'packets.jsonl').read_text().splitlines()]
    rotations = [p for p in packets if p['event'] == 'rotation']
    assert len(rotations) == 6, rotations
    print('PASS recoil_on_wall_and_miss (6 rotation packets)', flush=True)
    setup()
    command('ms cast as RecoilProbe MR_Projectile')
    time.sleep(.2)
    command('tp RecoilProbe 30 -60 -5.5 90 0')
    time.sleep(1.5)
    check('projectile_launch_snapshot', (7998, 8002), {'hit': 1, 'head': 1, 'back': 1})
    setup()
    command('ms cast as RecoilProbe MR_Projectile_MissingArgument')
    time.sleep(1.6)
    check('missing_callback_argument_fails_closed', (19998, 20002), {})
    setup()
    command('item replace entity RecoilProbe weapon.mainhand with stick')
    command('ms cast as RecoilProbe MR_Aim')
    time.sleep(.2)
    check_speed('aim_slows_movement', 6500)
    before = len([json.loads(line) for line in (server / 'packets.jsonl').read_text().splitlines()
                  if json.loads(line)['event'] == 'rotation'])
    command('ms cast as RecoilProbe MR_Fire')
    time.sleep(.6)
    aimed = [json.loads(line) for line in (server / 'packets.jsonl').read_text().splitlines()
             if json.loads(line)['event'] == 'rotation'][before:]
    assert len(aimed) == 3 and abs(sum(p['pitch'] for p in aimed) + 0.5) < 1e-5, aimed
    print('PASS aimed_recoil_scales_to_half', flush=True)
    command('ms cast as RecoilProbe MR_Aim')
    time.sleep(.2)
    check_speed('aim_toggle_restores_speed', 10000)
    command('attribute RecoilProbe minecraft:movement_speed modifier add other:aim_smoke 0.2 add_multiplied_total')
    command('ms cast as RecoilProbe MR_Aim')
    time.sleep(.2)
    check_speed('aim_combines_with_external_modifier', 7800)
    command('ms cast as RecoilProbe MR_StopAim')
    time.sleep(.2)
    check_speed('aim_stop_preserves_external_modifier', 12000)
    command('attribute RecoilProbe minecraft:movement_speed modifier remove other:aim_smoke')
    command('ms cast as RecoilProbe MR_AimKeep')
    time.sleep(.2)
    client.stdin.write('slot 1\n'); client.stdin.flush()
    time.sleep(.3)
    check_speed('retained_aim_suspends_on_another_slot', 10000)
    client.stdin.write('slot 0\n'); client.stdin.flush()
    time.sleep(.3)
    check_speed('retained_aim_resumes_on_original_slot', 6500)
    command('ms cast as RecoilProbe MR_StopAim')
    command('ms cast as RecoilProbe MR_Aim')
    time.sleep(.2)
    offset = len(output())
    command('ms reload')
    wait_for('spells loaded', offset, 20)
    time.sleep(1)
    check_speed('reload_clears_aim', 10000)
    setup()
    command('ms cast as RecoilProbe MR_Projectile')
    time.sleep(1.6)
    check('projectile_after_reload', (7998, 8002), {'hit': 1, 'head': 1, 'back': 1})
    setup()
    command('ms cast as RecoilProbe MR_Hit')
    time.sleep(.6)
    check('ray_after_reload', (7998, 8002), {'hit': 1, 'head': 1, 'back': 1})
    assert 'NoSuchMethodError' not in output() and 'NoClassDefFoundError' not in output(), output()[-6000:]
    print('ALL HIT RUNTIME CHECKS PASSED', flush=True)
finally:
    if client is not None and client.poll() is None:
        try:
            client.stdin.write('quit\n'); client.stdin.flush()
            client.wait(timeout=10)
        except (BrokenPipeError, subprocess.TimeoutExpired):
            client.terminate()
            client.wait(timeout=10)
    if proc.poll() is None:
        command('stop')
        try:
            proc.wait(timeout=30)
        except subprocess.TimeoutExpired:
            proc.terminate()
    log.close()
    print('Stopped isolated hit test server', flush=True)
