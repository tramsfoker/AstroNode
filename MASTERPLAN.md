# BAAK Işık Kirliliği Ölçüm & Haritalama — Orkestrasyon Master Planı

> **Versiyon:** 2.0  
> **Son Güncelleme:** 2026-03-05  
> **Proje Sahibi:** Baak Bilim Kulübü  
> **Hedef Platform:** Android (Min SDK 24, Target SDK 34)

---

## 0. Terminoloji & Kısaltmalar

| Kısaltma | Açıklama |
|----------|----------|
| SQM | Sky Quality Meter — gökyüzü parlaklık ölçer (Mag/arcsec² çıktısı) |
| OTG | USB On-The-Go — telefonu USB Host yapan adaptör |
| MPSAS | Magnitudes Per Square Arcsecond — SQM ölçü birimi |
| Bortle | Bortle Dark-Sky Scale (1-9) — ışık kirliliği sınıflandırma skalası |

---

## 1. Teknik Yığın (Tech Stack)

| Katman | Teknoloji | Notlar |
|--------|-----------|--------|
| Dil | Kotlin 1.9+ | Coroutines + Flow zorunlu |
| UI | Jetpack Compose (BOM 2024.x) | Material3, SADECE Dark Theme |
| Mimari | MVVM + Clean Architecture | Repository pattern, Use Case'ler |
| DI | Hilt (Dagger) | Tüm Manager sınıfları inject edilecek |
| Navigasyon | Compose Navigation | Tek Activity, çoklu Screen |
| USB Serial | usb-serial-for-android (mik3y) | SQM cihazıyla UART iletişimi |
| Sensörler | Android SensorManager | TYPE_ROTATION_VECTOR + TYPE_MAGNETIC_FIELD |
| Konum | Google FusedLocationProvider | Fine + Coarse permission |
| Backend | Firebase (Auth + Firestore) | Anonymous Auth, NoSQL belge modeli |
| Harita | Google Maps SDK (Compose) | maps-compose kütüphanesi |
| Async | Kotlin Coroutines + Flow | StateFlow → UI, SharedFlow → events |

---

## 2. Proje Dizin Yapısı (Package Convention)

```
com.baak.lightpollution/
├── App.kt                          # Hilt Application sınıfı
├── MainActivity.kt                 # Tek Activity, Compose host
│
├── core/                           # Paylaşılan katman
│   ├── theme/
│   │   ├── Color.kt                # Astronomi modu renk paleti
│   │   ├── Type.kt                 # Tipografi
│   │   └── Theme.kt                # BaakDarkTheme (TEK tema)
│   ├── constants/
│   │   └── AppConstants.kt         # SQM baud rate, Firestore collection adları vb.
│   ├── model/
│   │   └── SkyMeasurement.kt       # Ana veri sınıfı (data class)
│   └── util/
│       ├── BortleScale.kt          # MPSAS → Bortle dönüşüm fonksiyonları
│       └── OrientationUtils.kt     # Açı hesaplama yardımcıları
│
├── data/                           # Veri katmanı (Repository implementasyonları)
│   ├── repository/
│   │   ├── MeasurementRepositoryImpl.kt
│   │   └── SensorRepositoryImpl.kt
│   ├── usb/
│   │   ├── SqmUsbManager.kt        # USB bağlantı, okuma, parse
│   │   └── SqmProtocol.kt          # SQM komut/cevap parse kuralları
│   ├── sensor/
│   │   ├── LocationProvider.kt      # FusedLocation sarmalayıcısı
│   │   └── OrientationProvider.kt   # Rotation vector → Azimuth/Pitch/Roll
│   └── firebase/
│       ├── FirebaseAuthManager.kt   # Anonim giriş
│       └── FirestoreManager.kt      # CRUD operasyonları
│
├── domain/                          # İş mantığı (saf Kotlin, Android bağımsız)
│   ├── repository/
│   │   ├── MeasurementRepository.kt # Interface
│   │   └── SensorRepository.kt      # Interface
│   └── usecase/
│       ├── TakeMeasurementUseCase.kt    # SQM oku + sensör + kaydet
│       ├── GetMeasurementsUseCase.kt    # Firestore'dan çek
│       └── ExportDataUseCase.kt         # CSV dışa aktarım
│
└── ui/                              # Sunum katmanı
    ├── navigation/
    │   └── NavGraph.kt              # Screen route'ları
    ├── screen/
    │   ├── splash/
    │   │   └── SplashScreen.kt      # Baak logosu animasyonu
    │   ├── home/
    │   │   ├── HomeScreen.kt        # Ana ölçüm ekranı
    │   │   └── HomeViewModel.kt
    │   ├── map/
    │   │   ├── MapScreen.kt         # Isı haritası görselleştirme
    │   │   └── MapViewModel.kt
    │   └── history/
    │       ├── HistoryScreen.kt     # Geçmiş ölçümler listesi
    │       └── HistoryViewModel.kt
    └── component/
        ├── SqmGauge.kt             # Büyük dairesel MPSAS göstergesi
        ├── OrientationDisplay.kt    # Azimuth/Pitch/Roll küçük widget
        ├── ConnectionBadge.kt       # USB bağlı/bağlı değil rozeti
        └── MeasureButton.kt         # TEK BÜYÜK ölçüm butonu
```

---

## 3. Veri Modeli

### 3.1 Yerel Data Class

```kotlin
data class SkyMeasurement(
    val id: String = UUID.randomUUID().toString(),
    val sqmValue: Double,               // MPSAS (örn: 21.5)
    val bortleClass: Int,               // 1-9 (sqmValue'dan hesaplanır)
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,              // GPS'ten opsiyonel
    val azimuth: Float?,                // 0-360° (kuzeyden saat yönü)
    val pitch: Float?,                  // -90 (aşağı) ile +90 (yukarı)
    val roll: Float?,                   // -180 ile +180
    val orientationEnabled: Boolean,    // Kullanıcı toggle'ı açtı mı?
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String,               // Firebase anonymous UID
    val note: String? = null            // Opsiyonel kullanıcı notu
)
```

### 3.2 Firestore Belge Yapısı

**Koleksiyon:** `measurements`

```json
{
  "sqm_value": 21.53,
  "bortle_class": 4,
  "location": { "lat": 39.6345, "lng": 32.8597 },  // GeoPoint
  "altitude": 894.2,
  "orientation": {
    "azimuth": 182.4,
    "pitch": 78.5,
    "roll": -2.1,
    "enabled": true
  },
  "timestamp": "2026-03-05T22:15:00Z",   // Firestore Timestamp
  "device_id": "anon_abc123",
  "note": "Gölbaşı gözlem alanı, bulutsuz"
}
```

### 3.3 MPSAS → Bortle Dönüşüm Tablosu

| Bortle | MPSAS Aralığı | Renk Kodu | Açıklama |
|--------|--------------|-----------|----------|
| 1 | ≥ 21.99 | #000033 | Mükemmel karanlık alan |
| 2 | 21.89 – 21.99 | #000066 | Tipik karanlık alan |
| 3 | 21.69 – 21.89 | #003399 | Kırsal gökyüzü |
| 4 | 20.49 – 21.69 | #006633 | Kırsal/banliyö geçişi |
| 5 | 19.50 – 20.49 | #669900 | Banliyö |
| 6 | 18.94 – 19.50 | #CCCC00 | Parlak banliyö |
| 7 | 18.38 – 18.94 | #CC6600 | Banliyö/şehir geçişi |
| 8 | 17.80 – 18.38 | #CC3300 | Şehir gökyüzü |
| 9 | < 17.80 | #CC0000 | Şehir merkezi |

---

## 4. UI/UX Spesifikasyonu

### 4.1 Astronomi Modu Renk Paleti (Zorunlu)

```
Arka Plan (Surface):      #0A0A0A (neredeyse siyah)
Kart Arka Planı:          #1A1A1A
Birincil Vurgu:           #CC2200 (koyu kırmızı — gece görüşü dostu)
İkincil Vurgu:            #1A3A5C (koyu mavi)
Metin (Birincil):         #CC4444 (soluk kırmızı)
Metin (İkincil):          #884444 (daha koyu kırmızı)
Başarı/Bağlı:             #336633 (koyu yeşil)
Uyarı:                    #665522 (koyu amber)
Hata/Bağlı Değil:         #663333 (koyu kırmızı)
Devre Dışı:               #333333
```

> **KESİN KURAL:** Beyaz (#FFFFFF) veya parlak renkler hiçbir ekranda, hiçbir durumda KULLANILMAYACAKTIR. System bars dahil her şey karanlık olmalıdır.

### 4.2 Ekran Akışı

```
[Splash] ──→ [Home / Ölçüm] ──→ [Harita]
                    │
                    └──→ [Geçmiş]
```

### 4.3 Home (Ölçüm) Ekranı Wireframe

```
┌──────────────────────────────┐
│  ☰  BAAK BİLİM KULÜBÜ   ⚙  │  ← Üst bar + logo
├──────────────────────────────┤
│                              │
│     USB: ● Bağlı (CP2102)   │  ← ConnectionBadge
│                              │
│    ┌──────────────────┐      │
│    │                  │      │
│    │     21.53        │      │  ← SqmGauge (büyük dairesel)
│    │   Mag/arcsec²    │      │
│    │   Bortle 4 🟢    │      │
│    │                  │      │
│    └──────────────────┘      │
│                              │
│  Az: 182°  Pitch: 78°  R: -2│  ← OrientationDisplay
│  [Yönelim Verisini Ekle: ON]│  ← Toggle switch
│                              │
│  📍 39.6345, 32.8597        │  ← Konum göstergesi
│                              │
│    ╔══════════════════╗      │
│    ║                  ║      │
│    ║    ÖLÇÜM YAP     ║      │  ← MeasureButton (büyük, kırmızı)
│    ║                  ║      │
│    ╚══════════════════╝      │
│                              │
│  [Not ekle... (opsiyonel)]   │  ← TextField
│                              │
├──────────────────────────────┤
│  🏠 Ölçüm    🗺 Harita   📋 │  ← Bottom nav
└──────────────────────────────┘
```

---

## 5. SQM Protokol Spesifikasyonu

### 5.1 Seri Port Ayarları

| Parametre | Değer |
|-----------|-------|
| Baud Rate | 115200 |
| Data Bits | 8 |
| Parity | None |
| Stop Bits | 1 |
| Flow Control | None |

### 5.2 Komut/Cevap Formatı

**Ölçüm İsteme Komutu:** `rx\r` (ASCII, CR ile biter)

**Cevap Formatı (örnek):**
```
r, 21.53m,0000000002Hz,0000000000c,0000025.3s, 027.2C\r
```

**Parse Kuralları:**
```
Alan 1: "r"                  → Komut echo
Alan 2: " 21.53m"            → MPSAS değeri (trim + "m" kaldır → 21.53)
Alan 3: "0000000002Hz"       → Frekans (genelde kullanılmaz)
Alan 4: "0000000000c"        → Sayım (genelde kullanılmaz)
Alan 5: "0000025.3s"         → Periyot
Alan 6: " 027.2C"            → Sensör sıcaklığı
```

**Ayrıştırma Regex'i:**
```kotlin
val sqmRegex = Regex("""r,\s*([0-9.]+)m,""")
val matchResult = sqmRegex.find(responseString)
val mpsasValue = matchResult?.groupValues?.get(1)?.toDoubleOrNull()
```

---

## 6. Görev Haritası — Bağımlılık Grafiği

```
T1.1 ─┐
T1.2 ─┤
T1.3 ─┼──→ T1.4 (Tema & Temel UI İskeleti tamamlanır)
T1.5 ─┘         │
                 ├──→ T2.1 ──→ T2.2 ──→ T2.3 (Sensör Katmanı)
                 │
                 ├──→ T3.1 ──→ T3.2 ──→ T3.3 (USB / SQM Katmanı)
                 │
                 └──→ T4.1 ──→ T4.2 (Firebase Katmanı)
                           │
                           ▼
                  T5.1 (Orkestrasyon: Hepsini Birleştir)
                           │
                           ▼
                  T6.1 ──→ T6.2 ──→ T6.3 (Harita Katmanı)
                           │
                           ▼
                  T7.1 ──→ T7.2 ──→ T7.3 (Son Rötuşlar)
```

### Paralel Çalışma Haritası

```
Zaman ──────────────────────────────────────────────────▶

Agent A: [T1.1][T1.2][T1.3][T1.4]...[T5.1]...[T7.1][T7.2][T7.3]
Agent B:                   [T2.1][T2.2][T2.3]↗
Agent C:                   [T3.1][T3.2][T3.3]↗
Agent D:                   [T4.1][T4.2].......[T6.1][T6.2][T6.3]↗
```

> **Agent A** = UI & Entegrasyon, **Agent B** = Sensörler, **Agent C** = USB/SQM, **Agent D** = Firebase & Harita

---

## 7. Görev Tanımları (Atomik Task'lar)

---

### FAZ 1: Temel Kurulum & UI İskeleti

#### T1.1 — Proje Oluşturma & Gradle Yapılandırması
- **Bağımlılık:** Yok (Başlangıç noktası)  
- **Çıktı:** Derlenen boş Android projesi  
- **Detay:**
  - Android Studio'da `com.baak.lightpollution` paketli Empty Compose Activity oluştur
  - `build.gradle.kts` (app) içine tüm bağımlılıkları ekle:
    - Compose BOM, Material3, Navigation-Compose
    - Hilt (kapt veya ksp), hilt-navigation-compose
    - Google Maps Compose (`maps-compose`), Google Play Services Location
    - Firebase BOM (Auth, Firestore)
    - usb-serial-for-android (`com.github.mik3y:usb-serial-for-android:3.7.0`)
    - Coroutines (core + android)
  - `AndroidManifest.xml` permission'ları ekle: `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `USB_HOST`
  - `<uses-feature android:name="android.hardware.usb.host" android:required="false"/>`
  - Projeyi derle, sıfır hata olduğundan emin ol

#### T1.2 — Astronomi Modu Tema Sistemi
- **Bağımlılık:** T1.1  
- **Çıktı:** `core/theme/` paketi (Color.kt, Type.kt, Theme.kt)  
- **Detay:**
  - Bölüm 4.1'deki renk paletini `Color.kt`'ye tanımla
  - `darkColorScheme()` kullanarak tek bir `BaakDarkTheme` composable yaz
  - **Light theme composable YAZMA**, sadece dark
  - Status bar ve navigation bar renklerini `#0A0A0A` yap (system UI controller ile)
  - `Type.kt`'de font ailesi tanımla (monospace tercih edilir — astronomi hissi)
  - Doğrulama: Preview'da hiçbir yerde beyaz/parlak alan olmamalı

#### T1.3 — Navigasyon İskeleti
- **Bağımlılık:** T1.1  
- **Çıktı:** Çalışan 4 ekranlı navigasyon (boş ekranlar)  
- **Detay:**
  - `NavGraph.kt` oluştur, route'lar: `splash`, `home`, `map`, `history`
  - Bottom Navigation Bar (3 sekme: Ölçüm, Harita, Geçmiş)
  - Splash → Home otomatik geçiş (2 saniye delay veya logo animasyonu bitince)
  - Her ekranda placeholder `Text("Ekran Adı")` yeterli

#### T1.4 — Splash Ekranı & Home Ekranı UI Bileşenleri
- **Bağımlılık:** T1.2, T1.3  
- **Çıktı:** Görsel olarak tamamlanmış Splash ve Home ekranları  
- **Detay:**
  - **SplashScreen:** Ortada Baak Bilim Kulübü logosu (drawable olarak ekle veya placeholder ikon), altında "Işık Kirliliği Haritalama" yazısı, fade-in animasyonu
  - **HomeScreen UI Shell (veri bağlamadan):**
    - `ConnectionBadge`: "USB: Bağlı Değil" (kırmızı nokta) — parametre ile değişken
    - `SqmGauge`: Büyük daire içinde "—" ve "Mag/arcsec²" (veri gelince güncellenecek)
    - `OrientationDisplay`: Az/Pitch/Roll değerleri "—" ile placeholder
    - "Yönelim Verisini Ekle" Toggle/Switch
    - Konum göstergesi: "📍 Konum alınıyor..."
    - `MeasureButton`: Büyük, koyu kırmızı (#CC2200), köşeleri yuvarlak, "ÖLÇÜM YAP" yazılı
    - Opsiyonel not TextField'ı
  - Tüm bileşenler `@Preview` ile test edilmeli
  - **Bileşenler sadece UI — iş mantığı veya sensör bağlantısı YOK**

#### T1.5 — Hilt DI Kurulumu
- **Bağımlılık:** T1.1  
- **Çıktı:** Çalışan Hilt altyapısı  
- **Detay:**
  - `@HiltAndroidApp` Application sınıfı
  - `@AndroidEntryPoint` MainActivity
  - Boş `AppModule` (@Module @InstallIn(SingletonComponent)) oluştur
  - ViewModel'lar için `@HiltViewModel` kullanılacak (henüz oluşturma, altyapı yeterli)

**✅ FAZ 1 TAMAMLAMA KRİTERİ:** Uygulama açılıyor, splash gösteriyor, Home ekranına geçiyor. Tüm UI bileşenleri placeholder verilerle görünür. Hiçbir yerde beyaz ekran yok. Alt navigasyon çalışıyor.

---

### FAZ 2: Sensör Katmanı (Konum & Yönelim) ⟨Agent B — Paralel⟩

#### T2.1 — Konum Sağlayıcısı (LocationProvider)
- **Bağımlılık:** T1.5 (Hilt)  
- **Çıktı:** `data/sensor/LocationProvider.kt`  
- **Detay:**
  - `FusedLocationProviderClient` kullan
  - `StateFlow<LocationData?>` olarak konum yayınla (data class: lat, lng, altitude, accuracy)
  - Runtime permission handling (ACCESS_FINE_LOCATION):
    - `ActivityResultContracts.RequestMultiplePermissions` kullan
    - İzin reddedilirse kullanıcıya açıklayıcı dialog göster
  - Konum güncellemesi: `LocationRequest` ile her 5 saniyede veya 10m değişimde
  - `startUpdates()` / `stopUpdates()` yaşam döngüsüne uyumlu
  - Hilt ile `@Singleton` olarak provide et

#### T2.2 — Yönelim Sağlayıcısı (OrientationProvider)
- **Bağımlılık:** T1.5 (Hilt)  
- **Çıktı:** `data/sensor/OrientationProvider.kt`  
- **Detay:**
  - `SensorManager` üzerinden `TYPE_ROTATION_VECTOR` dinle (daha stabil, jiroskop + manyetometre füzyonu)
  - `SensorManager.getRotationMatrixFromVector()` → `SensorManager.getOrientation()` ile:
    - **Azimuth** (0-360°): Kuzeyden saat yönü pusula açısı
    - **Pitch** (-90 ile +90°): Telefonun yukarı/aşağı eğimi
    - **Roll** (-180 ile +180°): Telefonun yana yatması
  - `StateFlow<OrientationData?>` olarak yayınla (data class: azimuth, pitch, roll)
  - Low-pass filtre uygula (gürültü azaltma): `alpha = 0.15f`
  - Sensör olmayan cihazlar için null döndür ve UI'da uyar
  - `startListening()` / `stopListening()` yaşam döngüsüne uyumlu

#### T2.3 — Sensör Verilerinin UI'a Bağlanması
- **Bağımlılık:** T2.1, T2.2, T1.4  
- **Çıktı:** HomeViewModel'da sensör verileri canlı olarak görünür  
- **Detay:**
  - `HomeViewModel` içinde `LocationProvider` ve `OrientationProvider` inject et
  - UI state'e ekle: `locationState: StateFlow<LocationData?>`, `orientationState: StateFlow<OrientationData?>`
  - `HomeScreen`'de:
    - Konum göstergesi gerçek lat/lng göstersin
    - `OrientationDisplay` gerçek azimuth/pitch/roll göstersin
    - "Yönelim Verisini Ekle" toggle'ı: OFF iken `OrientationProvider.stopListening()` çağır ve açıları gizle
  - Permission akışı: Uygulama ilk açılışta konum izni istesin

**✅ FAZ 2 TAMAMLAMA KRİTERİ:** Telefonu hareket ettirince azimuth/pitch/roll değerleri canlı değişiyor. Konum doğru enlem/boylam gösteriyor. Yönelim toggle'ı çalışıyor.

---

### FAZ 3: USB OTG & SQM İletişimi ⟨Agent C — Paralel⟩

#### T3.1 — USB Bağlantı Yöneticisi (SqmUsbManager)
- **Bağımlılık:** T1.5 (Hilt)  
- **Çıktı:** `data/usb/SqmUsbManager.kt`  
- **Detay:**
  - `usb-serial-for-android` kütüphanesini kullan
  - USB cihaz ekleme/çıkarma broadcast receiver:
    - `UsbManager.ACTION_USB_DEVICE_ATTACHED`
    - `UsbManager.ACTION_USB_DEVICE_DETACHED`
  - Intent filter ile cihaz tipi belirleme (CP2102/CH340/FTDI gibi yaygın SQM çipleri)
  - USB izin isteme akışı (`UsbManager.requestPermission()`)
  - Bağlantı durumu: `StateFlow<UsbConnectionState>` (enum: DISCONNECTED, PERMISSION_PENDING, CONNECTED, ERROR)
  - Seri port ayarları: Bölüm 5.1'deki parametrelerle `UsbSerialPort.open()` ve `setParameters()`
  - `connect()` / `disconnect()` fonksiyonları
  - Hilt ile `@Singleton` olarak provide et

#### T3.2 — SQM Protokol Ayrıştırıcısı (SqmProtocol)
- **Bağımlılık:** Yok (saf Kotlin, Android bağımsız)  
- **Çıktı:** `data/usb/SqmProtocol.kt`  
- **Detay:**
  - `fun buildReadCommand(): ByteArray` → `"rx\r".toByteArray(Charsets.US_ASCII)`
  - `fun parseResponse(raw: String): SqmReading?`
    - Bölüm 5.2'deki regex ile MPSAS değerini çıkar
    - Sıcaklık değerini de parse et (opsiyonel ama yararlı)
    - Hatalı formatta `null` döndür
  - `data class SqmReading(val mpsas: Double, val temperature: Double?, val rawResponse: String)`
  - Unit test yaz: bilinen SQM çıktılarıyla doğrula
  - Edge case'ler: eksik veri, timeout, garble

#### T3.3 — SQM Okuma Akışı ve UI Entegrasyonu
- **Bağımlılık:** T3.1, T3.2, T1.4  
- **Çıktı:** Home ekranında SQM bağlantısı ve ölçüm çalışır  
- **Detay:**
  - `SqmUsbManager` içinde `suspend fun readMeasurement(): SqmReading?`
    - Komut gönder → timeout ile yanıt bekle (3 saniye) → parse et
    - Coroutine `withTimeout` kullan
    - IO dispatcher üzerinde çalışsın
  - `HomeViewModel` içinde:
    - `SqmUsbManager` inject et
    - USB bağlantı durumunu `ConnectionBadge`'e bağla
    - "ÖLÇÜM YAP" butonuna basılınca `readMeasurement()` çağır
    - Sonucu `SqmGauge`'a yansıt (MPSAS + Bortle rengi)
  - Hata durumları: USB kopmuşsa uyarı, timeout olursa "Tekrar deneyin" mesajı
  - `res/xml/device_filter.xml` oluştur (USB vendor/product ID'leri)

**✅ FAZ 3 TAMAMLAMA KRİTERİ:** OTG ile SQM bağlanınca "Bağlı" rozeti yeşile dönüyor. "ÖLÇÜM YAP"a basınca MPSAS değeri ekranda görünüyor. Bortle rengi doğru.

---

### FAZ 4: Firebase Katmanı ⟨Agent D — Paralel⟩

#### T4.1 — Firebase Proje Kurulumu & Anonim Auth
- **Bağımlılık:** T1.1  
- **Çıktı:** Çalışan Firebase bağlantısı ve anonim kullanıcı  
- **Detay:**
  - Firebase Console'da proje oluştur (baak-light-pollution)
  - `google-services.json`'ı `app/` dizinine ekle
  - Firebase Anonymous Authentication aktifleştir
  - `data/firebase/FirebaseAuthManager.kt`:
    - `suspend fun ensureAnonymousAuth(): String` → UID döndür
    - Uygulama açılışında otomatik anonim giriş yap
    - Oturum yoksa veya süresi dolmuşsa yeniden giriş
  - Firestore güvenlik kuralları yaz:
    ```
    rules_version = '2';
    service cloud.firestore {
      match /databases/{database}/documents {
        match /measurements/{doc} {
          allow read: if true;
          allow write: if request.auth != null;
        }
      }
    }
    ```

#### T4.2 — Firestore CRUD Operasyonları
- **Bağımlılık:** T4.1  
- **Çıktı:** `data/firebase/FirestoreManager.kt`  
- **Detay:**
  - **Yazma:** `suspend fun saveMeasurement(measurement: SkyMeasurement): Result<String>`
    - `measurements` koleksiyonuna yeni belge ekle
    - `GeoPoint` kullanarak konum kaydet
    - `Timestamp.now()` ile server timestamp
    - Offline persistence aktif (varsayılan Firestore özelliği — bağlantı kopsa da kaydet)
  - **Okuma:** `fun getMeasurements(): Flow<List<SkyMeasurement>>`
    - `snapshotListener` ile gerçek zamanlı dinle
    - Son 500 ölçümü çek (`orderBy("timestamp", DESCENDING).limit(500)`)
  - **Bölgesel Okuma:** `fun getMeasurementsInBounds(sw: GeoPoint, ne: GeoPoint): Flow<List<SkyMeasurement>>`
    - Harita görünüm sınırlarındaki verileri çek (basit lat/lng filtresi)
  - Hata yakalama: Network hatası → offline cache'den servis et, kullanıcıya bilgi ver

**✅ FAZ 4 TAMAMLAMA KRİTERİ:** Firestore Console'da test verisi yazılıp okunabiliyor. Anonim auth çalışıyor. Offline'da veri kaydedip online'da senkronize edebiliyor.

---

### FAZ 5: Orkestrasyon — Tam Ölçüm Akışı

#### T5.1 — TakeMeasurementUseCase & Tam Entegrasyon
- **Bağımlılık:** T2.3, T3.3, T4.2  
- **Çıktı:** Tek butona basarak tam ölçüm döngüsü  
- **Detay:**
  - `domain/usecase/TakeMeasurementUseCase.kt`:
    ```kotlin
    suspend operator fun invoke(orientationEnabled: Boolean, note: String?): Result<SkyMeasurement>
    ```
  - Akış:
    1. SQM'den ölçüm oku → MPSAS al
    2. MPSAS → Bortle dönüşümü yap
    3. Mevcut GPS konumunu al
    4. Yönelim açık ise azimuth/pitch/roll al
    5. `SkyMeasurement` oluştur
    6. Firestore'a kaydet
    7. Başarılı/başarısız Result döndür
  - `HomeViewModel` güncelle:
    - "ÖLÇÜM YAP" → `TakeMeasurementUseCase` çağır
    - Loading state göster (buton devre dışı + progress indicator)
    - Başarılıysa: kısa animasyon + son ölçüm bilgisi
    - Başarısızsa: hata mesajı (hangi adımda patladığını belirt)
  - Haptic feedback: Ölçüm tamamlanınca kısa titreşim

**✅ FAZ 5 TAMAMLAMA KRİTERİ:** SQM bağlı + GPS aktif iken "ÖLÇÜM YAP"a basınca tüm veri toplanıp Firestore'a yazılıyor. Ekranda sonuç gösteriliyor.

---

### FAZ 6: Harita Görselleştirme

#### T6.1 — Temel Harita Ekranı
- **Bağımlılık:** T4.2  
- **Çıktı:** Çalışan Google Maps ekranı  
- **Detay:**
  - Google Cloud Console'da Maps SDK API key al
  - `AndroidManifest.xml`'e meta-data olarak ekle
  - `maps-compose` kütüphanesi ile `GoogleMap` composable
  - Harita stili: **Gece modu** (karanlık tema harita JSON stili uygula)
  - Başlangıç kamerası: Türkiye merkezi (39.9, 32.8, zoom 6)
  - Kullanıcının konumuna "Konumuma Git" butonu

#### T6.2 — Veri Noktalarının Haritada Gösterimi
- **Bağımlılık:** T6.1, T4.2  
- **Çıktı:** Firestore verileri haritada renkli noktalar olarak  
- **Detay:**
  - `MapViewModel` oluştur:
    - Firestore'dan `getMeasurements()` akışını dinle
    - Harita görünüm sınırları değişince `getMeasurementsInBounds()` çağır
  - Her ölçüm noktası için `Circle` veya `Marker`:
    - Renk: Bortle skalasına göre (Bölüm 3.3 renk kodları)
    - Yarıçap: zoom seviyesine göre dinamik
  - Nokta tıklanınca bilgi penceresi (InfoWindow):
    - MPSAS değeri, Bortle sınıfı, tarih/saat, not (varsa)
  - Performans: 500+ nokta için marker clustering uygula (`maps-utils`)

#### T6.3 — Isı Haritası Katmanı (Opsiyonel Geliştirme)
- **Bağımlılık:** T6.2  
- **Çıktı:** Heatmap overlay  
- **Detay:**
  - `maps-utils` kütüphanesinin `HeatmapTileProvider` sınıfını kullan
  - `WeightedLatLng` olarak her noktanın MPSAS değerini ağırlık yap
  - Gradient: Bortle renk skalası (koyu mavi → kırmızı)
  - Toggle: Kullanıcı nokta görünümü ile ısı haritası arasında geçiş yapabilmeli
  - Harita karanlık ise ısı haritası kontrast oluşturmalı

**✅ FAZ 6 TAMAMLAMA KRİTERİ:** Harita gece modunda açılıyor. Firestore'daki veriler renkli noktalar/ısı haritası olarak görünüyor. Zoom, tıklama, bilgi penceresi çalışıyor.

---

### FAZ 7: Cilalama & Son Rötuşlar

#### T7.1 — Geçmiş Ekranı
- **Bağımlılık:** T4.2  
- **Çıktı:** Ölçüm geçmişi listesi  
- **Detay:**
  - `HistoryScreen`: `LazyColumn` ile ölçüm listesi
  - Her satır: tarih, saat, MPSAS, Bortle rozeti, konum özeti
  - Sıralama: Yeniden eskiye (varsayılan)
  - Filtre: Bortle sınıfına göre (opsiyonel chip group)
  - Bir ölçüme tıklayınca haritada o noktaya zoom

#### T7.2 — CSV Dışa Aktarım
- **Bağımlılık:** T4.2  
- **Çıktı:** Paylaşılabilir CSV dosyası  
- **Detay:**
  - `ExportDataUseCase`:
    - Tüm yerel/Firestore ölçümleri CSV'ye yaz
    - Sütunlar: timestamp, sqm_value, bortle, lat, lng, altitude, azimuth, pitch, roll, note
    - `ShareSheet` ile paylaşma (Intent.ACTION_SEND)
  - Geçmiş ekranında "Dışa Aktar" butonu

#### T7.3 — Hata Yönetimi, Edge Case'ler & UX İyileştirmeleri
- **Bağımlılık:** T5.1, T6.2  
- **Çıktı:** Sağlam, hata toleranslı uygulama  
- **Detay:**
  - USB kopma durumu: Ölçüm sırasında USB çıkarsa graceful hata mesajı
  - GPS yok / kapalı: Kullanıcıyı ayarlara yönlendir
  - İnternet yok: Offline mod bildirimi, yerel cache'den harita
  - Ekran açık kalma: Ölçüm sırasında `FLAG_KEEP_SCREEN_ON`
  - Düşük parlaklık uyarısı: Uygulama açılınca "Ekran parlaklığını en düşüğe ayarlayın" önerisi
  - ProGuard / R8 kuralları: usb-serial-for-android ve Firebase için keep rules
  - Uygulama ikonu: Baak temalı launcher icon (adaptive icon)

**✅ FAZ 7 TAMAMLAMA KRİTERİ:** Uygulama uçtan uca çalışıyor. Geçmiş ekranı dolu. CSV export çalışıyor. Edge case'lerde crash yok.

---

## 8. Firestore Güvenlik Kuralları (Detaylı)

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /measurements/{measurementId} {
      // Herkes okuyabilir (halka açık ışık kirliliği verisi)
      allow read: if true;

      // Sadece giriş yapmış kullanıcılar yazabilir
      allow create: if request.auth != null
        && request.resource.data.sqm_value is number
        && request.resource.data.sqm_value >= 10
        && request.resource.data.sqm_value <= 25
        && request.resource.data.location is map
        && request.resource.data.device_id == request.auth.uid;

      // Güncelleme ve silme yasak (veri bütünlüğü)
      allow update, delete: if false;
    }
  }
}
```

---

## 9. Git Commit Stratejisi

Her faz tamamlandığında yapılacak commit'ler:

| Faz | Commit Mesajı | Tag |
|-----|---------------|-----|
| F1 | `feat: project setup, dark theme, navigation shell` | `v0.1.0` |
| F2 | `feat: GPS location + orientation sensor providers` | `v0.2.0` |
| F3 | `feat: USB OTG SQM serial communication` | `v0.3.0` |
| F4 | `feat: Firebase anonymous auth + Firestore CRUD` | `v0.4.0` |
| F5 | `feat: full measurement orchestration pipeline` | `v0.5.0` |
| F6 | `feat: map visualization with Bortle color coding` | `v0.6.0` |
| F7 | `feat: history, CSV export, error handling, polish` | `v1.0.0` |

---

## 10. Agent Direktifleri (Vibecoding Kuralları)

### Genel Kurallar
1. **Bu dosyayı her zaman referans al.** Görev başlamadan önce ilgili bölümü oku.
2. **Modüler yaz.** Her sınıf tek bir sorumluluğa sahip olmalı (SRP).
3. **Hardcoded değer YASAK.** Sabitler `AppConstants.kt`'de tanımlanmalı.
4. **Türkçe yorum, İngilizce kod.** Değişken/fonksiyon adları İngilizce, yorumlar Türkçe.
5. **Her faz bitiminde bildir.** "Faz X tamamlandı, commit yapılabilir" mesajı ver.

### UI Kuralları
6. **BEYAZ RENK YASAK.** Hiçbir composable'da `Color.White` veya `#FFFFFF` kullanma.
7. **Tema dışı renk YASAK.** Tüm renkler `BaakDarkTheme` üzerinden gelmeli.
8. **Hardcoded dp/sp YASAK.** Boyutlar theme veya constants'tan gelmeli.

### Kod Kalitesi
9. **Coroutine kullan.** Callback hell YASAK. Suspend function + Flow tercih et.
10. **Repository pattern.** ViewModel asla doğrudan Firebase/Sensor/USB çağırmamalı.
11. **Null safety.** Force unwrap (`!!`) YASAK. `?.let`, `?:`, `Result` kullan.
12. **Error handling.** Tüm IO operasyonları `try-catch` veya `Result` ile sarılmalı.

### Test
13. **SqmProtocol** için unit test zorunlu (parse doğrulaması).
14. **BortleScale** dönüşüm fonksiyonu için unit test zorunlu.
15. **ViewModel** testleri nice-to-have (Faz 7'de).
