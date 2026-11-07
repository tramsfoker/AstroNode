// Ölçüm istatistikleri: il/ilçe (region_stats), ~5 km hücre (sky_cells) ve etkinlik özetleri.
// Fonksiyonlar saf mantık + db parametresi alır → emulator ile test edilebilir.
import { FieldValue, Timestamp } from 'firebase-admin/firestore';
import { findRegion, regionKey } from './regions.js';

export const CELL_PRECISION = 5; // geohash 5 karakter ≈ 4.9 km × 4.9 km

/** İstatistiğe girer mi? Test/gündüz ve aralık dışı ölçümler hariç. */
export function countsForStats(m) {
  return m && m.is_test !== true && m.observing?.is_daytime !== true &&
    typeof m.sqm_value === 'number' && m.sqm_value >= 10 && m.sqm_value <= 25;
}

function monthKey(ts) {
  const d = ts instanceof Timestamp ? ts.toDate() : new Date();
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
}

function statTargets(m, region) {
  const targets = [];
  if (region) {
    targets.push({
      path: `region_stats/${regionKey(region.country, region.admin1)}`,
      base: { country: region.country, admin1: region.admin1, admin2: null, level: 1 },
    });
    if (region.admin2) {
      targets.push({
        path: `region_stats/${regionKey(region.country, region.admin1, region.admin2)}`,
        base: { country: region.country, admin1: region.admin1, admin2: region.admin2, level: 2 },
      });
    }
  }
  if (typeof m.geohash === 'string' && m.geohash.length >= CELL_PRECISION) {
    const cell = m.geohash.slice(0, CELL_PRECISION);
    targets.push({
      path: `sky_cells/${cell}`,
      base: { geohash: cell, country: region?.country ?? null, admin1: region?.admin1 ?? null },
      isCell: true,
    });
  }
  return targets;
}

/**
 * Yeni ölçümü işler: bölgeyi yazar ve istatistikleri artırır. İdempotent (stats_applied işareti).
 * @returns {Promise<'applied'|'skipped'|'missing'>}
 */
export async function applyMeasurement(db, measurementId) {
  const ref = db.doc(`measurements/${measurementId}`);
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return 'missing';
    const m = snap.data();
    if (m.stats_applied) return 'skipped';

    const loc = m.location;
    const region = loc ? findRegion(loc.latitude, loc.longitude) : null;
    const counts = countsForStats(m);
    const targets = counts ? statTargets(m, region) : [];
    const sessionRef = counts && typeof m.session_id === 'string' ? db.doc(`sessions/${m.session_id}`) : null;

    // Transaction kuralı: önce tüm okumalar
    const targetSnaps = await Promise.all(targets.map((t) => tx.get(db.doc(t.path))));
    const sessionSnap = sessionRef ? await tx.get(sessionRef) : null;

    const v = m.sqm_value;
    const month = monthKey(m.timestamp);
    targets.forEach((t, i) => {
      const prev = targetSnaps[i].exists ? targetSnaps[i].data() : null;
      const best = prev?.best == null || v > prev.best ? v : prev.best;
      const update = {
        ...t.base,
        count: FieldValue.increment(1),
        sum: FieldValue.increment(v),
        sum_sq: FieldValue.increment(v * v),
        best,
        last_at: m.timestamp ?? FieldValue.serverTimestamp(),
        [`months.${month}.count`]: FieldValue.increment(1),
        [`months.${month}.sum`]: FieldValue.increment(v),
        updated_at: FieldValue.serverTimestamp(),
      };
      if (t.isCell) {
        update.sum_lat = FieldValue.increment(loc.latitude);
        update.sum_lng = FieldValue.increment(loc.longitude);
      }
      // set+merge: "months.X.count" noktalı anahtarları update gibi yorumlamaz → update/create ayrımı
      if (prev) tx.update(db.doc(t.path), update);
      else tx.set(db.doc(t.path), expandDotted(update));
    });

    if (sessionSnap?.exists) {
      const s = sessionSnap.data();
      tx.update(sessionRef, {
        measurement_count: FieldValue.increment(1),
        mpsas_sum: FieldValue.increment(v),
        best_mpsas: s.best_mpsas == null || v > s.best_mpsas ? v : s.best_mpsas,
      });
    }

    tx.update(ref, {
      region: region ?? null,
      stats_applied: true,
      stats_counted: counts,
    });
    return 'applied';
  });
}

/** Silinen ölçümün katkısını geri alır (en iyi değer gece yeniden hesaplanır). */
export async function revertMeasurement(db, m) {
  if (!m?.stats_applied || !m.stats_counted) return 'skipped';
  const region = m.region ?? null;
  const v = m.sqm_value;
  const month = monthKey(m.timestamp);
  const batch = db.batch();
  for (const t of statTargets(m, region)) {
    const upd = {
      count: FieldValue.increment(-1),
      sum: FieldValue.increment(-v),
      sum_sq: FieldValue.increment(-v * v),
      [`months.${month}.count`]: FieldValue.increment(-1),
      [`months.${month}.sum`]: FieldValue.increment(-v),
    };
    if (t.isCell && m.location) {
      upd.sum_lat = FieldValue.increment(-m.location.latitude);
      upd.sum_lng = FieldValue.increment(-m.location.longitude);
    }
    batch.set(db.doc(t.path), expandDotted(upd), { merge: true });
  }
  if (typeof m.session_id === 'string') {
    const sref = db.doc(`sessions/${m.session_id}`);
    if ((await sref.get()).exists) {
      batch.update(sref, { measurement_count: FieldValue.increment(-1), mpsas_sum: FieldValue.increment(-v) });
    }
  }
  await batch.commit();
  return 'reverted';
}

/** {"a.b.c": 1} → {a:{b:{c:1}}} (set ile kullanmak için) */
function expandDotted(obj) {
  const out = {};
  for (const [k, val] of Object.entries(obj)) {
    const parts = k.split('.');
    let cur = out;
    parts.slice(0, -1).forEach((p) => { cur = cur[p] ??= {}; });
    cur[parts.at(-1)] = val;
  }
  return out;
}

/**
 * Tüm istatistikleri sıfırdan hesaplar (silme/test işaretleme sonrası kaymaları düzeltir).
 * Bölgesi olmayan eski ölçümlere bölge de yazar.
 */
export async function rebuildAllStats(db, { pageSize = 500 } = {}) {
  const acc = new Map(); // path → aggregate
  const sessions = new Map();
  const regionFixes = [];
  let last = null;
  let total = 0;

  for (;;) {
    let q = db.collection('measurements').orderBy('__name__').limit(pageSize);
    if (last) q = q.startAfter(last);
    const page = await q.get();
    if (page.empty) break;
    for (const doc of page.docs) {
      total++;
      const m = doc.data();
      let region = m.region;
      if (region === undefined && m.location) {
        region = findRegion(m.location.latitude, m.location.longitude);
        regionFixes.push([doc.ref, region ?? null]);
      }
      const counts = countsForStats(m);
      if (!counts) continue;
      const v = m.sqm_value;
      const month = monthKey(m.timestamp);
      for (const t of statTargets(m, region ?? null)) {
        const a = acc.get(t.path) ?? { ...t.base, count: 0, sum: 0, sum_sq: 0, best: null, last_at: null, months: {}, ...(t.isCell ? { sum_lat: 0, sum_lng: 0 } : {}) };
        a.count++; a.sum += v; a.sum_sq += v * v;
        a.best = a.best == null || v > a.best ? v : a.best;
        if (m.timestamp && (!a.last_at || m.timestamp.toMillis() > a.last_at.toMillis())) a.last_at = m.timestamp;
        a.months[month] ??= { count: 0, sum: 0 };
        a.months[month].count++; a.months[month].sum += v;
        if (t.isCell) { a.sum_lat += m.location.latitude; a.sum_lng += m.location.longitude; }
        acc.set(t.path, a);
      }
      if (typeof m.session_id === 'string') {
        const s = sessions.get(m.session_id) ?? { measurement_count: 0, mpsas_sum: 0, best_mpsas: null };
        s.measurement_count++; s.mpsas_sum += v;
        s.best_mpsas = s.best_mpsas == null || v > s.best_mpsas ? v : s.best_mpsas;
        sessions.set(m.session_id, s);
      }
    }
    last = page.docs.at(-1);
  }

  const writer = db.bulkWriter();
  for (const [ref, region] of regionFixes) writer.update(ref, { region });
  for (const [path, a] of acc) writer.set(db.doc(path), { ...a, updated_at: FieldValue.serverTimestamp() });
  // Artık hiç ölçümü kalmayan istatistik belgelerini sil
  for (const coll of ['region_stats', 'sky_cells']) {
    for (const d of (await db.collection(coll).select().get()).docs) {
      if (!acc.has(`${coll}/${d.id}`)) writer.delete(d.ref);
    }
  }
  for (const [id, s] of sessions) {
    const ref = db.doc(`sessions/${id}`);
    if ((await ref.get()).exists) writer.update(ref, s);
  }
  await writer.close();
  return { measurements: total, statDocs: acc.size, sessions: sessions.size, regionFixes: regionFixes.length };
}
