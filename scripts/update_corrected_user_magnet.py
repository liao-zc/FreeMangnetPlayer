from pathlib import Path
old='f1006d95049a840aa34cec95967ea590bf5ffeaf'
new='2362347540b27d8a4142af2bbe0a071f26a3b7a1'
for name in ['verify_player_revision.py','user_network_probe.py','probe_user_metadata.py','build_peer_recovery_docs.py']:
    p=Path('scripts')/name;s=p.read_text(encoding='utf-8').replace(old,new).replace(old.upper(),new.upper())
    s=s.replace('iTorrents .net/.org 对本哈希返回 404，另外两个入口返回的内容不是种子数据。', '各入口的状态码、返回字节数与内容是否像种子数据，详见原始记录。此检查只代表 HTTP 服务观察，不代表播放成功。')
    p.write_text(s,encoding='utf-8')
