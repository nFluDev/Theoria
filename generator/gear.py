#!/usr/bin/env python3
"""Gear üreteci (Faz 6): data/materials.csv + ores.csv + alloys.csv + drops.csv (nitelik) ve data/balance/{tiers,tools,ores}.csv (sayı)
→ oyunun okuduğu her şey. Elle düzenlenmez; git'e girer.

    python3 -I generator/gear.py            # yaz (artık dosyaları siler)
    python3 -I generator/gear.py --check    # yazmadan: depodakiler üretilecekle aynı mı (PNG'ler piksel düzeyinde); fark varsa çıkış 1
    python3 -I generator/gear.py --list     # üretilen dosya sayıları

ÇIKTILAR (mods-src/theoria/src/generated/resources/, tümü bu üretecin malı):
  theoria/gear.json                       jar'a gömülü tablo: Java kayıt + vanilla override bunu okur (kurucu datapack'ten önce çalışır → ADR-003 Karar 2 ile aynı gerekçe)
  assets/theoria/{items,models,blockstates,equipment,textures}/   item/blok modelleri, equipment asset'leri, boyanmış dokular
  data/theoria/{recipe,loot_table,loot_modifiers,tags,worldgen,neoforge}/   tarifler, loot, tag'ler, cevher üretimi
  data/minecraft/{recipe,tags}/           netherite smithing tarifleri (neoforge:never) ve vanilla kazı-kapısı tag'leri
  data/c/tags/                            ortak (c:) tag'ler — diğer modlar/JEI için
Lang: src/main/resources/assets/theoria/lang/{en_us,tr_tr}.json içinde item.theoria.* / block.theoria.* / itemGroup.theoria.* anahtarları birleştirilir.
Dokular vanilla 26.3 jar'ından türetilir (generator/vanilla.py); jar yoksa --check PNG'leri atlar, yazma durur.
"""
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "tools/balance"))
import gear_assets as A  # noqa: E402
import gear_data as D  # noqa: E402
import gear_model as G  # noqa: E402
import pngio  # noqa: E402
import tiers  # noqa: E402
import vanilla  # noqa: E402

ROOT = HERE.parent
GEN = ROOT / "mods-src/theoria/src/generated/resources"
LANG = ROOT / "mods-src/theoria/src/main/resources/assets/theoria/lang"


def outputs():
    """{mutlak yol: str|bytes}. Dokular (PNG) vanilla jar gerektirir."""
    model = G.build()
    files = {}
    rel = {}
    rel["theoria/gear.json"] = A.gear_json(model, tiers.SPEAR)
    rel.update(A.resource_files(model, tiers.SPEAR))
    for part in (D.recipes(model), D.loot_tables(model), D.gate_tags(model), D.block_tags(model), D.item_tags(model), D.worldgen(model)):
        for path, content in part.items():
            if path in rel:
                raise SystemExit(f"gear: iki üreteç aynı dosyayı yazıyor: {path}")
            rel[path] = content
    files.update({GEN / p: c for p, c in rel.items()})
    en, tr = A.lang(model)
    for name, table in (("en_us", en), ("tr_tr", tr)):
        path = LANG / f"{name}.json"
        files[path] = A.merge_lang(path.read_text(encoding="utf-8"), table)
    return files


def differs(path, content):
    if not path.exists():
        return True
    if isinstance(content, bytes):
        return pngio.decode(path.read_bytes())[2] != pngio.decode(content)[2]
    return path.read_text(encoding="utf-8") != content


def main(argv):
    check = "--check" in argv
    if not vanilla.available() and not check:
        raise SystemExit("gear: vanilla jar bulunamadı — THEORIA_VANILLA_JAR ayarla (bkz. generator/vanilla.py)")
    if vanilla.available():
        files = outputs()
    else:  # jar yok: yalnız PNG olmayanları denetle
        import gear_art
        gear_art.material_textures = lambda m, o: {}
        A.plain_items = lambda model, root: {}
        A.blocks = lambda model, root: {}
        files = outputs()
        print("gear: vanilla jar yok — PNG ve düz item/blok tanımları denetlenmedi", file=sys.stderr)
    stale = [p for p, c in files.items() if differs(p, c)]
    owned = {p for p in files if GEN in p.parents}
    orphans = [p for p in sorted(GEN.rglob("*")) if p.is_file() and p not in owned] if vanilla.available() else []
    if "--list" in argv:
        kinds = {}
        for p in owned:
            kinds[p.suffix] = kinds.get(p.suffix, 0) + 1
        print(f"{len(owned)} dosya", dict(sorted(kinds.items())))
    if check:
        for p in stale + orphans:
            print(f"BAYAT: {p.relative_to(ROOT)} → python3 -I generator/gear.py")
        return 1 if stale or orphans else 0
    for p in stale:
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(files[p]) if isinstance(files[p], bytes) else p.write_text(files[p], encoding="utf-8")
    for p in orphans:
        p.unlink()
    for d in sorted((q for q in GEN.rglob("*") if q.is_dir()), reverse=True):
        if not any(d.iterdir()):
            d.rmdir()
    print(f"gear: {len(owned)} dosya ({len(stale)} yazıldı, {len(orphans)} artık silindi)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
