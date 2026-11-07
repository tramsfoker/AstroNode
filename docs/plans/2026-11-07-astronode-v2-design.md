# AstroNode v2 — Tasarım Dokümanı

> Tarih: 2026-11-07 · Durum: Onaylandı (Faz 0 uygulanıyor)
> Hedef: Uygulamayı güvenli, kararlı ve Google Play'e çıkabilir hâle getirmek;
> ardından içerik, Türkiye ışık kirliliği analizi ve kamera özellikleriyle dünyaya açmak.

---

## 1. Alınan Kararlar

| Konu | Karar |
|---|---|
| SQM'siz kullanıcı | Ölçüm verisi **giremez**. Harita/analiz/haber görür, **gökyüzü fotoğrafı** paylaşabilir. |
| Kamera | (A) Bölge fotoğraf galerisi — herkes. (B) SQM + fotoğraf eşli kayıt → AI eğitim verisi — sadece SQM sahipleri. |
| Backend | Firebase'de kalınır. Blaze planı + bütçe alarmı (maliyet tablosu §6). Cloud Functions Faz 2'den itibaren. |
| İçerik yönetimi | Süper admin (Baak) + onaylı organizatörler. Ek olarak otomatik RSS akışları. |
| Admin paneli | **Ayrı web paneli** (Firebase Hosting). Uygulama içinde sadece organizatörün kendi etkinlik yönetimi. |
| Analiz kaynakları | Kullanıcı ölçümleri (il/ilçe) + uydu (VIIRS) + yakındaki karanlık alanlar & gece skoru. |
| Play hesabı | Henüz yok → bkz. §7 (kişisel/kurumsal kararı). |
| Sıralama | Faz 0 (sağlamlaştırma) → Faz 1 (yayın) → Faz 2 (içerik) → Faz 3 (analiz) → Faz 4 (kamera). |

---

## 2. Mevcut Durum Tespiti (v1.1.0)

### 2.1 Güvenlik (yayın engelleyici)
1. Repoda `firestore.rules` yok — tüm yetki kontrolleri istemcide.
2. Kullanıcı kendi `role` alanını yazıyor → herkes `super_admin` olabilir.
3. Herkes her etkinliği bitirebilir/silebilir/katılımcı listesini değiştirebilir.
4. Gizli etkinlik kodları `sessions` sorgusu ile listelenebilir.
5. Ölçüm değerleri doğrulanmıyor (`MPSAS_MIN/MAX` kullanılmıyor), zaman damgası istemciden.
6. Maps API anahtarı manifestte (Android için normal — **Cloud Console'da paket+SHA-1 kısıtlaması şart**).
7. `.gitignore` son satırı UTF-16 bozuk → `google-services.json` commit'lenmiş.

### 2.2 Veri kaybı / hatalı davranış
- Google bağlama başarısız olunca doğrudan Google hesabına giriş → anonim ölçümler sahipsiz kalıyor.
- İnternetsizken `offline_<ts>` sahte UID ile ölçüm kaydı.
- `incrementMeasurementCount` hiç çağrılmıyor → profil istatistikleri 0.
- Katılım/ayrılma "oku-değiştir-yaz" → eşzamanlı katılımda kayıp.
- **Geçmiş ekranı ve CSV dışa aktarma herkesin ölçümlerini gösteriyor** (sadece kendi olmalı).
- Her açılışta global son 500 ölçüm dinleniyor (maliyet + gizlilik + yanlış analiz).
- Harita geohash sorgusu limitsiz (600 km yarıçapta binlerce okuma).
- "Hesabı sıfırla" anonim kullanıcıyı geri dönülmez şekilde kaybediyor.
- Hava durumu: HTTP kodu kontrol edilmiyor, yanıt kapatılmıyor.
- Etkinlik kodu benzersizliği kontrol edilmiyor.

### 2.3 Profesyonellik / Play gereksinimleri
- Release: minify kapalı, imzalama yok, `versionCode=1`, Crashlytics yok, test yok, CI yok.
- `GoogleSignIn` (kaldırıldı) → Credential Manager.
- Uygulama içi **hesap silme** + web üzerinden silme talebi URL'i (Play zorunlu).
- Gizlilik politikası, Veri Güvenliği formu.
- Metinler koda gömülü Türkçe (i18n yok).

---

## 3. Hedef Mimari

```
Android (Compose, MVVM, Hilt)
  ├─ Firebase Auth (Anonim → Google'a yükseltme, Credential Manager)
  ├─ Firestore (offline cache) ── Security Rules (tek yetki kaynağı)
  ├─ App Check (Play Integrity; debug provider)
  ├─ Crashlytics + Analytics
  ├─ Cloud Storage (Faz 4: fotoğraflar) ── Storage Rules
  └─ FCM (Faz 2: bildirimler)

Cloud Functions (Faz 2+)
  ├─ onMeasurementCreate → kullanıcı/etkinlik/bölge sayaçları
  ├─ scheduled: RSS çekme, il/ilçe agregasyonu, gece skoru önbelleği
  ├─ onPhotoUpload → küçük resim + EXIF temizleme + SafeSearch ön filtresi
  └─ admin callable: rol atama (custom claims), organizasyon onayı

Web Admin Paneli (Faz 2, Firebase Hosting)
  ├─ Organizasyon onayı, haber/duyuru yazma
  ├─ Bildirilen içerik kuyruğu (moderasyon)
  └─ Gizlilik politikası + hesap silme talep sayfası (Faz 0'da statik)
```

### 3.1 Yetki modeli
- **Roller custom claims ile** (`admin: true`, `organizer: true`) — kullanıcı belgesinden okunmaz.
  Faz 0'da `tools/set-admin.js` (Admin SDK) ile atanır; Faz 2'de web panelinden.
- `users/{uid}`: sadece sahibi okur/yazar; `role`, `organization_id`, `created_at` istemciden değiştirilemez;
  sayaçlar sadece `+1` artırılabilir (Faz 2'de Cloud Function'a taşınır).
- `sessions/{id}`: oluşturan yönetir; katılımcı sadece **kendini** ekler/çıkarır;
  liste sorgusu sadece `type == public` veya kendi etkinlikleri.
- `session_codes/{code}`: kod → etkinlik eşlemesi; `get` serbest, `list` yasak (kod tahmin/listeleme önlenir,
  belge kimliği olduğu için benzersizlik garanti).
- `measurements/{id}`: herkes okur (açık bilim verisi); oluşturma kuralları: `observer_uid == auth.uid`,
  `sqm_value` aralık kontrolü, `bortle_class` 1–9, `created_at == request.time`,
  `timestamp` (ölçüm anı) en fazla 5 dk ileri / 30 gün geri; güncelleme sadece sahibi ve sadece `note`/`is_test`;
  silme sahibi, etkinlik organizatörü veya admin.

### 3.2 Veri modeli değişiklikleri (geri uyumlu)
- `measurements`: `created_at` (sunucu zamanı) eklenir; `timestamp` ölçüm anı olarak kalır.
- `sessions`: değişmez; `session_codes/{code}` eklenir (`session_id`, `created_by`).
- Eski veriler için `tools/migrate-v2.js` (mevcut etkinliklere kod belgesi üretir).

---

## 4. Fazlar

### Faz 0 — Sağlamlaştırma (Play öncesi, şart)
1. `firestore.rules` + `storage.rules` (şimdilik kapalı) + emulator ile kural testleri.
2. Auth düzeltmeleri: sahte offline UID kaldırılır; Google bağlama çakışmasında veri kaybı yerine açık hata;
   Credential Manager'a geçiş.
3. Veri doğruluğu: ölçüm aralık kontrolü, sunucu zamanı, `session_codes`, transaction ile katılım,
   sayaçların artırılması.
4. Gizlilik/maliyet: Geçmiş + CSV sadece kendi ölçümleri; global 500'lük dinleyici kaldırılır;
   harita sorgusuna limit.
5. Hesap silme (uygulama içi) + gizlilik politikası + silme talep sayfası (statik web).
6. Crashlytics, App Check, R8/minify + proguard kuralları, imzalama yapılandırması, sürümleme.
7. GitHub Actions: derleme + birim testleri + kural testleri.
8. i18n altyapısı: metinler `strings.xml`'e (TR varsayılan + EN).

### Faz 1 — Yayın
- Play Console kurulumu, iç test → kapalı test (kişisel hesapta 12 test kullanıcısı / 14 gün) → üretim.
- Store listesi (TR+EN), ekran görüntüleri, Veri Güvenliği formu, içerik derecelendirmesi.

### Faz 2 — İçerik & Topluluk
- `organizations` + onay akışı, organizatör claim'i.
- `news` koleksiyonu (admin/organizatör yazar) + RSS (NASA APOD, ESA, IDA, TÜBİTAK vb.) zamanlanmış function ile.
- Astronomi takvimi (ay evreleri, meteor yağmurları, tutulmalar — lokal hesap + statik veri).
- FCM: etkinlik hatırlatma, "bu gece gökyüzü açık" bildirimi (konu bazlı).
- Web admin paneli.

### Faz 3 — Analiz
- İl/ilçe sınırları: **geoBoundaries (CC BY 4.0)** veya OSM (GADM ticari kullanıma kapalı).
- Zamanlanmış agregasyon → `region_stats/{il|ilçe}`: ortalama, medyan, eğilim, en karanlık nokta, sıralama.
- Uydu katmanı: **NASA Black Marble / VIIRS** (kamu malı) tile overlay.
  World Atlas 2016 (Falchi) **CC BY-NC** → yalnızca ticari olmayan kullanım; kullanılırsa uygulama ücretsiz kalmalı.
- Yakındaki karanlık alanlar: yüksek SQM ölçümleri + düşük VIIRS radyansı; "bu gece skoru" (bulut + ay + Bortle).

### Faz 4 — Kamera
- **A. Bölge galerisi:** yükleme → Function ile küçük resim, EXIF/GPS temizleme, konumu ~1 km'ye yuvarlama,
  SafeSearch ön filtresi. Play UGC politikası için: kullanım şartları, **içerik bildir**, **kullanıcı engelle**,
  web panelinde moderasyon kuyruğu.
- **B. AI veri seti:** SQM bağlıyken rehberli çekim (zenit, sabit ISO/pozlama — Camera2 manuel, yönelim doğrulama),
  fotoğraf + SQM + cihaz modeli + EXIF eşli kayıt, açık rıza ("araştırmada kullanılabilir").
  Yeterli veri sonrası TFLite modeli → "eğitim amaçlı tahmini Bortle" (doğrulanmamış etiketli).

---

## 5. Test Stratejisi
- Kural testleri: `@firebase/rules-unit-testing` + Firestore emulator (`firebase/` klasörü, `npm test`).
- Birim testleri: BortleScale, SqmProtocol, MoonCalc/SunCalc, CSV kaçışlama, doğrulama mantığı.
- CI her push'ta: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, kural testleri.
- Saha testi kontrol listesi (USB SQM + offline + çoklu cihaz katılımı) Faz 1 öncesi elle.

## 6. Maliyet Tahmini (Blaze, yaklaşık — firebase.google.com/pricing ile doğrula)

| Senaryo | Kullanım | Aylık |
|---|---|---|
| Başlangıç | ~500 aktif kullanıcı, birkaç bin ölçüm, ~1.000 foto | ~0 $ (ücretsiz kota) |
| Büyüme | ~5.000 aktif, ~20 bin ölçüm/ay, ~10 bin foto | ~5–25 $ |
| Mevcut kodla 5.000 kullanıcı | açılış başına 500 okuma | ~40–50 $ sadece okuma |

Önlem: 5 $ ve 20 $ bütçe alarmları, foto sıkıştırma + küçük resim, sayfalı sorgular.

## 7. Açık Konular
- Play geliştirici hesabı: **kişisel** (hızlı, 12 test kullanıcısı/14 gün kapalı test zorunlu) vs
  **kurumsal** (Baak Bilim Kulübü adına, D-U-N-S gerekir, kapalı test zorunluluğu yok).
- Gözlemci adının haritada görünmesi varsayılan olarak açık mı kapalı mı? (Öneri: kapalı, profilde açılabilir.)
- Gizli etkinlik ölçümlerinin haritada gizlenmesi (Faz 2: `visibility` alanı + göç).
