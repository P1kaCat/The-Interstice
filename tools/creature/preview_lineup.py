"""
Quick offline preview of the creature family at rest: every models/<species>.bbmodel rasterised with flat
lambert shading (painter's algorithm), side by side, profile and three-quarter rows, with a 2-block
player-height bar for scale. Development aid only (the in-game captures are the real validation).

Usage: python -I tools/creature/preview_lineup.py [out.png]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ORDER = ["adherent", "crawler", "strider", "burden", "clinger", "hollow", "watcher", "fractured"]
PX = 9   # image pixels per model px (16 model px = 1 block)


def load(name):
    m = json.load(open(os.path.join(ROOT, "models", f"{name}.bbmodel"), encoding="utf-8"))
    tris = []
    for e in m["elements"]:
        o = np.array(e["origin"], float)
        V = {k: o + np.array(v, float) for k, v in e["vertices"].items()}
        dark = "nail" in e["name"] or "mouth" in e["name"]
        for f in e["faces"].values():
            vs = [V[k] for k in f["vertices"]]
            for i in range(1, len(vs) - 1):
                tris.append((vs[0], vs[i], vs[i + 1], dark))
    return tris


def render(tris, yaw, w, h):
    img = Image.new("RGB", (w, h), (205, 205, 200))
    d = ImageDraw.Draw(img)
    c, s = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    R = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    light = np.array([0.4, 0.8, -0.45]); light /= np.linalg.norm(light)
    items = []
    for a, b, cc, dark in tris:
        A, B, C = R @ a, R @ b, R @ cc
        n = np.cross(B - A, C - A)
        ln = np.linalg.norm(n)
        if ln < 1e-9:
            continue
        n /= ln
        shade = 0.35 + 0.65 * abs(float(n @ light))
        items.append(((A[2] + B[2] + C[2]) / 3, (A, B, C), shade, dark))
    items.sort(key=lambda t: -t[0])
    for _, (A, B, C), shade, dark in items:
        base = (40, 36, 36) if dark else (150, 138, 134)
        col = tuple(int(v * shade) for v in base)
        d.polygon([(w / 2 + p[0] * PX, h - 12 - p[1] * PX) for p in (A, B, C)], fill=col)
    d.rectangle([8, h - 12 - 32 * PX, 14, h - 12], fill=(60, 90, 160))   # 2 blocks
    return img


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs", "creature", "family_lineup_preview.png")
    names = [n for n in ORDER if os.path.exists(os.path.join(ROOT, "models", f"{n}.bbmodel"))]
    w, h = 76 * PX, 50 * PX
    sheet = Image.new("RGB", (w * len(names), h * 2 + 20), (230, 230, 226))
    d = ImageDraw.Draw(sheet)
    for i, n in enumerate(names):
        tris = load(n)
        sheet.paste(render(tris, 90, w, h), (i * w, 20))
        sheet.paste(render(tris, 140, w, h), (i * w, 20 + h))
        d.text((i * w + 10, 4), n, fill=(0, 0, 0))
    sheet = sheet.resize((sheet.width // 2, sheet.height // 2), Image.LANCZOS)
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
