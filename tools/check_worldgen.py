#!/usr/bin/env python3
"""Faz 9b worldgen denetimi. Çalıştır: python3 -I tools/check_worldgen.py [--break]

Denetler (hepsi oyundan bağımsız):
  1. data/biomes.csv şeması: 20 biyom, kimlik/ad/renk/profil/kademe bandı; data/biome_climate.csv: her biyomun kutusu, yüzey kutuları TAM ve AYRIK bölüntü
     (rastgele 120k iklim hedefinde delik 0, çakışma 0 — çakışma = eşitlikte hep aynı biyom kazanır, "ölü biyom" üretir), mağara/dip kutuları yüzey değil.
  2. Üreteçler güncel: biomes.py --check, structures.py --check, gear.py --check (cevher biome modifier'ları).
  3. Biyom JSON'ları: placed feature + carver adları vanilla jar'da var (jar yoksa atlanır), 11 adım, kayıtlı etiketler; TerraBlender bölge JSON'u (eşleme sayısı = kutu × depth noktası,
     parametre aralıkları kodek sınırında, offset 0..1); Theoria biyomları minecraft:is_overworld etiketinde.
  4. Cevher bağı: ores.csv `biomes` kimlikleri biyom listesinde; has_ore etiketi var ve boş değil; biome modifier etiketi gösteriyor; Y aralığı dünya sınırında.
  5. Mob/biyom bandı: açık etiketli (is_overworld dışı) türler katıldığı Theoria biyomunun kademe bandına SLACK içinde (tür.tier ∈ [lo−SLACK, hi+SLACK]).
  6. Yapılar: structures.csv şeması; biyom/etiket başvuruları çözülür; küme aralığı/tuz; NBT dosyaları BAĞIMSIZ okunur: boyut, palet blokları vanilla'da/yerli blokta var, DataVersion;
     Haven: yatak + ocak + sandık, sandık loot tablosu var, mob_spawner/varlık YOK, her doğuş hücresinde blok ışığı ≥ 4 (ışık yayılımı şablon dosyasından yeniden hesaplanır),
     spawn_overrides her doğal kategoride BOŞ (bounding_box full); Zindan: spawner + elit varlık; Kale: boss varlığı mobs.csv'de boss.
  7. Loot tabloları: ayrıştırılır, öğeler var (vanilla/jar + Theoria öğeleri), netherite gear yok, ağırlıklar pozitif, sandık havuzu boş-yalnız değil.
  8. Özellik sırası (Faz 9b kapı çökmesi): her Theoria biyomunun adım listeleri data/worldgen_feature_order.json'daki vanilla-uyumlu sıraya dizili; sıra dosyası vanilla (+`--extra-biomes <jar>`)
     kenarlarına uyuyor; vanilla ∪ ek paket ∪ Theoria için FeatureSorter benzetimi ÇEVRİMSİZ (aksi halde oyun ilk chunk'ta 'Feature order cycle' ile çöker).
`--break`: bozuk kopyalarla denetimin KIRMIZI verdiğini kanıtlar (negatif test).
`--extra-biomes <jar|klasör>` (tekrarlanabilir): ek biyom paketi (ör. Terralith jar'ı) özellik sırası denetimine katılır.
"""
import copy, csv, json, pathlib, re, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "generator"))
import biomes as GB  # noqa: E402
import feature_order as FO  # noqa: E402
import structure_nbt as N  # noqa: E402
import vanilla  # noqa: E402

DATA = ROOT / "data"
GENB = ROOT / "mods-src/theoria/src/generated/biomes"
GENS = ROOT / "mods-src/theoria/src/generated/structures"
GENR = ROOT / "mods-src/theoria/src/generated/resources"
GENM = ROOT / "mods-src/theoria/src/generated/mobs"
GENC = ROOT / "mods-src/theoria/src/generated/catalysts"
SLACK = 3                       # biyom bandı ↔ tür kademesi toleransı (MobZones konum kapısı zaten ±1; bant ekolojik hedef)
NATURAL = ("monster", "creature", "ambient", "axolotls", "underground_water_creature", "water_creature", "water_ambient", "misc")
SPECIAL_BLOCKS = {"water", "lava", "air", "cave_air", "void_air", "bubble_column", "moving_piston", "piston_head"}
MIN_LIGHT = 4


def read_csv(path):
    with open(path, encoding="utf-8", newline="") as f:
        return [{k: v for k, v in r.items() if k is not None} for r in csv.DictReader(f)]


class Report:
    def __init__(self):
        self.errors, self.notes = [], []

    def err(self, msg):
        self.errors.append(msg)

    def note(self, msg):
        self.notes.append(msg)


def jload(path):
    return json.loads(path.read_text(encoding="utf-8"))


# ───────────────────────────────────────────────────────────────────────────────────────── 1. biyom ve iklim şeması
def check_schema(r, biomes, tiles, world):
    ids = [b["id"] for b in biomes]
    if len(biomes) < 20:
        r.err(f"biomes.csv: {len(biomes)} biyom (hedef ≥ 20)")
    if len(set(ids)) != len(ids):
        r.err("biomes.csv: yinelenen id")
    for b in biomes:
        w = f"biomes.csv {b['id']}"
        if not re.fullmatch(r"[a-z][a-z0-9_]*", b["id"]):
            r.err(f"{w}: id biçimi")
        if not b["en"].strip() or not b["tr"].strip():
            r.err(f"{w}: ad boş")
        for k in ("sky", "water", "grass", "foliage", "fog"):
            if b[k] and not re.fullmatch(r"#[0-9a-fA-F]{6}", b[k]):
                r.err(f"{w}: {k} rengi '{b[k]}'")
        if b["kind"] not in ("surface", "cave", "bottom", "ocean") or b["profile"] not in GB.PROFILES or b["precip"] not in ("rain", "snow", "none"):
            r.err(f"{w}: kind/profile/precip")
        lo, hi = int(b["tier_lo"]), int(b["tier_hi"])
        if not 0 <= lo <= hi <= 13:
            r.err(f"{w}: kademe bandı {lo}..{hi}")
        if (lo, hi) not in {tuple(v) for v in world["bands"].values()}:
            r.err(f"{w}: bant ({lo},{hi}) worldgen.json bands içinde yok")
        for j in b["joins"].split("|"):
            if j and not re.fullmatch(r"[a-z_]+:[a-z_/]+", j):
                r.err(f"{w}: joins '{j}'")
    per = {b["id"]: [] for b in biomes}
    for t in tiles:
        if t["biome"] not in per:
            r.err(f"biome_climate.csv: bilinmeyen biyom {t['biome']}")
            continue
        per[t["biome"]].append(t)
        try:
            for k in GB.AXES:
                lo, hi = GB.span(t[k])
                if not -2 <= lo <= hi <= 2:
                    r.err(f"biome_climate.csv {t['biome']}: {k} aralığı {t[k]} kodek sınırı dışında")
            if t["d"] != "surface":
                GB.span(t["d"])
        except (AssertionError, ValueError) as e:
            r.err(f"biome_climate.csv {t['biome']}: {e}")
    kinds = {b["id"]: b["kind"] for b in biomes}
    for bid, ts in per.items():
        if not ts:
            r.err(f"biome_climate.csv: {bid} için iklim kutusu yok")
        for t in ts:
            if (kinds[bid] in ("surface", "ocean")) != (t["d"] == "surface"):
                r.err(f"biome_climate.csv {bid}: kind {kinds[bid]} ↔ depth '{t['d']}' uyumsuz (yüzey biyomu yalnız 'surface', mağara/dip yalnız aralık)")
    holes, overlaps, n, examples = GB.coverage(biomes, dict(world, tiles=tiles))
    if holes or overlaps:
        r.err(f"iklim bölüntüsü: {n} hedefte delik {holes}, çakışma {overlaps} {examples}")
    r.note(f"biyomlar: {len(biomes)} biyom, {len(tiles)} iklim kutusu; yüzey bölüntüsü {n} rastgele hedefte delik {holes}/çakışma {overlaps}")


# ───────────────────────────────────────────────────────────────────────────────────────── 2. üreteçler güncel
def check_fresh(r):
    for script in ("biomes.py", "structures.py", "gear.py"):
        res = subprocess.run([sys.executable, "-I", str(ROOT / "generator" / script), "--check"], capture_output=True, text=True)
        lines = (res.stdout + res.stderr).strip().splitlines()
        if res.returncode != 0:
            r.err(f"generator/{script} --check kırmızı: " + "; ".join(lines[:4]))
    r.note("üreteçler güncel: biomes.py · structures.py · gear.py")


# ───────────────────────────────────────────────────────────────────────────────────────── 3. biyom JSON + bölge
def vanilla_names(prefix):
    return {n[len(prefix):-5] for n in vanilla.names(prefix) if n.endswith(".json")}


def check_biome_files(r, biomes, tiles, world):
    have_jar = vanilla.available()
    placed = vanilla_names("data/minecraft/worldgen/placed_feature/") if have_jar else set()
    carvers = vanilla_names("data/minecraft/worldgen/carver/") if have_jar else set()
    for b in biomes:
        path = GENB / f"data/theoria/worldgen/biome/{b['id']}.json"
        if not path.exists():
            r.err(f"biyom JSON yok: {b['id']}")
            continue
        d = jload(path)
        if len(d["features"]) != 11:
            r.err(f"{b['id']}: {len(d['features'])} özellik adımı (11 olmalı)")
        if have_jar:
            for step in d["features"]:
                for f in step:
                    ns, _, n = f.partition(":")
                    if ns == "minecraft" and n not in placed:
                        r.err(f"{b['id']}: vanilla'da placed feature yok: {f}")
            for c in d["carvers"]:
                if c.partition(":")[2] not in carvers:
                    r.err(f"{b['id']}: carver yok {c}")
        if "minecraft:gameplay/natural_mob_spawns" not in d["attributes"]:
            r.err(f"{b['id']}: natural_mob_spawns yok")
    region = jload(GENB / f"data/theoria/terrablender/region/{world['region']['name']}.json")
    expected = sum(2 if t["d"] == "surface" else 1 for t in tiles)
    if region["type"] != "overworld" or region["weight"] < 1 or len(region["biomeMappings"]) != expected:
        r.err(f"bölge JSON: type/weight/eşleme sayısı ({len(region['biomeMappings'])} ≠ {expected})")
    for m in region["biomeMappings"]:
        p = m["parameters"]
        for k in ("temperature", "humidity", "continentalness", "erosion", "depth", "weirdness"):
            v = p[k] if isinstance(p[k], list) else [p[k], p[k]]
            if not (len(v) == 2 and -2 <= v[0] <= v[1] <= 2):
                r.err(f"bölge JSON: {m['biome']} {k}={p[k]}")
        if not 0 <= p["offset"] <= 1:
            r.err(f"bölge JSON: {m['biome']} offset {p['offset']}")
    tag = jload(GENB / "data/minecraft/tags/worldgen/biome/is_overworld.json")["values"]
    missing = {f"theoria:{b['id']}" for b in biomes} - set(tag)
    if missing:
        r.err(f"minecraft:is_overworld etiketinde yok: {sorted(missing)}")
    r.note(f"biyom JSON: {len(biomes)} dosya, bölge {len(region['biomeMappings'])} eşleme (ağırlık {region['weight']})" + ("" if have_jar else " [vanilla jar yok: feature adları doğrulanmadı]"))


# ───────────────────────────────────────────────────────────────────────────────────────── 3b. özellik sırası (FeatureSorter benzetimi)
EXTRA_BIOMES = []     # --extra-biomes ile doldurulur


def theoria_feature_lists(biomes):
    out = {}
    for b in biomes:
        path = GENB / f"data/theoria/worldgen/biome/{b['id']}.json"
        if path.exists():
            out[f"theoria:{b['id']}"] = jload(path)["features"]
    return out


def check_feature_order(r, biomes, ours=None):
    if not vanilla.available():
        r.note("özellik sırası: vanilla jar yok, atlandı")
        return
    if not FO.ORDER_FILE.exists():
        r.err("data/worldgen_feature_order.json yok → python3 -I generator/feature_order.py --write --extra <Terralith.jar>")
        return
    order = FO.load_order()
    ranks = FO.rank_of(order)
    ours = theoria_feature_lists(biomes) if ours is None else ours
    for name, steps in ours.items():
        for s in FO.STEPS:
            if steps[s] != FO.sort_step(s, steps[s], ranks):
                r.err(f"{name}: adım {s} özellik listesi vanilla-uyumlu sıraya dizili değil (python3 -I generator/biomes.py)")
    base = FO.union_lists(EXTRA_BIOMES)
    bad = FO.violations(order, base)
    if bad:
        r.err(f"data/worldgen_feature_order.json {len(bad)} vanilla/ek-paket kenarına uymuyor (ilk: {bad[0]}) → generator/feature_order.py --write --extra <jar>")
    edges, who = FO.build_edges({**base, **ours})
    cyc = FO.find_cycle(edges)
    if cyc:
        r.err("özellik sırası ÇEVRİMİ (oyun 'Feature order cycle' ile çöker): " + FO.describe_cycle(cyc, who))
    extra = f" + {len(EXTRA_BIOMES)} ek paket" if EXTRA_BIOMES else " (ek biyom paketi verilmedi: Terralith için --extra-biomes <jar>)"
    r.note(f"özellik sırası: vanilla{extra} ∪ {len(ours)} Theoria biyomu, {sum(len(v) for v in order.values())} özellik sıralı, çevrim " + ("VAR" if cyc else "yok"))


# ───────────────────────────────────────────────────────────────────────────────────────── 4. cevher bağı
def check_ores(r, biomes, ores):
    known = {b["id"] for b in biomes}
    for o in ores:
        for v in o["biomes"].split("|"):
            if v and not v.startswith("#") and v not in known:
                r.err(f"ores.csv {o['id']}: bilinmeyen biyom '{v}'")
        if not -64 <= int(o["min_y"]) < int(o["max_y"]) <= 320:
            r.err(f"ores.csv {o['id']}: y aralığı dünya sınırında değil")
        tag = GENB / f"data/theoria/tags/worldgen/biome/has_ore/{o['id']}.json"
        if not tag.exists() or not jload(tag)["values"]:
            r.err(f"has_ore/{o['id']} etiketi yok/boş")
        mod = GENR / f"data/theoria/neoforge/biome_modifier/ore_{o['id']}.json"
        if not mod.exists() or jload(mod)["biomes"] != f"#theoria:has_ore/{o['id']}":
            r.err(f"ore_{o['id']} biome modifier has_ore etiketini göstermiyor")
        # yabani damar (hibrit, docs/10 Q63): özellik + yerleşim + TÜM Overworld biyomlarına biome modifier
        for kind in ("feature", "placed_feature"):
            if not (GENR / f"data/theoria/worldgen/{kind}/ore_{o['id']}_wild.json").exists():
                r.err(f"ore_{o['id']}_wild {kind} dosyası yok (python3 -I generator/gear.py)")
        wmod = GENR / f"data/theoria/neoforge/biome_modifier/ore_{o['id']}_wild.json"
        if not wmod.exists() or jload(wmod)["biomes"] != "#minecraft:is_overworld":
            r.err(f"ore_{o['id']}_wild biome modifier #minecraft:is_overworld göstermiyor")
        if not (o.get("wild_min_y") and -64 <= int(o["wild_min_y"]) < int(o["wild_max_y"]) <= 320 and int(o["wild_size"]) >= 3
                and (int(o["wild_rarity"]) > 0 or int(o["wild_count"]) > 0)):
            r.err(f"ores.csv {o['id']}: yabani damar parametreleri geçersiz")
    r.note(f"cevherler: {len(ores)} satır → has_ore etiketi + biome modifier bağlı; yabani damarlar #minecraft:is_overworld'e bağlı")


# ───────────────────────────────────────────────────────────────────────────────────────── 5. mob / biyom bandı
def check_mob_bands(r, biomes):
    species = read_csv(DATA / "mobs.csv")
    joins = {}
    for b in biomes:
        for j in b["joins"].split("|"):
            if j:
                joins.setdefault("#" + j, []).append(b)
    bad, pairs = [], 0
    for sp in species:
        tier = int(sp["tier"])
        for tag in sp["biomes"].split("|"):
            if tag in joins:
                for b in joins[tag]:
                    pairs += 1
                    if not int(b["tier_lo"]) - SLACK <= tier <= int(b["tier_hi"]) + SLACK:
                        bad.append(f"{sp['id']} (T{tier}, {tag}) ↔ {b['id']} T{b['tier_lo']}-{b['tier_hi']}")
    for line in bad:
        r.err("mob/biyom bandı: " + line)
    r.note(f"mob/biyom bandı: {pairs} açık etiket eşleşmesi, tolerans ±{SLACK}, uyumsuz {len(bad)}")


# ───────────────────────────────────────────────────────────────────────────────────────── 6. yapılar
def item_ids():
    ids = {f"minecraft:{n[len('assets/minecraft/items/'):-5]}" for n in vanilla.names("assets/minecraft/items/") if n.endswith(".json")}
    for base in (GENR, GENM, GENC):
        d = base / "assets/theoria/items"
        if d.is_dir():
            ids |= {f"theoria:{p.stem}" for p in d.glob("*.json")}
    return ids


def block_ids():
    ids = {f"minecraft:{n[len('assets/minecraft/blockstates/'):-5]}" for n in vanilla.names("assets/minecraft/blockstates/") if n.endswith(".json")}
    d = GENR / "assets/theoria/blockstates"
    if d.is_dir():
        ids |= {f"theoria:{p.stem}" for p in d.glob("*.json")}
    return ids


def template_from_nbt(path):
    root = N.nbt_read(path.read_bytes())
    size = root["size"]
    t = N.Template(size[0], size[1], size[2], path.stem)
    palette = []
    for e in root["palette"]:
        props = e.get("properties", {})
        palette.append(N.B(e["id"], **props) if props else e["id"])
    for b in root["blocks"]:
        x, y, z = b["pos"]
        t.cells[(x, y, z)] = (palette[b["state"]], b.get("nbt"))
    t.entities = [(e["blockPos"][0], e["blockPos"][1], e["blockPos"][2], e["nbt"]) for e in root["entities"]]
    return t, root


def check_structures(r, biomes, rows, world, ores):
    ids = {b["id"] for b in biomes}
    species = {s["id"]: s for s in read_csv(DATA / "mobs.csv")}
    blocks, items = block_ids(), item_ids()
    have_jar = vanilla.available()
    loots = {p.stem for p in (GENS / "data/theoria/loot_table/chests").glob("*.json")}
    seen_ids, salts = set(), {}
    for name, s in world["sets"].items():
        if not 0 < s["separation"] < s["spacing"]:
            r.err(f"structure_set {name}: separation < spacing olmalı")
        if s["salt"] in salts:
            r.err(f"structure_set tuzları çakışıyor: {name}/{salts[s['salt']]}")
        salts[s["salt"]] = name
    tiny = 0
    for row in rows:
        w = f"structures.csv {row['id']}"
        if row["id"] in seen_ids:
            r.err(f"{w}: yinelenen id")
        seen_ids.add(row["id"])
        if row["set"] not in world["sets"] or row["kind"] not in ("haven", "ruin", "dungeon", "keep") or row["adapt"] not in ("none", "beard_thin", "beard_box", "bury", "encapsulate"):
            r.err(f"{w}: set/kind/adapt")
        if row["loot"].partition("chests/")[2] not in loots:
            r.err(f"{w}: loot tablosu yok {row['loot']}")
        for v in row["biomes"].split("|"):
            if not v.startswith("#") and v not in ids:
                r.err(f"{w}: bilinmeyen biyom {v}")
            if v.startswith("#theoria:") and v not in ("#theoria:land", "#theoria:is_theoria"):
                r.err(f"{w}: bilinmeyen Theoria etiketi {v}")
        sj = GENS / f"data/theoria/worldgen/structure/{row['id']}.json"
        sd = jload(sj)
        ov = sd["spawn_overrides"]
        if row["kind"] == "haven":
            for cat in NATURAL:
                if cat not in ov or ov[cat]["spawns"] != [] or ov[cat]["bounding_box"] != "full":
                    r.err(f"{w}: spawn_overrides.{cat} boş + full olmalı (Haven mob'suz)")
        else:
            for cat, o in ov.items():
                for sp in o["spawns"]:
                    if sp["type"].partition("theoria:")[2] and sp["type"].partition("theoria:")[2] not in species:
                        r.err(f"{w}: spawn_overrides'ta bilinmeyen tür {sp['type']}")
        for v in row["variants"].split("|"):
            key = f"haven_{row['id'].rsplit('_', 1)[-1]}/{v}" if row["kind"] == "haven" else f"{row['id']}/{v}"
            path = GENS / f"data/theoria/structure/{key}.nbt"
            if not path.exists():
                r.err(f"{w}: şablon yok {key}")
                continue
            t, root = template_from_nbt(path)
            if root["DataVersion"] != N.DATA_VERSION:
                r.err(f"{key}: DataVersion {root['DataVersion']}")
            if row["kind"] == "haven" and (t.w, t.d) != (int(row["w"]), int(row["d"])):
                r.err(f"{key}: boyut {t.w}×{t.d} ≠ csv {row['w']}×{row['d']}")
            for e in root["palette"]:
                name = e["id"]
                if name.partition(":")[2] not in SPECIAL_BLOCKS and have_jar and name not in blocks:
                    r.err(f"{key}: bilinmeyen blok {name}")
            spawners = t.count("spawner")
            mobs_inside = len(t.entities)
            chests = [(pos, nbt) for pos, (s, nbt) in t.cells.items() if N.short(s) in ("chest", "barrel") and nbt]
            for pos, nbt in chests:
                lt = nbt.get("LootTable", "")
                if lt.partition("theoria:chests/")[2] not in loots and not lt.startswith("minecraft:"):
                    r.err(f"{key}: sandık loot tablosu yok {lt}")
            if row["kind"] == "haven":
                beds, furnaces = t.count("_bed"), sum(t.count(n) for n in ("furnace", "smoker"))
                if spawners or mobs_inside:
                    r.err(f"{key}: Haven'da spawner/varlık var ({spawners}/{mobs_inside}) — mob'suz olmalı")
                if beds < 2 or furnaces < 1 or len(chests) < 1:
                    r.err(f"{key}: yatak/ocak/depo eksik (yatak hücresi {beds}, ocak {furnaces}, sandık {len(chests)})")
                level, cell, total = t.darkest()
                if level < MIN_LIGHT:
                    r.err(f"{key}: doğuş hücresi {cell} blok ışığı {level} < {MIN_LIGHT} ({total} hücre) — mob doğabilir")
                tiny = max(tiny, total)
            if row["kind"] == "dungeon":
                if spawners < 4 or not any(e[3]["id"] == "theoria:stone_guardian" for e in t.entities):
                    r.err(f"{key}: Zindan'da ≥ 4 spawner ve elit (stone_guardian) bekleniyor")
            if row["kind"] == "keep":
                boss = [e[3]["id"].partition("theoria:")[2] for e in t.entities]
                if len(boss) != 1 or species.get(boss[0], {}).get("cls") != "boss":
                    r.err(f"{key}: Kale'de tam 1 boss varlığı bekleniyor ({boss})")
            for e in t.entities:
                eid = e[3]["id"].partition("theoria:")[2]
                if e[3]["id"].startswith("theoria:") and eid not in species:
                    r.err(f"{key}: bilinmeyen varlık {e[3]['id']}")
            for pos, (s, nbt) in t.cells.items():
                if N.short(s) == "spawner":
                    ent = nbt["SpawnData"]["entity"]["id"]
                    if ent.startswith("theoria:") and ent.partition("theoria:")[2] not in species:
                        r.err(f"{key}: spawner bilinmeyen tür {ent}")
    r.note(f"yapılar: {len(rows)} yapı, {len(world['sets'])} küme; Haven şablonları bağımsız okundu (yatak/ocak/sandık, spawner 0, ışık ≥ {MIN_LIGHT})")


# ───────────────────────────────────────────────────────────────────────────────────────── 7. loot
def check_loot(r):
    items = item_ids()
    have_jar = vanilla.available()
    n = 0
    for path in sorted((GENS / "data/theoria/loot_table/chests").glob("*.json")):
        d = jload(path)
        if d["type"] != "minecraft:chest" or not d["pools"]:
            r.err(f"{path.name}: tür/havuz")
        for pool in d["pools"]:
            real = [e for e in pool["entries"] if e["type"] == "minecraft:item"]
            if not real:
                r.err(f"{path.name}: yalnız boş girişli havuz")
            for e in pool["entries"]:
                n += 1
                if e.get("weight", 1) <= 0:
                    r.err(f"{path.name}: ağırlık ≤ 0")
                if e["type"] == "minecraft:item":
                    if "netherite" in e["name"] and ("sword" in e["name"] or "helmet" in e["name"] or "chestplate" in e["name"] or "leggings" in e["name"] or "boots" in e["name"] or "axe" in e["name"]):
                        r.err(f"{path.name}: netherite gear {e['name']}")
                    if (have_jar or e["name"].startswith("theoria:")) and e["name"] not in items:
                        r.err(f"{path.name}: öğe yok {e['name']}")
    r.note(f"loot: {n} giriş, öğeler çözüldü")


def run_all(biomes, tiles, world, ores, rows, fresh=True):
    r = Report()
    check_schema(r, biomes, tiles, world)
    if fresh:
        check_fresh(r)
        check_biome_files(r, biomes, tiles, world)
        check_feature_order(r, biomes)
        check_ores(r, biomes, ores)
        check_mob_bands(r, biomes)
        check_structures(r, biomes, rows, world, ores)
        check_loot(r)
    return r


def reversed_veg(biomes):
    """Negatif test girdisi: her biyomun bitki (adım 9) listesi TERSİNE — vanilla kenarlarıyla çelişmeli."""
    out = theoria_feature_lists(biomes)
    for steps in out.values():
        steps[9] = list(reversed(steps[9]))
    return out


def break_tests(biomes, tiles, world, ores, rows):
    """Negatif testler: bozulmuş girdiyle ilgili denetim KIRMIZI vermeli."""
    def red(label, mutate, only):
        b, t, w, o, s = copy.deepcopy((biomes, tiles, world, ores, rows))
        mutate(b, t, w, o, s)
        rep = Report()
        only(rep, b, t, w, o, s)
        ok = bool(rep.errors)
        print(f"  {'OK ' if ok else 'YEŞİL (kötü!)'} {label}")
        return ok
    results = [
        red("çakışan yüzey kutusu", lambda b, t, w, o, s: t.append(dict(t[14], biome="iron_uplands")), lambda rep, b, t, w, o, s: check_schema(rep, b, t, w)),
        red("delikli bölüntü (kutu silindi)", lambda b, t, w, o, s: t.__delitem__(slice(3, 5)), lambda rep, b, t, w, o, s: check_schema(rep, b, t, w)),
        red("iklim kutusuz biyom", lambda b, t, w, o, s: t.__setitem__(slice(None), [x for x in t if x["biome"] != "rune_vale"]), lambda rep, b, t, w, o, s: check_schema(rep, b, t, w)),
        red("kind↔depth uyumsuz", lambda b, t, w, o, s: [x.update(d="0.2:0.9") for x in t if x["biome"] == "tin_marsh"], lambda rep, b, t, w, o, s: check_schema(rep, b, t, w)),
        red("kademe bandı bozuk", lambda b, t, w, o, s: b[0].update(tier_lo="9", tier_hi="2"), lambda rep, b, t, w, o, s: check_schema(rep, b, t, w)),
        red("yabani damar yükseklik aralığı ters", lambda b, t, w, o, s: o[2].update(wild_min_y="200", wild_max_y="100"), lambda rep, b, t, w, o, s: check_ores(rep, b, o)),
        red("cevher bilinmeyen biyom", lambda b, t, w, o, s: o[0].update(biomes="yok_biyom"), lambda rep, b, t, w, o, s: check_ores(rep, b, o)),
        red("özellik sırası çevrimi (adım 9 tersine)", lambda b, t, w, o, s: None, lambda rep, b, t, w, o, s: check_feature_order(rep, b, ours=reversed_veg(b))),
        red("küme separation ≥ spacing", lambda b, t, w, o, s: w["sets"]["havens"].update(separation=30), lambda rep, b, t, w, o, s: check_structures(rep, b, s, w, o)),
    ]
    # NBT düzeyinde: Haven şablonuna spawner koy → denetim kırmızı olmalı
    src = GENS / "data/theoria/structure/haven_s/oak.nbt"
    tpl, root = template_from_nbt(src)
    tpl.put(3, 4, 3, "minecraft:spawner", {"id": "minecraft:mob_spawner", "SpawnData": {"entity": {"id": "minecraft:zombie"}}})
    dark = N.Template(8, 6, 8, "dark")
    dark.fill(0, 0, 0, 7, 0, 7, N.B("grass_block"))
    dark.fill(0, 1, 0, 7, 4, 7, N.AIR)
    for name, t in (("Haven'da spawner", tpl), ("karanlık Haven (ışık 0)", dark)):
        level, cell, total = t.darkest()
        bad = t.count("spawner") > 0 or level < MIN_LIGHT
        print(f"  {'OK ' if bad else 'YEŞİL (kötü!)'} {name}")
        results.append(bad)
    return all(results), len(results)


def main(argv):
    EXTRA_BIOMES.extend(argv[i + 1] for i, a in enumerate(argv) if a == "--extra-biomes" and i + 1 < len(argv))
    biomes = read_csv(DATA / "biomes.csv")
    tiles = read_csv(DATA / "biome_climate.csv")
    world = jload(DATA / "worldgen.json")
    ores = read_csv(DATA / "ores.csv")
    rows = read_csv(DATA / "structures.csv")
    if "--break" in argv:
        ok, n = break_tests(biomes, tiles, world, ores, rows)
        print(f"negatif testler: {'hepsi kırmızı verdi' if ok else 'BAZILARI YEŞİL KALDI'} ({n})")
        return 0 if ok else 1
    r = run_all(biomes, tiles, world, ores, rows)
    for n in r.notes:
        print("  " + n)
    if r.errors:
        print(f"HATA ({len(r.errors)}):")
        for e in r.errors:
            print("  - " + e)
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
