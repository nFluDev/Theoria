#!/usr/bin/env python3
"""Tuş çakışması taraması: oyunun yazdığı options.txt'deki key_* satırlarında aynı tuşa bağlı birden fazla OYUN-İÇİ eylem.

    python3 -I tools/pack/keys.py [instance_adı=Theoria]

Bilerek yok sayılanlar: F3 hata ayıklama kısayolları (`key.debug.*`, F3 ile birlikte basılır), JEI tuşları (`key.jei.*`; yalnızca ekran
bağlamında), değiştiricili bağlamalar (`key.mouse.left:SHIFT` …). Kalanlar gerçek çakışmadır: ikisi de oyun-içinde aynı tuşta tetiklenir.
İstenen çakışmalar ALLOWED'da (ör. Jade ayrıntıları Shift basılıyken: "gizlen" ile aynı tuş bilerek). Çıkış 0 = çakışma yok.
"""
import collections, pathlib, sys

name = sys.argv[1] if len(sys.argv) > 1 else "Theoria"
opts = pathlib.Path.home() / f"Library/Application Support/PrismLauncher/instances/{name}/minecraft/options.txt"
if not opts.exists():
    raise SystemExit(f"{opts} yok (oyunu bir kez çalıştır)")
IGNORE_PREFIX = ("key.debug.", "key.jei.")
ALLOWED = [{"key.sneak", "key.jade.show_details"}, {"key.pickItem", "key.spectatorHotbar"}]   # ikincisi vanilla'nın kendi (gözlemci) ikilisi
by_key = collections.defaultdict(list)
for line in opts.read_text().splitlines():
    if not line.startswith("key_"):
        continue
    action, key = line[4:].split(":", 1)
    if ":" in key or key.endswith(".unknown") or action.startswith(IGNORE_PREFIX):
        continue
    by_key[key].append(action)
bad = 0
for key, actions in sorted(by_key.items()):
    if len(actions) > 1 and not any(set(actions) <= ok for ok in ALLOWED):
        print(f"ÇAKIŞMA {key}: {', '.join(actions)}")
        bad += 1
print(f"# {bad} oyun-içi çakışma")
sys.exit(1 if bad else 0)
