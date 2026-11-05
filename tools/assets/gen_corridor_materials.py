"""TEMPORARY development textures for the Corridor's building materials (2026-11-05, after play feedback: the
regions looked like vanilla blocks mixed with modded ones). One coherent family with the existing concrete and
rust: low saturation, grime, soot, water stains, flat lighting. Final art: docs/PIXELLAB_ASSETS.md, batch
"Corridor materials". Every PNG carries a 'temporary' marker.

    python -I tools/assets/gen_corridor_materials.py
"""
import os

import numpy as np
from PIL import Image, PngImagePlugin

BLOCK = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "interstice", "textures", "block")
rng = np.random.default_rng(4242)
S = 16


def tile_noise(size, cells, seed):
    r = np.random.default_rng(seed)
    lat = r.random((cells, cells))
    xs = np.arange(size) * cells / size
    xi = np.floor(xs).astype(int)
    xf = xs - xi
    xf = xf * xf * (3 - 2 * xf)
    x0, x1 = xi % cells, (xi + 1) % cells
    a, b, c, d = lat[np.ix_(x0, x0)], lat[np.ix_(x0, x1)], lat[np.ix_(x1, x0)], lat[np.ix_(x1, x1)]
    fy, fx = xf[:, None], xf[None, :]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(seed, octaves=(2, 4, 8, 16)):
    out, amp, norm = np.zeros((S, S)), 1.0, 0.0
    for i, c in enumerate(octaves):
        out += tile_noise(S, c, seed + i) * amp
        norm += amp
        amp *= 0.55
    return out / norm


def tint(g, rgb):
    return g[..., None] * np.array(rgb)[None, None, :]


def save(arr, name, alpha=None):
    from PIL import Image as _I
    _p = os.path.join(BLOCK, name + ".png")
    if os.path.exists(_p) and "PixelLab" in str(_I.open(_p).info.get("interstice", "")):
        print("kept PixelLab texture", name)
        return
    arr = np.clip(arr, 0, 1)
    img = (arr * 255 + 0.5).astype(np.uint8)
    info = PngImagePlugin.PngInfo()
    info.add_text("interstice", "temporary development texture - replace with PixelLab art (docs/PIXELLAB_ASSETS.md)")
    if alpha is not None:
        img = np.dstack([img, (np.clip(alpha, 0, 1) * 255).astype(np.uint8)])
        Image.fromarray(img, "RGBA").save(os.path.join(BLOCK, name + ".png"), pnginfo=info)
    else:
        Image.fromarray(img, "RGB").save(os.path.join(BLOCK, name + ".png"), pnginfo=info)
    print("wrote", name)


def grime(seed, k=0.25):
    """Soot and water: darker towards the bottom, a few vertical runs."""
    runs = np.repeat(tile_noise(S, 8, seed)[:1, :], S, axis=0)
    return 1 - k * (0.5 * fbm(seed + 50) + 0.5 * runs * (np.arange(S)[:, None] / S))


# ----------------------------------------------------------------- District

def grimy_brick():
    g = np.zeros((S, S, 3))
    base = np.array([0.36, 0.22, 0.18])
    n = fbm(1)
    for y in range(S):
        row = y // 4
        for x in range(S):
            mortar = y % 4 == 3 or (x + (4 if row % 2 else 0)) % 8 == 7
            if mortar:
                g[y, x] = (0.16, 0.15, 0.14)
            else:
                brick = ((x + (4 if row % 2 else 0)) // 8 + row * 3) % 5
                g[y, x] = base * (0.82 + 0.06 * brick + (n[y, x] - 0.5) * 0.25)
    save(g * grime(2, 0.35)[..., None], "grimy_brick")


def plaster(name, seed, crack=False):
    n = fbm(seed)
    g = tint(0.42 + (n - 0.5) * 0.12, (1.0, 0.97, 0.9))
    stains = tile_noise(S, 4, seed + 7)
    g *= (1 - 0.18 * (stains > 0.62))[..., None]
    g *= grime(seed + 3, 0.2)[..., None]
    if crack:
        for (y, x) in [(1, 9), (2, 9), (3, 8), (4, 8), (5, 7), (6, 7), (7, 8), (8, 9), (9, 9), (10, 8), (11, 6), (12, 5)]:
            g[y, x] *= 0.45
        g[11:15, 9:14] = np.array([0.33, 0.21, 0.17]) * (0.9 + 0.2 * fbm(seed + 9)[11:15, 9:14, None])   # brick showing
    save(g, name)


def asphalt(name, line=False):
    n = fbm(10)
    speck = rng.random((S, S))
    g = 0.17 + (n - 0.5) * 0.06 + (speck > 0.9) * 0.05 - (speck < 0.06) * 0.03
    out = tint(g, (1.0, 1.0, 1.02))
    if line:
        wear = fbm(11)
        for y in range(S):
            for x in range(6, 10):
                if wear[y, x] > 0.38:
                    out[y, x] = np.array([0.62, 0.52, 0.22]) * (0.75 + 0.3 * wear[y, x])
    save(out, name)


def sidewalk():
    n = fbm(20)
    g = tint(0.3 + (n - 0.5) * 0.08, (1.0, 0.99, 0.97))
    g[7, :] *= 0.6
    g[15, :] *= 0.6
    g[:, 7] *= 0.6
    g[:, 15] *= 0.6
    g[0:7, 0:7] *= 1.04
    g[8:15, 8:15] *= 0.96
    save(g * grime(21, 0.15)[..., None], "sidewalk")


def floorboards():
    g = np.zeros((S, S, 3))
    for y in range(S):
        board = y // 4
        shade = 0.8 + 0.1 * ((board * 7) % 3)
        for x in range(S):
            grain = 0.9 + 0.1 * np.sin(x * 0.9 + board * 2.1)
            g[y, x] = np.array([0.27, 0.2, 0.15]) * shade * grain
        if y % 4 == 3:
            g[y, :] *= 0.45
    g[2, 5] *= 0.5
    g[9, 12] *= 0.5
    save(g * grime(30, 0.2)[..., None], "floorboards")


def tar_roof():
    n = fbm(40)
    speck = rng.random((S, S))
    g = 0.12 + (n - 0.5) * 0.04 + (speck > 0.85) * 0.06
    save(tint(g, (1.0, 0.98, 0.95)), "tar_roof")


def grimy_glass():
    n = fbm(50)
    g = tint(0.35 + n * 0.12, (0.85, 0.95, 0.9))
    a = 0.45 + (1 - n) * 0.25
    frame = np.zeros((S, S), bool)
    frame[0, :] = frame[15, :] = frame[:, 0] = frame[:, 15] = True
    g[frame] = (0.2, 0.2, 0.21)
    a = np.where(frame, 1.0, a)
    # dirt at the bottom and in the corners
    a[12:15, 1:15] = np.maximum(a[12:15, 1:15], 0.75)
    g[12:15, 1:15] *= 0.6
    save(g, "grimy_glass_pane", a)
    e = np.full((S, S, 3), 0.2)
    save(e, "grimy_glass_pane_top")


# ---------------------------------------------------------------- Ironworks

def corrugated(name, rgb, seed):
    n = fbm(seed)
    x = np.arange(S)
    ridge = 0.8 + 0.25 * np.sin(x * np.pi / 2)[None, :]
    g = tint(0.5 + (n - 0.5) * 0.15, rgb) * ridge[..., None]
    rust = tile_noise(S, 4, seed + 1)
    runs = np.repeat(tile_noise(S, 8, seed + 2)[:1, :], S, axis=0) * (np.arange(S)[:, None] / S)
    rmask = np.clip((rust - 0.55) * 3 + runs * 0.8 - 0.3, 0, 1)
    g = g * (1 - rmask[..., None]) + np.array([0.34, 0.19, 0.11])[None, None, :] * rmask[..., None] * (0.8 + 0.3 * n[..., None])
    save(g * grime(seed + 3, 0.2)[..., None], name)


def steel_beam():
    n = fbm(60)
    g = tint(0.2 + n * 0.07, (0.95, 0.98, 1.05))
    g[:, 0:2] *= 0.65
    g[:, 14:16] *= 0.65
    g[:, 7:9] *= 1.15
    for y in (2, 6, 10, 14):
        g[y, 3] = g[y, 12] = (0.32, 0.31, 0.3)
    save(g, "steel_beam")
    t = tint(0.16 + n * 0.05, (0.95, 0.98, 1.05))
    t[0:3, :] *= 1.3
    t[13:16, :] *= 1.3
    t[:, 7:9] *= 1.3
    save(t, "steel_beam_top")


def oxidized_plate():
    n = fbm(70)
    g = tint(0.27 + (n - 0.5) * 0.1, (0.82, 0.92, 0.88))
    ox = tile_noise(S, 4, 71)
    g = g * (1 - 0.35 * (ox > 0.6))[..., None] + np.array([0.2, 0.26, 0.22]) * 0.35 * (ox > 0.6)[..., None]
    g[[0, 15], :] *= 0.7
    g[:, [0, 15]] *= 0.7
    for (y, x) in [(2, 2), (2, 13), (13, 2), (13, 13)]:
        g[y, x] = (0.4, 0.42, 0.4)
    save(g, "oxidized_plate")


def hazard_stripe():
    g = np.zeros((S, S, 3))
    wear = fbm(80)
    for y in range(S):
        for x in range(S):
            yellow = ((x + y) // 4) % 2 == 0
            c = np.array([0.55, 0.45, 0.14]) if yellow else np.array([0.1, 0.1, 0.1])
            if wear[y, x] < 0.35:
                c = np.array([0.22, 0.21, 0.2])
            g[y, x] = c * (0.85 + 0.2 * wear[y, x])
    save(g, "hazard_stripe")


def container(name, rgb, seed):
    corrugated(name, rgb, seed)


# --------------------------------------------------------------------- Ward

def clinic_tile(name, seed, stained=False):
    n = fbm(seed)
    g = tint(0.62 + (n - 0.5) * 0.06, (0.93, 0.96, 0.94))
    for i in range(0, S, 4):
        g[i, :] *= 0.72
        g[:, i] *= 0.72
    g *= grime(seed + 1, 0.22)[..., None]
    if stained:
        st = tile_noise(S, 4, seed + 2)
        m = np.clip((st - 0.45) * 2.5, 0, 1)[..., None]
        g = g * (1 - m * 0.5) + np.array([0.42, 0.33, 0.16]) * m * 0.5
    save(g, name)


def clinic_floor():
    n = fbm(90)
    g = tint(0.4 + (n - 0.5) * 0.08, (0.86, 0.94, 0.9))
    scuff = tile_noise(S, 8, 91)
    g *= (1 - 0.15 * (scuff > 0.7))[..., None]
    g[:, 15] *= 0.8
    save(g, "clinic_floor")


def flesh_growth():
    n = fbm(100)
    v = tile_noise(S, 8, 101)
    veins = np.abs(np.sin(v * 9)) < 0.18
    g = np.dstack([0.24 + n * 0.12, 0.09 + n * 0.05, 0.08 + n * 0.04])
    g[veins] = (0.12, 0.03, 0.04)
    wet = rng.random((S, S)) > 0.94
    g[wet] = (0.45, 0.25, 0.22)
    save(g, "flesh_growth")
    a = np.where(veins | (tile_noise(S, 4, 102) > 0.68), 1.0, 0.0)
    gv = g.copy()
    save(gv, "growth_veins", a)


if __name__ == "__main__":
    os.makedirs(BLOCK, exist_ok=True)
    grimy_brick()
    plaster("plaster_wall", 110)
    plaster("plaster_cracked", 111, crack=True)
    asphalt("asphalt")
    asphalt("asphalt_line", line=True)
    sidewalk()
    floorboards()
    tar_roof()
    grimy_glass()
    corrugated("corrugated_metal", (0.62, 0.66, 0.7), 120)
    steel_beam()
    oxidized_plate()
    hazard_stripe()
    container("container_red", (0.62, 0.3, 0.26), 130)
    container("container_teal", (0.3, 0.48, 0.48), 131)
    clinic_tile("clinic_tile", 140)
    clinic_tile("stained_tile", 141, stained=True)
    clinic_floor()
    flesh_growth()
