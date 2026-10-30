package com.freemangnetplayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun ManagedLibrary(title: String, entries: List<LibraryEntry>, kind: String,
    onOpen: (LibraryEntry) -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    var selected by remember(kind) { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf(false) }
    var deleteData by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Row {
            TextButton(onClick = { selected = entries.map { it.source }.toSet() }) { Text("全选") }
            TextButton(onClick = { selected = emptySet() }) { Text("取消选择") }
            TextButton(onClick = { deleteData = kind == "缓存" || kind == "下载"; confirm = true }, enabled = selected.isNotEmpty()) { Text("删除所选 (${selected.size})") }
        }
        if(kind=="收藏") TextButton(onClick={
            var added=0
            entries.filter { it.source in selected }.forEach { entry -> runCatching {
                val t=TorrentTasks.obtain(context,entry.source,entry.title,true)
                val index=Regex("[?&]so=(\\d+)").find(entry.source)?.groupValues?.get(1)?.toIntOrNull()
                if(index!=null && t.files.isNotEmpty()) TorrentTasks.select(t,t.selected+index)
                TorrentTasks.download(t);added++
            }.onFailure { error=it.localizedMessage.orEmpty() } }
            if(added>0) { error="已添加 $added 个下载任务，请在下载列表查看";onRefresh() }
        },enabled=selected.isNotEmpty()) { Text("下载所选 (${selected.size})") }
        if (entries.isEmpty()) Text("这里暂无记录")
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.source }) { entry -> Card {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row {
                        Checkbox(entry.source in selected, { checked -> selected = if (checked) selected + entry.source else selected - entry.source })
                        Text(entry.title, Modifier.weight(1f))
                    }
                    Text(entry.status)
                    if (entry.totalBytes > 0) Text("${entry.downloadedBytes / 1024} KB / ${entry.totalBytes / 1024} KB")
                    Row {
                        TextButton(onClick = { onOpen(entry) }) { Text("播放 / 打开") }
                        TextButton(onClick = { selected = setOf(entry.source); deleteData = kind == "缓存" || kind == "下载"; confirm = true }) { Text("删除") }
                    }
                }
            } }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("删除所选 ${selected.size} 项？") },
        text = { Column {
            Text("缓存及下载删除会停止对应任务并清理所选本机文件。收藏和播放记录默认只删除记录，可勾选同时删除文件。")
            Row { Checkbox(deleteData, { deleteData = it }, enabled = kind != "缓存" && kind != "下载"); Text("同时删除本机下载 / 缓存文件") }
        } }, confirmButton = { TextButton(onClick = {
            error = StoredVideos.delete(context, kind, selected, deleteData).orEmpty()
            if (error.isEmpty()) { selected = emptySet(); confirm = false; onRefresh() }
        }) { Text("确认删除") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}
