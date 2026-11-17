"""Gear içerik modeli (Faz 6): CSV'leri okur, malzeme/cevher/alaşım modelini kurar. gear*.py bunu paylaşır.

Sayılar buradan ÜRETİLMEZ: zırh puanı/sertlik/dayanıklılık data/balance/tiers.csv'den, alet hasarı/hızı/kazı hızı tools.csv'den,
kazı kapıları ores.csv'den gelir (hepsi tools/balance/tiers.py'nin çıktısı). materials.csv/ores.csv/alloys.csv/drops.csv yalnız
niteliktir: id, ad, renk, tarif girdisi, büyü yeteneği, yerleşim.
"""
import csv
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
PIECES = ("helmet", "chestplate", "leggings", "boots")
TOOL_KINDS = ("sword", "axe", "pickaxe", "shovel", "hoe", "spear")
UNIT = {"helmet": 11, "chestplate": 16, "leggings": 15, "boots": 13}     # vanilla ArmorType birim dayanıklılığı
PIECE_TR = {"helmet": "Kask", "chestplate": "Göğüslük", "leggings": "Pantolon", "boots": "Bot",
            "sword": "Kılıç", "axe": "Balta", "pickaxe": "Kazma", "shovel": "Kürek", "hoe": "Çapa", "spear": "Mızrak"}
PIECE_EN = {k: k.capitalize() for k in PIECES + TOOL_KINDS}
RECIPE_PATTERN = {  # kategori "equipment": X = malzeme, # = sopa
    "helmet": ["XXX", "X X"], "chestplate": ["X X", "XXX", "XXX"], "leggings": ["XXX", "X X", "X X"], "boots": ["X X", "X X"],
    "sword": ["X", "X", "#"], "pickaxe": ["XXX", " # ", " # "], "axe": ["XX", "X#", " #"], "shovel": ["X", "#", "#"],
    "hoe": ["XX", " #", " #"], "spear": ["  X", " # ", "#  "]}


def read_csv(path):
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def item_ref(namespaced):
    return namespaced if ":" in namespaced else "minecraft:" + namespaced


def build():
    tiers = {r["id"]: r for r in read_csv(DATA / "balance/tiers.csv")}
    tools = {}
    for r in read_csv(DATA / "balance/tools.csv"):
        tools.setdefault(r["tool"], {"idx": int(r["idx"]), "kinds": {}})["kinds"][r["kind"]] = r
    gates = read_csv(DATA / "balance/ores.csv")
    ores = {r["id"]: r for r in read_csv(DATA / "ores.csv")}
    materials = []
    for r in read_csv(DATA / "materials.csv"):
        m = dict(id=r["id"], prefix=r["item_prefix"], vanilla=r["vanilla"] == "1", armor=r["armor"] == "1", tools=r["tools"] == "1",
                 en=r["en"], tr=r["tr"], color=r["color"], ingredient=r["ingredient"], enchantability=int(r["enchantability"]),
                 equip_sound=r["equip_sound"], traits={}, tier=None, tool=None)
        for part in filter(None, r["traits"].split(";")):
            key, value = part.split("=")
            m["traits"][key] = float(value)
        if m["armor"]:
            m["tier"] = tiers[m["id"]]
        if m["tools"]:
            m["tool"] = tools[m["id"]]
        if not m["vanilla"] and not m["color"]:
            raise SystemExit(f"materials.csv: {m['id']} yeni malzeme, renk gerekli")
        materials.append(m)
    alloys = []
    for r in read_csv(DATA / "alloys.csv"):
        ingredients = []
        for part in r["ingredients"].split():
            name, _, n = part.partition("*")
            ingredients += [item_ref(name)] * int(n or 1)
        alloys.append(dict(id=r["id"], result=item_ref(r["result"]), count=int(r["count"]), ingredients=ingredients))
    drops = [dict(r) for r in read_csv(DATA / "drops.csv")]
    return dict(materials=materials, tiers=tiers, tools=tools, gates=gates, ores=ores, alloys=alloys, drops=drops)


def item_id(m, piece):
    """Parça item kimliği: vanilla → minecraft:<önek>_<parça> (golden/wooden), yeni → theoria:<id>_<parça>."""
    return ("minecraft:" if m["vanilla"] else "theoria:") + f"{m['prefix']}_{piece}"


def local_ingredients(model):
    """theoria: ad alanındaki malzeme girdileri (külçe/mücevher) → [(malzeme, kısa ad)]."""
    return [(m, m["ingredient"].split(":", 1)[1]) for m in model["materials"] if m["ingredient"].startswith("theoria:")]


def raw_items(model):
    """Cevher damlaları: ores.csv `drop` (raw_*) — mücevher düşüren cevher (lonsdaleit) ham madde üretmez."""
    return sorted({o["drop"] for o in model["ores"].values() if o["drop"].startswith("raw_")})


def ore_blocks(model):
    """[(cevher satırı, blok kısa adı, deepslate mi)] — ores.csv `variants` alanından."""
    out = []
    for o in model["ores"].values():
        variants = o["variants"].split("+")
        if "stone" in variants:
            out.append((o, f"{o['id']}_ore", False))
        if "deepslate" in variants:
            out.append((o, f"deepslate_{o['id']}_ore", True))
    return out


def storage_blocks(model):
    return [(m, f"{short.removesuffix('_ingot')}_block") for m, short in local_ingredients(model)]
