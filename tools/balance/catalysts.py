#!/usr/bin/env python3
"""Theoria Kataliz Masası modeli — Faz 7a (2026-11-11). Sayıların TEK KAYNAĞI (tiers.py ile birlikte); tasarım: docs/adr/ADR-006.

Katalizör değeri = cap × EĞRİ[seviye] × taban(ev sahibi kademesi). Taban kademeyle büyür; böylece "katalizör" kademe
merdivenine yapışık kalır ve docs/06 §3 referans bütçesini (ref_bonus yarısı katalizör, ref_epf, Kalp payı) tutturur.

Üretilenler (elle düzenlenmez):
    data/balance/catalysts.csv         32 katalizör: tür, hedef, nitelik/etki, taban, cap, min kademe, imza malzeme
    data/balance/catalyst_values.csv   katalizör × hedef × kademe × seviye I-V değerleri (oyun-içi testlerin beklenen değerleri)
    data/balance/catalyst_rules.csv    yuva kuralı, seviye eğrisi, maliyet merdiveni, Netherite, Bilgelik, teknik tavanlar
    data/balance/enchant_caps.csv      Netherite'in büyü başına en çok eklediği seviye
    data/balance/innate.csv            öntanımlı özellikler (Q34): malzeme × ev sahibi sınıfı → etkin sanal büyü
    mods-src/theoria/src/main/resources/data/theoria/balance/catalysts/default.json   oyunun okuduğu biçim (7b)
Çalıştır:
    python3 -I tools/balance/catalysts.py            # dosyaları yeniden yazar
    python3 -I tools/balance/catalysts.py --check    # yazmaz; dosyalar bayatsa ya da bant kırmızıysa çıkış 1 (check_data.py bunu koşar)
    python3 -I tools/balance/catalysts.py --break    # negatif test: bozulmuş sabitlerle denetimler kırmızıya dönmeli
    python3 -I tools/balance/catalysts.py --report   # bütçe + bant tabloları + Hız/Kritik/Buz örneği
"""
import itertools, json, math, pathlib, sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))   # -I altında kendi klasörümüz yolda değil
import tiers as M

ROOT = M.ROOT
BAL = ROOT / "data/balance"
CATALYSTS_CSV, VALUES_CSV, RULES_CSV = BAL / "catalysts.csv", BAL / "catalyst_values.csv", BAL / "catalyst_rules.csv"
CAPS_CSV, INNATE_CSV = BAL / "enchant_caps.csv", BAL / "innate.csv"
GAME_JSON = M.RES / "data/theoria/balance/catalysts/default.json"

# ─── Genel kurallar (ADR-006 Karar 3-6) ───────────────────────────────────────────────────────────────────────────
LEVELS = 5
CURVE = (0.2, 0.4, 0.6, 0.8, 1.0)       # seviye L değeri = cap × CURVE[L-1]; V = cap
SLOT_BASE, SLOT_STEP, SLOT_MAX = 1, 4, 4  # yuva = min(4, 1 + ⌊kademe/4⌋)
PLATINUM_IDX, PLATINUM_SLOTS = 13, 1      # Platin +1 yuva (docs/05 §1; Q34) → en çok 5
TIERLESS_SLOTS = 3                        # yay/arbalet/üçlü mızrak/gürz: kademesiz ev sahipleri (Q37)
TIERLESS_HOSTS = ("minecraft:bow", "minecraft:crossbow", "minecraft:trident", "minecraft:mace")   # ev sahibi sınıfı "ranged" (kademe malzemesi yok)
TIERLESS_IDX = M.N - 1                    # kademesiz ev sahibi tablo aramasında bu kademe gibi okunur (nitelikli katalizörler 'fixed' tabanlı; Menzil Gücü kendi derecesini taşır)
# Forge malzemeleri (7b): katalizörün hammaddesi. Kimlikler Java kaydıyla (dev.theoria.catalyst.CatalystItems) ve lang ile birebir.
FORGE_ITEMS = dict(dust="theoria:catalyst_dust", essence="theoria:catalyst_essence", core="theoria:catalyst_core")
SLOT_SHARE = {"head": 0.15, "chest": 0.40, "legs": 0.30, "feet": 0.15}   # M.SLOT ile aynı (kullanılmaz; bilgi)

# Maliyet merdiveni: (toz, imza malzeme, öz, çekirdek). Toz = cevherlerden ek damla, öz = düşman damlası, çekirdek = boss/zindan
# (kaynaklar 7b'de geçici global loot modifier; Faz 8/9 yeniden düzenler). Aynı merdiven her katalizöre uygulanır; imza malzeme katalizöre özgü.
LADDER = ((3, 1, 0, 0), (6, 2, 1, 0), (10, 3, 2, 0), (16, 4, 4, 1), (24, 6, 6, 3))
LADDER_POINT = {"dust": 1, "signature": 2, "essence": 5, "core": 25}      # yalnız monoton/oransal denetim için "puan"
NETHERITE_COST = (1, 2, 4, 6, 9)          # n. Netherite uygulaması kaç külçe (toplam 22); eşya başına en çok 5 uygulama
NETHERITE_MAX = len(NETHERITE_COST)
NETHERITE_DEFAULT_CAP = 1                 # tabloda olmayan (başka mod) büyü: max_level ≥ 2 ise +1, değilse 0
ERUDITION_PCT = (100, 300, 600, 1000, 1600)   # Bilgelik I-V: XP'ye +%; V üstü +400/seviye (ADR-004 Karar 7)
ERUDITION_STEP, ERUDITION_TOTAL_CAP = 400, 2400   # çok parçada toplanır, toplam +%2400 (taslak; Q43)

# Birleşik (tüm donanımdan toplanan) özel niteliklerin yumuşak tavanı — teknik tavan DEĞİL, denge: katalizör + Durum ekranı toplamı
AGG_CAP = {"theoria:crit_chance": 0.75, "theoria:crit_damage": 2.0, "theoria:life_steal": 0.30,
           "theoria:food_saturation": 12.0, "minecraft:knockback_resistance": 1.0}

# ─── Katalizör tablosu ────────────────────────────────────────────────────────────────────────────────────────────
# (id, tür, TR, EN, hedefler, etki, işlem, taban, cap, min_idx, imza, not)
#   tür: A nitelik (ItemAttributeModifierEvent) · B element/etki (sanal büyü, GetEnchantmentLevelEvent) · R menzil/ağır (sanal büyü, dereceli)
#        D Bilgelik (sanal büyü + Java) · C Netherite (vanilla külçe; büyü seviyesi)
#   hedefler: weapon (kılıç/balta/mızrak) · tool (kazma/kürek/balta/çapa) · armor = head+chest+legs+feet · ranged (kademesiz ev sahipleri)
#   etki: nitelik id'si ya da "hedef=nitelik hedef=nitelik" · sanal büyü için "theoria:<id>"
#   taban: fixed (cap'in kendisi) · sword (kılıç hasarı) · piece_armor (parçanın zırhı) · hp_gap (ref can − 20) · tool_speed (kazı hızı)
A, B, R, D, C = "attribute", "enchant", "ranged", "erudition", "netherite"
ARMOR4 = ("head", "chest", "legs", "feet")
CATALYSTS = [
    ("power", A, "Güç", "Might", ("weapon",), "minecraft:attack_damage", "add_value", "sword", 0.16, 0, "minecraft:blaze_powder", "düz hasar; ref bütçesinin hasar yarısı"),
    ("attack_speed", A, "Çeviklik", "Swiftness", ("weapon",), "minecraft:attack_speed", "add_value", "fixed", 0.40, 0, "minecraft:sugar", "kılıç 1,6 → 2,0 vuruş/sn (V)"),
    ("crit_chance", A, "Kritik Şansı", "Critical Chance", ("weapon",), "theoria:crit_chance", "add_value", "fixed", 0.25, 2, "minecraft:rabbit_foot", "CriticalHitEvent; toplam ≤ 0,75"),
    ("crit_damage", A, "Kritik Hasar", "Critical Damage", ("weapon",), "theoria:crit_damage", "add_value", "fixed", 0.50, 2, "minecraft:fermented_spider_eye", "kritik çarpanına eklenir (vanilla 1,5 → 2,0 V)"),
    ("life_steal", A, "Yaşam Çalma", "Life Steal", ("weapon",), "theoria:life_steal", "add_value", "fixed", 0.10, 4, "minecraft:glistering_melon_slice", "verilen hasarın bu kesri kadar can; toplam ≤ 0,30"),
    ("armor", A, "Zırh Plakası", "Armor Plating", ARMOR4, "minecraft:armor", "add_value", "piece_armor", 0.16, 0, "minecraft:iron_nugget", "parçanın zırhının %'si; ref bütçesinin zırh yarısı"),
    ("toughness", A, "Sertlik", "Resilience", ARMOR4, "minecraft:armor_toughness", "add_value", "fixed", 0.75, 4, "minecraft:amethyst_shard", "parça başına (takım V: +3)"),
    ("max_health", A, "Kalp", "Heart", ARMOR4, "minecraft:max_health", "add_value", "hp_gap", 0.115, 1, "minecraft:golden_carrot", "parça başına ref can açığının %'si; takım IV ≈ Kalp bütçesi"),
    ("thorns", A, "Diken", "Thorns", ("chest", "legs"), "theoria:thorns", "add_value", "sword", 0.10, 2, "minecraft:cactus", "alınan her vuruşta saldırgana düz hasar"),
    ("steadfast", A, "Sabitlik", "Steadfast", ARMOR4, "minecraft:knockback_resistance", "add_value", "fixed", 0.20, 2, "minecraft:cobbled_deepslate", "parça başına (takım V: 0,8)"),
    ("sprint", A, "Koşu", "Sprint", ("legs", "feet"), "minecraft:movement_speed", "add_multiplied_base", "fixed", 0.05, 0, "minecraft:feather", "yalnız hareket (savaş gücü değil): çarpan serbest"),
    ("leap", A, "Sıçrayış", "Leap", ("legs", "feet"), "minecraft:jump_strength", "add_value", "fixed", 0.10, 0, "minecraft:slime_ball", "vanilla taban 0,42"),
    ("featherfall", A, "Tüy Düşüş", "Featherfall", ("feet",), "minecraft:safe_fall_distance", "add_value", "fixed", 6.0, 0, "minecraft:phantom_membrane", "güvenli düşüş mesafesi (blok)"),
    ("saturation", A, "Doygunluk", "Satiety", ("head", "chest"), "theoria:food_saturation", "add_value", "fixed", 4.0, 0, "minecraft:cooked_beef", "her yemekte ek satürasyon; toplam ≤ 12"),
    ("reach", A, "Erişim", "Reach", ("weapon", "tool"), "weapon=minecraft:entity_interaction_range tool=minecraft:block_interaction_range", "add_value", "fixed", 1.0, 0, "minecraft:lead", "vanilla 3,0 / 4,5 blok"),
    ("excavation", A, "Kazı", "Excavation", ("tool",), "minecraft:mining_efficiency", "add_value", "tool_speed", 0.30, 0, "minecraft:redstone", "doğru aletle kazı hızına düz ekleme"),
    ("fortune_luck", A, "Şans", "Fortune", ("weapon", "tool", "ranged") + ARMOR4, "minecraft:luck", "add_value", "fixed", 1.0, 0, "minecraft:lapis_lazuli", "her parçada; yağma/keşif tablosu luck'ı okur"),
    ("frost", B, "Buz", "Frost", ("weapon",), "theoria:frost", "enchant", "sword", 0.10, 0, "minecraft:blue_ice", "vuruşa buz hasarı + yavaşlatma (V: %60, 3 sn)"),
    ("fire", B, "Ateş", "Fire", ("weapon",), "theoria:fire", "enchant", "sword", 0.10, 0, "minecraft:blaze_rod", "vuruşa yangın hasarı + tutuşturma"),
    ("lightning", B, "Yıldırım", "Lightning", ("weapon",), "theoria:lightning", "enchant", "sword", 0.12, 2, "minecraft:lightning_rod", "vuruş hedefinden 2 hedefe zincirler"),
    ("poison", B, "Zehir", "Poison", ("weapon",), "theoria:poison", "enchant", "sword", 0.12, 0, "minecraft:spider_eye", "5 sn boyunca toplam hasar"),
    ("wither", B, "Solma", "Wither", ("weapon",), "theoria:wither", "enchant", "sword", 0.12, 4, "minecraft:wither_rose", "5 sn boyunca toplam hasar + iyileşmeyi keser"),
    ("cleave", B, "AoE (Yarım Daire)", "Cleave", ("weapon",), "theoria:cleave", "enchant", "sword", 0.15, 2, "minecraft:flint", "2 blok yarıçaptaki diğer hedeflere vuruşun bu kesri"),
    ("bleed", B, "Kanama", "Bleed", ("weapon",), "theoria:bleed", "enchant", "sword", 0.14, 2, "minecraft:beetroot", "6 sn boyunca toplam hasar; üst üste binmez"),
    ("radiance", B, "Işık", "Radiance", ("weapon",), "theoria:radiance", "enchant", "sword", 0.30, 4, "minecraft:glowstone_dust", "koşullu: yalnız hortlak/ölümsüz etiketli hedefe (sayımda ×0,4)"),
    ("umbra", B, "Gölge", "Umbra", ("weapon",), "theoria:umbra", "enchant", "sword", 0.30, 4, "minecraft:ink_sac", "koşullu: arkadan vuruş (sayımda ×0,4)"),
    ("tempest", B, "Fırtına", "Tempest", ("weapon",), "theoria:tempest", "enchant", "sword", 0.30, 6, "minecraft:breeze_rod", "koşullu: havadayken (Kanat sinerjisi; sayımda ×0,4)"),
    ("gravity", B, "Gravite", "Gravity", ("weapon",), "theoria:gravity", "enchant", "fixed", 1.5, 4, "minecraft:ender_pearl", "hasar yok: hedefi oyuncuya çeker (blok)"),
    ("restoration", B, "Onarım", "Restoration", ("weapon", "tool", "ranged") + ARMOR4, "theoria:restoration", "enchant", "fixed", 0.002, 2, "minecraft:moss_block", "saniyede azami dayanıklılığın bu kesri (V: 8 dk'da tam)"),
    ("ranged_power", R, "Menzil Gücü", "Ranged Power", ("ranged",), "theoria:ranged_power", "enchant", "sword", 0.60, 0, "minecraft:arrow", "Q37: yay/arbalet/üçlü mızrak/gürz; DERECELİ: taban = katalizörün derecesi"),
    ("erudition", D, "Bilgelik", "Erudition", ("weapon", "tool", "ranged") + ARMOR4, "theoria:erudition", "enchant", "fixed", 1.0, 0, "minecraft:book", "mob + blok XP'sine +%100…1600 (ADR-006 Karar 8)"),
    ("netherite", C, "Netherite", "Netherite", ("weapon", "tool") + ARMOR4 + ("ranged",), "minecraft:netherite_ingot", "bonus", "fixed", 1.0, 0, "minecraft:netherite_ingot", "evrensel: gerçek büyülerin hepsini +1 (vanilla külçe, yeni item yok)"),
]
FIELDS = "id,kind,tr,en,targets,effect,op,basis,cap,min_idx,signature,note".split(",")

# Netherite'in büyü başına eklediği en çok seviye (ADR-006 Karar 7). Tabloda olmayan büyü: NETHERITE_DEFAULT_CAP (max_level ≥ 2 ise).
ENCHANT_CAPS = {   # id: (cap, neden)
    "minecraft:protection": (1, "EPF: 4 parça × (IV+1) = 20 = EPF tavanı (%40); fazlası boşa"),
    "minecraft:fire_protection": (1, "koruma ailesi"), "minecraft:blast_protection": (1, "koruma ailesi"), "minecraft:projectile_protection": (1, "koruma ailesi"),
    "minecraft:feather_falling": (2, ""), "minecraft:respiration": (2, ""), "minecraft:depth_strider": (2, ""), "minecraft:soul_speed": (2, ""),
    "minecraft:swift_sneak": (2, ""), "minecraft:thorns": (2, ""), "minecraft:frost_walker": (1, ""), "minecraft:aqua_affinity": (0, "tek seviye"),
    "minecraft:sharpness": (5, "hasar katkısı kademe hasarının yanında küçük"), "minecraft:smite": (5, ""), "minecraft:bane_of_arthropods": (5, ""),
    "minecraft:power": (5, ""), "minecraft:efficiency": (5, ""), "minecraft:unbreaking": (5, "1/(L+1) onarım şansı: sınırsız büyüme güvenli"),
    "minecraft:fortune": (2, ""), "minecraft:looting": (2, ""), "minecraft:luck_of_the_sea": (2, ""),
    "minecraft:sweeping_edge": (2, ""), "minecraft:fire_aspect": (2, ""), "minecraft:impaling": (2, ""), "minecraft:piercing": (2, ""),
    "minecraft:quick_charge": (1, "V'te yükleme anlık"), "minecraft:density": (3, ""), "minecraft:breach": (3, ""), "minecraft:wind_burst": (1, ""),
    "minecraft:lunge": (2, ""), "minecraft:loyalty": (2, ""),
    "minecraft:knockback": (1, "yüksek seviyede mob'u haritadan atar"), "minecraft:punch": (1, ""),
    "minecraft:lure": (0, "ADR-004: etkin VI'da balık hiç vurmaz"), "minecraft:riptide": (0, "fırlatma hızı sınırsız"),
    "minecraft:mending": (0, "tek seviye"), "minecraft:silk_touch": (0, "tek seviye"), "minecraft:infinity": (0, "tek seviye"), "minecraft:flame": (0, "tek seviye"),
    "minecraft:channeling": (0, "tek seviye"), "minecraft:multishot": (0, "tek seviye"), "minecraft:binding_curse": (0, "lanet"), "minecraft:vanishing_curse": (0, "lanet"),
    "theoria:erudition": (1, "Bilgelik VI (ADR-004 Karar 7)"),
}

# Öntanımlı özellikler (Q34, ADR-006 Karar 9): (kademe id, ev sahibi sınıfı, sanal büyü, seviye). Silinemez, parlama vermez, sanal (anvil/taşlama görmez).
INNATE = [
    ("bronze", "armor", "minecraft:unbreaking", 1), ("bronze", "tool", "minecraft:unbreaking", 1), ("bronze", "weapon", "minecraft:unbreaking", 1),
    ("steel", "weapon", "minecraft:sharpness", 1), ("steel", "armor", "minecraft:protection", 1),
    ("gold", "weapon", "minecraft:looting", 1), ("gold", "tool", "minecraft:fortune", 1),
    ("cobalt", "tool", "minecraft:efficiency", 1),
    ("diamond", "armor", "minecraft:unbreaking", 2), ("diamond", "tool", "minecraft:unbreaking", 2), ("diamond", "weapon", "minecraft:unbreaking", 2),
    ("lonsdaleit", "armor", "minecraft:protection", 2), ("lonsdaleit", "weapon", "minecraft:sharpness", 2),
    ("lonsdaleit", "armor", "minecraft:unbreaking", 2), ("lonsdaleit", "tool", "minecraft:unbreaking", 2), ("lonsdaleit", "weapon", "minecraft:unbreaking", 2),
    ("mithril", "armor", "minecraft:unbreaking", 3), ("mithril", "tool", "minecraft:unbreaking", 3), ("mithril", "weapon", "minecraft:unbreaking", 3),
    ("orichalcum", "armor", "theoria:restoration", 2), ("orichalcum", "tool", "theoria:restoration", 2), ("orichalcum", "weapon", "theoria:restoration", 2),
    ("orichalcum", "armor", "minecraft:fire_protection", 1), ("orichalcum", "armor", "minecraft:blast_protection", 1), ("orichalcum", "armor", "minecraft:projectile_protection", 1),
    ("platinum", "armor", "minecraft:protection", 3),
    ("platinum", "armor", "minecraft:fire_protection", 2), ("platinum", "armor", "minecraft:blast_protection", 2), ("platinum", "armor", "minecraft:projectile_protection", 2),
]
INNATE_FIELDS = "tier,host,enchant,level".split(",")
# "Bakır: ucuz onarım" (docs/05 §1) karşılıksız bırakıldı: bakır onarım girdisi zaten ucuz (külçe) — yeni mekanik gerekmez.


# ─── Hesap ────────────────────────────────────────────────────────────────────────────────────────────────────────
def slots(idx, tierless=False):
    """Ev sahibi başına katalizör yuvası. idx: eşyanın kademesi (zırh merdiveni ya da alet merdiveni idx'i)."""
    if tierless:
        return TIERLESS_SLOTS
    return min(SLOT_MAX, SLOT_BASE + idx // SLOT_STEP) + (PLATINUM_SLOTS if idx >= PLATINUM_IDX else 0)


def hp_gap(idx): return M.exp_hp(idx) - M.PLAYER_BASE_HP


def piece(idx, which): return M.pieces(idx)[{"head": 0, "chest": 1, "legs": 2, "feet": 3}[which]]


def basis(kind, idx, target):
    if kind == "fixed": return 1.0
    if kind == "sword": return M.sword(idx)
    if kind == "piece_armor": return float(piece(idx, target))
    if kind == "hp_gap": return float(hp_gap(idx))
    if kind == "tool_speed": return M.tool_speed(idx)
    raise ValueError(kind)


def by_id(): return {c[0]: c for c in CATALYSTS}


def value(cid, target, idx, level):
    """Seviye L katalizörün idx kademesindeki ev sahibinde, hedef parçada verdiği değer (nitelik değeri / vuruş başı hasar / kesir)."""
    _, _, _, _, _, _, _, bas, cap, *_ = by_id()[cid]
    return cap * CURVE[level - 1] * basis(bas, idx, target)


def targets_of(c):
    return c[4]


def host_idx_range(target):
    """Hedef türünün ev sahibi kademeleri: zırh 0-13, silah/alet 0-13 (alet merdiveni TOOLS idx'leri), kademesiz tek 'derece' 0-13."""
    if target in ARMOR4: return [i for i in range(M.N)]
    if target in ("weapon", "tool"): return sorted({i for i, _ in M.TOOLS})
    return list(range(M.N))   # ranged: derece = ingot kademesi


def value_rows():
    rows = []
    for c in CATALYSTS:
        if c[1] in (D, C):
            continue
        for target in targets_of(c):
            for idx in host_idx_range(target):
                if idx < c[9]:
                    continue
                rows.append(dict(id=c[0], target=target, idx=idx, **{f"l{lv}": f"{value(c[0], target, idx, lv):.4f}".rstrip("0").rstrip(".") for lv in range(1, LEVELS + 1)}))
    return rows


def effects_of(c):
    """Nitelik katalizörü için hedef → nitelik id'si (Java'nın ayrıştırmasına gerek kalmasın). Diğer türlerde boş."""
    if c[1] != A:
        return {}
    if "=" in c[5]:
        return dict(part.split("=") for part in c[5].split())
    return {t: c[5] for t in c[4]}


def values_table():
    """id → hedef → kademe (dizin) → [I..V değerleri]. Java'nın tek sayı kaynağı; None = bu kademede o hedefe konamaz (min_idx ya da ev sahibi kademesi değil)."""
    out = {}
    for c in CATALYSTS:
        if c[1] in (D, C):
            continue
        per_target = {}
        for target in targets_of(c):
            rows = {}
            for idx in host_idx_range(target):
                if idx < c[9]:
                    continue
                rows[str(idx)] = [round(value(c[0], target, idx, lv), 4) for lv in range(1, LEVELS + 1)]
            per_target[target] = rows
        out[c[0]] = per_target
    return out


def erudition_pct(level):
    return ERUDITION_PCT[level - 1] if level <= LEVELS else ERUDITION_PCT[-1] + ERUDITION_STEP * (level - LEVELS)


def netherite_bonus(vanilla_level, applications, cap, max_level):
    """ADR-006 Karar 7: bonus = min(uygulama sayısı, büyü başına tavan); vanilla seviye ≥ 1 şart; etkin ≤ 255."""
    if vanilla_level < 1 or cap <= 0: return 0
    return max(0, min(applications, cap, 255 - vanilla_level))


def ladder_points(level):
    d, s, e, k = LADDER[level - 1]
    return d * LADDER_POINT["dust"] + s * LADDER_POINT["signature"] + e * LADDER_POINT["essence"] + k * LADDER_POINT["core"]


# ─── Çıktılar ─────────────────────────────────────────────────────────────────────────────────────────────────────
def rules():
    r = [("levels", LEVELS, "katalizör seviyesi I-V; aynı katalizör bir eşyada bir yuvada, seviyeyi yükseltmek = daha yüksek seviyeyle değiştirmek"),
         ("curve", " ".join(str(x) for x in CURVE), "değer = cap × curve[L-1] × taban(kademe)"),
         ("slot_base", SLOT_BASE, "yuva = min(slot_max, slot_base + ⌊kademe/slot_step⌋)"), ("slot_step", SLOT_STEP, ""), ("slot_max", SLOT_MAX, ""),
         ("platinum_idx", PLATINUM_IDX, "kademe ≥ bu → +platinum_slots yuva"), ("platinum_slots", PLATINUM_SLOTS, "en çok 5 yuva"),
         ("tierless_slots", TIERLESS_SLOTS, "yay/arbalet/üçlü mızrak/gürz (Q37)")]
    for lv, (d, s, e, k) in enumerate(LADDER, 1):
        r.append((f"cost_l{lv}", f"{d} {s} {e} {k}", f"seviye {lv}: toz imza öz çekirdek ({ladder_points(lv)} puan)"))
    r += [("netherite_cost", " ".join(map(str, NETHERITE_COST)), "n. uygulamanın külçe maliyeti"), ("netherite_max", NETHERITE_MAX, "eşya başına en çok uygulama"),
          ("netherite_default_cap", NETHERITE_DEFAULT_CAP, "enchant_caps.csv'de olmayan büyü: max_level ≥ 2 ise bu, değilse 0"),
          ("erudition_pct", " ".join(map(str, ERUDITION_PCT)), "Bilgelik I-V XP bonusu (%)"), ("erudition_step", ERUDITION_STEP, "V üstü her seviye +%"),
          ("erudition_total_cap", ERUDITION_TOTAL_CAP, "toplam XP bonusu tavanı (%) — mainhand + 4 zırh parçası toplanır"),
          ("enchant_level_cap", 255, "etkin büyü seviyesi tavanı (D13, ADR-004 Karar 5)")]
    r += [(f"agg_cap_{k.split(':')[1]}", v, "birleşik yumuşak tavan") for k, v in AGG_CAP.items()]
    return [dict(key=k, value=v, note=n) for k, v, n in r]


def outputs():
    cat_rows = [dict(zip(FIELDS, (c[0], c[1], c[2], c[3], " ".join(c[4]), c[5], c[6], c[7], c[8], c[9], c[10], c[11]))) for c in CATALYSTS]
    caps_rows = [dict(enchant=k, netherite_cap=v[0], note=v[1]) for k, v in ENCHANT_CAPS.items()]
    innate_rows = [dict(zip(INNATE_FIELDS, row)) for row in INNATE]
    game = dict(
        levels=LEVELS, curve=list(CURVE),
        slots=dict(base=SLOT_BASE, step=SLOT_STEP, max=SLOT_MAX, platinum_idx=PLATINUM_IDX, platinum_bonus=PLATINUM_SLOTS, tierless=TIERLESS_SLOTS),
        ladder=[dict(level=i + 1, dust=d, signature=s, essence=e, core=k) for i, (d, s, e, k) in enumerate(LADDER)],
        netherite=dict(cost=list(NETHERITE_COST), max=NETHERITE_MAX, default_cap=NETHERITE_DEFAULT_CAP, level_cap=255,
                       caps={k: v[0] for k, v in ENCHANT_CAPS.items()}),
        erudition=dict(pct=list(ERUDITION_PCT), step=ERUDITION_STEP, total_cap=ERUDITION_TOTAL_CAP),
        agg_caps=AGG_CAP,
        catalysts=[dict(id=c[0], kind=c[1], targets=list(c[4]), effect=c[5], effects=effects_of(c), op=c[6], basis=c[7], cap=c[8], min_idx=c[9], signature=c[10],
                        graded=c[1] == R) for c in CATALYSTS],
        tierless=dict(items=list(TIERLESS_HOSTS), idx=TIERLESS_IDX), forge=FORGE_ITEMS,
        values=values_table(),
        innate=[dict(tier=t, host=h, enchant=e, level=lv) for t, h, e, lv in INNATE])
    return {CATALYSTS_CSV: M.render(FIELDS, cat_rows),
            VALUES_CSV: M.render("id,target,idx,l1,l2,l3,l4,l5".split(","), value_rows()),
            RULES_CSV: M.render(["key", "value", "note"], rules()),
            CAPS_CSV: M.render(["enchant", "netherite_cap", "note"], caps_rows),
            INNATE_CSV: M.render(INNATE_FIELDS, innate_rows),
            GAME_JSON: M.dumps(game)}


# ─── Bütçe ve bantlar (docs/06 §3 ref bütçesi; sim.py'nin bantlarıyla aynı mantık) ──────────────────────────────────────────
KALP_SHARE = 0.36        # T13: kalp bütçesi 70 / can açığı 194 (docs/06 §3: "Kalp" katalizörleri ≈ +70)
REF_LEVEL = 4            # ref oyuncu katalizörleri IV seviyesinde taşır
DPS_CAP = 2.2            # tek silahta katalizör-maks DPS çarpanı en çok (max arketipi hasar ×2 + katalizör dışı kaynaklar)
COND = 0.4               # koşullu element etkisinin sayımdaki ağırlığı
HTD_CMAX = 30.0          # katalizör-maks savunma, max bandı (docs/06 §1)
CMAX_OVER_REF = 1.6      # katalizör-maks HTD / ref HTD en çok (katalizörler ref'i ezmesin)


def need_level(c, demand, target_values):
    """demand'i karşılayan en küçük seviye (yoksa None)."""
    for lv in range(1, LEVELS + 1):
        if target_values(lv) >= demand - 1e-9: return lv
    return None


def ref_demand(idx):
    """ref oyuncunun katalizörden beklediği (zırh kesri, hasar kesri, kalp kesri). ref_bonus'un yarısı katalizör (docs/06 §3 önerisi)."""
    half = M.ref_bonus(idx) / 2
    return half, half, (KALP_SHARE if hp_gap(idx) > 0 else 0.0)


def ref_supply(idx):
    c = by_id()
    armor = sum(value("armor", t, idx, REF_LEVEL) for t in ARMOR4) / M.set_armor(idx)
    dmg = value("power", "weapon", idx, REF_LEVEL) / M.sword(idx) if idx in {i for i, _ in M.TOOLS} else None
    kalp = (sum(value("max_health", t, idx, REF_LEVEL) for t in ARMOR4) / hp_gap(idx)) if hp_gap(idx) > 0 else 0.0
    return armor, dmg, kalp


def weapon_catalysts():
    return [c for c in CATALYSTS if "weapon" in c[4] and c[1] in (A, B)]


def dps_mult(idx, picks, level=LEVELS):
    """Seçilen weapon katalizörlerinin beklenen DPS çarpanı (sword tabanına göre). Tek hedef, koşulsuz sayılır; koşullu element ×COND."""
    sw = M.sword(idx)
    flat, speed, chance, cdmg = 0.0, 0.0, 0.0, 0.0
    for cid in picks:
        c = by_id()[cid]
        v = value(cid, "weapon", idx, level)
        if cid == "power": flat += v / sw
        elif cid == "attack_speed": speed += v
        elif cid == "crit_chance": chance += v
        elif cid == "crit_damage": cdmg += v
        elif c[1] == B and cid not in ("gravity", "restoration"):
            flat += (v / sw) * (COND if cid in ("radiance", "umbra", "tempest") else 1.0)
    chance = min(chance, AGG_CAP["theoria:crit_chance"])
    return (1 + flat) * (1 + speed / 1.6) * (1 + chance * (0.5 + cdmg))


def best_weapon(idx, n):
    pool = [c[0] for c in weapon_catalysts() if c[9] <= idx]
    best = (1.0, ())
    for k in range(1, min(n, len(pool)) + 1):
        for combo in itertools.combinations(pool, k):
            d = dps_mult(idx, combo)
            if d > best[0]: best = (d, combo)
    return best


def defence(idx, picks_per_piece):
    """Ref oyuncu, ama katalizör yarısı yerine picks (IV değil V) — (zırh, sertlik, can) toplam."""
    base = M.loadout(idx)
    half = M.ref_bonus(idx) / 2
    armor = M.set_armor(idx) * (1 + half)              # seviye yarısı
    hp = float(M.PLAYER_BASE_HP) + (M.exp_hp(idx) - M.PLAYER_BASE_HP) * (1 - KALP_SHARE)
    tough = float(M.tough(idx))
    for t in ARMOR4:
        for cid in picks_per_piece:
            if idx < by_id()[cid][9]: continue
            v = value(cid, t, idx, LEVELS)
            if cid == "armor": armor += v
            elif cid == "max_health": hp += v
            elif cid == "toughness": tough += v
    return dict(base, armor=math.floor(armor), toughness=tough, hp=hp)


def best_defence(idx, n):
    pool = [cid for cid in ("armor", "toughness", "max_health") if by_id()[cid][9] <= idx]
    best, best_pick = None, ()
    for k in range(1, min(n, len(pool)) + 1):
        for combo in itertools.combinations(pool, k):
            h = M.hits_to_die(defence(idx, combo), M.mob_dmg(idx))
            if best is None or h > best: best, best_pick = h, combo
    return best, best_pick


def checks():
    out = []

    def add(name, ok, detail=""): out.append((name, bool(ok), detail))

    ids = [c[0] for c in CATALYSTS]
    kinds = {k: sum(1 for c in CATALYSTS if c[1] == k) for k in (A, B, R, D, C)}
    add("32 katalizör, benzersiz id", len(CATALYSTS) == 32 and len(set(ids)) == 32 and all(i.replace("_", "").isalnum() and i.islower() for i in ids),
        f"{len(CATALYSTS)} katalizör · " + " ".join(f"{k}={v}" for k, v in kinds.items()))
    bad_t = [c[0] for c in CATALYSTS if not set(c[4]) <= {"weapon", "tool", "ranged", *ARMOR4}]
    bad_b = [c[0] for c in CATALYSTS if c[7] not in ("fixed", "sword", "piece_armor", "hp_gap", "tool_speed") or c[8] <= 0]
    add("hedef/taban/cap geçerli", not bad_t and not bad_b, f"hatalı: {bad_t + bad_b}")
    # tek id çakışmasın diye: bir etki ya da tür yinelenmiyor (netherite yalnız külçe)
    add("etki id'leri benzersiz", len({c[5] for c in CATALYSTS}) == len(CATALYSTS), "")

    sl = [slots(i) for i in range(M.N)]
    add("yuva monoton, 1..5, Platin 5", sl == sorted(sl) and sl[0] == 1 and sl[-1] == 5 and sl[12] == 4, f"T0..T13 = {sl}")

    lad = [ladder_points(lv) for lv in range(1, LEVELS + 1)]
    ratio = [lad[i + 1] / lad[i] for i in range(LEVELS - 1)]
    add("maliyet merdiveni monoton, seviye başına ≥ ×1,4 (çekirdek yalnız IV+)", all(r >= 1.4 for r in ratio) and LADDER[2][3] == 0 and LADDER[3][3] >= 1,
        f"puan {lad}, oranlar " + " ".join(f"{r:.1f}" for r in ratio))
    nc = list(NETHERITE_COST)
    add("Netherite maliyeti artan, toplam ≤ 24 külçe", nc == sorted(nc) and len(set(nc)) == len(nc) and sum(nc) <= 24, f"{nc} toplam {sum(nc)}")

    # ref bütçesi (docs/06 §3): armor + hasar yarım ref_bonus, Kalp payı
    lines, ok_ref, slot_ok, worst = [], True, True, 0.0
    tool_idx = {i for i, _ in M.TOOLS}
    for idx in range(M.N):
        da, dd, dk = ref_demand(idx)
        sa, sd, sk = ref_supply(idx)
        la = need_level("armor", da, lambda lv: sum(value("armor", t, idx, lv) for t in ARMOR4) / M.set_armor(idx)) if da else 0
        ld = need_level("power", dd, lambda lv: value("power", "weapon", idx, lv) / M.sword(idx)) if (dd and idx in tool_idx) else 0
        full = idx >= M.REF_BONUS_RAMP       # T0-3'te talep rampada: arz talebi aşar (düşük seviyeyle karşılanır, aşağıda denetlenir)
        if da and (sa < da - 1e-9 or (full and sa > da * 1.15 + 1e-9)): ok_ref = False
        if dd and sd is not None and (sd < dd - 1e-9 or (full and sd > dd * 1.15 + 1e-9)): ok_ref = False
        if dk and not (dk - 1e-9 <= sk <= dk * 1.15 + 1e-9) and slots(idx) >= 2: ok_ref = False
        need = 1 + (1 if hp_gap(idx) > 0 and idx >= 4 else 0)       # ref: parça başı zırh (+ T4'ten itibaren Kalp)
        if need > slots(idx): slot_ok = False
        lines.append((idx, da, sa, dd, sd, dk, sk, la, ld))
    add("ref bütçesi: zırh/hasar katalizör yarısı + Kalp payı IV ile tutturuluyor (≤ +%15 sapma)", ok_ref,
        "T4+: zırh IV %.1f%% (talep %.1f%%) · hasar IV %.1f%% (talep %.1f%%) · Kalp IV %.0f%% (talep %.0f%%)" % (
            lines[13][2] * 100, lines[13][1] * 100, (lines[13][4] or 0) * 100, lines[13][3] * 100, lines[13][6] * 100, lines[13][5] * 100))
    add("ref katalizör talebi yuvalara sığıyor (parça başı zırh + T4+'te Kalp)", slot_ok, "T4'te 2 yuva = 2 talep")
    add("erken kademede ref talebi IV'ten düşük seviyeyle karşılanıyor (yuva boşa gitmiyor)", all(l[7] is not None and l[7] <= REF_LEVEL for l in lines if l[1]), "")

    # katalizör-maks savunma/saldırı
    worst_dps, worst_def, ref_def = 0.0, 0.0, 1e9
    for idx in range(M.N):
        n = slots(idx)
        h, _ = best_defence(idx, n)
        ref_h = M.hits_to_die(M.loadout(idx), M.mob_dmg(idx))
        worst_def = max(worst_def, h)
        ref_def = min(ref_def, h / ref_h)
        if idx in tool_idx:
            worst_dps = max(worst_dps, best_weapon(idx, n)[0])
    add(f"katalizör-maks savunma (parçada tüm yuvalar) HTD ≤ {HTD_CMAX:g}", worst_def <= HTD_CMAX, f"en çok {worst_def:.1f}")
    over = max(best_defence(i, slots(i))[0] / M.hits_to_die(M.loadout(i), M.mob_dmg(i)) for i in range(M.N))
    add(f"katalizör-maks / ref savunma ≤ ×{CMAX_OVER_REF:g}", over <= CMAX_OVER_REF, f"en çok ×{over:.2f}")
    add(f"katalizör-maks silah DPS çarpanı ≤ ×{DPS_CAP:g}", worst_dps <= DPS_CAP, f"en çok ×{worst_dps:.2f}")

    # örnek: T8 elmas kılıç, 3 yuva: Hız IV + Kritik Şans III + Buz II  (Faz 7 bitiş kriteri)
    base = M.sword(8)
    ex_all = (1 + value("frost", "weapon", 8, 2) / base) * (1 + value("attack_speed", "weapon", 8, 4) / 1.6) * (1 + value("crit_chance", "weapon", 8, 3) * 0.5)
    add("örnek Hız IV + Kritik Şansı III + Buz II (T8, 3 yuva): DPS ×1,2-×1,6", 1.2 <= ex_all <= 1.6 and slots(8) == 3, f"×{ex_all:.2f}")

    # Netherite ve Bilgelik
    add("Netherite: Koruma IV ×4 parça, en çok uygulamada bile EPF tavanını (20) aşmıyor ve ona ulaşıyor",
        4 * (4 + netherite_bonus(4, NETHERITE_MAX, ENCHANT_CAPS['minecraft:protection'][0], 4)) == M.EPF_MAX, "")
    add("Netherite: etkin seviye ≤ 255", netherite_bonus(250, 5, 5, 255) == 5 and netherite_bonus(254, 5, 5, 255) == 1 and netherite_bonus(255, 5, 5, 255) == 0, "")
    add("Netherite: büyüsü olmayan / tek seviyeli eşyada etkisiz", netherite_bonus(0, 3, 5, 5) == 0 and netherite_bonus(1, 3, 0, 1) == 0, "")
    add("Bilgelik V = +%1600, VI = +%2000, tek parça VI tavanı aşmaz; iki VI tavana vurur",
        erudition_pct(5) == 1600 and erudition_pct(6) == 2000 and erudition_pct(6) <= ERUDITION_TOTAL_CAP < 2 * erudition_pct(6), f"VI {erudition_pct(6)}% / tavan {ERUDITION_TOTAL_CAP}%")
    add("Bilgelik I-V monoton, V üstü +%400", list(ERUDITION_PCT) == sorted(ERUDITION_PCT) and erudition_pct(7) - erudition_pct(6) == ERUDITION_STEP, "")

    # öntanımlı özellikler (Q34): tablo geçerli ve ref_epf'i aşmıyor
    tier_ids = {r[1] for r in M.ARMOR}
    inn_bad = [i for i in INNATE if i[0] not in tier_ids or i[1] not in ("armor", "tool", "weapon") or i[3] < 1 or (i[2] not in ENCHANT_CAPS and i[2] not in ("minecraft:fortune", "theoria:restoration"))]
    add("öntanımlı özellik tablosu geçerli (kademe, sınıf, büyü ∈ enchant_caps)", not inn_bad, f"hatalı: {inn_bad}")
    over_epf = []
    for tid, host, ench, lv in INNATE:
        if host != "armor" or ench != "minecraft:protection": continue
        idx = next((r[0] for r in M.ARMOR if r[1] == tid), None)
        if idx is None: continue      # geçersiz kademe zaten yukarıdaki denetimde kırmızı
        if 4 * lv > M.ref_epf(idx): over_epf.append((tid, 4 * lv, M.ref_epf(idx)))
    add("öntanımlı Koruma (4 parça) ref_epf'i aşmıyor (bütçe iki kez sayılmıyor)", not over_epf, f"aşanlar: {over_epf}")
    return out


def example_lines():
    base = M.sword(8)
    L = [("Hız IV", "attack_speed", 4), ("Kritik Şansı III", "crit_chance", 3), ("Buz II", "frost", 2)]
    parts = [f"{n}: " + (f"+{value(c, 'weapon', 8, lv):.2f}" if c != "frost" else f"+{value(c, 'weapon', 8, lv):.2f} hasar/vuruş") for n, c, lv in L]
    mult = (1 + value("frost", "weapon", 8, 2) / base) * (1 + value("attack_speed", "weapon", 8, 4) / 1.6) * (1 + value("crit_chance", "weapon", 8, 3) * 0.5)
    return parts, mult


def report():
    print("Katalizör bütçesi (docs/06 §3; talep = ref_bonus/2 · Kalp %36) — arz = IV katalizör, 4 parça")
    print("  kademe yuva | zırh talep/arz | hasar talep/arz | Kalp talep/arz | gereken seviye (zırh/hasar) | katalizör-maks HTD / ref HTD | maks silah DPS")
    tool_idx = {i for i, _ in M.TOOLS}
    for idx in range(M.N):
        da, dd, dk = ref_demand(idx)
        sa, sd, sk = ref_supply(idx)
        la = need_level("armor", da, lambda lv: sum(value("armor", t, idx, lv) for t in ARMOR4) / M.set_armor(idx)) if da else "-"
        ld = need_level("power", dd, lambda lv: value("power", "weapon", idx, lv) / M.sword(idx)) if (dd and idx in tool_idx) else "-"
        h, pick = best_defence(idx, slots(idx))
        rh = M.hits_to_die(M.loadout(idx), M.mob_dmg(idx))
        dps = f"×{best_weapon(idx, slots(idx))[0]:.2f}" if idx in tool_idx else "  -"
        print(f"  T{idx:<2} {slots(idx)} yuva | %{da*100:4.1f} / %{sa*100:4.1f} | " + (f"%{dd*100:4.1f} / %{sd*100:4.1f}" if sd is not None else "      -      ") +
              f" | %{dk*100:3.0f} / %{sk*100:3.0f} | {la}/{ld} | {h:5.1f} / {rh:4.1f} (×{h/rh:.2f}, {'+'.join(pick)}) | {dps}")
    parts, mult = example_lines()
    print(f"\nÖrnek (T8 elmas kılıç, kılıç hasarı {M.sword(8)}, {slots(8)} yuva): " + " · ".join(parts) + f" → beklenen DPS ×{mult:.2f}")
    print("Maliyet merdiveni (toz/imza/öz/çekirdek → puan): " + " · ".join(f"{'I II III IV V'.split()[i]} {LADDER[i]}→{ladder_points(i+1)}" for i in range(LEVELS)))


def negative():
    """Denetimlerin gerçekten kırmızıya döndüğünü kanıtlar: bilerek bozulmuş sabitlerle her biri en az bir denetimi düşürmeli."""
    g = globals()
    cases = [("seviye eğrisi yarıya", "CURVE", tuple(x / 2 for x in CURVE)), ("yuva tavanı 2", "SLOT_MAX", 2),
             ("Netherite maliyeti azalan", "NETHERITE_COST", (4, 3, 2, 1, 1)), ("Bilgelik toplam tavanı 1000", "ERUDITION_TOTAL_CAP", 1000),
             ("maliyet merdiveni düz", "LADDER", ((3, 1, 0, 0),) * 5), ("öntanımlı tablo bozuk", "INNATE", [("nope", "armor", "minecraft:protection", 1)]),
             ("Kalp cap'i ikiye katla", "CATALYSTS", [(*c[:8], c[8] * 2, *c[9:]) if c[0] == "max_health" else c for c in CATALYSTS]),
             ("koruma tavanı 3", "ENCHANT_CAPS", dict(ENCHANT_CAPS, **{"minecraft:protection": (3, "")}))]
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
    print("OK" if not green else f"HATA ({green} bozulma yakalanmadı)")
    return 1 if green else 0


def main(argv):
    if "--break" in argv:
        return negative()
    files = outputs()
    check = "--check" in argv
    stale = M.sync(files, check=True) if check else M.sync(files, check=False)
    if "--report" in argv:
        report()
    results = checks()
    failed = [r for r in results if not r[1]]
    if check or "--report" in argv or failed:
        for name, ok, detail in results:
            if not ok or "--report" in argv:
                print(f"  {'✅' if ok else '❌'} {name}" + (f" — {detail}" if detail else ""))
    for p in (stale if check else []):
        print(f"BAYAT: {p.relative_to(ROOT)} → python3 -I tools/balance/catalysts.py")
    bad = len(failed) + (len(stale) if check else 0)
    print("OK" if not bad else f"HATA ({bad})")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
