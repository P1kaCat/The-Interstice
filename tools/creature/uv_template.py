"""
Draws the UV layout of the exported Adherent mesh (LOD0) over its current skin, for texture painting
(PixelLab or by hand). Output: docs/uv/adherent_uv_template_512.png (layout over the skin) and
docs/uv/adherent_uv_wire_512.png (layout alone on transparent). Other species of the family:
docs/uv/<species>_uv_template_512.png and docs/uv/<species>_uv_wire_512.png.

Usage: python -I tools/creature/uv_template.py [species ...|all]   (default: adherent)
"""
import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "client", "resources", "assets", "interstice")
OUT = os.path.join(ROOT, "docs", "uv")
FAMILY = ["adherent", "crawler", "strider", "burden", "clinger", "hollow", "watcher", "fractured"]


def main(name="adherent"):
    data = json.load(open(os.path.join(ASSETS, "models", "entity", f"{name}.json"), encoding="utf-8"))
    lod = data["lods"][0]
    uv, idx = lod["uvs"], lod["indices"]
    skin = Image.open(os.path.join(ASSETS, "textures", "entity", f"{name}.png")).convert("RGBA")
    w, h = skin.size
    wire = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(wire)
    for i in range(0, len(idx) - 2, 3):
        pts = [(uv[v * 2] * w, uv[v * 2 + 1] * h) for v in idx[i:i + 3]]
        d.polygon(pts, outline=(255, 40, 200, 255))
    os.makedirs(OUT, exist_ok=True)
    wire.save(os.path.join(OUT, f"{name}_uv_wire_512.png"))
    Image.alpha_composite(skin, wire).save(os.path.join(OUT, f"{name}_uv_template_512.png"))
    print(f"{name}: {len(idx) // 3} triangles drawn into {OUT}")


if __name__ == "__main__":
    import sys
    names = sys.argv[1:] or ["adherent"]
    if names == ["all"]:
        names = FAMILY
    for n in names:
        main(n)
