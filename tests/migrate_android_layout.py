"""
core/android 를 표준 Gradle 구조(app/src/main/...)로 이전하고
Kotlin 패키지를 com.yggdrasil.assistant.* 로 통일하는 1회성 마이그레이션 스크립트.
"""
import os
import re
import shutil
import subprocess
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ANDROID = os.path.join(ROOT, "core", "android")
APP_MAIN = os.path.join(ANDROID, "app", "src", "main")
JAVA_BASE = os.path.join(APP_MAIN, "java", "com", "yggdrasil", "assistant")

# 기존 폴더 -> 최종 서브패키지 (패키지명과 디렉터리명을 일치시킴)
DIR_TO_PKG = {
    "integration": "integration",
    "local_llm": "llm",
    "permission": "permission",
    "persona": "persona",
    "privacy": "privacy",
    "proactive": "proactive",
    "rag": "rag",
    "screen_operator": "operator",
    "security": "security",
    "storage": "storage",
    "ui": "ui",
}


def move(src, dst):
    """git 추적 파일은 git mv, 미추적 파일은 일반 이동"""
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    tracked = subprocess.run(
        ["git", "ls-files", "--error-unmatch", src],
        cwd=ROOT, capture_output=True
    ).returncode == 0
    if tracked:
        subprocess.run(["git", "mv", src, dst], cwd=ROOT, check=True)
    else:
        shutil.move(src, dst)
    print(f"  이동: {os.path.relpath(src, ROOT)} -> {os.path.relpath(dst, ROOT)}")


def move_tree(src_dir, dst_dir):
    """디렉터리 내 파일을 하나씩 이동 (중첩 구조 유지)"""
    for base, _, files in os.walk(src_dir):
        for f in files:
            s = os.path.join(base, f)
            d = os.path.join(dst_dir, os.path.relpath(s, src_dir))
            move(s, d)
    shutil.rmtree(src_dir, ignore_errors=True)


print("1) Kotlin 소스 이동")
moved_kt = []
for old_dir, pkg in DIR_TO_PKG.items():
    src_dir = os.path.join(ANDROID, old_dir)
    if not os.path.isdir(src_dir):
        continue
    for f in os.listdir(src_dir):
        s = os.path.join(src_dir, f)
        if os.path.isfile(s) and f.endswith(".kt"):
            d = os.path.join(JAVA_BASE, pkg, f)
            move(s, d)
            moved_kt.append(d)

main_kt = os.path.join(ANDROID, "MainActivity.kt")
if os.path.exists(main_kt):
    d = os.path.join(JAVA_BASE, "MainActivity.kt")
    move(main_kt, d)
    moved_kt.append(d)

print("2) C++ JNI 소스 이동 -> src/main/cpp")
jni_dir = os.path.join(ANDROID, "local_llm", "jni")
if os.path.isdir(jni_dir):
    move_tree(jni_dir, os.path.join(APP_MAIN, "cpp"))

print("3) Manifest / res / assets 이동")
manifest = os.path.join(ANDROID, "AndroidManifest.xml")
if os.path.exists(manifest):
    move(manifest, os.path.join(APP_MAIN, "AndroidManifest.xml"))
res_dir = os.path.join(ANDROID, "res")
if os.path.isdir(res_dir):
    move_tree(res_dir, os.path.join(APP_MAIN, "res"))
old_assets = os.path.join(ANDROID, "src", "main", "assets")
if os.path.isdir(old_assets):
    new_assets = os.path.join(APP_MAIN, "assets")
    os.makedirs(new_assets, exist_ok=True)
    for name in os.listdir(old_assets):
        shutil.move(os.path.join(old_assets, name), os.path.join(new_assets, name))
    shutil.rmtree(os.path.join(ANDROID, "src"), ignore_errors=True)

print("4) 기존 모듈 build.gradle.kts -> app/build.gradle.kts")
old_gradle = os.path.join(ANDROID, "build.gradle.kts")
if os.path.exists(old_gradle):
    move(old_gradle, os.path.join(ANDROID, "app", "build.gradle.kts"))

# 비어버린 옛 디렉터리 정리
for old_dir in DIR_TO_PKG:
    p = os.path.join(ANDROID, old_dir)
    if os.path.isdir(p) and not any(os.scandir(p)):
        os.rmdir(p)

print("5) 패키지 통일: com.yggdrasil.core.* -> com.yggdrasil.assistant.*")
for path in moved_kt:
    with open(path, encoding="utf-8") as fp:
        text = fp.read()
    new = text.replace("com.yggdrasil.core.", "com.yggdrasil.assistant.")
    if new != text:
        with open(path, "w", encoding="utf-8", newline="\n") as fp:
            fp.write(new)
        print(f"  수정: {os.path.relpath(path, ROOT)}")

print("6) package 선언과 디렉터리 일치 여부 검증")
errors = 0
for path in moved_kt:
    with open(path, encoding="utf-8") as fp:
        m = re.search(r"^package\s+([\w.]+)", fp.read(), re.M)
    rel_dir = os.path.relpath(os.path.dirname(path), os.path.join(APP_MAIN, "java"))
    expected = rel_dir.replace(os.sep, ".")
    if not m or m.group(1) != expected:
        errors += 1
        print(f"  [불일치] {os.path.basename(path)}: {m.group(1) if m else '없음'} != {expected}")
print(f"  불일치 {errors}건")
print("완료")
