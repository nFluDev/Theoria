"""Bağımlılıksız PNG okuma/yazma (PIL yok). Üreteçler (generator/hud.py) ve denetimler (tools/check_hud.py, hud_preview.py) paylaşır.

Kapsam bilerek dar: interlace'siz, 1/2/4/8 bit, renk tipleri 0 (gri) 2 (RGB) 3 (palet) 4 (gri+alfa) 6 (RGBA).
Beklenmeyen biçim → AssertionError (sessizce yanlış piksel üretmek yerine dur).
Piksel = (r, g, b, a) demeti; satır listesi `rows[y][x]`.
"""
import struct
import zlib

SIGNATURE = b"\x89PNG\r\n\x1a\n"
CHANNELS = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}


def decode(raw):
    """PNG baytları → (genişlik, yükseklik, rows)."""
    assert raw[:8] == SIGNATURE, "PNG imzası yok"
    pos, idat, header, palette, trns = 8, b"", None, None, None
    while pos < len(raw):
        length = struct.unpack(">I", raw[pos:pos + 4])[0]
        tag, body = raw[pos + 4:pos + 8], raw[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif tag == b"IDAT":
            idat += body
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        pos += 12 + length
    width, height, depth, ctype, _comp, _filt, interlace = header
    assert interlace == 0 and depth in (1, 2, 4, 8) and ctype in CHANNELS, \
        f"beklenmeyen PNG biçimi: derinlik={depth} tip={ctype} interlace={interlace}"
    assert depth == 8 or ctype in (0, 3), f"{depth} bit yalnız gri/palet için destekli"
    channels = CHANNELS[ctype]
    bits_per_pixel = channels * depth
    stride = (width * bits_per_pixel + 7) // 8
    bpp = max(1, bits_per_pixel // 8)  # filtre için önceki baytın uzaklığı
    data = zlib.decompress(idat)
    rows, previous, offset = [], bytes(stride), 0
    for _ in range(height):
        filter_type = data[offset]
        line = bytearray(data[offset + 1:offset + 1 + stride])
        offset += 1 + stride
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = previous[i]
            c = previous[i - bpp] if i >= bpp else 0
            if filter_type == 1:
                line[i] = (line[i] + a) & 255
            elif filter_type == 2:
                line[i] = (line[i] + b) & 255
            elif filter_type == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif filter_type == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        previous = bytes(line)
        rows.append([_pixel(line, x, depth, ctype, palette, trns) for x in range(width)])
    return width, height, _colour_key(rows, ctype, depth, trns)


def _colour_key(rows, ctype, depth, trns):
    """tRNS, gri (0) ve RGB (2) PNG'lerde 'bu renk saydamdır' demektir (vanilla külçe/alet dokuları böyle). Yok sayılırsa köşeler opak siyah çıkar."""
    if not trns:
        return rows
    if ctype == 0 and len(trns) >= 2:
        key = int.from_bytes(trns[:2], "big")
        gray = key & 255 if depth == 8 else key * 255 // ((1 << depth) - 1)
        return [[(px[0], px[1], px[2], 0) if px[0] == gray else px for px in row] for row in rows]
    if ctype == 2 and depth == 8 and len(trns) >= 6:
        key = (trns[1], trns[3], trns[5])
        return [[(px[0], px[1], px[2], 0) if px[:3] == key else px for px in row] for row in rows]
    return rows


def _pixel(line, x, depth, ctype, palette, trns):
    if depth == 8:
        c = CHANNELS[ctype]
        px = line[x * c:(x + 1) * c]
        if ctype == 6:
            return tuple(px)
        if ctype == 4:
            return (px[0], px[0], px[0], px[1])
        if ctype == 2:
            return (px[0], px[1], px[2], 255)
        if ctype == 0:
            return (px[0], px[0], px[0], 255)
        value = px[0]
    else:
        per_byte = 8 // depth
        shift = 8 - depth - (x % per_byte) * depth
        value = (line[x // per_byte] >> shift) & ((1 << depth) - 1)
        if ctype == 0:
            gray = value * 255 // ((1 << depth) - 1)
            return (gray, gray, gray, 255)
    r, g, b = palette[value * 3:value * 3 + 3]
    return (r, g, b, trns[value] if trns and value < len(trns) else 255)


def encode(rows):
    """rows → RGBA 8 bit PNG baytları (süzgeçsiz; aynı girdi → aynı bayt)."""
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in rows)

    def chunk(tag, body):
        data = tag + body
        return struct.pack(">I", len(body)) + data + struct.pack(">I", zlib.crc32(data) & 0xFFFFFFFF)

    return (SIGNATURE + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
