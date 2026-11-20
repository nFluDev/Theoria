# Faz 9b kapısı — Chunky turu: çalışma kâğıdı ve kayıt

> Hazırlık 2026-11-19'da tamamlandı (aşağıda). **Gezi ve gözlem kullanıcıda**; bulgular bu dosyaya yazılır, sonra `data/worldgen.json` + `data/biome_climate.csv` + `data/ores.csv` ayarlanır. **Worldgen kabul edilene kadar DONDURULMAZ (R10).**

## Hazırlık durumu (2026-11-19)
- `tools/bin/` yeniden kuruldu (git'te yok): `packwiz` (`go install`, Go 1.27.4) + `packwiz-installer-bootstrap.jar` v0.0.3 (sha256 `a8fbb24d…d116c`, README'deki ile **eşleşti**).
- `python3 -I tools/pack/make_instance.py` → Prism **`Theoria`** instance'ı hazır (36 mod jar'ı; shader=on; senaryo yok). Chunky 1.5.66, TerraBlender 26.3.0.0.9, Terralith 2.6.5, Lootr, Theoria içinde.
- `python3 -I tools/pack/sync_jar.py --apply` → **OK (theoria-0.0.1.jar güncel)**; jar kaynaktan yeni (kaynak/veri jar'dan eski).
- `python3 -I tools/check_worldgen.py` → OK (yeniden koşuldu).
- Oyun **açılmadı**; hiçbir dünya üretilmedi. Bu kâğıttaki hiçbir sonuç ölçüm değildir, hepsi boş.

## ⚠ Düzeltme: `/chunky radius` birimi
`PROGRESS.md` "`/chunky radius 16` ≈ 1 089 chunk" diyordu; **yanlış**. Chunky 1.5.66 `Input.suffixValue`: sonek `c` = ×16 (chunk), `r` = ×512 (bölge), `k` = ×1000; **sonekesiz sayı BLOK**tur. `radius 16` yalnızca 16 blok yarıçap (≈ 3×3 chunk) üretir. Doğrusu **`/chunky radius 16c`** (256 blok → ≈ 1 024-1 089 chunk).

## ⚠ Kapı bulgusu: Theoria biyomları dünyada YOKTU (2026-11-19) — düzeltildi, yeni dünyada doğrulanacak
- **Belirti:** `T9-90101`'de `/locate biome theoria:<id>` ilk 7 biyomun 7'sinde "Could not find … within a reasonable distance" (otomatik tamamlamada görünüyorlardı = kayıtlı).
- **Kanıt:** `python3 -I tools/scan_world.py <dünya>` → 6 039 chunk: `minecraft` 137 394 · `terralith` 31 099 · **`theoria` 0**; Theoria cevher bölümü 0 (cevherler yalnızca Theoria biyomlarında); ama yapılar var (Haven S6 / M2 / L3, Zindan 9, Harabe 4; en yakın haven_s (−112, 400)).
- **Kök neden (TerraBlender 26.3.0.0.9 bytecode'u + log sırası):** `InitializationHandler.onServerAboutToStart` önce `LevelUtils.initializeOnServerStart` (biyom arama ağaçları), sonra `Regions.loadDataDrivenRegions` çağırıyor. Log: "Initialized TerraBlender biomes for level stem minecraft:overworld" satırı "Registered region theoria:overworld to index 1" satırından ÖNCE. Terralith ayrı bölge değil (Lithostitched ile preset listesini değiştiriyor), yani `minecraft:overworld` bölgesinin içinde; bizimki ağaca hiç girmedi.
- **Düzeltme:** `TerraBlenderBridge` (`ServerAboutToStartEvent` HIGHEST → `Regions.loadDataDrivenRegions` yansımayla, TerraBlender'dan ÖNCE). Yeni oyun log'unda şu satır "Initialized TerraBlender biomes for level stem minecraft:overworld"dan ÖNCE görünmeli: `Theoria: data-driven TerraBlender regions loaded before TerraBlender built its biome trees`.
- **Doğrulama (yeni):** GameTest `worlds_terrablender_generation` (normal preset'ten gerçek Overworld üretici, TerraBlender başlatması, 1152 örnek) → **514/1152 (%44), 12 farklı Theoria biyomu**; `-PwithTerraBlender -PgameTestOnly=worlds_` 10/10; varsayılan 79 test (yeni dahil): bilinen zamanlama kırmızıları (`gear_innate`/`catalyst_elements`, Q58) koşudan koşuya değişiyor, 1 koşuda 79/79. `/theoria test worlds` artık gerçek dünyada örnekler (≥ %5, ≥ 6 biyom).
- **2. hata (aynı kapı, hemen ardından): "Feature order cycle" çökmesi.** Bölge üretime girince oyun ilk chunk'ta çöktü (`crash-2026-11-19_23.45.57-client.txt`: `Feature order cycle found, involved sources: [theoria:storm_islands, theoria:iron_uplands, theoria:steel_steppe]`) ve çökme işleyicisi JVM'i kapatamadı: "Loading terrain…", tıklanamayan pencere, JVM CPU %2 — kullanıcı oyunu kapatamadı, süreç elle sonlandırıldı. Neden/çözüm: `docs/10` Q62 (`generator/feature_order.py`, vanilla ∪ Terralith'ten tek sıra). Python benzetimi eski dosyalarda çevrimi yeniden üretti, Java `worlds/feature_order` eski dosyalarla KIRMIZI, yenilerle yeşil. İlk türetme yalnız vanilla'dan yapılınca Terralith ile çevrim çıktı → Terralith dahil edildi.
- **Gerçek üretim kanıtı (Prism dışı, dev dünya):** `./gradlew runDevClient -PdevClient=worldgen -PwithTerraBlender` (+ `run-dev/mods`'a Terralith ve Lithostitched jar'ları) → `tools/scan_world.py mods-src/theoria/run-dev/saves/theoria-dev`: **1 715 chunk hepsi `full`, çökme yok; `theoria` palet payı %32,3** (storm_coast 8 948, ember_rift 3 182, tin_marsh 3 011, platinum_depths 2 811, mithril_rift 628 — bu bölgede 5/20 biyom); cevher bölümleri `deepslate_cobalt_ore` 1 392 · `tin_ore` 137 · `deepslate_platinum_ore` 17 · `titanium_ore` 7; yapılar Haven L 3 (en yakın (−176, 0)) · Zindan 4 · Harabe 1. Kullanıcı bu dev dünyada `/locate biome theoria:cobalt_canyon` ile biyomu buldu. (Senaryonun kendi raporu yazılmadı: pencere kullanıcı tarafından kapatıldı; `/theoria test worlds` gerçek dünyada Prism'de koşturulacak.)
- **Tarayıcı düzeltmesi:** `scan_world.py` ilk sürümde 26.3'ün düz-metin blok paleti girişlerini okumuyordu (cevher "yok" dedi, gerçekte vardı) → `str` ve `{id|Name}` ikisi de okunuyor.
- **Prism'de GERÇEK kanıt (T9-90202, 2026-11-20 00:17):** düzeltilmiş jar, Vitrail+tam paket: log temiz (köprü satırı TerraBlender başlatmasından ÖNCE, yeni çökme yok, Xaero/Waystones dışı ERROR yok); üretim sürerken tarama: 3 329 chunk (1 369 `full`, spawn bölgesi), **Theoria palet payı %7,1** (ash_desert 3 278 · crystal_caves 1 441 · copperwood 699 · mithril_rift 648 · platinum_depths 541; bu seed'in spawn çevresinde 5/20) — dev dünyadaki %32'den düşük: bölge dağılımı seed'e ve konuma göre değişiyor, ölçüm 5 seed × 1 000 chunk ile yapılacak.
- **(eski madde) Prism'de GERÇEK kanıt bekliyordu:** düzeltilmiş jar ile YENİ dünya (`T9-90202`…) + `tools/scan_world.py` (Theoria payı > 0) + `/locate biome`. Eski `T9-90101` ölü (silinebilir).
- **Araç:** `tools/scan_world.py` (bağımsız .mca okuyucu): biyom payı, `theoria:` yapı başlangıçları (Haven S/M/L sayısı ve spawn'a en yakın), cevher bölümleri → kapıdaki `/locate`, Haven bulunabilirliği ve cevher bulunurluğu ölçümlerinin büyük kısmını gezmeden verir; oyun açıkken de okur.

## Shader görüntü gecikmesi — kök neden bulundu (2026-11-19, kapı sırasında)
- **Belirti (kullanıcı):** Vulkan + Vitrail açıkken dünya görüntüsü hitbox'tan geç/eski güncelleniyor, birkaç adım sonra ani "ışınlanma"; shader kapalıyken yok; OpenGL+Iris'te yok. Hitbox/oyun durumu doğru, çizilen kare eski. (`MoveBench` bunu yakalayamamıştı: oyun döngüsü kare süresini ölçer, sunulan dünya karesini değil.)
- **Karşılaştırma:** kullanıcının sorunsuz **ChillCraft** instance'ı (aynı makine: NeoForge 26.3, Vitrail 0.13.0-beta, Complementary Unbound r5.9.3, Vulkan, ImmediatelyFast/EntityCulling/Dynamic FPS/FerriteCore aynı) ile salt-okunur fark: **Sodium 0.9.3-beta.1 ↔ bizde beta.4** (Sodium Extra/Reese's beta.1 ↔ beta.3); bizde ek: MoreCulling, ModernFix, BadOptimizations, Ksyxis. Zaman çizelgesi: Vitrail 0.13.0-beta 13 Eki, Sodium beta.1 15 Eki, beta.2-4 7-8 Kas (Vitrail'den sonra).
- **Düzeltme:** Sodium üçlüsü beta.1'e indirildi + MoreCulling kapatıldı (beta.2+ ister: "no reason provided") → kullanıcı: **"shader sorunu düzeldi"** (Unbound ile, `T9-90101`).
- **Paket:** `pack/modlist.toml` Sodium üçlüsü beta.1'e sabit, `moreculling` `[bilerek-yok]`, `cloth-config` `[bagimlilik-opsiyonel]`; `sync_packwiz.py` + `check_pack.py` (+ `--online`) OK; metafile sha512'leri instance'taki çalışan jar'larla birebir.
- **Araç dersi:** `sync_packwiz.py` ilk koşuda `sodium`'u beta.5'e kaydırdı (packwiz, `sodium-extra` eklenirken zorunlu bağımlılığı en yeniye çekiyor) → `check_pack` yakaladı; sync artık eklemelerden sonra sürüm kimliklerini doğrulayıp kayanları yeniden sabitliyor. Python 3.15.2 CA sertifikasız (`CERTIFICATE_VERIFY_FAILED`): araçları `SSL_CERT_FILE=/etc/ssl/cert.pem python3 -I …` ile çalıştır.
- **Açık:** (1) **ayrıştırılmadı**: Sodium sürümü mü MoreCulling mi (ikisi birden değişti; ayırmak için Sodium beta.4 + MoreCulling'siz tek test) · (2) **Complementary Reimagined** (paketin varsayılanı) ile doğrulama bekliyor (düzeltme Unbound'la görüldü) · (3) Prism `Theoria` instance'ındaki shader şu an **Unbound** (`vitrail/pack.txt`) ve `shaderpacks/` içinde Unbound var; pakette yalnızca Reimagined.

## Seed'ler (5 yeni; 9b ölçümünün 1101…5505 örneklemesiyle çakışmaz)
`90101` · `90202` · `90303` · `90404` · `90505` — her biri **ayrı** dünya `T9-<seed>`, üretim dünyası karıştırılmaz.

## Her dünya için (≈ 15-25 dk)
1. `prismlauncher --launch Theoria` → Tek oyunculu ▸ Yeni dünya: ad `T9-<seed>`, **Hile: açık**, Diğer ▸ Tohum = seed, dünya tipi varsayılan.
2. Önce `latest.log`'a bak: `ERROR`/`WARN` içinde `theoria`, `terrablender`, `worldgen` geçiyor mu → **Notlar'a yaz** (zararsız bilinenler: Dungeons and Taverns `jungle_log[axys=y]`, Xaero sürüm kontrolü, Waystones config).
3. Üretim:
   ```
   /chunky world minecraft:overworld
   /chunky center 0 0
   /chunky radius 16c
   /chunky start
   ```
   `/chunky progress` ile süreyi/`chunks/s`'i kaydet. Bitince dünya ve oyun açık kalsın.
4. **Biyom bulunurluğu** (20 biyom): `/locate biome theoria:<id>` → uzaklığı (blok) yaz; "bulunamadı" = ölü biyom adayı. Yeraltı biyomları (`crystal_caves`, `mithril_rift`, `platinum_depths`) için y = −40 civarında dur. Ek olarak yüzeyde gezerken F3'te biyom adı → **kabaca hangi biyom çok/az** (özellikle `storm_coast`, `storm_islands`: Q55 okyanus payı).
5. **Yapı bulunabilirliği:** `/locate structure #theoria:havens` · `#theoria:ruins` · `#theoria:dungeons` · `#theoria:keeps`; **her Haven boyu için** ayrı: `/locate structure theoria:haven_s`, `haven_m`, `haven_l` (+ vadi: `haven_valley_s|m|l`). Uzaklıkları yaz.
6. **Haven mob'suzluğu:** her boydan (S/M/L) en az birine ışınlan (`/tp` koordinat), `/time set midnight`, içinde ve binadan 12 blok çevresinde ≈ 5 dk bekle (otomatik: `-PdevClient=haven`); ayrıca 40+ blok dışında aynı sürede kontrol. Say: `/execute if entity @e[type=!player,type=!item,distance=..40]` (sonuç "count: N"). Yatak, ocak, sandık çalışıyor mu; **Lootr sandığı** açılıyor mu (loot var mı). Beklenen: Haven içi/çevresi **0 hostile**, dışarıda normal.
7. **Cevher bulunurluğu:** `/locate` yok. Kalay (y 40-110, Kalay Bataklığı) · kobalt (Kobalt Kanyonu, deepslate −56…−20) · mitril (Mitril Yarığı/Kristal Mağaraları, −64…−40) · platin (Platin Derinlikleri) için bir yer bulup damar görüp görmediğini yaz; titanyum/orikalkum (zirve y ≥ 140) ve lonsdaleit (Meteor Krateri) için zirve/krater bulundu mu, içinde cevher var mı (**Q56**: bulunmayan seed'de tırmanış kilidi riski).
8. **Performans:** gezerken F3 FPS (alçak/yüksek), render distance 12, chunk yükleme takılması var mı (R11). Ortalama FPS ve en kötü an yaz.

## Kayıt tablosu
Boş hücre = yapılmadı. Uzaklık blok cinsinden, `yok` = `/locate` bulamadı.

### Üretim / performans
| Seed | Chunky süre | chunks/s | latest.log hata | FPS (gezi) | Not |
|---|---|---|---|---|---|
| 90101 | 0:00:08 (**hız ölçümü DEĞİL**: spawn çevresi RD16 ile zaten üretilmişti, Chunky mevcut chunk'ları geçti) | — | Theoria/TerraBlender hatası yok; `Registered region theoria:overworld` ✓ | | 1089 chunk · region dosyaları 23 MB (4 dosya) |
| 90202 | **5:44** (16 641 chunk, `/chunky radius 64c`; gerçek üretim, Vitrail+tam paket) | **48** (≈ 21 ms/chunk) | Feature/TerraBlender/Theoria hatası yok, çökme yok | | 22 782 chunk (16 189 full), bellek: JVM 4,5 GB, sistem swap'ı ≈ 3 GB |
| 90303 | | | | | |
| 90404 | | | | | |
| 90505 | | | | | |

### Tarama (`tools/scan_world.py`, üretilmiş chunk'lardan; gezmeden ölçüm)
| Seed | Theoria yüzey payı | Biyom (yüzey+yeraltı) | Haven S / M / L | Zindan / Harabe / Kale | Cevher bölümü (kalay·kobalt·mitril·platin·titanyum·lonsdaleit·orikalkum) |
|---|--:|---|---|---|---|
| 90202 (2048², 16 641 chunk) | **%24,0** (hedef ≈ %25) | **13 / 20** var; YOK: tin_marsh, iron_uplands, cobalt_canyon, ice_graveyard, titan_peaks, storm_islands, orichalcum_sky | 40 / 28 / 14 (toplam 82 ≈ 4,9 / 1 000 chunk; en yakın: M (32,144), L (144,336), S (352,0)); vadi Haven 0 | 39 / 41 / `keep_ancient` 2 (`keep_mud`/`keep_storm` 0) | 6 534 · 7 720 · 1 214 · **82** · **1** · 28 · **0** |

Yorum (ölçüm, henüz karar değil): yüzey payı hedefte. Alandaki iklim sıcak/kurak (badlands, savana, orman sıcak kuşak): Theoria'nın yüzey biyomları ash_desert/steel_steppe/copperwood/shadow_forest/ember_rift/meteor_crater/storm_coast; soğuk (ice_graveyard), nemli (tin_marsh) ve yüksek (titan_peaks) biyomlar bu 2 km²'de yok — **bu bir iklim kuşağı etkisi olabilir** (`/locate` ile 6 400 blok içinde aranır). Asıl risk **yüksek kademe cevheri**: titanyum 1 bölüm, orikalkum 0 (Q56 riski gerçek); lonsdaleit 28 (meteor_crater var). `--share` tahmini (vanilla gürültü, Terralith'siz) ile gerçek dağılım farklı (ör. storm_coast tahmin %21,5, gerçek %1,4; meteor_crater tahmin %1,4, gerçek %2,1) → **kutu kalibrasyonu Terralith'li iklim örneğiyle yapılmalı** (Terralith noise_settings'i değiştiriyor).

`/locate biome` (T9-90202, oyuncunun konumundan; taramada eksik 7 biyom): tin_marsh **1 280** · iron_uplands **1 584** · cobalt_canyon **1 529** · ice_graveyard **1 788** · storm_islands **3 801** · titan_peaks **4 689** · **orichalcum_sky BULUNAMADI** (makul mesafede yok). Yani eksikler iklim kuşağı/mesafe etkisiydi (6 biyom 1,3-4,7 km'de var); `orichalcum_sky` ise Q57 gereği yalnızca aşırı yüksek zirve iklim kutusu, pratikte üretilmiyor.

### Hibrit cevher (2026-11-20, kullanıcı onayı) — `docs/10` Q63
Soru: "bu yalnız ore generation içinse neden vanillaya entegre değil?" Cevap: biyomlar cevher dışında da iş görüyor (mob ekolojisi, yapı etiketleri, keşif), cevherin biyoma bağlanması 9a tasarım kararıydı; bedeli ölçüldü (titanyum 1, orikalkum 0 bölüm; yüzey y ≥ 140 sütunların %0,83'ü). Karar: **hibrit** — ev damarı aynen, ek olarak `ore_<id>_wild` tüm `#minecraft:is_overworld` biyomlarında (daha seyrek, daha alçak). Ölçüm: `-PdevClient=worldgen` (Terralith'li, seed 260309): 1. deneme titanyum 0,21/chunk (çok bol) → 2. ayar: kobalt 1,32 · kalay 0,48 · mitril 0,11 · **titanyum 0,084 · orikalkum 0,026** · lonsdaleit 0,032 · platin 0,027 (bölüm / tam chunk). Dev yardımcıları: senaryo artık süreye bağlı bekliyor + `save-all flush` (ilk sürüm chunk sahibi sayısını "üretilen" sanıp erken kapattı → 26 sn kapanış takılması + StackOverflowError); NeoForge "Mods loaded with 1 warning" modalı (Terralith `logoFile`) gözetimsiz koşuyu durduruyordu → `run-dev/config/neoforge-client.toml` `showLoadWarnings = false` (yalnız dev klasörü).

### Çoklu-seed gerçek üretim taraması (dev, gözetimsiz; `tools/worldgen_sweep.py`, 2026-11-20)
5 seed × 1 200×1 200 blok (6 241 tam chunk), TerraBlender + Terralith + Theoria, hepsinde çökme/özellik-sırası/kapanış hatası **yok**. Seed başına: Theoria yüzey payı **%75 · %19 · %60 · %51 · %22** (seed 90101 · 90202 · 90303 · 90404 · 90505; ortalama %45, hedef ≈ %25; alan 1-3 bölge hücresi → yüksek varyans, karar için küçük örnek), 20 biyomun **6-10'u** bu alanda var (kalanı `/locate` ile 1,3-4,7 km'de), **Haven 22-35 / seed** (S 13-21, M 7-14, L 4-13; spawn'a en yakın 72-148 blok), Zindan 16-19, Harabe 0-22, Kale 0-2. **Cevher (bölüm / tam chunk):** platin 0,024-0,040 · mitril 0,12-0,29 · kobalt 1,0-1,8 sağlam; **ilk yabani ayarla titanyum 0,0 / 0,006 (90101 / 90303) ve orikalkum 0 — alçak/okyanus ağırlıklı seed'lerde y 80-170'te kaya yok** → yüksek kademe yabani damarları yer altı taş bandına (y 4'ten yukarı) taşındı (titanyum y 4-150 rarity 3 · lonsdaleit y 4-120 rarity 4 · orikalkum y 4-170 rarity 4; ev biyomu damarı aynen) → **titanyum 0,054-0,075 · orikalkum 0,013-0,023 · lonsdaleit 0,020-0,029, her seed'de** (üretim deterministik: biyom/yapı sayıları birebir aynı, yalnız cevher değişti). Kalay okyanus ağırlıklı seed'lerde 0,165-0,198 (diğerlerinde 0,59) → kalay yabani damarı da y 4'e indirildi → **0,396 / 0,412** (90101 / 90303). Son jar Prism'e kondu; GameTest `worlds_,gear_` 20/20 (iki koşu), `check_data`/`check_worldgen` (+`--break` 11/11)/`check_pack` yeşil.
**Açık:** (1) ~~Theoria yüzey payı~~ **çözüldü (docs/10 Q64):** üretimsiz büyük alan ölçümü (8 × 15 km) bölge ağırlığı 5'te %33,1 (= 5/15) verdi → ağırlık **3** (%22,0 ölçüldü, hedef ≈ %25); (2) ~~yüksek kademe yabani cevherin yer altında olması~~ **kullanıcı onayladı (2026-11-20)**; (3) Prism'de 5 seed gezisi ve Haven gece mob'suzluğu/Lootr gözle doğrulaması kullanıcıda.

`/locate structure` (T9-90202, oyuncunun konumundan, Prism): haven_s **349** · haven_m **23** · haven_l **570** blok. Kullanıcı: Haven'ların hissi çok iyi ("özellikle L muazzam güzel, kesinlikle burası benim olacak" — Faz 9 hedefi "burası artık benim" duygusu tutmuş). Gece mob'suzluğu ve Lootr gözle doğrulaması sürüyor.

### Haven doğuş testi (2026-11-20) — gözetimsiz, `-PdevClient=haven`
Elle test (kullanıcı, Prism, T9-90202): Lootr sandıkları **düzgün çalışıyor**; yapı içi 12 blok: `/execute if entity @e[type=!player,type=!item,distance=..12]` → **"Test failed"** (boş); 40 blok içi (`tellraw`): Cow, Pillager, Chicken, Sheep×3, Zombie×2, Skeleton, Chicken → hostile'lar yapının 12-40 blok dışında. (İlk komutum 40 blok içindeki HER canlıyı sayıyordu; yanıltıcıydı.)
Otomatik test (`HavenScenario`): gerçek dünya, Hard, gece, oyuncu Haven merkezinde (konum doğrulanır), sunucu 120 tps, 3 oyun dakikası × (S, M, L + kontrol), her doğuş `FinalizeSpawnEvent` ile yakalanır. **Sonuç (PASS):** yapı kutusunun İÇİNDE doğal doğuş **0 / 0 / 0** (hiçbir kategoriden); kontrol noktası (kuru kara, Haven'a 491 blok) 211 doğal hostile doğuş → doğuş çalışıyor; kutu dışı 12 blok halkada doğan hostile S **4** · M **30** · L **36** (korunmuyor); dışarıdan yürüyüp kutuya giren hostile S 1 (creeper) · M 2 (iskelet, zombi) · L 0 (doğuş engeli yürümeyi durdurmaz).
**Yorum (düzeltilmiş, 2026-11-20):** testteki "kutu" `StructureStart` sınırlayıcı kutusu, yani zaten **şablon + 12 blok her yana** (S 8×8 → 32×32, M 30×30 → 54×54, L 80×80 → 104×104: 9b'nin `beard_thin` varsayımı doğruydu). Testin "12 blok halka"sı bunun 12 blok DAHA dışı, yani **binadan 12-24 blok uzak boş arazi**. Sur/çit/temel/lambalar şablonun içinde (S'de çitler en dış sırada, M'de 108, L'de 250 çit/duvar bloğu) → korunan kutunun içinde. Sonuç: bina + 12 blok içinde doğal doğuş 0; 12 blok ötesi arazide doğuş serbest ve bilinçli (kullanıcı kararı 2026-11-20: "sur/border ise korunsun, dışındaysa spawn olabilir"). (Bu paragrafın ilk sürümü "9b varsayımı yanlış" diyordu — yanlıştı; kutu = şablon + 12'yi şablon boyutlarıyla karşılaştırmadan yorumlamıştım.)
**Senaryonun geliştirme dersleri (önceki 3 koşu GEÇERSİZ):** (1) ışınlama komutu `server` kaynağıyla çalışınca `@s` hiçbir varlığı seçmiyor, oyuncu merkezde kaldı ve tüm sayılar yanlış yerde ölçüldü → artık oyuncunun kendi komut kaynağı + konum doğrulaması; (2) sayım penceresi doğuş menziliyle (oyuncunun 128 bloğu) aynı ve her aşamada aynı olmalı; (3) "mob var mı" yetmez, doğan ile yürüyen ayrılmalı → `FinalizeSpawnEvent`.

### Biyom `/locate` (uzaklık)
| Biyom | 90101 | 90202 | 90303 | 90404 | 90505 |
|---|--:|--:|--:|--:|--:|
| tin_marsh | | | | | |
| steel_steppe | | | | | |
| copperwood | | | | | |
| iron_uplands | | | | | |
| luminous_valley | | | | | |
| cobalt_canyon | | | | | |
| ash_desert | | | | | |
| ice_graveyard | | | | | |
| ember_rift | | | | | |
| storm_coast | | | | | |
| crystal_caves | | | | | |
| shadow_forest | | | | | |
| titan_peaks | | | | | |
| ancient_ruins | | | | | |
| rune_vale | | | | | |
| meteor_crater | | | | | |
| storm_islands | | | | | |
| mithril_rift | | | | | |
| orichalcum_sky | | | | | |
| platinum_depths | | | | | |

### Yapılar `/locate structure` (uzaklık)
| Yapı | 90101 | 90202 | 90303 | 90404 | 90505 |
|---|--:|--:|--:|--:|--:|
| `#theoria:havens` | | | | | |
| haven_s | | | | | |
| haven_m | | | | | |
| haven_l | | | | | |
| haven_valley_s / m / l | | | | | |
| `#theoria:ruins` | | | | | |
| `#theoria:dungeons` | | | | | |
| `#theoria:keeps` | | | | | |

### Haven mob'suzluğu (gece, 5 dk)
| Seed | Boy | İçeride N | Çevre 12 blok N | Dışarı (40+) N | Yatak/ocak/sandık | Lootr |
|---|---|--:|--:|--:|---|---|
| | | | | | | |

### Cevher
| Cevher | 90101 | 90202 | 90303 | 90404 | 90505 |
|---|---|---|---|---|---|
| tin | | | | | |
| cobalt | | | | | |
| titanium | | | | | |
| lonsdaleit | | | | | |
| mithril | | | | | |
| orichalcum | | | | | |
| platinum | | | | | |

## Kabul ölçütü (dondurma için, kullanıcı karar verir)
- 5 seed'in hepsinde 20 biyomun hepsi `/locate` ile bulunuyor (yoksa ağırlık/kutu ayarı).
- Her Haven boyu 5 seed'de makul uzaklıkta (kümelerden ≈ 1 000 chunk'ta 3-4 Haven beklenir; 0 olan seed → sıklık artırılır).
- Haven içi/çevresi gece **0 hostile**; Lootr sandıkları çalışıyor.
- Cevher zinciri her seed'de tırmanılabilir (Q56); okyanus payı (Q55) kabul edilebilir; performans R11 içinde.
- Sonra: `docs/journal/f09.md`'ye tarih + commit yaz → **worldgen DONDURULDU**.

## Sonraki yardımcı (bulgulardan sonra, ben)
Dünyalar üretildikten sonra `region/*.mca` dosyalarını çevrimdışı tarayan küçük bir araç (biyom yüzey payı, kaya içi cevher sayısı, `structures.starts` ile Haven/yapı sayıları) yazılabilir: elle gezmeden 5 seed'in oranlarını verir. Gerçek `.mca` dosyaları olmadan 26.3 NBT biçimiyle doğrulanamayacağı için dünyalar çıkınca yazılır.
