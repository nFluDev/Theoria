#!/usr/bin/env python3
"""HUD ÇEVRİMDIŞI önizleme (Faz 3). Ekran yakalamak yasak (CLAUDE.md) → HUD'u aynı modelden, vanilla sprite'lardan ve
vanilla bitmap fontundan kendimiz çizeriz:  python3 -I tools/hud_preview.py [--scale 3] [--gui 427x240] [--lang tr_tr]

Çıktı: build/hud-preview/<durum>.png ve gallery.png (hepsi alt alta). Bu araç Java'nın çizim mantığının aynasıdır
(StatusLayer / StatusBars / HudNumbers / LevelLayer) — YAKLAŞIK bir önizlemedir: GPU örnekleme, Arayüz Ölçeği yuvarlaması ve
başka modların katmanları yoktur. Kesin onay oyunda kullanıcıdadır (Vulkan + OpenGL); bu araç yerleşim/renk/taşma hatalarını
oyuna girmeden yakalamak içindir. Model ve geometri tools/hud_model.py'den gelir (Java ile vaka tablolarıyla eşit).
"""
import argparse
import math
import pathlib
import sys
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "generator"))
import hud as sprite_generator  # noqa: E402
import hud_model as M  # noqa: E402
import pngio  # noqa: E402

OUT = ROOT / "build/hud-preview"
OWN = ROOT / "mods-src/theoria/src/main/resources/assets/theoria/textures/gui/sprites"
VANILLA = "assets/minecraft/textures/gui/sprites/"
WHITE = (255, 255, 255)


class Assets:
    def __init__(self):
        jar = sprite_generator.vanilla_jar()
        if jar is None:
            raise SystemExit("hud_preview: vanilla istemci jar'ı bulunamadı (THEORIA_VANILLA_JAR / Prism 26.3 / ./gradlew build)")
        self.zip = zipfile.ZipFile(jar)
        self.cache = {}
        _w, _h, self.font = pngio.decode(self.zip.read("assets/minecraft/textures/font/ascii.png"))

    def vanilla(self, name):
        if name not in self.cache:
            self.cache[name] = pngio.decode(self.zip.read(f"{VANILLA}{name}.png"))[2]
        return self.cache[name]

    def own(self, name):
        key = "own:" + name
        if key not in self.cache:
            self.cache[key] = pngio.decode((OWN / f"{name}.png").read_bytes())[2]
        return self.cache[key]

    def glyph(self, ch):
        """(bitmap satırları, genişlik): vanilla ascii.png 8x8 hücre; genişlik = son dolu sütun + 1; boşluk 4."""
        if ch == " ":
            return None, 4
        row, col = divmod(ord(ch), 16)
        bits = [[self.font[row * 8 + y][col * 8 + x][3] > 0 for x in range(8)] for y in range(8)]
        width = max((x + 1 for line in bits for x, on in enumerate(line) if on), default=0)
        return bits, width

    def text_width(self, text):
        """Font.width: her karakterin ilerlemesi = glif genişliği + 1 (boşluk 4)."""
        return sum(4 if c == " " else self.glyph(c)[1] + 1 for c in text)


class Canvas:
    """GUI pikseli cinsinden çizer, k kat büyütülmüş cihaz pikseline yazar (en yakın komşu, GPU gibi)."""

    def __init__(self, gui_w, gui_h, k):
        self.w, self.h, self.k = gui_w, gui_h, k
        top, bottom = (124, 156, 192), (75, 107, 58)
        self.px = [[tuple(round(top[c] + (bottom[c] - top[c]) * y / (gui_h * k)) for c in range(3)) + (255,)
                    for _ in range(gui_w * k)] for y in range(gui_h * k)]

    def _rect(self, x0, y0, x1, y1, color, alpha=255):
        k = self.k
        xa, xb = round(x0 * k), max(round(x1 * k), round(x0 * k) + 1)
        ya, yb = round(y0 * k), max(round(y1 * k), round(y0 * k) + 1)
        a = alpha / 255
        for y in range(max(ya, 0), min(yb, self.h * k)):
            row = self.px[y]
            for x in range(max(xa, 0), min(xb, self.w * k)):
                d = row[x]
                row[x] = (round(d[0] * (1 - a) + color[0] * a), round(d[1] * (1 - a) + color[1] * a), round(d[2] * (1 - a) + color[2] * a), 255)

    def blit(self, sprite, x, y, tint=None, u0=0, u1=None, alpha_mul=1.0):
        """Sprite (rows) → (x, y); yalnız [u0, u1) sütunları (kısmi blit). tint = (r,g,b) çarpanı, alpha_mul üstüne."""
        u1 = len(sprite[0]) if u1 is None else u1
        for sy, row in enumerate(sprite):
            for sx in range(u0, u1):
                r, g, b, a = row[sx]
                if a == 0:
                    continue
                if tint:
                    r, g, b = r * tint[0] // 255, g * tint[1] // 255, b * tint[2] // 255
                self._rect(x + sx, y + sy, x + sx + 1, y + sy + 1, (r, g, b), round(a * alpha_mul))

    def text(self, assets, text, x, y, scale, color, shadow=True):
        """Bitmap font; glif pikseli = scale GUI pikseli (Java: pose.scale). Gölge: (+scale, +scale), renk/4 (vanilla)."""
        for dx, dy, col in (((scale, scale, tuple(c // 4 for c in color)),) if shadow else ()) + ((0, 0, color),):
            cursor = 0
            for ch in text:
                bits, width = assets.glyph(ch)
                if bits:
                    for gy, line in enumerate(bits):
                        for gx, on in enumerate(line):
                            if on:
                                px, py = x + dx + (cursor + gx) * scale, y + dy + gy * scale
                                self._rect(px, py, px + scale, py + scale, col)
                cursor += width + 1 if ch != " " else 4

    def png(self, x0=0, y0=0, x1=None, y1=None):
        x1 = self.w if x1 is None else x1
        y1 = self.h if y1 is None else y1
        return pngio.encode([row[x0 * self.k:x1 * self.k] for row in self.px[y0 * self.k:y1 * self.k]])


def rgb(argb):
    return ((argb >> 16) & 255, (argb >> 8) & 255, argb & 255)


def draw_icon(cv, sprite, x, y, cfg, quarters, from_right, tint=None, alpha=1.0):
    """StatusBars.blit'in aynası: `quarters` çeyreğin piksel genişliği kadar sütun — soldan ya da (from_right) sağdan."""
    clip = M.clip_width(cfg, quarters)
    if clip <= 0:
        return
    size = cfg["layout"]["icon_size"]
    u0, u1 = (0, size) if clip >= size else (size - clip, size) if from_right else (0, clip)
    cv.blit(sprite, x, y, tint, u0, u1, alpha)


def layered_row(cv, A, cfg, xs, y, fill, palette, container, layer0, neutral, effect_tint, from_right, with_container=True, ghost=None):
    """StatusBars.layered'in aynası. ghost = (kaybedilen değerin dolgusu, vanilla yanıp sönen kat-0 sprite'ı): beyaz tonda altta çizilir."""
    qpi = cfg["layout"]["quarters_per_icon"]

    for x in xs:
        draw_icon(cv, container, x, y, cfg, qpi, False)

    def fill_layers(f, layer0_sprite, override):
        def put(x, layer, quarters):
            if layer == 0:
                draw_icon(cv, layer0_sprite, x, y, cfg, quarters, from_right)
            else:
                tint = override if override is not None else rgb(M.parse_color(palette[layer]))
                draw_icon(cv, neutral, x, y, cfg, quarters, from_right, tint)
        if f.base >= 0:
            for x in xs:
                put(x, f.base, qpi)
        if f.top > 0:
            for x, q in zip(xs, M.icon_quarters(f.top, cfg)):
                if q > 0:
                    put(x, f.layer, q)

    if ghost is not None:
        fill_layers(ghost[0], ghost[1], WHITE)
    fill_layers(fill, layer0, effect_tint)


def single_row(cv, cfg, xs, y, quarters, container, fill, from_right, tint=None, alpha=1.0):
    if container is not None:
        for x in xs:
            draw_icon(cv, container, x, y, cfg, cfg["layout"]["quarters_per_icon"], False)
    for x, q in zip(xs, M.icon_quarters(quarters, cfg)):
        draw_icon(cv, fill, x, y, cfg, q, from_right, tint, alpha)


def render(A, cfg, s, gui_w, gui_h, k, lang):
    """Bir sahne (dict) → Canvas. Sırası: arka plan, hotbar, XP, çubuklar, sayılar (Java katman sırası)."""
    lo, tints = cfg["layout"], cfg["tints"]
    cv = Canvas(gui_w, gui_h, k)
    geo = M.geometry(cfg, gui_w, gui_h)
    centre = gui_w // 2
    # hotbar + XP çubuğu (vanilla sprite'ları)
    cv.blit(A.vanilla("hud/hotbar"), centre - 91, gui_h - 22)
    xp_bg, xp_fg = A.vanilla("hud/experience_bar_background"), A.vanilla("hud/experience_bar_progress")
    cv.blit(xp_bg, centre - 91, geo["xp_top"])
    cv.blit(xp_fg, centre - 91, geo["xp_top"], None, 0, min(182, int(s.get("xp", (0, 0.0))[1] * 183)))
    level = s.get("xp", (0, 0.0))[0]
    if level > 0 and not s.get("jump_bar"):
        text, scale = str(level), lo["xp_text_scale"]
        tx = centre - A.text_width(text) * scale / 2
        ty = geo["xp_top"] + (lo["xp_bar_height"] - lo["glyph_height"] * scale) / 2
        off = lo["xp_text_outline"]
        for ox, oy in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)):
            cv.text(A, text, tx + ox * off, ty + oy * off, scale, (0, 0, 0), False)
        cv.text(A, text, tx, ty, scale, rgb(M.parse_color(cfg["text"]["xp_level"])), False)

    icons, step, size = lo["icons"], lo["icon_step"], lo["icon_size"]
    left = [centre - lo["bar_edge"] + i * step for i in range(icons)]
    right = [centre + lo["bar_edge"] - i * step - size for i in range(icons)]
    hardcore, kind = s.get("hardcore", False), s.get("effect")  # None | poison | wither | frozen
    h_suffix = "_hardcore" if hardcore else ""
    full_name = {None: "full", "poison": "poisoned_full", "wither": "withered_full", "frozen": "frozen_full"}[kind]
    if hardcore:
        full_name = {"full": "hardcore_full", "poisoned_full": "poisoned_hardcore_full", "withered_full": "withered_hardcore_full",
                     "frozen_full": "frozen_hardcore_full"}[full_name]
    effect_tint = rgb(M.parse_color(tints["effect_" + kind])) if kind else None
    container = A.vanilla("hud/heart/container" + h_suffix)

    cur, mx = s["health"]
    blink = s.get("ghost") is not None  # hasar yanıp sönmesi: kaybedilen kısım eski değerde beyaz kalır (StatusLayer'ın aynası)
    if blink:
        container = A.vanilla("hud/heart/container" + h_suffix + "_blinking")
    layered_row(cv, A, cfg, left, geo["y_b"], M.health_fill(cfg, cur), cfg["health"]["palette"], container,
                A.vanilla("hud/heart/" + full_name), A.own("hud/heart_neutral"), effect_tint, False, with_container=True,
                ghost=(M.health_fill(cfg, s["ghost"]), A.vanilla("hud/heart/" + full_name + "_blinking")) if blink else None)
    armor = s.get("armor", 0)
    if armor > 0:
        layered_row(cv, A, cfg, left, geo["y_a"], M.armor_fill(cfg, armor), cfg["armor"]["palette"], A.vanilla("hud/armor_empty"),
                    A.vanilla("hud/armor_full"), A.own("hud/armor_neutral"), None, False)
    absorption = s.get("absorption", 0)
    if absorption > 0:
        peak = s.get("absorption_peak", absorption)
        single_row(cv, cfg, left, geo["y_a"] - lo["row_height"], M.single_fill(absorption, peak, M.per_layer(cfg)), container,
                   A.vanilla("hud/heart/absorbing_" + ("hardcore_" if hardcore else "") + "full"), False)
    hunger = s.get("hunger", False)
    suffix = "_hunger" if hunger else ""
    food = s.get("food", 20)
    single_row(cv, cfg, right, geo["y_b"], M.single_fill(food, 20, M.per_layer(cfg)), A.vanilla("hud/food_empty" + suffix),
               A.vanilla("hud/food_full" + suffix), True)
    sat = M.single_fill(s.get("sat", 0), 20, M.per_layer(cfg))
    if sat > 0:
        argb = M.parse_color(tints["saturation"])
        single_row(cv, cfg, right, geo["y_b"], sat, None, A.own("hud/food_neutral"), True, rgb(argb), (argb >> 24) / 255)
    if s.get("vehicle"):
        vc, vm = s["vehicle"]
        layered_row(cv, A, cfg, right, geo["y_a"], M.health_fill(cfg, vc), cfg["health"]["palette"], A.vanilla("hud/heart/vehicle_container"),
                    A.vanilla("hud/heart/vehicle_full"), A.own("hud/vehicle_heart_neutral"), None, True)

    # sayılar (HudNumbers)
    suffixes = M.lang_suffixes(lang)
    room = geo["text_right"] - geo["text_left"]
    min_scale = lo["text_min_scale"]
    h_text, h_scale = M.fit(M.health_candidates(cur, mx, absorption, suffixes), room, A.text_width, min_scale)
    a_text = a_scale = None
    if armor > 0:
        a_text, a_scale = M.fit(M.armor_candidates(armor, s.get("toughness", 0), suffixes), room, A.text_width, min_scale)
    scale = h_scale if a_text is None else min(h_scale, a_scale)
    for text, row_y, color in ((h_text, geo["y_b"], cfg["text"]["health"]), (a_text, geo["y_a"], cfg["text"]["armor"])):
        if text:
            cv.text(A, text, centre - A.text_width(text) * scale / 2, row_y + (size - lo["glyph_height"] * scale) / 2, scale,
                    rgb(M.parse_color(color)))
    return cv, (h_text, h_scale, a_text, a_scale)


SCENES = (
    ("01_hp20_armor0_vanilla_start", "20 HP, zirh 0 (vanilla baslangic)", dict(health=(20, 20), food=20)),
    ("02_hp21", "21/21 HP: 5 kirmizi zemin + ilk kalbin ilk ceyregi turuncu", dict(health=(21, 21), food=20, sat=5)),
    ("03_hp25_of_40_armor34", "25/40 HP, zirh 34", dict(health=(25, 40), armor=34, food=14, sat=0, xp=(7, 0.4))),
    ("04_hp90_of_250_armor40", "90/250 HP (mutlak: kat 4), zirh 40", dict(health=(90, 250), armor=40, food=20, sat=20, xp=(12, 0.62))),
    ("05_hp175_armor147", "175 HP (beyaz katin ucu), zirh 147 (T8)", dict(health=(175, 214), armor=147, toughness=4, food=20, xp=(12, 0.1))),
    ("06_hp214_armor310", "214/214 HP ve zirh 310: son renk beyaz / mor, kalici", dict(health=(214, 214), armor=310, toughness=10, food=17, sat=2.5, xp=(40, 0.9))),
    ("07_hp235_armor354_horse", "235/250 HP (hala beyaz), zirh 354, binekte 22/30", dict(health=(235, 250), armor=354, toughness=10, vehicle=(22, 30), food=17, jump_bar=True)),
    ("08_absorb_armor60", "125/250 HP, zirh 60, emilim 8/12", dict(health=(125, 250), armor=60, absorption=8, absorption_peak=12, food=9, xp=(12, 0.1))),
    ("09_damage_blink", "Hasar: 18 HP, kaybedilen 7 ceyrek beyaz yanip soner (eski 25)", dict(health=(18, 40), ghost=25, armor=0, food=20)),
    ("10_big_numbers", "43200/249295 HP, zirh 123456 (+12345), emilim 12", dict(health=(43200, 249295), armor=123456, toughness=12345, absorption=12, food=20, xp=(9999, 0.5))),
    ("11_poison_low", "Zehir, 3/20 HP, aclik 4 (Hunger)", dict(health=(3, 20), effect="poison", food=4, hunger=True)),
    ("12_wither_hardcore", "Solma, hardcore, 60/120 HP", dict(health=(60, 120), effect="wither", hardcore=True, armor=75, food=20)),
)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scale", type=int, default=3, help="cihaz pikseli / GUI pikseli (Arayüz Ölçeği)")
    parser.add_argument("--gui", default="427x240", help="GUI-ölçekli pencere (1280x720 @ ölçek 3 = 427x240)")
    parser.add_argument("--lang", default="tr_tr", choices=("tr_tr", "en_us"))
    options = parser.parse_args()
    gui_w, gui_h = (int(v) for v in options.gui.split("x"))
    cfg, A, k = M.load(), Assets(), options.scale
    OUT.mkdir(parents=True, exist_ok=True)
    strips = []
    for name, caption, scene in SCENES:
        cv, (h_text, h_scale, a_text, a_scale) = render(A, cfg, scene, gui_w, gui_h, k, options.lang)
        # kırp: yalnız alt orta bölge; başlığı çiz
        x0, x1, y0 = gui_w // 2 - 120, gui_w // 2 + 120, gui_h - 82
        cv.text(A, caption, x0 + 3, y0 + 2, 1, WHITE)
        (OUT / f"{name}.png").write_bytes(cv.png(x0, y0, x1, gui_h))
        strips.append(cv.px[y0 * k:gui_h * k])
        cols = slice(x0 * k, x1 * k)
        strips[-1] = [row[cols] for row in strips[-1]]
        a_info = f", zirh '{a_text}' x{a_scale:.2f}" if a_text else ""
        print(f"  {name}: can '{h_text}' x{h_scale:.2f}{a_info}")
    gallery = [row for strip in strips for row in strip]
    (OUT / "gallery.png").write_bytes(pngio.encode(gallery))
    print(f"hud_preview: {len(SCENES)} sahne → {OUT.relative_to(ROOT)}/ (ölçek {k}, GUI {gui_w}x{gui_h}, dil {options.lang}); galeri: gallery.png")


if __name__ == "__main__":
    main()
