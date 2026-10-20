# Vektora geliştirme yol haritası

Tarih: 2026-10-20 · Dal: `feat/background-separation-and-layers`

Bu belge, uygulamanın bugünkü hâlinden sonra nelerin geliştirilebileceğini öncelik sırasıyla listeler. Her madde için
neden önemli olduğu, nasıl yapılacağı, kabul ölçütleri ve kabaca bir büyüklük (S: birkaç saat, M: bir-iki gün, L: bir
haftaya yakın) verilmiştir. En öncelikli madde, istenen **yapay zekâ kullanmayan otomatik arka plan silme** seçeneğidir.

## Bugünkü durum

Bu dalda şunlar var:

- **Arka plan:** Kenarlardaki düz zemin otomatik siliniyor (JPG gürültüsüne dayanıklı). Elle bölge seçimi ayarlanabilir
  renk toleransıyla yapılıyor. Fırçayla silme ve koruma, geri alma (Ctrl+Z), önerilen bölgeler, kenar halesi temizliği,
  kırmızı maske ve sonuç görünümü var.
- **Yapay zekâ ile konu ayırma:** U²-Net-p ve ISNet modelleri ONNX Runtime ile yerelde çalışıyor. Model ilk kullanımda
  indiriliyor ve SHA-256 ile doğrulanıyor. Toplu çalıştırma da var.
- **SVG:** Silinen alanlar gerçek oyuk olarak çıkıyor. Yollar çizim sırası korunarak renk katmanlarına gruplanıyor.
  Katmanlar gizlenip yeniden renklendirilebiliyor. Önce/sonra karşılaştırma görünümü var. Renk azaltma deterministik,
  Detay kaydırıcısı ara tonları birleştiriyor.
- **Eksikler:** Proje kaydetme/açma yok, sürükle-bırak yok, CI yok, arayüz testleri yok.

Konu ayırmanın bugün tek otomatik yolu yapay zekâ. Model indirilmeden ya da istenmeden, düz renkli olmayan bir zemini
otomatik silmenin yolu yok. Kenar temizliği yalnızca tek renkli düz zeminlerde çalışıyor.

## Öncelik özeti

| # | Başlık | Neden | Büyüklük |
|---|--------|-------|----------|
| 1 | Yapay zekâsız otomatik arka plan silme (GrabCut) | İstenen özellik. Model indirmeden degrade ve dokulu zeminleri ayırır. | L |
| 2 | Degradeye dayanıklı kenar temizliği ve "bitişik değil" seçimi | Düz olmayan ama sade zeminler için hızlı, modelsiz çözüm. | M |
| 3 | Proje kaydetme ve açma | Bugün uygulama kapanınca bütün emek kayboluyor. | M |
| 4 | Algısal renk karşılaştırma (Lab ΔE) | Tolerans göze göre değil, RGB kanallarına göre çalışıyor. | S |
| 5 | Mantıklı varsayılanlar ve katman patlamasına önlem | "Kaynak renklerini koru" yüzlerce katman üretebiliyor. | S |
| 6 | Katman düzenleme: birleştir, sırala, ayrı dışa aktar | Logo üretiminde katman kontrolü eksik kalıyor. | M |
| 7 | Toplu işlem kuralları | Benzer rozet setlerinde ayarları tek tek tekrarlamak gerekiyor. | M |
| 8 | Dışa aktarım iyileştirmeleri | PNG yok, SVG optimize edilmiyor. | M |
| 9 | Vektör kalitesi | Kenarlar yer yer tırtıklı, simetri ve şekil oturtma yok. | L |
| 10 | Arayüz ve kullanım kolaylığı | Sürükle-bırak, kısayollar, yakınlaştırma eksik. | M |
| 11 | Teknik altyapı | CI, arayüz testleri, kurulum paketi, ONNX Runtime sürüm kilidi. | M |

---

## 1. Yapay zekâsız otomatik arka plan silme (GrabCut)

**Amaç:** "Konuyu otomatik bul" seçeneğine model gerektirmeyen bir yöntem eklemek. Kullanıcı modeli indirmek
istemediğinde, çevrimdışıyken ya da model yanlış sonuç verdiğinde kullanılır.

**Yöntem:** Rother, Kolmogorov ve Blake'in GrabCut algoritması (Microsoft Research, 2004). Tamamen klasik bir görüntü
işleme yöntemidir; eğitilmiş model kullanmaz, deterministiktir.

1. **Başlangıç:** Kenarlara değen pikseller "kesin arka plan", ortadaki dikdörtgen "olası ön plan" sayılır. Dikdörtgen
   varsayılan olarak görselin %8 içeriden kırpılmış hâlidir; kullanıcı seçim ekranında yeniden çizebilir.
2. **Renk modelleri:** Ön plan ve arka plan için ayrı ayrı 5 bileşenli Gauss karışım modeli (GMM) kurulur.
3. **Grafik kesme:** Her piksel bir düğümdür. Veri terimi, pikselin iki GMM'ye uyumundan gelir. Komşuluk terimi, benzer
   renkli komşuların aynı tarafta kalmasını ödüllendirir. En küçük kesim Boykov–Kolmogorov max-flow ile bulunur.
4. **Yineleme:** GMM'ler yeni etiketlere göre güncellenir ve 3. adım tekrarlanır; genellikle 4–6 tur yeterlidir.
5. **Kullanıcı düzeltmesi:** Mevcut **Fırçayla koru** darbeleri "kesin ön plan", **Fırçayla sil** darbeleri "kesin arka
   plan" kısıtı olarak verilir ve algoritma yeniden çalışır. Yeni araç gerekmez; mevcut fırçalar anlam kazanır.

**Mimari:**

- `org.example.segmentation.GrabCutSegmenter implements SubjectSegmenter`. Mevcut arayüz aynen kullanılır, sonuç yine bir
  `SubjectMask` olur. Böylece maske katmanı, "Yalnızca konuyu koru", geri alma ve dışa aktarım ek kod gerektirmez.
- `SegmentationModel` listesine üçüncü seçenek eklenir: **"Modelsiz · GrabCut (indirme yok)"**. İndirme onayı ve
  `ModelStore` bu seçenek için atlanır.
- Max-flow ayrı ve test edilebilir bir sınıf olur (`MaxFlowGraph`). GMM ayrı bir sınıf olur (`ColorMixtureModel`).
- Performans: Büyük görseller en fazla yaklaşık 512 piksel kenara küçültülerek işlenir. Maske özgün boyuta büyütülür ve
  kenar bandında tam çözünürlükte bir kez daha iyileştirilir. Hedef: 1024×1024 görselde 2 saniyenin altında.
- Dış bağımlılık eklenmez. OpenCV'nin `grabCut` işlevi yerel kütüphane gerektirir ve ONNX Runtime'da yaşadığımız Windows
  çalışma zamanı sorununa benzer riskler taşır. Saf Java uygulaması tercih edilir.

**Kabul ölçütleri:**

- Gürültülü degrade zemin üzerindeki test rozetinde (`badge-photo.png`) konu, model kullanmadan ayrılır. Zeminin en az
  %95'i silinir, rozetin %1'inden azı kaybolur.
- Koru/sil fırça darbeleri yeniden çalıştırmada kesin kısıt olarak uygulanır.
- Aynı girdi her seferinde aynı maskeyi verir.
- İnternet bağlantısı olmadan çalışır; arayüzde indirme penceresi açılmaz.
- Birim testleri: max-flow küçük grafiklerde bilinen kesimi bulur, GMM iki belirgin rengi ayırır, uçtan uca sentetik
  disk testi geçer.

**Büyüklük:** L. Max-flow ve GMM yaklaşık 2 gün, entegrasyon ve arayüz 1 gün, performans ve testler 1–2 gün sürer.

**Riskler:** Arka planla aynı renkleri taşıyan konularda (örneğin beyaz zemin üzerinde beyaz bölgeleri olan logo)
GrabCut yanılabilir. Bu durumda fırça kısıtları ve önerilen bölgeler devreye girer. Bu sınırlama arayüzde dürüstçe
belirtilmelidir.

## 2. Degradeye dayanıklı kenar temizliği ve "bitişik değil" seçimi

GrabCut'tan daha basit, anında çalışan iki modelsiz seçenek:

- **Komşuya göre büyüyen bölge:** Bugünkü bölge seçimi her pikseli tıklanan renkle karşılaştırıyor. Bu yüzden yumuşak
  bir degrade kısa sürede tolerans dışına çıkıyor. Bir seçenek eklenir: her piksel, bölgeye bağlandığı komşusuyla
  karşılaştırılır ve yerel farkın üst sınırı ayrıca tutulur. Böylece yavaş değişen gökyüzü veya stüdyo fonu tek
  tıklamayla seçilir. Otomatik kenar temizliği de aynı moda geçirilebilir. Mevcut test
  `comparesNeighborsToTheFixedSeedColorRatherThanExpandingToleranceTransitively` varsayılan davranışı korumalı; yeni mod
  isteğe bağlı olmalıdır.
- **Bitişik değil (renk anahtarı):** Photoshop'taki "Contiguous" ayarının karşılığı. Tıklanan renge benzeyen bütün
  pikseller, birbirine bağlı olmasa da silinir. Harflerin içindeki kapalı boşluklar ve kareli desenler gibi dağınık
  zeminler için gerekir.
- Arayüz: Seçim ekranındaki "Bölge seç" aracının yanına iki küçük anahtar eklenir: "Degradeyi takip et" ve "Bitişik".
  İkisi de `BackgroundSelection` içinde tohumla birlikte saklanır, böylece geri alma ve dışa aktarım aynı kalır.

**Kabul:** 120 piksel boyunca beyazdan açık griye geçen bir zemin tek tıklamayla seçilir. Bitişik kapalıyken kopuk iki
aynı renkli alan birlikte silinir.

**Büyüklük:** M.

## 3. Proje kaydetme ve açma

Bugün tüm seçimler, maskeler ve katman ayarları bellekte duruyor ve uygulama kapanınca kayboluyor.

- `.vektora` dosyası: Bir ZIP içinde `project.json` (sürüm numarası, ikonlar, seçimler, tolerans, hale temizliği,
  gizli ve yeniden renklendirilmiş katmanlar, iz ayarları, paletler, tuval) ile kaynak görseller ve konu maskeleri
  (PNG olarak) saklanır.
- JSON için hafif bir kütüphane (Jackson veya Gson) eklenir ya da kayıtlar için küçük bir elle yazılmış yazıcı
  kullanılır.
- Menü: **Projeyi kaydet** (Ctrl+S), **Projeyi aç** (Ctrl+O), son açılan projeler listesi. Kaydedilmemiş değişiklik
  varken kapatmada uyarı gösterilir.
- Geri alma geçmişi kaydedilmez; açılan projede geçmiş boş başlar.

**Kabul:** Kaydet, kapat, aç döngüsünden sonra önizleme ve dışa aktarılan SVG bayt düzeyinde aynıdır.

**Büyüklük:** M.

## 4. Algısal renk karşılaştırma (Lab ΔE)

Tolerans bugün RGB kanallarındaki en büyük farka bakıyor. Göz maviye daha az, yeşile daha çok duyarlı olduğu için
aynı sayı farklı renklerde farklı hissediliyor.

- `matchesBackground` sRGB'yi CIELAB'a çevirip CIE76 ΔE ile karşılaştırır. Bir 256³'lük önbellek gerekmez, çevirme
  her piksel için ucuzdur. Gerekirse ilk kullanımda tohum rengi için bir kez yapılır.
- Kaydırıcının ölçeği ΔE'ye uyarlanır: varsayılan yaklaşık 12. Mevcut testlerdeki gri örnekleri (100/120/140) bu
  ölçekle aynı sonucu verir.
- Aynı ölçü önerilen bölgelerde ve hale temizliğinde de kullanılır.

**Kabul:** Mavi ve yeşil tonlarda eşit algılanan farklar aynı tolerans değeriyle seçilir. Mevcut testler geçer.

**Büyüklük:** S.

## 5. Mantıklı varsayılanlar ve katman patlamasına önlem

- Kenarları yumuşatılmış bir görsel "Kaynak renklerini koru" modunda yüzlerce katman üretiyor (test rozetinde 365).
- Önerilen varsayılan: **Renk sayısını azalt (8) + Detay "Ayrıntılı"**. Test rozetinde bu ayar 2–3 temiz katman veriyor.
- Kaynak modunda 64'ten fazla renk bulunursa önizlemenin üstünde bir bilgi şeridi gösterilir: "Bu görselde 365 ton var.
  Renk sayısını azaltmak ister misin?" Şeritte tek tık düğmesi olur.
- İlk açılışta kısa bir "nasıl çalışır" kartı gösterilir: 1) Görsel ekle, 2) Arka planı ayır, 3) Katmanları düzenle,
  4) Dışa aktar.

**Büyüklük:** S.

## 6. Katman düzenleme

A tasarımındaki "seçili katman" panelinin geri kalanı:

- **Birleştir:** İki katman tek renge indirilir. Yeniden renklendirmeyle aynı mekanizma kullanılır.
- **Sırala:** Yukarı/aşağı taşıma. Yığılmış çizimde sıranın görüntüyü değiştirebileceği arayüzde belirtilir.
- **Katmanı ayrı SVG olarak dışa aktar:** Baskı ve kesim plotter kullanıcıları için her renk ayrı dosyaya yazılır.
- **Katman adları:** "Kuş", "Halka" gibi kullanıcı adları verilir ve SVG'de `id` ile Inkscape `inkscape:label`
  olarak yazılır.
- **Mozaik (boşluksuz) mod:** Silinen alanlar için kullanılan `Area` çıkarma yöntemi, her katmanı üstündekilerden
  çıkarmak için de kullanılabilir. Sonuç, şekillerin üst üste binmediği bir SVG olur. Bu, vinil kesim ve gravür için
  önemlidir.

**Büyüklük:** M.

## 7. Toplu işlem kuralları

- Bugün elle seçimler piksel koordinatlarıyla saklanıyor ve başka görsele taşınamıyor.
- **Renk kuralı:** "Bu renge ΔE ≤ 12 yakın kenar bölgelerini sil" gibi bir kural tüm pakete uygulanır.
- **Ayar kopyalama:** Tolerans, hale temizliği, konu ayırma yöntemi, Detay ve katman renkleri seçili görselden diğer
  görsellere kopyalanır.
- Paket listesinde her görselin durumu gösterilir: işlenmemiş, arka planı ayrılmış, katmanları düzenlenmiş.

**Büyüklük:** M.

## 8. Dışa aktarım iyileştirmeleri

- **PNG dışa aktarımı:** Saydam PNG çıktısı 1x/2x/4x olarak alınır. SVG, JavaFX ile ya da `Area` geometrisi üzerinden
  Java2D ile rasterize edilir. README'deki "kapsam dışı" notu güncellenir.
- **SVG optimizasyonu:** Koordinat hassasiyeti ayarlanır (0/1/2 ondalık). Aynı renkli ardışık yollar tek `path`'te
  birleştirilir. Gereksiz `stroke` öznitelikleri kaldırılır. Hedef: test rozetinde dosya boyutunu %30–50 küçültmek.
- **Favicon/uygulama ikonu seti:** 16–512 px PNG ve `.ico` çıktısı tek tıklamayla üretilir.
- **PDF/EPS:** Talep olursa sonraya bırakılabilir.

**Büyüklük:** M.

## 9. Vektör kalitesi

- ImageTracer 1.1.2 eski bir sürüm. Kenarlar yer yer tırtıklı, eğri oturtma kaba.
- **Seçenek A:** Potrace benzeri eğri oturtmayı Java'da uygulamak. Önce köşe tespiti, sonra Bézier oturtma. Çok renkli
  çizim için katman başına ikili maske ile kullanılır.
- **Seçenek B:** VTracer'ı (Rust) yerel kütüphane olarak bağlamak. Kalite yüksek, ancak yerel bağımlılık ve platform
  paketleme yükü getirir. ONNX Runtime deneyimi bu riskin gerçek olduğunu gösterdi.
- **Seçenek C:** ImageTracer çıktısını sonradan düzeltmek: çok kısa kenarları birleştirmek, neredeyse düz çizgileri
  doğrultmak, dairesel yayları tanıyıp gerçek daire/elips olarak yazmak (Vectorizer.AI'daki "şekil oturtma").
- **Öneri:** Önce C (en düşük risk), sonra A.
- **Simetri:** Dikey veya yatay simetrik logolar için yarım çizip aynalama seçeneği eklenir.

**Büyüklük:** L.

## 10. Arayüz ve kullanım kolaylığı

- **Sürükle-bırak:** Görselleri pencereye bırakarak eklemek ve panodan yapıştırmak (Ctrl+V).
- **Kısayollar:** B fırça, E sil, P koru, W bölge seç, boşluk tuşuyla kaydırma, Ctrl+Shift+Z yinele. Yinele (redo)
  bugün yok, eklenmeli.
- **Önizlemede yakınlaştırma:** WebView içinde tekerlekle yakınlaştırma ve kaydırma.
- **Fırça imleci:** Seçim ekranında fırçanın boyutunu gösteren bir daire imleci.
- **Önerilen bölgelerin vurgulanması:** Çipin üzerine gelince ilgili bölge seçim ekranında sarı ile gösterilir.
- **Erişilebilirlik:** Tüm düğmelerin klavyeyle erişilebilir olduğu ve odak halkalarının göründüğü kontrol edilir.
  Metin kontrast oranları (en az 4.5:1) gözden geçirilir.
- **Dil:** Türkçe metinler bir kaynak dosyasına taşınır ve İngilizce eklenir.
- **Açık tema:** İsteğe bağlı. CSS değişkenleri hazır olduğu için düşük maliyetli.

**Büyüklük:** M.

## 11. Teknik altyapı

- **CI:** GitHub Actions'ta Windows ve Linux üzerinde `mvn test`. Gerçek model testi için model önbelleğe alınır ve
  `-Dvektora.modelDir` verilir.
- **Arayüz testleri:** TestFX ile temel akışlar test edilir: görsel ekle, bölge seç, önizle, dışa aktar. Bu oturumda
  ekran görüntüsü almak için kullanılan küçük harness bu testlere temel olabilir.
- **Kurulum paketi:** `jpackage` ile kendi JRE'sini taşıyan bir Windows kurulum dosyası hazırlanır. Pakete yeni bir MSVC
  çalışma zamanı konursa, şu an 1.20.0'da kilitli olan ONNX Runtime yükseltilebilir. Kilidin nedeni `pom.xml`'de
  yazılı.
- **Önizleme performansı:** Kaydırıcılar sürüklenirken izleme yeniden başlatılmamalı (debounce), aynı ayarlarla izleme
  sonucu önbelleğe alınmalı. Büyük görsellerde (2000 px üstü) önizleme küçültülmüş kopya üzerinde yapılır, dışa aktarım
  tam boyutta yapılır.
- **Bellek:** Her önizleme sekmesinde bir WebView açılıyor. Çok paletli projelerde sekmeler gerektiğinde yüklenmeli.

**Büyüklük:** M.

---

## Önerilen sıra

1. **Hafta 1:** 5 (varsayılanlar), 4 (Lab ΔE), 2 (degrade ve bitişik değil seçimi). Hızlı kazanımlar; modelsiz
   silme bugünden güçlenir.
2. **Hafta 2:** 1 (GrabCut). İstenen modelsiz otomatik silme.
3. **Hafta 3:** 3 (proje kaydetme), 10'un sürükle-bırak ve yinele kısmı.
4. **Hafta 4:** 6 (katman düzenleme ve mozaik mod), 8 (PNG ve SVG optimizasyonu).
5. **Sonra:** 7 (toplu kurallar), 9 (vektör kalitesi), 11 (CI, kurulum paketi).

Her adım kendi dalında, testleriyle birlikte ve ekran görüntüsüyle doğrulanarak ilerler.
