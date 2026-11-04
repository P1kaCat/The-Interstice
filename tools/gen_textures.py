"""
TEMPORARY DEVELOPMENT TEXTURES for THE INTERSTICE.

Procedurally generates clearly-placeholder textures so every system can be
tested before the final PixelLab artwork arrives. Each output is overwritten
by the matching PixelLab asset (same filename) - see docs/PIXELLAB_ASSETS.md.

Usage:  python -I tools/gen_textures.py
"""
import os
import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src")
BLOCK = os.path.join(ROOT, "main", "resources", "assets", "interstice", "textures", "block")
ENTITY = os.path.join(ROOT, "client", "resources", "assets", "interstice", "textures", "entity")
PARTICLE = os.path.join(ROOT, "client", "resources", "assets", "interstice", "textures", "particle")
ICON = os.path.join(ROOT, "main", "resources", "assets", "interstice", "icon.png")
for d in (BLOCK, ENTITY, PARTICLE):
    os.makedirs(d, exist_ok=True)

rng = np.random.default_rng(1337)


def tile_noise(size, cells, seed):
    """Seamless value noise (periodic lattice)."""
    r = np.random.default_rng(seed)
    lat = r.random((cells, cells))
    xs = np.arange(size) * cells / size
    xi = np.floor(xs).astype(int)
    xf = xs - xi
    xf = xf * xf * (3 - 2 * xf)
    x0, x1 = xi % cells, (xi + 1) % cells
    a = lat[np.ix_(x0, x0)]
    b = lat[np.ix_(x0, x1)]
    c = lat[np.ix_(x1, x0)]
    d = lat[np.ix_(x1, x1)]
    fy = xf[:, None]
    fx = xf[None, :]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(size, seed, octaves=(2, 4, 8, 16)):
    out = np.zeros((size, size))
    amp, norm = 1.0, 0.0
    for i, c in enumerate(octaves):
        if c > size:
            break
        out += tile_noise(size, c, seed + i) * amp
        norm += amp
        amp *= 0.55
    return out / norm


def save(arr, path, alpha=None):
    arr = np.clip(arr, 0, 1)
    img = (arr * 255).astype(np.uint8)
    if alpha is not None:
        a = (np.clip(alpha, 0, 1) * 255).astype(np.uint8)
        img = np.dstack([img, a])
        Image.fromarray(img, "RGBA").save(path)
    else:
        Image.fromarray(img, "RGB").save(path)


def tint(gray, rgb):
    return gray[..., None] * np.array(rgb)[None, None, :]


def concrete(seed, base=0.43, var=0.12):
    n = fbm(16, seed)
    speck = rng.random((16, 16))
    g = base + (n - 0.5) * var * 2 + (speck > 0.93) * 0.05 - (speck < 0.05) * 0.06
    return tint(g, (1.0, 0.99, 0.96))


# --- concrete family
save(concrete(10), os.path.join(BLOCK, "weathered_concrete.png"))
p = concrete(11, 0.41)
p[0, :] *= 0.72
p[:, 0] *= 0.72
p[15, :] *= 0.9
save(p, os.path.join(BLOCK, "concrete_panel.png"))
s = concrete(12, 0.4)
streak = fbm(16, 13, (4, 8))
s *= (0.75 + 0.25 * np.clip(streak[:, :1] * 1.3, 0, 1))[..., None] if False else 1
s = s * (0.8 + 0.2 * np.repeat(tile_noise(16, 8, 14)[0:1, :], 16, axis=0))[..., None]
s[..., 1] *= 0.97
s[..., 2] *= 0.9
save(s, os.path.join(BLOCK, "stained_concrete.png"))
c = concrete(15, 0.42)
for (y, x) in [(3, 2), (4, 3), (5, 3), (6, 4), (7, 5), (8, 5), (9, 6), (10, 8), (11, 9), (6, 10), (5, 11), (4, 13)]:
    c[y, x] *= 0.45
save(c, os.path.join(BLOCK, "cracked_concrete.png"))
f = concrete(16, 0.36, 0.08)
f[7, :] *= 0.85
f[:, 7] *= 0.85
save(f, os.path.join(BLOCK, "concrete_floor.png"))
r = concrete(17, 0.4, 0.25)
save(r, os.path.join(BLOCK, "concrete_rubble.png"))

# --- metals
n = fbm(16, 20)
rust = np.dstack([0.36 + n * 0.25, 0.2 + n * 0.12, 0.12 + n * 0.06])
rust[[0, 15], :] *= 0.8
rust[:, [0, 15]] *= 0.8
for (y, x) in [(2, 2), (2, 13), (13, 2), (13, 13)]:
    rust[y, x] = (0.55, 0.42, 0.33)
save(rust, os.path.join(BLOCK, "rusted_plate.png"))
dm = tint(0.16 + fbm(16, 21) * 0.09, (0.95, 0.98, 1.05))
dm[::5, :] *= 0.8
save(dm, os.path.join(BLOCK, "dark_metal.png"))
grate = tint(0.22 + fbm(16, 22) * 0.1, (1.0, 0.95, 0.9))
alpha = np.ones((16, 16))
for y in range(16):
    for x in range(16):
        if (x % 4 != 0) and (y % 4 != 0):
            alpha[y, x] = 0
save(grate, os.path.join(BLOCK, "metal_grate.png"), alpha)
pipe = np.dstack([0.33 + n * 0.2, 0.19 + n * 0.1, 0.12 + n * 0.05])
pipe[:, :] *= (0.75 + 0.35 * np.sin(np.linspace(0, np.pi, 16)))[None, :, None]
save(pipe, os.path.join(BLOCK, "rusted_pipe.png"))
end = np.dstack([0.3 + n * 0.15, 0.18 + n * 0.08, 0.12 + n * 0.04])
yy, xx = np.mgrid[0:16, 0:16]
rr = np.hypot(yy - 7.5, xx - 7.5)
end[rr < 5] *= 0.25
save(end, os.path.join(BLOCK, "rusted_pipe_top.png"))


# --- fixtures
def lamp(col, path):
    img = np.zeros((16, 16, 3)) + 0.12
    img[2:14, 2:14] = 0.2
    glow = np.exp(-((rr / 4.0) ** 2))
    img += glow[..., None] * np.array(col)[None, None, :]
    img[[2, 13], 2:14] = 0.25
    img[2:14, [2, 13]] = 0.25
    img[7, 2:14] = 0.25
    save(img, path)


lamp((0.75, 0.9, 1.0), os.path.join(BLOCK, "industrial_lamp.png"))
lamp((1.0, 0.7, 0.35), os.path.join(BLOCK, "sodium_lamp.png"))
lamp((1.0, 0.1, 0.06), os.path.join(BLOCK, "emergency_lamp.png"))
ff = np.zeros((16, 16, 3)) + 0.15
ff += (np.exp(-((rr / 6.0) ** 2)))[..., None] * np.array((0.95, 0.97, 1.0))
ff[rr > 7] = 0.18
save(ff, os.path.join(BLOCK, "flood_lamp_front.png"))
save(dm * 1.1, os.path.join(BLOCK, "flood_lamp_side.png"))

# --- entity: adherent (64x64)
S = 64
base = fbm(S, 30, (4, 8, 16, 32))
skin = np.dstack([0.5 + base * 0.2, 0.48 + base * 0.19, 0.45 + base * 0.17])
mott = fbm(S, 31, (8, 16, 32))
skin *= (0.75 + 0.35 * mott)[..., None]
# darker bony ridge / limb regions
skin[23:40, 0:34] *= 0.7
skin[8:26, 48:62] *= 0.82
skin[50:62, 30:50] *= 0.8
skin[40:46, 40:44] = (0.12, 0.11, 0.1)  # claws
skin[50:59, 0:22] *= 0.6  # jaw plate
skin[40:44, 20:22] = (0.05, 0.05, 0.05)  # slit (dark in base pass)
save(skin, os.path.join(ENTITY, "adherent.png"), np.ones((S, S)))
glow = np.zeros((S, S, 3))
ga = np.zeros((S, S))
glow[40:44, 20:22] = (0.85, 0.88, 0.9)
ga[40:44, 20:22] = 1.0
save(glow, os.path.join(ENTITY, "adherent_glow.png"), ga)

# --- chain link (32x32)
cl = fbm(32, 40, (4, 8, 16))
chain = np.dstack([0.3 + cl * 0.22, 0.2 + cl * 0.13, 0.15 + cl * 0.09])
chain *= (0.8 + 0.4 * np.sin(np.linspace(0, np.pi, 32)))[:, None, None]
save(chain, os.path.join(ENTITY, "chain_link.png"))

# --- horde sprite sheet: 4 frames of a spread-limbed silhouette seen on the wall (128x32)
sheet = np.zeros((32, 128, 3))
sa = np.zeros((32, 128))


def stroke(a, x0, y0, x1, y1, w=1.2):
    for t in np.linspace(0, 1, 40):
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        Y, X = np.ogrid[0:32, 0:128]
        a[(X - x) ** 2 + (Y - y) ** 2 <= w * w] = 1


for fr in range(4):
    ox = fr * 32
    ph = fr / 4 * 2 * np.pi
    stroke(sa, ox + 5, 16, ox + 23, 16, 3.6)  # body along x (head at +x)
    stroke(sa, ox + 22, 16, ox + 29, 16 + np.sin(ph) * 1.5, 2.2)  # neck/head
    for k, (bx, sgn) in enumerate([(19, 1), (19, -1), (9, 1), (9, -1)]):
        swing = np.sin(ph + (k % 2) * np.pi + (k // 2) * np.pi) * 4
        ex = ox + bx + swing + (5 if k < 2 else -3)
        ey = 16 + sgn * 11
        stroke(sa, ox + bx, 16, (ox + bx + ex) / 2, 16 + sgn * 7, 1.7)
        stroke(sa, (ox + bx + ex) / 2, 16 + sgn * 7, ex, ey, 1.4)
sheet[:] = 0.16
save(sheet, os.path.join(ENTITY, "horde_sprite.png"), sa)

# --- particles
for i in range(4):
    a = np.zeros((8, 8))
    yy8, xx8 = np.mgrid[0:8, 0:8]
    rad = 1.5 + i * 0.6
    a[np.hypot(yy8 - 3.5 + rng.random(), xx8 - 3.5 + rng.random()) < rad] = 1
    a *= rng.random((8, 8)) > 0.25
    save(np.ones((8, 8, 3)), os.path.join(PARTICLE, f"dust_{i}.png"), a)
m = np.zeros((8, 8))
m[3:5, 3:5] = 1
m[2:6, 3:5] = np.maximum(m[2:6, 3:5], 0.5)
save(np.ones((8, 8, 3)), os.path.join(PARTICLE, "mote_0.png"), m)

# --- icon (128x128): two walls, a chain, darkness
I = 128
icon = np.zeros((I, I, 3)) + 0.03
yy, xx = np.mgrid[0:I, 0:I]
fogc = np.exp(-((yy - 100) / 60.0) ** 2) * 0.18
icon += fogc[..., None] * np.array((0.8, 0.85, 0.9))
icon[:, :28] = (0.1, 0.1, 0.11)
icon[:, 100:] = (0.1, 0.1, 0.11)
for y in range(0, 96, 7):
    cx = 64 + np.sin(y / 30.0) * 3
    icon[y:y + 5, int(cx) - 3:int(cx) + 3] = (0.28, 0.2, 0.15)
icon[110:113, 60:68] = (0.9, 0.12, 0.08)
save(icon, ICON)
print("placeholder textures written")
