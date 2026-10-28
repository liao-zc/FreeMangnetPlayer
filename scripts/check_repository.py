"""Reject transparent-encryption payloads in Git objects before publishing."""
import subprocess

paths = subprocess.check_output(["git", "ls-files"], text=True, encoding="utf-8").splitlines()
text_types = (".md", ".kt", ".kts", ".js", ".py", ".xml", ".yml", ".txt", ".properties", ".pro", ".json")
for path in paths:
    data = subprocess.check_output(["git", "show", "HEAD:" + path])
    if b"E-SafeNet" in data[:32]:
        raise RuntimeError("Encrypted Git object: " + path)
    if path.endswith(text_types):
        if b"\x00" in data:
            raise RuntimeError("Binary data in text Git object: " + path)
        data.decode("utf-8-sig", errors="strict")
    if path.endswith(".png") and not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("Invalid screenshot Git object: " + path)
print(f"Verified {len(paths)} Git objects: no encrypted payloads; source text is UTF-8")
