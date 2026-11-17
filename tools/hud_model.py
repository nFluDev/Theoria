"""HUD katman modeli — REFERANS (Faz 3). Java'daki dev.theoria.hud.StatusModel / HudText / HudLayout bunun aynasıdır;
ikisi de data/hud/hud.json'u okur ve tools/check_hud.py üretilen vaka tablolarıyla (data/hud/hud_cases.csv,
hud_text_cases.csv) her ikisini de sınar. Modeli değiştirirsen: önce burada, sonra Java'da, sonra
`python3 -I tools/check_hud.py --write`.

Model (docs/04 §1, kararlar D1 + D16/D17):
  5 ikon x 4 çeyrek = 20 çeyrek = BİR KAT; 1 çeyrek = `unit` birim (can: 1 HP, D1). Renk MUTLAK değerin göstergesidir:
  kat = değer / 20 (azami cana bağlı DEĞİL). Palet P renk → P kat; paletin son rengi (beyaz / mor) KALICI — üstündeki her
  değer o renkle tam dolu çizilir, başa dönülmez (200, 215, 235 HP hep beyaz).
  Tamamlanan kat zemine oturur (base = kat-1, 5 ikon dolu), yeni kat soldan çeyrek çeyrek dolar (top).
"""
import json
import math
import pathlib
from typing import NamedTuple

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG = ROOT / "data/hud/hud.json"
LANG = ROOT / "mods-src/theoria/src/main/resources/assets/theoria/lang"
EPS = 1e-9  # ceil(value/unit) float gürültüsü: 214 * 120 / 214 = 120.00000000000001 → 121 olmasın

LANG_KEYS = ("thousand", "million", "billion", "trillion", "decimal")


def load(path=CONFIG):
    return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))


def parse_color(text):
    """'vanilla' → None; 'RRGGBB' → 0xFFRRGGBB; 'AARRGGBB' → olduğu gibi."""
    if text == "vanilla":
        return None
    value = int(text, 16)
    return value if len(text) == 8 else 0xFF000000 | value


class Fill(NamedTuple):
    layer: int  # üstte dolan katın paletteki sırası
    base: int   # zemindeki tamamlanmış kat (layer-1), yoksa -1
    top: int    # üst katın dolu çeyrek sayısı (0..20)


def per_layer(cfg):
    return cfg["layout"]["icons"] * cfg["layout"]["quarters_per_icon"]


def to_quarters(value, unit):
    """Birim (can/zırh) → çeyrek. value > 0 ise en az 1 çeyrek (0,5 can ölü görünmez); yukarı yuvarla (vanilla gibi)."""
    if not value > 0:
        return 0
    return max(1, math.ceil(value / unit - EPS))


def layered(value, unit, palette_len, layer_quarters):
    t = to_quarters(value, unit)
    layer = min(t // layer_quarters, palette_len - 1)
    # son katın ötesinde top 20'de kalır: son renk kalıcı ve tam dolu
    return Fill(layer, layer - 1, min(t - layer * layer_quarters, layer_quarters))


def health_fill(cfg, current):
    """Azami can çubuğu etkilemez (renk mutlak HP'dir); azami yalnızca sayıda görünür."""
    return layered(current, cfg["health"]["unit"], len(cfg["health"]["palette"]), per_layer(cfg))


def armor_fill(cfg, armor):
    return layered(armor, cfg["armor"]["unit"], len(cfg["armor"]["palette"]), per_layer(cfg))


def single_fill(value, scale_max, layer_quarters):
    """Tek katlı çubuk (açlık, satürasyon, emilim): value/scale_max oranı, çeyrek çözünürlüğünde."""
    if not value > 0 or not scale_max > 0:
        return 0
    return min(layer_quarters, max(1, math.ceil(value / scale_max * layer_quarters - EPS)))


def icon_quarters(top, cfg):
    qpi = cfg["layout"]["quarters_per_icon"]
    return [max(0, min(qpi, top - i * qpi)) for i in range(cfg["layout"]["icons"])]


def clip_width(cfg, quarters):
    """Bir ikonun `quarters` çeyreğinin piksel genişliği: 1/4 → 3, 1/2 → 5, 3/4 → 7, 4/4 → 9 (9 px ikon)."""
    size, qpi = cfg["layout"]["icon_size"], cfg["layout"]["quarters_per_icon"]
    return 0 if quarters <= 0 else size if quarters >= qpi else math.ceil(size * quarters / qpi)


# ---------------------------------------------------------------------------------------------- efektler (D18)
# Vanilla Hud.java'daki efektlerin aynası (kalp yanıp sönmesi, yenilenme dalgası, düşük can titremesi, açlık titremesi).
def blink_active(blink_until, tick, phase):
    return blink_until > tick and ((blink_until - tick) // phase) % 2 == 1


class HealthTracker:
    """Vanilla extractHealthLevel'ın durumu: hasar/iyileşmede (ve dokunulmazlık süresi varken) yanıp sönme başlar; kaybedilen
    kısım `display_hold_ticks` boyunca eski değerde (display) kalır. Java: dev.theoria.hud.HealthTracker."""

    def __init__(self, cfg):
        self.fx = cfg["effects"]
        self.last, self.display, self.last_change, self.blink_until = 0, -1, 0, 0

    def update(self, current, invulnerable, tick):
        fx = self.fx
        if tick < self.last_change:  # yeniden doğma: varlık tick sayacı sıfırlandı
            self.display, self.last_change, self.blink_until = -1, tick, 0
        if current < self.last and invulnerable:
            self.last_change, self.blink_until = tick, tick + fx["blink_damage_ticks"]
        elif current > self.last and invulnerable:
            self.last_change, self.blink_until = tick, tick + fx["blink_heal_ticks"]
        if self.display < 0 or tick - self.last_change > fx["display_hold_ticks"]:
            self.display, self.last_change = current, tick
        self.last = current

    def blinking(self, tick):
        return blink_active(self.blink_until, tick, self.fx["blink_phase_ticks"])


def regen_lifted(cfg, tick):
    """Yenilenme dalgasında kalkık ikon (0 = ilk) ya da -1 (duraklama)."""
    fx, icons = cfg["effects"], cfg["layout"]["icons"]
    index = (tick // fx["regen_step_ticks"]) % (icons + fx["regen_pause"])
    return index if index < icons else -1


def is_low_health(cfg, current_plus_absorption, maximum):
    return current_plus_absorption <= maximum * cfg["effects"]["low_health_fraction"]


def hunger_shakes(tick, food, saturation):
    """Vanilla: satürasyon 0 iken her food*3+1 tick'te ikonlar bir kare titrer."""
    return saturation <= 0 and tick % (food * 3 + 1) == 0


def fx_case(cfg, kind, args):
    p = args.split("|")
    if kind == "blink":
        return "1" if blink_active(int(p[0]), int(p[1]), cfg["effects"]["blink_phase_ticks"]) else "0"
    if kind == "regen":
        return str(regen_lifted(cfg, int(p[0])))
    if kind == "low":
        return "1" if is_low_health(cfg, float(p[0]), float(p[1])) else "0"
    if kind == "hunger":
        return "1" if hunger_shakes(int(p[0]), int(p[1]), float(p[2])) else "0"
    if kind == "tracker":  # "tick:can:dokunulmaz;..." → son durumda "display|yanıp sönüyor"
        tracker, tick = HealthTracker(cfg), 0
        for step in p[0].split(";"):
            tick, current, inv = (int(v) for v in step.split(":"))
            tracker.update(current, inv == 1, tick)
        return f"{tracker.display}|{1 if tracker.blinking(tick) else 0}"
    raise ValueError(kind)


# ---------------------------------------------------------------------------------------------- sayı metni
def fmt_tenths(tenths, decimal):
    whole, frac = divmod(tenths, 10)
    return str(whole) if frac == 0 else f"{whole}{decimal}{frac}"


def compact(value, suffixes):
    """10 000'e kadar tam sayı; sonra 1 ondalıklı kısaltma (249295 → '249,3B' tr / '249.3K' en). Taşma: 999,96B → '1M'."""
    if value != value or value in (math.inf, -math.inf):
        return "?"
    if value < 0:
        return "0"
    if value < 10_000:
        return str(int(value))
    steps = ((1e6, 1e3, "thousand"), (1e9, 1e6, "million"), (1e12, 1e9, "billion"), (math.inf, 1e12, "trillion"))
    for limit, divisor, key in steps:
        if value < limit:
            tenths = math.floor(value / divisor * 10 + 0.5)
            if tenths >= 10_000 and key != "trillion":
                continue
            return fmt_tenths(tenths, suffixes["decimal"]) + suffixes[key]
    return "?"


def fit(candidates, room, width, min_scale):
    """Adayları (en bilgilendiriciden en kısaya) dener: sığan ilkini, gerekirse küçülterek (taban min_scale) seçer.
    Hiçbiri sığmazsa sonuncuyu taban ölçekte döndürür."""
    for text in candidates:
        w = width(text)
        scale = 1.0 if w <= 0 else min(1.0, room / w)
        if scale >= min_scale:
            return text, scale
    return candidates[-1], min_scale


def health_candidates(current, maximum, absorption, suffixes):
    c = compact(math.ceil(current - EPS), suffixes)
    m = compact(int(math.floor(maximum + 0.5)), suffixes)
    full = f"{c}/{m}"
    out = []
    if absorption > 0:
        out.append(f"{full} (+{compact(math.ceil(absorption - EPS), suffixes)})")
    out += [full, c]
    return out


def armor_candidates(armor, toughness, suffixes):
    a = compact(int(armor), suffixes)
    out = []
    if toughness > 0:
        out.append(f"{a} (+{compact(int(math.floor(toughness + 0.5)), suffixes)})")
    out.append(a)
    return out


def lang_suffixes(name):
    table = json.loads((LANG / f"{name}.json").read_text(encoding="utf-8"))
    return {key: table[f"theoria.hud.num.{key}"] for key in LANG_KEYS}


# ---------------------------------------------------------------------------------------------- yerleşim
def geometry(cfg, gui_w, gui_h):
    """Orta sayı bölgesi ve satır y'leri (GUI pikseli). Java: HudLayout."""
    lo = cfg["layout"]
    centre = gui_w // 2
    bar_w = (lo["icons"] - 1) * lo["icon_step"] + lo["icon_size"]
    y_b = gui_h - lo["bottom"]
    return {
        "text_left": centre - lo["bar_edge"] + bar_w + lo["text_pad"],
        "text_right": centre + lo["bar_edge"] - bar_w - lo["text_pad"],
        "y_a": y_b - lo["row_height"],
        "y_b": y_b,
        "xp_top": gui_h - 24 - lo["xp_bar_height"],
    }


# ---------------------------------------------------------------------------------------------- vaka tabloları
def fmt_icons(top, cfg):
    return "".join(str(q) for q in icon_quarters(top, cfg))


def bar_expect(fill, cfg):
    return f"{fill.layer};{fill.base};{fill.top};{fmt_icons(fill.top, cfg)}"


def bar_case(cfg, kind, a, b):
    """(kind, a, b) → 'katman;zemin;üst;ikon-çeyrekleri'."""
    layer_q = per_layer(cfg)
    if kind == "health" or kind == "vehicle":  # b = azami (yalnızca belgeler için: çubuğu değiştirmediğini vaka çiftleri gösterir)
        return bar_expect(health_fill(cfg, a), cfg)
    if kind == "armor":
        return bar_expect(armor_fill(cfg, a), cfg)
    if kind in ("food", "saturation"):
        return bar_expect(Fill(0, -1, single_fill(a, 20, layer_q)), cfg)
    if kind == "absorption":
        return bar_expect(Fill(0, -1, single_fill(a, b, layer_q)), cfg)
    raise ValueError(kind)


def geom_case(cfg, a, b):
    g = geometry(cfg, int(a), int(b))
    return f"{g['text_left']};{g['text_right']};{g['y_a']};{g['y_b']};{g['xp_top']}"


def width6(text):
    """Testlerdeki sahte yazı genişliği (gerçek Font yok): karakter başına 6 px. Java aynısını kullanır."""
    return 6 * len(text)


def text_case(cfg, kind, lang, args):
    suffixes = lang_suffixes(lang)
    parts = args.split("|")
    min_scale = cfg["layout"]["text_min_scale"]
    if kind == "compact":
        return compact(float(parts[0]), suffixes)
    if kind == "health_text":
        cur, mx, ab, room = (float(p) for p in parts)
        text, scale = fit(health_candidates(cur, mx, ab, suffixes), room, width6, min_scale)
    elif kind == "armor_text":
        armor, tough, room = (float(p) for p in parts)
        text, scale = fit(armor_candidates(armor, tough, suffixes), room, width6, min_scale)
    else:
        raise ValueError(kind)
    return f"{text}|{scale:.3f}"


BAR_CASES = (
    # (id, kind, a, b) — docs/04 §1 örnekleri ve DoD ekranları (20/21/250 HP; zırh 0/40/310; binek) + sınırlar.
    # Can için b = azami: çubuğu DEĞİŞTİRMEZ (aynı a, farklı b çiftleri bunu kanıtlar; azami yalnızca sayıda görünür).
    ("hp_0", "health", 0, 20), ("hp_0_5", "health", 0.5, 20), ("hp_1", "health", 1, 20), ("hp_4", "health", 4, 20),
    ("hp_10_of_20", "health", 10, 20), ("hp_19_5", "health", 19.5, 20),
    ("hp_20", "health", 20, 20), ("hp_21_of_21", "health", 21, 21), ("hp_21_of_40", "health", 21, 40),
    ("hp_25_of_40", "health", 25, 40), ("hp_39_of_40", "health", 39, 40), ("hp_40", "health", 40, 40),
    ("hp_41_of_60", "health", 41, 60), ("hp_100", "health", 100, 100), ("hp_119_of_120", "health", 119, 120),
    ("hp_120", "health", 120, 120), ("hp_121", "health", 121, 121), ("hp_139_of_214", "health", 139, 214),
    ("hp_159", "health", 159, 214), ("hp_160", "health", 160, 214), ("hp_170", "health", 170, 214), ("hp_179", "health", 179, 214),
    ("hp_180", "health", 180, 180), ("hp_200", "health", 200, 214), ("hp_214", "health", 214, 214), ("hp_215_of_250", "health", 215, 250),
    ("hp_235_of_250", "health", 235, 250), ("hp_250", "health", 250, 250), ("hp_125_of_250", "health", 125, 250),
    ("hp_125_of_125", "health", 125, 125), ("hp_1_of_250", "health", 1, 250),
    ("hp_over_max", "health", 30, 20), ("hp_huge", "health", 249295, 249295), ("hp_huge_half", "health", 124648, 249295),
    ("armor_0", "armor", 0, 0), ("armor_1", "armor", 1, 0), ("armor_4", "armor", 4, 0), ("armor_20", "armor", 20, 0),
    ("armor_34", "armor", 34, 0), ("armor_40", "armor", 40, 0), ("armor_75", "armor", 75, 0), ("armor_114", "armor", 114, 0),
    ("armor_147", "armor", 147, 0), ("armor_159", "armor", 159, 0), ("armor_160", "armor", 160, 0), ("armor_310", "armor", 310, 0),
    ("armor_354", "armor", 354, 0), ("armor_442", "armor", 442, 0), ("armor_900", "armor", 900, 0),
    ("vehicle_30", "vehicle", 30, 30), ("vehicle_15_of_30", "vehicle", 15, 30), ("vehicle_53_of_53", "vehicle", 53, 53),
    ("food_0", "food", 0, 0), ("food_1", "food", 1, 0), ("food_5", "food", 5, 0), ("food_10", "food", 10, 0),
    ("food_19", "food", 19, 0), ("food_20", "food", 20, 0),
    ("sat_0", "saturation", 0, 0), ("sat_2_5", "saturation", 2.5, 0), ("sat_20", "saturation", 20, 0),
    ("abs_4_of_4", "absorption", 4, 4), ("abs_2_of_4", "absorption", 2, 4), ("abs_0_1_of_20", "absorption", 0.1, 20),
    ("abs_20_of_20", "absorption", 20, 20),
)
GEOM_CASES = (("geom_427x240", 427, 240), ("geom_320x180", 320, 180), ("geom_854x480", 854, 480), ("geom_1920x1080", 1920, 1080))
TEXT_CASES = (
    # (id, kind, lang, args)
    ("n_0", "compact", "en_us", "0"), ("n_9999", "compact", "en_us", "9999"), ("n_10000", "compact", "en_us", "10000"),
    ("n_43200_en", "compact", "en_us", "43200"), ("n_43200_tr", "compact", "tr_tr", "43200"),
    ("n_249295_en", "compact", "en_us", "249295"), ("n_249295_tr", "compact", "tr_tr", "249295"),
    ("n_999949", "compact", "tr_tr", "999949"), ("n_999950_promote", "compact", "tr_tr", "999950"),
    ("n_12M_tr", "compact", "tr_tr", "12345678"), ("n_3B_tr", "compact", "tr_tr", "3456789012"),
    ("n_5T_tr", "compact", "tr_tr", "5678901234567"), ("n_neg", "compact", "en_us", "-5"),
    ("h_plain_en", "health_text", "en_us", "20|20|0|94"), ("h_abs_en", "health_text", "en_us", "214|214|12|94"),
    ("h_big_tr", "health_text", "tr_tr", "43200|249295|12|94"), ("h_big_full_tr", "health_text", "tr_tr", "249295|249295|12345|94"),
    ("h_shrink_tr", "health_text", "tr_tr", "214|214|12|90"), ("h_drop_abs", "health_text", "tr_tr", "214|214|12|60"),
    ("h_drop_max", "health_text", "tr_tr", "214|214|12|30"), ("h_tiny_room", "health_text", "tr_tr", "214|214|12|4"),
    ("h_frac_hp", "health_text", "en_us", "0.5|20|0|94"),
    ("a_plain", "armor_text", "en_us", "310|0|94"), ("a_tough_tr", "armor_text", "tr_tr", "354|10|94"),
    ("a_big_tr", "armor_text", "tr_tr", "123456|12345|94"), ("a_drop_tough", "armor_text", "tr_tr", "354|10|40"),
)
FX_CASES = (
    # (id, kind, args)
    ("blink_never_started", "blink", "0|10"), ("blink_phase_a", "blink", "20|0"), ("blink_phase_b", "blink", "20|2"),
    ("blink_phase_c", "blink", "20|3"), ("blink_phase_d", "blink", "20|6"), ("blink_over", "blink", "20|20"),
    ("regen_0", "regen", "0"), ("regen_2", "regen", "2"), ("regen_8", "regen", "8"), ("regen_10_pause", "regen", "10"), ("regen_wraps", "regen", "26"),
    ("low_vanilla_4_of_20", "low", "4|20"), ("low_5_of_20", "low", "5|20"), ("low_42_of_214", "low", "42|214"), ("low_43_of_214", "low", "43|214"),
    ("hunger_tick0_empty", "hunger", "0|20|0"), ("hunger_tick61_food20", "hunger", "61|20|0"), ("hunger_saturated", "hunger", "61|20|1.5"),
    ("hunger_other_tick", "hunger", "5|20|0"),
    ("tracker_steady", "tracker", "0:20:0;10:20:0;30:20:0"),
    ("tracker_damage", "tracker", "0:20:0;10:17:1;11:17:1"),
    ("tracker_damage_hold_over", "tracker", "0:20:0;10:17:1;40:17:1"),
    ("tracker_heal", "tracker", "0:10:0;5:13:1;6:13:1"),
    ("tracker_respawn_reset", "tracker", "0:20:0;50:3:1;2:20:0"),
)
