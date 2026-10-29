from pathlib import Path
p=Path('scripts/verify_player_revision.py');s=p.read_text(encoding='utf-8')
s=s.replace("    user_observation('v0.1.13')", "    adb('shell','am','force-stop',PACKAGE); adb('shell','pm','clear',PACKAGE)\n    adb('shell','pm','grant',PACKAGE,'android.permission.POST_NOTIFICATIONS')\n    adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity'); time.sleep(2)\n    user_observation('v0.1.13')")
p.write_text(s,encoding='utf-8')
p=Path('scripts/build_peer_recovery_docs.py');s=p.read_text(encoding='utf-8')
s=s.replace("text+='\\n每一行按真实观察记录", "native=by['user-resource-v0.1.13'].get('native_baseline',{});ns=native.get('final_state',{})\ntext+=f\"\\n独立原生客户端（runner、纯 BTIH、未注入 .torrent）观察结果：元数据 {ns.get('metadata',False)}，资源节点 {ns.get('peers',0)}，有效载荷 {ns.get('payload_bytes',0):,} 字节；错误 {native.get('error','无额外错误')}。原始数据附在用户资源证据中。APP 还附加公共 Tracker 和 HTTP 元数据补充，因此不是完全相同的发现入口。\\n\"\ntext+='\\n每一行按真实观察记录")
p.write_text(s,encoding='utf-8')
