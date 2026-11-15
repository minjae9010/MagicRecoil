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
  client.on('abilities', packet => { client.lastAbilities = packet; });
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
    a.write('held_item_slot', { slotId: 0 }); b.write('held_item_slot', { slotId: 0 }); await delay(200);
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

    await command('ms variable modify Paimon ADS_A =30');
    await command('ms variable modify reload ADS_A =0');
    await delay(300); assert.equal((await snapshot()).prepared, 1);
    const originalAbilities = { ...a.lastAbilities };
    a.write('arm_animation', { hand: 0 }); await delay(400);
    const scoped = await snapshot(); near(scoped.speed, 0.065); near(scoped.serverWalkSpeed, 0.1); near(scoped.baseSpeed, 0.1);
    near(a.lastAbilities.walkingSpeed, scoped.speed / (2 * 0.65 - 1));
    assert.equal(a.lastAbilities.flags, originalAbilities.flags);
    const beforeHold = await snapshot();
    a.write('use_item', { hand: 0, sequence: 10, rotation: { x: beforeHold.yaw, y: beforeHold.pitch } }); await delay(500);
    const duringHold = await snapshot();
    assert.equal(duringHold.using, 1); assert.equal(duringHold.firing, 1);
    assert.ok(duringHold.ammo < beforeHold.ammo - 1);
    near(beforeHold.pitch - duringHold.pitch, (beforeHold.ammo - duringHold.ammo) * 0.75);
    a.write('block_dig', { status: 5, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 11 }); await delay(200);
    const released = await snapshot(); await delay(400);
    assert.equal(released.using, 0); assert.equal(released.firing, 0); assert.equal((await snapshot()).ammo, released.ammo);
    a.write('arm_animation', { hand: 0 }); await delay(400);
    near((await snapshot()).speed, 0.1); near(a.lastAbilities.walkingSpeed, 0.1);
    assert.equal(a.lastAbilities.flags, originalAbilities.flags);
    pass('left-click ADS/FOV packet toggle, actual movement attributes unchanged, scoped fire and abilities restoration');
    a.write('use_item', { hand: 0, sequence: 12, rotation: { x: 0, y: 0 } }); await delay(300);
    assert.equal((await snapshot()).firing, 1);
    await cast('Weapon_Reload_파이먼'); const reloadHold = await snapshot();
    await command('ms variable modify reload ADS_A =0'); await delay(300);
    assert.equal((await snapshot()).ammo, reloadHold.ammo); // Latched until release, even after reload clears.
    a.write('block_dig', { status: 5, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 13 }); await delay(200);
    a.write('use_item', { hand: 0, sequence: 14, rotation: { x: 0, y: 0 } }); await delay(300);
    assert.ok((await snapshot()).ammo < reloadHold.ammo);
    a.write('held_item_slot', { slotId: 1 }); await delay(200);
    assert.equal((await snapshot()).firing, 0);
    a.write('held_item_slot', { slotId: 0 }); await delay(200);
    assert.equal((await snapshot()).firing, 0);
    pass('genuine right-button item use: repeated fire, release stop, reload latch, item switch and unchanged ammo/recoil path');

    await command('item replace entity ADS_A hotbar.1 with blaze_rod'); // Death earlier drops the original fixture.
    a.write('held_item_slot', { slotId: 1 }); await delay(200);
    await command('ms variable modify Paimon ADS_A =30');
    const repeatBefore = await snapshot(); assert.equal(repeatBefore.prepared, 0);
    for (let i = 0; i < 4; i++) {
      a.write('use_item', { hand: 0, sequence: 30 + i, rotation: { x: 0, y: 0 } }); await delay(200);
    }
    assert.ok((await snapshot()).ammo < repeatBefore.ammo);
    await delay(500); const repeatedRelease = await snapshot(); assert.equal(repeatedRelease.firing, 0);
    await delay(300); assert.equal((await snapshot()).ammo, repeatedRelease.ammo);
    pass('optional legacy repeated-click mode: unmodified item, continuous click pulses and explicit six-tick release timeout');

    a.write('held_item_slot', { slotId: 0 }); await delay(200);
    a.write('use_item', { hand: 0, sequence: 40, rotation: { x: 0, y: 0 } }); await delay(200);
    assert.equal((await snapshot()).firing, 1);
    await cast('Hold_Eventless_Stop'); const directStop = await snapshot(); await delay(300);
    assert.equal((await snapshot()).ammo, directStop.ammo); assert.equal((await snapshot()).firing, 0);
    a.write('block_dig', { status: 5, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 41 }); await delay(100);
    a.write('use_item', { hand: 0, sequence: 42, rotation: { x: 0, y: 0 } }); await delay(200);
    assert.equal((await snapshot()).firing, 1);
    await command('ms reload'); await delay(1200); const msReloaded = await snapshot();
    assert.equal(msReloaded.firing, 0); assert.equal(msReloaded.using, 0);
    await delay(300); assert.equal((await snapshot()).ammo, msReloaded.ammo);
    a.write('use_item', { hand: 0, sequence: 43, rotation: { x: 0, y: 0 } }); await delay(200);
    assert.equal((await snapshot()).firing, 1);
    a.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 44 }); await delay(200);
    assert.equal((await snapshot()).firing, 0);
    a.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 45 }); await delay(200);
    a.write('use_item', { hand: 0, sequence: 46, rotation: { x: 0, y: 0 } }); await delay(200);
    assert.equal((await snapshot()).firing, 1);
    await command('kill ADS_A'); await delay(800);
    assert.equal((await snapshot()).firing, 0);
    pass('AutoFire eventless stop, ms reload, F swap and death terminate active native firing');

    await command('item replace entity ADS_A weapon.mainhand with diamond_hoe');
    await cast('Hold_Aim');
    a.write('use_item', { hand: 0, sequence: 47, rotation: { x: 0, y: 0 } }); await delay(200);
    assert.equal((await snapshot()).firing, 1); // Leave ADS + firing active for normal shutdown cleanup.
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
