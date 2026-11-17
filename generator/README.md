# generator

`data/*.csv` → datapack + resourcepack + lang üreteçleri (Python, bağımsız, `python3 -I` ile çalışır).
Üretilen dosyalar git'e girer; elle düzenlenmez. Her üreteç `--check` ile "depodakiler üretilecekle aynı mı" sorusunu yazmadan yanıtlar.

| Üreteç | Faz | Ne üretir |
|---|--|---|
| `hud.py` | 3 ✅ | vanilla dolgu sprite'larından boyanabilir nötr HUD sprite'ları (`python3 -I generator/hud.py [--check]`) |
| `gear.py` | 6 ✅ | `data/materials.csv` + `ores.csv` + `alloys.csv` + `drops.csv` (nitelik) ve `data/balance/{tiers,tools,ores}.csv` (sayı) → `mods-src/theoria/src/generated/resources/` altında: `theoria/gear.json` (jar'a gömülü tablo), item/blok modelleri, equipment asset'leri, **boyanmış dokular**, tarifler, loot, kazı-kapısı etiketleri, ortak `c:` etiketleri, cevher özellikleri + biyom değiştiricileri, netherite temizliği; lang anahtarları `src/main/resources/assets/theoria/lang/*.json`'a birleştirilir. `python3 -I generator/gear.py [--check|--list]` |
| `catalysts.py` | 7 | planlanan |
| `mobs.py` | 8 | planlanan |
| `structures.py` | 9 | planlanan |

Ortak modüller: `pngio.py` (bağımlılıksız PNG okuma/yazma; `tRNS` renk anahtarı dahil), `vanilla.py` (26.3 istemci jar'ına erişim: doku şablonları, vanilla etiketleri), `gear_model.py` (CSV → içerik modeli), `gear_assets.py` / `gear_data.py` / `gear_art.py` (kaynak paketi / veri paketi / doku boyama).
**Yeni malzeme eklemek:** `data/balance` için `tools/balance/tiers.py`'ye kademe, `data/materials.csv`'ye satır (renk, girdi, büyü yeteneği), cevheri varsa `data/ores.csv` + `tiers.py ORES`; sonra `python3 -I tools/balance/tiers.py && python3 -I generator/gear.py && python3 -I tools/check_gear.py`. Java'ya dokunmak gerekmez.
Dokular vanilla demir şablonlarından türetilir (HUD sprite'larıyla aynı durum) → yayın öncesi lisans notu Faz 13.
