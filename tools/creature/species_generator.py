"""
THE INTERSTICE creature family: species generator (Crawler, Strider, Burden, Clinger, Hollow, Watcher,
Fractured). The Adherent v4 keeps its own frozen generator (design_adherent.py); this script derives the
other species from the same anatomical template so the family shares one body plan:

1. Template: the Adherent v4 skeleton (24 bones) and its lofted meshes, built by the same code.
2. Morph: per bone, the segment is lengthened/shortened along its axis ("len") and thickened across it
   ("thick", "width" for the x component), pivots and vertices together; "shift" moves a joint;
   "bend" rotates a bone's whole subtree at rest (posture: a raised neck, a twisted spine, ...).
   Left/right keys can differ (asymmetry); a key without a side applies to both, mirrored.
3. Plant: the rest pose is rolled/pitched/translated so the four contacts sit on the ground; the
   remaining per-limb differences are absorbed by the IK of every clip.
4. Skin: same UV packing and painting, species palette and surface patterns (development texture).
5. Animation: the same clip set as the Adherent (plus "search"), parametrised per species: cadence,
   stride, stance, gait phase offsets, lifts, posture, body motion, observation and recovery timing.
   Contacts are IK-solved per key and validated between keys at 240 Hz with the runtime interpolation.

Time convention (do not change): looping clips are written over a 0..1 phase, one-shot clips (observe,
rear, threat, recover) receive seconds.

Turning in place ("turn"): a looping clip in which the planted hands and feet counter-rotate around the
support centre while the others step round. Played forwards when the body yaw increases, backwards when
it decreases; its "cycle_degrees" says how much yaw one cycle covers, so the runtime drives it by the
rotation actually made (like walk/run by distance).

Space: Blockbench px (16 = 1 block), Y up, ground y = 0, front -Z. Rotations R = Rz * Ry * Rx.

Usage: python -I tools/creature/species_generator.py <species> [<species> ...]   (or "all")
Writes models/<species>.bbmodel and models/<species>_validation.txt; then export with
python -I tools/creature/export_adherent.py <species>

python -I tools/creature/species_generator.py adherent
builds the unmorphed template, checks that its skeleton matches models/adherent.bbmodel, and adds (or
replaces) only the "turn" clip in that file: the Adherent's geometry, texture and other clips are kept
as design_adherent.py wrote them.
"""
import base64
import io
import json
import math
import os
import sys
import uuid

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEX = 512

# ============================================================================ species table
# Morph factors are relative to the Adherent template. Strides/positions are in the species' own px.
SPECIES = {
    # the template itself: only used to author clips merged into models/adherent.bbmodel
    "adherent": dict(title="Adherent", scale=1.0, morph={}, bend={}, merge_only=True,
                     palette=dict(skin=(0.42, 0.40, 0.40), fold=(0.38, 0.27, 0.28))),
    "crawler": dict(
        title="Crawler", scale=0.62,
        morph={"hips": dict(len=0.9), "spine": dict(len=0.9, thick=0.9), "chest": dict(thick=0.85, width=1.1),
               "neck": dict(len=0.6), "arm": dict(len=1.2, thick=0.9), "fore": dict(len=1.25, thick=0.85),
               "fingers": dict(len=1.25), "thigh": dict(len=1.15, thick=0.85), "shin": dict(len=1.2, thick=0.85),
               "foot": dict(len=1.1)},
        bend={"chest": (-5, 0, 0), "neck": (-6, 0, 0), "arm": (0, 0, 18), "thigh": (0, 0, 16)},
        palette=dict(skin=(0.33, 0.31, 0.31), fold=(0.29, 0.20, 0.21), mottle=0.35),
        anim=dict(walk=dict(length=1.1, stride=4.6, stance=0.62, lifts=(1.6, 1.3), centers=0.6, amp=0.7, low=1.0),
                  run=dict(length=0.5, stride=7.5, stance=0.38, lifts=(2.4, 2.0), centers=1.6, amp=0.8),
                  climb=dict(length=1.3, stride=4.2, stance=0.66, lifts=(1.8, 1.4)),
                  idle=dict(breath=0.6, twitch=5.0), recover=dict(length=1.8), rear=dict(hips=48))),
    "strider": dict(
        title="Strider", scale=1.0,
        morph={"hips": dict(len=1.1, thick=0.75, width=0.8), "spine": dict(len=1.2, thick=0.72, width=0.75),
               "chest": dict(thick=0.8, width=0.85), "neck": dict(len=1.15, thick=0.75), "head": dict(len=1.05, thick=0.85),
               "shoulder": dict(thick=0.8), "arm": dict(len=1.75, thick=0.7), "fore": dict(len=1.85, thick=0.7),
               "hand": dict(len=1.1), "fingers": dict(len=1.25), "thigh": dict(len=1.8, thick=0.72),
               "shin": dict(len=1.9, thick=0.65), "foot": dict(len=1.7, thick=0.7), "toes": dict(len=1.1)},
        bend={"neck": (8, 0, 0)},
        palette=dict(skin=(0.52, 0.49, 0.47), fold=(0.44, 0.33, 0.33), tendons=1.6),
        anim=dict(walk=dict(length=3.4, stride=13.0, stance=0.74, lifts=(5.0, 4.5), centers=2.0, amp=0.6),
                  run=dict(length=1.5, stride=19.0, stance=0.45, lifts=(7.0, 6.0), centers=6.5, amp=0.7),
                  climb=dict(length=3.6, stride=11.0, stance=0.75, lifts=(5.0, 4.0)),
                  idle=dict(breath=0.8), recover=dict(length=5.2), rear=dict(hips=58))),
    "burden": dict(
        title="Burden", scale=1.3,
        morph={"hips": dict(thick=1.35, width=1.15), "spine": dict(thick=1.4, width=1.2),
               "chest": dict(len=1.05, thick=1.45, width=1.2), "neck": dict(len=0.55, thick=1.55),
               "head": dict(len=0.9, thick=1.1, width=1.15), "jaw": dict(thick=1.2),
               "shoulder": dict(thick=1.6), "arm": dict(len=0.95, thick=1.5), "fore": dict(len=0.85, thick=1.55),
               "hand": dict(thick=1.35, width=1.3), "fingers": dict(len=0.85, thick=1.4), "thigh": dict(len=0.85, thick=1.55),
               "shin": dict(len=0.85, thick=1.5), "foot": dict(len=0.9, thick=1.4), "toes": dict(thick=1.3)},
        bend={"neck": (-12, 0, 0)},
        palette=dict(skin=(0.40, 0.36, 0.35), fold=(0.36, 0.24, 0.24), scars=0.5),
        anim=dict(walk=dict(length=3.2, stride=7.5, stance=0.8, lifts=(1.6, 1.2), centers=1.2, amp=1.6, heavy=1.0),
                  run=dict(length=1.25, stride=11.0, stance=0.5, lifts=(2.6, 2.2), centers=3.5, amp=1.3, heavy=1.0),
                  climb=dict(length=3.4, stride=6.5, stance=0.8, lifts=(2.4, 1.8)),
                  idle=dict(breath=1.3), recover=dict(length=4.2), rear=dict(hips=42), threat=dict(jaw=-26))),
    "clinger": dict(
        title="Clinger", scale=0.95,
        morph={"hips": dict(thick=0.62, width=1.25), "spine": dict(thick=0.58, width=1.35), "chest": dict(thick=0.62, width=1.4),
               "neck": dict(len=0.55, thick=0.8, width=1.2), "head": dict(thick=0.65, width=1.3), "jaw": dict(thick=0.7, width=1.2),
               "shoulder": dict(width=1.2), "arm": dict(len=1.1, thick=0.9), "fore": dict(len=1.15, thick=0.85),
               "hand": dict(len=1.2, width=1.3, thick=0.8), "fingers": dict(len=2.0, thick=0.85),
               "thigh": dict(thick=0.9), "shin": dict(len=1.05), "toes": dict(len=1.8)},
        bend={"arm": (0, 0, 32), "thigh": (0, 0, 30)},
        palette=dict(skin=(0.45, 0.45, 0.44), fold=(0.36, 0.33, 0.32), mottle=0.7),
        anim=dict(walk=dict(length=3.0, stride=4.5, stance=0.82, lifts=(1.6, 1.2), centers=0.8, amp=0.5, low=2.0),
                  run=dict(length=0.95, stride=8.0, stance=0.45, lifts=(2.6, 2.0), centers=2.0, amp=0.6, low=1.5),
                  climb=dict(length=3.2, stride=4.5, stance=0.84, lifts=(1.6, 1.2)),
                  cling=dict(breath=0.04, grip=20, spread=(30, 22)),
                  idle=dict(breath=0.08), recover=dict(length=3.0), rear=dict(hips=40))),
    "hollow": dict(
        title="Hollow", scale=1.05,
        morph={"hips": dict(thick=0.6, width=0.62), "spine": dict(len=1.12, thick=0.5, width=0.58), "chest": dict(thick=0.66, width=0.72),
               "neck": dict(len=1.2, thick=0.55), "head": dict(len=1.05, thick=0.82), "shoulder": dict(thick=0.7),
               "arm": dict(len=1.05, thick=0.55), "fore": dict(len=1.1, thick=0.5), "hand": dict(thick=0.7),
               "fingers": dict(len=1.15, thick=0.7), "thigh": dict(thick=0.55), "shin": dict(thick=0.5), "foot": dict(thick=0.6)},
        bend={"chest": (-8, 0, 0), "neck": (5, 0, 0)},
        palette=dict(skin=(0.58, 0.55, 0.53), fold=(0.50, 0.38, 0.38), ribs=0.35),
        anim=dict(walk=dict(length=2.8, stride=5.5, stance=0.74, lifts=(2.2, 1.8), centers=1.0, amp=0.9, hitch=1.0,
                            offsets={"leg_l": 0.0, "arm_l": 0.31, "leg_r": 0.52, "arm_r": 0.79}),
                  run=dict(length=1.0, stride=9.0, stance=0.42, lifts=(3.2, 2.8), centers=2.6, amp=1.0, hitch=0.6),
                  climb=dict(length=2.9, stride=5.5, stance=0.76, lifts=(2.6, 2.0)),
                  idle=dict(breath=2.2, irregular=1.0), recover=dict(length=3.8), rear=dict(hips=66))),
    "watcher": dict(
        title="Watcher", scale=1.0,
        morph={"chest": dict(thick=1.1, width=1.05), "neck": dict(len=1.9, thick=0.9), "head": dict(len=1.25, thick=1.05, width=0.9),
               "jaw": dict(len=1.2), "shoulder": dict(thick=1.2), "arm": dict(len=1.05), "fore": dict(len=1.05)},
        bend={"neck": (95, 0, 0), "head": (-88, 0, 0)},
        palette=dict(skin=(0.46, 0.44, 0.44), fold=(0.40, 0.29, 0.30), head_pale=0.12),
        anim=dict(walk=dict(length=2.6, stride=6.0, stance=0.74, lifts=(2.4, 1.8), centers=1.0, amp=0.35, steady_head=1.0),
                  run=dict(length=0.95, stride=10.0, stance=0.42, lifts=(3.4, 3.0), centers=3.0, amp=0.6, steady_head=1.0),
                  climb=dict(length=2.8, stride=6.0, stance=0.75, lifts=(2.8, 2.2)),
                  observe=dict(neck=(-10, 22, 0), head=(-8, 32, 16), delay=0.6, turn=1.6),
                  idle=dict(breath=0.4), recover=dict(length=3.4), rear=dict(hips=74))),
    "fractured": dict(
        title="Fractured", scale=1.0,
        morph={"arm_l": dict(len=1.4, thick=0.9), "fore_l": dict(len=1.45), "hand_l": dict(len=1.15, width=1.1),
               "fingers_l": dict(len=1.3), "shoulder_l": dict(thick=1.35, shift=(0.6, 2.0, 0.0)),
               "arm_r": dict(len=0.8, thick=1.25), "fore_r": dict(len=0.82, thick=1.2), "shoulder_r": dict(shift=(0.0, -0.8, 1.0)),
               "thigh_l": dict(len=1.12), "shin_l": dict(len=1.1), "thigh_r": dict(len=0.95, thick=1.15),
               "chest": dict(width=1.1)},
        bend={"spine": (0, 8, 0), "neck": (0, 14, 0), "head": (0, 0, 12)},
        palette=dict(skin=(0.45, 0.41, 0.40), fold=(0.40, 0.27, 0.27), scars=0.9),
        anim=dict(walk=dict(length=2.4, stride=6.0, stance=0.7, lifts={"arm_l": 3.2, "arm_r": 1.6, "leg_l": 2.0, "leg_r": 2.4},
                            centers={"arm_l": 1.6, "arm_r": 0.4}, amp=1.0, roll=6.0,
                            offsets={"leg_l": 0.0, "arm_l": 0.16, "leg_r": 0.47, "arm_r": 0.86}),
                  run=dict(length=0.9, stride=10.0, stance=0.42, lifts={"arm_l": 4.2, "arm_r": 2.4, "leg_l": 2.8, "leg_r": 3.4},
                           centers={"arm_l": 3.6, "arm_r": 2.0}, amp=1.1, roll=8.0,
                           offsets={"leg_l": 0.0, "leg_r": 0.1, "arm_l": 0.5, "arm_r": 0.62}),
                  climb=dict(length=2.7, stride=6.0, stance=0.74, lifts=(2.8, 2.2),
                             offsets={"leg_l": 0.0, "arm_r": 0.21, "leg_r": 0.5, "arm_l": 0.79}),
                  recover=dict(length=3.6), rear=dict(hips=62))),
}

DEFAULT_ANIM = dict(
    walk=dict(length=2.2, stride=6.0, stance=0.72, lifts=(2.4, 1.8), centers=1.0, amp=1.0,
              offsets={"leg_l": 0.0, "arm_l": 0.25, "leg_r": 0.5, "arm_r": 0.75}),
    run=dict(length=0.85, stride=10.0, stance=0.42, lifts=(3.5, 3.0), centers=3.0, amp=1.0,
             offsets={"leg_l": 0.0, "leg_r": 0.08, "arm_l": 0.48, "arm_r": 0.56}),
    climb=dict(length=2.6, stride=6.0, stance=0.75, lifts=(2.8, 2.2),
               offsets={"leg_l": 0.0, "arm_r": 0.25, "leg_r": 0.5, "arm_l": 0.75}),
    cling=dict(breath=0.18, grip=10, spread=(24, 18)),
    idle=dict(breath=1.0, twitch=0.0, irregular=0.0),
    observe=dict(neck=(-6, 9, 0), head=(-4, 14, 2), delay=0.25, turn=0.5),
    rear=dict(hips=71), threat=dict(jaw=-32), recover=dict(length=3.2),
    # sweep: degrees a planted contact turns round the support centre during its stance
    turn=dict(sweep=16.0, stance=0.7, offsets={"leg_l": 0.0, "arm_r": 0.25, "leg_r": 0.5, "arm_l": 0.75}),
)

if len(sys.argv) < 2:
    sys.exit("usage: species_generator.py <species>|all")
if sys.argv[1] == "all":
    import subprocess
    procs = [subprocess.Popen([sys.executable, "-I", __file__, n]) for n in SPECIES if not SPECIES[n].get("merge_only")]
    sys.exit(max(p.wait() for p in procs))
NAME = sys.argv[1]
if NAME not in SPECIES:
    sys.exit(f"unknown species {NAME}; known: {', '.join(SPECIES)}")
SP = SPECIES[NAME]
SC = SP["scale"]


def cfg(clip):
    c = dict(DEFAULT_ANIM.get(clip, {}))
    c.update(SP.get("anim", {}).get(clip, {}))
    return c


OUT = os.path.join(ROOT, "models", f"{NAME}.bbmodel")
REPORT = os.path.join(ROOT, "models", f"{NAME}_validation.txt")
rng = np.random.default_rng(23 + sum(map(ord, NAME)))


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




# ============================================================================ species morph
# rest contacts of the template (bone carrying it, rest point)
TEMPLATE_CONTACT = {"arm_l": ("hand_l", (9.4, 0.15, -23.9)), "arm_r": ("hand_r", (-9.4, 0.15, -23.9)),
                    "leg_l": ("foot_l", (5.8, 0.1, 15.5)), "leg_r": ("foot_r", (-5.8, 0.1, 15.5))}
MAIN_CHILD = {"hips": "spine", "spine": "chest", "chest": "neck", "neck": "head"}
for _s in "lr":
    MAIN_CHILD.update({f"shoulder_{_s}": f"arm_{_s}", f"arm_{_s}": f"fore_{_s}", f"fore_{_s}": f"hand_{_s}",
                       f"hand_{_s}": f"fingers_{_s}", f"thigh_{_s}": f"shin_{_s}", f"shin_{_s}": f"foot_{_s}",
                       f"foot_{_s}": f"toes_{_s}"})
LEAF_DIR = {"head": (0, -0.35, -1), "jaw": (0, -0.1, -1), "fingers": (0, -0.25, -1), "toes": (0, -0.3, -1)}


def bone_dir(b):
    if b in MAIN_CHILD:
        d = BONES[MAIN_CHILD[b]][1] - BONES[b][1]
    else:
        d = np.array(LEAF_DIR[b.split("_")[0]], float)
    return d / np.linalg.norm(d)


def factors(b):
    """Morph factors of bone b: generic key (both sides, mirrored) overridden by the sided key."""
    f = dict(len=1.0, thick=1.0, width=1.0, shift=(0.0, 0.0, 0.0))
    base = b.split("_")[0]
    side = b.split("_")[1] if "_" in b else None
    generic = dict(SP["morph"].get(base, {}))
    if "shift" in generic and side == "r":
        generic["shift"] = (-generic["shift"][0], generic["shift"][1], generic["shift"][2])
    f.update(generic)
    if side:
        f.update(SP["morph"].get(b, {}))
    return f


def deform(q, b):
    """Morphs an offset q from bone b's rest pivot."""
    f = factors(b)
    d = DIRS[b]
    a = float(q @ d)
    perp = q - a * d
    perp = perp * f["thick"]
    perp[0] *= f["width"]
    return a * f["len"] * d + perp


# 0. uniform scale of the template
for _b, (_p, _piv) in list(BONES.items()):
    BONES[_b] = (_p, _piv * SC)
for _m in MESHES:
    _m.verts = [np.array(v) * SC for v in _m.verts]
CONTACT_REST = {k: (b, np.array(p) * SC) for k, (b, p) in TEMPLATE_CONTACT.items()}

# 1. per-bone segment morph (directions taken from the scaled template)
DIRS = {b: bone_dir(b) for b in ORDER}
OLD = {b: BONES[b][1].copy() for b in ORDER}
NEW = {}
for _b in ORDER:
    _parent = BONES[_b][0]
    if _parent is None:
        NEW[_b] = OLD[_b].copy()
    else:
        _off = deform(OLD[_b] - OLD[_parent], _parent)
        _sh = np.array(factors(_b)["shift"], float) * SC
        NEW[_b] = NEW[_parent] + _off + _sh
for _m in MESHES:
    _m.verts = [NEW[_m.bone] + deform(np.array(v) - OLD[_m.bone], _m.bone) for v in _m.verts]
CONTACT_REST = {k: (b, NEW[b] + deform(p - OLD[b], b)) for k, (b, p) in CONTACT_REST.items()}
for _b in ORDER:
    BONES[_b] = (BONES[_b][0], NEW[_b])


# 2. rest bends: rotate a bone's whole subtree about its pivot (posture baked into the rest geometry)
def subtree(b):
    out = [b]
    for c in ORDER:
        if BONES[c][0] in out and c not in out:
            out.append(c)
    return out


def rotate_points(R, center, pts):
    return [center + R @ (np.array(p) - center) for p in pts]


def rot_deg(r):
    x, y, z = (math.radians(a) for a in r)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def apply_rigid(R, center, bones_set):
    for b in bones_set:
        BONES[b] = (BONES[b][0], rotate_points(R, center, [BONES[b][1]])[0])
    for m in MESHES:
        if m.bone in bones_set:
            m.verts = rotate_points(R, center, m.verts)
    for k, (b, p) in list(CONTACT_REST.items()):
        if b in bones_set:
            CONTACT_REST[k] = (b, rotate_points(R, center, [p])[0])


for _b in ORDER:   # root to leaves
    _r = SP.get("bend", {}).get(_b) or SP.get("bend", {}).get(_b.split("_")[0])
    if not _r:
        continue
    if _b.endswith("_r") and _b.split("_")[0] in SP.get("bend", {}):
        _r = (_r[0], -_r[1], -_r[2])
    apply_rigid(rot_deg(_r), BONES[_b][1].copy(), subtree(_b))


# 3. plant: roll and pitch so the contacts are level, then put them on the ground
def contact_points():
    return {k: p for k, (b, p) in CONTACT_REST.items()}


ALL = set(ORDER)
for _ in range(4):
    c = contact_points()
    yl, yr = (c["arm_l"][1] + c["leg_l"][1]) / 2, (c["arm_r"][1] + c["leg_r"][1]) / 2
    xl, xr = (c["arm_l"][0] + c["leg_l"][0]) / 2, (c["arm_r"][0] + c["leg_r"][0]) / 2
    roll = math.degrees(math.atan2(yr - yl, xl - xr))
    center = np.array([0.0, (yl + yr) / 2, 0.0])
    apply_rigid(rot_deg((0, 0, roll)), center, ALL)
    c = contact_points()
    yh, yf = (c["arm_l"][1] + c["arm_r"][1]) / 2, (c["leg_l"][1] + c["leg_r"][1]) / 2
    zh, zf = (c["arm_l"][2] + c["arm_r"][2]) / 2, (c["leg_l"][2] + c["leg_r"][2]) / 2
    pitch = math.degrees(math.atan2(yh - yf, zf - zh))   # positive x rotation lifts the front (-z)
    center = np.array([0.0, (yh + yf) / 2, (zh + zf) / 2])
    apply_rigid(rot_deg((-pitch, 0, 0)), center, ALL)
_c = contact_points()
_dy = 0.12 - max(p[1] for p in _c.values())   # the shortest limb touches; the IK flexes the others
_dz = -np.mean([p[2] for p in _c.values()]) + (-23.9 + 15.5) / 2 * SC   # keep the support centred like the template
for _b in ORDER:
    BONES[_b] = (BONES[_b][0], BONES[_b][1] + np.array([0.0, _dy, _dz]))
for _m in MESHES:
    _m.verts = [np.array(v) + np.array([0.0, _dy, _dz]) for v in _m.verts]
CONTACT_REST = {k: (b, p + np.array([0.0, _dy, _dz])) for k, (b, p) in CONTACT_REST.items()}
REST_CONTACT_HEIGHT = {k: float(p[1]) for k, (b, p) in CONTACT_REST.items()}
# IK targets: the ground under each rest contact
GROUND_CONTACT = {k: (b, (float(p[0]), 0.12, float(p[2]))) for k, (b, p) in CONTACT_REST.items()}


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
SKIN = np.array(SP["palette"]["skin"])        # species base colour (development texture)
FOLD = np.array(SP["palette"]["fold"])
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
        P = SP["palette"]
        if P.get("mottle"):     # concrete-like blotches (camouflage)
            blot = np.clip((big * 0.6 + fine * 0.4 - 0.5) * 3, 0, 1) * P["mottle"]
            col = col * (1 - 0.35 * blot[..., None]) + np.array((0.30, 0.30, 0.30)) * 0.35 * blot[..., None]
        if P.get("ribs") and m.bone in ("chest", "spine"):
            ribs = (np.sin(vv * 2 * math.pi * 9) > 0.55) & (around < 0.5)
            col *= (1 - P["ribs"] * ribs)[..., None]
        if P.get("scars"):      # healed seams
            seam = np.abs(np.sin((uu * 1.3 + vv * 2.1 + big * 1.5) * math.pi * 2)) < 0.03
            col = col * (1 - 0.35 * P["scars"] * seam[..., None]) + np.array((0.45, 0.25, 0.25)) * 0.35 * P["scars"] * seam[..., None]
        if P.get("tendons"):
            tend2 = np.abs(np.sin(uu * 2 * math.pi * 4 + big * 3)) > 0.97
            col *= (1 - 0.12 * P["tendons"] * tend2)[..., None]
        if P.get("head_pale") and m.material == "head":
            col = np.clip(col + P["head_pale"], 0, 1)
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
JOINTS = {"arm_l": ("arm_l", "fore_l", "shoulder_l"), "arm_r": ("arm_r", "fore_r", "shoulder_r"),
          "leg_l": ("thigh_l", "shin_l", "foot_l"), "leg_r": ("thigh_r", "shin_r", "foot_r")}
# limb -> (bone carrying the contact point, rest contact point on the morphed geometry, IK joints)
CONTACT = {limb: (b, tuple(float(c) for c in p), JOINTS[limb]) for limb, (b, p) in CONTACT_REST.items()}
# limb -> ground target under the rest contact (the IK brings the contact there)
GOAL = {limb: g for limb, (b, g) in GROUND_CONTACT.items()}
FLATTEN = {"arm_l": ("hand_l", ("hips", "spine", "chest", "shoulder_l", "arm_l", "fore_l")),
           "arm_r": ("hand_r", ("hips", "spine", "chest", "shoulder_r", "arm_r", "fore_r")),
           "leg_l": ("toes_l", ("hips", "thigh_l", "shin_l", "foot_l")),
           "leg_r": ("toes_r", ("hips", "thigh_r", "shin_r", "foot_r"))}


def lifts_of(c):
    l = c["lifts"]
    if isinstance(l, dict):
        return dict(l)
    return {"arm_l": l[0], "arm_r": l[0], "leg_l": l[1], "leg_r": l[1]}


def centers_of(c):
    v = c.get("centers", 0.0)
    if isinstance(v, dict):
        return dict(v)
    return {"arm_l": v, "arm_r": v}



def add(pose, b, rot=(0, 0, 0), pos=(0, 0, 0)):
    e = pose.setdefault(b, {"rot": (0, 0, 0), "pos": (0, 0, 0)})
    e["rot"] = tuple(a + c for a, c in zip(e.get("rot", (0, 0, 0)), rot))
    e["pos"] = tuple(a + c for a, c in zip(e.get("pos", (0, 0, 0)), pos))


def solve_contacts(pose, targets, flatten=True, lateral=False):
    """targets: limb -> (dx, dy, dz) offsets of the contact from its rest position (body space).
    lateral: also solve x (the first joint gets an abduction degree of freedom), for turning."""
    from scipy.optimize import minimize
    for limb, off in targets.items():
        b, rest, joints = CONTACT[limb]
        goal = np.array(GOAL[limb]) + np.array(off)
        base = {j: pose.get(j, {}).get("rot", (0, 0, 0)) for j in joints}
        reg = (0.0004, 0.0004, 0.001, 0.0006)

        end, chain = FLATTEN[limb]
        end_rot = pose.get(end, {}).get("rot", (0, 0, 0))

        def with_(v):
            p = {k: dict(e) for k, e in pose.items()}
            for j, dv in zip(joints, v[:3]):
                r = base[j]
                p.setdefault(j, {})["rot"] = (r[0] + dv, r[1], r[2])
            if lateral:
                r = p[joints[0]]["rot"]
                p[joints[0]]["rot"] = (r[0], r[1], r[2] + v[3])
            if flatten:   # palm / toes kept flat on the contact surface, inside the solve
                tot = sum(p.get(c, {}).get("rot", (0, 0, 0))[0] for c in chain)
                p.setdefault(end, {})["rot"] = (-tot, end_rot[1], end_rot[2])
            return p

        def cost(v):
            w = point(globals_(with_(v)), b, rest)
            ex = (w[0] - goal[0]) ** 2 if lateral else 0.0
            return ex + (w[1] - goal[1]) ** 2 + (w[2] - goal[2]) ** 2 + sum(rg * dv * dv for rg, dv in zip(reg, v))

        best = None
        for start in (((0, 0, 0, 0), (15, -15, 0, 0)) if lateral else ((0, 0, 0), (15, -15, 0))):
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


def locomotion(t, stance, stride, lifts, offsets, body, wall=False, centers=None, wall_spread=(24, 18)):
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
            add(pose, f"arm_{s}", rot=(0, 0, sx * wall_spread[0]))
            add(pose, f"thigh_{s}", rot=(0, 0, sx * wall_spread[1]))
    return solve_contacts(pose, targets)


@animation("walk", cfg("walk")["length"], "loop", 41, stride=cfg("walk")["stride"], stance=cfg("walk")["stance"],
           offsets=cfg("walk")["offsets"])
def walk(t):
    c = cfg("walk")

    def body(pose, t, p):
        a = c["amp"]
        heavy = c.get("heavy", 0.0)
        add(pose, "hips", rot=(0, 2.5 * a * S(p), 2.0 * a * S(p)),
            pos=(0, -(0.35 * a + 0.9 * heavy) * abs(S(p * 2)) * SC - c.get("low", 0.0) * SC, 0))
        add(pose, "chest", rot=(1.5 * a * S(p * 2 + 1.0), -3.0 * a * S(p), -2.5 * a * S(p) + c.get("roll", 0.0) * S(p + 0.4)),
            pos=(0, -(0.45 * a + 0.8 * heavy) * max(0, S(p * 2 + 0.6)) * SC - c.get("low", 0.0) * SC, 0))
        if c.get("hitch"):   # a hitch in the gait: a brief drop, then a catch
            add(pose, "spine", rot=(-5 * c["hitch"] * max(0.0, S(p * 3 + 0.7)) ** 8, 0, 3 * c["hitch"] * S(p * 5)))
        if c.get("roll"):
            add(pose, "hips", rot=(0, 1.5 * c["roll"] * S(p + 1.1), -0.6 * c["roll"] * S(p)))
        head_k = 0.2 if c.get("steady_head") else 1.0
        add(pose, "neck", rot=(-1.5 * a * S(p * 2 + 1.4), 2.0 * a * S(p) * head_k, 0))
        add(pose, "head", rot=(1.2 * a * S(p * 2 + 1.8) * head_k, -1.5 * a * S(p) * head_k - (2.0 * a * S(p) * (1 - head_k)), 0))
    return locomotion(t, c["stance"], c["stride"], lifts_of(c), c["offsets"], body, centers=centers_of(c))


@animation("run", cfg("run")["length"], "loop", 41, stride=cfg("run")["stride"], stance=cfg("run")["stance"],
           offsets=cfg("run")["offsets"])
def run(t):
    c = cfg("run")

    def body(pose, t, p):
        a = c["amp"]
        heavy = c.get("heavy", 0.0)
        add(pose, "hips", rot=(5 * a * S(p), 0, c.get("roll", 0.0) * 0.5 * S(p + 0.3)),
            pos=(0, -(1.2 * a + 1.2 * heavy) * abs(S(p)) * SC - c.get("low", 0.0) * SC, 0))
        add(pose, "spine", rot=(-6 * a * S(p + 0.6), 0, 0))
        add(pose, "chest", rot=(-5 * a * S(p + 1.2), 0, 1.5 * a * S(p) + c.get("roll", 0.0) * S(p + 0.9)),
            pos=(0, -0.9 * a * max(0, S(p + 2.0)) * SC - c.get("low", 0.0) * SC, 0))
        if c.get("hitch"):
            add(pose, "spine", rot=(-6 * c["hitch"] * max(0.0, S(p * 2 + 0.4)) ** 6, 0, 4 * c["hitch"] * S(p * 3)))
        head_k = 0.3 if c.get("steady_head") else 1.0
        add(pose, "neck", rot=(6 + 3 * a * S(p + 1.8) * head_k, 0, 0))
        add(pose, "head", rot=(-4 * a * S(p + 2.2) * head_k, 0, 0))
    return locomotion(t, c["stance"], c["stride"], lifts_of(c), c["offsets"], body, centers=centers_of(c))


@animation("climb", cfg("climb")["length"], "loop", 33, stride=cfg("climb")["stride"], stance=cfg("climb")["stance"],
           offsets=cfg("climb")["offsets"])
def climb(t):
    c = cfg("climb")
    sp = cfg("cling")["spread"]

    def body(pose, t, p):
        add(pose, "hips", pos=(0, -3.0 * SC, 0), rot=(0, 3 * S(p), 3 * S(p)))
        add(pose, "chest", pos=(0, -2.4 * SC, 0), rot=(0, -4 * S(p), -4 * S(p)))
        add(pose, "neck", rot=(10, 3 * S(p), 0))
        add(pose, "head", rot=(6, -2 * S(p), 0))
    return locomotion(t, c["stance"], c["stride"], lifts_of(c), c["offsets"], body, wall=True, wall_spread=sp)


@animation("cling", 6.0, "loop", 25)
def cling(t):
    c = cfg("cling")
    p = TAU * t
    pose = {}
    b = c["breath"] / 0.18
    add(pose, "hips", pos=(0, -3.0 * SC, 0))
    add(pose, "chest", pos=(0, (-2.4 + 0.18 * b * S(p * 2)) * SC, 0), rot=(0.6 * b * S(p * 2), 0, 0))
    add(pose, "neck", rot=(10, 0, 0))
    for s, sx in (("l", 1), ("r", -1)):
        add(pose, f"arm_{s}", rot=(0, 0, sx * c["spread"][0]))
        add(pose, f"thigh_{s}", rot=(0, 0, sx * c["spread"][1]))
        add(pose, f"fingers_{s}", rot=(c["grip"], 0, 0))   # gripping
    return solve_contacts(pose, {l: (0, 0, 0) for l in CONTACT})


@animation("idle", 7.0, "loop", 29)
def idle(t):
    c = cfg("idle")
    p = TAU * t
    pose = {}
    breath = S(p * 2) + c["irregular"] * 0.6 * S(p * 5 + 1.3) * max(0.0, S(p * 1 + 0.4))
    shift = S(p)
    k = c["breath"]
    add(pose, "chest", pos=(0, 0.22 * k * breath * SC, 0), rot=(0.7 * k * breath, 0, 0.6 * shift))
    add(pose, "spine", rot=(0.4 * k * breath, 0, 0))
    add(pose, "hips", pos=(0.12 * shift * SC, 0, 0), rot=(0, 0, -0.4 * shift))
    add(pose, "neck", rot=(0.4 * S(p * 2 + 0.8), 0, 0))
    tw = c["twitch"] * (1.0 if S(p * 3 + 0.5) > 0.2 else -0.6 if S(p * 3 + 0.5) < -0.6 else 0.0)   # sudden small head jerks
    add(pose, "head", rot=(0.4 * tw, 0.6 * S(p + 0.3) + tw, 0))
    return solve_contacts(pose, {l: (0, 0, 0) for l in CONTACT})


@animation("observe", 4.0, "hold", 17)
def observe(t):
    # seconds: the body stops; the neck and head turn late and slowly, then hold
    c = cfg("observe")
    pose = {}
    k = min(1.0, max(0.0, (t - c["delay"]) / c["turn"]))
    k = k * k * (3 - 2 * k)
    add(pose, "neck", rot=tuple(v * k for v in c["neck"]))
    add(pose, "head", rot=tuple(v * k for v in c["head"]))
    return solve_contacts(pose, {l: (0, 0, 0) for l in CONTACT})


@animation("search", 3.6, "loop", 25)
def search(t):
    # phase: head low, sweeping, sniffing pauses
    p = TAU * t
    pose = {}
    add(pose, "neck", rot=(-12 + 3 * S(p * 2), 18 * S(p), 0))
    add(pose, "head", rot=(-6 + 4 * max(0.0, S(p * 4)) ** 4, 14 * S(p + 0.5), 4 * S(p)))
    add(pose, "chest", pos=(0, -0.8 * SC, 0))
    return solve_contacts(pose, {l: (0, 0, 0) for l in CONTACT})


@animation("rear", 1.6, "hold", 25)
def rear(t):
    # seconds: hind feet anchored, pelvis over the support, chest rising, arms stop carrying weight
    c = cfg("rear")
    k = min(1.0, t / 0.75)
    k = k * k * (3 - 2 * k)
    q = c["hips"] / 71.0
    pose = {}
    add(pose, "hips", rot=(c["hips"] * k, 0, 0), pos=(0, 1.5 * k * SC, 5.0 * k * q * SC))
    add(pose, "spine", rot=(9 * k * q, 0, 0))
    add(pose, "chest", rot=(3 * k, 0, 0))
    add(pose, "neck", rot=(-22 * k * q, 0, 0))
    add(pose, "head", rot=(-26 * k * q, 0, 0))
    for s, sx in (("l", 1), ("r", -1)):
        add(pose, f"arm_{s}", rot=(-66 * k * q, 0, sx * 6 * k))
        add(pose, f"fore_{s}", rot=(-14 * k, 0, 0))
        add(pose, f"hand_{s}", rot=(-30 * k, 0, 0))
        add(pose, f"fingers_{s}", rot=(-25 * k, 0, 0))
    return solve_contacts(pose, {"leg_l": (0, 0, 0), "leg_r": (0, 0, 0)})


@animation("threat", 1.8, "hold", 19)
def threat(t):
    # seconds: the neck tenses and the mouth opens progressively
    c = cfg("threat")
    k = min(1.0, t / 1.2)
    k = k * k * (3 - 2 * k)
    pose = {}
    add(pose, "neck", rot=(8 * k, 0, 0))
    add(pose, "head", rot=(10 * k, 0, 0))
    add(pose, "jaw", rot=(c["jaw"] * k, 0, 0))
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
        add(pose, f"arm_{s}", rot=(-45 + 35 * S(p + i * 1.7), 0, sx * (35 + 20 * S(p * 2 + i))))
        add(pose, f"fore_{s}", rot=(20 * S(p + i), 0, 0))
        add(pose, f"fingers_{s}", rot=(-20 + 25 * S(p * 2 + i), 0, 0))
        add(pose, f"thigh_{s}", rot=(-30 + 25 * S(p + i * 2), 0, sx * 25))
        add(pose, f"shin_{s}", rot=(20 * S(p + i), 0, 0))
    return pose


@animation("recover", cfg("recover")["length"], "hold", 33)
def recover(t):
    # seconds: impact absorbed flat on the ground, one hand plants, the limbs come back under the body
    L = cfg("recover")["length"]
    u = t * 3.2 / L          # the template timing, stretched to the species' recovery time
    pose = {}
    impact = max(0.0, 1 - u / 0.35)
    k = min(1.0, max(0.0, (u - 0.5) / 2.3))
    k = k * k * (3 - 2 * k)
    down = 1 - k
    add(pose, "hips", pos=(0, (-9.0 * down - 1.0 * impact) * SC, 0), rot=(0, 0, 14 * down))
    add(pose, "spine", pos=(0, -1.5 * down * SC, 0))
    add(pose, "chest", pos=(0, -6.5 * down * SC, 0), rot=(6 * down, 0, -10 * down))
    add(pose, "neck", rot=(20 * down, 0, 0))
    add(pose, "head", rot=(10 * down, 12 * down, 16 * down))
    targets = {}
    for i, (s, sx) in enumerate((("l", 1), ("r", -1))):
        delay = 0.0 if s == "l" else 0.45
        ki = min(1.0, max(0.0, (u - 0.5 - delay) / 1.4))
        ki = ki * ki * (3 - 2 * ki)
        targets[f"arm_{s}"] = (0, 0, 4.0 * (1 - ki) * SC)
        targets[f"leg_{s}"] = (0, 0, -3.0 * (1 - ki) * SC)
        add(pose, f"arm_{s}", rot=(0, 0, sx * 35 * (1 - ki)))
        add(pose, f"thigh_{s}", rot=(0, 0, sx * 30 * (1 - ki)))
    return solve_contacts(pose, targets)



def turn_pivot():
    return np.array([0.0, 0.0, float(np.mean([GOAL[l][2] for l in CONTACT]))])


def turn_angle(ph, stance, sweep):
    """Angle (deg) of a contact round the pivot, in body space: planted contacts sweep -sweep/2 -> +sweep/2
    (the body turns the other way above them); swinging ones come back, smoothstepped."""
    if ph < stance:
        return -sweep / 2 + sweep * ph / stance, 0.0
    q = (ph - stance) / (1 - stance)
    return sweep / 2 - sweep * q * q * (3 - 2 * q), q


def turn_target(limb, ph, c):
    a, q = turn_angle(ph, c["stance"], c["sweep"])
    g = np.array(GOAL[limb], float) - turn_pivot()
    ry = rot_mat((0, a, 0))
    w = ry @ g + turn_pivot()
    lift = (lifts_of(c)[limb] * math.sin(math.pi * q)) if q > 0 else 0.0
    return w + np.array([0.0, lift, 0.0])


def turn_cfg():
    c = cfg("turn")
    w = cfg("walk")
    c.setdefault("length", round(w["length"] * 0.85, 3))   # the species' own cadence
    c.setdefault("lifts", tuple(0.8 * x for x in w["lifts"]) if not isinstance(w["lifts"], dict)
                 else {k: 0.8 * v for k, v in w["lifts"].items()})
    return c


@animation("turn", turn_cfg()["length"], "loop", 33, turn=turn_cfg())
def turn(t):
    c = turn_cfg()
    p = TAU * t
    pose = {}
    # symmetric body motion only (the clip also plays backwards for the other direction)
    add(pose, "hips", rot=(0, 1.2 * S(p * 2), 1.0 * S(p * 2 + 0.5)), pos=(0, -0.3 * abs(S(p * 2)) * SC, 0))
    add(pose, "chest", rot=(0.8 * S(p * 2 + 1.0), -1.6 * S(p * 2), -1.2 * S(p * 2 + 0.5)))
    add(pose, "neck", rot=(-0.8 * S(p * 2 + 1.4), 0, 0))
    targets = {}
    for limb, off in c["offsets"].items():
        ph = (t + off) % 1.0
        targets[limb] = tuple(turn_target(limb, ph, c) - np.array(GOAL[limb], float))
        if limb.startswith("arm") and ph >= c["stance"]:
            q = (ph - c["stance"]) / (1 - c["stance"])
            add(pose, f"fingers_{limb[-1]}", rot=(-12 * math.sin(math.pi * q), 0, 0))
    return solve_contacts(pose, targets, lateral=True)


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
    if a["name"] == "turn":
        c = a["turn"]
        worst_y = worst_xz = 0.0
        wl = {}
        steps = int(a["length"] * 240)
        for si in range(steps + 1):
            tm = a["length"] * si / steps
            pose = {}
            for (b, ch), vals in channels.items():
                pose.setdefault(b, {})[ch] = tuple(sample_channel(times, vals, tm, True, a["length"]))
            G = globals_(pose)
            for limb in CONTACT:
                ph = (tm / a["length"] + c["offsets"][limb]) % 1
                if ph >= c["stance"] * 0.97:
                    continue
                w = point(G, CONTACT[limb][0], CONTACT[limb][1])
                e = turn_target(limb, ph, c)
                worst_y = max(worst_y, abs(w[1] - e[1]))
                d = math.hypot(w[0] - e[0], w[2] - e[2])
                worst_xz = max(worst_xz, d)
                wl[limb] = max(wl.get(limb, 0.0), abs(w[1] - e[1]), d)
        report.append(f"turn     planted contact error <= {worst_y:.3f} px height, {worst_xz:.3f} px on the ground "
                      f"({c['sweep'] / c['stance']:.1f} deg per cycle, interpolated, 240 Hz) ["
                      + " ".join(f"{k} {v:.2f}" for k, v in wl.items()) + "]")
    if a["name"] in ("walk", "run", "climb", "idle", "cling", "rear", "observe", "search"):
        loop = a["loop"] == "loop"
        worst_y = worst_slip = 0.0
        wl = {}
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
                    ph = (tm / a["length"] + a["offsets"][limb]) % 1
                    if ph < a["stance"] * 0.97:
                        worst_y = max(worst_y, abs(w[1] - GOAL[limb][1]))   # height only; z checked as slip
                        wl[limb] = max(wl.get(limb, 0.0), abs(w[1] - GOAL[limb][1]))
                        if limb in prev and prev[limb][0] < a["stance"] * 0.97 and ph > prev[limb][0]:
                            expected = a["stride"] / a["stance"] * (a["length"] / steps) / a["length"]
                            worst_slip = max(worst_slip, abs((w[2] - prev[limb][1]) - expected))
                        prev[limb] = (ph, w[2])
                    else:
                        prev.pop(limb, None)
                else:
                    worst_y = max(worst_y, abs(w[1] - GOAL[limb][1]))
                    wl[limb] = max(wl.get(limb, 0.0), abs(w[1] - GOAL[limb][1]))
                    if limb in prev:
                        worst_slip = max(worst_slip, abs(w[2] - prev[limb]))
                    prev[limb] = w[2]
        report.append(f"{a['name']:8s} contact height error <= {worst_y:.3f} px, slip per 1/240 s <= {worst_slip:.4f} px "
                      f"(interpolated, sampled at 240 Hz) [" + " ".join(f"{k} {v:.2f}" for k, v in wl.items()) + "]")

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
    if a.get("turn"):
        meta["cycle_degrees"] = r(a["turn"]["sweep"] / a["turn"]["stance"])
    animations.append({"uuid": str(uuid.uuid4()), "name": a["name"], "loop": a["loop"], "override": False, "length": a["length"],
                       "snapping": 24, "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "",
                       "loop_delay": "", "animators": animators, "interstice": meta})

if SP.get("merge_only"):
    # the Adherent: keep design_adherent.py's file, add/replace only the clips authored here (turn)
    with open(OUT, encoding="utf-8") as fh:
        existing = json.load(fh)
    groups = {}

    def walk_groups(nodes):
        for n in nodes:
            if isinstance(n, dict):
                groups[n["name"]] = n
                walk_groups(n.get("children", []))
    walk_groups(existing["outliner"])
    worst = max(float(np.abs(np.array(groups[b]["origin"], float) - BONES[b][1]).max()) for b in ORDER)
    if worst > 0.05:   # the file stores 4 decimals; the re-planted template lands within a few hundredths of a px
        sys.exit(f"the template skeleton differs from models/adherent.bbmodel by {worst:.3f} px; not merging")
    merged = [a for a in animations if a["name"] == "turn"]
    for a in merged:
        a["animators"] = {groups[an["name"]]["uuid"]: an for an in a["animators"].values()}
    existing["animations"] = [a for a in existing["animations"] if a["name"] not in {m["name"] for m in merged}] + merged
    NL = chr(10)
    with open(OUT, "w", encoding="utf-8", newline=NL) as fh:
        json.dump(existing, fh, indent=1)
    turn_lines = [l for l in report if l.startswith("turn")]
    with open(os.path.join(ROOT, "models", "adherent_turn_validation.txt"), "w", encoding="utf-8") as fh:
        fh.write(f"adherent.bbmodel: template skeleton matches within {worst:.4f} px; merged clips: "
                 + ", ".join(m["name"] for m in merged) + NL + NL.join(turn_lines) + NL)
    print(f"merged {[m['name'] for m in merged]} into {OUT} (skeleton match {worst:.4f} px)")
    print(NL.join(turn_lines))
    sys.exit(0)

buf = io.BytesIO()
skin_png.save(buf, "PNG")
model = {
    "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
    "name": NAME, "model_identifier": NAME, "visible_box": [3, 4, 0],
    "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
    "resolution": {"width": TEX, "height": TEX},
    "elements": elements, "outliner": outliner,
    "textures": [{"path": "", "name": f"{NAME}.png", "folder": "", "namespace": "", "id": "0", "width": TEX, "height": TEX,
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
lines = [f"{NAME}.bbmodel ({SP['title']}): {len(BONES)} bones, {len(MESHES)} meshes, {nv} vertices, {nf} faces, texture {TEX}x{TEX}, "
         f"{tpp:.1f} texels/px",
         f"rest pose size (blocks): width {dims[0]:.2f}, height {dims[1]:.2f} (shoulder top {allv[:, 1].max() / 16:.2f}), "
         f"length {dims[2]:.2f}"]
Gr = globals_(rear(1.0))
reared = max(point(Gr, m.bone, v)[1] for m in MESHES for v in m.verts[::3]) / 16
lines.append(f"reared height (blocks): {reared:.2f}")
lines.append("rest contact heights before IK (px): " + ", ".join(f"{k} {v:.2f}" for k, v in REST_CONTACT_HEIGHT.items()))
lines += report
with open(REPORT, "w", encoding="utf-8") as fh:
    fh.write("\n".join(lines) + "\n")
print("\n".join(lines))
