#!/usr/bin/env python3
"""Drop zinciri denetimi (Faz 8): merdivenin her öğesinin en az bir kaynağı var, her mob merdiveni besliyor.

    python3 -I tools/check_drops.py            # denetle, drop matrisini yaz (çıkış 0 = yeşil)
    python3 -I tools/check_drops.py --matrix   # + tür × merdiven öğesi matrisi
    python3 -I tools/check_drops.py --break    # negatif test: bozulmuş veriyle denetimler kırmızıya dönmeli

"Merdiven öğesi" (docs/05 §1, §3): kademe malzemelerinin girdileri (materials.csv), cevher damlaları (ores.csv), karbon tozu, deri, katalizör hammaddeleri
(toz/öz/çekirdek) ve katalizör imza malzemeleri (catalysts.csv). "Kaynak": cevher bloğu, mob damlası (data/mob_drops.csv, data/drops.csv, data/catalyst_drops.csv),
alaşım/eritme tarifi (girdileri de kaynaklı olmak şartıyla), sandık (çekirdek), ya da vanilla'da bulunabilen öğe (26.3 istemci jar'ındaki tarif sonuçları
ve loot tabloları taranır; jar yoksa vanilla öğeler "var" sayılır ve bu uyarılır).
Bantlar: kademe malzemesinin (kalay, kobalt, titanyum, mitril, orikalkum, platin, karbon tozu) en az bir MOB kaynağı, malzeme kademesinin ±2'sinde bir türden gelir.
"""
import csv, json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "generator"))
DATA = ROOT / "data"
NEAR = 2                      # mob kaynağı malzeme kademesine en çok bu kadar uzak olabilir
MIN_ESSENCE_SOURCES = 10      # öz: en az bu kadar farklı düşman
MIN_CORE_BOSSES = 3           # çekirdek: en az bu kadar boss (vanilla + yeni)
FORGE = {"dust": "theoria:catalyst_dust", "essence": "theoria:catalyst_essence", "core": "theoria:catalyst_core"}
CORE_CHEST_SOURCES = ("ancient_city", "woodland_mansion", "end_city_treasure", "bastion_treasure", "stronghold_library", "trial_chambers/reward_unique")   # generator/catalysts.py CORE_CHESTS


def read_csv(name):
    with open(DATA / name, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def load():
    """Denetimin tek veri kaynağı; --break bunu bozarak çağırır."""
    species = read_csv("mobs.csv")
    d = dict(
        species={r["id"]: dict(tier=int(r["tier"]), cls=r["cls"]) for r in species},
        mob_drops=read_csv("mob_drops.csv"),
        vanilla_drops=read_csv("drops.csv"),
        catalyst_drops=read_csv("catalyst_drops.csv"),
        ores=read_csv("ores.csv"),
        materials=read_csv("materials.csv"),
        alloys=read_csv("alloys.csv"),
        catalysts=read_csv("balance/catalysts.csv"),
        tiers={r["id"]: int(r["idx"]) for r in read_csv("balance/tiers.csv")},
    )
    return d


def vanilla_items():
    """26.3 jar'ında tarif sonucu ya da loot girdisi olan öğeler; jar yoksa None."""
    try:
        import vanilla
    except ImportError:
        return None
    if not vanilla.available():
        return None
    items = set()
    for name in vanilla.names("data/minecraft/recipe/"):
        raw = vanilla.read(name)
        try:
            j = json.loads(raw)
        except (ValueError, TypeError):
            continue
        res = j.get("result")
        if isinstance(res, dict) and "id" in res:
            items.add(res["id"])
        elif isinstance(res, str):
            items.add(res)
    for name in vanilla.names("data/minecraft/loot_table/"):
        raw = vanilla.read(name)
        for m in re.finditer(rb'"name"\s*:\s*"(minecraft:[a-z0-9_/]+)"', raw or b""):
            items.add(m.group(1).decode())
    return items


def entity_short(entity):
    return entity.split(":")[-1]


def analyse(d, vanilla=None):
    """→ (sources: item → [açıklama], mob_sources: item → [(tür, kademe)], ladder: küme, hatalar)."""
    sources, mob_sources, errors = {}, {}, []

    def add(item, why):
        sources.setdefault(item, []).append(why)

    # cevherler: damla + eritme (raw → malzeme girdisi)
    ore_of_item = {}
    for o in d["ores"]:
        drop = o["drop"] if ":" in o["drop"] else "theoria:" + o["drop"]
        add(drop, f"cevher:{o['id']}")
        ore_of_item[drop] = o["id"]
    for m in d["materials"]:
        ing = m["ingredient"]
        if ing.startswith("theoria:"):
            for o in d["ores"]:
                if o["id"] == m["id"]:
                    add(ing, f"eritme:{o['drop']}")
    # mob damlaları
    for row in d["mob_drops"]:
        chance = float(row["chance"])
        if not 0 < chance <= 1 or int(row["min"]) > int(row["max"]) or int(row["min"]) < 1:
            errors.append(f"mob_drops.csv {row['entity']}/{row['item']}: şans (0,1] ve 1 ≤ min ≤ max olmalı")
        add(row["item"], f"mob:{row['entity']}")
        tier = d["species"].get(row["entity"], {}).get("tier")
        if tier is None:
            errors.append(f"mob_drops.csv: tanımsız tür {row['entity']}")
        else:
            mob_sources.setdefault(row["item"], []).append((row["entity"], tier))
    for row in d["vanilla_drops"] + d["catalyst_drops"]:
        add(row["item"], f"mob:{entity_short(row['entity'])}")
    # katalizör hammaddeleri: toz cevherlerden (generator/catalysts.py loot değiştiricileri), çekirdek sandıklardan
    if d["ores"]:
        add(FORGE["dust"], "cevher:(ek damla)")
    for chest in CORE_CHEST_SOURCES:
        add(FORGE["core"], f"sandık:{chest}")
    # alaşımlar: girdilerin hepsi kaynaklıysa
    changed = True
    while changed:
        changed = False
        for a in d["alloys"]:
            ingredients = [part.partition("*")[0] for part in a["ingredients"].split()]
            if a["result"] not in sources and all(i in sources or (i.startswith("minecraft:") and (vanilla is None or i in vanilla)) for i in ingredients):
                add(a["result"], f"alaşım:{a['id']}")
                changed = True
    # merdiven
    ladder = set()
    for m in d["materials"]:
        if m["ingredient"]:
            ladder.add(m["ingredient"])
    ladder |= {o["drop"] if ":" in o["drop"] else "theoria:" + o["drop"] for o in d["ores"]}
    ladder |= set(FORGE.values()) | {"theoria:carbon_dust", "minecraft:leather"}
    ladder |= {c["signature"] for c in d["catalysts"] if c["signature"]}
    return sources, mob_sources, ladder, errors


def checks(d, vanilla=None):
    sources, mob_sources, ladder, errors = analyse(d, vanilla)
    out = []

    def add(name, ok, detail=""):
        out.append((name, bool(ok), detail))

    add("damla satırları geçerli (şans, min ≤ max, tanımlı tür)", not errors, "; ".join(errors[:3]))
    # 1) her merdiven öğesinin kaynağı var
    missing = []
    for item in sorted(ladder):
        if item in sources:
            continue
        if item.startswith("minecraft:") and (vanilla is None or item in vanilla):
            continue
        missing.append(item)
    add(f"merdivenin {len(ladder)} öğesinin hepsinin en az bir kaynağı var", not missing, f"kaynaksız: {missing}")
    # 2) her tür merdiveni besliyor
    feeders = {sp: [] for sp in d["species"]}
    for row in d["mob_drops"]:
        if row["item"] in ladder and row["entity"] in feeders:
            feeders[row["entity"]].append(row["item"])
    idle = [sp for sp, items in feeders.items() if not items]
    add(f"{len(d['species'])} türün her biri merdivenin en az bir öğesini düşürür", not idle, f"hiçbir şey düşürmeyen: {idle}")
    # 3) kademe malzemelerinin yakın bir mob kaynağı
    tier_of_item = {}
    for o in d["ores"]:
        if o["id"] in d["tiers"]:
            tier_of_item["theoria:" + o["drop"] if ":" not in o["drop"] else o["drop"]] = d["tiers"][o["id"]]
    tier_of_item["theoria:carbon_dust"] = d["tiers"].get("steel", 5)
    far = []
    for item, idx in sorted(tier_of_item.items()):
        if item == "theoria:lonsdaleit":
            continue   # elmas+karbon sentezi ve cevher; mob kaynağı gerekmez
        if not any(abs(t - idx) <= NEAR for _, t in mob_sources.get(item, [])):
            far.append(f"{item} (T{idx})")
    add(f"kademe malzemelerinin (±{NEAR} kademede) bir mob kaynağı var", not far, f"yakın mob kaynağı yok: {far}")
    # 4) öz ve çekirdek
    essence = {w for w in sources.get(FORGE["essence"], []) if w.startswith("mob:")}
    add(f"öz en az {MIN_ESSENCE_SOURCES} farklı düşmandan", len(essence) >= MIN_ESSENCE_SOURCES, f"{len(essence)} kaynak")
    cores = {w for w in sources.get(FORGE["core"], []) if w.startswith("mob:")}
    bosses = {sp for sp, v in d["species"].items() if v["cls"] == "boss"}
    add(f"çekirdek en az {MIN_CORE_BOSSES} boss'tan (vanilla + yeni)", len(cores) >= MIN_CORE_BOSSES, f"{sorted(cores)}")
    boss_core = [b for b in sorted(bosses) if not any(r["entity"] == b and r["item"] == FORGE["core"] and float(r["chance"]) >= 1.0 for r in d["mob_drops"])]
    add("her yeni boss garantili çekirdek düşürür", not boss_core, f"çekirdeksiz: {boss_core}")
    boss_heart = [b for b in sorted(bosses) if not any(r["entity"] == b and r["item"] == "theoria:heart_crystal" for r in d["mob_drops"])]
    add("her yeni boss Kalp Kristali düşürür", not boss_heart, f"kristalsiz: {boss_heart}")
    # 5) theoria: öğeleri tanımlı
    known_theoria = {"theoria:heart_crystal", "theoria:carbon_dust"} | set(FORGE.values())
    known_theoria |= {m["ingredient"] for m in d["materials"] if m["ingredient"].startswith("theoria:")}
    known_theoria |= {"theoria:" + o["drop"] for o in d["ores"] if ":" not in o["drop"]}
    known_theoria |= {a["result"] for a in d["alloys"]}
    unknown = sorted({r["item"] for r in d["mob_drops"] + d["vanilla_drops"] + d["catalyst_drops"] if r["item"].startswith("theoria:") and r["item"] not in known_theoria})
    add("damla satırlarındaki theoria: öğeleri tanımlı", not unknown, f"tanımsız: {unknown}")
    # 6) vanilla öğeler gerçekten vanilla'da var
    if vanilla is not None:
        bad = sorted({r["item"] for r in d["mob_drops"] + d["vanilla_drops"] + d["catalyst_drops"] if r["item"].startswith("minecraft:") and r["item"] not in vanilla})
        bad += sorted(i for i in ladder if i.startswith("minecraft:") and i not in vanilla and i not in sources)
        add("vanilla öğeler 26.3'te tarif sonucu ya da loot girdisi", not bad, f"vanilla'da bulunamadı: {sorted(set(bad))}")
    return out


def matrix(d):
    sources, mob_sources, ladder, _ = analyse(d, None)
    theoria_items = sorted(i for i in ladder if i.startswith("theoria:"))
    rows = {}
    for r in d["mob_drops"]:
        rows.setdefault(r["entity"], {})[r["item"]] = r["chance"]
    print("Drop matrisi (tür × kademe malzemesi / katalizör hammaddesi; hücre = şans):")
    header = [i.split(":")[1] for i in theoria_items]
    print(f"{'tür':18} {'kdm':>3} " + " ".join(f"{h[:12]:>12}" for h in header))
    for sp, v in sorted(d["species"].items(), key=lambda kv: (kv[1]["tier"], kv[0])):
        print(f"{sp:18} {v['tier']:>3} " + " ".join(f"{rows.get(sp, {}).get(i, ''):>12}" for i in theoria_items))
    print("Kaynak sayısı:", ", ".join(f"{i.split(':')[1]}={len(sources.get(i, []))}" for i in theoria_items))


def negative(vanilla):
    """Bilerek bozulmuş veriyle her denetim en az bir kez kırmızıya dönmeli."""
    def broken(mutator):
        d = load()
        mutator(d)
        return [n for n, ok, _ in checks(d, vanilla) if not ok]

    cases = [
        ("kalay damlaları silindi", lambda d: d.update(mob_drops=[r for r in d["mob_drops"] if r["item"] != "theoria:raw_tin"], ores=[o for o in d["ores"] if o["id"] != "tin"])),
        ("karbon tozu damlaları silindi", lambda d: d.update(mob_drops=[r for r in d["mob_drops"] if r["item"] != "theoria:carbon_dust"], vanilla_drops=[])),
        ("bir türün bütün damlaları silindi", lambda d: d.update(mob_drops=[r for r in d["mob_drops"] if r["entity"] != "steel_wolf"])),
        ("platin yalnız kademe 1 türünde", lambda d: d.update(mob_drops=[dict(r, entity="mud_walker") if r["item"] == "theoria:raw_platinum" else r for r in d["mob_drops"]])),
        ("öz kaynakları 3'e indi", lambda d: d.update(mob_drops=[r for r in d["mob_drops"] if r["item"] != FORGE["essence"] or r["entity"] in ("mud_king", "storm_drake", "platinum_colossus")], catalyst_drops=[])),
        ("boss çekirdeği şanssız", lambda d: d.update(mob_drops=[dict(r, chance="0.5") if r["entity"] == "mud_king" and r["item"] == FORGE["core"] else r for r in d["mob_drops"]])),
        ("boss Kalp Kristali yok", lambda d: d.update(mob_drops=[r for r in d["mob_drops"] if r["item"] != "theoria:heart_crystal"])),
        ("tanımsız tür damlası", lambda d: d.update(mob_drops=d["mob_drops"] + [dict(entity="yok_boyle_tur", item="minecraft:leather", chance="0.5", min="1", max="1", looting="0", note="")])),
        ("imza malzemesi kaynaksız", lambda d: d.update(catalysts=d["catalysts"] + [dict(id="x", signature="theoria:never_obtainable")])),
    ]
    green = 0
    for name, mutate in cases:
        red = broken(mutate)
        print(f"  {'✅ kırmızı' if red else '❌ YEŞİL KALDI'}: {name} → {len(red)} denetim düştü")
        green += 0 if red else 1
    print("OK" if not green else f"HATA ({green} bozulma yakalanmadı)")
    return 1 if green else 0


def main(argv):
    vanilla = vanilla_items()
    if vanilla is None:
        print("UYARI: 26.3 istemci jar'ı bulunamadı — vanilla öğelerin varlığı denetlenmedi (THEORIA_VANILLA_JAR).")
    if "--break" in argv:
        return negative(vanilla)
    d = load()
    results = checks(d, vanilla)
    for name, ok, detail in results:
        print(f"  {'✅' if ok else '❌'} {name}" + (f" — {detail}" if detail and not ok else ""))
    if "--matrix" in argv:
        matrix(d)
    failed = [r for r in results if not r[1]]
    print("OK" if not failed else f"HATA ({len(failed)})")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
