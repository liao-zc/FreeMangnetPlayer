"""Local clean filter: read through the authorized transparent-decryption reader."""
import sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent
path = (root / sys.argv[1]).resolve()
if not path.is_relative_to(root):
    raise RuntimeError("Filter input must stay in the repository")
data = path.read_bytes()
if b"E-SafeNet" in data[:32] or b"\x00" in data:
    raise RuntimeError("Refusing to stage encrypted or binary source: " + str(path))
data.decode("utf-8-sig", errors="strict")
sys.stdout.buffer.write(data)
