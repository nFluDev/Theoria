"""Mod availability matrix (Modrinth, NeoForge only).

Usage:  python3 -I tools/modmatrix/matrix.py [--md]
Reads every slug from pack/modlist.toml (except [meta] and [bilerek-yok]) plus WATCH below,
queries Modrinth for NeoForge builds on each game version in GAME_VERSIONS and checks that the
version pinned in modlist.toml still exists for 26.3.
"""
import json, pathlib, sys, time, tomllib, urllib.parse, urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
GAME_VERSIONS = ["26.2", "26.3"]
TARGET = "26.3"
# Not in the pack, but tracked (requested features / possible replacements).
WATCH = """trinkets-updated artifacts relics-mod apotheosis apothic-attributes silent-gear appleskin modernfix
iris journeymap regions-unexplored biomesoplenty yungs-api yungs-better-dungeons when-dungeons-arise alexs-mobs
mowzies-mobs l_enders-cataclysm ice-and-fire-ce mcmouser macos-input-fixes cosmetic-armor-reworked armor-toggle
ftb-quests puffish-skills c2me-fabric vmp-fabric""".split()
UA = {"User-Agent": "theoria-modmatrix/0.2"}
# Shader paketleri Modrinth'te `iris`/`optifine` yükleyici etiketli (Iris modu DEĞİL); NeoForge süzgeci uygulanmaz.
SHADERPACKS = {"complementary-reimagined"}


def get(url):
    for attempt in range(3):
        try:
            return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if e.code == 429:
                time.sleep(5)
                continue
            raise
        except Exception:
            time.sleep(2)
    return "ERR"


def versions(slug, gv, loader="neoforge"):
    params = {"game_versions": json.dumps([gv])}
    if slug not in SHADERPACKS:
        params["loaders"] = json.dumps([loader])
    q = urllib.parse.urlencode(params)
    return get(f"https://api.modrinth.com/v2/project/{slug}/version?{q}")


def load_modlist():
    data = tomllib.loads((ROOT / "pack/modlist.toml").read_text())
    pins = {}
    for section, entries in data.items():
        if section in ("meta", "bilerek-yok"):
            continue
        for slug, ver in entries.items():
            pins[slug] = (section, ver)
    return pins


def main():
    md = "--md" in sys.argv
    pins = load_modlist()
    rows = []
    for slug in list(pins) + [w for w in WATCH if w not in pins]:
        section, pin = pins.get(slug, ("watch", None))
        cells = {}
        for gv in GAME_VERSIONS:
            v = versions(slug, gv)
            if v is None:
                cells[gv] = "no-project"
            elif v == "ERR":
                cells[gv] = "ERR"
            elif not v:
                cells[gv] = "—"
            else:
                cells[gv] = f"{v[0]['version_number']} ({v[0]['version_type'][0]}, {v[0]['date_published'][:10]})"
                if gv == TARGET:
                    cells["_all"] = [x["version_number"] for x in v]
            time.sleep(0.12)
        if pin is None:
            pin_state = ""
        elif "_all" not in cells:
            pin_state = "PIN YOK (26.3 NeoForge build yok)"
        elif any(pin == n or pin in n for n in cells["_all"]):
            latest = cells["_all"][0]
            pin_state = "ok" if (pin == latest or pin in latest) else f"ok, daha yeni: {latest}"
        else:
            pin_state = f"PIN BULUNAMADI (en yeni: {cells['_all'][0]})"
        rows.append((section, slug, cells.get("26.2"), cells.get("26.3"), pin or "", pin_state))
    if md:
        print("| Bölüm | Slug | 26.2 NeoForge | 26.3 NeoForge | Sabit | Sabit durumu |")
        print("|---|---|---|---|---|---|")
        for r in rows:
            print("| " + " | ".join(r) + " |")
    else:
        for r in rows:
            print(f"{r[0]:22} {r[1]:28} 26.2: {r[2]:38} 26.3: {r[3]:38} pin={r[4]} {r[5]}")
    bad = [r for r in rows if r[5].startswith("PIN")]
    print(f"\n# {len(rows)} mod, {len(bad)} sabit sorunu", file=sys.stderr)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
