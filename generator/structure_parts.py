"""Yapı parçaları (Faz 9b): palet tabloları + ev/oda/kule/bahçe yardımcıları. Haven (structure_havens.py) ve diğer sınıflar (structure_others.py) kullanır.

Şablon koordinatları: x doğu, z güney, y yukarı. `G` = zemin katmanı + 1: y=G-1 yüzey bloğuyla aynı hizadadır (çim/zemin), y=G ilk hava katmanı.
Yerleşimde `start_height = -G` olduğundan şablon yüzeye G kat gömülü oturur (temel y=0..G-2, kaplama y=G-1).
"""
import random, zlib

from structure_nbt import AIR, B, Long, Template, is_opaque, short

# ───────────────────────────────────────────────────────────────────────────────────────── paletler (sınıf × varyant)
PAL = {
    "haven": {
        "oak":    dict(wall="oak_planks", alt="mossy_cobblestone", frame="oak_log", floor="spruce_planks", roof="oak_stairs", roofblock="oak_planks", slab="oak_slab",
                       fence="oak_fence", door="oak_door", found="cobblestone", pillar="stripped_oak_log"),
        "spruce": dict(wall="spruce_planks", alt="mossy_stone_bricks", frame="spruce_log", floor="dark_oak_planks", roof="spruce_stairs", roofblock="spruce_planks", slab="spruce_slab",
                       fence="spruce_fence", door="spruce_door", found="stone_bricks", pillar="stripped_spruce_log"),
        "stone":  dict(wall="stone_bricks", alt="mossy_stone_bricks", frame="polished_andesite", floor="spruce_planks", roof="stone_brick_stairs", roofblock="stone_bricks", slab="stone_brick_slab",
                       fence="spruce_fence", door="spruce_door", found="stone_bricks", pillar="chiseled_stone_bricks"),
    },
    "ruin": {
        "moss": dict(wall="mossy_stone_bricks", alt="cracked_stone_bricks", frame="mossy_cobblestone", floor="stone_bricks", found="cobblestone", pillar="chiseled_stone_bricks"),
        "sand": dict(wall="sandstone", alt="cut_sandstone", frame="chiseled_sandstone", floor="smooth_sandstone", found="sandstone", pillar="chiseled_sandstone"),
    },
    "dungeon": {
        "stone":     dict(wall="stone_bricks", alt="mossy_stone_bricks", crack="cracked_stone_bricks", floor="stone_bricks", pillar="chiseled_stone_bricks"),
        "deepslate": dict(wall="deepslate_bricks", alt="cracked_deepslate_bricks", crack="deepslate_tiles", floor="polished_deepslate", pillar="chiseled_deepslate"),
    },
    "keep": {
        "mud":     dict(wall="mud_bricks", alt="packed_mud", frame="mangrove_log", floor="mud_bricks", roof="mud_brick_stairs", roofblock="mud_bricks", slab="mud_brick_slab",
                        found="mud_bricks", pillar="mud_bricks", boss="theoria:mud_king"),
        "storm":   dict(wall="stone_bricks", alt="cracked_stone_bricks", frame="chiseled_stone_bricks", floor="polished_andesite", roof="stone_brick_stairs", roofblock="stone_bricks",
                        slab="stone_brick_slab", found="stone_bricks", pillar="chiseled_stone_bricks", boss="theoria:storm_drake"),
        "ancient": dict(wall="deepslate_bricks", alt="cracked_deepslate_bricks", frame="polished_deepslate", floor="polished_deepslate", roof="deepslate_brick_stairs",
                        roofblock="deepslate_bricks", slab="deepslate_brick_slab", found="deepslate_bricks", pillar="chiseled_deepslate", boss="theoria:platinum_colossus"),
    },
}


def rng_for(name):
    return random.Random(zlib.crc32(name.encode("utf-8")))


def chest_nbt(loot, kind="chest"):
    return {"id": f"minecraft:{kind}", "LootTable": loot, "LootTableSeed": Long(0)}


def spawner_nbt(entity, count=3, delay=(200, 600), range_=16):
    from structure_nbt import Int, Short
    return {"id": "minecraft:mob_spawner", "SpawnData": {"entity": {"id": entity}}, "SpawnPotentials": [{"weight": Int(1), "data": {"entity": {"id": entity}}}],
            "SpawnCount": Short(count), "SpawnRange": Short(4), "MaxNearbyEntities": Short(count + 3), "RequiredPlayerRange": Short(range_),
            "MinSpawnDelay": Short(delay[0]), "MaxSpawnDelay": Short(delay[1]), "Delay": Short(20)}


# ───────────────────────────────────────────────────────────────────────────────────────── zemin / kabuk
def clear(t, x0, z0, x1, z1, G, top):
    t.air(x0, G, z0, x1, top, z1)


def ground(t, p, x0, z0, x1, z1, G, surface="grass_block"):
    if G >= 2:
        t.fill(x0, 0, z0, x1, G - 2, z1, B(p.get("found", "stone_bricks")))
    t.fill(x0, G - 1, z0, x1, G - 1, z1, B(surface))


def door(t, p, x, y, z, facing):
    t.put(x, y, z, B(p.get("door", "spruce_door"), facing=facing, half="lower", hinge="left", open=False, powered=False))
    t.put(x, y + 1, z, B(p.get("door", "spruce_door"), facing=facing, half="upper", hinge="left", open=False, powered=False))


def gable(t, p, x0, z0, x1, z1, y):
    """x boyunca uzanan beşik çatı; duvar kutusu (x0,z0)-(x1,z1), z'de 1 taşma. Çatı gövdesi dolu (tavan arası boşluğu doğuşa açık kalmasın)."""
    zn, zs = z0 - 1, z1 + 1
    i = 0
    while zn <= zs:
        yy = y + i
        if zn == zs:
            t.fill(x0, yy, zn, x1, yy, zn, B(p["roofblock"]))
        else:
            t.fill(x0, yy, zn, x1, yy, zn, B(p["roof"], facing="north"))
            t.fill(x0, yy, zs, x1, yy, zs, B(p["roof"], facing="south"))
            if i >= 1 and zn + 1 <= zs - 1:
                t.fill(x0, yy, zn + 1, x1, yy, zs - 1, B(p["roofblock"]))
        zn, zs, i = zn + 1, zs - 1, i + 1
    return i


# ───────────────────────────────────────────────────────────────────────────────────────── ev (oda bölmeli)
def hall(t, p, x0, z0, x1, z1, G, wh, splits=(), door_x=None, window_every=3, rng=None, gable_roof=True):
    """Dikdörtgen yapı: zemin, duvarlar (alt sırada yosunlu karışım), köşe direkleri, pencereler, düz tavan, dolu beşik çatı, iç bölmeler (kapı aralığı ile).
    Dönüş: oda aralıkları [(xa, xb, za, zb)] (iç hücreler)."""
    rng = rng or random.Random(0)
    t.fill(x0, G - 1, z0, x1, G - 1, z1, B(p["floor"]))
    for y in range(G, G + wh):
        t.walls(x0, y, z0, x1, y, z1, B(p["wall"]))
    for x in range(x0, x1 + 1):                                  # alt sıra: yosunlu/eski görünüm (terk edilmiş estetik)
        for z in (z0, z1):
            if rng.random() < 0.35:
                t.put(x, G, z, B(p["alt"]))
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if rng.random() < 0.35:
                t.put(x, G, z, B(p["alt"]))
    for cx in (x0, x1):
        for cz in (z0, z1):
            t.fill(cx, G, cz, cx, G + wh - 1, cz, B(p["frame"], axis="y"))
    if window_every:
        for x in range(x0 + 2, x1 - 1, window_every):
            for z in (z0, z1):
                t.put(x, G + 1, z, B("glass_pane"))
        for z in range(z0 + 2, z1 - 1, window_every):
            for x in (x0, x1):
                t.put(x, G + 1, z, B("glass_pane"))
    t.fill(x0 + 1, G + wh, z0 + 1, x1 - 1, G + wh, z1 - 1, B(p["floor"]))      # tavan
    for sx in splits:
        t.fill(sx, G, z0 + 1, sx, G + wh - 1, z1 - 1, B(p["wall"]))
        zm = (z0 + z1) // 2
        t.air(sx, G, zm, sx, G + 1, zm + 1)
    if door_x is not None:
        t.air(door_x, G, z1, door_x, G + 1, z1)
        door(t, p, door_x, G, z1, "south")
    if gable_roof:
        gable(t, p, x0, z0, x1, z1, G + wh)
    edges = [x0] + list(splits) + [x1]
    return [(edges[i] + 1, edges[i + 1] - 1, z0 + 1, z1 - 1) for i in range(len(edges) - 1)]


# ───────────────────────────────────────────────────────────────────────────────────────── mobilya
def bed(t, x, y, z, facing, color="red"):
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
    t.put(x, y, z, B(f"{color}_bed", facing=facing, part="foot", occupied=False))
    t.put(x + dx, y, z + dz, B(f"{color}_bed", facing=facing, part="head", occupied=False))


def chest(t, x, y, z, facing, loot):
    t.put(x, y, z, B("chest", facing=facing, type="single", waterlogged=False), chest_nbt(loot))


def barrel(t, x, y, z, loot=None):
    t.put(x, y, z, B("barrel", facing="up", open=False), chest_nbt(loot, "barrel") if loot else None)


def furnace(t, x, y, z, facing, kind="furnace"):
    t.put(x, y, z, B(kind, facing=facing, lit=False))


def hang_lantern(t, x, y, z):
    t.put(x, y, z, B("lantern", hanging=True, waterlogged=False))


def lamp_post(t, p, x, z, G):
    t.put(x, G, z, B(p["fence"]))
    t.put(x, G + 1, z, B(p["fence"]))
    t.put(x, G + 2, z, B("lantern", hanging=False, waterlogged=False))


def furnish(t, p, kind, rect, G, wh, loot, rng):
    xa, xb, za, zb = rect
    if kind == "bedroom":
        for x in range(xa + 1, xb, 3):
            bed(t, x, G, za + 1, "north", rng.choice(("red", "blue", "white", "green")))
        chest(t, xb, G, zb, "west", loot)
        t.put(xa, G, zb, B("bookshelf"))
    elif kind == "kitchen":
        furnace(t, xa, G, za, "south")
        furnace(t, xa + 1, G, za, "south", "smoker")
        t.put(xa + 2, G, za, B("crafting_table"))
        t.put(xb, G, za, B("cauldron"))
        barrel(t, xb, G, zb, loot)
        t.put(xa + 3, G, za, B("composter", level=0))
    elif kind == "storage":
        for x in range(xa, xb + 1):
            chest(t, x, G, za, "south", loot) if (x - xa) % 2 == 0 else barrel(t, x, G, za, loot if rng.random() < 0.6 else None)
        t.put(xa, G, zb, B("hay_block", axis="y"))
        t.put(xb, G, zb, B("hay_block", axis="y"))
    elif kind == "library":
        for x in range(xa, xb + 1):
            t.fill(x, G, za, x, G + 1, za, B("bookshelf"))
        for z in range(za + 1, zb + 1, 2):
            t.fill(xa, G, z, xa, G + 1, z, B("bookshelf"))
        t.put(xb, G, zb, B("lectern", facing="west", has_book=False, powered=False))
    elif kind == "hall":
        for z in range(za + 2, zb - 1, 4):                              # uzun masa (çit + halı yerine basit levha sırası)
            t.fill(xa + 3, G, z, xb - 3, G, z, B(p["fence"]))
            t.fill(xa + 3, G + 1, z, xb - 3, G + 1, z, B("oak_pressure_plate", powered=False))
        for x in range(xa + 2, xb - 1, 6):
            for z in (za, zb):
                t.fill(x, G, z, x, G + wh - 1, z, B(p["pillar"]))
        chest(t, xb, G, za, "west", loot)
    # tavan feneri: oda içinde her ~5 hücrede bir
    for x in range(xa + 1, xb, 5) if xb - xa > 4 else [(xa + xb) // 2]:
        for z in range(za + 1, zb, 5) if zb - za > 4 else [(za + zb) // 2]:
            hang_lantern(t, x, G + wh - 1, z)


def ensure_light(t, minimum=5, rounds=10):
    """Güvenlik ağı: doğuş hücresi `minimum` ışığın altındaysa fener ekle (tavan varsa asılı, yoksa zemin feneri). Her turda en karanlıktan başlayıp
    yarıçapı (15-minimum) içinde fener koyduğu hücreleri atlar (yaklaşık), sonra ışığı yeniden hesaplar. Kalan karanlık hücre sayısını döndürür."""
    reach = 15 - minimum - 1
    for _ in range(rounds):
        level = t.light_levels()
        dark = sorted((c for c in t.spawn_cells() if level.get(c, 0) < minimum), key=lambda c: level.get(c, 0))
        if not dark:
            return 0
        placed = []
        for (x, y, z) in dark:
            if any(abs(x - qx) + abs(y - qy) + abs(z - qz) <= reach for qx, qy, qz in placed):
                continue
            above = t.get(x, y + 1, z)
            t.put(x, y, z, B("lantern", hanging=above is not None and is_opaque(above), waterlogged=False))
            placed.append((x, y, z))
    level = t.light_levels()
    return sum(1 for c in t.spawn_cells() if level.get(c, 0) < minimum)


# ───────────────────────────────────────────────────────────────────────────────────────── kule / bahçe / gölet
def tower(t, p, x0, z0, G, height, size=8, loot=None, door_facing="south"):
    """size×size kule: temel, 2 kat, merdiven, siperlik; kapı `door_facing` yüzünde."""
    x1, z1 = x0 + size - 1, z0 + size - 1
    t.fill(x0, G - 1, z0, x1, G - 1, z1, B(p["floor"]))
    for y in range(G, G + height):
        t.walls(x0, y, z0, x1, y, z1, B(p["wall"]))
    for cx in (x0, x1):
        for cz in (z0, z1):
            t.fill(cx, G, cz, cx, G + height, cz, B(p["frame"], axis="y"))
    for y in (G + 2, G + 8):
        for k in range(2, size - 2, 2):
            for (x, z) in ((x0 + k, z0), (x0 + k, z1), (x0, z0 + k), (x1, z0 + k)):
                if y < G + height:
                    t.put(x, y, z, B("glass_pane"))
    mid = G + 5                                                   # ara kat (merdiven deliği)
    t.fill(x0 + 1, mid, z0 + 1, x1 - 1, mid, z1 - 1, B(p["floor"]))
    t.fill(x0 + 1, G + height, z0 + 1, x1 - 1, G + height, z1 - 1, B(p["floor"]))
    lx = x0 + 1
    for y in range(G, G + height):                                # iç duvar boyunca merdiven (batı iç yüzü)
        t.put(lx, y, z0 + 2, B("ladder", facing="east", waterlogged=False))
    t.air(lx, mid, z0 + 2, lx, mid, z0 + 2)
    for x in range(x0, x1 + 1):                                   # siperlik
        for z in range(z0, z1 + 1):
            if (x in (x0, x1) or z in (z0, z1)) and (x + z) % 2 == 0:
                t.put(x, G + height + 1, z, B(p["wall"]))
    dx, dz = {"south": ((x0 + x1) // 2, z1), "north": ((x0 + x1) // 2, z0), "east": (x1, (z0 + z1) // 2), "west": (x0, (z0 + z1) // 2)}[door_facing]
    t.air(dx, G, dz, dx, G + 1, dz)
    door(t, p, dx, G, dz, door_facing)
    cx, cz = x0 + size // 2, z0 + size // 2
    hang_lantern(t, cx, mid - 1, cz)
    hang_lantern(t, cx, G + height - 1, cz)
    if loot:
        chest(t, x1 - 1, mid + 1, z1 - 1, "west", loot)


def pond(t, x0, z0, x1, z1, G, rng):
    """Çim yüzeyi 2 derin gölet; kıyı kumu, nilüfer."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if edge:
                t.put(x, G - 1, z, B("sand" if rng.random() < 0.6 else "clay"))
            else:
                t.put(x, G - 1, z, B("water", level=0))
                if G >= 3:
                    t.put(x, G - 2, z, B("water", level=0))
                if rng.random() < 0.3:
                    t.put(x, G, z, B("lily_pad"))


def garden(t, x0, z0, x1, z1, G, rng, density=0.25):
    flowers = ("dandelion", "poppy", "azure_bluet", "oxeye_daisy", "cornflower", "allium")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if t.get(x, G, z) in (None, AIR) and t.get(x, G - 1, z) == B("grass_block"):
                r = rng.random()
                if r < density:
                    t.put(x, G, z, B(rng.choice(flowers)))
                elif r < density + 0.2:
                    t.put(x, G, z, B("short_grass"))


def pergola(t, p, x0, z0, x1, z1, G):
    """Asma bahçe: 4 köşe direği, levha çatı, sarkan köklü yaprak, fener."""
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        t.fill(x, G, z, x, G + 2, z, B(p["fence"]))
    t.fill(x0, G + 3, z0, x1, G + 3, z1, B(p["slab"], type="top", waterlogged=False))
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if (x + z) % 2 == 0:
                t.put(x, G + 3, z, B("flowering_azalea_leaves", distance=1, persistent=True, waterlogged=False))
                t.put(x, G + 2, z, B("hanging_roots", waterlogged=False))
    hang_lantern(t, (x0 + x1) // 2, G + 2, (z0 + z1) // 2)


def tree(t, x, z, G, height=5, leaf="oak_leaves", log="oak_log"):
    t.fill(x, G, z, x, G + height - 1, z, B(log, axis="y"))
    for dy in range(height - 2, height + 1):
        r = 2 if dy < height else 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if (abs(dx) + abs(dz)) <= r + (1 if dy < height - 1 else 0) and not (dx == 0 and dz == 0 and dy < height):
                    cx, cz = x + dx, z + dz
                    if 0 <= cx < t.w and 0 <= cz < t.d and t.get(cx, G + dy, cz) in (None, AIR):
                        t.put(cx, G + dy, cz, B(leaf, distance=1, persistent=True, waterlogged=False))


def lamp_grid(t, p, x0, z0, x1, z1, G, step=7):
    for x in range(x0, x1 + 1, step):
        for z in range(z0, z1 + 1, step):
            if t.get(x, G, z) == AIR and t.get(x, G - 1, z) == B("grass_block") and t.get(x, G + 1, z) == AIR and t.get(x, G + 2, z) == AIR:
                lamp_post(t, p, x, z, G)
