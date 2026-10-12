"""Encode the existing 14-frame pilot and make a lossless moving comparison.
Usage: python3 delivery-review.py /absolute/path/to/the/pilot-identity
No canonical writes, filtering reruns, active-manifest changes or full-film batch.
"""
import json
import pathlib
import sys
import time
from PIL import Image, ImageDraw

folder = pathlib.Path(sys.argv[1]).resolve()
pilot = json.loads((folder / "report.json").read_text())
output = folder / "delivery-q94"
output.mkdir(exist_ok=True)
started = time.monotonic()
records = []
for entry in pilot["records"]:
    index = entry["index"]
    target = output / f"frame_{index:05d}.webp"
    Image.open(folder / f"a-edge-aware-{index:05d}.png").save(target, quality=94, method=6)
    records.append({"index": index, "bytes": target.stat().st_size})
root = pathlib.Path(__file__).resolve().parents[1]
media = root.parents[1] / "test-results/scroll-film-navigator/media"
active = json.loads((media / "manifest.json").read_text())
frames = []
for index in range(139, 151):
    files = [folder/f"source-{index:05d}.png", media/f'{active["identity"]}/1600/robotic-arm/frame_{index:05d}.webp',
             folder/f"a-edge-aware-{index:05d}.webp", output/f"frame_{index:05d}.webp"]
    sheet = Image.new("RGB", (1600, 850), "black")
    draw = ImageDraw.Draw(sheet)
    for n, (file, label) in enumerate(zip(files, ["Canonical PNG scaled", "Current WebP q82", "A / lossless WebP", "A / WebP q94"])):
        x, y = (n % 2)*800, (n//2)*425
        draw.text((x+8, y+5), f"{label}; source frame {index}", fill="white")
        sheet.paste(Image.open(file).convert("RGB").crop((250, 180, 1050, 580)), (x, y+25))
    frames.append(sheet)
frames[6].save(output/"comparison-00145.png")
# APNG is lossless; forward/reverse preserves the authored source cadence.
sequence = frames + frames[-2:0:-1]
sequence[0].save(output/"temporal-comparison.png", save_all=True, append_images=sequence[1:], duration=42, loop=0, disposal=0, blend=0)
summary = {"method": "14 existing masked edge-aware pilot PNGs encoded at Pillow WebP q94. APNG compares 12 consecutive canonical times, forward/reverse at approximately 24fps, identical 800x400 crops, no comparison-video compression. Still/temporal/HTML/WebGL acceptance remains separate. Sample extrapolation is not measured full-film size.",
           "pilotIdentity": pilot["identity"], "records": records, "durationSeconds": time.monotonic()-started,
           "meanBytes": sum(r["bytes"] for r in records)/len(records), "estimatedFilmBytes": sum(r["bytes"] for r in records)/len(records)*192}
(output/"report.json").write_text(json.dumps(summary, indent=2)+"\n")
print(json.dumps(summary))
