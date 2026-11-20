#!/usr/bin/env python3
"""Üretilmiş bir dünyanın .mca dosyalarını çevrimdışı tarar (yalnızca okur): biyom payı, Theoria yapı başlangıçları, Theoria cevher bölümleri.

    python3 -I tools/scan_world.py <dünya klasörü | region klasörü> [--dim overworld] [--json]
    python3 -I tools/scan_world.py "$HOME/Library/Application Support/PrismLauncher/instances/Theoria/minecraft/saves/T9-90202"

Faz 9b kapısı için yazıldı: "bölge JSON'u yüklendi" ≠ "dünyada çıkıyor" (TerraBlender 26.3.0.0.9 veri-güdümlü bölgeleri geç yüklüyordu → 6 039 chunk'ta 0 Theoria
biyomu). Bu araç, oyuna girmeden üretilmiş chunk'lardan şunu söyler:
  - her biyomun palet girişi sayısı (bölüm sayısı ≈ alan; 16×16×16'lık bölüm başına bir giriş ya da birkaç), ad alanına göre toplam, Theoria biyomları ayrıca
  - `structures.starts` içindeki geçerli yapı başlangıçları (Haven S/M/L, Harabe, Zindan, Kale → bulunabilirlik/sıklık, en yakın spawn'a uzaklık)
  - `theoria:*_ore` bloklarını içeren bölüm sayısı (cevher bulunurluğu; blok sayısı değil, bölümde var/yok)
Bağımlılıksız (zlib; LZ4 sıkıştırmalı bölge dosyası varsa o chunk'ı atlar ve sayar). Oyun açıkken de okunur (yazılan chunk yarım olabilir → bitince tekrar koş).
"""
import argparse, collections, gzip, json, pathlib, struct, sys, zlib


class LongRef:
    """NBT long dizisine tembel başvuru (blok verisi gibi büyük dizileri açmadan; yalnız gereken uzunlar okunur)."""
    __slots__ = ("buf", "pos", "n")

    def __init__(self, buf, pos, n):
        self.buf, self.pos, self.n = buf, pos, n

    def get(self, i):
        return struct.unpack_from(">q", self.buf, self.pos + 8 * i)[0]


def read_nbt(buf):
    pos = 0

    def u8():
        nonlocal pos
        v = buf[pos]; pos += 1
        return v

    def i32():
        nonlocal pos
        v = struct.unpack_from(">i", buf, pos)[0]; pos += 4
        return v

    def string():
        nonlocal pos
        n = struct.unpack_from(">H", buf, pos)[0]; pos += 2
        v = buf[pos:pos + n].decode("utf-8", "replace"); pos += n
        return v

    def payload(t):
        nonlocal pos
        if t in (1, 2, 3, 4, 5, 6):
            fmt, size = {1: (">b", 1), 2: (">h", 2), 3: (">i", 4), 4: (">q", 8), 5: (">f", 4), 6: (">d", 8)}[t]
            v = struct.unpack_from(fmt, buf, pos)[0]; pos += size
            return v
        if t == 7:
            n = i32(); pos += n; return None
        if t == 8:
            return string()
        if t == 9:
            it = u8(); n = i32()
            return [payload(it) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = u8()
                if tt == 0:
                    return d
                k = string(); d[k] = payload(tt)
        if t == 11:
            n = i32(); pos += 4 * n; return None
        if t == 12:
            n = i32(); ref = LongRef(buf, pos, n); pos += 8 * n
            return ref
        raise ValueError(f"NBT etiketi {t}")

    t = u8(); string()
    return payload(t)


def chunks(path):
    data = path.read_bytes()
    for i in range(1024):
        off = int.from_bytes(data[i * 4:i * 4 + 3], "big")
        if off == 0:
            continue
        p = off * 4096
        ln = struct.unpack_from(">i", data, p)[0]
        comp = data[p + 4]
        raw = data[p + 5:p + 4 + ln]
        try:
            if comp == 2:
                raw = zlib.decompress(raw)
            elif comp == 1:
                raw = gzip.decompress(raw)
            elif comp != 3:
                yield None, f"sıkıştırma={comp}"
                continue
            yield read_nbt(raw), None
        except Exception as e:  # yarım yazılmış chunk
            yield None, type(e).__name__


def surface_biomes(nbt):
    """Chunk'ın 16 örnek sütununda (her 4×4'ün ortası) YÜZEY biyomları (WORLD_SURFACE yüksekliğindeki biyom hücresi). Eksik veri → []."""
    hm = (nbt.get("Heightmaps") or {}).get("WORLD_SURFACE")
    secs = {s.get("Y"): s for s in nbt.get("sections", []) if isinstance(s.get("Y"), int)}
    if not isinstance(hm, LongRef) or not secs:
        return []
    min_y = min(secs) * 16
    out = []
    for qz in range(4):
        for qx in range(4):
            x, z = qx * 4 + 2, qz * 4 + 2
            i = x + z * 16
            h = ((hm.get(i // 7) >> ((i % 7) * 9)) & 0x1FF) - 1 + min_y
            sec = secs.get(h >> 4)
            b = (sec or {}).get("biomes") or {}
            pal = b.get("palette") or []
            if not pal:
                continue
            if len(pal) == 1:
                out.append(pal[0]); continue
            bits = max(1, (len(pal) - 1).bit_length())
            per = 64 // bits
            idx = (((h & 15) >> 2) * 4 + qz) * 4 + qx
            val = (b["data"].get(idx // per) >> ((idx % per) * bits)) & ((1 << bits) - 1)
            out.append(pal[val] if val < len(pal) else pal[0])
    return out


def find_region_dir(arg, dim):
    p = pathlib.Path(arg)
    if (p / "region").is_dir():
        return p / "region"
    for cand in (p / "dimensions" / "minecraft" / dim / "region", p / "DIM" / dim / "region"):
        if cand.is_dir():
            return cand
    if p.name == "region" and p.is_dir():
        return p
    raise SystemExit(f"region klasörü bulunamadı: {arg}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("world")
    ap.add_argument("--dim", default="overworld")
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()
    rdir = find_region_dir(a.world, a.dim)

    biome = collections.Counter(); ores = collections.Counter(); starts = collections.defaultdict(list); surface = collections.Counter()
    n = 0; err = collections.Counter(); status = collections.Counter()
    for f in sorted(rdir.glob("r.*.mca")):
        for nbt, e in chunks(f):
            if e:
                err[e] += 1; continue
            n += 1
            status[str(nbt.get("Status"))] += 1
            if nbt.get("Status") == "minecraft:full":
                for sb in surface_biomes(nbt):
                    surface[sb] += 1
            for sec in nbt.get("sections", []):
                for b in (sec.get("biomes") or {}).get("palette", []) or []:
                    biome[b] += 1
                bs = (sec.get("block_states") or {}).get("palette", []) or []
                # 26.3: blok paleti girişi düz ad (özelliksiz blok) ya da {id|Name, properties} olabilir
                names = {(e2 if isinstance(e2, str) else (e2.get("id") or e2.get("Name"))) for e2 in bs if isinstance(e2, (str, dict))}
                for nm in names:
                    if nm and nm.startswith("theoria:") and "ore" in nm:
                        ores[nm] += 1
            for sname, st in ((nbt.get("structures") or {}).get("starts") or {}).items():
                if isinstance(st, dict) and st.get("id") not in (None, "INVALID"):
                    starts[sname].append((nbt.get("xPos", 0) * 16, nbt.get("zPos", 0) * 16))

    total = sum(biome.values()) or 1
    ours = {k: v for k, v in biome.items() if k.startswith("theoria:")}
    ns = collections.Counter()
    for k, v in biome.items():
        ns[k.split(":")[0]] += v
    out = {
        "region_dir": str(rdir), "chunks": n, "read_errors": dict(err), "status": dict(status),
        "biome_namespaces": dict(ns), "theoria_share_percent": round(100 * sum(ours.values()) / total, 2),
        "theoria_biomes": dict(sorted(ours.items(), key=lambda kv: -kv[1])),
        "structure_starts": {k: len(v) for k, v in sorted(starts.items())},
        "theoria_structure_nearest_to_origin": {
            k: min(v, key=lambda c: c[0] ** 2 + c[1] ** 2) for k, v in sorted(starts.items()) if k.startswith("theoria:")},
        "theoria_ore_sections": dict(ores),
        "surface_samples": sum(surface.values()),
        "surface_share_percent": {k: round(100 * v / (sum(surface.values()) or 1), 2) for k, v in surface.most_common()},
    }
    sv = sum(surface.values()) or 1
    out["theoria_surface_percent"] = round(100 * sum(v for k, v in surface.items() if k.startswith("theoria:")) / sv, 2)
    if a.json:
        print(json.dumps(out, indent=2, ensure_ascii=False)); return
    print(f"{rdir}\n  chunk: {n}  okunamayan: {dict(err) or 0}  durum: {dict(status)}")
    print("  biyom ad alanları (palet girişi):", dict(ns))
    print(f"  THEORIA biyom payı: %{out['theoria_share_percent']}  " + ("" if ours else "← HİÇ YOK"))
    for k, v in out["theoria_biomes"].items():
        print(f"    {v:7d}  {k}")
    print("  yapı başlangıçları (tüm ad alanları):")
    for k, v in out["structure_starts"].items():
        mark = "  ←" if k.startswith("theoria:") else ""
        near = out["theoria_structure_nearest_to_origin"].get(k)
        print(f"    {v:5d}  {k}{mark}" + (f"  en yakın (x,z)={near}" if near else ""))
    print("  theoria cevher bölümleri:", out["theoria_ore_sections"] or "yok")
    print(f"  YÜZEY (tam chunk'lar, {out['surface_samples']} örnek sütun): Theoria %{out['theoria_surface_percent']}")
    for k, v in list(out["surface_share_percent"].items())[:30]:
        print(f"    %{v:5.2f}  {k}" + ("  ←" if k.startswith("theoria:") else ""))


if __name__ == "__main__":
    main()
