"""Fail CI for unreadable Markdown, encrypted bytes, or broken local links."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
count = 0
for path in ROOT.rglob("*.md"):
    if any(part in {".git", ".gradle", "build"} for part in path.parts):
        continue
    data = path.read_bytes()
    text = data.decode("utf-8-sig", errors="strict")
    if b"E-SafeNet" in data[:256] or b"\x00" in data:
        raise RuntimeError(f"Unreadable/encrypted Markdown: {path}")
    for target in re.findall(r"\]\(([^)]+)\)", text):
        target = target.split("#", 1)[0].strip("<>")
        if not target or "://" in target or target.startswith("mailto:"):
            continue
        linked = (path.parent / target).resolve()
        if not linked.is_relative_to(ROOT) or not linked.is_file():
            raise RuntimeError(f"Broken relative link in {path}: {target}")
        if linked.suffix.lower() == ".png" and not linked.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError(f"Invalid PNG linked by {path}: {target}")
    count += 1
print(f"Validated {count} UTF-8 Markdown files and their relative links")
