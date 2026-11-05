"""
THE INTERSTICE creature family: species generator (Adherent, Crawler, Strider, Burden, Clinger, Hollow,
Watcher, Fractured). Every species, the Adherent included, is derived from one anatomical template so
the family shares one body plan:

1. Template (v5): the Adherent skeleton (24 bones) and its lofted meshes. Paths are Catmull-Rom
   subdivided; limbs carry muscle bellies (biceps/triceps, forearm flexors, quadriceps/hamstrings),
   tendon ridges and skin folds; the body has erector muscles along the spine, a row of vertebral
   processes breaking the skin, ribs under the flanks, scapular ridges, elbow points, knee caps and hock
   spurs; the head has a brow ridge, rows of sunken sockets where eyes would be, nostril slits and two
   interlocking rows of teeth.
2. Morph: per bone, the segment is lengthened/shortened along its axis ("len") and thickened across it
   ("thick", "width" for the x component), pivots and vertices together; "shift" moves a joint;
   "bend" rotates a bone's whole subtree at rest (posture: a raised neck, a twisted spine, ...).
   Left/right keys can differ (asymmetry); a key without a side applies to both, mirrored.
3. Plant: the rest pose is rolled/pitched/translated so the four contacts sit on the ground; the
   remaining per-limb differences are absorbed by the IK of every clip.
4. Skin: UV packing and procedural painting (species palette, countershading, wrinkles at the joints,
   mottling, pores, veins, species patterns), with ambient occlusion baked from the rest pose
   (development texture until the PixelLab art replaces it).
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

Since v5 the Adherent itself ("adherent", the unmorphed template) is generated here too; design_adherent.py
is the frozen v4 generator, kept for reference (its output: models/archive/adherent_v4.bbmodel).
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
    # the template itself (the dominant species): no morph, the ash-grey skin of the v4 Adherent
    "adherent": dict(title="Adherent", scale=1.0, morph={}, bend={},
                     palette=dict(skin=(0.47, 0.43, 0.42), fold=(0.42, 0.29, 0.30))),
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
                  idle=dict(breath=0.6, twitch=5.0), recover=dict(length=1.8), rear=dict(hips=48),
                  crawl=dict(length=1.1, stride=5.0, spread=(30, 24), weave=1.4))),
    "strider": dict(
        title="Strider", scale=1.0,
        morph={"hips": dict(len=1.1, thick=0.75, width=0.8), "spine": dict(len=1.2, thick=0.72, width=0.75),
               "chest": dict(thick=0.8, width=0.85), "neck": dict(len=1.15, thick=0.75), "head": dict(len=1.05, thick=0.85),
               "shoulder": dict(thick=0.8), "arm": dict(len=1.75, thick=0.7), "fore": dict(len=1.85, thick=0.7),
               "hand": dict(len=1.1), "fingers": dict(len=1.25), "thigh": dict(len=1.8, thick=0.72),
               "shin": dict(len=1.9, thick=0.65), "foot": dict(len=1.7, thick=0.7), "toes": dict(len=1.1)},
        bend={"neck": (8, 0, 0)},
        palette=dict(skin=(0.52, 0.49, 0.47), fold=(0.44, 0.33, 0.33), tendons=1.6),
        anim=dict(walk=dict(length=3.4, stride=13.0, stance=0.74, lifts=(5.0, 4.5), centers=2.0, amp=0.6, peel=10.0),
                  run=dict(length=1.5, stride=19.0, stance=0.45, lifts=(7.0, 6.0), centers=6.5, amp=0.7),
                  climb=dict(length=3.6, stride=11.0, stance=0.75, lifts=(5.0, 4.0)),
                  idle=dict(breath=0.8), recover=dict(length=5.2), rear=dict(hips=58))),
    "burden": dict(
        title="Burden", scale=1.3, tex=1024,
        morph={"hips": dict(thick=1.35, width=1.15), "spine": dict(thick=1.4, width=1.2),
               "chest": dict(len=1.05, thick=1.45, width=1.2), "neck": dict(len=0.55, thick=1.55),
               "head": dict(len=0.9, thick=1.1, width=1.15), "jaw": dict(thick=1.2),
               "shoulder": dict(thick=1.6), "arm": dict(len=0.95, thick=1.5), "fore": dict(len=0.85, thick=1.55),
               "hand": dict(thick=1.35, width=1.3), "fingers": dict(len=0.85, thick=1.4), "thigh": dict(len=0.85, thick=1.55),
               "shin": dict(len=0.85, thick=1.5), "foot": dict(len=0.9, thick=1.4), "toes": dict(thick=1.3)},
        bend={"neck": (-12, 0, 0)},
        palette=dict(skin=(0.40, 0.36, 0.35), fold=(0.36, 0.24, 0.24), scars=0.5), rib_relief=0.4,
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
        palette=dict(skin=(0.58, 0.55, 0.53), fold=(0.50, 0.38, 0.38), ribs=0.35), rib_relief=1.8,
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
                  crawl=dict(neck=-78.0, head=62.0),
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
                            offsets={"leg_l": 0.0, "arm_l": 0.16, "leg_r": 0.47, "arm_r": 0.86}, peel=10.0),
                  run=dict(length=0.9, stride=10.0, stance=0.42, lifts={"arm_l": 4.2, "arm_r": 2.4, "leg_l": 2.8, "leg_r": 3.4},
                           centers={"arm_l": 3.6, "arm_r": 2.0}, amp=1.1, roll=8.0,
                           offsets={"leg_l": 0.0, "leg_r": 0.1, "arm_l": 0.5, "arm_r": 0.62}),
                  climb=dict(length=2.7, stride=6.0, stance=0.74, lifts=(2.8, 2.2),
                             offsets={"leg_l": 0.0, "arm_r": 0.21, "leg_r": 0.5, "arm_l": 0.79}),
                  recover=dict(length=3.6), rear=dict(hips=62))),
}

# Family look: moss (hanging strands with their own bones, clumps lying on the back) and eyes (small, deep red,
# recessed in the sockets; "glow" scales the emissive atlas, the game adds the species' light on top).
LOOK = {
    "adherent": dict(moss=dict(strands=30, patches=14, length=(2.0, 6.0), width=0.55), eyes=dict(per_side=3, size=1.0, glow=1.0)),
    # small and quick: little moss, short; sharp eyes
    "crawler": dict(moss=dict(strands=12, patches=7, length=(1.0, 2.6), width=0.42), eyes=dict(per_side=3, size=0.9, glow=1.1)),
    # long strands swinging from the long limbs; two eyes per side
    "strider": dict(moss=dict(strands=24, patches=8, length=(3.0, 9.0), width=0.48), eyes=dict(per_side=2, size=1.0, glow=0.85)),
    # heavy growth on the heavy body, long and thick
    "burden": dict(moss=dict(strands=42, patches=16, length=(3.0, 8.0), width=0.72, green=(0.15, 0.18, 0.11)),
                   eyes=dict(per_side=2, size=1.15, glow=0.9)),
    # overgrown: it stays still for minutes and the wall grows on it; dim, tiny eyes
    "clinger": dict(moss=dict(strands=46, patches=18, length=(2.0, 7.0), width=0.6, green=(0.19, 0.21, 0.16)),
                    eyes=dict(per_side=3, size=0.7, glow=0.6)),
    # starved: a few thin wisps
    "hollow": dict(moss=dict(strands=8, patches=3, length=(1.5, 4.0), width=0.38), eyes=dict(per_side=3, size=0.9, glow=0.7)),
    # the watcher's eyes are the brightest of the family
    "watcher": dict(moss=dict(strands=26, patches=10, length=(2.5, 7.0), width=0.5), eyes=dict(per_side=3, size=1.25, glow=1.6)),
    # asymmetric even here: three eyes on the left, one on the right
    "fractured": dict(moss=dict(strands=30, patches=12, length=(2.0, 7.0), width=0.55), eyes=dict(left=3, right=1, size=1.0, glow=1.2)),
}
for _k, _v in LOOK.items():
    SPECIES[_k].update(_v)

DEFAULT_ANIM = dict(
    # stab: share of the body's pitch/yaw/roll the head cancels (the gaze holds its line); reach: forward
    # overshoot of a swinging hand or foot before it plants; peel: degrees the wrist lifts off the ground at the
    # end of a hand's stance (the fingers stay down) and the toes trail after lift-off; sway: (px, deg) the
    # pelvis shifts and rolls over the limbs carrying the weight
    walk=dict(length=2.2, stride=6.0, stance=0.72, lifts=(2.4, 1.8), centers=1.0, amp=1.0,
              offsets={"leg_l": 0.0, "arm_l": 0.25, "leg_r": 0.5, "arm_r": 0.75},
              stab=0.55, reach=0.12, peel=22.0, sway=(0.35, 1.6), bounce=0.35),
    run=dict(length=0.85, stride=10.0, stance=0.42, lifts=(3.5, 3.0), centers=3.0, amp=1.0,
             offsets={"leg_l": 0.0, "leg_r": 0.08, "arm_l": 0.48, "arm_r": 0.56},
             stab=0.4, reach=0.08, peel=14.0, sway=(0.2, 1.0), bounce=1.6),
    climb=dict(length=2.6, stride=6.0, stance=0.75, lifts=(2.8, 2.2),
               offsets={"leg_l": 0.0, "arm_r": 0.25, "leg_r": 0.5, "arm_l": 0.75},
               stab=0.3, reach=0.1, peel=16.0, sway=(0.0, 1.2)),
    cling=dict(breath=0.18, grip=10, spread=(24, 18)),
    # crawl: flattened to pass under one block (the body drops until the back is below CRAWL_TOP px), limbs
    # splayed out like a lizard's, the spine weaving side to side, the head kept low
    crawl=dict(length=2.0, stride=8.0, stance=0.72, lifts=(1.4, 1.1), spread=(46, 38), neck=-8.0, head=6.0, weave=1.0,
               offsets={"leg_l": 0.0, "arm_r": 0.25, "leg_r": 0.5, "arm_l": 0.75}),
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
    procs = [subprocess.Popen([sys.executable, "-I", __file__, n]) for n in SPECIES]
    sys.exit(max(p.wait() for p in procs))
NAME = sys.argv[1]
if NAME not in SPECIES:
    sys.exit(f"unknown species {NAME}; known: {', '.join(SPECIES)}")
SP = SPECIES[NAME]
SC = SP["scale"]
TEX = SP.get("tex", TEX)   # a bigger body gets a bigger atlas, to keep about 4-5 texels per px


# v7 gait: the v6 strides were far too short for the speeds the server moves the creatures at (5-6 leg cycles a
# second: a frantic hop). Strides are scaled up for every species, the run gets a short stance (moments with no
# limb on the ground) and a bounce driven by which limbs carry the weight; the game picks walk or run from the
# actual speed (AdherentAnimator), not only from the behaviour.
GAIT = {"walk": dict(stride=1.9, lift=1.15),
        "run": dict(stride=1.45, stance=0.85, stance_min=0.32, lift=1.3)}


def cfg(clip):
    c = dict(DEFAULT_ANIM.get(clip, {}))
    c.update(SP.get("anim", {}).get(clip, {}))
    g = GAIT.get(clip)
    if g:
        c["stride"] = c["stride"] * g["stride"]
        if "stance" in g:
            c["stance"] = max(g["stance_min"], c["stance"] * g["stance"])
        l = c["lifts"]
        c["lifts"] = {k: v * g["lift"] for k, v in l.items()} if isinstance(l, dict) else tuple(v * g["lift"] for v in l)
        # the hands' stance moves forward with the longer stride: an arm reaches well ahead of the shoulder but not
        # far behind it (with the v6 centres the planted hand lost the ground at the end of a long stance)
        k = g["stride"] * 1.25
        ce = c.get("centers", 0.0)
        c["centers"] = {a: v * k for a, v in ce.items()} if isinstance(ce, dict) else ce * k
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


def _cr(p0, p1, p2, p3, f):
    return 0.5 * ((2 * p1) + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f * f + (-p0 + 3 * p1 - 3 * p2 + p3) * f * f * f)


def path_interp(path, x):
    """Catmull-Rom point of a loft path at fractional control index x: (center, rx, ry)."""
    P = [(np.array(c, float), float(rx), float(ry)) for c, rx, ry in path]
    n = len(P)
    i = min(int(math.floor(x)), n - 2)
    f = x - i

    def g(k):
        return P[min(max(k, 0), n - 1)]
    p0, p1, p2, p3 = g(i - 1), g(i), g(i + 1), g(i + 2)
    c = _cr(p0[0], p1[0], p2[0], p3[0], f)
    rx = max(0.03, _cr(p0[1], p1[1], p2[1], p3[1], f))
    ry = max(0.03, _cr(p0[2], p1[2], p2[2], p3[2], f))
    return c, rx, ry


def refine(path, sub):
    """Subdivides a loft path with Catmull-Rom (sub rings per control segment)."""
    if sub <= 1:
        return [(np.array(c, float), rx, ry) for c, rx, ry in path]
    n = len(path)
    return [path_interp(path, i / sub) for i in range((n - 1) * sub + 1)]


def path_point(path, s, a, shape=None, out=1.0):
    """A point on (out=1) or off (out>1) the surface of a loft path, s in 0..1 along it, a the angle round it
    (0 = +x side, 90 deg = the frame's up: dorsal on the torso, anterior on the limbs)."""
    n = len(path)
    x = s * (n - 1)
    c, rx, ry = path_interp(path, x)
    c2, _, _ = path_interp(path, min(n - 1, x + 0.05))
    c1, _, _ = path_interp(path, max(0.0, x - 0.05))
    side, up = frame(c2 - c1)
    kx, ky = shape(a, s) if shape else (1.0, 1.0)
    return c + side * rx * kx * out * math.cos(a) + up * ry * ky * out * math.sin(a)


def loft(bone_name, material, path, sides=10, shape=None, cap0=True, cap1=True, sub=1, blend=None):
    """path: list of (center, rx, ry). Builds rings around a curve; shape(a, s01) -> (kx, ky) multipliers.
    sub: Catmull-Rom subdivision of the path (smooth silhouettes, room for muscle relief).
    blend: (other_bone, s0, s1): the skin is carried by other_bone at the start of the path and hands over
    to bone_name between s0 and s1 (smoothstep), so a limb grows out of the torso instead of being stuck
    into it; the exporter mixes this with its usual joint blending."""
    m = Mesh(bone_name, material)
    m.blend = blend
    path = refine(path, sub)
    pts = [np.array(p[0], float) for p in path]
    n = len(pts)
    rings = []
    m.ring_centers = []
    m.sides = sides
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
        m.ring_centers.append(len(rings) - 1)
    m.rings = rings
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


def spike(bone_name, material, base, tip, r0, sides=6, rings=3):
    """A tapering cone (vertebral process, claw, tooth, bony spur) from base to tip."""
    base, tip = np.array(base, float), np.array(tip, float)
    path = [(base + (tip - base) * f, max(0.03, r0 * (1 - f) ** 0.8), max(0.03, r0 * (1 - f) ** 0.8))
            for f in np.linspace(0, 1, rings)]
    return loft(bone_name, material, path, sides=sides, cap1=False)


def bump(s, c, w):
    return math.exp(-((s - c) / w) ** 2)


def torso_shape(a, s):
    # dorsal ridge on top, slightly flattened belly, back muscles either side of the spine
    sa, ca = math.sin(a), math.cos(a)
    kx = 1.0 - 0.18 * max(0.0, sa) ** 3
    ky = 1.0 + 0.10 * max(0.0, sa) ** 8 - 0.12 * max(0.0, -sa) ** 2
    erector = 0.12 * abs(math.sin(2 * a)) ** 6 if sa > 0 else 0.0   # either side of the dorsal line, symmetric
    return kx * (1 + erector), ky * (1 + erector * 0.5)


RIB_S = (0.16, 0.3, 0.44, 0.58, 0.72)   # where the ribs run along the chest


def rib_relief(a, s):
    """Ribs pressing out through the skin of the flanks: narrow ridges slanting back and down, strongest at
    mid-flank, fading towards the spine and the belly, with sunken intercostal grooves between them."""
    sa, ca = math.sin(a), abs(math.cos(a))
    flank = ca ** 1.5 * max(0.0, 1 - ((sa - 0.12) / 0.78) ** 2)
    if flank <= 0:
        return 0.0
    slant = 0.07 * (0.6 - sa)                  # the lower the point, the further back its rib
    k = 0.0
    for c in RIB_S:
        k += bump(s + slant, c, 0.045)
    return flank * (0.13 * k - 0.04)


def chest_shape(a, s):
    kx, ky = torso_shape(a, s)
    sa = math.sin(a)
    # pectoral and serratus masses low on the sides, a sunken belly line underneath
    lat = 0.06 * max(0.0, -sa) ** 0.5 * max(0.0, abs(math.cos(a))) ** 2 * bump(s, 0.45, 0.3)
    rib = rib_relief(a, s) * SP.get("rib_relief", 1.0)
    return kx * (1 + lat + rib), ky * (1 - 0.05 * max(0.0, -sa) ** 6 + 0.6 * rib)


def wedge_shape(a, s):
    # cuneiform head: narrow keel on top, broad jaw line underneath, cheek muscles at the hinge
    sa = math.sin(a)
    kx = 1.0 - 0.45 * max(0.0, sa) ** 1.5
    ky = 1.0 - 0.15 * max(0.0, -sa)
    cheek = 0.12 * max(0.0, abs(math.cos(a))) ** 4 * max(0.0, -sa + 0.3) * bump(s, 0.28, 0.18)
    return kx * (1 + cheek), ky


def flat_shape(a, s):
    return 1.0, 0.75


def muscle(*bellies, ridges=0.0, ridge_n=3, ridge_from=0.4, folds=0.0, folds_n=6):
    """Longitudinal muscle bellies (s centre, width, amplitude, angle of the bulge, how one-sided it is),
    tendon ridges near the far end, skin folds along the segment."""
    def f(a, s):
        k = 1.0
        for c, w, amp, ang, side in bellies:
            k += 1.5 * amp * bump(s, c, w) * (1 - side + side * max(0.0, math.cos(a - ang)) ** 2)
        if ridges:
            k += 1.3 * ridges * max(0.0, math.cos(ridge_n * a)) ** 8 * max(0.0, (s - ridge_from) / (1 - ridge_from))
        if folds:
            k += folds * max(0.0, math.sin(s * math.pi * folds_n)) ** 12
        return k, k
    return f


FRONT, BACK = math.pi / 2, 3 * math.pi / 2   # angles round a limb: the frame's up is anterior on the limbs
EYES = SP.get("eyes", {})

# --- torso: three overlapping segments forming one continuous mass
HIPS = [((0, 17.2, 20.6), 2.2, 2.0), ((0, 17.4, 18.6), 4.2, 3.7), ((0, 17.3, 15.0), 5.0, 4.2),
        ((0, 17.7, 11.0), 4.2, 3.7), ((0, 18.2, 7.5), 3.6, 3.3)]
SPINE = [((0, 18.1, 9.0), 3.8, 3.4), ((0, 18.8, 5.0), 3.3, 3.1), ((0, 19.7, 1.0), 4.6, 4.1), ((0, 20.4, -2.8), 6.0, 5.2)]
CHEST = [((0, 20.2, -1.8), 6.0, 5.2), ((0, 20.9, -5.5), 7.3, 6.1), ((0, 21.2, -9.3), 7.6, 6.0),
         ((0, 20.1, -12.4), 6.2, 5.1), ((0, 19.4, -14.2), 4.8, 4.3)]
NECK = [((0, 20.4, -12.0), 4.5, 4.3), ((0, 18.6, -15.4), 3.7, 3.6), ((0, 16.7, -18.3), 3.1, 3.2), ((0, 15.8, -19.8), 2.9, 3.1)]
HEAD = [((0, 16.0, -18.6), 3.0, 3.4), ((0, 15.8, -21.4), 3.15, 3.75), ((0, 14.4, -24.4), 2.55, 3.25),
        ((0, 12.9, -26.9), 1.65, 2.2), ((0, 12.2, -28.5), 0.75, 1.05)]
loft("hips", "skin", HIPS, sides=16, shape=torso_shape, cap1=False, sub=3)
loft("spine", "skin", SPINE, sides=16, shape=torso_shape, cap0=False, cap1=False, sub=3)
loft("chest", "skin", CHEST, sides=24, shape=chest_shape, cap0=False, sub=10)   # dense enough for the rib relief
# --- neck: thick and tense, ringed with folds of skin
loft("neck", "skin", NECK, sides=14, cap0=False, cap1=False, sub=4,
     shape=muscle((0.3, 0.3, 0.06, BACK, 0.7), folds=0.06, folds_n=5))
# --- head: a wedge hood, folds over where eyes should be; the mouth sits underneath
loft("head", "head", HEAD, sides=16, shape=wedge_shape, cap0=False, sub=3)
loft("head", "mouth", [((0, 12.0, -22.2), 1.5, 1.0), ((0, 11.6, -25.0), 1.25, 0.85), ((0, 11.5, -27.2), 0.6, 0.45)], sides=8)
JAW = [((0, 12.0, -21.6), 1.9, 0.95), ((0, 11.2, -24.8), 1.45, 0.8), ((0, 11.15, -27.4), 0.75, 0.5)]
loft("jaw", "skin", JAW, sides=10, sub=2)

# --- spine: a row of vertebral processes breaking the skin along the back, longest over the shoulders
for _part, _bone, _ss in ((HIPS, "hips", (0.15, 0.35, 0.55, 0.75, 0.92)), (SPINE, "spine", (0.2, 0.45, 0.7)),
                          (CHEST, "chest", (0.08, 0.3, 0.52, 0.74)), (NECK, "neck", (0.2, 0.5))):
    for _s in _ss:
        _shape = torso_shape if _part is not NECK else None
        _base = path_point(_part, _s, math.pi / 2, _shape, out=0.88)
        _top = path_point(_part, _s, math.pi / 2, _shape, out=1.0)
        _h = 0.7 + (0.7 if _part is CHEST and _s < 0.6 else 0.3 if _part is SPINE else 0.0) - (0.3 if _part is NECK else 0.0)
        _h *= 0.7 + 0.6 * float(rng.random())          # irregular: some broken short, some long
        _tip = _top + np.array([0.0, _h, 0.55 * _h])     # swept back
        spike(_bone, "bone", _base, _tip, 0.7 if _part is not NECK else 0.5)

# --- ribs: part of the chest skin (rib_relief in chest_shape)

# --- head detail: a heavy brow ridge, rows of sunken sockets where eyes would be, nostril slits, teeth
for sx in (1, -1):
    _brow = []
    for _s in np.linspace(0.12, 0.5, 5):
        a = math.radians(48) if sx > 0 else math.radians(132)
        _brow.append((path_point(HEAD, _s, a, wedge_shape, out=0.96), 0.55 - 0.25 * _s, 0.45 - 0.2 * _s))
    loft("head", "skin", _brow, sides=8, sub=2)
    _n_eyes = EYES.get("left", EYES.get("per_side", 3)) if sx > 0 else EYES.get("right", EYES.get("per_side", 3))
    for _k, (_s, _r) in enumerate(((0.33, 0.36), (0.22, 0.42), (0.43, 0.3))):
        a = math.radians(24) if sx > 0 else math.radians(156)
        c = path_point(HEAD, _s, a, wedge_shape, out=0.92)
        o = path_point(HEAD, _s, a, wedge_shape, out=1.1)
        # the orbit: a raised rim of bone, open to the outside (the eye shows inside it)
        loft("head", "pit", [(c, _r * 0.7, _r * 0.6), ((c + o) / 2, _r * 1.05, _r * 0.85), (o, _r * 1.1, _r * 0.9)],
             sides=10, cap1=False)
        if _k < _n_eyes:
            # a small eye sunk deep in the socket: its front stays well behind the rim
            n_ = o - c
            n_ = n_ / np.linalg.norm(n_)
            re = _r * 0.5 * EYES.get("size", 1.0)
            e0 = path_point(HEAD, _s, a, wedge_shape, out=1.0)
            loft("head", "eye", [(e0 - n_ * re * 0.7, re * 0.45, re * 0.45), (e0, re, re * 0.85),
                                 (e0 + n_ * re * 0.55, re * 0.55, re * 0.5)], sides=8, sub=2)
    a = math.radians(-10) if sx > 0 else math.radians(190)
    _n0 = path_point(HEAD, 0.86, a, wedge_shape, out=0.98)
    _n1 = path_point(HEAD, 0.95, a, wedge_shape, out=0.98)
    loft("head", "pit", [(_n0, 0.18, 0.1), (_n1, 0.12, 0.07)], sides=6)
    # teeth: the upper row hangs outside the jaw like an overbite; the lower row stands inside the jaw and
    # shows when the mouth opens
    for j, _z in enumerate(np.linspace(-22.0, -27.0, 6)):
        jw = float(np.interp(-_z, [-c[2] for c, _, _ in JAW], [rx for _, rx, _ in JAW]))
        x = sx * (jw + 0.12)
        spike("head", "tooth", (x * 0.9, 12.7, _z), (x, 10.9 + 0.25 * (j % 2), _z - 0.2), 0.26 - 0.025 * j, sides=5)
        if j < 5:
            xl = sx * jw * 0.62
            spike("jaw", "tooth", (xl, 12.2, _z - 0.4), (xl * 0.97, 13.4, _z - 0.55), 0.22 - 0.025 * j, sides=5)

for s, sx in (("l", 1), ("r", -1)):
    def X(x, sx=sx):
        return sx * x
    LAT = 0.0 if sx > 0 else math.pi   # lateral side of this limb
    # scapula: a plate pushing up under the skin of the shoulders, its spine a hard ridge
    loft(f"shoulder_{s}", "skin", [((X(5.0), 23.8, -11.2), 0.9, 0.5), ((X(5.8), 24.3, -8.6), 2.2, 0.85),
                                   ((X(5.2), 23.7, -5.6), 1.0, 0.5)], sides=12, sub=3)
    loft(f"shoulder_{s}", "bone", [((X(4.6), 24.4, -10.6), 0.3, 0.26), ((X(6.0), 25.05, -8.4), 0.38, 0.32),
                                   ((X(6.9), 24.8, -6.6), 0.26, 0.22)], sides=6, sub=2)
    # upper arm: it grows out of the chest (its first rings sit inside the ribcage and are carried by the
    # chest, handing over to the arm across the deltoid), deltoid cap, biceps in front, triceps behind
    loft(f"arm_{s}", "skin", [((X(2.6), 22.2, -8.6), 1.2, 1.5), ((X(4.4), 21.8, -8.7), 2.8, 3.4), ((X(6.5), 20.9, -9.0), 3.5, 3.9),
                              ((X(7.4), 17.8, -9.8), 2.9, 2.95), ((X(7.8), 15.0, -10.4), 2.6, 2.45),
                              ((X(8.4), 11.2, -11.4), 2.2, 2.2), ((X(8.5), 9.5, -12.3), 2.0, 2.1)], sides=12, sub=3,
         shape=muscle((0.34, 0.13, 0.12, LAT, 0.6), (0.62, 0.14, 0.10, FRONT, 0.8), (0.59, 0.17, 0.12, BACK, 0.8)),
         blend=("shoulder_" + s, 0.0, 0.62))
    # connective masses between the ribcage and the arm: pectoral in front, latissimus behind; each is
    # carried by the chest at one end and by the arm at the other, so it stretches like muscle
    loft(f"arm_{s}", "skin", [((X(1.2), 16.6, -11.6), 1.8, 0.8), ((X(4.4), 16.9, -11.0), 2.0, 1.0),
                              ((X(6.9), 16.8, -10.6), 1.6, 0.9)], sides=10, sub=2, blend=("chest", 0.0, 0.9))
    loft(f"arm_{s}", "skin", [((X(3.8), 19.0, -3.5), 1.6, 0.9), ((X(6.0), 18.6, -7.0), 1.7, 1.0),
                              ((X(7.6), 17.6, -8.6), 1.3, 0.9)], sides=10, sub=2, blend=("chest", 0.0, 0.9))
    # very long forearm, muscular below the elbow, tendons standing out towards the wrist
    loft(f"fore_{s}", "skin", [((X(8.35), 11.3, -10.9), 2.1, 2.35), ((X(8.6), 8.0, -14.0), 2.25, 2.0),
                               ((X(9.0), 4.0, -18.5), 1.5, 1.4), ((X(9.2), 1.9, -21.7), 1.3, 1.2)], sides=12, sub=3,
         shape=muscle((0.25, 0.2, 0.12, BACK, 0.5), (0.3, 0.18, 0.08, LAT, 0.7), ridges=0.10, ridge_n=3, ridge_from=0.45))
    # elbow: the point of the ulna, sharp under the skin
    spike(f"fore_{s}", "bone", (X(8.4), 11.0, -10.4), (X(8.6), 12.2, -7.9), 0.85, sides=7)
    # palm, flattened, with knuckles
    loft(f"hand_{s}", "skin", [((X(9.2), 2.1, -20.9), 1.6, 1.2), ((X(9.3), 1.45, -23.0), 2.2, 0.95),
                               ((X(9.4), 1.05, -24.6), 2.35, 0.8)], sides=12, shape=flat_shape, sub=2)
    # three long fingers and a shorter thumb, swollen joints, each ending in a dark curved nail
    for j, (dx, reach) in enumerate(((-1.45, 0.0), (0.0, 1.4), (1.45, 0.3))):
        x = X(9.4 + dx)
        tip = -29.3 - reach
        loft(f"fingers_{s}", "skin", [((x, 0.95, -24.3), 0.62, 0.56), ((x, 1.35, -26.6 - reach * 0.4), 0.5, 0.47),
                                      ((x, 0.7, tip + 0.9), 0.42, 0.4)], sides=8, cap0=False, sub=3,
             shape=muscle((0.05, 0.12, 0.25, FRONT, 0.3), (0.5, 0.1, 0.22, FRONT, 0.4)))
        loft(f"fingers_{s}", "nail", [((x, 0.62, tip + 1.1), 0.38, 0.36), ((x, 0.3, tip + 0.2), 0.25, 0.25),
                                      ((x, 0.02, tip - 0.6), 0.05, 0.05)], sides=7, sub=2)
    thumb_x = 9.4 - 2.1
    loft(f"hand_{s}", "skin", [((X(thumb_x + 0.6), 1.7, -22.0), 0.58, 0.52), ((X(thumb_x), 1.1, -23.8), 0.48, 0.45),
                               ((X(thumb_x - 0.4), 0.6, -24.9), 0.38, 0.36)], sides=8, cap0=False, sub=2)
    loft(f"hand_{s}", "nail", [((X(thumb_x - 0.45), 0.55, -25.0), 0.32, 0.3), ((X(thumb_x - 0.6), 0.1, -25.8), 0.04, 0.04)], sides=7)
    # thigh: it grows out of the pelvis (first rings inside the hips, carried by them, handing over to the
    # thigh across the hip joint); glutes behind, quadriceps in front, hamstrings behind
    loft(f"thigh_{s}", "skin", [((X(0.8), 18.3, 15.4), 1.2, 1.6), ((X(2.2), 18.0, 15.2), 2.6, 3.4), ((X(4.3), 17.3, 14.7), 3.8, 4.2),
                                ((X(5.1), 14.9, 13.2), 3.3, 3.5), ((X(5.4), 12.4, 11.7), 2.8, 2.9),
                                ((X(5.8), 9.0, 9.8), 2.3, 2.3), ((X(5.8), 8.0, 9.9), 2.1, 2.1)], sides=12, sub=3,
         shape=muscle((0.36, 0.12, 0.10, BACK, 0.6), (0.57, 0.17, 0.10, FRONT, 0.7), (0.54, 0.17, 0.12, BACK, 0.7),
                      (0.44, 0.14, 0.06, LAT, 0.6)),
         blend=("hips", 0.2, 0.5))
    # groin and gluteal masses bridging the pelvis and the thigh
    loft(f"thigh_{s}", "skin", [((X(1.8), 14.6, 14.0), 1.6, 0.8), ((X(3.6), 14.2, 13.4), 1.7, 0.9),
                                ((X(5.0), 13.6, 12.6), 1.3, 0.8)], sides=10, sub=2, blend=("hips", 0.0, 0.9))
    loft(f"thigh_{s}", "skin", [((X(2.0), 18.7, 17.4), 1.6, 0.9), ((X(4.0), 17.9, 16.4), 1.9, 1.1),
                                ((X(5.2), 15.8, 15.0), 1.4, 0.9)], sides=12, sub=3, blend=("hips", 0.0, 0.8))
    # knee cap
    loft(f"shin_{s}", "skin", [((X(5.8), 9.6, 8.6), 0.7, 0.6), ((X(5.8), 9.1, 7.9), 1.0, 0.9), ((X(5.8), 8.4, 7.7), 0.6, 0.5)], sides=8)
    # lower leg back to the raised hock: a lean calf, a hard tendon behind
    loft(f"shin_{s}", "skin", [((X(5.8), 9.0, 9.3), 2.2, 2.3), ((X(5.8), 7.4, 14.0), 1.7, 1.6),
                               ((X(5.8), 6.5, 18.6), 1.4, 1.5), ((X(5.8), 6.4, 19.5), 1.1, 1.2)], sides=12, sub=3,
         shape=muscle((0.25, 0.2, 0.12, math.pi / 2, 0.6), ridges=0.08, ridge_n=2, ridge_from=0.5))
    # the hock juts back into a bony spur
    spike(f"foot_{s}", "bone", (X(5.8), 6.6, 19.2), (X(5.8), 7.7, 21.7), 0.8, sides=7)
    # metatarsus down to the ball of the foot
    loft(f"foot_{s}", "skin", [((X(5.8), 6.6, 18.9), 1.35, 1.4), ((X(5.8), 3.6, 17.2), 1.0, 1.0),
                               ((X(5.8), 1.1, 15.6), 1.05, 0.9)], sides=10, sub=3,
         shape=muscle(ridges=0.08, ridge_n=2, ridge_from=0.0))
    for dx in (-0.95, 0.0, 0.95):
        x = X(5.8 + dx)
        loft(f"toes_{s}", "skin", [((x, 1.0, 15.5), 0.5, 0.44), ((x, 0.65, 13.6), 0.4, 0.36)], sides=8, cap0=False, cap1=False, sub=2,
             shape=muscle((0.4, 0.15, 0.2, FRONT, 0.4)))
        loft(f"toes_{s}", "nail", [((x, 0.62, 13.7), 0.34, 0.32), ((x, 0.25, 12.7), 0.2, 0.2), ((x, 0.02, 11.8), 0.04, 0.04)], sides=7)


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


# ============================================================================ moss
# Dark, damp moss grows on the creature after it is planted (it hangs from the final, morphed body):
# short clumps lying on the back, shoulders and neck; strands hanging from the flanks, the undersides and
# the upper limbs. Each strand gets its own bone ("moss_<n>", rooted on the body bone it grows from) that
# the game swings with gravity and the body's motion; the strand's root is skinned to the body.
MOSS = dict(strands=30, patches=14, length=(2.0, 6.0), width=0.55)
MOSS.update(SP.get("moss", {}))
DANGLE = {}   # moss bone -> rest direction (unit, Blockbench space)


def mesh_vertex_normals(m):
    out = []
    for ri, ring in enumerate(m.rings):
        c = np.mean([m.verts[k] for k in ring[:-1]], axis=0)
        for kk, k in enumerate(ring[:-1]):
            n = m.verts[k] - c
            nl = np.linalg.norm(n)
            out.append(((m, ri, kk), m.verts[k].copy(), n / nl if nl > 1e-6 else np.array([0.0, 1.0, 0.0]), m.params[k][1]))
    return out


def drape(where):
    """From a root vertex, walk round its ring towards the ground until the surface turns under: the
    path a strand lying on the skin would follow (offset just above the skin)."""
    m, ri, kk = where
    ring = m.rings[ri][:-1]
    n_ = len(ring)
    c = np.mean([m.verts[k] for k in ring], axis=0)

    def at(k):
        p = m.verts[ring[k]]
        nn = p - c
        nn = nn / max(1e-6, np.linalg.norm(nn))
        return p + nn * 0.3, nn
    pts = [at(kk)]
    cur = kk
    for _ in range(n_):
        nxt = min(((cur - 1) % n_, (cur + 1) % n_), key=lambda k: m.verts[ring[k]][1])
        if m.verts[ring[nxt]][1] >= m.verts[ring[cur]][1] - 1e-3:
            break
        cur = nxt
        pts.append(at(cur))
        if pts[-1][1][1] < 0.05:
            break
    return pts


_STRAND_BONES = ("hips", "spine", "chest", "neck", "shoulder", "arm", "fore", "thigh", "shin")
_PATCH_BONES = ("hips", "spine", "chest", "neck", "shoulder", "arm", "head")
_cand_strand, _cand_patch = [], []
for _m in list(MESHES):
    if _m.material != "skin" or getattr(_m, "rings", None) is None or len(_m.rings) < 3:
        continue
    _base = _m.bone.split("_")[0]
    for _w, _v, _n, _sv in mesh_vertex_normals(_m):
        if _base in ("fore", "shin") and _sv > 0.45:
            continue
        if _base == "head" and _sv > 0.3:
            continue
        torso = _base in ("hips", "spine", "chest", "neck", "shoulder")
        if _base in _STRAND_BONES and -0.65 < _n[1] < (0.92 if torso else 0.42) and _v[1] > 3.0:
            _cand_strand.append((_m.bone, _v, _n, _w))
        if _base in _PATCH_BONES and _n[1] > 0.5:
            _cand_patch.append((_m.bone, _v, _n, _w))


def _pick(cands, count, min_d):
    chosen = []
    order = rng.permutation(len(cands))
    for i in order:
        b, v, n, w = cands[i]
        if all(np.linalg.norm(v - c[1]) >= min_d for c in chosen):
            chosen.append((b, v, n, w))
        if len(chosen) >= count:
            break
    return chosen


_moss_n = 0
for _b, _v, _n, _w in _pick(_cand_strand, MOSS["strands"], 1.6 * SC ** 0.5):
    w = MOSS["width"] * float(rng.uniform(0.85, 1.45)) * SC ** 0.5
    if _n[1] > 0.1:
        # lies on the skin from the root down to where the body turns under (carried by the body bone)
        dp = drape(_w)
        if len(dp) >= 2:
            loft(_b, "moss", [(p, w * 0.75, w * 0.4) for p, _ in dp], sides=4, cap0=False, cap1=False)
        _v, _n = dp[-1][0], dp[-1][1]
    L = float(rng.uniform(*MOSS["length"])) * SC
    L = min(L, (_v[1] - 1.2) * 0.85)
    if L < 0.8:
        continue
    out_ = np.array([_n[0], 0.0, _n[2]])
    out_ = out_ / max(1e-6, np.linalg.norm(out_))
    down = np.array([0.0, -1.0, 0.0])
    j1, j2 = rng.normal(0, 0.12, 3), rng.normal(0, 0.18, 3)
    p0 = _v - _n * 0.2
    p1 = p0 + out_ * 0.45 + down * 0.25 * L
    p2 = p1 + out_ * 0.12 + down * 0.35 * L + j1 * L * 0.3
    p3 = p2 + down * 0.4 * L + j2 * L * 0.3
    name = f"moss_{_moss_n}"
    _moss_n += 1
    BONES[name] = (_b, p0.copy())
    ORDER.append(name)
    d = p3 - p0
    DANGLE[name] = d / np.linalg.norm(d)
    loft(name, "moss", [(p0, w, w), (p1, w * 0.9, w * 0.8), (p2, w * 0.6, w * 0.55), (p3, w * 0.12, w * 0.1)],
         sides=4, sub=1, cap0=False, cap1=False, blend=(_b, 0.0, 0.3))
for _b, _v, _n, _w in _pick(_cand_patch, MOSS["patches"], 1.4 * SC ** 0.5):
    # an irregular clump: one to three overlapping lobes lying on the skin
    for _lobe in range(int(rng.integers(1, 4))):
        t_ = np.cross(_n, rng.normal(0, 1, 3))
        t_ = t_ / max(1e-6, np.linalg.norm(t_))
        l_ = float(rng.uniform(1.2, 2.8)) * SC ** 0.6
        w = float(rng.uniform(0.7, 1.3)) * SC ** 0.6
        c_ = _v + _n * 0.12 + np.cross(_n, t_) * float(rng.uniform(-0.6, 0.6))
        loft(_b, "mossbed", [(c_ - t_ * l_ / 2, w * 0.4, 0.16), (c_, w, 0.36), (c_ + t_ * l_ / 2, w * 0.4, 0.16)], sides=5)


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


def streak_noise(rows, cols):
    """Noise stretched across the islands' u axis: wrinkles running round a limb, varying along it."""
    small = rng.random((rows, cols))
    return np.array(Image.fromarray((small * 255).astype(np.uint8)).resize((TEX, TEX), Image.BICUBIC)) / 255.0


n_wrinkle = streak_noise(TEX // 2, TEX // 40)
n_fiber = streak_noise(TEX // 48, TEX // 2)      # fibres running along v (down a strand)
glow = np.zeros((TEX, TEX, 4))
n_blot = smooth_noise(18)


def texel_grid(m, w, h):
    """3D position and outward normal of every texel of a loft's island (bilinear over its rings)."""
    G = np.array([[m.verts[k] for k in ring] for ring in m.rings])          # (n, sides + 1, 3)
    C = G[:, :-1].mean(axis=1)                                              # ring centres
    n, ns = G.shape[0], G.shape[1] - 1
    uu, vv = np.meshgrid(np.linspace(0, 1, w), np.linspace(0, 1, h))
    fi, fk = vv * (n - 1), uu * ns
    i0 = np.clip(np.floor(fi).astype(int), 0, max(0, n - 2))
    k0 = np.clip(np.floor(fk).astype(int), 0, ns - 1)
    a, b = (fi - i0)[..., None], (fk - k0)[..., None]
    i1 = np.minimum(i0 + 1, n - 1)
    P = (G[i0, k0] * (1 - a) * (1 - b) + G[i1, k0] * a * (1 - b) + G[i0, k0 + 1] * (1 - a) * b + G[i1, k0 + 1] * a * b)
    Cc = C[i0] * (1 - a) + C[i1] * a
    N = P - Cc
    N /= np.maximum(np.linalg.norm(N, axis=-1, keepdims=True), 1e-6)
    return P, N


def bake_ao():
    """Ambient occlusion of the rest pose, per texel: the creature's own mass darkens creases, armpits,
    the underside, between fingers and teeth (a voxelised solid, hemisphere probes up to 5 px)."""
    allv = np.array([v for m in MESHES for v in m.verts])
    res = 0.5
    lo = allv.min(0) - 7
    dims = (np.ceil((allv.max(0) + 7 - lo) / res) + 1).astype(int)
    occ = np.zeros(dims, bool)
    for m in MESHES:
        G = np.array([[m.verts[k] for k in ring] for ring in m.rings])
        if G.shape[0] < 2:
            continue
        # densify around (3x) and along (4x) the rings, and fill towards the centre: a solid, not a shell
        Ga = np.concatenate([G[:, :-1] * (1 - f) + G[:, 1:] * f for f in (0, 1 / 3, 2 / 3)], axis=1)
        C = G[:, :-1].mean(axis=1)
        for i in range(len(G) - 1):
            for f in (0.0, 0.25, 0.5, 0.75):
                ring = Ga[i] * (1 - f) + Ga[i + 1] * f
                cen = C[i] * (1 - f) + C[i + 1] * f
                for r in (0.0, 0.3, 0.55, 0.78, 0.92, 1.0):
                    q = np.floor((cen + (ring - cen) * r - lo) / res).astype(int)
                    occ[q[:, 0], q[:, 1], q[:, 2]] = True
    golden = math.pi * (3 - math.sqrt(5))
    K = 40
    D = np.array([(math.cos(golden * i) * math.sqrt(1 - (1 - 2 * (i + 0.5) / K) ** 2), 1 - 2 * (i + 0.5) / K,
                   math.sin(golden * i) * math.sqrt(1 - (1 - 2 * (i + 0.5) / K) ** 2)) for i in range(K)])
    dist = ((1.2, 1.0), (2.2, 0.8), (3.5, 0.6), (5.0, 0.45))
    out = {}
    for m in MESHES:
        x0, y0, w, h = m.island
        P, N = texel_grid(m, w, h)
        P, N = P.reshape(-1, 3), N.reshape(-1, 3)
        wd = np.clip(N @ D.T, 0, None)                                       # (T, K) cosine weights
        o = np.zeros_like(wd)
        start = P + N * 0.9
        for d, wt in dist:
            q = np.floor((start[:, None, :] + D[None, :, :] * d - lo) / res).astype(int)
            q = np.clip(q, 0, dims - 1)
            o += wt * occ[q[..., 0], q[..., 1], q[..., 2]]
        o /= sum(wt for _, wt in dist)
        ao = 1 - (wd * o).sum(1) / np.maximum(wd.sum(1), 1e-6)
        out[id(m)] = ao.reshape(h, w)
    return out


AO = bake_ao()
SKIN = np.array(SP["palette"]["skin"])        # species base colour (development texture)
FOLD = np.array(SP["palette"]["fold"])
for m in MESHES:
    x0, y0, w, h = m.island
    uu, vv = np.meshgrid(np.linspace(0, 1, w), np.linspace(0, 1, h))
    region = (slice(y0, y0 + h), slice(x0, x0 + w))
    big, mid, fine = n_big[region], n_mid[region], n_fine[region]
    around = np.sin(uu * 2 * math.pi)                       # +1 top, -1 underside
    if m.material in ("bone", "tooth"):
        # bone breaking through: skin at the base, pale and dry above, darker and worn at the tip
        base_col = np.array((0.62, 0.58, 0.51)) if m.material == "bone" else np.array((0.74, 0.70, 0.58))
        col = base_col * (0.82 + 0.3 * fine)[..., None]
        root = np.clip(1 - vv / 0.35, 0, 1)[..., None]
        gum = SKIN if m.material == "bone" else np.array((0.42, 0.17, 0.18))
        col = col * (1 - root) + gum * root
        col *= (1 - 0.25 * np.clip((vv - 0.75) * 4, 0, 1))[..., None]
    elif m.material == "eye":
        # deep red, darker at the rim; it also glows (emissive atlas below)
        col = np.array((0.42, 0.02, 0.02)) * (0.75 + 0.5 * np.clip(1 - np.abs(vv - 0.55) * 2.2, 0, 1))[..., None]
    elif m.material in ("moss", "mossbed"):
        # damp moss: desaturated green-grey fibres over near-black, ragged and stringy
        fib = n_fiber[region]
        dark = np.array((0.05, 0.06, 0.045))
        green = np.array(MOSS.get("green", (0.24, 0.29, 0.17)))
        grey = np.array((0.27, 0.28, 0.25))
        mixg = np.clip((fib - 0.22) * 2.4, 0, 1)[..., None]
        col = dark * (1 - mixg) + (green * (1 - 0.4 * big[..., None]) + grey * 0.4 * big[..., None]) * mixg
        col *= (0.8 + 0.4 * fine)[..., None]
    elif m.material == "pit":
        col = np.array((0.05, 0.025, 0.025)) * (0.8 + 0.5 * fine)[..., None]
    elif m.material == "nail":
        col = np.array((0.07, 0.06, 0.06)) * (0.9 + 0.3 * fine)[..., None]
        col += (vv < 0.25)[..., None] * 0.04
    elif m.material == "mouth":
        col = np.array((0.36, 0.13, 0.14)) * (0.8 + 0.4 * fine)[..., None] + 0.06 * (noise[region] > 0.93)[..., None]
    else:
        base = SKIN * (0.86 + 0.22 * big + 0.08 * mid)[..., None]
        crease = np.clip((mid - 0.55) * 2.2, 0, 1) * 0.7
        col = base * (1 - crease[..., None]) + FOLD * crease[..., None]
        # countershading: a darker back, a paler, faintly flushed belly
        top = np.clip(around, 0, 1)[..., None]
        belly = np.clip(-around, 0, 1)[..., None]
        col = col * (1 - 0.22 * top) * (1 + 0.10 * belly) + np.array((0.08, 0.02, 0.02)) * 0.4 * belly
        # wrinkles running round the segment, deepest near its ends (the joints)
        jw = np.clip(1 - np.minimum(vv, 1 - vv) / 0.3, 0, 1) * 0.7 + 0.3
        wr = np.clip((np.abs(n_wrinkle[region] - 0.5) - 0.38) * 8, 0, 1) * jw
        col *= (1 - 0.22 * wr)[..., None]
        # diseased-looking mottling and dark pores
        blot = np.clip((n_blot[region] - 0.62) * 4, 0, 1)
        col = col * (1 - 0.18 * blot[..., None]) + np.array((0.30, 0.20, 0.20)) * 0.12 * blot[..., None]
        col *= (1 - 0.35 * (noise[region] > 0.985))[..., None]
        # long tendons and stretched skin lines along limbs
        tend = ((np.abs(mid - 0.5) < 0.008) & (big > 0.5)).astype(float)    # faint tendon lines
        col *= (1 - 0.12 * tend)[..., None]
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
            col = col * 0.78 + np.array((0.20, 0.15, 0.15)) * 0.1  # calloused, darker extremities
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
    ao = AO[id(m)]
    col = col * (0.26 + 0.74 * np.clip(ao, 0, 1) ** 1.6)[..., None]       # baked ambient occlusion
    img[region] = np.clip(col, 0, 1)
    if m.material == "moss":
        # ragged, stringy strands: fibres thin out towards the tip (alpha-tested in game)
        a_ = n_fiber[region] > (0.3 + 0.45 * vv ** 1.5)
        a_ |= vv < 0.12
        alpha[region] = a_.astype(float)
    elif m.material == "mossbed":
        # ragged clumps: fibres fray at the ends of each lobe
        edge = np.clip(1 - np.minimum(vv, 1 - vv) / 0.3, 0, 1)
        alpha[region] = (n_fiber[region] > 0.15 + 0.55 * edge).astype(float)
    else:
        alpha[region] = 1
    if m.material == "eye":
        g_ = float(EYES.get("glow", 1.0))
        core = np.clip(1 - np.abs(vv - 0.6) * 1.6, 0.25, 1)
        glow[region] = np.dstack([np.clip(g_ * core * 1.0, 0, 1), np.clip(g_ * core * 0.06, 0, 1),
                                  np.clip(g_ * core * 0.03, 0, 1), np.ones_like(core)])
skin_rgb = Image.fromarray((img * 255).astype(np.uint8), "RGB").filter(ImageFilter.SMOOTH)
skin_png = Image.fromarray(np.dstack([np.asarray(skin_rgb), (alpha * 255).astype(np.uint8)]), "RGBA")
glow_png = Image.fromarray((glow * 255).astype(np.uint8), "RGBA")


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


def solve_contacts(pose, targets, flatten=True, lateral=False, peel=None):
    """targets: limb -> (dx, dy, dz) offsets of the contact from its rest position (body space).
    lateral: also solve x (the first joint gets an abduction degree of freedom), so a planted contact does not
    drift sideways when the body sways or turns. peel: limb -> degrees added to the flattened end segment
    (hand: the wrist rises off the ground; toes: they trail), solved with the rest of the limb."""
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
                p.setdefault(end, {})["rot"] = (-tot + (peel or {}).get(limb, 0.0), end_rot[1], end_rot[2])
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


def gait_offset(phase, stance, stride, lift, reach=0.0):
    """Contact offset along z (body moves -z): planted contact slides +z linearly; swing returns with lift.
    The lift peaks early (the limb clears the ground at once, then glides); reach carries it a little past
    the plant point before it settles onto it (zero at both ends of the swing, so the contacts are unchanged)."""
    if phase < stance:
        return 0.0, -stride / 2 + stride * phase / stance
    q = (phase - stance) / (1 - stance)
    e = q * q * (3 - 2 * q) + reach * math.sin(math.pi * q) * q * q
    return lift * math.sin(math.pi * q ** 0.75), stride / 2 - stride * e


def soft_stance(ph, stance, band=0.07):
    """How much weight a limb carries: builds after touch-down, fades before lift-off, 0 in the swing."""
    if ph >= stance:
        return 0.0
    a = min(1.0, ph / band)
    b = min(1.0, (stance - ph) / band)
    return (a * a * (3 - 2 * a)) * (b * b * (3 - 2 * b))


def chain_angles(pose):
    return sum((np.array(pose.get(b, {}).get("rot", (0, 0, 0)), float) for b in ("hips", "spine", "chest", "neck")),
               np.zeros(3))


ANIMS = []


def animation(name, length, loop, keys, **meta):
    def deco(fn):
        ANIMS.append(dict(name=name, length=length, loop=loop, keys=keys, fn=fn, **meta))
        return fn
    return deco


def S(x):
    return math.sin(x)


def locomotion(t, stance, stride, lifts, offsets, body, wall=False, centers=None, wall_spread=(24, 18),
               stab=0.0, reach=0.0, peel=0.0, sway=(0.0, 0.0), bounce=0.0):
    pose = {}
    p = TAU * t
    body(pose, t, p)
    if bounce:
        # the pelvis and the shoulders sink while their pair of limbs carries the weight and rise when it is off
        # the ground (the flight phases of the run)
        load = {k: sum(soft_stance((t + offsets[l]) % 1.0, stance) for l in ls)
                for k, ls in (("hips", ("leg_l", "leg_r")), ("chest", ("arm_l", "arm_r")))}
        add(pose, "hips", pos=(0, bounce * SC * (0.55 - min(1.0, load["hips"])), 0))
        add(pose, "chest", pos=(0, bounce * SC * (0.55 - min(1.0, load["chest"])), 0))
    # weight shift: the pelvis drifts and rolls over the side whose limbs carry the weight
    sup = {"l": 0.0, "r": 0.0}
    for limb, off in offsets.items():
        sup[limb[-1]] += soft_stance((t + off) % 1.0, stance)
    shift = (sup["l"] - sup["r"]) / 2
    if sway[0] or sway[1]:
        add(pose, "hips", rot=(0, 0, -sway[1] * shift), pos=(sway[0] * shift * SC, 0, 0))
    # the head holds its line: it cancels part of the oscillation the body passes up the spine
    if stab:
        def chain_at(tt):
            q_ = {}
            body(q_, tt, TAU * tt)
            return chain_angles(q_)
        mean = np.mean([chain_at(tt) for tt in np.linspace(0, 1, 16, endpoint=False)], axis=0)
        d = chain_angles(pose) - mean
        add(pose, "head", rot=(-stab * d[0], -stab * d[1], -0.7 * stab * d[2]))
    targets, peels = {}, {}
    for limb, off in offsets.items():
        ph = (t + off) % 1.0
        lift, dz = gait_offset(ph, stance, stride, lifts[limb], reach)
        targets[limb] = (0.0, lift, dz + (centers or {}).get(limb, 0.0))
        s = limb[-1]
        q = (ph - stance) / (1 - stance) if ph >= stance else -1.0
        if limb.startswith("arm"):
            if q >= 0:
                add(pose, f"fingers_{s}", rot=(-14 * math.sin(math.pi * q), 0, 0))
            elif peel:
                # the wrist rises before lift-off while the fingers stay on the ground
                k = min(1.0, max(0.0, (ph - 0.72 * stance) / (0.28 * stance)))
                k = k * k * (3 - 2 * k)
                peels[limb] = -peel * k
                add(pose, f"fingers_{s}", rot=(0.9 * peel * k, 0, 0))
        elif peel and 0 <= q < 0.7:
            peels[limb] = -0.8 * peel * math.sin(math.pi * q / 0.7)   # the toes trail after lift-off
    if wall:
        for s, sx in (("l", 1), ("r", -1)):
            add(pose, f"arm_{s}", rot=(0, 0, sx * wall_spread[0]))
            add(pose, f"thigh_{s}", rot=(0, 0, sx * wall_spread[1]))
    return solve_contacts(pose, targets, lateral=not wall, peel=peels)


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
    return locomotion(t, c["stance"], c["stride"], lifts_of(c), c["offsets"], body, centers=centers_of(c),
                      stab=1.0 if c.get("steady_head") else c["stab"], reach=c["reach"], peel=c["peel"], sway=c["sway"],
                      bounce=c.get("bounce", 0.0) * (1.0 + 0.6 * c.get("heavy", 0.0)))


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
    return locomotion(t, c["stance"], c["stride"], lifts_of(c), c["offsets"], body, centers=centers_of(c),
                      stab=1.0 if c.get("steady_head") else c["stab"], reach=c["reach"], peel=c["peel"], sway=c["sway"],
                      bounce=c.get("bounce", 0.0) * (1.0 + 0.6 * c.get("heavy", 0.0)))


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
    return locomotion(t, c["stance"], c["stride"], lifts_of(c), c["offsets"], body, wall=True, wall_spread=sp,
                      stab=c["stab"], reach=c["reach"], peel=c["peel"], sway=c["sway"])


CRAWL_TOP = 14.5   # px: the back must pass under a one-block ceiling (16 px), a little margin
_body_skin = [m for m in MESHES if m.material == "skin" and m.bone in ("hips", "spine", "chest")]
_top = max(float(v[1]) for m in _body_skin for v in m.verts)
_bottom = min(float(v[1]) for m in _body_skin if m.bone == "hips" for v in m.verts)
CRAWL_DROP = max(0.0, min(_top - CRAWL_TOP, _bottom - 0.4))   # never through the floor


@animation("crawl", cfg("crawl")["length"], "loop", 33, stride=cfg("crawl")["stride"], stance=cfg("crawl")["stance"],
           offsets=cfg("crawl")["offsets"])
def crawl(t):
    c = cfg("crawl")
    w = c["weave"]

    def body(pose, t, p):
        add(pose, "hips", pos=(0, -CRAWL_DROP, 0), rot=(0, 6 * w * S(p), 2.5 * S(p)))
        add(pose, "spine", rot=(-2, -4 * w * S(p + 0.5), 0))
        add(pose, "chest", rot=(-3, -7 * w * S(p + 1.0), -2.5 * S(p)))
        add(pose, "neck", rot=(c["neck"], 4 * w * S(p + 1.6), 0))
        add(pose, "head", rot=(c["head"], -3 * w * S(p + 2.0), 0))
    return locomotion(t, c["stance"], c["stride"], lifts_of(c), c["offsets"], body, wall=True, wall_spread=c["spread"],
                      stab=0.35, reach=0.05, peel=8.0)


# v8 postures (2026-11-05): lower gaits to pass under a 3-block ceiling (stoop) and a 2-block one (crouch); the
# one-block gap stays the crawl. The body drops just enough for the back and the head (neck bowed, face kept
# forward) to clear the ceiling with a margin; a creature that already fits keeps its walk height. The server
# picks the posture from the clearance it meets (AdherentEntity.updatePosture), the client the clip.
STOOP_TOP, CROUCH_TOP = 45.0, 29.5   # px: under 48 and 32 with a margin
POSTURES = {"stoop": dict(top=STOOP_TOP, neck=-14.0, head=10.0, stride=0.85, length=1.12, lift=0.85),
            "crouch": dict(top=CROUCH_TOP, neck=-26.0, head=19.0, stride=0.7, length=1.25, lift=0.7)}
# everything on the trunk counts (spines and spurs included), not the limbs nor the hanging moss
_TRUNK = [m for m in MESHES if not m.bone.startswith(("arm", "fore", "hand", "fingers", "thigh", "shin", "foot", "toes", "moss"))]


def _posture_top(drop, neck, head):
    pose = {}
    add(pose, "hips", pos=(0, -drop, 0))
    add(pose, "neck", rot=(neck, 0, 0))
    add(pose, "head", rot=(head, 0, 0))
    G = globals_(pose)
    return max(point(G, m.bone, v)[1] for m in _TRUNK for v in m.verts[::3])


def _posture_drop(p):
    top0 = _posture_top(0.0, p["neck"], p["head"])
    return max(0.0, min(top0 - p["top"], _bottom - 0.4))


POSTURE_DROP = {k: _posture_drop(v) for k, v in POSTURES.items()}


def _low_walk(t, name):
    c = cfg("walk")
    p = POSTURES[name]
    drop = POSTURE_DROP[name]

    def body(pose, t, ph):
        a = c["amp"] * 0.7
        add(pose, "hips", rot=(0, 2.0 * a * S(ph), 1.5 * a * S(ph)), pos=(0, -drop - 0.25 * a * abs(S(ph * 2)) * SC, 0))
        add(pose, "chest", rot=(1.0 * a * S(ph * 2 + 1.0), -2.5 * a * S(ph), -2.0 * a * S(ph)),
            pos=(0, -0.3 * a * max(0, S(ph * 2 + 0.6)) * SC, 0))
        add(pose, "neck", rot=(p["neck"] - 1.0 * a * S(ph * 2 + 1.4), 1.5 * a * S(ph), 0))
        add(pose, "head", rot=(p["head"], -1.2 * a * S(ph), 0))
    lifts = {k: v * p["lift"] for k, v in lifts_of(c).items()}
    return locomotion(t, c["stance"], c["stride"] * p["stride"], lifts, c["offsets"], body, centers=centers_of(c),
                      stab=c["stab"], reach=c["reach"] * 0.5, peel=c["peel"], sway=c["sway"], bounce=0.0)


@animation("stoop", cfg("walk")["length"] * POSTURES["stoop"]["length"], "loop", 41,
           stride=cfg("walk")["stride"] * POSTURES["stoop"]["stride"], stance=cfg("walk")["stance"], offsets=cfg("walk")["offsets"])
def stoop(t):
    return _low_walk(t, "stoop")


@animation("crouch", cfg("walk")["length"] * POSTURES["crouch"]["length"], "loop", 41,
           stride=cfg("walk")["stride"] * POSTURES["crouch"]["stride"], stance=cfg("walk")["stance"], offsets=cfg("walk")["offsets"])
def crouch(t):
    return _low_walk(t, "crouch")


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


def _smooth01(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


@animation("rear", 1.6, "hold", 33)
def rear(t):
    # seconds: a short crouch gathers the body (hands still planted), the chest rises over the anchored hind feet
    # with a slight overshoot and settles; the arms lift late, claws opening; the head keeps its eyes forward
    c = cfg("rear")
    q = c["hips"] / 71.0
    crouch = math.sin(math.pi * t / 0.3) if t < 0.3 else 0.0
    k = _smooth01((t - 0.14) / 0.66)
    k += 0.06 * math.sin(math.pi * min(1.0, max(0.0, (t - 0.62) / 0.7)))      # overshoot, then settle
    ka = _smooth01((t - 0.24) / 0.62)                                         # the arms follow late
    pose = {}
    add(pose, "hips", rot=(c["hips"] * k - 4 * crouch, 0, 0), pos=(0, (1.5 * k - 1.3 * crouch) * SC, 5.0 * k * q * SC))
    add(pose, "spine", rot=(9 * k * q + 5 * crouch, 0, 0))
    add(pose, "chest", rot=(3 * k + 3 * crouch, 0, 0), pos=(0, -1.2 * crouch * SC, 0))
    add(pose, "neck", rot=(-22 * k * q + 7 * crouch, 0, 0))
    add(pose, "head", rot=(-26 * k * q - 9 * crouch, 0, 0))
    free = {b: dict(v) for b, v in pose.items()}
    for s, sx in (("l", 1), ("r", -1)):
        add(free, f"arm_{s}", rot=(-66 * ka * q, 0, sx * 7 * ka))
        add(free, f"fore_{s}", rot=(-14 * ka, 0, 0))
        add(free, f"hand_{s}", rot=(-30 * ka, 0, 0))
        add(free, f"fingers_{s}", rot=(-25 * ka - 32 * ka * (1 - ka), 0, 0))   # the claws flare as the arms rise
    legs = {"leg_l": (0, 0, 0), "leg_r": (0, 0, 0)}
    free = solve_contacts(free, legs)
    if ka >= 1.0:
        return free
    planted = solve_contacts({b: dict(v) for b, v in pose.items()}, dict(legs, arm_l=(0, 0, 0), arm_r=(0, 0, 0)))
    for s in "lr":   # hand over from the planted arms to the lifted ones
        for b in (f"shoulder_{s}", f"arm_{s}", f"fore_{s}", f"hand_{s}", f"fingers_{s}"):
            a_ = np.array(planted.get(b, {}).get("rot", (0, 0, 0)), float)
            f_ = np.array(free.get(b, {}).get("rot", (0, 0, 0)), float)
            free.setdefault(b, {})["rot"] = tuple(a_ + (f_ - a_) * ka)
    return free


@animation("threat", 1.8, "hold", 37)
def threat(t):
    # seconds: the head draws back (anticipation), then thrusts forward and down while the jaw opens late and
    # wide; a fine tension tremor runs through the neck once it is set
    c = cfg("threat")
    back = math.sin(math.pi * t / 0.4) if t < 0.4 else 0.0
    k = _smooth01((t - 0.2) / 0.9)
    kj = _smooth01((t - 0.35) / 1.0)
    trem = 0.7 * k * math.sin(TAU * 3.5 * t) * (0.6 + 0.4 * math.sin(TAU * 0.9 * t))
    pose = {}
    add(pose, "neck", rot=(8 * k - 9 * back + trem, 0.6 * trem, 0))
    add(pose, "head", rot=(10 * k - 7 * back - 0.6 * trem, 0, 0.5 * trem))
    add(pose, "jaw", rot=(c["jaw"] * kj + 2.5 * back, 0, 0))
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
# INTERSTICE_ONLY=stoop,crouch: compute only these clips and merge them into the existing .bbmodel (minutes
# instead of the full regeneration); everything else in the file is kept as it is.
ONLY = [s for s in os.environ.get("INTERSTICE_ONLY", "").split(",") if s]
for a in ANIMS:
    if ONLY and a["name"] not in ONLY:
        continue
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
    if a["name"] in ("walk", "run", "climb", "crawl", "stoop", "crouch", "idle", "cling", "rear", "observe", "search"):
        loop = a["loop"] == "loop"
        worst_y = worst_slip = worst_x = 0.0
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
                        worst_x = max(worst_x, abs(w[0] - GOAL[limb][0]))
                        wl[limb] = max(wl.get(limb, 0.0), abs(w[1] - GOAL[limb][1]))
                        if limb in prev and prev[limb][0] < a["stance"] * 0.97 and ph > prev[limb][0]:
                            expected = a["stride"] / a["stance"] * (a["length"] / steps) / a["length"]
                            worst_slip = max(worst_slip, abs((w[2] - prev[limb][1]) - expected))
                        prev[limb] = (ph, w[2])
                    else:
                        prev.pop(limb, None)
                else:
                    worst_y = max(worst_y, abs(w[1] - GOAL[limb][1]))
                    worst_x = max(worst_x, abs(w[0] - GOAL[limb][0]))
                    wl[limb] = max(wl.get(limb, 0.0), abs(w[1] - GOAL[limb][1]))
                    if limb in prev:
                        worst_slip = max(worst_slip, abs(w[2] - prev[limb]))
                    prev[limb] = w[2]
        report.append(f"{a['name']:8s} contact height error <= {worst_y:.3f} px, sideways <= {worst_x:.3f} px, "
                      f"slip per 1/240 s <= {worst_slip:.4f} px "
                      f"(interpolated, sampled at 240 Hz) [" + " ".join(f"{k} {v:.2f}" for k, v in wl.items()) + "]")

# ============================================================================ write the .bbmodel
def r(x):
    return round(float(x), 4)


group_uuid = {b: str(uuid.uuid4()) for b in ORDER}
SKIN_EXTRA = {}
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
    if getattr(m, "blend", None):
        ob, s0, s1 = m.blend
        ws_ = []
        for k in range(len(m.verts)):
            q = min(1.0, max(0.0, (m.params[k][1] - s0) / max(1e-6, s1 - s0)))
            ws_.append(round(1.0 - q * q * (3 - 2 * q), 4))
        SKIN_EXTRA[uid] = {"bone": ob, "w": ws_}
    elements.append({"name": f"{m.bone}_{m.material}_{i}", "color": i % 8, "origin": [r(c) for c in piv], "rotation": [0, 0, 0],
                     "export": True, "visibility": True, "locked": False, "render_order": "default",
                     "allow_mirror_modeling": True, "vertices": verts, "faces": faces, "type": "mesh", "uuid": uid})
    children[m.bone].append(uid)


def group(b):
    g = {"name": b, "origin": [r(c) for c in BONES[b][1]], "rotation": [0, 0, 0], "color": 0, "uuid": group_uuid[b],
         "export": True, "mirror_uv": False, "isOpen": False, "locked": False, "visibility": True, "autouv": 0,
         "children": children[b] + [group(c) for c in ORDER if BONES[c][0] == b]}
    if b in DANGLE:   # a moss strand: the game swings it with gravity and the body's motion
        g["interstice"] = {"dangle": [r(c) for c in DANGLE[b]]}
    return g


outliner = [group(b) for b in ORDER if BONES[b][0] is None]
if ONLY:
    # merge mode: animators are keyed by the bone groups of the file being updated
    with open(OUT, encoding="utf-8") as fh:
        _existing = json.load(fh)

    def _uuids(g):
        group_uuid[g["name"]] = g["uuid"]
        for ch in g.get("children", []):
            if isinstance(ch, dict):
                _uuids(ch)
    for _g in _existing["outliner"]:
        _uuids(_g)

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

if ONLY:
    _existing["animations"] = [x for x in _existing["animations"] if x["name"] not in ONLY] + animations
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(_existing, fh, indent=1)
    _val = os.path.join(ROOT, "models", f"{NAME}_validation.txt")
    _old = open(_val, encoding="utf-8").read().splitlines() if os.path.exists(_val) else []
    _old = [ln for ln in _old if not any(ln.startswith(n + " ") or ln.startswith(n + ":") for n in ONLY)]
    for _n in ONLY:
        if _n in POSTURE_DROP:
            _p = POSTURES[_n]
            report.append(f"{_n}: body dropped {POSTURE_DROP[_n] / 16:.2f} blocks, back and head top "
                          f"{_posture_top(POSTURE_DROP[_n], _p['neck'], _p['head']) / 16:.2f} blocks (ceiling {(_p['top'] + 3) / 16:.0f})")
    with open(_val, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(_old + report) + "\n")
    print("\n".join(report))
    sys.exit(0)

buf = io.BytesIO()
skin_png.save(buf, "PNG")
gbuf = io.BytesIO()
glow_png.save(gbuf, "PNG")
model = {
    "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
    "name": NAME, "model_identifier": NAME, "visible_box": [3, 4, 0],
    "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
    "resolution": {"width": TEX, "height": TEX},
    "elements": elements, "outliner": outliner,
    "textures": [{"path": "", "name": f"{NAME}.png", "folder": "", "namespace": "", "id": "0", "width": TEX, "height": TEX,
                  "uv_width": TEX, "uv_height": TEX, "particle": False, "render_mode": "default", "visible": True,
                  "mode": "bitmap", "saved": True, "uuid": str(uuid.uuid4()),
                  "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()},
                 {"path": "", "name": f"{NAME}_eyes.png", "folder": "", "namespace": "", "id": "1", "width": TEX, "height": TEX,
                  "uv_width": TEX, "uv_height": TEX, "particle": False, "render_mode": "emissive", "visible": True,
                  "mode": "bitmap", "saved": True, "uuid": str(uuid.uuid4()),
                  "source": "data:image/png;base64," + base64.b64encode(gbuf.getvalue()).decode()}],
    "animations": animations,
    # per-vertex skin hand-over (limbs growing out of the torso, connective masses, moss roots); the exporter
    # mixes it with its own joint blending. Blockbench drops unknown fields: re-run the generator after edits.
    "interstice_skin": SKIN_EXTRA,
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
_crawl_tops = []
for _ph in (0.0, 0.25, 0.5, 0.75):
    _Gc = globals_(crawl(_ph))
    _crawl_tops.append(max(point(_Gc, m.bone, v)[1] for m in MESHES if m.material in ("skin", "head")
                           and not m.bone.startswith(("arm", "fore", "hand", "fingers", "thigh", "shin", "foot", "toes", "moss"))
                           for v in m.verts[::2]) / 16)
lines.append(f"crawl: body dropped {CRAWL_DROP / 16:.2f} blocks, back and head top at most {max(_crawl_tops):.2f} blocks "
             f"(limbs splayed outside)")
# deformation of the skin where limbs join the torso, with the exporter's weights (skinning.py: plane hand-over
# along the main chain, radial at side branches, plus the gradient hand-over above); linear blend here, the game
# uses dual quaternions on LOD0 (tools/creature/joint_report.py compares both on the written file)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from skinning import Rig as _Rig, mix_weights as _mix  # noqa: E402
_RIG = _Rig(ORDER, [ORDER.index(BONES[b][0]) if BONES[b][0] else None for b in ORDER], [BONES[b][1] for b in ORDER],
            {ORDER.index(b) for b in DANGLE})


def _skin_weights(m):
    bi = ORDER.index(m.bone)
    out = []
    for k, v in enumerate(m.verts):
        js, ws = _RIG.weights(np.array(v, float), bi, "legacy" if bi in _RIG.hanging else "plane")
        if getattr(m, "blend", None):
            ob, s0, s1 = m.blend
            q = min(1.0, max(0.0, (m.params[k][1] - s0) / max(1e-6, s1 - s0)))
            js, ws = _mix(js, ws, ORDER.index(ob), 1.0 - q * q * (3 - 2 * q))
        acc = {}
        for j, w in zip(js, ws):
            acc[ORDER[j]] = acc.get(ORDER[j], 0.0) + w
        out.append(acc)
    return out


_JUNCTION = [m for m in MESHES if m.material == "skin" and m.bone.split("_")[0] in ("hips", "chest", "arm", "thigh")]
_JW = [_skin_weights(m) for m in _JUNCTION]
_EDGES = []
for m in _JUNCTION:
    es = set()
    for f in m.faces:
        for i in range(len(f)):
            a_, b_ = f[i], f[(i + 1) % len(f)]
            es.add((min(a_, b_), max(a_, b_)))
    _EDGES.append(sorted(es))
deform_lines = []
for a, times, channels in BAKED:
    if a["name"] not in ("walk", "run", "climb", "rear", "turn"):
        continue
    ratios, per_mesh = [], {}
    for tm in np.linspace(0, a["length"], 13):
        pose = {}
        for (b, ch), vals in channels.items():
            pose.setdefault(b, {})[ch] = tuple(sample_channel(times, vals, tm, a["loop"] == "loop", a["length"]))
        G = globals_(pose)
        for mi, (m, W, E) in enumerate(zip(_JUNCTION, _JW, _EDGES)):
            P = [sum(w * point(G, b, v) for b, w in W[k].items()) for k, v in enumerate(m.verts)]
            for i_, j_ in E:
                l0 = np.linalg.norm(m.verts[i_] - m.verts[j_])
                if l0 < 0.05:
                    continue
                r_ = np.linalg.norm(P[i_] - P[j_]) / l0
                ratios.append(r_)
                lo, hi = per_mesh.get(mi, (9.0, 0.0))
                per_mesh[mi] = (min(lo, r_), max(hi, r_))
    ratios = np.array(ratios)
    worst = sorted(per_mesh.items(), key=lambda kv: -max(kv[1][1], 1 / max(kv[1][0], 1e-3)))[:2]
    deform_lines.append(f"{a['name']:8s} junction skin (hips, chest, arms, thighs), edge length / rest over 13 poses: "
                        f"1-99 % {np.percentile(ratios, 1):.2f}-{np.percentile(ratios, 99):.2f}, extremes {ratios.min():.2f}-{ratios.max():.2f} "
                        f"(worst: " + ", ".join(f"{_JUNCTION[mi].bone}#{mi} {lo:.2f}-{hi:.2f}" for mi, (lo, hi) in worst) + ")")
lines.append("rest contact heights before IK (px): " + ", ".join(f"{k} {v:.2f}" for k, v in REST_CONTACT_HEIGHT.items()))
lines += report
lines += deform_lines
lines.append(f"moss: {sum(1 for b in DANGLE)} hanging strands (own bones), "
             f"{sum(1 for m in MESHES if m.material == 'mossbed')} clumps; eyes: {sum(1 for m in MESHES if m.material == 'eye')}")
with open(REPORT, "w", encoding="utf-8") as fh:
    fh.write("\n".join(lines) + "\n")
print("\n".join(lines))
