"""Generate Breadcrumb launcher icons and splash assets.

The mark is a trail of crumbs: five discs following a curve, growing and
brightening toward the end, on a deep warm ink ground. Discrete dots, not a
connected line -- crumbs are dropped one at a time.

Run from the repo root:  python tools/generate_icons.py

Writes:
  android/app/src/main/res/mipmap-*/ic_launcher.png
  android/app/src/main/res/mipmap-*/ic_launcher_round.png
  android/app/src/main/res/mipmap-*/ic_launcher_foreground.png
  android/app/src/main/res/mipmap-*/ic_launcher_background.png
  android/app/src/main/res/mipmap-*/ic_launcher_monochrome.png
  android/app/src/main/res/drawable-*/splash_icon.png
  android/app/src/main/ic_launcher-playstore.png
  tools/preview.png
"""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android" / "app" / "src" / "main" / "res"
MAIN = ROOT / "android" / "app" / "src" / "main"

SS = 4                 # supersample factor for clean edges
M = 512                # master logical size
N = M * SS             # supersampled canvas

# --- palette -----------------------------------------------------------
INK_OUTER = (14, 11, 9)
INK_INNER = (48, 36, 26)
BRAND_BG = "#171310"   # splash window background

# crumbs: (radius as fraction of canvas, alpha, rgb), evenly spaced along the curve
CRUMBS = [
    (0.037, 110, (232, 152, 32)),
    (0.047, 150, (240, 164, 42)),
    (0.059, 190, (245, 177, 60)),
    (0.073, 225, (250, 191, 90)),
    (0.090, 255, (255, 208, 138)),
]

# Curve the trail follows, in normalised canvas coords. Endpoints and radii are
# sized so the whole mark clears the adaptive-icon mask AND the round mask --
# the first pass had the largest crumb clipped off the top-right corner.
P0, P1, P2, P3 = (0.285, 0.685), (0.365, 0.730), (0.560, 0.590), (0.645, 0.350)

# adaptive icons and the Android 12 splash both keep art inside the centre 2/3
SAFE = 2 / 3


def cubic(t):
    mt = 1 - t
    x = mt**3 * P0[0] + 3 * mt * mt * t * P1[0] + 3 * mt * t * t * P2[0] + t**3 * P3[0]
    y = mt**3 * P0[1] + 3 * mt * mt * t * P1[1] + 3 * mt * t * t * P2[1] + t**3 * P3[1]
    return x, y


def even_points(n):
    """Space n points by arc length, not by bezier parameter.

    Sampling t uniformly bunches the crumbs where the curve is slow, which read
    as a kinked line rather than a trail.
    """
    steps = 2000
    pts = [cubic(i / steps) for i in range(steps + 1)]
    cum = [0.0]
    for i in range(1, len(pts)):
        dx, dy = pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]
        cum.append(cum[-1] + (dx * dx + dy * dy) ** 0.5)
    total = cum[-1]
    out, j = [], 0
    for k in range(n):
        target = total * k / (n - 1)
        while j < len(cum) - 1 and cum[j] < target:
            j += 1
        out.append(pts[j])
    return out


def disc(draw, cx, cy, r, fill):
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill)


def make_background():
    """Deep ink with a soft warm glow offset toward the bright end of the trail."""
    g = Image.new("RGB", (96, 96))
    px = g.load()
    gx, gy = 0.66 * 96, 0.36 * 96
    for y in range(96):
        for x in range(96):
            d = (((x - gx) / 96) ** 2 + ((y - gy) / 96) ** 2) ** 0.5
            k = max(0.0, 1.0 - d / 0.85) ** 1.7
            px[x, y] = tuple(
                int(INK_OUTER[i] + (INK_INNER[i] - INK_OUTER[i]) * k) for i in range(3)
            )
    return g.resize((N, N), Image.BICUBIC)


def make_foreground(mono=False):
    """The crumb trail on transparency, art confined to the centre 2/3."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))

    pts = even_points(len(CRUMBS))

    # Restrained glow behind the brightest crumb -- enough to give the end of the
    # trail some lift, not enough to haze the disc edge.
    if not mono:
        glow = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        gx, gy = pts[-1]
        disc(gd, gx * N, gy * N, 0.135 * N, (255, 190, 105, 38))
        glow = glow.filter(ImageFilter.GaussianBlur(0.042 * N))
        img.alpha_composite(glow)

    d = ImageDraw.Draw(img)
    for (cx, cy), (r, a, rgb) in zip(pts, CRUMBS):
        fill = (255, 255, 255, a) if mono else (*rgb, a)
        disc(d, cx * N, cy * N, r * N, fill)
    return img


def down(img, size):
    return img.resize((size, size), Image.LANCZOS)


def centre_crop(img):
    """Crop to the adaptive-icon safe zone, matching what the launcher mask shows."""
    inset = int(N * (1 - SAFE) / 2)
    return img.crop((inset, inset, N - inset, N - inset))


def round_mask(size):
    m = Image.new("L", (size * SS, size * SS), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size * SS, size * SS], fill=255)
    return m.resize((size, size), Image.LANCZOS)


def write(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print(f"  {path.relative_to(ROOT)}  {img.size[0]}px")


def main():
    bg = make_background().convert("RGBA")
    fg = make_foreground()
    mono = make_foreground(mono=True)

    # full-bleed composite, then cropped to what the launcher actually shows
    flat = centre_crop(Image.alpha_composite(bg, fg))

    # adaptive icon layers are 108dp; legacy icons are 48dp
    adaptive = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
    legacy = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    # Android 12 splash icon is 288dp with art inside a 192dp circle
    splash = {"mdpi": 288, "hdpi": 432, "xhdpi": 576, "xxhdpi": 864, "xxxhdpi": 1152}

    print("adaptive layers:")
    for d, s in adaptive.items():
        write(down(bg, s), RES / f"mipmap-{d}" / "ic_launcher_background.png")
        write(down(fg, s), RES / f"mipmap-{d}" / "ic_launcher_foreground.png")
        write(down(mono, s), RES / f"mipmap-{d}" / "ic_launcher_monochrome.png")

    print("legacy icons:")
    for d, s in legacy.items():
        sq = down(flat, s)
        write(sq, RES / f"mipmap-{d}" / "ic_launcher.png")
        rnd = sq.copy()
        rnd.putalpha(round_mask(s))
        write(rnd, RES / f"mipmap-{d}" / "ic_launcher_round.png")

    print("splash icon:")
    for d, s in splash.items():
        write(down(fg, s), RES / f"drawable-{d}" / "splash_icon.png")

    print("store + preview:")
    write(down(flat, 512), MAIN / "ic_launcher-playstore.png")

    # side-by-side preview: full bleed, launcher-cropped, round, and small
    pv = Image.new("RGBA", (880, 260), (24, 24, 28, 255))
    pv.alpha_composite(down(Image.alpha_composite(bg, fg), 200), (30, 30))
    pv.alpha_composite(down(flat, 200), (260, 30))
    r = down(flat, 200)
    r.putalpha(round_mask(200))
    pv.alpha_composite(r, (490, 30))
    pv.alpha_composite(down(flat, 96), (720, 30))
    pv.alpha_composite(down(flat, 48), (720, 140))
    write(pv, ROOT / "tools" / "preview.png")


if __name__ == "__main__":
    main()
