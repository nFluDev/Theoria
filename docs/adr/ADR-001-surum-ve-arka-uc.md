# ADR-001 — Sürüm, loader, Java ve grafik arka ucu

**Durum:** Kabul · 2026-11-09 (Faz 0: iş 1-3 + spike S1) · **Etkiler:** tüm fazlar
**Kapanış:** 2026-11-10, Fable 5.2 xHigh gözden geçirdi — kararlar değişmedi (mod matrisi yeniden çalıştırıldı: 62 mod, 0 sabit sorunu); **düzeltildi:** "Faz 5 FPS hedefi karşılanıyor" ifadesi ölçümün gerçek kapsamına indirildi (20 sn'lik örnek, boş dünya, paket yok); OpenGL+Iris yedeği "denenmedi" olarak etiketlendi ve kanıtı Faz 5'e bağlandı; faz yükümlülükleri tek başlıkta toplandı.

Kanıt etiketleri: **[log]** komut/log çıktısı · **[kaynak]** kaynaktan okundu, çalıştırılmadı · **[çıkarım]** denenmedi.

## Bağlam
- Seçenekler: MC 26.2 (Haz 2026) / 26.3 (Eyl 2026); NeoForge (D8); Java 25 zorunlu (MC 26.x); Vulkan (deneysel, D7) / OpenGL.
- Makine: i5-9400F · RX 580 8 GB (MoltenVK) · 16 GB · macOS 26.7.2 Intel · 1920×1080 @120 Hz. Global `JAVA_HOME` = Java 21 (LucidRem, Gradle 8.14.2).
- 26.4 snapshot-7'de; Vulkan orada varsayılan oluyor.

## Karar
1. **Minecraft 26.3 + NeoForge 26.3.0.101** (Maven'de 26.3 hattının en yenisi; 26.3.x MC yaması yok). 26.4 → ayrı göç fazı (release + mod ekosistemi oturunca).
2. **MDK:** `NeoForgeMDKs/MDK-26.3-ModDevGradle` → ModDevGradle 2.0.148, Gradle 9.2.1, tek mod `theoria`, paket `dev.theoria` (`mods-src/theoria`).
3. **Java 25 = Temurin 25.0.5+8**, kullanıcı düzeyinde `~/Library/Java/JavaVirtualMachines/temurin-25.jdk` (sudo yok, Java 21 yerinde). **Global `JAVA_HOME` değiştirilmez**; Theoria Gradle'ı `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=25`) ile her zaman JDK 25'te çalışır, derleme toolchain'i 25. Kabuk için `source tools/env.sh`. Prism instance'ları Prism'in Java 25.0.1'ini (`java-runtime-epsilon`) kullanır.
4. **Grafik arka ucu: Vulkan birincil** (oyun + geliştirme testleri), ~~**OpenGL yedek**~~ → **kalktı, bkz. "Faz 5 değişikliği (D19)"**. Kod arka uçtan bağımsızdır (ADR-002 kuralları); ~~her görsel DoD iki arka uçta da kontrol edilir~~ → görsel DoD'ler yalnızca Vulkan'da.
5. **Shader:** Vitrail 0.13.0-beta (NeoForge) + Sodium 0.9.3-beta.4 + Complementary Reimagined r5.9.3; sürümler `pack/modlist.toml`'da sabit.
6. Dev ortamında üçüncü taraf modlar yalnızca `localRuntime` (Modrinth Maven, sürüm id'si) — derleme bağımlılığı yok (Curios hariç, Faz 11).

## Faz 5 değişikliği (D19, 2026-11-11, kullanıcı)
Faz 5 başlarken kullanıcı kararı: **"Shader için Vitrail ve bağımlılıkları; Iris yok; Vulkan-tabanlı geliştiriyoruz, OpenGL hedeflemiyoruz."** Sonuçlar:
- **Karar 4'ün OpenGL yedeği kalktı.** Geliştirme, FPS ölçümü ve görsel DoD'ler yalnızca Vulkan'da yapılır; kod yine Vulkan/OpenGL-bağımsız yazılır (ADR-002 kuralları değişmedi — `GuiGraphics`/render-pipeline soyutlaması), ama OpenGL için ayrıca **test edilmez ve desteklenmez**.
- **Geri dönüş planındaki "OpenGL + Iris 1.11.7" maddesi geçersiz.** Iris pakette yok (`pack/modlist.toml [bilerek-yok]`, `tools/check_pack.py` Iris'i hata sayar). Vulkan regresyonunda (MoltenVK/Vitrail/26.4) çare: sürümleri sabit tut, Vitrail/MoltenVK güncellemesini bekle; 26.4 göçü ayrı faz kalır.
- **Faz 5 FPS tablosundan "OpenGL seti (Iris + Complementary)" satırı iptal**; yerine Vulkan'da paket-içi/dışı ek ölçümler (`docs/journal/f05.md`). "OpenGL + Iris yedeğinin kanıtı Faz 5'te" yükümlülüğü ortadan kalktı.
- **Vitrail'in bağımlılıkları** (Modrinth sürüm API'si, 0.13.0-beta+mc26.3): **zorunlu = yalnızca Sodium** (pakette 0.9.3-beta.4); isteğe bağlı = `fabric-api` (NeoForge'da gerekmez) ve `chloride` (Sodium'a ek özellik; pakete alınmadı). Shaderpack: Complementary Reimagined r5.9.3 (Vitrail'in kendi uyumluluk tablosunda r5.9 için "çizilir, referansa yakın"). Vitrail'in README'si "bitmiş görüntü istiyorsan OpenGL'de Iris'i kullan" diyor; bilerek bu yolu seçmiyoruz — beta riski (R1) kabul edildi.

**Faz 5 FPS sonucu (tam paket, Vulkan, tam ekran 1080p, shader açık, vanilla grafik ayarları, arazi üretilmiş; ayrıntı `docs/journal/f05.md`):** RD12 83,2 · RD16 57,8 · RD24 60,2 · RD32 46,5 ort. FPS (min 74 / 41 / 53 / 44). RD32'de ilk 5 dk kararlı 47-50 FPS; 10 dk testi ölçüm dışı nedenlerle (vanilla AFK/odak FPS sınırı) tamamlanamadı ve kullanıcı kararıyla yinelenmedi — "10 dk ≥ 60" DoD maddesi **kanıtlanmadı**. S1 (vanilla arazi, paket yok) ile fark: RD16'da 87,7 → 57,8; nedenleri ayrıştırılmadı.

## Kanıt
Bu bölümdeki her satır **[log]**'dur (aksi belirtilmedikçe). S1 çıktıları: `docs/adr/kanit/s1_fps_vk_shaders_fs.txt` (A), `s1_fps_vk_noshaders_fs.partial.log` (B).

**Ortam**
```
$ /usr/libexec/java_home -v 25   → /Users/naer/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
$ java -version (JDK 25)         → openjdk version "25.0.5" 2026-10-20 LTS (Temurin-25.0.5+8)
$ ./gradlew --version            → Launcher JVM: 21.0.12 · Daemon JVM: Compatible with Java 25 (from gradle/gradle-daemon-jvm.properties)
$ ./gradlew build                → BUILD SUCCESSFUL in 3m 32s (ilk: decompile + 7301 dosya)
brew install openjdk@25          → başarısız: Tier 3 (macOS 26 Intel), bottle yok, tam Xcode gerekir — hiçbir şey değişmedi
LucidRem gradle-wrapper          → gradle-8.14.2 (Java 25'te çalışamaz → global JAVA_HOME'a dokunulmadı)
```
**Mod matrisi:** `python3 -I tools/modmatrix/matrix.py` → `# 62 mod, 0 sabit sorunu` (çıkış 0; 2026-11-10'da yeniden çalıştırıldı, aynı sonuç). Paket listesinin 26.3 NeoForge'da tamamı var; 26.2'de Mac Input Fixes yok ve çoğu mod daha eski (`docs/11`).

**S1 — yeni NeoForge Prism instance'ı `Theoria-S1`** (NeoForge 26.3.0.101 + Sodium 0.9.3-beta.4 + Vitrail 0.13.0-beta + Complementary Reimagined r5.9.3, kullanıcının `Vulkan Optimization` options.txt'si ve shader ayarları kopyalandı; FPS ölçümü Theoria spike jar'ı: sabit seed 260309, yaratıcı, kamera yavaş dönerken 20 s boyunca saniyede bir `Minecraft#getFps`, her RD öncesi 15 s yerleşme):
```
Using graphics backend Vulkan, using drivers: 1.2.350 MoltenVK 1.4.2   (AMD Radeon RX 580, LWJGL 3.4.3)
Vitrail 0.13.0-beta starting on NeoForge 26.3.0.101, Minecraft 26.3
Client setup reached on the Vulkan backend, Sodium is present
Drawing ComplementaryReimagined_r5.9.3 from world0 for minecraft:overworld, at 1920x1080, 7 full screen passes before the final
```
| Çalıştırma (tam ekran 1920×1080, exclusive) | RD | ort. FPS | min | p10 | medyan | maks |
|---|--:|--:|--:|--:|--:|--:|
| A · Vulkan + Vitrail + Complementary | 12 | **89,7** | 88 | 88 | 90 | 91 |
| A · Vulkan + Vitrail + Complementary | 16 | **87,7** | 78 | 82 | 88 | 95 |
| A · Vulkan + Vitrail + Complementary | 24 | **75,5** | 70 | 70 | 76 | 81 |
| B · Vulkan, shader kapalı | 12 | **642,0** | 502 | 537 | 660 | 734 |
- A ve B logları: 0 ERROR/istisna. B, RD16 ölçümünde kullanıcı oyundan çıkınca yarıda kaldı (log: pause → disconnect → stop; spike kodunun kapanışı değil).
- Pencere modu: geliştirme istemcisi (`runSpikeClient`, 1280×720) Vulkan'da ve OpenGL'de (`4.1 ATI-7.1.8`) sorunsuz açıldı ve kapandı (ADR-002 kanıtı). Mevcut 26.2 NeoForge instance'ı da `Using graphics backend Vulkan ... MoltenVK 1.4.2` yazıyor.

## Sonuçlar
- ✅ Vulkan + shader yığını yeni NeoForge instance'ında çalışıyor; shader açıkken RD12-24'te 70-91 FPS (RD24'te p10=70). Bu, **boş yaratıcı dünyada, paket modları olmadan, RD başına 20 sn'lik** örnektir: Faz 5 DoD'sinin (tam paket, 10 dk, ≥ 60) kanıtı değil, yalnızca olumlu bir ön işarettir. RD12 ≈ RD16 (89,7 / 87,7) → darboğaz görüş mesafesi değil, shader'ın tam ekran geçişleri (GPU).
- ✅ Shader kapalıyken RD12'de ~640 FPS → işlemci/CPU tarafı rahat; shader maliyeti GPU'da (RX 580). İçeriğimiz (yeni mob/biyom) shader'lı bütçeyi zorlayacağı için Faz 8-9'da RD16 ölçümü tekrarlanır.
- ✅ Java 25 kurulumu yan yana; LucidRem etkilenmedi.
- ⚠ Ölçülmeyen (Faz 5 M1 FPS tablosunda tamamlanacak): pencere modu **+ shader**, RD32 + shader (kullanıcının oynadığı ayar), ~~OpenGL'de aynı mod seti~~ (iptal, D19), 10 dk süreklilik/bellek (26.x Vulkan kapanış sızıntısı raporları).
- ⚠ Vitrail beta → sürüm sabit; Vitrail güncellemesi ayrı bir doğrulama turu ister.
- ⚠ Şablondaki `gradle-wrapper.jar` resmî 8.14.5 jar'ı (dağıtım 9.2.1); Faz 1'de `./gradlew wrapper --gradle-version 9.2.1` ile yenilenir.

## Sonraki fazlara yükümlülükler
- **Faz 1:** `./gradlew wrapper --gradle-version 9.2.1`; `git init` (Q15); spike temizliği şu ayrımla:
  - **taşınır, silinmez:** S4 ve S6 GameTest'leri (`SpikeTests`) kalıcı test paketine — ADR-003/004'ün dayandığı vanilla/NeoForge davranışlarının (30 ve 20 tavanları, `Reduction.ARMOR` kancası, 255 kesiği) NeoForge güncellemelerine karşı regresyon testi; Faz 2b ve 7b bunların üstüne ekler. S5 dump'ı da kalır: `data/vanilla_mobs.csv`'nin tek üreticisidir (sürüm göçünde yeniden çalıştırılır).
  - **korunur:** AT dosyası (ADR-003), FW + Puzzles Lib `localRuntime`'ı (Faz 4 geliştirmesi), `FpsBench` (Faz 5 FPS tablosu; yalnızca dev bayrağıyla etkin).
  - **silinebilir:** S2/S3 istemci senaryosu ve `-PspikeMods` — çıktıları `docs/adr/kanit/`'ta saklı.
- **Görsel DoD'ler (Faz 3/4/10/11):** ~~iki arka uçta kontrol~~ → yalnızca Vulkan (D19); geliştirme istemcisinde geçiş `run/options.txt` → `preferredGraphicsBackend:"vulkan"` | `"opengl"`.
- **Faz 5 (M1) FPS tablosu:** tam paketle A setinin tekrarı + eksik dört ölçüm (pencere+shader, RD32+shader, 10 dk süreklilik/bellek); hedef görüş mesafesi kararı (Q14). ~~OpenGL seti Iris + Complementary ile~~ → **iptal (D19)**.
- **Faz 8-9:** kendi mob/biyom içeriğimizle RD16 + shader ölçümü tekrarlanır.
- **Vitrail/Sodium güncellemesi veya 26.4:** ayrı doğrulama turu (A ölçümü yeniden); 26.4 ayrı göç fazı.

## Geri dönüş planı
- ~~Vulkan regresyonu: `preferredGraphicsBackend:"opengl"` + Iris 1.11.7~~ → **kalktı (D19, yukarıda)**; çare sürüm sabitleme / güncelleme bekleme. (Eski metin:) Kod değişmez. **[çıkarım]:** Iris 1.11.7'nin 26.3 NeoForge build'i Modrinth'te var (mod matrisi) ama bu makinede Sodium 0.9.3-beta.4 ile birlikte denenmedi → kanıt Faz 5'in OpenGL ölçümü.
- NeoForge 26.3.0.101'de engelleyici hata: 26.3.0.x içinde yükselt (MDG `neo_version`), ADR'ye not düş.
- 26.3 mod ekosistemi bir zorunlu modu (FW/JEI/Curios) kaybederse: o mod için son 26.3 build'ine sabitlenir; 26.2'ye dönüş düşünülmez (26.2'de zaten daha eski).
