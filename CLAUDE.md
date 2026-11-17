# Theoria — Claude Code proje talimatları

Minecraft **Java 26.3** · **NeoForge 26.3.x** (Fabric'e geçiş Q1'e bağlı) · **Java 25** · Vulkan çalışıyor (D7) + Vitrail shader. **Yalnızca Vulkan (D19): OpenGL hedeflenmez, Iris yok.**
Kararlar: `docs/10-acik-sorular.md` §A (D1-D7).
Tek oyuncu önceliği; sunucu-otoriter mimari (çok oyunculu uyumluluğu bedava gelsin diye).
Dil: dokümanlar ve oyun içi metin Türkçe + İngilizce lang dosyası; kod yorumları İngilizce/Türkçe karışık olabilir ama kısa.

## Her oturumun başında (sırayla)
1. `STATUS/PROGRESS.md` oku — şu an hangi fazdayız, son durum, bir sonraki adım.
2. O fazın prompt'unu oku: `prompts/faz-NN-*.md`.
3. Sadece o fazın ilgili `docs/` dosyalarını oku (hepsini değil). İndeks: `README.md`.

## Her fazın/alt adımın sonunda (ZORUNLU protokol)
1. Testleri/doğrulama araçlarını çalıştır; sonuçları dürüstçe yaz (başarısız olanı gizleme).
2. `docs/journal/fNN.md` dosyasına: ne yapıldı, ne öğrenildi, hangi karar neden alındı (kısa, tarihli).
3. `STATUS/PROGRESS.md` güncelle (durum tablosu + "Bir sonraki adım" + açık sorunlar).
4. Kullanıcı isterse commit at (kendiliğinden commit/push yapma).
5. Kullanıcıya **devir bildirimi** ver — tam bu biçimde (şablon: `prompts/_devir-sablonu.md`):
   `➡ SONRAKİ: <faz/adım> · Model: <Sonnet 5.5|Opus 5.5> · Effort: <high|xHigh|max> · Önce: </clear|/compact> · Başlat: prompts/<dosya>`
   Gerekçeyi tek cümleyle ekle. Model değişimi yalnızca `/clear` sınırında önerilir (önbellek kaybı).

## Mühendislik kuralları (LucidRem derslerinden — bkz. docs/02-teknik-temel.md §7)
- **Render:** yalnızca Mojang'ın `GuiGraphics` / render-pipeline soyutlamasını kullan. Doğrudan OpenGL çağrısı ve özel GLSL/pipeline YOK. Shader desteği **Vitrail Shaders** (Vulkan-yerli) ile gelir; biz shader yazmayız ama içeriğimiz vanilla render tipleriyle çizilir ki shader paketleri (Complementary Reimagined) onu da işlesin. Iris Vulkan'da çalışmıyor ve kullanılmaz; OpenGL yedeği yok (D19) — görsel doğrulama yalnızca Vulkan'da.
- **Üçüncü taraf mod API'sine derleme yapma** (Relics dersi): Curios dışında hiçbir moda `compileOnly` bile bağlanma; entegrasyon gerekiyorsa tag/JSON/komut düzeyinde yap.
- **Ekranlar oyunu durdurmamalı** (`isPauseScreen() == false`) — yoksa tek oyunculuda sunucu paket işlemez, animasyonlar donar.
- **Her sayı tek kaynaktan gelir:** `data/balance/tiers.csv` ← `tools/balance/tiers.py`. Koda sihirli sayı gömme.
- **İçerik veri-güdümlü** (JSON/data component); Java yalnızca mekanik için. Yeni kademe eklemek = veri satırı eklemek olmalı.
- **Doğrulanabilirlik:** her sistem için bir `tools/check_*.py` veya oyun-içi test komutu (`/theoria test ...`). "Çalışıyor" demeden önce çalıştır.
- **Ekran görüntüsü alma** (kullanıcının özel pencereleri yakalanabilir). Görsel doğrulama çevrimdışı render/test ile; HUD'u kullanıcı onaylar.
- Tahmin etme: 26.x API'si 1.21.x'ten farklı. Emin değilsen decompile edilmiş vanilla kaynağa / NeoForge primer'ına bak, sonra yaz.
- Kullanıcıya soru gerekiyorsa `docs/10-acik-sorular.md`'ye ekle ve faz sonunda topluca sor.

## Model / effort rehberi
Özet: `docs/08-model-ve-baglam-rehberi.md`. Her faz prompt'unun başında önerilen model+effort yazılı.
Bir faz önerilenden daha zor çıkarsa devir bildirimine bunu yaz ("bu adım Opus 5.5 xHigh ister").

## Yapı
`docs/` tasarım · `prompts/` faz prompt'ları · `STATUS/` ilerleme · `mods-src/` NeoForge çok-modüllü Gradle projesi ·
`pack/` modlist + packwiz · `generator/` veri/doku üreteçleri · `tools/` doğrulama ve denge araçları · `data/` tek-kaynak veri.
