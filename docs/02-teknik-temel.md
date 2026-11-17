# 02 — Teknik temel

Doğrulama tarihi: **9 Kasım 2026** (Modrinth API, NeoForge Maven, minecraft.wiki, resmî sürüm notları). **Faz 0'da makinede doğrulandı → `docs/adr/ADR-001..005`**; 26.3 API farkları: `docs/journal/f00.md`.

## 1. Sürüm kararı (öneri: 26.3)
| | 26.2 "Chaos Cubed" | 26.3 "Wilderness Bound" |
|---|---|---|
| Çıkış | 16 Haz 2026 | 15 Eyl 2026 |
| NeoForge | 26.2.0.93 | **26.3.0.101** |
| Vulkan | Deneysel, opt-in | Deneysel, opt-in (26.4 snapshot'larında **varsayılan** oluyor) |
| Fantastic Wings | 26.2.0 | **26.3.1** |
| JEI / Curios | 30.48 / 16.0.0 | **31.17 / 17.0.1** |
| Sodium | 0.9.2 | 0.9.3-beta.4 |
| Mac Input Fixes (NeoForged) | yok | **var** |
| McMouser | var (26.1 build) | yok |
| Yeni vanilla | — | Dappled forest, Abandoned camp yapısı, minder/saman yatak |

**Öneri 26.3**: mod erişimi daha taze; yeni vanilla "abandoned camp" Haven fikriyle uyumlu. Risk: 26.4 çıkınca Vulkan varsayılan olacak; biz 26.3'te kalırız, 26.4'ü ayrı bir göç fazı olarak değerlendiririz.

## 2. Loader: NeoForge
Fantastic Wings, Curios (yalnızca NeoForge/Forge), Sophisticated vb. NeoForge'da; LucidRem deneyimimiz NeoForge. Fabric'e geçiş yok.
- NeoForge sürüm biçimi artık dört bileşenli (`26.3.0.101`); MDK yeni Gradle eklentisiyle (NeoGradle/ModDevGradle), **Gradle ≥ 9.1**, toolchain **Java 25**.
- **26.1'den beri Minecraft obfuscate değil** — Mojang isimleriyle gelir; Parchment gerekmez.
- 26.x vanilla kod değişiklikleri büyük (renk/render durumu "extract/submit" modeli, kayıt defterleri, codec adları `MAP_CODEC`). **1.21.1 bilgisine güvenme**; NeoForge primer'ı: docs.neoforged.net/primer/docs/26.1/ (+ 26.2/26.3 notları).

## 3. Ortam
| Parça | Durum (9 Kas 2026) | Eylem |
|---|---|---|
| Java | 21.0.12 (brew, global `JAVA_HOME` — LucidRem) + **Temurin 25.0.5** (`~/Library/Java/JavaVirtualMachines/temurin-25.jdk`, Faz 0) | Global `JAVA_HOME` **değişmez** (LucidRem Gradle 8.14 Java 25'te çalışmaz); Theoria Gradle daemon'u `gradle-daemon-jvm.properties` ile 25 seçer; kabuk: `source tools/env.sh` (ADR-001) |
| Prism Launcher | kurulu | Yeni instance: MC 26.3 + NeoForge 26.3.0.101 |
| Makine | i5-9400F · **RX 580 8 GB** (Metal 2) · 16 GB RAM · macOS 26.7.2 (Intel) | Oyuna 6-8 GB |
| IDE | VS Code | IntelliJ ≥ 2025.2/2025.3 Java 25 için daha rahat; RAM'e göre karar |
| Gradle | wrapper 9.2.1 + ModDevGradle 2.0.148 | `NeoForgeMDKs/MDK-26.3-ModDevGradle` (Faz 0) |

## 4. Vulkan ve shader (D7: Vulkan çalışıyor)
**Güncelleme 9 Kas:** Kullanıcının `Vulkan Optimization` instance'ı (26.3, Fabric) `preferredGraphicsBackend:"vulkan"` ile Sodium 0.9.3-beta + Vitrail 0.13.0-beta + Complementary Reimagined çalıştırıyor (log'da Vitrail pipeline/shader cache kayıtları var). Spike S1 artık "keşif" değil, **yeni NeoForge instance'ında aynı yığının tekrarı** ve FPS tabanı. Aşağıdaki "bilinmeyen" paragrafı tarihsel kayıt.

**Shader stratejisi:** Iris Vulkan'da çalışmaz → **Vitrail Shaders** (NeoForge 26.3 ✅). Kuralımız: kendi bloklarımız/entity'lerimiz/eşyalarımız **vanilla render tipleriyle** çizilsin (özel pipeline yok); böylece Vitrail paketleri (g-buffer, gölge, emissive) onları da işler. Emissive dokular için standart `_e`/emissive kaynak kurallarına uy. Shader paketine özel bir şey yazmayız.

### Eski değerlendirme (tarihsel)
**Bilinenler:** 26.2'den beri vanilla'da Graphics API seçeneği var (Default / Prefer Vulkan (Experimental) / Prefer OpenGL); Vulkan başarısızsa OpenGL'e düşer. Gereksinim: Vulkan 1.2 + dynamic rendering + push descriptors. macOS'ta MoltenVK (Vulkan→Metal). Sodium 0.9.x deneysel Vulkan desteği getirdi. Iris Vulkan modunda **çalışmıyor**. 26.3 snapshot 2'de "Mac'te Vulkan'da dünya pembe" bilinen hata olarak listelenmişti; Prism'de 26.2-snapshot-1 Mac Vulkan çökme raporu var.

**Bilinmeyen (ölçülecek):** RX 580 (Polaris, Metal 2) MoltenVK ile bu gereksinimleri sağlıyor mu? Sağlıyorsa performans OpenGL'den iyi mi?

**Strateji (iki arka uçta da çalışan kod):**
1. Spike **S1**: temiz vanilla 26.3, "Prefer Vulkan" → açılıyor mu, dünya render oluyor mu, FPS, kapanışta bellek sızıntısı (bilinen hata).
2. Sonuç **çalışıyor** → oyun Vulkan'la oynanır, geliştirme de Vulkan'la test edilir. **Çalışmıyor** → "Prefer OpenGL" ile oynanır, ama kod yine Vulkan-uyumlu yazılır (aşağıdaki kurallar), 26.4/MoltenVK güncellemesinde tekrar denenir.
3. **Kod kuralları (Vulkan-hazır):** yalnızca `GuiGraphics`/Mojang render pipeline API'si; `GL11/GL30` çağrısı yok; özel shader yok (gerekirse vanilla pipeline'a veri-güdümlü tanım); dokular normal resource; `RenderSystem` durum hileleri yok.
4. Performans araçları: Sodium (0.9.x) + Lithium + FerriteCore + EntityCulling; Distant Horizons opsiyonel (Vulkan altında uyumu ayrıca ölçülür).

## 5. Sürüm bağımlılık politikası
- Çekirdek = yalnızca **NeoForge + Minecraft + Curios API**'ye derleriz. Diğer her mod runtime'da isteğe bağlı.
- `pack/modlist.toml` her mod için `{slug, min, max, neden}` tutar; `tools/modmatrix/matrix.py` sürüm bazında erişilebilirliği Modrinth'ten çeker (Relics/LucidRem §52-53 dersi: sürüm aralığı yetmez, API taşınabilir).

## 6. Veri-güdümlü içerik ilkesi
26.x'te eşya davranışı büyük ölçüde **data component** ve JSON ile tanımlı (araç materyali, equipment asset, büyü etkileri, biyomlar, yapılar). Hedef: **yeni kademe/mob/katalizör = bir CSV/JSON satırı**. Java yalnızca yeni *mekanik* (Kataliz Masası mantığı, hasar formülü, HUD) için.
`generator/` → `data/*.csv` + şablonlardan datapack+resourcepack üretir; üretilen dosyalar git'e girer (diff görülebilir), elle düzenlenmez.

## 7. LucidRem'den taşınan dersler (`/Users/naer/code/LucidRem/project.md` §45.5, §53, §55, §58, §61)
1. **Pause'u bölen ekran** (`isPauseScreen()=true`) tek oyunculuda sunucuyu durdurur → Kanat tıklaması işlemez, animatör ilerlemez. Ekran `false` döndürmeli.
2. `renderEntityInInventoryFollowsMouse` **ham fare koordinatı** ister (fark değil); yoksa model donar.
3. **Kırmızı kalbi yeşile boyayamazsın** (`setColor` çarpar): üst katlar için nötr (beyaz) kalp sprite'ı üret, boya; 0. kat vanilla sprite aynen.
4. Yarım zırh sprite'ında boş yarı opak gri → katmanlı çizimde siyah yarım; üretecte saydamlaştır.
5. Sayılar boşluğa sığmalı: tek ölçek yetmez, 0,25 tabanlı ölçek + kısaltma.
6. Üçüncü taraf mod API'sine derleme yapma; NBT/JSON/tag ile entegre ol.
7. Colorful Hearts gibi vanilla katmanını değiştiren modlar kendi barımızla çakışır → paketten çıkar.
8. `project.md` 3000 satıra çıktı ve bağlam şişirdi → burada **faz başına küçük journal** (`docs/journal/fNN.md`).
9. Statik ilklendirme sırası açılış çökmesi yaptı → kayıtları tek yerden, sıralı yap.
10. Ekran görüntüsü yakalama başkasının penceresini yakaladı → kullanma.
