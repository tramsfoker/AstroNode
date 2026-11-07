// AstroNode Cloud Functions giriş noktası.
// Bölge: Firestore veritabanının konumuyla uyumlu olmalı (bkz. docs/RELEASE_CHECKLIST.md).
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';
import { setGlobalOptions } from 'firebase-functions/v2';
import { onDocumentCreated, onDocumentDeleted } from 'firebase-functions/v2/firestore';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import * as functionsV1 from 'firebase-functions/v1';
import { logger } from 'firebase-functions';

import { applyMeasurement, revertMeasurement, rebuildAllStats } from './stats.js';
import { fetchAllFeeds } from './feeds.js';
import * as admin from './admin.js';

initializeApp();
const db = getFirestore();
const authAdmin = getAuth();

const REGION = process.env.FUNCTIONS_REGION || 'europe-west1';
setGlobalOptions({ region: REGION, maxInstances: 10, memory: '512MiB' });

// --- Ölçüm istatistikleri ---
export const onMeasurementCreated = onDocumentCreated('measurements/{id}', async (event) => {
  const result = await applyMeasurement(db, event.params.id);
  logger.debug('measurement stats', { id: event.params.id, result });
});

export const onMeasurementDeleted = onDocumentDeleted('measurements/{id}', async (event) => {
  await revertMeasurement(db, event.data?.data());
});

export const nightlyStatsRebuild = onSchedule(
  { schedule: '30 3 * * *', timeZone: 'Europe/Istanbul', timeoutSeconds: 540, memory: '1GiB' },
  async () => logger.info('stats rebuild', await rebuildAllStats(db)),
);

// --- Haber akışı ---
export const fetchNewsFeeds = onSchedule(
  { schedule: 'every 6 hours', timeoutSeconds: 300 },
  async () => logger.info('feeds', await fetchAllFeeds(db)),
);

// --- Admin callable'ları ---
const wrap = (fn) => onCall(async (req) => {
  try {
    return await fn({ db, authAdmin }, req.auth, req.data ?? {});
  } catch (e) {
    if (e instanceof admin.HttpsLikeError) throw new HttpsError(e.code, e.message);
    logger.error(e);
    throw new HttpsError('internal', 'İşlem başarısız');
  }
});

export const setUserRole = wrap(admin.setUserRole);
export const reviewOrganizationRequest = wrap(admin.reviewOrganizationRequest);
export const resolveReport = wrap(admin.resolveReport);
export const findUser = wrap(admin.findUser);
export const runFeedsNow = wrap(async (ctx, caller) => { admin.requireAdmin(caller); return fetchAllFeeds(db); });
export const runStatsRebuildNow = wrap(async (ctx, caller) => { admin.requireAdmin(caller); return rebuildAllStats(db); });

// --- Hesap silindiğinde veri temizliği (konsoldan / e-posta talebiyle silinen hesaplar için) ---
export const onAuthUserDeleted = functionsV1.region(REGION).auth.user().onDelete(async (user) => {
  await admin.deleteUserData(db, user.uid);
});
