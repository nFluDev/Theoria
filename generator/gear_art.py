"""Gear dokuları (Faz 6): vanilla demir şablonlarından malzeme rengine BOYANMIŞ doku varyantları.

NEDEN PİŞİRİLMİŞ RENK (çalışma anı tint'i değil). docs/03 "tint'li doku varyantları": her malzeme için ayrı PNG üretilir;
böylece item/blok/zırh tek biçimde çizilir — BlockColor/ItemColor kodu, DYED_COLOR hilesi, shader paketiyle uyum riski yok
(CLAUDE.md: yalnızca vanilla render tipleri). Sanat işi küçük kalır: yeni malzeme = materials.csv'ye renk.

YÖNTEM. Şablonlar vanilla demir setidir (nötr gri metal). Parlaklık → 3 duraklı renk rampası (gölge · malzeme rengi · parlak):
  t = L / Lmax ;  t<0,55: gölge→renk ;  t≥0,55: renk→parlak
Kahverengi sap pikselleri (alet/silah) olduğu gibi kalır. Cevherde taş/deepslate tabanı olduğu gibi kalır, yalnızca vanilla
cevher dokusunun taban dokudan FARKLI pikselleri (benekler) boyanır. Çıktı deterministiktir (aynı girdi → aynı bayt).
Lisans notu: şablonlar Mojang dokularından türetilir (generator/hud.py ile aynı durum) → yayın öncesi lisans notu Faz 13.
"""
import vanilla
import pngio

SHADOW, HILITE_DEFAULT = 0.28, 0.60


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def brightness(px):
    return 0.30 * px[0] + 0.59 * px[1] + 0.11 * px[2]


def _mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def ramp(color, t, hilite=HILITE_DEFAULT):
    shadow = tuple(round(c * SHADOW) for c in color)
    light = _mix(color, (255, 255, 255), hilite)
    t = max(0.0, min(1.0, t))
    return _mix(shadow, color, t / 0.55) if t < 0.55 else _mix(color, light, (t - 0.55) / 0.45)


def is_wood(px):
    """Demir şablonlarındaki kahverengi sap: kırmızı > yeşil ≥ mavi ve belirgin doygunluk (demir gri: kanal farkı ≤ ~12)."""
    return px[0] - px[2] >= 24 and px[0] >= px[1] >= px[2]


def recolor(rows, color, keep_wood=False, spread=False, hilite=HILITE_DEFAULT):
    """Opak pikselleri rampa ile boyar. spread=True: tonlar [Lmin, Lmax] aralığına yayılır (renkli şablonlar: ham cevher, elmas)."""
    work = [px for row in rows for px in row if px[3] > 0 and not (keep_wood and is_wood(px))]
    if not work:
        raise SystemExit("gear_art: boyanacak piksel yok")
    lo = min(brightness(px) for px in work) if spread else 0.0
    hi = max(brightness(px) for px in work)
    span = max(1.0, hi - lo)
    out = []
    for row in rows:
        line = []
        for px in row:
            if px[3] == 0 or (keep_wood and is_wood(px)):
                line.append(px)
            else:
                line.append((*ramp(color, (brightness(px) - lo) / span, hilite), px[3]))
        out.append(line)
    return out


def png(rows):
    return pngio.encode(rows)


def template(name):
    return vanilla.texture(name)[2]


# ------------------------------------------------------------------ item / equipment / block builders
def tool_icon(template_name, color):
    return png(recolor(template(template_name), color, keep_wood=True))


def metal_icon(template_name, color, spread=False, hilite=HILITE_DEFAULT):
    return png(recolor(template(template_name), color, spread=spread, hilite=hilite))


def ore_block(pattern, base, color):
    """pattern: vanilla cevher dokusu adı (örn. block/iron_ore), base: taş dokusu (block/stone). Taban pikselleri korunur."""
    ore, stone = template(pattern), template(base)
    mask = [[tuple(o) != tuple(s) for o, s in zip(orow, srow)] for orow, srow in zip(ore, stone)]
    specks = [[ore[y][x] if mask[y][x] else (0, 0, 0, 0) for x in range(len(ore[0]))] for y in range(len(ore))]
    painted = recolor(specks, color, spread=True)
    return png([[painted[y][x] if mask[y][x] else stone[y][x] for x in range(len(ore[0]))] for y in range(len(ore))])


def material_textures(m, ores_by_id):
    """Bir malzemenin tüm dokuları → {kaynak yolu: PNG baytı} (assets/theoria/textures/ altına göre). m: gear.py malzeme sözlüğü."""
    c, out, i = hex_rgb(m["color"]), {}, m["id"]
    if m["armor"]:
        for piece in ("helmet", "chestplate", "leggings", "boots"):
            out[f"item/{i}_{piece}"] = metal_icon(f"item/iron_{piece}", c)
        for layer in ("humanoid", "humanoid_leggings", "humanoid_baby"):
            out[f"entity/equipment/{layer}/{i}"] = metal_icon(f"entity/equipment/{layer}/iron", c)
    if m["tools"]:
        for kind in ("sword", "pickaxe", "axe", "shovel", "hoe"):
            out[f"item/{i}_{kind}"] = tool_icon(f"item/iron_{kind}", c)
        out[f"item/{i}_spear"] = tool_icon("item/iron_spear", c)
        out[f"item/{i}_spear_in_hand"] = tool_icon("item/iron_spear_in_hand", c)
    return out
