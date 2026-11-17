"""Validate the S5 vanilla mob dump and (optionally) install it as data/vanilla_mobs.csv.

Usage:  python3 -I tools/check_vanilla_mobs.py [path/to/vanilla_mobs.csv] [--install]
Default input: mods-src/theoria/build/dev-out/vanilla_mobs.csv (written by ./gradlew runGameTestServer).
Exit 0 = all checks pass.

Known dump caveats (values come from attribute suppliers + LivingEntity#getExperienceReward on a fresh entity):
  slime / magma_cube xp = size (size is assigned at spawn, so the dump shows 0);
  ender_dragon xp is awarded by its death sequence (12000 first kill / 500), not getExperienceReward.
"""
import csv, pathlib, shutil, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEFAULT = ROOT / "mods-src/theoria/build/dev-out/vanilla_mobs.csv"
TARGET = ROOT / "data/vanilla_mobs.csv"
HEADER = ("id,category,class,max_health,attack_damage,armor,armor_toughness,movement_speed,flying_speed,follow_range,"
          "knockback_resistance,attack_knockback,scale,xp_min,xp_max,width,height,fire_immune,note").split(",")
# Spot checks against well-known vanilla values: id -> {column: expected}
EXPECT = {
    "minecraft:zombie": {"max_health": "20", "attack_damage": "3", "armor": "2", "xp_min": "5"},
    "minecraft:skeleton": {"max_health": "20", "xp_min": "5"},
    "minecraft:creeper": {"max_health": "20"},
    "minecraft:enderman": {"max_health": "40", "attack_damage": "7"},
    "minecraft:cow": {"max_health": "10", "xp_min": "1", "xp_max": "3"},
    "minecraft:wither": {"max_health": "300", "armor": "4", "xp_min": "50"},
    "minecraft:warden": {"max_health": "500", "attack_damage": "30"},
    "minecraft:blaze": {"xp_min": "10", "fire_immune": "true"},
    "minecraft:ender_dragon": {"max_health": "200"},
    "minecraft:player": {"max_health": "20", "attack_damage": "1"},
}


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    src = pathlib.Path(args[0]) if args else DEFAULT
    errors = []
    if not src.is_file():
        print(f"FAIL: {src} yok — önce ./gradlew runGameTestServer")
        return 1
    with src.open(newline="") as f:
        rows = list(csv.reader(f))
    if rows[0] != HEADER:
        errors.append(f"header mismatch: {rows[0]}")
    data = {r[0]: dict(zip(rows[0], r)) for r in rows[1:]}
    if len(data) < 90:
        errors.append(f"only {len(data)} rows (< 90)")
    ids = [r[0] for r in rows[1:]]
    if ids != sorted(ids) or len(ids) != len(set(ids)):
        errors.append("ids not sorted/unique")
    for mob, cols in EXPECT.items():
        if mob not in data:
            errors.append(f"missing {mob}")
            continue
        for c, v in cols.items():
            if data[mob].get(c) != v:
                errors.append(f"{mob}.{c} = {data[mob].get(c)!r}, expected {v!r}")
    for mob, d in data.items():
        if not d["max_health"]:
            errors.append(f"{mob}: no max_health")
    monsters = sum(1 for d in data.values() if d["category"] == "monster")
    print(f"{src.name}: {len(data)} living types, {monsters} monster; {len(EXPECT)} spot checks")
    for e in errors:
        print("FAIL:", e)
    if errors:
        return 1
    print("OK")
    if "--install" in sys.argv:
        shutil.copyfile(src, TARGET)
        print(f"installed -> {TARGET.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
