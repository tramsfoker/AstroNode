# AstroNode — Faz 2 Yol Haritası
## "v1.0 Çalışıyor, Şimdi Cilalamaya ve Genişletmeye Geçiyoruz"

---

## Öncelik Sıralaması

| # | Özellik | Etkinliğe Yetişmeli mi? | Zorluk |
|---|---------|------------------------|--------|
| 1 | Aydınlık/Karanlık Tema Geçişi | ✅ Evet | Orta |
| 2 | Logo & Görsel Varlıklar | ✅ Evet | Kolay |
| 3 | Splash Animasyonu (Logo ile) | ✅ Evet | Kolay |
| 4 | Session Kodu (Oluştur + Katıl) | ✅ Evet — sahada lazım | Yapıldı, test et |
| 5 | Geçmiş Ekranı Filtreleme | ✅ Evet | Orta |
| 6 | Analiz / Grafik Ekranı | İyi olur | Orta-Zor |
| 7 | Hesabım / Profil Ekranı | İyi olur | Orta |
| 8 | Offline → Online Senkron Testi | ✅ Evet | Yapıldı, doğrula |
| 9 | UI Cilalama (Animasyonlar vb.) | Güzel olur | Orta |

---

## GÖREV 1: Aydınlık / Karanlık Tema Geçişi

### Konsept
- Gündüz: Açık tema (beyaz arka plan, koyu metin) — normal kullanım
- Gece/Ölçüm: Astronomi modu (mevcut karanlık tema) — sahada göz dostu
- Kullanıcı elle seçebilmeli VEYA otomatik (gün batımına göre)

### Teknik Plan
- Color.kt'ye light palette ekle
- Theme.kt'ye BaakLightTheme composable ekle
- Tema seçimi: Settings / Ayarlar menüsünde toggle
  - "Astronomi Modu" (karanlık) / "Gündüz Modu" (açık) / "Otomatik"
- Otomatik mod: LocationProvider'dan alınan enlem/boylam ile 
  gün batımı saatini hesapla → otomatik geçiş
- Tercih: DataStore / SharedPreferences ile sakla

### Renk Paleti — Gündüz Modu
```
Surface:           #F5F5F5
CardBackground:    #FFFFFF
PrimaryAccent:     #B71C1C (koyu kırmızı)
SecondaryAccent:   #1565C0 (mavi)
TextPrimary:       #212121
TextSecondary:     #757575
Success:           #2E7D32
Warning:           #F57F17
Error:             #C62828
TopBar:            #B71C1C (kırmızı üst bar, beyaz yazı)
```

---

## GÖREV 2: Logo & Görsel Varlıklar

### Baak Logosu (Satürn + BAAK yazısı)
Logonun dijital versiyonunu şu amaçlarla kullanacağız:
- Splash ekranı (büyük, animasyonlu)
- Home ekranı üst bar (küçük)
- Uygulama ikonu (launcher icon)
- Hakkında ekranı

### Görsel Oluşturma Araçları

| İhtiyaç | Araç | Nasıl |
|---------|------|-------|
| **Logo vektörel (SVG)** | Figma (ücretsiz) veya Inkscape | Satürn şeklini çiz, BAAK yazısını ekle, SVG olarak dışa aktar |
| **Logo animasyonu** | Lottie + LottieFiles.com | SVG'yi LottieFiles'a yükle, animasyon ekle (dönen halka, fade-in yazı), JSON indir |
| **Uygulama ikonu** | Android Studio → Image Asset | Adaptive icon: ön plan = logo, arka plan = siyah veya kırmızı |
| **AI ile logo iyileştirme** | Claude artifact veya Canva AI | Mevcut logoyu referans ver, stil öner |
| **Harita marker ikonu** | Figma / Canva | Bortle renkleriyle küçük daire ikonları |

### Hızlı Yol — Mevcut Logoyu Kullanma
1. Logonun PNG veya fotoğrafını çek (temiz, düz arka plan)
2. remove.bg ile arka planı sil → şeffaf PNG
3. Android Studio → res/drawable/ klasörüne koy
4. Launcher icon: Android Studio → New → Image Asset → logonu seç

### Lottie Animasyonu (Splash için)
1. LottieFiles.com'da ücretsiz hesap aç
2. "Saturn" veya "planet" ara → hazır animasyon bul
3. Veya kendi SVG'ni yükle → basit animasyon ekle
4. JSON dosyasını indir → res/raw/splash_animation.json
5. Compose'da: `val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.splash_animation))`

---

## GÖREV 3: Splash Animasyonu

### Senaryo
1. Siyah ekran (0ms)
2. Satürn logosu ortadan büyüyerek fade-in (0-600ms)
3. Satürn halkası hafif dönüş animasyonu (sürekli, yavaş)
4. "BAAK BİLİM KULÜBÜ" yazısı alttan slide-up + fade (400-800ms)
5. "AstroNode" alt yazısı fade-in (800-1000ms)
6. 2 saniye bekle → Home'a geçiş (fade out)

### Bağımlılık
- Lottie Compose kütüphanesi: `com.airbnb.android:lottie-compose:6.3.0`
- Logo dosyası (PNG veya Lottie JSON)

---

## GÖREV 4: Session Kodu — Test & Doğrulama

Kod sistemi yazıldı ama test edilmesi lazım:

### Test Senaryoları
1. Etkinlik oluştur → 6 haneli kod görünüyor mu?
2. Kodu kopyalayıp başka cihazda gir → katılıyor mu?
3. Yanlış kod gir → hata mesajı veriyor mu?
4. Offline'da etkinlik oluştur → online olunca senkronize oluyor mu?
5. Etkinlik seçili iken ölçüm yap → Firestore'da session_id var mı?

---

## GÖREV 5: Geçmiş Ekranı — Filtreleme & Gruplama

### Filtreler (Ekranın üstünde chip/tab olarak)
- **Zaman:** Bugün / Bu Hafta / Bu Ay / Tümü
- **Etkinlik:** Serbest Ölçüm / [Etkinlik adları dropdown]
- **Bortle:** 1-3 (Karanlık) / 4-6 (Orta) / 7-9 (Aydınlık)
- **Sıralama:** Yeniden Eskiye / Eskiden Yeniye / En Karanlık / En Aydınlık

### Gruplama
- Ölçümleri güne göre grupla:
  "5 Mart 2026 — 7 ölçüm"
    ├── 23:40 — 10.22 MPSAS (Bortle 9)
    ├── 23:35 — 9.45 MPSAS (Bortle 9)
    └── ...

### Arama
- Nota göre arama (TextField)

---

## GÖREV 6: Analiz / Grafik Ekranı

### Konsept
Bottom Navigation'a 4. sekme: "Analiz" (📊 ikonu)

### Grafikler (Recharts tarzı, Compose'da)
1. **Zaman Serisi Grafiği:**
   - X ekseni: Tarih/saat
   - Y ekseni: MPSAS değeri
   - Filtrelenebilir: son 7 gün / 30 gün / etkinlik bazlı

2. **Bortle Dağılımı (Pasta/Bar grafik):**
   - Kaç ölçüm hangi Bortle sınıfında

3. **Konum Karşılaştırma:**
   - Farklı gözlem noktalarının ortalama MPSAS değerleri
   - Bar chart

4. **Etkinlik Özet Kartı:**
   - Seçili etkinliğin istatistikleri:
     → Toplam ölçüm sayısı
     → Ortalama / Min / Max MPSAS
     → Katılımcı sayısı
     → En karanlık nokta
     → Süre (ilk-son ölçüm arası)

### Kütüphane Seçenekleri
- `YCharts` (Jetpack Compose native)
- `Vico` (Compose chart library)
- `MPAndroidChart` (klasik, ama View-based)
→ Öneri: **Vico** — Compose native, karanlık tema uyumlu

### Rapor Oluşturma
- "PDF Rapor Oluştur" butonu:
  → Etkinlik bilgileri + grafikler + özet tablo
  → Paylaş (Intent.ACTION_SEND)

---

## GÖREV 7: Hesabım / Profil Ekranı

### Erişim
- Home ekranı üst sağda profil ikonu (veya hamburger menü)

### İçerik
```
┌──────────────────────────────┐
│  👤 Gözlemci Profili          │
│                              │
│  Kullanıcı ID: anon_abc123   │
│  Takma Ad: [düzenlenebilir]   │
│  Organizasyon: Baak Bilim K.  │
│                              │
├──────────────────────────────┤
│  📊 İstatistiklerim          │
│                              │
│  Toplam Ölçüm:    47         │
│  Katıldığım Etkinlik: 3      │
│  En Karanlık Ölçüm: 21.53    │
│  Favori Gözlem Noktam: Uludağ│
│                              │
├──────────────────────────────┤
│  ⚙️ Ayarlar                  │
│                              │
│  Tema: [Astronomi / Gündüz / Oto] │
│  Birim: [MPSAS / Bortle]     │
│  Dil: Türkçe                 │
│                              │
├──────────────────────────────┤
│  ℹ️ Hakkında                 │
│                              │
│  AstroNode v1.0.0            │
│  Baak Bilim Kulübü           │
│  [Logo]                      │
│  github.com/tramsfoker/...   │
└──────────────────────────────┘
```

### Takma Ad Sistemi
- İlk açılışta "Takma adınızı girin" dialog'u
- Firestore'da `users/{uid}` koleksiyonuna kaydet
- Ölçümlerde `observer_name` alanı ekle
- Etkinlik raporlarında kim hangi ölçümü yaptı görünsün

---

## GÖREV 8: Offline → Online Senkron Doğrulama

### Test Planı
1. WiFi kapat
2. Etkinlik oluştur (offline)
3. 3-4 ölçüm yap (offline)
4. WiFi aç
5. Firestore Console'da kontrol:
   - Session belgesi var mı?
   - Ölçümlerde session_id doğru mu?
   - Tüm ölçümler geldi mi?
6. Başka cihazda haritaya bak — noktalar görünüyor mu?

---

## GÖREV 9: UI Cilalama

### Animasyonlar
- Ölçüm butonu: Basınca pulse efekti
- SQM Gauge: Değer gelince sayı animasyonlu artar (CountUp)
- Bortle renk geçişi: Smooth color transition
- Sayfa geçişleri: Slide + fade
- Bağlantı banner: Slide down/up

### Mikro İyileştirmeler
- Haptic feedback: Ölçüm başarılı → kısa titreşim ✅ (zaten var)
- Ses: Ölçüm başarılı → kısa "bip" (opsiyonel, gece rahatsız edebilir)
- Skeleton loading: Veriler yüklenirken shimmer efekti
- Empty state: Güzel illüstrasyon + açıklayıcı metin

---

## Uygulama Sırası (Önerilen)

### Sprint 1 — Etkinliğe Hazırlık (Bu Hafta)
1. ✅ Logo PNG'sini hazırla ve uygulamaya ekle
2. Aydınlık/Karanlık tema toggle
3. Splash animasyonu (logo ile)
4. Session kodunu sahada test et

### Sprint 2 — Kullanılabilirlik (Etkinlik Sonrası)
5. Geçmiş ekranı filtreleme
6. Hesabım / Profil ekranı
7. UI cilalama

### Sprint 3 — Bilimsel Değer (1 Ay İçinde)
8. Analiz / Grafik ekranı
9. PDF rapor oluşturma
10. 40 kişi ölçek testi
