# Generate launcher icons from a single source PNG.
# Usage: D:\Python311\python.exe scripts\gen_icons.py
#
# Adaptive icons: launchers only guarantee the centre ~2/3 (66/108dp) survives
# their circle/squircle mask. The artwork is therefore scaled INTO that safe
# zone, and the surrounding area is filled with the source's own beige using a
# feathered rounded mask (both sides are the same colour, so no seam is visible).
# This keeps the book complete instead of letting the launcher slice it.
#
# Legacy / store icons keep the source's own rounded-square artwork unchanged.
import os
import numpy as np
import cv2
from PIL import Image, ImageDraw, ImageFilter

SRC = (r"C:\Users\30760\AppData\Local\CodeBuddyExtension\Data"
       r"\a0b83d64-6cf2-4dbc-a1b0-5c2b9298103d\CodeBuddyIDE"
       r"\a0b83d64-6cf2-4dbc-a1b0-5c2b9298103d\history"
       r"\9d7c42e789058b06c73916a15a28d9fd"
       r"\0f818bea2d7a4180ab2d0c9ae8d8e0b3\assets\IMG_20261122_223708.7f69bdb112.png")
ROOT = r"d:\Desktop\Android-APK\Local comics"
RES = os.path.join(ROOT, "app", "src", "main", "res")
STORE = os.path.join(ROOT, "dist", "store")

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
# artwork size as a fraction of the 108dp canvas.
# Safe zone is 0.66; launcher masks typically show ~0.72, so 0.52 keeps a
# comfortable margin and matches how most icons look next to others.
ART_RATIO = 0.52


def main():
    # 源图的圆角外是**透明**的（不是白色）；必须先按 alpha 合成到米色上，
    # 否则 convert("RGB") 会把透明区变成纯黑（上一版的黑角就是这么来的）。
    img = Image.open(SRC).convert("RGBA")
    arr = np.array(img)
    h, w = arr.shape[:2]
    src_alpha = arr[:, :, 3]
    outside = src_alpha < 16

    strip = arr[int(h * 0.06), int(w * 0.15):int(w * 0.85), :3].reshape(-1, 3)
    opaque_strip = strip[strip.min(axis=1) > 20]
    colors, counts = np.unique(opaque_strip, axis=0, return_counts=True)
    bg = tuple(int(v) for v in colors[counts.argmax()])

    bg_arr = np.array(bg, np.float32)
    a = (src_alpha.astype(np.float32) / 255.0)[:, :, None]
    arr = (arr[:, :, :3].astype(np.float32) * a + bg_arr * (1.0 - a)).astype(np.uint8)

    # Cut the book out by colour distance to the beige field (small tolerance so
    # the tan cover survives), then dilate 1px to restore anti-aliased edges.
    diff = np.abs(arr.astype(np.int16) - np.array(bg, np.int16)).max(axis=2)
    alpha = np.where((diff <= 12) | outside, 0, 255).astype(np.uint8)
    alpha = cv2.dilate(alpha, np.ones((3, 3), np.uint8), iterations=1)
    alpha = cv2.GaussianBlur(alpha, (3, 3), 0)

    ys, xs = np.where(alpha > 8)
    if len(xs) == 0:
        raise SystemExit("artwork mask is empty")
    cut = np.dstack([arr, alpha])[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    art = Image.fromarray(cut, "RGBA")

    def scale_art(size):
        """Book only, scaled to ART_RATIO and centred on a beige canvas."""
        s = max(2, int(size * ART_RATIO))
        scale = min(s / art.width, s / art.height)
        scaled = art.resize(
            (max(1, int(art.width * scale)), max(1, int(art.height * scale))),
            Image.LANCZOS,
        )
        canvas = Image.new("RGBA", (size, size), bg + (255,))
        canvas.alpha_composite(
            scaled,
            ((size - scaled.width) // 2, (size - scaled.height) // 2),
        )
        return canvas

    # legacy icons: the source's own rounded-square artwork, untouched
    rgba = np.dstack([arr, np.where(outside, 0, 255).astype(np.uint8)])
    rounded = Image.fromarray(rgba, "RGBA")

    for name, size in DENSITIES.items():
        d = os.path.join(RES, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        scale_art(size).save(os.path.join(d, "ic_launcher_foreground.png"))

        legacy = rounded.resize((size, size), Image.LANCZOS)
        legacy.save(os.path.join(d, "ic_launcher.png"))

        mask_img = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask_img).ellipse((0, 0, size - 1, size - 1), fill=255)
        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_icon.paste(legacy, (0, 0), mask_img)
        round_icon.save(os.path.join(d, "ic_launcher_round.png"))

    # themed icon silhouette from the cut-out alpha
    body = cut[:, :, 3]
    silhouette = np.dstack([
        np.full(body.shape, 255, np.uint8),
        np.full(body.shape, 255, np.uint8),
        np.full(body.shape, 255, np.uint8),
        body,
    ])
    mono_src = Image.fromarray(silhouette, "RGBA")
    s = int(432 * ART_RATIO)
    scale = min(s / mono_src.width, s / mono_src.height)
    mono_scaled = mono_src.resize(
        (max(1, int(mono_src.width * scale)), max(1, int(mono_src.height * scale))),
        Image.LANCZOS,
    )
    mono = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    mono.alpha_composite(
        mono_scaled,
        ((432 - mono_scaled.width) // 2, (432 - mono_scaled.height) // 2),
    )
    mono.save(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_monochrome.png"))

    os.makedirs(STORE, exist_ok=True)
    rounded.resize((512, 512), Image.LANCZOS).save(os.path.join(STORE, "icon-512.png"))

    print("hex bg: #%02X%02X%02X" % bg)


if __name__ == "__main__":
    main()
