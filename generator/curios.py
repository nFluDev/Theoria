#!/usr/bin/env python3
"""Curios slot generator (Phase 11, ADR-007): data/curios_slots.csv -> Curios slot-type JSONs.

    python3 -I generator/curios.py            # write
    python3 -I generator/curios.py --check    # nothing is written: are the repo's files what would be generated?
    python3 -I generator/curios.py --total    # print the total slot count (check_curios.py and docs use it)

WHAT. Under mods-src/theoria/src/generated/curios/data/theoria/curios/:
  slots/<id>.json      {"operation": "set", "size": N} — one per slot type. Curios (17.0.1) merges a slot id across namespaces: `set` without `replace` takes the
                       LARGER size; icon/order/validators stay Curios' own. So the file raises a preset's size and changes nothing else.
  entities/player.json {"entities": ["minecraft:player"], "slots": [every id]} — Curios defines slot TYPES but gives them to no entity by itself (a fresh game reported
                       "Loaded 11 curio slots, Loaded 0 curio entities" and a player with no Curios inventory); this file is what gives the player the slots.
Java needs no slot code at all; "a new slot type = a CSV row" (CLAUDE.md: content is data).
"""
import csv
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
CSV = ROOT / "data/curios_slots.csv"
OUT = ROOT / "mods-src/theoria/src/generated/curios/data/theoria/curios"
# Curios 17.0.1 preset slot types (CuriosSlotTypes.Preset); ids outside this list need their own icon/order/lang and are refused here.
PRESETS = ["back", "belt", "body", "bracelet", "charm", "curio", "feet", "hands", "head", "necklace", "ring"]


def read_rows():
    rows = []
    with CSV.open(encoding="utf-8", newline="") as fh:
        lines = [line for line in fh if not line.lstrip().startswith("#")]
    for row in csv.DictReader(lines):
        rows.append({"id": row["id"].strip(), "size": int(row["size"]), "note": row["note"].strip()})
    return rows


def validate(rows):
    errors = []
    seen = set()
    for row in rows:
        if row["id"] not in PRESETS:
            errors.append(f"curios_slots.csv: '{row['id']}' is not a Curios preset slot type {PRESETS}")
        if row["id"] in seen:
            errors.append(f"curios_slots.csv: '{row['id']}' appears twice")
        seen.add(row["id"])
        if not 1 <= row["size"] <= 64:
            errors.append(f"curios_slots.csv: {row['id']} size {row['size']} outside 1..64")
    return errors


def render(rows):
    files = {f"slots/{row['id']}.json": json.dumps({"operation": "set", "size": row["size"]}, indent=2) + "\n" for row in rows}
    files["entities/player.json"] = json.dumps({"entities": ["minecraft:player"], "slots": [row["id"] for row in rows]}, indent=2) + "\n"
    return files


def main(argv):
    rows = read_rows()
    errors = validate(rows)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    if "--total" in argv:
        print(sum(row["size"] for row in rows))
        return 0
    wanted = render(rows)
    have = {p.relative_to(OUT).as_posix(): p.read_text(encoding="utf-8") for p in OUT.rglob("*.json")} if OUT.is_dir() else {}
    if "--check" in argv:
        stale = [n for n in sorted(set(wanted) | set(have)) if wanted.get(n) != have.get(n)]
        if stale:
            print("generator/curios.py --check: stale or stray files: " + ", ".join(stale), file=sys.stderr)
            return 1
        print(f"OK ({len(rows)} slot types, {sum(r['size'] for r in rows)} slots, assigned to the player)")
        return 0
    for name in set(have) - set(wanted):
        (OUT / name).unlink()
    for name, text in wanted.items():
        (OUT / name).parent.mkdir(parents=True, exist_ok=True)
        (OUT / name).write_text(text, encoding="utf-8")
    print(f"wrote {len(rows)} slot types + the player assignment, {sum(r['size'] for r in rows)} slots -> {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
