"""Gear üreteci, kaynak paketi tarafı (Faz 6): gear.json (jar'a gömülü tablo), lang, item/blok modelleri, equipment asset'leri, dokular.

Her fonksiyon {yol: içerik} döner; yol `mods-src/theoria/src/generated/resources/` köküne göredir (gear.py yazar/denetler).
"""
import json

import gear_art
import gear_model as G
import tiers

NETHERITE_GEAR = [f"minecraft:netherite_{p}" for p in G.PIECES + ("sword", "axe", "pickaxe", "shovel", "hoe", "spear")] + [
    "minecraft:netherite_horse_armor", "minecraft:netherite_nautilus_armor", "minecraft:netherite_upgrade_smithing_template"]


def dumps(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


def num(x):
    """JSON'a girecek sayı: 11.0 yerine 11 (tam sayıysa)."""
    x = float(x)
    return int(x) if x == int(x) else x


# ------------------------------------------------------------------------------------------------ gear.json
def armor_entry(m):
    t = m["tier"]
    adur = int(t["adur"])
    return dict(
        points={p: int(t[{"helmet": "helm", "chestplate": "chest", "leggings": "legs", "boots": "boots"}[p]]) for p in G.PIECES},
        toughness=num(t["tough"]),
        durability={p: int(adur * G.UNIT[p] / G.UNIT["chestplate"] + 0.5) for p in G.PIECES},   # yarım yukarı (Java Math.round ile aynı)
        speed=m["traits"].get("speed", 0.0), knockback=m["traits"].get("kb", 0.0))


def tool_entry(m, spears):
    t = m["tool"]
    kinds = {k: dict(damage=num(r["damage"]), speed=num(r["speed"])) for k, r in t["kinds"].items()}
    pick = t["kinds"]["pickaxe"]
    return dict(idx=t["idx"], durability=int(pick["durability"]), mining_speed=num(pick["mining_speed"]), kinds=kinds, spear=list(spears[m["id"]]))


def resolved_hardness(base, block_id, siblings=()):
    """Çözümlenmiş blok sertliği. Deepslate varyantı, aynı cevherin taş varyantı da varsa vanilla oranıyla (x1,5) sertleşir (iron/deepslate_iron);
    yalnız deepslate'te bulunan cevherde (kobalt, mitril, platin) tablodaki değer bloğun kendisinindir. base None = vanilla sertliği korunur."""
    if base is None:
        return None
    name = block_id.split(":")[1]
    stone_twin = block_id.split(":")[0] + ":" + name.removeprefix("deepslate_")
    deep = name.startswith("deepslate_") and stone_twin in siblings
    return round(base * (tiers.DEEPSLATE_HARDNESS if deep else 1.0), 2)


def block_entries(model):
    gates = {b: (float(g["hardness"]), g["blocks"].split()) for g in model["gates"] if g["hardness"] for b in g["blocks"].split()}
    out = []
    for o, name, deep in G.ore_blocks(model):
        base, siblings = gates[f"theoria:{name}"]
        hardness = resolved_hardness(base, f"theoria:{name}", siblings)
        out.append(dict(id=name, kind="ore", hardness=hardness, resistance=hardness, xp_min=int(o["xp_min"]), xp_max=int(o["xp_max"]), ore=o["id"]))
    for m, name in G.storage_blocks(model):
        out.append(dict(id=name, kind="storage", hardness=5.0, resistance=6.0, material=m["id"]))
    return out


def item_entries(model):
    names = [short for _, short in G.local_ingredients(model)] + G.raw_items(model) + ["carbon_dust"]
    return [dict(id=n) for n in names]


def gear_json(model, spears):
    mats = []
    for m in model["materials"]:
        mats.append(dict(id=m["id"], vanilla=m["vanilla"], idx=int(m["tier"]["idx"]) if m["armor"] else int(m["tool"]["idx"]), prefix=m["prefix"], enchantability=m["enchantability"], equip_sound=m["equip_sound"],
                         ingredient=m["ingredient"], armor=armor_entry(m) if m["armor"] else None,
                         tools=tool_entry(m, spears) if m["tools"] else None))
    gates = [dict(ore=g["ore"], tool_idx=int(g["tool_idx"]),
                  blocks=[dict(id=b, hardness=resolved_hardness(float(g["hardness"]) if g["hardness"] else None, b, g["blocks"].split())) for b in g["blocks"].split()])
             for g in model["gates"]]
    return dumps(dict(version=1, materials=mats, blocks=block_entries(model), items=item_entries(model), gates=gates, removed=NETHERITE_GEAR))


# ------------------------------------------------------------------------------------------------ lang
def lang(model):
    en, tr = {}, {}
    en["itemGroup.theoria.gear"], tr["itemGroup.theoria.gear"] = "Theoria: Gear", "Theoria: Teçhizat"
    for m in model["materials"]:
        if m["vanilla"]:
            continue
        for kind in (G.PIECES if m["armor"] else ()) + (G.TOOL_KINDS if m["tools"] else ()):
            key = f"item.theoria.{m['id']}_{kind}"
            en[key], tr[key] = f"{m['en']} {G.PIECE_EN[kind]}", f"{m['tr']} {G.PIECE_TR[kind]}"
    for m, short in G.local_ingredients(model):
        gem = not short.endswith("_ingot")
        en[f"item.theoria.{short}"] = m["en"] if gem else f"{m['en']} Ingot"
        tr[f"item.theoria.{short}"] = m["tr"] if gem else f"{m['tr']} Külçesi"
        en[f"block.theoria.{short.removesuffix('_ingot')}_block"] = f"Block of {m['en']}"
        tr[f"block.theoria.{short.removesuffix('_ingot')}_block"] = f"{m['tr']} Bloğu"
    for raw in G.raw_items(model):
        o = next(o for o in model["ores"].values() if o["drop"] == raw)
        en[f"item.theoria.{raw}"], tr[f"item.theoria.{raw}"] = f"Raw {o['en']}", f"Ham {o['tr']}"
    en["item.theoria.carbon_dust"], tr["item.theoria.carbon_dust"] = "Carbon Dust", "Karbon Tozu"
    for o, name, deep in G.ore_blocks(model):
        en[f"block.theoria.{name}"] = ("Deepslate " if deep else "") + f"{o['en']} Ore"
        tr[f"block.theoria.{name}"] = ("Derin " if deep else "") + f"{o['tr']} Cevheri"
    return en, tr


def merge_lang(existing_text, generated):
    """Elle yazılmış anahtarlar yerinde kalır; gear'ın sahip olduğu (item.theoria.* / block.theoria.* / itemGroup.theoria.*) anahtarlar yeniden yazılır."""
    owned = ("item.theoria.", "block.theoria.", "itemGroup.theoria.")
    foreign = ("item.theoria.catalyst", "block.theoria.catalyst_forge", "itemGroup.theoria.catalysts",   # generator/catalysts.py'nin anahtarları: dokunma
               "item.theoria.relic.", "itemGroup.theoria.relics")                                           # generator/relics.py'nin anahtarları (Faz 12): dokunma
    current = json.loads(existing_text)
    # generator/mobs.py'nin anahtarları (spawn yumurtaları, Kalp Kristali, mob sekmesi): dokunma
    mobs = lambda k: k.endswith("_spawn_egg") or k.startswith(("item.theoria.heart_crystal", "item.theoria.oblivion_orb", "itemGroup.theoria.mobs"))
    merged = {k: v for k, v in current.items() if not k.startswith(owned) or k.startswith(foreign) or mobs(k)}
    merged.update(generated)
    return dumps(dict(sorted(merged.items())))   # tek kanonik sıra: gear.py ve catalysts.py aynı dosyayı birleştirir, sıra kavgası olmasın


# ------------------------------------------------------------------------------------------------ models / item definitions / equipment
def item_definition(model_id, extra=None):
    d = {"model": {"type": "minecraft:model", "model": model_id}}
    if extra:
        d.update(extra)
    return dumps(d)


def spear_definition(i):
    return dumps({
        "model": {"type": "minecraft:select", "property": "minecraft:display_context",
                  "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": {"type": "minecraft:model", "model": f"theoria:item/{i}_spear"}}],
                  "fallback": {"type": "minecraft:model", "model": f"theoria:item/{i}_spear_in_hand"}},
        "swap_animation_scale": 1.95})


def flat_model(parent, layer):
    return dumps({"parent": parent, "textures": {"layer0": layer}})


def resource_files(model, spears):
    """assets/theoria altındaki her şey: item tanımları, modeller, blockstate, equipment, dokular."""
    out, root = {}, "assets/theoria/"
    for m in model["materials"]:
        if m["vanilla"]:
            continue
        i = m["id"]
        if m["armor"]:
            for p in G.PIECES:
                out[f"{root}items/{i}_{p}.json"] = item_definition(f"theoria:item/{i}_{p}")
                out[f"{root}models/item/{i}_{p}.json"] = flat_model("minecraft:item/generated", f"theoria:item/{i}_{p}")
            layers = {k: [{"texture": f"theoria:{i}"}] for k in ("humanoid", "humanoid_baby", "humanoid_leggings")}
            out[f"{root}equipment/{i}.json"] = dumps({"layers": layers})
        if m["tools"]:
            for k in ("sword", "axe", "pickaxe", "shovel", "hoe"):
                out[f"{root}items/{i}_{k}.json"] = item_definition(f"theoria:item/{i}_{k}")
                out[f"{root}models/item/{i}_{k}.json"] = flat_model("minecraft:item/handheld", f"theoria:item/{i}_{k}")
            out[f"{root}items/{i}_spear.json"] = spear_definition(i)
            out[f"{root}models/item/{i}_spear.json"] = flat_model("minecraft:item/generated", f"theoria:item/{i}_spear")
            out[f"{root}models/item/{i}_spear_in_hand.json"] = flat_model("minecraft:item/spear_in_hand", f"theoria:item/{i}_spear_in_hand")
        for path, data in gear_art.material_textures(m, model["ores"]).items():
            out[f"{root}textures/{path}.png"] = data
    out.update(plain_items(model, root))
    out.update(blocks(model, root))
    return out


# vanilla şablonu → boyanacak malzeme külçesi/ham/mücevher/toz
def plain_items(model, root):
    out = {}
    for m, short in G.local_ingredients(model):
        c = gear_art.hex_rgb(m["color"])
        gem = not short.endswith("_ingot")
        out[f"{root}textures/item/{short}.png"] = gear_art.metal_icon("item/diamond" if gem else "item/iron_ingot", c, spread=gem)
    for raw in G.raw_items(model):
        o = next(o for o in model["ores"].values() if o["drop"] == raw)
        out[f"{root}textures/item/{raw}.png"] = gear_art.metal_icon("item/raw_iron", gear_art.hex_rgb(o["color"]), spread=True)
    out[f"{root}textures/item/carbon_dust.png"] = gear_art.metal_icon("item/gunpowder", (74, 78, 90), spread=True, hilite=0.35)
    names = [short for _, short in G.local_ingredients(model)] + G.raw_items(model) + ["carbon_dust"]
    for n in names:
        out[f"{root}items/{n}.json"] = item_definition(f"theoria:item/{n}")
        out[f"{root}models/item/{n}.json"] = flat_model("minecraft:item/generated", f"theoria:item/{n}")
    return out


# vanilla cevher dokusu desenleri: yeni cevher → hangi vanilla cevherin beneklerini ödünç alır (görsel çeşitlilik)
ORE_PATTERN = {"tin": "iron", "cobalt": "lapis", "titanium": "gold", "lonsdaleit": "diamond", "mithril": "emerald", "orichalcum": "copper", "platinum": "redstone"}


def blocks(model, root):
    out = {}

    def block_files(name, texture_png):
        out[f"{root}textures/block/{name}.png"] = texture_png
        out[f"{root}blockstates/{name}.json"] = dumps({"variants": {"": {"model": f"theoria:block/{name}"}}})
        out[f"{root}models/block/{name}.json"] = dumps({"parent": "minecraft:block/cube_all", "textures": {"all": f"theoria:block/{name}"}})
        out[f"{root}items/{name}.json"] = item_definition(f"theoria:block/{name}")

    for o, name, deep in G.ore_blocks(model):
        pattern = ORE_PATTERN[o["id"]]
        block_files(name, gear_art.ore_block(f"block/{'deepslate_' if deep else ''}{pattern}_ore", "block/deepslate" if deep else "block/stone", gear_art.hex_rgb(o["color"])))
    for m, name in G.storage_blocks(model):
        block_files(name, gear_art.metal_icon("block/iron_block", gear_art.hex_rgb(m["color"])))
    return out
