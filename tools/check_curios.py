#!/usr/bin/env python3
"""Curios Max + kozmetik denetimi (Faz 11). Çalıştır: python3 -I tools/check_curios.py   (negatif test: --break)

Ekranı yakalamak yasak (CLAUDE.md); Curios Max'in ve kozmetik görünümün nasıl göründüğü kullanıcı onayıdır. Bu araç kaynakta ve veride görülebilecek,
oyun açmadan yakalanabilecek hataları keser (oyun içi karşılığı: `/theoria test curios|cosmetics`, GameTest `curios_*` / `cosmetics_*`, `runDevClient -PdevClient=curios`):
  1. Slot verisi: data/curios_slots.csv geçerli (yalnız Curios ön ayar türleri, tekrar yok), toplam >= 30 (Faz 11 DoD), üretilen JSON'lar güncel (generator/curios.py --check).
  2. Derleme bağı: Curios, üçüncü taraf modlar arasında DERLEMEYE giren TEK mod (CLAUDE.md istisnası); başka compileOnly/implementation/api yok; sürüm kimliği
     paketin curios.pw.toml'uyla aynı; neoforge.mods.toml'da zorunlu bağımlılık var ve paketteki sürümü kapsıyor; mixin yok.
  3. Katman sınırı: `top.theillusivec4` yalnızca dev.theoria.curios içinde (+ devtest). dev.theoria.cosmetics Curios'tan bağımsız.
  4. Render kuralı: cosmetics ve curios paketlerinde doğrudan GL / LWJGL / RenderSystem yok; kozmetik görünüm TEK AvatarRenderStateModifier ile (kendi katmanımız yok).
  5. Ekran oyunu durdurmaz: CuriosMaxScreen.isPauseScreen() false (LucidRem §45.5/1). Ekran Curios'un KLASİK ekranının alt sınıfıdır (`extends CuriosScreen`), menü klasik menünün
     (`extends CuriosMenu`) — "birebir klasik görünüm" kararı (2026-11-20, kullanıcı); Curios'un kendi paket/sayfa/hızlı-taşıma kodu ikisinde de çalışır.
  6. Kuralın kaynağı tek: görünüm kararı yalnızca CosmeticLook'ta (istemci değiştiricisi onu çağırır; kendi if-zinciri yok); göğüs "kanat" kuralı bileşenle (GLIDER), öğe adıyla değil.
  7. Sunucu doğrular: gizle noktası isteği yalnızca 0..3'ü kabul eder (`onToggleRequest`), kozmetik yerleştirme `CosmeticRules.accepts` ile (CosmeticSlot#mayPlace).
Çıkış 0 + son satır 'OK' = temiz.
"""
import argparse
import re
import subprocess
import sys
import pathlib
import tomllib

ROOT = pathlib.Path(__file__).resolve().parents[1]
MOD = ROOT / "mods-src/theoria"
JAVA = MOD / "src/main/java/dev/theoria"
MIN_SLOTS = 30
errors, notes = [], []


def err(msg):
    errors.append(msg)


def read(path):
    return pathlib.Path(path).read_text(encoding="utf-8")


def java_sources(break_it):
    sources = {}
    for p in sorted(JAVA.rglob("*.java")):
        rel = p.relative_to(JAVA).as_posix()
        if rel.startswith(("curios/", "cosmetics/", "devtest/CuriosTests")) or rel in ("Theoria.java", "TheoriaClient.java"):
            sources[rel] = p.read_text(encoding="utf-8")
    if break_it:  # negatif test: kuralları ihlal eden satırlar eklenmiş gibi davran
        sources["cosmetics/CosmeticRules.java"] += "\nimport top.theillusivec4.curios.api.CuriosApi;\nimport org.lwjgl.opengl.GL11;\n"
        sources["curios/client/CuriosMaxScreen.java"] = sources["curios/client/CuriosMaxScreen.java"].replace("return false;", "return true;", 1)
        sources["cosmetics/client/CosmeticsClient.java"] += "\nif (stack.is(Items.ELYTRA)) {}\n"
        sources["curios/client/CuriosMaxScreen.java"] = sources["curios/client/CuriosMaxScreen.java"].replace("extends CuriosScreen", "extends Screen")
        sources["cosmetics/CosmeticsModule.java"] = sources["cosmetics/CosmeticsModule.java"].replace("slotIndex >= 0 &&", "")
    return sources


def check_slots(break_it):
    gen = ROOT / "generator/curios.py"
    sys.path.insert(0, str(gen.parent))
    import importlib.util
    spec = importlib.util.spec_from_file_location("curios_gen", gen)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    rows = mod.read_rows()
    if break_it:
        rows = [dict(r, size=1) for r in rows] + [{"id": "tiara", "size": 3, "note": ""}, dict(rows[0])]
    for e in mod.validate(rows):
        err(e)
    total = sum(r["size"] for r in rows)
    if total < MIN_SLOTS:
        err(f"curios_slots.csv: {total} slot toplam, Faz 11 DoD en az {MIN_SLOTS} istiyor")
    if not break_it:
        out = subprocess.run([sys.executable, "-I", str(gen), "--check"], capture_output=True, text=True)
        if out.returncode != 0:
            err("generator/curios.py --check kırmızı (üretilen slot JSON'ları bayat; python3 -I generator/curios.py): " + (out.stderr.strip() or out.stdout.strip()))
    notes.append(f"slot verisi: {len(rows)} tür, toplam {total} slot (>= {MIN_SLOTS}); üretilen JSON'lar güncel")


def check_dependency(break_it):
    gradle = read(MOD / "build.gradle")
    if break_it:
        gradle += '\ncompileOnly("maven.modrinth:jei:abc123")\n'
    curios_id = None
    for line in gradle.splitlines():
        code = line.split("//")[0].strip()
        m = re.match(r'(compileOnly|implementation|api|runtimeOnly)\b', code)
        if m and ("maven." in code or "mezz" in code or "files(" in code):
            if "maven.modrinth:curios:" in code:
                if m.group(1) not in ("compileOnly",):
                    err(f"build.gradle: Curios yalnızca compileOnly (+ localRuntime) olabilir: {code!r}")
                curios_id = re.search(r"curios:(\w+)", code).group(1)
            else:
                err(f"build.gradle: Curios dışında üçüncü taraf modun derleme/çalışma bağımlılığı yasak (CLAUDE.md): {code!r}")
    if curios_id is None:
        err("build.gradle: Curios compileOnly bağımlılığı yok")
    pw = tomllib.loads(read(ROOT / "pack/packwiz/mods/curios.pw.toml"))
    pack_id = pw["update"]["modrinth"]["version"]
    if curios_id and curios_id != pack_id:
        err(f"build.gradle'daki Curios sürüm kimliği ({curios_id}) paketinkiyle ({pack_id}) aynı değil")
    modlist = tomllib.loads(read(ROOT / "pack/modlist.toml"))
    curios_version = None
    for table in modlist.values():
        if isinstance(table, dict) and "curios" in table:
            curios_version = table["curios"]
    toml = read(MOD / "src/main/templates/META-INF/neoforge.mods.toml")
    if break_it:
        toml = toml.replace('modId="curios"', 'modId="nothing"') + '\n[[mixins]]\nconfig="theoria.mixins.json"\n'
    blocks = [b for b in re.split(r"(?m)^\[\[", toml) if b.startswith("dependencies.") and 'modId="curios"' in b]
    if not blocks:
        err("neoforge.mods.toml: curios için bağımlılık bloğu yok")
    else:
        block = blocks[0]
        if 'type="required"' not in block:
            err("neoforge.mods.toml: curios bağımlılığı 'required' olmalı (paket zorunlu mod olarak taşıyor)")
        rng = re.search(r'versionRange="\[([\d.]+),([\d.]+)\)"', block)
        if not rng:
            err("neoforge.mods.toml: curios versionRange '[a,b)' biçiminde değil")
        elif curios_version:
            v = tuple(int(x) for x in curios_version.split(".")[:3])
            lo = tuple(int(x) for x in rng.group(1).split("."))
            hi = tuple(int(x) for x in rng.group(2).split("."))
            if not lo <= v < hi:
                err(f"neoforge.mods.toml: curios aralığı [{rng.group(1)},{rng.group(2)}) paketin sürümünü ({curios_version}) kapsamıyor")
    if re.search(r"^\s*\[\[mixins\]\]", toml, re.M):
        err("neoforge.mods.toml: [[mixins]] bloğu var — Faz 11 mixin'siz (ADR-002 c, ADR-007)")
    if "srcDir('src/generated/curios')" not in gradle and "srcDir(\"src/generated/curios\")" not in gradle:
        err("build.gradle: src/generated/curios kaynak klasörü eklenmemiş (slot JSON'ları jar'a girmez)")
    notes.append(f"derleme bağı: yalnız Curios (compileOnly + localRuntime, sürüm kimliği paketle aynı), zorunlu bağımlılık, mixin yok")


def check_layers(sources):
    for name, text in sources.items():
        is_curios_pkg = name.startswith("curios/") or name.startswith("devtest/")
        for m in re.finditer(r"^import\s+(top\.theillusivec4[\w.]*);", text, re.M):
            if not is_curios_pkg:
                err(f"{name}: Curios sınıfı içe aktarılıyor ({m.group(1)}) — yalnızca dev.theoria.curios (ve devtest) Curios'a dokunabilir (ADR-007 K1)")
        if name.startswith(("curios/", "cosmetics/")):
            for m in re.finditer(r"^import\s+((?:org\.lwjgl|com\.mojang\.blaze3d\.opengl)[\w.]*|[\w.]*\bGL\d*\w*);|\bRenderSystem\b|\bGL11\b", text, re.M):
                err(f"{name}: doğrudan GL/LWJGL/RenderSystem kullanımı (CLAUDE.md render kuralı): {m.group(0)!r}")
    notes.append("katman sınırı: Curios yalnızca curios paketinde; cosmetics bağımsız; doğrudan GL yok")


def check_render_and_screen(sources):
    client = sources.get("cosmetics/client/CosmeticsClient.java", "")
    if client.count("registerAvatarEntityModifier") != 1:
        err("CosmeticsClient: tam bir AvatarRenderStateModifier kaydı olmalı (ADR-002 c)")
    if re.search(r"Items\.ELYTRA|\.is\(Items\.", client):
        err("CosmeticsClient: kural öğe adıyla yazılmış — kural yalnızca CosmeticLook'ta ve bileşenle (GLIDER) olmalı")
    if "CosmeticLook.resolve" not in client:
        err("CosmeticsClient: görünüm kararı CosmeticLook.resolve ile verilmiyor")
    rules = sources.get("cosmetics/CosmeticRules.java", "")
    if "DataComponents.GLIDER" not in rules:
        err("CosmeticRules: kanat kuralı DataComponents.GLIDER bileşenine dayanmıyor (D15)")
    screen = sources.get("curios/client/CuriosMaxScreen.java", "")
    m = re.search(r"isPauseScreen\(\)\s*\{\s*return\s+(\w+);", screen)
    if not m or m.group(1) != "false":
        err("CuriosMaxScreen.isPauseScreen() false dönmeli (sunucu paket işlemez, LucidRem §45.5/1)")
    if not re.search(r"class CuriosMaxScreen extends CuriosScreen\b", screen):
        err("CuriosMaxScreen, Curios'un klasik CuriosScreen'inin alt sınıfı olmalı (kullanıcı kararı 2026-11-20: klasik görünüm birebir)")
    menu = sources.get("curios/CuriosMaxMenu.java", "")
    if not re.search(r"class CuriosMaxMenu extends CuriosMenu\b", menu):
        err("CuriosMaxMenu, Curios'un klasik CuriosMenu'sünün alt sınıfı olmalı")
    if "this.menuType = MENU.get()" not in menu:
        err("CuriosMaxMenu kendi MenuType'ını sunucuya bildirmiyor (menuType ataması yok) — istemci Curios'un ekranını açar")
    module = sources.get("cosmetics/CosmeticsModule.java", "")
    if not re.search(r"slotIndex >= 0 && slotIndex < Cosmetics\.SLOTS\.size\(\)", module):
        err("CosmeticsModule.onToggleRequest: istemciden gelen slot indeksi 0..3 ile sınırlanmıyor")
    notes.append("render: tek AvatarRenderStateModifier → CosmeticLook; kural bileşenle (GLIDER); ekran = Curios'un klasik ekranının alt sınıfı, oyunu durdurmaz; gizle isteği sunucuda 0..3 ile sınırlı")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--break", dest="broken", action="store_true", help="negatif test: kuralları ihlal eder, kırmızı beklenir")
    args = parser.parse_args()
    sources = java_sources(args.broken)
    check_slots(args.broken)
    check_dependency(args.broken)
    check_layers(sources)
    check_render_and_screen(sources)
    for n in notes:
        print(" -", n)
    if errors:
        print(f"\n{len(errors)} HATA:")
        for e in errors:
            print(" ✗", e)
        sys.exit(1)
    print("OK")


if __name__ == "__main__":
    main()
