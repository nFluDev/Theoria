#!/usr/bin/env python3
"""Yapı üreteci (Faz 9b): data/structures.csv + data/worldgen.json → NBT şablonları, jigsaw tek-parça havuzları, yapı/yapı kümesi JSON'ları, sandık loot'u, etiketler.
Elle düzenlenmez; NBT elle inşa edilmez (docs/05 §6). Şablonlar kod-üretimli: palet × arketip (generator/structure_parts|havens|others.py).

    python3 -I generator/structures.py            # yaz (artık dosyaları siler)
    python3 -I generator/structures.py --check    # yazmadan: depodakiler üretilecekle aynı mı (NBT bayt düzeyinde); fark varsa çıkış 1
    python3 -I generator/structures.py --list     # üretilen dosyalar
    python3 -I generator/structures.py --light    # Haven şablonlarının en karanlık doğuş hücresi (mob'suzluk kanıtının yarısı)

ÇIKTILAR (mods-src/theoria/src/generated/structures/, tümü bu üretecin malı):
  data/theoria/structure/<yapı>/<varyant>.nbt                     şablonlar (26.3 StructureTemplate biçimi, DataVersion 5023)
  data/theoria/worldgen/template_pool/<yapı>.json                 tek-parça havuz (palet varyantları eşit ağırlıkta)
  data/theoria/worldgen/structure/<yapı>.json                     minecraft:jigsaw (spawn_overrides: Haven'da tüm doğal kategoriler boş = mob yok)
  data/theoria/worldgen/structure_set/<küme>.json                 random_spread yerleşimi (data/worldgen.json `sets`)
  data/theoria/tags/worldgen/structure/{havens,ruins,dungeons,keeps}.json   /locate structure #theoria:havens
  data/theoria/tags/worldgen/biome/has_structure/<yapı>.json      biyom listesi (structures.csv `biomes`; "#etiket" ve Theoria biyom kimlikleri)
  data/theoria/loot_table/chests/*.json                           Lootr uyumlu vanilla chest tabloları
Lang: structure.theoria.<id> anahtarları src/main/resources/assets/theoria/lang/{en_us,tr_tr}.json içinde birleştirilir.
"""
import csv, json, pathlib, sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import structure_havens as H  # noqa: E402
import structure_others as O  # noqa: E402

ROOT = HERE.parent
DATA = ROOT / "data"
GEN = ROOT / "mods-src/theoria/src/generated/structures"
LANG = ROOT / "mods-src/theoria/src/main/resources/assets/theoria/lang"
OWNED_PREFIX = ("structure.theoria.",)
NS = "theoria"
NATURAL = ("monster", "creature", "ambient", "axolotls", "underground_water_creature", "water_creature", "water_ambient", "misc")
SET_OF = {"havens": "havens", "havens_valley": "havens", "ruins": "ruins", "dungeons": "dungeons", "keeps": "keeps"}


def dumps(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


def read_csv(path):
    with open(path, encoding="utf-8", newline="") as f:
        return [{k: v for k, v in row.items() if k is not None} for row in csv.DictReader(f)]


# ───────────────────────────────────────────────────────────────────────────────────────── loot (vanilla chest tablosu; Lootr bunları kendisi dönüştürür)
def item(name, weight, lo=1, hi=None, **extra):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    if hi is not None or lo != 1:
        hi = lo if hi is None else hi
        e["modifier"] = {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}} if hi != lo else {"type": "minecraft:set_count", "count": lo}
    e.update(extra)
    return e


def book(weight):
    return {"type": "minecraft:item", "name": "minecraft:book", "weight": weight, "modifier": {"type": "minecraft:enchant_randomly", "options": "#minecraft:on_random_loot"}}


def pool(entries, lo, hi=None):
    rolls = lo if hi in (None, lo) else {"type": "minecraft:uniform", "min": lo, "max": hi}
    return {"rolls": rolls, "entries": entries}


T = "theoria:"
LOOT = {
    # "kısmen dolu" sandık: boş giriş ağırlığı yüksek; ücretsiz gear yok — yalnız yolculuğu ödüllendiren sarf/ham madde
    "haven_small": [pool([item("minecraft:bread", 10, 2, 4), item("minecraft:torch", 10, 4, 12), item("minecraft:coal", 8, 3, 6), item("minecraft:apple", 6, 1, 3),
                          item("minecraft:cooked_porkchop", 6, 1, 3), item("minecraft:stick", 6, 2, 8), item(T + "raw_tin", 8, 1, 3), item(T + "carbon_dust", 5, 1, 2),
                          item("minecraft:bone_meal", 5, 2, 6), {"type": "minecraft:empty", "weight": 18}], 2, 3)],
    "haven_medium": [pool([item("minecraft:bread", 10, 3, 6), item("minecraft:torch", 8, 8, 16), item("minecraft:coal", 8, 4, 8), item("minecraft:iron_ingot", 6, 1, 3),
                           item("minecraft:copper_ingot", 6, 2, 5), item(T + "tin_ingot", 6, 1, 3), item(T + "carbon_dust", 6, 2, 4), item(T + "catalyst_dust", 6, 1, 3),
                           item("minecraft:golden_apple", 2), book(4), item("minecraft:arrow", 6, 8, 16), {"type": "minecraft:empty", "weight": 14}], 3, 5)],
    "haven_large": [pool([item("minecraft:cooked_beef", 10, 3, 6), item("minecraft:torch", 8, 12, 24), item("minecraft:iron_ingot", 8, 2, 5), item("minecraft:gold_ingot", 5, 1, 3),
                          item(T + "bronze_ingot", 5, 1, 3), item(T + "steel_ingot", 3, 1, 2), item(T + "catalyst_dust", 6, 2, 4), item(T + "catalyst_essence", 4, 1, 2),
                          item("minecraft:golden_apple", 3), item("minecraft:diamond", 2, 1, 1), book(6), item("minecraft:arrow", 6, 12, 24), {"type": "minecraft:empty", "weight": 10}], 4, 6)],
    "ruin": [pool([item("minecraft:bone", 8, 2, 6), item("minecraft:rotten_flesh", 8, 2, 6), item("minecraft:iron_ingot", 6, 1, 3), item("minecraft:gold_nugget", 6, 2, 8),
                   item(T + "raw_tin", 6, 2, 5), item(T + "tin_ingot", 5, 1, 3), item(T + "bronze_ingot", 3, 1, 2), item(T + "catalyst_dust", 7, 1, 3),
                   book(4), item("minecraft:saddle", 1), {"type": "minecraft:empty", "weight": 10}], 2, 4)],
    "dungeon": [pool([item("minecraft:iron_ingot", 8, 2, 5), item("minecraft:gold_ingot", 6, 1, 4), item(T + "cobalt_ingot", 6, 1, 2), item(T + "steel_ingot", 5, 1, 3),
                      item(T + "raw_cobalt", 6, 2, 4), item(T + "catalyst_dust", 8, 2, 4), item(T + "catalyst_essence", 6, 1, 2), item("minecraft:diamond", 3, 1, 2),
                      item("minecraft:golden_apple", 3), book(6), item("minecraft:music_disc_cat", 1)], 3, 5)],
    "keep": [pool([item(T + "mithril_ingot", 5, 1, 2), item(T + "titanium_ingot", 5, 1, 3), item(T + "orichalcum_ingot", 3, 1, 1), item(T + "platinum_ingot", 2, 1, 1),
                   item(T + "catalyst_core", 6, 1, 1), item(T + "catalyst_essence", 8, 2, 4), item("minecraft:diamond", 8, 1, 3), item("minecraft:golden_apple", 4, 1, 2),
                   item("minecraft:enchanted_golden_apple", 1), book(8)], 4, 6)],
}


def loot_json(name):
    return dumps({"type": "minecraft:chest", "pools": LOOT[name]})


# ───────────────────────────────────────────────────────────────────────────────────────── model
def model():
    rows = read_csv(DATA / "structures.csv")
    world = json.loads((DATA / "worldgen.json").read_text(encoding="utf-8"))
    return rows, world


def build_template(row, variant):
    name = f"{row['id']}/{variant}"
    if row["kind"] == "haven":
        size = row["id"].rsplit("_", 1)[-1]                       # haven_s · haven_valley_m …
        return H.BUILDERS[size](variant, f"{row['kind']}_{size}/{variant}")   # vadi/genel aynı şablonu paylaşır
    return O.BUILDERS[row["kind"]](variant, name)


def template_key(row, variant):
    """Aynı boyutta Haven'lar şablonu paylaşır (haven_m ve haven_valley_m aynı dosyaları kullanır): dosya yolu = <sınıf_boyut>/<varyant>."""
    if row["kind"] == "haven":
        return f"haven_{row['id'].rsplit('_', 1)[-1]}/{variant}"
    return f"{row['id']}/{variant}"


def spawn_overrides(row):
    if row["mobs"] == "none":
        return {cat: {"bounding_box": "full", "spawns": []} for cat in NATURAL}
    by_cat = {}
    for part in row["mobs"].split("|"):
        cat, entity, weight, lo, hi = part.rsplit("/", 4) if part.count("/") >= 4 else (None,) * 5
        assert cat in NATURAL, f"{row['id']}: kategori {cat}"
        by_cat.setdefault(cat, []).append({"type": entity, "weight": int(weight), "count": int(lo) if lo == hi else {"type": "minecraft:uniform", "min_inclusive": int(lo), "max_inclusive": int(hi)}})
    return {cat: {"bounding_box": "piece" if row["kind"] != "keep" else "full", "spawns": spawns} for cat, spawns in by_cat.items()}


def structure_json(row):
    start_y = row["y"]
    if row["y_mode"] == "absolute":
        lo, _, hi = start_y.partition(":")
        height = {"type": "minecraft:uniform", "min_inclusive": {"absolute": int(lo)}, "max_inclusive": {"absolute": int(hi)}}
    else:
        height = {"absolute": int(start_y)}
    out = {"type": "minecraft:jigsaw", "biomes": f"#{NS}:has_structure/{row['id']}", "step": row["step"], "terrain_adaptation": row["adapt"],
           "spawn_overrides": spawn_overrides(row), "start_pool": f"{NS}:{row['id']}", "size": 1, "start_height": height,
           "max_distance_from_center": 80, "use_expansion_hack": False}
    if row["y_mode"] == "surface":
        out["project_start_to_heightmap"] = "WORLD_SURFACE_WG"
    return dumps(out)


def pool_json(row):
    variants = row["variants"].split("|")
    return dumps({"fallback": "minecraft:empty", "elements": [
        {"element": {"element_type": "minecraft:single_pool_element", "location": f"{NS}:{template_key(row, v)}", "processors": "minecraft:empty", "projection": "rigid"}, "weight": 1}
        for v in variants]})


def set_json(set_name, rows, world):
    s = world["sets"][set_name]
    placement = {"type": "minecraft:random_spread", "salt": s["salt"], "separation": s["separation"], "spacing": s["spacing"]}
    if s.get("spread", "linear") != "linear":
        placement["spread_type"] = s["spread"]
    return dumps({"placement": placement, "structures": [{"structure": f"{NS}:{r['id']}", "weight": int(r["weight"])} for r in rows]})


def biome_tag(row):
    values = [v if v.startswith("#") or ":" in v else f"{NS}:{v}" for v in row["biomes"].split("|") if v]
    return dumps({"replace": False, "values": values})


def tag(values):
    return dumps({"replace": False, "values": values})


def merge_lang(existing_text, generated):
    current = json.loads(existing_text)
    merged = {k: v for k, v in current.items() if not k.startswith(OWNED_PREFIX)}
    merged.update(generated)
    return dumps(dict(sorted(merged.items())))


def outputs(only_text=False):
    rows, world = model()
    ids = [r["id"] for r in rows]
    assert len(ids) == len(set(ids)), "structures.csv: yinelenen id"
    rel = {}
    cache = {}
    for r in rows:
        assert r["set"] in world["sets"], f"{r['id']}: bilinmeyen küme {r['set']}"
        for v in r["variants"].split("|"):
            key = template_key(r, v)
            if key not in cache and not only_text:
                cache[key] = build_template(r, v).to_nbt()
            if not only_text:
                rel[f"data/{NS}/structure/{key}.nbt"] = cache[key]
        rel[f"data/{NS}/worldgen/template_pool/{r['id']}.json"] = pool_json(r)
        rel[f"data/{NS}/worldgen/structure/{r['id']}.json"] = structure_json(r)
        rel[f"data/{NS}/tags/worldgen/biome/has_structure/{r['id']}.json"] = biome_tag(r)
    for set_name in world["sets"]:
        members = [r for r in rows if r["set"] == set_name]
        assert members, f"küme {set_name} boş"
        rel[f"data/{NS}/worldgen/structure_set/{set_name}.json"] = set_json(set_name, members, world)
    for group, kinds in (("havens", ("haven",)), ("ruins", ("ruin",)), ("dungeons", ("dungeon",)), ("keeps", ("keep",))):
        rel[f"data/{NS}/tags/worldgen/structure/{group}.json"] = tag([f"{NS}:{r['id']}" for r in rows if r["kind"] in kinds])
    for name in LOOT:
        rel[f"data/{NS}/loot_table/chests/{name}.json"] = loot_json(name)
    files = {GEN / p: c for p, c in rel.items()}
    en = {f"structure.{NS}.{r['id']}": r["en"] for r in rows}
    tr = {f"structure.{NS}.{r['id']}": r["tr"] for r in rows}
    for name, table in (("en_us", en), ("tr_tr", tr)):
        path = LANG / f"{name}.json"
        files[path] = merge_lang(path.read_text(encoding="utf-8"), table)
    return files


def differs(path, content):
    if not path.exists():
        return True
    return path.read_bytes() != content if isinstance(content, bytes) else path.read_text(encoding="utf-8") != content


def stale_files(files):
    return [p for p in sorted(GEN.rglob("*")) if p.is_file() and p not in files]


def main(argv):
    if "--light" in argv:
        rows, _ = model()
        worst = 15
        for r in rows:
            if r["kind"] != "haven":
                continue
            for v in r["variants"].split("|"):
                level, cell, total = build_template(r, v).darkest()
                print(f"  {template_key(r, v):16s} en karanlık doğuş hücresi: ışık {level} @ {cell} ({total} hücre)")
                worst = min(worst, level)
        print(f"en kötü: {worst}  → " + ("OK" if worst >= 4 else "KARANLIK"))
        return 0 if worst >= 4 else 1
    files = outputs()
    changed = [p for p, c in files.items() if differs(p, c)]
    orphans = stale_files(files)
    if "--list" in argv:
        print(f"{len(files)} dosya")
        for p in sorted(files):
            print("  ", p.relative_to(ROOT))
        return 0
    if "--check" in argv:
        for p in changed:
            print(f"BAYAT: {p.relative_to(ROOT)} → python3 -I generator/structures.py")
        for p in orphans:
            print(f"ARTIK: {p.relative_to(ROOT)} → python3 -I generator/structures.py")
        print("OK" if not changed and not orphans else f"HATA ({len(changed) + len(orphans)})")
        return 1 if changed or orphans else 0
    for p in changed:
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(files[p]) if isinstance(files[p], bytes) else p.write_text(files[p], encoding="utf-8")
    for p in orphans:
        p.unlink()
    print(f"structures: {len(files)} dosya, {len(changed)} yazıldı, {len(orphans)} artık silindi")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
