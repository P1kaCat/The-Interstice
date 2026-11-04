"""
Writes models/adherent.bbmodel (Blockbench "Modded Entity" format, box UV) from the
Adherent's original hand-written geometry, with the current skin embedded.

This was the one-time bridge from code to Blockbench. After seeding, the .bbmodel is the
source of truth: edit it in Blockbench (or through the Blockbench MCP), then run
bbmodel_to_java.py to regenerate AdherentGeometry.java.

Usage: python -I tools/bbmodel/seed_adherent_bbmodel.py [--force]
"""
import base64
import json
import math
import os
import sys
import uuid

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "models", "adherent.bbmodel")
SKIN = os.path.join(ROOT, "src", "client", "resources", "assets", "interstice", "textures", "entity", "adherent.png")

# Original geometry, transcribed 1:1 from AdherentModel.createLayer (Mojang model space:
# Y down, offsets relative to the parent part, radians).
# part: (name, parent, offset xyz, rotation xyz, cubes[(u, v, x, y, z, w, h, d, inflate, mirror)])
PARTS = [
    ("body", None, (0, 9, 1), (0, 0, 0), [(0, 0, -3.5, -3, -9, 7, 6, 17, 0, False), (0, 23, -1, -5, -8, 2, 2, 15, 0, False)]),
    ("neck", "body", (0, -1, -9), (0.5, 0, 0), [(34, 25, -1.5, -1.5, -7, 3, 3, 7, 0, False)]),
    ("head", "neck", (0, 0, -7), (0, 0, 0), [(0, 40, -2.5, -2.5, -5, 5, 5, 5, 0, False), (20, 40, -0.5, -2.2, -5.15, 1, 4, 0, 0.01, False)]),
    ("jaw", "head", (0, 2, -1), (0, 0, 0), [(0, 50, -2, 0, -6, 4, 2, 7, 0, False)]),
    ("chest_l", "body", (2.5, 2.5, -6), (-1.2, 0, 0.2), [(48, 0, -0.5, 0, -0.5, 1, 6, 1, 0, False)]),
    ("chest_r", "body", (-2.5, 2.5, -6), (-1.2, 0, -0.2), [(48, 0, -0.5, 0, -0.5, 1, 6, 1, 0, True)]),
]
for s, suf in ((1, "l"), (-1, "r")):
    PARTS += [
        (f"arm_{suf}", "body", (s * 3.5, 0, -7), (0, 0, 0), [(48, 8, -1, -1, -1, 2, 15, 2, 0, False)]),
        (f"fore_{suf}", f"arm_{suf}", (0, 14, 0), (0, 0, 0), [(56, 8, -0.75, 0, -0.75, 1.5, 16, 1.5, 0, False)]),
        (f"hand_{suf}", f"fore_{suf}", (0, 16, 0), (-0.5, 0, 0), [
            (40, 40, -1.1, 0, -0.3, 0.6, 5, 0.6, 0, False),
            (40, 40, -0.3, 0, -0.3, 0.6, 6, 0.6, 0, False),
            (40, 40, 0.5, 0, -0.3, 0.6, 5, 0.6, 0, False)]),
        (f"leg_{suf}", "body", (s * 3, 1, 7), (0, 0, 0), [(30, 50, -1.25, -1, -1.25, 2.5, 9, 2.5, 0, False)]),
        (f"shin_{suf}", f"leg_{suf}", (0, 8.5, 0), (0, 0, 0), [(42, 50, -0.9, 0, -0.9, 1.8, 10, 1.8, 0, False)]),
    ]

TEX_W = TEX_H = 64


def box_faces(u, v, w, h, d):
    """Blockbench box-UV face rectangles for a cube unwrapped at (u, v)."""
    return {
        "north": {"uv": [u + d, v + d, u + d + w, v + d + h], "texture": 0},
        "east": {"uv": [u, v + d, u + d, v + d + h], "texture": 0},
        "south": {"uv": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h], "texture": 0},
        "west": {"uv": [u + d + w, v + d, u + 2 * d + w, v + d + h], "texture": 0},
        "up": {"uv": [u + d + w, v + d, u + d, v], "texture": 0},
        "down": {"uv": [u + d + w, v, u + d + 2 * w, v + d], "texture": 0},
    }


def r(x):
    return round(x, 4) + 0.0


def build():
    # absolute Java-space pivots (unrotated rest pose), parents first
    abs_pivot = {}
    elements, groups = [], {}
    for name, parent, off, rot, cubes in PARTS:
        base = abs_pivot[parent] if parent else (0.0, 0.0, 0.0)
        p = (base[0] + off[0], base[1] + off[1], base[2] + off[2])
        abs_pivot[name] = p
        # Java model space -> Blockbench space: x mirrored, y flipped around 24 px (ground), z unchanged
        origin = [r(-p[0]), r(24 - p[1]), r(p[2])]
        group = {
            "name": name, "origin": origin,
            "rotation": [r(-math.degrees(rot[0])), r(-math.degrees(rot[1])), r(math.degrees(rot[2]))],
            "color": 0, "uuid": str(uuid.uuid4()), "export": True, "mirror_uv": False,
            "isOpen": True, "locked": False, "visibility": True, "autouv": 0, "children": [],
        }
        for i, (u, v, x, y, z, w, h, d, inflate, mirror) in enumerate(cubes):
            cid = str(uuid.uuid4())
            elements.append({
                "name": name if len(cubes) == 1 else f"{name}_{i}", "box_uv": True, "rescale": False,
                "locked": False, "render_order": "default", "allow_mirror_modeling": True,
                "from": [r(-(p[0] + x + w)), r(24 - (p[1] + y + h)), r(p[2] + z)],
                "to": [r(-(p[0] + x)), r(24 - (p[1] + y)), r(p[2] + z + d)],
                "autouv": 0, "color": 0, "inflate": inflate, "origin": origin, "rotation": [0, 0, 0],
                "uv_offset": [u, v], "mirror_uv": mirror, "faces": box_faces(u, v, w, h, d),
                "type": "cube", "uuid": cid,
            })
            group["children"].append(cid)
        groups[name] = (group, parent)
    outliner = []
    for name, (group, parent) in groups.items():
        (groups[parent][0]["children"] if parent else outliner).append(group)
    textures = []
    if os.path.exists(SKIN):
        with open(SKIN, "rb") as f:
            data = base64.b64encode(f.read()).decode()
        textures.append({
            "path": "", "name": "adherent.png", "folder": "", "namespace": "", "id": "0",
            "width": TEX_W, "height": TEX_H, "uv_width": TEX_W, "uv_height": TEX_H, "particle": False,
            "render_mode": "default", "visible": True, "mode": "bitmap", "saved": True,
            "uuid": str(uuid.uuid4()), "source": "data:image/png;base64," + data,
        })
    return {
        "meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
        "name": "adherent", "model_identifier": "adherent", "modded_entity_version": "fabric",
        "modded_entity_flip_y": True, "visible_box": [1, 2, 0], "variable_placeholders": "",
        "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
        "resolution": {"width": TEX_W, "height": TEX_H},
        "elements": elements, "outliner": outliner, "textures": textures,
    }


if __name__ == "__main__":
    if os.path.exists(OUT) and "--force" not in sys.argv:
        sys.exit(f"{OUT} already exists (it is the source of truth now). Pass --force to overwrite.")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(build(), f, indent=1)
    print("wrote", OUT)
