package com.freemangnetplayer

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.masterwok.simpletorrentandroid.TorrentSession
import com.masterwok.simpletorrentandroid.TorrentSessionOptions
import com.masterwok.simpletorrentandroid.contracts.TorrentSessionListener
import com.masterwok.simpletorrentandroid.models.TorrentSessionStatus
import com.frostwire.jlibtorrent.TorrentHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var session: TorrentSession? = null
    private lateinit var playerView: PlayerView
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        val root=FrameLayout(this); playerView=PlayerView(this); root.addView(playerView,FrameLayout.LayoutParams(-1,-1)); setContentView(root)
        val uri= intent.data?.takeIf { it.scheme=="magnet" }?.toString() ?: intent.getStringExtra("magnet")
        if(uri.isNullOrBlank()){Toast.makeText(this,"没有收到有效磁力链接",Toast.LENGTH_LONG).show();finish();return}
        val folder=File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),"FreeMangnetPlayer").apply{mkdirs()}
        val engine=TorrentSession(TorrentSessionOptions(downloadLocation=folder,onlyDownloadLargestFile=true,shouldStream=true,streamBufferSize=24))
        session=engine
        engine.listener=object:TorrentSessionListener {
            override fun onPieceFinished(h:TorrentHandle,s:TorrentSessionStatus) { openVideo(s.videoFileUri) }
            override fun onAddTorrent(h:TorrentHandle,s:TorrentSessionStatus) { runOnUiThread { Toast.makeText(this@PlayerActivity,"种子已加入，正在获取文件信息",Toast.LENGTH_SHORT).show() } }
            override fun onTorrentError(h:TorrentHandle,s:TorrentSessionStatus) { showError("种子错误：${s.state}") }
            override fun onTorrentFinished(h:TorrentHandle,s:TorrentSessionStatus) { openVideo(s.videoFileUri) }
            override fun onMetadataFailed(h:TorrentHandle,s:TorrentSessionStatus) { showError("无法获取种子文件信息") }
            override fun onMetadataReceived(h:TorrentHandle,s:TorrentSessionStatus) { }
            override fun onTorrentDeleteFailed(h:TorrentHandle,s:TorrentSessionStatus) { }
            override fun onTorrentPaused(h:TorrentHandle,s:TorrentSessionStatus) { }
            override fun onTorrentDeleted(h:TorrentHandle,s:TorrentSessionStatus) { }
            override fun onTorrentRemoved(h:TorrentHandle,s:TorrentSessionStatus) { }
            override fun onTorrentResumed(h:TorrentHandle,s:TorrentSessionStatus) { }
            override fun onBlockUploaded(h:TorrentHandle,s:TorrentSessionStatus) { }
        }
        lifecycleScope.launch(Dispatchers.IO){ try { engine.start(this@PlayerActivity,Uri.parse(uri)) } catch(e:Exception){showError("启动下载失败：${e.localizedMessage}")} }
    }
    private fun openVideo(uri:Uri){ runOnUiThread { if(player!=null)return@runOnUiThread
        player=ExoPlayer.Builder(this).build().also { it.setMediaItem(MediaItem.fromUri(uri));it.prepare();it.playWhenReady=true;playerView.player=it }
    } }
    private fun showError(message:String){runOnUiThread{Toast.makeText(this,message,Toast.LENGTH_LONG).show()}}
    override fun onDestroy(){player?.release();player=null;super.onDestroy()}
}
