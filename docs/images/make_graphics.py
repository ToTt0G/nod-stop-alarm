#!/usr/bin/env python3
"""Builds Nod's store graphics from the raw emulator screenshots.

    python3 docs/images/make_graphics.py

Inputs:  docs/images/raw/*.png (English), docs/images/raw/sv/*.png (Swedish),
         docs/images/logo.png, app/src/main/res/font/{space_grotesk,manrope}.ttf
Outputs: fastlane/metadata/android/{en-US,sv-SE}/images/{phoneScreenshots/01-08,featureGraphic,icon}.png,
         docs/images/{hero,feature}.png
Needs Pillow (pip install --user pillow).
"""
import math
import os
import shutil

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
IMG = os.path.join(ROOT, "docs", "images")
RAW = os.path.join(IMG, "raw")
FONTS = os.path.join(ROOT, "app", "src", "main", "res", "font")
META = os.path.join(ROOT, "fastlane", "metadata", "android")

INK = (14, 15, 17)
LIME = (200, 245, 90)
WHITE = (255, 255, 255)
MUTED = (154, 160, 166)

SS = 2  # supersampling factor for shapes, so edges come out anti-aliased

SHOTS = [  # (raw file, English caption, Swedish caption)
    ("trip", "Never miss your stop", "Missa aldrig din hållplats"),
    ("zone", "Pick a station or draw your own zone", "Välj en station eller rita din egen zon"),
    ("lock", "Live on your lock screen", "Live på låsskärmen"),
    ("alarm", "Wakes you gently, right on time", "Väcker dig mjukt, i rätt tid"),
    ("widgets", "Widgets that update live", "Widgetar som uppdateras live"),
    ("search", "Search any stop, worldwide", "Sök hållplatser i hela världen"),
    ("zone_peek", "The map gets out of your way", "Kartan håller sig ur vägen"),
    ("home", "Private, free, open source", "Privat, gratis, öppen källkod"),
]
TAGLINE = {"en-US": "Never miss your stop", "sv-SE": "Missa aldrig din hållplats"}
SUBLINE = {"en-US": "Free  ·  Private  ·  Open source", "sv-SE": "Gratis  ·  Privat  ·  Öppen källkod"}


# ---------- fonts ----------

def font(name, size, weight):
    f = ImageFont.truetype(os.path.join(FONTS, name), size)
    try:
        f.set_variation_by_axes([weight])
    except (OSError, AttributeError):
        pass
    return f


def headline(size, weight=600):
    return font("space_grotesk.ttf", size, weight)


def body(size, weight=500):
    return font("manrope.ttf", size, weight)


def text_w(f, s):
    return f.getlength(s)


def wrap_balanced(s, f, max_w, max_lines=2):
    """Fits s in max_lines lines, choosing the split whose longest line is shortest."""
    if text_w(f, s) <= max_w:
        return [s]
    words = s.split()
    best = None
    for i in range(1, len(words)):
        lines = [" ".join(words[:i]), " ".join(words[i:])]
        widest = max(text_w(f, l) for l in lines)
        if widest <= max_w and (best is None or widest < best[0]):
            best = (widest, lines)
    if best and max_lines >= 2:
        return best[1]
    return None


def fit_text(s, maker, size, max_w, max_lines=2, min_size=24):
    """Largest font size (<= size) at which s fits in max_lines lines of max_w."""
    while size >= min_size:
        f = maker(size)
        lines = wrap_balanced(s, f, max_w, max_lines)
        if lines:
            return f, lines
        size -= 2
    f = maker(min_size)
    return f, [s]


def draw_lines(img, lines, f, x, y, fill, line_h, align="center", width=None):
    d = ImageDraw.Draw(img)
    for i, line in enumerate(lines):
        if align == "center":
            lx = x + (width - text_w(f, line)) / 2
        else:
            lx = x
        d.text((lx, y + i * line_h), line, font=f, fill=fill)


# ---------- backgrounds ----------

def glow_layer(size, centre, radii, colour=LIME, alpha=60, blur=None):
    """A soft elliptical glow, returned as an RGBA layer."""
    w, h = size
    k = 8  # draw small and scale up: a huge blur is cheap that way
    small = Image.new("L", (max(1, w // k), max(1, h // k)), 0)
    cx, cy = centre[0] / k, centre[1] / k
    rx, ry = radii[0] / k, radii[1] / k
    ImageDraw.Draw(small).ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=alpha)
    small = small.filter(ImageFilter.GaussianBlur((blur or max(rx, ry) * 0.6)))
    mask = small.resize((w, h), Image.BICUBIC)
    layer = Image.new("RGBA", (w, h), colour + (0,))
    layer.putalpha(mask)
    return layer


def background(size, glows):
    """Ink background with a subtle top-to-bottom lift and soft lime glows."""
    w, h = size
    bg = Image.new("RGB", (w, h), INK)
    grad = Image.linear_gradient("L").resize((w, h))  # 0 at top, 255 at bottom
    top = Image.new("RGB", (w, h), (22, 24, 28))
    bg = Image.composite(bg, top, grad)
    bg = bg.convert("RGBA")
    for g in glows:
        bg.alpha_composite(glow_layer(size, *g))
    return bg


# ---------- phone ----------

def rounded_mask(size, radius):
    w, h = size
    m = Image.new("L", (w * SS, h * SS), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * SS - 1, h * SS - 1), radius * SS, fill=255)
    return m.resize((w, h), Image.LANCZOS)


def hide_carrier(shot):
    """Paints over the emulator's carrier label ("T-Mobile") at the top left of the lock screen.
    The wallpaper there is a smooth gradient, so a heavy blur of the surrounding strip hides it."""
    box = (0, 0, 420, 130)
    strip = shot.crop(box)
    # Rebuild the strip from its right edge and bottom edge, which hold no text.
    clean = strip.resize((4, 4), Image.BOX).resize(strip.size, Image.BICUBIC)
    mask = Image.new("L", strip.size, 0)
    ImageDraw.Draw(mask).rectangle((0, 25, 330, 110), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(14))
    strip.paste(clean, (0, 0), mask)
    shot = shot.copy()
    shot.paste(strip, box[:2])
    return shot


def phone(shot_path, screen_h):
    """The screenshot in a slim dark phone body. Returns an RGBA image (no shadow)."""
    shot = Image.open(shot_path).convert("RGB")
    if os.path.basename(shot_path) == "lock.png":
        shot = hide_carrier(shot)
    sw = round(screen_h * shot.width / shot.height)
    screen = shot.resize((sw, screen_h), Image.LANCZOS)

    bezel = max(6, round(screen_h * 0.012))
    rim = max(2, round(screen_h * 0.0025))
    pw, ph = sw + 2 * bezel, screen_h + 2 * bezel
    r_out = round(pw * 0.115)
    r_in = r_out - bezel

    body_img = Image.new("RGBA", (pw, ph), (0, 0, 0, 0))
    # outer rim (lighter edge), then body
    rim_layer = Image.new("RGBA", (pw, ph), (58, 62, 70, 255))
    body_img.paste(rim_layer, (0, 0), rounded_mask((pw, ph), r_out))
    inner = Image.new("RGBA", (pw - 2 * rim, ph - 2 * rim), (24, 26, 30, 255))
    body_img.paste(inner, (rim, rim), rounded_mask(inner.size, r_out - rim))
    body_img.paste(screen, (bezel, bezel), rounded_mask((sw, screen_h), r_in))
    return body_img


def with_shadow(img, blur, offset=(0, 0), opacity=150, spread=0):
    """Pads img and puts a soft drop shadow under it."""
    pad = blur * 3 + spread
    w, h = img.size
    out = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    a = img.split()[3].point(lambda v: v * opacity // 255)
    sh = Image.new("RGBA", img.size, (0, 0, 0, 255))
    sh.putalpha(a)
    shadow = Image.new("RGBA", out.size, (0, 0, 0, 0))
    shadow.paste(sh, (pad + offset[0], pad + offset[1]))
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    out.alpha_composite(shadow)
    out.alpha_composite(img, (pad, pad))
    return out, pad


def rotated(img, deg):
    """Rotate with supersampling so the edges stay smooth."""
    big = img.resize((img.width * SS, img.height * SS), Image.LANCZOS)
    big = big.rotate(deg, resample=Image.BICUBIC, expand=True)
    return big.resize((big.width // SS, big.height // SS), Image.LANCZOS)


# ---------- outputs ----------

def phone_screenshot(raw_path, caption, out_path):
    W, H = 1080, 1920
    screen_h = 1420
    ph = phone(raw_path, screen_h)
    phone_top = H - 64 - ph.height
    pcx = W // 2
    pcy = phone_top + ph.height // 2

    bg = background((W, H), [
        ((pcx, pcy - 60), (430, 560), LIME, 30),
    ])

    shadowed, pad = with_shadow(ph, blur=36, offset=(0, 26), opacity=200)
    bg.alpha_composite(shadowed, (pcx - ph.width // 2 - pad, phone_top - pad))

    f, lines = fit_text(caption, headline, 88, 900, max_lines=2)
    line_h = round(f.size * 1.12)
    block_h = line_h * (len(lines) - 1) + f.size
    area_top, area_bot = 70, phone_top - 40
    y = area_top + (area_bot - area_top - block_h) / 2 - f.size * 0.12
    draw_lines(bg, lines, f, 0, y, WHITE, line_h, "center", W)

    save(bg, out_path)


def feature_graphic(raw_path, tagline, out_path):
    W, H = 1024, 500
    bg = background((W, H), [
        ((790, 270), (260, 240), LIME, 40),
    ])

    # phone on the right, slightly tilted, running off the bottom edge
    ph = phone(raw_path, 760)
    ph = rotated(ph, -8)
    shadowed, pad = with_shadow(ph, blur=22, offset=(0, 16), opacity=210)
    bg.alpha_composite(shadowed, (790 - ph.width // 2 - pad, 54 - pad))

    # logo + wordmark + tagline on the left
    x = 72
    logo = Image.open(os.path.join(IMG, "logo.png")).convert("RGBA").resize((104, 104), Image.LANCZOS)
    wf = headline(112, 600)
    tf, tlines = fit_text(tagline, headline, 50, 470, max_lines=2)
    t_lh = round(tf.size * 1.14)
    block = 104 + 34 + t_lh * (len(tlines) - 1) + tf.size
    y0 = (H - block) // 2
    bg.alpha_composite(logo, (x, y0))
    d = ImageDraw.Draw(bg)
    # vertically centre the wordmark on the logo using its real ink bounds
    bb = d.textbbox((0, 0), "Nod", font=wf)
    d.text((x + 104 + 26 - bb[0], y0 + 52 - (bb[1] + bb[3]) / 2), "Nod", font=wf, fill=WHITE)
    draw_lines(bg, tlines, tf, x, y0 + 104 + 34 - tf.size * 0.1, WHITE, t_lh, "left")
    save(bg, out_path)


def hero(raws, tagline, subline, out_path):
    W, H = 1600, 900
    bg = background((W, H), [
        ((1110, 480), (400, 380), LIME, 34),
    ])

    left, centre, right = raws  # lock, trip, zone
    pcx = 1110
    specs = [  # (path, screen height, angle, centre x, top)
        (left, 600, 9, pcx - 255, 200),
        (right, 600, -9, pcx + 255, 200),
        (centre, 700, 0, pcx, 110),
    ]
    for path, sh, ang, cx, top in specs:
        ph = phone(path, sh)
        if ang:
            ph = rotated(ph, ang)
        shadowed, pad = with_shadow(ph, blur=28, offset=(0, 20), opacity=220)
        bg.alpha_composite(shadowed, (cx - ph.width // 2 - pad, top - pad))

    x = 110
    logo = Image.open(os.path.join(IMG, "logo.png")).convert("RGBA").resize((96, 96), Image.LANCZOS)
    wf = headline(104, 600)
    tf, tlines = fit_text(tagline, headline, 78, 560, max_lines=2)
    t_lh = round(tf.size * 1.1)
    sf = body(30, 600)
    block = 96 + 52 + t_lh * (len(tlines) - 1) + tf.size + 40 + sf.size
    y0 = (H - block) // 2
    bg.alpha_composite(logo, (x, y0))
    d = ImageDraw.Draw(bg)
    bb = d.textbbox((0, 0), "Nod", font=wf)
    d.text((x + 96 + 24 - bb[0], y0 + 48 - (bb[1] + bb[3]) / 2), "Nod", font=wf, fill=WHITE)
    ty = y0 + 96 + 52
    draw_lines(bg, tlines, tf, x, ty - tf.size * 0.1, WHITE, t_lh, "left")
    sy = ty + t_lh * (len(tlines) - 1) + tf.size + 40
    d.text((x, sy), subline, font=sf, fill=LIME)
    save(bg, out_path)


def icon(out_path):
    """Full-bleed 512 square (Play applies its own mask): the logo's gradient, redrawn edge to edge,
    with the lime glyph lifted from logo.png at its original size and position."""
    logo = Image.open(os.path.join(IMG, "logo.png")).convert("RGBA").resize((512, 512), Image.LANCZOS)
    r, g, b, a = logo.split()
    # Lime has a large green-minus-blue gap (245 - 90); the dark tile has almost none.
    from PIL import ImageChops
    glyph = ImageChops.subtract(g, b).point(lambda v: max(0, min(255, (v - 8) * 255 // 147)))
    glyph = ImageChops.multiply(glyph, a)

    # Radial gradient like the logo: #1E2126 near the top left fading to ink.
    k = 4
    small = Image.new("RGB", (128, 128))
    px = small.load()
    c0, c1 = (30, 33, 38), INK
    for y in range(128):
        for x in range(128):
            d = min(1.0, math.hypot(x - 0.30 * 128, y - 0.22 * 128) / (0.95 * 128))
            px[x, y] = tuple(round(c0[i] + (c1[i] - c0[i]) * d) for i in range(3))
    out = small.resize((512, 512), Image.BICUBIC).convert("RGBA")
    out.paste(Image.new("RGBA", (512, 512), LIME + (255,)), (0, 0), glyph)
    save(out, out_path)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img = img.convert("RGB")
    img.save(path, optimize=True)
    print("wrote", os.path.relpath(path, ROOT), img.size)


def raw(locale, name):
    p = os.path.join(RAW, "sv", name + ".png") if locale == "sv-SE" else os.path.join(RAW, name + ".png")
    return p if os.path.exists(p) else os.path.join(RAW, name + ".png")


def main():
    for locale in ("en-US", "sv-SE"):
        out_dir = os.path.join(META, locale, "images")
        for i, (name, en, sv) in enumerate(SHOTS, 1):
            phone_screenshot(raw(locale, name), en if locale == "en-US" else sv,
                             os.path.join(out_dir, "phoneScreenshots", "%02d.png" % i))
        feature_graphic(raw(locale, "trip"), TAGLINE[locale], os.path.join(out_dir, "featureGraphic.png"))
        icon(os.path.join(out_dir, "icon.png"))

    shutil.copyfile(os.path.join(META, "en-US", "images", "featureGraphic.png"), os.path.join(IMG, "feature.png"))
    print("wrote docs/images/feature.png")
    hero([raw("en-US", "lock"), raw("en-US", "trip"), raw("en-US", "zone")],
         TAGLINE["en-US"], SUBLINE["en-US"], os.path.join(IMG, "hero.png"))


if __name__ == "__main__":
    main()
