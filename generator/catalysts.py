#!/usr/bin/env python3
"""Katalizör üreteci (Faz 7b): tools/balance/catalysts.py (sayı) + data/balance/tiers.csv → oyunun okuduğu kaynak/veri dosyaları.
Elle düzenlenmez; git'e girer. Sayıların tek kaynağı tools/balance/catalysts.py'dir; bu üreteç yalnız biçim üretir (yeni katalizör = oradaki bir satır).

    python3 -I generator/catalysts.py            # yaz (artık dosyaları siler)
    python3 -I generator/catalysts.py --check    # yazmadan: depodakiler üretilecekle aynı mı (PNG'ler piksel düzeyinde); fark varsa çıkış 1
    python3 -I generator/catalysts.py --list     # üretilen dosya sayıları

ÇIKTILAR (mods-src/theoria/src/generated/catalysts/ — gear.py'nin `generated/resources` klasöründen AYRI, yoksa gear.py bunları "artık" sayıp silerdi):
  assets/theoria/{items,models,blockstates,textures}/   31 katalizör + 3 hammadde + Kataliz Ocağı: item/blok tanımı, model, boyanmış doku
  data/theoria/enchantment/                              14 büyü: 13 sanal (ADR-006 Karar 6) + gerçek `theoria:erudition`
  data/theoria/{recipe,loot_table,loot_modifiers}/       ocak tarifi, ocak loot'u, geçici hammadde kaynakları (toz/öz/çekirdek)
  data/minecraft/tags/enchantment/non_treasure.json      Bilgelik büyü masasından/ticaretten/ganimetten çıkabilsin
Lang: src/main/resources/assets/theoria/lang/{en_us,tr_tr}.json içinde bu üretecin sahip olduğu anahtarlar (OWNED) birleştirilir; geri kalanı yerinde kalır.
Dokular vanilla 26.3 jar'ından türetilir (generator/vanilla.py), gear ile aynı 'pişirilmiş renk' yöntemi (gear_art.py).
"""
import colorsys
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "tools/balance"))
import catalysts as C  # noqa: E402  (tools/balance/catalysts.py: sayıların tek kaynağı)
import gear_art  # noqa: E402
import pngio  # noqa: E402
import tiers as M  # noqa: E402
import vanilla  # noqa: E402

ROOT = HERE.parent
GEN = ROOT / "mods-src/theoria/src/generated/catalysts"
LANG = ROOT / "mods-src/theoria/src/main/resources/assets/theoria/lang"
OWNED = ("item.theoria.catalyst", "block.theoria.catalyst_forge", "itemGroup.theoria.catalysts", "container.theoria.catalyst_forge",
         "theoria.catalyst.", "enchantment.theoria.", "attribute.name.theoria.")

# Geçici hammadde kaynakları (ADR-006 Karar 11: oran/şans 7b'de ayarlanır, Faz 8/9 yeniden düzenler)
DUST_ORES = ["coal", "iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald"]          # + deepslate varyantları (coal/emerald hariç)
CATALYST_DROPS_CSV = ROOT / "data/catalyst_drops.csv"   # Faz 8: vanilla düşman/boss damlaları (öz/çekirdek) veri olarak; yeni türlerin damlaları data/mob_drops.csv
CORE_CHESTS = ["ancient_city", "woodland_mansion", "end_city_treasure", "bastion_treasure", "stronghold_library", "trial_chambers/reward_unique"]
DUST_CHANCE, DUST_MAX = 0.35, 2
CORE_CHEST_CHANCE = 0.25


def dumps(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


# ------------------------------------------------------------------------------------------------ renk ve doku
def hue(index, count):
    r, g, b = colorsys.hsv_to_rgb(index / count, 0.62, 0.96)
    return round(r * 255), round(g * 255), round(b * 255)


TEMPLATE = {C.A: "item/diamond", C.B: "item/amethyst_shard", C.R: "item/emerald", C.D: "item/lapis_lazuli"}


def catalyst_defs():
    return [c for c in C.CATALYSTS if c[1] != C.C]       # Netherite = vanilla külçe, öğe yok


def textures():
    out = {}
    defs = catalyst_defs()
    for i, c in enumerate(defs):
        out[f"assets/theoria/textures/item/catalyst/{c[0]}.png"] = gear_art.metal_icon(TEMPLATE[c[1]], hue(i, len(defs)), spread=True, hilite=0.55)
    out["assets/theoria/textures/item/catalyst_dust.png"] = gear_art.metal_icon("item/gunpowder", (158, 104, 214), spread=True, hilite=0.45)
    out["assets/theoria/textures/item/catalyst_essence.png"] = gear_art.metal_icon("item/glowstone_dust", (96, 200, 214), spread=True, hilite=0.5)
    out["assets/theoria/textures/item/catalyst_core.png"] = gear_art.metal_icon("item/echo_shard", (232, 190, 70), spread=True, hilite=0.55)
    out["assets/theoria/textures/block/catalyst_forge.png"] = gear_art.metal_icon("block/polished_blackstone", (150, 90, 200), spread=True, hilite=0.4)
    return out


def item_definition(model):
    return dumps({"model": {"type": "minecraft:model", "model": model}})


def flat_model(texture):
    return dumps({"parent": "minecraft:item/generated", "textures": {"layer0": texture}})


def assets():
    out = {}
    for c in catalyst_defs():
        out[f"assets/theoria/items/catalyst/{c[0]}.json"] = item_definition(f"theoria:item/catalyst/{c[0]}")
        out[f"assets/theoria/models/item/catalyst/{c[0]}.json"] = flat_model(f"theoria:item/catalyst/{c[0]}")
    for name in ("catalyst_dust", "catalyst_essence", "catalyst_core"):
        out[f"assets/theoria/items/{name}.json"] = item_definition(f"theoria:item/{name}")
        out[f"assets/theoria/models/item/{name}.json"] = flat_model(f"theoria:item/{name}")
    out["assets/theoria/blockstates/catalyst_forge.json"] = dumps({"variants": {"": {"model": "theoria:block/catalyst_forge"}}})
    out["assets/theoria/models/block/catalyst_forge.json"] = dumps({"parent": "minecraft:block/cube_all", "textures": {"all": "theoria:block/catalyst_forge"}})
    out["assets/theoria/items/catalyst_forge.json"] = item_definition("theoria:block/catalyst_forge")
    out.update(textures())
    return out


# ------------------------------------------------------------------------------------------------ büyüler (datapack)
WEAPON_ITEMS = "#minecraft:enchantable/melee_weapon"
ANY_ITEMS = "#minecraft:enchantable/durability"


def enchantment(eid, supported, slots, effects=None, max_level=1, weight=1, cost=(1, 0), anvil=2):
    d = {"anvil_cost": anvil, "description": {"translate": f"enchantment.theoria.{eid}"}, "max_cost": {"base": cost[0] + 20, "per_level_above_first": cost[1]},
         "max_level": max_level, "min_cost": {"base": cost[0], "per_level_above_first": cost[1]}, "slots": slots, "supported_items": supported, "weight": weight}
    if effects:
        d["effects"] = effects
    return dumps(d)


def lookup(cid, target):
    """Menzil Gücü için LevelBasedValue.lookup: P = 1 + 5g + (L-1) → values[P-1] (ADR-006 Karar 6). g = derece (0..13); tabloda olmayan hücre 0."""
    values = []
    for g in range(M.N):
        for lv in range(1, C.LEVELS + 1):
            values.append(round(C.value(cid, target, g, lv), 4) if g >= C.by_id()[cid][9] else 0.0)
    return {"type": "minecraft:lookup", "values": values, "fallback": 0.0}   # LevelBasedValue sabiti düz sayıdır ("minecraft:constant" tipi yok)


def enchantments():
    out = {}
    base = "data/theoria/enchantment/"
    for c in C.CATALYSTS:
        cid, kind = c[0], c[1]
        if kind == C.B and cid != "restoration":
            out[f"{base}{cid}.json"] = enchantment(cid, WEAPON_ITEMS, ["mainhand"])      # taşıyıcı: etkisi Java'da (ElementEffects)
        elif cid == "restoration":
            out[f"{base}{cid}.json"] = enchantment(cid, ANY_ITEMS, ["any"])             # etkisi Java'da (Restoration)
        elif kind == C.R:
            effects = {"minecraft:damage": [{"effect": {"type": "minecraft:add", "value": lookup(cid, "ranged")}}]}
            out[f"{base}{cid}.json"] = enchantment(cid, "#minecraft:enchantable/weapon", ["mainhand"], effects)
        elif kind == C.D:
            # GERÇEK büyü (büyü masası/örs/ganimet): etkisi Java'da (Erudition); V seviyeye kadar alınabilir, Netherite ile VI.
            out[f"{base}{cid}.json"] = enchantment(cid, ANY_ITEMS, ["any"], max_level=5, weight=2, cost=(12, 9), anvil=4)
    out["data/minecraft/tags/enchantment/non_treasure.json"] = dumps({"values": ["theoria:erudition"]})
    return out


# ------------------------------------------------------------------------------------------------ tarif ve loot
def recipes():
    return {"data/theoria/recipe/catalyst_forge.json": dumps({
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:iron_ingot"] * 4 + ["minecraft:obsidian"] * 2 + ["minecraft:diamond", "minecraft:lava_bucket"],
        "result": {"id": "theoria:catalyst_forge"}})}


def uniform(lo, hi):
    return {"type": "minecraft:uniform", "min": lo, "max": hi}


def table_item(item, chance, lo, hi, looting=False):
    modifier = [{"type": "minecraft:set_count", "count": uniform(lo, hi)}]
    if looting:
        modifier.append({"type": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting", "count": uniform(0.0, 1.0)})
    return {"type": "minecraft:generic", "pools": [{"rolls": 1, "condition": {"type": "minecraft:random_chance", "chance": chance},
                                                   "entries": [{"type": "minecraft:item", "name": item, "modifier": modifier}]}]}


def add_table(target, table):
    return dumps({"type": "neoforge:add_table", "condition": {"type": "neoforge:loot_table_id", "loot_table_id": target}, "table": table})


def catalyst_drops():
    import csv
    with open(CATALYST_DROPS_CSV, newline="", encoding="utf-8") as f:
        return [dict(entity=r["entity"], item=r["item"], chance=float(r["chance"]), min=int(r["min"]), max=int(r["max"]), looting=r["looting"] == "1") for r in csv.DictReader(f)]


def loot():
    out = {}
    out["data/theoria/loot_table/blocks/catalyst_forge.json"] = dumps({
        "type": "minecraft:block", "random_sequence": "theoria:blocks/catalyst_forge",
        "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"}, "entries": [{"type": "minecraft:item", "name": "theoria:catalyst_forge"}]}]})
    # Toz: cevher bloklarından ek damla. İpek dokunuşuyla kırılan cevher toz vermez (blok kendisi düşer).
    dust = {"type": "minecraft:block", "pools": [{"rolls": 1, "condition": {"type": "minecraft:all_of", "terms": [
        {"type": "minecraft:random_chance", "chance": DUST_CHANCE}, {"type": "minecraft:inverted", "term": "minecraft:tool/can_silk_touch"}]},
        "entries": [{"type": "minecraft:item", "name": "theoria:catalyst_dust", "modifier": [{"type": "minecraft:set_count", "count": uniform(1, DUST_MAX)}]}]}]}
    out["data/theoria/loot_table/catalyst/dust_from_ore.json"] = dumps(dust)
    ores = []
    for o in DUST_ORES:
        ores.append(f"minecraft:blocks/{o}_ore")
        if o not in ("coal", "emerald"):
            ores.append(f"minecraft:blocks/deepslate_{o}_ore")
    ores += ["minecraft:blocks/deepslate_coal_ore", "minecraft:blocks/deepslate_emerald_ore", "minecraft:blocks/nether_gold_ore", "minecraft:blocks/nether_quartz_ore"]
    ores += [f"theoria:blocks/{n}" for n in our_ore_blocks()]
    for target in sorted(set(ores)):
        out[f"data/theoria/loot_modifiers/catalyst_dust_{target.split(':')[0]}_{target.split('/')[-1]}.json"] = add_table(target, "theoria:catalyst/dust_from_ore")
    # Vanilla düşman/boss damlaları (öz, çekirdek): data/catalyst_drops.csv satır başına bir tablo + bir yükleme değiştirici
    for d in catalyst_drops():
        short, name = d["entity"].split(":")[1], d["item"].split(":")[1]
        table = table_item(d["item"], d["chance"], d["min"], d["max"], looting=d["looting"]) | {"type": "minecraft:entity"}
        out[f"data/theoria/loot_table/catalyst/{name}_from_{short}.json"] = dumps(table)
        out[f"data/theoria/loot_modifiers/catalyst_{name.removeprefix('catalyst_')}_{short}.json"] = add_table(f"{d['entity'].split(':')[0]}:entities/{short}", f"theoria:catalyst/{name}_from_{short}")
    out["data/theoria/loot_table/catalyst/core_from_chest.json"] = dumps(table_item("theoria:catalyst_core", CORE_CHEST_CHANCE, 1, 1) | {"type": "minecraft:chest"})
    for chest in CORE_CHESTS:
        out[f"data/theoria/loot_modifiers/catalyst_core_{chest.replace('/', '_')}.json"] = add_table(f"minecraft:chests/{chest}", "theoria:catalyst/core_from_chest")
    return out


def our_ore_blocks():
    """Gear'ın cevher blokları (generator/gear_model.py ile aynı kural): ores.csv varyantlarından."""
    import gear_model as G
    return [name for _, name, _ in G.ore_blocks(G.build())]


# ------------------------------------------------------------------------------------------------ lang
HOST_EN = {"weapon": "weapons", "tool": "tools", "ranged": "bows and heavy weapons", "head": "helmets", "chest": "chestplates", "legs": "leggings", "feet": "boots"}
HOST_TR = {"weapon": "silahlar", "tool": "aletler", "ranged": "yay ve ağır silahlar", "head": "kasklar", "chest": "göğüslükler", "legs": "pantolonlar", "feet": "botlar"}
DESC_EN = {
    "frost": "Adds frost damage per hit and slows the target (3 s).", "fire": "Adds fire damage per hit and sets the target ablaze.",
    "lightning": "Adds lightning damage and chains to 2 more enemies nearby.", "poison": "Poisons the target: the damage lands over 5 seconds.",
    "wither": "Withers the target: the damage lands over 5 seconds.", "cleave": "A share of every hit also strikes enemies within 2 blocks of the target.",
    "bleed": "The target bleeds: the damage lands over 6 seconds. Does not stack.", "radiance": "Extra damage, against undead only.",
    "umbra": "Extra damage when you strike from behind.", "tempest": "Extra damage while you are airborne.",
    "gravity": "Pulls the struck target towards you.", "restoration": "Mends the item over time, also while you carry it.",
    "ranged_power": "Extra damage for bows, crossbows, tridents and maces. Made with an ingot: its tier is the catalyst's grade.",
    "erudition": "More experience from kills and mined blocks (adds up over held item and armor, capped).",
}
DESC_TR = {
    "frost": "Her vuruşa buz hasarı ekler ve hedefi yavaşlatır (3 sn).", "fire": "Her vuruşa ateş hasarı ekler ve hedefi tutuşturur.",
    "lightning": "Yıldırım hasarı ekler ve yakındaki 2 düşmana daha zincirler.", "poison": "Hedefi zehirler: hasar 5 saniyeye yayılır.",
    "wither": "Hedefi soldurur: hasar 5 saniyeye yayılır.", "cleave": "Her vuruşun bir kısmı hedefin 2 blok çevresindeki düşmanlara da isabet eder.",
    "bleed": "Hedef kanar: hasar 6 saniyeye yayılır. Üst üste binmez.", "radiance": "Ekstra hasar; yalnız ölümsüzlere.",
    "umbra": "Arkadan vurduğunda ekstra hasar.", "tempest": "Havadayken ekstra hasar.",
    "gravity": "Vurulan hedefi sana doğru çeker.", "restoration": "Eşyayı zamanla onarır; üzerinde taşırken de.",
    "ranged_power": "Yay, arbalet, üçlü mızrak ve gürz için ekstra hasar. Bir külçeyle yapılır: külçenin kademesi katalizörün derecesidir.",
    "erudition": "Öldürme ve blok kırma XP'sini artırır (elde tutulan + zırh toplanır, tavanlı).",
}
PROBLEM_EN = {
    "no_target": "Put an item to upgrade in the left slot.", "not_host": "This item cannot carry catalysts.", "no_input": "Put catalysts or a Netherite ingot in the input slots.",
    "not_a_catalyst": "Only catalysts and Netherite ingots go in the input slots.", "wrong_class": "This catalyst does not fit this kind of item.",
    "tier_too_low": "This item's tier is too low for this catalyst.", "no_slot": "No free catalyst slot — remove one with ×.",
    "not_better": "The item already has this catalyst at the same or a higher level.", "netherite_no_enchant": "Netherite needs an item that has enchantments.",
    "netherite_no_gain": "No enchantment on this item would gain a level from Netherite.", "netherite_full": "This item has had Netherite applied the most times it can.",
    "netherite_ingots": "Not enough Netherite ingots (needs %d in one stack).",
}
PROBLEM_TR = {
    "no_target": "Sol yuvaya geliştirilecek eşyayı koy.", "not_host": "Bu eşya katalizör taşıyamaz.", "no_input": "Girdi yuvalarına katalizör ya da Netherite külçesi koy.",
    "not_a_catalyst": "Girdi yuvalarına yalnız katalizör ve Netherite külçesi konur.", "wrong_class": "Bu katalizör bu tür eşyaya uymuyor.",
    "tier_too_low": "Eşyanın kademesi bu katalizör için çok düşük.", "no_slot": "Boş katalizör yuvası yok — × ile birini çıkar.",
    "not_better": "Eşyada bu katalizör zaten aynı ya da daha yüksek seviyede.", "netherite_no_enchant": "Netherite için büyülü bir eşya gerekir.",
    "netherite_no_gain": "Bu eşyadaki hiçbir büyü Netherite ile seviye kazanmaz.", "netherite_full": "Bu eşyaya en fazla sayıda Netherite uygulanmış.",
    "netherite_ingots": "Yeterli Netherite külçesi yok (tek yığında %d gerekir).",
}
FORGE_EN = {
    "no_input": "Put raw materials in the input slots to forge a catalyst.", "no_signature": "A catalyst needs its signature material (e.g. blaze powder for Might).",
    "ambiguous_signature": "Use only one signature material at a time.", "insufficient": "Not enough materials for level I.",
    "no_grade_ingot": "Menzil Gücü also needs one ingot: its tier is the catalyst's grade.", "foreign_item": "Only catalyst materials go in the input slots.",
}
FORGE_TR = {
    "no_input": "Katalizör üretmek için girdi yuvalarına hammadde koy.", "no_signature": "Katalizör için imza malzemesi gerekir (örn. Güç için alev tozu).",
    "ambiguous_signature": "Aynı anda tek imza malzemesi kullan.", "insufficient": "I. seviye için malzeme yetmiyor.",
    "no_grade_ingot": "Menzil Gücü bir külçe de ister: külçenin kademesi katalizörün derecesidir.", "foreign_item": "Girdi yuvalarına yalnız katalizör hammaddesi konur.",
}


def lang():
    en, tr = {}, {}
    en["itemGroup.theoria.catalysts"], tr["itemGroup.theoria.catalysts"] = "Theoria: Catalysts", "Theoria: Katalizörler"
    en["block.theoria.catalyst_forge"], tr["block.theoria.catalyst_forge"] = "Catalyst Forge", "Kataliz Ocağı"
    en["container.theoria.catalyst_forge"], tr["container.theoria.catalyst_forge"] = "Catalyst Forge", "Kataliz Ocağı"
    for key, a, b in (("catalyst_dust", "Catalyst Dust", "Katalizör Tozu"), ("catalyst_essence", "Catalyst Essence", "Katalizör Özü"), ("catalyst_core", "Catalyst Core", "Katalizör Çekirdeği")):
        en[f"item.theoria.{key}"], tr[f"item.theoria.{key}"] = a, b
    for c in catalyst_defs():
        en[f"item.theoria.catalyst.{c[0]}"], tr[f"item.theoria.catalyst.{c[0]}"] = f"{c[3]} Catalyst", f"{c[2]} Katalizörü"
        if c[1] in (C.B, C.R, C.D):
            en[f"enchantment.theoria.{c[0]}"], tr[f"enchantment.theoria.{c[0]}"] = c[3], c[2]
            en[f"theoria.catalyst.desc.{c[0]}"], tr[f"theoria.catalyst.desc.{c[0]}"] = DESC_EN[c[0]], DESC_TR[c[0]]
    for name, a, b in (("crit_chance", "Critical Chance", "Kritik Şansı"), ("crit_damage", "Critical Damage", "Kritik Hasar"), ("life_steal", "Life Steal", "Yaşam Çalma"),
                       ("thorns", "Thorns", "Diken"), ("food_saturation", "Food Saturation", "Yemek Satürasyonu")):
        en[f"attribute.name.theoria.{name}"], tr[f"attribute.name.theoria.{name}"] = a, b
    for k, v in PROBLEM_EN.items():
        en[f"theoria.catalyst.problem.{k}"] = v
    for k, v in PROBLEM_TR.items():
        tr[f"theoria.catalyst.problem.{k}"] = v
    for k, v in FORGE_EN.items():
        en[f"theoria.catalyst.forge.problem.{k}"] = v
    for k, v in FORGE_TR.items():
        tr[f"theoria.catalyst.forge.problem.{k}"] = v
    for row in M.ARMOR:     # kademe adları (ipucu satırlarında "en düşük eşya kademesi" ve derece)
        en[f"theoria.catalyst.tier.{row[0]}"], tr[f"theoria.catalyst.tier.{row[0]}"] = row[1].capitalize(), row[2]
    for k, v in HOST_EN.items():
        en[f"theoria.catalyst.host.{k}"] = v
    for k, v in HOST_TR.items():
        tr[f"theoria.catalyst.host.{k}"] = v
    ui = {
        "slots": ("Catalysts %d/%d", "Katalizörler %d/%d"), "slot.empty": ("— empty —", "— boş —"), "slot.locked": ("locked", "kilitli"),
        "apply": ("Apply", "Uygula"), "forge": ("Forge", "Üret"), "remove": ("Take out (no refund)", "Çıkar (iade yok)"),
        "preview.title": ("Preview", "Önizleme"), "preview.nothing": ("Nothing to apply yet.", "Henüz uygulanacak bir şey yok."),
        "line.add": ("+ %s", "+ %s"), "line.upgrade": ("▲ %s → %s", "▲ %s → %s"), "line.netherite": ("%s → %s (Netherite)", "%s → %s (Netherite)"),
        "line.make": ("Forge: %s", "Üret: %s"), "line.cost": ("%s: %s", "%s: %s"), "line.next": ("Next: %s", "Sonraki: %s"),
        "cost.dust": ("%d dust", "%d toz"), "cost.essence": ("%d essence", "%d öz"), "cost.core": ("%d core", "%d çekirdek"), "cost.signature": ("%d × %s", "%d × %s"),
        "tooltip.fits": ("Fits: %s", "Uyar: %s"), "tooltip.min_tier": ("Minimum item tier: %s", "En düşük eşya kademesi: %s"),
        "tooltip.grade": ("Grade: %s", "Derece: %s"), "tooltip.applied_at": ("Applied in the Catalyst Forge", "Kataliz Ocağı'nda uygulanır"),
        "tooltip.forge_cost": ("Forge cost: %s", "Üretim maliyeti: %s"), "tooltip.slots": ("Catalysts %d/%d", "Katalizörler %d/%d"),
        "tooltip.innate": ("Innate: %s", "Doğuştan: %s"), "tooltip.netherite": ("Netherite ×%d", "Netherite ×%d"),
        "tooltip.raw_material": ("Catalyst material — used in the Catalyst Forge", "Katalizör hammaddesi — Kataliz Ocağı'nda kullanılır"),
        "tooltip.signature": ("Signature material of the %s catalyst", "%s katalizörünün imza malzemesi"),
        "tooltip.value": ("%s", "%s"),
    }
    for k, (a, b) in ui.items():
        en[f"theoria.catalyst.{k}"], tr[f"theoria.catalyst.{k}"] = a, b
    return en, tr


def merge_lang(existing_text, generated):
    current = json.loads(existing_text)
    merged = {k: v for k, v in current.items() if not k.startswith(OWNED)}
    merged.update(generated)
    return dumps(dict(sorted(merged.items())))   # gear_assets.merge_lang ile aynı kanonik sıra


# ------------------------------------------------------------------------------------------------ çıktı
def outputs():
    rel = {}
    for part in (assets(), enchantments(), recipes(), loot()):
        for path, content in part.items():
            if path in rel:
                raise SystemExit(f"catalysts: iki parça aynı dosyayı yazıyor: {path}")
            rel[path] = content
    files = {GEN / p: c for p, c in rel.items()}
    en, tr = lang()
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


def main(argv):
    check = "--check" in argv
    if not vanilla.available():
        if not check:
            raise SystemExit("catalysts: vanilla jar bulunamadı — THEORIA_VANILLA_JAR ayarla (bkz. generator/vanilla.py)")
        gear_art.metal_icon = lambda *a, **k: b""     # jar yok: dokular denetlenmez
        print("catalysts: vanilla jar yok — PNG'ler denetlenmedi", file=sys.stderr)
    files = outputs()
    if not vanilla.available():
        files = {p: c for p, c in files.items() if p.suffix != ".png"}
    stale = [p for p, c in files.items() if differs(p, c)]
    owned = {p for p in files if GEN in p.parents}
    orphans = [p for p in sorted(GEN.rglob("*")) if p.is_file() and p not in owned] if GEN.exists() and vanilla.available() else []
    if "--list" in argv:
        kinds = {}
        for p in owned:
            kinds[p.suffix] = kinds.get(p.suffix, 0) + 1
        print(f"{len(owned)} dosya", dict(sorted(kinds.items())))
    if check:
        for p in stale + orphans:
            print(f"BAYAT: {p.relative_to(ROOT)} → python3 -I generator/catalysts.py")
        return 1 if stale or orphans else 0
    for p in stale:
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(files[p]) if isinstance(files[p], bytes) else p.write_text(files[p], encoding="utf-8")
    for p in orphans:
        p.unlink()
    if GEN.exists():
        for d in sorted((q for q in GEN.rglob("*") if q.is_dir()), reverse=True):
            if not any(d.iterdir()):
                d.rmdir()
    print(f"catalysts: {len(owned)} dosya ({len(stale)} yazıldı, {len(orphans)} artık silindi)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
