#!/usr/bin/env python3
"""HUD denetimi (Faz 3). Çalıştır: python3 -I tools/check_hud.py   (yeniden üret: --write · negatif test: --break)

Ekranı yakalamak yasak (CLAUDE.md); görsel onay kullanıcıdadır. Bu araç ekranda görülemeyecek hataları önceden keser:
  1. hud.json şeması ve içsel tutarlılık (yerleşim, XP yazısı barın içine sığıyor, birim/efekt değerleri).
  2. Palet kuralları (D16/D17), ölçülebilir hâliyle: can paleti kırmızıyla başlar, YEŞİL ve SİYAH yok (zehir/solma renkleri),
     ortalarda turkuaz var, son renk beyaz; zırh paleti ışık spektrumu sırasında (kırmızı → mor), son renk mor; renkler birbirine
     karışmıyor (ikili uzaklık eşiği).
  3. Katman modeli, docs/04 §1 ve DoD'nin elle yazılmış beklenenlerine karşı (20/21/25/40 HP, 200/215/235 HP hep beyaz, zırh…).
     Beklenenler hud_model.py'den ÇIKARILMADI — modelin kendi kendini doğrulamaması için bağımsız yazıldı.
  4. Özellik taramaları: çeyrek sayısı değerle azalmaz; son renk kalıcı (başa dönme yok); azami can çubuğu etkilemez.
  5. data/hud/hud_cases.csv + hud_text_cases.csv + hud_fx_cases.csv güncel mi — Java (`/theoria test hud`, GameTest
     `hud_model`) bunlarla sınanır.
  6. Sprite'lar: Java'nın başvurduğu her kimlik var (vanilla jar'da / kendi PNG'miz), kendi sprite'larımız nötr (r==g==b;
     `setColor` ÇARPAR), `_half` sprite'ına başvuru yok (çeyrek kırpmayla çizilir), artık PNG yok; generator/hud.py ile özdeş.
  7. Lang: sayı kısaltma anahtarları iki dilde var (hud_model.lang_suffixes yoksa KeyError).
Çıkış 0 + son satır 'OK' = temiz.
"""
import argparse
import colorsys
import csv
import io
import itertools
import pathlib
import re
import subprocess
import sys
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "generator"))
import hud_model as M  # noqa: E402
import pngio  # noqa: E402
import hud as sprite_generator  # noqa: E402  (generator/hud.py)

DATA = ROOT / "data/hud"
JAVA = ROOT / "mods-src/theoria/src/main/java/dev/theoria/hud"
SPRITE_DIR = ROOT / "mods-src/theoria/src/main/resources/assets/theoria/textures/gui/sprites/hud"
errors, notes = [], []


def err(message):
    errors.append(message)


# ---------------------------------------------------------------------------------------------- 1. şema
def check_config(cfg):
    lo = cfg["layout"]
    for key in ("icons", "quarters_per_icon", "icon_size", "icon_step", "row_height", "bottom", "reserved_rows", "bar_edge",
                "text_pad", "glyph_height", "xp_bar_height"):
        if not isinstance(lo.get(key), int) or lo[key] <= 0:
            err(f"hud.json layout.{key}: pozitif tam sayı olmalı ({lo.get(key)!r})")
    if (lo["icons"], lo["quarters_per_icon"]) != (5, 4):
        err(f"hud.json: D1 5 ikon x 4 çeyrek ister, bulunan {lo['icons']} x {lo['quarters_per_icon']}")
    if not 0 < lo["text_min_scale"] <= 1:
        err("hud.json layout.text_min_scale (0,1] aralığında olmalı")
    if lo["xp_text_scale"] * lo["glyph_height"] > lo["xp_bar_height"]:
        err(f"XP yazısı barın içine sığmıyor: {lo['xp_text_scale']} x {lo['glyph_height']} > {lo['xp_bar_height']}")
    if lo["icon_step"] > lo["icon_size"]:
        err("icon_step > icon_size: ikonlar arası boşluk kalır (vanilla 8/9)")
    if cfg["health"]["unit"] != 1:
        err(f"hud.json health.unit {cfg['health']['unit']}: D1 'bir çeyrek = 1 HP' der")
    if not cfg["armor"]["unit"] > 0:
        err("hud.json armor.unit pozitif olmalı")
    for key in ("effect_poison", "effect_wither", "effect_frozen", "saturation", "blink"):
        if not re.fullmatch(r"[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?", cfg["tints"][key]):
            err(f"hud.json tints.{key}: renk değil")
    fx = cfg["effects"]
    for key in ("blink_damage_ticks", "blink_heal_ticks", "blink_phase_ticks", "display_hold_ticks", "low_health_jitter", "regen_lift",
                "regen_step_ticks", "regen_pause", "hunger_shake"):
        if not isinstance(fx.get(key), int) or fx[key] <= 0:
            err(f"hud.json effects.{key}: pozitif tam sayı olmalı ({fx.get(key)!r})")
    if not 0 < fx["low_health_fraction"] < 1:
        err("hud.json effects.low_health_fraction (0,1) aralığında olmalı")
    # bilgi: zırh göstergesi hangi kademede doyar (unit = 1 → 'HP gibi' → palet aralığında bitiyor)
    span = len(cfg["armor"]["palette"]) * M.per_layer(cfg) * cfg["armor"]["unit"]
    saturated = []
    with open(ROOT / "data/balance/tiers.csv", newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if float(row["total"]) >= span:
                saturated.append(f"T{row['idx']}")
    notes.append(f"hud.json: can {len(cfg['health']['palette'])} renk (≥ {len(cfg['health']['palette']) * M.per_layer(cfg)} HP hep son renk), "
                 f"zırh {len(cfg['armor']['palette'])} renk (≥ {span:g} zırh hep son renk; takım toplamı bunu aşan kademeler: "
                 f"{', '.join(saturated) if saturated else 'yok'})")


# ---------------------------------------------------------------------------------------------- 2. palet kuralları
VANILLA_REPRESENTATIVE = {"health": (255, 19, 19), "armor": (184, 185, 196)}
MIN_DISTANCE = 100  # redmean uzaklığı (0..765): 'birbirine karışmayan'


def rgb_of(entry, name):
    if entry == "vanilla":
        return VANILLA_REPRESENTATIVE[name]
    value = int(entry, 16) & 0xFFFFFF
    return (value >> 16) & 255, (value >> 8) & 255, value & 255


def hue_sat_val(rgb):
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in rgb))
    return h * 360, s, v


def redmean(a, b):
    rm = (a[0] + b[0]) / 2
    dr, dg, db = a[0] - b[0], a[1] - b[1], a[2] - b[2]
    return ((2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db) ** 0.5


def check_palettes(cfg):
    for name in ("health", "armor"):
        palette = cfg[name]["palette"]
        if len(palette) < 9:
            err(f"{name}.palette: ≥ 9 renk istendi (D16/D17), bulunan {len(palette)}")
        if palette[0] != "vanilla":
            err(f"{name}.palette: kat 0 'vanilla' olmalı (boyanmaz)")
        colours = [rgb_of(e, name) for e in palette]
        for (a, ra), (b, rb) in itertools.combinations(list(zip(palette, colours)), 2):
            if redmean(ra, rb) < MIN_DISTANCE:
                err(f"{name}.palette: '{a}' ile '{b}' birbirine karışıyor (uzaklık {redmean(ra, rb):.0f} < {MIN_DISTANCE})")
        if len(set(palette)) != len(palette):
            err(f"{name}.palette: yinelenen renk")
    health = [rgb_of(e, "health") for e in cfg["health"]["palette"]]
    for entry, rgb in zip(cfg["health"]["palette"][1:], health[1:]):
        hue, sat, val = hue_sat_val(rgb)
        if sat > 0.25 and 70 <= hue <= 165:
            err(f"health.palette: '{entry}' yeşil (ton {hue:.0f}°) — zehir rengi, kullanılmaz (D16)")
        if val < 0.45:
            err(f"health.palette: '{entry}' siyaha yakın (parlaklık {val:.2f}) — solma rengi, kullanılmaz (D16)")
    if cfg["health"]["palette"][-1].upper() != "FFFFFF":
        err("health.palette: son renk beyaz (FFFFFF) olmalı ve kalıcıdır (D16)")
    n = len(health)
    turquoise = [i for i, rgb in enumerate(health) if i and 165 <= hue_sat_val(rgb)[0] <= 195 and hue_sat_val(rgb)[1] > 0.5]
    if not any(n / 3 <= i <= 2 * n / 3 for i in turquoise):
        err("health.palette: orta kısımda (üçte biri ile üçte ikisi arası) turkuaz (ton 165-195°) yok (D16)")
    first_hue = hue_sat_val(health[0])[0]
    if not (first_hue <= 10 or first_hue >= 350):
        err("health.palette: kat 0 kırmızı olmalı")
    armor = [rgb_of(e, "armor") for e in cfg["armor"]["palette"]]
    hues = [hue_sat_val(rgb)[0] for rgb in armor[1:]]
    if any(b <= a for a, b in zip(hues, hues[1:])):
        err("armor.palette: kat 1'den itibaren renkler ışık spektrumu sırasında (ton artan: kırmızı → turuncu → sarı → yeşil → camgöbeği → mavi → çivit → mor) değil (D17): "
            + ", ".join(f"{h:.0f}°" for h in hues))
    if hues and not (hues[0] <= 15 or hues[0] >= 345):
        err("armor.palette: spektrum kırmızıyla başlamalı (kat 1)")
    if hues and not 260 <= hues[-1] <= 295:
        err(f"armor.palette: son renk mor olmalı (ton 260-295°), bulunan {hues[-1]:.0f}°")
    notes.append(f"palet kuralları: can {n} renk (kırmızı → … turkuaz (kat {turquoise}) … → beyaz, yeşil/siyah yok), zırh spektrum sırası kırmızıdan mora; "
                 f"en yakın iki renk arası ≥ {MIN_DISTANCE}")


# ---------------------------------------------------------------------------------------------- 3. beklenenler
# (açıklama, tür, a, b, 'katman;zemin;üst;ikon-çeyrekleri'). Elle yazıldı; hud_model.py'den türetilmedi.
EXPECTED = (
    ("20 HP: beş kırmızı kalp (kat 0 zemin, üst boş)", "health", 20, 20, "1;0;0;00000"),
    ("10 HP: iki buçuk kalp", "health", 10, 20, "0;-1;10;44200"),
    ("1 HP: tek çeyrek", "health", 1, 20, "0;-1;1;10000"),
    ("0,5 HP: ölü görünmez, en az bir çeyrek", "health", 0.5, 20, "0;-1;1;10000"),
    ("0 HP: boş", "health", 0, 20, "0;-1;0;00000"),
    ("19,5 HP: vanilla gibi yukarı yuvarla → 20 çeyrek = tam bir kat", "health", 19.5, 20, "1;0;0;00000"),
    ("21 HP: 5 kırmızı zemin + ilk kalbin ilk çeyreği turuncu", "health", 21, 21, "1;0;1;10000"),
    ("25 HP: 1 kalp + 1 çeyrek", "health", 25, 40, "1;0;5;41000"),
    ("40 HP: beş turuncu kalp (kat 1 tam)", "health", 40, 40, "2;1;0;00000"),
    ("41 HP: 5 turuncu zemin + sarı çeyrek", "health", 41, 60, "2;1;1;10000"),
    ("120 HP: kat 5 tam, kat 6 başlıyor", "health", 120, 120, "6;5;0;00000"),
    ("160 HP: son renk (beyaz) katı başlıyor, zemin kat 7 tam", "health", 160, 160, "8;7;0;00000"),
    ("170 HP: beyaz yarım kat", "health", 170, 170, "8;7;10;44200"),
    ("180 HP: palet doldu, beyaz tam", "health", 180, 180, "8;7;20;44444"),
    ("200 HP: beyaz (D16 örneği)", "health", 200, 200, "8;7;20;44444"),
    ("215 HP: hâlâ beyaz, başa dönmez (D16 örneği)", "health", 215, 215, "8;7;20;44444"),
    ("235 HP: hâlâ beyaz (D16 örneği)", "health", 235, 235, "8;7;20;44444"),
    ("1000 HP: son renk kalıcı", "health", 1000, 1000, "8;7;20;44444"),
    ("125/250: renk mutlak HP'dir (azami fark etmez): kat 6, 5 çeyrek", "health", 125, 250, "6;5;5;41000"),
    ("125/125: aynı çubuk", "health", 125, 125, "6;5;5;41000"),
    ("1/250: yine de bir çeyrek", "health", 1, 250, "0;-1;1;10000"),
    ("30/20 (azami geçici düşük): çubuk taşmaz", "health", 30, 20, "1;0;10;44200"),
    ("zırh 0: boş", "armor", 0, 0, "0;-1;0;00000"),
    ("zırh 4 (deri takım): bir tam ikon", "armor", 4, 0, "0;-1;4;40000"),
    ("zırh 20: beş çelik ikon (kat 0 tam)", "armor", 20, 0, "1;0;0;00000"),
    ("zırh 40: iki kat tam, kat 1 zemin", "armor", 40, 0, "2;1;0;00000"),
    ("zırh 147 (T8 elmas): kat 7 başlangıcı, 7 çeyrek", "armor", 147, 0, "7;6;7;43000"),
    ("zırh 160: son renk (mor) katı başlıyor", "armor", 160, 0, "8;7;0;00000"),
    ("zırh 310: mor, tam dolu, kalıcı", "armor", 310, 0, "8;7;20;44444"),
    ("zırh 900: mor, tam dolu", "armor", 900, 0, "8;7;20;44444"),
    ("binek 30/30: 1 kat + 10 çeyrek", "vehicle", 30, 30, "1;0;10;44200"),
    ("binek 15/30: 3,75 kalp", "vehicle", 15, 30, "0;-1;15;44430"),
    ("açlık 20: tam", "food", 20, 0, "0;-1;20;44444"),
    ("açlık 10: iki buçuk ikon", "food", 10, 0, "0;-1;10;44200"),
    ("açlık 1", "food", 1, 0, "0;-1;1;10000"),
    ("satürasyon 2,5 → yukarı yuvarla: 3 çeyrek", "saturation", 2.5, 0, "0;-1;3;30000"),
    ("emilim 4/4: tam", "absorption", 4, 4, "0;-1;20;44444"),
    ("emilim 2/4: yarı", "absorption", 2, 4, "0;-1;10;44200"),
    ("emilim 0,1/20: yine de bir çeyrek", "absorption", 0.1, 20, "0;-1;1;10000"),
)
# elle yazılmış sayı biçimi beklenenleri (en/tr)
EXPECTED_TEXT = (
    ("10 000 kısaltma tr", "compact", "tr_tr", "249295", "249,3B"),
    ("10 000 kısaltma en", "compact", "en_us", "249295", "249.3K"),
    ("9999 olduğu gibi", "compact", "tr_tr", "9999", "9999"),
    ("999 950 → bir üst birime taşar", "compact", "tr_tr", "999950", "1M"),
    ("3,5 milyar tr", "compact", "tr_tr", "3456789012", "3,5Mr"),
    ("docs/04 örneği: 43,2B / 249,3B (+12)", "health_text", "tr_tr", "43200|249295|12|94", "43,2B/249,3B (+12)|0.870"),
)
# elle yazılmış efekt beklenenleri (vanilla Hud.java değerleri)
EXPECTED_FX = (
    ("hasar: bitişe 17 tick kala (17//3 = 5, tek) kalp yanıp sönüyor", "blink", "20|3", "1"),
    ("yenilenme: 10. tickte duraklama", "regen", "10", "-1"),
    ("düşük can: 20 HP'nin %20'si = vanilla'nın 4 HP'si", "low", "4|20", "1"),
    ("düşük can: 5/20 değil", "low", "5|20", "0"),
    ("açlık titremesi: satürasyon 0, food 20, tick 61 (= 20*3+1)", "hunger", "61|20|0", "1"),
    ("hasar sonrası: eski değer (display) 20 tick korunur", "tracker", "0:20:0;10:17:1;11:17:1", "20|0"),
    ("hasar sonrası: 20 tick geçince display güncellenir", "tracker", "0:20:0;10:17:1;40:17:1", "17|0"),
)


def check_expected(cfg, broken):
    if broken:  # negatif test: modeli bilerek boz (katman böleni x3) — beklenenler kırmızıya dönmeli
        original = M.layered
        M.layered = lambda value, unit, plen, lq: original(value, unit, plen, lq * 3)
    bad = 0
    for text, kind, a, b, expect in EXPECTED:
        got = M.bar_case(cfg, kind, a, b)
        if got != expect:
            bad += 1
            err(f"model: {text}: beklenen {expect}, bulunan {got}")
    for text, kind, lang, args, expect in EXPECTED_TEXT:
        got = M.text_case(cfg, kind, lang, args)
        if got != expect:
            bad += 1
            err(f"metin: {text}: beklenen {expect!r}, bulunan {got!r}")
    for text, kind, args, expect in EXPECTED_FX:
        got = M.fx_case(cfg, kind, args)
        if got != expect:
            bad += 1
            err(f"efekt: {text}: beklenen {expect!r}, bulunan {got!r}")
    notes.append(f"model: {len(EXPECTED)} + {len(EXPECTED_TEXT)} + {len(EXPECTED_FX)} elle yazılmış beklenen, {bad} sapma")


# ---------------------------------------------------------------------------------------------- 4. özellikler
def check_properties(cfg):
    layer_q = M.per_layer(cfg)
    swept = 0
    for name, section, fill_of in (("can", "health", lambda v: M.health_fill(cfg, v)), ("zırh", "armor", lambda v: M.armor_fill(cfg, v))):
        palette_len = len(cfg[section]["palette"])
        span = palette_len * layer_q * cfg[section]["unit"]
        previous = (-1, -1)
        for value in sorted({0, 0.4, 0.5} | {i / 2 for i in range(0, 2400)}):
            fill = fill_of(value)
            swept += 1
            key = (fill.layer, fill.top)
            if key < previous:
                err(f"özellik ({name}): değer {value} için dolgu geriledi {key} < {previous} (başa dönme / titreme)")
            if not 0 <= fill.top <= layer_q or fill.base != fill.layer - 1 or not 0 <= fill.layer < palette_len:
                err(f"özellik ({name}): değer {value}: geçersiz dolgu {fill}")
            if value >= span and (fill.layer, fill.top) != (palette_len - 1, layer_q):
                err(f"özellik ({name}): değer {value} ≥ palet aralığı {span:g}: son renk tam dolu kalıcı olmalı, bulunan {fill}")
            if (value > 0) != ((fill.layer * layer_q + fill.top) > 0):
                err(f"özellik ({name}): değer {value}: dolu/boş durumu yanlış {fill}")
            previous = key
    # bir birim artış en çok 1 çeyrek kaydırır
    for value in range(0, 400):
        a, b = M.health_fill(cfg, value), M.health_fill(cfg, value + 1)
        if (b.layer * layer_q + b.top) - (a.layer * layer_q + a.top) > 1:
            err(f"özellik: {value} → {value + 1} HP çubuğu 1 çeyrekten fazla zıpladı")
            break
    notes.append(f"özellik taraması: {swept} nokta, çeyrek sayısı monoton, son renk kalıcı, başa dönme yok")


# ---------------------------------------------------------------------------------------------- 5. vaka tabloları
def render_case_files(cfg):
    def table(header, rows):
        buf = io.StringIO()
        writer = csv.writer(buf, lineterminator="\n")
        writer.writerow(header)
        writer.writerows(rows)
        return buf.getvalue()

    bars = [[i, k, a, b, M.bar_case(cfg, k, a, b)] for i, k, a, b in M.BAR_CASES]
    bars += [[i, "geom", w, h, M.geom_case(cfg, w, h)] for i, w, h in M.GEOM_CASES]
    texts = [[i, k, lang, args, M.text_case(cfg, k, lang, args)] for i, k, lang, args in M.TEXT_CASES]
    fx = [[i, k, args, M.fx_case(cfg, k, args)] for i, k, args in M.FX_CASES]
    return {"hud_cases.csv": table(["id", "kind", "a", "b", "expect"], bars),
            "hud_text_cases.csv": table(["id", "kind", "lang", "args", "expect"], texts),
            "hud_fx_cases.csv": table(["id", "kind", "args", "expect"], fx)}


def check_case_files(cfg, write):
    for name, content in render_case_files(cfg).items():
        path = DATA / name
        if write:
            path.write_text(content, encoding="utf-8")
        elif not path.exists() or path.read_text(encoding="utf-8") != content:
            err(f"{path.relative_to(ROOT)} bayat → python3 -I tools/check_hud.py --write")
    notes.append(f"vaka tabloları: {len(M.BAR_CASES)} çubuk + {len(M.GEOM_CASES)} yerleşim + {len(M.TEXT_CASES)} metin + {len(M.FX_CASES)} efekt vakası"
                 + (" (yazıldı)" if write else ""))


# ---------------------------------------------------------------------------------------------- 6. sprite'lar
# Kendi sprite'larımız nötr olmalı: bir kanalı sıfıra yakın piksel o rengi üretemez. Üreteç tam nötr yazar → tolerans küçük.
NEUTRAL_TOLERANCE = 2


def referenced_sprites():
    path = JAVA / "client/HudSprites.java"
    if not path.exists():
        err(f"{path.relative_to(ROOT)} yok")
        return [], []
    text = path.read_text(encoding="utf-8")
    vanilla = sorted(set(re.findall(r'vanilla\("([a-z0-9_/]+)"\)', text)))
    own = sorted(set(re.findall(r'own\("([a-z0-9_/]+)"\)', text)))
    if not vanilla or not own:
        err("HudSprites.java: sprite başvurusu bulunamadı (vanilla(\"…\") / own(\"…\") kalıbı)")
    return vanilla, own


def check_sprites():
    vanilla, own = referenced_sprites()
    for sprite in vanilla + own:
        if sprite.endswith("_half") or "/half" in sprite or "_half_" in sprite:
            err(f"sprite '{sprite}': yarım sprite'a başvuru — yarım/çeyrek dolgu kırpmayla çizilir (generator/hud.py açıklaması)")
    jar = sprite_generator.vanilla_jar()
    if jar is None:
        notes.append("vanilla sprite varlığı ATLANDI: vanilla jar bulunamadı (THEORIA_VANILLA_JAR / Prism / ./gradlew build)")
    else:
        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())
            for sprite in vanilla:
                if f"{sprite_generator.VANILLA_PREFIX}{sprite}.png" not in names:
                    err(f"vanilla sprite yok: {sprite} ({jar.name}) — yanlış kimlik oyunda mor-siyah 'eksik doku' karesi çizer")
        notes.append(f"sprite'lar: {len(vanilla)} vanilla kimlik {jar.name} içinde var")
    for sprite in own:
        file = SPRITE_DIR.parent / f"{sprite}.png"
        if not file.exists():
            err(f"kendi sprite'ımız yok: {sprite} ({file.relative_to(ROOT)})")
            continue
        _w, _h, rows = pngio.decode(file.read_bytes())
        if any(max(px[:3]) - min(px[:3]) > NEUTRAL_TOLERANCE for row in rows for px in row if px[3] > 0):
            err(f"{sprite}: nötr değil (r==g==b değil) — setColor ÇARPAR, boyama istenen rengi veremez")
    on_disk = {p.stem for p in SPRITE_DIR.glob("*.png")}
    used = {s.split("/")[-1] for s in own}
    for orphan in sorted(on_disk - used):
        err(f"artık sprite: {orphan}.png Java'da kullanılmıyor")
    status = subprocess.run([sys.executable, "-I", str(ROOT / "generator/hud.py"), "--check"], capture_output=True, text=True)
    if status.returncode == 1:
        err("generator/hud.py --check: " + (status.stdout.strip() or status.stderr.strip()))
    elif status.returncode == 2:
        notes.append("generator özdeşliği ATLANDI: vanilla jar yok")
    notes.append(f"kendi sprite'ları: {len(own)} adet, nötr (kanal farkı ≤ {NEUTRAL_TOLERANCE}), generator/hud.py ile özdeş")


# ---------------------------------------------------------------------------------------------- main
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="vaka tablolarını (data/hud/*.csv) yeniden yaz")
    parser.add_argument("--break", dest="broken", action="store_true", help="negatif test: modeli bozar, kırmızı beklenir")
    options = parser.parse_args()

    cfg = M.load()
    check_config(cfg)
    check_palettes(cfg)
    check_expected(cfg, options.broken)
    if not options.broken:
        check_properties(cfg)
        check_case_files(cfg, options.write)
        check_sprites()
    for note in notes:
        print("  " + note)
    if errors:
        print(f"HATA ({len(errors)}):")
        for e in errors:
            print("  - " + e)
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
