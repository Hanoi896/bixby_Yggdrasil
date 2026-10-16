import os
import re
import sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

ROOT_DIR = os.path.abspath(".")

def inspect_file(rel_path):
    full_path = os.path.join(ROOT_DIR, rel_path)
    if not os.path.exists(full_path):
        return f"[NOT FOUND] {rel_path}"
    with open(full_path, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()
    
    issues = []
    
    # 1. Check for empty methods / functions
    empty_kt = re.findall(r'fun\s+([a-zA-Z0-9_]+)\s*\([^)]*\)[^{]*\{\s*\}', content)
    if empty_kt:
        issues.append(f"Empty Kotlin methods: {empty_kt}")
        
    empty_js = re.findall(r'function\s+([a-zA-Z0-9_]+)\s*\([^)]*\)\s*\{\s*\}', content)
    if empty_js:
        issues.append(f"Empty JS functions: {empty_js}")

    # 2. Check for dummy returns: return emptyList(), return null, return "", return true without logic
    dummy_returns = re.findall(r'(\breturn\s+(?:null|""|emptyList\(\)|emptyMap\(\)|false|true|0)\s*;?)', content)
    
    # 3. Check for fake data structures (e.g. hardcoded sample lists)
    fake_lists = re.findall(r'(listOf\([^)]*sample[^)]*\)|listOf\([^)]*test[^)]*\))', content, re.IGNORECASE)
    if fake_lists:
        issues.append(f"Hardcoded test/sample list: {fake_lists}")
        
    # 4. Check for TODO, FIXME, mock, stub
    todos = re.findall(r'//.*(?:TODO|FIXME|나중에|임시|가짜|mock|dummy|깡통).*', content, re.IGNORECASE)
    # filter out comments that say "절대 금지" or "Zero-Mock"
    filtered_todos = [t for t in todos if not any(kw in t for kw in ["금지", "Zero-Mock", "배제하고", "사용하지 않고", "차단"])]
    if filtered_todos:
        issues.append(f"Suspicious comments: {filtered_todos[:3]}")
        
    # 5. Check if class has real implementation
    lines = [line.strip() for line in content.splitlines() if line.strip() and not line.strip().startswith("//")]
    
    return {
        "file": rel_path,
        "total_lines": len(content.splitlines()),
        "effective_lines": len(lines),
        "issues": issues,
        "dummy_returns_count": len(dummy_returns)
    }

kt_files = []
for root, dirs, files in os.walk(os.path.join(ROOT_DIR, "core", "android", "app", "src", "main", "java")):
    for f in files:
        if f.endswith(".kt"):
            kt_files.append(os.path.relpath(os.path.join(root, f), ROOT_DIR).replace("\\", "/"))

js_files = []
for root, dirs, files in os.walk(os.path.join(ROOT_DIR, "playground.yggdrasil", "code")):
    for f in files:
        if f.endswith(".js"):
            js_files.append(os.path.relpath(os.path.join(root, f), ROOT_DIR).replace("\\", "/"))

cpp_files = [
    "core/android/app/src/main/cpp/ExaoneRuntime.cpp",
    "core/android/app/src/main/cpp/ExaoneRuntime.h",
    "core/android/app/src/main/cpp/llama_jni_bridge.cpp"
]

all_files = kt_files + js_files + cpp_files

print(f"=== 전수 검사 대상 파일 수: {len(all_files)}개 ===")
flagged = 0
for f in all_files:
    res = inspect_file(f)
    if res["issues"] or res["dummy_returns_count"] > 5 or res["effective_lines"] < 15:
        flagged += 1
        print(f"\n[FILE] {res['file']} (유효 {res['effective_lines']}줄 / 총 {res['total_lines']}줄)")
        for iss in res["issues"]:
            print(f"  - {iss}")
        if res["dummy_returns_count"] > 5:
            print(f"  - dummy/trivial return 발생 횟수: {res['dummy_returns_count']}회")

print(f"\n=== 검사 완료: 의심/주의 파일 {flagged}개 / 총 {len(all_files)}개 ===")
