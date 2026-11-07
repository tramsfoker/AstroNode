// Admin paneli uçtan uca testi: gerçek Chromium + Auth/Firestore/Functions emulator.
// Çalıştırma: npm run test:e2e   (ekran görüntüsü: test-e2e/out/)
import { createServer } from 'node:http';
import { readFile, mkdir } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, Timestamp, GeoPoint } from 'firebase-admin/firestore';

// Playwright bu ortamda global kurulu (NODE_PATH); CI'da devDependency olarak gelir
const { chromium } = createRequire(import.meta.url)('playwright');

const PROJECT = process.env.GCLOUD_PROJECT || 'demo-astronode-fn';
const here = fileURLToPath(new URL('.', import.meta.url));
const publicDir = join(here, '..', 'public');
const sdkDir = join(here, '..', 'node_modules', 'firebase');
const outDir = join(here, 'out');

initializeApp({ projectId: PROJECT });
const auth = getAuth();
const db = getFirestore();

// --- Veri hazırlığı ---
const adminUser = await auth.createUser({ email: 'admin@astronode.test', password: 'sifre123', displayName: 'Baak Admin' });
await auth.setCustomUserClaims(adminUser.uid, { admin: true, organizer: true });
const applicant = await auth.createUser({ email: 'kulup@astronode.test', password: 'sifre123' });
await db.doc('organization_requests/req1').set({ requester_uid: applicant.uid, requester_name: 'Ayşe', org_name: 'Ankara Gökyüzü Derneği',
  city: 'Ankara', contact_email: 'kulup@astronode.test', status: 'pending', created_at: Timestamp.now() });
await db.doc('measurements/spam1').set({ observer_uid: 'spammer', device_id: 'spammer', sqm_value: 24.9, bortle_class: 1,
  location: new GeoPoint(39.9, 32.8), geohash: 'sxk3', timestamp: Timestamp.now(), is_test: false });
await db.doc('reports/rep1').set({ reporter_uid: applicant.uid, target_type: 'measurement', target_id: 'spam1', reason: 'spam',
  details: 'Bu değer gerçek olamaz', status: 'open', created_at: Timestamp.now() });
const observer = await auth.createUser({ email: 'gozlemci@astronode.test', password: 'sifre123' });

// --- Statik sunucu ---
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css' };
const server = createServer(async (req, res) => {
  try {
    let p = decodeURIComponent(new URL(req.url, 'http://x').pathname);
    if (p.endsWith('/')) p += 'index.html';
    const file = normalize(join(publicDir, p));
    if (!file.startsWith(publicDir)) throw new Error('yol dışı');
    res.writeHead(200, { 'content-type': types[extname(file)] ?? 'application/octet-stream' });
    res.end(await readFile(file));
  } catch { res.writeHead(404); res.end('not found'); }
}).listen(5055, '127.0.0.1');

const browser = await chromium.launch();
const errors = [];
try {
  const page = await browser.newPage({ viewport: { width: 1200, height: 900 } });
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('dialog', (d) => d.accept(d.type() === 'prompt' ? '' : undefined));
  // CDN yerine npm paketindeki aynı sürüm dosyalar
  await page.route('https://www.gstatic.com/firebasejs/11.10.0/*', async (route) => {
    const name = new URL(route.request().url()).pathname.split('/').pop();
    route.fulfill({ contentType: 'text/javascript', body: await readFile(join(sdkDir, name)) });
  });
  await page.addInitScript((projectId) => {
    window.ASTRONODE_FIREBASE_CONFIG = { apiKey: 'demo-key', authDomain: 'localhost', projectId, emulators: true };
  }, PROJECT);

  await page.goto('http://127.0.0.1:5055/admin/');
  await page.getByText('Google ile giriş yap').waitFor();
  await mkdir(outDir, { recursive: true });
  await page.screenshot({ path: join(outDir, '1-login.png') });

  // Yetkisiz kullanıcı reddedilir
  await page.evaluate(() => window.__testSignIn('gozlemci@astronode.test', 'sifre123'));
  await page.getByText('Bu hesabın yönetim yetkisi yok').waitFor();

  // Admin girişi + özet
  await page.evaluate(() => window.__testSignIn('admin@astronode.test', 'sifre123'));
  await page.locator('#who', { hasText: 'Süper admin' }).waitFor();
  await page.waitForFunction(() => [...document.querySelectorAll('.kpi b')].every((b) => b.textContent !== '…'));
  const kpis = await page.$$eval('.kpi', (els) => els.map((e) => e.textContent));
  assert.ok(kpis.some((k) => k.startsWith('1') && k.includes('Açık bildirim')), `KPI: ${kpis}`);
  assert.ok(kpis.some((k) => k.startsWith('1') && k.includes('Bekleyen başvuru')), `KPI: ${kpis}`);
  await page.screenshot({ path: join(outDir, '2-dashboard.png') });

  // Duyuru yayınla (kurallar gerçek emulator'da uygulanıyor)
  await page.getByRole('button', { name: 'Duyurular' }).click();
  await page.fill('input[name=title]', 'Uludağ gözlem gecesi');
  await page.fill('textarea[name=body]', 'Cumartesi 21:00, Sarıalan. Herkes davetli!');
  await page.check('input[name=pinned]');
  await page.getByRole('button', { name: 'Yayınla' }).click();
  await page.getByText('Yayınlandı ✓').waitFor();
  await page.locator('#posts').getByText('Uludağ gözlem gecesi').waitFor();
  const posts = await db.collection('news').where('type', '==', 'post').get();
  assert.equal(posts.size, 1); assert.equal(posts.docs[0].data().pinned, true);
  await page.screenshot({ path: join(outDir, '3-posts.png') });

  // Organizasyon onayı (callable → emulator'daki Cloud Function)
  await page.getByRole('button', { name: 'Organizasyonlar' }).click();
  await page.getByText('Ankara Gökyüzü Derneği').waitFor();
  await page.screenshot({ path: join(outDir, '4-orgs.png') });
  await page.getByRole('button', { name: 'Onayla' }).click();
  await page.getByText('Bekleyen başvuru yok.').waitFor();
  assert.equal((await auth.getUser(applicant.uid)).customClaims.organizer, true);

  // Bildirim: içeriği sil + engelle
  await page.getByRole('button', { name: 'Bildirimler' }).click();
  await page.getByText('Bu değer gerçek olamaz').waitFor();
  await page.screenshot({ path: join(outDir, '5-reports.png') });
  await page.getByRole('button', { name: 'Sil + engelle' }).click();
  await page.getByText('Açık bildirim yok').waitFor();
  assert.equal((await db.doc('measurements/spam1').get()).exists, false);

  // Kullanıcı arama + rol
  await page.getByRole('button', { name: 'Kullanıcılar' }).click();
  await page.fill('input[name=q]', 'gozlemci@astronode.test');
  await page.getByRole('button', { name: 'Ara', exact: true }).click();
  await page.getByText(`UID: ${observer.uid}`).waitFor();
  await page.screenshot({ path: join(outDir, '6-users.png') });

  // Telefon genişliğinde yerleşim
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: 'Özet' }).click();
  await page.screenshot({ path: join(outDir, '7-mobile.png'), fullPage: true });
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  assert.equal(overflow, false, 'mobilde yatay kaydırma olmamalı');

  assert.deepEqual(errors, [], 'sayfa hatası olmamalı');
  console.log('✔ admin paneli e2e testi geçti');
} finally {
  await browser.close();
  server.close();
}
