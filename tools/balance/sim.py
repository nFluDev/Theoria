#!/usr/bin/env python3
"""Theoria denge simülasyonu (Faz 2a). Model ve sabitler tools/balance/tiers.py'de; burada senaryolar ve bantlar.

    python3 -I tools/balance/sim.py            # tüm tablolar + bant denetimleri (kırmızı varsa çıkış 1)
    python3 -I tools/balance/sim.py --check    # yalnızca denetimler + sim_cases.csv güncel mi (preflight bunu koşar)
    python3 -I tools/balance/sim.py --cases    # data/balance/sim_cases.csv'yi yeniden yazar (2b GameTest'lerinin beklenen değerleri)
    python3 -I tools/balance/sim.py --fit      # katsayı ızgarası: seçilen değerler neden seçildi
    python3 -I tools/balance/sim.py --mobs     # data/vanilla_mobs.csv: her mob için kademe başına ölüm vuruşu (Faz 8 girdisi)
    python3 -I tools/balance/sim.py --budget   # docs/04 taslak puan değerleri referans bütçeyi kaç seviyede dolduruyor (Faz 7/10 girdisi)

Terimler: HTD = ölüm vuruşu (oyuncu kaç vuruşta ölür) · HTK = öldürme vuruşu · H = zırh öncesi hasar (Normal zorluk).
Arketipler (tiers.loadout): min = az yatırım · ref = beklenen bütçe · max = çok yatırım.
"""
import contextlib, csv, itertools, math, pathlib, sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))   # -I altında kendi klasörümüz yolda değil
import tiers as M

CASES_CSV = M.ROOT / "data/balance/sim_cases.csv"
TIERS = range(M.N)
STRONG, ELITE = 7 / 3, 12 / 3          # enderman (7) ve ravager (12) sınıfı: zombi (3) hasarının katı
HARD = 1.5                             # vanilla Zor: oyuncuya gelen mob hasarı ×1,5
ATTACK_PERIOD, MOB_PERIOD, MOB_FIRST = 0.625, 1.0, 0.5   # sn: kılıç 1,6 vuruş/sn; zombi 1 vuruş/sn, ilk temas 0,5 sn

# Bantlar (docs/06 §1). Denetimler bunlara göre yeşil/kırmızı.
HTD_BAND = (6.0, 8.0)        # ref, eşit kademe zombi sınıfı
HTK_BAND = (4.5, 7.0)        # ref, kritiksiz
MIN_HTD, MAX_HTD = 2.0, 30.0 # min arketip en az / max arketip en çok
STRONG_HTD = 1.5             # ref, enderman sınıfı: tam candan en az 2 vuruş
UP2_BAND, DOWN2_MIN = (2.5, 5.0), 10.0   # ref, 2 kademe üstü / altı mob
HARD_HTD = 3.5               # ref, Zor zorluk (hedefin yarısı; paketin referans zorluğu Normal)
DUEL_MAX = 0.60              # ref, yerinde durarak bir eşit-kademe mob öldürmenin can bedeli
UPGRADE_MIN = 1.04           # bir üst takımı giymek (bonussuz) en az +%4 dayanma
BREAK_MIN = 80               # göğüslük: eşit kademe vuruşla en az bu kadar vuruş (vanilla deri = 80)


def htd(p, kind="ref", mob=None, mult=1.0):
    return M.hits_to_die(M.loadout(p, kind), M.mob_dmg(p if mob is None else mob) * mult)


def htk(p, kind="ref"):
    return M.hits_to_kill(M.loadout(p, kind), p)


def duel(p, kind="ref"):
    """Yerinde durarak tek eşit-kademe mob: (kaybedilen can oranı, ölmeden art arda kaç mob). Regen yok."""
    player = M.loadout(p, kind)
    kill_time = (math.ceil(htk(p, kind) - 1e-9) - 1) * ATTACK_PERIOD
    taken = 0 if kill_time <= MOB_FIRST else math.floor((kill_time - MOB_FIRST) / MOB_PERIOD - 1e-9) + 1
    per_hit = M.damage_taken(M.mob_dmg(p), player["armor"], player["toughness"], player["epf"])[1]
    cost = taken * per_hit / player["hp"]
    return cost, (math.inf if cost == 0 else math.ceil(1 / cost - 1e-9) - 1)


def set_dr(p, mob=None):       # yalnızca kademe takımı (bonussuz)
    return M.dr(M.set_armor(p), M.mob_dmg(p if mob is None else mob), M.tough(p))


def upgrade(p):                # kademe p mob'una karşı: p takımı / p-1 takımı (aynı can, bonussuz)
    hit = M.mob_dmg(p)
    return (1 - M.dr(M.set_armor(p - 1), hit, M.tough(p - 1))) / (1 - M.dr(M.set_armor(p), hit, M.tough(p)))


def wear(hit):                 # vanilla LivingEntity#doHurtEquipment: parça başı (int) max(1, H/4)
    return int(max(1.0, hit / 4.0))


def break_hits(p, mult=1.0):   # göğüslük kaç eşit-kademe vuruşta kırılır (Kırılmazlık yok)
    return M.adur(p) / wear(M.mob_dmg(p) * mult)


# ─── Denetimler ──────────────────────────────────────────────────────────────────────────────────────────────────
def checks():
    out = []

    def add(name, values, ok, fmt="{:.2f}"):
        bad = [f"T{p}={fmt.format(v)}" for p, v in values if not ok(v)]
        vals = [v for _, v in values]
        out.append((name, not bad, f"{fmt.format(min(vals))} … {fmt.format(max(vals))}" + (f"  ✗ {', '.join(bad)}" if bad else "")))

    add(f"ref HTD {HTD_BAND[0]:g}–{HTD_BAND[1]:g}", [(p, htd(p)) for p in TIERS], lambda v: HTD_BAND[0] <= v <= HTD_BAND[1])
    add(f"ref HTK {HTK_BAND[0]:g}–{HTK_BAND[1]:g}", [(p, htk(p)) for p in TIERS], lambda v: HTK_BAND[0] <= v <= HTK_BAND[1])
    add(f"min HTD ≥ {MIN_HTD:g}", [(p, htd(p, "min")) for p in TIERS], lambda v: v >= MIN_HTD)
    add(f"max HTD ≤ {MAX_HTD:g}", [(p, htd(p, "max")) for p in TIERS], lambda v: v <= MAX_HTD)
    add(f"ref güçlü mob (×{STRONG:.2f}) HTD ≥ {STRONG_HTD:g}", [(p, htd(p, mult=STRONG)) for p in TIERS], lambda v: v >= STRONG_HTD)
    add(f"ref +2 kademe HTD {UP2_BAND[0]:g}–{UP2_BAND[1]:g}", [(p, htd(p, mob=p + 2)) for p in TIERS if p + 2 < M.N],
        lambda v: UP2_BAND[0] <= v <= UP2_BAND[1])
    add(f"ref −2 kademe HTD ≥ {DOWN2_MIN:g}", [(p, htd(p, mob=p - 2)) for p in TIERS if p >= 2], lambda v: v >= DOWN2_MIN)
    add(f"ref Zor zorluk HTD ≥ {HARD_HTD:g}", [(p, htd(p, mult=HARD)) for p in TIERS], lambda v: v >= HARD_HTD)
    add(f"ref düello bedeli ≤ %{DUEL_MAX * 100:g}", [(p, duel(p)[0]) for p in TIERS], lambda v: v <= DUEL_MAX)
    add(f"takım yükseltme değeri ≥ ×{UPGRADE_MIN:g}", [(p, upgrade(p)) for p in TIERS if p >= 1], lambda v: v >= UPGRADE_MIN)
    add(f"göğüslük ömrü ≥ {BREAK_MIN} vuruş", [(p, break_hits(p)) for p in TIERS], lambda v: v >= BREAK_MIN, "{:.0f}")

    # Formül özellikleri (ızgara üzerinde): tekdüzelik ve tavan
    grid_a, grid_h, grid_t = (0, 1, 4, 34, 147, 309, 1002, 5000), (0.5, 1, 3, 27.7, 110.8, 500, 5000), (0, 2, 8, 20, 100)
    mono = True
    for t in grid_t:
        for a in grid_a:
            taken = [M.damage_taken(h, a, t)[0] for h in grid_h]
            mono &= all(x < y for x, y in zip(taken, taken[1:]))                       # büyük vuruş asla daha az vurmaz
            mono &= all(M.dr(a, h, t) <= M.ARMOR_CAP + 1e-12 for h in grid_h)
        for h in grid_h:
            ratios = [M.dr(a, h, t) for a in grid_a]
            mono &= all(x <= y for x, y in zip(ratios, ratios[1:]))                    # daha çok zırh asla daha kötü değil
    for a, h in itertools.product(grid_a, grid_h):
        ratios = [M.dr(a, h, t) for t in grid_t]
        mono &= all(x <= y for x, y in zip(ratios, ratios[1:]))                        # daha çok sertlik asla daha kötü değil
    out.append(("formül tekdüze + tavan ≤ %90", mono, f"{len(grid_a) * len(grid_h) * len(grid_t)} nokta"))
    top = M.loadout(M.N - 1)
    out.append(("alt kademe mob'da tavan (T13 ref ↔ T0 zombi)", M.dr(top["armor"], M.mob_dmg(0), top["toughness"]) >= 0.85,
                f"%{M.dr(top['armor'], M.mob_dmg(0), top['toughness']) * 100:.0f}"))
    stacked = (1 - M.ARMOR_CAP) * M.ench_factor(M.EPF_MAX)
    out.append(("zırh tavanı × EPF tavanı: en az %5 hasar geçer", stacked >= 0.05, f"%{stacked * 100:.1f}"))
    return out


def warnings():
    out = []
    one_shot = [f"T{p} ({htd(p, mult=ELITE):.2f})" for p in TIERS if htd(p, mult=ELITE) < 1.0]
    if one_shot:
        out.append(f"elit mob (×{ELITE:g}, ravager sınıfı) ref oyuncuyu tam candan tek atar: {', '.join(one_shot)} → Faz 8: kademe bandı/çarpan indirimi")
    return out


# ─── Tablolar ────────────────────────────────────────────────────────────────────────────────────────────────────
def table(title, header, lines):
    print(f"\n{title}")
    widths = [max(len(str(c)) for c in col) for col in zip(header, *lines)]
    for row in (header, *lines):
        print("  " + "  ".join(str(c).rjust(w) for c, w in zip(row, widths)))


def report():
    name = lambda p: f"T{p} {M.ARMOR[p][2]}"
    table("1) Dövüş — eşit kademe zombi sınıfı (Normal). HTD: min/ref/max; HTK ve düello: ref",
          ["kademe", "H", "ref can", "ref zırh", "sert", "EPF", "ref DR", "HTD min", "HTD ref", "HTD max", "HTK", "düello", "zincir"],
          [[name(p), f"{M.mob_dmg(p):.1f}", f"{r['hp']:.0f}", r["armor"], r["toughness"], r["epf"],
            f"%{M.dr(r['armor'], M.mob_dmg(p), r['toughness']) * 100:.0f}", f"{htd(p, 'min'):.1f}", f"{htd(p):.1f}", f"{htd(p, 'max'):.1f}",
            f"{htk(p):.1f}", f"%{duel(p)[0] * 100:.0f}", duel(p)[1]] for p in TIERS for r in [M.loadout(p)]])
    table(f"2) Kademe eğimi ve zor mob'lar — ref HTD (güçlü ×{STRONG:.2f} enderman, elit ×{ELITE:g} ravager, Zor ×{HARD})",
          ["kademe", "−2 kad.", "eşit", "+2 kad.", "güçlü", "elit", "Zor", "Zor+güçlü"],
          [[name(p), f"{htd(p, mob=p - 2):.1f}" if p >= 2 else "–", f"{htd(p):.1f}", f"{htd(p, mob=p + 2):.1f}" if p + 2 < M.N else "–",
            f"{htd(p, mult=STRONG):.2f}", f"{htd(p, mult=ELITE):.2f}", f"{htd(p, mult=HARD):.1f}", f"{htd(p, mult=HARD * STRONG):.2f}"] for p in TIERS])
    table("3) Zırhın değeri — yalnızca kademe takımı (bonussuz) ve göğüslük ömrü (vuruş)",
          ["kademe", "takım", "DR eşit", "DR ↔T0", "DR güçlü", "yükseltme", "dayan.", "aşınma/vuruş", "ömür", "ömür elit"],
          [[name(p), M.set_armor(p), f"%{set_dr(p) * 100:.0f}", f"%{set_dr(p, 0) * 100:.0f}",
            f"%{M.dr(M.set_armor(p), M.mob_dmg(p) * STRONG, M.tough(p)) * 100:.0f}", f"×{upgrade(p):.2f}" if p else "–",
            M.adur(p), wear(M.mob_dmg(p)), f"{break_hits(p):.0f}", f"{break_hits(p, ELITE):.0f}"] for p in TIERS])
    print("\n4) İyileşme — tüm iyileşme (azami can / 20) ile ölçeklenir; vanilla zamanlaması aynı kalır")
    print(f"  yavaş regen (açlık ≥ 18): 80 tick'te azami canın 1/20'si = %{100 / 20 / 4:.2f}/sn → sıfırdan tam can {20 * 4} sn (her kademede)")
    print(f"  hızlı regen (açlık 20 + satürasyon): 10 tick'te en çok 1/20 = %{100 / 20 / 0.5:.0f}/sn; 1 satürasyon puanı ≈ azami canın %{100 / 20 / 1.5:.1f}'i")
    print(f"  ölçeksiz kalsaydı T13 ref ({M.exp_hp(M.N - 1)} can): 0,25 can/sn → tam can {M.exp_hp(M.N - 1) * 4 / 60:.0f} dk")


# ─── Katsayı ızgarası ────────────────────────────────────────────────────────────────────────────────────────────
@contextlib.contextmanager
def patched(**values):
    old = {k: getattr(M, k) for k in values}
    try:
        for k, v in values.items():
            setattr(M, k, v)
        yield
    finally:
        for k, v in old.items():
            setattr(M, k, v)


FIT_AXES = ((15, 20, 25, 30, 35, 40, 50, 60), (4, 5, 6, 7, 8, 9, 10, 12), (1.28, 1.29, 1.30, 1.31, 1.32, 1.33), (7, 8, 9, 99))
DRAFT = (60, 4, 1.28, 99)      # Faz 0 taslağı: min(0,90, A/(A+60+4·H)), mob hasarı 1,28^idx, sertlik formülde yok


def fit():
    """Seçim kuralı: (1) tüm denetimler yeşil, (2) tek adımlık komşularından en çok yeşil olanlar (sağlamlık), (3) HTD bandında pay.
    İlk gruptaki noktalar birbirine eşdeğer (HTD farkı ≈ 0,1 vuruş); aralarından yuvarlak sayılı olan seçildi."""
    chosen = (M.ARMOR_K1, M.ARMOR_K2, M.G_MOB_DMG, M.TOUGH_FROM)
    grid = {}
    for key in itertools.product(*FIT_AXES):
        with patched(ARMOR_K1=key[0], ARMOR_K2=key[1], G_MOB_DMG=key[2], TOUGH_FROM=key[3]):
            vals = [htd(p) for p in TIERS]
            last = M.loadout(M.N - 1)
            grid[key] = dict(key=key, failed=[n for n, ok, _ in checks() if not ok], lo=min(vals), hi=max(vals),
                             margin=min(min(vals) - HTD_BAND[0], HTD_BAND[1] - max(vals)),
                             strong=min(htd(p, mult=STRONG) for p in TIERS), elite=min(htd(p, mult=ELITE) for p in TIERS),
                             mn=min(htd(p, "min") for p in TIERS), dr=M.dr(last["armor"], M.mob_dmg(M.N - 1), last["toughness"]),
                             up=min(upgrade(p) for p in TIERS if p))

    def neighbours(key):
        for i, axis in enumerate(FIT_AXES):
            j = axis.index(key[i])
            for step in (-1, 1):
                if 0 <= j + step < len(axis):
                    yield key[:i] + (axis[j + step],) + key[i + 1:]

    for r in grid.values():
        near = list(neighbours(r["key"]))
        r["near"], r["near_green"] = len(near), sum(not grid[n]["failed"] for n in near)
    green = [r for r in grid.values() if not r["failed"]]
    line = lambda r: (f"  k1={r['key'][0]:<2} k2={r['key'][1]:<2} g={r['key'][2]:.2f} sert≥T{r['key'][3]:<2} | HTD {r['lo']:.2f}…{r['hi']:.2f} (pay {r['margin']:+.2f}) "
                      f"| yeşil komşu {r['near_green']}/{r['near']} | güçlü {r['strong']:.2f} elit {r['elite']:.2f} min {r['mn']:.2f} | T13 DR %{r['dr'] * 100:.0f} yükseltme ×{r['up']:.3f}"
                      + ("  ← SEÇİLEN" if r["key"] == chosen else "") + (f"  ✗ {r['failed'][0]}" + (f" (+{len(r['failed']) - 1})" if len(r["failed"]) > 1 else "") if r["failed"] else ""))
    print(f"ızgara: {len(grid)} nokta (k1 × k2 × mob hasar büyümesi × sertlik başlangıcı; 99 = takım sertliği yok); tüm denetimleri geçen: {len(green)}")
    print("\nyeşil noktalar — sağlamlık (yeşil komşu sayısı), sonra HTD payı sırasıyla ilk 12:")
    for r in sorted(green, key=lambda r: (-r["near_green"], -r["margin"]))[:12]:
        print(line(r))
    print("\ntaslak ve seçilen:")
    for key in (DRAFT, chosen):
        print(line(grid[key]) if key in grid else f"  {key} ızgarada değil")
    print("\nseçilenin tek adımlık komşuları:")
    for key in neighbours(chosen) if chosen in grid else ():
        print(line(grid[key]))


# ─── Vanilla mob raporu / bütçe duyarlılığı ──────────────────────────────────────────────────────────────────────
def mobs():
    path = M.ROOT / "data/vanilla_mobs.csv"
    picks = (0, 4, 8, 13)
    lines = []
    for row in csv.DictReader(open(path, newline="", encoding="utf-8")):
        if not row["attack_damage"] or row["id"] == "minecraft:player" or float(row["attack_damage"]) <= 0:
            continue
        base, hp, armor = float(row["attack_damage"]), float(row["max_health"]), float(row["armor"] or 0)
        cells = []
        for p in picks:
            die = M.hits_to_die(M.loadout(p), base * M.mob_dmg_mult(p))
            kill = M.hits_to_kill(M.loadout(p), p, hp, armor)
            cells.append(f"{die:5.2f}{'!' if die < 1 else ' '}/{kill:5.1f}")
        lines.append((base, [row["id"].split(":")[1], f"{hp:g}", f"{base:g}", f"{armor:g}", *cells]))
    lines.sort(key=lambda x: -x[0])
    table("Vanilla mob'lar (attribute taban hasarı; elde silah/mermi/patlama HARİÇ) — ref oyuncu HTD / HTK, '!' = tam candan tek atar",
          ["mob", "can", "hasar", "zırh", *[f"T{p}" for p in picks]], [l for _, l in lines])


def budget():
    """docs/04 §5 taslak puan değerleri: Vitalite +4 can/puan, Dayanıklılık +2 zırh/puan, Güç +0,5 hasar/puan; 4 puan/seviye."""
    lines = []
    for p in (2, 4, 8, 13):
        hp_gap = M.exp_hp(p) - M.PLAYER_BASE_HP
        arm_gap = M.set_armor(p) * M.ref_bonus(p)
        dmg_gap = M.sword(p) * M.ref_bonus(p)
        pts = hp_gap / 4 + arm_gap / 2 + dmg_gap / 0.5
        lines.append([f"T{p}", f"+{hp_gap:.0f}", f"{hp_gap / 4:.0f}", f"+{arm_gap:.0f}", f"{arm_gap / 2:.0f}", f"+{dmg_gap:.1f}", f"{dmg_gap / 0.5:.0f}",
                      f"{pts:.0f}", f"L{1 + math.ceil(pts / 4)}"])
    table("Referans bütçeyi YALNIZCA seviye puanıyla doldurmak (katalizör ve kalp eşyası sayılmadan)",
          ["kademe", "can açığı", "Vit puanı", "zırh bonusu", "Day puanı", "hasar bonusu", "Güç puanı", "toplam puan", "yeten seviye"], lines)
    p = M.N - 1
    for level in (25, 50, 100):
        pts = 4 * (level - 1)
        vit = dict(M.loadout(p), hp=M.loadout(p)["hp"] + 4 * pts)
        end = dict(M.loadout(p), armor=M.loadout(p)["armor"] + 2 * pts, toughness=M.loadout(p)["toughness"] + 0.2 * pts)
        print(f"  T13 ref + L{level} ({pts} puan) tek stat'a: hepsi Vitalite → HTD {M.hits_to_die(vit, M.mob_dmg(p)):.0f} · "
              f"hepsi Dayanıklılık → HTD {M.hits_to_die(end, M.mob_dmg(p)):.0f}  (ref {htd(p):.1f}, max bandı ≤ {MAX_HTD:g})")
    print("  → taslak puan değerleri bütçeye göre çok cömertti; Faz 10 yumuşak tavanlı yeni değerleri tools/balance/stats.py'de (bütçe payı denetimi: stats.py --report).")


# ─── 2b için beklenen değerler ───────────────────────────────────────────────────────────────────────────────────
CASE_FIELDS = "case,tier,hit,armor,toughness,epf,resistance,bypass_armor,raw,after_armor,final,note".split(",")


def cases():
    rows = []

    def add(name, raw, armor=0, toughness=0, epf=0, resistance=0, bypass=False, note="", tier=0):
        # tier > 0: saldıran o kademede vanilla zombi sınıfı; oyuna taban hasar (MOB_BASE_DMG) verilir, çarpan boru hattından gelir.
        # tier = 0: oyuna doğrudan `raw` verilir (kademesiz saldıran). İkisinde de zırh öncesi H = raw.
        hit = M.MOB_BASE_DMG if tier else raw
        after, final = M.damage_taken(raw, armor, toughness, epf, resistance, bypass)
        rows.append(dict(case=name, tier=tier, hit=f"{hit:.4f}", armor=armor, toughness=toughness, epf=epf, resistance=resistance, bypass_armor=str(bypass).lower(),
                         raw=f"{raw:.4f}", after_armor=f"{after:.4f}", final=f"{final:.4f}", note=note))

    t8 = M.mob_dmg(8)
    add("armor300_vs_t8", t8, armor=300, tier=8, note="Faz 2 DoD: /theoria test balance (zirh 300 oyuncu, kademe 8 zombi sinifi)")
    add("no_armor", t8, tier=8, note="zirh 0: azaltma yok")
    add("vanilla_zombie_armor", 4.0, armor=2, note="vanilla zombi (2 zirh) tahta kilica karsi")
    add("small_hit_cap", 1.0, armor=1000, note="tavan %90")
    add("big_hit", 200.0, armor=300, note="buyuk vurus daha az azalir")
    add("toughness_8", 200.0, armor=300, toughness=8, note="T=8 buyuk-vurus cezasini yarilar")
    add("toughness_20", 200.0, armor=300, toughness=20)
    add("epf_16", t8, epf=16, tier=8, note="Koruma IV x4: vanilla %64 -> %32")
    add("epf_20", t8, epf=20, tier=8, note="EPF tavani: vanilla %80 -> %40")
    add("epf_over_cap", t8, epf=40, tier=8, note="20 ustu EPF sayilmaz")
    add("armor_epf_resistance", t8, armor=300, epf=16, resistance=1, tier=8, note="carpimsal: zirh x Direnc I x EPF 16")
    add("bypass_armor_epf", 20.0, armor=300, epf=16, bypass=True, note="buyu hasari: zirh yok, EPF var")
    for p in TIERS:
        r = M.loadout(p)
        add(f"ref_t{p}", M.mob_dmg(p), armor=r["armor"], toughness=r["toughness"], epf=r["epf"], tier=p, note=f"ref oyuncu, esit kademe; can {r['hp']:.0f}")
    return {CASES_CSV: M.render(CASE_FIELDS, rows)}


def main(argv):
    if "--fit" in argv:
        return fit() or 0
    if "--mobs" in argv:
        return mobs() or 0
    if "--budget" in argv:
        return budget() or 0
    quiet = "--check" in argv
    if "--cases" in argv:
        written = M.sync(cases(), check=False)
        print(f"{CASES_CSV.relative_to(M.ROOT)}: " + ("yeniden yazıldı" if written else "zaten güncel"))
        return 0
    stale = M.sync(cases(), check=True)
    if not quiet:
        report()
    results = checks()
    print("\nDenetimler:" if not quiet else "", end="\n" if not quiet else "")
    for name, ok, detail in results:
        if not quiet or not ok:
            print(f"  {'✅' if ok else '❌'} {name}: {detail}")
    for w in warnings():
        if not quiet:
            print(f"  ⚠ {w}")
    for p in stale:
        print(f"  ❌ BAYAT: {p.relative_to(M.ROOT)} → python3 -I tools/balance/sim.py --cases")
    failed = sum(not ok for _, ok, _ in results) + len(stale)
    print("OK" if not failed else f"HATA ({failed})")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
