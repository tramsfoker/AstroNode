# AstroNode — Çok Kullanıcılı Mimari & Yönetim Planı
## Dünya Çapında 40+ Organizasyon Senaryosu

---

## 1. Kullanıcı Rolleri

| Rol | Kimdir | Yapabildiği |
|-----|--------|-------------|
| **Süper Admin** | Baak Bilim Kulübü yöneticileri (sen) | Her şeyi görür, siler, düzenler. Organizasyonları onaylar. |
| **Organizatör** | Bir gözlem grubunun lideri | Kendi organizasyonu altında session oluşturur, üyelerini yönetir, raporları görür |
| **Gözlemci** | Sahada ölçüm yapan kişi | Session'a kodla katılır, ölçüm yapar, kendi geçmişini görür |
| **Misafir** | Anonim kullanıcı | Serbest ölçüm yapar, haritayı görür, hiçbir session'a katılamaz |

---

## 2. Firestore Veri Mimarisi (Genişletilmiş)

```
firestore/
│
├── users/{uid}
│   ├── displayName: "Hakan Y."
│   ├── email: "hakan@..." (opsiyonel, anonim ise null)
│   ├── role: "observer" | "organizer" | "super_admin"
│   ├── organizationId: "org_baak_bursa"
│   ├── totalMeasurements: 47
│   ├── createdAt: timestamp
│   ├── lastActiveAt: timestamp
│   └── settings: { theme: "auto", unit: "mpsas" }
│
├── organizations/{orgId}
│   ├── name: "Baak Bilim Kulübü — Bursa"
│   ├── description: "Bursa merkezli amatör astronomi topluluğu"
│   ├── logoUrl: "..."
│   ├── adminUids: ["uid1", "uid2"]
│   ├── memberCount: 15
│   ├── isVerified: true (Süper Admin onaylar)
│   ├── createdAt: timestamp
│   └── contact: { email: "...", website: "..." }
│
├── sessions/{sessionId}
│   ├── name: "Uludağ Gözlem Gecesi"
│   ├── description: "2100m rakımda karanlık alan gözlemi"
│   ├── code: "A7X3K9"
│   ├── organizationId: "org_baak_bursa"
│   ├── organizationName: "Baak Bilim Kulübü" (denormalize)
│   ├── createdBy: "uid_hakan"
│   ├── creatorName: "Hakan Y."
│   ├── status: "active" | "completed" | "cancelled"
│   ├── startDate: timestamp
│   ├── endDate: timestamp (null iken devam ediyor)
│   ├── location: { name: "Uludağ", geopoint: GeoPoint }
│   ├── participantIds: ["uid1", "uid2", ...]
│   ├── participantCount: 8
│   ├── measurementCount: 45
│   ├── avgMpsas: 19.7
│   ├── bestMpsas: 21.2
│   ├── tags: ["uludağ", "yüksek_rakım", "kış"]
│   ├── isPublic: true (haritada herkes görebilir mi?)
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
│
├── measurements/{measurementId}
│   ├── ... (mevcut alanlar)
│   ├── sessionId: "session_abc"
│   ├── organizationId: "org_baak_bursa"
│   ├── observerUid: "uid_hakan"
│   ├── observerName: "Hakan Y."
│   └── isVerified: false (admin doğrulaması)
│
├── session_invites/{inviteId}  (ileride — e-posta/link ile davet)
│   ├── sessionId: "session_abc"
│   ├── invitedEmail: "ahmet@..."
│   ├── status: "pending" | "accepted" | "declined"
│   └── createdAt: timestamp
│
└── reports/{reportId}  (ileride — otomatik raporlar)
    ├── sessionId: "session_abc"
    ├── generatedBy: "uid_hakan"
    ├── type: "session_summary" | "monthly" | "annual"
    ├── data: { ... hesaplanmış istatistikler }
    ├── pdfUrl: "..." (Storage'da)
    └── createdAt: timestamp
```

---

## 3. Session Yaşam Döngüsü

```
                    Organizatör
                        │
            ┌───────────▼───────────┐
            │   Session Oluştur     │
            │   (ad, açıklama,      │
            │    konum, tarih)      │
            └───────────┬───────────┘
                        │
                  Kod üretilir
                  (örn: A7X3K9)
                        │
            ┌───────────▼───────────┐
            │    STATUS: ACTIVE     │
            │                       │
            │  Katılımcılar kodla   │
            │  katılır, ölçüm yapar │
            │                       │
            │  Organizatör:         │
            │  - Canlı ölçüm takibi │
            │  - Katılımcı listesi  │
            │  - Anlık istatistik   │
            └───────┬───────┬───────┘
                    │       │
         ┌──────────▼─┐  ┌──▼──────────┐
         │ Bitir      │  │ İptal Et    │
         │ (normal)   │  │ (hava vb.)  │
         └──────┬─────┘  └──────┬──────┘
                │               │
    ┌───────────▼──┐    ┌───────▼───────┐
    │  COMPLETED   │    │  CANCELLED    │
    │              │    │               │
    │  Rapor üret  │    │  Veriler      │
    │  PDF paylaş  │    │  korunur ama  │
    │  Arşive al   │    │  raporlanmaz  │
    └──────────────┘    └───────────────┘
```

---

## 4. Yönetim Paneli Ekranları (İleride)

### 4.1 Organizatör Paneli
Organizatörün gördüğü "Yönetim" sekmesi:

```
┌──────────────────────────────┐
│  🏢 Baak Bilim Kulübü        │
│  Bursa — 15 üye              │
├──────────────────────────────┤
│                              │
│  📋 Aktif Etkinlikler (2)    │
│  ┌────────────────────────┐  │
│  │ Uludağ Gecesi  A7X3K9  │  │
│  │ 8 katılımcı • 45 ölçüm │  │
│  │ [Canlı Takip] [Bitir]  │  │
│  └────────────────────────┘  │
│                              │
│  📁 Geçmiş Etkinlikler (12)  │
│  ┌────────────────────────┐  │
│  │ Kazdağı Kampı          │  │
│  │ 12 Şub — 6 katılımcı   │  │
│  │ [Rapor] [Düzenle] [Sil]│  │
│  └────────────────────────┘  │
│                              │
│  👥 Üyeler                   │
│  Hakan Y. (Organizatör)     │
│  Ahmet K. (Gözlemci)        │
│  Elif S. (Gözlemci)         │
│  [Üye Davet Et]             │
│                              │
│  [+ Yeni Etkinlik Oluştur]  │
└──────────────────────────────┘
```

### 4.2 Süper Admin Paneli
Sadece Baak yöneticilerinin gördüğü:

```
┌──────────────────────────────┐
│  🛡️ Süper Admin Paneli       │
├──────────────────────────────┤
│                              │
│  📊 Genel İstatistikler      │
│  Toplam ölçüm: 12,450       │
│  Aktif kullanıcı: 156       │
│  Organizasyon: 8             │
│  Aktif session: 3            │
│                              │
│  🏢 Organizasyonlar          │
│  ┌────────────────────────┐  │
│  │ Baak Bursa  ✅ Onaylı   │  │
│  │ TÜBİTAK Astro ⏳Bekliyor│  │
│  │ [Onayla] [Reddet] [Sil]│  │
│  └────────────────────────┘  │
│                              │
│  🚩 Şüpheli Veriler         │
│  MPSAS > 22 (gerçekçi değil)│
│  Aynı noktadan 100+ ölçüm   │
│  [İncele] [Sil] [Kullanıcıyı│
│   Engelle]                   │
│                              │
│  📈 Kullanım Grafikleri      │
│  Firestore okuma/yazma       │
│  Aylık büyüme                │
└──────────────────────────────┘
```

---

## 5. Session Yönetim İşlemleri

### Organizatör Yapabilir:
| İşlem | Açıklama | Kural |
|-------|----------|-------|
| Oluştur | Yeni session aç | Organizasyon üyesi olmalı |
| Düzenle | Ad, açıklama, tarih değiştir | Sadece kendi session'ı |
| Bitir | Status → completed | Sadece kendi session'ı |
| İptal Et | Status → cancelled | Sadece kendi session'ı |
| Katılımcı Çıkar | Bir gözlemciyi session'dan çıkar | Kendi session'ı |
| Rapor Oluştur | PDF/CSV rapor | Kendi session'ı |
| Sil | Session ve ölçümleri sil | ❌ YAPAMAZ (veri bütünlüğü) |

### Süper Admin Yapabilir:
| İşlem | Açıklama |
|-------|----------|
| Tümünü gör | Tüm session/ölçüm/kullanıcıları listele |
| Session sil | Spam/test verisini temizle |
| Ölçüm sil | Hatalı/sahte veriyi kaldır |
| Kullanıcı engelle | Kötü niyetli kullanıcıyı devre dışı bırak |
| Organizasyon onayla | Yeni organizasyonları doğrula |
| Veri doğrula | Ölçümlere "verified" işareti koy |

### Gözlemci Yapabilir:
| İşlem | Açıklama |
|-------|----------|
| Katıl | Session koduyla katıl |
| Ayrıl | Kendi isteğiyle session'dan çık |
| Ölçüm yap | Session'a bağlı ölçüm kaydet |
| Kendi verisini gör | Kendi ölçüm geçmişi |
| ❌ Başkasının verisini silme | Asla |

---

## 6. Güvenlik Kuralları (Firestore Rules)

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Kullanıcı profili
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth.uid == userId;
    }
    
    // Organizasyonlar
    match /organizations/{orgId} {
      allow read: if true;  // Herkes görebilir
      allow create: if request.auth != null;
      allow update: if request.auth.uid in resource.data.adminUids
                    || isSuperAdmin();
      allow delete: if isSuperAdmin();
    }
    
    // Session'lar
    match /sessions/{sessionId} {
      allow read: if true;  // Halka açık veriler
      allow create: if request.auth != null
                    && isOrganizer();
      allow update: if request.auth.uid == resource.data.createdBy
                    || isSuperAdmin();
      allow delete: if isSuperAdmin();
    }
    
    // Ölçümler
    match /measurements/{measurementId} {
      allow read: if true;
      allow create: if request.auth != null
                    && request.resource.data.sqm_value >= 5
                    && request.resource.data.sqm_value <= 25;
      allow update: if isSuperAdmin();
      allow delete: if isSuperAdmin()
                    || request.auth.uid == resource.data.observerUid;
    }
    
    // Yardımcı fonksiyonlar
    function isSuperAdmin() {
      return get(/databases/$(database)/documents/users/$(request.auth.uid))
             .data.role == "super_admin";
    }
    
    function isOrganizer() {
      return get(/databases/$(database)/documents/users/$(request.auth.uid))
             .data.role in ["organizer", "super_admin"];
    }
  }
}
```

---

## 7. Kimlik Doğrulama Yol Haritası

### Faz A: Şimdi (Anonim + Kod)
```
Mevcut: Anonim auth + session kodu
Pro: Hızlı, sahada pratik
Con: Kullanıcı tanımlanamuyor, cihaz değişince veri kaybolur
```

### Faz B: Yakın Gelecek (Google Sign-In)
```
Anonim → Google ile giriş yap (isteğe bağlı)
Pro: Kalıcı kimlik, cihaz değişse bile veri korunur
Con: Google hesabı lazım
Uygulama: Firebase Auth → Google provider ekle
```

### Faz C: İleride (E-posta + Organizasyon Daveti)
```
Organizatör üye davet eder (e-posta)
Davet linki ile kayıt → otomatik organizasyona eklenir
Pro: Kontrollü büyüme
Con: Karmaşık akış
```

### Tavsiye: Faz A + B birlikte yap
- Anonim giriş varsayılan kalsın (sahada hızlı)
- Profil ekranında "Google ile bağla" opsiyonu
- Google ile bağlayınca anonim hesap kalıcı hesaba dönüşür
- Mevcut ölçümler korunur

---

## 8. Veri Doğrulama & Kalite Kontrol

### Otomatik Kontroller (Client-side)
| Kontrol | Kural | Aksiyon |
|---------|-------|---------|
| MPSAS aralık | 5.0 - 25.0 | Aralık dışı → kaydetme |
| GPS doğruluğu | accuracy < 100m | Kötü GPS → uyarı göster |
| Tekrar ölçüm | Aynı noktada <30sn | "Az önce ölçtünüz" uyarısı |
| Sıcaklık | SQM sıcaklık -40/+80°C | Aralık dışı → sensör hatası |

### Admin Doğrulaması (İleride)
| Kontrol | Kural | Aksiyon |
|---------|-------|---------|
| Outlier tespiti | Bölge ortalamasından ±5 sapma | Bayrak koy, admin incelesin |
| Toplu veri | Bir kullanıcı >100 ölçüm/saat | Spam şüphesi → bayrak |
| Konum tutarlılığı | 1dk'da 100km uzak ölçüm | İmkansız → bayrak |

---

## 9. Uygulama Sırası

### Hemen (Sprint 2 devamı)
- [x] Profil ekranı (basit)
- [ ] Google Sign-In (opsiyonel bağlama)
- [ ] Session durumu: active/completed/cancelled

### Etkinlik sonrası (Sprint 3)
- [ ] Organizasyon modeli oluştur
- [ ] Organizatör paneli (basit)
- [ ] Session düzenleme/bitirme/iptal
- [ ] Katılımcı listesi görüntüleme

### 40 kişi öncesi (Sprint 4)
- [ ] Süper admin paneli
- [ ] Firestore güvenlik kuralları (gerçek)
- [ ] Veri doğrulama kontrolleri
- [ ] Organizasyon onaylama sistemi

### Ölçeklendirme (Sprint 5+)
- [ ] E-posta davet sistemi
- [ ] PDF rapor oluşturma (gerçek)
- [ ] Web dashboard (opsiyonel)
- [ ] API (üniversiteler için veri erişimi)
