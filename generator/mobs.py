#!/usr/bin/env python3
"""Mob üreteci (Faz 8): data/mobs.csv + mob_drops.csv + mob_rigs.json (+ data/balance/mob_*.csv) → oyunun okuduğu her şey. Elle düzenlenmez; git'e girer.

    python3 -I generator/mobs.py            # yaz (artık dosyaları siler)
    python3 -I generator/mobs.py --check    # yazmadan: depodakiler üretilecekle aynı mı (PNG'ler piksel düzeyinde); fark varsa çıkış 1
    python3 -I generator/mobs.py --list     # üretilen dosya sayıları

ÇIKTILAR (mods-src/theoria/src/generated/mobs/, tümü bu üretecin malı):
  theoria/mobs.json                       jar'a gömülü tür tablosu: Java kayıt (EntityType) + nitelikler + spawn kuralları bunu okur (kurucu datapack'ten önce çalışır, ADR-003 Karar 2)
  theoria/mob_rigs.json                   derlenmiş iskeletler (UV yerleşimi + doku boyutu): istemci modeli bunu okur
  assets/theoria/textures/entity/<id>.png tür dokuları (tek tint'li, iskelet UV'sine boyanmış)
  assets/theoria/{items,models/item,textures/item}/   spawn yumurtası + Kalp Kristali
  data/theoria/loot_table/entities/<id>.json          tür başına drop tablosu (data/mob_drops.csv)
  data/theoria/neoforge/biome_modifier/spawn_<id>.json + tags/worldgen/biome/spawn/<id>.json   doğal spawn (biyom etiketi + ağırlık + grup)
  data/theoria/tags/entity_type/{species,bosses}.json, data/minecraft/tags/entity_type/undead.json
Lang: src/main/resources/assets/theoria/lang/{en_us,tr_tr}.json içinde entity.theoria.* / theoria.mob.* / *_spawn_egg / heart_crystal / itemGroup.theoria.mobs anahtarları birleştirilir.
"""
import json, math, pathlib, shutil, sys, zlib

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "tools/balance"))
import pngio  # noqa: E402
import mobs as B  # noqa: E402  (tools/balance/mobs.py)
import tiers as M  # noqa: E402

ROOT = HERE.parent
GEN = ROOT / "mods-src/theoria/src/generated/mobs"
LANG = ROOT / "mods-src/theoria/src/main/resources/assets/theoria/lang"
OWNED_PREFIX = ("entity.theoria.", "theoria.mob.", "itemGroup.theoria.mobs", "item.theoria.heart_crystal")
EGG_SUFFIX = "_spawn_egg"

# Kalp Kristali (boss ödülü) ve sınıf adları
CLASS_NAMES = {"weak": ("Weak", "Zayıf"), "normal": ("Normal", "Normal"), "strong": ("Strong", "Güçlü"), "elite": ("Elite", "Elit"), "boss": ("Boss", "Boss")}
FAMILY_NAMES = {"swamp": ("Swamp", "Bataklık"), "wild": ("Wild", "Yaban"), "underground": ("Underground", "Yeraltı"), "mountain": ("Mountain", "Dağ"),
                "ember": ("Ember", "Alev/Kül"), "frost": ("Frost", "Buz"), "shadow": ("Shadow", "Gölge"), "ancient": ("Ancient", "Kadim")}


def dumps(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


def ceil_int(x):
    return int(math.ceil(x - 1e-9))


# ──────────────────────────────────────────────────────────────────────────────────────────────── iskelet derleme
def compile_rigs():
    """Kutulara UV (vanilla kutu açılımı: 2·(w+d) × (d+h)) yerleştirir (raf paketleme), doku boyutunu seçer."""
    raw = json.loads(B.RIGS_JSON.read_text(encoding="utf-8"))
    out = {}
    for name, rig in raw.items():
        if name.startswith("_"):
            continue
        boxes = []
        for pi, part in enumerate(rig["parts"]):
            for bi, box in enumerate(part["boxes"]):
                _, _, _, w, h, d = box["b"]
                assert all(float(v).is_integer() for v in (w, h, d)), f"{name}.{part['name']}: kutu boyutları tam sayı olmalı"
                boxes.append((pi, bi, 2 * (int(w) + int(d)), int(d) + int(h)))
        placement = None
        for tex_w in (32, 64, 128, 256):
            x = y = row_h = 0
            pos = {}
            for pi, bi, bw, bh in sorted(boxes, key=lambda b: (-b[3], -b[2], b[0], b[1])):
                if bw > tex_w:
                    break
                if x + bw > tex_w:
                    x, y, row_h = 0, y + row_h, 0
                pos[(pi, bi)] = (x, y)
                x += bw
                row_h = max(row_h, bh)
            else:
                tex_h = ((y + row_h) + 7) // 8 * 8
                if tex_h <= tex_w * 2:
                    placement = (tex_w, tex_h, pos)
                    break
        assert placement, f"rig {name}: doku sığmadı"
        tex_w, tex_h, pos = placement
        parts = []
        for pi, part in enumerate(rig["parts"]):
            p = {k: v for k, v in part.items() if k != "boxes"}
            p["boxes"] = [dict(box, uv=list(pos[(pi, bi)])) for bi, box in enumerate(part["boxes"])]
            parts.append(p)
        out[name] = dict(tex=[tex_w, tex_h], parts=parts)
    return out


# ──────────────────────────────────────────────────────────────────────────────────────────────── doku boyama
def hex_rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def mix(a, b, t):
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def palette(sp):
    base, accent = hex_rgb(sp["color"]), hex_rgb(sp["accent"])
    return {"base": base, "accent": accent, "dark": mix(base, (10, 10, 14), 0.45), "glow": mix(accent, (255, 255, 255), 0.55)}


def noise(key, x, y):
    return (zlib.crc32(f"{key}:{x}:{y}".encode()) % 1000) / 1000.0


def clamp(v):
    return max(0, min(255, int(round(v))))


def paint(rig, sp):
    tex_w, tex_h = rig["tex"]
    px = [[(0, 0, 0, 0)] * tex_w for _ in range(tex_h)]
    pal = palette(sp)
    key = sp["id"]
    for part in rig["parts"]:
        for box in part["boxes"]:
            _, _, _, w, h, d = box["b"]
            w, h, d = int(w), int(h), int(d)
            u, v = box["uv"]
            color = pal[box["tint"]]
            faces = {"top": (u + d, v, w, d, 1.12), "bottom": (u + d + w, v, w, d, 0.70), "right": (u, v + d, d, h, 0.92),
                     "front": (u + d, v + d, w, h, 1.0), "left": (u + d + w, v + d, d, h, 0.92), "back": (u + 2 * d + w, v + d, w, h, 0.84)}
            for _, (fx, fy, fw, fh, shade) in faces.items():
                for yy in range(fh):
                    for xx in range(fw):
                        n = noise(key, fx + xx, fy + yy)
                        c = color
                        if box["tint"] == "base" and n > 0.94:      # birkaç vurgu lekesi
                            c = mix(color, pal["accent"], 0.55)
                        elif box["tint"] == "dark" and n > 0.9:
                            c = mix(color, pal["base"], 0.35)
                        f = shade * (0.93 + 0.14 * noise(key + "n", fx + xx, fy + yy))
                        px[fy + yy][fx + xx] = (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), 255)
            if box.get("face") == "eyes":
                eye = pal["dark"] if box["tint"] == "glow" else pal["glow"]
                ew = 1 if w < 6 else 2
                ey = max(0, round(h * 0.38))
                for ex in (max(0, round(w * 0.2)), w - ew - max(0, round(w * 0.2))):
                    for dx in range(ew):
                        for dy in range(2 if h >= 8 else 1):
                            if 0 <= ex + dx < w and 0 <= ey + dy < h:
                                px[v + d + ey + dy][u + d + ex + dx] = (*eye, 255)
    return px


# ──────────────────────────────────────────────────────────────────────────────────────────────── 16×16 simgeler
EGG_ROWS = [4, 6, 8, 10, 10, 12, 12, 12, 12, 12, 10, 10, 8, 6, 4]   # yumurta silueti (satır genişlikleri), 15 satır


def egg_png(sp):
    base, accent = hex_rgb(sp["color"]), hex_rgb(sp["accent"])
    rows = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for r, w in enumerate(EGG_ROWS):
        x0 = (16 - w) // 2
        for i in range(w):
            x = x0 + i
            edge = i in (0, w - 1) or r in (0, len(EGG_ROWS) - 1)
            spot = noise("egg" + sp["id"], x, r) > 0.82
            c = mix(base, accent, 0.9) if spot else base
            f = 0.62 if edge else (0.86 if i >= w - 2 else 1.0)
            rows[r + 1][x] = (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), 255)
    return pngio.encode(rows)


HEART = ["..XX..XX..", ".XXXXXXXX.", "XXHHXXXXXX", "XXHXXXXXXX", "XXXXXXXXXX", ".XXXXXXXX.", "..XXXXXX..", "...XXXX...", "....XX...."]


def heart_png():
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for r, line in enumerate(HEART):
        for c, ch in enumerate(line):
            if ch == ".":
                continue
            x, y = c + 3, r + 3
            px[y][x] = (255, 232, 235, 255) if ch == "H" else (214, 38, 64, 255) if (r + c) % 5 else (186, 24, 52, 255)
    for y in range(16):                          # koyu çerçeve
        for x in range(16):
            if px[y][x][3] == 0 and any(0 <= y + dy < 16 and 0 <= x + dx < 16 and px[y + dy][x + dx][3] == 255 and px[y + dy][x + dx][0] > 100
                                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[y][x] = (92, 10, 28, 255)
    return pngio.encode(px)


# ──────────────────────────────────────────────────────────────────────────────────────────────── tablolar
def species_rows():
    rows = B.species()
    drops = {}
    for d in B.read_csv(B.DROPS_CSV):
        drops.setdefault(d["entity"], []).append(dict(item=d["item"], chance=float(d["chance"]), min=int(d["min"]), max=int(d["max"]), looting=d["looting"] == "1"))
    out = []
    for r in rows:
        spawn = [b for b in r["biomes"].split("|") if b]
        out.append(dict(
            id=r["id"], family=r["family"], archetype=r["archetype"], rig=r["rig"], tier=int(r["tier"]), cls=r["cls"], boss=r["cls"] == "boss",
            hp=float(r["hp"]), dmg=float(r["dmg"]), armor=float(r["armor"]), speed=float(r["speed"]), follow=float(r["follow"]), kb_res=float(r["kb_res"]),
            scale=float(r["scale"]), width=float(r["width"]), height=float(r["height"]), color=r["color"], accent=r["accent"],
            biomes=spawn, min_y=int(r["min_y"]), max_y=int(r["max_y"]), weight=int(float(r["weight"])), group_min=int(r["group_min"]), group_max=int(r["group_max"]),
            params=B.params(r), xp=B.XP_BASE[r["cls"]], drops=drops.get(r["id"], []), en=r["en"], tr=r["tr"]))
    unknown = set(drops) - {r["id"] for r in out}
    assert not unknown, f"mob_drops.csv: tanımsız tür {sorted(unknown)}"
    return out


def mobs_json(species):
    return dumps(dict(species=species, classes=list(B.CLASSES)))


def loot_table(sp):
    """26.3 biçimi (generator/gear_data.py ile aynı): havuzda tekil `condition`, girdide `modifier`, işlev adı `type`."""
    pools = []
    for d in sp["drops"]:
        count = {"type": "minecraft:uniform", "min": d["min"], "max": d["max"]} if d["min"] != d["max"] else {"type": "minecraft:constant", "value": d["min"]}
        modifiers = [{"type": "minecraft:set_count", "count": count}]
        if d["looting"]:
            modifiers.append({"type": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting", "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0}})
        pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": d["item"], "modifier": modifiers}]}
        if d["chance"] < 1.0:
            pool["condition"] = {"type": "minecraft:random_chance", "chance": d["chance"]}
        pools.append(pool)
    return dumps({"type": "minecraft:entity", "random_sequence": f"theoria:entities/{sp['id']}", "pools": pools})


def item_definition(model):
    return dumps({"model": {"type": "minecraft:model", "model": model}})


def flat_model(texture):
    return dumps({"parent": "minecraft:item/generated", "textures": {"layer0": texture}})


def lang(species):
    en, tr = {}, {}
    for sp in species:
        en[f"entity.theoria.{sp['id']}"], tr[f"entity.theoria.{sp['id']}"] = sp["en"], sp["tr"]
        en[f"item.theoria.{sp['id']}{EGG_SUFFIX}"], tr[f"item.theoria.{sp['id']}{EGG_SUFFIX}"] = f"{sp['en']} Spawn Egg", f"{sp['tr']} Doğurma Yumurtası"
    en["item.theoria.heart_crystal"], tr["item.theoria.heart_crystal"] = "Heart Crystal", "Kalp Kristali"
    en["itemGroup.theoria.mobs"], tr["itemGroup.theoria.mobs"] = "Theoria: Mobs", "Theoria: Mob'lar"
    for cls, (a, b) in CLASS_NAMES.items():
        en[f"theoria.mob.cls.{cls}"], tr[f"theoria.mob.cls.{cls}"] = a, b
    for fam, (a, b) in FAMILY_NAMES.items():
        en[f"theoria.mob.family.{fam}"], tr[f"theoria.mob.family.{fam}"] = a, b
    ui = {"tier": ("Tier %s · %s · %s", "Kademe %s · %s · %s"), "drops": ("Drops:", "Düşürür:"), "drop": ("  %s (%s%%)", "  %s (%%%s)"),
          "heart.used": ("Max health +%s (total +%s)", "Azami can +%s (toplam +%s)"), "heart.full": ("You cannot take any more Heart Crystals.", "Daha fazla Kalp Kristali alamazsın."),
          "heart.tooltip": ("Right-click: permanently +%s max health (up to %s crystals)", "Sağ tık: kalıcı olarak +%s azami can (en çok %s kristal)"),
          "boss.phase": ("%s grows enraged!", "%s öfkeleniyor!"), "boss.adds": ("%s calls for help!", "%s yardım çağırıyor!")}
    for k, (a, b) in ui.items():
        en[f"theoria.mob.{k}"], tr[f"theoria.mob.{k}"] = a, b
    return en, tr


def merge_lang(existing_text, generated):
    current = json.loads(existing_text)
    merged = {k: v for k, v in current.items() if not (k.startswith(OWNED_PREFIX) or (k.startswith("item.theoria.") and k.endswith(EGG_SUFFIX)))}
    merged.update(generated)
    return dumps(dict(sorted(merged.items())))   # gear/catalysts ile aynı kanonik sıra


# ──────────────────────────────────────────────────────────────────────────────────────────────── çıktı
def outputs():
    species = species_rows()
    rigs = compile_rigs()
    files = {}
    rel = {"theoria/mobs.json": mobs_json(species), "theoria/mob_rigs.json": dumps(rigs)}
    for sp in species:
        rel[f"assets/theoria/textures/entity/{sp['id']}.png"] = pngio.encode(paint(rigs[sp["rig"]], sp))
        egg = f"{sp['id']}{EGG_SUFFIX}"
        rel[f"assets/theoria/items/{egg}.json"] = item_definition(f"theoria:item/{egg}")
        rel[f"assets/theoria/models/item/{egg}.json"] = flat_model(f"theoria:item/{egg}")
        rel[f"assets/theoria/textures/item/{egg}.png"] = egg_png(sp)
        rel[f"data/theoria/loot_table/entities/{sp['id']}.json"] = loot_table(sp)
        if sp["biomes"]:
            rel[f"data/theoria/tags/worldgen/biome/spawn/{sp['id']}.json"] = dumps({"replace": False, "values": sp["biomes"]})
            count = sp["group_min"] if sp["group_min"] == sp["group_max"] else {"type": "minecraft:uniform", "min_inclusive": sp["group_min"], "max_inclusive": sp["group_max"]}
            # 26.3: spawners is a weighted list whose entries are flat {type, count, weight} (MobSpawnSettings is an environment attribute now)
            rel[f"data/theoria/neoforge/biome_modifier/spawn_{sp['id']}.json"] = dumps({
                "type": "neoforge:add_spawns", "biomes": f"#theoria:spawn/{sp['id']}",
                "spawners": [{"type": f"theoria:{sp['id']}", "count": count, "weight": sp["weight"]}]})
    rel["assets/theoria/items/heart_crystal.json"] = item_definition("theoria:item/heart_crystal")
    rel["assets/theoria/models/item/heart_crystal.json"] = flat_model("theoria:item/heart_crystal")
    rel["assets/theoria/textures/item/heart_crystal.png"] = heart_png()
    rel["data/theoria/tags/entity_type/species.json"] = dumps({"replace": False, "values": [f"theoria:{s['id']}" for s in species]})
    rel["data/theoria/tags/entity_type/bosses.json"] = dumps({"replace": False, "values": [f"theoria:{s['id']}" for s in species if s["boss"]]})
    rel["data/minecraft/tags/entity_type/undead.json"] = dumps({"replace": False, "values": ["theoria:pale_wraith", "theoria:shade_seep"]})
    files.update({GEN / p: c for p, c in rel.items()})
    en, tr = lang(species)
    for name, table in (("en_us", en), ("tr_tr", tr)):
        path = LANG / f"{name}.json"
        files[path] = merge_lang(path.read_text(encoding="utf-8"), table)
    return files


def differs(path, content):
    if not path.exists():
        return True
    if isinstance(content, bytes):
        return pngio.decode(path.read_bytes())[2] != pngio.decode(content)[2]
    return path.read_text(encoding="utf-8") != content


def stale_files(files):
    """Bu üretecin dizininde olup üretilmeyen (artık) dosyalar."""
    return [p for p in sorted(GEN.rglob("*")) if p.is_file() and p not in files]


def main(argv):
    files = outputs()
    changed = [p for p, c in files.items() if differs(p, c)]
    orphans = stale_files(files)
    if "--list" in argv:
        kinds = {}
        for p in files:
            kinds[p.suffix] = kinds.get(p.suffix, 0) + 1
        print(f"{len(files)} dosya: " + ", ".join(f"{k} {v}" for k, v in sorted(kinds.items())))
        return 0
    if "--check" in argv:
        for p in changed:
            print(f"BAYAT: {p.relative_to(ROOT)} → python3 -I generator/mobs.py")
        for p in orphans:
            print(f"ARTIK: {p.relative_to(ROOT)} → python3 -I generator/mobs.py")
        print("OK" if not changed and not orphans else f"HATA ({len(changed) + len(orphans)})")
        return 1 if changed or orphans else 0
    for p in changed:
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(files[p]) if isinstance(files[p], bytes) else p.write_text(files[p], encoding="utf-8")
    for p in orphans:
        p.unlink()
    print(f"mobs: {len(files)} dosya, {len(changed)} yazıldı, {len(orphans)} artık silindi")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
