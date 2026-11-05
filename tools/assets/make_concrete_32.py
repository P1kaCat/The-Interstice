"""32x32 versions of the original concrete and metal family, 4 variants each (2026-11-05).

The first textures of the project were 16x16 procedural placeholders; next to the 32x32 PixelLab materials they
looked coarse, and the cracked concrete repeated the same marks on every block. Each material is now drawn at
32x32 in its original colours (read from the old 16x16 file, kept as <name>_legacy16.png the first time), four
times with different noise seeds and cracks: <name>.png (variant 0) and <name>_1..3.png. The blockstate picks one
at random per block position (vanilla weighted variants), so nothing lines up. Panels keep their seams, plates
their rivets, the grate its holes (alpha).

    python -I tools/assets/make_concrete_32.py
"""
import json
import os
import shutil

import numpy as np
from PIL import Image, PngImagePlugin
from scipy.ndimage import gaussian_filter

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "interstice")
TEX = os.path.join(ROOT, "textures", "block")
N = 32


def noise(sigma, seed):
    r = np.random.default_rng(seed)
    n = gaussian_filter(r.standard_normal((N, N)), sigma, mode="wrap")
    return (n - n.mean()) / (n.std() + 1e-9)


def walk_cracks(count, seed, length=(12, 26)):
    r = np.random.default_rng(seed)
    m = np.zeros((N, N))
    for _ in range(count):
        x, y = r.random() * N, r.random() * N
        ang = r.random() * 6.28
        for _ in range(int(r.integers(*length))):
            m[int(y) % N, int(x) % N] = 1
            if r.random() < 0.25:
                m[int(y + 1) % N, int(x) % N] = 0.5
            ang += r.normal(0, 0.5)
            x += np.cos(ang)
            y += np.sin(ang)
    return m


def legacy_colour(name):
    src = os.path.join(TEX, name + ".png")
    keep = os.path.join(os.path.dirname(__file__), "..", "archive", "textures_legacy16", name + "_legacy16.png")
    if not os.path.exists(keep):
        shutil.copyfile(src, keep)
    a = np.asarray(Image.open(keep).convert("RGBA")).astype(float)
    opaque = a[..., 3] > 10
    return a[..., :3][opaque].mean(0)


def concrete(base, seed, grain=7, mottle=8, cracks=0, crack_dark=0.55, speck=0.08):
    r = np.random.default_rng(seed)
    lum = r.standard_normal((N, N)) * grain + noise(3.5, seed + 1) * mottle + noise(1.2, seed + 2) * grain * 0.6
    lum += (r.random((N, N)) > 1 - speck) * r.uniform(6, 16, (N, N)) - (r.random((N, N)) < speck * 0.7) * 10
    rgb = base[None, None, :] + lum[..., None]
    if cracks:
        c = walk_cracks(cracks, seed + 3)
        rgb *= (1 - (1 - crack_dark) * c)[..., None]
    return rgb


def save(name, rgb, alpha=None):
    a = np.full((N, N), 255.0) if alpha is None else alpha
    info = PngImagePlugin.PngInfo()
    info.add_text("interstice", "32x32 procedural (tools/assets/make_concrete_32.py), original colours")
    Image.fromarray(np.dstack([np.clip(rgb, 0, 255), a]).astype(np.uint8), "RGBA").save(os.path.join(TEX, name + ".png"), pnginfo=info)


def variants(name, make, model_parent="minecraft:block/cube_all", tex_key="all", alpha=None):
    names = [name] + [f"{name}_{i}" for i in (1, 2, 3)]
    for i, n in enumerate(names):
        out = make(i)
        if isinstance(out, tuple):
            save(n, out[0], out[1])
        else:
            save(n, out)
    for i, n in enumerate(names):
        p = os.path.join(ROOT, "models", "block", f"{n}.json") if i else None
        if p:
            with open(p, "w", encoding="utf-8", newline="\n") as f:
                json.dump({"parent": model_parent, "textures": {tex_key: f"interstice:block/{n}"}}, f, indent=2)
    bs = os.path.join(ROOT, "blockstates", f"{name}.json")
    with open(bs, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"variants": {"": [{"model": f"interstice:block/{n}"} for n in names]}}, f, indent=2)
    print("wrote", name, "x4")


if __name__ == "__main__":
    c = {n: legacy_colour(n) for n in ("weathered_concrete", "concrete_panel", "stained_concrete", "cracked_concrete",
                                         "concrete_floor", "concrete_rubble", "rusted_plate", "dark_metal", "metal_grate")}

    variants("weathered_concrete", lambda i: concrete(c["weathered_concrete"], 100 + i, cracks=(1 if i == 3 else 0)))

    def panel(i):
        rgb = concrete(c["concrete_panel"], 200 + i, mottle=6)
        rgb[0:2, :] *= 0.72
        rgb[:, 0:2] *= 0.72
        rgb[30:32, :] *= 0.9
        return rgb
    variants("concrete_panel", panel)

    def stained(i):
        rgb = concrete(c["stained_concrete"], 300 + i, mottle=10)
        runs = gaussian_filter(np.random.default_rng(310 + i).random(N), 1.2, mode="wrap")[None, :]
        streak = np.clip((runs - runs.mean()) * 8, -1, 1) * np.linspace(0.3, 1.0, N)[:, None]
        rgb *= (1 - 0.18 * np.clip(streak, 0, 1))[..., None]
        rgb[..., 2] *= 0.94
        return rgb
    variants("stained_concrete", stained)

    variants("cracked_concrete", lambda i: concrete(c["cracked_concrete"], 400 + i, cracks=2 + (i & 1), crack_dark=0.42))

    def floor(i):
        rgb = concrete(c["concrete_floor"], 500 + i, grain=6, mottle=6, cracks=(1 if i in (1, 3) else 0), crack_dark=0.7)
        rgb[15:17, :] *= 0.88   # the old joint through the middle, softer
        return rgb
    variants("concrete_floor", floor)

    def rubble(i):
        r = np.random.default_rng(600 + i)
        rgb = concrete(c["concrete_rubble"], 600 + i, grain=10, mottle=10)
        # chunks: lighter stones with a dark lower edge
        for _ in range(9):
            x, y, s = r.integers(0, N), r.integers(0, N), r.integers(3, 7)
            for dy in range(s):
                for dx in range(s):
                    if (dx - s / 2) ** 2 + (dy - s / 2) ** 2 < (s / 2) ** 2:
                        k = 1.12 if dy < s - 1 else 0.7
                        rgb[(y + dy) % N, (x + dx) % N] *= k
        return rgb
    variants("concrete_rubble", rubble)

    def plate(i):
        base = c["rusted_plate"]
        rgb = base[None, None, :] + (np.random.default_rng(700 + i).standard_normal((N, N)) * 6 + noise(3, 701 + i) * 12)[..., None]
        rust = np.clip(noise(2.5, 702 + i) * 0.6 + 0.2, 0, 1)
        rgb = rgb * (1 - 0.25 * rust[..., None]) + np.array([96, 50, 28]) * 0.25 * rust[..., None]
        rgb[[0, 1, 30, 31], :] *= 0.78
        rgb[:, [0, 1, 30, 31]] *= 0.78
        for (y, x) in ((4, 4), (4, 27), (27, 4), (27, 27)):
            rgb[y:y + 2, x:x + 2] = base * 1.35
        return rgb
    variants("rusted_plate", plate)

    def dark(i):
        rgb = c["dark_metal"][None, None, :] + (np.random.default_rng(800 + i).standard_normal((N, N)) * 3 + noise(3, 801 + i) * 5)[..., None]
        rgb[::8, :] *= 0.8   # every 8 px: the lines run on across blocks (10 left a double line at the joints)
        return rgb
    variants("dark_metal", dark)

    def grate(i):
        rgb = c["metal_grate"][None, None, :] + (np.random.default_rng(900 + i).standard_normal((N, N)) * 6 + noise(3, 901 + i) * 6)[..., None]
        alpha = np.full((N, N), 255.0)
        for y in range(N):
            for x in range(N):
                if x % 8 >= 2 and y % 8 >= 2:
                    alpha[y, x] = 0
        rgb[alpha > 0] *= 1.0
        return rgb, alpha
    variants("metal_grate", grate)
