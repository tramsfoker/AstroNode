// Admin işlemleri (callable fonksiyonların saf mantığı). Yetki kontrolü burada yapılır.
import { FieldValue } from 'firebase-admin/firestore';

export class HttpsLikeError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}

export function requireAdmin(auth) {
  if (!auth) throw new HttpsLikeError('unauthenticated', 'Giriş gerekli');
  if (auth.token?.admin !== true) throw new HttpsLikeError('permission-denied', 'Sadece süper admin');
}

const ROLE_CLAIMS = {
  admin: (orgId) => ({ admin: true, organizer: true, ...(orgId ? { org_id: orgId } : {}) }),
  organizer: (orgId) => ({ organizer: true, ...(orgId ? { org_id: orgId } : {}) }),
  observer: () => ({}),
};
const ROLE_LABEL = { admin: 'super_admin', organizer: 'organizer', observer: 'observer' };

export async function setUserRole({ db, authAdmin }, caller, { uid, role, orgId = null }) {
  requireAdmin(caller);
  if (typeof uid !== 'string' || !ROLE_CLAIMS[role]) throw new HttpsLikeError('invalid-argument', 'uid ve role (admin|organizer|observer) gerekli');
  if (uid === caller.uid && role !== 'admin') throw new HttpsLikeError('failed-precondition', 'Kendi admin yetkini kaldıramazsın');
  await authAdmin.setCustomUserClaims(uid, ROLE_CLAIMS[role](orgId));
  await db.doc(`users/${uid}`).set({ role: ROLE_LABEL[role], organization_id: orgId }, { merge: true });
  return { ok: true };
}

export async function reviewOrganizationRequest({ db, authAdmin }, caller, { requestId, approve, note = '' }) {
  requireAdmin(caller);
  const reqRef = db.doc(`organization_requests/${requestId}`);
  const snap = await reqRef.get();
  if (!snap.exists) throw new HttpsLikeError('not-found', 'Başvuru bulunamadı');
  const r = snap.data();
  if (r.status !== 'pending') throw new HttpsLikeError('failed-precondition', 'Başvuru zaten sonuçlanmış');

  if (!approve) {
    await reqRef.update({ status: 'rejected', review_note: String(note).slice(0, 500), reviewed_by: caller.uid, reviewed_at: FieldValue.serverTimestamp() });
    return { ok: true, status: 'rejected' };
  }
  const orgRef = db.collection('organizations').doc();
  const batch = db.batch();
  batch.set(orgRef, {
    name: r.org_name, description: r.description ?? null, city: r.city ?? null, website: r.website ?? null,
    admin_uids: [r.requester_uid], verified: true, created_at: FieldValue.serverTimestamp(),
  });
  batch.update(reqRef, { status: 'approved', organization_id: orgRef.id, review_note: String(note).slice(0, 500), reviewed_by: caller.uid, reviewed_at: FieldValue.serverTimestamp() });
  batch.set(db.doc(`users/${r.requester_uid}`), { role: 'organizer', organization_id: orgRef.id, organization_name: r.org_name }, { merge: true });
  await batch.commit();
  const user = await authAdmin.getUser(r.requester_uid);
  await authAdmin.setCustomUserClaims(r.requester_uid, { ...(user.customClaims ?? {}), organizer: true, org_id: orgRef.id });
  return { ok: true, status: 'approved', organizationId: orgRef.id };
}

const REPORTABLE = { measurement: 'measurements', news: 'news', session: 'sessions' };

export async function resolveReport({ db, authAdmin }, caller, { reportId, action }) {
  requireAdmin(caller);
  if (!['dismiss', 'delete_content', 'ban_user'].includes(action)) throw new HttpsLikeError('invalid-argument', 'Geçersiz işlem');
  const ref = db.doc(`reports/${reportId}`);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsLikeError('not-found', 'Rapor bulunamadı');
  const r = snap.data();
  let ownerUid = null;
  if (action !== 'dismiss' && REPORTABLE[r.target_type]) {
    const target = db.doc(`${REPORTABLE[r.target_type]}/${r.target_id}`);
    const t = await target.get();
    if (t.exists) {
      const d = t.data();
      ownerUid = d.observer_uid ?? d.device_id ?? d.author_uid ?? d.created_by ?? null;
      await target.delete();
    }
  }
  if (r.target_type === 'user') ownerUid = r.target_id;
  if (action === 'ban_user' && ownerUid) {
    await authAdmin.updateUser(ownerUid, { disabled: true });
    await authAdmin.revokeRefreshTokens(ownerUid);
  }
  await ref.update({ status: 'resolved', action, resolved_by: caller.uid, resolved_at: FieldValue.serverTimestamp() });
  return { ok: true };
}

export async function findUser({ authAdmin }, caller, { query }) {
  requireAdmin(caller);
  const q = String(query ?? '').trim();
  if (!q) throw new HttpsLikeError('invalid-argument', 'UID veya e-posta gerekli');
  try {
    const u = q.includes('@') ? await authAdmin.getUserByEmail(q) : await authAdmin.getUser(q);
    return { uid: u.uid, email: u.email ?? null, displayName: u.displayName ?? null, disabled: u.disabled, claims: u.customClaims ?? {}, created: u.metadata.creationTime, lastSignIn: u.metadata.lastSignInTime };
  } catch {
    throw new HttpsLikeError('not-found', 'Kullanıcı bulunamadı');
  }
}

/** Auth kullanıcısı silinince (ör. konsoldan, e-posta ile silme talebi) kalan verileri temizler. */
export async function deleteUserData(db, uid) {
  const writer = db.bulkWriter();
  for (const field of ['observer_uid', 'device_id']) {
    for (const d of (await db.collection('measurements').where(field, '==', uid).get()).docs) writer.delete(d.ref);
  }
  for (const d of (await db.collection('sessions').where('created_by', '==', uid).get()).docs) {
    const code = d.get('code');
    if (code) writer.delete(db.doc(`session_codes/${code}`));
    writer.delete(d.ref);
  }
  for (const d of (await db.collection('sessions').where('participant_ids', 'array-contains', uid).get()).docs) {
    writer.update(d.ref, { participant_ids: FieldValue.arrayRemove(uid), participant_count: FieldValue.increment(-1) });
  }
  writer.delete(db.doc(`users/${uid}`));
  await writer.close();
}
