#!/usr/bin/env python3
"""Theoria denge modeli — TEK KAYNAK. Faz 2a'da (2026-11-10) tools/balance/sim.py ile kilitlendi.

Bu dosyadaki sabitler ve formüller her sayının kaynağıdır. Üretilenler:
    data/balance/tiers.csv    kademe satırları (zırh, sertlik, silah, mob çarpanları, referans bütçe)
    data/balance/combat.csv   çalışma anı katsayıları (hasar azaltma, büyü koruması ölçeği, attribute teknik tavanları)
  Faz 6 — gear (generator/gear.py bunları okur; oyuna giden sayılar theoria/gear.json'a oradan akar):
    data/balance/tools.csv    alet/silah kademesi × tür: hasar, vuruş hızı, dayanıklılık, kazı hızı
    data/balance/ores.csv     kazı kapıları: cevher → gereken alet idx, sertlik, blok listesi
  Faz 2b — oyunun okuduğu biçim (aynı sayılar; elle düzenlenmez):
    mods-src/theoria/src/main/resources/data/theoria/balance/combat/default.json   datapack: hasar azaltma katsayıları
    mods-src/theoria/src/main/resources/data/theoria/balance/tier/<id>.json        datapack: kademe başına bir dosya
    mods-src/theoria/src/main/resources/theoria/technical_caps.json                jar'a gömülü: attribute teknik tavanları (ADR-003 Karar 2)
Çalıştır:
    python3 -I tools/balance/tiers.py            # CSV'leri ve oyun JSON'larını yeniden yazar
    python3 -I tools/balance/tiers.py --check    # yazmaz; dosyalar güncel değilse çıkış 1
    python3 -I tools/balance/tiers.py --md | --ores | --tools | --xp   # docs tabloları
Bir sayıyı değiştirdikten sonra: tiers.py → sim.py --cases → sim.py (bantlar yeşil mi) → docs/06 tablosu.
"""
import csv, io, json, math, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
TIERS_CSV = ROOT / "data/balance/tiers.csv"
COMBAT_CSV = ROOT / "data/balance/combat.csv"
TOOLS_CSV = ROOT / "data/balance/tools.csv"
ORES_CSV = ROOT / "data/balance/ores.csv"
RES = ROOT / "mods-src/theoria/src/main/resources"
COMBAT_JSON = RES / "data/theoria/balance/combat/default.json"
TIER_DIR = RES / "data/theoria/balance/tier"
CAPS_JSON = RES / "theoria/technical_caps.json"

# idx, id, TR ad, göğüslük zırhı (kullanıcının dizisi 1..82; 98-118-142 genişletme: son adımların ≈ +%20 büyümesi sürer), kaynak
ARMOR = [
    (0,  "leather",    "Deri",        1,   "inek/at derisi"),
    (1,  "tin",        "Kalay",       3,   "YENI maden: Kalay Cevheri"),
    (2,  "copper",     "Bakir",       6,   "vanilla bakir"),
    (3,  "bronze",     "Bronz",       9,   "alasim: Kalay + Bakir (yeni maden yok)"),
    (4,  "iron",       "Demir",       14,  "vanilla demir"),
    (5,  "steel",      "Celik",       24,  "alasim: Demir + Karbon Tozu (kurt/iskelet drop'u)"),
    (6,  "gold",       "Altin",       32,  "vanilla altin (guclendirilmis)"),
    (7,  "cobalt",     "Kobalt",      46,  "YENI maden: Kobalt Cevheri (deepslate)"),
    (8,  "diamond",    "Elmas",       59,  "vanilla elmas"),
    (9,  "titanium",   "Titanyum",    67,  "YENI maden: Titanyum Cevheri (dag tepeleri/cekirdek)"),
    (10, "lonsdaleit", "Lonsdaleit",  82,  "YENI: sikistirilmis karbon; meteor kraterleri + Elmas+Karbon sentezi"),
    (11, "mithril",    "Mitril",      98,  "YENI maden: Mitril Cevheri (derin/ucurum)"),
    (12, "orichalcum", "Orikalkum",   118, "YENI maden: Orikalkum (yuzen adalar/nadir)"),
    (13, "platinum",   "Platin",      142, "YENI maden: Platin (en derin/en nadir)"),
]
N = len(ARMOR)
# alet/silah merdiveni: (idx_gucu, id)  odun/tas armor idx'te yok -> sahte idx
TOOLS = [(0,"wood"),(1,"stone"),(2,"copper"),(4,"iron"),(6,"gold"),(7,"cobalt"),
         (8,"diamond"),(10,"lonsdaleit"),(11,"mithril"),(12,"orichalcum"),(13,"platinum")]
SLOT = {"helmet":0.15,"chestplate":0.40,"leggings":0.30,"boots":0.15}

# ─── Hasar azaltma (ADR-003; sim.py --fit ile seçildi) ────────────────────────────────────────────────────────────
#   tau  = TOUGHNESS_HALF / (TOUGHNESS_HALF + T)                  T = armor_toughness
#   oran = min(ARMOR_CAP, A / (A + ARMOR_K1 + ARMOR_K2 * H * tau))  A = floor(armor), H = zırh öncesi hasar
ARMOR_K1 = 30          # küçük vuruşlara karşı yarı-nokta (H → 0 iken A = 30 → %50)
ARMOR_K2 = 8           # vuruş büyüklüğü terimi: kabaca "zırh, yenen vuruşun 8 katıysa ≈ %50"
ARMOR_CAP = 0.90       # zırh azaltması tavanı
TOUGHNESS_HALF = 8     # T = 8 büyük-vuruş cezasını yarılar (vanilla'daki 2 + T/4 ile aynı biçim)
# Büyü koruması: vanilla EPF puanı başına %4 (≤ 20 puan = %80). Bizde vanilla azaltması bu çarpanla ölçeklenir → %2/puan, ≤ %40.
ENCH_SCALE = 0.5
EPF_STEP, EPF_MAX = 0.04, 20        # vanilla CombatRules.getDamageAfterMagicAbsorb
RESIST_STEP = 0.20                  # vanilla Direnç efekti / seviye (dokunulmaz)
# Attribute teknik tavanları (denge değeri DEĞİL; jar'a gömülü okunur — ADR-003 Karar 2)
ATTR_MAX = {"armor": 1_000_000, "armor_toughness": 1_000, "max_health": 1_000_000, "attack_damage": 1_000_000}

# ─── Kademe eğrileri ─────────────────────────────────────────────────────────────────────────────────────────────
PLAYER_BASE_HP = 20                 # vanilla
MOB_BASE_HP, MOB_BASE_DMG, MOB_BASE_ARMOR = 20, 3, 2   # "zombi sınıfı" = vanilla zombi (Normal zorluk)
G_SWORD, G_TDUR, G_ADUR = 1.30, 1.50, 1.50
G_MOB_HP, G_MOB_DMG, G_EXP_HP = 1.33, 1.31, 1.20
TOUGH_FROM, TOUGH_STEP = 8, 2       # takım sertliği = 2·(idx−8): titanyum 2 … platin 10
# Referans ("beklenen") oyuncunun bütçesi — Faz 7 (katalizör) + Faz 10 (seviye) toplamı bunu tutturmalı.
REF_BONUS, REF_BONUS_RAMP = 0.25, 4 # düz zırh ve hasar bonusu = takımın/kılıcın %25'i; T0'da 0, T4'te tam
REF_EPF_FROM, REF_EPF_STEP, REF_EPF_MAX = 3, 2, 16      # EPF = 2·(idx−3), en çok 16 (Koruma IV × 4)


def sword(p):   return round(4.0 * G_SWORD ** p, 1)     # saldırı hasarı (tam şarj, kritiksiz)
def tdur(p):    return round(80 * G_TDUR ** p)          # alet dayanıklılığı
def adur(p):    return round(80 * G_ADUR ** p)          # göğüslük dayanıklılığı
def tough(p):   return TOUGH_STEP * max(0, p - TOUGH_FROM)
def mob_hp_mult(p):     return round(G_MOB_HP ** p, 4)  # MAX_HEALTH çarpanı
def mob_dmg_mult(p):    return round(G_MOB_DMG ** p, 4) # zırhla azaltılabilen hasar + mob zırhı çarpanı
def exp_hp(p):  return round(PLAYER_BASE_HP * G_EXP_HP ** p)   # beklenen oyuncu canı (taban + seviye + katalizör + kalp)
def mob_bypass_mult(p): return round(exp_hp(p) / PLAYER_BASE_HP, 4)   # zırhı delen hasar (büyü…) can bütçesiyle büyür
def mob_hp(p):  return MOB_BASE_HP * mob_hp_mult(p)
def mob_dmg(p): return MOB_BASE_DMG * mob_dmg_mult(p)
def ref_bonus(p): return REF_BONUS * min(1.0, p / REF_BONUS_RAMP)
def ref_epf(p):   return max(0, min(REF_EPF_MAX, REF_EPF_STEP * (p - REF_EPF_FROM)))


def pieces(p):
    """(kask, göğüs, pantolon, bot). Paylar vanilla elmas oranı; her parça en az 1 zırh verir."""
    chest = ARMOR[p][3]
    total = chest / SLOT["chestplate"]
    return (max(1, round(total * SLOT["helmet"])), chest,
            max(1, round(total * SLOT["leggings"])), max(1, round(total * SLOT["boots"])))


def set_armor(p): return sum(pieces(p))


def dr(armor, hit, toughness=0.0):
    """Zırh azaltma oranı. armor: oyundaki getArmorValue() gibi aşağı yuvarlanır."""
    armor = math.floor(armor)
    if armor <= 0 or hit <= 0:
        return 0.0
    tau = TOUGHNESS_HALF / (TOUGHNESS_HALF + max(0.0, toughness))
    return min(ARMOR_CAP, armor / (armor + ARMOR_K1 + ARMOR_K2 * hit * tau))


def ench_factor(epf):       return 1.0 - min(max(epf, 0), EPF_MAX) * EPF_STEP * ENCH_SCALE
def resist_factor(level):   return max(0.0, 1.0 - RESIST_STEP * level)


def damage_taken(hit, armor=0, toughness=0.0, epf=0, resistance=0, bypass_armor=False):
    """Oyundaki sırayla: zırh → Direnç → büyü koruması. (after_armor, final) döndürür."""
    after_armor = hit if bypass_armor else hit * (1.0 - dr(armor, hit, toughness))
    return after_armor, after_armor * resist_factor(resistance) * ench_factor(epf)


def loadout(p, kind="ref"):
    """Kademe p'deki oyuncu arketipi: min (az yatırım) · ref (beklenen bütçe) · max (çok yatırım)."""
    base = dict(armor=set_armor(p), toughness=tough(p), hp=float(exp_hp(p)), epf=0, attack=sword(p))
    if kind == "min":      # yalnızca kademe takımı; can bütçesinin yarısı; büyü yok
        base.update(hp=PLAYER_BASE_HP + 0.5 * (exp_hp(p) - PLAYER_BASE_HP))
    elif kind == "ref":
        b = ref_bonus(p)
        base.update(armor=math.floor(set_armor(p) * (1 + b)), epf=ref_epf(p), attack=sword(p) * (1 + b))
    elif kind == "max":    # zırh ve hasar bonusu %100, +8 sertlik, can ×1,5, EPF 20
        base.update(armor=2 * set_armor(p), toughness=tough(p) + 8, hp=1.5 * exp_hp(p), epf=EPF_MAX, attack=2 * sword(p))
    else:
        raise ValueError(kind)
    return base


def hits_to_die(player, hit, bypass_armor=False):
    return player["hp"] / damage_taken(hit, player["armor"], player["toughness"], player["epf"], 0, bypass_armor)[1]


def hits_to_kill(player, p, hp_base=MOB_BASE_HP, armor_base=MOB_BASE_ARMOR):
    """Kademe p'deki mob'u (vanilla taban can/zırh × kademe çarpanı) kaç vuruşta öldürür."""
    armor = math.floor(armor_base * mob_dmg_mult(p))
    return hp_base * mob_hp_mult(p) / damage_taken(player["attack"], armor)[0]


# --- Kazi kapilari (docs/05 §7): blok -> gereken ALET idx'i (TOOLS tablosundaki idx) + sertlik ---
# alet idx: odun0 tas1 bakir2 demir4 altin6 kobalt7 elmas8 lonsdaleit10 mitril11 orikalkum12 platin13
# id, TR, min_alet_idx, taban_sertlik_carpani (None = vanilla sertligi korunur), bloklar (deepslate varyanti sertligi x1,5: vanilla orani)
# Obsidyen/Antik Enkaz: docs/05 §7 tablosundaki 9,0 sertlik KALDIRILDI (Faz 6): vanilla 50/30 kritik yapi bloklaridir (portal, kitlesel
# yikim); 9,0 elmas kazmayla 1 sn'den kisa surerdi. Kapi (alet idx 8) aynen duruyor, sertlik vanilla.
ORES = [
    ("coal", "Komur", 0, 1.0, ("minecraft:coal_ore", "minecraft:deepslate_coal_ore")),
    ("tin", "Kalay", 1, 1.0, ("theoria:tin_ore", "theoria:deepslate_tin_ore")),
    ("copper", "Bakir", 1, 1.1, ("minecraft:copper_ore", "minecraft:deepslate_copper_ore")),
    ("iron", "Demir", 2, 1.2, ("minecraft:iron_ore", "minecraft:deepslate_iron_ore")),
    ("redstone", "Kirmizitas", 2, 1.2, ("minecraft:redstone_ore", "minecraft:deepslate_redstone_ore")),
    ("lapis", "Lapis", 2, 1.2, ("minecraft:lapis_ore", "minecraft:deepslate_lapis_ore")),
    ("gold", "Altin", 4, 1.3, ("minecraft:gold_ore", "minecraft:deepslate_gold_ore")),
    ("cobalt", "Kobalt", 4, 1.5, ("theoria:deepslate_cobalt_ore",)),
    ("diamond", "Elmas", 7, 1.8, ("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore")),
    ("obsidian", "Obsidyen", 8, None, ("minecraft:obsidian",)),
    ("ancient_debris", "Antik Enkaz", 8, None, ("minecraft:ancient_debris",)),
    ("titanium", "Titanyum", 8, 2.2, ("theoria:titanium_ore",)),
    ("lonsdaleit", "Lonsdaleit", 8, 2.6, ("theoria:lonsdaleit_ore",)),
    ("mithril", "Mitril", 10, 3.2, ("theoria:deepslate_mithril_ore",)),
    ("orichalcum", "Orikalkum", 11, 3.8, ("theoria:orichalcum_ore",)),
    ("platinum", "Platin", 12, 4.5, ("theoria:deepslate_platinum_ore",)),
]
DEEPSLATE_HARDNESS = 1.5   # vanilla: deepslate cevheri 4,5 = tas cevheri 3,0 x 1,5
def tool_speed(idx): return round(2.0 * 1.28 ** idx, 1)   # kazi hizi (vanilla odun 2, elmas 8)
def block_hardness(mult): return None if mult is None else round(3.0 * mult, 1)   # vanilla cevher ~3

# --- Alet/silah turleri (Faz 6). Hasar = kilic hasarinin orani, TOPLAM (tooltip'te gorunen; oyuncunun taban 1'i dahil);
#     hiz = saniyede vurus. Oranlar vanilla elmas/demir setinden: balta 1,5x yavas ama agir, kazma/kurek hafif, capa neredeyse silah degil. ---
TOOL_KINDS = [  # tur, hasar orani, vurus hizi/sn (None = mizragin profilinden)
    ("sword", 1.00, 1.6), ("axe", 1.50, 1.0), ("pickaxe", 0.65, 1.2), ("shovel", 0.75, 1.0), ("hoe", 0.25, 2.0), ("spear", 0.60, None),
]
# Mizrak profili (Item.Properties#spear parametreleri; vanilla degerleri): ilk deger saldiri suresi (sn) -> hiz = 1/sure.
# Yeni kademeler en yakin vanilla profilinin degerini kullanir (kobalt = demir, lonsdaleit = elmas, ust kademeler = netherit seviyesi).
SPEAR = {
    "wood": (0.65, 0.70, 0.75, 5.0, 14.0, 10.0, 5.1, 15.0, 4.6), "stone": (0.75, 0.82, 0.70, 4.5, 13.0, 9.0, 5.1, 13.75, 4.6),
    "copper": (0.85, 0.82, 0.65, 4.0, 12.0, 8.25, 5.1, 12.5, 4.6), "iron": (0.95, 0.95, 0.60, 2.5, 11.0, 6.75, 5.1, 11.25, 4.6),
    "gold": (0.95, 0.70, 0.70, 3.5, 13.0, 8.5, 5.1, 13.75, 4.6), "cobalt": (0.95, 0.95, 0.60, 2.5, 11.0, 6.75, 5.1, 11.25, 4.6),
    "diamond": (1.05, 1.075, 0.50, 3.0, 10.0, 6.5, 5.1, 10.0, 4.6), "lonsdaleit": (1.05, 1.075, 0.50, 3.0, 10.0, 6.5, 5.1, 10.0, 4.6),
    "mithril": (1.15, 1.20, 0.40, 2.5, 9.0, 5.5, 5.1, 8.75, 4.6), "orichalcum": (1.15, 1.20, 0.40, 2.5, 9.0, 5.5, 5.1, 8.75, 4.6),
    "platinum": (1.15, 1.20, 0.40, 2.5, 9.0, 5.5, 5.1, 8.75, 4.6),
}


def tool_damage(idx, ratio): return max(1.0, round(ratio * sword(idx), 1))   # toplam saldiri hasari (oyuncunun taban 1'i dahil)
def tool_speed_attack(tool_id, speed): return round(1.0 / SPEAR[tool_id][0], 3) if speed is None else speed

TOOLS_FIELDS = "tool,idx,kind,damage,speed,durability,mining_speed".split(",")
ORES_FIELDS = "ore,tr,tool_idx,tool,hardness,blocks".split(",")


def tool_rows():
    return [dict(tool=tid, idx=idx, kind=kind, damage=tool_damage(idx, ratio), speed=tool_speed_attack(tid, speed),
                 durability=tdur(idx), mining_speed=tool_speed(idx) if kind in ("pickaxe", "axe", "shovel", "hoe") else "")
            for idx, tid in TOOLS for kind, ratio, speed in TOOL_KINDS]


def ore_rows():
    names = {i: t for i, t in TOOLS}
    return [dict(ore=o, tr=tr, tool_idx=m, tool=names[m] if m in names else "", hardness="" if h is None else block_hardness(h),
                 blocks=" ".join(blocks)) for o, tr, m, h, blocks in ORES]


# --- Seviye XP egrisi (2026-11-20, kullanici: "L50 ~38 saat cok hizli"): L25 ~12 sa, L50 ~32 sa, L75 ~68 sa (stats.py ANCHORS denetler) ---
# Parcali kuvvet yasasi: iki bagli nokta arasinda R(L) = Xa * ((L-1)/(La-1))^e, e = ln(Xb/Xa)/ln((Lb-1)/(La-1)). Son noktadan (KNEE) sonra bir seviyenin maliyeti L ile dogrusal artar.
# Degerler tempo modelinden (stats.py pace(): saatte 90 oldurme x 1,4 sinif karisimi, TIER_HOURS) turetildi; L2 = 55 ham XP (vanilla 5. seviye) korunur.
XP_ANCHORS = [(2, 55), (25, 19_700), (50, 182_000), (75, 1_410_000), (100, 9_490_000), (200, 222_000_000)]
XP_KNEE = XP_ANCHORS[-1][0]


def _poly(L):
    """R(L) for 2 <= L <= KNEE (float)."""
    for (la, xa), (lb, xb) in zip(XP_ANCHORS, XP_ANCHORS[1:]):
        if L <= lb:
            e = math.log(xb / xa) / math.log((lb - 1) / (la - 1))
            return xa * ((L - 1) / (la - 1)) ** e
    raise ValueError(L)


def xp_total(L):
    if L <= 1: return 0
    if L <= XP_KNEE: return round(_poly(L))
    per_level = _poly(XP_KNEE) - _poly(XP_KNEE - 1)      # cost of the last tabulated level
    return round(_poly(XP_KNEE) + per_level / (2 * XP_KNEE) * (L * L - XP_KNEE * XP_KNEE))   # base = R(KNEE) (Faz 13: was R(KNEE-1) -> L200->L201 cost 12k instead of ~5M)


FIELDS = ("idx,id,tr,helm,chest,legs,boots,total,tough,adur,sword,tdur,mob_hp_mult,mob_dmg_mult,mob_bypass_mult,"
          "mobhp,mobdmg,exphp,ref_bonus,ref_epf,dr,hits,kills,src").split(",")


def rows():
    out = []
    for idx, id_, tr, chest, src in ARMOR:
        helm, _, legs, boots = pieces(idx)
        ref = loadout(idx)
        out.append(dict(
            idx=idx, id=id_, tr=tr, helm=helm, chest=chest, legs=legs, boots=boots, total=set_armor(idx),
            tough=tough(idx), adur=adur(idx), sword=sword(idx), tdur=tdur(idx),
            mob_hp_mult=mob_hp_mult(idx), mob_dmg_mult=mob_dmg_mult(idx), mob_bypass_mult=mob_bypass_mult(idx),
            mobhp=round(mob_hp(idx)), mobdmg=round(mob_dmg(idx), 1), exphp=exp_hp(idx),
            ref_bonus=round(ref_bonus(idx), 4), ref_epf=ref_epf(idx),
            dr=round(dr(ref["armor"], mob_dmg(idx), ref["toughness"]) * 100),       # ref oyuncu, eşit kademe zombi sınıfı
            hits=round(hits_to_die(ref, mob_dmg(idx)), 1), kills=round(hits_to_kill(ref, idx), 1), src=src))
    return out


COMBAT = [  # key, value, açıklama
    ("armor_k1", ARMOR_K1, "oran = min(cap, A / (A + k1 + k2*H*tau))"),
    ("armor_k2", ARMOR_K2, "H = zirh oncesi hasar (ADR-003 tanimi)"),
    ("armor_cap", ARMOR_CAP, "zirh azaltma tavani"),
    ("toughness_half", TOUGHNESS_HALF, "tau = half / (half + armor_toughness)"),
    ("ench_scale", ENCH_SCALE, "Reduction.ENCHANTMENTS: vanilla azaltma * bu carpan (EPF basi %2; en cok %40)"),
    ("attr_max_armor", ATTR_MAX["armor"], "teknik tavan (denge degeri degil)"),
    ("attr_max_armor_toughness", ATTR_MAX["armor_toughness"], "teknik tavan"),
    ("attr_max_health", ATTR_MAX["max_health"], "teknik tavan"),
    ("attr_max_attack_damage", ATTR_MAX["attack_damage"], "teknik tavan"),
]


def render(fieldnames, dict_rows):
    buf = io.StringIO()
    w = csv.DictWriter(buf, fieldnames=fieldnames, lineterminator="\n")
    w.writeheader(); w.writerows(dict_rows)
    return buf.getvalue()


def dumps(obj):
    return json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


def tier_json(r):
    """Bir kademenin oyunda okunan biçimi (dev.theoria.balance.Tier codec'i ile birebir)."""
    return dict(id=r["id"], idx=r["idx"], helmet=r["helm"], chestplate=r["chest"], leggings=r["legs"], boots=r["boots"],
                toughness=r["tough"], armor_durability=r["adur"], sword_damage=r["sword"], tool_durability=r["tdur"],
                mob_hp_mult=r["mob_hp_mult"], mob_dmg_mult=r["mob_dmg_mult"], mob_bypass_mult=r["mob_bypass_mult"],
                ref_health=r["exphp"], ref_bonus=r["ref_bonus"], ref_epf=r["ref_epf"])


def outputs():
    files = {TIERS_CSV: render(FIELDS, rows()),
             TOOLS_CSV: render(TOOLS_FIELDS, tool_rows()),
             ORES_CSV: render(ORES_FIELDS, ore_rows()),
             COMBAT_CSV: render(["key", "value", "note"], [dict(key=k, value=v, note=n) for k, v, n in COMBAT]),
             COMBAT_JSON: dumps({k: v for k, v, _ in COMBAT if not k.startswith("attr_max_")}),
             CAPS_JSON: dumps({k.removeprefix("attr_max_"): v for k, v, _ in COMBAT if k.startswith("attr_max_")})}
    for r in rows():
        files[TIER_DIR / f"{r['id']}.json"] = dumps(tier_json(r))
    return files


def sync(files, check):
    """check=False: dosyaları yazar, üretilmeyen kademe JSON'larını siler. check=True: yazmaz; bayat/artık dosyaları döndürür."""
    stale = [p for p, text in files.items() if not p.exists() or p.read_text(encoding="utf-8") != text]
    orphans = [p for p in sorted(TIER_DIR.glob("*.json")) if p not in files] if COMBAT_JSON in files else []
    if not check:
        for p in stale:
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(files[p], encoding="utf-8")
        for p in orphans:
            p.unlink()
    return stale + orphans


def main(argv):
    stale = sync(outputs(), "--check" in argv)
    if "--check" in argv:
        for p in stale:
            print(f"BAYAT: {p.relative_to(ROOT)} → python3 -I tools/balance/tiers.py")
        return 1 if stale else 0
    if "--ores" in argv:
        print("| Blok | Gereken alet idx | Sertlik | Gereken en dusuk alet |")
        for r in ore_rows(): print(f"| {r['tr']} | {r['tool_idx']} | {r['hardness'] or 'vanilla'} | {r['tool'] or '-'} |")
        print("| alet | idx | kazi hizi |")
        for i,t in TOOLS: print(f"| {t} | {i} | {tool_speed(i)} |")
    if "--tools" in argv:
        print("| alet | idx | " + " | ".join(k for k, _, _ in TOOL_KINDS) + " | dayaniklilik | kazi hizi |")
        for idx, tid in TOOLS:
            print(f"| {tid} | {idx} | " + " | ".join(f"{tool_damage(idx, r)} @{tool_speed_attack(tid, sp)}" for _, r, sp in TOOL_KINDS) + f" | {tdur(idx)} | {tool_speed(idx)} |")
    if "--xp" in argv:
        for L in (2,3,5,10,20,30,50,100,200,300,500,1000,2500,5000,9999): print(f"| {L} | {xp_total(L):,} | {4*(L-1):,} |")
    if "--md" in argv:
        print("| idx | Kademe | Kask | Göğüs | Pantolon | Bot | Takım | Sertlik | Zırh dayan. (göğüs) | Kılıç | Alet dayan. | Mob can × | Mob hasar × | Delici × | Zombi sınıfı can / hasar | Ref can | Ref azaltma | Ölüm vuruşu | Öldürme vuruşu |")
        print("|--:|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|")
        for r in rows():
            print(f"| {r['idx']} | {r['tr']} | {r['helm']} | {r['chest']} | {r['legs']} | {r['boots']} | {r['total']} | {r['tough']} | {r['adur']} | {r['sword']} | {r['tdur']} "
                  f"| {r['mob_hp_mult']:.2f} | {r['mob_dmg_mult']:.2f} | {r['mob_bypass_mult']:.2f} | {r['mobhp']} / {r['mobdmg']} | {r['exphp']} | %{r['dr']} | {r['hits']} | {r['kills']} |")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
