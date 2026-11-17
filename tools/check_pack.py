#!/usr/bin/env python3
"""Paket denetimi (Faz 5). Çalıştır: python3 -I tools/check_pack.py   (--online: Modrinth'e karşı · --break: negatif test)

pack/modlist.toml (tek kaynak) ≡ pack/packwiz/ (packwiz metadosyaları + ayar dosyaları). Oyun açmadan yakalanabilecek hatalar:
  1. Eşleşme: modlist'teki her slug'ın metadosyası var, pakette modlist'te olmayan metadosyası YOK (bağımlılıklar [bagimlilik*] ile açıkça sabit);
     metadosyası dosya adı sabitin sürümünü içerir; indirme yalnızca cdn.modrinth.com'dan, sha512 var.
  2. Bütünlük: index.toml'daki her dosya var ve sha256'sı uyuyor, dizinde indekste olmayan dosya yok, pack.toml'daki index özeti güncel,
     pack.toml sürümleri modlist [meta] ile aynı.
  3. D19 (Vulkan-yerli): Vitrail + Sodium + Complementary Reimagined zorunlu ve opsiyonel DEĞİL; Iris/[bilerek-yok] modları yok; options.txt Vulkan'ı seçer
     (OpenGL yok); vitrail/pack.txt paketteki shaderpack dosyasını seçer ve etkin.
  4. ADR-005 (macOS): options.txt `ctrlClickEmulatesRightClick:false` ve `quitShortcuts:false`; Mac Input Fixes yedeği opsiyonel ve varsayılan KAPALI;
     McMouser yok. Ayar dosyaları index'te `preserve = true` (kullanıcı değişikliği ezilmez).
  4b. QoL: Curios tuşu G'de değil (vanilla Hızlı Eylemler ile çakışır; gerçek tarama: tools/pack/keys.py).
  5. Opsiyonel kuralı: [option] yalnızca yedek/opsiyonel bölümlerde, hep `default = false` + açıklama; temel bölümlerde yok.
  --online: her sürüm kimliği Modrinth'te var, dosya sha512'si API ile aynı, ZORUNLU bağımlılıkları pakette (veya aynı ailenin opsiyonelinde) var.
Çıkış 0 + son satır 'OK' = temiz.
"""
import argparse
import hashlib
import json
import pathlib
import re
import shutil
import sys
import tempfile
import tomllib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
SKIP = {"meta", "bilerek-yok"}
OPTIONAL_SECTIONS = {"mac-yedek", "opsiyonel-test-edilecek", "bagimlilik-opsiyonel"}
REQUIRED_SLUGS = {"vitrail-shaders", "sodium", "complementary-reimagined", "fantastic-wings", "jei", "curios"}
PRESERVE = {"options.txt", "vitrail/pack.txt", "shaderpacks/ComplementaryReimagined_r5.9.3.zip.txt", "config/terralith.json"}


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_kv(text):
    out = {}
    for line in text.splitlines():
        if line and not line.startswith("#") and (":" in line or "=" in line):
            sep = ":" if ":" in line.split("=", 1)[0] else "="
            k, v = line.split(sep, 1)
            out[k.strip()] = v.strip()
    return out


def check(pack, modlist_path, online=False):
    errors, notes = [], []
    err = errors.append
    ml = tomllib.loads(modlist_path.read_text(encoding="utf-8"))
    meta = ml["meta"]
    entries = {}   # slug -> (section, pin)
    for section, items in ml.items():
        if section in SKIP:
            continue
        for slug, pin in items.items():
            if slug in entries:
                err(f"{slug}: modlist'te iki kez ({entries[slug][0]} ve {section})")
            entries[slug] = (section, pin)

    # --- 1. eşleşme + metadosya biçimi
    metas = {}
    for sub in ("mods", "shaderpacks"):
        for f in sorted((pack / sub).glob("*.pw.toml")) if (pack / sub).exists() else []:
            metas[f.name[: -len(".pw.toml")]] = (f, tomllib.loads(f.read_text(encoding="utf-8")))
    for slug in sorted(entries.keys() - metas.keys()):
        err(f"{slug}: modlist'te var, pack/packwiz'de metadosyası yok (tools/pack/sync_packwiz.py)")
    for slug in sorted(metas.keys() - entries.keys()):
        err(f"{slug}: pakette var, modlist'te yok — ya [bagimlilik] bölümüne sabitle ya da kaldır")
    for slug, (f, d) in metas.items():
        if slug not in entries:
            continue
        section, pin = entries[slug]
        where = f"{slug}"
        dl = d.get("download", {})
        if not str(dl.get("url", "")).startswith("https://cdn.modrinth.com/data/"):
            err(f"{where}: indirme bağlantısı cdn.modrinth.com değil ({dl.get('url')})")
        if dl.get("hash-format") != "sha512" or not re.fullmatch(r"[0-9a-f]{128}", str(dl.get("hash", ""))):
            err(f"{where}: sha512 özeti yok/bozuk")
        mr = d.get("update", {}).get("modrinth", {})
        if not mr.get("mod-id") or not mr.get("version"):
            err(f"{where}: update.modrinth (mod-id/version) eksik")
        if d.get("side") not in ("client", "server", "both"):
            err(f"{where}: side geçersiz ({d.get('side')})")
        if pin not in str(d.get("filename", "")):
            err(f"{where}: dosya adı ({d.get('filename')}) sabitlenen sürümü '{pin}' içermiyor")
        opt = d.get("option")
        if section in OPTIONAL_SECTIONS:
            if not opt or opt.get("optional") is not True or opt.get("default") is not False or not opt.get("description"):
                err(f"{where}: [{section}] bölümünde → [option] optional=true, default=false, description gerekli")
        elif opt is not None:
            err(f"{where}: temel bölümde ([{section}]) [option] olmamalı")

    # --- 2. index bütünlüğü
    pack_toml = tomllib.loads((pack / "pack.toml").read_text(encoding="utf-8"))
    index_path = pack / pack_toml["index"]["file"]
    if sha256(index_path) != pack_toml["index"]["hash"]:
        err("pack.toml'daki index özeti güncel değil (packwiz refresh)")
    if pack_toml["versions"].get("minecraft") != meta["minecraft"]:
        err(f"pack.toml minecraft {pack_toml['versions'].get('minecraft')} ≠ modlist {meta['minecraft']}")
    if pack_toml["versions"].get("neoforge") != meta["neoforge"]:
        err(f"pack.toml neoforge {pack_toml['versions'].get('neoforge')} ≠ modlist {meta['neoforge']}")
    index = tomllib.loads(index_path.read_text(encoding="utf-8"))
    listed = {}
    for e in index.get("files", []):
        listed[e["file"]] = e
        f = pack / e["file"]
        if not f.exists():
            err(f"index.toml: {e['file']} dosyası yok")
        elif sha256(f) != e["hash"]:
            err(f"index.toml: {e['file']} özeti uyuşmuyor (packwiz refresh)")
    on_disk = {p.relative_to(pack).as_posix() for p in pack.rglob("*") if p.is_file() and not p.name.startswith(".")}
    for rel in sorted(on_disk - set(listed) - {"pack.toml", "index.toml"}):
        err(f"{rel}: dizinde var, index.toml'da yok")
    for rel in sorted(PRESERVE):
        if rel not in listed:
            err(f"{rel}: pakette olmalı")
        elif listed[rel].get("preserve") is not True:
            err(f"{rel}: index'te preserve = true değil (kullanıcının değişikliği her güncellemede ezilir)")

    # --- 3. D19 Vulkan-yerli
    for slug in sorted(REQUIRED_SLUGS):
        if slug not in metas:
            err(f"D19/zorunlu: {slug} pakette yok")
        elif "option" in metas[slug][1]:
            err(f"D19/zorunlu: {slug} opsiyonel olamaz")
    for slug in ml.get("bilerek-yok", {}):
        if slug in metas:
            err(f"[bilerek-yok] modu pakette: {slug}")
    for slug, (f, d) in metas.items():
        text = (str(d.get("name", "")) + " " + str(d.get("filename", ""))).lower()
        if re.search(r"\biris\b", text) or "iris-" in text or text.startswith("iris"):
            err(f"D19: Iris pakette görünüyor ({slug}: {d.get('filename')})")
    options = parse_kv((pack / "options.txt").read_text(encoding="utf-8")) if (pack / "options.txt").exists() else {}
    if options.get("preferredGraphicsBackend") != '"vulkan"':
        err(f'D19: options.txt preferredGraphicsBackend:"vulkan" olmalı (şimdi {options.get("preferredGraphicsBackend")})')
    if not options.get("version", "").isdigit():
        err("options.txt: ilk satır version:<veri sürümü> olmalı (yoksa eski seçenek düzeltmeleri çalışır)")
    run_dev = ROOT / "mods-src/theoria/run-dev/options.txt"
    if run_dev.exists() and options.get("version", "").isdigit():
        dev_ver = parse_kv(run_dev.read_text(encoding="utf-8")).get("version")
        if dev_ver and dev_ver != options["version"]:
            err(f"options.txt version:{options['version']} ≠ geliştirme istemcisinin {dev_ver} (oyunun veri sürümü değişmiş olabilir)")
    vit = parse_kv((pack / "vitrail/pack.txt").read_text(encoding="utf-8")) if (pack / "vitrail/pack.txt").exists() else {}
    shader_meta = metas.get("complementary-reimagined", (None, {}))[1]
    if vit.get("enabled") != "true":
        err("vitrail/pack.txt: enabled=true olmalı")
    if shader_meta and vit.get("pack") != shader_meta.get("filename"):
        err(f"vitrail/pack.txt pack={vit.get('pack')} ≠ shaderpack dosyası {shader_meta.get('filename')}")
    if shader_meta and f"shaderpacks/{shader_meta.get('filename')}.txt" not in listed:
        err("shaderpacks/<paket>.zip.txt (shaderpack ayarları) pakette yok")

    # --- 3b. QoL: gerçek tuş çakışması (tools/pack/keys.py oyun yazdıktan sonra tüm çakışmaları tarar)
    if options.get("key_key.curios.open.desc", "key.keyboard.g") == "key.keyboard.g":
        err("QoL: Curios'u açma tuşu vanilla 26.x 'Hızlı eylemler' (G) ile çakışır — options.txt'de key_key.curios.open.desc başka tuşa bağlanmalı")

    # --- 4. ADR-005 macOS
    if options.get("ctrlClickEmulatesRightClick") != "false":
        err("ADR-005: options.txt ctrlClickEmulatesRightClick:false olmalı")
    if options.get("quitShortcuts") != "false":
        err("ADR-005: options.txt quitShortcuts:false olmalı")
    mac = metas.get("mac-input-fixes-neoforged")
    if not mac:
        err("ADR-005 Yedek 1: mac-input-fixes-neoforged metadosyası yok (opsiyonel, kapalı)")
    if "mcmouser" in metas:
        err("ADR-005: mcmouser 26.3'te yüklenmez — pakette olamaz")

    # --- --online
    if online:
        ids = {d["update"]["modrinth"]["mod-id"]: slug for slug, (f, d) in metas.items() if "update" in d}
        for slug, (f, d) in sorted(metas.items()):
            vid = d["update"]["modrinth"]["version"]
            try:
                v = json.load(urllib.request.urlopen(urllib.request.Request(f"https://api.modrinth.com/v2/version/{vid}", headers={"User-Agent": "theoria-check-pack/0.1"}), timeout=30))
            except Exception as e:
                err(f"{slug}: Modrinth sürümü {vid} okunamadı ({e})")
                continue
            api_hashes = {x["hashes"]["sha512"] for x in v["files"]}
            if d["download"]["hash"] not in api_hashes:
                err(f"{slug}: sha512 Modrinth'teki dosyalarla uyuşmuyor")
            if "26.3" not in v["game_versions"]:
                err(f"{slug}: sürüm 26.3 için işaretli değil ({v['game_versions']})")
            for dep in v["dependencies"]:
                if dep["dependency_type"] == "required" and dep["project_id"] not in ids:
                    err(f"{slug}: zorunlu bağımlılık pakette yok (project {dep['project_id']})")
        notes.append(f"çevrimiçi: {len(metas)} sürüm kimliği, özet ve zorunlu bağımlılıklar Modrinth'e karşı denetlendi")
    return errors, notes


MUTATIONS = [
    ("modlist'te olmayan metadosyası (iris)", "eşleşme",
     lambda p: (p / "mods/iris.pw.toml").write_text((p / "mods/sodium.pw.toml").read_text())),
    ("sodium metadosyası silindi", "Vitrail bağımlılığı",
     lambda p: (p / "mods/sodium.pw.toml").unlink()),
    ("options.txt OpenGL'e çevrildi", "D19",
     lambda p: (p / "options.txt").write_text((p / "options.txt").read_text().replace('"vulkan"', '"opengl"'))),
    ("options.txt Ctrl+tık çevirisi açıldı", "ADR-005",
     lambda p: (p / "options.txt").write_text((p / "options.txt").read_text().replace("ctrlClickEmulatesRightClick:false", "ctrlClickEmulatesRightClick:true"))),
    ("jei metadosyasının sha512'si bozuk biçimde (gerçek değerin doğruluğunu yalnızca --online sınar)", "özet",
     lambda p: (p / "mods/jei.pw.toml").write_text(re.sub(r'hash = "[0-9a-f]{128}"', 'hash = "deadbeef"', (p / "mods/jei.pw.toml").read_text()))),
    ("Mac Input Fixes varsayılan AÇIK yapıldı", "opsiyonel kuralı",
     lambda p: (p / "mods/mac-input-fixes-neoforged.pw.toml").write_text((p / "mods/mac-input-fixes-neoforged.pw.toml").read_text().replace("default = false", "default = true"))),
    ("indirme bağlantısı Modrinth dışına", "alan adı",
     lambda p: (p / "mods/lithium.pw.toml").write_text((p / "mods/lithium.pw.toml").read_text().replace("https://cdn.modrinth.com", "https://example.com"))),
    ("vitrail/pack.txt başka pakete işaret ediyor", "shaderpack",
     lambda p: (p / "vitrail/pack.txt").write_text((p / "vitrail/pack.txt").read_text().replace("ComplementaryReimagined_r5.9.3.zip", "Other.zip"))),
]


def rehash(pack):
    """Negatif testte özet uyuşmazlığı DEĞİL, kuralın kendisi yakalansın diye index.toml ve pack.toml özetlerini yeniden hesaplar."""
    index = pack / "index.toml"
    text = index.read_text(encoding="utf-8")
    for m in re.finditer(r'file = "([^"]+)"\nhash = "[0-9a-f]+"', text):
        f = pack / m.group(1)
        if f.exists():
            text = text.replace(m.group(0), f'file = "{m.group(1)}"\nhash = "{sha256(f)}"')
    index.write_text(text, encoding="utf-8")
    pt = (pack / "pack.toml").read_text(encoding="utf-8")
    (pack / "pack.toml").write_text(re.sub(r'(\[index\][^\[]*?hash = ")[0-9a-f]+(")', lambda m: m.group(1) + sha256(index) + m.group(2), pt, flags=re.S), encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--online", action="store_true", help="Modrinth API'ye karşı da denetle")
    ap.add_argument("--break", dest="broken", action="store_true", help="negatif test: her ihlal yakalanmalı, çıkış 1")
    a = ap.parse_args()
    modlist = ROOT / "pack/modlist.toml"
    if a.broken:
        missed = 0
        for title, tag, mutate in MUTATIONS:
            tmp = pathlib.Path(tempfile.mkdtemp(prefix="check-pack-"))
            try:
                shutil.copytree(ROOT / "pack/packwiz", tmp / "p")
                mutate(tmp / "p")
                rehash(tmp / "p")
                errors, _ = check(tmp / "p", modlist)
            finally:
                shutil.rmtree(tmp, ignore_errors=True)
            print(("  yakalandı: " if errors else "  KAÇTI:     ") + title + (f"  → {errors[0]}" if errors else ""))
            missed += 0 if errors else 1
        print(f"HATA: {len(MUTATIONS)} ihlal denendi, {missed} kaçtı" if missed else f"(negatif test: {len(MUTATIONS)}/{len(MUTATIONS)} ihlal yakalandı — beklenen kırmızı)")
        return 1
    errors, notes = check(ROOT / "pack/packwiz", modlist, online=a.online)
    for n in notes:
        print("  " + n)
    if errors:
        print(f"HATA ({len(errors)}):")
        for e in errors:
            print("  - " + e)
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
