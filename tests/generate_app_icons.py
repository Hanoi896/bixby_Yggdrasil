import os
from PIL import Image, ImageOps, ImageDraw

src_path = r"C:\Users\nhj23\.gemini\antigravity-ide\brain\67357968-aec3-4ec2-87f4-348a485ecfdd\.user_uploaded\media_1791980543086.jpg"
res_dir = r"c:\Users\nhj23\bixby-workspace\bixby_Yggdrasil\core\android\res"

print(f"이미지 로드 중: {src_path}")
img = Image.open(src_path).convert("RGBA")
w, h = img.size

# 하단 텍스트(pixta 워터마크)를 피하고 마법진 중심을 정사각형으로 크롭
# 이미지 중심 계산
center_x = w // 2
center_y = int(h * 0.47)  # 마법진 중심이 약간 위쪽에 위치함
radius = int(min(w, h) * 0.44) # 원형 마법진 바깥 테두리까지 포괄

box = (center_x - radius, center_y - radius, center_x + radius, center_y + radius)
cropped = img.crop(box)

# 512x512 마스터 생성 (순백 배경 + 선명한 흑색 마법진)
master_size = 512
master_img = cropped.resize((master_size, master_size), Image.Resampling.LANCZOS)

# 원형(Round) 아이콘용 마스크 생성
round_mask = Image.new("L", (master_size, master_size), 0)
draw = ImageDraw.Draw(round_mask)
draw.ellipse((0, 0, master_size, master_size), fill=255)

master_round = master_img.copy()
master_round.putalpha(round_mask)

# 안드로이드 해상도별 저장 디렉터리 및 크기
densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

for folder, size in densities.items():
    target_folder = os.path.join(res_dir, folder)
    os.makedirs(target_folder, exist_ok=True)
    
    # 1. 일반 사각/적응형 아이콘
    icon_square = master_img.resize((size, size), Image.Resampling.LANCZOS)
    icon_square.save(os.path.join(target_folder, "ic_launcher.png"), "PNG")
    
    # 2. 원형 아이콘 (One UI용)
    icon_round = master_round.resize((size, size), Image.Resampling.LANCZOS)
    icon_round.save(os.path.join(target_folder, "ic_launcher_round.png"), "PNG")
    print(f"생성 완료: {folder} ({size}x{size})")

# 고해상도 drawable 생성
drawable_dir = os.path.join(res_dir, "drawable")
os.makedirs(drawable_dir, exist_ok=True)
master_img.save(os.path.join(drawable_dir, "ic_launcher.png"), "PNG")
master_round.save(os.path.join(drawable_dir, "ic_launcher_round.png"), "PNG")

print("✨ 모든 안드로이드 규격 앱 아이콘 생성 완료!")
