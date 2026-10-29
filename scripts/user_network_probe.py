"""Independent native client discovery of the exact user-provided info hash."""
from pathlib import Path
s=Path('scripts/public_network_probe.py').read_text(encoding='utf-8')
start=s.index("    base = ")
end=s.index("    session = ",start)
s=s[:start]+'''    digest = '2362347540b27d8a4142af2bbe0a071f26a3b7a1'
    plain = f'magnet:?xt=urn:btih:{digest}'
    result.update({'resource':'User-provided magnet','id':digest,'plain_magnet':plain})
'''+s[end:]
s=s.replace(' < 75:', ' < 150:').replace('public-baseline-data','user-baseline-data').replace('public-network-baseline.json','user-network-baseline.json')
exec(compile(s,'user_network_probe','exec'))
