# Portfolio Pro

**Portfolio Pro**, Kotlin ile geliştirilmiş Android portföy takip uygulamasıdır. Uygulama; farklı varlıkları tek yerde izlemek, alış/satış/para girişi/para çıkışı/transfer/temettü işlemlerini kaydetmek, portföy performansını takip etmek ve Excel dosyaları üzerinden veri aktarımı yapmak için geliştirilir.

> Sürüm: **5.0.0**  
> Android uygulama kimliği: `com.portfolio.app`  
> Minimum Android: **7.0 (API 24)**  
> Derleme SDK: **35**

## Özellikler

- Portföye varlık ekleme ve varlık bilgilerini düzenleme
- TRY ve USD cinsinden varlıkları takip etme
- Alış, satış, para girişi, para çıkışı, transfer ve temettü işlemlerini tek işlem ekranından kaydetme
- İşlem geçmişini görüntüleme ve işlemleri düzenleme/silme
- Ortalama maliyet, mevcut miktar ve gerçekleşen kâr/zarar hesaplamaları
- Güncel fiyat ve döviz kuru verilerini uygulama içinde takip etme
- Portföy ve varlık fiyat geçmişi için günlük kayıtlar
- Excel çalışma kitabından varlık ve işlem geçmişini içe aktarma
- Excel dosyasına portföy ve işlem verilerini aktarma

Özelliklerin kullanılabilirliği veri kaynağına, internet bağlantısına ve içe aktarılan dosyanın yapısına bağlı olabilir.

## Teknoloji altyapısı

| Bileşen | Kullanılan teknoloji |
|---|---|
| Dil | Kotlin |
| Android arayüzü | Android Views, XML, ViewBinding |
| Yerel veritabanı | Room |
| Asenkron işlemler | Kotlin Coroutines, Flow |
| Ağ iletişimi | Retrofit |
| JSON | Gson |
| Derleme | Gradle, Android Gradle Plugin, KSP |
| Otomatik APK | GitHub Actions |

## Projeyi derleme

### Gereksinimler

- JDK 21
- Android SDK 35
- Gradle (GitHub Actions iş akışında Gradle 8.7 kullanılır)

### Komut satırından

Depoyu klonlayın ve proje klasörüne geçin:

```bash
git clone https://github.com/keskin16-tr/com.pro.portfolio.app.git
cd com.pro.portfolio.app
```

İlgili geliştirme dalına geçmek için:

```bash
git checkout portfolio-pro-5.0-analytics
```

Debug APK oluşturun:

```bash
gradle --no-daemon assembleDebug
```

APK çıktısı:

```
app/build/outputs/apk/debug/app-debug.apk
```

Android Studio ile açmak için depoyu klonladıktan sonra proje kök dizinindeki `settings.gradle.kts` dosyasını içeren klasörü açın ve Gradle senkronizasyonunun tamamlanmasını bekleyin.

## GitHub Actions ile APK oluşturma

Depodaki `.github/workflows/build.yml` iş akışı, `portfolio-pro-4.3` dalına gönderilen değişikliklerde Android debug APK derlemesini çalıştırır.

1. GitHub deposunda **Actions** sekmesini açın.
2. **Portfolio Pro Android APK** iş akışını seçin.
3. İlgili çalıştırmanın tamamlanmasını bekleyin.
4. Başarılı çalıştırmanın **Artifacts** bölümünden `Portfolio-Pro-debug` dosyasını indirin.

İş akışı APK dosyasının gerçekten oluştuğunu kontrol eder ve başarılı derlemede 14 gün süreyle artifact olarak saklar.

## Excel ile yedekleme ve geri yükleme

Excel içe aktarma özelliği, aynı çalışma kitabında varlıkları ve bunlara ait işlem geçmişini birlikte okuyabilir.

### Desteklenen sayfalar

- Varlık sayfası: `Varlıklar`, `Portföy`, `Portfolio` veya `Holdings`
- İşlem sayfası: `İşlemler`, `Transactions` veya `Trades`

Sayfa ve sütun adları Türkçe/İngilizce eş anlamlı başlıklarla tanınır. Sütun sırasının sabit olması gerekmez.

### Varlık sütunları

Kullanılan Excel şablonundaki sütunlar:

`id`, `name`, `symbol`, `category`, `account`, `quantity`, `unit_price`, `currency`, `cost_per_unit`, `notes`, `created_at`, `updated_at`, `price_source`, `market_symbol`

Aynı sembol ve para birimine sahip varlıklar farklı hesaplarda bulunabilir. Bu kayıtlar `account` alanına göre ayrı varlıklar olarak korunur. `Banka` ve `Nakit` kategorileri birbirinden ayrı tutulur.

### İşlem sütunları

Kullanılan Excel şablonundaki sütunlar:

`id`, `asset_id`, `asset_name`, `transaction_type`, `quantity`, `unit_price`, `currency`, `transaction_date`, `notes`, `created_at`, `applied`, `realized_pnl`, `realized_pnl_try`

İşlem türleri alış (`BUY`), satış (`SELL`), para girişi (`CASH_IN`), para çıkışı (`CASH_OUT`), transfer (`TRANSFER`) ve temettü (`DIVIDEND`) kayıtlarını kapsar. `applied=0` olan geçmiş kayıtları işlem geçmişinde korunur, varlık miktarına ikinci kez uygulanmaz. Tarihler Excel tarih seri numarası, Unix zaman damgası ve ISO-8601 tarih biçimlerinden okunabilir.

Excel dışa aktarma da aynı iki sayfa adını ve sütun düzenini kullanır: `Varlıklar` ve `İşlemler`.

### Geri yükleme seçenekleri

- **Birleştir:** Mevcut portföy korunur. Aynı sembol/para birimi/hesap eşleşen varlıklar tekrar eklenmez; mükerrer olmayan işlem geçmişi aktarılır. Mevcut varlıklara ait içe aktarılan işlemler `applied=0` geçmiş kaydı olarak saklanır ve mevcut bakiyeye yeniden uygulanmaz. Yeni eklenen varlıklara ait işlemlerde kaynak dosyanın `applied` değeri korunur.
- **Tamamen değiştir:** Mevcut portföy verileri temizlenerek seçilen yedekteki varlıklar ve işlemler geri yüklenir. Bu seçeneği kullanmadan önce mevcut verilerin yedeğini alın.

> Önemli: Excel dosyanızın sütun adları ve veri biçimleri farklıysa içe aktarma öncesinde dosyanın bir kopyasını kullanarak test edin. Tamamen değiştir seçeneği mevcut portföy kayıtlarını siler.

## Veri ve gizlilik

Portföy kayıtları uygulamanın yerel Room veritabanında tutulur. Piyasa fiyatı ve döviz kuru güncellemeleri için internet bağlantısı gerekebilir. Excel dosyalarını ve oluşturulan yedekleri güvenli bir yerde saklayın; uygulama verilerini silmek veya uygulamayı kaldırmak yerel kayıtları etkileyebilir.

## Katkı ve hata bildirimi

Hata bildirirken mümkünse aşağıdaki bilgileri ekleyin:

- Uygulama sürümü ve Android sürümü
- Sorunun tekrarlanma adımları
- Beklenen davranış ve gerçekleşen davranış
- İlgili GitHub Actions derleme bağlantısı veya hata günlüğü

Hassas portföy bilgileri, hesap numaraları veya kişisel veriler içeren Excel dosyalarını herkese açık sorun kayıtlarına eklemeyin.

## Lisans

Bu depoda ayrıca bir lisans dosyası yayımlanmadıysa, kodun yeniden dağıtım ve kullanım hakları için depo sahibinden izin alınmalıdır.


## Kalıcı APK imzası ve güncelleme

GitHub Actions ilk kurulumda varsayılan Android debug imzasını kullanabilir. GitHub'ın her yeni derleme için temiz bir sanal makine açması nedeniyle bu debug imzası sonraki derlemelerde değişebilir. Android, aynı paket adına sahip ancak farklı sertifikayla imzalanmış APK'nın mevcut uygulamanın üzerine kurulmasına izin vermez.

Gelecek sürümlerde aynı imzayı kullanmak için bir kez bir imza anahtarı oluşturun ve GitHub deposunda **Settings → Secrets and variables → Actions** bölümüne şu dört Actions Secret değerini ekleyin:

- `ANDROID_KEYSTORE_BASE64`: Keystore dosyasının Base64 içeriği.
- `ANDROID_KEYSTORE_PASSWORD`: Keystore parolası.
- `ANDROID_KEY_ALIAS`: Anahtar takma adı.
- `ANDROID_KEY_PASSWORD`: Anahtar parolası.

Keystore oluşturma örneği:

```bash
keytool -genkeypair -v -keystore portfolio-release.jks -alias portfolio -keyalg RSA -keysize 2048 -validity 10000
```

Linux/macOS Base64:
```bash
base64 -w 0 portfolio-release.jks
```

Windows PowerShell Base64:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("portfolio-release.jks"))
```

Keystore dosyasını ve parolalarını güvenli bir yerde saklayın; sonraki sürümlerde aynı anahtar kullanılmalıdır. **Önemli:** Daha önce kurulmuş APK farklı/geçici bir debug anahtarıyla imzalandıysa yeni kalıcı anahtar o eski APK'nın imzasını geriye dönük olarak eşleştiremez. Bu durumda ilk geçişte uygulama verilerini Excel ile yedekleyip eski uygulamayı bir kez kaldırmak ve kalıcı imzalı sürümü kurmak gerekir. Sonraki sürümler aynı keystore ile imzalanırsa kaldırmadan güncellenebilir.


### 4.3.1 düzenleme ve Excel uyumluluğu

- Excel içe aktarma, sayfa başında başlık satırı veya boş satır bulunsa da sütun başlıklarını arar.
- İşlem kayıtları `asset_id` ile; kimlik bulunmadığında sembol veya varlık adı ile eşleştirilir. Excel'in sayısal kimlikleri `12.0` biçiminde saklaması da desteklenir.
- Dışa aktarma yalnızca `Varlıklar` ve `İşlemler` sayfalarını üretir. Temettü kayıtları `İşlemler` sayfasında `Temettü` türü olarak yer alır.
- Varlık düzenleme ekranında görünen sembol (`symbol`) ile veri sağlayıcısına gönderilen piyasa sembolü/sorgu terimi (`market_symbol`) ayrı alanlardır. Hesap, aracı kurum, not, veri kaynağı, güncel fiyat, miktar ve maliyet alanları da gösterilir.
- İşlem düzenleme ekranında işlem türü, bağlı varlık/hesap, tarih, miktar, fiyat, komisyon/stopaj ve not değiştirilebilir.
