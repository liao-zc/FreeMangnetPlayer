from pathlib import Path
p=Path('app/src/main/java/com/freemangnetplayer/TorrentHttpStream.kt');s=p.read_text(encoding='utf-8')
s=s.replace('    val uri: Uri = Uri.parse("http://127.0.0.1:${server.localPort}/$token/video.rmvb")','    private val resourcePath = "/$token/video.${MediaFormats.extension(target.path).ifBlank { "bin" }}"\n    val uri: Uri = Uri.parse("http://127.0.0.1:${server.localPort}$resourcePath")')
s=s.replace('request[1].substringBefore(\'?\') != "/$token/video.rmvb"','request[1].substringBefore(\'?\') != resourcePath')
p.write_text(s,encoding='utf-8')
