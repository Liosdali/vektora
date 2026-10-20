# Vektora — PNG'den SVG ikon paketi

Flat renkli PNG ikonlarını yerel olarak gerçek SVG vektör path'lerine dönüştüren Java masaüstü uygulaması.

## Gereksinimler

- Java 22
- Maven 3.9 veya üzeri

Uygulamayı başlat:

```powershell
mvn javafx:run
```

Testleri çalıştır:

```powershell
mvn test
```

## Kullanım

1. **PNG/JPG ekle** ile bir veya daha fazla ikon dosyası seç (.png, .jpg, .jpeg). Geçerli görseller pakete eklenir;
   okunamayan dosyalar ayrıca bildirilir.
2. Soldaki listeden bir ikon seçerek SVG önizlemesini aç. Önizlemede kaynak görsel ile SVG aynı çerçevede üst üste
   gösterilir. Ayırıcıyı sürükleyerek ya da **Kaynak** / **SVG** düğmeleriyle karşılaştırabilirsin.
3. **Paket ayarları** altında SVG tuval genişliğini/yüksekliğini ve görünür ikon ölçeğini ayarla. Örneğin `0.5`, ikonu tuval içinde yarı boyuta getirir.
4. **Vektör ve renk** menüsünden kaynak renklerini koru, kaynak renk sayısını azalt veya özel paletleri kullan seçeneklerinden birini seç.
   **Detay** kaydırıcısı "En ayrıntılı" ile "En sade" arasında ayarlanır. "Ayrıntılı" ve üstü seviyelerde kenar
   yumuşatmasının ara tonları (iki rengin karışımı olan ya da baskın bir renge çok yakın tonlar) en yakın renge katılır,
   böylece kenarlarda ince şerit katmanları oluşmaz. Sadeleştikçe küçük kırıntılar atılır, kenarlar daha düz çizilir
   ve SVG küçülür. "Renk sayısını azalt" modu paleti görselden deterministik olarak hesaplar; aynı görsel her seferinde
   aynı renklere izlenir.
5. Özel palet modunda **Palet ekle** ile yeni bir renk varyantı oluştur; palete istediğin sayıda renk ekleyebilir veya renk seçicilerden değiştirebilirsin. Her palet ikon başına ayrı bir SVG varyantı üretir.
6. **Dışa aktar** menüsünden ikon başına SVG dosyalarını, tek bir SVG sprite'ı veya bu çıktıları birlikte içeren ZIP paketini seç.

Sprite dosyası açıldığında ikonları bir ızgarada gösterir; aynı zamanda her ikon `<symbol>` olarak tanımlı ve `<use href="ikon-sprite.svg#ikon-adi-varyant">` ile yeniden kullanılabilir.

SVG çıktısı piksel çözünürlüğünden bağımsızdır; genişlik, yükseklik ve ölçek değerleri SVG tuvaline ve `viewBox`'a uygulanır. PNG dışa aktarımı bu uygulamanın kapsamında değildir.

Vektörleştirme, şeffaf kenar boşluklarını kırpar ve kenarlarda baskın olan düz arka plan rengini (yakın tonlarıyla birlikte) şeffaflaştırır. Tek renkli özel palet, şeffaf arka planı koruyarak yalnızca çizimi renklendirir.

**Konuyu otomatik bul**, fotoğraf, degrade veya dokulu zeminler gibi düz renkle silinemeyen arka planlar içindir.
Düğme bir konu ayırma modelini bilgisayarında çalıştırır ve konunun dışında kalan her şeyi siler. Seçim ekranında
silinecek alanlar kırmızıyla görünür. Koru fırçası, elle silme ve **Geri al** bu sonucun üzerinde de çalışır.
**Yalnızca konuyu koru** kutusu, modeli yeniden çalıştırmadan sonucu kapatıp açar. **Tüm görsellerde bul**
paketteki her görselde konuyu ayırır. Her görsel ayrıca geri alınabilir. İki model vardır:
**Hızlı · U²-Net-p** (4,4 MB) ve **Kaliteli · ISNet** (170 MB). Seçilen model ilk kullanımda, onay verdikten sonra
bir kez `~/.vektora/models` klasörüne indirilir ve boyutu ile SHA-256 özeti doğrulanır. Görseller bilgisayardan
çıkmaz. Model kaynakları ve lisansları `src/main/resources/META-INF/third-party/segmentation-models-NOTICE.txt`
dosyasındadır.

Rozet veya ikon içindeki kapalı arka plan alanlarını temizlemek için ikonu seçip **Elle seç ve düzelt** düğmesine tıkla.
Seçim ekranında üç araç vardır:

- **Bölge seç:** Tıkladığın pikselle bağlantılı, rengi **Renk toleransı** sınırında kalan alan silinir. Kopuk ama aynı
  renkteki alanlar korunur. Tolerans, sağ paneldeki kaydırıcıyla her ikon için ayrı ayarlanır (varsayılan 24).
- **Fırçayla sil:** Boyadığın alan renginden bağımsız olarak saydamlaştırılır.
- **Fırçayla koru:** Boyadığın alan hiçbir araçla silinmez. Yanlışlıkla silinen yerleri geri getirmek için kullanılır.

Araç çubuğunun altındaki **Önerilen bölgeler**, hâlâ görünen en büyük renk alanlarını görünür alana oranlarıyla
listeler. Görselin kenarına değen alanlar önce gelir. Bir öneriye tıklamak o alana tıklamakla aynıdır.

**Maske** görünümünde silinecek alanlar (otomatik kenar temizliği dahil) kırmızıyla, **Sonuç** görünümünde
saydam hâliyle gösterilir. Fare tekerleği yakınlaştırır, sağ tıkla sürüklemek görüntüyü kaydırır. **Kenar halesini temizle**
açıkken silinen zemine karışmış kenar pikselleri ya tamamen silinir ya da logonun rengine çekilir. Böylece SVG'de
logonun etrafında ince renkli bir çerçeve kalmaz. **Geri al** (Ctrl+Z) son düzenlemeyi, **Seçimleri temizle** ise
tüm düzenlemeleri geri alır. Temizleme işlemi de geri alınabilir.

Silinen alanlar SVG'de gerçek oyuk olarak çıkar. Altta kalan şekiller bu alanlarda boyanmaz. Yollar, aynı renkteki
ardışık yollar bir `<g data-color="#rrggbb">` grubunda toplanacak şekilde gruplanır. Illustrator, Figma ve Inkscape
bunları katman olarak gösterir. Soldaki **Katmanlar** listesinde her katmanın renk kutusundan rengini değiştirebilirsin. Değişiklik önizlemeye ve
tüm dışa aktarımlara uygulanır, **↺** özgün renge döndürür. Bir rengi gizlemek, o rengi önizlemeden, SVG
dosyalarından, sprite'tan ve ZIP'ten siler. Kaldırılan rengin altındaki alan da saydam olur.

Bu seçimler önizlemeye, SVG dosyalarına, sprite'a ve ZIP'e birlikte uygulanır. Otomatik algılama veya tek renk
paleti kuş, yazı ya da benzeri nesneleri anlamsal olarak tanımaz. Karmaşık görsellerde kaldırılacak bölgeleri
kullanıcı seçmelidir.

İşleme yereldir; PNG görselleri bilgisayardan dışarı gönderilmez. Vektörleştirme kütüphanesinin lisansı `src/main/resources/META-INF/third-party/imagetracerjava-LICENSE.txt` dosyasındadır.
