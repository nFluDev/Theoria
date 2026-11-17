#!/usr/bin/env python3
"""Gear dokuları için çevrimdışı önizleme (ekran görüntüsü yerine): üretilmiş PNG'leri ×4 büyütüp contact sheet yapar.

    python3 -I tools/gear_preview.py        # → build/gear-preview/{items,blocks,equipment}.png
Satır = malzeme; sütun = parça. Yaklaşıktır — kesin onay oyunda (Vulkan + Vitrail).
"""
import pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "generator"))
import pngio  # noqa: E402

TEX = ROOT / "mods-src/theoria/src/generated/resources/assets/theoria/textures"
OUT = ROOT / "build/gear-preview"
SCALE, GAP, BG = 4, 2, (58, 58, 64, 255)


def load(path):
    return pngio.decode(path.read_bytes())[2] if path.exists() else None


def sheet(rows_of_paths, cell):
    """rows_of_paths: [[Path|None]] → rows (piksel). Her hücre cell×cell, ×SCALE büyütülmüş."""
    out = []
    for row in rows_of_paths:
        tiles = [load(p) if p else None for p in row]
        for y in range(cell * SCALE + GAP):
            line = []
            for tile in tiles:
                for x in range(cell * SCALE + GAP):
                    if tile is None or x >= cell * SCALE or y >= cell * SCALE or y // SCALE >= len(tile) or x // SCALE >= len(tile[0]):
                        line.append(BG)
                    else:
                        px = tile[y // SCALE][x // SCALE]
                        line.append(px if px[3] == 255 else tuple(round(BG[i] + (px[i] - BG[i]) * px[3] / 255) for i in range(3)) + (255,))
            out.append(line)
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    items = TEX / "item"
    mats = sorted({p.name.rsplit("_", 1)[0] for p in items.glob("*_helmet.png")} | {p.name.rsplit("_", 1)[0] for p in items.glob("*_sword.png")})
    cols = ["helmet", "chestplate", "leggings", "boots", "sword", "axe", "pickaxe", "shovel", "hoe", "spear"]
    rows = [[items / f"{m}_{c}.png" for c in cols] for m in mats]
    extra = sorted(p for p in items.glob("*.png") if not any(p.name.startswith(m + "_") and p.stem.split("_", 1)[-1] in cols + ["spear_in_hand"] for m in mats))
    rows += [extra[i:i + 10] for i in range(0, len(extra), 10)]
    (OUT / "items.png").write_bytes(pngio.encode(sheet(rows, 16)))
    blocks = sorted((TEX / "block").glob("*.png"))
    (OUT / "blocks.png").write_bytes(pngio.encode(sheet([blocks[i:i + 8] for i in range(0, len(blocks), 8)], 16)))
    eq = sorted((TEX / "entity/equipment/humanoid").glob("*.png"))
    rows = [[p, TEX / "entity/equipment/humanoid_leggings" / p.name] for p in eq]
    (OUT / "equipment.png").write_bytes(pngio.encode(sheet(rows, 64)))
    print("→", OUT, [p.name for p in OUT.glob("*.png")])


main()
