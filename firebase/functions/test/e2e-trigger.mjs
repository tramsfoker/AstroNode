// Uçtan uca: emulator'da gerçek trigger çalışıyor mu? (functions + firestore emulator ile)
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, GeoPoint, Timestamp } from 'firebase-admin/firestore';
initializeApp({ projectId: 'demo-astronode-fn' });
const db = getFirestore();
await db.doc('measurements/e2e').set({ sqm_value: 21.2, bortle_class: 4, location: new GeoPoint(40.07, 29.22), geohash: 'sxk9q1xy',
  timestamp: Timestamp.now(), observer_uid: 'u', device_id: 'u', is_test: false, observing: { is_daytime: false } });
for (let i = 0; i < 40; i++) {
  const d = (await db.doc('measurements/e2e').get()).data();
  if (d.stats_applied) {
    const s = (await db.doc('region_stats/TR__Bursa__Osmangazi').get()).data();
    console.log('TRIGGER_OK', JSON.stringify(d.region), 'count=', s?.count);
    process.exit(0);
  }
  await new Promise((r) => setTimeout(r, 500));
}
console.log('TRIGGER_TIMEOUT'); process.exit(1);
