"""Gear üreteci, veri paketi tarafı (Faz 6): tarifler, loot, tag'ler (kazı kapıları dahil), cevher üretimi, netherite temizliği.

Yollar `mods-src/theoria/src/generated/resources/` köküne göredir. Formatlar 26.3 vanilla kaynağından doğrulandı
(loot: "type"/"modifier"/"condition" anahtarları; tarif sonucu {"id","count"}; dünya üretimi klasörü worldgen/feature).
"""
import gear_assets as A
import gear_model as G

dumps = A.dumps
STICK = "minecraft:stick"
# vanilla araç seviyesi (needs_*_tool) → bizim alet idx'imizin karşıladığı: odun 0 hiçbiri · taş/bakır taş · demir/altın/kobalt demir · elmas+ elmas
VANILLA_LEVEL_TAGS = ["minecraft:needs_stone_tool", "minecraft:needs_iron_tool", "minecraft:needs_diamond_tool"]
VANILLA_TOOL_TAGS = {0: "minecraft:incorrect_for_wooden_tool", 1: "minecraft:incorrect_for_stone_tool", 2: "minecraft:incorrect_for_copper_tool",
                     4: "minecraft:incorrect_for_iron_tool", 6: "minecraft:incorrect_for_gold_tool", 8: "minecraft:incorrect_for_diamond_tool"}


# vanilla tarif ilerlemelerinin klasörü (data/minecraft/advancement/recipes/<klasör>/): kask/göğüs/pantolon/bot/at ve denizci zırhı/kılıç/mızrak = combat; kazma/balta/kürek/çapa = tools
ADVANCEMENT_DIR = {**{k: "combat" for k in ("helmet", "chestplate", "leggings", "boots", "horse_armor", "nautilus_armor", "sword", "spear")},
                   **{k: "tools" for k in ("axe", "pickaxe", "shovel", "hoe")}}


def vanilla_level(idx):
    """Alet idx'inin vanilla needs_* tag'lerinden kaçını karşıladığı (0 hiçbiri, 1 taş, 2 demir, 3 elmas)."""
    return 0 if idx < 1 else 1 if idx < 4 else 2 if idx < 8 else 3


def tag(values, replace=False, remove=None):
    d = {"values": values}
    if replace:
        d["replace"] = True
    if remove:
        d["remove"] = remove
    return dumps(d)


def shaped(pattern, key, result, count=1, category="equipment"):
    return dumps({"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern,
                  "result": {"id": result, **({"count": count} if count > 1 else {})}})


def shapeless(ingredients, result, count=1, category="misc", group=None):
    d = {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients, "result": {"id": result, **({"count": count} if count > 1 else {})}}
    if group:
        d["group"] = group
    return dumps(d)


def cooking(kind, ingredient, result, group, xp=0.7, time=200):
    return dumps({"type": f"minecraft:{kind}", "cookingtime": time, "experience": xp, "group": group, "ingredient": ingredient, "result": {"id": result}})


# ------------------------------------------------------------------------------------------------ recipes
def recipes(model):
    out, base = {}, "data/theoria/recipe/"
    for m in model["materials"]:
        if m["vanilla"]:
            continue
        mat = m["ingredient"]
        kinds = (G.PIECES if m["armor"] else ()) + (G.TOOL_KINDS if m["tools"] else ())
        for k in kinds:
            key = {"X": mat} if k in G.PIECES else {"X": mat, "#": STICK}
            out[f"{base}{m['id']}_{k}.json"] = shaped(G.RECIPE_PATTERN[k], key, f"theoria:{m['id']}_{k}")
    for m, name in G.storage_blocks(model):
        ing = m["ingredient"]
        out[f"{base}{name}.json"] = shaped(["###", "###", "###"], {"#": ing}, f"theoria:{name}", category="building")
        out[f"{base}{ing.split(':')[1]}_from_{name}.json"] = shapeless([f"theoria:{name}"], ing, 9, group=ing.split(":")[1])
    for o, name, deep in G.ore_blocks(model):
        result = _smelts_to(model, o)
        group = result.split(":")[1]
        out[f"{base}{group}_from_smelting_{name}.json"] = cooking("smelting", f"theoria:{name}", result, group)
        out[f"{base}{group}_from_blasting_{name}.json"] = cooking("blasting", f"theoria:{name}", result, group)
    for raw in G.raw_items(model):
        ore = next(o for o in model["ores"].values() if o["drop"] == raw)
        result = _smelts_to(model, ore)
        group = result.split(":")[1]
        out[f"{base}{group}_from_smelting_{raw}.json"] = cooking("smelting", f"theoria:{raw}", result, group)
        out[f"{base}{group}_from_blasting_{raw}.json"] = cooking("blasting", f"theoria:{raw}", result, group)
    for a in model["alloys"]:
        out[f"{base}{a['id']}.json"] = shapeless(a["ingredients"], a["result"], a["count"])
    # Netherite (D6): smithing yükseltmeleri yok sayılır (neoforge:never); külçe/hurda/blok tarifleri kalır (evrensel katalizör).
    # Şablon ÇOĞALTMA tarifi dokunulmadan kalır: NeoForge vanilla tarifin kendi kopyasını (#c:gems/diamond) gönderiyor ve o kazanıyor;
    # tarifin tek girdisi şablonun kendisi → şablon hiçbir kaynaktan (loot süzgeci, ticaret yok) çıkmadığı için etkisiz.
    never = dumps({"neoforge:conditions": [{"type": "neoforge:never"}]})
    for item in A.NETHERITE_GEAR:
        name = item.split(":")[1]
        if name == "netherite_upgrade_smithing_template":
            continue
        recipe = f"{name}_smithing"
        out[f"data/minecraft/recipe/{recipe}.json"] = never
        # Tarif tarif-kitabı ilerlemesinden (advancement) anılıyor: ilerleme kalırsa kayıt yüklemesi "Unbound values in registry recipe" ile ÇÖKER.
        out[f"data/minecraft/advancement/recipes/{ADVANCEMENT_DIR[name.removeprefix('netherite_')]}/{recipe}.json"] = never
    return out


def _smelts_to(model, ore):
    """Cevher / ham madde neyi eritir: theoria:<id>_ingot, mücevher cevheri (lonsdaleit) için mücevherin kendisi."""
    ingredient = next(m["ingredient"] for m in model["materials"] if m["id"] == ore["id"])
    return ingredient


# ------------------------------------------------------------------------------------------------ loot
def loot_tables(model):
    out = {}
    base = "data/theoria/loot_table/"
    for o, name, deep in G.ore_blocks(model):
        drop = "theoria:" + o["drop"]
        out[f"{base}blocks/{name}.json"] = dumps({
            "type": "minecraft:block", "random_sequence": f"theoria:blocks/{name}",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": f"theoria:{name}"},
                {"type": "minecraft:item", "name": drop,
                 "modifier": [{"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                              {"type": "minecraft:explosion_decay"}]}]}]}]})
    for m, name in G.storage_blocks(model):
        out[f"{base}blocks/{name}.json"] = dumps({
            "type": "minecraft:block", "random_sequence": f"theoria:blocks/{name}",
            "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"}, "entries": [{"type": "minecraft:item", "name": f"theoria:{name}"}]}]})
    for d in model["drops"]:
        out[f"{base}entities/drop_{d['entity']}_{d['item'].split(':')[1]}.json"] = dumps({
            "type": "minecraft:entity",
            "pools": [{"rolls": 1, "condition": {"type": "minecraft:random_chance", "chance": float(d["chance"])},
                       "entries": [{"type": "minecraft:item", "name": d["item"], "modifier": [
                           {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": int(d["min"]), "max": int(d["max"])}},
                           {"type": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                            "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0}}]}]}]})
        out[f"data/theoria/loot_modifiers/drop_{d['entity']}_{d['item'].split(':')[1]}.json"] = dumps({
            "type": "neoforge:add_table",
            "condition": {"type": "neoforge:loot_table_id", "loot_table_id": f"minecraft:entities/{d['entity']}"},
            "table": f"theoria:entities/drop_{d['entity']}_{d['item'].split(':')[1]}"})
    # Netherite eşyaları (D6) bastion/ruined portal gibi her kaynaktan süzülür
    out["data/theoria/loot_modifiers/remove_netherite_gear.json"] = dumps({"type": "theoria:remove_items", "items": "#theoria:removed_gear"})
    return out


# ------------------------------------------------------------------------------------------------ tags
def gate_tags(model):
    """Kazı kapıları (docs/05 §7). theoria:gate/<N> = alet idx'i N isteyen bloklar; her alet kademesinin incorrect_for_* tag'i =
    (karşılamadığı vanilla needs_* seviyeleri) ∪ (idx'inden büyük N'ler). Vanilla araçlar vanilla tag adını (replace), yeniler theoria:incorrect_for_<id>_tool kullanır."""
    out, base = {}, "data/theoria/tags/block/"
    by_idx = {}
    for g in model["gates"]:
        if int(g["tool_idx"]) >= 1:
            by_idx.setdefault(int(g["tool_idx"]), []).extend(g["blocks"].split())
    for n, blocks in sorted(by_idx.items()):
        out[f"{base}gate/{n}.json"] = tag(blocks)
    tool_ids = {m["id"]: m for m in model["materials"] if m["tools"]}
    for tid, m in tool_ids.items():
        idx = m["tool"]["idx"]
        values = [t for level, t in enumerate(VANILLA_LEVEL_TAGS, start=1) if level > vanilla_level(idx)]
        values += [f"#theoria:gate/{n}" for n in sorted(by_idx) if n > idx]
        if m["vanilla"]:
            path = VANILLA_TOOL_TAGS[idx]
            ns, name = path.split(":")
            out[f"data/{ns}/tags/block/{name}.json"] = tag([("#" + v) if v.startswith("minecraft:needs") else v for v in values], replace=True)
        else:
            out[f"{base}incorrect_for_{tid}_tool.json"] = tag([("#" + v) if v.startswith("minecraft:needs") else v for v in values])
    return out


def block_tags(model):
    out, base = {}, "data/"
    ores = G.ore_blocks(model)
    storage = G.storage_blocks(model)
    # Kataliz Ocağı (Faz 7b, generator/catalysts.py) aynı vanilla tag dosyasını paylaşamaz (tek dosya yolu) → buraya elle eklenir.
    out["data/minecraft/tags/block/mineable/pickaxe.json"] = tag([f"theoria:{n}" for _, n, _ in ores] + [f"theoria:{n}" for _, n in storage] + ["theoria:catalyst_forge"])
    # Depolama blokları vanilla needs_* seviyesine, külçenin kademesine göre girer. Kırmızıtaş cevheri vanilla'da needs_iron_tool'dadır;
    # bizde bakır alet yeter (gate/2) → o tag'den ÇIKARILIR (`remove`), yoksa bakır kazma "yanlış alet" sayılırdı.
    for level, vanilla in ((1, "needs_stone_tool"), (2, "needs_iron_tool"), (3, "needs_diamond_tool")):
        names = [f"theoria:{n}" for m, n in storage if vanilla_level(int(m["tier"]["idx"])) == level]
        remove = ["minecraft:redstone_ore", "minecraft:deepslate_redstone_ore"] if vanilla == "needs_iron_tool" else None
        if names or remove:
            out[f"data/minecraft/tags/block/{vanilla}.json"] = tag(names, remove=remove)
    for o, name, deep in ores:
        out[f"data/c/tags/block/ores/{o['id']}.json"] = tag([f"theoria:{n}" for oo, n, _ in ores if oo is o])
    out["data/c/tags/block/ores.json"] = tag([f"#c:ores/{i}" for i in sorted({o['id'] for o, _, _ in ores})])
    out["data/c/tags/block/ores_in_ground/stone.json"] = tag([f"theoria:{n}" for _, n, deep in ores if not deep])
    out["data/c/tags/block/ores_in_ground/deepslate.json"] = tag([f"theoria:{n}" for _, n, deep in ores if deep])
    for m, name in storage:
        out[f"data/c/tags/block/storage_blocks/{m['id']}.json"] = tag([f"theoria:{name}"])
    out["data/c/tags/block/storage_blocks.json"] = tag([f"#c:storage_blocks/{m['id']}" for m, _ in storage])
    return out


def item_tags(model):
    out = {}
    ores = G.ore_blocks(model)
    for i in sorted({o["id"] for o, _, _ in ores}):
        out[f"data/c/tags/item/ores/{i}.json"] = tag([f"theoria:{n}" for o, n, _ in ores if o["id"] == i])
    out["data/c/tags/item/ores.json"] = tag([f"#c:ores/{i}" for i in sorted({o['id'] for o, _, _ in ores})])
    for raw in G.raw_items(model):
        ore = raw.removeprefix("raw_")
        out[f"data/c/tags/item/raw_materials/{ore}.json"] = tag([f"theoria:{raw}"])
    out["data/c/tags/item/raw_materials.json"] = tag([f"#c:raw_materials/{r.removeprefix('raw_')}" for r in G.raw_items(model)])
    gems, ingots = [], []
    for m, short in G.local_ingredients(model):
        if short.endswith("_ingot"):
            out[f"data/c/tags/item/ingots/{m['id']}.json"] = tag([f"theoria:{short}"])
            ingots.append(m["id"])
        else:
            out[f"data/c/tags/item/gems/{m['id']}.json"] = tag([f"theoria:{short}"])
            gems.append(m["id"])
    out["data/c/tags/item/ingots.json"] = tag([f"#c:ingots/{i}" for i in ingots])
    out["data/c/tags/item/gems.json"] = tag([f"#c:gems/{i}" for i in gems])
    for m, name in G.storage_blocks(model):
        out[f"data/c/tags/item/storage_blocks/{m['id']}.json"] = tag([f"theoria:{name}"])
    out["data/c/tags/item/storage_blocks.json"] = tag([f"#c:storage_blocks/{m['id']}" for m, _ in G.storage_blocks(model)])
    out["data/c/tags/item/dusts/carbon.json"] = tag(["theoria:carbon_dust"])
    out["data/c/tags/item/dusts.json"] = tag(["#c:dusts/carbon"])
    # vanilla tür tag'leri: büyülenebilirlik (enchantable/*), tarif/loot koşulları ve diğer modlar bunlara bakar
    groups = {"swords": "sword", "axes": "axe", "pickaxes": "pickaxe", "shovels": "shovel", "hoes": "hoe", "spears": "spear",
              "head_armor": "helmet", "chest_armor": "chestplate", "leg_armor": "leggings", "foot_armor": "boots"}
    for group, piece in groups.items():
        values = [f"theoria:{m['id']}_{piece}" for m in model["materials"] if not m["vanilla"] and (m["armor"] if piece in G.PIECES else m["tools"])]
        out[f"data/minecraft/tags/item/{group}.json"] = tag(values)
    for m in model["materials"]:
        if not m["vanilla"]:
            out[f"data/theoria/tags/item/repairs_{m['id']}.json"] = tag([m["ingredient"]])
    out["data/theoria/tags/item/removed_gear.json"] = tag(A.NETHERITE_GEAR)
    return out


# ------------------------------------------------------------------------------------------------ worldgen (cevher damarları)
def worldgen(model):
    out = {}
    seen = set()
    for o, name, deep in G.ore_blocks(model):
        if o["id"] in seen:
            continue
        seen.add(o["id"])
        targets = []
        for variant, tag_name, deepslate in (("stone", "minecraft:stone_ore_replaceables", False), ("deepslate", "minecraft:deepslate_ore_replaceables", True)):
            if variant in o["variants"].split("+"):
                block = f"theoria:{'deepslate_' if deepslate else ''}{o['id']}_ore"
                targets.append({"state": block, "target": {"predicate_type": "minecraft:tag_match", "tag": tag_name}})
        out[f"data/theoria/worldgen/feature/ore_{o['id']}.json"] = dumps({
            "type": "minecraft:ore", "size": int(o["size"]), "discard_chance_on_air_exposure": 0.0, "targets": targets})
        placement = []
        if int(o["rarity"]) > 0:
            placement.append({"type": "minecraft:rarity_filter", "chance": int(o["rarity"])})
        else:
            placement.append({"type": "minecraft:count", "count": int(o["count"])})
        placement += [{"type": "minecraft:in_square"},
                      {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": int(o["min_y"])}, "max_inclusive": {"absolute": int(o["max_y"])}}},
                      {"type": "minecraft:biome"}]
        out[f"data/theoria/worldgen/placed_feature/ore_{o['id']}.json"] = dumps({"feature": f"theoria:ore_{o['id']}", "placement": placement})
        out[f"data/theoria/neoforge/biome_modifier/ore_{o['id']}.json"] = dumps({
            "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld", "features": f"theoria:ore_{o['id']}", "step": "underground_ores"})
    return out
