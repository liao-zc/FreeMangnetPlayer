"""Independent native client discovery of the exact user-provided info hash."""
from pathlib import Path
s=Path('scripts/public_network_probe.py').read_text(encoding='utf-8')
start=s.index("    base = ")
end=s.index("    session = ",start)
s=s[:start]+'''    digest = 'f1006d95049a840aa34cec95967ea590bf5ffeaf'
    plain = f'magnet:?xt=urn:btih:{digest}'
    result.update({'resource':'User-provided magnet','id':digest,'plain_magnet':plain})
'''+s[end:]
s=s.replace(' < 75:', ' < 150:').replace('public-baseline-data','user-baseline-data').replace('public-network-baseline.json','user-network-baseline.json')
exec(compile(s,'user_network_probe','exec'))
