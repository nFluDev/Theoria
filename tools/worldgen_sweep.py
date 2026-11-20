#!/usr/bin/env python3
"""Çoklu-seed GERÇEK dünya üretimi ölçümü (gözetimsiz): her seed için dev istemcisinde gerçek worldgen (TerraBlender + Terralith + Theoria) çalışır, üretilen chunk'lar
tools/scan_world.py ile okunur, seed'ler karşılaştırılır.

    python3 -I tools/worldgen_sweep.py 90101 90202 90303 90404 90505 [--tiles 5] [--keep]

Gerekenler: `mods-src/theoria/run-dev/mods/` içinde Terralith + Lithostitched jar'ları, `run-dev/config/neoforge-client.toml` `showLoadWarnings = false` (yoksa NeoForge'un
"yüklendi, uyarı var" modalı koşuyu durdurur), Prism oyunu KAPALI. Her seed bir dev penceresi açar (≈ 4-6 dk) ve kendi kapanır; pencereye dokunma.
Çıktı: mods-src/theoria/build/dev-out/worldgen_sweep.json + .md. Seed başına: tam chunk, Theoria yüzey payı, 20 biyomdan kaç tanesi var/hangisi yok, Haven S/M/L ve spawn'a en yakın,
Zindan/Harabe/Kale, cevher bölümü / tam chunk, log'da çökme/özellik-sırası hatası.
"""
import argparse, json, pathlib, subprocess, sys, time

ROOT = pathlib.Path(__file__).resolve().parents[1]
MOD = ROOT / "mods-src/theoria"
OUT = MOD / "build/dev-out"
BIOMES = ["tin_marsh", "steel_steppe", "copperwood", "iron_uplands", "luminous_valley", "cobalt_canyon", "ash_desert", "ice_graveyard", "ember_rift", "storm_coast",
          "crystal_caves", "shadow_forest", "titan_peaks", "ancient_ruins", "rune_vale", "meteor_crater", "storm_islands", "mithril_rift", "orichalcum_sky", "platinum_depths"]
ORES = ["tin", "cobalt", "mithril", "platinum", "titanium", "lonsdaleit", "orichalcum"]


def run_seed(seed, tiles):
    t0 = time.time()
    cmd = (f"source {ROOT}/tools/env.sh >/dev/null 2>&1; ./gradlew runDevClient -PdevClient=worldgen -PwithTerraBlender -PdevSeed={seed} -PdevTiles={tiles} --console=plain")
    proc = subprocess.run(["bash", "-c", cmd], cwd=MOD, capture_output=True, text=True)
    secs = time.time() - t0
    log = (MOD / "run-dev/logs/latest.log")
    text = log.read_text(encoding="utf-8", errors="replace") if log.exists() else ""
    world = MOD / f"run-dev/saves/theoria-dev-{seed}"
    res = {"seed": seed, "gradle_exit": proc.returncode, "wall_seconds": round(secs),
           "feature_order_errors": text.count("Feature order cycle"), "chunk_gen_errors": text.count("Exception generating new chunk"),
           "shutdown_errors": text.count("Exception stopping the server")}
    if not world.exists():
        res["error"] = "dünya klasörü yok (senaryo koşmadı?)"
        return res, None
    scan = subprocess.run([sys.executable, "-I", str(ROOT / "tools/scan_world.py"), str(world), "--json"], capture_output=True, text=True)
    try:
        d = json.loads(scan.stdout)
    except json.JSONDecodeError:
        res["error"] = "tarama JSON'u okunamadı: " + (scan.stderr or scan.stdout)[:200]
        return res, world
    full = d["status"].get("minecraft:full", 0) or 1
    present = {k.split(":", 1)[1] for k in d["theoria_biomes"]}
    st = {k.split(":", 1)[1]: v for k, v in d["structure_starts"].items() if k.startswith("theoria:")}
    near = d["theoria_structure_nearest_to_origin"]
    res.update({
        "chunks": d["chunks"], "full": full, "theoria_surface_percent": d["theoria_surface_percent"],
        "biomes_present": len(present), "biomes_missing": [b for b in BIOMES if b not in present],
        "haven": {s: st.get(f"haven_{s}", 0) for s in "sml"}, "haven_valley": sum(v for k, v in st.items() if k.startswith("haven_valley")),
        "haven_nearest_blocks": min((round((x * x + z * z) ** 0.5) for k, (x, z) in ((k.split(':', 1)[1], v) for k, v in near.items()) if k.startswith("haven")), default=None),
        "dungeon": st.get("dungeon", 0), "ruin": st.get("ruin", 0), "keeps": sum(v for k, v in st.items() if k.startswith("keep")),
        "ore_per_full_chunk": {o: round(sum(v for k, v in d["theoria_ore_sections"].items() if k.endswith(f"{o}_ore")) / full, 3) for o in ORES},
    })
    return res, world


def table(results):
    ok = [r for r in results if "full" in r]
    lines = ["| Seed | tam chunk | Theoria yüzey | biyom var/20 | YOK | Haven S/M/L (en yakın) | Zindan/Harabe/Kale | " + " | ".join(ORES) + " | hata |",
             "|---|--:|--:|--:|---|---|---|" + "--:|" * len(ORES) + "---|"]
    for r in results:
        if "full" not in r:
            lines.append(f"| {r['seed']} | — | — | — | — | — | — |" + " — |" * len(ORES) + f" {r.get('error', '?')} |")
            continue
        h = r["haven"]
        err = r["feature_order_errors"] + r["chunk_gen_errors"] + r["shutdown_errors"]
        lines.append(f"| {r['seed']} | {r['full']} | %{r['theoria_surface_percent']} | {r['biomes_present']} | {', '.join(r['biomes_missing']) or '—'} | {h['s']}/{h['m']}/{h['l']} ({r['haven_nearest_blocks']} blok) | {r['dungeon']}/{r['ruin']}/{r['keeps']} | "
                     + " | ".join(str(r["ore_per_full_chunk"][o]) for o in ORES) + f" | {err or 'yok'} |")
    if ok:
        mean = lambda xs: round(sum(xs) / len(xs), 3)
        lines.append(f"| **ortalama** | {int(mean([r['full'] for r in ok]))} | %{mean([r['theoria_surface_percent'] for r in ok])} | {mean([r['biomes_present'] for r in ok])} | | | | "
                     + " | ".join(str(mean([r["ore_per_full_chunk"][o] for r in ok])) for o in ORES) + " | |")
        lines.append(f"| **en düşük** | {min(r['full'] for r in ok)} | %{min(r['theoria_surface_percent'] for r in ok)} | {min(r['biomes_present'] for r in ok)} | | | | "
                     + " | ".join(str(min(r["ore_per_full_chunk"][o] for r in ok)) for o in ORES) + " | |")
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("seeds", nargs="+", type=int)
    ap.add_argument("--tiles", type=int, default=5)
    ap.add_argument("--keep", action="store_true", help="taranan dünyaları silme")
    a = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    for seed in a.seeds:
        print(f"[{time.strftime('%H:%M:%S')}] seed {seed} ({a.tiles}×{a.tiles} tile) başlıyor", flush=True)
        res, world = run_seed(seed, a.tiles)
        results.append(res)
        print(f"[{time.strftime('%H:%M:%S')}] seed {seed}: " + (res.get("error") or f"tam chunk {res['full']}, Theoria %{res['theoria_surface_percent']}, biyom {res['biomes_present']}/20, {res['wall_seconds']} sn"), flush=True)
        (OUT / "worldgen_sweep.json").write_text(json.dumps(results, indent=1, ensure_ascii=False), encoding="utf-8")
        if world and not a.keep:
            subprocess.run(["rm", "-rf", str(world)])
    md = table(results)
    (OUT / "worldgen_sweep.md").write_text(md + "\n", encoding="utf-8")
    print("\n" + md)


if __name__ == "__main__":
    main()
