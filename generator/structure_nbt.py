"""Yapı şablonu kütüphanesi (Faz 9b): bağımlılıksız NBT yazıcı/okuyucu + blok ızgarası (`Template`) + ışık/doğuş analizi.
generator/structures.py (üreteç) ve tools/check_worldgen.py (denetim) paylaşır; elle NBT düzenlenmez (docs/05 §6).

26.3 şablon biçimi (StructureTemplate#save): {size:[x,y,z], palette:[{id,properties}], blocks:[{pos:[x,y,z],state:i,nbt?}], entities:[{pos:[d,d,d],blockPos:[i,i,i],nbt}], DataVersion}
Dosya: gzip'li NBT (mtime=0 → bayt düzeyinde deterministik).
"""
import gzip, heapq, io, struct

DATA_VERSION = 5023          # version.json world_version (26.3)
AIR = "minecraft:air"
FACINGS = ("north", "east", "south", "west")
DIRS = {"north": (0, -1), "east": (1, 0), "south": (0, 1), "west": (-1, 0)}


# ───────────────────────────────────────────────────────────────────────────────────────── NBT
class Byte(int): pass
class Short(int): pass
class Int(int): pass
class Long(int): pass
class Double(float): pass


def _name(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _tid(v):
    if isinstance(v, bool):
        return 1
    return {Byte: 1, Short: 2, Int: 3, Long: 4, Double: 6, str: 8, list: 9, dict: 10}[type(v)]


def _payload(v):
    if isinstance(v, bool):
        return struct.pack(">b", int(v))
    if isinstance(v, Byte):
        return struct.pack(">b", v)
    if isinstance(v, Short):
        return struct.pack(">h", v)
    if isinstance(v, Int):
        return struct.pack(">i", v)
    if isinstance(v, Long):
        return struct.pack(">q", v)
    if isinstance(v, Double):
        return struct.pack(">d", v)
    if isinstance(v, str):
        return _name(v)
    if isinstance(v, list):
        if not v:
            return struct.pack(">bi", 0, 0)
        t = _tid(v[0])
        assert all(_tid(x) == t for x in v), "NBT liste türü karışık"
        return struct.pack(">bi", t, len(v)) + b"".join(_payload(x) for x in v)
    if isinstance(v, dict):
        return b"".join(struct.pack(">b", _tid(x)) + _name(k) + _payload(x) for k, x in v.items()) + b"\0"
    raise TypeError(type(v))


def nbt_bytes(root):
    raw = struct.pack(">b", 10) + _name("") + _payload(root)
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0, compresslevel=9) as g:
        g.write(raw)
    return buf.getvalue()


def nbt_read(data):
    """gzip'li NBT → Python (Byte/Int/… sarmalayıcılar korunur). Denetim aracı için bağımsız okuyucu."""
    raw = gzip.decompress(data)
    pos = 0

    def take(fmt):
        nonlocal pos
        size = struct.calcsize(fmt)
        out = struct.unpack_from(fmt, raw, pos)[0]
        pos += size
        return out

    def string():
        nonlocal pos
        n = take(">H")
        s = raw[pos:pos + n].decode("utf-8")
        pos += n
        return s

    def read(t):
        if t == 1: return Byte(take(">b"))
        if t == 2: return Short(take(">h"))
        if t == 3: return Int(take(">i"))
        if t == 4: return Long(take(">q"))
        if t == 5: return Double(take(">f"))
        if t == 6: return Double(take(">d"))
        if t == 8: return string()
        if t == 9:
            et = take(">b")
            return [read(et) for _ in range(take(">i"))]
        if t == 10:
            out = {}
            while True:
                et = take(">b")
                if et == 0:
                    return out
                key = string()            # Python önce SAĞ tarafı değerlendirir: anahtar ayrı okunmalı
                out[key] = read(et)
        raise ValueError(f"NBT tür {t}")

    assert take(">b") == 10
    string()
    return read(10)


# ───────────────────────────────────────────────────────────────────────────────────────── blok durumu
def B(name, **props):
    """B("oak_stairs", facing="north", half="bottom") → 'minecraft:oak_stairs[facing=north,half=bottom]' (özellikler sıralı → kanonik)."""
    if ":" not in name:
        name = "minecraft:" + name
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={str(v).lower() if isinstance(v, bool) else v}" for k, v in sorted(props.items())) + "]"


def parse_state(s):
    name, _, rest = s.partition("[")
    props = dict(kv.split("=") for kv in rest.rstrip("]").split(",")) if rest else {}
    return name, props


def rotate_state(s, rot):
    """Saat yönünde rot × 90° (kuzey→doğu). facing ve axis döner; bağlantı özellikleri (north/east/…) sonradan hesaplandığı için burada yoktur."""
    if rot % 4 == 0:
        return s
    name, props = parse_state(s)
    if "facing" in props and props["facing"] in FACINGS:
        props["facing"] = FACINGS[(FACINGS.index(props["facing"]) + rot) % 4]
    if props.get("axis") in ("x", "z") and rot % 2:
        props["axis"] = "z" if props["axis"] == "x" else "x"
    return B(name, **props)


OPAQUE_WORDS = ("planks", "log", "wood", "stone", "brick", "cobble", "deepslate", "dirt", "grass_block", "mud", "andesite", "tuff", "sand", "terracotta", "concrete",
                "bookshelf", "crafting_table", "furnace", "smoker", "barrel", "hay_block", "clay", "gravel", "blackstone", "basalt", "podzol", "moss_block", "glowstone", "sea_lantern")
NOT_OPAQUE = ("stairs", "slab", "fence", "pane", "bars", "door", "trapdoor", "wall", "carpet", "bed", "chest", "leaves", "lantern", "torch", "sapling", "button", "plate", "stone_brick_wall")
LIGHTS = {"lantern": 15, "soul_lantern": 10, "torch": 14, "wall_torch": 14, "sea_lantern": 15, "glowstone": 15, "shroomlight": 15, "jack_o_lantern": 15, "campfire": 15, "candle": 3}
AIRY = ("air", "cave_air", "void_air")


def short(state):
    return state.partition("[")[0].removeprefix("minecraft:").removeprefix("theoria:")


def is_opaque(state):
    n = short(state)
    return any(w in n for w in OPAQUE_WORDS) and not any(w in n for w in NOT_OPAQUE)


def is_air(state):
    return short(state) in AIRY


def light_of(state):
    n = short(state)
    if n == "campfire" and "lit=false" in state:
        return 0
    return LIGHTS.get(n, 0)


# ───────────────────────────────────────────────────────────────────────────────────────── şablon
class Template:
    def __init__(self, w, h, d, name=""):
        self.w, self.h, self.d, self.name = w, h, d, name
        self.cells = {}          # (x,y,z) → (state, nbt|None)
        self.entities = []       # (x, y, z, nbt)

    def put(self, x, y, z, state, nbt=None):
        assert 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d, f"{self.name}: ({x},{y},{z}) şablon dışında {self.w}×{self.h}×{self.d}"
        if ":" not in state:
            state = "minecraft:" + state
        self.cells[(x, y, z)] = (state, nbt)

    def get(self, x, y, z):
        return self.cells.get((x, y, z), (None, None))[0]

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, state)

    def air(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, AIR)

    def walls(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1):
                        self.put(x, y, z, state)

    def box(self, x0, y0, z0, x1, y1, z1, state):
        """Boş kutu: yalnız 6 yüz."""
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1) or y in (y0, y1):
                        self.put(x, y, z, state)

    def entity(self, x, y, z, nbt):
        self.entities.append((x, y, z, nbt))

    def paste(self, sub, ox, oy, oz, rot=0, skip_air=False):
        """`sub`ı (x,y,z) kaydırmasıyla yapıştırır; rot × 90° saat yönü. Boyutlar döndükçe değişir (w↔d)."""
        for (x, y, z), (state, nbt) in sub.cells.items():
            if skip_air and is_air(state):
                continue
            nx, nz, sw, sd = x, z, sub.w, sub.d
            for _ in range(rot % 4):
                nx, nz = sd - 1 - nz, nx
                sw, sd = sd, sw
            self.put(ox + nx, oy + y, oz + nz, rotate_state(state, rot), nbt)
        for (x, y, z, nbt) in sub.entities:
            nx, nz, sw, sd = x, z, sub.w, sub.d
            for _ in range(rot % 4):
                nx, nz = sd - 1 - nz, nx
                sw, sd = sd, sw
            self.entities.append((ox + nx, oy + y, oz + nz, nbt))

    # ── bağlantılar: çit/cam/parmaklık komşu hesapları (şablonda açık yazılır; yerleştirmede güncelleme beklemeyiz)
    def connect(self):
        for (x, y, z), (state, nbt) in list(self.cells.items()):
            n = short(state)
            kind = "fence" if n.endswith("_fence") else "pane" if (n.endswith("glass_pane") or n == "iron_bars") else None
            if not kind:
                continue
            name, props = parse_state(state)
            for f, (dx, dz) in DIRS.items():
                other = self.get(x + dx, y, z + dz)
                on = other is not None and (is_opaque(other) or (short(other).endswith("_fence") and kind == "fence" and short(other) == n)
                                            or (kind == "pane" and (short(other).endswith("glass_pane") or short(other) == "iron_bars")))
                props[f] = "true" if on else "false"
            props.setdefault("waterlogged", "false")
            self.cells[(x, y, z)] = (B(name, **props), nbt)

    # ── NBT
    def to_nbt(self):
        self.connect()
        states = sorted({s for s, _ in self.cells.values()})
        index = {s: i for i, s in enumerate(states)}
        palette = []
        for s in states:
            name, props = parse_state(s)
            e = {"id": name}                      # 26.3: palette girişi {id, properties} (1.21'deki {Name, Properties} DEĞİL — NbtUtils#readBlockState)
            if props:
                e["properties"] = dict(sorted(props.items()))
            palette.append(e)
        blocks = []
        for (x, y, z) in sorted(self.cells, key=lambda p: (p[1], p[2], p[0])):
            state, nbt = self.cells[(x, y, z)]
            b = {"pos": [Int(x), Int(y), Int(z)], "state": Int(index[state])}
            if nbt:
                b["nbt"] = nbt
            blocks.append(b)
        ents = [{"pos": [Double(x + 0.5), Double(y), Double(z + 0.5)], "blockPos": [Int(x), Int(y), Int(z)], "nbt": nbt} for x, y, z, nbt in self.entities]
        root = {"DataVersion": Int(DATA_VERSION), "size": [Int(self.w), Int(self.h), Int(self.d)], "palette": palette, "blocks": blocks, "entities": ents}
        return nbt_bytes(root)

    # ── analiz
    def stats(self):
        names = {}
        for s, nbt in self.cells.values():
            n = short(s)
            names[n] = names.get(n, 0) + 1
        return names

    def count(self, word):
        return sum(1 for s, _ in self.cells.values() if short(s) == word or short(s).endswith(word))

    def light_levels(self):
        """Blok ışığı yayılımı (basit model: opak olmayan her hücre geçirir, her adım −1). Dönüş: {(x,y,z): seviye}. Şablon dışı ışık sayılmaz (muhafazakâr)."""
        level = {}
        heap = []
        for pos, (s, _) in self.cells.items():
            lv = light_of(s)
            if lv:
                level[pos] = lv
                heapq.heappush(heap, (-lv, pos))
        while heap:
            neg, (x, y, z) = heapq.heappop(heap)
            lv = -neg
            if level.get((x, y, z), 0) > lv or lv <= 1:
                continue
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                p = (x + dx, y + dy, z + dz)
                s = self.get(*p)
                if s is None or (is_opaque(s) or "leaves" in short(s)):
                    continue
                if level.get(p, 0) < lv - 1:
                    level[p] = lv - 1
                    heapq.heappush(heap, (-(lv - 1), p))
        return level

    def spawn_cells(self):
        """Doğal düşman doğabilecek hücreler: kendisi opak olmayan ve boş (hava benzeri) + altı opak. Tuple listesi."""
        out = []
        for (x, y, z), (s, _) in self.cells.items():
            if not (is_air(s) or short(s) in ("short_grass", "tall_grass", "moss_carpet", "lily_pad") or short(s).endswith(("carpet", "tulip", "dandelion", "poppy", "azure_bluet", "oxeye_daisy", "cornflower", "allium"))):
                continue
            below = self.get(x, y - 1, z)
            if below is not None and is_opaque(below) and short(below) not in ("sea_lantern", "glowstone"):
                out.append((x, y, z))
        return out

    def darkest(self):
        level = self.light_levels()
        cells = self.spawn_cells()
        if not cells:
            return 15, None, 0
        worst = min(cells, key=lambda p: level.get(p, 0))
        return level.get(worst, 0), worst, len(cells)
