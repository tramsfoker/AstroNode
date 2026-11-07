// AstroNode Firestore kural testleri — emulator üzerinde çalışır: `npm test`
import { test, before, after, beforeEach, describe } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing';
import {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, query, where, orderBy, getDocs,
  writeBatch, serverTimestamp, Timestamp, GeoPoint, arrayUnion,
} from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-astronode-core",
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const alice = () => env.authenticatedContext('alice').firestore();
const bob = () => env.authenticatedContext('bob').firestore();
const admin = () => env.authenticatedContext('root', { admin: true }).firestore();
const anon = () => env.unauthenticatedContext().firestore();

/** Kurallar dışında veri hazırlamak için */
async function seed(fn) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
}

function newUser(overrides = {}) {
  return {
    display_name: 'Alice', role: 'observer', organization_id: null, organization_name: null,
    total_measurements: 0, total_sessions: 0, best_mpsas: null,
    created_at: Date.now(), last_active_at: Date.now(),
    settings: { theme: 'auto', unit: 'mpsas' },
    ...overrides,
  };
}

function measurement(uid, overrides = {}) {
  return {
    sqm_value: 19.5, bortle_class: 4, location: new GeoPoint(40.1, 29.0), geohash: 'sxk9abc',
    orientation: { enabled: false }, timestamp: Timestamp.now(), created_at: serverTimestamp(),
    device_id: uid, observer_uid: uid, observer_name: 'Alice', is_test: false,
    observing: { is_daytime: false },
    ...overrides,
  };
}

function session(uid, code, overrides = {}) {
  return {
    name: 'Uludağ Gecesi', description: null, date: Date.now(), organizer_name: 'Alice',
    participant_count: 1, created_by: uid, is_Active: true, status: 'active', type: 'public',
    participant_ids: [uid], code, ...overrides,
  };
}

async function createSessionAs(db, uid, id, code, overrides = {}) {
  const batch = writeBatch(db);
  batch.set(doc(db, 'sessions', id), session(uid, code, overrides));
  batch.set(doc(db, 'session_codes', code), { session_id: id, created_by: uid });
  return batch.commit();
}

// ---------------------------------------------------------------------------
describe('users', () => {
  test('kullanıcı kendi profilini observer olarak oluşturabilir', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'users/alice'), newUser()));
  });

  test('kullanıcı kendini super_admin olarak oluşturamaz', async () => {
    await assertFails(setDoc(doc(alice(), 'users/alice'), newUser({ role: 'super_admin' })));
  });

  test('kullanıcı mevcut profilinde rolünü yükseltemez', async () => {
    await seed((db) => setDoc(doc(db, 'users/alice'), newUser()));
    await assertFails(updateDoc(doc(alice(), 'users/alice'), { role: 'super_admin' }));
  });

  test('başkasının profilini okuyamaz/yazamaz', async () => {
    await seed((db) => setDoc(doc(db, 'users/alice'), newUser()));
    await assertFails(getDoc(doc(bob(), 'users/alice')));
    await assertFails(updateDoc(doc(bob(), 'users/alice'), { display_name: 'hacked' }));
  });

  test('sayaç sadece 1 artırılabilir', async () => {
    await seed((db) => setDoc(doc(db, 'users/alice'), newUser({ total_measurements: 3 })));
    await assertSucceeds(updateDoc(doc(alice(), 'users/alice'), { total_measurements: 4 }));
    await assertFails(updateDoc(doc(alice(), 'users/alice'), { total_measurements: 1000 }));
  });

  test('ad ve ayar güncellenebilir, hesap silinebilir', async () => {
    await seed((db) => setDoc(doc(db, 'users/alice'), newUser()));
    await assertSucceeds(setDoc(doc(alice(), 'users/alice'),
      { display_name: 'Ali', settings: { theme: 'dark', unit: 'mpsas' } }, { merge: true }));
    await assertSucceeds(deleteDoc(doc(alice(), 'users/alice')));
  });

  test('oturumsuz erişim yok', async () => {
    await assertFails(getDoc(doc(anon(), 'users/alice')));
  });
});

// ---------------------------------------------------------------------------
describe('measurements', () => {
  test('geçerli ölçüm kaydedilir', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'measurements/m1'), measurement('alice')));
  });

  test('başkası adına ölçüm yazılamaz', async () => {
    await assertFails(setDoc(doc(alice(), 'measurements/m1'), measurement('bob')));
  });

  test('aralık dışı ve gerçek dışı değerler reddedilir', async () => {
    await assertFails(setDoc(doc(alice(), 'measurements/m1'), measurement('alice', { sqm_value: 99 })));
    await assertFails(setDoc(doc(alice(), 'measurements/m2'), measurement('alice', { sqm_value: 3 })));
    await assertFails(setDoc(doc(alice(), 'measurements/m3'), measurement('alice', { bortle_class: 12 })));
    await assertFails(setDoc(doc(alice(), 'measurements/m4'), measurement('alice', { sqm_value: '19' })));
  });

  test('gündüz/test ölçümü düşük değerle kaydedilebilir', async () => {
    await assertSucceeds(setDoc(doc(alice(), 'measurements/m1'),
      measurement('alice', { sqm_value: 3.2, is_test: true })));
  });

  test('gelecek tarihli veya istemci created_at reddedilir', async () => {
    const future = Timestamp.fromMillis(Date.now() + 60 * 60 * 1000);
    await assertFails(setDoc(doc(alice(), 'measurements/m1'), measurement('alice', { timestamp: future })));
    await assertFails(setDoc(doc(alice(), 'measurements/m2'), measurement('alice', { created_at: Timestamp.now() })));
  });

  test('offline biriken 1 gün önceki ölçüm kabul edilir', async () => {
    const yesterday = Timestamp.fromMillis(Date.now() - 24 * 60 * 60 * 1000);
    await assertSucceeds(setDoc(doc(alice(), 'measurements/m1'), measurement('alice', { timestamp: yesterday })));
  });

  test('bilinmeyen alan eklenemez', async () => {
    await assertFails(setDoc(doc(alice(), 'measurements/m1'), measurement('alice', { verified: true })));
  });

  test('katılmadığı etkinliğe ölçüm yazılamaz', async () => {
    await seed((db) => setDoc(doc(db, 'sessions/s1'), session('bob', 'ABCDEF')));
    await assertFails(setDoc(doc(alice(), 'measurements/m1'),
      measurement('alice', { session_id: 's1', session_name: 'x' })));
  });

  test('katıldığı etkinliğe ölçüm yazılabilir', async () => {
    await seed((db) => setDoc(doc(db, 'sessions/s1'),
      session('bob', 'ABCDEF', { participant_ids: ['bob', 'alice'], participant_count: 2 })));
    await assertSucceeds(setDoc(doc(alice(), 'measurements/m1'),
      measurement('alice', { session_id: 's1', session_name: 'x' })));
  });

  test('oturumlu herkes okur, oturumsuz okuyamaz', async () => {
    await seed((db) => setDoc(doc(db, 'measurements/m1'), { ...measurement('alice'), created_at: Timestamp.now() }));
    await assertSucceeds(getDoc(doc(bob(), 'measurements/m1')));
    await assertFails(getDoc(doc(anon(), 'measurements/m1')));
  });

  test('sadece sahibi silebilir (eski device_id alanı dahil)', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'measurements/m1'), { ...measurement('alice'), created_at: Timestamp.now() });
      await setDoc(doc(db, 'measurements/legacy'), { sqm_value: 18, bortle_class: 5, device_id: 'alice' });
    });
    await assertFails(deleteDoc(doc(bob(), 'measurements/m1')));
    await assertSucceeds(deleteDoc(doc(alice(), 'measurements/m1')));
    await assertSucceeds(deleteDoc(doc(alice(), 'measurements/legacy')));
  });

  test('etkinlik organizatörü kendi etkinliğindeki ölçümü silebilir, admin her şeyi', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'sessions/s1'), session('bob', 'ABCDEF'));
      await setDoc(doc(db, 'measurements/m1'), { ...measurement('alice'), session_id: 's1', created_at: Timestamp.now() });
      await setDoc(doc(db, 'measurements/m2'), { ...measurement('alice'), created_at: Timestamp.now() });
    });
    await assertSucceeds(deleteDoc(doc(bob(), 'measurements/m1')));
    await assertSucceeds(deleteDoc(doc(admin(), 'measurements/m2')));
  });

  test('sahibi değeri değiştiremez, sadece not ve test işareti', async () => {
    await seed((db) => setDoc(doc(db, 'measurements/m1'), { ...measurement('alice'), created_at: Timestamp.now() }));
    await assertFails(updateDoc(doc(alice(), 'measurements/m1'), { sqm_value: 22 }));
    await assertSucceeds(updateDoc(doc(alice(), 'measurements/m1'), { note: 'bulutlu', is_test: true }));
    await assertFails(updateDoc(doc(alice(), 'measurements/m1'), { is_test: false }));
  });
});

// ---------------------------------------------------------------------------
describe('sessions & session_codes', () => {
  test('etkinlik + kod birlikte oluşturulur', async () => {
    await assertSucceeds(createSessionAs(alice(), 'alice', 's1', 'ABC234'));
  });

  test('kod belgesi olmadan etkinlik oluşturulamaz', async () => {
    await assertFails(setDoc(doc(alice(), 'sessions/s1'), session('alice', 'ABC234')));
  });

  test('alınmış kod tekrar kullanılamaz', async () => {
    await createSessionAs(alice(), 'alice', 's1', 'ABC234');
    await assertFails(createSessionAs(bob(), 'bob', 's2', 'ABC234'));
  });

  test('başkası adına etkinlik oluşturulamaz', async () => {
    await assertFails(createSessionAs(alice(), 'bob', 's1', 'ABC234'));
  });

  test('kodlar listelenemez ama bilinen kod okunabilir', async () => {
    await createSessionAs(alice(), 'alice', 's1', 'ABC234', { type: 'private' });
    await assertFails(getDocs(collection(bob(), 'session_codes')));
    await assertSucceeds(getDoc(doc(bob(), 'session_codes/ABC234')));
  });

  test('public liste sorgusu çalışır, filtresiz liste reddedilir', async () => {
    await createSessionAs(alice(), 'alice', 's1', 'ABC234');
    await createSessionAs(alice(), 'alice', 's2', 'XYZ789', { type: 'private' });
    const db = bob();
    await assertSucceeds(getDocs(query(collection(db, 'sessions'),
      where('type', '==', 'public'), where('is_Active', '==', true), orderBy('date', 'desc'))));
    await assertFails(getDocs(query(collection(db, 'sessions'), where('is_Active', '==', true))));
  });

  test('kendi etkinliklerim sorguları çalışır', async () => {
    const db = alice();
    await assertSucceeds(getDocs(query(collection(db, 'sessions'),
      where('created_by', '==', 'alice'), where('is_Active', '==', true))));
    await assertSucceeds(getDocs(query(collection(db, 'sessions'),
      where('participant_ids', 'array-contains', 'alice'), where('is_Active', '==', true))));
  });

  test('katılımcı sadece kendini ekleyebilir', async () => {
    await createSessionAs(alice(), 'alice', 's1', 'ABC234', { type: 'private' });
    await assertSucceeds(updateDoc(doc(bob(), 'sessions/s1'),
      { participant_ids: ['alice', 'bob'], participant_count: 2 }));
    // Bob, Alice'i çıkaramaz / başkasını ekleyemez
    await assertFails(updateDoc(doc(bob(), 'sessions/s1'),
      { participant_ids: ['bob', 'mallory'], participant_count: 2 }));
  });

  test('davetli etkinliğe kodla katılınamaz', async () => {
    await createSessionAs(alice(), 'alice', 's1', 'ABC234', { type: 'invite_only' });
    await assertFails(updateDoc(doc(bob(), 'sessions/s1'),
      { participant_ids: ['alice', 'bob'], participant_count: 2 }));
  });

  test('biten etkinliğe katılınamaz', async () => {
    await createSessionAs(alice(), 'alice', 's1', 'ABC234');
    await updateDoc(doc(alice(), 'sessions/s1'), { is_Active: false, status: 'completed' });
    await assertFails(updateDoc(doc(bob(), 'sessions/s1'),
      { participant_ids: ['alice', 'bob'], participant_count: 2 }));
  });

  test('katılımcı ayrılabilir', async () => {
    await createSessionAs(alice(), 'alice', 's1', 'ABC234');
    await updateDoc(doc(bob(), 'sessions/s1'), { participant_ids: ['alice', 'bob'], participant_count: 2 });
    await assertSucceeds(updateDoc(doc(bob(), 'sessions/s1'), { participant_ids: ['alice'], participant_count: 1 }));
  });

  test('başkası etkinliği bitiremez/silemez/yeniden adlandıramaz', async () => {
    await createSessionAs(alice(), 'alice', 's1', 'ABC234');
    await assertFails(updateDoc(doc(bob(), 'sessions/s1'), { is_Active: false, status: 'cancelled' }));
    await assertFails(updateDoc(doc(bob(), 'sessions/s1'), { name: 'hacked' }));
    await assertFails(deleteDoc(doc(bob(), 'sessions/s1')));
  });

  test('organizatör bitirir ve siler; kodu değiştiremez', async () => {
    await createSessionAs(alice(), 'alice', 's1', 'ABC234');
    await assertFails(updateDoc(doc(alice(), 'sessions/s1'), { code: 'ZZZZZZ' }));
    await assertSucceeds(updateDoc(doc(alice(), 'sessions/s1'), { is_Active: false, status: 'completed' }));
    await assertSucceeds(deleteDoc(doc(alice(), 'sessions/s1')));
    await assertSucceeds(deleteDoc(doc(alice(), 'session_codes/ABC234')));
  });

  test('arrayUnion ile tekrar katılım (zaten üye) reddedilir', async () => {
    await createSessionAs(alice(), 'alice', 's1', 'ABC234');
    await assertFails(updateDoc(doc(alice(), 'sessions/s1'), { participant_ids: arrayUnion('alice'), participant_count: 2 }));
  });
});

// ---------------------------------------------------------------------------
describe('diğer koleksiyonlar', () => {
  test('tanımsız koleksiyonlar kapalı', async () => {
    await assertFails(setDoc(doc(alice(), 'news/n1'), { title: 'x' }));
    await assertFails(getDoc(doc(alice(), 'anything/x')));
  });
});
