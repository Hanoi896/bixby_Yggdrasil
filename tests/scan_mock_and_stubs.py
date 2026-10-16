import os
import re
import sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

ROOT_DIR = os.path.abspath(".")

EXTENSIONS = {".kt", ".cpp", ".h", ".js", ".bxb"}
EXCLUDE_DIRS = {".git", ".gradle", "build", ".bixby", "node_modules", "llama.cpp", ".system_generated"}

PATTERNS = {
    "TODO/FIXME/Placeholder": re.compile(r'(TODO|FIXME|나중에\s*구현|추후\s*구현|미구현|unchanged code|\.\.\.\s*rest of)', re.IGNORECASE),
    "Mock/Dummy Variable or Class": re.compile(r'\b(mock\w*|dummy\w*|fake\w*)\b', re.IGNORECASE),
    "NotImplemented Exception": re.compile(r'(NotImplementedError|UnsupportedOperationException|TODO\(\))'),
    "Fake Random/Hash Simulator": re.compile(r'(Random\(|hashCode\(\))\s*%\s*\d+'),
}

EMPTY_METHOD_KOTLIN = re.compile(r'fun\s+\w+\s*\([^)]*\)\s*(?::\s*[^\{]+)?\s*\{\s*\}')
EMPTY_METHOD_CPP = re.compile(r'\w+\s+\w+::\w+\s*\([^)]*\)\s*\{\s*\}')

results = []

for root, dirs, files in os.walk(ROOT_DIR):
    dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
    for file in files:
        ext = os.path.splitext(file)[1].lower()
        if ext in EXTENSIONS:
            filepath = os.path.join(root, file)
            relpath = os.path.relpath(filepath, ROOT_DIR).replace("\\", "/")
            try:
                with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                    lines = content.splitlines()

                for i, line in enumerate(lines, start=1):
                    # Check regex patterns
                    for category, pat in PATTERNS.items():
                        m = pat.search(line)
                        if m:
                            # Filter false positives:
                            matched_text = m.group(0)
                            # Ignore comments explaining that we *prohibit* mocks
                            if "Zero-Mock" in line or "절대 금지" in line or "더미" in line and "금지" in line:
                                continue
                            results.append({
                                "file": relpath,
                                "line": i,
                                "category": category,
                                "matched": matched_text,
                                "content": line.strip()
                            })

                # Check empty methods
                if ext == ".kt":
                    for m in EMPTY_METHOD_KOTLIN.finditer(content):
                        line_no = content[:m.start()].count("\n") + 1
                        results.append({
                            "file": relpath,
                            "line": line_no,
                            "category": "Empty Method Body",
                            "matched": m.group(0),
                            "content": m.group(0).strip()
                        })
                elif ext in (".cpp", ".h"):
                    for m in EMPTY_METHOD_CPP.finditer(content):
                        line_no = content[:m.start()].count("\n") + 1
                        results.append({
                            "file": relpath,
                            "line": line_no,
                            "category": "Empty Method Body",
                            "matched": m.group(0),
                            "content": m.group(0).strip()
                        })

            except Exception as e:
                pass

print(f"Total findings: {len(results)}\n")
for r in results:
    print(f"[{r['category']}] {r['file']}:{r['line']}")
    print(f"    Match: {r['matched']}")
    print(f"    Code:  {r['content']}\n")
