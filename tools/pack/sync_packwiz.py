#!/usr/bin/env python3
"""pack/modlist.toml (tek kaynak) -> pack/packwiz/ (packwiz metadosyaları).

    python3 -I tools/pack/sync_packwiz.py            # eksik/sürümü değişen girişleri ekler, `packwiz refresh` yapar
    python3 -I tools/pack/sync_packwiz.py --dry-run  # yalnızca ne yapacağını söyler (ağ: Modrinth API)

Her giriş Modrinth'ten (oyun 26.3, NeoForge; shaderpack için yükleyici süzgeci yok) tam sürüm kimliğine çözülür ve
`packwiz modrinth add --project-id … --version-id …` ile eklenir; sürüm kimliği metadosyada kilit işlevi görür.
packwiz'in kendiliğinden eklediği zorunlu bağımlılıklar `[bagimlilik]` bölümünde modlist'e yazılmalıdır (check_pack.py denetler).
packwiz ikilisi: tools/bin/packwiz (pack/README.md: nasıl derlenir).
"""
import json, pathlib, re, subprocess, sys, time, tomllib, urllib.error, urllib.parse, urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
PACK = ROOT / "pack/packwiz"
PW = ROOT / "tools/bin/packwiz"
UA = {"User-Agent": "theoria-pack-sync/0.1"}
SHADERPACKS = {"complementary-reimagined"}
SKIP_SECTIONS = {"meta", "bilerek-yok"}
# Bu bölümlerdeki girişler pakete "isteğe bağlı, varsayılan KAPALI" olarak girer (packwiz [option]).
OPTIONAL_DEFAULT_ON = {"opsiyonel-acik"}   # [option] optional=true, default=true: kurulumda işaretli gelir, kullanıcı kaldırabilir
OPTIONAL = {
    "opsiyonel-acik": "Varsayılan AÇIK ama kapatılabilir (kurulum penceresinde işareti kaldır): modun kendi içinde aç/kapa anahtarı yok.",
    "opsiyonel-test-edilecek": "Opsiyonel; Vulkan altında denenmeden varsayılan açık değil (docs/journal/f05.md).",
    "bagimlilik-opsiyonel": "Bir opsiyonel modun bağımlılığı (better-combat / sophisticated-backpacks): ana moduyla birlikte aç/kapa.",
}


def api(path):
    for _ in range(4):
        try:
            return json.load(urllib.request.urlopen(urllib.request.Request("https://api.modrinth.com/v2" + path, headers=UA), timeout=30))
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(5)
                continue
            raise
        except Exception:
            time.sleep(2)
    raise SystemExit(f"Modrinth API yanıt vermedi: {path}")


def resolve(slug, pin, game):
    params = {"game_versions": json.dumps([game])}
    if slug not in SHADERPACKS:
        params["loaders"] = json.dumps(["neoforge"])
    versions = api(f"/project/{slug}/version?{urllib.parse.urlencode(params)}")
    exact = [v for v in versions if v["version_number"] == pin]
    if exact:
        return exact[0], True
    loose = [v for v in versions if pin in v["version_number"]]
    if loose:
        return loose[0], False
    raise SystemExit(f"{slug}: sabit '{pin}' {game} için bulunamadı (en yeni: {versions[0]['version_number'] if versions else 'yok'})")


def pw(*args, check=True):
    proc = subprocess.run([str(PW), *args], cwd=PACK, capture_output=True, text=True)
    out = proc.stdout + proc.stderr
    if check and proc.returncode != 0:
        raise SystemExit(f"packwiz {' '.join(args)} başarısız:\n{out}")
    return out


def existing_metafiles():
    found = {}
    for f in list(PACK.glob("mods/*.pw.toml")) + list(PACK.glob("shaderpacks/*.pw.toml")):
        d = tomllib.loads(f.read_text(encoding="utf-8"))
        mr = d.get("update", {}).get("modrinth", {})
        found[mr.get("mod-id", f.stem)] = (f, mr.get("version"))
    return found


# Kullanıcının sonradan değiştirebileceği ayar dosyaları: packwiz-installer bunları yalnızca YOKSA yazar (index.toml `preserve = true`).
PRESERVE = ["options.txt", "vitrail/pack.txt", "shaderpacks/ComplementaryReimagined_r5.9.3.zip.txt", "config/terralith.json", "config/inventoryprofilesnext/inventoryprofiles.json", "config/appleskin-client.toml"]


def ensure_preserve():
    index = PACK / "index.toml"
    text = index.read_text(encoding="utf-8")
    for rel in PRESERVE:
        block = re.search(r'\[\[files\]\]\nfile = "' + re.escape(rel) + r'"\n(?:[a-z-]+ = [^\n]*\n)*', text)
        if not block:
            raise SystemExit(f"index.toml'da {rel} yok (dosya pack/packwiz altında olmalı)")
        if "preserve = true" not in block.group(0):
            text = text.replace(block.group(0), block.group(0) + "preserve = true\n")
    index.write_text(text, encoding="utf-8")


def mark_optional(metafile, description, default=False):
    text = metafile.read_text(encoding="utf-8")
    if "[option]" in text:
        text = re.sub(r"\n\[option\].*?(?=\n\[|\Z)", "", text, flags=re.S)
    text = text.rstrip("\n") + f'\n\n[option]\noptional = true\ndefault = {str(default).lower()}\ndescription = {json.dumps(description, ensure_ascii=False)}\n'
    metafile.write_text(text, encoding="utf-8")


def main():
    dry = "--dry-run" in sys.argv
    if not PW.exists():
        raise SystemExit(f"{PW} yok — pack/README.md 'packwiz derleme' bölümüne bak")
    data = tomllib.loads((ROOT / "pack/modlist.toml").read_text(encoding="utf-8"))
    meta = data["meta"]
    PACK.mkdir(parents=True, exist_ok=True)
    if not (PACK / "pack.toml").exists():
        if dry:
            print("pack.toml yok — init yapılacak")
        else:
            pw("init", "--name", "Theoria", "--author", "Theoria", "--version", "0.1.0", "--mc-version", meta["minecraft"],
               "--modloader", "neoforge", "--neoforge-version", meta["neoforge"], "-y")
            print("packwiz init: pack.toml oluşturuldu")
    have = existing_metafiles()
    wanted = []
    for section, entries in data.items():
        if section in SKIP_SECTIONS:
            continue
        for slug, pin in entries.items():
            wanted.append((section, slug, pin))
    changed = 0
    resolved = []
    for section, slug, pin in wanted:
        ver, exact = resolve(slug, pin, meta["minecraft"])
        resolved.append((section, slug, ver))
        time.sleep(0.12)
        cur = have.get(ver["project_id"])
        tag = "" if exact else f"  (kısmi eşleşme: '{pin}' → '{ver['version_number']}')"
        if cur and cur[1] == ver["id"]:
            print(f"= {slug:28} {ver['version_number']}{tag}")
        else:
            print(f"+ {slug:28} {ver['version_number']}  [{ver['id']}]{tag}" + (" (sürüm değişiyor)" if cur else ""))
            changed += 1
            if not dry:
                if cur:
                    cur[0].unlink()
                pw("modrinth", "add", "--project-id", ver["project_id"], "--version-id", ver["id"], "-y")
        if section in OPTIONAL and not dry:
            have2 = existing_metafiles()
            mark_optional(have2[ver["project_id"]][0], OPTIONAL[section], section in OPTIONAL_DEFAULT_ON)
    if not dry:
        # packwiz, bir modu eklerken ZORUNLU BAĞIMLILIĞINI en yeni sürüme çekebilir (2026-11-19: sodium-extra eklenirken sabitlenen Sodium beta.1 → beta.5 oldu).
        # Bu yüzden eklemelerden sonra her girişin sürüm kimliği modlist'in çözdüğüyle karşılaştırılır; kayanlar yeniden sabitlenir.
        have3 = existing_metafiles()
        for section, slug, ver in resolved:
            cur = have3.get(ver["project_id"])
            if cur and cur[1] == ver["id"]:
                continue
            print(f"! {slug:28} packwiz bağımlılık çözümü sürümü kaydırdı ({cur[1] if cur else 'yok'}) → {ver['id']} yeniden sabitleniyor")
            if cur:
                cur[0].unlink()
            pw("modrinth", "add", "--project-id", ver["project_id"], "--version-id", ver["id"], "-y")
            changed += 1
            if section in OPTIONAL:
                mark_optional(existing_metafiles()[ver["project_id"]][0], OPTIONAL[section], section in OPTIONAL_DEFAULT_ON)
        pw("refresh")
        ensure_preserve()
        pw("refresh")   # pack.toml'daki index özetini yeniden hesaplar; preserve bayrakları korunur
    print(f"# {len(wanted)} giriş, {changed} değişiklik" + (" (dry-run)" if dry else ""))


if __name__ == "__main__":
    main()
