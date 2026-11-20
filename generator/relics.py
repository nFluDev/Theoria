#!/usr/bin/env python3
"""Relic generator (Faz 12): data/relics.csv + data/relic_sources.csv (+ data/curios_slots.csv) → everything the game reads for relics. Never edited by hand; checked in.

    python3 -I generator/relics.py            # write (removes stray files)
    python3 -I generator/relics.py --check    # write nothing: are the repo's files what would be generated? (PNGs compared pixel for pixel); exit 1 if not
    python3 -I generator/relics.py --list     # file counts

OUTPUT (mods-src/theoria/src/generated/relics/ — its own folder, like generator/catalysts.py, so no other generator treats these files as strays):
  theoria/relics.json                                   the table the game reads from the jar (relics, kinds, parameters, sources) — Java has no relic numbers of its own
  assets/theoria/{items,models/item,textures/item}/relic/<id>.*   25 item definitions, flat models, drawn icons (generator/relic_art.py)
  data/curios/tags/item/<slot>.json                     #curios:<slot> membership — this tag is what lets a relic into a Curios slot
  data/theoria/loot_table/relics/<source>.json          one weighted pool per source (chest or boss)
  data/theoria/loot_modifiers/relics_<source>.json      neoforge:add_table onto the source's target table (the same hook generator/catalysts.py uses)
Lang: the keys this generator OWNS (OWNED prefixes) are merged into src/main/resources/assets/theoria/lang/{en_us,tr_tr}.json; everything else stays.
"""
import csv
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import pngio  # noqa: E402,F401  (relic_art imports it; kept so a broken install fails here with a clear message)
import relic_art  # noqa: E402

ROOT = HERE.parent
DATA = ROOT / "data"
GEN = ROOT / "mods-src/theoria/src/generated/relics"
LANG = ROOT / "mods-src/theoria/src/main/resources/assets/theoria/lang"
OWNED = ("item.theoria.relic.", "theoria.relic.", "itemGroup.theoria.relics")

RARITIES = ["common", "uncommon", "rare", "legendary"]
KINDS = {  # kind → (required params, number of values the description shows)
    "effect": ({"effect", "amp"}, lambda r: 0),
    "attribute": ({"mod", "fmt"}, lambda r: 0 if r["params"]["fmt"] == "none" else len(r["mods"])),
    "magnet": ({"radius", "pull"}, lambda r: 1),
    "food": ({"interval", "food", "saturation"}, lambda r: 1),
    "repair": ({"interval", "amount"}, lambda r: 1),
    "immune": ({"damage", "factor"}, lambda r: 1),
    "reveal": ({"radius", "interval", "duration"}, lambda r: 1),
    "survive": ({"cooldown", "heal"}, lambda r: 2),
}
OPS = {"add": "add_value", "mult_base": "add_multiplied_base", "mult_total": "add_multiplied_total"}
DAMAGE_TAGS = ["minecraft:is_fire", "minecraft:is_explosion", "minecraft:is_freezing", "minecraft:is_lightning"]
SOURCE_TYPES = ["chest", "boss"]
ID_RE = re.compile(r"^[a-z][a-z0-9_]*$")
RES_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_/.-]+$")

CHEST_NAMES = {"ruin": ("Ruins", "Harabeler"), "haven_large": ("Large Havens", "Büyük Sığınaklar"), "dungeon": ("Dungeons", "Zindanlar"), "keep": ("Keeps", "Kaleler")}


def dumps(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


# ------------------------------------------------------------------------------------------------------------------------------ read + validate
def read_csv(path):
    with path.open(encoding="utf-8", newline="") as fh:
        lines = [line for line in fh if not line.lstrip().startswith("#") and line.strip()]
    return list(csv.DictReader(lines))


def number(text):
    value = float(text)
    return int(value) if value == int(value) and "." not in text else value


def parse_params(text):
    out = {}
    for part in text.split(";"):
        key, _, value = part.partition("=")
        out[key.strip()] = value.strip()
    return out


def slot_ids():
    return [r["id"] for r in read_csv(DATA / "curios_slots.csv")]


def read_sources():
    return [{"id": r["source"].strip(), "type": r["type"].strip(), "target": r["target"].strip(), "chance": float(r["chance"])} for r in read_csv(DATA / "relic_sources.csv")]


def read_relics():
    relics = []
    for index, r in enumerate(read_csv(DATA / "relics.csv")):
        raw = parse_params(r["params"])
        entry = {"id": r["id"].strip(), "slot": r["slot"].strip(), "rarity": r["rarity"].strip(), "kind": r["kind"].strip(), "raw": raw, "index": index,
                 "en": r["en"].strip(), "tr": r["tr"].strip(), "desc_en": r["desc_en"].strip(), "desc_tr": r["desc_tr"].strip(), "art": r["art"].strip(), "mods": [], "sources": []}
        entry["params"] = {k: (v if k in ("effect", "damage", "fmt", "mod") else number(v)) for k, v in raw.items() if k != "mod"}
        if "fmt" in raw:
            entry["params"]["fmt"] = raw["fmt"]
        for spec in raw.get("mod", "").split("|") if raw.get("mod") else []:
            attribute, _, rest = spec.rpartition(":")  # attribute id contains ':' — split from the right: op, amount
            attribute, _, op = attribute.rpartition(":")
            entry["mods"].append({"attribute": attribute, "op": OPS.get(op, op), "amount": number(rest)})
        for pair in r["sources"].split("|"):
            source, _, weight = pair.partition(":")
            entry["sources"].append({"source": source.strip(), "weight": int(weight)})
        relics.append(entry)
    return relics


def validate(relics, sources):
    errors = []
    slots = set(slot_ids())
    source_ids = {s["id"] for s in sources}
    seen = set()
    for s in sources:
        if s["type"] not in SOURCE_TYPES:
            errors.append(f"relic_sources.csv: {s['id']} type '{s['type']}' is not one of {SOURCE_TYPES}")
        if not 0.0 < s["chance"] <= 1.0:
            errors.append(f"relic_sources.csv: {s['id']} chance {s['chance']} outside (0, 1]")
        if not RES_RE.match(s["target"]):
            errors.append(f"relic_sources.csv: {s['id']} target '{s['target']}' is not a resource id")
    if len({s["id"] for s in sources}) != len(sources):
        errors.append("relic_sources.csv: a source appears twice")
    for r in relics:
        where = f"relics.csv: {r['id']}"
        if not ID_RE.match(r["id"]) or r["id"] in seen:
            errors.append(f"{where}: id must be unique snake_case")
        seen.add(r["id"])
        if r["slot"] not in slots:
            errors.append(f"{where}: slot '{r['slot']}' is not in curios_slots.csv {sorted(slots)}")
        if r["rarity"] not in RARITIES:
            errors.append(f"{where}: rarity '{r['rarity']}' is not one of {RARITIES}")
        if r["kind"] not in KINDS:
            errors.append(f"{where}: kind '{r['kind']}' is not one of {sorted(KINDS)}")
            continue
        required, shows = KINDS[r["kind"]]
        missing = required - set(r["raw"])
        if missing:
            errors.append(f"{where}: kind {r['kind']} needs parameters {sorted(missing)}")
            continue
        if r["kind"] == "attribute":
            if r["params"]["fmt"] not in ("pct", "num", "none"):
                errors.append(f"{where}: fmt must be pct, num or none")
            if not r["mods"]:
                errors.append(f"{where}: attribute relic without a modifier")
            for m in r["mods"]:
                if m["op"] not in OPS.values() or not RES_RE.match(m["attribute"]):
                    errors.append(f"{where}: bad modifier {m}")
        if r["kind"] == "effect" and not RES_RE.match(r["params"]["effect"]):
            errors.append(f"{where}: effect id '{r['params']['effect']}'")
        if r["kind"] == "immune":
            if r["params"]["damage"] not in DAMAGE_TAGS:
                errors.append(f"{where}: damage tag must be one of {DAMAGE_TAGS}")
            if not 0.0 < r["params"]["factor"] <= 1.0:
                errors.append(f"{where}: factor outside (0, 1]")
        if r["kind"] == "survive" and not 0.0 < r["params"]["heal"] <= 1.0:
            errors.append(f"{where}: heal outside (0, 1]")
        try:
            relic_art.parse(r["art"])
        except (ValueError, IndexError) as e:
            errors.append(f"{where}: art '{r['art']}': {e}")
        for name, text in (("en", r["en"]), ("tr", r["tr"]), ("desc_en", r["desc_en"]), ("desc_tr", r["desc_tr"])):
            if not text:
                errors.append(f"{where}: {name} is empty")
        expected = shows(r)
        for name in ("desc_en", "desc_tr"):
            if r[name].count("%s") != expected:
                errors.append(f"{where}: {name} has {r[name].count('%s')} '%s', the {r['kind']} kind shows {expected} value(s)")
        if not r["sources"]:
            errors.append(f"{where}: no source")
        for s in r["sources"]:
            if s["source"] not in source_ids:
                errors.append(f"{where}: unknown source '{s['source']}'")
            if s["weight"] <= 0:
                errors.append(f"{where}: source weight must be positive")
        if r["rarity"] == "legendary" and any(s["source"] in ("ruin", "haven_large", "dungeon") for s in r["sources"]):
            errors.append(f"{where}: a legendary relic comes only from bosses and keeps")
    for s in sources:
        if not any(x["source"] == s["id"] for r in relics for x in r["sources"]):
            errors.append(f"relic_sources.csv: source '{s['id']}' drops nothing")
    return errors


# ------------------------------------------------------------------------------------------------------------------------------ outputs
def game_json(relics, sources):
    return dumps({
        "rarities": RARITIES,
        "relics": [{"id": r["id"], "slot": r["slot"], "rarity": r["rarity"], "kind": r["kind"], "params": r["params"], "mods": r["mods"], "sources": r["sources"]} for r in relics],
        "sources": sources,
    })


def assets(relics):
    out = {}
    for r in relics:
        out[f"assets/theoria/items/relic/{r['id']}.json"] = dumps({"model": {"type": "minecraft:model", "model": f"theoria:item/relic/{r['id']}"}})
        out[f"assets/theoria/models/item/relic/{r['id']}.json"] = dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"theoria:item/relic/{r['id']}"}})
        out[f"assets/theoria/textures/item/relic/{r['id']}.png"] = relic_art.icon(r["art"])
    return out


def tags(relics):
    by_slot = {}
    for r in relics:
        by_slot.setdefault(r["slot"], []).append(f"theoria:relic/{r['id']}")
    return {f"data/curios/tags/item/{slot}.json": dumps({"replace": False, "values": values}) for slot, values in sorted(by_slot.items())}


def loot(relics, sources):
    out = {}
    for s in sources:
        entries = [{"type": "minecraft:item", "name": f"theoria:relic/{r['id']}", "weight": x["weight"]} for r in relics for x in r["sources"] if x["source"] == s["id"]]
        pool = {"rolls": 1, "entries": entries}
        if s["chance"] < 1.0:
            pool["condition"] = {"type": "minecraft:random_chance", "chance": s["chance"]}
        table = {"type": "minecraft:entity" if s["type"] == "boss" else "minecraft:chest", "pools": [pool]}
        out[f"data/theoria/loot_table/relics/{s['id']}.json"] = dumps(table)
        out[f"data/theoria/loot_modifiers/relics_{s['id']}.json"] = dumps(
            {"type": "neoforge:add_table", "condition": {"type": "neoforge:loot_table_id", "loot_table_id": s["target"]}, "table": f"theoria:relics/{s['id']}"})
    return out


def lang(relics, sources):
    en, tr = {}, {}
    for r in relics:
        en[f"item.theoria.relic.{r['id']}"], tr[f"item.theoria.relic.{r['id']}"] = r["en"], r["tr"]
        en[f"theoria.relic.desc.{r['id']}"], tr[f"theoria.relic.desc.{r['id']}"] = r["desc_en"], r["desc_tr"]
    for s in sources:
        if s["type"] == "chest":
            en[f"theoria.relic.source.{s['id']}"], tr[f"theoria.relic.source.{s['id']}"] = CHEST_NAMES[s["id"]]
    ui = {"slot": ("Slot: %s", "Yuva: %s"), "source": ("Found in: %s", "Bulunur: %s"), "survived": ("%s saved you!", "%s seni kurtardı!"),
          "rarity.common": ("Common relic", "Sıradan relic"), "rarity.uncommon": ("Uncommon relic", "Seyrek relic"), "rarity.rare": ("Rare relic", "Nadir relic"),
          "rarity.legendary": ("Legendary relic", "Efsanevi relic")}
    for key, (a, b) in ui.items():
        en[f"theoria.relic.{key}"], tr[f"theoria.relic.{key}"] = a, b
    en["itemGroup.theoria.relics"], tr["itemGroup.theoria.relics"] = "Theoria: Relics", "Theoria: Relic'ler"
    return en, tr


def merge_lang(existing_text, generated):
    current = json.loads(existing_text)
    merged = {k: v for k, v in current.items() if not k.startswith(OWNED)}
    merged.update(generated)
    return dumps(dict(sorted(merged.items())))  # the same canonical order as generator/catalysts.py


def outputs():
    relics, sources = read_relics(), read_sources()
    errors = validate(relics, sources)
    if errors:
        raise SystemExit("generator/relics.py: data/relics.csv or data/relic_sources.csv is invalid:\n  " + "\n  ".join(errors))
    rel = {"theoria/relics.json": game_json(relics, sources)}
    for part in (assets(relics), tags(relics), loot(relics, sources)):
        for path, content in part.items():
            if path in rel:
                raise SystemExit(f"generator/relics.py: two parts write the same file {path}")
            rel[path] = content
    files = {GEN / p: c for p, c in rel.items()}
    en, tr = lang(relics, sources)
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
    files = outputs()
    stale = [p for p, c in files.items() if differs(p, c)]
    owned = {p for p in files if GEN in p.parents}
    orphans = [p for p in sorted(GEN.rglob("*")) if p.is_file() and p not in owned] if GEN.exists() else []
    if "--list" in argv:
        kinds = {}
        for p in owned:
            kinds[p.suffix] = kinds.get(p.suffix, 0) + 1
        print(f"{len(owned)} files", dict(sorted(kinds.items())))
    if check:
        for p in stale + orphans:
            print(f"STALE: {p.relative_to(ROOT)} → python3 -I generator/relics.py")
        if not (stale or orphans):
            print(f"OK ({len(owned)} files)")
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
    print(f"relics: {len(owned)} files ({len(stale)} written, {len(orphans)} strays removed)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
