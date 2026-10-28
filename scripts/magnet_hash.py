import base64
import html
import re

def canonical_hash(uri):
    match = re.search(r'(?i)xt=urn:btih:([a-f0-9]{40}|[a-z2-7]{32})(?=[&\s"<>]|$)', html.unescape(uri))
    if not match:
        return None
    value = match.group(1)
    return value.lower() if len(value) == 40 else base64.b32decode(value.upper()).hex()
