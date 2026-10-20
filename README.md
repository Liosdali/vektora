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
2. Soldaki listeden bir ikon seçerek SVG önizlemesini aç.
3. **Paket ayarları** altında SVG tuval genişliğini/yüksekliğini ve görünür ikon ölçeğini ayarla. Örneğin `0.5`, ikonu tuval içinde yarı boyuta getirir.
4. **Vektör ve renk** menüsünden kaynak renklerini koru, kaynak renk sayısını azalt veya özel paletleri kullan seçeneklerinden birini seç.
   **Detay** kaydırıcısı "En ayrıntılı" ile "En sade" arasında ayarlanır. Sadeleştikçe küçük kırıntılar atılır, kenarlar
   daha düz çizilir ve SVG küçülür. Kenarları yumuşatılmış görsellerde "Renk sayısını azalt" ile birlikte kullanmak
   katman sayısını belirgin şekilde düşürür.
5. Özel palet modunda **Palet ekle** ile yeni bir renk varyantı oluştur; palete istediğin sayıda renk ekleyebilir veya renk seçicilerden değiştirebilirsin. Her palet ikon başına ayrı bir SVG varyantı üretir.
6. **Dışa aktar** menüsünden ikon başına SVG dosyalarını, tek bir SVG sprite'ı veya bu çıktıları birlikte içeren ZIP paketini seç.

Sprite dosyası açıldığında ikonları bir ızgarada gösterir; aynı zamanda her ikon `<symbol>` olarak tanımlı ve `<use href="ikon-sprite.svg#ikon-adi-varyant">` ile yeniden kullanılabilir.

SVG çıktısı piksel çözünürlüğünden bağımsızdır; genişlik, yükseklik ve ölçek değerleri SVG tuvaline ve `viewBox`'a uygulanır. PNG dışa aktarımı bu uygulamanın kapsamında değildir.

Vektörleştirme, şeffaf kenar boşluklarını kırpar ve kenarlarda baskın olan düz arka plan rengini (yakın tonlarıyla birlikte) şeffaflaştırır. Tek renkli özel palet, şeffaf arka planı koruyarak yalnızca çizimi renklendirir.

Rozet veya ikon içindeki kapalı arka plan alanlarını temizlemek için ikonu seçip **Arka planı ayır** düğmesine tıkla.
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
bunları katman olarak gösterir. Soldaki **Katmanlar** listesinde bir rengi gizlemek, o rengi önizlemeden, SVG
dosyalarından, sprite'tan ve ZIP'ten siler. Kaldırılan rengin altındaki alan da saydam olur.

Bu seçimler önizlemeye, SVG dosyalarına, sprite'a ve ZIP'e birlikte uygulanır. Otomatik algılama veya tek renk
paleti kuş, yazı ya da benzeri nesneleri anlamsal olarak tanımaz. Karmaşık görsellerde kaldırılacak bölgeleri
kullanıcı seçmelidir.

İşleme yereldir; PNG görselleri bilgisayardan dışarı gönderilmez. Vektörleştirme kütüphanesinin lisansı `src/main/resources/META-INF/third-party/imagetracerjava-LICENSE.txt` dosyasındadır.
