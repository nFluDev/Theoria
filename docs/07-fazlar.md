# 07 — Fazlar

Her faz: **hedef · işler · bitiş kriteri (DoD) · model/effort · bağlam politikası**. Efor tahmini "odaklı çalışma" oturumudur (bir oturum ≈ yarım-bir gün).
Ayrıntılı model gerekçesi: `docs/08`. Başlatma prompt'ları: `prompts/faz-NN-*.md`.

| Faz | Ad | Model · Effort | Oturum | Kilometre taşı |
|--:|---|---|--:|---|
| 0 | Ortam ve spike'lar | **Opus 5.5 · xHigh** (ADR kapanışı: max) | 3-4 | |
| 1 | İskelet | Sonnet 5.5 · high | 1-2 | |
| 2 | Denge çekirdeği | **Opus 5.5 · xHigh** → Sonnet 5.5 · xHigh | 3-4 | |
| 3 | HUD | Sonnet 5.5 · xHigh | 2-3 | |
| 4 | Kanatlar + ESC menüsü | Sonnet 5.5 · xHigh | 2-3 | |
| 5 | Pack tabanı + QoL + macOS | Sonnet 5.5 · high | 2 | **M1** |
| 6 | Materyal, zırh, alet, silah | Sonnet 5.5 · xHigh | 4-5 | |
| 7 | Kataliz Masası | Opus 5.5 · high (tasarım) → Sonnet 5.5 · xHigh | 3-4 | |
| 8 | Mob'lar ve drop zinciri | Sonnet 5.5 · xHigh (boss AI: Opus 5.5 · high) | 5-7 | **M2** |
| 9 | Dünya: biyom, yapı, Haven | Opus 5.5 · high (worldgen tasarımı) → Sonnet 5.5 · xHigh | 5-7 | |
| 10 | Durum ekranı + seviye | Sonnet 5.5 · xHigh | 3 | |
| 11 | Curios Max + Kozmetik | Opus 5.5 · xHigh (render kancası) → Sonnet 5.5 · xHigh | 3-4 | **M3** |
| 12 | Relics (v1.1) | Sonnet 5.5 · high | 2-3 | |
| 13 | Entegrasyon, denge, yayın | Opus 5.5 · high (inceleme) + Sonnet 5.5 · high (düzeltme) | 3-4 | **v1.0** |

Toplam: ≈ 40-55 oturum. Hız kapsamı kesmekle ayarlanır: M2 (Faz 8) zaten oynanabilir ve eğlenceli çekirdek.

---

## Faz 0 — Ortam ve spike'lar
**Hedef:** Varsayımları kanıtla, ortamı kur, kritik kararları ADR'ye bağla.
**İşler**
1. JDK 25 kur; `JAVA_HOME`; Gradle ≥ 9.1.
2. NeoForge 26.3.0.101 MDK'sı → `mods-src/theoria` (mod id `theoria`, paket `dev.theoria`); `./gradlew build` ve `runClient` açılıyor.
3. Prism instance (MC 26.3 + NeoForge) + `tools/modmatrix/matrix.py` yeniden çalıştır; docs/11 güncelle; 26.2 vs 26.3 son karar.
4. **S1 Vulkan (kısa doğrulama — D7):** yeni NeoForge instance'ında Sodium + Vitrail + Complementary Reimagined ile Vulkan açılıyor mu; FPS tabanı (render distance 12/16/24); pencere/tam ekran. Referans: kullanıcının `Vulkan Optimization` instance'ı (Fabric) ve `options.txt`'i.
5. **S2 Fantastic Wings:** 26.3.1 yüklü, kanat verme komutu/API, kataloğu çalışma anında okuma yolu, animatörün tick bağımlılığı.
6. **S3 Render kancaları (26.x):** (a) `GuiGraphics` ile özel HUD katmanı (vanilla kalp/zırh/yemek katmanını gizle + kendimizi çiz), (b) 3B oyuncu ekran içi çizimi (`renderEntityInInventory…` imzası), (c) zırh render katmanını gizleme/değiştirme kancası.
7. **S4 Enchant seviye tavanı:** `ItemEnchantments` codec'i 255'in üstünü kabul ediyor mu? Datapack enchantment'ı `LevelBasedValue` ile 300. seviyede etki veriyor mu?
8. **S5 Mob dump:** tüm vanilla (ve 26.x) `LivingEntity` attribute varsayılanları (HP, attack_damage, armor, speed, follow_range, XP ödülü) → `data/vanilla_mobs.csv`.
9. **S6 Hasar boru hattı:** `CombatRules` yerine kendi azaltmamızı hangi olay/mixin'den enjekte ederiz? Test: zırh 300 iken vanilla formülün %80'de tavanlanması kaldırılabiliyor mu?
10. **S7 Mac Input:** Mac Input Fixes (NeoForged) 26.3'te Ctrl+Click ve Ctrl+Q'yu çözüyor mu? McMouser 26.2 build'i 26.3'te açılıyor mu?
**DoD:** `docs/journal/f00.md` + `docs/adr/` içinde ADR-001 (sürüm+arka uç), ADR-002 (render kancaları), ADR-003 (hasar formülü enjeksiyonu), ADR-004 (enchant tavanı), ADR-005 (Mac giriş). Hiçbir spike "belki" ile kapanmaz; ya kanıtlandı ya fallback seçildi.
**Bağlam:** her spike bitince journal'a yaz; 3 spike sonra `/compact` (odak: "ADR sonuçları ve açık sorular"). Faz sonu `/clear`. ADR kapanış turu **Opus 5.5 · max** (kısa, tek karar belgesi).

## Faz 1 — İskelet
**İşler:** Gradle çok-paketli yapı; `core` (kayıtlar sıralı, config, `Payload` yardımcıları, attachment sarmalayıcı), `lang` tr/en altyapısı; `/theoria` komut kökü; `tools/check_data.py`; `.gitignore`; GitHub Actions değil, yerel `tools/preflight.py` (derle + check). **DoD:** boş mod açılıyor, `/theoria version` çalışıyor, preflight yeşil.
**Bağlam:** kısa faz, `/clear` yok gerek (Faz 2'ye geçerken var).

## Faz 2 — Denge çekirdeği
**2a (Opus 5.5 · xHigh):** `tools/balance/sim.py` — kademe×katalizör×seviye simülasyonu; hasar azaltma formülü seçimi; HP/regen politikası; mob ölçekleme eğrisi; sonuçlar `docs/06` güncellenir.
**2b (Sonnet 5.5 · xHigh):** `balance` paketi: `TierRegistry` (datapack'ten), attribute pipeline, hasar azaltma enjeksiyonu (ADR-003), mob ölçekleme, regen. `/theoria test balance`.
**DoD:** zırh 300'lü bir oyuncu, tier-8 mob karşısında simülasyonun öngördüğü ±%10 hasarı alıyor (oyun-içi ölçüm).
**Bağlam:** 2a→2b arası `/clear` + model değişimi.

## Faz 3 — HUD
**İşler:** `generator/hud.py` (nötr sprite'lar, yarım zırh saydamlığı); `StatusBars` katmanlı kalp/zırh/SAT/binek; orta sayılar; XP barı + seviye; vanilla katmanları gizle; Arayüz Ölçeği testleri; `tools/check_hud.py` (21 HP vb.). LucidRem `client/*Overlay.java` referans.
**DoD:** kullanıcı onayı (3 ekran: 20 HP, 21 HP, 250 HP; zırh 0/40/310; binekte/değilken). Vulkan'da doğru görüntü (D19: OpenGL hedeflenmez).

## Faz 4 — Kanatlar + ESC menüsü
**İşler:** `PauseMenuHook`; `WingsScreen` (dinamik liste, 3B oyuncu, sürükle-döndür, oyun durmuyor); sunucu paketi; kalıcılık (+ ölüm testi); FW animatörü tick; sorun-gidermeler LucidRem §45.5'e göre.
**DoD:** kanat tak→anında 3B önizlemede ve oyunda görünür; öl→yeniden doğ→kanat duruyor; pause ekranında kanat animasyonu akıyor.

## Faz 5 — Pack tabanı + QoL + macOS (M1)
**İşler:** `pack/modlist.toml` (JEI, Curios, FW, Sodium + Sodium Extra, Lithium, FerriteCore, EntityCulling, ImmediatelyFast, BadOptimizations, ScalableLux, MoreCulling, ModernFix-mvus, **Vitrail Shaders + Complementary Reimagined**, Jade, Mouse Tweaks, Xaero, Waystones, Compasses, Lootr, Terralith, TerraBlender, D&T, Chunky, Mac Input Fixes); `docs/12`'deki geçici doldurucu yapı/mob modlarından seçim; config ayarları; packwiz kurulumu; Prism instance dışa aktarım; performans taban ölçümü (FPS, Vulkan/OpenGL); Mac sorunları (D1/D2) çöz; vanilla hissi kontrol listesi.
**DoD (M1):** yeni dünya aç → vanilla gibi başla → kanat tak → HUD doğru → Ctrl+tık/Ctrl+Q çalışıyor → **Vulkan + Vitrail shader açıkken** HUD ve Kanatlar ekranı doğru görünüyor → 10 dk FPS ≥ hedef (≥ 60). `docs/journal/f05.md`'ye FPS tablosu.
**Bağlam:** M1 sonrası `/clear`; **içerik fazları başlıyor**.

## Faz 6 — Materyal, zırh, alet, silah
**İşler:** **kazı kapıları** (`docs/05 §7`: cevher→gereken alet idx + sertlik, `incorrect_for_*_tool` tag'leri); `data/materials.csv` + `generator/gear.py` → item modelleri (tint'li), tarifler, loot, tag'ler, lang; kayıt (14 zırh + 11 alet/silah kademesi); equipment asset'leri (zırh 3B dokusu tint'li); yeni cevherler + `worlds.OreFeatures` (datapack feature); alaşımlar; öntanımlı büyüler; vanilla netherite gear kaldırma; vanilla eşya override; JEI kategorileri.
**DoD:** survival'de Kalay→Platin tırmanışı el ile yapılabilir (creative ile cevherler bulunup test); stat tooltip'leri tablo ile birebir; `check_data.py` yeşil.

## Faz 7 — Kataliz Masası
**7a (Opus 5.5 · high) ✅ 2026-11-11 → ADR-006:** component şeması, katalizör kuralları (yuva, üst sınır), UX akışı, tarifler (maliyet), Netherite kuralı, Q34/Q37.
**7b ✅ 2026-11-11 (Sonnet 5.5 · xHigh) → ADR-006 "Faz 7b sonucu":** block+menu+screen (BlockEntity gerekmedi), katalizör item'ları (~32), **Bilgelik (XP) büyüsü + katalizörü** (`docs/05 §3 Tür D`), uygulama mantığı, datapack enchantment efektleri (element), JEI entegrasyonu, tooltip.
**DoD:** *elmas* kılıca (demir kılıç 2 yuvalı; 3 katalizör T8'de sığar) Hız+Kritik+Buz katalizörü uygula → tooltip/stat/etki tamam; Netherite ile Keskinlik V→VI; kayıt/yükle sonrası kalıcı.

## Faz 8 — Mob'lar ve drop zinciri (M2)
**İşler:** 12 arketip entity; `data/mobs.csv`; model+animasyon (blocky + tint); AI; spawn kuralları (biyom+kademe bandı); drop tabloları → materyal/öz/çekirdek; vanilla mob ölçekleme; kapı boss'ları (8); öldürünce XP kademe çarpanı; spawn egg'ler; JEI "drop" gösterimi.
**DoD (M2):** her mob'un drop'u merdivenin bir öğesini besliyor (matris `tools/check_drops.py`); 30+ mob; 2 boss; TTK ölçümü docs/06 hedefinde.
**Bağlam:** mob başına prompt yok; mob *aileleri* halinde ilerle; ailede `/compact`.

## Faz 9 — Dünya: biyom, yapı, Haven
**9a (Opus 5.5 · high):** TerraBlender bölge planı, kademe bandı eşlemesi, cevher dağılımı, yapı sistemi tasarımı (jigsaw/structure set), Haven kuralları, performans bütçesi.
**9b (Sonnet 5.5 · xHigh):** 20 biyom; `generator/structures.py` (palet×arketip NBT üretimi); Haven ailesi (S/M/L); Harabe/Zindan/Kale; loot (Lootr uyumlu); spawn override; Explorer's Compass uyumu; GameTest.
**DoD:** 5 kez farklı seed'de 1000 chunk gezi (Chunky + kanat): biyom çeşitliliği, yapı sıklığı hedefte, Haven bulunabilir, mob'suz; ana dünya "dondurulabilir" (Faz 9 sonrası worldgen değişmez).

## Faz 10 — Durum ekranı + seviye
**İşler:** `stats` attachment (XP **long**, tavan 9999); XP→seviye akışı (iki parçalı eğri `docs/06 §5`); ekran; paketler; efektler (düz modifier'lar); respec item; HUD seviyesi; mob XP ölçeği ayarı.
**DoD:** L2'ye 55 ham XP'de çıkılıyor; her stat doğrulanmış etkide; harcanan XP seviye düşürmüyor.

## Faz 11 — Curios Max + Kozmetik (M3)
**11a (Opus 5.5 · xHigh):** zırh render kancası (S3 sonucuna göre), gizleme stratejisi, Curios Max ekranı yaklaşımı.
**11b (Sonnet 5.5 · xHigh):** `CuriosMaxScreen`, slot tanımları, kozmetik slotlar + sekme, ayar ekranı (kozmetik/zırh göster-gizle), çok oyunculu görünürlük.
**DoD:** kozmetik takıp gerçek zırhın stat'ını alırken görünümün değişmesi; ayardan her ikisini kapatınca vanilla; Curios Max'te ≥ 30 slot.

## Faz 12 — Relics (v1.1)
~25 relic (veri+olay), boss/zindan loot'una bağlama, Curios slotları, JEI tooltip. DoD: her relic için `/theoria test relic <id>`.

## Faz 13 — Entegrasyon, denge, yayın (v1.0)
Uzun oyun testi (kullanıcı), `sim.py` ile yeniden denge, performans, çökme avı, JEI/Jade metinleri, ekran-dışı kontroller, README ve sürüm, packwiz yayınlama. **Opus 5.5 · high kod incelemesi** (code-review skill) → Sonnet düzeltir.

---

## Kapsam kesme sırası (çok geç kalırsak)
1. Relics → v1.1 (zaten sonda) · 2. Kozmetik ince ayar · 3. Biyom sayısı 20→12 · 4. Mob sayısı 40→30 · 5. Boss sayısı · 6. Alet/silah kademe sayısı (11→8). **Dokunulmaz:** HUD, Kanatlar, kademe merdiveni, Kataliz Masası, Haven'lar.
