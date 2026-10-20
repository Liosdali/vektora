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

SVG çıktısı piksel çözünürlüğünden bağımsızdır; genişlik, yükseklik ve ölçek değerleri SVG tuvaline ve `viewBox`'a uygulanır. PNG dışa aktarımı bu uygulamanın kapsamında değildir.

İşleme yereldir; PNG görselleri bilgisayardan dışarı gönderilmez. Vektörleştirme kütüphanesinin lisansı `src/main/resources/META-INF/third-party/imagetracerjava-LICENSE.txt` dosyasındadır.
