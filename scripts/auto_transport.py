from pathlib import Path
p = Path('app/src/main/java/com/masterwok/simpletorrentandroid/TorrentSession.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('    val dhtNodes: Long', '''    fun enableOutgoingUtp() {
        val settings = SettingsPack()
        settings.setBoolean(com.frostwire.jlibtorrent.swig.settings_pack.bool_types.enable_outgoing_utp.swigValue(), true)
        sessionManager.applySettings(settings)
    }
    val dhtNodes: Long''')
p.write_text(s, encoding='utf-8')
p = Path('app/src/main/java/com/freemangnetplayer/TorrentTasks.kt')
s = p.read_text(encoding='utf-8').replace('    var lastDiscovery = 0L', '    var lastDiscovery = 0L\n    var outgoingUtp by mutableStateOf(false)')
s = s.replace('                    val engine = TorrentSession(options)', '''                    // Prefer TCP initially: the emulator's throttled uTP path can stall.
                    // Retain incoming uTP and automatically enable outgoing uTP if TCP
                    // fails to produce a useful connection; this is not a bandwidth cap.
                    options.settingsPack.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), false)
                    task.outgoingUtp = false
                    val engine = TorrentSession(options)''')
needle = '                    task.networkMessage = task.engine?.networkMessage.orEmpty()'
assert needle in s
s = s.replace(needle, needle + '''
                    if (!task.outgoingUtp && !task.paused && !task.queued && !task.complete &&
                        System.currentTimeMillis() - task.startedAt > 12_000 &&
                        (task.peers == 0 || task.payloadRate < 1024)) {
                        task.engine?.enableOutgoingUtp(); task.outgoingUtp = true
                        handle.forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL)
                        handle.forceDHTAnnounce(); connectExplicitPeers(task, handle)
                    }''')
p.write_text(s, encoding='utf-8')
p = Path('app/src/main/java/com/freemangnetplayer/PlayerActivity.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('.put("fullscreen", fullscreen)', '.put("outgoingUtp", current.outgoingUtp).put("fullscreen", fullscreen)')
s = s.replace('TCP / uTP、DHT、Tracker、局域网发现均已启用', 'TCP / uTP 自动选择：优先 TCP，无有效传输后自动尝试出站 uTP；DHT、Tracker、局域网发现已启用')
p.write_text(s, encoding='utf-8')
p = Path('scripts/build_performance_docs.py')
s = p.read_text(encoding='utf-8').replace('出站 uTP 原先关闭；现在 TCP 和 uTP 双向均启用，以保留更多可连接资源节点。', '出站 uTP 原先永久关闭。本轮实测发现模拟器限速做种端的 uTP 路径反复读超时，因此现在优先 TCP、保留入站 uTP；12 秒后仍无有效连接时自动启用出站 uTP。该策略选择传输路径，不限制字节速度。')
s = s.replace('TCP/uTP、DHT、Tracker、局域网发现开启', 'TCP/uTP 自动选择，DHT、Tracker、局域网发现开启')
p.write_text(s, encoding='utf-8')
# Handle only a documented emulator launcher ANR; do not dismiss app failures.
p = Path('scripts/verify_optimization.py')
s = p.read_text(encoding='utf-8').replace("    require_visible('设置', '解析资源', '播放', '下载', '收藏')", "    from capture_usage_screenshots import visible_text\n    if \"Quickstep isn't responding\" in visible_text():\n        find_and_tap('Wait')\n        adb('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/.MainActivity')\n        time.sleep(5)\n    require_visible('设置', '解析资源', '播放', '下载', '收藏')", 1)
p.write_text(s, encoding='utf-8')
print('Added TCP-first transport with automatic uTP fallback and explicit environment evidence')
