import os
from PIL import Image, ImageDraw, ImageFont

def create_thaiflash_icon(size, is_round=False):
    scale = 4
    canvas_size = size * scale
    img = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))

    margin = int(canvas_size * 0.04)
    rect = [margin, margin, canvas_size - margin, canvas_size - margin]

    # Royal Violet -> Deep Indigo gradient (matching LearnThai family)
    bg_img = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    bg_draw = ImageDraw.Draw(bg_img)
    top_color = (103, 58, 183)     # #673AB7
    bottom_color = (49, 27, 146)   # #311B92

    for y in range(canvas_size):
        ratio = y / canvas_size
        r = int(top_color[0] * (1 - ratio) + bottom_color[0] * ratio)
        g = int(top_color[1] * (1 - ratio) + bottom_color[1] * ratio)
        b = int(top_color[2] * (1 - ratio) + bottom_color[2] * ratio)
        bg_draw.line([(0, y), (canvas_size, y)], fill=(r, g, b, 255))

    mask = Image.new("L", (canvas_size, canvas_size), 0)
    mask_draw = ImageDraw.Draw(mask)
    if is_round:
        mask_draw.ellipse(rect, fill=255)
    else:
        mask_draw.rounded_rectangle(rect, radius=int(canvas_size * 0.22), fill=255)

    img.paste(bg_img, (0, 0), mask)
    draw = ImageDraw.Draw(img)

    # Back stacked flashcard (subtle depth)
    back_card = [
        int(canvas_size * 0.21),
        int(canvas_size * 0.14),
        int(canvas_size * 0.85),
        int(canvas_size * 0.76),
    ]
    draw.rounded_rectangle(back_card, radius=int(canvas_size * 0.12), fill=(255, 255, 255, 70))

    # Front flashcard shadow
    front_card = [
        int(canvas_size * 0.16),
        int(canvas_size * 0.20),
        int(canvas_size * 0.80),
        int(canvas_size * 0.82),
    ]
    shadow_offset = int(canvas_size * 0.025)
    draw.rounded_rectangle(
        [front_card[0], front_card[1] + shadow_offset, front_card[2], front_card[3] + shadow_offset],
        radius=int(canvas_size * 0.12),
        fill=(20, 8, 65, 130),
    )

    # Front flashcard surface
    draw.rounded_rectangle(front_card, radius=int(canvas_size * 0.12), fill=(255, 255, 255, 245))

    # Thai character "ก" centered on flashcard
    font_path = "C:/Windows/Fonts/LeelaUIb.ttf"
    if not os.path.exists(font_path):
        font_path = "C:/Windows/Fonts/tahomabd.ttf"
    font = ImageFont.truetype(font_path, int(canvas_size * 0.40))
    thai_char = "ก"
    bbox = draw.textbbox((0, 0), thai_char, font=font)
    char_w = bbox[2] - bbox[0]
    char_h = bbox[3] - bbox[1]
    cx = (front_card[0] + front_card[2]) / 2
    cy = (front_card[1] + front_card[3]) / 2 - int(canvas_size * 0.02)
    draw.text((cx - char_w / 2 - bbox[0], cy - char_h / 2 - bbox[1]), thai_char, font=font, fill=(74, 20, 140, 255))

    # Golden Lightning Bolt badge (top-right corner of card) -> "Flash"
    bolt_cx = int(canvas_size * 0.77)
    bolt_cy = int(canvas_size * 0.27)
    badge_r = int(canvas_size * 0.13)
    draw.ellipse(
        [bolt_cx - badge_r, bolt_cy - badge_r, bolt_cx + badge_r, bolt_cy + badge_r],
        fill=(255, 193, 7, 255),
        outline=(255, 255, 255, 255),
        width=int(canvas_size * 0.016),
    )

    # Lightning bolt polygon
    s = badge_r * 0.65
    bolt_pts = [
        (bolt_cx + s * 0.15, bolt_cy - s * 0.95),
        (bolt_cx - s * 0.55, bolt_cy + s * 0.08),
        (bolt_cx - s * 0.02, bolt_cy + s * 0.08),
        (bolt_cx - s * 0.18, bolt_cy + s * 0.95),
        (bolt_cx + s * 0.55, bolt_cy - s * 0.08),
        (bolt_cx + s * 0.02, bolt_cy - s * 0.08),
    ]
    draw.polygon(bolt_pts, fill=(49, 27, 146, 255))

    return img.resize((size, size), Image.Resampling.LANCZOS)


project_root = r"C:\Users\Jordan\AndroidStudioProjects\ThaiFlash"
res_dir = os.path.join(project_root, "app", "src", "main", "res")

densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

for folder, size in densities.items():
    folder_path = os.path.join(res_dir, folder)
    os.makedirs(folder_path, exist_ok=True)
    create_thaiflash_icon(size, is_round=False).save(os.path.join(folder_path, "ic_launcher.webp"), "WEBP", quality=95)
    create_thaiflash_icon(size, is_round=True).save(os.path.join(folder_path, "ic_launcher_round.webp"), "WEBP", quality=95)

drawable_dir = os.path.join(res_dir, "drawable")
os.makedirs(drawable_dir, exist_ok=True)

fav_512 = create_thaiflash_icon(512, is_round=False)
fav_512.save(os.path.join(drawable_dir, "app_icon_512.png"), "PNG")

fav_32 = create_thaiflash_icon(32, is_round=False)
fav_32.save(os.path.join(project_root, "favicon.ico"), format="ICO")

print("ThaiFlash launcher icons, app_icon_512.png, and favicon.ico generated successfully!")
