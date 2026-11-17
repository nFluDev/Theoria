"""Vanilla 26.3 kaynaklarına erişim (doku şablonları + tag/loot okuması). Üreteçler ve denetimler paylaşır.

Kaynak sırası: THEORIA_VANILLA_JAR → Prism kütüphanesi (istemci jar'ı) → Gradle'ın yamalı jar'ı. Hiçbiri yoksa `None`:
üretilmiş dosyalar git'te olduğu için jar yalnızca YENİDEN ÜRETİRKEN gerekir; denetimler jar yoksa ilgili adımı atlar.
Sonuçlar belleğe alınır (her çağrıda zip açılmaz).
"""
import os
import pathlib
import re
import zipfile

import pngio

ROOT = pathlib.Path(__file__).resolve().parents[1]
_cache = {"zip": None, "path": None, "looked": False}


def jar_path():
    env = os.environ.get("THEORIA_VANILLA_JAR")
    candidates = [pathlib.Path(env)] if env else []
    candidates.append(pathlib.Path.home() / "Library/Application Support/PrismLauncher/libraries/com/mojang/minecraft/26.3/minecraft-26.3-client.jar")
    artifacts = ROOT / "mods-src/theoria/build/moddev/artifacts"
    if artifacts.is_dir():
        candidates += sorted(p for p in artifacts.glob("minecraft-patched-*.jar") if re.fullmatch(r"minecraft-patched-[\d.]+\.jar", p.name))
    return next((p for p in candidates if p.is_file()), None)


def _zip():
    if not _cache["looked"]:
        _cache["looked"] = True
        _cache["path"] = jar_path()
        _cache["zip"] = zipfile.ZipFile(_cache["path"]) if _cache["path"] else None
    return _cache["zip"]


def available():
    return _zip() is not None


def read(name):
    """Jar içindeki bir dosyanın baytları; yoksa None."""
    z = _zip()
    if z is None:
        return None
    try:
        return z.read(name)
    except KeyError:
        return None


def texture(name):
    """assets/minecraft/textures/<name>.png → (w, h, rows). Yoksa SystemExit (şablon eksikse üretim durmalı)."""
    raw = read(f"assets/minecraft/textures/{name}.png")
    if raw is None:
        raise SystemExit(f"vanilla: şablon doku yok: {name} (jar: {_cache['path']}). THEORIA_VANILLA_JAR ile 26.3 istemci jar'ını göster.")
    return pngio.decode(raw)


def names(prefix):
    z = _zip()
    return [] if z is None else [n for n in z.namelist() if n.startswith(prefix)]
