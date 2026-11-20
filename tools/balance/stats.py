#!/usr/bin/env python3
"""Theoria durum ekranı dengesi — Faz 10 (2026-11-20). Seviye eğrisi, 8 stat'ın yumuşak tavanlı getirileri, tempo modeli ve bant denetimleri.

Sayıların TEK kaynağı bu dosya (+ `tiers.py` XP eğrisi, `mobs.py` mob XP'si). Üretilenler (elle düzenlenmez):
    data/balance/stats.csv           stat × etki: tavan, yarı-nokta, ilk puan eğimi (docs/04 §5 tablosu buradan)
    data/balance/leveling.csv        seviye/puan/respec sabitleri
    data/balance/pacing.csv          referans tempo: kademe başına süre, seviye, bütçe doluluğu (belge tablosu)
    mods-src/theoria/src/main/resources/theoria/stats.json   oyunun okuduğu biçim (jar'a gömülü: öğeler/bileşenler veri paketinden önce kaydolur,
                                                             istemci ekranı aynı sayıları ister — gear.json / technical_caps.json ile aynı neden)
Çalıştır:
    python3 -I tools/balance/stats.py            # yazar
    python3 -I tools/balance/stats.py --check    # yazmaz; bayat dosya ya da kırmızı bant → çıkış 1
    python3 -I tools/balance/stats.py --report   # stat, bütçe ve tempo tabloları
    python3 -I tools/balance/stats.py --break    # negatif test: bozulmuş sabitlerle denetimler kırmızıya dönmeli

MODEL (docs/04 §5, docs/06 §3 §5, Q3):
  * Seviye: ömür boyu ham XP → L (iki parçalı eğri `tiers.xp_total`), tavan 9999. Seviye başına POINTS_PER_LEVEL puan (2026-11-20'den beri 1: her puan eskisinin 4 katı etkili, yarı-noktalar ÷4 — toplam güç aynı); harcama seviyeyi düşürmez.
  * Her stat etkisi `f(n) = tavan · n / (n + yarı)`: n = o stat'a konan puan. İlk puan eğimi tavan/yarı; n → ∞ iken f → tavan (yumuşak tavan,
    azalan getiri). 39.992 puan hiçbir stat'ı tavanın üstüne çıkaramaz (R17).
  * Bütçe: referans oyuncunun seviye payı = can/zırh/hasar bonusunun belirli oranı (docs/06 §3: can %40, düz zırh/hasar bonusunun yarısı). Tavan ve
    yarı-noktalar, referans tempoda (aşağıda) T3..T13'te bu payı ±%25 içinde tutturacak şekilde seçildi; `--report` tabloyu basar.
  * Tempo (varsayım, kullanıcı onayına açık — docs/10 Q66): saatte KILLS_PER_HOUR öldürme, sınıf karışımı CLASS_MIX × 'normal' XP, kademe p'de
    TIER_HOURS[p] saat; XP bonusu (Bilgelik) yok sayılır = muhafazakâr alt sınır.
"""
import csv, json, math, pathlib, sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))   # -I altında kendi klasörümüz yolda değil
import tiers as M
import mobs as MB

ROOT = M.ROOT
BAL = ROOT / "data/balance"
STATS_CSV, LEVELING_CSV, PACING_CSV = BAL / "stats.csv", BAL / "leveling.csv", BAL / "pacing.csv"
GAME_JSON = M.RES / "theoria/stats.json"
LANG = M.RES / "assets/theoria/lang"

# ─── Seviye ve puan ──────────────────────────────────────────────────────────────────────────────────────────────
MAX_LEVEL = 9999
POINTS_PER_LEVEL = 4       # 2026-11-20 (kullanıcı kararı): seviye başı 4 puan ama puan başı etki 1 puanlık modelin %25'i — 4 puanı bir stat'a verirsen tek puanlık etki, 4 stat'a dağıtırsan ince ayar


def max_points():
    return POINTS_PER_LEVEL * (MAX_LEVEL - 1)


XP_ANCHORS, XP_KNEE = M.XP_ANCHORS, M.XP_KNEE        # tek kaynak tiers.py (parçalı kuvvet yasası); oyunun okuduğu stats.json'a aynen gider
RESPEC_ITEM = "theoria:oblivion_orb"
RESPEC_BOSS_CHANCE = 0.05                           # her boss öldürmesinde (data/mob_drops.csv ile eşit olmalı)

# ─── Stat'lar ────────────────────────────────────────────────────────────────────────────────────────────────────
# etki: (kimlik, tür, öznitelik/anahtar, işlem, tavan, yarı-nokta). tür: attribute (vanilla/Theoria özniteliği + kalıcı modifier) ·
# exhaustion (açlık düşüş hızı −%) · xp_percent (Bilgelik büyüsü toplamına eklenen %; Erudition.totalPercent).
# Birimler: can/zırh/hasar/sertlik/şans/mining = düz; agility = taban hız çarpanına +oran; kritik şans = olasılık; kritik hasar = +çarpan;
# food_saturation = yemekle gelen ek satürasyon; exhaustion = 0..1 azalma oranı; xp_percent = yüzde puan.
STATS = [
    ("strength", "Güç", "Strength", [
        ("attack_damage", "attribute", "minecraft:attack_damage", "add_value", 50.0, 1400.0),
        ("mining_efficiency", "attribute", "minecraft:mining_efficiency", "add_value", 4.0, 1400.0)]),
    ("vitality", "Vitalite", "Vitality", [
        ("max_health", "attribute", "minecraft:max_health", "add_value", 120.0, 490.0)]),
    ("endurance", "Dayanıklılık", "Endurance", [
        ("armor", "attribute", "minecraft:armor", "add_value", 100.0, 650.0),
        ("armor_toughness", "attribute", "minecraft:armor_toughness", "add_value", 4.0, 650.0),
        ("knockback_resistance", "attribute", "minecraft:knockback_resistance", "add_value", 0.5, 650.0)]),
    ("agility", "Çeviklik", "Agility", [
        ("movement_speed", "attribute", "minecraft:movement_speed", "add_multiplied_base", 0.30, 100.0)]),
    ("satiety", "Tokluk", "Satiety", [
        ("food_saturation", "attribute", "theoria:food_saturation", "add_value", 6.0, 150.0),
        ("exhaustion", "exhaustion", "", "", 0.50, 200.0)]),
    ("precision", "İsabet", "Precision", [
        ("crit_chance", "attribute", "theoria:crit_chance", "add_value", 0.35, 500.0),
        ("crit_damage", "attribute", "theoria:crit_damage", "add_value", 1.0, 500.0)]),
    ("fortune", "Şans", "Fortune", [
        ("luck", "attribute", "minecraft:luck", "add_value", 10.0, 200.0)]),
    ("wisdom", "Bilgelik", "Wisdom", [
        ("xp_percent", "xp_percent", "", "", 300.0, 500.0)]),
]
STAT_IDS = [s[0] for s in STATS]
# Referans oyuncunun puan dağılımı (bütçe denetimi): savaş stat'ları ağırlıklı, kalan beşi eşit pay.
REF_SPLIT = dict(vitality=0.30, endurance=0.25, strength=0.25, **{k: 0.04 for k in ("agility", "satiety", "precision", "fortune", "wisdom")})
# docs/06 §3: seviyenin bütçedeki payı (kalanı katalizör / kalp eşyası): can %40 ("seviye ≈ +80 / +194"), düz zırh ve hasar bonusunun yarısı.
LEVEL_SHARE = dict(hp=0.40, armor=0.50, damage=0.50)
BUDGET_TIERS = range(3, M.N)            # T0-T2 payları ≤ 4 can: gürültü; denetim T3'ten başlar
BUDGET_TOL = 0.25                       # hedefi AŞMA toleransı (göreli), mutlak payın (aşağıda) dışındaki kısım için
BUDGET_UNDER = 0.65                     # hedefin ALTINDA kalma toleransı: yavaşlatılmış seviye eğrisi (L50 ≈ 32 sa) geç kademelerde payı %40-60 altında bırakır (kasıtlı)
BUDGET_FLOOR = dict(hp=2.0, armor=1.5, damage=0.5)   # bu kadarlık mutlak fark sayılmaz: T3'te hedef 0,8 hasar iken ±%25 = ±0,2 ölçülemez ince
HTD_MAX = 30.0                          # sim.py MAX_HTD ile aynı: tüm tavanlar dolu ref oyuncu ≤ 30 ölüm vuruşu (docs/06 §1)

# ─── Tempo (varsayım, docs/10 Q66) ───────────────────────────────────────────────────────────────────────────────
KILLS_PER_HOUR = 90
CLASS_MIX = 1.4                         # ortalama öldürme XP'si / 'normal' sınıf tabanı
TIER_HOURS = [round(3 + 0.25 * p * p) for p in range(M.N)]   # kademe p'de geçen saat: 3 3 4 5 7 9 12 15 19 23 28 33 39 45 = 245 sa
ANCHORS = [(25, 10, 14), (50, 28, 36), (75, 60, 76), (100, 115, 145), (200, 330, 470)]    # (seviye, en erken saat, en geç saat): kullanıcı hedefi 2026-11-20 —
                                             # L25 ≈ 12 sa, L25→L50 ≈ 20 sa (L50 ≈ 32), L50→L75 ≈ 36 sa (L75 ≈ 68); sonrası aynı yönde ağırlaşır (artan zorluk, ortalama)
EPS = 1e-9


def bonus(effect, n):
    """Bir etkinin n puandaki değeri (yumuşak tavan)."""
    _id, _kind, _attr, _op, cap, half = effect
    return cap * n / (n + half) if n > 0 else 0.0


def effects_of(stat_id):
    return next(s[3] for s in STATS if s[0] == stat_id)


def effect_value(stat_id, effect_id, n):
    return bonus(next(e for e in effects_of(stat_id) if e[0] == effect_id), n)


def level_of(xp):
    lo, hi = 1, MAX_LEVEL
    while lo < hi:
        mid = (lo + hi + 1) // 2
        if M.xp_total(mid) <= xp:
            lo = mid
        else:
            hi = mid - 1
    return lo


# ─── Referans tempo ──────────────────────────────────────────────────────────────────────────────────────────────
def pace():
    """Kademe başına: başlangıç/bitiş saati, bitişte toplam XP ve seviye (saat saat toplar)."""
    rows, xp, t = [], 0.0, 0
    for p in range(M.N):
        per_hour = KILLS_PER_HOUR * CLASS_MIX * MB.XP_BASE["normal"] * MB.xp_mult(p)
        start = t
        xp += TIER_HOURS[p] * per_hour
        t += TIER_HOURS[p]
        rows.append(dict(tier=p, id=M.ARMOR[p][1], hour_start=start, hour_end=t, xp_per_kill=round(MB.XP_BASE["normal"] * MB.xp_mult(p), 1),
                         xp_end=round(xp), level_end=level_of(xp)))
    return rows


def hour_of_level(level):
    """Referans oyuncunun `level`e ulaştığı saat (saat içi doğrusal); ulaşılamazsa None."""
    need, xp, t = M.xp_total(level), 0.0, 0.0
    for p in range(M.N):
        per_hour = KILLS_PER_HOUR * CLASS_MIX * MB.XP_BASE["normal"] * MB.xp_mult(p)
        if xp + TIER_HOURS[p] * per_hour >= need:
            return t + (need - xp) / per_hour
        xp += TIER_HOURS[p] * per_hour
        t += TIER_HOURS[p]
    return t + (need - xp) / per_hour      # T13 bitti: oyuncu T13 hızında oynamaya devam eder (tempo tablosu açık uçlu)


# ─── Bütçe: referans oyuncunun seviye payı ──────────────────────────────────────────────────────────────────────
def ref_points(level, stat_id):
    return REF_SPLIT[stat_id] * POINTS_PER_LEVEL * (level - 1)


def budget_row(p, level):
    """Kademe p, referans seviye `level`: modelin verdiği seviye payı ile docs/06 §3 hedefi."""
    got = dict(hp=effect_value("vitality", "max_health", ref_points(level, "vitality")),
               armor=effect_value("endurance", "armor", ref_points(level, "endurance")),
               damage=effect_value("strength", "attack_damage", ref_points(level, "strength")))
    want = dict(hp=(M.exp_hp(p) - M.PLAYER_BASE_HP) * LEVEL_SHARE["hp"],
                armor=M.set_armor(p) * M.ref_bonus(p) * LEVEL_SHARE["armor"],
                damage=M.sword(p) * M.ref_bonus(p) * LEVEL_SHARE["damage"])
    return got, want


def budget_miss(got, want, key):
    """Hedeften göreli sapma; küçük mutlak farklar (BUDGET_FLOOR) sayılmaz. TEK YÖNLÜ DEĞİL: hedefi AŞMA toleransı BUDGET_TOL, hedefin ALTINDA kalma toleransı
    BUDGET_UNDER — seviye kasıtlı yavaşlatıldı (2026-11-20, kullanıcı), referans oyuncu geç kademelerde tasarım bütçesinin seviye payını DOLDURAMAZ; fazlası bu paydan değil
    katalizör/gear/mob zorluğundan gelir. Aşmak ise hâlâ hata (seviye bütçeyi ezemez)."""
    if want <= 0:
        return 0.0
    over = max(0.0, got - want - BUDGET_FLOOR[key]) / want
    under = max(0.0, want - got - BUDGET_FLOOR[key]) / want
    return max(over / BUDGET_TOL, under / BUDGET_UNDER) * BUDGET_TOL


def maxed_loadout(p=None):
    """Tüm stat tavanları dolu (seviye payı yerine tavan) ref oyuncu — HTD üst sınırı denetimi için."""
    p = M.N - 1 if p is None else p
    base = M.loadout(p)
    got, want = budget_row(p, 10_001)   # ~L10001: pratikte tavan (puan sayısından bağımsız: etki n/yarı oranına bağlı)
    cap = lambda sid, eid: next(e for e in effects_of(sid) if e[0] == eid)[4]
    return dict(base, hp=base["hp"] - want["hp"] + cap("vitality", "max_health"),
                armor=base["armor"] - want["armor"] + cap("endurance", "armor"),
                toughness=base["toughness"] + cap("endurance", "armor_toughness"),
                attack=base["attack"] - want["damage"] + cap("strength", "attack_damage"))


# ─── Çıktılar ────────────────────────────────────────────────────────────────────────────────────────────────────
CASE_LEVELS = (1, 2, 3, 4, 5, 10, 20, 50, 100, 199, 200, 201, 500, 1000, 5000, 9998, 9999)
CASE_POINTS = (1, 4, 40, 400, 4000, 39992)


def cases():
    xp = [dict(kind="xp", a=L, b=M.xp_total(L)) for L in CASE_LEVELS]
    lvl = [dict(kind="level", a=x, b=level_of(x)) for x in (0, 54, 55, 56, 252, 253, 254, 6_913, 6_914, 287_603, 287_604, 17_400_152_175, 17_400_152_176, 99_000_000_000)]
    bon = [dict(kind="bonus", a=f"{s[0]}.{e[0]}", b=n, c=round(bonus(e, n), 9)) for s in STATS for e in s[3] for n in CASE_POINTS]
    return xp, lvl, bon


def game_json():
    xp, lvl, bon = cases()
    return dict(
        leveling=dict(max_level=MAX_LEVEL, points_per_level=POINTS_PER_LEVEL, xp_anchors=[list(a) for a in XP_ANCHORS], xp_knee=XP_KNEE),
        respec=dict(item=RESPEC_ITEM, boss_chance=RESPEC_BOSS_CHANCE),
        stats=[dict(id=sid, effects=[dict(id=e[0], kind=e[1], attribute=e[2], op=e[3], cap=e[4], half=e[5]) for e in effs]) for sid, _tr, _en, effs in STATS],
        cases=dict(xp=[[c["a"], c["b"]] for c in xp], level=[[c["a"], c["b"]] for c in lvl], bonus=[[c["a"], c["b"], c["c"]] for c in bon]))


def outputs():
    stats_rows = [dict(stat=sid, tr=tr, en=en, effect=e[0], kind=e[1], attribute=e[2], op=e[3], cap=e[4], half=e[5], slope0=round(e[4] / e[5], 6))
                  for sid, tr, en, effs in STATS for e in effs]
    leveling = [dict(key=k, value=v, note=n) for k, v, n in (
        ("max_level", MAX_LEVEL, "ömür boyu seviye tavanı (karar D5)"), ("points_per_level", POINTS_PER_LEVEL, "seviye başına beceri puanı"),
        ("xp_anchors", json.dumps(XP_ANCHORS), "R(L): bağlı noktalar arası kuvvet yasası (L2 = 55 ham XP = vanilla 5. seviye)"),
        ("xp_knee", XP_KNEE, "L > knee: seviye başı maliyet L ile doğrusal"), ("respec_item", RESPEC_ITEM, "puanları sıfırlayan nadir eşya (Q6)"),
        ("respec_boss_chance", RESPEC_BOSS_CHANCE, "boss öldürmesinde düşme olasılığı (mob_drops.csv ile eşit)"),
        ("kills_per_hour", KILLS_PER_HOUR, "tempo varsayımı (Q66)"), ("class_mix", CLASS_MIX, "tempo varsayımı: ortalama XP / normal sınıf"),
        ("tier_hours", " ".join(map(str, TIER_HOURS)), "tempo varsayımı: kademe başına saat"))]
    pace_rows = []
    for r in pace():
        got, want = budget_row(r["tier"], r["level_end"])
        pace_rows.append(dict(r, hp_got=round(got["hp"], 1), hp_want=round(want["hp"], 1), armor_got=round(got["armor"], 1), armor_want=round(want["armor"], 1),
                              dmg_got=round(got["damage"], 2), dmg_want=round(want["damage"], 2)))
    return {STATS_CSV: M.render(list(stats_rows[0]), stats_rows), LEVELING_CSV: M.render(["key", "value", "note"], leveling),
            PACING_CSV: M.render(list(pace_rows[0]), pace_rows), GAME_JSON: M.dumps(game_json())}


# ─── Denetimler ──────────────────────────────────────────────────────────────────────────────────────────────────
def checks():
    out = []

    def add(name, ok, detail=""):
        out.append((name, bool(ok), detail))

    # Seviye eğrisi
    add("L1 = 0 XP, L2 = 55 ham XP (vanilla 5. seviye)", M.xp_total(1) == 0 and M.xp_total(2) == 55 == 5 * 5 + 6 * 5, f"L2 = {M.xp_total(2)}")
    add("XP eğrisi 1..9999 kesin artan", all(M.xp_total(L) < M.xp_total(L + 1) for L in range(1, MAX_LEVEL)))
    # Parçalı kuvvet yasasında bağlı noktada üs değişir; L100'de seviye başı maliyet %28 düşer (610 k → 441 k, kullanıcının L100/L200 noktaları, Q-notu). Uçurum (L200'de %99,75 düşüş olmuştu) kabul edilmez.
    add("seviye başı maliyet uçuruma düşmez (L3..9999: bir önceki adımın ≥ %60'ı; L200'de kırılma yok)",
        all(M.xp_total(L + 1) - M.xp_total(L) >= 0.6 * (M.xp_total(L) - M.xp_total(L - 1)) for L in range(3, MAX_LEVEL)),
        f"L199→200 = {M.xp_total(200) - M.xp_total(199):,}, L200→201 = {M.xp_total(201) - M.xp_total(200):,}; L99→100 = {M.xp_total(100) - M.xp_total(99):,}, L100→101 = {M.xp_total(101) - M.xp_total(100):,}")
    add("L9999 long'a sığar (yüzlerce milyar; pratikte ulaşılmaz)", 1e11 < M.xp_total(MAX_LEVEL) < 2 ** 62, f"{M.xp_total(MAX_LEVEL):,}")
    add("level_of: xp_total(L) → L ve bir eksiği → L−1", all(level_of(M.xp_total(L)) == L and level_of(M.xp_total(L) - 1) == L - 1 for L in (2, 3, 50, 200, 201, 1000, 9999)))
    add("toplam puan L9999'da = puan/seviye × 9998", max_points() == POINTS_PER_LEVEL * 9_998)
    add("sabitler tiers.xp_total ile aynı sayıları kullanıyor", (XP_ANCHORS, XP_KNEE) == (M.XP_ANCHORS, M.XP_KNEE) and XP_ANCHORS[0] == (2, 55))
    # Stat tablosu
    add("8 stat, kimlikler benzersiz, her birinin en az bir etkisi var", len(set(STAT_IDS)) == len(STAT_IDS) == 8 and all(s[3] for s in STATS))
    effects = [(s[0], e) for s in STATS for e in s[3]]
    add("etki kimlikleri stat içinde benzersiz, tavan ve yarı-nokta > 0", all(e[4] > 0 and e[5] > 0 for _, e in effects) and
        all(len({e[0] for e in s[3]}) == len(s[3]) for s in STATS))
    add("etki türü geçerli (attribute özniteliği ve işlemi dolu)", all(e[1] in ("attribute", "exhaustion", "xp_percent") and (e[1] != "attribute" or (e[2] and e[3] in ("add_value", "add_multiplied_base"))) for _, e in effects))
    add("yumuşak tavan: L9999'daki tüm puanlar bile tavanın altında, kesin artan, azalan getiri",
        all(bonus(e, max_points()) < e[4] and all(bonus(e, n) < bonus(e, n + 1) and
            bonus(e, n + 1) - bonus(e, n) < bonus(e, n) - bonus(e, n - 1) + EPS for n in (1, 10, 100, 1000, 9_000)) for _, e in effects))
    add("sonsuz puanda getiri kayboluyor: L9999'da son puan ilk puanın <%1'i", all((bonus(e, max_points()) - bonus(e, max_points() - 1)) < 0.01 * (bonus(e, 1) - bonus(e, 0)) for _, e in effects))
    add("tek stat'a yığmak bile tavanı aşmaz: L9999'da stat ≥ %88 tavanda (hiçbiri 'ölü' değil)", all(bonus(e, max_points()) >= 0.88 * e[4] for _, e in effects))
    add("kritik stat'ı ortak tavanların (agg_caps) altında kalır (kritik şans ≤ 0,75; hasar ≤ 2,0; yemek satürasyonu ≤ 12)",
        effects_of("precision")[0][4] <= 0.75 and effects_of("precision")[1][4] <= 2.0 and effects_of("satiety")[0][4] <= 12.0)
    # Bütçe (docs/06 §3) — tavan/yarı-nokta bu payı tutturmalı
    rows = pace()
    for key, label in (("hp", "can (Vitalite)"), ("armor", "düz zırh (Dayanıklılık)"), ("damage", "hasar (Güç)")):
        bad = []
        for p in BUDGET_TIERS:
            got, want = budget_row(p, rows[p]["level_end"])
            if budget_miss(got[key], want[key], key) > BUDGET_TOL:
                bad.append(f"T{p}: {got[key]:.1f} / {want[key]:.1f}")
        add(f"bütçe: {label} seviye payı T3..T13'te hedefi en çok %{int(BUDGET_TOL * 100)} aşıyor, en çok %{int(BUDGET_UNDER * 100)} altında kalıyor (±{BUDGET_FLOOR[key]:g} mutlak pay)", not bad, "; ".join(bad))
    # Tavanlar dolu oyuncu
    mx = maxed_loadout()
    htd = M.hits_to_die(mx, M.mob_dmg(M.N - 1))
    add(f"tüm seviye tavanları dolu T13 ref oyuncu ölüm vuruşu ≤ {HTD_MAX:g} (ref {M.hits_to_die(M.loadout(M.N - 1), M.mob_dmg(M.N - 1)):.1f})",
        htd <= HTD_MAX and htd > M.hits_to_die(M.loadout(M.N - 1), M.mob_dmg(M.N - 1)), f"HTD {htd:.1f}")
    # Tempo
    for level, lo, hi in ANCHORS:
        h = hour_of_level(level)
        add(f"tempo: L{level} {lo}–{hi} saat arasında (varsayımlara göre)", h is not None and lo <= h <= hi, f"{h:.0f} sa" if h is not None else "ulaşılamıyor")
    add("tempo: seviye kademeyle azalmıyor, T13 sonunda L120..L220", all(rows[i]["level_end"] <= rows[i + 1]["level_end"] for i in range(M.N - 1)) and 120 <= rows[-1]["level_end"] <= 220,
        f"T13 sonu L{rows[-1]['level_end']}")
    add("tempo: mob XP büyümesi mobs.py ile aynı (1,75) ve T13 normal öldürme 5K–15K XP", MB.XP_GROWTH == 1.75 and 5_000 <= MB.XP_BASE["normal"] * MB.xp_mult(M.N - 1) <= 15_000,
        f"{MB.XP_BASE['normal'] * MB.xp_mult(M.N - 1):.0f}")
    # Respec
    add("respec eşyası ve boss düşme olasılığı data/mob_drops.csv ile uyumlu", respec_drops_match(), f"mob_drops.csv: {respec_drops()}")
    # Dil anahtarları
    missing = missing_lang()
    add("her stat ve etki için en/tr dil anahtarı var", not missing, f"eksik: {missing[:6]}")
    return out


def respec_drops():
    path = ROOT / "data/mob_drops.csv"
    if not path.exists():
        return []
    return [(r["entity"], float(r["chance"])) for r in csv.DictReader(path.open(encoding="utf-8")) if r["item"] == RESPEC_ITEM]


def respec_drops_match():
    drops = respec_drops()
    return len(drops) >= 1 and all(abs(c - RESPEC_BOSS_CHANCE) < 1e-9 for _, c in drops)


def missing_lang():
    missing = []
    keys = [f"theoria.stat.{s[0]}" for s in STATS] + [f"theoria.stat.{s[0]}.desc" for s in STATS] + [f"item.{RESPEC_ITEM.replace(':', '.')}"]
    for name in ("en_us", "tr_tr"):
        path = LANG / f"{name}.json"
        have = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
        missing += [f"{name}:{k}" for k in keys if k not in have]
    return missing


def report():
    print(f"Seviye: tavan {MAX_LEVEL}, {POINTS_PER_LEVEL} puan/seviye, L2 = {M.xp_total(2)} XP, L9999 = {M.xp_total(MAX_LEVEL):,} XP (xp_growth {MB.XP_GROWTH})")
    print("\nSTAT ETKİLERİ — f(n) = tavan·n/(n+yarı) · ilk puan eğimi = tavan/yarı · değerler n puanda")
    print(f"{'stat':11} {'etki':18} {'tavan':>8} {'yarı':>7} {'eğim':>9} " + " ".join(f"{n:>9}" for n in (1, 10, 100, 1000, max_points())))
    for sid, _tr, _en, effs in STATS:
        for e in effs:
            print(f"{sid:11} {e[0]:18} {e[4]:>8g} {e[5]:>7g} {e[4] / e[5]:>9.4f} " + " ".join(f"{bonus(e, n):>9.3f}" for n in (1, 10, 100, 1000, max_points())))
    print("\nTEMPO + BÜTÇE — referans oyuncu her kademenin sonunda (varsayım: "
          f"{KILLS_PER_HOUR} öldürme/sa × {CLASS_MIX} sınıf karışımı; Bilgelik yok sayıldı)")
    print(f"{'kdm':>3} {'saat':>9} {'XP/öldürme':>11} {'seviye':>7} {'can pay':>14} {'zırh pay':>14} {'hasar pay':>14}")
    for r in pace():
        got, want = budget_row(r["tier"], r["level_end"])
        print(f"T{r['tier']:<2} {r['hour_start']:>3}-{r['hour_end']:<5} {r['xp_per_kill']:>11,.0f} {r['level_end']:>7} "
              f"{got['hp']:>6.1f}/{want['hp']:<6.1f} {got['armor']:>6.1f}/{want['armor']:<6.1f} {got['damage']:>6.2f}/{want['damage']:<6.2f}")
    for level in (10, 25, 50, 100, 200, 500, 1000):
        h = hour_of_level(level)
        print(f"  L{level}: " + (f"{h:.0f} saat" if h is not None else "T13 sonunda bile ulaşılmaz"))
    mx = maxed_loadout()
    print(f"\nT13 ref HTD {M.hits_to_die(M.loadout(M.N - 1), M.mob_dmg(M.N - 1)):.1f} → tüm seviye tavanları dolu HTD {M.hits_to_die(mx, M.mob_dmg(M.N - 1)):.1f} (üst sınır {HTD_MAX:g})")
    total = 10_000
    print(f"Eşit dağılımla L9999 (≈{total // 8} puan/stat): " + ", ".join(f"{e[0]} {bonus(e, total // 8):.2f}/{e[4]:g}" for _, _, _, effs in STATS for e in effs))


def negative():
    """Denetimlerin gerçekten kırmızıya döndüğünü kanıtlar."""
    g = globals()
    base_stats = [(sid, tr, en, list(effs)) for sid, tr, en, effs in STATS]
    cases_ = [("Vitalite tavanı ×3 (bütçe taşar)", "STATS", [(s[0], s[1], s[2], [(e[0], e[1], e[2], e[3], e[4] * 3 if e[0] == "max_health" else e[4], e[5]) for e in s[3]]) for s in STATS]),
              ("Güç yarı-noktası 10 (hızlı doyum)", "STATS", [(s[0], s[1], s[2], [(e[0], e[1], e[2], e[3], e[4], 10.0 if e[0] == "attack_damage" else e[5]) for e in s[3]]) for s in STATS]),
              ("9 stat (biri çift)", "STATS", STATS + [STATS[0]]),
              ("stat tavanı 0", "STATS", [(s[0], s[1], s[2], [(e[0], e[1], e[2], e[3], 0.0 if e[0] == "luck" else e[4], e[5]) for e in s[3]]) for s in STATS]),
              ("seviye başı 32 puan", "POINTS_PER_LEVEL", 32),
              ("saatte 600 öldürme (tempo çok hızlı)", "KILLS_PER_HOUR", 600),
              ("L9999 yerine 5000 tavan", "MAX_LEVEL", 5000)]
    green = 0
    for name, key, bad in cases_:
        old = g[key]
        g[key] = bad
        g["STAT_IDS"] = [s[0] for s in g["STATS"]]
        try:
            red = [n for n, ok, _ in checks() if not ok]
        except Exception as exc:     # çökmek de "yakalandı" sayılır (geçersiz tablo)
            red = [f"istisna: {exc.__class__.__name__}"]
        finally:
            g[key] = old
            g["STAT_IDS"] = [s[0] for s in base_stats]
        print(f"  {'✅ kırmızı' if red else '❌ YEŞİL KALDI'}: {name} → {len(red)} denetim düştü")
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
                print(f"  {'✅' if ok else '❌'} {name}" + (f" — {detail}" if detail and (not ok or "--report" in argv) else ""))
    for p in (stale if check else []):
        print(f"BAYAT: {p.relative_to(ROOT)} → python3 -I tools/balance/stats.py")
    bad = len(failed) + (len(stale) if check else 0)
    print("OK" if not bad else f"HATA ({bad})")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
