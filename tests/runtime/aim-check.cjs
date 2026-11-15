// Real local Paper process + synthetic protocol clients. Does not test rendering or human mouse feel.
// Prepare an isolated server as described in README.md, then pass its directory as the sole argument.
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { EventEmitter, once } = require('node:events');
const assert = require('node:assert/strict');
const mc = require('minecraft-protocol');
const serverDir = path.resolve(process.argv[2] || '');
if (!process.argv[2]) throw new Error('Usage: node aim-check.cjs <isolated-server-directory>');
const properties = fs.readFileSync(path.join(serverDir, 'server.properties'), 'utf8');
if (!/^server-ip=127\.0\.0\.1\s*$/m.test(properties) || !/^online-mode=false\s*$/m.test(properties)) {
  throw new Error('This fixture requires an isolated offline server bound to 127.0.0.1');
}
const port = Number(properties.match(/^server-port=(\d+)/m)?.[1]);
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java.exe') : 'java';
const server = spawn(java, ['-Xms256M', '-Xmx1G', '-jar', 'paper.jar', '--nogui'], { cwd: serverDir, windowsHide: true });
const lines = new EventEmitter();
let pending = '';
const logFile = path.join(serverDir, 'ads-test.log');
server.stdout.on('data', chunk => {
  fs.appendFileSync(logFile, chunk);
  pending += chunk.toString();
  let end;
  while ((end = pending.indexOf('\n')) !== -1) {
    lines.emit('line', pending.slice(0, end).replace(/\x1b\[[0-9;]*m/g, ''));
    pending = pending.slice(end + 1);
  }
});
server.stderr.on('data', chunk => fs.appendFileSync(logFile, chunk));
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
function until(predicate, timeout = 15000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { lines.off('line', listener); reject(new Error('Server response timeout')); }, timeout);
    function listener(line) {
      if (predicate(line)) { clearTimeout(timer); lines.off('line', listener); resolve(line); }
    }
    lines.on('line', listener);
  });
}
async function command(text) { server.stdin.write(text + '\n'); await delay(200); }
async function snapshot(name = 'ADS_A') {
  const response = until(line => line.includes(`AIM player=${name} `));
  server.stdin.write(`mrprobe aim ${name}\n`);
  return Object.fromEntries((await response).split(/\s+/).filter(s => s.includes('=')).map(s => {
    const [key, value] = s.split('='); return [key, key === 'player' ? value : Number(value)];
  }));
}
async function cast(spell, name = 'ADS_A') { await command(`ms cast as ${name} ${spell}`); }
const clients = [];
async function connect(name) {
  const client = mc.createClient({ host: '127.0.0.1', port, username: name, auth: 'offline', version: '1.21.11' });
  clients.push(client);
  client.on('error', error => console.error(name, error.message));
  client.on('position', p => {
    client.write('teleport_confirm', { teleportId: p.teleportId });
    client.write('position_look', { x: p.x, y: p.y, z: p.z, yaw: p.yaw, pitch: p.pitch,
      flags: { onGround: true, hasHorizontalCollision: false } });
    client.write('player_loaded', {});
  });
  client.on('update_health', p => { if (p.health <= 0) client.write('client_command', { actionId: 0 }); });
  await once(client, 'login'); await delay(600);
  return client;
}
function near(actual, expected) { assert.ok(Math.abs(actual - expected) < 1e-5, `${actual} != ${expected}`); }
function pass(name) { console.log('PASS ' + name); }

(async () => {
  try {
    await until(line => line.includes('Done ('), 90000);
    await command('difficulty peaceful'); // Keep idle protocol clients safe from ambient mobs.
    const a = await connect('ADS_A'); const b = await connect('ADS_B');
    for (const name of ['ADS_A', 'ADS_B']) {
      await command(`item replace entity ${name} weapon.mainhand with stick`);
      await command(`item replace entity ${name} hotbar.1 with blaze_rod`);
    }
    near((await snapshot()).speed, 0.1);
    a.write('use_item', { hand: 0, sequence: 1, rotation: { x: 0, y: 0 } }); await delay(700);
    near((await snapshot()).speed, 0.065); near((await snapshot()).multiplier, 0.5);
    a.write('use_item', { hand: 0, sequence: 2, rotation: { x: 0, y: 0 } }); await delay(700);
    near((await snapshot()).speed, 0.1); pass('native main-hand right-click toggles ADS through MagicSpells');

    await cast('ADS_Aim'); await command('tp ADS_A ~ ~ ~ 0 0'); // teleport deliberately cancels ADS
    await cast('ADS_Recoil'); near((await snapshot()).pitch, -2);
    await delay(500); await cast('ADS_Aim'); await cast('ADS_Recoil'); near((await snapshot()).pitch, -3);
    pass('real rotation: normal 2 degrees, aimed 1 degree');

    a.write('held_item_slot', { slotId: 1 }); await delay(300);
    near((await snapshot()).speed, 0.1); assert.equal((await snapshot()).states, 0);
    a.write('held_item_slot', { slotId: 0 }); await delay(300);
    await cast('ADS_Keep'); a.write('held_item_slot', { slotId: 1 }); await delay(300);
    near((await snapshot()).speed, 0.1); near((await snapshot()).multiplier, 1); assert.equal((await snapshot()).states, 1);
    a.write('held_item_slot', { slotId: 0 }); await delay(300); near((await snapshot()).speed, 0.065);
    a.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 3 }); await delay(300);
    near((await snapshot()).speed, 0.1); assert.equal((await snapshot()).states, 1);
    a.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 4 }); await delay(300);
    near((await snapshot()).speed, 0.065); await cast('ADS_Stop');
    await cast('ADS_Aim');
    a.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 5 }); await delay(300);
    assert.equal((await snapshot()).states, 0);
    a.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 6 }); await delay(300);
    pass('actual slot/F-key packets: cancellation and retained-state suspension/restoration');

    await cast('ADS_On'); await cast('ADS_On'); near((await snapshot()).speed, 0.065);
    await cast('ADS_Stop'); await cast('ADS_Disabled'); assert.equal((await snapshot()).states, 0);
    await cast('ADS_Denied'); assert.equal((await snapshot()).states, 0);
    await command('clear ADS_A cobblestone'); // Reset the reagent fixture when reusing a saved player.
    await cast('ADS_Cost'); assert.equal((await snapshot()).states, 0);
    await command('give ADS_A cobblestone 2'); await cast('ADS_Cost');
    near((await snapshot()).speed, 0.065); assert.equal((await snapshot()).cobblestone, 1);
    await cast('ADS_Cost'); near((await snapshot()).speed, 0.065); assert.equal((await snapshot()).cobblestone, 1);
    await cast('ADS_Stop'); pass('idempotent ON, disabled state, modifiers, missing reagents, costs and cooldown');

    await command('attribute ADS_A minecraft:movement_speed modifier add other:ads_test 0.2 add_multiplied_total');
    await cast('ADS_Aim'); near((await snapshot()).speed, 0.078);
    await cast('ADS_Reload'); near((await snapshot()).speed, 0.12);
    await command('attribute ADS_A minecraft:movement_speed modifier remove other:ads_test');
    await cast('ADS_Buff'); await cast('ADS_Aim'); await cast('ADS_Buff');
    near((await snapshot()).speed, 0.1); pass('external speed modifier survives ADS stop; reload and BuffEnd cleanup');

    await cast('ADS_Aim'); await cast('ADS_Keep', 'ADS_B');
    assert.equal((await snapshot()).states, 2); near((await snapshot('ADS_B')).speed, 0.065);
    await cast('ADS_Reload'); near((await snapshot()).speed, 0.1); near((await snapshot('ADS_B')).speed, 0.065);
    b.end(); await delay(500); assert.equal((await snapshot()).states, 0);
    await cast('ADS_Aim'); await command('kill ADS_A'); await delay(800);
    assert.equal((await snapshot()).states, 0); near((await snapshot()).speed, 0.1);
    await command('item replace entity ADS_A weapon.mainhand with stick');
    await cast('ADS_Aim'); await command('ms reload'); await delay(1500);
    assert.equal((await snapshot()).states, 0); near((await snapshot()).speed, 0.1);
    await cast('ADS_Aim'); near((await snapshot()).speed, 0.065); await cast('ADS_Stop');
    pass('two-player isolation, logout, death/respawn and MagicSpells reload/re-registration');

    await command('item replace entity ADS_A weapon.mainhand with diamond_hoe');
    await command('ms variable modify Paimon ADS_A =30');
    await command('ms variable modify reload ADS_A =0');
    await cast('ADS_PaimonAim');
    const beforeFire = await snapshot();
    await cast('Weapon_파이먼_Buff'); await delay(500);
    const firing = await snapshot();
    const shots = beforeFire.ammo - firing.ammo;
    assert.ok(shots > 0 && shots < 15, `Expected ammo consumption, got ${shots}`);
    near(beforeFire.pitch - firing.pitch, shots * 0.75); near(firing.speed, 0.065);
    await cast('Weapon_Reload_파이먼');
    const reload = await snapshot(); await delay(500);
    assert.equal((await snapshot()).ammo, reload.ammo); near((await snapshot()).speed, 0.1);
    await cast('Weapon_파이먼_Buff');
    pass('Paimon-shaped Buff -> Passive -> Beam -> guarded recoil: ammo per shot, ADS scaling and reload blocking');

    await command('item replace entity ADS_A weapon.mainhand with stick');
    await cast('ADS_Aim'); // Leave ADS active for the server's plugin shutdown cleanup.
    console.log('ALL ADS RUNTIME CHECKS PASSED');
  } catch (error) {
    console.error(error); process.exitCode = 1;
  } finally {
    const exit = once(server, 'exit'); server.stdin.write('stop\n');
    await Promise.race([exit, delay(15000)]);
    clients.forEach(c => { try { c.end(); } catch {} });
    if (server.exitCode === null) server.kill();
  }
})();
