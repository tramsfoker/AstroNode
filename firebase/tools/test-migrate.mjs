// Göç script'ini emulator üzerinde doğrular: `npm run test:tools`
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';
import { db } from './_admin.mjs';
import { GeoPoint } from 'firebase-admin/firestore';

await db.doc('measurements/old1').set({ sqm_value: 19, bortle_class: 5, device_id: 'u1', location: new GeoPoint(40.1, 29.0) });
await db.doc('sessions/s1').set({ name: 'Eski', code: 'ABC234', created_by: 'u1', participant_ids: ['u1', 'u2'], participant_count: 1, is_Active: true });
await db.doc('sessions/s2').set({ name: 'Çakışan', code: 'ABC234', created_by: 'u2', participant_ids: ['u2'], participant_count: 1, is_Active: true, type: 'private' });
await db.doc('users/u3').set({ role: 'super_admin' });

const run = (...args) => execFileSync('node', ['tools/migrate-v2.mjs', ...args], { encoding: 'utf8', env: process.env });
run();
assert.equal((await db.doc('measurements/old1').get()).data().observer_uid, undefined, 'deneme modu yazmamalı');
console.log(run('--apply'));

const m = (await db.doc('measurements/old1').get()).data();
assert.equal(m.observer_uid, 'u1');
assert.match(m.geohash, /^[0-9a-z]{7}$/);
const s1 = (await db.doc('sessions/s1').get()).data();
const s2 = (await db.doc('sessions/s2').get()).data();
assert.equal(s1.type, 'public');
assert.equal(s1.participant_count, 2);
assert.notEqual(s1.code, s2.code, 'çakışan kodlar ayrışmalı');
for (const [id, s] of [['s1', s1], ['s2', s2]]) {
  assert.equal((await db.doc(`session_codes/${s.code}`).get()).data().session_id, id);
}
// Tekrar çalıştırmak bir şey değiştirmemeli (idempotent)
assert.match(run('--apply'), /0 ölçüm, 0 etkinlik/);
console.log('✔ migrate-v2 emulator testi geçti');
