// Bir kullanıcıya süper admin (veya organizatör) yetkisi verir/geri alır — custom claims.
// Kullanım:
//   node tools/set-admin.mjs <uid> admin          → süper admin yap
//   node tools/set-admin.mjs <uid> organizer      → organizatör yap
//   node tools/set-admin.mjs <uid> none           → tüm yetkileri kaldır
// Kullanıcının UID'si: Firebase Console → Authentication → Users.
// Değişiklik kullanıcı uygulamayı yeniden açınca (token yenilenince) geçerli olur.
import { auth, db } from './_admin.mjs';

const [uid, role] = process.argv.slice(2);
if (!uid || !['admin', 'organizer', 'none'].includes(role)) {
  console.error('Kullanım: node tools/set-admin.mjs <uid> admin|organizer|none');
  process.exit(1);
}

const claims = role === 'none' ? {} : role === 'admin' ? { admin: true, organizer: true } : { organizer: true };
await auth.setCustomUserClaims(uid, claims);
// Profil belgesindeki rol sadece görüntüleme içindir (yetki claims'ten gelir)
await db.doc(`users/${uid}`).set(
  { role: role === 'admin' ? 'super_admin' : role === 'organizer' ? 'organizer' : 'observer' },
  { merge: true },
);
console.log(`✔ ${uid} → ${JSON.stringify(claims)}`);
