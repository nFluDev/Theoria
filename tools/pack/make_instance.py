#!/usr/bin/env python3
"""pack/packwiz'ten bir Prism instance üretir (ve isteğe bağlı .mrpack dışa aktarır).

    python3 -I tools/pack/make_instance.py                         # oynanış instance'ı "Theoria" (senaryo yok)
    python3 -I tools/pack/make_instance.py --scenario hud          # gözetimsiz HUD senaryosu (kendini kapatır)
    python3 -I tools/pack/make_instance.py --name Theoria-Diag --scenario move --shader on   # hareket ölçümü (ayrı instance; oynanış instance'ına dokunmaz)
    python3 -I tools/pack/make_instance.py --scenario fps --rd 16,24,32 --sample 20 --fullscreen --label _vk_full
    python3 -I tools/pack/make_instance.py --shader off --with distanthorizons      # opsiyonel modu elle ekle
    python3 -I tools/pack/make_instance.py --export                # pack/dist/Theoria-<sürüm>.mrpack (Theoria jar'ı dahil)

Akış: yerel HTTP sunucusu pack/packwiz'i sunar → packwiz-installer-bootstrap (Prism'in yaptığıyla aynı yol) `minecraft/` içine kurar
→ Theoria jar'ı (mods-src/theoria/build/libs) kopyalanır → instance.cfg / mmc-pack.json yazılır. Ardından başlatmak için:
    prismlauncher --launch <ad>
Senaryo bayrakları yalnızca instance.cfg JvmArgs'ına yazılır (jar'daki devtest paketi bayraksız etkisizdir).
Bu araç yalnızca <ad> instance'ının klasörüne yazar; başka instance'lara dokunmaz.
"""
import argparse, http.server, json, pathlib, shutil, subprocess, sys, tempfile, threading, tomllib, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
PACK = ROOT / "pack/packwiz"
BIN = ROOT / "tools/bin"
MOD = ROOT / "mods-src/theoria"
INSTANCES = pathlib.Path.home() / "Library/Application Support/PrismLauncher/instances"
PRISM_JAVA = pathlib.Path.home() / "Library/Application Support/PrismLauncher/java/java-runtime-epsilon"


def sh(cmd, **kw):
    return subprocess.run(cmd, check=True, **kw)


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a):
        pass


# Opsiyonel modun bağımlılıkları (packwiz [option] ilişki bilmez): --with ile birlikte gelir.
OPT_DEPS = {"better-combat": ["player-animation-library"], "sophisticated-backpacks": ["sophisticated-core"]}


def serve(directory):
    handler = lambda *a, **k: Quiet(*a, directory=str(directory), **k)
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return srv


def filtered_pack(tmp, keep):
    """pack/packwiz'in kopyası; [option] taşıyan metadosyalardan `keep` dışındakiler çıkarılır, index yeniden hesaplanır.
    (packwiz-installer'ın -g kipi `default = false` seçeneklerini YOK SAYIP hepsini kurar; kesin kapalı kurulum için süzülür.)"""
    shutil.copytree(PACK, tmp)
    for f in list(tmp.glob("mods/*.pw.toml")) + list(tmp.glob("shaderpacks/*.pw.toml")):
        if "[option]" in f.read_text(encoding="utf-8") and f.name[: -len(".pw.toml")] not in keep:
            f.unlink()
    sh([str(BIN / "packwiz"), "refresh"], cwd=tmp, stdout=subprocess.DEVNULL)


def ensure_preserved(game_dir):
    """`preserve = true` dosyaları kullanıcı değişikliğini ezmesin diye installer yalnızca YOKSA yazar; yine de eksikse paketten kopyala
    (ör. ölçümden sonra options.txt silindiğinde oynanış instance'ı paket varsayılanıyla başlasın)."""
    index = tomllib.loads((PACK / "index.toml").read_text(encoding="utf-8"))
    for entry in index.get("files", []):
        if entry.get("preserve") and not (game_dir / entry["file"]).exists():
            (game_dir / entry["file"]).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(PACK / entry["file"], game_dir / entry["file"])
            print(f"  + {entry['file']} (eksikti, paketten kopyalandı)")


def install_pack(game_dir, keep):
    tmp = pathlib.Path(tempfile.mkdtemp(prefix="theoria-pack-")) / "pack"
    try:
        filtered_pack(tmp, keep)
        srv = serve(tmp)
        try:
            url = f"http://127.0.0.1:{srv.server_address[1]}/pack.toml"
            sh(["java", "-jar", str(BIN / "packwiz-installer-bootstrap.jar"), "-g", "-s", "client", url], cwd=game_dir,
               stdout=subprocess.DEVNULL)
        finally:
            srv.shutdown()
    finally:
        shutil.rmtree(tmp.parent, ignore_errors=True)


# S1 ile karşılaştırılabilir koşu için kullanıcı instance'ının YALNIZCA grafik anahtarları alınır (tuş, dil, sunucu vb. alınmaz).
RENDER_KEYS = ["ao", "biomeBlendRadius", "chunkSectionFadeInTime", "cutoutLeaves", "entityDistanceScaling", "entityShadows", "graphicsPreset",
               "improvedTransparency", "maxAnisotropyBit", "textureFiltering", "mipmapLevels", "particles", "renderClouds", "cloudRange",
               "weatherRadius", "vignette", "simulationDistance", "prioritizeChunkUpdates"]


def user_render_options(instance):
    src = INSTANCES / instance / "minecraft/options.txt"
    if not src.exists():
        raise SystemExit(f"{src} yok")
    kv = dict(line.split(":", 1) for line in src.read_text(encoding="utf-8").splitlines() if ":" in line)
    return {k: kv[k] for k in RENDER_KEYS if k in kv}


def set_options(path, kv):
    lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else ["version:5023"]
    seen = set()
    out = []
    for line in lines:
        k = line.split(":", 1)[0]
        if k in kv:
            out.append(f"{k}:{kv[k]}")
            seen.add(k)
        else:
            out.append(line)
    out += [f"{k}:{v}" for k, v in kv.items() if k not in seen]
    path.write_text("\n".join(out) + "\n", encoding="utf-8")


def write_instance(inst, name, jvm_args, quit_after, mem_max, size, override_window):
    ml = tomllib.loads((ROOT / "pack/modlist.toml").read_text(encoding="utf-8"))["meta"]
    java_dir = PRISM_JAVA
    cfg = f"""[General]
AutomaticJava=true
ConfigVersion=1.3
InstanceType=OneSix
IgnoreJavaCompatibility=false
JavaPath={java_dir}/bin/java
OverrideJavaLocation={'true' if java_dir.exists() else 'false'}
JvmArgs={json.dumps(jvm_args) if jvm_args else ''}
OverrideJavaArgs={'true' if jvm_args else 'false'}
MaxMemAlloc={mem_max}
MinMemAlloc=4096
OverrideMemory=true
OverrideWindow={'true' if override_window else 'false'}
MinecraftWinWidth={size[0]}
MinecraftWinHeight={size[1]}
QuitAfterGameStop={'true' if quit_after else 'false'}
RecordGameTime=true
ShowConsole=false
ShowConsoleOnError=true
iconKey=default
name={name}
notes=Theoria paketi (pack/packwiz) - tools/pack/make_instance.py ile uretildi; elle degisiklik yeniden uretimde ezilir (oyun verisi minecraft/ korunur).
"""
    (inst / "instance.cfg").write_text(cfg, encoding="utf-8")
    comp = lambda uid, ver, **kw: {"uid": uid, "version": ver, **kw}
    pack = {"formatVersion": 1, "components": [
        comp("org.lwjgl3", "3.4.3", dependencyOnly=True),
        comp("net.minecraft", ml["minecraft"], important=True),
        comp("net.neoforged", ml["neoforge"]),
    ]}
    (inst / "mmc-pack.json").write_text(json.dumps(pack, indent=4) + "\n", encoding="utf-8")


def export_mrpack(version):
    dist = ROOT / "pack/dist"
    dist.mkdir(parents=True, exist_ok=True)
    out = dist / f"Theoria-{version}.mrpack"
    sh([str(BIN / "packwiz"), "modrinth", "export", "-o", str(out)], cwd=PACK)
    jar = latest_jar()
    tmp = out.with_suffix(".tmp")
    with zipfile.ZipFile(out) as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            zout.writestr(item, zin.read(item.filename))
        zout.write(jar, f"overrides/mods/{jar.name}")
    tmp.replace(out)
    print(f"mrpack: {out} ({out.stat().st_size // 1024} KB; Theoria jar'ı overrides/mods içinde)")
    return out


def latest_jar():
    jars = sorted((MOD / "build/libs").glob("theoria-*.jar"), key=lambda p: p.stat().st_mtime)
    if not jars:
        raise SystemExit("Theoria jar'ı yok: cd mods-src/theoria && ./gradlew build")
    return jars[-1]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--name", default="Theoria")
    ap.add_argument("--scenario", choices=["none", "smoke", "hud", "wings", "fps", "move"], default="none")
    ap.add_argument("--label", default="")
    ap.add_argument("--rd", default="16", help="fps: virgüllü render mesafeleri")
    ap.add_argument("--phase", type=int, default=30, help="move: her faz (yürü, uç) için saniye")
    ap.add_argument("--shots", action="store_true", help="move: oyunun kendi çerçeve tamponundan kareler kaydet (masaüstü yakalanmaz)")
    ap.add_argument("--settle", type=int, default=300, help="fps: her RD için yerleşme tick'i")
    ap.add_argument("--sample", type=int, default=20, help="fps: örnekleme süresi (sn)")
    ap.add_argument("--fullscreen", action="store_true", help="fps: tam ekran (exclusive) 1080p; yoksa pencere")
    ap.add_argument("--size", default="1280x720", help="pencere boyutu (programArguments yok -> options.txt overrideWidth/Height)")
    ap.add_argument("--shader", choices=["on", "off"], default="on")
    ap.add_argument("--with", dest="extra", default="", help="opsiyonel modlar (slug,slug…) açık kurulur (bağımlılıkları otomatik)")
    ap.add_argument("--no-install", action="store_true", help="packwiz kurulumunu atla (yalnızca ayarları yeniden yaz)")
    ap.add_argument("--export", action="store_true", help=".mrpack dışa aktar ve çık")
    ap.add_argument("--mem", type=int, default=6144)
    ap.add_argument("--user-options", default=None, help="başka bir Prism instance'ının yalnızca grafik ayarlarını al (S1 kıyası; ör. 'Vulkan Optimization')")
    a = ap.parse_args()

    if a.export:
        export_mrpack(tomllib.loads((PACK / "pack.toml").read_text(encoding="utf-8"))["version"])
        return

    inst = INSTANCES / a.name
    game = inst / "minecraft"
    game.mkdir(parents=True, exist_ok=True)
    keep = set()
    for slug in filter(None, a.extra.split(",")):
        keep |= {slug, *OPT_DEPS.get(slug, [])}
    if not a.no_install:
        print(f"packwiz-installer → {game} (opsiyonel açık: {sorted(keep) or 'yok'})")
        install_pack(game, keep)
        ensure_preserved(game)
    for old in (game / "mods").glob("theoria-*.jar"):
        old.unlink()
    jar = latest_jar()
    shutil.copy2(jar, game / "mods" / jar.name)
    print(f"  + mods/{jar.name}")

    opts = {}
    size = (1920, 1080) if (a.scenario == "fps" and a.fullscreen) else tuple(map(int, a.size.split("x")))
    if a.scenario == "fps":
        w, h = size
        opts |= {"enableVsync": "false", "maxFps": "260", "fullscreen": "true" if a.fullscreen else "false",
                 "exclusiveFullscreen": "true" if a.fullscreen else "false", "overrideWidth": str(w), "overrideHeight": str(h)}
    elif a.scenario != "none":
        opts |= {"pauseOnLostFocus": "false", "fullscreen": "false"}
    if a.scenario != "none":
        # Vanilla 26.3 varsayılanı "afk": 60 sn girdisiz kalınca <=30 FPS, 10 dk sonra 10 FPS (FramerateLimitTracker). Gözetimsiz ölçüm
        # bunu ölçmesin diye "minimized" (kullanıcının kendi oynanış ayarı); paket varsayılanı (options.txt) DEĞİŞMEZ.
        opts["inactivityFpsLimit"] = '"minimized"'
    if a.user_options:
        opts = {**user_render_options(a.user_options), **opts}   # ölçüm anahtarları (vsync, tam ekran…) kullanıcınınkini ezer
    if opts:
        set_options(game / "options.txt", opts)
    # Dynamic FPS (pakette, oynanış için iyi) odağı olmayan pencereyi ~1 FPS'e kısar; gözetimsiz senaryo/ölçümde pencere odağı garanti
    # değildir (kullanıcı başka uygulamada çalışır) → yalnızca senaryo instance'ında kapatılır, paket varsayılanı DEĞİŞMEZ.
    dyn = game / "config/dynamic_fps.json"
    dyn.parent.mkdir(parents=True, exist_ok=True)
    if a.scenario != "none":
        dyn.write_text('{"enabled": false}\n', encoding="utf-8")
    elif dyn.exists() and dyn.read_text(encoding="utf-8").strip() == '{"enabled": false}':
        dyn.unlink()   # oynanış kipine dönünce paket varsayılanı geri gelsin
    vit = game / "vitrail/pack.txt"
    if vit.exists():
        vit.write_text(vit.read_text(encoding="utf-8").replace("enabled=true", f"enabled={'true' if a.shader == 'on' else 'false'}")
                       .replace("enabled=false", f"enabled={'true' if a.shader == 'on' else 'false'}"), encoding="utf-8")

    jvm = []
    if a.scenario != "none":
        out = MOD / "build/dev-out/pack"
        jvm += ["-Dtheoria.dev=true", f"-Dtheoria.dev.client={a.scenario}", f"-Dtheoria.dev.out={out}"]
        if a.label:
            jvm.append(f"-Dtheoria.dev.label={a.label}")
        if a.scenario == "move":
            jvm.append(f"-Dtheoria.dev.phase={a.phase}")
            if a.shots:
                jvm.append("-Dtheoria.dev.shots=true")   # oyunun KENDI çerçeve tamponu (masaüstü değil) PNG olarak dev-out/pack/shots'a
        if a.scenario == "fps":
            jvm += [f"-Dtheoria.dev.rd={a.rd}", f"-Dtheoria.dev.settle={a.settle}", f"-Dtheoria.dev.sample={a.sample}"]
            if a.fullscreen:
                jvm.append("-Dtheoria.dev.fullscreen=true")
    if "mac-input-fixes-neoforged" in a.extra:
        jvm.append("--enable-native-access=macos_input_fixes")   # ADR-005: JDK uyarısı
    write_instance(inst, a.name, " ".join(jvm), a.scenario != "none", a.mem, size, a.scenario != "none")
    mods = sorted(p.name for p in (game / "mods").glob("*.jar"))
    print(f"instance hazır: {inst}  ({len(mods)} mod jar'ı; shader={a.shader}; senaryo={a.scenario})")
    print(f"başlat: prismlauncher --launch {a.name}")


if __name__ == "__main__":
    main()
