# AstroNode — Sprint 3 Plan
## Veri Kalitesi, İzinler, Analiz ve Hava Durumu

---

## GÖREV 1: Kendi Ölçümlerimi Yönetme

### Sorun
Test verisi, dandik ölçüm, hatalı kayıt → haritayı kirletiyor.

### Çözüm
- Geçmiş ekranında her ölçümün yanında silme ikonu (🗑️)
- Sadece KENDİ ölçümünü silebilir (observerUid == currentUid)
- Silmeden önce onay: "Bu ölçümü silmek istediğinize emin misiniz?"
- Haritada "Sadece doğrulanmış ölçümleri göster" toggle'ı
- Ölçüme "test" flag'i koyabilme (ölçüm sırasında checkbox):
  "Bu bir test ölçümüdür (haritada gösterilmez)"

---

## GÖREV 2: Session Erişim Kontrolü

### Sorun
Herkes her etkinliği görüyor ve katılabiliyor.

### Çözüm — Session Tipleri

| Tip | Görünürlük | Katılım | Kullanım |
|-----|-----------|---------|----------|
| **Herkese Açık** | Herkes listede görür | Kod ile katılır | Büyük halk etkinlikleri |
| **Gizli (Kodlu)** | Listede GÖRÜNMEZ | Sadece kodu bilen katılır | Kulüp gözlemleri |
| **Davetli** | Listede GÖRÜNMEZ | Organizatör davet eder | Bilimsel çalışmalar |

Session oluştururken "Etkinlik Tipi" seçimi:
- Herkese Açık (varsayılan)
- Gizli — Sadece Kodla
- Davetli — Sadece Davetliler

### Session Yönetimi
- Organizatör (createdBy) = tam yetki
- Katılımcı = sadece ölçüm yap + ayrıl
- Organizatör ölçüm silebilir (kendi session'ındaki)
- Session silme: SADECE ölçüm yoksa veya tüm ölçümler silinmişse

---

## GÖREV 3: Analiz Ekranı İyileştirme

### Sorun
Grafikler ve istatistikler okunmuyor, ne olduğu anlaşılmıyor.

### Çözüm — Basit ve Anlaşılır Kartlar

Analiz ekranını tamamen yeniden tasarla:

#### A) Üst Kısım — Büyük Özet Kartı
```
┌─────────────────────────────────┐
│  📊 GÖZLEM ÖZETİ               │
│                                 │
│  En İyi Ölçüm:  21.53 MPSAS    │
│  ████████████████████░ Bortle 4 │
│  "Mükemmel karanlık alan"       │
│                                 │
│  Ortalama:  15.4 MPSAS          │
│  ████████████░░░░░░░░ Bortle 7  │
│                                 │
│  Toplam: 47 ölçüm • 5 gün      │
│  Son: 2 saat önce               │
└─────────────────────────────────┘
```

#### B) Bortle Dağılımı — Yatay Renkli Bar
```
┌─────────────────────────────────┐
│  Bortle Dağılımı                │
│                                 │
│  B1-3 ██░░░░░░░░░░░░  3 (%6)   │
│  B4-6 ████████░░░░░░ 18 (%38)  │
│  B7-9 ████████████░░ 26 (%55)  │
│                                 │
│  Renk skalası: 🔵→🟢→🟡→🔴    │
└─────────────────────────────────┘
```

#### C) Zaman Grafiği — Basitleştirilmiş
```
┌─────────────────────────────────┐
│  Son 7 Gün                     │
│                                 │
│  MPSAS                          │
│  22 ┤              •            │
│  20 ┤          •       •        │
│  18 ┤      •               •   │
│  16 ┤  •                       │
│  14 ┤                           │
│     └──┴──┴──┴──┴──┴──┴──┤     │
│       Pzt Sal Çar Per Cum      │
│                                 │
│  ⬆ Daha karanlık = daha iyi    │
└─────────────────────────────────┘
```

#### D) Etkinlik Rapor Kartı
```
┌─────────────────────────────────┐
│  📋 Uludağ Gözlem Gecesi       │
│  12 Mart • 8 katılımcı          │
│                                 │
│  En İyi: 21.2  Ort: 19.7       │
│  ████████████████░░░ Bortle 4-5 │
│                                 │
│  🌡 12°C  ☁ %15  💨 8 km/s     │
│  (Open-Meteo hava verisi)       │
│                                 │
│  [PDF Rapor]  [CSV İndir]       │
└─────────────────────────────────┘
```

---

## GÖREV 4: Hava Durumu Entegrasyonu (Open-Meteo)

### API Bilgisi
- URL: https://api.open-meteo.com/v1/forecast
- Maliyet: ÜCRETSİZ (ticari olmayan kullanım)
- API Key: GEREKMİYOR
- Rate limit: 10.000 istek/gün (fazlasıyla yeterli)

### Alınacak Veriler
| Parametre | Açıklama | Birim |
|-----------|----------|-------|
| cloud_cover | Toplam bulut örtüsü | % |
| cloud_cover_low | Alçak bulut | % |
| cloud_cover_mid | Orta bulut | % |
| cloud_cover_high | Yüksek bulut | % |
| temperature_2m | Sıcaklık | °C |
| relative_humidity_2m | Nem | % |
| wind_speed_10m | Rüzgar | km/s |
| visibility | Görüş mesafesi | m |
| is_day | Gündüz mü | 0/1 |

### Örnek API Çağrısı
```
https://api.open-meteo.com/v1/forecast
  ?latitude=40.22
  &longitude=28.99
  &hourly=cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high,
          temperature_2m,relative_humidity_2m,wind_speed_10m,visibility
  &current_weather=true
  &timezone=auto
```

### Nerede Kullanılacak

A) Ölçüm anında (otomatik):
   - ÖLÇÜM YAP tıklanınca API'den anlık hava durumunu çek
   - SkyMeasurement'a ekle:
     weather: { temp: 12, humidity: 65, cloud_cover: 15, wind: 8 }
   - Firestore'a kaydedilir → raporda kullanılır

B) Home ekranında (anlık bilgi):
   - GPS konumuna göre anlık hava durumu küçük widget
   - "☁ %15  🌡 12°C  💨 8 km/s"
   - Gözlem tavsiyesi: "%15 bulut → Gözlem için uygun! ✅"

C) Analiz/Rapor'da (tarihsel):
   - Ölçüm zamanındaki hava durumu kaydedilmiş olacak
   - Etkinlik raporunda: o geceki ortalama hava durumu

D) Gözlem Planlayıcısı (ileride):
   - Önümüzdeki 7 gün bulut tahmini
   - "Cuma gecesi en az bulutlu → Gözlem önerisi"

### Veri Modeli Güncelleme
SkyMeasurement'a eklenecek:
```kotlin
data class WeatherData(
    val temperature: Double?,      // °C
    val humidity: Int?,             // %
    val cloudCover: Int?,           // % toplam
    val cloudCoverLow: Int?,       // %
    val cloudCoverHigh: Int?,      // %
    val windSpeed: Double?,        // km/s
    val visibility: Double?        // metre
)

// SkyMeasurement'a ekle:
val weather: WeatherData? = null
```

---

## UYGULAMA SIRASI

### Hemen (Bu Sprint)
1. [ ] Kendi ölçümünü silme
2. [ ] Test ölçümü flag'i
3. [ ] Session tipleri (açık/gizli/davetli)
4. [ ] Analiz ekranı yeniden tasarım
5. [ ] Open-Meteo entegrasyonu (ölçüm anında)
6. [ ] Home ekranında hava durumu widget

### Sonra
7. [ ] Gözlem planlayıcısı
8. [ ] PDF rapor oluşturma
9. [ ] Organizatör yönetim paneli
