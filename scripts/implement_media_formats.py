from pathlib import Path
p=Path('app/src/main/java/com/freemangnetplayer/MainActivity.kt');s=p.read_text(encoding='utf-8')
s=s.replace('    private val onMagnet: (String, String) -> Unit,\n    private val onPage: (String, String, Int, Int) -> Unit = { _, _, _, _ -> }','    private val onPage: (String, String, Int, Int) -> Unit = { _, _, _, _ -> },\n    private val onMagnet: (String, String) -> Unit')
s=s.replace('pending == 0 && results.isEmpty() && searchState != "error"','pending == 0 && !searchNeedsFormSubmit && results.isEmpty() && searchState != "error"')
p.write_text(s,encoding='utf-8')
p=Path('app/src/main/assets/magnet-search.js');p.write_text(Path('scripts/search-pagination.js').read_text(encoding='utf-8'),encoding='utf-8')
p=Path('app/src/main/java/com/freemangnetplayer/TorrentTasks.kt');s=p.read_text(encoding='utf-8')
start=s.index('    val video get() =');end=s.index('\n',start)
s=s[:start]+'    val video get() = MediaFormats.isVideo(path)'+s[end:];p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/freemangnetplayer/PlayerActivity.kt');s=p.read_text(encoding='utf-8')
s=s.replace('    private fun startPlayback(task: TorrentTask, file: TaskFile) {\n        val realMedia = file.path.substringAfterLast(\'.\').lowercase() in listOf("rm", "rmvb")','    private fun startPlayback(task: TorrentTask, file: TaskFile, forceSoftware: Boolean = false) {\n        val realMedia = forceSoftware || MediaFormats.useSoftware(file.path)')
needle='                        savedPosition = media.currentPosition\n'
assert needle in s;s=s.replace(needle,needle+'''                        if (media !is VlcTorrentPlayer && error.errorCode in 3000..4999) {
                            releasePlayer()
                            playerMessage = "正在尝试兼容解码：${file.path}"
                            startPlayback(task, file, forceSoftware = true)
                            return@post
                        }
''',1)
p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/freemangnetplayer/TorrentHttpStream.kt');s=p.read_text(encoding='utf-8').replace('Content-Type: application/vnd.rn-realmedia','Content-Type: application/octet-stream');p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/freemangnetplayer/VlcTorrentPlayer.kt');s=p.read_text(encoding='utf-8').replace('RealMedia decoder could not read the video stream','兼容解码器无法读取视频流');p.write_text(s,encoding='utf-8')
p=Path('app/build.gradle.kts');s=p.read_text(encoding='utf-8').replace('versionCode = 14; versionName = "0.1.13"','versionCode = 15; versionName = "0.1.14"');p.write_text(s,encoding='utf-8')
