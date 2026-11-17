#!/usr/bin/env python3
"""HUD sprite üreteci (Faz 3): vanilla dolgu sprite'larından BOYANABİLİR nötr sürümler.

    python3 -I generator/hud.py            # yaz
    python3 -I generator/hud.py --check    # yazmadan: depodakiler üretilecekle aynı mı (piksel düzeyinde)

NEDEN. Üst katların rengi `blitSprite(..., argb)` ile verilir ve renk ÇARPILIR (LucidRem dersi, docs/02 §7.3):
vanilla kalp (187,19,19) / (255,19,19) / (255,200,200) → G ve B kanalı boş, yeşille çarpınca koyu bordo çıkar;
kırmızı bir kalbi başka renge boyamak matematiksel olarak imkânsız. Nötr (r==g==b) bir taban, her renge boyanır.
Kat 0 HİÇ boyanmaz: vanilla sprite aynen çizilir (düşük canda görünüm vanilla ile piksel piksel aynı).

NE ÜRETİLİR (assets/theoria/textures/gui/sprites/hud/):
  heart_neutral          ← heart/full          (can, absorpsiyon dışı üst katlar)
  vehicle_heart_neutral  ← heart/vehicle_full  (binek sağlığı üst katları)
  armor_neutral          ← armor_full          (zırh üst katları; siyah dış hat korunur)
  food_neutral           ← food_full           (satürasyon parlak katmanı)

NE ÜRETİLMEZ — 'yarım' sprite'lar. Çeyrek/yarım dolgu çalışma anında kırpmayla yapılır (26.3'te kısmi `blitSprite`
var; vanilla XP barı da onu kullanır): bu yüzden armor_half'in opak koyu yarısı katmanlı çizimde altındaki katı
siyahla örtme sorunu (LucidRem ders 4, docs/02 §7.4) ÇIKMAZ — yarım zırh = armor_full'ün sol yarısı, sağı saydam.
tools/check_hud.py kodda `_half` sprite'ına başvuru olmadığını denetler.

Vanilla kaynak: ortamdaki 26.3 istemci jar'ı (bkz. vanilla_jar). Üretilen PNG'ler git'e girer; jar yalnızca yeniden üretirken gerekir.
"""
import os
import pathlib
import re
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import pngio  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPRITES = ROOT / "mods-src/theoria/src/main/resources/assets/theoria/textures/gui/sprites/hud"
VANILLA_PREFIX = "assets/minecraft/textures/gui/sprites/"

# own sprite → vanilla sprite it is derived from
NEUTRAL = {
    "heart_neutral": "hud/heart/full",
    "vehicle_heart_neutral": "hud/heart/vehicle_full",
    "armor_neutral": "hud/armor_full",
    "food_neutral": "hud/food_full",
}

# Nötr tabanın ton aralığı. En koyu ton 150'de, en açık 255'te: daha geniş aralık (örn. 90-255) boyanınca gölgeleri
# çamura çevirir, daha dar aralık sprite'ı düz lekeye. Tonların SIRASI korunur, mutlak değerleri değil.
TONE_LOW, TONE_HIGH = 150, 255
# Bundan koyu piksel (zırhın siyah dış hattı) olduğu gibi kalır: gri 150'ye çıkarsa dış hat kaybolur.
KEEP_DARK = 24


def vanilla_jar():
    """Vanilla 26.3 istemci jar'ı: THEORIA_VANILLA_JAR → Prism kütüphanesi → Gradle'ın yamalı jar'ı. Yoksa None."""
    env = os.environ.get("THEORIA_VANILLA_JAR")
    candidates = [pathlib.Path(env)] if env else []
    candidates.append(pathlib.Path.home() / "Library/Application Support/PrismLauncher/libraries/com/mojang/minecraft/26.3/minecraft-26.3-client.jar")
    artifacts = ROOT / "mods-src/theoria/build/moddev/artifacts"
    if artifacts.is_dir():
        candidates += sorted(p for p in artifacts.glob("minecraft-patched-*.jar") if re.fullmatch(r"minecraft-patched-[\d.]+\.jar", p.name))
    return next((p for p in candidates if p.is_file()), None)


def brightness(px):
    return 0.30 * px[0] + 0.59 * px[1] + 0.11 * px[2]


def neutralise(rows):
    """Renkli sprite → nötr taban. Tonlar parlaklığa göre sıralanıp TONE_LOW..TONE_HIGH'a eşit aralıkla yayılır.
    (Kanalları doğrudan griye çevirmek — luminance — işe yaramadı: kırmızının katsayısı düşük olduğu için gövde
    ~90'a düşüyor ve boyanınca kapkara kalıyordu.)"""
    tones = sorted({px[:3] for row in rows for px in row if px[3] > 0 and brightness(px) >= KEEP_DARK}, key=brightness)
    if not tones:
        raise SystemExit("hud: sprite'ta nötrleştirilecek ton yok")
    step = (TONE_HIGH - TONE_LOW) / (len(tones) - 1) if len(tones) > 1 else 0
    ramp = {tone: int(round(TONE_LOW + step * i)) if len(tones) > 1 else TONE_HIGH for i, tone in enumerate(tones)}
    out = []
    for row in rows:
        line = []
        for px in row:
            if px[3] == 0:
                line.append((0, 0, 0, 0))
            elif px[:3] in ramp:
                v = ramp[px[:3]]
                line.append((v, v, v, px[3]))
            else:  # KEEP_DARK'tan koyu
                line.append(px)
        out.append(line)
    return out


def build(jar_path):
    """{ad: rows} — üretilecek bütün nötr sprite'lar."""
    result = {}
    with zipfile.ZipFile(jar_path) as jar:
        for name, source in NEUTRAL.items():
            entry = f"{VANILLA_PREFIX}{source}.png"
            if entry not in jar.namelist():
                raise SystemExit(f"hud: vanilla sprite yok: {entry} ({jar_path.name})")
            _w, _h, rows = pngio.decode(jar.read(entry))
            result[name] = neutralise(rows)
    return result


def main():
    check = "--check" in sys.argv[1:]
    jar_path = vanilla_jar()
    if jar_path is None:
        print("hud: vanilla istemci jar'ı bulunamadı (THEORIA_VANILLA_JAR, Prism 26.3 kütüphanesi ya da ./gradlew build sonrası yamalı jar)")
        return 2
    sprites = build(jar_path)
    stale = []
    for name, rows in sprites.items():
        target = SPRITES / f"{name}.png"
        if check:
            if not target.exists() or pngio.decode(target.read_bytes())[2] != rows:
                stale.append(name)
        else:
            SPRITES.mkdir(parents=True, exist_ok=True)
            target.write_bytes(pngio.encode(rows))
    if check:
        if stale:
            print(f"hud: bayat/eksik sprite: {', '.join(stale)} → python3 -I generator/hud.py")
            return 1
        print(f"hud: {len(sprites)} nötr sprite üretilecekle özdeş (kaynak {jar_path.name})")
        return 0
    print(f"hud: {len(sprites)} nötr sprite yazıldı → {SPRITES.relative_to(ROOT)} (kaynak {jar_path.name})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
