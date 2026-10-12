"""Bounded offline pilot, never publishes media or writes canonical PNGs.

Requires the local Python Pillow/numpy/OpenCV tools. These are study tools, not
browser dependencies. Approval is required before adopting a full-film pipeline.
"""
import hashlib
import json
import pathlib
import subprocess
import time
import cv2
import numpy as np
import PIL
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUTPUT = ROOT.parents[1] / "test-results/scroll-film-navigator/finalization/restoration"
INDICES = [49, 97, *range(139, 151)]
CONFIG = {"version": 1, "width": 1600, "bilateralDiameter": 9, "sigmaColor": 2.0,
          "sigmaSpace": 4.0, "maximumDarkLuma": 40, "maximumLocalRange": 5,
          "ditherAmplitude": 0.65, "seed": 42, "encoding": "Pillow lossless WebP",
          "pillow": PIL.__version__, "numpy": np.__version__, "opencv": cv2.__version__}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def study():
    started = time.monotonic()
    sources = {i: ROOT / f"assets/frames/robotic-arm/frame_{i:05d}.png" for i in INDICES}
    hashes = {i: digest(p) for i, p in sources.items()}
    identity = hashlib.sha256(json.dumps({"config": CONFIG, "sources": hashes}, sort_keys=True).encode()).hexdigest()
    folder = OUTPUT / identity
    folder.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((ROOT.parents[1] / "test-results/scroll-film-navigator/media/manifest.json").read_text())
    rng = np.random.default_rng(CONFIG["seed"])
    # One fixed screen-coordinate field across the complete pilot. Never randomize
    # per frame: temporal noise is an explicit review concern, not hidden jitter.
    noise = rng.uniform(-1, 1, (900, 1600)).astype(np.float32)
    noise -= cv2.GaussianBlur(noise, (5, 5), 0.8)
    noise *= CONFIG["ditherAmplitude"]
    records = []
    for index, path in sources.items():
        raw = subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-threads", "2", "-i", str(path),
                              "-vf", "scale=1600:900:flags=lanczos", "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"],
                             check=True, capture_output=True).stdout
        source = np.frombuffer(raw, np.uint8).reshape(900, 1600, 3).copy()
        work = source.astype(np.float32)
        luma = work @ np.array([.2126, .7152, .0722], np.float32)
        local_range = cv2.dilate(luma, np.ones((9, 9), np.uint8)) - cv2.erode(luma, np.ones((9, 9), np.uint8))
        mask = np.clip((40-luma)/10, 0, 1) * np.clip((5-local_range)/2, 0, 1)
        smoothed = cv2.bilateralFilter(work, 9, 2.0, 4.0)
        candidates = {
            "a-edge-aware": work + (smoothed-work)*mask[..., None],
            "b-dither-only": work,
        }
        Image.fromarray(source).save(folder / f"source-{index:05d}.png")
        record = {"index": index, "sourceHash": hashes[index], "darkMaskFraction": float(np.mean(mask > 0)), "outputs": {}}
        for name, value in candidates.items():
            result = np.clip(np.rint(value + noise[..., None]*mask[..., None]), 0, 255).astype(np.uint8)
            image = Image.fromarray(result)
            png = folder / f"{name}-{index:05d}.png"
            encoded = folder / f"{name}-{index:05d}.webp"
            image.save(png); image.save(encoded, lossless=True, method=6)
            difference = np.abs(result.astype(np.int16)-source.astype(np.int16))
            record["outputs"][name] = {"bytes": encoded.stat().st_size, "sha256": digest(encoded),
                                        "maximumChannelChange": int(difference.max()), "meanChannelChange": float(difference.mean()),
                                        "protectedRegionExact": bool(np.all(result[mask == 0] == source[mask == 0]))}
        records.append(record)
    if any(digest(path) != hashes[index] for index, path in sources.items()):
        raise RuntimeError("Canonical source changed during pilot")
    crop = (250, 180, 1050, 580)
    for index in [49, 97, 145]:
        files = [folder/f"source-{index:05d}.png",
                 ROOT.parents[1]/f'test-results/scroll-film-navigator/media/{manifest["identity"]}/1600/robotic-arm/frame_{index:05d}.webp',
                 folder/f"a-edge-aware-{index:05d}.webp", folder/f"b-dither-only-{index:05d}.webp"]
        sheet = Image.new("RGB", (1600, 850), "black"); draw = ImageDraw.Draw(sheet)
        for n, (file, label) in enumerate(zip(files, ["Canonical scaled PNG", "Current q82 WebP", "A: masked edge-aware + fixed dither / lossless", "B: fixed dither / lossless"])):
            x, y = (n % 2)*800, (n//2)*425
            draw.text((x+8, y+5), f"{label}; frame {index}", fill="white")
            sheet.paste(Image.open(file).convert("RGB").crop(crop), (x, y+25))
        sheet.save(folder/f"comparison-{index:05d}.png")
    report = {"identity": identity, "config": CONFIG, "sourcePreservation": "14 selected SHA-256 identities rechecked; full baseline inventory is separately retained",
              "method": "Matched FFmpeg Lanczos 1600x900 source. A filters only dark low-detail regions; B only adds fixed sub-LSB dither. Protected regions are exact. Lossless delivery preserves each candidate's eight-bit pixels. No recovered detail claim; no higher-bit display claim. Human temporal acceptance remains pending.",
              "durationSeconds": time.monotonic()-started, "records": records}
    (folder/"report.json").write_text(json.dumps(report, indent=2)+"\n")
    print(json.dumps({"output": str(folder), "durationSeconds": report["durationSeconds"], "frames": len(records), "identity": identity}))


if __name__ == "__main__":
    study()
