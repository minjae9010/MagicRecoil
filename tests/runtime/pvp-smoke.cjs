// Actual connected Player entities, driven by headless protocol clients on a disposable Paper server.
// This does not render player models or simulate human input/latency.
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { EventEmitter, once } = require('node:events');
const mc = require('minecraft-protocol');

function option(name, fallback) {
  const index = process.argv.indexOf('--' + name);
  return index < 0 ? fallback : process.argv[index + 1];
}
const root = path.resolve(__dirname, '../..');
const baseArg = option('base-server');
if (!baseArg) throw new Error('Usage: node tests/runtime/pvp-smoke.cjs --base-server <Paper 1.21.11 directory> [--java <Java 21 executable>]');
const base = path.resolve(baseArg);
const directory = path.resolve(option('work-dir', path.join(root, '.work/pvp-hit/server')));
if (base === directory || directory.startsWith(base + path.sep) || base.startsWith(directory + path.sep)) {
  throw new Error('Use separate base and disposable server directories');
}
const port = Number(option('port', '25588'));
const java = option('java', 'java');
const addon = path.resolve(option('addon-jar', path.join(root, 'build/libs/MagicRecoil-0.3.0-alpha.1.jar')));
const probe = path.resolve(option('probe-jar', path.join(root, 'build/runtime-probe/MagicRecoilProbe-0.3.0-alpha.1.jar')));
fs.mkdirSync(directory, { recursive: true });
for (const name of ['cache', 'libraries', 'versions']) {
  if (fs.existsSync(path.join(base, name)) && !fs.existsSync(path.join(directory, name))) {
    fs.cpSync(path.join(base, name), path.join(directory, name), { recursive: true });
  }
}
for (const name of ['paper.jar', 'eula.txt']) fs.copyFileSync(path.join(base, name), path.join(directory, name));
const spellsDir = path.join(directory, 'plugins/MagicSpells');
fs.mkdirSync(spellsDir, { recursive: true });
fs.copyFileSync(addon, path.join(directory, 'plugins/MagicRecoil.jar'));
fs.copyFileSync(probe, path.join(directory, 'plugins/MagicRecoilProbe.jar'));
fs.copyFileSync(path.resolve(option('magic-spells-jar', path.join(base, 'plugins/MagicSpells.jar'))), path.join(directory, 'plugins/MagicSpells.jar'));
fs.copyFileSync(path.join(base, 'plugins/MagicSpells/general.yml'), path.join(spellsDir, 'general.yml'));
let yaml = fs.readFileSync(path.join(__dirname, 'spells-pvp.yml'), 'utf8');
for (const hook of ['hit', 'head', 'body', 'front', 'back', 'left', 'right', 'above', 'below']) {
  yaml += `\nMR_PVP_hook_${hook}:\n  spell-class: .ExternalCommandSpell\n  helper-spell: true\n  can-target: [players]\n  execute-on-console-instead: true\n  command-to-execute: ["mrprobe pvp hook ${hook}"]\n`;
}
fs.writeFileSync(path.join(spellsDir, 'spells-pvp.yml'), yaml);
fs.writeFileSync(path.join(directory, 'server.properties'), `server-ip=127.0.0.1
server-port=${port}
online-mode=false
enforce-secure-profile=false
gamemode=creative
allow-flight=true
pvp=true
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
spawn-protection=0
view-distance=2
simulation-distance=2
max-players=2
network-compression-threshold=-1
`);
const logPath = path.join(directory, 'pvp-console.log');
fs.writeFileSync(logPath, '');
const reportPath = path.join(directory, 'pvp-results.json');
const report = { server: 'Paper 1.21.11', magicSpells: 'Beta 18', client: 'minecraft-protocol 1.68.0',
  humanInput: false, forcedPose: false,
  poseSetup: 'Bukkit setSwimming/setGliding; normal pose updates, no setPose override',
  motion: 'Stationary targets with gravity disabled; no human movement or latency simulation',
  cases: [], healthPackets: [] };
const events = new EventEmitter();
const clients = [];
const server = spawn(java, ['-Xms256M', '-Xmx1G', '-XX:ActiveProcessorCount=2', '-jar', 'paper.jar', '--nogui'],
  { cwd: directory, windowsHide: true });
let pending = '';
server.stdout.on('data', chunk => {
  fs.appendFileSync(logPath, chunk);
  pending += chunk.toString();
  let end;
  while ((end = pending.indexOf('\n')) >= 0) {
    const line = pending.slice(0, end).replace(/\x1b\[[0-9;]*m/g, '');
    pending = pending.slice(end + 1);
    events.emit('line', line);
    const marker = line.indexOf('PVP_PROBE ');
    if (marker >= 0) {
      try { events.emit('probe', JSON.parse(line.slice(marker + 10))); }
      catch (error) { console.error('Malformed probe JSON:', line); }
    }
  }
});
server.stderr.on('data', chunk => fs.appendFileSync(logPath, chunk));
function until(event, predicate, timeout = 10000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { events.off(event, listener); reject(new Error('Timed out waiting for ' + event)); }, timeout);
    function listener(value) {
      if (predicate(value)) { clearTimeout(timer); events.off(event, listener); resolve(value); }
    }
    events.on(event, listener);
  });
}
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
function command(value) { server.stdin.write(value + '\n'); }
async function connect(name) {
  const client = mc.createClient({ host: '127.0.0.1', port, username: name, auth: 'offline', version: '1.21.11' });
  clients.push(client);
  let loaded = false;
  client.on('position', p => {
    client.write('teleport_confirm', { teleportId: p.teleportId });
    client.write('position_look', { x: p.x, y: p.y, z: p.z, yaw: p.yaw, pitch: p.pitch,
      flags: { onGround: false, hasHorizontalCollision: false } });
    if (!loaded) { loaded = true; client.write('player_loaded', {}); }
  });
  client.on('update_health', p => report.healthPackets.push({ player: name, health: p.health }));
  client.on('error', error => console.error(name + ': ' + error.message));
  await once(client, 'login');
  return client;
}
const multipliers = { front: 1, back: 1.5, left: 1.1, right: 1.2, above: 1.3, below: 1.4 };
async function runCase(pose, region, side, castPath, yaw = 0, category = 'box_contract', enabled = true) {
  const id = `${category}_${pose}_${region}_${side}_${castPath}_${yaw}`;
  const setupPromise = until('probe', data => data.phase === 'setup' && data.id === id);
  command(`mrprobe pvp setup ${id} ${pose} ${region} ${side} ${yaw}`);
  const setup = await setupPromise;
  const wantedPose = { standing: 'STANDING', swimming: 'SWIMMING', gliding: 'FALL_FLYING' }[pose];
  const validPose = setup.pose === wantedPose && (pose !== 'swimming' || setup.swimming && setup.inWater)
    && (pose !== 'gliding' || setup.gliding);
  if (!validPose) {
    report.cases.push({ id, category, setup, pass: false, reason: 'Server did not maintain the requested live pose' });
    console.log('FAIL pose ' + id + ' ' + JSON.stringify(setup));
    return;
  }
  const resultPromise = until('probe', data => data.phase === 'result' && data.id === id);
  command(`mrprobe pvp fire ${castPath}`);
  const result = await resultPromise;
  const head = region !== 'body';
  const expectedHealth = enabled ? 20 - 2 * multipliers[side] * (head ? 2 : 1) : 20;
  const wantedHooks = enabled ? { hit: 1, [head ? 'head' : 'body']: 1, [side]: 1 } : {};
  const hooksMatch = ['hit', 'head', 'body', ...Object.keys(multipliers)]
    .every(key => (result.hooks[key] || 0) === (wantedHooks[key] || 0));
  const healthMatch = Math.abs(result.health - expectedHealth) < 0.001;
  const samePose = result.pose === wantedPose;
  const pass = hooksMatch && healthMatch && samePose;
  report.cases.push({ id, category, setup, result, expectedHealth, expectedHooks: wantedHooks, pass });
  if (!pass) console.log('FAIL ' + id + ' health=' + result.health + ' expected=' + expectedHealth + ' hooks=' + JSON.stringify(result.hooks));
  else if (report.cases.length % 12 === 0) console.log('PASS progress ' + report.cases.length + ' cases; ' + id);
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
}

(async () => {
  try {
    console.log('Starting two-player PvP/pose tests on 127.0.0.1:' + port);
    await until('line', line => line.includes('Done ('), 120000);
    // Peaceful mode heals players independently of the ordinary regeneration gamerule.
    command('difficulty normal');
    // The diagnostic plugin sets gamerules through Bukkit, avoiding version-specific command names.
    await connect('MR_PVP_Attacker');
    await connect('MR_PVP_Target');
    await delay(4000); // Allow the ordinary spawn-protection period to expire.
    const poses = ['standing', 'swimming', 'gliding'];
    const sides = option('quick', 'false') === 'true' ? ['front'] : Object.keys(multipliers);
    for (const pose of poses) for (const region of ['upper', 'body']) for (const side of sides) for (const castPath of ['ray', 'particle']) {
      await runCase(pose, region, side, castPath);
    }
    for (const pose of poses) for (const castPath of ['ray', 'particle']) {
      await runCase(pose, 'eye', 'front', castPath, 0, 'eye_anchor');
    }
    if (option('quick', 'false') !== 'true') {
      for (const pose of poses) for (const side of ['front', 'back', 'left', 'right']) {
        await runCase(pose, 'upper', side, 'ray', 90, 'rotated_facing');
      }
      const policy = until('probe', data => data.phase === 'policy' && !data.pvp);
      command('mrprobe pvp policy false'); await policy;
      for (const castPath of ['ray', 'particle']) await runCase('standing', 'upper', 'front', castPath, 0, 'pvp_disabled', false);
    }
    report.summary = Object.fromEntries([...new Set(report.cases.map(c => c.category))].map(category => {
      const cases = report.cases.filter(c => c.category === category);
      return [category, { passed: cases.filter(c => c.pass).length, failed: cases.filter(c => !c.pass).length }];
    }));
    console.log(JSON.stringify(report.summary));
    console.log('Detailed report: ' + reportPath);
    if (report.cases.some(c => !c.pass)) process.exitCode = 1;
  } catch (error) {
    report.error = error.stack;
    console.error(error.stack);
    process.exitCode = 1;
  } finally {
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
    for (const client of clients) client.end();
    if (server.exitCode === null) {
      command('stop');
      const timeout = setTimeout(() => { if (server.exitCode === null) server.kill(); }, 20000);
      await once(server, 'exit');
      clearTimeout(timeout);
    }
    console.log('Stopped PvP test server and both clients');
  }
})();
