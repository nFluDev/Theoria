#!/usr/bin/env python3
"""Gear denetimi (Faz 6). Çalıştır: python3 -I tools/check_gear.py   (negatif test: --break)

Oyun açmadan yakalanabilecek hataları keser (oyun içi karşılığı: `/theoria test gear`, GameTest `gear_*`):
  1. Şema: materials/ores/alloys/drops.csv ↔ tiers.csv/tools.csv/ores.csv tutarlı (her zırhlı malzeme bir kademe, her aletli malzeme bir alet
     kademesi; yeni malzemenin rengi var; cevher blokları üreteçle eşleşiyor; vanilla malzeme id'leri DEĞİŞMİYOR — D6).
  2. Üreteç güncel: `generator/gear.py --check` (CSV → gear.json/asset/veri JSON/doku; PNG'ler piksel düzeyinde).
  3. Referans bütünlüğü: item tanımı → model → doku; blockstate → model; equipment asset → 3 katman dokusu; her kayıtlı id için iki dilde lang anahtarı,
     artık lang anahtarı yok; tarif/loot/tag içinde anılan her theoria: kimliği gerçekten var; anılan her #theoria: tag dosyası var.
  4. Kazı kapıları (oyundan BAĞIMSIZ çözümleyici): vanilla needs_* tag'leri (jar) + bizim tag'ler (replace/remove anlamıyla) çözülür; her alet kademesi × kapılı blok için
     "yanlış alet" kümesi ⇔ alet idx < blok idx. Jar yoksa bu adım UYARIYLA atlanır.
  5. Sertlik: gear.json'daki çözümlenmiş blok sertliği tablo kuralıyla (deepslate × 1,5, yalnız taş ikizi varsa) aynı.
  6. Tırmanış (DoD "Kalay→Platin el ile yapılabilir"): üretilmiş tarif + loot grafiğinde, yalnızca odun/taş/kömür/mob dropu ile başlayıp, kazı kapılarına
     uyarak (cevher ancak yeterli kazmayla düşer) her zırh/alet/silah ve her cevher türü ulaşılabilir; kilit açılma sırası yazdırılır. Kırık zincir kırmızıdır.
Çıkış 0 + son satır 'OK' = temiz.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "generator"))
sys.path.insert(0, str(ROOT / "tools/balance"))
import gear_model as G  # noqa: E402
import tiers  # noqa: E402
import vanilla  # noqa: E402

GEN = ROOT / "mods-src/theoria/src/generated/resources"
LANG = ROOT / "mods-src/theoria/src/main/resources/assets/theoria/lang"
SOUNDS = {"leather", "chain", "copper", "iron", "gold", "diamond", "netherite"}
VANILLA_PARENTS = {"minecraft:item/generated", "minecraft:item/handheld", "minecraft:item/spear_in_hand", "minecraft:block/cube_all"}
VANILLA_IDS = {"wood": "wooden", "stone": "stone", "leather": "leather", "copper": "copper", "iron": "iron", "gold": "golden", "diamond": "diamond"}
errors, notes = [], []


def err(msg):
    errors.append(msg)


# ---------------------------------------------------------------------------------------------------- 1. şema
def check_schema(model, errs=None):
    errs = errs if errs is not None else errors
    mats = {m["id"] for m in model["materials"]}
    if len(mats) != len(model["materials"]):
        errs.append("materials.csv: yinelenen id")
    for m in model["materials"]:
        where = f"materials.csv {m['id']}"
        if m["armor"] and m["id"] not in model["tiers"]:
            errs.append(f"{where}: armor=1 ama tiers.csv'de kademe yok")
        if m["tools"] and m["id"] not in model["tools"]:
            errs.append(f"{where}: tools=1 ama tools.csv'de alet kademesi yok")
        if not m["armor"] and not m["tools"]:
            errs.append(f"{where}: ne zırh ne alet")
        if m["vanilla"] != (m["id"] in VANILLA_IDS):
            errs.append(f"{where}: vanilla bayrağı yanlış (D6: yalnızca {sorted(VANILLA_IDS)} vanilla id'dir)")
        if m["vanilla"] and m["prefix"] != VANILLA_IDS.get(m["id"]):
            errs.append(f"{where}: vanilla önek '{m['prefix']}' (beklenen {VANILLA_IDS.get(m['id'])})")
        if not m["vanilla"]:
            if not re.fullmatch(r"#[0-9a-fA-F]{6}", m["color"]):
                errs.append(f"{where}: renk '{m['color']}' #rrggbb değil")
            if m["prefix"] != m["id"]:
                errs.append(f"{where}: yeni malzemede item_prefix == id olmalı")
            if not m["ingredient"].startswith("theoria:"):
                errs.append(f"{where}: yeni malzemenin girdisi theoria: kimliği olmalı")
            if m["armor"] and m["equip_sound"] not in SOUNDS:
                errs.append(f"{where}: equip_sound '{m['equip_sound']}' ∉ {sorted(SOUNDS)}")
        if m["enchantability"] <= 0:
            errs.append(f"{where}: büyü yeteneği pozitif olmalı")
        if set(m["traits"]) - {"speed", "kb"}:
            errs.append(f"{where}: bilinmeyen özellik {sorted(set(m['traits']) - {'speed', 'kb'})}")
        if m["traits"] and not m["armor"]:
            errs.append(f"{where}: özellikler yalnız zırhta tanımlı")
    for t in model["tiers"]:
        if not any(m["id"] == t and m["armor"] for m in model["materials"]):
            errs.append(f"tiers.csv: '{t}' için materials.csv'de zırh satırı yok")
    for t in model["tools"]:
        if not any(m["id"] == t and m["tools"] for m in model["materials"]):
            errs.append(f"tools.csv: '{t}' için materials.csv'de alet satırı yok")
    # cevherler
    gate_blocks = {b for g in model["gates"] for b in g["blocks"].split()}
    for o in model["ores"].values():
        variants = set(o["variants"].split("+"))
        if not variants <= {"stone", "deepslate"} or not variants:
            errs.append(f"ores.csv {o['id']}: variants '{o['variants']}'")
        if not re.fullmatch(r"#[0-9a-fA-F]{6}", o["color"]):
            errs.append(f"ores.csv {o['id']}: renk")
        if o["id"] not in mats:
            errs.append(f"ores.csv {o['id']}: aynı id'li malzeme yok (eritme sonucu ondan gelir)")
        if int(o["min_y"]) >= int(o["max_y"]) or int(o["size"]) < 3:
            errs.append(f"ores.csv {o['id']}: yükseklik aralığı ters ya da damar < 3 (boyut 2 damarlar hiç blok koymayabiliyor)")
        if int(o["rarity"]) == 0 and int(o["count"]) <= 0:
            errs.append(f"ores.csv {o['id']}: count ya da rarity gerekli")
        # Hibrit (docs/10 Q63): yabani damar — TÜM Overworld biyomlarında, ev damarından seyrek
        if not o.get("wild_min_y"):
            errs.append(f"ores.csv {o['id']}: yabani damar sütunları (wild_*) boş")
        else:
            if not -64 <= int(o["wild_min_y"]) < int(o["wild_max_y"]) <= 320 or int(o["wild_size"]) < 3:
                errs.append(f"ores.csv {o['id']}: yabani yükseklik aralığı ters/dünya dışı ya da damar < 3")
            if int(o["wild_rarity"]) == 0 and int(o["wild_count"]) <= 0:
                errs.append(f"ores.csv {o['id']}: yabani damar için wild_count ya da wild_rarity gerekli")
        expected = {f"theoria:{n}" for _, n, _ in G.ore_blocks({"ores": {o["id"]: o}})}
        if not expected <= gate_blocks:
            errs.append(f"ores.csv {o['id']}: {sorted(expected - gate_blocks)} tiers.py ORES kapı tablosunda yok")
    theoria_gate = {b for b in gate_blocks if b.startswith("theoria:")}
    produced = {f"theoria:{n}" for _, n, _ in G.ore_blocks(model)}
    if theoria_gate != produced:
        errs.append(f"kapı tablosundaki theoria blokları ≠ ores.csv blokları: {sorted(theoria_gate ^ produced)}")
    for a in model["alloys"]:
        if len(a["ingredients"]) > 9 or a["count"] < 1:
            errs.append(f"alloys.csv {a['id']}: ≤ 9 girdi, adet ≥ 1")
    for d in model["drops"]:
        if not 0 < float(d["chance"]) <= 1 or int(d["min"]) > int(d["max"]):
            errs.append(f"drops.csv {d['entity']}: şans (0,1] ve min ≤ max olmalı")
    return errs


# ---------------------------------------------------------------------------------------------------- 3. referans bütünlüğü
def registered_ids(gear):
    items = {f"{m['prefix'] if m['vanilla'] else m['id']}_{k}" for m in gear["materials"] if not m["vanilla"]
             for k in (G.PIECES if m["armor"] else ()) + (G.TOOL_KINDS if m["tools"] else ())}
    items |= {i["id"] for i in gear["items"]}
    blocks = {b["id"] for b in gear["blocks"]}
    return items, blocks


def strings(node):
    if isinstance(node, str):
        yield node
    elif isinstance(node, dict):
        for k, v in node.items():
            yield from strings(v)
    elif isinstance(node, list):
        for v in node:
            yield from strings(v)


def check_assets(gen, gear, errs):
    assets = gen / "assets/theoria"
    items, blocks = registered_ids(gear)
    for name in sorted(items | blocks):
        definition = assets / "items" / f"{name}.json"
        if not definition.exists():
            errs.append(f"item tanımı yok: items/{name}.json")
            continue
        for ref in strings(json.loads(definition.read_text(encoding="utf-8"))):
            if ref.startswith("theoria:") and "/" in ref:
                model = assets / "models" / f"{ref.split(':')[1]}.json"
                if not model.exists():
                    errs.append(f"items/{name}.json → model yok: {ref}")
                    continue
                check_model(assets, model, errs)
    for name in sorted(blocks):
        state = assets / "blockstates" / f"{name}.json"
        if not state.exists():
            errs.append(f"blockstate yok: {name}")
            continue
        for ref in strings(json.loads(state.read_text(encoding="utf-8"))):
            if ref.startswith("theoria:") and not (assets / "models" / f"{ref.split(':')[1]}.json").exists():
                errs.append(f"blockstates/{name}.json → model yok: {ref}")
    for m in gear["materials"]:
        if m["vanilla"] or not m["armor"]:
            continue
        equipment = assets / "equipment" / f"{m['id']}.json"
        if not equipment.exists():
            errs.append(f"equipment asset yok: {m['id']}")
            continue
        layers = json.loads(equipment.read_text(encoding="utf-8"))["layers"]
        for layer in ("humanoid", "humanoid_leggings", "humanoid_baby"):
            for entry in layers.get(layer, []):
                tex = assets / "textures/entity/equipment" / layer / f"{entry['texture'].split(':')[1]}.png"
                if not tex.exists():
                    errs.append(f"equipment/{m['id']}.json → doku yok: {tex.relative_to(assets)}")
            if layer not in layers:
                errs.append(f"equipment/{m['id']}.json: '{layer}' katmanı yok")


def check_model(assets, model, errs):
    data = json.loads(model.read_text(encoding="utf-8"))
    parent = data.get("parent")
    if parent and parent not in VANILLA_PARENTS and not parent.startswith("theoria:"):
        errs.append(f"{model.name}: bilinmeyen parent {parent}")
    for tex in data.get("textures", {}).values():
        if tex.startswith("theoria:") and not (assets / "textures" / f"{tex.split(':')[1]}.png").exists():
            errs.append(f"{model.name} → doku yok: {tex}")


def check_lang(gear, langs, errs):
    items, blocks = registered_ids(gear)
    want = {f"item.theoria.{i}" for i in items - blocks} | {f"block.theoria.{b}" for b in blocks} | {"itemGroup.theoria.gear"}
    mobs_file = ROOT / "mods-src/theoria/src/generated/mobs/theoria/mobs.json"
    if mobs_file.exists():
        species = json.loads(mobs_file.read_text(encoding="utf-8")).get("species", [])
        want |= {f"item.theoria.{sp['id']}_spawn_egg" for sp in species}
        want |= {"item.theoria.heart_crystal", "item.theoria.oblivion_orb", "itemGroup.theoria.mobs"}
    for name, table in langs.items():
        for key in sorted(want - set(table)):
            errs.append(f"lang {name}: '{key}' yok")
        foreign = ("item.theoria.catalyst", "block.theoria.catalyst_forge", "itemGroup.theoria.catalysts",   # generator/catalysts.py'nin anahtarları (Faz 7b)
                   "item.theoria.relic.", "itemGroup.theoria.relics")                                           # generator/relics.py'nin anahtarları (Faz 12)
        for key in sorted(k for k in table if k.startswith(("item.theoria.", "block.theoria.", "itemGroup.theoria.")) and k not in want and not k.startswith(foreign)):
            errs.append(f"lang {name}: artık anahtar '{key}'")


def listify(x):
    return [] if x is None else x if isinstance(x, list) else [x]


def refs_of(rel, data):
    """Dosya türüne göre (kimlik, tür) çiftleri: tarif girdileri/sonucu, loot `name`'leri, tag değerleri. Tür: item | block | tag."""
    out = []
    if "/recipe/" in rel and "data/minecraft/" not in rel:
        raw = listify(data.get("ingredient")) + listify(data.get("ingredients")) + list(data.get("key", {}).values()) + [data["result"]["id"]]
        out += [(r, "tag" if r.startswith("#") else "item") for r in raw if isinstance(r, str)]
    elif "/loot_table/" in rel:
        def walk(n):
            if isinstance(n, dict):
                if n.get("type") == "minecraft:item" and "name" in n:
                    out.append((n["name"], "item"))
                for v in n.values():
                    walk(v)
            elif isinstance(n, list):
                for v in n:
                    walk(v)
        walk(data)
    elif "/tags/" in rel:
        kind = "block" if "/tags/block/" in rel else "item"
        out += [(v, "tag" if v.startswith("#") else kind) for v in data.get("values", []) + data.get("remove", [])]
    return out


def check_refs(gen, gear, errs):
    items, blocks = registered_ids(gear)
    other = {"catalyst_forge"}   # Faz 7b'nin bloğu: gear'ın pickaxe tag'ine elle eklenir (aynı vanilla tag dosyası, generator/catalysts.py ile paylaşılamaz); varlığını CatalystChecks/GameTest'ler denetler
    own = {"item": items | blocks | other, "block": blocks | other}
    have_jar = vanilla.available()
    v_items = {n[len("assets/minecraft/items/"):-5] for n in vanilla.names("assets/minecraft/items/") if n.endswith(".json")} if have_jar else set()
    v_blocks = {n[len("assets/minecraft/blockstates/"):-5] for n in vanilla.names("assets/minecraft/blockstates/") if n.endswith(".json")} if have_jar else set()
    tag_files = {p.relative_to(gen).as_posix() for p in gen.rglob("*.json") if "/tags/" in p.as_posix()}
    for path in sorted((gen / "data").rglob("*.json")):
        rel = path.relative_to(gen).as_posix()
        for ref, kind in refs_of(rel, json.loads(path.read_text(encoding="utf-8"))):
            ns, name = ref.lstrip("#").split(":")
            if kind == "tag":
                if ns == "theoria" and not any(f"data/theoria/tags/{t}/{name}.json" in tag_files for t in ("block", "item")):
                    errs.append(f"{rel}: tag dosyası yok: {ref}")
            elif ns == "theoria":
                if name not in own[kind] and not (kind == "item" and name in own["block"]):
                    errs.append(f"{rel}: theoria:{name} kayıtlı {kind} değil")
            elif ns == "minecraft" and have_jar:
                if name not in (v_items if kind == "item" else v_blocks):
                    errs.append(f"{rel}: vanilla'da böyle bir {kind} yok: {ref}")


# ---------------------------------------------------------------------------------------------------- 4. kazı kapıları (bağımsız çözümleyici)
def resolve_tags(vanilla_tags, ours):
    """TagLoader anlamı: vanilla tanımları sırayla, sonra bizimkiler; `replace` önceki her şeyi atar, `remove` (sıralı) çıkarır. Çıktı: {tag: {blok}} (iç içe #tag'ler açık)."""
    raw = {}
    for source in (vanilla_tags, ours):
        for name, spec in source.items():
            if spec.get("replace"):
                raw[name] = {"values": [], "remove": []}
            cur = raw.setdefault(name, {"values": [], "remove": []})
            cur["values"] = cur["values"] + spec.get("values", [])
            cur["remove"] = cur["remove"] + spec.get("remove", [])
    memo = {}

    def expand(name, trail=()):
        if name in memo:
            return memo[name]
        if name in trail:
            raise SystemExit(f"tag döngüsü: {name}")
        out = set()
        for v in raw.get(name, {"values": []})["values"]:
            out |= expand(v[1:], trail + (name,)) if v.startswith("#") else {v}
        for v in raw.get(name, {"remove": []})["remove"]:
            out -= {v}
        memo[name] = out
        return out
    return {n: expand(n) for n in raw}


def load_block_tags(gen):
    ours = {}
    for path in sorted((gen / "data").glob("*/tags/block/**/*.json")):
        ns = path.relative_to(gen / "data").parts[0]
        name = f"{ns}:{path.relative_to(gen / 'data' / ns / 'tags/block').with_suffix('').as_posix()}"
        ours[name] = json.loads(path.read_text(encoding="utf-8"))
    return ours


def vanilla_block_tags():
    out = {}
    for n in vanilla.names("data/minecraft/tags/block/"):
        if n.endswith(".json"):
            out["minecraft:" + n[len("data/minecraft/tags/block/"):-5]] = json.loads(vanilla.read(n))
    return out


def check_gates(model, resolved, errs):
    """Her alet kademesi × kapılı blok: blok o kademenin 'yanlış alet' kümesinde ⇔ alet idx < blok idx."""
    n = 0
    for tid, spec in model["tools"].items():
        mat = next(m for m in model["materials"] if m["id"] == tid)
        tag = f"minecraft:incorrect_for_{'wooden' if tid == 'wood' else 'gold' if tid == 'gold' else tid}_tool" if mat["vanilla"] else f"theoria:incorrect_for_{tid}_tool"
        if tag not in resolved:
            errs.append(f"kazı kapısı tag'i yok: {tag}")
            continue
        for g in model["gates"]:
            need = int(g["tool_idx"])
            for b in g["blocks"].split():
                wrong = b in resolved[tag]
                if wrong != (spec["idx"] < need):
                    errs.append(f"{tag}: {b} (alet idx {need} ister) — {tid} (idx {spec['idx']}) için yanlış-alet={wrong}, beklenen {spec['idx'] < need}")
                n += 1
    return n


# ---------------------------------------------------------------------------------------------------- 5. sertlik
def check_hardness(model, gear, errs):
    resolved = {b["id"]: b["hardness"] for g in gear["gates"] for b in g["blocks"]}
    for g in model["gates"]:
        base = float(g["hardness"]) if g["hardness"] else None
        blocks = g["blocks"].split()
        for b in blocks:
            twin = b.split(":")[0] + ":" + b.split(":")[1].removeprefix("deepslate_")
            expected = None if base is None else round(base * (1.5 if b.split(":")[1].startswith("deepslate_") and twin in blocks else 1.0), 2)
            if resolved.get(b, "yok") != expected:
                errs.append(f"gear.json sertlik {b}: {resolved.get(b, 'yok')} ≠ {expected}")
    for b in gear["blocks"]:
        if b["kind"] == "ore" and b["hardness"] != resolved.get(f"theoria:{b['id']}"):
            errs.append(f"gear.json blok {b['id']} sertliği kapı tablosundakiyle aynı değil")


# ---------------------------------------------------------------------------------------------------- 6. tırmanış (ulaşılabilirlik)
VANILLA_PICKS = {  # alet kimliği → (kazma, malzeme) — vanilla tarifleri sabit ve bilinir
    "wood": "minecraft:planks", "stone": "minecraft:cobblestone", "copper": "minecraft:copper_ingot", "iron": "minecraft:iron_ingot",
    "gold": "minecraft:gold_ingot", "diamond": "minecraft:diamond"}
VANILLA_ORE_DROP = {"minecraft:coal_ore": "minecraft:coal", "minecraft:copper_ore": "minecraft:copper_ingot", "minecraft:iron_ore": "minecraft:iron_ingot",
                    "minecraft:gold_ore": "minecraft:gold_ingot", "minecraft:diamond_ore": "minecraft:diamond"}   # eritme dahil: fırın serbest


def load_recipes(gen):
    out = []
    for path in sorted((gen / "data/theoria/recipe").glob("*.json")):
        d = json.loads(path.read_text(encoding="utf-8"))
        if d["type"] in ("minecraft:crafting_shaped", "minecraft:crafting_shapeless"):
            ing = set(d["key"].values()) if "key" in d else set(d["ingredients"])
        else:
            ing = {d["ingredient"]}
        out.append((path.stem, ing, d["result"]["id"]))
    return out


def loot_drops(gen, ore_drop_items):
    """theoria blok → düşürdüğü item (loot tablosundaki ikinci alternatif)."""
    drops = {}
    for path in (gen / "data/theoria/loot_table/blocks").glob("*_ore.json"):
        d = json.loads(path.read_text(encoding="utf-8"))
        children = d["pools"][0]["entries"][0]["children"]
        drops[f"theoria:{path.stem}"] = children[1]["name"]
    return drops


def climb(model, gen, errs, verbose=False):
    """Sabit nokta: sahip olunan kazma idx'lerine göre cevher açılır, tarifler tetiklenir. Döner: kilit açılma sırası [(tur, ne)]."""
    recipes = load_recipes(gen)
    drops = {**VANILLA_ORE_DROP, **loot_drops(gen, None)}
    gate = {b: int(g["tool_idx"]) for g in model["gates"] for b in g["blocks"].split()}
    idx = {t: v["idx"] for t, v in model["tools"].items()}
    have = {"minecraft:stick", "minecraft:planks", "minecraft:cobblestone", "minecraft:coal", "theoria:carbon_dust"}   # odun, taş, kömür; karbon tozu iskelet/kurt dropu
    order, best_pick = [], -1
    changed = True
    while changed:
        changed = False
        for tid, mat in VANILLA_PICKS.items():                                  # vanilla kazmalar
            item = f"minecraft:{'wooden' if tid == 'wood' else 'golden' if tid == 'gold' else tid}_pickaxe"
            if mat in have and item not in have:
                have.add(item); order.append(("alet", item)); changed = True
        for ore, drop in drops.items():                                          # cevher: yeterli kazma varsa düşer
            if drop not in have and best_pick >= gate[ore]:
                have.add(drop); order.append(("cevher", f"{ore} → {drop}")); changed = True
        for name, ing, result in recipes:                                        # tarifler (girdilerin hepsi elde, theoria: ya da vanilla temel)
            if result not in have and all(i in have or (i.startswith("theoria:") and False) for i in ing):
                have.add(result); order.append(("tarif", f"{name} → {result}")); changed = True
        picks = [idx[t] for t in idx if f"{'minecraft' if t in VANILLA_PICKS else 'theoria'}:{('wooden' if t == 'wood' else 'golden' if t == 'gold' else t)}_pickaxe" in have]
        if picks and max(picks) > best_pick:
            best_pick = max(picks); changed = True
    expected = {f"theoria:{m['id']}_{k}" for m in model["materials"] if not m["vanilla"] for k in (G.PIECES if m["armor"] else ()) + (G.TOOL_KINDS if m["tools"] else ())}
    expected |= {f"theoria:{short}" for _, short in G.local_ingredients(model)} | {f"theoria:{n}" for _, n in G.storage_blocks(model)}
    for missing in sorted(expected - have):
        errs.append(f"tırmanış: {missing} hiçbir yoldan elde edilemiyor (tarif/cevher/kapı zinciri kırık)")
    return order


# ---------------------------------------------------------------------------------------------------- ana akış
def run_checks():
    model = G.build()
    check_schema(model)
    notes.append(f"şema: {len(model['materials'])} malzeme, {len(model['ores'])} cevher, {len(model['alloys'])} alaşım, {len(model['drops'])} drop satırı")
    result = subprocess.run([sys.executable, "-I", str(ROOT / "generator/gear.py"), "--check"], capture_output=True, text=True)
    if result.returncode != 0:
        err("üretilen gear dosyaları güncel değil → python3 -I generator/gear.py" + (f" ({result.stdout.strip().splitlines()[0]})" if result.stdout.strip() else ""))
    else:
        notes.append("generator/gear.py --check: depodaki dosyalar üreteçle özdeş" + ("" if vanilla.available() else " (vanilla jar yok: PNG'ler atlandı)"))
    gear = json.loads((GEN / "theoria/gear.json").read_text(encoding="utf-8"))
    check_assets(GEN, gear, errors)
    langs = {n: json.loads((LANG / f"{n}.json").read_text(encoding="utf-8")) for n in ("en_us", "tr_tr")}
    check_lang(gear, langs, errors)
    check_refs(GEN, gear, errors)
    items, blocks = registered_ids(gear)
    notes.append(f"referanslar: {len(items)} item + {len(blocks)} blok → tanım/model/doku/lang tamam; tarif/loot/tag içi kimlikler geçerli")
    check_hardness(model, gear, errors)
    if vanilla.available():
        resolved = resolve_tags(vanilla_block_tags(), load_block_tags(GEN))
        pairs = check_gates(model, resolved, errors)
        notes.append(f"kazı kapıları: {pairs} (alet × kapılı blok) çifti vanilla tag'leri + bizimkilerle bağımsız çözüldü")
    else:
        notes.append("UYARI: vanilla jar yok — kazı kapıları çözümlemesi atlandı")
    path = climb(model, GEN, errors)
    picks = [what.split(" → ")[0] if kind == "tarif" else what for kind, what in path if (kind == "alet") or (kind == "tarif" and what.endswith("_pickaxe"))]
    picks = [x.split(":")[-1].replace("_pickaxe", "") for x in picks if "pickaxe" in x]
    notes.append(f"tırmanış: {' → '.join(picks)} ({len(path)} kilit açıldı; her gear, cevher ve alaşım ulaşılabilir)")


def break_tests():
    """Her biri bilerek bozulmuş girdiyle denetleyicinin KIRMIZI vermesini bekler."""
    model = G.build()
    results = []

    def expect(name, errs):
        results.append((name, bool(errs)))

    def mutate(fn):
        m = json.loads(json.dumps(model, default=str))
        fn(m)
        return m

    bad = mutate(lambda m: m["materials"][3].update(color=""))
    expect("yeni malzemede renk yok", check_schema(bad, []))
    expect("zırhlı malzemenin kademesi yok", check_schema(mutate(lambda m: m["tiers"].pop("steel")), []))
    expect("cevher blokları kapı tablosunda yok", check_schema(mutate(lambda m: m["gates"].pop(1)), []))
    expect("vanilla id'li yeni malzeme (D6)", check_schema(mutate(lambda m: m["materials"][1].update(prefix="stonee")), []))
    gear = json.loads((GEN / "theoria/gear.json").read_text(encoding="utf-8"))
    langs = {"en_us": {}, "tr_tr": {}}
    e = []
    check_lang(gear, langs, e)
    expect("lang anahtarları eksik", e)
    # kapı çözümleyicisi: bir kapı tag'i silinirse kırmızı olmalı
    if vanilla.available():
        ours = load_block_tags(GEN)
        ours.pop("theoria:gate/4", None)
        e = []
        check_gates(model, resolve_tags(vanilla_block_tags(), ours), e)
        expect("gate/4 tag'i yok → demir ve ötesi kapı bozulur", e)
        ours = load_block_tags(GEN)
        ours["minecraft:needs_iron_tool"] = {"values": ours["minecraft:needs_iron_tool"].get("values", [])}   # `remove` düşer: kırmızıtaş vanilla kapısına döner
        e = []
        check_gates(model, resolve_tags(vanilla_block_tags(), ours), e)
        expect("kırmızıtaş needs_iron_tool'da kalırsa bakır kazma kapıda takılır", e)
    e = []
    check_hardness(model, {"gates": [{"ore": "iron", "tool_idx": 2, "blocks": [{"id": "minecraft:iron_ore", "hardness": 9.9}]}], "blocks": []}, e)
    expect("sertlik uyuşmazlığı", e)
    e = []
    broken = json.loads(json.dumps(model, default=str))
    broken["gates"] = [dict(g, tool_idx="13") if g["ore"] == "cobalt" else g for g in broken["gates"]]     # kobalt cevheri imkânsız bir kazma ister
    climb(broken, GEN, e)
    expect("kobalt cevheri imkânsız kazma isterse zincir kırılır", e)
    for name, ok in results:
        print(f"  {'PASS' if ok else 'FAIL'}  {name}: {'kırmızı verdi' if ok else 'YEŞİL KALDI (denetleyici kör)'}")
    return all(ok for _, ok in results), len(results)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--break", dest="broken", action="store_true", help="negatif test: bozuk girdiyle kırmızı beklenir")
    args = ap.parse_args()
    if args.broken:
        ok, n = break_tests()
        print(f"negatif testler: {'hepsi kırmızı verdi' if ok else 'BAŞARISIZ'} ({n} vaka)")
        return 0 if ok else 1
    run_checks()
    for note in notes:
        print("  " + note)
    if errors:
        print(f"HATA ({len(errors)}):")
        for e in errors[:60]:
            print("  - " + e)
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
