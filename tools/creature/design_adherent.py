"""
THE ADHERENT v4: organic low-poly creature, generated as an editable Blockbench "Generic" model.

Writes models/adherent.bbmodel: one bone (group) per joint, real meshes (lofted, tapered sections
with oblique faces and overlapping joints) attached to bones, a 512x512 painted skin, and keyframed
animations whose limb angles are solved by inverse kinematics so hands and feet keep their contacts.
The game asset is produced from the .bbmodel by export_adherent.py (skin weights, LOD, channels).

Anatomy (references in docs/monster-references): four limbs (two load-bearing arms, two digitigrade
hind legs), heavy shoulders with protruding scapulae, arched back, hollow abdomen, narrow pelvis,
very long forearms, hands with three long fingers and a shorter thumb with dark curved nails, a small
wedge-shaped head sunk between the shoulders on a thick neck, folds hiding the eyes and a narrow mouth
underneath that opens only when threatening. ~3 blocks long, ~1.6 blocks at the shoulders, ~3.4 reared.

Space: Blockbench space, pixels (16 px = 1 block), Y up, ground at y=0, front towards -Z.
Rotations: degrees in the file; matrices R = Rz * Ry * Rx (same convention as the in-game skinning).

Usage: python -I tools/creature/design_adherent.py
"""
import base64
import io
import json
import math
import os
import uuid

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "models", "adherent.bbmodel")
REPORT = os.path.join(ROOT, "models", "adherent_validation.txt")
TEX = 512
rng = np.random.default_rng(23)

# ============================================================================ skeleton
BONES = {}  # name -> (parent, pivot)


def bone(name, parent, pivot):
    BONES[name] = (parent, np.array(pivot, float))


bone("hips", None, (0, 17.0, 13.0))
bone("spine", "hips", (0, 18.5, 4.0))
bone("chest", "spine", (0, 21.0, -5.0))
bone("neck", "chest", (0, 20.5, -13.0))
bone("head", "neck", (0, 15.8, -18.8))
bone("jaw", "head", (0, 12.3, -21.4))
for s, sx in (("l", 1), ("r", -1)):
    bone(f"shoulder_{s}", "chest", (sx * 5.8, 21.5, -8.0))
    bone(f"arm_{s}", f"shoulder_{s}", (sx * 7.2, 18.8, -9.5))
    bone(f"fore_{s}", f"arm_{s}", (sx * 8.4, 10.2, -11.5))
    bone(f"hand_{s}", f"fore_{s}", (sx * 9.2, 1.7, -21.6))
    bone(f"fingers_{s}", f"hand_{s}", (sx * 9.4, 0.9, -24.4))
    bone(f"thigh_{s}", "hips", (sx * 4.8, 16.5, 14.0))
    bone(f"shin_{s}", f"thigh_{s}", (sx * 5.8, 8.8, 9.6))
    bone(f"foot_{s}", f"shin_{s}", (sx * 5.8, 6.3, 18.8))
    bone(f"toes_{s}", f"foot_{s}", (sx * 5.8, 1.0, 15.4))

ORDER = list(BONES)  # parents always precede children


# ============================================================================ mesh building
class Mesh:
    def __init__(self, bone_name, material):
        self.bone = bone_name
        self.material = material
        self.verts = []   # positions
        self.params = []  # (u around 0..1, v along 0..1) for painting/UV
        self.faces = []   # tuples of vertex indices (tri or quad)
        self.island = None


MESHES = []


def frame(t):
    t = t / np.linalg.norm(t)
    ref = np.array([0.0, 1.0, 0.0]) if abs(t[1]) < 0.9 else np.array([0.0, 0.0, -1.0])
    side = np.cross(t, ref)
    side /= np.linalg.norm(side)
    up = np.cross(side, t)
    return side, up


def loft(bone_name, material, path, sides=10, shape=None, cap0=True, cap1=True):
    """path: list of (center, rx, ry). Builds rings around a curve; shape(a, s01) -> (kx, ky) multipliers."""
    m = Mesh(bone_name, material)
    pts = [np.array(p[0], float) for p in path]
    n = len(pts)
    rings = []
    for i, (c, rx, ry) in enumerate(path):
        c = np.array(c, float)
        t = (pts[min(i + 1, n - 1)] - pts[max(i - 1, 0)])
        side, up = frame(t)
        ring = []
        for k in range(sides + 1):
            a = 2 * math.pi * k / sides
            kx, ky = shape(a, i / (n - 1)) if shape else (1.0, 1.0)
            p = c + side * rx * kx * math.cos(a) + up * ry * ky * math.sin(a)
            m.verts.append(p)
            m.params.append((k / sides, i / (n - 1)))
            ring.append(len(m.verts) - 1)
        rings.append(ring)
    for i in range(n - 1):
        for k in range(sides):
            a, b, c, d = rings[i][k], rings[i][k + 1], rings[i + 1][k + 1], rings[i + 1][k]
            m.faces.append((a, d, c, b))
    for cap, ring, center in ((cap0, rings[0], pts[0]), (cap1, rings[-1], pts[-1])):
        if not cap:
            continue
        m.verts.append(center + (pts[0] - pts[1]) * 0.15 if ring is rings[0] else center + (pts[-1] - pts[-2]) * 0.15)
        m.params.append((0.5, 0.0 if ring is rings[0] else 1.0))
        ci = len(m.verts) - 1
        for k in range(sides):
            if ring is rings[0]:
                m.faces.append((ci, ring[k], ring[k + 1]))
            else:
                m.faces.append((ci, ring[k + 1], ring[k]))
    MESHES.append(m)
    return m


def torso_shape(a, s):
    # dorsal ridge on top, slightly flattened belly
    sa = math.sin(a)
    kx = 1.0 - 0.18 * max(0.0, sa) ** 3
    ky = 1.0 + 0.10 * max(0.0, sa) ** 8 - 0.12 * max(0.0, -sa) ** 2
    return kx, ky


def wedge_shape(a, s):
    # cuneiform head: narrow keel on top, broad jaw line underneath
    sa = math.sin(a)
    kx = 1.0 - 0.45 * max(0.0, sa) ** 1.5
    ky = 1.0 - 0.15 * max(0.0, -sa)
    return kx, ky


def flat_shape(a, s):
    return 1.0, 0.75


# --- torso: three overlapping segments forming one continuous mass
loft("hips", "skin", [((0, 17.2, 20.6), 2.2, 2.0), ((0, 17.4, 18.6), 4.2, 3.7), ((0, 17.3, 15.0), 5.0, 4.2),
                      ((0, 17.7, 11.0), 4.2, 3.7), ((0, 18.2, 7.5), 3.6, 3.3)], sides=12, shape=torso_shape, cap1=False)
loft("spine", "skin", [((0, 18.1, 9.0), 3.8, 3.4), ((0, 18.8, 5.0), 3.3, 3.1), ((0, 19.7, 1.0), 4.6, 4.1),
                       ((0, 20.4, -2.8), 6.0, 5.2)], sides=12, shape=torso_shape, cap0=False, cap1=False)
loft("chest", "skin", [((0, 20.2, -1.8), 6.0, 5.2), ((0, 20.9, -5.5), 7.3, 6.1), ((0, 21.2, -9.3), 7.6, 6.0),
                       ((0, 20.1, -12.4), 6.2, 5.1), ((0, 19.4, -14.2), 4.8, 4.3)], sides=12, shape=torso_shape, cap0=False)
# --- neck: thick and tense
loft("neck", "skin", [((0, 20.4, -12.0), 4.5, 4.3), ((0, 18.6, -15.4), 3.7, 3.6), ((0, 16.7, -18.3), 3.1, 3.2),
                      ((0, 15.8, -19.8), 2.9, 3.1)], sides=10, cap0=False, cap1=False)
# --- head: a wedge hood, folds over where eyes should be; the mouth sits underneath
loft("head", "head", [((0, 16.0, -18.6), 3.0, 3.4), ((0, 15.8, -21.4), 3.15, 3.75), ((0, 14.4, -24.4), 2.55, 3.25),
                      ((0, 12.9, -26.9), 1.65, 2.2), ((0, 12.2, -28.5), 0.75, 1.05)], sides=12, shape=wedge_shape, cap0=False)
loft("head", "mouth", [((0, 12.0, -22.2), 1.5, 1.0), ((0, 11.6, -25.0), 1.25, 0.85), ((0, 11.5, -27.2), 0.6, 0.45)], sides=8)
loft("jaw", "skin", [((0, 12.0, -21.6), 1.9, 0.95), ((0, 11.2, -24.8), 1.45, 0.8), ((0, 11.15, -27.4), 0.75, 0.5)], sides=8)

for s, sx in (("l", 1), ("r", -1)):
    def X(x, sx=sx):
        return sx * x
    # scapula: a plate pushing up under the skin of the shoulders
    loft(f"shoulder_{s}", "skin", [((X(5.0), 24.1, -11.2), 1.0, 0.6), ((X(5.9), 24.7, -8.6), 2.4, 1.35),
                                   ((X(5.2), 24.0, -5.6), 1.1, 0.7)], sides=8)
    # upper arm (deltoid bulk into the elbow), overlapping the forearm
    loft(f"arm_{s}", "skin", [((X(7.1), 19.8, -9.1), 3.0, 3.3), ((X(7.8), 15.0, -10.4), 2.6, 2.45),
                              ((X(8.4), 11.2, -11.4), 2.2, 2.2), ((X(8.5), 9.5, -12.3), 2.0, 2.1)], sides=10)
    # very long forearm, muscular below the elbow, thin at the wrist
    loft(f"fore_{s}", "skin", [((X(8.35), 11.3, -10.9), 2.1, 2.35), ((X(8.6), 8.0, -14.0), 2.25, 2.0),
                               ((X(9.0), 4.0, -18.5), 1.5, 1.4), ((X(9.2), 1.9, -21.7), 1.3, 1.2)], sides=10)
    # palm, flattened
    loft(f"hand_{s}", "skin", [((X(9.2), 2.1, -20.9), 1.6, 1.2), ((X(9.3), 1.45, -23.0), 2.2, 0.95),
                               ((X(9.4), 1.05, -24.6), 2.35, 0.8)], sides=10, shape=flat_shape)
    # three long fingers and a shorter thumb, each ending in a dark curved nail
    for j, (dx, reach) in enumerate(((-1.45, 0.0), (0.0, 1.4), (1.45, 0.3))):
        x = X(9.4 + dx)
        tip = -29.3 - reach
        loft(f"fingers_{s}", "skin", [((x, 0.95, -24.3), 0.58, 0.52), ((x, 1.35, -26.6 - reach * 0.4), 0.5, 0.47),
                                      ((x, 0.7, tip + 0.9), 0.42, 0.4)], sides=6, cap0=False)
        loft(f"fingers_{s}", "nail", [((x, 0.62, tip + 1.1), 0.36, 0.34), ((x, 0.3, tip + 0.2), 0.24, 0.24),
                                      ((x, 0.02, tip - 0.6), 0.05, 0.05)], sides=6)
    thumb_x = 9.4 - 2.1
    loft(f"hand_{s}", "skin", [((X(thumb_x + 0.6), 1.7, -22.0), 0.55, 0.5), ((X(thumb_x), 1.1, -23.8), 0.48, 0.45),
                               ((X(thumb_x - 0.4), 0.6, -24.9), 0.38, 0.36)], sides=6, cap0=False)
    loft(f"hand_{s}", "nail", [((X(thumb_x - 0.45), 0.55, -25.0), 0.32, 0.3), ((X(thumb_x - 0.6), 0.1, -25.8), 0.04, 0.04)], sides=6)
    # thigh: heavy enough to carry the reared body
    loft(f"thigh_{s}", "skin", [((X(4.7), 17.0, 14.4), 3.4, 3.8), ((X(5.3), 12.8, 12.0), 2.9, 3.0),
                                ((X(5.8), 9.0, 9.8), 2.3, 2.3), ((X(5.8), 8.0, 9.9), 2.1, 2.1)], sides=10)
    # lower leg back to the raised hock
    loft(f"shin_{s}", "skin", [((X(5.8), 9.0, 9.3), 2.2, 2.3), ((X(5.8), 7.4, 14.0), 1.7, 1.6),
                               ((X(5.8), 6.5, 18.6), 1.4, 1.5), ((X(5.8), 6.4, 19.5), 1.1, 1.2)], sides=10)
    # metatarsus down to the ball of the foot
    loft(f"foot_{s}", "skin", [((X(5.8), 6.6, 18.9), 1.35, 1.4), ((X(5.8), 3.6, 17.2), 1.0, 1.0),
                               ((X(5.8), 1.1, 15.6), 1.05, 0.9)], sides=8)
    for dx in (-0.95, 0.0, 0.95):
        x = X(5.8 + dx)
        loft(f"toes_{s}", "skin", [((x, 1.0, 15.5), 0.48, 0.42), ((x, 0.65, 13.6), 0.4, 0.36)], sides=6, cap0=False, cap1=False)
        loft(f"toes_{s}", "nail", [((x, 0.62, 13.7), 0.34, 0.32), ((x, 0.25, 12.7), 0.2, 0.2), ((x, 0.02, 11.8), 0.04, 0.04)], sides=6)


# ============================================================================ UV islands + painting
def island_size(m):
    v = np.array(m.verts)
    span = np.ptp(v, axis=0)
    circ = 2 * math.pi * max(0.5, (span[0] + span[1]) / 4)
    length = max(1.0, np.linalg.norm(span))
    return circ, length


def pack(tpp):
    x = y = row = 0
    pad = 2
    for m in sorted(MESHES, key=lambda mm: -island_size(mm)[1]):
        c, l = island_size(m)
        w, h = max(8, int(c * tpp)), max(8, int(l * tpp))
        if x + w + pad > TEX:
            x, y, row = 0, y + row + pad, 0
        if y + h > TEX:
            return False
        m.island = (x, y, w, h)
        x += w + pad
        row = max(row, h)
    return True


tpp = 12.0
while not pack(tpp):
    tpp *= 0.95

img = np.zeros((TEX, TEX, 3))
alpha = np.zeros((TEX, TEX))
noise = rng.random((TEX, TEX))


def smooth_noise(scale):
    small = rng.random((max(2, TEX // scale), max(2, TEX // scale)))
    return np.array(Image.fromarray((small * 255).astype(np.uint8)).resize((TEX, TEX), Image.BICUBIC)) / 255.0


n_big, n_mid, n_fine = smooth_noise(32), smooth_noise(10), smooth_noise(4)
SKIN = np.array((0.47, 0.43, 0.42))      # ash grey, warm
FOLD = np.array((0.42, 0.29, 0.30))      # warmer in creases
for m in MESHES:
    x0, y0, w, h = m.island
    uu, vv = np.meshgrid(np.linspace(0, 1, w), np.linspace(0, 1, h))
    region = (slice(y0, y0 + h), slice(x0, x0 + w))
    big, mid, fine = n_big[region], n_mid[region], n_fine[region]
    around = np.sin(uu * 2 * math.pi)                       # +1 top, -1 underside
    if m.material == "nail":
        col = np.array((0.07, 0.06, 0.06)) * (0.9 + 0.3 * fine)[..., None]
        col += (vv < 0.25)[..., None] * 0.04
    elif m.material == "mouth":
        col = np.array((0.36, 0.13, 0.14)) * (0.8 + 0.4 * fine)[..., None] + 0.06 * (noise[region] > 0.93)[..., None]
    else:
        base = SKIN * (0.86 + 0.22 * big + 0.08 * mid)[..., None]
        crease = np.clip((mid - 0.55) * 2.2, 0, 1) * 0.55
        col = base * (1 - crease[..., None]) + FOLD * crease[..., None]
        col *= (0.82 + 0.18 * (around * 0.5 + 0.5))[..., None]  # soft underside darkening only
        # long tendons and stretched skin lines along limbs
        tend = (np.abs(np.sin(uu * 2 * math.pi * 3 + big * 4)) > 0.985)
        col *= (1 - 0.18 * tend)[..., None]
        if m.bone == "chest":
            ribs = (np.sin(vv * 2 * math.pi * 6) > 0.72) & (around < 0.3)
            col *= (1 - 0.14 * ribs)[..., None]
        if m.material == "head":
            folds = np.abs(np.sin((vv * 9 + uu * 2.5) * math.pi + mid * 5)) > 0.9
            col *= (1 - 0.22 * (folds & (vv > 0.25)))[..., None]
            near_mouth = np.clip((vv - 0.6) * 2.5, 0, 1) * np.clip(-around, 0, 1)
            col = col * (1 - 0.4 * near_mouth[..., None]) + np.array((0.38, 0.2, 0.21)) * 0.4 * near_mouth[..., None]
        if m.bone.startswith(("hand", "fingers", "toes")) or m.bone.startswith("foot"):
            col *= (0.9 + 0.1 * fine)[..., None]
            col = col * 0.92 + np.array((0.34, 0.27, 0.26)) * 0.08  # calloused, slightly darker
        veins = (np.abs(mid - 0.5) < 0.012) & (fine > 0.4)
        col *= (1 - 0.25 * veins)[..., None]
    img[region] = np.clip(col, 0, 1)
    alpha[region] = 1
skin_png = Image.fromarray((np.dstack([img, alpha]) * 255).astype(np.uint8), "RGBA").filter(ImageFilter.SMOOTH)


def uv_of(m, k):
    x0, y0, w, h = m.island
    u, v = m.params[k]
    return [round(x0 + 0.5 + u * (w - 1), 3), round(y0 + 0.5 + v * (h - 1), 3)]


# ============================================================================ forward kinematics
def rot_mat(r):
    x, y, z = (math.radians(a) for a in r)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def globals_(pose):
    """pose: bone -> {'rot': (deg x,y,z), 'pos': (px)}. Returns bone -> (R, t) global transforms."""
    G = {}
    for b in ORDER:
        parent, piv = BONES[b]
        rot = pose.get(b, {}).get("rot", (0, 0, 0))
        pos = np.array(pose.get(b, {}).get("pos", (0, 0, 0)), float)
        local_t = piv - (BONES[parent][1] if parent else 0) + pos
        if parent:
            PR, Pt = G[parent]
            G[b] = (PR @ rot_mat(rot), Pt + PR @ local_t)
        else:
            G[b] = (rot_mat(rot), local_t)
    return G


def point(G, b, p_rest):
    R, t = G[b]
    return t + R @ (np.array(p_rest, float) - BONES[b][1])


# ============================================================================ animation authoring
TAU = 2 * math.pi
CONTACT = {   # limb -> (bone carrying the contact point, rest contact point, IK joints)
    "arm_l": ("hand_l", (9.4, 0.15, -23.9), ("arm_l", "fore_l", "shoulder_l")),
    "arm_r": ("hand_r", (-9.4, 0.15, -23.9), ("arm_r", "fore_r", "shoulder_r")),
    "leg_l": ("foot_l", (5.8, 0.1, 15.5), ("thigh_l", "shin_l", "foot_l")),
    "leg_r": ("foot_r", (-5.8, 0.1, 15.5), ("thigh_r", "shin_r", "foot_r")),
}
FLATTEN = {"arm_l": ("hand_l", ("hips", "spine", "chest", "shoulder_l", "arm_l", "fore_l")),
           "arm_r": ("hand_r", ("hips", "spine", "chest", "shoulder_r", "arm_r", "fore_r")),
           "leg_l": ("toes_l", ("hips", "thigh_l", "shin_l", "foot_l")),
           "leg_r": ("toes_r", ("hips", "thigh_r", "shin_r", "foot_r"))}


def add(pose, b, rot=(0, 0, 0), pos=(0, 0, 0)):
    e = pose.setdefault(b, {"rot": (0, 0, 0), "pos": (0, 0, 0)})
    e["rot"] = tuple(a + c for a, c in zip(e.get("rot", (0, 0, 0)), rot))
    e["pos"] = tuple(a + c for a, c in zip(e.get("pos", (0, 0, 0)), pos))


def solve_contacts(pose, targets, flatten=True):
    """targets: limb -> (dx, dy, dz) offsets of the contact from its rest position (body space)."""
    from scipy.optimize import minimize
    for limb, off in targets.items():
        b, rest, joints = CONTACT[limb]
        goal = np.array(rest) + np.array(off)
        base = {j: pose.get(j, {}).get("rot", (0, 0, 0)) for j in joints}
        reg = (0.0004, 0.0004, 0.001)

        end, chain = FLATTEN[limb]
        end_rot = pose.get(end, {}).get("rot", (0, 0, 0))

        def with_(v):
            p = {k: dict(e) for k, e in pose.items()}
            for j, dv in zip(joints, v):
                r = base[j]
                p.setdefault(j, {})["rot"] = (r[0] + dv, r[1], r[2])
            if flatten:   # palm / toes kept flat on the contact surface, inside the solve
                tot = sum(p.get(c, {}).get("rot", (0, 0, 0))[0] for c in chain)
                p.setdefault(end, {})["rot"] = (-tot, end_rot[1], end_rot[2])
            return p

        def cost(v):
            w = point(globals_(with_(v)), b, rest)
            return (w[1] - goal[1]) ** 2 + (w[2] - goal[2]) ** 2 + sum(rg * dv * dv for rg, dv in zip(reg, v))

        best = None
        for start in ((0, 0, 0), (15, -15, 0)):
            r = minimize(cost, start, method="Nelder-Mead", options={"xatol": 1e-4, "fatol": 1e-9, "maxiter": 3000})
            best = r if best is None or r.fun < best.fun else best
        solved = with_(best.x)
        pose.clear()
        pose.update(solved)
    return pose


def gait_offset(phase, stance, stride, lift):
    """Contact offset along z (body moves -z): planted contact slides +z linearly; swing returns with lift."""
    if phase < stance:
        return 0.0, -stride / 2 + stride * phase / stance
    q = (phase - stance) / (1 - stance)
    e = q * q * (3 - 2 * q)
    return lift * math.sin(math.pi * q), stride / 2 - stride * e


ANIMS = []


def animation(name, length, loop, keys, **meta):
    def deco(fn):
        ANIMS.append(dict(name=name, length=length, loop=loop, keys=keys, fn=fn, **meta))
        return fn
    return deco


def S(x):
    return math.sin(x)


def locomotion(t, stance, stride, lifts, offsets, body, wall=False, centers=None):
    pose = {}
    p = TAU * t
    body(pose, t, p)
    targets = {}
    for limb, off in offsets.items():
        ph = (t + off) % 1.0
        lift, dz = gait_offset(ph, stance, stride, lifts[limb])
        targets[limb] = (0.0, lift, dz + (centers or {}).get(limb, 0.0))
        if limb.startswith("arm"):
            s = limb[-1]
            spread = 1.0 if ph >= stance else 0.0
            add(pose, f"fingers_{s}", rot=(-14 * spread * math.sin(math.pi * (ph - stance) / (1 - stance)) if spread else 0, 0, 0))
    if wall:
        for s, sx in (("l", 1), ("r", -1)):
            add(pose, f"arm_{s}", rot=(0, 0, sx * 24))
            add(pose, f"thigh_{s}", rot=(0, 0, sx * 18))
    return solve_contacts(pose, targets)


@animation("walk", 2.2, "loop", 41, stride=6.0, stance=0.72)
def walk(t):
    def body(pose, t, p):
        add(pose, "hips", rot=(0, 2.5 * S(p), 2.0 * S(p)), pos=(0, -0.35 * abs(S(p * 2)), 0))
        add(pose, "chest", rot=(1.5 * S(p * 2 + 1.0), -3.0 * S(p), -2.5 * S(p)), pos=(0, -0.45 * max(0, S(p * 2 + 0.6)), 0))
        add(pose, "neck", rot=(-1.5 * S(p * 2 + 1.4), 2.0 * S(p), 0))
        add(pose, "head", rot=(1.2 * S(p * 2 + 1.8), -1.5 * S(p), 0))
    return locomotion(t, 0.72, 6.0, {"arm_l": 2.4, "arm_r": 2.4, "leg_l": 1.8, "leg_r": 1.8},
                      {"leg_l": 0.0, "arm_l": 0.25, "leg_r": 0.5, "arm_r": 0.75}, body,
                      centers={"arm_l": 1.0, "arm_r": 1.0})


@animation("run", 0.85, "loop", 41, stride=10.0, stance=0.42)
def run(t):
    def body(pose, t, p):
        add(pose, "hips", rot=(5 * S(p), 0, 0), pos=(0, -1.2 * abs(S(p)), 0))
        add(pose, "spine", rot=(-6 * S(p + 0.6), 0, 0))
        add(pose, "chest", rot=(-5 * S(p + 1.2), 0, 1.5 * S(p)), pos=(0, -0.9 * max(0, S(p + 2.0)), 0))
        add(pose, "neck", rot=(6 + 3 * S(p + 1.8), 0, 0))
        add(pose, "head", rot=(-4 * S(p + 2.2), 0, 0))
    return locomotion(t, 0.42, 10.0, {"arm_l": 3.5, "arm_r": 3.5, "leg_l": 3.0, "leg_r": 3.0},
                      {"leg_l": 0.0, "leg_r": 0.08, "arm_l": 0.48, "arm_r": 0.56}, body,
                      centers={"arm_l": 3.0, "arm_r": 3.0})


@animation("climb", 2.6, "loop", 33, stride=6.0, stance=0.75)
def climb(t):
    def body(pose, t, p):
        # pressed to the surface, the shoulders working, the head held off the wall
        add(pose, "hips", pos=(0, -3.0, 0), rot=(0, 3 * S(p), 3 * S(p)))
        add(pose, "chest", pos=(0, -2.4, 0), rot=(0, -4 * S(p), -4 * S(p)))
        add(pose, "neck", rot=(10, 3 * S(p), 0))
        add(pose, "head", rot=(6, -2 * S(p), 0))
    return locomotion(t, 0.75, 6.0, {"arm_l": 2.8, "arm_r": 2.8, "leg_l": 2.2, "leg_r": 2.2},
                      {"leg_l": 0.0, "arm_r": 0.25, "leg_r": 0.5, "arm_l": 0.75}, body, wall=True)


@animation("cling", 6.0, "loop", 25)
def cling(t):
    p = TAU * t
    pose = {}
    add(pose, "hips", pos=(0, -3.0, 0))
    add(pose, "chest", pos=(0, -2.4 + 0.18 * S(p * 2), 0), rot=(0.6 * S(p * 2), 0, 0))
    add(pose, "neck", rot=(10, 0, 0))
    for s, sx in (("l", 1), ("r", -1)):
        add(pose, f"arm_{s}", rot=(0, 0, sx * 24))
        add(pose, f"thigh_{s}", rot=(0, 0, sx * 18))
        add(pose, f"fingers_{s}", rot=(10, 0, 0))   # gripping
    return solve_contacts(pose, {l: (0, 0, 0) for l in CONTACT})


@animation("idle", 7.0, "loop", 29)
def idle(t):
    p = TAU * t
    pose = {}
    breath = S(p * 2)                       # two slow breaths per loop
    shift = S(p)                            # minute weight shift
    add(pose, "chest", pos=(0, 0.22 * breath, 0), rot=(0.7 * breath, 0, 0.6 * shift))
    add(pose, "spine", rot=(0.4 * breath, 0, 0))
    add(pose, "hips", pos=(0.12 * shift, 0, 0), rot=(0, 0, -0.4 * shift))
    add(pose, "neck", rot=(0.4 * S(p * 2 + 0.8), 0, 0))
    add(pose, "head", rot=(0, 0.6 * S(p + 0.3), 0))
    return solve_contacts(pose, {l: (0, 0, 0) for l in CONTACT})


@animation("observe", 4.0, "hold", 17)
def observe(t):
    # the body stops; the neck and head turn late and slowly, then hold
    pose = {}
    k = min(1.0, max(0.0, (t - 0.25) / 0.5))
    k = k * k * (3 - 2 * k)
    add(pose, "neck", rot=(-6 * k, 9 * k, 0))
    add(pose, "head", rot=(-4 * k, 14 * k, 2 * k))
    return solve_contacts(pose, {l: (0, 0, 0) for l in CONTACT})


@animation("rear", 1.6, "hold", 25)
def rear(t):
    # hind feet anchored, pelvis over the support, chest rising, arms stop carrying weight
    k = min(1.0, t / 0.75)
    k = k * k * (3 - 2 * k)
    lift = min(1.0, max(0.0, (t - 0.15) / 0.6))
    pose = {}
    add(pose, "hips", rot=(71 * k, 0, 0), pos=(0, 1.5 * k, 5.0 * k))
    add(pose, "spine", rot=(9 * k, 0, 0))
    add(pose, "chest", rot=(3 * k, 0, 0))
    add(pose, "neck", rot=(-22 * k, 0, 0))
    add(pose, "head", rot=(-26 * k, 0, 0))
    for s, sx in (("l", 1), ("r", -1)):
        add(pose, f"arm_{s}", rot=(-66 * k, 0, sx * 6 * k))     # arms stop carrying weight and hang
        add(pose, f"fore_{s}", rot=(-14 * k, 0, 0))
        add(pose, f"hand_{s}", rot=(-30 * k, 0, 0))
        add(pose, f"fingers_{s}", rot=(-25 * k, 0, 0))
    pose = solve_contacts(pose, {"leg_l": (0, 0, 0), "leg_r": (0, 0, 0)})
    if lift < 1.0:   # arms still carry weight at the very start
        pass
    return pose


@animation("threat", 1.8, "hold", 19)
def threat(t):
    # the neck tenses and the mouth opens progressively
    k = min(1.0, t / 1.2)
    k = k * k * (3 - 2 * k)
    pose = {}
    add(pose, "neck", rot=(8 * k, 0, 0))                      # the neck tenses and lifts the head
    add(pose, "head", rot=(10 * k, 0, 0))
    add(pose, "jaw", rot=(-32 * k, 0, 0))                     # negative x drops the jaw tip
    return pose


@animation("fall", 1.0, "loop", 21)
def fall(t):
    p = TAU * t
    pose = {}
    add(pose, "hips", rot=(10 * S(p), 0, 12 * S(p + 1)))
    add(pose, "chest", rot=(-12 * S(p + 0.5), 0, -10 * S(p + 1.5)))
    add(pose, "neck", rot=(-25 + 10 * S(p * 2), 0, 0))
    add(pose, "jaw", rot=(-18, 0, 0))
    for i, (s, sx) in enumerate((("l", 1), ("r", -1))):
        add(pose, f"arm_{s}", rot=(-45 + 35 * S(p + i * 1.7), 0, sx * (35 + 20 * S(p * 2 + i))))  # reaching for a hold
        add(pose, f"fore_{s}", rot=(20 * S(p + i), 0, 0))
        add(pose, f"fingers_{s}", rot=(-20 + 25 * S(p * 2 + i), 0, 0))
        add(pose, f"thigh_{s}", rot=(-30 + 25 * S(p + i * 2), 0, sx * 25))
        add(pose, f"shin_{s}", rot=(20 * S(p + i), 0, 0))
    return pose


@animation("recover", 3.2, "hold", 33)
def recover(t):
    # impact absorbed flat on the ground, one hand plants, the limbs come back under the body
    pose = {}
    impact = max(0.0, 1 - t / 0.35)
    k = min(1.0, max(0.0, (t - 0.5) / 2.3))
    k = k * k * (3 - 2 * k)
    down = 1 - k
    add(pose, "hips", pos=(0, -9.0 * down - 1.0 * impact, 0), rot=(0, 0, 14 * down))
    add(pose, "spine", pos=(0, -1.5 * down, 0))
    add(pose, "chest", pos=(0, -6.5 * down, 0), rot=(6 * down, 0, -10 * down))
    add(pose, "neck", rot=(20 * down, 0, 0))
    add(pose, "head", rot=(10 * down, 12 * down, 16 * down))
    hand_first = min(1.0, max(0.0, (t - 0.5) / 0.6))
    targets = {}
    for i, (s, sx) in enumerate((("l", 1), ("r", -1))):
        delay = 0.0 if s == "l" else 0.45                         # one hand plants first
        ki = min(1.0, max(0.0, (t - 0.5 - delay) / 1.4))
        ki = ki * ki * (3 - 2 * ki)
        targets[f"arm_{s}"] = (0, 0, 4.0 * (1 - ki))
        targets[f"leg_{s}"] = (0, 0, -3.0 * (1 - ki))
        add(pose, f"arm_{s}", rot=(0, 0, sx * 35 * (1 - ki)))
        add(pose, f"thigh_{s}", rot=(0, 0, sx * 30 * (1 - ki)))
    return solve_contacts(pose, targets)


# ============================================================================ sampling, validation
def catmull(p0, p1, p2, p3, f):
    return 0.5 * ((2 * p1) + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f * f + (-p0 + 3 * p1 - 3 * p2 + p3) * f * f * f)


def sample_channel(times, values, t, loop, length):
    """Exactly the interpolation the game uses (catmull-rom, looping neighbours wrap)."""
    n = len(times)
    if t <= times[0]:
        return values[0]
    if t >= times[-1]:
        return values[-1]
    i = max(0, np.searchsorted(times, t, side="right") - 1)
    i = min(i, n - 2)
    f = (t - times[i]) / (times[i + 1] - times[i])

    def at(k):
        if loop:
            m = n - 1  # last key duplicates the first
            return values[k % m]
        return values[min(max(k, 0), n - 1)]
    return catmull(at(i - 1), at(i), at(i + 1), at(i + 2), f)


report = []
BAKED = []
for a in ANIMS:
    times = [a["length"] * i / (a["keys"] - 1) for i in range(a["keys"])]
    poses = []
    for i, tm in enumerate(times):
        t01 = tm / a["length"]
        if a["loop"] == "loop" and i == len(times) - 1:
            poses.append(poses[0])
        elif a["loop"] == "loop":
            poses.append(a["fn"](min(t01, 1.0)))       # cycles are written over a 0..1 phase
        else:
            poses.append(a["fn"](tm))                  # one-shot/hold clips are written in seconds
    bones_used = sorted({b for p in poses for b in p})
    channels = {}
    for b in bones_used:
        for ch in ("rot", "pos"):
            vals = np.array([p.get(b, {}).get(ch, (0, 0, 0)) for p in poses], float)
            if np.abs(vals).max() > 1e-6:
                channels[(b, ch)] = vals
    BAKED.append((a, times, channels))

    # validate contacts between keyframes at 240 Hz of animation time
    if a["name"] in ("walk", "run", "climb", "idle", "cling", "rear"):
        loop = a["loop"] == "loop"
        worst_y = worst_slip = 0.0
        limbs = list(CONTACT) if a["name"] != "rear" else ["leg_l", "leg_r"]
        prev = {}
        steps = int(a["length"] * 240)
        for si in range(steps + 1):
            tm = a["length"] * si / steps
            pose = {}
            for (b, ch), vals in channels.items():
                v = sample_channel(times, vals, tm, loop, a["length"])
                pose.setdefault(b, {})[ch] = tuple(v)
            G = globals_(pose)
            for limb in limbs:
                b, rest, _ = CONTACT[limb]
                w = point(G, b, rest)
                if a.get("stance"):
                    ph = (tm / a["length"] + {"walk": {"leg_l": 0, "arm_l": .25, "leg_r": .5, "arm_r": .75},
                                               "run": {"leg_l": 0, "leg_r": .08, "arm_l": .48, "arm_r": .56},
                                               "climb": {"leg_l": 0, "arm_r": .25, "leg_r": .5, "arm_l": .75}}[a["name"]][limb]) % 1
                    if ph < a["stance"] * 0.97:
                        worst_y = max(worst_y, abs(w[1] - rest[1]))   # height only; z checked as slip
                        if limb in prev and prev[limb][0] < a["stance"] * 0.97 and ph > prev[limb][0]:
                            expected = a["stride"] / a["stance"] * (a["length"] / steps) / a["length"]
                            worst_slip = max(worst_slip, abs((w[2] - prev[limb][1]) - expected))
                        prev[limb] = (ph, w[2])
                    else:
                        prev.pop(limb, None)
                else:
                    worst_y = max(worst_y, abs(w[1] - rest[1]))
                    if limb in prev:
                        worst_slip = max(worst_slip, abs(w[2] - prev[limb]))
                    prev[limb] = w[2]
        report.append(f"{a['name']:8s} contact height error <= {worst_y:.3f} px, slip per 1/240 s <= {worst_slip:.4f} px "
                      f"(interpolated, sampled at 240 Hz)")

# ============================================================================ write the .bbmodel
def r(x):
    return round(float(x), 4)


group_uuid = {b: str(uuid.uuid4()) for b in ORDER}
elements = []
children = {b: [] for b in ORDER}
for i, m in enumerate(MESHES):
    piv = BONES[m.bone][1]
    keys = [f"v{k}" for k in range(len(m.verts))]
    verts = {keys[k]: [r(c) for c in (v - piv)] for k, v in enumerate(m.verts)}
    faces = {}
    for fi, f in enumerate(m.faces):
        faces[f"f{fi}"] = {"uv": {keys[k]: uv_of(m, k) for k in f}, "vertices": [keys[k] for k in f], "texture": 0}
    uid = str(uuid.uuid4())
    elements.append({"name": f"{m.bone}_{m.material}_{i}", "color": i % 8, "origin": [r(c) for c in piv], "rotation": [0, 0, 0],
                     "export": True, "visibility": True, "locked": False, "render_order": "default",
                     "allow_mirror_modeling": True, "vertices": verts, "faces": faces, "type": "mesh", "uuid": uid})
    children[m.bone].append(uid)


def group(b):
    return {"name": b, "origin": [r(c) for c in BONES[b][1]], "rotation": [0, 0, 0], "color": 0, "uuid": group_uuid[b],
            "export": True, "mirror_uv": False, "isOpen": False, "locked": False, "visibility": True, "autouv": 0,
            "children": children[b] + [group(c) for c in ORDER if BONES[c][0] == b]}


outliner = [group(b) for b in ORDER if BONES[b][0] is None]

animations = []
for a, times, channels in BAKED:
    animators = {}
    for (b, ch), vals in channels.items():
        an = animators.setdefault(group_uuid[b], {"name": b, "type": "bone", "keyframes": []})
        for tm, v in zip(times, vals):
            an["keyframes"].append({"channel": "rotation" if ch == "rot" else "position",
                                    "data_points": [{"x": str(r(v[0])), "y": str(r(v[1])), "z": str(r(v[2]))}],
                                    "uuid": str(uuid.uuid4()), "time": r(tm), "color": -1, "interpolation": "catmullrom"})
    meta = {}
    if a.get("stride"):
        meta["cycle_blocks"] = r(a["stride"] / a["stance"] / 16.0)
    animations.append({"uuid": str(uuid.uuid4()), "name": a["name"], "loop": a["loop"], "override": False, "length": a["length"],
                       "snapping": 24, "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "",
                       "loop_delay": "", "animators": animators, "interstice": meta})

buf = io.BytesIO()
skin_png.save(buf, "PNG")
model = {
    "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
    "name": "adherent", "model_identifier": "adherent", "visible_box": [3, 4, 0],
    "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
    "resolution": {"width": TEX, "height": TEX},
    "elements": elements, "outliner": outliner,
    "textures": [{"path": "", "name": "adherent.png", "folder": "", "namespace": "", "id": "0", "width": TEX, "height": TEX,
                  "uv_width": TEX, "uv_height": TEX, "particle": False, "render_mode": "default", "visible": True,
                  "mode": "bitmap", "saved": True, "uuid": str(uuid.uuid4()),
                  "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}],
    "animations": animations,
}
with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(model, fh, indent=1)

nv = sum(len(m.verts) for m in MESHES)
nf = sum(len(m.faces) for m in MESHES)
allv = np.array([v for m in MESHES for v in m.verts])
dims = np.ptp(allv, axis=0) / 16
lines = [f"adherent.bbmodel: {len(BONES)} bones, {len(MESHES)} meshes, {nv} vertices, {nf} faces, texture {TEX}x{TEX}, "
         f"{tpp:.1f} texels/px",
         f"rest pose size (blocks): width {dims[0]:.2f}, height {dims[1]:.2f} (shoulder top {allv[:, 1].max() / 16:.2f}), "
         f"length {dims[2]:.2f}"]
Gr = globals_(rear(1.0))
reared = max(point(Gr, m.bone, v)[1] for m in MESHES for v in m.verts[::3]) / 16
lines.append(f"reared height (blocks): {reared:.2f}")
lines += report
with open(REPORT, "w", encoding="utf-8") as fh:
    fh.write("\n".join(lines) + "\n")
print("\n".join(lines))
