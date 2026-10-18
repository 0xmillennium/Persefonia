"""Deterministic offline robotic-arm background treatment, never a runtime effect."""
import argparse
import json
import pathlib
import subprocess
import cv2
import numpy as np
import PIL
from PIL import Image, features

CONFIG = {"version": 2, "width": 1600, "background": [10, 12, 13],
          "foregroundContrast": 12, "foregroundLuma": 45,
          "matte": "continuous squared plate contrast; no expanded binary mask"}


def protection(source):
    height, width = source.shape[:2]
    work = source.astype(np.float32)
    luma = work @ np.array([.2126, .7152, .0722], np.float32)
    spread = cv2.dilate(luma, np.ones((9, 9), np.uint8)) - cv2.erode(luma, np.ones((9, 9), np.uint8))
    y, x = np.mgrid[0:height:16, 0:width:16].astype(np.float32)
    sx, sy = x/width, y/height
    terms = [(a, b) for a in range(5) for b in range(5-a)]
    design = np.stack([sx**a * sy**b for a, b in terms], axis=-1).reshape(-1, len(terms))
    observations = work[::16, ::16].reshape(-1, 3)
    eligible = ((luma[::16, ::16] < 40) & (spread[::16, ::16] < 6)).reshape(-1)
    # Robust fitting prevents isolated dark subject surfaces from owning the plate.
    weights = eligible.astype(np.float32)
    for _ in range(4):
        root = np.sqrt(weights)
        coefficients = np.linalg.lstsq(design*root[:, None], observations*root[:, None], rcond=None)[0]
        error = np.max(np.abs(design @ coefficients-observations), axis=1)
        weights = eligible * np.minimum(1, 2/np.maximum(error, .01))
    yy, xx = np.mgrid[:height, :width].astype(np.float32)
    xx /= width
    yy /= height
    plate = sum((xx**a * yy**b)[..., None]*coefficients[i] for i, (a, b) in enumerate(terms))
    difference = np.max(np.abs(work-plate), axis=2)
    # Binary neighborhood protection retains background around sparse wireframes
    # as a visible halo. A continuous local matte preserves actual high-contrast
    # pixels without retaining an arbitrary expanded patch around them.
    alpha = np.clip(difference/CONFIG["foregroundContrast"], 0, 1)**2
    alpha[luma >= CONFIG["foregroundLuma"]] = 1
    return alpha


def versions():
    return {"pillow": PIL.__version__, "numpy": np.__version__, "opencv": cv2.__version__, "webp": features.version("webp")}

def render(folder, quality, source_folder, digits):
    for width in [960, 1600]:
        (folder/str(width)/"robotic-arm").mkdir(parents=True, exist_ok=True)
    records = []
    for index in range(1, 193):
        path = source_folder/f"frame_{index:0{digits}d}.png"
        raw = subprocess.run(["ffmpeg", "-v", "error", "-threads", "2", "-i", str(path), "-vf", "scale=1600:900:flags=lanczos", "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], check=True, capture_output=True).stdout
        source = np.frombuffer(raw, np.uint8).reshape(900, 1600, 3)
        alpha = protection(source)
        result = np.rint(source*alpha[..., None]+np.array(CONFIG["background"], np.float32)*(1-alpha[..., None])).astype(np.uint8)
        if not np.array_equal(result[alpha == 1], source[alpha == 1]):
            raise RuntimeError(f"Protected detail changed in frame {index}")
        image = Image.fromarray(result)
        for width in [960, 1600]:
            delivered = image if width == 1600 else image.resize((960, 540), Image.Resampling.LANCZOS)
            delivered.save(folder/str(width)/"robotic-arm"/f"frame_{index:05d}.webp", quality=quality, method=6)
        records.append({"index": index, "protectedFraction": float(np.mean(alpha == 1)), "protectedPixelsExact": True, "nearFlatBackgroundFraction": float(np.mean(alpha < .05))})
        if index % 16 == 0:
            print(f"Robotic-arm background delivery: {index}/192", flush=True)
    (folder/"background-validation.json").write_text(json.dumps({"config": CONFIG, "versions": versions(), "quality": quality, "records": records}, indent=2)+"\n")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--versions", action="store_true")
    parser.add_argument("--output", type=pathlib.Path)
    parser.add_argument("--quality", type=int, default=96)
    parser.add_argument("--source", type=pathlib.Path)
    parser.add_argument("--digits", type=int, choices=[4, 5])
    args = parser.parse_args()
    if args.versions:
        print(json.dumps(versions()))
    elif args.output and args.source and args.digits:
        render(args.output, args.quality, args.source, args.digits)
    else:
        parser.error("--versions or --output with --source and --digits required")
