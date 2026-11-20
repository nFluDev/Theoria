#!/usr/bin/env python3
"""Türkçe dil tamamlama paketi (Faz 13, 2026-11-20).

Pakete giren üçüncü taraf modların `tr_tr.json`'ı eksik ya da hiç yok. Oyun dili Türkçe seçilince o satırlar İngilizce kalır. Bu üreteç, `data/lang_tr/<ad_alanı>.json`'daki
(anahtar → Türkçe) çevirileri `src/generated/lang_tr/assets/<ad_alanı>/lang/tr_tr.json` olarak üretir; Theoria jar'ı bunları taşır, Minecraft aynı yoldaki dosyaları
modların kendilerininkiyle BİRLEŞTİRİR (yığın): modun kendi çevirisi varsa o anahtar `data/lang_tr`'ye hiç yazılmaz (yalnız EKSİK anahtarlar çevrilir).

Kural (kullanıcı): özel adlar ve oyun-içi terimler İngilizce kalır — Waystone, Sharestone, Portstone, Warp Plate, Sophisticated …, mod adları; eşya/blok adı olan satırlar çevrilmez.
Kapsam dışı (bilinçli): ayar ekranı/ayar açıklaması satırları (Xaero harita ayarları, ModernFix, Sodium, Collective, Sound Physics…) — oyun içinde görünmeyen yönetici metinleri.

  python3 -I generator/lang_tr.py            # yaz
  python3 -I generator/lang_tr.py --check    # güncel mi + biçim
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "data/lang_tr"
OUT = ROOT / "mods-src/theoria/src/generated/lang_tr"
NS = re.compile(r"^[a-z0-9_.\-]+$")


def outputs():
    files = {}
    for p in sorted(SRC.glob("*.json")):
        ns = p.stem
        if not NS.match(ns):
            raise SystemExit(f"{p.name}: geçersiz ad alanı")
        data = json.loads(p.read_text(encoding="utf-8"))
        for k, v in data.items():
            if not isinstance(v, str) or not v.strip():
                raise SystemExit(f"{p.name}: '{k}' boş ya da metin değil")
        files[OUT / f"assets/{ns}/lang/tr_tr.json"] = json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    return files


def main():
    files = outputs()
    if "--check" in sys.argv:
        bad = [str(p.relative_to(ROOT)) for p, t in files.items() if not p.exists() or p.read_text(encoding="utf-8") != t]
        stale = [str(p.relative_to(ROOT)) for p in OUT.rglob("tr_tr.json") if p not in files] if OUT.exists() else []
        if bad or stale:
            print("BAYAT:", ", ".join(bad + stale), "→ python3 -I generator/lang_tr.py")
            raise SystemExit(1)
        print(f"lang_tr: {len(files)} ad alanı, {sum(len(json.loads(t)) for t in files.values())} çeviri güncel")
        return
    if OUT.exists():
        for p in OUT.rglob("tr_tr.json"):
            p.unlink()
    for p, t in files.items():
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(t, encoding="utf-8")
    print(f"lang_tr: {len(files)} ad alanı yazıldı")


if __name__ == "__main__":
    main()
