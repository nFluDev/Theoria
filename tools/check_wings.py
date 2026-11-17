#!/usr/bin/env python3
"""Kanat denetimi (Faz 4). Çalıştır: python3 -I tools/check_wings.py   (negatif test: --break)

Ekranı yakalamak yasak (CLAUDE.md); kanatların nasıl göründüğü kullanıcı onayıdır. Bu araç kaynakta ve veride görülebilecek,
oyun açmadan yakalanabilecek hataları keser (oyun içi karşılığı: `/theoria test wings`, GameTest `wings_*`, `runDevClient -PdevClient=wings`):
  1. Fantastic Wings'e DERLEME bağımlılığı yok (CLAUDE.md: üçüncü taraf mod API'sine derleme yapma): build.gradle'da yalnızca
     `localRuntime`; wings paketi `fuzs.*` / `fantasticwings` sınıfı içe aktarmaz.
  2. Render kuralı: wings paketinde doğrudan GL / LWJGL / vertex-pipeline yok; önizleme `GuiGraphicsExtractor#entity` ile çizilir,
     vanilla `*FollowsAngle/FollowsMouse` (±30° sınırlı, ADR-002 b) kullanılmaz.
  3. Ekran oyunu durdurmaz: `isPauseScreen()` `false` döndürür (LucidRem §45.5/1; sunucu paket işlemez, animatör donar).
  4. Komut sızıntısı yok: sunucu yalnızca `wings give @s ` ve `wings take @s` çalıştırır; `give` kimliği kayıt defteri anahtarı
     olarak doğrulandıktan sonra (WingsServer'da `contains` çağrısı `run("wings give` satırından önce).
  5. Kimlik doğrulaması tek yerde ve sıkı: payload kimliği 64 karakterle sınırlı (codec), `WingsCatalog.parse` boş yol / büyük harf / boşluk reddeder
     (Java tarafı: `/theoria test wings` > ids).
  6. Lang: Fantastic Wings jar'ındaki her kanat (Gradle önbelleğinde) için `theoria.wings.name.<ns>.<yol>` iki dilde var;
     jar bulunamazsa bu adım UYARIYLA atlanır (bayat kalıntı anahtar da uyarıdır). Kart etiketi yedeği: kimlikten türetilmiş ad.
Çıkış 0 + son satır 'OK' = temiz.
"""
import argparse
import glob
import json
import pathlib
import re
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
MOD = ROOT / "mods-src/theoria"
JAVA = MOD / "src/main/java/dev/theoria/wings"
LANG = MOD / "src/main/resources/assets/theoria/lang"
errors = []
notes = []


def err(msg):
    errors.append(msg)


def java_sources(break_it):
    sources = {p.relative_to(JAVA).as_posix(): p.read_text(encoding="utf-8") for p in sorted(JAVA.rglob("*.java"))}
    if break_it:  # negatif test: kuralları ihlal eden satırlar eklenmiş gibi davran
        sources["client/WingsScreen.java"] = (sources["client/WingsScreen.java"].replace("return false;", "return true;", 1)
                                              + "\nimport fuzs.fantasticwings.common.flight.Flight;\nimport org.lwjgl.opengl.GL11;\nx.renderEntityInInventoryFollowsAngle();\n")
        sources["WingsServer.java"] = sources["WingsServer.java"] + '\nrun(player, "op @s");\n'
    return sources


def check_dependencies(sources):
    gradle = (MOD / "build.gradle").read_text(encoding="utf-8")
    for line in gradle.splitlines():
        code = line.split("//")[0]
        if re.search(r"fantastic-wings|puzzles-lib|fuzs", code) and not re.search(r"localRuntime", code):
            err(f"build.gradle: Fantastic Wings / Puzzles Lib yalnızca localRuntime olabilir: {line.strip()!r}")
    for name, text in sources.items():
        for m in re.finditer(r"^import\s+(fuzs\.[\w.]+|[\w.]*fantasticwings[\w.]*);", text, re.M):
            err(f"{name}: Fantastic Wings sınıfı içe aktarılıyor ({m.group(1)}) — yalnızca kayıt defteri ve komut düzeyi (ADR-002 d)")
    notes.append("derleme bağımlılığı: yok (yalnızca localRuntime), wings paketi fuzs.* içe aktarmıyor")


def check_render(sources):
    forbidden = re.compile(r"^import\s+(org\.lwjgl\.[\w.]+|com\.mojang\.blaze3d\.(opengl|vertex|pipeline|shaders)[\w.]*|com\.mojang\.blaze3d\.systems\.RenderSystem);", re.M)
    for name, text in sources.items():
        for m in forbidden.finditer(text):
            err(f"{name}: doğrudan GL / vertex / RenderSystem içe aktarımı ({m.group(1)}) — yalnızca GuiGraphicsExtractor (CLAUDE.md)")
    preview = sources.get("client/WingsPreview.java", "")
    if not re.search(r"\bg\.entity\(", preview):
        err("client/WingsPreview.java: önizleme GuiGraphicsExtractor#entity ile çizilmiyor (ADR-002 b)")
    for name, text in sources.items():
        code = re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.S)
        if re.search(r"FollowsAngle|FollowsMouse", code):
            err(f"{name}: vanilla *FollowsAngle/FollowsMouse kullanılıyor — baş gövdeye göre ters döner (ADR-002 b)")
    notes.append("render: yalnızca GuiGraphicsExtractor#entity; vanilla FollowsAngle/FollowsMouse yok; GL/LWJGL/RenderSystem yok")


def check_screen(sources):
    screen = re.sub(r"/\*.*?\*/|//[^\n]*", "", sources["client/WingsScreen.java"], flags=re.S)
    m = re.search(r"boolean\s+isPauseScreen\s*\(\s*\)\s*\{\s*return\s+(true|false)\s*;", screen)
    if not m:
        err("WingsScreen: isPauseScreen() bulunamadı")
    elif m.group(1) != "false":
        err("WingsScreen.isPauseScreen() true döndürüyor — tek oyunculuda sunucu paket işlemez, kanat animatörü donar (LucidRem 45.5)")
    notes.append("WingsScreen.isPauseScreen() = false")


def check_commands(sources):
    server = re.sub(r"/\*.*?\*/|//[^\n]*", "", sources["WingsServer.java"], flags=re.S)
    calls = []
    for m in re.finditer(r"\brun\(\s*player\s*,", server):  # argument = text up to the matching ")" (depth-counted)
        depth, i = 1, m.end()
        while depth and i < len(server):
            depth += {"(": 1, ")": -1}.get(server[i], 0)
            i += 1
        calls.append(server[m.end():i - 1])
    allowed = {'"wings take @s"', '"wings give @s " + id'}
    for call in calls:
        if call.strip() not in allowed:
            err(f"WingsServer: beklenmeyen komut çalıştırılıyor: run(player, {call.strip()}) — yalnızca {sorted(allowed)}")
    if len(calls) < 3:
        err(f"WingsServer: beklenen 3 run(...) çağrısı (equip, take, respawn), bulundu {len(calls)}")
    # give, kayıt defteri doğrulamasından önce çalışmasın: her `wings give` satırından önce aynı metodda `contains(` olmalı
    for method in re.split(r"\n    (?:public |private |static )", server):
        if '"wings give @s "' in method and "contains(" not in method:
            err("WingsServer: bir metot kimliği kayıt defteri anahtarı olarak doğrulamadan (WingsCatalog.contains) `wings give` çalıştırıyor")
    codec = (JAVA / "SelectWingsPayload.java").read_text(encoding="utf-8")
    if "stringUtf8(WingsCatalog.MAX_ID_LENGTH)" not in codec:
        err("SelectWingsPayload: kimlik uzunluğu codec'te sınırlı değil")
    notes.append(f"komutlar: yalnızca `wings give @s <doğrulanmış kimlik>` ve `wings take @s` ({len(calls)} çağrı)")


def fw_wings():
    jars = glob.glob(str(pathlib.Path.home() / ".gradle/caches/modules-2/files-2.1/maven.modrinth/fantastic-wings/*/*/*.jar"))
    if not jars:
        return None
    with zipfile.ZipFile(jars[0]) as z:
        return sorted(pathlib.PurePosixPath(n).stem for n in z.namelist()
                      if re.fullmatch(r"data/fantasticwings/fantasticwings/wings/[^/]+\.json", n))


def check_lang():
    wings = fw_wings()
    langs = {name: json.loads((LANG / f"{name}.json").read_text(encoding="utf-8")) for name in ("en_us", "tr_tr")}
    if wings is None:
        notes.append("UYARI: Fantastic Wings jar'ı Gradle önbelleğinde yok — kanat adı kontrolü atlandı (./gradlew build ile indirilir)")
        return
    if not wings:
        err("Fantastic Wings jar'ında hiç kanat kaydı yok")
    for lang, table in langs.items():
        for wing in wings:
            key = f"theoria.wings.name.fantasticwings.{wing}"
            if not table.get(key, "").strip():
                err(f"lang {lang}: '{key}' yok (kart etiketi kimlikten türetilmiş ada düşer)")
        known = {f"theoria.wings.name.fantasticwings.{w}" for w in wings}
        stale = sorted(k for k in table if k.startswith("theoria.wings.name.") and k not in known)
        if stale:
            notes.append(f"UYARI: lang {lang}: Fantastic Wings'te olmayan kanat adları: {stale}")
    notes.append(f"lang: {len(wings)} kanat × 2 dil adı var ({', '.join(wings)})")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--break", dest="broken", action="store_true", help="negatif test: kuralları ihlal eder, kırmızı beklenir")
    options = parser.parse_args()
    sources = java_sources(options.broken)
    check_dependencies(sources)
    check_render(sources)
    check_screen(sources)
    check_commands(sources)
    check_lang()
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
