"""Conservative background replacement pilot; never publishes or edits sources.

Fits the existing low-detail plate and blends a charcoal background using
a continuous contrast matte. Bright/high-contrast source pixels are protected.
This is not a claim of a complete foreground matte or recovered source detail.
"""
import hashlib
import json
import pathlib
import time
import cv2
import numpy as np
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
EVIDENCE = ROOT.parents[1] / "test-results/scroll-film-navigator/finalization"
PILOT = EVIDENCE / "restoration/bda6383ac5b326369ce98bb04b4318b9623e593b0c3105a564ac6a5318481b26"
INDICES = [49, 97, *range(139, 151)]
import importlib.util
import sys
sys.dont_write_bytecode = True
MODULE = ROOT / "scripts/background-transform.py"
spec = importlib.util.spec_from_file_location("background_transform", MODULE)
operator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(operator)
CONFIG, protection = operator.CONFIG, operator.protection

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def study():
    started = time.monotonic()
    canonical = {i: ROOT/f"assets/frames/robotic-arm/frame_{i:05d}.png" for i in INDICES}
    hashes = {i: digest(path) for i, path in canonical.items()}
    identity = hashlib.sha256(json.dumps({"config": CONFIG, "sources": hashes}, sort_keys=True).encode()).hexdigest()
    output = EVIDENCE / "background" / identity
    output.mkdir(parents=True, exist_ok=True)
    sources = {i: np.asarray(Image.open(PILOT/f"source-{i:05d}.png").convert("RGB")) for i in INDICES}
    masks = {i: protection(source) for i, source in sources.items()}
    records = []
    temporal = []
    for index, source in sources.items():
        mask = masks[index]
        blend = 1-mask
        target = np.array(CONFIG["background"], np.float32)
        result = np.rint(source*(1-blend[..., None])+target*blend[..., None]).astype(np.uint8)
        png = output/f"background-{index:05d}.png"
        webp = output/f"background-{index:05d}.webp"
        Image.fromarray(result).save(png)
        Image.fromarray(result).save(webp, quality=96, method=6)
        Image.fromarray(np.rint(mask*255).astype(np.uint8)).save(output/f"protection-{index:05d}.png")
        records.append({"index": index, "sourceHash": hashes[index], "protectedFraction": float(np.mean(mask == 1)),
                        "protectedPixelsExact": bool(np.array_equal(result[mask == 1], source[mask == 1])),
                        "nearFlatBackgroundFraction": float(np.mean(blend > .95)), "bytes": webp.stat().st_size,
                        "encodedSha256": digest(webp)})
        if index in [49, 97, 145]:
            sheet = Image.new("RGB", (1600, 475), "#0a0c0d")
            draw = ImageDraw.Draw(sheet)
            for n, (image, label) in enumerate([(source, "Original scaled source"), (result, "Conservative charcoal background / source details protected")]):
                draw.text((n*800+10, 8), f"{label}; frame {index}", fill="#eeeae0")
                sheet.paste(Image.fromarray(image).resize((800, 450), Image.Resampling.LANCZOS), (n*800, 25))
            sheet.save(output/f"comparison-{index:05d}.png")
        if 139 <= index <= 150:
            sheet = Image.new("RGB", (1600, 450), "black")
            sheet.paste(Image.fromarray(source).resize((800, 450), Image.Resampling.LANCZOS), (0, 0))
            sheet.paste(Image.fromarray(result).resize((800, 450), Image.Resampling.LANCZOS), (800, 0))
            temporal.append(sheet)
    sequence = temporal + temporal[-2:0:-1]
    sequence[0].save(output/"temporal-comparison.png", save_all=True, append_images=sequence[1:], duration=42, loop=0)
    if any(digest(path) != hashes[index] for index, path in canonical.items()):
        raise RuntimeError("Canonical sources changed")
    report = {"identity": identity, "config": CONFIG, "records": records, "seconds": time.monotonic()-started,
              "method": "Charcoal replacement with a continuous plate-contrast matte. Bright/high-contrast pixels exact before encoding; low-contrast tones change. No geometric dilation, segmentation edge or randomized temporal noise. Not a semantic alpha matte or recovered detail. No publication."}
    (output/"report.json").write_text(json.dumps(report, indent=2)+"\n")
    print(json.dumps({"output": str(output), "seconds": report["seconds"], "frames": len(records)}))


if __name__ == "__main__":
    study()
