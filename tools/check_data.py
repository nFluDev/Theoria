#!/usr/bin/env python3
"""Theoria veri ve kaynak tutarlılık denetimi. Çalıştır: python3 -I tools/check_data.py

Denetler:
  1. lang: tr_tr.json ve en_us.json aynı anahtarlara sahip, boş değer yok, %s yer tutucu sayıları eşit;
     Java'da kullanılan her çeviri anahtarı lang'de var; kullanılmayan komut/ayar anahtarı yok.
  2. data/balance: tiers.csv şeması (id/idx benzersizliği, parça toplamı, kademeler azalmıyor), combat.csv anahtarları;
     tools/balance/tiers.py ile güncel mi (CSV'ler + oyunun okuduğu datapack JSON'ları + technical_caps.json, artık dosya yok);
     tools/balance/sim.py --check (denge bantları + sim_cases.csv güncel mi).
  3. src/main/resources altındaki tüm JSON'lar ayrıştırılabiliyor.
  4. Hiçbir .java 600 satırı geçmiyor (docs/03 §6).
  5. Gear (Faz 6): tools/check_gear.py — materials/ores/alloys/drops.csv şeması, üretilen dosyalar güncel, model/doku/lang/tarif/loot/tag referansları,
     bağımsız kazı-kapısı çözümlemesi, blok sertliği. data/balance/tools.csv ve ores.csv güncelliği check_tiers içinde (tiers.py --check) denetlenir.
  7. Worldgen (Faz 9b): tools/check_worldgen.py — biomes/biome_climate/structures/worldgen/ores CSV'leri ↔ üretilen biyom/bölge/yapı/loot dosyaları, NBT'ler bağımsız okunur.
  8. Durum ekranı (Faz 10): tools/balance/stats.py --check (stat/seviye CSV'leri + oyun JSON'u üreteçle özdeş; seviye eğrisi, yumuşak tavanlar, referans bütçe payı,
     tempo bantları, respec düşüşü, dil anahtarları) + tools/balance/mobs.py --check (mob XP büyümesi stats.py'nin tempo modeliyle aynı sayıdır).
  9. Relic (Faz 12): tools/check_relics.py — data/relics.csv + relic_sources.csv ↔ üretilen öğe/doku/Curios etiketi/loot/lang, yuva kapasitesi, güç bütçesi, kaynaklar, Java'da gömülü relic kimliği yok.
  6. Katalizör (Faz 7a/7b): generator/catalysts.py --check (item/blok/büyü/loot/lang dosyaları güncel) + tools/balance/catalysts.py --check — catalysts/catalyst_values/catalyst_rules/enchant_caps/innate.csv + oyun JSON'u üreteçle özdeş;
     ref bütçesi (zırh/hasar/Kalp), yuva sığması, katalizör-maks bantları, Netherite ve Bilgelik kuralları (negatif test: catalysts.py --break).
Çıkış kodu: 0 = temiz, 1 = hata var. Yeni veri dosyası (materials.csv, mobs.csv …) geldikçe buraya şeması eklenir.
"""
import csv, json, pathlib, re, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
MOD = ROOT / "mods-src/theoria/src/main"
LANG = MOD / "resources/assets/theoria/lang"
errors, notes = [], []


def err(msg):
    errors.append(msg)


def check_lang():
    files = {name: LANG / f"{name}.json" for name in ("en_us", "tr_tr")}
    data = {}
    for name, path in files.items():
        if not path.exists():
            err(f"lang: {path.relative_to(ROOT)} yok")
            return
        data[name] = json.loads(path.read_text(encoding="utf-8"))
    en, tr = data["en_us"], data["tr_tr"]
    for key in sorted(set(en) - set(tr)):
        err(f"lang: '{key}' tr_tr.json'da eksik")
    for key in sorted(set(tr) - set(en)):
        err(f"lang: '{key}' en_us.json'da eksik")
    placeholder = re.compile(r"%(?:\d+\$)?s")
    for key in sorted(set(en) & set(tr)):
        for name, table in (("en_us", en), ("tr_tr", tr)):
            if not str(table[key]).strip():
                err(f"lang: '{key}' {name}'de boş")
        if len(placeholder.findall(en[key])) != len(placeholder.findall(tr[key])):
            err(f"lang: '{key}' yer tutucu sayısı farklı (en: {en[key]!r}, tr: {tr[key]!r})")
    # keys referenced from Java
    used = set()
    pattern = re.compile(r'(?:translatable|translatableWithFallback|translation)\(\s*"([a-z0-9_.]+)"')
    for java in (MOD / "java").rglob("*.java"):
        used.update(pattern.findall(java.read_text(encoding="utf-8")))
    for key in sorted(used - set(en)):
        if key.endswith(".") and any(k.startswith(key) for k in en):   # dinamik anahtar ailesi: translatable("theoria.catalyst.desc." + id) — önek en az bir anahtarla eşleşmeli
            continue
        err(f"lang: Java'da kullanılan '{key}' lang dosyalarında yok")
    # hand-written keys that nothing references (config screen derives *.tooltip / title / section keys itself)
    derived = re.compile(r"\.tooltip$|^theoria\.configuration\.(title|section\.)|^theoria\.mod\.")
    for key in sorted(k for k in en if k.startswith(("commands.theoria.", "theoria.configuration.")) and k not in used and not derived.search(k)):
        err(f"lang: '{key}' hiçbir yerde kullanılmıyor")
    notes.append(f"lang: {len(en)} anahtar × 2 dil, Java'da {len(used)} anahtar")


def run_tool(script, *args):
    """Runs a tools/balance script in --check mode (it never writes); returns (ok, last output line)."""
    result = subprocess.run([sys.executable, "-I", str(ROOT / "tools/balance" / script), *args], capture_output=True, text=True)
    lines = (result.stdout + result.stderr).strip().splitlines()
    return result.returncode == 0, lines


def check_tiers():
    path = ROOT / "data/balance/tiers.csv"
    expected = ("idx,id,tr,helm,chest,legs,boots,total,tough,adur,sword,tdur,mob_hp_mult,mob_dmg_mult,mob_bypass_mult,"
                "mobhp,mobdmg,exphp,ref_bonus,ref_epf,dr,hits,kills,src").split(",")
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames != expected:
            err(f"tiers.csv: başlık beklenenden farklı: {reader.fieldnames}")
            return
        rows = list(reader)
    ids, previous = set(), None
    for n, row in enumerate(rows):
        where = f"tiers.csv satır {n + 2}"
        if row["idx"] != str(n):
            err(f"{where}: idx {row['idx']} (beklenen {n}; ardışık olmalı)")
        if not re.fullmatch(r"[a-z][a-z0-9_]*", row["id"]):
            err(f"{where}: id '{row['id']}' geçersiz (küçük harf/rakam/alt çizgi)")
        if row["id"] in ids:
            err(f"{where}: id '{row['id']}' yinelenmiş")
        ids.add(row["id"])
        try:
            nums = {k: float(row[k]) for k in expected[3:-1]}
        except ValueError as e:
            err(f"{where}: sayı değil ({e})")
            continue
        if any(v < 0 for v in nums.values()):
            err(f"{where}: negatif değer")
        if min(nums["helm"], nums["chest"], nums["legs"], nums["boots"]) < 1:
            err(f"{where}: 1'den az zırh veren parça var")
        if nums["helm"] + nums["chest"] + nums["legs"] + nums["boots"] != nums["total"]:
            err(f"{where}: parça toplamı {nums['helm'] + nums['chest'] + nums['legs'] + nums['boots']:.0f} ≠ takım {nums['total']:.0f}")
        if not 0 <= nums["dr"] <= 90:
            err(f"{where}: azaltma %{row['dr']} 0-90 dışında")
        if previous:  # a higher tier is never weaker
            for key in ("total", "tough", "adur", "sword", "tdur", "mob_hp_mult", "mob_dmg_mult", "mob_bypass_mult", "exphp"):
                if nums[key] < previous[key]:
                    err(f"{where}: {key} önceki kademeden küçük ({row[key]})")
        previous = nums

    for extra in ("tools.csv", "ores.csv"):
        if not (ROOT / "data/balance" / extra).exists():
            err(f"data/balance/{extra} yok → python3 -I tools/balance/tiers.py")
    combat = ROOT / "data/balance/combat.csv"
    needed = {"armor_k1", "armor_k2", "armor_cap", "toughness_half", "ench_scale",
              "attr_max_armor", "attr_max_armor_toughness", "attr_max_health", "attr_max_attack_damage"}
    with open(combat, newline="", encoding="utf-8") as f:
        values = {r["key"]: r["value"] for r in csv.DictReader(f)}
    for key in sorted(needed - set(values)):
        err(f"combat.csv: '{key}' eksik")
    for key in sorted(set(values) - needed):
        err(f"combat.csv: bilinmeyen anahtar '{key}' (check_data.py'ye ekle)")
    for key, value in values.items():
        try:
            if float(value) <= 0:
                err(f"combat.csv: {key} = {value} (pozitif olmalı)")
        except ValueError:
            err(f"combat.csv: {key} = '{value}' sayı değil")
    if not 0 < float(values.get("armor_cap", 0.5)) < 1 or not 0 < float(values.get("ench_scale", 0.5)) <= 1:
        err("combat.csv: armor_cap (0,1) ve ench_scale (0,1] aralığında olmalı")

    # single source: the generators must reproduce the checked-in files byte for byte; the balance bands must hold
    fresh, lines = run_tool("tiers.py", "--check")
    if not fresh:
        err("üretilen denge dosyaları (CSV'ler, oyun JSON'ları) güncel değil → python3 -I tools/balance/tiers.py" + (f" ({lines[-1]})" if lines else ""))
    cat_ok, lines = run_tool("catalysts.py", "--check")     # Faz 7a: katalizör CSV'leri + oyun JSON'u güncel, bütçe bantları yeşil
    if not cat_ok:
        for line in lines[:-1] or lines:
            err("catalysts.py: " + line.strip())
    balanced, lines = run_tool("sim.py", "--check")
    if not balanced:
        for line in lines[:-1] or lines:
            err("sim.py: " + line.strip())
    notes.append("katalizör modeli (catalysts.py): " + ("CSV'ler + oyun JSON'u güncel, ref bütçesi ve bantlar yeşil" if cat_ok else "KIRMIZI"))
    gen = subprocess.run([sys.executable, "-I", str(ROOT / "generator/catalysts.py"), "--check"], capture_output=True, text=True)   # Faz 7b: item/blok/büyü/loot/lang dosyaları güncel mi
    if gen.returncode != 0:
        err("generator/catalysts.py --check kırmızı (üretilen dosyalar bayat; python3 -I generator/catalysts.py):")
        for line in (gen.stdout + gen.stderr).strip().splitlines()[:12]:
            err("  " + line)
    notes.append("katalizör üreteci (generator/catalysts.py): " + ("üretilen dosyalar güncel" if gen.returncode == 0 else "BAYAT"))
    notes.append(f"tiers.csv: {len(rows)} kademe, combat.csv: {len(values)} katsayı" + (", üreteçle özdeş (CSV + oyun JSON'ları)" if fresh else "")
                 + (", denge bantları yeşil" if balanced else ""))


def check_stats():
    """Faz 10: stat/seviye sayıları tek kaynaktan (tools/balance/stats.py) geliyor; XP büyümesi mobs.py'de, tempo modeli ikisini birlikte denetler."""
    for script in ("stats.py", "mobs.py"):
        ok, lines = run_tool(script, "--check")
        if not ok:
            for line in lines[:12]:
                err(f"{script}: " + line.strip())
    notes.append("durum ekranı (stats.py + mobs.py XP): CSV'ler + oyun JSON'u güncel, seviye/stat/bütçe/tempo bantları yeşil")


def check_gear():
    """Faz 6: asıl denetim tools/check_gear.py'de (kendi başına da koşar, negatif testi --break); burada preflight'ın tek kapısına bağlanır."""
    result = subprocess.run([sys.executable, "-I", str(ROOT / "tools/check_gear.py")], capture_output=True, text=True)
    lines = (result.stdout + result.stderr).strip().splitlines()
    if result.returncode != 0 or not lines or lines[-1] != "OK":
        err("check_gear.py kırmızı:")
        for line in lines[:12]:
            err("  " + line.strip())
        return
    notes.extend(line.strip() for line in lines[:-1] if line.strip().startswith(("referanslar", "kazı kapıları")))
    notes.append("gear: check_gear.py OK (şema, üreteç güncel, referanslar, kazı kapıları, sertlik)")


def check_relics():
    """Faz 12: tools/check_relics.py — relic tablosu (CSV) ↔ üretilen öğe/doku/etiket/loot/lang, yuva kapasitesi, güç bütçesi, kaynaklar, Java'da gömülü kimlik yok."""
    result = subprocess.run([sys.executable, "-I", str(ROOT / "tools/check_relics.py")], capture_output=True, text=True)
    lines = (result.stdout + result.stderr).strip().splitlines()
    if result.returncode != 0 or not lines or lines[-1] != "OK":
        err("check_relics.py kırmızı:")
        for line in lines[:12]:
            err("  " + line.strip())
        return
    notes.append("relic'ler: check_relics.py OK (tablo, üretilen dosyalar, yuva kapasitesi, güç bütçesi, kaynaklar, varlıklar)")


def check_world():
    """Faz 9b: tools/check_worldgen.py — biyom/iklim şeması + bölüntü, üreteçler güncel, cevher etiketleri, mob bandı, yapı NBT'leri (Haven mob'suz/ışık), loot."""
    result = subprocess.run([sys.executable, "-I", str(ROOT / "tools/check_worldgen.py")], capture_output=True, text=True)
    lines = (result.stdout + result.stderr).strip().splitlines()
    if result.returncode != 0 or not lines or lines[-1] != "OK":
        err("check_worldgen.py kırmızı:")
        for line in lines[:14]:
            err("  " + line.strip())
        return
    notes.extend(line.strip() for line in lines[:-1] if line.strip().startswith(("biyomlar", "yapılar", "cevherler", "mob/biyom")))
    notes.append("worldgen: check_worldgen.py OK (biyom/iklim, üreteçler, cevher etiketleri, yapı NBT, Haven mob'suzluk/ışık, loot)")


def check_json():
    count = 0
    for path in (MOD / "resources").rglob("*.json"):
        count += 1
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            err(f"json: {path.relative_to(ROOT)}: {e}")
    for sub in ("resources", "biomes", "structures"):                     # Faz 9b: biomes/ ve structures/ üretilen dizinleri de ayrıştırılır
        generated = ROOT / "mods-src/theoria/src/generated" / sub
        if generated.exists():
            for path in generated.rglob("*.json"):
                count += 1
                try:
                    json.loads(path.read_text(encoding="utf-8"))
                except json.JSONDecodeError as e:
                    err(f"json: {path.relative_to(ROOT)}: {e}")
    notes.append(f"json: {count} dosya ayrıştırıldı")


def check_java_size():
    longest = ("", 0)
    for java in (MOD / "java").rglob("*.java"):
        lines = java.read_text(encoding="utf-8").count("\n")
        if lines > longest[1]:
            longest = (java.name, lines)
        if lines > 600:
            err(f"java: {java.relative_to(ROOT)} {lines} satır (> 600)")
    notes.append(f"java: en uzun dosya {longest[0]} ({longest[1]} satır)")


for check in (check_lang, check_tiers, check_stats, check_gear, check_relics, check_world, check_json, check_java_size):
    check()
for note in notes:
    print("  " + note)
if errors:
    print(f"HATA ({len(errors)}):")
    for e in errors:
        print("  - " + e)
    sys.exit(1)
print("OK")
