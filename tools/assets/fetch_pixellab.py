"""Downloads finished PixelLab jobs listed in a jobs file (name job_id kind) into a raw folder and builds a
contact sheet (x4 scale) to review them. The download URL needs no auth (PixelLab MCP 'download' link).

    python -I tools/assets/fetch_pixellab.py <jobs.txt> <raw_dir> [sheet.png]
"""
import io
import os
import sys
import time
import urllib.request

from PIL import Image, ImageDraw


def fetch(job):
    url = f"https://api.pixellab.ai/mcp/images/{job}/download"
    for _ in range(20):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "interstice-asset-fetch"}), timeout=30) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 423:
                time.sleep(3)
                continue
            raise
    raise RuntimeError("not ready: " + job)


def main(jobs, raw, sheet=None):
    os.makedirs(raw, exist_ok=True)
    rows = [l.split() for l in open(jobs, encoding="utf-8") if l.strip() and not l.startswith("#")]
    shown = []
    for name, job, kind in rows:
        p = os.path.join(raw, name + ".png")
        if not os.path.exists(p):
            data = fetch(job)
            Image.open(io.BytesIO(data)).save(p)
            print("fetched", name)
        shown.append((name, Image.open(p).convert("RGBA")))
    if sheet:
        S, cols = 4, 6
        cw, ch = 32 * S + 8, 64 * S + 20
        n = len(shown)
        im = Image.new("RGBA", (cols * cw, ((n + cols - 1) // cols) * ch), (60, 60, 70, 255))
        d = ImageDraw.Draw(im)
        for i, (name, t) in enumerate(shown):
            x, y = (i % cols) * cw, (i // cols) * ch
            # checker behind transparent pixels
            for cy in range(0, t.height * S, 8):
                for cx in range(0, t.width * S, 8):
                    if (cx // 8 + cy // 8) % 2:
                        d.rectangle([x + cx, y + cy, x + cx + 7, y + cy + 7], fill=(90, 90, 100, 255))
            big = t.resize((t.width * S, t.height * S), Image.NEAREST)
            im.alpha_composite(big, (x, y))
            d.text((x + 2, y + ch - 18), name[:22], fill=(255, 255, 255, 255))
        im.save(sheet)
        print("sheet", sheet)


if __name__ == "__main__":
    main(*sys.argv[1:])
