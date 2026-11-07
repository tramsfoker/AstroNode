// AstroNode yönetim paneli. Yetki kontrolü sunucuda (Firestore kuralları + callable'lar);
// buradaki rol kontrolleri sadece arayüzü düzenler. Tüm kullanıcı verisi textContent ile basılır (XSS yok).
import { initializeApp } from 'https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js';
import { getAuth, GoogleAuthProvider, signInWithPopup, onAuthStateChanged, signOut, connectAuthEmulator, signInWithEmailAndPassword } from 'https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js';
import {
  getFirestore, collection, query, where, orderBy, limit, getDocs, addDoc, deleteDoc, doc, setDoc,
  serverTimestamp, Timestamp, getCountFromServer, getDoc, connectFirestoreEmulator,
} from 'https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js';
import { getFunctions, httpsCallable, connectFunctionsEmulator } from 'https://www.gstatic.com/firebasejs/11.10.0/firebase-functions.js';

const FUNCTIONS_REGION = 'europe-west1';
const DEFAULT_FEEDS = [
  { id: 'nasa-apod', name: 'NASA APOD', url: 'https://apod.nasa.gov/apod.rss', lang: 'en' },
  { id: 'nasa-news', name: 'NASA', url: 'https://www.nasa.gov/news-release/feed/', lang: 'en' },
  { id: 'esa-science', name: 'ESA Space Science', url: 'https://www.esa.int/rssfeed/Our_Activities/Space_Science', lang: 'en' },
  { id: 'darksky', name: 'DarkSky International', url: 'https://darksky.org/feed/', lang: 'en' },
  { id: 'skytel', name: 'Sky & Telescope', url: 'https://skyandtelescope.org/feed/', lang: 'en' },
];
const TABS = [
  ['dashboard', 'Özet', 'admin'], ['posts', 'Duyurular', 'organizer'], ['orgs', 'Organizasyonlar', 'admin'],
  ['reports', 'Bildirimler', 'admin'], ['users', 'Kullanıcılar', 'admin'], ['feeds', 'RSS', 'admin'],
];

const $ = (id) => document.getElementById(id);
const el = (tag, props = {}, ...children) => {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === 'onclick') n.addEventListener('click', v);
    else if (k === 'class') n.className = v;
    else n.setAttribute(k, v);
  }
  for (const c of children) n.append(c instanceof Node ? c : document.createTextNode(String(c ?? '')));
  return n;
};
const fmt = (ts) => (ts?.toDate ? ts.toDate().toLocaleString('tr-TR') : '');
const empty = (ul, text) => { ul.replaceChildren(el('li', { class: 'muted' }, text)); };

// Firebase Hosting proje yapılandırmasını kendisi sunar (/__/firebase/init.json);
// yerel test için window.ASTRONODE_FIREBASE_CONFIG kullanılabilir.
const config = window.ASTRONODE_FIREBASE_CONFIG ?? await (await fetch('/__/firebase/init.json')).json();
const app = initializeApp(config);
const auth = getAuth(app);
const db = getFirestore(app);
const fns = getFunctions(app, FUNCTIONS_REGION);
const call = (name, data) => httpsCallable(fns, name)(data).then((r) => r.data);

// Sadece yerel testte (window.ASTRONODE_FIREBASE_CONFIG.emulators) — canlı yapılandırma init.json'dan gelir
if (config.emulators) {
  connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
  connectFirestoreEmulator(db, '127.0.0.1', 8080);
  connectFunctionsEmulator(fns, '127.0.0.1', 5001);
  window.__testSignIn = (email, pass) => signInWithEmailAndPassword(auth, email, pass);
}

let role = null; // 'admin' | 'organizer'
let me = null;

$('signin').onclick = async () => {
  $('login-error').textContent = '';
  try { await signInWithPopup(auth, new GoogleAuthProvider()); } catch (e) { $('login-error').textContent = e.message; }
};
$('signout').onclick = () => signOut(auth);

onAuthStateChanged(auth, async (user) => {
  me = user;
  if (!user) return showLogin();
  const { claims } = await user.getIdTokenResult(true);
  role = claims.admin ? 'admin' : claims.organizer ? 'organizer' : null;
  if (!role) {
    await signOut(auth);
    showLogin('Bu hesabın yönetim yetkisi yok. Süper admin seni yetkilendirmeli (npm run set-admin).');
    return;
  }
  $('who').textContent = `${user.email ?? user.uid} · ${role === 'admin' ? 'Süper admin' : 'Organizatör'}`;
  $('signout').hidden = false;
  $('login').hidden = true;
  $('app').hidden = false;
  buildTabs(claims);
});

function showLogin(msg = '') {
  $('login').hidden = false; $('app').hidden = true; $('signout').hidden = true; $('who').textContent = '';
  $('login-error').textContent = msg;
}

function canSee(r) { return role === 'admin' || r === 'organizer'; }

function buildTabs(claims) {
  const nav = $('tabs');
  nav.replaceChildren();
  const visible = TABS.filter(([, , r]) => canSee(r));
  for (const [id, label] of visible) nav.append(el('button', { 'data-id': id, onclick: () => openTab(id) }, label));
  document.querySelectorAll('[data-role="admin"]').forEach((n) => { if (role !== 'admin' && !n.classList.contains('panel')) n.hidden = true; });
  window.__claims = claims;
  window.__orgName = null;
  if (claims.org_id) {
    getDoc(doc(db, 'organizations', claims.org_id)).then((d) => { window.__orgName = d.data()?.name ?? null; }).catch(() => {});
  }
  openTab(visible[0][0]);
}

const loaders = { dashboard: loadDashboard, posts: loadPosts, orgs: loadOrgs, reports: loadReports, feeds: loadFeeds, users: () => {} };

function openTab(id) {
  document.querySelectorAll('.tabs button').forEach((b) => b.classList.toggle('active', b.dataset.id === id));
  document.querySelectorAll('.panel').forEach((p) => { p.hidden = p.dataset.tab !== id; });
  Promise.resolve(loaders[id]?.()).catch((e) => alert(e.message));
}

// ---------------- Özet ----------------
async function loadDashboard() {
  const count = async (q) => (await getCountFromServer(q)).data().count;
  const items = [
    ['Ölçüm', count(collection(db, 'measurements'))],
    ['Kullanıcı', count(collection(db, 'users'))],
    ['Açık bildirim', count(query(collection(db, 'reports'), where('status', '==', 'open')))],
    ['Bekleyen başvuru', count(query(collection(db, 'organization_requests'), where('status', '==', 'pending')))],
    ['Organizasyon', count(collection(db, 'organizations'))],
    ['Haber', count(collection(db, 'news'))],
  ];
  const kpis = $('kpis');
  kpis.replaceChildren(...items.map(([label]) => el('div', { class: 'kpi' }, el('b', {}, '…'), label)));
  const values = await Promise.allSettled(items.map(([, p]) => p));
  values.forEach((v, i) => { kpis.children[i].firstChild.textContent = v.status === 'fulfilled' ? v.value.toLocaleString('tr-TR') : '—'; });
}

$('rebuild').onclick = () => runMaint('runStatsRebuildNow');
$('fetch-now').onclick = () => runMaint('runFeedsNow');
async function runMaint(name) {
  const out = $('maint-out');
  out.textContent = 'Çalışıyor…';
  try { out.textContent = JSON.stringify(await call(name, {}), null, 2); } catch (e) { out.textContent = 'Hata: ' + e.message; }
}

// ---------------- Duyurular ----------------
$('post-form').onsubmit = async (ev) => {
  ev.preventDefault();
  const f = new FormData(ev.target);
  const msg = $('post-msg');
  const optional = (k) => (f.get(k) || '').toString().trim() || null;
  const claims = window.__claims ?? {};
  const data = {
    type: 'post',
    title: f.get('title').toString().trim(),
    body: f.get('body').toString().trim(),
    link: optional('link'),
    image_url: optional('image_url'),
    author_uid: me.uid,
    author_name: me.displayName ?? null,
    org_id: claims.org_id ?? null,
    org_name: window.__orgName ?? (role === 'admin' ? 'AstroNode' : null),
    published_at: serverTimestamp(),
    pinned: role === 'admin' && f.get('pinned') === 'on',
    lang: 'tr',
  };
  const ed = optional('event_date');
  if (ed) data.event_date = Timestamp.fromDate(new Date(ed));
  if (data.image_url && !data.image_url.startsWith('https://')) { msg.textContent = 'Görsel URL https ile başlamalı'; return; }
  try {
    await addDoc(collection(db, 'news'), data);
    ev.target.reset();
    msg.textContent = 'Yayınlandı ✓';
    loadPosts();
  } catch (e) { msg.textContent = 'Hata: ' + e.message; }
};

async function loadPosts() {
  const ul = $('posts');
  const q = role === 'admin'
    ? query(collection(db, 'news'), where('type', '==', 'post'), orderBy('published_at', 'desc'), limit(30))
    : query(collection(db, 'news'), where('author_uid', '==', me.uid), orderBy('published_at', 'desc'), limit(30));
  const snap = await getDocs(q);
  if (snap.empty) return empty(ul, 'Henüz duyuru yok.');
  ul.replaceChildren(...snap.docs.map((d) => {
    const p = d.data();
    return el('li', {},
      el('div', { class: 'grow' }, el('strong', {}, p.title), ' ', p.pinned ? el('span', { class: 'badge' }, 'sabit') : '',
        el('div', { class: 'muted' }, `${fmt(p.published_at)} · ${p.org_name ?? p.author_name ?? ''}`)),
      el('div', { class: 'actions' }, el('button', { class: 'ghost', onclick: async () => {
        if (!confirm('Duyuru silinsin mi?')) return;
        await deleteDoc(doc(db, 'news', d.id)); loadPosts();
      } }, 'Sil')));
  }));
}

// ---------------- Organizasyonlar ----------------
async function loadOrgs() {
  const ul = $('orgs');
  const snap = await getDocs(query(collection(db, 'organization_requests'), where('status', '==', 'pending'), orderBy('created_at', 'desc'), limit(50)));
  if (snap.empty) return empty(ul, 'Bekleyen başvuru yok.');
  ul.replaceChildren(...snap.docs.map((d) => {
    const r = d.data();
    const review = async (approve) => {
      const note = approve ? '' : (prompt('Ret nedeni (başvurana gösterilir):') ?? '');
      try { await call('reviewOrganizationRequest', { requestId: d.id, approve, note }); loadOrgs(); } catch (e) { alert(e.message); }
    };
    return el('li', {},
      el('div', { class: 'grow' }, el('strong', {}, r.org_name), ` · ${r.city ?? ''}`,
        el('div', { class: 'muted' }, `${r.requester_name ?? ''} · ${r.contact_email} · ${r.website ?? ''} · ${fmt(r.created_at)}`),
        r.description ? el('div', {}, r.description) : ''),
      el('div', { class: 'actions' },
        el('button', { class: 'ok', onclick: () => review(true) }, 'Onayla'),
        el('button', { class: 'ghost', onclick: () => review(false) }, 'Reddet')));
  }));
}

// ---------------- Bildirimler ----------------
const REASONS = { spam: 'Spam/sahte veri', wrong_data: 'Hatalı ölçüm', offensive: 'Rahatsız edici', privacy: 'Gizlilik', other: 'Diğer' };
async function loadReports() {
  const ul = $('reports');
  const snap = await getDocs(query(collection(db, 'reports'), where('status', '==', 'open'), orderBy('created_at', 'desc'), limit(50)));
  if (snap.empty) return empty(ul, 'Açık bildirim yok. 🎉');
  ul.replaceChildren(...snap.docs.map((d) => {
    const r = d.data();
    const act = async (action, ask) => {
      if (ask && !confirm(ask)) return;
      try { await call('resolveReport', { reportId: d.id, action }); loadReports(); } catch (e) { alert(e.message); }
    };
    return el('li', {},
      el('div', { class: 'grow' }, el('strong', {}, REASONS[r.reason] ?? r.reason), ` · ${r.target_type}`,
        el('div', { class: 'muted' }, `Hedef: ${r.target_id} · ${fmt(r.created_at)}`), r.details ? el('div', {}, r.details) : ''),
      el('div', { class: 'actions' },
        el('button', { class: 'ghost', onclick: () => act('dismiss') }, 'Yoksay'),
        el('button', { onclick: () => act('delete_content', 'İçerik silinsin mi?') }, 'İçeriği sil'),
        el('button', { onclick: () => act('ban_user', 'İçerik silinip kullanıcı engellensin mi?') }, 'Sil + engelle')));
  }));
}

// ---------------- Kullanıcılar ----------------
$('user-form').onsubmit = async (ev) => {
  ev.preventDefault();
  const out = $('user-out');
  out.replaceChildren(el('p', { class: 'muted' }, 'Aranıyor…'));
  try {
    const u = await call('findUser', { query: new FormData(ev.target).get('q') });
    const current = u.claims.admin ? 'admin' : u.claims.organizer ? 'organizer' : 'observer';
    const select = el('select', {}, ...['observer', 'organizer', 'admin'].map((r) => {
      const o = el('option', { value: r }, { observer: 'Gözlemci', organizer: 'Organizatör', admin: 'Süper admin' }[r]);
      if (r === current) o.selected = true;
      return o;
    }));
    out.replaceChildren(
      el('p', {}, el('strong', {}, u.displayName ?? '(adsız)'), ` · ${u.email ?? 'e-posta yok'}`, u.disabled ? ' · ENGELLİ' : ''),
      el('p', { class: 'muted' }, `UID: ${u.uid} · Oluşturma: ${u.created ?? ''} · Son giriş: ${u.lastSignIn ?? ''}`),
      el('div', { class: 'row' }, select, el('button', { onclick: async (e) => {
        e.preventDefault();
        try { await call('setUserRole', { uid: u.uid, role: select.value, orgId: u.claims.org_id ?? null }); alert('Rol güncellendi'); } catch (err) { alert(err.message); }
      } }, 'Rolü kaydet')));
  } catch (e) { out.replaceChildren(el('p', { class: 'error' }, e.message)); }
};

// ---------------- RSS ----------------
$('feed-form').onsubmit = async (ev) => {
  ev.preventDefault();
  const f = new FormData(ev.target);
  await setDoc(doc(db, 'feed_sources', f.get('id')), { name: f.get('name'), url: f.get('url'), lang: f.get('lang'), enabled: true });
  ev.target.reset();
  loadFeeds();
};
$('seed-feeds').onclick = async () => {
  for (const s of DEFAULT_FEEDS) await setDoc(doc(db, 'feed_sources', s.id), { name: s.name, url: s.url, lang: s.lang, enabled: true }, { merge: true });
  loadFeeds();
};
async function loadFeeds() {
  const ul = $('feeds');
  const snap = await getDocs(collection(db, 'feed_sources'));
  if (snap.empty) return empty(ul, 'Kayıtlı kaynak yok — sunucu varsayılan kaynakları kullanıyor. “Varsayılan kaynakları yükle” ile düzenlenebilir hale getir.');
  ul.replaceChildren(...snap.docs.map((d) => {
    const s = d.data();
    return el('li', {},
      el('div', { class: 'grow' }, el('strong', {}, s.name), ' ', el('span', { class: 'badge' }, s.enabled === false ? 'kapalı' : 'açık'),
        el('div', { class: 'muted' }, s.url)),
      el('div', { class: 'actions' },
        el('button', { class: 'ghost', onclick: async () => { await setDoc(doc(db, 'feed_sources', d.id), { enabled: s.enabled === false }, { merge: true }); loadFeeds(); } },
          s.enabled === false ? 'Aç' : 'Kapat'),
        el('button', { class: 'ghost', onclick: async () => { if (confirm('Kaynak silinsin mi?')) { await deleteDoc(doc(db, 'feed_sources', d.id)); loadFeeds(); } } }, 'Sil')));
  }));
}
