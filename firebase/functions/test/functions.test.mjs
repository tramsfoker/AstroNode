// Cloud Functions mantık testleri — Firestore emulator üzerinde (npm run test:functions)
import { test, before, describe } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, GeoPoint, Timestamp } from 'firebase-admin/firestore';
import { applyMeasurement, revertMeasurement, rebuildAllStats } from '../src/stats.js';
import { parseFeed, fetchAllFeeds, newsIdFor } from '../src/feeds.js';
import * as admin from '../src/admin.js';

initializeApp({ projectId: 'demo-astronode-fn' });
const db = getFirestore();

async function clear() {
  for (const c of ['measurements', 'region_stats', 'sky_cells', 'sessions', 'news', 'organization_requests', 'organizations', 'users', 'reports', 'feed_sources', 'session_codes']) {
    const docs = (await db.collection(c).get()).docs;
    await Promise.all(docs.map((d) => d.ref.delete()));
  }
}

const m = (extra = {}) => ({
  sqm_value: 20, bortle_class: 4, location: new GeoPoint(40.214, 28.98), geohash: 'sxk9mabc',
  timestamp: Timestamp.fromDate(new Date('2026-11-06T20:00:00Z')), observer_uid: 'u1', device_id: 'u1',
  is_test: false, observing: { is_daytime: false }, ...extra,
});

describe('istatistikler', () => {
  before(clear);

  test('ölçüm il/ilçe/hücre istatistiklerini oluşturur, bölgeyi yazar', async () => {
    await db.doc('sessions/s1').set({ name: 'S', created_by: 'u1' });
    await db.doc('measurements/a').set(m({ session_id: 's1' }));
    await db.doc('measurements/b').set(m({ sqm_value: 21 }));
    assert.equal(await applyMeasurement(db, 'a'), 'applied');
    assert.equal(await applyMeasurement(db, 'b'), 'applied');

    const ma = (await db.doc('measurements/a').get()).data();
    assert.deepEqual(ma.region, { country: 'TR', admin1: 'Bursa', admin2: 'Nilüfer' });

    const prov = (await db.doc('region_stats/TR__Bursa').get()).data();
    assert.equal(prov.count, 2); assert.equal(prov.sum, 41); assert.equal(prov.best, 21); assert.equal(prov.level, 1);
    assert.equal(prov.months['2026-11'].count, 2);
    const dist = (await db.doc('region_stats/TR__Bursa__Nilüfer').get()).data();
    assert.equal(dist.count, 2); assert.equal(dist.level, 2);
    const cell = (await db.doc('sky_cells/sxk9m').get()).data();
    assert.equal(cell.count, 2);
    assert.ok(Math.abs(cell.sum_lat / cell.count - 40.214) < 1e-9);
    const s = (await db.doc('sessions/s1').get()).data();
    assert.equal(s.measurement_count, 1); assert.equal(s.best_mpsas, 20);
  });

  test('aynı ölçüm iki kez işlenmez (idempotent)', async () => {
    assert.equal(await applyMeasurement(db, 'a'), 'skipped');
    assert.equal((await db.doc('region_stats/TR__Bursa').get()).data().count, 2);
  });

  test('test ve gündüz ölçümleri istatistiğe girmez ama bölge yazılır', async () => {
    await db.doc('measurements/t').set(m({ is_test: true, sqm_value: 5 }));
    await db.doc('measurements/d').set(m({ observing: { is_daytime: true } }));
    await applyMeasurement(db, 't'); await applyMeasurement(db, 'd');
    assert.equal((await db.doc('region_stats/TR__Bursa').get()).data().count, 2);
    assert.equal((await db.doc('measurements/t').get()).data().region.admin1, 'Bursa');
  });

  test('yurt dışı ölçüm sadece hücreye girer', async () => {
    await db.doc('measurements/x').set(m({ location: new GeoPoint(37.98, 23.72), geohash: 'swbb5ftz' }));
    await applyMeasurement(db, 'x');
    assert.equal((await db.doc('measurements/x').get()).data().region, null);
    assert.equal((await db.doc('sky_cells/swbb5').get()).data().count, 1);
  });

  test('silme sayaçları geri alır', async () => {
    const data = (await db.doc('measurements/b').get()).data();
    await db.doc('measurements/b').delete();
    assert.equal(await revertMeasurement(db, data), 'reverted');
    const prov = (await db.doc('region_stats/TR__Bursa').get()).data();
    assert.equal(prov.count, 1); assert.equal(prov.sum, 20);
  });

  test('gece yeniden hesaplama doğru sonucu ve en iyi değeri üretir, boş belgeleri siler', async () => {
    await db.doc('region_stats/TR__Hayali').set({ count: 9 });
    await db.doc('measurements/old').set(m({ location: new GeoPoint(39.92, 32.854), geohash: 'sxk3xyz1' }));
    const r = await rebuildAllStats(db, { pageSize: 2 });
    assert.ok(r.measurements >= 5);
    const prov = (await db.doc('region_stats/TR__Bursa').get()).data();
    assert.equal(prov.count, 1); assert.equal(prov.best, 20); // silinen 21 artık en iyi değil
    assert.equal((await db.doc('region_stats/TR__Ankara__Çankaya').get()).data().count, 1);
    assert.equal((await db.doc('region_stats/TR__Hayali').get()).exists, false);
    assert.equal((await db.doc('sessions/s1').get()).data().measurement_count, 1);
  });
});

describe('haber akışı', () => {
  before(clear);
  const rss = readFileSync(new URL('./fixtures/rss.xml', import.meta.url), 'utf8');
  const atom = readFileSync(new URL('./fixtures/atom.xml', import.meta.url), 'utf8');

  test('RSS ayrıştırılır: HTML temizlenir, görsel bulunur, kötü bağlantı atılır', () => {
    const items = parseFeed(rss);
    assert.equal(items.length, 2);
    assert.equal(items[0].title, 'Galaxy & Nebula');
    assert.equal(items[0].summary, 'Beautiful image of space');
    assert.equal(items[0].image_url, 'https://example.org/a.jpg');
    assert.equal(items[1].image_url, 'https://example.org/b.png');
  });

  test('Atom ayrıştırılır', () => {
    const items = parseFeed(atom);
    assert.equal(items.length, 1);
    assert.equal(items[0].link, 'https://example.org/atom1');
  });

  test('kaynaklar çekilir, hata veren kaynak diğerlerini durdurmaz, tekrar çekmek çoğaltmaz', async () => {
    await db.doc('feed_sources/ok').set({ name: 'OK', url: 'https://ok', enabled: true });
    await db.doc('feed_sources/bad').set({ name: 'Bad', url: 'https://bad', enabled: true });
    await db.doc('feed_sources/off').set({ name: 'Off', url: 'https://off', enabled: false });
    const fetcher = async (url) => { if (url === 'https://bad') throw new Error('timeout'); return rss; };
    const now = new Date('2026-11-07T00:00:00Z');
    const rep = await fetchAllFeeds(db, { fetcher, now });
    assert.deepEqual(rep.map((r) => r.source).sort(), ['bad', 'ok']);
    assert.ok(rep.find((r) => r.source === 'bad').error);
    await fetchAllFeeds(db, { fetcher, now });
    const news = (await db.collection('news').get()).docs;
    assert.equal(news.length, 2);
    assert.equal((await db.doc(`news/${newsIdFor('https://example.org/a')}`).get()).data().source_name, 'OK');
  });

  test('60 günden eski RSS haberleri silinir, duyurular kalır', async () => {
    await db.doc('news/oldrss').set({ type: 'rss', published_at: Timestamp.fromDate(new Date('2026-01-01')) });
    await db.doc('news/oldpost').set({ type: 'post', published_at: Timestamp.fromDate(new Date('2026-01-01')) });
    await fetchAllFeeds(db, { fetcher: async () => rss, now: new Date('2026-11-07T00:00:00Z') });
    assert.equal((await db.doc('news/oldrss').get()).exists, false);
    assert.equal((await db.doc('news/oldpost').get()).exists, true);
  });
});

describe('admin işlemleri', () => {
  before(clear);
  const claims = new Map();
  const disabled = new Set();
  const authAdmin = {
    setCustomUserClaims: async (uid, c) => { claims.set(uid, c); },
    getUser: async (uid) => ({ uid, customClaims: claims.get(uid) ?? {}, metadata: {} }),
    updateUser: async (uid, p) => { if (p.disabled) disabled.add(uid); },
    revokeRefreshTokens: async () => {},
  };
  const ctx = { db, authAdmin };
  const adminCaller = { uid: 'root', token: { admin: true } };
  const userCaller = { uid: 'u1', token: {} };

  test('admin olmayan çağıramaz', async () => {
    await assert.rejects(admin.setUserRole(ctx, userCaller, { uid: 'u1', role: 'admin' }), { code: 'permission-denied' });
    await assert.rejects(admin.setUserRole(ctx, null, { uid: 'u1', role: 'admin' }), { code: 'unauthenticated' });
  });

  test('rol atama claims ve profil rolünü günceller', async () => {
    await admin.setUserRole(ctx, adminCaller, { uid: 'u2', role: 'organizer', orgId: 'o9' });
    assert.deepEqual(claims.get('u2'), { organizer: true, org_id: 'o9' });
    assert.equal((await db.doc('users/u2').get()).data().role, 'organizer');
    await assert.rejects(admin.setUserRole(ctx, adminCaller, { uid: 'root', role: 'observer' }), { code: 'failed-precondition' });
  });

  test('organizasyon başvurusu onaylanır: organizasyon + claims', async () => {
    await db.doc('organization_requests/r1').set({ requester_uid: 'u3', org_name: 'Ankara Astro', status: 'pending', contact_email: 'a@b.co' });
    const res = await admin.reviewOrganizationRequest(ctx, adminCaller, { requestId: 'r1', approve: true });
    assert.equal(res.status, 'approved');
    assert.equal((await db.doc(`organizations/${res.organizationId}`).get()).data().name, 'Ankara Astro');
    assert.deepEqual(claims.get('u3'), { organizer: true, org_id: res.organizationId });
    await assert.rejects(admin.reviewOrganizationRequest(ctx, adminCaller, { requestId: 'r1', approve: true }), { code: 'failed-precondition' });
  });

  test('rapor: içerik silinir ve kullanıcı engellenir', async () => {
    await db.doc('measurements/spam').set({ observer_uid: 'bad', sqm_value: 25 });
    await db.doc('reports/rp1').set({ target_type: 'measurement', target_id: 'spam', status: 'open' });
    await admin.resolveReport(ctx, adminCaller, { reportId: 'rp1', action: 'ban_user' });
    assert.equal((await db.doc('measurements/spam').get()).exists, false);
    assert.ok(disabled.has('bad'));
    assert.equal((await db.doc('reports/rp1').get()).data().status, 'resolved');
  });

  test('hesap silinince tüm verisi temizlenir', async () => {
    await db.doc('users/gone').set({ display_name: 'x' });
    await db.doc('measurements/g1').set({ observer_uid: 'gone' });
    await db.doc('measurements/g2').set({ device_id: 'gone' });
    await db.doc('sessions/gs').set({ created_by: 'gone', code: 'ZZZ222' });
    await db.doc('session_codes/ZZZ222').set({ session_id: 'gs' });
    await db.doc('sessions/other').set({ created_by: 'x', participant_ids: ['x', 'gone'], participant_count: 2 });
    await admin.deleteUserData(db, 'gone');
    for (const p of ['users/gone', 'measurements/g1', 'measurements/g2', 'sessions/gs', 'session_codes/ZZZ222']) {
      assert.equal((await db.doc(p).get()).exists, false, p);
    }
    const other = (await db.doc('sessions/other').get()).data();
    assert.deepEqual(other.participant_ids, ['x']); assert.equal(other.participant_count, 1);
  });
});
