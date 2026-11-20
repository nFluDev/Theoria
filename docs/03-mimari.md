# 03 — Mimari

## 1. Repo yapısı
```
Theoria/
├─ CLAUDE.md                     # Claude Code talimatları + devir protokolü
├─ README.md                     # belge indeksi
├─ docs/                         # tasarım (00..11) + journal/
├─ prompts/                      # faz prompt'ları + devir şablonu
├─ STATUS/PROGRESS.md            # tek sayfa canlı durum (her oturum buradan başlar)
├─ data/                         # TEK KAYNAK veri (CSV/JSON): kademeler, katalizörler, moblar, biyomlar, HUD (data/hud/hud.json + hud_*cases.csv ← tools/check_hud.py --write)
│  ├─ balance/tiers.csv  combat.csv   #   ← tools/balance/tiers.py üretir (kademeler · çalışma anı katsayıları); aynı üreteç oyunun okuduğu
│  │                                  #     mods-src/…/resources/data/theoria/balance/{combat/default.json, tier/<id>.json} + theoria/technical_caps.json'u da yazar
│  ├─ balance/sim_cases.csv      #   ← tools/balance/sim.py --cases (oyun-içi testlerin beklenen değerleri)
│  ├─ balance/tools.csv  ores.csv   ← tools/balance/tiers.py (Faz 6: alet türü × kademe sayıları · kazı kapıları)
│  ├─ materials.csv  ores.csv  alloys.csv  drops.csv   (Faz 6 ✅: nitelikler — sayılar yukarıdaki CSV'lerden; generator/gear.py okur)
│  ├─ catalysts.csv  mobs.csv  biomes.csv  structures.csv   (Faz 7-9'da dolar)
├─ generator/                    # data/ → datapack + resourcepack (+ lang) üreteçleri (Python, bağımlılıksız); hud.py (Faz 3 ✅: nötr sprite'lar), gear.py (+ gear_model/assets/data/art, vanilla.py; Faz 6 ✅ → src/generated/resources), pngio.py
├─ tools/                        # doğrulama & denge araçları
│  ├─ balance/ (tiers.py, sim.py)    modmatrix/ (matrix.py)    check_*.py
├─ mods-src/theoria/             # NeoForge çok-modüllü Gradle projesi (aşağıda)
├─ pack/                         # Prism/packwiz: modlist.toml, config/, defaultconfigs/, kubejs yok
└─ build/ , run/                 # .gitignore'da
```

## 2. Kod modülleri (`mods-src/theoria`)
**Tek mod id (`theoria`), tek jar** — modüller Gradle alt projeleri değil *paketlerdir*; LucidRem dersi: modüller arası bağ yoğun, ayrı jar'lar yönetim yükü. Ama paket sınırları katı: bir paket yalnızca `core`'a ve kendi `api` arayüzüne bağımlı olabilir.

```
dev.theoria
├─ core        kayıt (DeferredRegister sıralı), config, network helper, attachment'lar, lang, DataGen tabanı
├─ balance     (Faz 2 ✅) TierRegistry (datapack), TechnicalCaps (attribute tavanları), DamageHooks (zırh/büyü modifier'ı, mob hasar çarpanı, iyileşme),
               MobScaling (mob kademesi attachment'ı + can/zırh modifier'ı), BalanceProbe/BalanceChecks (`/theoria test balance`), TierCommand (`/theoria tier`)
├─ hud         (Faz 3 ✅) pure: HudConfig (data/hud/hud.json), StatusModel (kat modeli), HudText (kısaltma + sığdırma), HudLayout (geometri), HudEffects + HealthTracker (vanilla efektlerinin kararları), HudChecks (`/theoria test hud`);
               client: HudClient (katman kaydı + vanilla iptali), StatusLayer, StatusBars (kırpmalı çizim), HudNumbers, LevelLayer (XP seviyesi bar içinde), HudSprites
├─ wings       (Faz 4 ✅) common: WingsCatalog (FW kayıt defteri, kimlik ayrıştırma), WingChoice (`theoria:wings` attachment), SelectWingsPayload, WingsServer (doğrula → `/wings` sunucu kaynağıyla; ölümde yeniden tak), PreviewAngle (döner tabla durum makinesi), WingsLayout (ekran + ESC satırı geometrisi), WingsChecks (`/theoria test wings`);
               client: WingsClient (ESC düğmesi), WingsScreen (pause'suz), WingCard, WingsPreview (`GuiGraphicsExtractor#entity`)
├─ gear        (Faz 6 ✅) GearData (jar'daki `theoria/gear.json`), GearRegistry (yeni zırh/alet/silah/cevher/blok/yan ürün kaydı + yaratıcı sekme), GearStats (tablo → vanilla bileşenler),
               VanillaOverrides (`ModifyDefaultComponentsEvent`: vanilla deri/odun/taş/bakır/demir/altın/elmas statları — yeni id yok), OreHardness (AT + ortak kurulum),
               RemoveItemsLootModifier (netherite gear her loot'tan süzülür), GearChecks (`/theoria test gear`), GearApi
├─ catalyst    Kataliz Masası (block+menu+screen), katalizör item'ları, uygulama mantığı
├─ fauna       entity'ler, AI, model/animasyon, drop tabloları
├─ worlds      (Faz 9b ✅) yalnızca `WorldChecks` (`/theoria test worlds`): biyomlar/cevher etiketleri/yapılar/Haven spawn engeli/TerraBlender bölgesi yüklü mü. Dünya üretiminin kendisi VERİdir:
│    + `TerraBlenderBridge` (kapı sırasında eklendi): TerraBlender 26.3 veri-güdümlü bölgeleri biyom ağaçlarından SONRA yüklüyor → `ServerAboutToStart` HIGHEST'te yansımayla erken yükler (docs/10 Q61); `WorldChecks.probe` gerçek biyom kaynağını örnekler
               biyomlar + TerraBlender `terrablender:region` JSON'u (generator/biomes.py), yapı NBT'leri + jigsaw havuzları + spawn_overrides + loot (generator/structures.py), cevher biome modifier'ları (generator/gear.py)
├─ stats       seviye, ömür boyu XP, beceri puanı, Durum ekranı
├─ curios      (Faz 11 ✅) **Curios'a dokunan TEK paket** (ADR-007 K1): CuriosMaxMenu (+ MaxCurioSlot: Curios'un CurioSlot'u, isActive sekmeye/sayfaya bakar), CuriosMaxLayout (saf geometri), CuriosOpen (Curios'un menüsünü bizimkiyle değiştirir) + OpenCuriosMaxPayload, CuriosChecks (`/theoria test curios`);
│    client: CuriosMaxScreen (pause'suz; sekmeler, arama, kozmetik sekmesi, 3B önizleme), CuriosClient. Slot boyutları VERİ: `data/curios_slots.csv` → `generator/curios.py`. `WornCurios` (Faz 12): relic'lerin Curios'u okuduğu TEK kapı (takılı yığınlar, yuva sayısı, test için yerleştir/değiştir)
├─ relics      (Faz 12 ✅) RelicData (jar JSON `theoria/relics.json` ← `generator/relics.py` ← `data/relics.csv`), RelicItem/RelicItems (+ sekme), RelicText (ipucu, saf), RelicEffects (takılı relic'ten nitelik/etki/zamanlı türler), RelicDamage (bağışıklık + ölümden dönüş), RelicProbe (per-relic gerçek-kod denetimi), RelicChecks (`/theoria test relic [id]`). Curios'a dokunmaz; ikon/etiket/loot/lang VERİdir
├─ cosmetics   (Faz 11 ✅) Curios'tan bağımsız: Cosmetics (`theoria:cosmetics` attachment, 4 yuva), CosmeticRules (hangi parça sığar, kanat = GLIDER), CosmeticLook (görünüm kuralı, saf), CosmeticContainer/CosmeticSlot (menü slotları), CosmeticsChecks;
│    client: CosmeticsClient (tek AvatarRenderStateModifier), CosmeticsConfig (3 istemci ayarı)
├─ macos       (gerekirse) Ctrl+Click / Ctrl+Q mixin'leri
└─ devtest     yalnızca -Dtheoria.dev=true ile etkin: GameTest'ler (core_*, vanilla_* ADR regresyonları, balance_* Faz 2 testleri, mob dump),
               gözetimsiz istemci senaryoları (duman testi, FPS ölçümü). Hiçbir paket devtest'e bağımlı olamaz.
```
Yan etkiler tek yönde akar: `core ← balance ← {gear, catalyst, fauna, stats} ← {hud, wings, curios, cosmetics}`, `curios ← relics` (relics yalnız `WornCurios` üzerinden); `worlds` yalnızca `core`+`gear`(item id'leri).

## 3. Veri akışı
```
data/*.csv ──generator──▶ datapack (loot, tarif, tag, biyom, yapı JSON)
                      └─▶ resourcepack (model, lang, tint'li doku varyantları)
                      └─▶ src/generated/resources  (git'e girer)
Java: yalnızca mekanik; stat sayıları data/balance'tan okunur (build zamanı sabit değil, datapack'ten yüklenir)
```
- Çalışma anında bir **`TierRegistry`** (`AddServerReloadListenersEvent`) `tiers.csv`/`combat.csv`'den türeyen JSON'u (datapack: `data/theoria/balance/…`) okur → denge turu için oyunu yeniden derlemeden `/reload`; bozuk/eksik veri reload'u sesizce değil, yüksek sesle düşürür. Attribute teknik tavanları datapack'ten gelemez (mod kurucusu datapack'ten önce çalışır): `theoria/technical_caps.json` jar'dan okunur.
- `sim_cases.csv` jar'a Gradle `processResources` ile kopyalanır (repoda tek kopya `data/balance/`'de kalır); `/theoria test balance` beklenen değerleri oradan okur.

## 4. Sunucu-otoriter durum
Tüm kalıcı oyuncu durumu **NeoForge data attachment** (serializable, `copyOnDeath`): `wings`, `level_xp_total`, `level`, `skill_points`, `stats{...}`, `cosmetic_slots`, `catalyst_cache`. İstemci yalnızca sunucudan gelen senkronize kopyayı gösterir; değişiklik isteği paket (`Payload`) ile gider, sunucu doğrular. Tek oyunculuda bile entegre sunucu bunu yapar → çok oyunculu bedava.

## 5. Test ve doğrulama
| Araç | Ne doğrular |
|---|---|
| `tools/balance/tiers.py`, `sim.py` | formül sağlığı: ölüm/öldürme vuruşu bantları, kademe eğimi, zırh ömrü (`sim.py`; `--fit`, `--mobs`, `--budget`); `check_data.py` her koşuda `--check` çağırır |
| `tools/preflight.py` | tek komut: veri denetimleri + derleme + GameTest'ler (`--client`: + istemci duman testi) |
| `tools/check_data.py` | CSV şeması, id benzersizliği, lang anahtarları eksik/artık, `data/balance/*.csv` üreteçle güncel mi, denge bantları yeşil mi |
| `tools/check_hud.py` | (Faz 3) hud.json şeması · katman modeli elle yazılmış beklenenlere karşı (20/21/25/40 HP, 250 HP, zırh 0/40/310, binek…) · özellik taraması (monoton, tam can = tam dolu, titreme yok) · Java'nın sınandığı vaka tabloları güncel mi · sprite'lar var/nötr/`_half` yok/artık yok/üreteçle özdeş · lang anahtarları; `--write` tabloları yeniden yazar, `--break` negatif test |
| `tools/check_wings.py` | (Faz 4) FW'ye derleme bağımlılığı yok (yalnızca `localRuntime`) · wings paketinde GL/LWJGL/RenderSystem yok, vanilla `*FollowsAngle/Mouse` yok, `entity()` var · `isPauseScreen()=false` · sunucu yalnızca `wings give @s <doğrulanmış id>` / `wings take @s` çalıştırır · FW jar'ındaki her kanat için tr/en ad var. `--break` negatif test |
| `tools/check_gear.py` | (Faz 6) materials/ores/alloys/drops.csv şeması ↔ tiers/tools/ores.csv · `generator/gear.py --check` (PNG'ler piksel düzeyinde) · item→model→doku, blockstate, equipment (3 katman), lang (iki dil, artık anahtar yok), tarif/loot/tag içi kimlikler · **kazı kapıları oyundan bağımsız çözülür** (vanilla jar etiketleri + bizimkiler, replace/remove anlamıyla; 264 çift) · blok sertliği · **tırmanış ulaşılabilirliği** (tarif+loot grafiği, kapılara uyarak her gear/cevher/alaşım elde edilebilir). `--break` 9 negatif vaka; `check_data.py` bunu çağırır |
| `tools/check_worldgen.py` | (Faz 9b) biomes/biome_climate/structures/worldgen/ores CSV şemaları · **yüzey iklim kutuları tam ve ayrık bölüntü** (çakışma = ölü biyom) · üreteçler güncel (biomes/structures/gear) · biyom JSON'larında vanilla feature/carver adları · bölge JSON'u eşleme sayısı/kodek sınırı · cevher → `has_ore` etiketi → biome modifier · mob/biyom kademe bandı · **NBT şablonları bağımsız okunur** (Haven: yatak+ocak+sandık, spawner/varlık 0, her doğuş hücresinde blok ışığı ≥ 4, `spawn_overrides` her doğal kategoride boş+full; Zindan: spawner+elit; Kale: tam 1 boss) · loot tabloları. `--break` 9 negatif vaka; `check_data.py` bunu çağırır |
| `tools/gear_preview.py` | çevrimdışı doku önizlemesi (`build/gear-preview/*.png` contact sheet): ekran yakalama yerine; kesin onay oyunda |
| `tools/hud_preview.py` | çevrimdışı HUD önizlemesi (`build/hud-preview/*.png`): aynı model + vanilla sprite + vanilla bitmap font; ekran yakalama yerine. Yaklaşık — kesin onay oyunda |
| `/theoria test <alan>` (`core.check.Checks`) | canlı dünyada güvenle çalışan öz-denetimler (okur ya da dokunduğunu geri alır) |
| `/theoria test hud` | (Faz 3) pure model: hud.json, 69 çubuk/yerleşim, 26 sayı/sığdırma ve 24 efekt (yanıp sönme fazı, yenilenme dalgası, düşük can, açlık titremesi, `HealthTracker`) vakası Python referansıyla eşit (Java ≡ `tools/hud_model.py`) |
| `/theoria test wings` | (Faz 4) FW kataloğu (≥ 1 kanat) · `/wings give|take` düğümleri · attachment + payload bağlı · kötü kimlikler reddedilir · `PreviewAngle` kuralları · yerleşim (11 016 boyut × katalog büyüklüğü vakası) ve ESC satırı (56 580 vaka) taraması |
| `/theoria test gear` | (Faz 6) 14 zırh + 11 alet kademesi tabloda · her zırh seti/alet balance datapack'iyle aynı (parça puanı, takım toplamı, sertlik, dayanıklılık, kılıç hasarı) · pickaxe × kapılı blok matrisi (264) · cevher sertliği · netherite smithing tarifleri yüklü değil |
| `/theoria test balance` | (Faz 2) veri yüklü · attribute tavanları · `armor300_vs_t8` ve 26 `sim_cases.csv` satırı gerçek `hurtServer` çağrısıyla ±%10 · mob kademesi · iyileşme ölçeği; yalnızca atılıp-giden varlıklar kullanır |
| `/theoria test worlds` | (Faz 9b) 20 biyom kayıtlı + `is_overworld` · her cevher özelliği yalnız kendi `has_ore` biyomlarında · 11 yapı/25 havuz şablonu dolu · Haven'lar 8 doğal kategoriyi engelliyor · TerraBlender bölgesi `theoria:overworld` yüklü (TerraBlender yoksa atlanır) |
| GameTest `worlds_*` | (Faz 9b) `worlds_checks` · `worlds_biomes` (biomes.csv ↔ kayıt, etiketler, `joins`) · `worlds_ores` (ores.csv `biomes` ↔ etiket) · `worlds_structures` (structures.csv ↔ yapı/küme/havuz/şablon boyutu, sandık loot'u var) · **`worlds_haven`: gerçek `Structure#generate` + `placeInChunk`, sonra motorun kendi doğuş kapısı `ChunkGenerator#getMobsAt` her kategori × ızgara noktasında boş; yatak/ocak/sandık(+loot) yerinde, spawner/varlık 0** · `worlds_loot` (800 çekiliş/tablo, Haven'da gear yok) · `worlds_climate_probe` (ölçüm yardımcısı) |
| GameTest `gear_*` | (Faz 6) `gear_checks` · `gear_ids` (vanilla malzeme için yeni id yok) · `gear_armor_csv`/`gear_tools_csv` (tiers.csv/tools.csv ↔ gerçek item bileşenleri: 14 set, 66 alet/silah) · `gear_gate_matrix` (ores.csv × tools.csv, bağımsız beklenti) · `gear_mining` (gerçek kırma ilerlemesi) · `gear_innate` (hız/geri itme nitelikleri, zırh giyince) · `gear_loot` (cevher tabloları, karbon tozu GLM'i, bastion sandıklarında netherite gear 0/1200) · `gear_recipes` (115 tarif + 4 gerçek crafting) · `gear_worldgen` (7 cevher özelliği her Overworld biyomuna eklendi, gerçek yerleştirme) |
| GameTest (`./gradlew runGameTestServer`, `devtest`) | yıkıcı/senaryolu testler: `balance_*` (26 sim satırı mob + oyuncu kurbanında ±%0,3, öldürme vuruşları, mob hasar yolları, zorluk sırası, Breach, i-frame, iyileşme, kaydet/yükle, komutlar), ölüm→yeniden doğuş; yapı/loot (Faz 9) |
| `./gradlew runDevClient` | gözetimsiz istemci: payload ↔ senkron attachment, komut çıktısı, lang; `-PdevClient=fps` FPS ölçümü; `-PdevClient=wings` Kanatlar senaryosu (ESC düğmesi, ESC duraklatır/ekran duraklatmaz, gerçek tıklamalarla tak/değiştir/çıkar, öl→yeniden doğ, GUI ölçekleri); `-PdevClient=hud` HUD senaryosu (hasar yanıp sönmesi, düşük can, yenilenme, açlık titremesi, 20/21/250 HP, zırh 310, seviye, emilim, zehir, binek, Arayüz Ölçeği 1-4, yaratıcıda sessizlik; vanilla katmanlar iptal, yükseklik sayaçları; başlangıçta envanter ve zırh slotları temizlenir) |
| Elle (kullanıcı) | HUD görünümü, Kanatlar ekranı/kanatların görünümü, ekran hissi, FPS |

## 6. Konvansiyonlar
- Paket: `dev.theoria.<alan>`; mod id `theoria`; kaynak namespace `theoria:`.
- Item id: `<kademe>_<parça>` (`mithril_chestplate`), mob id: `<ad>` küçük harf.
- Lang: `tr_tr.json` + `en_us.json` her zaman birlikte (check_data.py zorlar).
- Hiçbir `*.java` 600 satırı geçmez; ekran sınıfları layout/mantık ayrı.
- Yorum: *neden*, nadiren *ne*. Her "tuhaf" çözümün yanında bug/ders referansı.
