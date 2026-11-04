"""
Converts models/adherent.bbmodel (Blockbench, Modded Entity format, box UV) into
AdherentGeometry.java using Mojang mappings (the mod's mappings; Blockbench's own Java
export targets Yarn names, which do not compile here).

Also, with --texture, writes the .bbmodel's embedded skin to textures/entity/adherent.png.

Rules (checked, with explicit errors):
- Bones are Blockbench groups. The animator needs these bones, under these parents:
  hips > spine > chest > neck > head > jaw_l, jaw_r;
  chest > arm_x > elbow_x > fore_x > hand_x;  hips > leg_x > shin_x > foot_x   (x = l, r).
  Extra bones and cubes are allowed anywhere.
- Cubes may not be rotated themselves (Java entity models cannot): rotate their group instead.
- Box UV only.

Usage: python -I tools/bbmodel/bbmodel_to_java.py [--texture] [--check]
  --check  only validate and print the geometry; write nothing.
"""
import base64
import json
import math
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "models", "adherent.bbmodel")
OUT = os.path.join(ROOT, "src", "client", "java", "com", "interstice", "client", "render", "creatures", "AdherentGeometry.java")
SKIN = os.path.join(ROOT, "src", "client", "resources", "assets", "interstice", "textures", "entity", "adherent.png")

REQUIRED = {
    "hips": None, "spine": "hips", "chest": "spine", "neck": "chest", "head": "neck",
    "jaw_l": "head", "jaw_r": "head",
    "arm_l": "chest", "elbow_l": "arm_l", "fore_l": "elbow_l", "hand_l": "fore_l",
    "arm_r": "chest", "elbow_r": "arm_r", "fore_r": "elbow_r", "hand_r": "fore_r",
    "leg_l": "hips", "shin_l": "leg_l", "foot_l": "shin_l",
    "leg_r": "hips", "shin_r": "leg_r", "foot_r": "shin_r",
}
ANIM_OUT = os.path.join(ROOT, "src", "client", "java", "com", "interstice", "client", "render", "creatures", "AdherentAnimations.java")


class ConvertError(Exception):
    pass


def f(x):
    """Java float literal without noise."""
    x = round(float(x), 4) + 0.0
    s = repr(x)
    if s.endswith(".0"):
        s = s[:-2]
    return s + "f"


def walk(model):
    elements = {e["uuid"]: e for e in model.get("elements", [])}
    parts = []  # (name, parentName, group, cubes)

    def visit(node, parent_name, parent_origin):
        if isinstance(node, str):
            raise ConvertError(f"cube '{elements.get(node, {}).get('name', node)}' sits outside any bone; put it in a group")
        name = node["name"]
        if not re.fullmatch(r"[a-z][a-z0-9_]*", name):
            raise ConvertError(f"bone name '{name}' must be lowercase letters, digits and underscores")
        cubes = []
        for child in node.get("children", []):
            if isinstance(child, str):
                e = elements.get(child)
                if e is None or e.get("type", "cube") != "cube":
                    continue  # locators, meshes etc. are ignored
                if any(abs(a) > 1e-6 for a in e.get("rotation", [0, 0, 0])):
                    raise ConvertError(f"cube '{e['name']}' in bone '{name}' is rotated; Java entity models only rotate bones")
                if not e.get("box_uv", model["meta"].get("box_uv", True)):
                    raise ConvertError(f"cube '{e['name']}' uses per-face UV; switch the model to box UV")
                cubes.append(e)
        parts.append((name, parent_name, node, cubes, parent_origin))
        for child in node.get("children", []):
            if isinstance(child, dict):
                visit(child, name, node["origin"])

    for top in model.get("outliner", []):
        visit(top, None, [0, 24, 0])
    return parts


def convert(model):
    if model.get("meta", {}).get("model_format") not in ("modded_entity", "free"):
        raise ConvertError("model format must be Modded Entity")
    parts = walk(model)
    names = [p[0] for p in parts]
    dupes = {n for n in names if names.count(n) > 1}
    if dupes:
        raise ConvertError(f"bone names must be unique: {sorted(dupes)}")
    parent_of = {p[0]: p[1] for p in parts}
    for bone, parent in REQUIRED.items():
        if bone not in parent_of:
            raise ConvertError(f"missing required bone '{bone}'")
        if parent_of[bone] != parent:
            raise ConvertError(f"bone '{bone}' must be a child of '{parent}', found under '{parent_of[bone]}'")

    tw = model.get("resolution", {}).get("width", 64)
    th = model.get("resolution", {}).get("height", 64)
    lines = []
    for name, parent, g, cubes, po in parts:
        o = g["origin"]
        # Blockbench (x right, y up, absolute) -> Mojang model space (x mirrored, y down, relative to parent)
        ox, oy, oz = -(o[0] - po[0]), -(o[1] - po[1]), o[2] - po[2]
        rot = g.get("rotation", [0, 0, 0])
        rx, ry, rz = -math.radians(rot[0]), -math.radians(rot[1]), math.radians(rot[2])
        builder = "CubeListBuilder.create()"
        for e in cubes:
            fr, to = e["from"], e["to"]
            x, y, z = o[0] - to[0], o[1] - to[1], fr[2] - o[2]
            w, h, d = to[0] - fr[0], to[1] - fr[1], to[2] - fr[2]
            u, v = e.get("uv_offset", [0, 0])
            builder += f"\n                        .texOffs({int(u)}, {int(v)})"
            if e.get("mirror_uv"):
                builder += ".mirror()"
            infl = e.get("inflate", 0) or 0
            dims = f"{f(x)}, {f(y)}, {f(z)}, {f(w)}, {f(h)}, {f(d)}"
            builder += f".addBox({dims}, new CubeDeformation({f(infl)}))" if infl else f".addBox({dims})"
            if e.get("mirror_uv"):
                builder += ".mirror(false)"
        if any(abs(a) > 1e-9 for a in (rx, ry, rz)):
            pose = f"PartPose.offsetAndRotation({f(ox)}, {f(oy)}, {f(oz)}, {f(rx)}, {f(ry)}, {f(rz)})"
        else:
            pose = f"PartPose.offset({f(ox)}, {f(oy)}, {f(oz)})"
        owner = "root" if parent is None else parent
        lines.append(f"        PartDefinition {name} = {owner}.addOrReplaceChild(\"{name}\", {builder},\n                {pose});")
    body = "\n".join(lines)
    return f"""package com.interstice.client.render.creatures;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by tools/bbmodel/bbmodel_to_java.py from models/adherent.bbmodel. Do not edit:
 * change the .bbmodel in Blockbench and re-run the converter.
 */
@SuppressWarnings("unused")
public final class AdherentGeometry {{
    private AdherentGeometry() {{}}

    public static LayerDefinition createLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {tw}, {th});
    }}
}}
"""


def ident(name):
    return re.sub(r"[^A-Za-z0-9]", "_", name).upper()


def convert_animations(model):
    anims = model.get("animations", [])
    bones = {}

    def collect(nodes):
        for n in nodes:
            if isinstance(n, dict):
                bones[n["uuid"]] = n["name"]
                collect(n.get("children", []))
    collect(model.get("outliner", []))
    methods, fields = [], []
    for a in anims:
        name = ident(a["name"])
        length = float(a.get("length", 1))
        builder = f"AnimationDefinition.Builder.withLength({f(length)})"
        if a.get("loop") == "loop":
            builder += ".looping()"
        lines = [f"        AnimationDefinition.Builder b = {builder};"]
        for uid, animator in a.get("animators", {}).items():
            bone = bones.get(uid, animator.get("name"))
            for channel, target in (("rotation", "ROTATION"), ("position", "POSITION")):
                kfs = sorted((k for k in animator.get("keyframes", []) if k.get("channel") == channel), key=lambda k: float(k["time"]))
                if not kfs:
                    continue
                parts = []
                for k in kfs:
                    dp = k["data_points"][0]
                    x, y, z = (float(dp[c]) for c in "xyz")
                    if channel == "rotation":
                        v = (-math.radians(x), -math.radians(y), math.radians(z))
                    else:
                        v = (-x, -y, z)
                    interp = "CATMULLROM" if k.get("interpolation") == "catmullrom" else "LINEAR"
                    parts.append(f"new Keyframe({f(k['time'])}, new Vector3f({f(v[0])}, {f(v[1])}, {f(v[2])}), AnimationChannel.Interpolations.{interp})")
                sep = ",\n                "
                lines.append(f"        b.addAnimation(\"{bone}\", new AnimationChannel(AnimationChannel.Targets.{target},\n                "
                             + sep.join(parts) + "));")
        lines.append("        return b.build();")
        body = "\n".join(lines)
        methods.append(f"    private static AnimationDefinition {name.lower()}() {{\n{body}\n    }}")
        fields.append(f"    public static final AnimationDefinition {name} = {name.lower()}();")
    return f"""package com.interstice.client.render.creatures;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import org.joml.Vector3f;

/**
 * GENERATED by tools/bbmodel/bbmodel_to_java.py from the animations in models/adherent.bbmodel.
 * Do not edit: change the animations in Blockbench and re-run the converter.
 * Values are offsets from the rest pose (radians / model pixels), Mojang model space.
 */
public final class AdherentAnimations {{
    private AdherentAnimations() {{}}

{chr(10).join(fields)}

{chr(10).join(methods)}
}}
"""


def main():
    with open(SRC, encoding="utf-8") as fh:
        model = json.load(fh)
    try:
        java = convert(model)
    except ConvertError as e:
        sys.exit(f"bbmodel_to_java: {e}")
    if "--check" in sys.argv:
        print(java)
        return
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(java)
    print("wrote", OUT)
    with open(ANIM_OUT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(convert_animations(model))
    print("wrote", ANIM_OUT, f"({len(model.get('animations', []))} animations)")
    if "--texture" in sys.argv:
        tex = model.get("textures", [])
        src = tex[0].get("source", "") if tex else ""
        if not src.startswith("data:image/png;base64,"):
            sys.exit("no embedded PNG texture in the model")
        with open(SKIN, "wb") as fh:
            fh.write(base64.b64decode(src.split(",", 1)[1]))
        print("wrote", SKIN)


if __name__ == "__main__":
    main()
