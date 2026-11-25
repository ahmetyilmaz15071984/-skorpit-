# OSINT Kit — Android

[![APK Derle](https://github.com/ahmetyilmaz15071984/-skorpit-/actions/workflows/build.yml/badge.svg)](https://github.com/ahmetyilmaz15071984/-skorpit-/actions/workflows/build.yml)

Herkese açık kaynaklardan bilgi toplamak için 5 modüllü bir Android uygulaması.
Kotlin + Jetpack Compose, minimum Android 8.0 (API 26).

| Sekme | Ne yapar |
|---|---|
| **Kullanıcı** | Bir kullanıcı adını 56 sitede arar (45'i otomatik, 11'i giriş istediği için "elle bak" bağlantısı). Sonuçlar: VAR / YOK / ? (site engelledi) / ELLE |
| **Ağ** | Alan adı veya IP: DNS kayıtları, SPF/DMARC, RDAP (kayıt firması, oluşturulma/bitiş, alan adı yaşı), ham WHOIS (.tr dahil), IP konumu ve ASN, IP bloğu sahibi, ters DNS, crt.sh ile alt alan adları |
| **Foto** | EXIF metadata: GPS koordinatı (harita bağlantısı), cihaz, çekim tarihi, yazılım, seri no, tüm etiketler. Tamamen telefonda çalışır |
| **E-posta** | Biçim, alt adres etiketi, Gmail normal biçimi, MX ve barındıran sağlayıcı, tek kullanımlık/gizleyici adres tespiti, Gravatar profili |
| **Telefon** | Ülke, hat türü (mobil/sabit/VoIP…), E.164 ve diğer biçimler, Türkiye için ilk tahsis edilen operatör. Çevrimdışı çalışır |

Ek özellikler: sonuçlardan diğer modüllere tek dokunuşla geçiş (ör. e-postanın alan adını Ağ sekmesinde açma), uzun basarak kopyalama, raporu paylaşma, başka uygulamadan **Paylaş → OSINT Kit** ile fotoğraf ya da metin gönderme.

---

## APK'yı indirme

Her `main` dalına gönderimde GitHub Actions APK'yı otomatik derler (yaklaşık 4–6 dakika).

- **Son derleme:** [Actions](https://github.com/ahmetyilmaz15071984/-skorpit-/actions) → en üstteki başarılı "APK Derle" çalışması → en altta **Artifacts → OsintKit-apk** (zip içinde `OsintKit.apk`).
- **Doğrudan APK:** [Releases](https://github.com/ahmetyilmaz15071984/-skorpit-/releases) sayfası. Yeni sürüm yayınlamak için `v1.0.0` gibi bir etiket gönderin:
  ```bash
  git tag v1.0.0
  git push origin v1.0.0
  ```

### Telefona kurma
İndirilen `OsintKit.apk` dosyasını açın → istenirse **Bilinmeyen uygulamaları yükle** iznini verin → **Yükle**.
Play Protect "tanınmayan uygulama" uyarısı gösterirse **Yine de yükle** deyin (Play Store dışından kurulan her uygulamada çıkar).

Sabit bir imza anahtarı (`app/debug.keystore`) kullanıldığı için yeni sürümler eskisinin üzerine kurulur.

## Android Studio ile
Klasörü **File → Open** ile açın, Gradle eşitlemesi bitince ▶ **Run**.

---

## Kullanılan ücretsiz servisler (API anahtarı gerekmez)

- DNS: `dns.google` (yedek: Cloudflare) DNS-over-HTTPS
- Kayıt bilgisi: `rdap.org` (RDAP), `whois.iana.org` + ilgili WHOIS sunucusu (TCP 43)
- IP: `ipinfo.io` (anahtarsız kullanımda aylık sınır var), `rdap.org/ip`
- Alt alan adları: `crt.sh` (yoğun olduğunda yavaş yanıt verebilir)
- E-posta: Gravatar
- Telefon: Google libphonenumber (uygulamanın içinde, internet kullanmaz)

## Bilinen sınırlar

- Siteler sayfa yapılarını değiştirdikçe kullanıcı adı kontrolleri bozulabilir. "?" sonucu sitenin isteği engellediği anlamına gelir. Liste `app/src/main/java/com/osintkit/app/core/UsernameScanner.kt` içinde; yeni site eklemek tek satırdır.
- "VAR" sonucu yalnızca o adla bir hesap olduğunu gösterir, aynı kişiye ait olduğunu kanıtlamaz.
- Mesajlaşma ve sosyal medya uygulamaları fotoğraflardaki konumu siler. Android de galeriden seçilen fotoğraflarda konumu gizleyebilir; uygulama bunun için izin ister.
- IP konumu şehir düzeyinde yaklaşıktır, bir kişinin adresini göstermez.
- Türkiye operatör bilgisi ön eke göre ilk tahsisi gösterir; numara taşınmış olabilir.

## Proje yapısı

```
app/src/main/java/com/osintkit/app/
├── core/        Saf Kotlin: HTTP, JSON, DNS, WHOIS/RDAP, kullanıcı adı tarayıcı, e-posta (birim testli)
├── android/     ExifInterface ve libphonenumber kullanan analizciler
├── ui/          Compose ekranları, tema, uygulama durumu
└── MainActivity.kt
```

## Yasal not

Uygulama yalnızca herkese açık bilgileri sorgular. Kişisel verileri KVKK ve ilgili mevzuata uygun,
meşru amaçlarla kullanmak kullanıcının sorumluluğundadır. `debug.keystore` herkese açık bir geliştirme
anahtarıdır; uygulamayı mağazada yayınlayacaksanız kendi imza anahtarınızı oluşturun.
