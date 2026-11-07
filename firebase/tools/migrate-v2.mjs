// v1.1 → v1.2 tek seferlik veri göçü. Önce deneme modunda çalıştır, çıktıyı incele, sonra --apply ekle.
//   node tools/migrate-v2.mjs            (sadece raporlar, hiçbir şey yazmaz)
//   node tools/migrate-v2.mjs --apply    (değişiklikleri uygular)
//
// Yaptıkları:
//  1) measurements: eksik observer_uid'i device_id'den, eksik geohash'i konumdan doldurur
//  2) sessions: her etkinlik için session_codes/{kod} belgesi oluşturur (çakışmada yeni kod üretir),
//     eksik type → public, participant_count'u listeyle eşitler
//  3) users: istemcinin kendine atadığı yetkili rolleri raporlar (yetki artık custom claims'ten gelir)
import { db, APPLY, log } from './_admin.mjs';
import { geohashForLocation } from 'geofire-common';

const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const newCode = () => Array.from({ length: 6 }, () => CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)]).join('');

async function commitInChunks(ops) {
  for (let i = 0; i < ops.length; i += 400) {
    const batch = db.batch();
    ops.slice(i, i + 400).forEach((op) => op(batch));
    if (APPLY) await batch.commit();
  }
}

// 1) Ölçümler
let mFixed = 0;
const mOps = [];
for (const doc of (await db.collection('measurements').get()).docs) {
  const d = doc.data();
  const patch = {};
  if (!d.observer_uid && d.device_id) patch.observer_uid = d.device_id;
  if (!d.geohash && d.location) patch.geohash = geohashForLocation([d.location.latitude, d.location.longitude]).slice(0, 7);
  if (Object.keys(patch).length) {
    mFixed++;
    log(`ölçüm ${doc.id}:`, patch);
    mOps.push((b) => b.update(doc.ref, patch));
  }
}
await commitInChunks(mOps);

// 2) Etkinlikler + kodlar
let sFixed = 0;
const usedCodes = new Set((await db.collection('session_codes').get()).docs.map((d) => d.id));
for (const doc of (await db.collection('sessions').get()).docs) {
  const d = doc.data();
  const patch = {};
  let code = d.code;
  const codeDoc = code ? await db.doc(`session_codes/${code}`).get() : null;
  const codeOk = codeDoc?.exists && codeDoc.data().session_id === doc.id;
  if (!codeOk) {
    if (!code || !/^[A-HJ-NP-Z2-9]{6}$/.test(code) || usedCodes.has(code)) {
      do { code = newCode(); } while (usedCodes.has(code));
      patch.code = code;
    }
    usedCodes.add(code);
  }
  if (!d.type) patch.type = 'public';
  const ids = Array.isArray(d.participant_ids) ? d.participant_ids : [];
  if (d.participant_count !== ids.length) patch.participant_count = ids.length;
  if (!codeOk || Object.keys(patch).length) {
    sFixed++;
    log(`etkinlik ${doc.id} (${d.name}):`, patch, codeOk ? '' : `→ kod belgesi ${code}`);
    if (APPLY) {
      const batch = db.batch();
      if (Object.keys(patch).length) batch.update(doc.ref, patch);
      if (!codeOk) batch.set(db.doc(`session_codes/${code}`), { session_id: doc.id, created_by: d.created_by || '' });
      await batch.commit();
    }
  }
}

// 3) Şüpheli roller
const suspicious = (await db.collection('users').get()).docs.filter((d) => (d.data().role || 'observer') !== 'observer');
suspicious.forEach((d) => console.log(`⚠ kullanıcı ${d.id} role=${d.data().role} — gerçekten yetkiliyse: node tools/set-admin.mjs ${d.id} admin`));

console.log(`\nÖzet: ${mFixed} ölçüm, ${sFixed} etkinlik güncellenecek; ${suspicious.length} şüpheli rol.`);
if (!APPLY) console.log('Hiçbir şey yazılmadı. Uygulamak için --apply ekleyin.');
