#!/usr/bin/env python3
"""Relic denetimi (Faz 12). Çalıştır: python3 -I tools/check_relics.py   (negatif test: --break)

Oyun açmadan yakalanabilecek her şey; oyun içi karşılığı: `/theoria test relic [id]` (25 relic için ayrı denetim: gerçek öğe gerçek Curios yuvasında, gerçek etki kodu),
GameTest `relic_*` (32). İkonların/ipuçlarının *görünüşü* kullanıcı onayıdır (CLAUDE.md: ekran görüntüsü alınmaz).
  1. Veri: data/relics.csv + data/relic_sources.csv geçerli (generator/relics.py doğrulaması: yuva Curios'ta var, nadirlik, tür parametreleri, açıklamadaki %s sayısı = türün gösterdiği
     değer sayısı, her relic'in kaynağı var, efsanevi yalnız boss/kale), en az 24 relic ("~25"), üretilen dosyalar güncel (generator/relics.py --check).
  2. Yuva kapasitesi: bir yuva türündeki relic sayısı o türün slot sayısını (data/curios_slots.csv) aşmaz — hepsi aynı anda takılabilir.
  3. Güç bütçesi: tüm nitelik relic'leri birden takılsa her niteliğin toplamı tavanın altında (BUDGET; katalizör parça başı tavanlarının küçük bir kesri); relic nitelikleri
     tek parça başına bir kimlik taşır, yani aynı relic iki kez takılsa katlanmaz (Java: modifierId).
  4. Kaynaklar: kutu kaynağı = structures.csv'deki loot tablosu, boss kaynağı = mobs.csv'deki boss; boss her ölümde bir relic bırakır (chance = 1); her kaynağın bir tablosu ve bir loot
     değiştiricisi var; her relic Curios etiketinde (#curios:<yuva>) ve etiket dosyasında başka öğe yok.
  5. Varlıklar: her relic için öğe tanımı, model, doku (16×16, yeterince dolu, hiçbiri ötekinin aynısı değil) ve iki dilde ad + açıklama.
  6. Kaynak kod: relic paketi Curios'a dokunmaz (tek kapı dev.theoria.curios.WornCurios), GL yok, hiçbir relic kimliği Java'ya gömülmemiş (içerik veri-güdümlü), her Java dosyası < 600 satır.
Çıkış 0 + son satır 'OK' = temiz.
"""
import argparse
import csv
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "generator"))
import pngio  # noqa: E402
import relics as gen  # noqa: E402

GEN = ROOT / "mods-src/theoria/src/generated/relics"
LANG = ROOT / "mods-src/theoria/src/main/resources/assets/theoria/lang"
JAVA = ROOT / "mods-src/theoria/src/main/java/dev/theoria"
MIN_RELICS = 24
# Toplam üst sınırlar (tüm relic'ler birden). Katalizör parça başı tavanları: crit_chance 0,25 · crit_damage 0,5 · life_steal 0,1 · knockback_resistance 0,2/parça (catalysts.csv).
BUDGET = {
    "theoria:crit_chance": 0.10, "theoria:crit_damage": 0.30, "theoria:life_steal": 0.06, "minecraft:max_health": 12.0, "minecraft:movement_speed": 0.25,
    "minecraft:attack_speed": 0.30, "minecraft:knockback_resistance": 0.50, "minecraft:luck": 2.0, "minecraft:step_height": 1.0,
    "minecraft:block_interaction_range": 2.0, "minecraft:safe_fall_distance": 8.0,
}
errors, notes = [], []


def err(msg):
    errors.append(msg)


def load(break_it):
    relics, sources = gen.read_relics(), gen.read_sources()
    if break_it:  # negatif test: kuralları ihlal eden satırlar eklenmiş gibi davran
        base = dict(relics[0])
        relics.append(dict(base, id="bad_slot", slot="tiara", sources=[{"source": "ruin", "weight": 1}]))
        relics.append(dict(base, id="bad_legend", rarity="legendary", sources=[{"source": "ruin", "weight": 1}]))
        relics.append(dict(base, id="bad_desc", desc_en="no placeholder here", kind="magnet", raw={"radius": "8", "pull": "0.3"}, params={"radius": 8, "pull": 0.3}, mods=[], sources=[{"source": "ruin", "weight": 1}]))
        relics.append(dict(base, id="bad_source", sources=[{"source": "nowhere", "weight": 1}]))
        relics.append(dict(relics[5], id="bad_budget", mods=[{"attribute": "theoria:crit_chance", "op": "add_value", "amount": 0.9}]))
        sources = sources + [dict(sources[0], id="empty_source")]
    return relics, sources


def check_data(relics, sources, break_it):
    for e in gen.validate(relics, sources):
        err(e)
    if len(relics) < MIN_RELICS:
        err(f"relics.csv: {len(relics)} relic, Faz 12 ~25 istiyor (en az {MIN_RELICS})")
    if not break_it:
        out = subprocess.run([sys.executable, "-I", str(ROOT / "generator/relics.py"), "--check"], capture_output=True, text=True)
        if out.returncode != 0:
            err("generator/relics.py --check kırmızı (üretilen dosyalar bayat; python3 -I generator/relics.py): " + (out.stdout + out.stderr).strip().splitlines()[0])
    notes.append(f"veri: {len(relics)} relic, {len(sources)} kaynak, geçerli; üretilen dosyalar güncel")


def check_slots(relics):
    sizes = {r["id"]: int(r["size"]) for r in gen.read_csv(ROOT / "data/curios_slots.csv")}
    per = {}
    for r in relics:
        per[r["slot"]] = per.get(r["slot"], 0) + 1
    for slot, n in sorted(per.items()):
        if n > sizes.get(slot, 0):
            err(f"yuva '{slot}': {n} relic var ama yalnız {sizes.get(slot, 0)} yuva (hepsi aynı anda takılamaz; data/curios_slots.csv)")
    notes.append("yuva kapasitesi: " + ", ".join(f"{s} {n}/{sizes[s]}" for s, n in sorted(per.items()) if s in sizes))


def check_budget(relics):
    totals = {}
    for r in relics:
        for m in r["mods"]:
            totals[m["attribute"]] = totals.get(m["attribute"], 0.0) + m["amount"]
    for attribute, total in sorted(totals.items()):
        cap = BUDGET.get(attribute)
        if attribute == "minecraft:fall_damage_multiplier":
            if total < -1.0 - 1e-9:
                err(f"{attribute}: toplam {total} < -1 (düşme çarpanı negatif olamaz)")
        elif cap is None:
            err(f"{attribute}: BUDGET'te tavanı yok (tools/check_relics.py'ye ekle)")
        elif total > cap + 1e-9:
            err(f"{attribute}: tüm relic'ler birden +{total:g}, bütçe tavanı {cap:g}")
    notes.append("güç bütçesi (hepsi birden): " + ", ".join(f"{a.split(':')[1]} {t:g}/{BUDGET.get(a, '-')}" for a, t in sorted(totals.items())))


def check_sources(relics, sources, break_it):
    loots = {r["loot"] for r in gen.read_csv(ROOT / "data/structures.csv")}
    bosses = {r["id"] for r in gen.read_csv(ROOT / "data/mobs.csv") if r["cls"] == "boss"}
    for s in sources:
        if s["type"] == "chest":
            name = s["target"].replace("theoria:", "")
            if name not in loots:
                err(f"relic_sources.csv: {s['id']} hedefi {s['target']} structures.csv'de bir loot tablosu değil")
        else:
            if s["id"] not in bosses:
                err(f"relic_sources.csv: {s['id']} mobs.csv'de boss değil")
            if s["chance"] != 1.0:
                err(f"relic_sources.csv: boss {s['id']} her ölümde bir relic bırakmalı (chance 1.0, şu an {s['chance']})")
            if s["target"] != f"theoria:entities/{s['id']}":
                err(f"relic_sources.csv: boss {s['id']} hedefi theoria:entities/{s['id']} olmalı")
    if break_it:
        return
    for s in sources:
        for path in (f"data/theoria/loot_table/relics/{s['id']}.json", f"data/theoria/loot_modifiers/relics_{s['id']}.json"):
            if not (GEN / path).is_file():
                err(f"kaynak {s['id']}: {path} yok")
    for slot in {r["slot"] for r in relics}:
        tag = GEN / f"data/curios/tags/item/{slot}.json"
        have = set(json.loads(tag.read_text(encoding="utf-8"))["values"]) if tag.is_file() else set()
        want = {f"theoria:relic/{r['id']}" for r in relics if r["slot"] == slot}
        if have != want:
            err(f"#curios:{slot} etiketi relic'lerle eşleşmiyor (fazla {sorted(have - want)}, eksik {sorted(want - have)})")
    notes.append(f"kaynaklar: {sum(1 for s in sources if s['type'] == 'chest')} kutu tablosu + {sum(1 for s in sources if s['type'] == 'boss')} boss, her biri tablo + loot değiştiricisi; her relic Curios etiketinde")


def check_assets(relics, break_it):
    if break_it:
        return
    en = json.loads((LANG / "en_us.json").read_text(encoding="utf-8"))
    tr = json.loads((LANG / "tr_tr.json").read_text(encoding="utf-8"))
    seen = {}
    for r in relics:
        for rel in (f"assets/theoria/items/relic/{r['id']}.json", f"assets/theoria/models/item/relic/{r['id']}.json"):
            if not (GEN / rel).is_file():
                err(f"{r['id']}: {rel} yok")
        png = GEN / f"assets/theoria/textures/item/relic/{r['id']}.png"
        if not png.is_file():
            err(f"{r['id']}: doku yok")
            continue
        w, h, rows = pngio.decode(png.read_bytes())
        opaque = sum(1 for row in rows for px in row if px[3] > 0)
        if (w, h) != (16, 16) or opaque < 20:
            err(f"{r['id']}: doku {w}×{h}, {opaque} dolu piksel (16×16 ve ≥ 20 bekleniyor)")
        key = tuple(tuple(px) for row in rows for px in row)
        if key in seen:
            err(f"{r['id']}: dokusu {seen[key]} ile aynı")
        seen[key] = r["id"]
        for lang_name, table in (("en_us", en), ("tr_tr", tr)):
            for key_name in (f"item.theoria.relic.{r['id']}", f"theoria.relic.desc.{r['id']}"):
                if not table.get(key_name):
                    err(f"{r['id']}: {lang_name}'de '{key_name}' yok")
    notes.append(f"varlıklar: {len(relics)} öğe tanımı + model + 16×16 doku (hepsi farklı) + iki dilde ad/açıklama")


def check_java(relics, break_it):
    sources = {}
    for p in sorted((JAVA / "relics").glob("*.java")):
        sources[p.relative_to(JAVA).as_posix()] = p.read_text(encoding="utf-8")
    if break_it:
        sources["relics/RelicEffects.java"] += '\nimport top.theillusivec4.curios.api.CuriosApi;\nimport org.lwjgl.opengl.GL11;\nString x = "night_vision_ring";\n'
        sources["relics/RelicProbe.java"] += "\n" + "\n" * 600
    ids = {r["id"] for r in relics}
    for rel, text in sources.items():
        if "top.theillusivec4" in text:
            err(f"{rel}: Curios sınıfına dokunuyor — yalnız dev.theoria.curios.WornCurios (ADR-007 K1)")
        if re.search(r"org\.lwjgl|com\.mojang\.blaze3d\.opengl|GL11|RenderSystem", text):
            err(f"{rel}: doğrudan GL/LWJGL/RenderSystem (CLAUDE.md render kuralı)")
        for literal in set(re.findall(r'"([a-z][a-z0-9_]*)"', text)) & ids:
            err(f'{rel}: "{literal}" bir relic kimliği Java\'ya gömülmüş — içerik veri-güdümlü (data/relics.csv)')
        if len(text.splitlines()) >= 600:
            err(f"{rel}: {len(text.splitlines())} satır (< 600 olmalı, docs/03 §6)")
    wc = JAVA / "curios/WornCurios.java"
    if not wc.is_file():
        err("dev/theoria/curios/WornCurios.java yok (relic'lerin tek Curios kapısı)")
    notes.append(f"kaynak kod: relics paketi {len(sources)} dosya, Curios/GL/gömülü kimlik yok, hepsi < 600 satır")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--break", dest="break_it", action="store_true", help="negatif test: kuralları ihlal eden girdiler ekle; kırmızı çıkmalı")
    args = parser.parse_args()
    relics, sources = load(args.break_it)
    check_data(relics, sources, args.break_it)
    check_slots(relics)
    check_budget(relics)
    check_sources(relics, sources, args.break_it)
    check_assets(relics, args.break_it)
    check_java(relics, args.break_it)
    for line in notes:
        print("·", line)
    if errors:
        for e in errors:
            print("✘", e)
        print(f"{len(errors)} ihlal")
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
