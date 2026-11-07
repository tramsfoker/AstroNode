# AstroNode v1.2 — Yayın Kontrol Listesi

Kod tarafı hazır. Aşağıdaki adımlar **senin hesaplarınla** yapılması gerekenler.
Sırayla git; her adımın yanında neden gerektiği yazıyor.

> ⚠️ **Önemli:** Yeni güvenlik kuralları eski (v1.1) uygulamanın bazı yazmalarını reddeder.
> Kuralları yayınladığın gün kulüpteki telefonlara yeni APK'yı da kur.

---

## 0. Bir kerelik kurulum (bilgisayarında)

```bash
cd firebase
npm ci
npx firebase login
```

## 1. Mevcut verileri yeni yapıya taşı (≈5 dk)

Firebase Console → Proje ayarları → Hizmet hesapları → **Yeni özel anahtar oluştur** →
dosyayı `firebase/service-account.json` olarak kaydet (git'e girmez, `.gitignore`'da).

```bash
export GOOGLE_APPLICATION_CREDENTIALS=./service-account.json
npm run migrate            # sadece rapor — çıktıyı oku
npm run migrate -- --apply # uygula
```

Ne yapar: eski ölçümlere `observer_uid`/`geohash` ekler, her etkinlik için benzersiz
`session_codes` belgesi açar, kendine yetki vermiş kullanıcıları listeler. Hiçbir şey silmez,
tekrar çalıştırmak güvenlidir.

## 2. Güvenlik kurallarını ve indeksleri yayınla

```bash
npm test                 # 35 kural testi geçmeli
npm run deploy:rules     # firestore.rules + indeksler + storage.rules
```

İndekslerin oluşması birkaç dakika sürer (Console → Firestore → İndeksler).

## 3. Kendine süper admin yetkisi ver

Console → Authentication → Users → kendi UID'ni kopyala:

```bash
npm run set-admin -- <UID> admin
```

Uygulamayı kapatıp aç (token yenilenir). Yetki artık sunucuda; kimse kendine veremez.

## 4. Gizlilik politikası ve hesap silme sayfası

1. `firebase/public/privacy.html` ve `delete-account.html` içindeki
   **[İLETİŞİM E-POSTASI] / [CONTACT EMAIL]** yerlerine iletişim adresini yaz.
2. Yayınla: `npx firebase deploy --only hosting`
3. Kontrol et: https://astronode-50a6c.web.app/privacy ve /delete-account

Bu iki URL Play Console'da zorunlu.

## 5. API anahtarlarını kısıtla (Google Cloud Console → APIs & Services → Credentials)

- **Maps anahtarı** (manifestteki `AIzaSyAB…`): *Application restrictions → Android apps* →
  paket `com.baak.astronode` + SHA-1 parmak izleri:
  - debug: `./gradlew signingReport` çıktısındaki SHA-1
  - **Play App Signing** SHA-1'i (Play Console → Kurulum → Uygulama imzalama) — yayından sonra eklenir
  - *API restrictions*: sadece **Maps SDK for Android**
- **Firebase anahtarı** (`google-services.json` içindeki): aynı Android kısıtlaması.

Bu yapılmazsa anahtar başka uygulamalarda kullanılıp senin faturana yazılabilir.

## 6. Firebase Blaze + bütçe alarmı

Console → Kullanım ve faturalandırma → **Blaze**'e geç → Cloud Billing → Bütçeler:
**5 $** ve **20 $** alarmları (e-posta). Küçük ölçekte fatura 0 $ kalır.

## 7. App Check (sahte istemcileri engeller)

1. Console → App Check → Android uygulaması → **Play Integrity** → SHA-256
   (Play Console → Uygulama imzalama sayfasından).
2. Debug cihaz: debug APK'yı çalıştır, Logcat'te `DebugAppCheckProvider` satırındaki token'ı
   Console → App Check → *Debug token'larını yönet*'e ekle.
3. 1–2 hafta **Metrikler**'i izle (doğrulanmış istek oranı ~%100 olmalı), sonra Firestore için
   **Enforce**'a bas. Erken enforce edersen eski sürümler çalışmaz.

## 8. İmzalama anahtarı (BİR KEZ — ASLA KAYBETME)

```bash
keytool -genkeypair -v -keystore astronode-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Proje köküne `keystore.properties` (git'e girmez):

```properties
storeFile=astronode-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

`.jks` dosyasını ve şifreleri **iki ayrı yerde yedekle** (ör. şifre yöneticisi + harici disk).
Play App Signing açık olduğu için bu "yükleme anahtarı"dır; kaybedilirse Google'dan sıfırlanabilir
ama zahmetlidir.

Yayın paketi: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`

## 9. Google Play Console

**Hesap türü kararı** (henüz hesap yok):

| | Kişisel | Kurumsal (Baak Bilim Kulübü) |
|---|---|---|
| Gereken | Kimlik doğrulama, 25 $ | D-U-N-S numarası, kurum belgeleri, 25 $ |
| Yayın öncesi | **12 test kullanıcısı × 14 gün kapalı test zorunlu** | Kapalı test zorunlu değil |
| Mağazada görünen | Senin adın | Kulüp adı |

Kulüp adına uzun vadeli bir proje olduğu için **kurumsal** öneririm; D-U-N-S birkaç hafta
sürebilir. Hızlı başlamak istersen kişisel hesap + kulüp üyeleriyle kapalı test de olur.

**Uygulama içeriği formları:**

- Gizlilik politikası URL'i → adım 4
- Hesap silme URL'i → `/delete-account`
- **Veri Güvenliği** (taslak cevaplar — son kontrolü sen yap):
  - Konum → *Hassas konum*: toplanıyor, uygulama işlevi; diğer kullanıcılara gösteriliyor
  - Kişisel bilgiler → *Ad* (takma ad), *E-posta* (Google bağlanırsa): toplanıyor, hesap yönetimi
  - Uygulama etkinliği / Uygulama bilgileri ve performans → *Çökme günlükleri, Tanılama*: Crashlytics
  - Cihaz veya diğer kimlikler → Firebase Analytics/Installations
  - Aktarımda şifreleme: **Evet** · Kullanıcı silme talep edebilir: **Evet**
- İçerik derecelendirmesi anketi, hedef kitle (13+), reklam yok
- Konum izni: sadece uygulama açıkken (arka plan konumu YOK — ek beyan gerekmez)

**Mağaza listesi (TR + EN):** kısa/uzun açıklama, 512×512 ikon
(`app/src/main/res/playstore_icon_512.png`), 1024×500 öne çıkan görsel, en az 2 telefon ekran görüntüsü.

## 10. Yayın öncesi saha testi (elle)

- [ ] Temiz kurulum, **internet kapalı** açılış → anlaşılır mesaj, çökme yok
- [ ] SQM bağla → izin ver → **bağlanıyor** (izin hatası düzeltildi), ölçüm al
- [ ] Offline ölçüm al → interneti aç → senkron oldu, Geçmiş'te görünüyor
- [ ] Etkinlik oluştur → başka telefondan kodla katıl → iki telefon aynı anda katılsın
- [ ] Gizli etkinlik herkese açık listede **görünmüyor**
- [ ] Başkasının etkinliğini bitirme/silme düğmesi yok
- [ ] Geçmiş ekranında **sadece kendi** ölçümlerin var
- [ ] Google bağla → uygulamayı sil/kur → Google ile giriş → ölçümler geri geldi
- [ ] Hesabı sil → Console'da kullanıcı ve ölçümleri gitti
- [ ] Android 14/15 cihazda USB izin akışı
