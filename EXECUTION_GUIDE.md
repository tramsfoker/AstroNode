# BAAK Projesi — Uygulama Rehberi
## "Masterplan'ı Aldım, Şimdi Ne Yapıyorum?"

---

## BÖLÜM 1: İlk Gün Kurulumu (Tek Seferlik)

Aşağıdaki adımları **sırayla ve bir kez** yapacaksın. Bunlar tamamlanmadan agent'lara iş verme.

### Adım 1.1 — GitHub Repo Oluştur

```bash
# 1) GitHub'da yeni repo oluştur (boş, README yok)
#    Repo adı: baak-light-pollution
#    Visibility: Private (sonra public yaparsın)

# 2) Lokalde Android Studio ile Empty Compose Activity projesi aç
#    Package: com.baak.lightpollution
#    Min SDK: 24
#    Language: Kotlin

# 3) Git başlat ve bağla
cd /path/to/BaakLightPollution
git init
git remote add origin https://github.com/SENIN_KULLANICIN/baak-light-pollution.git

# 4) .gitignore kontrol et (Android Studio otomatik oluşturur ama kontrol et)
cat .gitignore
# İçinde olması gerekenler:
#   *.iml, .gradle, /local.properties, .idea/, /build,
#   /captures, .externalNativeBuild, google-services.json

# 5) MASTERPLAN.md'yi repo kök dizinine koy
cp ~/Downloads/MASTERPLAN.md ./MASTERPLAN.md

# 6) İlk commit
git add .
git commit -m "chore: initial project setup with MASTERPLAN"
git branch -M main
git push -u origin main
```

### Adım 1.2 — Branch Stratejisi

```
main                          ← Kararlı, çalışan sürüm
  └── develop                 ← Tüm feature'lar buraya merge olur
        ├── feature/f1-theme-ui        ← Faz 1
        ├── feature/f2-sensors         ← Faz 2 (paralel)
        ├── feature/f3-usb-sqm         ← Faz 3 (paralel)
        ├── feature/f4-firebase        ← Faz 4 (paralel)
        ├── feature/f5-orchestration   ← Faz 5 (merge sonrası)
        ├── feature/f6-map             ← Faz 6
        └── feature/f7-polish          ← Faz 7
```

```bash
# develop branch'ını oluştur
git checkout -b develop
git push -u origin develop
```

**Kural:** Her faz kendi branch'ında geliştirilir → develop'a PR ile merge → test → main'e merge.

### Adım 1.3 — Firebase Projesi Kur

Bu adımları **tarayıcıda elle** yapacaksın, agent yapamaz:

```
1. https://console.firebase.google.com adresine git
2. "Add Project" → Proje adı: "baak-light-pollution"
3. Google Analytics: İstersen aç (opsiyonel)
4. Proje oluşturulduktan sonra:

── Authentication Kurulumu ──
5. Sol menü → Build → Authentication → "Get Started"
6. "Sign-in method" sekmesi → "Anonymous" → Enable → Save

── Firestore Kurulumu ──
7. Sol menü → Build → Firestore Database → "Create Database"
8. Location: europe-west1 (Türkiye'ye yakın) veya eur3
9. "Start in test mode" seç (sonra kuralları güncelleyeceğiz)

── Android Uygulaması Ekleme ──
10. Proje ayarları (dişli ikon) → General → "Add App" → Android
11. Package name: com.baak.lightpollution
12. App nickname: BAAK Light Pollution
13. SHA-1: (şimdilik boş bırakabilirsin, Maps için lazım olacak)
14. "Register App" → google-services.json dosyasını indir
15. İndirilen google-services.json'ı projenin app/ dizinine koy

── Firestore Kuralları ──
16. Firestore → Rules sekmesine git
17. MASTERPLAN.md Bölüm 8'deki kuralları yapıştır
18. "Publish"
```

### Adım 1.4 — Google Maps API Key Al

```
1. https://console.cloud.google.com adresine git
2. Firebase projesiyle aynı GCP projesini seç (otomatik bağlı olabilir)
3. APIs & Services → Library → "Maps SDK for Android" → Enable
4. APIs & Services → Credentials → "Create Credentials" → API Key
5. API key'i kısıtla:
   - Application restrictions: Android apps
   - Package: com.baak.lightpollution
   - SHA-1: (debug keystore SHA-1'ini ekle)
6. API key'i not al, agent'a vereceksin

# Debug SHA-1 almak için terminalde:
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
```

### Adım 1.5 — Cursor'da Projeyi Aç

```
1. Cursor'u aç
2. File → Open Folder → Android proje klasörünü seç
3. MASTERPLAN.md'nin proje kök dizininde olduğundan emin ol
4. .cursorrules dosyası oluştur (Adım 2'de anlatılıyor)
```

---

## BÖLÜM 2: Cursor Agent Konfigürasyonu

### 2.1 — `.cursorrules` Dosyası Oluştur

Projenin kök dizinine `.cursorrules` adlı dosya koy. Bu dosya her agent oturumunda otomatik okunur:

```markdown
# BAAK Light Pollution Project — Agent Direktifleri

## Zorunlu Referans
Her görev başlamadan önce MASTERPLAN.md dosyasını oku ve ilgili bölümü referans al.

## Teknoloji Kararları
- Dil: Kotlin, UI: Jetpack Compose (Material3), Mimari: MVVM + Clean Architecture
- DI: Hilt, Async: Coroutines + Flow, USB: usb-serial-for-android
- Backend: Firebase (Anonymous Auth + Firestore)
- Harita: Google Maps SDK (maps-compose)
- Min SDK: 24, Target SDK: 34

## Kod Stili
- Değişken/fonksiyon/sınıf adları: İngilizce
- Yorumlar: Türkçe
- Hardcoded değer yasak → AppConstants.kt kullan
- Force unwrap (!!) yasak → null safety ile çalış
- Callback yasak → suspend function + Flow kullan

## UI Kuralları (KRİTİK)
- SADECE Dark Theme. Color.White veya parlak renk KULLANMA.
- Renk paleti MASTERPLAN.md Bölüm 4.1'de tanımlı, ona uy.
- Tüm renkler tema üzerinden gelmeli, hardcoded hex yasak.

## Dosya Yapısı
MASTERPLAN.md Bölüm 2'deki paket yapısına uy. Yeni dosya oluştururken
doğru pakete koy.

## Git
Değişikliklerin sonunda hangi dosyaları oluşturduğunu/değiştirdiğini listele.
```

### 2.2 — Cursor Settings

Cursor Settings → Features bölümünde:

```
- "Large context" veya "Long context": ON (MASTERPLAN büyük dosya)
- "Codebase indexing": ON (proje yapısını tanısın)
- Model: Claude Sonnet 4 veya GPT-4o (agent modu için)
```

---

## BÖLÜM 3: Faz Faz Uygulama Sırası

### ═══ FAZ 1: Temel Kurulum (Tek Agent, Sıralı) ═══

Bu fazı **tek bir Cursor Composer oturumunda** sırayla yaptır.

**Branch oluştur:**
```bash
git checkout develop
git checkout -b feature/f1-theme-ui
```

**Agent Komutu #1 (T1.1 + T1.5):**
```
@MASTERPLAN.md

Görev: T1.1 ve T1.5'i birlikte yap.

build.gradle.kts (app seviyesi) dosyasına MASTERPLAN Bölüm 1'deki tüm
bağımlılıkları ekle. Hilt kurulumunu yap (@HiltAndroidApp, @AndroidEntryPoint,
boş AppModule). AndroidManifest.xml'e gerekli permission'ları ekle.

Henüz UI veya iş mantığı YAZMA — sadece Gradle + Hilt + Manifest.
Projenin hatasız derlenmesini sağla.
```

**Agent Komutu #2 (T1.2):**
```
@MASTERPLAN.md Bölüm 4.1'i oku.

Görev: T1.2 — Astronomi Modu Tema Sistemi.

core/theme/ paketinde Color.kt, Type.kt, Theme.kt oluştur.
MASTERPLAN'daki hex kodlarını kullan. SADECE darkColorScheme() ile
BaakDarkTheme composable yaz. Light theme YAZMA.
Status bar ve navigation bar rengini #0A0A0A yap.
Font: monospace ailesi.
```

**Agent Komutu #3 (T1.3):**
```
@MASTERPLAN.md Bölüm 4.2'yi oku.

Görev: T1.3 — Navigasyon İskeleti.

ui/navigation/NavGraph.kt oluştur. Route'lar: splash, home, map, history.
Bottom Navigation Bar: Ölçüm, Harita, Geçmiş (3 sekme).
Her ekran şimdilik placeholder Text göstersin.
Splash ekranından Home'a 2 saniyelik delay ile otomatik geçiş.
BaakDarkTheme'i uygula.
```

**Agent Komutu #4 (T1.4):**
```
@MASTERPLAN.md Bölüm 4.3'teki wireframe'i oku.

Görev: T1.4 — Splash ve Home ekranı UI bileşenleri.

SplashScreen.kt: Ortada logo placeholder (Icon), altında uygulama adı, fade-in.
HomeScreen.kt: Wireframe'deki tüm bileşenleri oluştur:
- ConnectionBadge (parametre: isConnected)
- SqmGauge (parametre: mpsasValue, bortleClass)
- OrientationDisplay (parametre: azimuth, pitch, roll)
- MeasureButton (parametre: onClick, isLoading)
- Yönelim toggle switch
- Konum göstergesi
- Not TextField

Hepsi SADECE UI, iş mantığı yok. Placeholder/dummy verilerle @Preview yaz.
Tek bir beyaz piksel bile OLMASIN.
```

**Commit:**
```bash
# Agent'ın çıktısını kontrol et, derle, çalıştır
# Emülatörde aç — karanlık tema, navigasyon çalışmalı

git add .
git commit -m "feat: project setup, dark theme, navigation shell, UI components"
git push origin feature/f1-theme-ui

# develop'a merge
git checkout develop
git merge feature/f1-theme-ui
git push origin develop
```

---

### ═══ FAZ 2, 3, 4: Paralel Çalışma ═══

Faz 1 bittikten sonra bu üç fazı **aynı anda** başlatabilirsin.

#### Yöntem A: Cursor'da Sıralı Ama Hızlı (Tek Kişi)

Tek kişiysen paralel branch'larda çalış, ama sırayla agent'a ver:

```bash
# Üç branch'ı birden oluştur
git checkout develop
git checkout -b feature/f2-sensors
# ... f2 işini yap, commit, develop'a geri dön ...
git checkout develop
git checkout -b feature/f3-usb-sqm
# ... f3 işini yap, commit ...
git checkout develop
git checkout -b feature/f4-firebase
# ... f4 işini yap, commit ...
```

#### Yöntem B: Cursor Multi-Tab (Paralel Agent)

Cursor'da birden fazla Composer penceresi açabilirsin:

```
Tab 1 (Agent B): Faz 2 — Sensörler
Tab 2 (Agent C): Faz 3 — USB/SQM
Tab 3 (Agent D): Faz 4 — Firebase
```

⚠️ **Dikkat:** Aynı dosyayı iki agent aynı anda değiştirirse conflict olur. Bu fazlar farklı paketlerde çalıştığı için sorun olmaz:
- Agent B → `data/sensor/` paketi
- Agent C → `data/usb/` paketi  
- Agent D → `data/firebase/` paketi

**Çakışma riski olan tek dosya:** `AppModule.kt` (Hilt provides). Bunu en son tek agent'a birleştirt.

---

**FAZ 2 — Agent Komutu (Sensörler):**

```bash
git checkout develop && git checkout -b feature/f2-sensors
```

```
@MASTERPLAN.md Bölüm 7, Faz 2'yi oku (T2.1, T2.2, T2.3).

Görev: Tüm Faz 2'yi yap.

1) data/sensor/LocationProvider.kt oluştur:
   - FusedLocationProviderClient kullan
   - StateFlow<LocationData?> olarak konum yayınla
   - Runtime permission handling ekle
   - Hilt @Singleton ile provide et

2) data/sensor/OrientationProvider.kt oluştur:
   - TYPE_ROTATION_VECTOR kullan
   - getRotationMatrixFromVector → getOrientation ile azimuth/pitch/roll hesapla
   - Low-pass filtre (alpha=0.15)
   - StateFlow<OrientationData?> olarak yayınla
   - Hilt @Singleton ile provide et

3) HomeViewModel'ı güncelle:
   - Her iki provider'ı inject et
   - UI state'e bağla
   - Yönelim toggle'ını çalışır hale getir

Sonunda oluşturduğun/değiştirdiğin dosyaların listesini ver.
```

```bash
# Test: Emülatörde konum simüle et, sensör değerleri değişmeli
git add . && git commit -m "feat: GPS location + orientation sensor providers"
git push origin feature/f2-sensors
```

---

**FAZ 3 — Agent Komutu (USB/SQM):**

```bash
git checkout develop && git checkout -b feature/f3-usb-sqm
```

```
@MASTERPLAN.md Bölüm 5 (SQM Protokol) ve Bölüm 7 Faz 3'ü oku (T3.1, T3.2, T3.3).

Görev: Tüm Faz 3'ü yap.

1) data/usb/SqmProtocol.kt (saf Kotlin, Android bağımsız):
   - buildReadCommand(): "rx\r" byte array
   - parseResponse(raw): regex ile MPSAS değeri çıkar
   - data class SqmReading(mpsas, temperature, rawResponse)
   - Unit test yaz (test/SqmProtocolTest.kt)

2) data/usb/SqmUsbManager.kt:
   - usb-serial-for-android kütüphanesi
   - USB attach/detach broadcast receiver
   - USB permission request akışı
   - StateFlow<UsbConnectionState> (DISCONNECTED, PERMISSION_PENDING, CONNECTED, ERROR)
   - Seri port: 115200 baud, 8N1
   - suspend fun readMeasurement(): SqmReading?
   - Hilt @Singleton

3) res/xml/device_filter.xml oluştur (SQM USB cihazları için)

4) HomeViewModel'da USB state'i ConnectionBadge'e bağla.
   ÖLÇÜM YAP butonuna basınca readMeasurement çağır.

Sonunda dosya listesini ver.
```

```bash
# Test: Gerçek SQM cihazıyla veya mock ile
git add . && git commit -m "feat: USB OTG SQM serial communication"
git push origin feature/f3-usb-sqm
```

---

**FAZ 4 — Agent Komutu (Firebase):**

```bash
git checkout develop && git checkout -b feature/f4-firebase
```

```
@MASTERPLAN.md Bölüm 3.2 (Firestore yapısı) ve Bölüm 7 Faz 4'ü oku (T4.1, T4.2).

Görev: Tüm Faz 4'ü yap.

ÖNEMLİ: google-services.json zaten app/ dizininde.

1) data/firebase/FirebaseAuthManager.kt:
   - suspend fun ensureAnonymousAuth(): String (UID döndürür)
   - Uygulama açılışında otomatik anonim giriş
   - Hilt @Singleton

2) data/firebase/FirestoreManager.kt:
   - suspend fun saveMeasurement(SkyMeasurement): Result<String>
     → "measurements" koleksiyonuna yaz, GeoPoint kullan
   - fun getMeasurements(): Flow<List<SkyMeasurement>>
     → snapshotListener ile gerçek zamanlı, son 500
   - fun getMeasurementsInBounds(sw, ne): Flow<List<SkyMeasurement>>
     → Harita sınırlarındaki verileri çek

3) core/model/SkyMeasurement.kt data class'ını oluştur
   (MASTERPLAN Bölüm 3.1'deki yapı)

4) core/util/BortleScale.kt: MPSAS → Bortle dönüşüm fonksiyonu
   + Unit test yaz

Sonunda dosya listesini ver.
```

```bash
# Test: Firestore Console'da veri görünmeli
git add . && git commit -m "feat: Firebase anonymous auth + Firestore CRUD"
git push origin feature/f4-firebase
```

---

### ═══ Paralel Branch'ları Birleştirme ═══

Üç faz da bittikten sonra:

```bash
# develop'a merge (sırayla)
git checkout develop

git merge feature/f2-sensors
# Conflict varsa çöz (genelde olmaz)

git merge feature/f3-usb-sqm
# Conflict varsa çöz

git merge feature/f4-firebase
# Muhtemel conflict: AppModule.kt → elle birleştir

git push origin develop
```

**Conflict olursa agent'a ver:**
```
Bu üç branch'ı develop'a merge ettim, AppModule.kt'de conflict var.
Üç branch'ın eklediği Hilt @Provides fonksiyonlarını tek bir
AppModule.kt'de birleştir. Hiçbirini silme.
```

---

### ═══ FAZ 5: Orkestrasyon (Merge Sonrası) ═══

```bash
git checkout develop && git checkout -b feature/f5-orchestration
```

```
@MASTERPLAN.md Bölüm 7, T5.1'i oku.

Görev: T5.1 — TakeMeasurementUseCase ve tam entegrasyon.

develop branch'ında şu anda sensörler, USB/SQM ve Firebase ayrı çalışıyor.
Bunları birleştiren use case yaz:

domain/usecase/TakeMeasurementUseCase.kt:
  1. SqmUsbManager.readMeasurement() → MPSAS
  2. BortleScale.toBortle(mpsas) → Bortle sınıfı
  3. LocationProvider'dan anlık konum al
  4. OrientationProvider'dan açılar al (toggle açıksa)
  5. SkyMeasurement oluştur
  6. FirestoreManager.saveMeasurement() → Firestore'a yaz
  7. Result<SkyMeasurement> döndür

HomeViewModel'ı güncelle:
  - ÖLÇÜM YAP → TakeMeasurementUseCase çağır
  - Loading, success, error state'leri yönet
  - Başarılıysa haptic feedback (Vibrator)
```

```bash
git add . && git commit -m "feat: full measurement orchestration pipeline"
git push origin feature/f5-orchestration
git checkout develop && git merge feature/f5-orchestration
git push origin develop
```

---

### ═══ FAZ 6: Harita (F5 Sonrası) ═══

```bash
git checkout develop && git checkout -b feature/f6-map
```

```
@MASTERPLAN.md Bölüm 7, Faz 6'yı oku (T6.1, T6.2, T6.3).
@MASTERPLAN.md Bölüm 3.3 Bortle renk tablosu.

Görev: Tüm Faz 6'yı yap.

Google Maps API Key: [BURAYA API KEY'İNİ YAPIŞTIR]

1) AndroidManifest.xml'e Maps API key meta-data ekle
2) MapScreen.kt: maps-compose ile GoogleMap
   - Gece modu harita stili (karanlık JSON)
   - Başlangıç kamera: Türkiye (39.9, 32.8, zoom 6)
   - "Konumuma Git" FAB butonu
3) MapViewModel.kt: Firestore'dan veri çek
4) Her ölçüm noktası = renkli daire (Bortle renk kodlarıyla)
5) Tıklanınca InfoWindow: MPSAS, Bortle, tarih, not
6) Marker clustering (500+ nokta performansı için)
7) Isı haritası toggle (HeatmapTileProvider)
```

---

### ═══ FAZ 7: Cilalama ═══

```bash
git checkout develop && git checkout -b feature/f7-polish
```

```
@MASTERPLAN.md Bölüm 7, Faz 7'yi oku (T7.1, T7.2, T7.3).

Görev: Tüm Faz 7'yi yap.

1) HistoryScreen.kt: LazyColumn ile ölçüm geçmişi
   - Tarih, MPSAS, Bortle rozeti, konum
   - Tıklayınca haritada o noktaya git

2) ExportDataUseCase.kt: CSV dışa aktarım
   - Sütunlar: timestamp, sqm_value, bortle, lat, lng, altitude, az, pitch, roll, note
   - ShareSheet ile paylaşma

3) Edge case'ler:
   - USB kopma → graceful hata
   - GPS kapalı → ayarlara yönlendir
   - İnternet yok → offline bildirimi
   - Ölçüm sırasında ekran açık kalma
   - Uygulama ikonu (adaptive icon)
```

```bash
git add . && git commit -m "feat: history, CSV export, error handling, polish"
git push origin feature/f7-polish
git checkout develop && git merge feature/f7-polish

# Son: develop → main
git checkout main && git merge develop
git tag v1.0.0
git push origin main --tags
```

---

## BÖLÜM 4: Agent'a Komut Verme İpuçları

### Altın Kurallar

```
1. HER ZAMAN @MASTERPLAN.md referansıyla başla
2. Tek seferde tek faz veya en fazla 2-3 task ver
3. "Tüm uygulamayı yaz" DEMEYİN — çok büyük, hata yapar
4. Çıktı bekle → derle → test et → sonra sıradaki görev
5. Hata varsa hatayı yapıştır, "düzelt" de
```

### Agent Hata Yaptığında

```
Bu dosyada hata var: [HATA MESAJINI YAPIŞTIR]

@MASTERPLAN.md'deki Bölüm X'e göre düzelt.
Sadece hatalı kısmı değiştir, çalışan kodu bozma.
```

### Agent Yanlış Renk/Tema Kullandığında

```
KRİTİK HATA: Bu ekranda beyaz/parlak renk kullanmışsın.
@MASTERPLAN.md Bölüm 4.1'deki renk paletini oku.
Tüm Color.White, #FFFFFF ve parlak renkleri kaldır.
Sadece tema renklerini kullan.
```

### Agent Monolitik Kod Yazdığında

```
Bu kodu MASTERPLAN'daki paket yapısına göre böl:
- USB mantığı → data/usb/
- Sensör mantığı → data/sensor/
- Firebase mantığı → data/firebase/
- ViewModel doğrudan Firebase çağırmamalı → Repository pattern
```

---

## BÖLÜM 5: Firebase Geliştirme Sırasında İpuçları

### Test Verisi Ekleme (Console'dan)

Firestore Console → measurements → "Add Document":

```json
{
  "sqm_value": 21.53,
  "bortle_class": 4,
  "location": [GeoPoint: 39.93, 32.86],
  "altitude": 894,
  "orientation": {
    "azimuth": 180,
    "pitch": 75,
    "roll": 0,
    "enabled": true
  },
  "timestamp": [Timestamp: now],
  "device_id": "test_device_001",
  "note": "Test verisi - Ankara"
}
```

### Firestore Debug Logları

```kotlin
// Geliştirme sırasında Firestore debug loglarını aç
// Bu satırı Application.onCreate() içine koy:
FirebaseFirestore.setLoggingEnabled(true)
```

### Kuralları Test Etme

Firebase Console → Firestore → Rules → "Rules Playground" sekmesi:
- Simulate read/write işlemlerini test edebilirsin
- Auth olmadan write denemesi → deny olmalı

### Dikkat: Fiyatlandırma

```
Firestore ücretsiz kotalar (günlük):
- 50.000 okuma
- 20.000 yazma
- 1 GB depolama

Geliştirme sırasında snapshotListener sürekli okuma yapar.
Geliştirirken listener'ları kapatmayı unutma.
Billing alarm kur: $0 bütçe, e-posta bildirimi.
```

---

## BÖLÜM 6: Kontrol Listesi & İlerleme Takibi

Her task'ı bitirince bu listeyi güncelle:

```
FAZ 1: Temel Kurulum
  [ ] T1.1 — Gradle + Dependencies + Manifest
  [ ] T1.2 — Dark Theme (Color, Type, Theme)
  [ ] T1.3 — Navigation (4 ekran + Bottom Nav)
  [ ] T1.4 — Splash + Home UI bileşenleri
  [ ] T1.5 — Hilt DI altyapısı
  [ ] → Git commit + develop merge ✓

FAZ 2: Sensörler (Paralel)
  [ ] T2.1 — LocationProvider
  [ ] T2.2 — OrientationProvider
  [ ] T2.3 — Sensör → UI bağlama
  [ ] → Git commit ✓

FAZ 3: USB/SQM (Paralel)
  [ ] T3.1 — SqmUsbManager
  [ ] T3.2 — SqmProtocol + Unit Test
  [ ] T3.3 — SQM → UI bağlama
  [ ] → Git commit ✓

FAZ 4: Firebase (Paralel)
  [ ] T4.1 — Auth + Firestore kurulumu
  [ ] T4.2 — FirestoreManager CRUD
  [ ] → Git commit ✓

  [ ] ═══ Paralel branch merge ═══

FAZ 5: Orkestrasyon
  [ ] T5.1 — TakeMeasurementUseCase
  [ ] → Git commit ✓

FAZ 6: Harita
  [ ] T6.1 — Temel harita ekranı
  [ ] T6.2 — Veri noktaları gösterimi
  [ ] T6.3 — Isı haritası
  [ ] → Git commit ✓

FAZ 7: Cilalama
  [ ] T7.1 — Geçmiş ekranı
  [ ] T7.2 — CSV export
  [ ] T7.3 — Edge case'ler + ikon
  [ ] → Git commit + v1.0.0 tag ✓
```

---

## BÖLÜM 7: Sık Karşılaşılan Sorunlar

| Sorun | Çözüm |
|-------|-------|
| `google-services.json not found` | app/ dizinine koy, .gitignore'dan çıkarMA (güvenlik) |
| USB OTG emülatörde çalışmıyor | Gerçek cihazda test et, emülatör USB Host desteklemez |
| Firestore permission denied | Auth yapılmadan write denenmiş → ensureAnonymousAuth() çağır |
| Compose Preview karanlık görünmüyor | Preview'a `@Preview(uiMode = UI_MODE_NIGHT_YES)` ekle |
| Maps API key çalışmıyor | SHA-1 fingerprint eşleşiyor mu kontrol et |
| Sensör emülatörde null | Extended Controls → Virtual Sensors'tan simüle et |
| Branch merge conflict | `AppModule.kt` en sık çakışır, elle birleştir |
| Gradle sync hata | `File → Invalidate Caches → Restart` dene |
