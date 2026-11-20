# generator

`data/*.csv` → datapack + resourcepack + lang üreteçleri (Python, bağımsız, `python3 -I` ile çalışır).
Üretilen dosyalar git'e girer; elle düzenlenmez. Her üreteç `--check` ile "depodakiler üretilecekle aynı mı" sorusunu yazmadan yanıtlar.

| Üreteç | Faz | Ne üretir |
|---|--|---|
| `hud.py` | 3 ✅ | vanilla dolgu sprite'larından boyanabilir nötr HUD sprite'ları (`python3 -I generator/hud.py [--check]`) |
| `gear.py` | 6 ✅ | `data/materials.csv` + `ores.csv` + `alloys.csv` + `drops.csv` (nitelik) ve `data/balance/{tiers,tools,ores}.csv` (sayı) → `mods-src/theoria/src/generated/resources/` altında: `theoria/gear.json` (jar'a gömülü tablo), item/blok modelleri, equipment asset'leri, **boyanmış dokular**, tarifler, loot, kazı-kapısı etiketleri, ortak `c:` etiketleri, cevher özellikleri + biyom değiştiricileri, netherite temizliği; lang anahtarları `src/main/resources/assets/theoria/lang/*.json`'a birleştirilir. `python3 -I generator/gear.py [--check|--list]` |
| `catalysts.py` | 7 ✅ | katalizör öğeleri, Kataliz Ocağı, büyü/loot/lang dosyaları → `src/generated/catalysts/` |
| `mobs.py` | 8 ✅ | `data/mobs.csv` + `mob_drops.csv` + rig'ler → tür tablosu, dokular, loot, doğuş biome modifier'ları → `src/generated/mobs/` |
| `biomes.py` | 9b ✅ | `data/biomes.csv` + `biome_climate.csv` + `worldgen.json` + `ores.csv`(`biomes`) → 20 biyom JSON'u, **TerraBlender veri-güdümlü bölgesi** (`data/theoria/terrablender/region/`), biyom etiketleri (`is_theoria`, `land`, `band/*`, `has_ore/*`, mevcut `c:`/`minecraft:` etiketlerine katılım) → `src/generated/biomes/`. `--coverage` (yüzey bölüntüsü tam+ayrık mı), `--share` (devtest `ClimateProbe` örneklerinden bölge-içi biyom payı + cevher bulunurluğu) |
| `curios.py` | 11 ✅ | `data/curios_slots.csv` → `data/theoria/curios/slots/<id>.json` (Curios ön ayar türlerinin boyutu) + `entities/player.json` (slotları oyuncuya verir) → `src/generated/curios/`. `--check`, `--total` (35 slot; DoD ≥ 30) |
| `relics.py` | 12 ✅ | `data/relics.csv` + `relic_sources.csv` (+ `curios_slots.csv`) → 25 relic öğesi (tanım, model, çizilmiş 16×16 ikon — `relic_art.py`), `#curios:<yuva>` etiketleri, kaynak başına loot tablosu + `neoforge:add_table` değiştiricisi, oyunun okuduğu `theoria/relics.json`, iki dilde lang → `src/generated/relics/`. `--check`, `--list` |
| `structures.py` | 9b ✅ | `data/structures.csv` + `worldgen.json` → kod-üretimli **NBT şablonları** (Haven S/M/L, Harabe, Zindan, 3 Kale; `structure_nbt/parts/havens/others.py`), jigsaw tek-parça havuzları, yapı + yapı kümesi JSON'ları, Haven `spawn_overrides` (tüm doğal kategoriler boş), sandık loot'u → `src/generated/structures/`. `--light` (Haven'ların en karanlık doğuş hücresi) |

Ortak modüller: `structure_nbt.py` (bağımlılıksız NBT yazıcı/okuyucu + blok ızgarası + ışık/doğuş analizi), `pngio.py` (bağımlılıksız PNG okuma/yazma; `tRNS` renk anahtarı dahil), `vanilla.py` (26.3 istemci jar'ına erişim: doku şablonları, vanilla etiketleri), `gear_model.py` (CSV → içerik modeli), `gear_assets.py` / `gear_data.py` / `gear_art.py` (kaynak paketi / veri paketi / doku boyama).
**Yeni malzeme eklemek:** `data/balance` için `tools/balance/tiers.py`'ye kademe, `data/materials.csv`'ye satır (renk, girdi, büyü yeteneği), cevheri varsa `data/ores.csv` + `tiers.py ORES`; sonra `python3 -I tools/balance/tiers.py && python3 -I generator/gear.py && python3 -I tools/check_gear.py`. Java'ya dokunmak gerekmez.
Dokular vanilla demir şablonlarından türetilir (HUD sprite'larıyla aynı durum) → yayın öncesi lisans notu Faz 13.
