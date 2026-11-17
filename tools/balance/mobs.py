#!/usr/bin/env python3
"""Theoria mob dengesi — Faz 8 (2026-11-11). `data/mobs.csv` (tür tablosu, elle) + bu dosyadaki sabitler → sayılar ve bant denetimleri.

Kural özeti (docs/06 §4, §12; ADR-007):
  * mobs.csv'deki can/hasar/zırh "kademe 0 eşdeğeri" tabandır; tür kademesi (`tier`) üç çarpanı seçer (`tiers.csv`: mob_hp_mult / mob_dmg_mult /
    mob_bypass_mult) → nihai can = taban × 1,33^idx, hasar = taban × 1,31^idx (büyücü büyüsü: × bypass), zırh = taban × 1,31^idx.
  * Sınıf (weak/normal/strong/elite/boss) can aralığını, ref oyuncunun öldürme vuruşunu (HTK) ve ölüm vuruşunu (HTD) bantlar.
  * Bölge kademesi (vanilla düşmanlar + yeni türlerin spawn bandı): dünya doğuş noktasına uzaklık + derinlik + yükseklik + boyut → kademe.
  * XP: taban (sınıf) × xp_growth^kademe.

Üretilenler (elle düzenlenmez):
    data/balance/mob_zones.csv        bölge kademesi sabitleri
    data/balance/mob_zone_cases.csv   bölge kademesi örnekleri (oyun-içi test beklenen değerleri)
    data/balance/mob_xp.csv           kademe başına XP çarpanı + sınıf tabanları
    data/balance/mob_ttk.csv          tür başına nihai sayılar ve ref HTD/HTK/TTK (oyun-içi test beklenen değerleri)
    data/balance/mob_weapons.csv      mob elindeki vanilla silahların vanilla toplam hasarı (Q35: mob silahı vanilla'ya sabitlenir)
    mods-src/theoria/src/main/resources/theoria/mob_balance.json   oyunun okuduğu biçim (jar'a gömülü)
Çalıştır:
    python3 -I tools/balance/mobs.py            # yazar
    python3 -I tools/balance/mobs.py --check    # yazmaz; bayat dosya ya da kırmızı bant → çıkış 1
    python3 -I tools/balance/mobs.py --break    # negatif test: bozulmuş sabit/veri ile denetimler kırmızıya dönmeli
    python3 -I tools/balance/mobs.py --report   # tür tablosu
"""
import csv, json, math, pathlib, sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))   # -I altında kendi klasörümüz yolda değil
import tiers as M

ROOT = M.ROOT
BAL = ROOT / "data/balance"
MOBS_CSV = ROOT / "data/mobs.csv"
DROPS_CSV = ROOT / "data/mob_drops.csv"
RIGS_JSON = ROOT / "data/mob_rigs.json"
ZONES_CSV, ZONE_CASES_CSV, XP_CSV, TTK_CSV, WEAPONS_CSV = (BAL / n for n in ("mob_zones.csv", "mob_zone_cases.csv", "mob_xp.csv", "mob_ttk.csv", "mob_weapons.csv"))
GAME_JSON = M.RES / "theoria/mob_balance.json"

ARCHETYPES = ("humanoid", "predator", "swarm", "charger", "shooter", "caster", "flyer", "serpent", "giant", "sentinel", "assassin", "drake")
FAMILIES = ("swamp", "wild", "underground", "mountain", "ember", "frost", "shadow", "ancient")   # docs/05 §4.2 (bataklık, yaban, yeraltı, dağ, alev, buz, gölge, kadim)
CLASSES = ("weak", "normal", "strong", "elite", "boss")

# ─── Bölge kademesi (docs/06 §4 "bölge bandı yatay + dikey"): Faz 9a biyom bandlarıyla yeniden ayarlanır ───────────────
ZONE = dict(
    dist_step=600,        # doğuş noktasından her 600 blok = +1 kademe (7.800 blokta 13)
    depth_from=0,         # y < 0 → derinlik cezası başlar
    depth_step=32,        # her 32 blok derinlik +1 (y −64 → +2)
    height_from=140,      # y > 140 → yükseklik primi başlar (dağ zirveleri, yüzen adalar)
    height_step=40,       # her 40 blok yükseklik +1
    nether=4,             # Nether tabanı +4 (nether wastes ≈ bakır/demir bölgesi, ilerledikçe zirve)
    end=8,                # End tabanı +8
    cap=13,               # kademe tavanı (Platin)
    band=1,               # yeni türün spawn bandı: tür kademesi ± band
)
# XP (docs/06 §5): kill XP = sınıf tabanı × xp_growth^kademe. 2,0^13 = 8192 → zombi sınıfı 5 XP × 8192 ≈ 41K (docs/06'daki "T13'te ≈ 50K"). Faz 10 ayarlar.
XP_GROWTH = 2.0
XP_BASE = dict(weak=3, normal=5, strong=10, elite=20, boss=40)
# Kalp Kristali (boss ödülü): her biri kalıcı +can; docs/06 §3 "kalp ödül eşyaları ≈ +44 can" → 11 adet.
HEART_HP, HEART_MAX = 4, 11

# Sınıf bantları (ref oyuncu, kendi kademesinde, Normal zorluk): taban can aralığı · ölüm vuruşu en az · öldürme vuruşu aralığı. `docs/06 §1`: zombi sınıfı HTD 6-8, HTK 4,5-7.
CLASS_BAND = dict(
    weak=dict(hp=(6, 14), htd_min=5.0, htk=(1.0, 3.8)),
    normal=dict(hp=(14, 30), htd_min=4.0, htk=(3.2, 7.5)),
    strong=dict(hp=(30, 70), htd_min=2.0, htk=(6.0, 14.5)),
    elite=dict(hp=(70, 120), htd_min=2.0, htk=(14.0, 30.0)),
    boss=dict(hp=(150, 400), htd_min=2.0, htk=(35.0, 150.0)),
)
BASE_DMG_MAX = 7          # docs/06 §4: taban hasar ≤ 7 ("güçlü" sınırı) → ref HTD ≥ 2 her kademede
ATTACK_PERIOD = 0.625     # sn: kılıç 1,6 vuruş/sn (sim.py ile aynı)
MIN_SPECIES, MIN_BOSSES = 30, 2

# Vanilla mob'ların elinde taşıdığı silahların VANILLA toplam hasarı (oyuncunun taban 1'i dahil; docs/06 §10 tablosu). Gear tablosu bunları yükseltti
# (D6); mob'lar vanilla vuruşunda kalır (Q35 seçenek a): olay kancası farkı mob'un hasar niteliğinden çıkarır.
VANILLA_WEAPONS = {
    "minecraft:wooden_sword": 4, "minecraft:stone_sword": 5, "minecraft:iron_sword": 6, "minecraft:golden_sword": 4,
    "minecraft:diamond_sword": 7, "minecraft:netherite_sword": 8, "minecraft:copper_sword": 5,
    "minecraft:wooden_axe": 7, "minecraft:stone_axe": 9, "minecraft:iron_axe": 9, "minecraft:golden_axe": 7,
    "minecraft:diamond_axe": 9, "minecraft:netherite_axe": 10,
}


def read_csv(path):
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def species():
    return read_csv(MOBS_CSV)


def params(row):
    """`k=v;k=v` → dict (değerler metin)."""
    out = {}
    for part in filter(None, (row.get("params") or "").split(";")):
        k, _, v = part.partition("=")
        out[k.strip()] = v.strip()
    return out


# ─── Bölge kademesi ──────────────────────────────────────────────────────────────────────────────────────────────
def zone_tier(dim, dist, y, z=ZONE):
    """dim: overworld|nether|end · dist: doğuş noktasına yatay uzaklık (blok) · y: yükseklik."""
    base = {"overworld": 0, "nether": z["nether"], "end": z["end"]}[dim]
    tier = base + math.floor(dist / z["dist_step"])
    if y < z["depth_from"]:
        tier += math.floor((z["depth_from"] - y) / z["depth_step"])
    if y > z["height_from"]:
        tier += math.floor((y - z["height_from"]) / z["height_step"])
    return max(0, min(z["cap"], tier))


ZONE_CASE_POINTS = [("overworld", 0, 64), ("overworld", 599, 64), ("overworld", 600, 64), ("overworld", 2500, 64), ("overworld", 7800, 64), ("overworld", 20000, 64),
                    ("overworld", 0, -1), ("overworld", 0, -33), ("overworld", 0, -64), ("overworld", 3000, -64), ("overworld", 0, 140), ("overworld", 0, 141),
                    ("overworld", 0, 181), ("overworld", 0, 320), ("overworld", 4000, 250), ("nether", 0, 64), ("nether", 1200, 64), ("nether", 6000, 64),
                    ("end", 0, 64), ("end", 600, 64), ("end", 3000, 64)]


def xp_mult(tier):
    return XP_GROWTH ** tier


# ─── Tür sayıları ───────────────────────────────────────────────────────────────────────────────────────────────────
def final_stats(row):
    """Kademe çarpanları uygulanmış nihai sayılar ve ref oyuncuya karşı HTD/HTK/TTK (sim.py ile aynı formüller)."""
    tier = int(row["tier"])
    hp, dmg, armor = float(row["hp"]), float(row["dmg"]), float(row["armor"])
    hp_f, armor_f = hp * M.mob_hp_mult(tier), armor * M.mob_dmg_mult(tier)
    caster = row["archetype"] in ("caster",) or "cast" in params(row)
    # Büyücü büyüsü zırhı deler (DamageTypes.MAGIC → #bypasses_armor): delici çarpan; öteki hepsi zırhla azaltılabilir hasar.
    dmg_f = dmg * (M.mob_bypass_mult(tier) if caster else M.mob_dmg_mult(tier))
    player = M.loadout(tier)
    htd = M.hits_to_die(player, dmg_f, bypass_armor=caster)
    htk = M.hits_to_kill(player, tier, hp, armor)
    return dict(id=row["id"], tier=tier, cls=row["cls"], hp=round(hp_f, 2), dmg=round(dmg_f, 2), armor=round(armor_f, 2), bypass=int(caster),
                htd=round(htd, 2), htk=round(htk, 2), ttk=round(math.ceil(htk - 1e-9) * ATTACK_PERIOD, 2))


# ─── Çıktılar ───────────────────────────────────────────────────────────────────────────────────────────────────────
def outputs():
    zones = [dict(key=k, value=v, note=n) for (k, v), n in zip(ZONE.items(), (
        "doğuş noktasından her N blok +1 kademe", "bu yüksekliğin altı derinlik", "her N blok derinlik +1", "bu yüksekliğin üstü yükseklik primi", "her N blok yükseklik +1",
        "Nether taban kademesi", "End taban kademesi", "kademe tavanı", "yeni tür spawn bandı: tür kademesi ± band"))]
    cases = [dict(dim=d, dist=dist, y=y, tier=zone_tier(d, dist, y)) for d, dist, y in ZONE_CASE_POINTS]
    xp = [dict(kind="growth", key="xp_growth", value=XP_GROWTH)] + [dict(kind="base", key=c, value=v) for c, v in XP_BASE.items()] + \
         [dict(kind="tier", key=str(t), value=round(xp_mult(t), 4)) for t in range(M.N)] + \
         [dict(kind="heart", key="hp", value=HEART_HP), dict(kind="heart", key="max", value=HEART_MAX)]
    ttk = [final_stats(r) for r in species()]
    weapons = [dict(item=k, vanilla_total=v) for k, v in VANILLA_WEAPONS.items()]
    game = dict(zone=ZONE, xp=dict(growth=XP_GROWTH, base=XP_BASE), heart=dict(hp=HEART_HP, max=HEART_MAX), weapons=VANILLA_WEAPONS)
    return {
        ZONES_CSV: M.render(["key", "value", "note"], zones),
        ZONE_CASES_CSV: M.render(["dim", "dist", "y", "tier"], cases),
        XP_CSV: M.render(["kind", "key", "value"], xp),
        TTK_CSV: M.render(list(ttk[0].keys()), ttk),
        WEAPONS_CSV: M.render(["item", "vanilla_total"], weapons),
        GAME_JSON: M.dumps(game),
    }


# ─── Denetimler ─────────────────────────────────────────────────────────────────────────────────────────────────────
def checks():
    rows = species()
    out = []

    def add(name, ok, detail=""):
        out.append((name, bool(ok), detail))

    ids = [r["id"] for r in rows]
    add("tür kimlikleri benzersiz ve [a-z0-9_]", len(ids) == len(set(ids)) and all(i.replace("_", "").isalnum() and i == i.lower() for i in ids))
    add(f"en az {MIN_SPECIES} tür", len(rows) >= MIN_SPECIES, f"{len(rows)} tür")
    bosses = [r for r in rows if r["cls"] == "boss"]
    add(f"en az {MIN_BOSSES} boss", len(bosses) >= MIN_BOSSES, f"{len(bosses)} boss")
    add("12 arketipin hepsi kullanılıyor", {r["archetype"] for r in rows} == set(ARCHETYPES), f"eksik: {set(ARCHETYPES) - {r['archetype'] for r in rows}}")
    add("8 ailenin hepsi kullanılıyor", {r["family"] for r in rows} == set(FAMILIES), f"eksik: {set(FAMILIES) - {r['family'] for r in rows}}")
    add("sınıflar geçerli", all(r["cls"] in CLASSES for r in rows))
    add("bossların sınıfı boss, başka sınıf boss değil", all((r["cls"] == "boss") == ("phases" in params(r)) for r in rows))
    add("kademe 1..13 aralığında ve her kademede en az bir tür", {int(r["tier"]) for r in rows} == set(range(1, M.N)),
        f"eksik kademe: {sorted(set(range(1, M.N)) - {int(r['tier']) for r in rows})}")
    rigs = json.loads(RIGS_JSON.read_text(encoding="utf-8")) if RIGS_JSON.exists() else {}
    add("her tür bir iskelet (rig) kullanır ve iskelet tanımlı", all(r["rig"] in rigs for r in rows), f"tanımsız: {sorted({r['rig'] for r in rows} - set(rigs))}")
    add("taban hasar ≤ 7 (ref HTD ≥ 2 her kademede; docs/06 §4)", all(float(r["dmg"]) <= BASE_DMG_MAX for r in rows))
    for cls, band in CLASS_BAND.items():
        group = [r for r in rows if r["cls"] == cls]
        bad_hp = [r["id"] for r in group if not band["hp"][0] <= float(r["hp"]) <= band["hp"][1]]
        add(f"{cls}: taban can {band['hp'][0]}–{band['hp'][1]}", not bad_hp, f"dışı: {bad_hp}")
        stats = [final_stats(r) for r in group]
        bad_htd = [s["id"] for s in stats if s["htd"] < band["htd_min"]]
        add(f"{cls}: ref ölüm vuruşu ≥ {band['htd_min']}", not bad_htd, f"düşük: {[(s['id'], s['htd']) for s in stats if s['id'] in bad_htd]}")
        bad_htk = [s["id"] for s in stats if not band["htk"][0] <= s["htk"] <= band["htk"][1]]
        add(f"{cls}: ref öldürme vuruşu {band['htk'][0]}–{band['htk'][1]}", not bad_htk, f"dışı: {[(s['id'], s['htk']) for s in stats if s['id'] in bad_htk]}")
    spawnable = [r for r in rows if r["cls"] != "boss"]
    add("boss dışı her tür bir biyom + ağırlık > 0 taşır, boss'larınki 0", all(r["biomes"] and float(r["weight"]) > 0 for r in spawnable) and all(not r["biomes"] and float(r["weight"]) == 0 for r in bosses))
    add("spawn y aralığı geçerli, grup 1 ≤ min ≤ max", all(int(r["min_y"]) < int(r["max_y"]) and 1 <= int(r["group_min"]) <= int(r["group_max"]) for r in rows))
    known = set(ids)
    adds_ok = all(params(r).get("adds", "") in known | {""} for r in rows)
    add("boss yardımcıları (adds) tanımlı türler", adds_ok)
    add("tür boyutları ve ölçek pozitif", all(float(r[k]) > 0 for r in rows for k in ("width", "height", "scale", "speed", "follow")))
    add("renkler #rrggbb", all(len(r["color"]) == 7 and len(r["accent"]) == 7 for r in rows))
    # Bölge kuralı
    add("bölge kademesi doğuşta 0, tavanı aşmaz, tek yönlü artar",
        zone_tier("overworld", 0, 64) == 0 and zone_tier("overworld", 10 ** 7, 64) == ZONE["cap"] and
        all(zone_tier("overworld", d, 64) <= zone_tier("overworld", d + 100, 64) for d in range(0, 9000, 100)))
    add("boyut tabanları sıralı (overworld < nether < end)", ZONE["nether"] < ZONE["end"] <= ZONE["cap"])
    # XP
    add("XP çarpanı kademeyle artar ve T13'te zombi sınıfı 20K–60K", all(xp_mult(t + 1) > xp_mult(t) for t in range(M.N - 1)) and 20_000 <= XP_BASE["normal"] * xp_mult(M.N - 1) <= 60_000,
        f"T13 normal = {XP_BASE['normal'] * xp_mult(M.N - 1):.0f}")
    add("Kalp Kristali toplamı ≈ docs/06 §3 kalp ödülü (+44 can)", HEART_HP * HEART_MAX == 44)
    return out


def report():
    rows = species()
    print(f"{len(rows)} tür · {sum(1 for r in rows if r['cls'] == 'boss')} boss · arketip {len({r['archetype'] for r in rows})}/12 · aile {len({r['family'] for r in rows})}/8")
    print(f"{'tür':18} {'kdm':>3} {'sınıf':7} {'arketip':9} {'taban c/h/z':>12} {'nihai can':>10} {'nihai hasar':>11} {'HTD':>6} {'HTK':>6} {'TTK sn':>7} {'XP':>8}")
    for r in sorted(rows, key=lambda r: (int(r["tier"]), r["id"])):
        s = final_stats(r)
        xp = XP_BASE[r["cls"]] * xp_mult(s["tier"])
        print(f"{r['id']:18} {s['tier']:>3} {r['cls']:7} {r['archetype']:9} {r['hp']:>4}/{r['dmg']:>2}/{r['armor']:>2} {s['hp']:>10.1f} {s['dmg']:>10.1f}{'*' if s['bypass'] else ' '} "
              f"{s['htd']:>6.2f} {s['htk']:>6.1f} {s['ttk']:>7.1f} {xp:>8.0f}")
    print("(* = zırhı delen büyü hasarı)")
    print("Bölge kademesi örnekleri:", " · ".join(f"{d[:3]} d={dist} y={y} → T{zone_tier(d, dist, y)}" for d, dist, y in ZONE_CASE_POINTS[:12]))


def negative():
    """Denetimlerin gerçekten kırmızıya döndüğünü kanıtlar."""
    g = globals()
    original = [dict(r) for r in species()]
    cases = [("sınıf bandı can ×10", "CLASS_BAND", {**CLASS_BAND, "normal": dict(CLASS_BAND["normal"], hp=(140, 300))}),
             ("XP büyümesi 1,2", "XP_GROWTH", 1.2), ("Kalp kristali 3 can", "HEART_HP", 3), ("taban hasar tavanı 3", "BASE_DMG_MAX", 3),
             ("bölge nether > end", "ZONE", dict(ZONE, nether=9, end=8)), ("en az 60 tür", "MIN_SPECIES", 60)]
    green = 0
    for name, key, bad in cases:
        old = g[key]
        g[key] = bad
        try:
            red = [n for n, ok, _ in checks() if not ok]
        finally:
            g[key] = old
        print(f"  {'✅ kırmızı' if red else '❌ YEŞİL KALDI'}: {name} → {len(red)} denetim düştü")
        green += 0 if red else 1
    # Veri bozma: bir türün arketipini sil
    broken = [dict(r) for r in original]
    broken[0]["archetype"] = "yok"
    old_species = g["species"]
    g["species"] = lambda: broken
    try:
        red = [n for n, ok, _ in checks() if not ok]
    finally:
        g["species"] = old_species
    print(f"  {'✅ kırmızı' if red else '❌ YEŞİL KALDI'}: arketip silindi → {len(red)} denetim düştü")
    green += 0 if red else 1
    print("OK" if not green else f"HATA ({green} bozulma yakalanmadı)")
    return 1 if green else 0


def main(argv):
    if "--break" in argv:
        return negative()
    files = outputs()
    check = "--check" in argv
    stale = [p for p, text in files.items() if not p.exists() or p.read_text(encoding="utf-8") != text]
    if not check:
        for p in stale:
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(files[p], encoding="utf-8")
    if "--report" in argv:
        report()
    results = checks()
    failed = [r for r in results if not r[1]]
    if check or "--report" in argv or failed:
        for name, ok, detail in results:
            if not ok or "--report" in argv:
                print(f"  {'✅' if ok else '❌'} {name}" + (f" — {detail}" if detail and not ok else ""))
    for p in (stale if check else []):
        print(f"BAYAT: {p.relative_to(ROOT)} → python3 -I tools/balance/mobs.py")
    bad = len(failed) + (len(stale) if check else 0)
    print("OK" if not bad else f"HATA ({bad})")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
