#!/usr/bin/env python3
"""Prism "Theoria" oynanış instance'ındaki Theoria jar'ı derleme çıktısıyla aynı mı? (Faz 6 sonrası öğrenilen ders)

    python3 -I tools/pack/sync_jar.py            # denetle: aynıysa OK, farklıysa çıkış kodu 1
    python3 -I tools/pack/sync_jar.py --apply    # eski jar'ı sil, güncelini kopyala (instance.cfg / ayarlar / dünyalara DOKUNMAZ)
    python3 -I tools/pack/sync_jar.py --name Theoria-Diag   # başka instance

Neden: Faz 6'da test geçici bir instance'ta yapıldı, asıl "Theoria" instance'ı Faz 5'teki eski jar'la kaldı; kullanıcı oyunda hiçbir
Gear içeriği göremedi. `make_instance.py` instance.cfg'yi baştan yazar (oynama süresi vb. sıfırlanır); bu araç yalnızca jar'ı değiştirir.
Instance yoksa (başka makine, Prism yok) denetim geçer.
"""
import hashlib, pathlib, shutil, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
LIBS = ROOT / "mods-src/theoria/build/libs"
INSTANCES = pathlib.Path.home() / "Library/Application Support/PrismLauncher/instances"


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main():
    args = sys.argv[1:]
    name = args[args.index("--name") + 1] if "--name" in args else "Theoria"
    mods = INSTANCES / name / "minecraft/mods"
    if not mods.is_dir():
        print(f"OK (instance yok: {name})")
        return 0
    jars = sorted(LIBS.glob("theoria-*.jar"), key=lambda p: p.stat().st_mtime)
    if not jars:
        print("Theoria jar'ı yok: cd mods-src/theoria && ./gradlew build")
        return 1
    built = jars[-1]
    current = sorted(mods.glob("theoria-*.jar"))
    if len(current) == 1 and current[0].name == built.name and sha(current[0]) == sha(built):
        print(f"OK ({name}: {built.name} güncel)")
        return 0
    if "--apply" in args:
        for old in current:
            old.unlink()
        shutil.copy2(built, mods / built.name)
        print(f"OK ({name}: {built.name} kopyalandı; önceki: {[p.name for p in current] or 'yok'})")
        return 0
    print(f"ESKİ: {name} instance'ındaki jar derleme çıktısından farklı ({[p.name for p in current] or 'jar yok'} ≠ {built.name}). "
          f"Yenile: python3 -I tools/pack/sync_jar.py --apply")
    return 1


if __name__ == "__main__":
    sys.exit(main())
