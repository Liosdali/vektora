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

1. **PNG ekle** ile bir veya daha fazla ikon dosyası seç. Geçerli görseller pakete eklenir; okunamayan dosyalar ayrıca bildirilir.
2. Soldaki listeden bir ikon seçerek SVG önizlemesini aç.
3. **Paket ayarları** altında SVG tuval genişliğini/yüksekliğini ve görünür ikon ölçeğini ayarla. Örneğin `0.5`, ikonu tuval içinde yarı boyuta getirir.
4. **Vektör ve renk** menüsünden kaynak renklerini koru, kaynak renk sayısını azalt veya özel paletleri kullan seçeneklerinden birini seç.
5. Özel palet modunda **Palet ekle** ile yeni bir renk varyantı oluştur; palete istediğin sayıda renk ekleyebilir veya renk seçicilerden değiştirebilirsin. Her palet ikon başına ayrı bir SVG varyantı üretir.
6. **Dışa aktar** menüsünden ikon başına SVG dosyalarını, tek bir SVG sprite'ı veya bu çıktıları birlikte içeren ZIP paketini seç.

Sprite dosyası açıldığında ikonları bir ızgarada gösterir; aynı zamanda her ikon `<symbol>` olarak tanımlı ve `<use href="ikon-sprite.svg#ikon-adi-varyant">` ile yeniden kullanılabilir.

SVG çıktısı piksel çözünürlüğünden bağımsızdır; genişlik, yükseklik ve ölçek değerleri SVG tuvaline ve `viewBox`'a uygulanır. PNG dışa aktarımı bu uygulamanın kapsamında değildir.

Vektörleştirme, şeffaf kenar boşluklarını kırpar ve kenarlarda baskın olan düz arka plan rengini (yakın tonlarıyla birlikte) şeffaflaştırır. Tek renkli özel palet, şeffaf arka planı koruyarak yalnızca çizimi renklendirir.

Rozet veya ikon içindeki kapalı arka plan alanlarını temizlemek için ikonu seçip **Arka plan bölgelerini seç** düğmesine tıkla. Kaynak PNG'de silmek istediğin bağlı renk alanını seç; gerekirse diğer alanlar için tekrar tıkla. Seçilen pikselin renginden her kanalda en fazla 24 farklı olan, dört yönden bağlı pikseller saydamlaştırılır; kopuk ama aynı renkteki alanlar korunur. **Seçimleri temizle** yalnızca seçili ikonun seçimlerini sıfırlar. Bu seçimler önizlemeye, SVG dosyalarına, sprite'a ve ZIP'e birlikte uygulanır. Otomatik algılama veya tek renk paleti kuş, yazı ya da benzeri nesneleri anlamsal olarak tanımaz; karmaşık görsellerde kaldırılacak bölgeleri kullanıcı seçmelidir.

İşleme yereldir; PNG görselleri bilgisayardan dışarı gönderilmez. Vektörleştirme kütüphanesinin lisansı `src/main/resources/META-INF/third-party/imagetracerjava-LICENSE.txt` dosyasındadır.
