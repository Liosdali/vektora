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

SVG çıktısı piksel çözünürlüğünden bağımsızdır; genişlik, yükseklik ve ölçek değerleri SVG tuvaline ve `viewBox`'a uygulanır.

Aynı menüde raster çıktılar da vardır: **Saydam PNG** her ikonu tuval ölçüsünün 1, 2 ve 4 katında (`ikon.png`, `ikon@2x.png`, `ikon@4x.png`) yazar; **Favicon ve uygulama ikonları** 16–512 piksel kare PNG'ler ile 16/32/48 piksellik bir `ikon.ico` üretir. **Katmanları ayrı SVG** her rengi yalnızca o renkte görünen alanla ayrı bir dosyaya yazar (baskı ve kesim plotter'ları için). Tuval bölümündeki **SVG'yi küçült** ortak öznitelikleri katman grubuna taşır, bitişik yolları birleştirir ve koordinatları yuvarlar; görünüş değişmeden dosya genellikle %40–70 küçülür. **Boşluksuz (mozaik) çizim** şekillerin üst üste binmediği bir SVG verir. PDF/EPS çıktısı henüz yoktur.

Vektörleştirme, şeffaf kenar boşluklarını kırpar ve kenarlarda baskın olan düz arka plan rengini (yakın tonlarıyla birlikte) şeffaflaştırır. Tek renkli özel palet, şeffaf arka planı koruyarak yalnızca çizimi renklendirir.

**Konuyu otomatik bul**, fotoğraf, degrade veya dokulu zeminler gibi düz renkle silinemeyen arka planlar içindir.
Düğme bir konu ayırma modelini bilgisayarında çalıştırır ve konunun dışında kalan her şeyi siler. Seçim ekranında
silinecek alanlar kırmızıyla görünür. Koru fırçası, elle silme ve **Geri al** bu sonucun üzerinde de çalışır.
**Yalnızca konuyu koru** kutusu, modeli yeniden çalıştırmadan sonucu kapatıp açar. **Tüm görsellerde bul**
paketteki her görselde konuyu ayırır. Her görsel ayrıca geri alınabilir. Üç yöntem vardır:
**Modelsiz · GrabCut** indirme gerektirmez ve çevrimdışı çalışır; renk modelleri ile grafik kesmeyi kullanan klasik bir
yöntemdir, aynı görsel her seferinde aynı sonucu verir. Konu zeminle aynı renkleri taşıyorsa yanılabilir; o zaman seçim
ekranında **Konu çerçevesi** ile konunun çevresine çerçeve çiz ya da fırçalarla düzelt (fırça darbeleri kesin kısıt olur
ve yöntem kendiliğinden yeniden çalışır). Modelli yöntemler **Hızlı · U²-Net-p** (4,4 MB) ve **Kaliteli · ISNet**
(170 MB) yereldir. Seçilen model ilk kullanımda, onay verdikten sonra
bir kez `~/.vektora/models` klasörüne indirilir ve boyutu ile SHA-256 özeti doğrulanır. Görseller bilgisayardan
çıkmaz. Model kaynakları ve lisansları `src/main/resources/META-INF/third-party/segmentation-models-NOTICE.txt`
dosyasındadır.

Rozet veya ikon içindeki kapalı arka plan alanlarını temizlemek için ikonu seçip **Elle seç ve düzelt** düğmesine tıkla.
Seçim ekranında üç araç vardır:

- **Bölge seç (W):** Tıkladığın pikselle bağlantılı, rengi **Renk toleransı** sınırında kalan alan silinir. Tolerans
  göze göre renk farkıdır (CIELAB ΔE: 2 zor fark edilir, 10 açıkça farklıdır) ve her ikon için ayrı ayarlanır
  (varsayılan 12). **Degradeyi takip et** her pikseli yanındaki seçili alanla karşılaştırır, böylece yavaş değişen bir
  fon tek tıkla seçilir. **Bitişik** kapatılırsa tıklanan renge benzeyen bütün pikseller, bağlı olmasalar da silinir.
- **Fırçayla sil (E):** Boyadığın alan renginden bağımsız olarak saydamlaştırılır.
- **Fırçayla koru (P):** Boyadığın alan hiçbir araçla silinmez. Yanlışlıkla silinen yerleri geri getirmek için kullanılır.
  **B** son kullanılan fırçaya döner; fırçanın boyutu imleçte daire olarak görünür.
- **Konu çerçevesi (F):** Konunun çevresine çerçeve sürükle; modelsiz ayırma çerçevenin dışını arka plan sayar.

Araç çubuğunun altındaki **Önerilen bölgeler**, hâlâ görünen en büyük renk alanlarını görünür alana oranlarıyla
listeler. Görselin kenarına değen alanlar önce gelir. Bir önerinin üzerine gelmek silinecek alanı sarıyla gösterir;
tıklamak o alana tıklamakla aynıdır.

**Maske** görünümünde silinecek alanlar (otomatik kenar temizliği dahil) kırmızıyla, **Sonuç** görünümünde
saydam hâliyle gösterilir. Fare tekerleği yakınlaştırır, sağ tıkla ya da boşluk tuşuna basılı tutarak sürüklemek görüntüyü kaydırır. **Kenar halesini temizle**
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

## Proje, katmanlar ve toplu işlem

- **Proje** menüsü paketi `.vektora` dosyası olarak kaydeder ve açar (Ctrl+S, Ctrl+O, son projeler). Dosya bütün seçimleri,
  maskeleri, katman düzenlemelerini ve ayarları taşır; yeniden açılan proje aynı SVG'yi bayt bayt üretir. Kaydedilmemiş
  değişiklik varken pencere başlığı • ile işaretlenir ve kapatırken sorulur. Geri alma geçmişi kaydedilmez.
- Görseller pencereye sürüklenerek ya da Ctrl+V ile panodan eklenebilir; bırakılan bir `.vektora` dosyası açılır.
  **Yinele** (Ctrl+Shift+Z veya Ctrl+Y) geri alınan arka plan düzenlemesini yeniden uygular.
- Yeni paketler 8 renge indirgenmiş ve "Ayrıntılı" detayla başlar. Kaynak renkleri korunurken 64'ten fazla katman
  çıkarsa önizlemenin üstünde tek tıkla renk azaltma önerilir.
- Katman satırındaki **⋯** menüsü katmanı yeniden adlandırır (SVG grup kimliği ve Inkscape katman adı olur), çizim
  sırasında taşır ya da başka bir katmanla birleştirir.
- **Şekilleri oturt** daireye ya da elipse benzeyen çizgileri kusursuz eğrilerle, neredeyse düz kenarları düz
  çizgilerle yeniden çizer. **Simetri** sol ya da üst yarıyı ötekine aynalar.
- **Toplu işlem** bölümü seçilen bir renge yakın kenar bölgelerini bütün pakette siler ve seçili görselin tolerans,
  hale temizliği, konu ayırma yöntemi ve katman düzenlemelerini diğer görsellere aktarır. Paket listesi her görselin
  işlenmemiş, arka planı ayrılmış ya da katmanları düzenlenmiş olduğunu gösterir.
- Önizleme tekerlekle yakınlaştırılır, sağ tıkla kaydırılır, çift tıkla sığdırılır. Başlıktaki **Açık tema** düğmesi
  açık temaya geçer.

## Geliştirme

- **Testler:** `mvn test`. Gerçek konu ayırma modeliyle çalışan test yalnızca `-Dvektora.modelDir=<klasör>`
  `u2netp.onnx` dosyasını gösterdiğinde çalışır. `IconWorkspaceFlowTest` arayüzü JavaFX üzerinde gerçekten
  sürer (görsel ekleme, arka plan seçme, geri alma, katman gizleme); ekran yoksa kendiliğinden atlanır.
  Linux'ta sanal ekranla çalıştırmak için: `xvfb-run -a mvn test`.
- **CI:** `.github/workflows/ci.yml` her push ve PR'da Windows ve Linux üzerinde testleri çalıştırır. Model bir kez
  indirilir, SHA-256 ile doğrulanır ve önbelleğe alınır.
- **Kurulum paketi:** `./packaging/package-windows.ps1` kendi Java çalışma zamanını taşıyan bir Windows
  kurulum dosyası (`-Type exe` veya `msi`, WiX Toolset 3 gerekir) ya da kurulumsuz bir uygulama klasörü
  (`-Type app-image`) üretir; çıktı `target/installer` altındadır. Paket, betiği çalıştıran JDK'nın çalışma
  zamanını taşır. ONNX Runtime'ın 1.20.0'da kilitli olmasının nedeni `pom.xml`'de yazılıdır; pakete daha yeni bir
  MSVC çalışma zamanı eklenirse yükseltilebilir.
- **Önizleme performansı:** Aynı ayarlarla yapılan izlemeler önbellekten gelir; katman gizlemek ya da önizlemeyi
  yeniden açmak yeniden izleme yapmaz. 2000 pikselden büyük görsellerin önizlemesi en fazla 1000 piksellik bir
  kopyadan, tam görselin renkleriyle izlenir; dışa aktarım her zaman tam boyuttadır. Önizleme sekmelerinden
  yalnızca seçili olan bir tarayıcı motoru tutar.
