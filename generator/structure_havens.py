"""Haven ailesi (Faz 9b): S 8×8 · M 30×30 · L 80×80. Yatak + ocak + depo odası hazır; mob yok (yapı spawn override + her doğuş hücresi aydınlık: ensure_light).
Ücretsiz gear yok; kısmen dolu sandıklar `chests/haven_*` loot tablolarını kullanır (Lootr vanilla loot tablosu olan sandıkları kendisi dönüştürür).
"""
from structure_nbt import AIR, B, Template
from structure_parts import (PAL, barrel, bed, chest, clear, door, ensure_light, furnace, furnish, garden, gable, ground, hall, hang_lantern, lamp_grid, lamp_post,
                             pergola, pond, rng_for, tower, tree)

LOOT = {"s": "theoria:chests/haven_small", "m": "theoria:chests/haven_medium", "l": "theoria:chests/haven_large"}


def foundation_depth(size):
    return {"s": 3, "m": 4, "l": 6}[size]


def haven_s(variant, name):
    p, G, rng = PAL["haven"][variant], foundation_depth("s"), rng_for(name)
    loot = LOOT["s"]
    t = Template(8, G + 9, 8, name)
    clear(t, 0, 0, 7, 7, G, G + 8)
    ground(t, p, 0, 0, 7, 7, G)
    hall(t, p, 1, 1, 6, 6, G, 3, door_x=3, window_every=3, rng=rng)
    bed(t, 2, G, 3, "north", "red")
    furnace(t, 4, G, 2, "south")
    t.put(3, G, 2, B("crafting_table"))
    chest(t, 5, G, 2, "south", loot)
    barrel(t, 5, G, 5, loot)
    hang_lantern(t, 3, G + 2, 3)
    hang_lantern(t, 4, G + 2, 4)
    lamp_post(t, p, 0, 6, G)
    lamp_post(t, p, 7, 6, G)
    garden(t, 0, 0, 7, 7, G, rng, density=0.18)
    ensure_light(t)
    return t


def haven_m(variant, name):
    p, G, rng = PAL["haven"][variant], foundation_depth("m"), rng_for(name)
    loot = LOOT["m"]
    t = Template(30, G + 13, 30, name)
    clear(t, 0, 0, 29, 29, G, G + 12)
    ground(t, p, 0, 0, 29, 29, G)
    rooms = hall(t, p, 3, 3, 26, 12, G, 4, splits=(11, 19), door_x=15, rng=rng)
    for kind, rect in zip(("bedroom", "kitchen", "storage"), rooms):
        furnish(t, p, kind, rect, G, 4, loot, rng)
    pond(t, 4, 17, 12, 24, G, rng)
    pergola(t, p, 18, 15, 25, 19, G)
    for x in range(18, 26, 2):                                   # sebze/otlak yatakları: sıra sıra dandelion/poppy + saman
        t.put(x, G, 23, B("hay_block", axis="y"))
    t.put(15, G - 1, 15, B("dirt_path"))
    for z in range(13, 29):
        t.put(15, G - 1, z, B("dirt_path"))
    for x in range(1, 29):                                       # sınır çiti + güney kapı boşluğu
        if x not in (14, 15, 16):
            t.put(x, G, 28, B(p["fence"]))
    for z in range(13, 28):
        t.put(1, G, z, B(p["fence"]))
        t.put(28, G, z, B(p["fence"]))
    t.put(15, G, 28, B("oak_fence_gate", facing="south", open=False, in_wall=False, powered=False))
    tree(t, 6, 1, G, 5)
    tree(t, 22, 25, G, 5)
    tree(t, 27, 16, G, 5)
    lamp_grid(t, p, 1, 1, 28, 28, G, step=5)
    garden(t, 1, 13, 28, 27, G, rng, density=0.22)
    ensure_light(t)
    return t


def lodge(p, name, w, d, rooms, loot, wh=4):
    """Ayrı küçük konak (kuzey cephe, kapı güneyde): L'de dönerek yapıştırılır."""
    rng = rng_for(name)
    t = Template(w, 4 + wh + 8, d, name)
    G = 4
    t.fill(0, 0, 0, w - 1, G - 2, d - 1, B(p["found"]))
    t.fill(0, G - 1, 0, w - 1, G - 1, d - 1, B("grass_block"))
    n = len(rooms)
    step = (w - 2) // n
    splits = tuple(1 + step * i for i in range(1, n))
    rects = hall(t, p, 1, 1, w - 2, d - 2, G, wh, splits=splits, door_x=(w - 1) // 2, rng=rng)
    for kind, rect in zip(rooms, rects):
        furnish(t, p, kind, rect, G, wh, loot, rng)
    return t, G


def haven_l(variant, name):
    p, G, rng = PAL["haven"][variant], foundation_depth("l"), rng_for(name)
    loot = LOOT["l"]
    t = Template(80, G + 24, 80, name)
    clear(t, 0, 0, 79, 79, G, G + 23)
    ground(t, p, 0, 0, 79, 79, G)
    # dış sur (3..76), güney kapı 37..42
    for y in range(G, G + 6):
        t.walls(3, y, 3, 76, y, 76, B(p["wall"]))
    for x in range(3, 77):
        for z in (3, 76):
            if (x + z) % 2 == 0:
                t.put(x, G + 6, z, B(p["wall"]))
            if rng.random() < 0.25:
                t.put(x, G, z, B(p["alt"]))
    for z in range(3, 77):
        for x in (3, 76):
            if (x + z) % 2 == 0:
                t.put(x, G + 6, z, B(p["wall"]))
    t.air(37, G, 76, 42, G + 4, 76)
    t.fill(37, G + 5, 76, 42, G + 5, 76, B(p["frame"], axis="x"))
    for x in (38, 41):
        hang_lantern(t, x, G + 4, 76)
    # köşe kuleleri
    for (tx, tz) in ((3, 3), (69, 3), (3, 69), (69, 69)):
        tower(t, p, tx, tz, G, 12, size=8, loot=loot if (tx, tz) == (3, 3) else None, door_facing="east" if tx < 40 else "west")
    # ana konak (kuzey)
    rooms = hall(t, p, 14, 8, 65, 26, G, 5, splits=(27, 52), door_x=39, rng=rng)
    for kind, rect in zip(("bedroom", "hall", "kitchen"), rooms):
        furnish(t, p, kind, rect, G, 5, loot, rng)
    # kanat konakları (batı: yatak odaları, doğu: kitaplık + depo + mutfak), 90° döndürülerek yapıştırılır
    west, wg = lodge(p, name + "/w", 24, 13, ("bedroom", "bedroom", "bedroom"), loot)
    east, eg = lodge(p, name + "/e", 24, 13, ("library", "storage", "kitchen"), loot)
    t.paste(west, 6, G - wg, 30, rot=1)
    t.paste(east, 61, G - eg, 30, rot=3)
    # avlu: gölet, çardak, bahçe, ağaçlar, yol
    pond(t, 32, 44, 47, 56, G, rng)
    pergola(t, p, 22, 62, 30, 66, G)
    pergola(t, p, 50, 62, 58, 66, G)
    for z in range(27, 76):
        t.put(39, G - 1, z, B("dirt_path"))
        t.put(40, G - 1, z, B("dirt_path"))
    for (x, z) in ((24, 34), (56, 34), (24, 52), (56, 52), (30, 70), (52, 70), (20, 20), (60, 20)):
        tree(t, x, z, G, 6)
    lamp_grid(t, p, 4, 4, 75, 75, G, step=6)
    garden(t, 4, 27, 75, 75, G, rng, density=0.15)
    ensure_light(t)
    return t


BUILDERS = {"s": haven_s, "m": haven_m, "l": haven_l}
