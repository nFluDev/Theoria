"""Haven dışı yapı sınıfları (Faz 9b): Harabe (orta, seyrek mob), Zindan (çok katlı yeraltı, yoğun mob + elit), Kale/Tapınak (boss). Mob'suzluk şartı YOK."""
from structure_nbt import AIR, B, Template
from structure_parts import (PAL, barrel, chest, clear, chest_nbt, door, furnish, ground, hang_lantern, hall, lamp_post, rng_for, spawner_nbt, tower, tree)

LOOT = {"ruin": "theoria:chests/ruin", "dungeon": "theoria:chests/dungeon", "keep": "theoria:chests/keep"}
BOSS_NBT = {"PersistenceRequired": True}


def ruin(variant, name):
    """22×22: yarı çökmüş duvar halkası, kırık direkler, ortada sunak + sandık, sağda çöken oda."""
    p, G, rng = PAL["ruin"][variant], 2, rng_for(name)
    t = Template(22, G + 9, 22, name)
    clear(t, 0, 0, 21, 21, G, G + 8)
    ground(t, p, 0, 0, 21, 21, G, "grass_block" if variant == "moss" else "sand")
    t.fill(2, G - 1, 2, 19, G - 1, 19, B(p["floor"]))
    for x in range(2, 20):                                      # bozuk duvar halkası (yükseklik rastgele)
        for z in range(2, 20):
            if x in (2, 19) or z in (2, 19):
                h = rng.choice((0, 1, 1, 2, 3, 4))
                for y in range(h):
                    t.put(x, G + y, z, B(p["alt"] if rng.random() < 0.4 else p["wall"]))
    for (x, z) in ((4, 4), (17, 4), (4, 17), (17, 17), (8, 4), (13, 17)):   # kırık direkler
        for y in range(rng.choice((2, 3, 5))):
            t.put(x, G + y, z, B(p["pillar"] if y % 2 else p["frame"]))
    t.fill(9, G, 9, 12, G, 12, B(p["pillar"]))                   # sunak
    t.put(10, G + 1, 10, B("chiseled_stone_bricks") if variant == "moss" else B("chiseled_sandstone"))
    chest(t, 11, G + 1, 10, "south", LOOT["ruin"])
    chest(t, 17, G, 17, "west", LOOT["ruin"])
    for _ in range(14):
        x, z = rng.randrange(3, 19), rng.randrange(3, 19)
        if t.get(x, G, z) == AIR:
            t.put(x, G, z, B("cobweb") if variant == "moss" else B("dead_bush"))
    if variant == "moss":
        tree(t, 1, 1, G, 5)
    return t


def room_cells(x0, z0, x1, z1):
    return (x0, x1, z0, z1)


def dungeon(variant, name):
    """38×38×15: 3×3 oda ızgarası, 2 kat (alt y1-5, üst y8-12); odalar iki yüzlü kapı aralıklı koridorlarla bağlı; spawner'lı odalar, sandıklar,
    ortadaki alt oda elit (stone_guardian) + 2 sandık. Işık bilerek yok (mob yoğun)."""
    p, rng = PAL["dungeon"][variant], rng_for(name)
    t = Template(38, 15, 38, name)
    t.fill(0, 0, 0, 37, 14, 37, B(p["wall"]))                                           # dolu kabuk
    mobs = ("theoria:cave_crawler", "theoria:rock_worm", "theoria:cobalt_spider")
    starts = (2, 14, 26)
    for fi, fy in enumerate((1, 8)):
        top = fy + 4
        for rx in starts:
            for rz in starts:
                t.air(rx, fy, rz, rx + 9, top, rz + 9)
        for off in (11, 23):                                                            # koridor şeritleri (2 geniş)
            for a in starts:
                t.air(off, fy, a + 3, off + 1, fy + 2, a + 6)
                t.air(a + 3, fy, off, a + 6, fy + 2, off + 1)
        for rx in starts:
            for rz in starts:
                if rng.random() < 0.5:                                                  # çürümüş zemin/duvar karışımı
                    for _ in range(10):
                        x, z = rng.randrange(rx, rx + 10), rng.randrange(rz, rz + 10)
                        t.put(x, fy - 1, z, B(p["alt"]))
        for i, (rx, rz) in enumerate(((2, 2), (26, 2), (2, 26), (26, 26))):
            cx, cz = rx + 5, rz + 5
            t.put(cx, fy, cz, B("spawner"), spawner_nbt(mobs[(i + fi) % 3]))
            chest(t, rx + 8, fy, rz + 8, "north", LOOT["dungeon"])
        for i, (rx, rz) in enumerate(((14, 2), (2, 14), (26, 14), (14, 26))):
            if rng.random() < 0.6:
                chest(t, rx + 8, fy, rz + 1, "south", LOOT["dungeon"])
    # merkez alt oda: elit nöbetçi (boss odası), iki sandık
    t.put(19, 1, 19, B("spawner"), spawner_nbt("theoria:rock_worm", count=2))
    t.entity(16, 1, 16, {"id": "theoria:stone_guardian", "PersistenceRequired": True})
    chest(t, 15, 1, 20, "east", LOOT["dungeon"])
    chest(t, 22, 1, 20, "west", LOOT["dungeon"])
    # alt-üst kat bağlantısı: iki köşe merdiven boşluğu (merdiven + boşluk)
    for (sx, sz) in ((3, 3), (34, 34)):
        t.air(sx, 6, sz, sx + 1, 8, sz + 1)
        t.fill(sx, 1, sz, sx, 8, sz, B("ladder", facing="east", waterlogged=False))
        t.fill(sx - 1, 1, sz, sx - 1, 8, sz, B(p["wall"]))
    for x in range(0, 38):
        for z in range(0, 38):
            if rng.random() < 0.06 and t.get(x, 1, z) == AIR:
                t.put(x, 1, z, B("cobweb"))
    return t


def keep(variant, name):
    """48×48: dış sur (2..45) + 4 kule + güney kapı; orta 22×22 arena (boss varlığı) + kuzey hazine odası; boss `PAL['keep'][v]['boss']`."""
    p, G, rng = PAL["keep"][variant], 4, rng_for(name)
    t = Template(48, G + 20, 48, name)
    clear(t, 0, 0, 47, 47, G, G + 19)
    ground(t, p, 0, 0, 47, 47, G, "grass_block")
    for y in range(G, G + 7):
        t.walls(2, y, 2, 45, y, 45, B(p["wall"]))
    for x in range(2, 46):
        for z in (2, 45):
            if (x + z) % 2 == 0:
                t.put(x, G + 7, z, B(p["wall"]))
            if rng.random() < 0.3:
                t.put(x, G, z, B(p["alt"]))
    for z in range(2, 46):
        for x in (2, 45):
            if (x + z) % 2 == 0:
                t.put(x, G + 7, z, B(p["wall"]))
    t.air(22, G, 45, 25, G + 4, 45)
    t.fill(22, G + 5, 45, 25, G + 5, 45, B(p["frame"], axis="x"))
    for (tx, tz) in ((2, 2), (38, 2), (2, 38), (38, 38)):
        tower(t, p, tx, tz, G, 14, size=8, door_facing="east" if tx < 20 else "west")
    # arena: 22×22 iç salon (13..34 × 13..34), 4 yüksek duvar, tavan açık (gökyüzü)
    for y in range(G, G + 8):
        t.walls(12, y, 12, 35, y, 35, B(p["wall"]))
    t.fill(13, G - 1, 13, 34, G - 1, 34, B(p["floor"]))
    t.air(22, G, 35, 25, G + 3, 35)
    for (x, z) in ((14, 14), (33, 14), (14, 33), (33, 33)):
        t.fill(x, G, z, x, G + 6, z, B(p["pillar"]))
        t.put(x, G + 7, z, B("lantern", hanging=False, waterlogged=False))
    t.entity(23, G, 22, {"id": p["boss"], "PersistenceRequired": True})
    # kuzey hazine odası (boss yenilince açık): sandıklar
    t.walls(18, G, 5, 29, G + 3, 11, B(p["wall"]))
    t.fill(19, G, 6, 28, G + 2, 10, AIR)
    t.fill(18, G + 4, 5, 29, G + 4, 11, B(p["wall"]))
    t.air(23, G, 11, 24, G + 1, 12)
    t.fill(19, G - 1, 6, 28, G - 1, 10, B(p["floor"]))
    for x in (20, 22, 25, 27):
        chest(t, x, G, 7, "south", LOOT["keep"])
    hang_lantern(t, 23, G + 3, 8)
    for _ in range(18):
        x, z = rng.randrange(4, 44), rng.randrange(4, 44)
        if t.get(x, G, z) == AIR and t.get(x, G - 1, z) == B("grass_block"):
            t.put(x, G, z, B("short_grass"))
    return t


BUILDERS = {"ruin": ruin, "dungeon": dungeon, "keep": keep}
