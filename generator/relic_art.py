"""Relic icons (Faz 12): 16×16 pixel art drawn from a few primitives — no vanilla texture, no PIL. One shape per kind of object (ring, pendant, belt, …), tinted by the relic's
metal and gem colours (the `art` column of data/relics.csv: shape/#metal/#gem). Deterministic (same input → same bytes) so `generator/relics.py --check` can compare pixels.

Each shape returns a canvas {(x, y): (r, g, b, a)}; `render` adds a 1-pixel dark outline and a top-left highlight so the small icons read against any background.
"""
import colorsys
import math
import pngio

SIZE = 16


def shade(rgb, factor):
    """Multiply brightness (factor < 1 darker, > 1 lighter, clamped)."""
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in rgb))
    r, g, b = colorsys.hsv_to_rgb(h, max(0.0, min(1.0, s * (1.0 if factor <= 1 else 0.85))), max(0.0, min(1.0, v * factor)))
    return round(r * 255), round(g * 255), round(b * 255)


class Canvas:
    def __init__(self):
        self.px = {}

    def put(self, x, y, rgb):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.px[(x, y)] = rgb

    def disc(self, cx, cy, r, rgb, inner=None):
        for y in range(SIZE):
            for x in range(SIZE):
                d = math.hypot(x - cx, y - cy)
                if d <= r and (inner is None or d >= inner):
                    self.put(x, y, rgb)

    def rect(self, x0, y0, x1, y1, rgb):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, rgb)

    def line(self, x0, y0, x1, y1, rgb):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(steps + 1):
            self.put(round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps), rgb)

    def diamond(self, cx, cy, r, rgb):
        for y in range(SIZE):
            for x in range(SIZE):
                if abs(x - cx) + abs(y - cy) <= r:
                    self.put(x, y, rgb)


def gem(c, cx, cy, r, color):
    c.diamond(cx, cy, r, shade(color, 0.7))
    c.diamond(cx, cy, max(r - 1, 0), color)
    c.put(cx - 1, cy - 1, shade(color, 1.55))


def ring(c, metal, stone):
    c.disc(8, 10, 5.4, shade(metal, 0.8), inner=3.1)
    c.disc(8, 10, 4.6, metal, inner=3.4)
    c.put(5, 8, shade(metal, 1.5))
    c.put(6, 7, shade(metal, 1.5))
    gem(c, 8, 4, 2, stone)


def pendant(c, metal, stone):
    c.line(2, 1, 7, 9, shade(metal, 0.85))
    c.line(13, 1, 8, 9, shade(metal, 0.85))
    c.line(3, 1, 7, 8, metal)
    c.line(12, 1, 8, 8, metal)
    c.disc(8, 10, 2.2, shade(metal, 0.8))
    gem(c, 8, 12, 3, stone)


def belt(c, metal, stone):
    c.rect(1, 6, 14, 10, shade(metal, 0.7))
    c.rect(1, 7, 14, 9, metal)
    for x in range(2, 14, 3):
        c.put(x, 8, shade(metal, 0.75))
    c.rect(6, 5, 9, 11, shade(metal, 1.2))
    c.rect(7, 6, 8, 10, shade(metal, 0.7))
    gem(c, 7, 8, 1, stone)
    c.put(8, 8, stone)


def bracelet(c, metal, stone):
    c.disc(8, 9, 6.2, shade(metal, 0.75), inner=3.2)
    c.disc(8, 9, 5.5, metal, inner=3.9)
    for ang in (200, 270, 340):
        c.put(round(8 + 4.7 * math.cos(math.radians(ang))), round(9 + 4.7 * math.sin(math.radians(ang))), stone)
    c.put(4, 6, shade(metal, 1.5))


def charm(c, metal, stone):
    c.disc(8, 3, 2.4, shade(metal, 0.8), inner=1.2)
    c.line(8, 5, 8, 7, shade(metal, 0.85))
    gem(c, 8, 10, 4, stone)
    c.put(8, 13, shade(stone, 0.7))


def clover(c, metal, stone):
    for cx, cy in ((5, 5), (11, 5), (5, 11), (11, 11)):
        c.disc(cx, cy, 3.1, shade(stone, 0.8))
        c.disc(cx, cy, 2.4, stone)
    c.disc(8, 8, 2.6, shade(stone, 0.9))
    c.line(8, 9, 11, 14, shade(metal, 0.9))
    c.put(5, 4, shade(stone, 1.5))
    c.put(11, 4, shade(stone, 1.5))


def apple(c, metal, stone):
    c.disc(6, 9, 4.6, shade(metal, 0.8))
    c.disc(10, 9, 4.6, shade(metal, 0.8))
    c.disc(6, 9, 3.8, metal)
    c.disc(10, 9, 3.8, metal)
    c.rect(6, 5, 10, 13, metal)
    c.line(8, 3, 9, 5, shade((120, 80, 40), 1.0))
    c.disc(11, 3, 2.0, stone)
    c.put(5, 8, shade(metal, 1.55))
    c.put(5, 7, shade(metal, 1.4))
    gem(c, 8, 10, 1, stone)


def cloak(c, metal, stone):
    for y in range(2, 15):
        half = 3 + (y - 2) * 0.45
        c.rect(round(8 - half), y, round(7 + half), y, metal if y % 5 else shade(metal, 0.85))
    c.rect(4, 2, 11, 3, shade(metal, 1.25))
    gem(c, 8, 4, 1, stone)
    c.line(5, 4, 4, 14, shade(metal, 0.75))
    c.line(10, 4, 11, 14, shade(metal, 0.75))


def pack(c, metal, stone):
    c.rect(3, 4, 12, 14, shade(metal, 0.75))
    c.rect(4, 5, 11, 13, metal)
    c.rect(4, 4, 11, 6, shade(metal, 1.2))
    c.rect(5, 9, 10, 12, shade(metal, 0.85))
    c.rect(7, 8, 8, 9, stone)
    c.line(2, 3, 2, 12, shade(metal, 0.7))
    c.line(13, 3, 13, 12, shade(metal, 0.7))
    c.rect(5, 2, 10, 3, shade(metal, 0.9))


def circlet(c, metal, stone):
    for x in range(1, 15):
        y = 11 - round(5 * math.sin(math.pi * (x - 1) / 13))
        c.put(x, y, metal)
        c.put(x, y + 1, shade(metal, 0.75))
    c.line(1, 11, 1, 13, shade(metal, 0.75))
    c.line(14, 11, 14, 13, shade(metal, 0.75))
    gem(c, 8, 4, 2, stone)
    c.put(4, 7, shade(metal, 1.5))


def scale(c, metal, stone):
    c.disc(8, 8, 6.8, shade(metal, 0.7))
    c.disc(8, 8, 6.0, metal)
    for (cx, cy) in ((5, 6), (8, 6), (11, 6), (6, 9), (10, 9), (8, 12)):
        c.disc(cx, cy, 1.9, shade(metal, 0.82), inner=1.1)
    c.put(5, 4, shade(metal, 1.55))
    gem(c, 8, 8, 1, stone)


def glove(c, metal, stone):
    c.rect(4, 8, 11, 14, shade(metal, 0.75))
    c.rect(5, 8, 10, 13, metal)
    for x in (4, 6, 8, 10):
        c.rect(x, 2, x + 1, 8, shade(metal, 0.8))
        c.rect(x, 3, x, 8, metal)
    c.rect(1, 7, 3, 10, shade(metal, 0.8))
    c.rect(1, 7, 2, 9, metal)
    c.rect(4, 12, 11, 14, shade(stone, 0.9))
    c.put(5, 9, shade(metal, 1.5))


def anklet(c, metal, stone):
    c.disc(8, 6, 5.2, shade(metal, 0.8), inner=3.0)
    c.disc(8, 6, 4.5, metal, inner=3.4)
    for x in (4, 8, 12):
        c.line(x, 10, x, 12, shade(metal, 0.85))
        c.disc(x, 13, 1.4, stone)
    c.put(5, 4, shade(metal, 1.5))


def feather(c, metal, stone):
    # a filled leaf along the bottom-left → top-right diagonal, a darker quill down its middle
    x0, y0, x1, y1 = 2.0, 14.0, 14.0, 2.0
    length = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / length, (y1 - y0) / length
    for y in range(SIZE):
        for x in range(SIZE):
            along = ((x - x0) * ux + (y - y0) * uy) / length
            across = (x - x0) * -uy + (y - y0) * ux
            if 0.12 <= along <= 1.0 and abs(across) <= 3.4 * math.sin(math.pi * min(along, 1.0)) ** 0.7 + 0.5:
                c.put(x, y, shade(metal, 1.0 if across < 0 else 0.82))
    c.line(2, 14, 12, 4, shade(stone, 0.9))
    c.line(2, 14, 4, 12, shade((150, 110, 70), 1.0))
    c.put(12, 3, shade(stone, 1.4))
    c.put(9, 6, shade(metal, 1.45))


SHAPES = {"ring": ring, "pendant": pendant, "belt": belt, "bracelet": bracelet, "charm": charm, "clover": clover, "apple": apple, "cloak": cloak, "pack": pack,
          "circlet": circlet, "scale": scale, "glove": glove, "anklet": anklet, "feather": feather}

def render(shape, metal, stone):
    canvas = Canvas()
    SHAPES[shape](canvas, metal, stone)
    pixels = dict(canvas.px)
    outline = {}
    for (x, y) in pixels:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if p not in pixels and 0 <= p[0] < SIZE and 0 <= p[1] < SIZE:
                outline[p] = (30, 24, 36)
    pixels.update(outline)
    return [[(*pixels[(x, y)], 255) if (x, y) in pixels else (0, 0, 0, 0) for x in range(SIZE)] for y in range(SIZE)]


def parse(art):
    """'ring/#c8c8d7/#6e46dc' → (shape, metal rgb, gem rgb); raises ValueError on anything else."""
    shape, metal, stone = art.split("/")
    if shape not in SHAPES:
        raise ValueError(f"unknown shape '{shape}' (one of {sorted(SHAPES)})")
    return shape, tuple(int(metal[i:i + 2], 16) for i in (1, 3, 5)), tuple(int(stone[i:i + 2], 16) for i in (1, 3, 5))


def icon(art):
    shape, metal, stone = parse(art)
    return pngio.encode(render(shape, metal, stone))
