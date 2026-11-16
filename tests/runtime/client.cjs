// Headless protocol simulator, NOT a vanilla rendering or human-input test.
const mc = require('minecraft-protocol');
const fs = require('node:fs');
const client = mc.createClient({
  host: '127.0.0.1', port: Number(process.env.RECOIL_TEST_PORT || 25579),
  username: 'RecoilProbe', auth: 'offline', version: process.env.RECOIL_TEST_VERSION || '1.21.11'
});
let yaw = 0, pitch = 0, loaded = false, compensation = 0;
function log(event, data = {}) {
  const line = JSON.stringify({ time: Date.now(), event, ...data });
  fs.appendFileSync('packets.jsonl', line + '\n');
  console.log(line);
}
function look() {
  client.write('look', { yaw, pitch, flags: { onGround: true, hasHorizontalCollision: false } });
}
client.on('position', packet => {
  // The fixture expects only the initial absolute login position, not teleport tests.
  yaw = packet.yaw; pitch = packet.pitch;
  log('position', packet);
  client.write('teleport_confirm', { teleportId: packet.teleportId });
  client.write('position_look', { x: packet.x, y: packet.y, z: packet.z, yaw, pitch,
    flags: { onGround: true, hasHorizontalCollision: false } });
  if (!loaded) { loaded = true; client.write('player_loaded', {}); }
});
client.on('player_rotation', packet => {
  yaw = packet.relativeYaw ? yaw + packet.yaw : packet.yaw;
  pitch = Math.max(-90, Math.min(90, packet.relativePitch ? pitch + packet.pitch : packet.pitch));
  log('rotation', { ...packet, clientYaw: yaw, clientPitch: pitch });
  if (compensation) {
    pitch = Math.max(-90, Math.min(90, pitch + compensation));
    log('synthetic-input', { yaw, pitch, compensation });
  }
  look();
});
client.on('login', () => log('login'));
client.on('kick_disconnect', packet => log('kick', packet));
client.on('error', error => log('error', { message: error.message }));
client.on('end', reason => { log('end', { reason }); process.exit(); });
process.stdin.setEncoding('utf8');
process.stdin.on('data', line => {
  const [command, a, b] = line.trim().split(/\s+/);
  if (command === 'look' && Number.isFinite(Number(a)) && Number.isFinite(Number(b))) {
    yaw = Number(a); pitch = Math.max(-90, Math.min(90, Number(b))); look(); log('input', { yaw, pitch });
  }
  if (command === 'compensate' && Number.isFinite(Number(a))) {
    compensation = Number(a); log('compensation-setting', { compensation });
  }
  if (command === 'quit') client.end();
});
