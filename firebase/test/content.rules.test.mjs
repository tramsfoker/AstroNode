// Haber, istatistik, organizasyon başvurusu ve rapor kuralları
import { test, before, after, beforeEach, describe } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, collection, query, where, getDocs, serverTimestamp, Timestamp } from 'firebase/firestore';

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-astronode-content',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const user = (uid = 'alice') => env.authenticatedContext(uid).firestore();
const organizer = (uid = 'olga', org = 'org1') => env.authenticatedContext(uid, { organizer: true, org_id: org }).firestore();
const admin = () => env.authenticatedContext('root', { admin: true, organizer: true }).firestore();
const seed = (fn) => env.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()));

const post = (uid, extra = {}) => ({
  type: 'post', title: 'Gözlem gecesi', body: 'Cumartesi Uludağ', author_uid: uid, author_name: 'X',
  org_id: 'org1', org_name: 'Baak', published_at: serverTimestamp(), pinned: false, lang: 'tr', ...extra,
});

describe('news', () => {
  test('normal kullanıcı duyuru yazamaz', async () => {
    await assertFails(setDoc(doc(user(), 'news/p1'), post('alice')));
  });
  test('organizatör kendi organizasyonu adına yazar, başkası adına yazamaz', async () => {
    await assertSucceeds(setDoc(doc(organizer(), 'news/p1'), post('olga')));
    await assertFails(setDoc(doc(organizer(), 'news/p2'), post('olga', { org_id: 'org2' })));
  });
  test('organizatör sabitleyemez, admin sabitleyebilir', async () => {
    await assertFails(setDoc(doc(organizer(), 'news/p1'), post('olga', { pinned: true })));
    await assertSucceeds(setDoc(doc(admin(), 'news/p2'), post('root', { pinned: true })));
  });
  test('javascript: bağlantısı reddedilir', async () => {
    await assertFails(setDoc(doc(organizer(), 'news/p1'), post('olga', { link: 'javascript:alert(1)' })));
  });
  test('RSS haberi istemciden yazılamaz ama okunur', async () => {
    await assertFails(setDoc(doc(admin(), 'news/rss_1'), { type: 'rss', title: 'x' }));
    await seed((db) => setDoc(doc(db, 'news/rss_1'), { type: 'rss', title: 'x', published_at: Timestamp.now() }));
    await assertSucceeds(getDoc(doc(user(), 'news/rss_1')));
  });
  test('yazar kendi duyurusunu siler, başkası silemez', async () => {
    await setDoc(doc(organizer(), 'news/p1'), post('olga'));
    await assertFails(deleteDoc(doc(user(), 'news/p1')));
    await assertSucceeds(deleteDoc(doc(organizer(), 'news/p1')));
  });
});

describe('stats', () => {
  test('istatistikler okunur, yazılamaz', async () => {
    await seed((db) => setDoc(doc(db, 'region_stats/TR__Bursa'), { count: 3 }));
    await assertSucceeds(getDoc(doc(user(), 'region_stats/TR__Bursa')));
    await assertFails(setDoc(doc(admin(), 'region_stats/TR__Bursa'), { count: 999 }));
    await assertFails(setDoc(doc(user(), 'sky_cells/sxk9a'), { count: 1 }));
  });
  test('etkinlik sahibi sunucu sayaçlarını değiştiremez', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'sessions/s1'), { name: 'S', created_by: 'alice', participant_ids: ['alice'], participant_count: 1,
        is_Active: true, status: 'active', type: 'public', code: 'ABC234', date: 1, measurement_count: 2 });
    });
    await assertFails(updateDoc(doc(user(), 'sessions/s1'), { measurement_count: 500 }));
    await assertSucceeds(updateDoc(doc(user(), 'sessions/s1'), { name: 'Yeni ad' }));
  });
});

const orgReq = (uid, extra = {}) => ({
  requester_uid: uid, requester_name: 'Ali', org_name: 'Ankara Astronomi', description: 'Kulüp', city: 'Ankara',
  website: null, contact_email: 'ali@example.org', status: 'pending', created_at: serverTimestamp(), ...extra,
});

describe('organization_requests', () => {
  test('kullanıcı başvuru yapar, onaylanmış olarak oluşturamaz', async () => {
    await assertSucceeds(setDoc(doc(user(), 'organization_requests/r1'), orgReq('alice')));
    await assertFails(setDoc(doc(user(), 'organization_requests/r2'), orgReq('alice', { status: 'approved' })));
    await assertFails(setDoc(doc(user(), 'organization_requests/r3'), orgReq('bob')));
    await assertFails(setDoc(doc(user(), 'organization_requests/r4'), orgReq('alice', { contact_email: 'yok' })));
  });
  test('başkasının başvurusunu göremez; admin hepsini listeler', async () => {
    await setDoc(doc(user(), 'organization_requests/r1'), orgReq('alice'));
    await assertFails(getDoc(doc(user('bob'), 'organization_requests/r1')));
    await assertSucceeds(getDocs(query(collection(user(), 'organization_requests'), where('requester_uid', '==', 'alice'))));
    await assertSucceeds(getDocs(collection(admin(), 'organization_requests')));
  });
  test('organizasyon belgesi istemciden oluşturulamaz', async () => {
    await assertFails(setDoc(doc(admin(), 'organizations/o1'), { name: 'x' }));
  });
});

describe('reports', () => {
  const report = (uid, extra = {}) => ({ reporter_uid: uid, target_type: 'measurement', target_id: 'm1', reason: 'spam',
    details: null, status: 'open', created_at: serverTimestamp(), ...extra });
  test('kullanıcı rapor oluşturur, okuyamaz; admin okur', async () => {
    await assertSucceeds(setDoc(doc(user(), 'reports/x1'), report('alice')));
    await assertFails(getDoc(doc(user(), 'reports/x1')));
    await assertSucceeds(getDoc(doc(admin(), 'reports/x1')));
  });
  test('geçersiz rapor reddedilir', async () => {
    await assertFails(setDoc(doc(user(), 'reports/x2'), report('alice', { reason: 'nope' })));
    await assertFails(setDoc(doc(user(), 'reports/x3'), report('bob')));
    await assertFails(setDoc(doc(user(), 'reports/x4'), report('alice', { status: 'resolved' })));
  });
});
