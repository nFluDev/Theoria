#!/usr/bin/env python3
"""Özellik sırası (feature order): vanilla'nın FeatureSorter'ının Python benzetimi + Theoria biyomlarının dizileceği vanilla-uyumlu sıra.

    python3 -I generator/feature_order.py --write --extra <Terralith.jar>   # data/worldgen_feature_order.json: vanilla ∪ ek biyom paketi(leri)nden türet
    python3 -I generator/feature_order.py --check [--extra <jar>]           # dosyadaki sıra vanilla'nın (+ek paketlerin) tüm kenarlarına uyuyor mu (jar yoksa atlanır)

NEDEN (Faz 9b kapısı, 2026-11-19): Theoria biyomları dünya üretimine ilk kez girince oyun çöktü: `IllegalStateException: Feature order cycle found, involved sources:
[theoria:storm_islands, theoria:iron_uplands, theoria:steel_steppe]`. Vanilla `FeatureSorter.buildFeaturesPerStep` TÜM olası biyomların özellik listelerinden
bir graf kurar (her biyomun düzleştirilmiş listesinde ardışık çiftler için kenar, düğüm = (adım, özellik)) ve çevrim varsa üretim hemen düşer. Profillerdeki
bitki listeleri elle, biyomdan biyoma farklı sırayla yazılmıştı (flower_default < trees_savanna < patch_grass_normal < flower_default) ve vanilla'yla da çelişiyordu
(savana: patch_tall_grass < trees_savanna < patch_grass_savanna). TerraBlender bölgesi dünyaya girene kadar bu hiç çalıştırılmamıştı.

ÇÖZÜM: her adımda vanilla Overworld biyomlarının kenarlarından TEK bir topolojik sıra türetilir (`data/worldgen_feature_order.json`); üreteç her Theoria biyomunun her
adım listesini bu sıraya göre dizer. Tüm kenarlar bu toplam sırada ileri gittiği için (vanilla kenarları da, bizimkiler de) birleşim çevrimsizdir.
PAKETTEKİ DİĞER BİYOM MODLARI: yalnız vanilla'dan türetilen sıra yetmez (ilk deneme Terralith'in `wildflowers_meadow < patch_bush < brown_mushroom_normal` kısıtlarıyla çevrim
verdi). Bu yüzden sıra vanilla ∪ Terralith kenarlarından türetilir (`--extra`), dosyaya kaynak jar adları yazılır; Terralith sürümü değişince yeniden türet.
tools/check_worldgen.py `--extra-biomes <jar>` bunu somut jar'a karşı sınar; asıl hakem oyun içi `/theoria test worlds` (gerçek `FeatureSorter`, paketteki tüm biyomlarla).
"""
import collections, heapq, json, pathlib, sys, zipfile

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(HERE))
import vanilla  # noqa: E402

ORDER_FILE = ROOT / "data/worldgen_feature_order.json"
STEPS = (1, 2, 3, 6, 7, 8, 9, 10)   # Theoria biyomlarının doldurduğu adımlar (üreteç)
NON_OVERWORLD = {"nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas", "the_end", "end_highlands", "end_midlands",
                 "small_end_islands", "end_barrens", "the_void"}
BIOME_PREFIX = "data/minecraft/worldgen/biome/"
UNKNOWN = 10 ** 6


def vanilla_overworld_lists():
    """{biyom: [[adım 0 özellikleri], …]} — vanilla jar'daki Overworld biyomları. Jar yoksa SystemExit."""
    if not vanilla.available():
        raise SystemExit("feature_order: vanilla jar yok (THEORIA_VANILLA_JAR / ./gradlew build)")
    out = {}
    for n in sorted(vanilla.names(BIOME_PREFIX)):
        if not n.endswith(".json"):
            continue
        name = n[len(BIOME_PREFIX):-5]
        if name in NON_OVERWORLD:
            continue
        out[name] = json.loads(vanilla.read(n))["features"]
    return out


def extra_lists(path):
    """Başka bir mod jar'ının / klasörünün biyomları: {ad: [[…], …]} (minecraft ad alanı hariç)."""
    p = pathlib.Path(path)
    out = {}
    if p.is_dir():
        for f in sorted(p.glob("data/*/worldgen/biome/**/*.json")):
            ns = f.relative_to(p / "data").parts[0]
            if ns != "minecraft":
                out[f"{ns}:{f.stem}"] = json.loads(f.read_text(encoding="utf-8")).get("features", [])
        return out
    with zipfile.ZipFile(p) as z:
        for n in sorted(z.namelist()):
            parts = n.split("/")
            if len(parts) > 4 and parts[0] == "data" and parts[2:4] == ["worldgen", "biome"] and n.endswith(".json") and parts[1] != "minecraft":
                out[f"{parts[1]}:{pathlib.PurePosixPath(n).stem}"] = json.loads(z.read(n)).get("features", [])
    return out


def flatten(steps):
    """Biyomun adım listeleri → FeatureSorter'ın düzleştirdiği [(adım, özellik)] (özellik ad alanlı: 'minecraft:xyz')."""
    return [(i, f) for i, fs in enumerate(steps) for f in fs]


def build_edges(sources):
    """sources: {ad: adım listeleri}. FeatureSorter gibi: düzleştirilmiş listede ardışık çift = kenar. Dönüş: (kenarlar, kenar→kaynak adları)."""
    edges = collections.defaultdict(set)
    who = collections.defaultdict(set)
    for name, steps in sources.items():
        flat = flatten(steps)
        for i, node in enumerate(flat):
            edges[node]
            if i + 1 < len(flat):
                edges[node].add(flat[i + 1])
                who[(node, flat[i + 1])].add(name)
    return edges, who


def find_cycle(edges):
    """Yönlü grafta bir çevrim (düğüm listesi, ilki sonda tekrar eder) ya da None. Yinelemeli DFS (özyineleme sınırı yok)."""
    WHITE, GRAY, BLACK = 0, 1, 2
    color = collections.defaultdict(int)
    for root in sorted(edges):
        if color[root]:
            continue
        stack = [(root, iter(sorted(edges[root])))]
        path = [root]
        color[root] = GRAY
        while stack:
            node, it = stack[-1]
            nxt = next(it, None)
            if nxt is None:
                color[node] = BLACK
                stack.pop()
                path.pop()
                continue
            if color[nxt] == GRAY:
                return path[path.index(nxt):] + [nxt]
            if color[nxt] == WHITE:
                color[nxt] = GRAY
                stack.append((nxt, iter(sorted(edges[nxt]))))
                path.append(nxt)
    return None


def describe_cycle(cycle, who):
    parts = []
    for a, b in zip(cycle, cycle[1:]):
        src = sorted(who.get((a, b), []))
        parts.append(f"{a[1].partition(':')[2]} < {b[1].partition(':')[2]} (adım {a[0]}; {', '.join(src[:3])}{'…' if len(src) > 3 else ''})")
    return " ; ".join(parts)


def derive_order(lists):
    """Adım başına topolojik sıra: {adım: [özellik, …]}. Vanilla'nın kendisi çevrimsiz olmalı (değilse SystemExit)."""
    out = {}
    for step in STEPS:
        succ = collections.defaultdict(set)
        nodes = set()
        for steps in lists.values():
            fs = steps[step] if step < len(steps) else []
            nodes.update(fs)
            for a, b in zip(fs, fs[1:]):
                succ[a].add(b)
        indeg = {n: 0 for n in nodes}
        for a, bs in succ.items():
            for b in bs:
                indeg[b] += 1
        ready = [n for n, d in indeg.items() if d == 0]
        heapq.heapify(ready)
        order = []
        while ready:
            n = heapq.heappop(ready)
            order.append(n)
            for b in sorted(succ[n]):
                indeg[b] -= 1
                if indeg[b] == 0:
                    heapq.heappush(ready, b)
        if len(order) != len(nodes):
            raise SystemExit(f"feature_order: vanilla adım {step} çevrimli (beklenmiyor)")
        out[step] = order
    return out


def load_order():
    d = json.loads(ORDER_FILE.read_text(encoding="utf-8"))
    return {int(k): v for k, v in d["steps"].items()}


def rank_of(order):
    return {step: {f: i for i, f in enumerate(fs)} for step, fs in order.items()}


def sort_step(step, features, ranks):
    """Bir adım listesini vanilla-uyumlu sıraya diz; vanilla'da o adımda hiç olmayan özellikler sona (ada göre: kendi aramızda tutarlı)."""
    r = ranks.get(step, {})
    return sorted(features, key=lambda f: (r.get(f, UNKNOWN), f))


def union_lists(extra_paths):
    lists = {f"minecraft:{k}": v for k, v in vanilla_overworld_lists().items()}
    for path in extra_paths:
        lists.update(extra_lists(path))
    return lists


def violations(order, lists):
    """Sıraya aykırı kenarlar: [(biyom, adım, a, b)] — a, b aynı adımda ardışık ama sıra dosyasında b, a'dan önce (ya da ikisi de sıra dosyasında yok değil, biri yok)."""
    ranks = rank_of(order)
    bad = []
    for name, steps in lists.items():
        for step in STEPS:
            fs = steps[step] if step < len(steps) else []
            r = ranks.get(step, {})
            for a, b in zip(fs, fs[1:]):
                if a not in r or b not in r or r[a] >= r[b]:
                    bad.append((name, step, a, b))
    return bad


def write(extra_paths):
    lists = union_lists(extra_paths)
    order = derive_order(lists)
    payload = {
        "_comment": "Faz 9b: vanilla Overworld (+ pakete katılan biyom paketleri) biyomlarından türetilen adım başına özellik sırası (generator/feature_order.py --write --extra <jar>). "
                    "generator/biomes.py her Theoria biyomunun listesini bu sıraya göre dizer: aksi halde FeatureSorter 'Feature order cycle' ile üretimi düşürür. Elle düzenlenmez.",
        "jar": pathlib.Path(vanilla.jar_path()).name if vanilla.jar_path() else None,
        "extra": [pathlib.Path(p).name for p in extra_paths],
        "steps": {str(s): order[s] for s in STEPS},
    }
    ORDER_FILE.write_text(json.dumps(payload, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"yazıldı: {ORDER_FILE.relative_to(ROOT)} ({sum(len(v) for v in order.values())} özellik, {len(STEPS)} adım, {len(lists)} kaynak biyom; ek: {payload['extra'] or 'yok'})")


def check(extra_paths):
    if not vanilla.available():
        print("atlandı (vanilla jar yok)")
        return 0
    bad = violations(load_order(), union_lists(extra_paths))
    if bad:
        print(f"ESKİ/UYUMSUZ: sıra dosyası {len(bad)} kenara uymuyor (ilk: {bad[0]}) → python3 -I generator/feature_order.py --write --extra <jar>")
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    extras = [a[i + 1] for i, x in enumerate(a) if x == "--extra" and i + 1 < len(a)]
    if "--write" in a:
        write(extras)
    elif "--check" in a:
        sys.exit(check(extras))
    else:
        print(__doc__)
