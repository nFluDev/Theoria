#!/usr/bin/env python3
"""Biyom üreteci (Faz 9b): data/biomes.csv + data/biome_climate.csv + data/worldgen.json + data/ores.csv(`biomes`) → 20 Theoria biyomu, TerraBlender bölgesi, etiketler.
Elle düzenlenmez; git'e girer.

    python3 -I generator/biomes.py            # yaz (artık dosyaları siler)
    python3 -I generator/biomes.py --check    # yazmadan: depodakiler üretilecekle aynı mı; fark varsa çıkış 1
    python3 -I generator/biomes.py --list     # üretilen dosya sayıları
    python3 -I generator/biomes.py --coverage # yüzey kutuları tam ve ayrık bölüntü mü (delik/çakışma)
    python3 -I generator/biomes.py --share    # devtest ClimateProbe örneklerinden bölge-içi biyom payı ve cevher bulunurluğu (ÖLÇÜM DEĞİL; Chunky kapısı ayrı)

ÇIKTILAR (mods-src/theoria/src/generated/biomes/, tümü bu üretecin malı):
  data/theoria/worldgen/biome/<id>.json                 20 biyom (26.3 biçimi: `attributes` + `effects`; özellik listeleri aşağıdaki profillerden)
  data/theoria/terrablender/region/<region>.json        TerraBlender 26.3 veri-güdümlü bölge (`terrablender:region` dünya kaydı) — Java'ya derleme bağımlılığı yok
  data/theoria/tags/worldgen/biome/{is_theoria,land}.json, band/t*_*.json, has_ore/<cevher>.json   (has_ore: ores.csv `biomes`; generator/gear.py'nin biome modifier'ı bunları okur)
  data/minecraft/tags/worldgen/biome/is_overworld.json (+ is_forest, is_hill, is_mountain), data/c/tags/worldgen/biome/*.json   biyomların mevcut mob/yapı/cevher etiketlerine katılımı
Lang: biome.theoria.<id> anahtarları src/main/resources/assets/theoria/lang/{en_us,tr_tr}.json içinde birleştirilir.

Biyom bandı (tier_lo..tier_hi) tür/cevher ekolojisi içindir: MobZones konum formülünü kendiliğinden değiştirmez (docs/journal/f09.md 9a).
"""
import collections, csv, json, math, pathlib, sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(HERE))
import feature_order as FO  # noqa: E402
DATA = ROOT / "data"
GEN = ROOT / "mods-src/theoria/src/generated/biomes"
LANG = ROOT / "mods-src/theoria/src/main/resources/assets/theoria/lang"
OWNED_PREFIX = ("biome.theoria.",)
NS = "theoria"

# ───────────────────────────────────────────────────────────────────────────────────────── özellik profilleri (vanilla placed feature adları; tools/check_worldgen.py jar'a karşı doğrular)
ORES = ["ore_dirt", "ore_gravel", "ore_granite_upper", "ore_granite_lower", "ore_diorite_upper", "ore_diorite_lower", "ore_andesite_upper", "ore_andesite_lower", "ore_tuff",
        "ore_coal_upper", "ore_coal_lower", "ore_iron_upper", "ore_iron_middle", "ore_iron_small", "ore_gold", "ore_gold_lower", "ore_redstone", "ore_redstone_lower",
        "ore_diamond", "ore_diamond_medium", "ore_diamond_large", "ore_diamond_buried", "ore_lapis", "ore_lapis_buried", "ore_copper", "underwater_magma"]
DISKS = ["disk_sand", "disk_clay", "disk_gravel"]
# profil: vegetal_decoration (adım 9) + isteğe bağlı ek adımlar {adım: [özellik]}; "kit": doğuş seti; "carvers": taşıyıcılar
PROFILES = {
    "marsh":     dict(veg=["trees_swamp", "flower_swamp", "patch_grass_normal", "patch_waterlily", "brown_mushroom_swamp", "red_mushroom_swamp", "patch_sugar_cane_swamp", "patch_firefly_bush_swamp"], disks=DISKS, kit="farm"),
    "steppe":    dict(veg=["patch_grass_savanna", "patch_tall_grass", "flower_default", "trees_savanna", "patch_bush"], disks=DISKS, kit="farm"),
    "grove":     dict(veg=["trees_dappled_forest", "patch_grass_forest", "forest_flowers", "brown_mushroom_normal", "red_mushroom_normal", "patch_bush"], disks=DISKS, kit="farm"),
    "uplands":   dict(veg=["trees_windswept_hills", "patch_grass_normal", "flower_default", "forest_rock", "patch_bush"], disks=DISKS, kit="farm"),
    "valley":    dict(veg=["trees_meadow", "wildflowers_meadow", "flower_meadow", "patch_grass_meadow", "patch_firefly_bush_near_water"], disks=DISKS, kit="farm"),
    "canyon":    dict(veg=["trees_badlands", "patch_grass_badlands", "patch_dead_bush_badlands", "patch_sugar_cane_badlands"], disks=DISKS, kit="dry"),
    "desert":    dict(veg=["patch_dry_grass_desert", "patch_dead_bush_2", "patch_sugar_cane_desert", "patch_cactus_desert", "desert_well"], disks=DISKS, kit="desert"),
    "frozen":    dict(veg=["trees_snowy", "ice_patch", "ice_spike", "patch_grass_taiga"], disks=DISKS, kit="frozen", top=["freeze_top_layer"]),
    "rift":      dict(veg=["trees_badlands", "patch_dry_grass_badlands", "patch_dead_bush_badlands"], disks=DISKS, kit="dry"),
    "coast":     dict(veg=["trees_windswept_savanna", "patch_grass_normal", "seagrass_normal", "patch_bush"], disks=DISKS, kit="farm"),
    "crystal":   dict(veg=["glow_lichen", "spore_blossom"], under=["large_dripstone", "pointed_dripstone", "dripstone_cluster"], disks=[], kit="cave"),
    "shadow":    dict(veg=["dark_forest_vegetation", "brown_mushroom_old_growth", "red_mushroom_old_growth", "patch_grass_forest"], disks=DISKS, kit="farm"),
    "peaks":     dict(veg=["trees_grove", "pile_snow", "patch_grass_taiga"], extra_ores=["ore_emerald", "ore_infested"], disks=[], kit="frozen", top=["freeze_top_layer"]),
    "ruins":     dict(veg=["trees_old_growth_pine_taiga", "forest_rock", "patch_grass_taiga"], disks=DISKS, kit="farm"),
    "vale":      dict(veg=["trees_cherry", "flower_cherry", "patch_grass_meadow"], disks=DISKS, kit="farm"),
    "crater":    dict(veg=["patch_dead_bush", "patch_dry_grass_badlands"], disks=["disk_gravel"], kit="dry"),
    "islands":   dict(veg=["trees_savanna", "patch_grass_normal", "kelp_cold", "seagrass_normal", "sea_pickle"], disks=DISKS, kit="ocean"),
    "rift_cave": dict(veg=["glow_lichen"], under=["large_dripstone", "pointed_dripstone", "dripstone_cluster"], disks=[], kit="cave"),
    "sky":       dict(veg=["trees_meadow", "flower_meadow", "patch_grass_meadow"], disks=[], kit="farm", top=["freeze_top_layer"]),
    "depths":    dict(veg=["glow_lichen"], disks=[], kit="cave"),
}
STANDARD_MONSTERS = [("spider", 100, 4, 4), ("zombie", 95, 4, 4), ("skeleton", 100, 4, 4), ("creeper", 100, 4, 4), ("slime", 100, 4, 4), ("enderman", 10, 1, 4), ("witch", 5, 1, 1)]
KITS = {
    "farm":   dict(creature=[("sheep", 12, 4, 4), ("pig", 10, 4, 4), ("chicken", 10, 4, 4), ("cow", 8, 4, 4)], monster=STANDARD_MONSTERS, ambient=[("bat", 10, 8, 8)]),
    "dry":    dict(creature=[("rabbit", 4, 2, 3)], monster=STANDARD_MONSTERS, ambient=[("bat", 10, 8, 8)]),
    "desert": dict(creature=[("rabbit", 4, 2, 3)], monster=[m for m in STANDARD_MONSTERS if m[0] != "zombie"] + [("husk", 95, 4, 4)], ambient=[("bat", 10, 8, 8)]),
    "frozen": dict(creature=[("rabbit", 10, 2, 3), ("polar_bear", 1, 1, 2)], monster=[m for m in STANDARD_MONSTERS if m[0] != "skeleton"] + [("stray", 80, 4, 4)], ambient=[("bat", 10, 8, 8)]),
    "cave":   dict(creature=[], monster=STANDARD_MONSTERS, ambient=[("bat", 10, 8, 8)], underground_water_creature=[("glow_squid", 10, 4, 6)]),
    "ocean":  dict(creature=[], monster=STANDARD_MONSTERS[:5], ambient=[], water_creature=[("squid", 10, 1, 4)], water_ambient=[("cod", 15, 3, 6), ("salmon", 15, 1, 5)]),
}
CARVERS = ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]
ATTR = "minecraft:"


def dumps(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


def read_csv(path):
    with open(path, encoding="utf-8", newline="") as f:
        return [{k: v for k, v in row.items() if k is not None} for row in csv.DictReader(f)]


def model():
    biomes = read_csv(DATA / "biomes.csv")
    ores = read_csv(DATA / "ores.csv")
    world = json.loads((DATA / "worldgen.json").read_text(encoding="utf-8"))
    world["tiles"] = read_csv(DATA / "biome_climate.csv")
    return biomes, ores, world


def span(text):
    lo, _, hi = text.partition(":")
    lo, hi = float(lo), float(hi or lo)
    assert lo <= hi, f"aralık ters: {text}"
    return lo, hi


def num(x):
    return int(x) if float(x).is_integer() else round(float(x), 6)


def interval(lo, hi):
    return num(lo) if lo == hi else [num(lo), num(hi)]


def spawns(entries):
    return [dict(type=f"minecraft:{t}", weight=w, count=a if a == b else dict(type="minecraft:uniform", min_inclusive=a, max_inclusive=b)) for t, w, a, b in entries]


# ───────────────────────────────────────────────────────────────────────────────────────── biyom JSON
def biome_json(b):
    prof = PROFILES[b["profile"]]
    kit = KITS[prof["kit"]]
    by_cat = {cat: spawns(rows) for cat, rows in kit.items() if rows}
    if prof["kit"] == "cave":
        by_cat.setdefault("underground_water_creature", [])
    attrs = {f"{ATTR}gameplay/natural_mob_spawns": {"argument": {"spawn_costs": {}, "spawns_by_category": by_cat}, "modifier": "overlay"},
             f"{ATTR}visual/sky_color": b["sky"]}
    if b["fog"]:
        attrs[f"{ATTR}visual/fog_color"] = b["fog"]
        attrs[f"{ATTR}visual/water_fog_color"] = b["water"]
    effects = {"water_color": b["water"]}
    if b["grass"]:
        effects["grass_color"] = b["grass"]
    if b["foliage"]:
        effects["foliage_color"] = b["foliage"]
    steps = [[] for _ in range(11)]
    steps[1] = ["minecraft:lake_lava_underground", "minecraft:lake_lava_surface"]
    steps[2] = ["minecraft:amethyst_geode"]
    steps[3] = ["minecraft:monster_room", "minecraft:monster_room_deep"]
    steps[6] = [f"minecraft:{n}" for n in ORES + prof.get("extra_ores", []) + prof.get("disks", [])]
    steps[7] = [f"minecraft:{n}" for n in prof.get("under", [])]
    steps[8] = ["minecraft:spring_water", "minecraft:spring_lava"]
    steps[9] = [f"minecraft:{n}" for n in prof["veg"]]
    steps[10] = [f"minecraft:{n}" for n in prof.get("top", [])]
    # Her adım listesi vanilla-uyumlu tek sıraya dizilir (generator/feature_order.py): elle yazılmış profil sıraları FeatureSorter'da "Feature order cycle" ile üretimi düşürüyordu.
    ranks = FO.rank_of(FO.load_order())
    for s in FO.STEPS:
        steps[s] = FO.sort_step(s, steps[s], ranks)
    out = {"attributes": attrs, "carvers": CARVERS, "downfall": float(b["downfall"]), "effects": effects, "features": steps,
           "has_precipitation": b["precip"] != "none", "temperature": float(b["temperature"])}
    return out


# ───────────────────────────────────────────────────────────────────────────────────────── TerraBlender bölgesi
AXES = ("t", "h", "c", "e", "w")
SURFACE_OFFSET = 0.001   # eşitlikte mağara/dip kutuları yüzey noktasına karşı kazansın (vanilla'da 0; burada yalnız bağ bozucu)


def climate_points(tile):
    """Vanilla'nın OverworldBiomeBuilder kalıbı: yüzey kutusu depth 0 ve 1 olmak üzere iki eşleme, mağara/dip kutusu tek aralık."""
    base = {k: span(tile[k]) for k in AXES}
    depths = [(0.0, 0.0), (1.0, 1.0)] if tile["d"] == "surface" else [span(tile["d"])]
    offset = float(tile.get("offset") or (SURFACE_OFFSET if tile["d"] == "surface" else 0.0))
    return [dict(base, d=dd, offset=offset) for dd in depths]


def region_json(biomes, world):
    ids = {b["id"] for b in biomes}
    mappings = []
    for tile in world["tiles"]:
        assert tile["biome"] in ids, f"biome_climate.csv: bilinmeyen biyom {tile['biome']}"
        for p in climate_points(tile):
            mappings.append({"parameters": {"temperature": interval(*p["t"]), "humidity": interval(*p["h"]), "continentalness": interval(*p["c"]),
                                            "erosion": interval(*p["e"]), "depth": interval(*p["d"]), "weirdness": interval(*p["w"]), "offset": num(p["offset"])},
                             "biome": f"{NS}:{tile['biome']}"})
    r = world["region"]
    return dumps({"name": f"{NS}:{r['name']}", "type": "overworld", "weight": r["weight"], "biomeMappings": mappings})


def distance(v, lo, hi):
    return max(v - hi, lo - v, 0.0)


def fitness(p, target):
    """Climate.ParameterPoint#fitness: eksen başına aralığa uzaklığın karesi + offset²."""
    return sum(distance(target[k], *p[k]) ** 2 for k in ("t", "h", "c", "e", "d", "w")) + p["offset"] ** 2


def nearest(points, target):
    return min(points, key=lambda x: fitness(x[1], target))[0]


def region_points(biomes, world):
    return [(t["biome"], p) for t in world["tiles"] for p in climate_points(t)]


def coverage(biomes, world, samples=120000, seed=11):
    """Yüzey kutuları (depth 0) tam ve ayrık bölüntü mü? Rastgele iklim hedefleri: kaç yüzey kutusu kapsıyor (1 olmalı)."""
    import random
    rng = random.Random(seed)
    surf = [t for t in world["tiles"] if t["d"] == "surface"]
    spans = [{k: span(t[k]) for k in AXES} for t in surf]
    holes = overlaps = 0
    examples = []
    caves = [t for t in world["tiles"] if t["d"] != "surface"]
    cave_spans = [({k: span(t[k]) for k in AXES}, span(t["d"])) for t in caves]
    for _ in range(samples):
        tg_c = dict(t=rng.uniform(-1, 1), h=rng.uniform(-1, 1), c=rng.uniform(-1.2, 1), e=rng.uniform(-1, 1), w=rng.uniform(-1, 1))
        d = rng.uniform(0.0, 1.3)
        n_c = sum(all(lo <= tg_c[k] <= hi for k, (lo, hi) in sp.items()) and dlo <= d <= dhi for sp, (dlo, dhi) in cave_spans)
        if n_c > 1:
            overlaps += 1
            if len(examples) < 3:
                examples.append(("mağara kutuları çakışıyor", n_c, d))
    for _ in range(samples):
        tg = dict(t=rng.uniform(-1, 1), h=rng.uniform(-1, 1), c=rng.uniform(-1.2, 1), e=rng.uniform(-1, 1), w=rng.uniform(-1, 1))
        n = sum(all(lo <= tg[k] <= hi for k, (lo, hi) in sp.items()) for sp in spans)
        if n == 0:
            holes += 1
        elif n > 1:
            overlaps += 1
        if n != 1 and len(examples) < 3:
            examples.append((n, {k: round(v, 3) for k, v in tg.items()}))
    return holes, overlaps, samples, examples


def share(biomes, world, samples_csv):
    """Gerçek iklim örneklerinden (devtest ClimateProbe → build/dev-out/world/climate_*.csv) bölge içi biyom payı + cevher bulunurluğu.
    Gerçek vanilla iklim gürültüsü ve gerçek yüzey yüksekliği kullanılır; cevher için yalnız KAYA olan (y < yüzey) örnekler sayılır (havada damar olmaz).
    TerraBlender bölge seçimi (ağırlık) simüle EDİLMEZ — yalnız bölgenin İÇİ. Chunky turu hâlâ kapıdır."""
    pts = region_points(biomes, world)
    surface, under = collections.Counter(), collections.Counter()
    rock = collections.defaultdict(lambda: collections.Counter())      # y → biyom → sayı (yalnız kaya içindeki örnekler)
    rock_n = collections.Counter()
    n_surface = n_under = rows = 0
    columns = {}
    samples = []
    for path in samples_csv:
        with open(path, encoding="utf-8", newline="") as f:
            for r in csv.DictReader(f):
                rows += 1
                key = (r["seed"], r["x"], r["z"])
                if r["layer"] == "surface":
                    columns[key] = int(r["y"])
                    tg = {k: float(r[k]) for k in ("t", "h", "c", "e", "d", "w")}
                    surface[nearest(pts, tg)] += 1
                    n_surface += 1
                else:
                    samples.append((key, int(r["y"]), {k: float(r[k]) for k in ("t", "h", "c", "e", "d", "w")}))
    for key, y, tg in samples:
        if y >= columns.get(key, -999) - 1:
            continue                                                  # hava ya da yüzey katmanı: cevher yok
        b = nearest(pts, tg)
        rock[y][b] += 1
        rock_n[y] += 1
        if -60 <= y <= 60:
            under[b] += 1
            n_under += 1
    return surface, n_surface, rock, rock_n, under, n_under, rows


# ───────────────────────────────────────────────────────────────────────────────────────── etiketler
def tag(values):
    return dumps({"replace": False, "values": values})


def ore_tag_values(spec):
    return [v if v.startswith("#") or ":" in v else f"{NS}:{v}" for v in spec.split("|") if v]


def tags(biomes, ores, world):
    ids = [f"{NS}:{b['id']}" for b in biomes]
    known = {b["id"] for b in biomes}
    out = {f"data/{NS}/tags/worldgen/biome/is_theoria.json": tag(ids),
           f"data/{NS}/tags/worldgen/biome/land.json": tag([f"{NS}:{b['id']}" for b in biomes if b["kind"] == "surface"])}
    for name, (lo, hi) in world["bands"].items():
        members = [f"{NS}:{b['id']}" for b in biomes if (int(b["tier_lo"]), int(b["tier_hi"])) == (lo, hi)]
        out[f"data/{NS}/tags/worldgen/biome/band/{name}.json"] = tag(members)
    for o in ores:
        for v in o["biomes"].split("|"):
            assert v.startswith("#") or v in known, f"ores.csv {o['id']}: bilinmeyen biyom '{v}'"
        out[f"data/{NS}/tags/worldgen/biome/has_ore/{o['id']}.json"] = tag(ore_tag_values(o["biomes"]))
    joins = {"minecraft:is_overworld": list(ids)}
    for b in biomes:
        for j in b["joins"].split("|"):
            if j:
                joins.setdefault(j, []).append(f"{NS}:{b['id']}")
    for j, values in sorted(joins.items()):
        ns, _, path = j.partition(":")
        out[f"data/{ns}/tags/worldgen/biome/{path}.json"] = tag(values)
    return out


# ───────────────────────────────────────────────────────────────────────────────────────── lang
def merge_lang(existing_text, generated):
    current = json.loads(existing_text)
    merged = {k: v for k, v in current.items() if not k.startswith(OWNED_PREFIX)}
    merged.update(generated)
    return dumps(dict(sorted(merged.items())))


# ───────────────────────────────────────────────────────────────────────────────────────── çıktı
def outputs():
    biomes, ores, world = model()
    ids = [b["id"] for b in biomes]
    assert len(ids) == len(set(ids)), "biomes.csv: yinelenen id"
    for b in biomes:
        assert b["profile"] in PROFILES, f"{b['id']}: bilinmeyen profil {b['profile']}"
        assert b["kind"] in ("surface", "cave", "bottom", "ocean"), f"{b['id']}: kind"
        assert b["precip"] in ("rain", "snow", "none"), f"{b['id']}: precip"
    rel = {f"data/{NS}/worldgen/biome/{b['id']}.json": dumps(biome_json(b)) for b in biomes}
    rel[f"data/{NS}/terrablender/region/{world['region']['name']}.json"] = region_json(biomes, world)
    rel.update(tags(biomes, ores, world))
    files = {GEN / p: c for p, c in rel.items()}
    en = {f"biome.{NS}.{b['id']}": b["en"] for b in biomes}
    tr = {f"biome.{NS}.{b['id']}": b["tr"] for b in biomes}
    for name, table in (("en_us", en), ("tr_tr", tr)):
        path = LANG / f"{name}.json"
        files[path] = merge_lang(path.read_text(encoding="utf-8"), table)
    return files


def stale_files(files):
    return [p for p in sorted(GEN.rglob("*")) if p.is_file() and p not in files]


def main(argv):
    if "--coverage" in argv or "--share" in argv:
        biomes, ores, world = model()
        holes, overlaps, n, examples = coverage(biomes, world)
        print(f"Yüzey kutuları: {n} rastgele hedefte delik {holes}, çakışma {overlaps} {examples if holes or overlaps else ''}")
        if "--share" not in argv:
            return 1 if holes or overlaps else 0
        files = [pathlib.Path(a) for a in argv if a.endswith(".csv")] or sorted((ROOT / "mods-src/theoria/build/dev-out/world").glob("climate_*.csv"))
        if not files:
            print("örnek yok: önce ./gradlew runGameTestServer (devtest ClimateProbe) → build/dev-out/world/climate_*.csv")
            return 1
        surface, total, rock, rock_n, under, n_under, rows = share(biomes, world, files)
        print(f"{len(files)} örnek dosyası, {rows} satır; yüzey sütunu {total}  (ÖLÇÜM DEĞİL: yalnız bölge İÇİ pay; denetimli ağırlık Chunky ile)")
        for b in biomes:
            print(f"  {b['id']:18s} yüzey {surface[b['id']] / max(total, 1) * 100:5.1f}%  yeraltı(y-60..60) {under[b['id']] / max(n_under, 1) * 100:5.1f}%  band T{b['tier_lo']}-{b['tier_hi']} {b['kind']}")
        dead = [b["id"] for b in biomes if b["kind"] in ("surface", "ocean") and surface[b["id"]] == 0]
        for o in ores:
            lo, hi = int(o["min_y"]), int(o["max_y"])
            wanted = {v for v in o["biomes"].split("|") if not v.startswith("#")}
            ys = [y for y in rock if lo <= y <= hi]
            tot = sum(rock_n[y] for y in ys)
            hit = sum(rock[y][b] for y in ys for b in wanted)
            avail = tot / max(1, sum(1 for y in ys for _ in range(1))) if ys else 0
            print(f"  cevher {o['id']:10s} y{lo}…{hi}: kaya içindeki {tot} örneğin %{hit / max(tot, 1) * 100:.1f}'i tanımlı Theoria biyomlarında (vanilla etiket yedekleri sayılmaz)")
        print("yüzeyde ÖLÜ biyom: " + (", ".join(dead) if dead else "yok"))
        return 1 if dead or holes or overlaps else 0
    files = outputs()
    changed = [p for p, c in files.items() if not p.exists() or p.read_text(encoding="utf-8") != c]
    orphans = stale_files(files)
    if "--list" in argv:
        print(f"{len(files)} dosya")
        return 0
    if "--check" in argv:
        for p in changed:
            print(f"BAYAT: {p.relative_to(ROOT)} → python3 -I generator/biomes.py")
        for p in orphans:
            print(f"ARTIK: {p.relative_to(ROOT)} → python3 -I generator/biomes.py")
        print("OK" if not changed and not orphans else f"HATA ({len(changed) + len(orphans)})")
        return 1 if changed or orphans else 0
    for p in changed:
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(files[p], encoding="utf-8")
    for p in orphans:
        p.unlink()
    print(f"biomes: {len(files)} dosya, {len(changed)} yazıldı, {len(orphans)} artık silindi")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
